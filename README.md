# Rokid AR Glasses Weather Assistant

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.x-blue.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-green.svg)](https://developer.android.com/jetpack/compose)

一个面向 **Rokid AR 智能眼镜** 的双端天气助手应用 —— Android 手机作为中继桥梁，连接眼镜硬件和云端 AI，在眼镜上实时显示天气信息并支持远程拍照。

> 项目已通过完整链路验证：电脑代码 → Android 手机 → Rokid AI App → Rokid Glasses → 图片回传 → 眼镜显示

---

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                      用户操作                            │
│              (语音 / 手机按钮 / 眼镜触控)                  │
└──────────────┬──────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────┐
│                  Android 手机 (Kotlin)                    │
│  ┌──────────────────────────────────────────────────┐   │
│  │  MainActivity                                    │   │
│  │  ├─ 授权管理 (AuthorizationHelper)               │   │
│  │  ├─ 连接管理 (CXRLink)                           │   │
│  │  ├─ 天气获取 (WeatherRepository → wttr.in API)   │   │
│  │  ├─ 定位服务 (GPS + Geocoder)                    │   │
│  │  ├─ Custom View 渲染 (JSON → 眼镜 UI)            │   │
│  │  └─ 拍照管理 (图像流回调)                         │   │
│  └──────────────────────────────────────────────────┘   │
│                         │  CXR-L SDK                     │
│                         │  (蓝牙 / Wi-Fi)                 │
└─────────────────────────┼──────────────────────────────┘
                          │
┌─────────────────────────▼──────────────────────────────┐
│                   Rokid AI App                          │
│              (Rokid 眼镜系统服务)                          │
└─────────────────────────┬──────────────────────────────┘
                          │
┌─────────────────────────▼──────────────────────────────┐
│                   Rokid Glasses                         │
│  ├─ 显示 Custom View (天气卡片)                           │
│  ├─ 执行拍照命令                                          │
│  └─ 回传 JPEG 图像流                                      │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│              AIUI Agent (Rokid 小程序)                  │
│  直接在眼镜 OS 运行，支持语音对话式天气查询                 │
└─────────────────────────────────────────────────────────┘
```

## 技术栈

| 模块 | 技术 |
|------|------|
| **Android App** | Kotlin, Jetpack Compose, CXR-L SDK |
| **构建工具** | Gradle (Kotlin DSL), Version Catalog |
| **天气数据** | [wttr.in](https://wttr.in) 免费 API |
| **眼镜 SDK** | `com.rokid.cxr:client-l:1.0.4` |
| **AIUI Agent** | Rokid AIUI 框架 (ink 模板语言) |
| **最低 Android** | API 31 (Android 12+) |

## 项目结构

```
Rokid/
├── README.md                          # 项目文档
├── LICENSE                            # MIT 许可证
├── .gitignore                         # Git 忽略规则
├── ROKID_DEVELOPMENT_HANDOFF.md       # 开发交接文档（详细配置/陷阱记录）
├── deliverables/                      # 可交付产物
│   ├── Rokid-Weather-v1.0-debug.apk   # 编译好的 APK
│   ├── SHA256SUMS.txt                 # 校验和
│   └── step11-phone.png               # 运行截图
│
├── Android Project/                   # Android 手机端应用
│   ├── build.gradle.kts               # 根级构建脚本
│   ├── settings.gradle.kts            # Gradle 设置
│   ├── gradle.properties              # Gradle 属性
│   ├── gradlew / gradlew.bat          # Gradle Wrapper
│   ├── gradle/
│   │   ├── libs.versions.toml         # 依赖版本管理
│   │   └── wrapper/                   # Wrapper 配置
│   └── app/
│       ├── build.gradle.kts           # App 模块构建
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── res/                   # 资源文件
│           ├── keepRules/             # R8 混淆规则
│           └── java/com/example/myapplication/
│               ├── MainActivity.kt            # ★ 主界面 (657 行)
│               ├── RokidApplication.kt        # Application 单例
│               ├── ui/theme/                  # Compose 主题
│               ├── weather/                   # 天气模块
│               │   ├── WeatherInfo.kt         # 数据模型
│               │   ├── WeatherService.kt      # HTTP 客户端
│               │   ├── WeatherRepository.kt   # 业务逻辑 (含28城市中英映射)
│               │   └── WeatherCustomViewFactory.kt  # Custom View JSON 生成
│               └── location/                  # 定位模块
│                   ├── DeviceLocationProvider.kt   # GPS 定位
│                   └── LocationNameResolver.kt     # 反地理编码
│
└── my-rokid-app/                      # Rokid AIUI 眼镜端应用
    ├── app.js                         # 应用入口
    ├── app.json                       # 页面路由配置
    ├── package.json                   # npm 配置
    ├── preview.html                   # 浏览器端天气卡片预览
    └── pages/
        ├── index/index.ink            # 首页
        └── weather/index.ink          # ★ 天气卡片 (语音对话)
```

## 核心功能

### ✅ 已实现并通过实测

1. **Rokid 眼镜连接与授权**
   - 通过 Rokid AI App 获取授权 token
   - CXRLink 连接、蓝牙状态监听
   - 连接状态实时反馈

2. **眼镜 Custom View 天气显示**
   - 支持按城市名（中文/英文）查询天气
   - 支持 GPS 自动定位获取本地天气
   - 每 30 分钟自动刷新
   - 显示：城市、天气状况、温度、体感温度、湿度、风力、更新时间

3. **眼镜远程拍照**
   - 1280×720 JPEG 拍照
   - 图像实时回传手机显示
   - 照片保存到本地存储

4. **AIUI 语音天气卡片**（眼镜端原生体验）
   - 语音触发天气查询
   - 精美天气卡片（当前 + 三天预报）
   - 深色主题，适合 AR 场景

### 🔮 下一步建议

- **视觉助手**：拍照 → AI 识别 → 结果显示到眼镜
- **语音播报**：眼镜直接播报天气/识别结果
- **架构重构**：Activity 逻辑拆分到 ViewModel/Repository
- **正式发布**：更换正式包名、加强断线重连和错误处理

## 快速开始

### 环境要求

- **Android Studio** (最新版推荐)
- **Android SDK** (API 31+)
- **Rokid AI App** (v1.10.12+ 已安装在手机上)
- **Rokid AR Glasses** 与手机配对
- **Node.js** (AIUI 小程序调试用)

### 构建与安装 Android 应用

```bash
# 1. 进入 Android 项目目录
cd "Android Project"

# 2. 构建 Debug APK
./gradlew assembleDebug

# 3. 连接手机（无线 ADB），确认设备在线
adb devices -l

# 4. 安装到手机
adb -s <序列号> install -r app/build/outputs/apk/debug/app-debug.apk

# 5. 启动应用
adb -s <序列号> shell monkey -p com.example.myapplication 1
```

### 使用步骤

1. 打开手机上的 Rokid AI App，确保眼镜已连接
2. 启动本应用，点击「授权并连接眼镜」
3. 授权通过后，点击「眼镜显示测试」查看连接确认
4. 点击「获取天气」在眼镜上显示天气信息
5. 天气每 30 分钟自动刷新
6. 点击「眼镜拍照」控制眼镜拍照并在手机查看照片

### AIUI 小程序调试

```bash
cd my-rokid-app
npm install
npm start
```

### 天气卡片浏览器预览

```bash
cd my-rokid-app
# 用任意 HTTP 服务器启动
npx serve .
# 打开 http://localhost:3000/preview.html
```

## ⚠️ 关键 SDK 陷阱

这些是实际开发中踩过的坑，请务必注意：

| 陷阱 | 说明 |
|------|------|
| **connect() 不阻塞** | `CXRLink.connect(token)` 返回 true 不代表连接完成，必须等 `onCXRLConnected(true)` + `onGlassBtConnected(true)` 回调 |
| **回调注册顺序** | 必须先 `configCXRSession()` 再 `setCXRImageCbk()`，否则 SDK 静默忽略图片回调 |
| **Custom View 先打开** | 拍照前必须先收到 `onCustomViewOpened()` 回调 |
| **眼镜权限特殊** | 眼镜相机/麦克风权限通过 `GlassPermission` 请求，不走 Android 运行时权限 |

> 更多细节见 [ROKID_DEVELOPMENT_HANDOFF.md](ROKID_DEVELOPMENT_HANDOFF.md)

## 天气 API

使用 [wttr.in](https://wttr.in) 免费天气 API，无需注册和 API Key：

- 按城市：`https://wttr.in/Shanghai?format=j1`
- 按坐标：`https://wttr.in/31.23,121.47?format=j1`

支持 28 个中国主要城市的中英文名称自动映射。

## 相关资源

- [Rokid 开发者文档](https://custom.rokid.com/prod/rokid_web/84feb39f8ef141b0ad0326f902ab881f/pc/cn/3b63d21420e645e3affca478b39e4a13.html)
- [CXR-L SDK 参考](https://maven.rokid.com/repository/maven-public/)
- [wttr.in API 文档](https://github.com/chubin/wttr.in)

## License

MIT License - 详见 [LICENSE](LICENSE) 文件
