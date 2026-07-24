# Rokid 天气

Android 手机端通过 Rokid CXR-L SDK 连接 Rokid AI App 与眼镜，在眼镜 Custom View 中显示杭州实时天气。

## 当前功能

- 通过 GPS 或网络定位获取当前位置，并从 `wttr.in` 查询当地实时天气。
- 在手机和眼镜上显示今天、明天、后天的天气及最低/最高温度。
- 定位权限拒绝、定位关闭或超时时自动回退到杭州天气。
- 启动后自动请求 Rokid 授权并连接眼镜，无需手动点击连接。
- “查询并显示天气”一次完成重新定位、刷新天气和打开或更新眼镜界面。
- 在手机页面查看并手动刷新天气。
- 授权连接 Rokid 眼镜并打开、增量更新或关闭天气 Custom View。
- 每 30 分钟在前台自动刷新天气。
- 调用眼镜相机并在手机端显示、保存回传照片。
- 处理天气重试、协程取消、重复连接、断线和 Activity 销毁清理。

## 环境要求

- Android Studio 与 JDK 11。
- Android SDK 36；手机系统 API 31 或更高。
- Rokid AI App / Hi Rokid 已安装并登录。
- Rokid 眼镜与手机蓝牙连接。
- 手机可以访问互联网和 `wttr.in`。

## 构建与测试

在本目录执行：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

生成的可安装 APK：

```text
app\build\outputs\apk\debug\app-debug.apk
```

## 安装与启动

```powershell
$adb = "C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
& $adb -s "<serial>" install -r "app\build\outputs\apk\debug\app-debug.apk"
& $adb -s "<serial>" shell am start -n com.example.myapplication/.MainActivity
```

## 验收流程

1. 启动应用并授予定位权限，确认手机显示当地实时天气。
2. 确认应用自动完成 Rokid 授权和眼镜连接。
3. 点击“查询并显示天气”，确认应用重新定位、刷新天气并在眼镜显示。
4. 再次点击“查询并显示天气”，确认眼镜界面执行增量更新。
5. 点击“眼镜拍照”，确认手机显示并保存回传照片。
6. 点击“关闭眼镜显示”，确认 Custom View 关闭。
7. 强制停止并重新启动应用，确认天气可重新加载且连接入口可用。

## 当前限制

- 尚未提供手动城市选择；无法定位时固定回退到杭州。
- 天气依赖公开的 `wttr.in` 服务，不提供可用性保证。
- 当前包名仍为 `com.example.myapplication`，使用 debug 签名，仅用于开发验收。
- 正式发布前需要确定正式包名、应用图标、版本策略和 release 签名。
