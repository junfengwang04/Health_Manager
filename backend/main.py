from datetime import datetime, timedelta
import hashlib
import os
import sqlite3
from typing import Dict

from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, Field


DB_PATH = os.getenv("AUTH_DB_PATH", "auth.db")
PASSWORD_SALT = os.getenv("PASSWORD_SALT", "health_manager_default_salt")
RATE_LIMIT_WINDOW_SECONDS = 60
RATE_LIMIT_MAX_REGISTER_PER_IP = 1

app = FastAPI(title="Health Manager Auth API", version="1.0.0")
register_hits: Dict[str, datetime] = {}


class RegisterRequest(BaseModel):
    phone: str = Field(min_length=11, max_length=11)
    password: str = Field(min_length=6, max_length=128)
    name: str = Field(min_length=1, max_length=64)
    role: str = Field(default="elder")


class LoginRequest(BaseModel):
    phone: str = Field(min_length=11, max_length=11)
    password: str = Field(min_length=6, max_length=128)


def db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    conn = db()
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            phone TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            name TEXT NOT NULL,
            role TEXT NOT NULL,
            created_at TEXT NOT NULL
        )
        """
    )
    conn.commit()
    conn.close()


def hash_password(password: str) -> str:
    return hashlib.sha256(f"{password}:{PASSWORD_SALT}".encode("utf-8")).hexdigest()


def client_ip(request: Request) -> str:
    forwarded_for = request.headers.get("x-forwarded-for")
    if forwarded_for:
        return forwarded_for.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


def check_register_rate_limit(ip: str) -> None:
    now = datetime.utcnow()
    last_hit = register_hits.get(ip)
    if last_hit and now - last_hit < timedelta(seconds=RATE_LIMIT_WINDOW_SECONDS):
        retry_after = RATE_LIMIT_WINDOW_SECONDS - int((now - last_hit).total_seconds())
        raise HTTPException(status_code=429, detail=f"注册过于频繁，请 {retry_after}s 后重试")
    register_hits[ip] = now


@app.on_event("startup")
def startup() -> None:
    init_db()


@app.post("/auth/register")
def register(payload: RegisterRequest, request: Request):
    ip = client_ip(request)
    check_register_rate_limit(ip)
    conn = db()
    try:
        existing = conn.execute("SELECT id FROM users WHERE phone = ?", (payload.phone,)).fetchone()
        if existing:
            raise HTTPException(status_code=409, detail="手机号已注册")
        conn.execute(
            "INSERT INTO users(phone, password_hash, name, role, created_at) VALUES(?,?,?,?,?)",
            (
                payload.phone,
                hash_password(payload.password),
                payload.name.strip(),
                payload.role.strip().lower(),
                datetime.utcnow().isoformat(),
            ),
        )
        conn.commit()
        return {"ok": True, "message": "注册成功"}
    finally:
        conn.close()


@app.post("/auth/login")
def login(payload: LoginRequest):
    conn = db()
    try:
        row = conn.execute(
            "SELECT phone, name, role, password_hash FROM users WHERE phone = ?",
            (payload.phone,),
        ).fetchone()
        if not row:
            raise HTTPException(status_code=401, detail="账号或密码错误")
        if row["password_hash"] != hash_password(payload.password):
            raise HTTPException(status_code=401, detail="账号或密码错误")
        return {
            "ok": True,
            "user": {
                "phone": row["phone"],
                "name": row["name"],
                "role": row["role"],
            },
        }
    finally:
        conn.close()
