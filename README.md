# Health Manager 3.0

面向老人健康管理与子女远程监护的 Android 应用。  
项目聚焦“用药提醒 + 健康记录 + 家属协同 + 云端同步”，适用于课程作品展示、毕设原型与移动端健康场景实践。

## 项目定位

- 目标人群：老人用户（被监护端）与子女用户（监护端）
- 核心价值：提升服药依从性、简化健康记录、降低家庭照护沟通成本
- 形态特点：双角色登录、跨设备同步、弱网可用（本地缓存 + 云端快照）

## 核心功能

### 老人端

- 药品识别入库（OCR 识别国药准字）
- 闹钟提醒与服药决策（已服/待会吃/跳过）
- 饮食识别与热量记录
- 血压等健康指标录入与趋势查看
- AI 健康总结与语音播报
- 设备码展示与绑定请求确认

### 子女端

- 多老人绑定与关系管理（发起绑定、取消绑定）
- 实时健康监控卡片（多个老人并行展示）
- 异常预警（血压异常/服药未完成/无数据）
- 点击进入老人健康详情页
- 一键导出近 7 天血压与历史服药记录

## 技术栈

- 客户端语言：Kotlin
- UI：Jetpack Compose
- 架构：ViewModel + Repository + Room
- 本地数据库：Room
- 相机能力：CameraX
- OCR：ML Kit Chinese Text Recognition
- 网络：OkHttp + Retrofit + Ktor
- 云端服务：PocketBase（账号、绑定关系、快照同步）
- AI：Qwen 兼容接口
- 识别能力：百度菜品识别接口
- 调度与提醒：AlarmManager + Foreground Service + WorkManager
- 可选后端：FastAPI + SQLite（鉴权演示）

## 技术路线

### 路线一：数据链路

1. 本地采集（药品/服药/血压/饮食/识别历史）写入 Room  
2. 老人端登录后周期上云（elder 快照）  
3. 子女端基于绑定关系拉取老人快照并本地呈现  
4. 绑定变化时即时刷新监护列表与数据

### 路线二：账号与绑定

1. 老人端/子女端独立注册登录  
2. 老人端持有 6 位设备码  
3. 子女端输入设备码发起绑定请求  
4. 老人端确认后关系激活，监护数据联通

### 路线三：提醒与适老化交互

1. 精确闹钟 + 前台服务 + 全屏通知  
2. 厂商权限引导（例如 OPPO 自启动/电池优化）  
3. 弱网/失败兜底与可见提示

## 项目结构

```text
app/src/main/java/com/pbz/healthmanager
├─ alarm/                  闹钟调度、广播、前台服务、权限引导
├─ analysis/               OCR 解析逻辑
├─ data/
│  ├─ local/               Room 实体/DAO/数据库
│  ├─ remote/              PocketBase/AI/识别接口配置与服务
│  └─ repository/          业务数据编排
├─ ui/screens/             Compose 页面
└─ viewmodel/              状态管理与业务流转
```

## 环境要求

- Android Studio Hedgehog 或更高
- JDK 17（建议）
- Android SDK 34
- 最低系统：Android 7.0（API 24）
- 推荐真机：Android 12+（权限与闹钟行为更接近线上）

## 配置说明

### 1) 复制配置模板

将 `local.properties.example` 复制为项目根目录 `local.properties`，并填写你自己的配置：

```properties
PB_BASE_URL=http://your-pocketbase-host:8090
PB_ADMIN_EMAIL=your_admin_email@example.com
PB_ADMIN_PASSWORD=your_admin_password

QWEN_API_KEY=your_qwen_api_key
QWEN_CHAT_URL=https://your_qwen_endpoint/v1/chat/completions
QWEN_MODEL=qwen3.5-flash

BAIDU_API_KEY=your_baidu_api_key
BAIDU_SECRET_KEY=your_baidu_secret_key
BAIDU_TOKEN_URL=https://your_baidu_token_endpoint/oauth/2.0/token
BAIDU_FOOD_IDENTIFY_URL=https://your_baidu_food_identify_endpoint/rest/2.0/image-classify/v2/dish
```

可选项（如你仍使用 Bmob）：

```properties
BMOB_APP_ID=your_bmob_app_id
BMOB_REST_KEY=your_bmob_rest_key
BMOB_MASTER_KEY=your_bmob_master_key
BMOB_BASE_URL=https://api.bmobapp.com
```

### 2) 本仓库默认不包含任何真实密钥

- 已移除真实 Key、真实 IP、真实域名、真实账号密码
- 所有服务配置由 `local.properties` 注入 BuildConfig
- 开源仓库仅保留模板，不保留生产凭据

## 运行方式

### Android 客户端

1. 导入项目到 Android Studio  
2. 填写 `local.properties`  
3. 直接运行 `app` 模块到真机

### 可选 FastAPI 鉴权服务

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

接口：

- `POST /auth/register`（带基础限流）
- `POST /auth/login`

## 使用流程（演示版）

### 老人端

1. 注册并登录老人账号  
2. 扫描药品并配置提醒  
3. 录入血压与饮食  
4. 查看总结报告与设备码  
5. 在“绑定请求”中确认子女绑定

### 子女端

1. 注册并登录子女账号  
2. 在“添加绑定”输入老人设备码发起请求  
3. 老人确认后自动出现在监护列表  
4. 在健康监控页查看多老人状态与告警  
5. 进入详情页导出血压和服药历史

## 开源发布到 GitHub / Gitee 操作

### 1) 本地安全检查

- 确认 `local.properties`、`.env`、`.db` 未被提交
- 确认源码中无 `sk-`、`AKIA`、`Bearer xxx` 等明文密钥
- 如历史提交曾出现密钥，先轮换密钥再清理历史

### 2) 初始化仓库并推送

```bash
git init
git add .
git commit -m "feat: initial open-source release"
git branch -M main
git remote add github https://github.com/<your-name>/<your-repo>.git
git push -u github main
git remote add gitee https://gitee.com/<your-name>/<your-repo>.git
git push -u gitee main
```

## 安全与隐私建议

- 不在源码写入任何真实 API Key、服务器地址、账号密码
- 生产环境使用服务端签发短时 Token
- 对敏感操作加日志审计与限流策略
- 健康数据遵循最小化采集与最小化可见原则

## 许可证

本项目采用双许可证模式：

- 开源协议：AGPL-3.0-only
- 商业闭源或不按 AGPL 义务使用：需获得作者商业授权

补充声明：

- 仓库开源代码主要用于学习交流
- 商业使用若需豁免 AGPL 义务，必须获得作者授权

详见 `LICENSE` 文件。

## 免责声明

本项目用于学习、演示与原型验证，不构成医疗诊断或治疗建议。  
请在实际应用中遵守当地法律法规和隐私合规要求。
