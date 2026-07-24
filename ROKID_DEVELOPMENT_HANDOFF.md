# Rokid Glasses 开发交接

## 目标

在 Windows 电脑上使用 Android Studio 开发 Rokid Glasses 应用。目前已完成 Android 手机作为 CXR-L 客户端，与 Rokid AI App 和眼镜建立双向通信，准备进入正式功能开发。

## 项目位置

- 工作区：`E:\workbuddy\Rokid`
- Android 项目：`E:\workbuddy\Rokid\Android Project`
- 应用包名：`com.example.myapplication`
- Debug APK：`E:\workbuddy\Rokid\Android Project\app\build\outputs\apk\debug\app-debug.apk`
- 主要页面：`app/src/main/java/com/example/myapplication/MainActivity.kt`
- Application：`app/src/main/java/com/example/myapplication/RokidApplication.kt`

## 环境与设备

- Android Studio 和 Android SDK 已安装。
- Android 手机型号：`LYN-AN00`。
- 手机通过无线 ADB 连接电脑。
- 常用 ADB 路径：`C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- 曾使用的设备序列号：`adb-AKAD6R4716002061-NrYHb1 (2)._adb-tls-connect._tcp`
- Rokid AI App 版本：`1.10.12.0713`。
- 眼镜系统版本曾显示：`1.22.009-20260710-150201`。

设备序列号可能变化，每次操作前先运行：

```powershell
& "C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices -l
```

## SDK 配置

项目已完成以下配置：

- Maven 仓库：`https://maven.rokid.com/repository/maven-public/`
- 依赖：`com.rokid.cxr:client-l:1.0.4`
- `minSdk = 31`
- Manifest 已声明 `android.permission.INTERNET`
- Manifest 已注册 `RokidApplication`

官方文档入口：

`https://custom.rokid.com/prod/rokid_web/84feb39f8ef141b0ad0326f902ab881f/pc/cn/3b63d21420e645e3affca478b39e4a13.html`

## 已实现并实测通过

1. 通过 Rokid AI App 获取授权 token。
2. 使用 `CXRLink.connect(token)` 连接 Rokid 服务。
3. 监听 `onCXRLConnected` 和 `onGlassBtConnected`，确认眼镜真实连接。
4. 配置 `CUSTOMVIEW` CXR 会话并监听会话状态。
5. 使用 `customViewOpen()` 在眼镜显示“Rokid 双向通信成功”。
6. 使用 `customViewClose()` 关闭眼镜显示。
7. 使用 `takePhoto(1280, 720, 70)` 调用眼镜相机。
8. 通过 `IImageStreamCbk` 接收 JPEG，在手机页面显示缩略图。
9. 照片保存到：
   `/sdcard/Android/data/com.example.myapplication/files/Pictures/`

完整链路已验证：

`电脑代码 -> Android 手机应用 -> Rokid AI App -> Rokid Glasses -> 图片回传手机 -> 内容显示回眼镜`

## 当前手机界面

- “授权并连接眼镜”
- “眼镜显示测试”
- “关闭眼镜显示”
- “眼镜拍照”
- 状态文字
- 最近一次眼镜照片缩略图

## 关键 SDK 陷阱

### 1. connect 返回值不代表连接完成

`CXRLink.connect(token)` 返回 `true` 只表示连接请求已提交。真实状态必须以以下回调为准：

- `onCXRLConnected(true)`
- `onGlassBtConnected(true)`

### 2. 必须先配置会话

拍照等能力要求当前会话类型不是 `NONE`。当前使用：

```kotlin
CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMVIEW)
```

### 3. 图片回调注册顺序非常重要

正确顺序：

1. 创建 `CXRLink`
2. 设置连接回调
3. `configCXRSession(CUSTOMVIEW, sessionCallback)`
4. `setCXRImageCbk(imageCallback)`
5. `setCXRCustomViewCbk(customViewCallback)`
6. `connect(token)`

如果在配置会话前调用 `setCXRImageCbk()`，SDK 会静默忽略回调，表现为眼镜已拍照但手机永远收不到图片。

### 4. Custom View 打开后再调用子能力

官方流程要求收到 `onCustomViewOpened()` 后，再调用拍照或音频能力。当前应用已经据此控制按钮状态。

### 5. 眼镜权限不是 Android 运行时权限

眼镜相机和麦克风权限通过 Rokid 授权接口请求：

```kotlin
arrayOf(GlassPermission.CAMERA, GlassPermission.MICROPHONE)
```

已有 token 时可能不会再次弹授权页面，这是正常行为。

## Custom View 协议

当前使用官方 JSON Schema：

- 根节点：`LinearLayout`
- 文本节点：`TextView`
- 每个节点必须有唯一 `props.id`
- 尺寸必须带 `dp`，字号必须带 `sp`
- `customViewOpen()` 传完整视图树 JSON
- `customViewUpdate()` 传增量更新数组
- `ImageView` 需要先用 `customViewSetIcons()` 下发 Base64 PNG

当前显示测试只使用文本，不需要图标资源。

## 构建与安装

构建：

```powershell
cd "E:\workbuddy\Rokid\Android Project"
.\gradlew.bat assembleDebug
```

安装并启动，先将 `<serial>` 替换为 `adb devices -l` 显示的实际序列号：

```powershell
$adb = "C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb -s "<serial>" install -r "app\build\outputs\apk\debug\app-debug.apk"
& $adb -s "<serial>" shell monkey -p com.example.myapplication 1
```

## 正式开发建议

推荐第一个正式功能做“视觉助手”：

1. 用户从手机按钮或语音触发拍照。
2. 眼镜拍摄当前视野并回传手机。
3. 手机将 JPEG 发送给视觉 AI。
4. AI 返回物体识别、OCR 或场景描述。
5. 使用 `customViewUpdate()` 把结果显示到眼镜。
6. 可选：将结果转为语音播报。

开始前需要确定：

- 具体产品用途，例如 OCR 翻译、场景问答、物品识别或工作辅助。
- 使用哪家视觉 AI 服务及 API 密钥管理方案。
- 是否需要语音触发和语音播报。

正式发布前还应完成：

- 将包名从 `com.example.myapplication` 改为正式包名。
- 修改应用名称和图标。
- 把 Activity 中的连接、会话、拍照逻辑拆到 ViewModel/Repository。
- 安全保存 token 和 API 密钥，禁止将密钥硬编码进 APK。
- 增加断线重连、超时、生命周期清理和错误状态。
