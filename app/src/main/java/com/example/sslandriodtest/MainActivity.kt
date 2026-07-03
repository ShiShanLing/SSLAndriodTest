package com.example.sslandriodtest

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.PoiInfo
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.poi.*
import com.example.sslandriodtest.ui.theme.SSLAndriodTestTheme

class MainActivity : ComponentActivity() {

    private var mapView: MapView? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "需要位置权限才能使用虚拟定位功能", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        SDKInitializer.setAgreePrivacy(applicationContext, true)
        SDKInitializer.initialize(applicationContext)
        SDKInitializer.setCoordType(CoordType.BD09LL)

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        setContent {
            SSLAndriodTestTheme {
                MockLocationScreen(onMapViewCreated = { mapView = it })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }
}

// ── WGS-84 → BD-09 坐标转换（用于地图显示）──
fun gpsToBdLatLng(wgsLat: Double, wgsLng: Double): LatLng {
    val (bdLat, bdLng) = wgs84ToBd09(wgsLat, wgsLng)
    return LatLng(bdLat, bdLng)
}

@SuppressLint("MissingPermission")
fun getRealLocation(context: Context, forceFresh: Boolean = false, onLocation: (LatLng) -> Unit) {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(context, "未授予位置权限", Toast.LENGTH_SHORT).show()
        return
    }

    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    var delivered = false

    // 第一步：先尝试获取最后已知位置（快速响应）
    // forceFresh=true 时跳过缓存，直接请求新位置（停止模拟后缓存里可能还是假位置）
    if (!forceFresh) {
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null && !delivered) {
                    delivered = true
                    onLocation(gpsToBdLatLng(location.latitude, location.longitude))
                }
            }
            .addOnFailureListener { /* 继续等待下面的实时定位 */ }
    }

    // 第二步：请求一次高精度实时定位
    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
        .setMinUpdateIntervalMillis(500L)
        .setMaxUpdates(1)
        .build()

    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (delivered) return
            result.lastLocation?.let { loc ->
                delivered = true
                onLocation(gpsToBdLatLng(loc.latitude, loc.longitude))
                // 拿到后停止更新
                fusedClient.removeLocationUpdates(this)
            }
        }
    }

    fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())

    // 3秒超时保护
    Handler(Looper.getMainLooper()).postDelayed({
        if (!delivered) {
            fusedClient.removeLocationUpdates(callback)
            Toast.makeText(context, "定位超时，请确保已开启GPS并到开阔地带重试", Toast.LENGTH_LONG).show()
        }
    }, 3000)
}

// ── 检查本应用是否已设为模拟位置应用 ──
fun isMockLocationApp(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_MOCK_LOCATION,
        Process.myUid(),
        context.packageName
    ) == AppOpsManager.MODE_ALLOWED
}

@Composable
fun MockLocationScreen(onMapViewCreated: (MapView) -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var baiduMap by remember { mutableStateOf<BaiduMap?>(null) }
    var selectedLatLng by remember { mutableStateOf(LatLng(39.9042, 116.4074)) }
    var isMocking by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var selectedAddress by remember { mutableStateOf("北京市（默认）") }
    var statusMessage by remember { mutableStateOf("就绪") }
    var showFavoritesDialog by remember { mutableStateOf(false) }
    var isMockApp by remember { mutableStateOf(isMockLocationApp(context)) }
    val favoriteManager = remember { FavoriteManager(context) }
    var favorites by remember { mutableStateOf(favoriteManager.getAll()) }

    // 生命周期监听：从设置页返回时重新检查模拟位置权限
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isMockApp = isMockLocationApp(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 地图就绪后：绑定拖动监听 + 自动定位到真实位置
    LaunchedEffect(baiduMap) {
        baiduMap?.setOnMapStatusChangeListener(object : BaiduMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(status: com.baidu.mapapi.map.MapStatus?) {}
            override fun onMapStatusChangeStart(status: com.baidu.mapapi.map.MapStatus?, reason: Int) {}
            override fun onMapStatusChange(status: com.baidu.mapapi.map.MapStatus?) {}
            override fun onMapStatusChangeFinish(status: com.baidu.mapapi.map.MapStatus?) {
                status?.target?.let { latLng -> selectedLatLng = latLng }
            }
        })
        // 启动时自动定位到真实位置
        getRealLocation(context) { realLatLng ->
            selectedLatLng = realLatLng
            selectedAddress = "我的位置"
            baiduMap?.setMapStatus(
                MapStatusUpdateFactory.newLatLngZoom(realLatLng, 15f)
            )
            statusMessage = "已定位到我的位置"
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── 百度地图 ──
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).also { mv ->
                        onMapViewCreated(mv)
                        baiduMap = mv.map.apply {
                            setMapStatus(
                                MapStatusUpdateFactory.newLatLngZoom(
                                    LatLng(39.9042, 116.4074), 13f
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── 中心红色定位标记 ──
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE53935),
                    modifier = Modifier.size(20.dp),
                    shadowElevation = 4.dp
                ) {}
                Surface(
                    color = Color.White,
                    modifier = Modifier.size(7.dp).padding(1.dp),
                    shape = CircleShape
                ) {}
            }

            // ── 右侧浮动按钮：定位到我 + 缩放 ──
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 定位到我
                FloatingActionButton(
                    onClick = {
                        getRealLocation(context, forceFresh = true) { realLatLng ->
                            selectedLatLng = realLatLng
                            selectedAddress = "我的位置"
                            baiduMap?.setMapStatus(
                                MapStatusUpdateFactory.newLatLngZoom(realLatLng, 16f)
                            )
                            statusMessage = "已定位到我的位置"
                            if (isMocking) {
                                val (wgsLat, wgsLng) = bd09ToWgs84(realLatLng.latitude, realLatLng.longitude)
                                MockLocationService.update(context, wgsLat, wgsLng)
                                statusMessage = "模拟位置已更新到我的位置"
                            }
                        }
                    },
                    containerColor = Color.White,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "定位到我",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                }
                // 放大
                FloatingActionButton(
                    onClick = {
                        baiduMap?.let { map ->
                            val current = map.mapStatus?.zoom ?: 13f
                            map.setMapStatus(MapStatusUpdateFactory.zoomTo((current + 1f).coerceAtMost(21f)))
                        }
                    },
                    containerColor = Color.White,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "放大", tint = Color.DarkGray, modifier = Modifier.size(22.dp))
                }
                // 缩小
                FloatingActionButton(
                    onClick = {
                        baiduMap?.let { map ->
                            val current = map.mapStatus?.zoom ?: 13f
                            map.setMapStatus(MapStatusUpdateFactory.zoomTo((current - 1f).coerceAtLeast(3f)))
                        }
                    },
                    containerColor = Color.White,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "缩小", tint = Color.DarkGray, modifier = Modifier.size(22.dp))
                }
            }

            // ── 顶部搜索栏 ──
            TopSearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .align(Alignment.TopCenter),
                onClick = { showSearchDialog = true }
            )

            // ── 底部控制面板 ──
            BottomControlPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                selectedLatLng = selectedLatLng,
                selectedAddress = selectedAddress,
                isMocking = isMocking,
                statusMessage = statusMessage,
                onSaveFavorite = {
                    val name = selectedAddress.takeIf { it != "北京市（默认）" && it != "我的位置" }
                        ?: "坐标点(${String.format("%.3f", selectedLatLng.latitude)},${String.format("%.3f", selectedLatLng.longitude)})"
                    favoriteManager.add(FavoriteLocation(name, selectedLatLng.latitude, selectedLatLng.longitude))
                    favorites = favoriteManager.getAll()
                    statusMessage = "已收藏：$name"
                },
                onOpenFavorites = { showFavoritesDialog = true },
                onUseMyLocation = {
                    statusMessage = "正在获取真实位置..."
                    getRealLocation(context, forceFresh = true) { realLatLng ->
                        selectedLatLng = realLatLng
                        selectedAddress = "我的位置"
                        baiduMap?.setMapStatus(
                            MapStatusUpdateFactory.newLatLngZoom(realLatLng, 16f)
                        )
                        if (isMocking) {
                            val (wgsLat, wgsLng) = bd09ToWgs84(realLatLng.latitude, realLatLng.longitude)
                            MockLocationService.update(context, wgsLat, wgsLng)
                            statusMessage = "模拟位置已切换到我的当前位置"
                        } else {
                            statusMessage = "已定位到我的位置"
                        }
                    }
                },
                onDiagnose = {
                    val (wgsLat, wgsLng) = bd09ToWgs84(selectedLatLng.latitude, selectedLatLng.longitude)
                    val msg = if (isMocking) {
                        "正在模拟定位\n" +
                        "目标 WGS-84: ${String.format("%.6f", wgsLat)}, ${String.format("%.6f", wgsLng)}\n" +
                        "已通过 GPS + Network + Fused 三个 Provider 注入\n" +
                        "已清除 Mock 标记，第三方 App 应该读到模拟位置"
                    } else {
                        "当前未在模拟定位\n" +
                        "目标坐标: ${String.format("%.6f", wgsLat)}, ${String.format("%.6f", wgsLng)}\n\n" +
                        "点击「开始模拟定位」启动注入"
                    }
                    AlertDialog.Builder(context)
                        .setTitle("🔍 模拟定位诊断")
                        .setMessage(msg)
                        .setPositiveButton("知道了", null)
                        .show()
                },
               onToggleMock = {
                    if (isMocking) {
                        MockLocationService.stop(context)
                        isMocking = false
                        statusMessage = "已停止模拟"
                    } else {
                        try {
                            // BD-09 坐标转 WGS-84 后注入系统
                            val (wgsLat, wgsLng) = bd09ToWgs84(
                                selectedLatLng.latitude,
                                selectedLatLng.longitude
                            )
                            MockLocationService.start(context, wgsLat, wgsLng)
                            isMocking = true
                            val wgsInfo = String.format("WGS-84: %.4f, %.4f", wgsLat, wgsLng)
                            statusMessage = "正在模拟定位... $wgsInfo"
                            AlertDialog.Builder(context)
                                .setTitle("✅ 模拟定位已启动")
                                .setMessage(
                                    "模拟坐标已通过 GPS + Network + Fused 三个 Provider 注入！\n\n" +
                                    "已清除 Mock 标记，第三方 App 应读到模拟位置。\n\n" +
                                    "现在可以打开高德地图 / 智租换电等 App 验证效果。"
                                )
                                .setPositiveButton("知道了", null)
                                .setNegativeButton("关闭", null)
                                .show()
                        } catch (e: SecurityException) {
                            AlertDialog.Builder(context)
                                .setTitle("\uD83D\uDD27 设置模拟位置应用")
                                .setMessage(
                                    "请在开发者选项中找到并设置：\n\n" +
                                    "① 进入 开发者选项\n" +
                                    "② 向下滑动到页面底部\n" +
                                    "③ 找到「选择模拟位置信息应用」\n" +
                                    "④ 选择「SSLAndriodTest」\n" +
                                    "⑤ 返回本应用重新点击开始\n"
                                )
                                .setPositiveButton("去设置") { _, _ ->
                                    try {
                                        val intent = Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS").apply {
                                            putExtra(":android:show_fragment", "com.android.settings.development.DevelopmentSettingsDashboardFragment")
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            val intent = Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS")
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "请手动打开开发者选项", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                .setNegativeButton("取消", null)
                                .show()
                            statusMessage = "请先设置模拟位置应用"
                        }
                    }
                }
            )
        }
    }

    if (showSearchDialog) {
        SearchDialog(
            onDismiss = { showSearchDialog = false },
            onLocationSelected = { latLng, name ->
                selectedLatLng = latLng
                selectedAddress = name
                baiduMap?.setMapStatus(
                    MapStatusUpdateFactory.newLatLngZoom(latLng, 16f)
                )
                showSearchDialog = false
                if (isMocking) {
                    val (wgsLat, wgsLng) = bd09ToWgs84(latLng.latitude, latLng.longitude)
                    MockLocationService.update(context, wgsLat, wgsLng)
                    statusMessage = "已更新模拟位置"
                }
            }
        )
    }

    if (showFavoritesDialog) {
        FavoritesDialog(
            favorites = favorites,
            onDismiss = { showFavoritesDialog = false },
            onSelect = { fav ->
                val latLng = LatLng(fav.latitude, fav.longitude)
                selectedLatLng = latLng
                selectedAddress = fav.name
                baiduMap?.setMapStatus(
                    MapStatusUpdateFactory.newLatLngZoom(latLng, 16f)
                )
                showFavoritesDialog = false
                if (isMocking) {
                    val (wgsLat, wgsLng) = bd09ToWgs84(latLng.latitude, latLng.longitude)
                    MockLocationService.update(context, wgsLat, wgsLng)
                    statusMessage = "已切换到：${fav.name}"
                } else {
                    statusMessage = "已选择：${fav.name}"
                }
            },
            onDelete = { index ->
                favoriteManager.remove(index)
                favorites = favoriteManager.getAll()
            }
        )
    }

    // 强制检查：未设为模拟位置应用时拦截使用
    if (!isMockApp) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = { Text("需要设置模拟定位", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "本应用需要先设置为「模拟位置信息应用」才能使用。\n\n" +
                    "请按以下步骤操作：\n\n" +
                    "① 进入 开发者选项\n" +
                    "② 向下滑动到页面底部\n" +
                    "③ 找到「选择模拟位置信息应用」\n" +
                    "④ 选择「SSLAndriodTest」\n" +
                    "⑤ 返回本应用\n\n" +
                    "设置完成后点击「我已设置」按钮。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "请手动打开开发者选项", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isMockApp = isMockLocationApp(context)
                }) {
                    Text("我已设置")
                }
            }
        )
    }
}

@Composable
fun TopSearchBar(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.shadow(6.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "搜索地点...",
                color = Color.Gray,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("搜索", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun BottomControlPanel(
    modifier: Modifier = Modifier,
    selectedLatLng: LatLng,
    selectedAddress: String,
    isMocking: Boolean,
    statusMessage: String,
    onSaveFavorite: () -> Unit,
    onOpenFavorites: () -> Unit,
    onUseMyLocation: () -> Unit,
    onDiagnose: () -> Unit,
    onToggleMock: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 地点名
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = if (isMocking) Color(0xFF4CAF50) else Color(0xFF2196F3),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selectedAddress,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF212121),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }

            // 坐标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CoordinateItem("纬度", String.format("%.6f", selectedLatLng.latitude))
                CoordinateItem("经度", String.format("%.6f", selectedLatLng.longitude))
            }

            // 使用当前位置 + 收藏 + 收藏夹 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onUseMyLocation,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("当前位置", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onSaveFavorite,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFA726))
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("收藏此处", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onOpenFavorites,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2196F3))
                ) {
                    Icon(Icons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("收藏夹", fontSize = 12.sp)
                }
            }

            Text(
                text = statusMessage,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isMocking) Color(0xFF4CAF50) else Color(0xFF616161)
            )

            // 诊断 + 开始/停止 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 诊断按钮
                OutlinedButton(
                    onClick = onDiagnose,
                    modifier = Modifier.width(72.dp).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF9C27B0))
                ) {
                    Text("诊断", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                // 开始/停止按钮
                Button(
                    onClick = onToggleMock,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMocking) Color(0xFFE53935) else Color(0xFF2196F3)
                    )
                ) {
                    Icon(
                        if (isMocking) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isMocking) "停止模拟定位" else "开始模拟定位",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun CoordinateItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF757575))
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
    }
}

@Composable
fun FavoritesDialog(
    favorites: List<FavoriteLocation>,
    onDismiss: () -> Unit,
    onSelect: (FavoriteLocation) -> Unit,
    onDelete: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bookmarks, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("收藏夹")
            }
        },
        text = {
            if (favorites.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无收藏地点", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(favorites.size) { index ->
                        val fav = favorites[index]
                        Surface(
                            onClick = { onSelect(fav) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF5F5F5)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = fav.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(
                                        text = String.format("%.4f, %.4f", fav.latitude, fav.longitude),
                                        fontSize = 12.sp, color = Color.Gray
                                    )
                                }
                                IconButton(onClick = { onDelete(index) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    )
}

@Composable
fun SearchDialog(
    onDismiss: () -> Unit,
    onLocationSelected: (LatLng, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PoiInfo>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("搜索地点") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("输入地点名称") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (query.isNotBlank()) {
                                    isSearching = true; errorMessage = ""
                                    performPoiSearch(query) { results, error ->
                                        searchResults = results; errorMessage = error; isSearching = false
                                    }
                                }
                            },
                            enabled = !isSearching
                        ) { Icon(Icons.Default.Search, contentDescription = "搜索") }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                } else if (errorMessage.isNotEmpty()) {
                    Text(text = errorMessage, color = Color(0xFFE53935), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                } else if (searchResults.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(searchResults) { item ->
                            Surface(
                                onClick = { item.location?.let { latLng -> onLocationSelected(latLng, item.name ?: "未知位置") } },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(text = item.name ?: "未知位置", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(text = item.address ?: "", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    )
}

fun performPoiSearch(query: String, onResult: (List<PoiInfo>, String) -> Unit) {
    val searcher = PoiSearch.newInstance()
    searcher.setOnGetPoiSearchResultListener(object : OnGetPoiSearchResultListener {
        override fun onGetPoiResult(result: PoiResult?) {
            if (result?.error == SearchResult.ERRORNO.NO_ERROR) {
                onResult(result.allPoi ?: emptyList(), "")
            } else {
                onResult(emptyList(), "未找到相关地点，请更换关键词")
            }
            searcher.destroy()
        }
        override fun onGetPoiDetailResult(result: PoiDetailResult?) {}
        override fun onGetPoiDetailResult(result: PoiDetailSearchResult?) {}
        override fun onGetPoiIndoorResult(result: PoiIndoorResult?) {}
    })
    val option = PoiCitySearchOption().city("北京").keyword(query).pageNum(0).pageCapacity(20)
    if (!searcher.searchInCity(option)) {
        onResult(emptyList(), "搜索请求失败")
        searcher.destroy()
    }
}
