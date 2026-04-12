package com.pbz.healthmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OldRegisterScreen(
    onBackClick: () -> Unit,
    onRegisterClick: (account: String, password: String) -> Unit,
    registerError: String = "",
    isLoading: Boolean = false
) {
    RegisterFormScreen(
        title = "老人账号注册",
        themeColor = Color(0xFF2E7D32),
        onBackClick = onBackClick,
        onRegisterClick = onRegisterClick,
        registerError = registerError,
        isLoading = isLoading
    )
}

@Composable
fun KidRegisterScreen(
    onBackClick: () -> Unit,
    onRegisterClick: (account: String, password: String) -> Unit,
    registerError: String = "",
    isLoading: Boolean = false
) {
    RegisterFormScreen(
        title = "子女账号注册",
        themeColor = Color(0xFF2979FF),
        onBackClick = onBackClick,
        onRegisterClick = onRegisterClick,
        registerError = registerError,
        isLoading = isLoading
    )
}

@Composable
private fun RegisterFormScreen(
    title: String,
    themeColor: Color,
    onBackClick: () -> Unit,
    onRegisterClick: (account: String, password: String) -> Unit,
    registerError: String,
    isLoading: Boolean
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F8FA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("返回登录", color = Color(0xFF374151))
            }

            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text("手机号") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码（至少6位）") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("确认密码") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            if (registerError.isNotBlank()) {
                Text(registerError, color = Color(0xFFD32F2F), fontSize = 13.sp)
            }

            Button(
                onClick = {
                    if (password != confirmPassword) return@Button
                    onRegisterClick(account, password)
                },
                enabled = !isLoading && password == confirmPassword,
                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (isLoading) "注册中..." else "注册账号", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            if (password != confirmPassword && confirmPassword.isNotBlank()) {
                Text("两次密码不一致", color = Color(0xFFD32F2F), fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
