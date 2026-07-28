# Rokid Weather

一个面向 Rokid AR 眼镜的 Android 天气显示示例。手机应用获取当前位置和天气数据，通过公开的 CXR-L SDK 将当前天气与未来三天预报显示到眼镜 Custom View。

## 功能

- 通过 Rokid AI App / Hi Rokid 完成授权和眼镜连接
- 使用手机 GPS 获取当前位置，定位不可用时降级到杭州
- 从 `wttr.in` 获取当前天气和未来三天预报
- 显示温度、体感温度、湿度、风向、风速和更新时间
- 通过 CXR Custom View 在眼镜端显示和刷新天气
- 应用运行期间每 30 分钟自动刷新天气
- 提供可选的 Rokid AIUI 天气卡片示例和浏览器预览

本仓库专注于天气查询与眼镜显示，不包含其他业务场景。

## 工作流程

```text
手机定位
  -> wttr.in 天气接口
  -> Android 天气数据模型
  -> CXR-L Custom View JSON
  -> Rokid 眼镜显示
```

## 项目结构

```text
.
├── Android Project/        Android 手机应用与 CXR-L 眼镜显示
│   ├── app/src/main/java/
│   │   └── com/example/myapplication/
│   │       ├── location/   手机定位与地名解析
│   │       └── weather/    天气请求、解析和 Custom View
│   └── app/src/test/       天气解析单元测试
├── my-rokid-app/           Rokid AIUI 天气卡片示例
└── LICENSE                 MIT License
```

## 环境要求

- Android Studio（使用自带 JDK）
- Android SDK 36.1；最低运行版本 Android 12 / API 31
- Rokid AI App 或 Hi Rokid 已在手机安装并登录
- Rokid 眼镜已与手机完成配对
- 可访问 `wttr.in` 和 Rokid Maven 仓库的网络
- Node.js，仅在调试 `my-rokid-app` 时需要

Android 应用使用：

```kotlin
implementation("com.rokid.cxr:client-l:1.0.4")
```

## 构建 Android 应用

首次构建前，在 `Android Project/local.properties` 配置本机 Android SDK 路径。该文件已被 Git 忽略：

```properties
sdk.dir=C\:\\Users\\your-name\\AppData\\Local\\Android\\Sdk
```

Windows：

```powershell
cd "Android Project"
.\gradlew.bat testDebugUnitTest assembleDebug
```

macOS / Linux：

```bash
cd "Android Project"
./gradlew testDebugUnitTest assembleDebug
```

生成的 APK 位于：

```text
Android Project/app/build/outputs/apk/debug/app-debug.apk
```

## 使用步骤

1. 打开 Rokid AI App / Hi Rokid，确认眼镜蓝牙已连接。
2. 在手机上启动 Rokid Weather。
3. 允许位置权限；拒绝后应用会显示杭州天气。
4. 点击“查询并显示天气”。
5. 完成 Rokid 授权后，天气卡片会显示在眼镜上。
6. 点击“关闭眼镜显示”可关闭 Custom View。

## AIUI 天气卡片

`my-rokid-app` 是独立的天气卡片示例，可导入 Rokid AIUI 开发工具。浏览器预览不需要安装依赖，直接打开 `my-rokid-app/preview.html` 即可。

## 天气接口

应用使用 [wttr.in](https://github.com/chubin/wttr.in) 的 JSON 接口，无需 API Key：

```text
https://wttr.in/Hangzhou?format=j1
https://wttr.in/30.27410,120.15510?format=j1
```

请求失败时最多重试一次。城市和天气描述在客户端转换为适合中文显示的文本。

## 权限与隐私

- `INTERNET`：访问天气接口并连接 Rokid 服务。
- `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION`：获取本地天气。
- 应用不申请手机或眼镜相机权限。
- 应用不持久化位置历史。
- 查询本地天气时，当前位置坐标会发送给 `wttr.in`；生产使用前应根据实际隐私政策替换或自建天气服务。

## 已知限制

- CXR-L 依赖 Rokid AI App / Hi Rokid，不能脱离配套应用独立连接眼镜。
- 天气展示依赖网络和第三方免费服务，不提供可用性保证。
- 示例包名仍为 `com.example.myapplication`，正式发布前应替换为自有包名。
- Rokid SDK 1.0.4 使用旧版 Activity Result 回调，编译时可能出现弃用警告。

## License

[MIT License](LICENSE)
