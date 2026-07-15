<p align="center">
  <img src="app/src/main/res/mipmap-xxhdpi/weather_icon.png" width="112" alt="锤子天气图标" />
</p>

<h1 align="center">锤子天气复刻 (Smartisan Weather Revived)</h1>

<p align="center">
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin" alt="Kotlin 2.4.0" /></a>
  <a href="https://developer.android.com/build"><img src="https://img.shields.io/badge/AGP-9.4.0--alpha04-3DDC84?logo=android" alt="AGP 9.4.0-alpha04" /></a>
  <a href="https://developer.android.com/about/versions/oreo/android-8.1"><img src="https://img.shields.io/badge/minSdk-27-3DDC84?logo=android" alt="minSdk 27" /></a>
</p>

Smartisan OS 早已退出历史舞台，其中的天气应用也留在了旧 Android 里，但它的设计并未随时间过时。

它延续了 Smartisan OS 一贯的设计取向：白、灰、黑构成安静的底色，拟物化的质感没有盖过信息本身，温度、天气和预报始终处在最清楚的位置。随天气变化的背景为界面添上情绪，刷新、城市切换和温标转换时的动画，则让每次操作得到细腻而明确的回应。功能不算复杂，却在信息、质感和趣味之间找到了很好的分寸。

为了延续这套设计，本项目以原版 APK 为基准，使用现代 Android 技术栈重写这款天气应用，让它能够在现代 Android 设备上继续使用。界面延续原版的设计语言，动画与交互则结合现代 Android 重新打磨，天气数据换用更准确的小米天气中国区混合接口。

## 相较原版的改进

- **更新天气数据源**：替换已经不适合继续使用的原版数据链路，接入目前在中国地区更及时、准确的小米天气中国区混合接口，提供实时天气、逐小时/逐日预报、空气质量和天气预警。
- **统一数据口径**：目前只使用小米天气中国区混合源，不叠加其他备用天气源；网络失败时读取同一来源的本地缓存，避免不同模型之间的数据跳变。
- **重新实现城市与定位**：城市搜索、坐标反查和天气城市匹配使用新的数据层；定位基于 Android 系统能力，不依赖第三方定位 SDK。
- **现代数据架构**：使用 Room、DataStore、Coroutines、ViewModel 和 StateFlow 管理城市、设置、缓存与页面状态。
- **适配现代 Android**：支持 edge-to-edge、手势导航、刘海与系统栏 Insets，并对平板、折叠屏和多窗口中的内容宽度进行约束。
- **清理历史包袱**：源码全部使用 Kotlin，不依赖原版系统框架，不保留旧包路径、旧数据库或旧设置迁移代码。

## 当前功能

- 实时天气、逐小时预报、逐日预报、空气质量和天气预警
- 多城市切换、城市搜索、城市管理与拖拽排序
- 系统定位、定位城市匹配和本地天气缓存
- 摄氏度 / 华氏度切换及原版风格动画
- 城市分页、边界阻尼、刷新反馈和天气背景过渡
- 首次启动隐私提示、权限申请及离线协议页面

## 天气数据

应用目前仅使用小米天气中国区混合接口。天气数据由小米接口返回，实际内容、准确性和可用性以小米提供的服务为准。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 构建 | Android Gradle Plugin `9.4.0-alpha04`、Gradle `9.6.1`、JDK 25（Java 17 字节码） |
| 语言 | Kotlin `2.4.0` |
| UI | XML Layout、Android View、自定义 View、多 Activity |
| 状态 | ViewModel、StateFlow、Coroutines `1.11.0` |
| 存储 | Room `3.0.0`、bundled SQLite `2.7.0`、DataStore `1.2.1` |
| 网络 | `HttpURLConnection`、`org.json` |
| SDK | `minSdk 27` / `targetSdk 37` / `compileSdk 37` |

## 构建

准备 JDK 25 和 Android SDK，然后执行：

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/`。

## 致谢

感谢 [People-11](https://github.com/People-11/) 的 [SmartisanOS_APP_Port](https://github.com/People-11/SmartisanOS_APP_Port/) 移植工作。本项目使用该项目提供的 `Weather_8.1.3.apk` 进行逆向分析，用于提取原版资源，并确认 UI 层级、视觉细节与交互行为。

## 免责声明

本项目与字节跳动、小米无关，仅为个人兴趣驱动的非官方重写。

- Smartisan OS、相关商标、视觉设计及原版素材的知识产权归原权利人所有。
- 天气数据仅供参考，准确性和可用性以小米天气接口为准。
