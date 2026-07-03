# SSLAndriodTest

Android 虚拟定位应用 —— 基于 Jetpack Compose + 百度地图 SDK，通过 FusedLocationProviderClient Mock 模式实现 GPS / Network / Fused 三 Provider 定位注入，支持高德地图等第三方 App 定位欺骗。

> 本项目使用 [Qoder AI](https://qoder.ai) 辅助开发完成。

---

## 技术功能

### 核心定位欺骗

| 功能 | 实现方式 |
|------|----------|
| **三 Provider 注入** | 同时注册 GPS、Network、Fused 三个 TestProvider，覆盖所有定位路径 |
| **Fused Mock 模式** | 通过 `FusedLocationProviderClient.setMockMode(true)` + `setMockLocation()` 直接覆盖融合定位数据，解决高德地图等 App 绕过 GPS Mock 的问题 |
| **Mock 标记清除** | 反射调用 `setIsFromMockProvider(false)`、`setMock(false)`，修改 `mIsFromMockProvider` 私有字段，清除 extras 中的 `mockLocation` 标记，绕过第三方 App 的 Mock 检测 |
| **前台服务定时推送** | `ForegroundService`（`foregroundServiceType="location"`）每 300ms 调用 `tick()` 重新注入坐标，App 切后台不中断；启动时 burst 5 次（80ms 间隔）确保立即生效 |
| **完整坐标转换** | WGS-84 ↔ GCJ-02 ↔ BD-09 四步转换算法，确保百度地图显示与系统 GPS 注入坐标准确对应 |

### 权限与安全

| 功能 | 实现方式 |
|------|----------|
| **强制权限检查** | 启动时通过 `AppOpsManager` 检查模拟位置权限，未设置时弹出不可关闭弹窗（`dismissOnBackPress=false`、`dismissOnClickOutside=false`）引导用户配置 |
| **ON_RESUME 自动刷新** | 通过 `LifecycleEventObserver` 监听生命周期，从开发者选项返回后自动重新检查权限状态 |
| **精准跳转** | 点击「去设置」直接跳转 `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` 开发者选项页面 |

### 地图与交互

| 功能 | 实现方式 |
|------|----------|
| **百度地图选点** | 集成百度地图 SDK 7.6.4，支持拖动选点、地图缩放手势 |
| **POI 城市搜索** | 基于百度 `PoiCitySearchOption`，输入地点名称即可搜索并跳转 |
| **收藏管理** | `SharedPreferences` 持久化存储用户收藏地点，默认预置苏州坐标，支持添加/选择/删除 |
| **真实定位兜底** | 停止模拟后点击定位按钮，`forceFresh=true` 跳过 `lastLocation` 缓存强制请求新 GPS 位置，3 秒超时保护 |
| **浮动控制按钮** | 右下角 `FloatingActionButton` 快速定位到当前位置，左下角缩放控制 |

### 架构设计

```
┌──────────────────────────────────────────────────┐
│              MainActivity (Compose UI)            │
│   ┌──────────┐  ┌──────────┐  ┌───────────┐      │
│   │ 百度地图  │  │ 搜索/收藏 │  │ 底部控制栏 │      │
│   └────┬─────┘  └──────────┘  └─────┬─────┘      │
│        │                           │             │
│        ▼                           ▼             │
│  ┌───────────────────────────────────────────┐   │
│  │        MockLocationService                │   │
│  │   (ForegroundService, 300ms tick)         │   │
│  │         ┌─────────────────────┐           │   │
│  │         │ MockLocationProvider │           │   │
│  │         └───────┬─────────────┘           │   │
│  │     ┌───────────┼───────────┐              │   │
│  │     ▼           ▼           ▼              │   │
│  │  GPS Provider  Network   Fused Provider    │   │
│  │  (TestProvider)(TestProvider)(setMockMode) │   │
│  └───────────────────────────────────────────┘   │
│         │ LocationSanitizer (反射清除Mock标记)    │
└──────────────────────────────────────────────────┘
```

### 关键文件

| 文件 | 职责 |
|------|------|
| `MainActivity.kt` | Compose UI、地图交互、定位逻辑、权限检查拦截弹窗 |
| `MockLocationProvider.kt` | 核心 Mock 逻辑：三 Provider 注册 + Fused Mock 模式 + Burst Push |
| `MockLocationService.kt` | 前台服务：300ms 定时 tick + 通知栏 + 生命周期管理 |
| `LocationSanitizer.kt` | 反射清除 Location 对象上的 Mock 标记 |
| `MockLocationHelper.kt` | 坐标转换工具：WGS-84 ↔ GCJ-02 ↔ BD-09 完整四步算法 |
| `FavoriteManager.kt` | 收藏地点管理：SharedPreferences 读写 |
| `AndroidManifest.xml` | 权限声明 + 前台服务声明 |

---

## 技术栈

| 技术 | 版本 |
|------|------|
| Kotlin | 2.2.10 |
| Jetpack Compose (BOM) | 2026.02 |
| Android Gradle Plugin | 9.2.1 |
| compileSdk / minSdk | 37 / 30 |
| 百度地图 SDK | 7.6.4 |
| Google Play Services Location | 21.3.0 |
| 开发工具 | **Qoder AI** |

---

## 使用步骤

### 1. 安装应用

下载 [Release APK](https://github.com/ShiShanLing/SSLAndriodTest/releases) 并安装到 Android 设备（Android 11+）。

### 2. 开启开发者选项

设置 → 关于手机 → 连续点击「版本号」7 次。

### 3. 设置模拟位置应用

开发者选项 → 选择模拟位置信息应用 → 选择「SSLAndriodTest」。

> 应用启动时会强制提示此步骤，未设置则无法使用。

### 4. 选择目标位置

拖动地图将图钉对准目标位置，或使用搜索/收藏功能快速跳转。

### 5. 开始模拟定位

点击「开始模拟定位」按钮，系统将通过 GPS + Network + Fused 三个 Provider 注入模拟坐标。

### 6. 验证效果

打开高德地图、智租换电等第三方 App，确认位置已变更为模拟位置。

> **提示**：部分 App 会通过 WiFi/基站扫描计算位置，建议模拟时关闭 WiFi 和移动数据，迫使 App 退回到 GPS 定位路径。

---

## 本地构建

```bash
# Debug 构建
./gradlew :app:installDebug

# Release 构建
./gradlew :app:assembleRelease
```

生成的 APK 路径：

```
app/build/outputs/apk/release/app-release.apk
```

---

## 开发工具

本项目全程使用 [**Qoder AI**](https://qoder.ai) 辅助开发，包括：

- 项目初始化与依赖配置
- 百度地图 SDK 集成与调试
- FusedLocationProviderClient Mock 定位方案设计与实现
- LocationSanitizer 反射清除 Mock 标记
- 前台服务定时推送机制
- 坐标转换算法（WGS-84 ↔ GCJ-02 ↔ BD-09）
- UI 交互设计与优化
- 编译错误排查与修复
- GitHub 仓库创建与 GitHub Pages 部署

---

## 注意事项

| 说明 | 详情 |
|------|------|
| 适用场景 | 开发调试、个人测试 |
| 部分 App 无效 | 银行、支付、打车、游戏等可能通过其他方式检测模拟定位 |
| 数据保存 | 自定义收藏保存在手机本地，卸载 App 后会丢失 |
| 清除模拟 | 停止模拟，或在开发者选项中取消本 App 的模拟位置权限 |

---

## License

MIT
