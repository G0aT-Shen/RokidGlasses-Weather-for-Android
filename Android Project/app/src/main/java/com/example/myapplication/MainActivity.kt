package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.location.DeviceLocationProvider
import com.example.myapplication.location.LocationNameResolver
import com.example.myapplication.weather.WeatherCustomViewFactory
import com.example.myapplication.weather.WeatherInfo
import com.example.myapplication.weather.WeatherRepository
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.ICXRLinkCbk
import com.rokid.cxr.link.callbacks.ICXRSessionCbk
import com.rokid.cxr.link.callbacks.ICustomViewCbk
import com.rokid.cxr.link.callbacks.IImageStreamCbk
import com.rokid.cxr.link.utils.CxrDefs
import com.rokid.cxr.link.utils.GlassInfo
import com.rokid.sprite.aiapp.externalapp.auth.AuthResult
import com.rokid.sprite.aiapp.externalapp.auth.AuthorizationHelper
import com.rokid.sprite.aiapp.externalapp.auth.GlassPermission
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private companion object {
        const val AUTH_REQUEST_CODE = 1001
        const val WEATHER_REFRESH_INTERVAL_MS = 30 * 60 * 1000L
    }

    private var status by mutableStateOf("尚未连接")
    private var isConnectionInProgress by mutableStateOf(false)
    private var isGlassesConnected by mutableStateOf(false)
    private var isCustomViewOpen by mutableStateOf(false)
    private var latestPhoto by mutableStateOf<Bitmap?>(null)
    private var photoPending = false
    private val weatherRepository = WeatherRepository()
    private var weather by mutableStateOf<WeatherInfo?>(null)
    private var isWeatherLoading by mutableStateOf(false)
    private var weatherError by mutableStateOf<String?>(null)
    private var weatherRequestJob: Job? = null
    private var weatherAutoRefreshJob: Job? = null
    private var locationJob: Job? = null
    private var isLocating by mutableStateOf(false)
    private var currentCoordinates: Pair<Double, Double>? = null
    private var currentLocationName: String? = null
    private var initialConnectionRequested = false
    private var displayWeatherWhenReady = false
    private val deviceLocationProvider by lazy { DeviceLocationProvider(this) }
    private val locationNameResolver by lazy { LocationNameResolver(this) }
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            locateAndLoadWeather()
        } else {
            useDefaultLocation("未授予定位权限，显示杭州天气")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = status)
                        WeatherSection(
                            weather = weather,
                            isLoading = isWeatherLoading,
                            isLocating = isLocating,
                            error = weatherError,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Button(
                            onClick = ::queryAndDisplayWeather,
                            enabled = !isConnectionInProgress && !isLocating && !isWeatherLoading,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(
                                when {
                                    isConnectionInProgress -> "正在连接眼镜"
                                    isLocating -> "正在定位"
                                    isWeatherLoading -> "正在查询天气"
                                    else -> "查询并显示天气"
                                }
                            )
                        }
                        Button(
                            onClick = ::closeCustomView,
                            enabled = isCustomViewOpen,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text("关闭眼镜显示")
                        }
                        Button(
                            onClick = ::takePhoto,
                            enabled = isGlassesConnected && isCustomViewOpen,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text("眼镜拍照")
                        }
                        latestPhoto?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "眼镜拍摄的照片",
                                modifier = Modifier.padding(top = 16.dp).size(240.dp)
                            )
                        }
                    }
                }
            }
        }
        requestLocationWeather()
        startWeatherAutoRefresh()
    }

    private fun loadWeather() = loadWeather(null)

    private fun loadWeather(onSuccess: ((WeatherInfo) -> Unit)?) {
        if (isWeatherLoading || isLocating) return
        isWeatherLoading = true
        weatherError = null
        val coordinates = currentCoordinates
        weatherRequestJob = lifecycleScope.launch {
            try {
                val latestWeather = if (coordinates == null) {
                    weatherRepository.getWeather("杭州")
                } else {
                    weatherRepository.getWeather(
                        coordinates.first,
                        coordinates.second,
                        currentLocationName,
                    )
                }
                weather = latestWeather
                isWeatherLoading = false
                onSuccess?.invoke(latestWeather)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("RokidWeather", "Weather request failed", error)
                val message = when (error) {
                    is SocketTimeoutException -> "天气请求超时，请稍后重试"
                    is UnknownHostException -> "无法连接天气服务，请检查网络"
                    else -> error.message ?: "天气数据获取失败，请稍后重试"
                }
                weatherError = message
                if (onSuccess != null) status = "天气查询失败：$message"
            } finally {
                isWeatherLoading = false
                weatherRequestJob = null
            }
        }
    }

    private fun refreshWeather() {
        loadWeather(::updateOpenWeatherView)
    }

    private fun queryAndDisplayWeather() {
        displayWeatherWhenReady = true
        authorizeAndConnect()
        requestLocationWeather()
    }

    private fun requestLocationWeather() {
        if (isLocating || isWeatherLoading) return
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFineLocation || hasCoarseLocation) {
            locateAndLoadWeather()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    private fun locateAndLoadWeather() {
        if (isLocating || isWeatherLoading) return
        if (!deviceLocationProvider.isLocationEnabled()) {
            useDefaultLocation("定位服务未开启，显示杭州天气")
            return
        }

        isLocating = true
        weatherError = null
        status = "正在获取当前位置..."
        locationJob = lifecycleScope.launch {
            try {
                val location = deviceLocationProvider.getCurrentLocation()
                if (location == null) {
                    isLocating = false
                    useDefaultLocation("定位超时，显示杭州天气")
                } else {
                    currentCoordinates = location.latitude to location.longitude
                    currentLocationName = locationNameResolver.resolve(
                        location.latitude,
                        location.longitude,
                    )
                    isLocating = false
                    requestInitialConnection()
                    status = "定位成功，正在获取当地天气"
                    loadWeather(::handleLocatedWeather)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecurityException) {
                isLocating = false
                useDefaultLocation("无法使用定位权限，显示杭州天气")
            } finally {
                isLocating = false
                locationJob = null
            }
        }
    }

    private fun useDefaultLocation(message: String) {
        currentCoordinates = null
        currentLocationName = null
        status = message
        requestInitialConnection()
        loadWeather(::handleWeatherResult)
    }

    private fun handleLocatedWeather(latestWeather: WeatherInfo) {
        status = "已定位到 ${latestWeather.city}"
        handleWeatherResult(latestWeather)
    }

    private fun handleWeatherResult(latestWeather: WeatherInfo) {
        if (displayWeatherWhenReady) {
            displayPendingWeather(latestWeather)
        } else {
            updateOpenWeatherView(latestWeather)
        }
    }

    private fun displayPendingWeather(latestWeather: WeatherInfo? = weather) {
        if (!displayWeatherWhenReady || isLocating || isWeatherLoading) return
        val readyWeather = latestWeather ?: return
        val link = (application as RokidApplication).sharedLink
        if (link == null || !link.isGlassBtConnected()) {
            status = "天气已更新，正在等待眼镜连接"
            return
        }
        isGlassesConnected = true
        displayWeatherOnGlasses(link, readyWeather)
    }

    private fun requestInitialConnection() {
        if (initialConnectionRequested) return
        initialConnectionRequested = true
        authorizeAndConnect()
    }

    private fun startWeatherAutoRefresh() {
        weatherAutoRefreshJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(WEATHER_REFRESH_INTERVAL_MS)
                    refreshWeather()
                }
            }
        }
    }

    private fun updateOpenWeatherView(latestWeather: WeatherInfo) {
        if (!isCustomViewOpen) return

        val link = (application as RokidApplication).sharedLink
        if (link == null || !link.isGlassBtConnected() || !link.customViewIsOpen()) {
            isCustomViewOpen = false
            return
        }

        status = if (link.customViewUpdate(WeatherCustomViewFactory.createUpdate(latestWeather))) {
            "天气已刷新，正在更新眼镜显示..."
        } else {
            "天气已刷新，但眼镜更新请求发送失败"
        }
    }

    private fun authorizeAndConnect() {
        val rokidApplication = application as RokidApplication
        if (rokidApplication.sharedLink?.isGlassBtConnected() == true) {
            isGlassesConnected = true
            isConnectionInProgress = false
            status = "眼镜已连接，无需重复连接"
            displayPendingWeather()
            return
        }
        if (isConnectionInProgress) return

        if (!AuthorizationHelper.isRequiredRokidAppInstalled(this) &&
            !AuthorizationHelper.isRequiredHiRokidInstalled(this)
        ) {
            status = "请先安装并登录 Rokid AI App / Hi Rokid"
            return
        }

        isConnectionInProgress = true
        status = "正在请求眼镜相机权限..."
        val result = AuthorizationHelper.requestAuthorization(
            this,
            arrayOf(GlassPermission.CAMERA, GlassPermission.MICROPHONE),
            AUTH_REQUEST_CODE
        )
        result?.let { handleAuthorizationResult(it.first, it.second) }
    }

    @Deprecated("Rokid SDK 1.0.4 uses the activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTH_REQUEST_CODE) {
            handleAuthorizationResult(resultCode, data)
        }
    }

    private fun handleAuthorizationResult(resultCode: Int, data: Intent?) {
        when (val result = AuthorizationHelper.parseAuthorizationResult(resultCode, data)) {
            is AuthResult.AuthSuccess -> connectToGlasses(result.token)
            is AuthResult.AuthFail -> {
                isConnectionInProgress = false
                status = "授权失败，请重试"
            }
            is AuthResult.AuthCancel -> {
                isConnectionInProgress = false
                status = "已取消授权"
            }
        }
    }

    private fun connectToGlasses(token: String) {
        status = "授权成功，正在连接眼镜..."
        val rokidApplication = application as RokidApplication
        rokidApplication.resetSession()
        val link = CXRLink(applicationContext)
        link.setCXRLinkCbk(createLinkCallback(link))
        val sessionConfigured = link.configCXRSession(
            CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMVIEW),
            createSessionCallback()
        )
        if (!sessionConfigured) {
            isConnectionInProgress = false
            link.disconnect()
            status = "CXR 会话配置失败"
            return
        }
        link.setCXRImageCbk(createImageCallback())
        link.setCXRCustomViewCbk(createCustomViewCallback())
        val requestSent = link.connect(token)
        rokidApplication.sharedLink = link
        status = if (requestSent) {
            "连接请求已发送，等待真实连接结果"
        } else {
            isConnectionInProgress = false
            rokidApplication.resetSession()
            "连接请求失败，请确认 Rokid AI App 已登录"
        }
    }

    private fun createLinkCallback(link: CXRLink) = object : ICXRLinkCbk {
        override fun onCXRLConnected(isConnected: Boolean) {
            runOnUiThread {
                isGlassesConnected = isConnected && link.isGlassBtConnected()
                if (!isConnected || isGlassesConnected) isConnectionInProgress = false
                status = when {
                    isGlassesConnected -> "眼镜已连接，可以测试显示"
                    isConnected -> "已连接 Rokid 服务，正在等待眼镜蓝牙连接"
                    else -> "Rokid 服务连接失败，请打开 Rokid AI App"
                }
                if (isGlassesConnected) displayPendingWeather()
            }
        }

        override fun onGlassBtConnected(isConnected: Boolean) {
            runOnUiThread {
                isGlassesConnected = isConnected
                isConnectionInProgress = false
                if (!isConnected) {
                    isCustomViewOpen = false
                    photoPending = false
                }
                status = if (isConnected) "眼镜已连接，可以测试显示" else "眼镜蓝牙未连接"
                if (isConnected) displayPendingWeather()
            }
        }

        override fun onGlassDeviceInfo(deviceInfo: GlassInfo) = Unit
        override fun onGlassWearingStatus(wearing: Boolean) = Unit
        override fun onGlassAiAssistStart() = Unit
        override fun onGlassAiAssistStop() = Unit
        override fun onGlassAiInterrupt(interruptWake: Boolean) = Unit
    }

    private fun createCustomViewCallback() = object : ICustomViewCbk {
        override fun onCustomViewOpened() {
            runOnUiThread {
                displayWeatherWhenReady = false
                isCustomViewOpen = true
                status = "天气已显示在眼镜上"
            }
        }

        override fun onCustomViewUpdated() {
            runOnUiThread {
                displayWeatherWhenReady = false
                status = "眼镜天气已更新"
            }
        }

        override fun onCustomViewClosed() {
            runOnUiThread {
                isCustomViewOpen = false
                status = "眼镜显示已关闭"
            }
        }

        override fun onCustomViewIconsSent() = Unit

        override fun onCustomViewError(errorCode: Int, message: String?) {
            runOnUiThread {
                displayWeatherWhenReady = false
                isCustomViewOpen = false
                status = "眼镜显示失败（$errorCode）：${message.orEmpty()}"
            }
        }
    }

    private fun createImageCallback() = object : IImageStreamCbk {
        override fun onImageReceived(data: ByteArray?) {
            Log.i("RokidDemo", "Image callback bytes=${data?.size ?: -1}")
            photoPending = false
            if (data == null || data.isEmpty()) {
                runOnUiThread { status = "眼镜返回了空图片，请重新拍照" }
                return
            }
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            val picturesDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val photoFile = File(picturesDirectory, "rokid_${System.currentTimeMillis()}.jpg")
            photoFile.writeBytes(data)
            runOnUiThread {
                latestPhoto = bitmap
                status = "拍照成功，已保存 ${photoFile.name}"
            }
        }

        override fun onImageError(errorCode: Int, message: String) {
            runOnUiThread {
                status = "拍照失败（$errorCode）：$message"
            }
        }
    }

    private fun createSessionCallback() = object : ICXRSessionCbk {
        override fun onSessionAvailable(reason: CxrDefs.CXRSessionReason) {
            runOnUiThread {
                status = if (isGlassesConnected) {
                    "CXR 会话可用，可以测试显示"
                } else {
                    "CXR 会话可用，正在等待眼镜蓝牙连接"
                }
                displayPendingWeather()
            }
        }

        override fun onSessionStart(reason: CxrDefs.CXRSessionReason) {
            runOnUiThread {
                status = if (isCustomViewOpen) {
                    "天气已显示在眼镜上"
                } else {
                    "CXR 会话运行中"
                }
                displayPendingWeather()
            }
        }

        override fun onSessionPause(reason: CxrDefs.CXRSessionReason) {
            runOnUiThread { status = "CXR 会话已暂停：$reason" }
        }

        override fun onSessionUnavailable(reason: CxrDefs.CXRSessionReason) {
            runOnUiThread { status = "CXR 会话不可用：$reason" }
        }
    }

    private fun displayWeatherOnGlasses(link: CXRLink, latestWeather: WeatherInfo) {
        val viewIsOpen = link.customViewIsOpen()
        val requestSent = if (viewIsOpen) {
            link.customViewUpdate(WeatherCustomViewFactory.createUpdate(latestWeather))
        } else {
            link.customViewOpen(WeatherCustomViewFactory.create(latestWeather))
        }

        status = when {
            !requestSent && viewIsOpen -> "眼镜天气更新请求发送失败"
            !requestSent -> "眼镜天气显示请求发送失败"
            viewIsOpen -> "天气已获取，正在更新眼镜显示..."
            else -> "天气已获取，正在打开眼镜显示..."
        }
    }

    private fun closeCustomView() {
        val link = (application as RokidApplication).sharedLink
        if (link == null || !link.customViewClose()) {
            status = "关闭眼镜显示失败"
        }
    }

    private fun takePhoto() {
        val link = (application as RokidApplication).sharedLink
        if (link == null || !link.isGlassBtConnected()) {
            isGlassesConnected = false
            status = "眼镜未连接，请先重新连接"
            return
        }
        if (!link.customViewIsOpen()) {
            isCustomViewOpen = false
            status = "请先打开眼镜天气显示"
            return
        }

        status = "正在调用眼镜相机..."
        photoPending = true
        if (!link.takePhoto(1280, 720, 70)) {
            photoPending = false
            val cameraGranted = AuthorizationHelper.hasGlassPermission(GlassPermission.CAMERA)
            status = if (cameraGranted) {
                "拍照请求失败：CXR 媒体服务尚未就绪"
            } else {
                "拍照请求失败：未授予眼镜相机权限"
            }
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (photoPending) {
                photoPending = false
                status = "15 秒内未收到图片，请确认眼镜处于佩戴和亮屏状态"
            }
        }, 15_000)
    }

    override fun onDestroy() {
        weatherRequestJob?.cancel()
        weatherAutoRefreshJob?.cancel()
        locationJob?.cancel()
        if (!isChangingConfigurations) {
            val rokidApplication = application as RokidApplication
            rokidApplication.sharedLink?.let { link ->
                if (link.customViewIsOpen()) link.customViewClose()
            }
            rokidApplication.resetSession()
        }
        super.onDestroy()
    }
}

@Composable
private fun WeatherSection(
    weather: WeatherInfo?,
    isLoading: Boolean,
    isLocating: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            isLocating -> Text("正在定位...")
            isLoading -> Text("正在获取天气...")
            error != null -> Text(error)
            weather != null -> {
                Text("实时天气")
                Text("${weather.city} · ${weather.condition} · ${weather.temperature}°C")
                Text("体感 ${weather.feelsLike}°C · 湿度 ${weather.humidity}%")
                Text("${weather.wind} · ${weather.updateTime} 更新")
                if (weather.forecast.isNotEmpty()) {
                    Text("未来三天", modifier = Modifier.padding(top = 8.dp))
                    weather.forecast.forEach { forecast ->
                        Text(forecast.displayText())
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RokidPreview() {
    MyApplicationTheme {
        Text("Rokid Glasses")
    }
}
