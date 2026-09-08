# Compose 迁移记录

2026-09-08，迁移前基准提交 `387bf0a`。主天气、城市搜索、城市管理、天气预警、首次说明、应用提示对话框和小组件配置页都由 Compose 承载。多 Activity 结构保留。

## 实现边界

- 使用 Compose Foundation、Animation 和 Canvas，自定义 Smartisan 组件；无 Material 页面、AndroidView 或 XML inflate。
- 原 PNG、selector、NinePatch 继续编译为 Android Drawable，通过受 Compose 生命周期管理的 Painter 绘制。背景绘制不参与组件测量，保留原拉伸区域、透明阴影及日夜资源。
- 主天气直接读取 `WeatherUiState` 和领域天气模型。删除仅服务于旧 View 的可变天气 Bean、映射、RecyclerView adapter、手动页面同步与旧 View 控件；根据 Lint 和当前 Kotlin 引用清理 312 个未使用资源符号，包括旧通知兼容资源、旧协议文字及 XML 专用尺寸。
- `res/layout/weather_widget_compact.xml` 是 Android 桌面小组件选择器使用的 RemoteViews 预览，运行时小组件已经由 Glance 生成；这是系统接口资源。
- `SmartisanLocation` 仅用于 Activity 搜索上下文，不再参与天气页面渲染。
- Compose BOM 使用官方稳定版 `2026.08.00`；Compose compiler 跟随 Kotlin `2.4.0`。保留工作区已有的 AGP 版本调整，当前版本为 `9.4.0-alpha08`。

## 原版行为

| 项目 | 迁移目标 |
| --- | --- |
| 切换城市 | 标题、数字和内容分区移动；按钮与卡片背景固定；60dp 换城阈值、120dp 淡出、边界阻尼、五次 ease-out 收拢 |
| 内容与背景 | 内容 200ms / 50ms；背景延迟 100ms 后过渡 400ms |
| 主温度 | 原 PNG 数字图集、1400ms 滚动；C/F 使用原模糊图条；未变化温度 70ms 延迟后 125/250/250ms 抖动 |
| 小温度 C/F | 300ms 纵向切换；快速反向以最新单位为目标 |
| 城市拖拽 | 按柄 down 启动、原行隐藏、上下阴影、200ms 换位、150ms 落位、边缘滚动；取消恢复；完成才事务提交 |
| 搜索 | 无分页；0.5 拖动倍率、2.5 最大拖动率、250ms 黏性回弹；IME 与普通内容分别消费 |
| 按压 | selector 背景、文字与图标状态同步；快速点击保留可见按压帧；cancel 立即清除 |
| 生命周期 | `collectAsStateWithLifecycle` 收集状态；进入后台取消主温度、刷新及预警循环，恢复到最新状态 |
| 首次说明 | 同意之前不创建天气 ViewModel、不发起天气网络请求；保留原粗体文本与退出路径 |
| 大屏 | 全窗口背景与四边安全 Insets；手机画布最大 480dp 居中 |

## 验证方法

迁移前在独立 worktree 构建 APK，安装到 Pixel 10 Pro 模拟器，固定应用语言为 `zh-CN`，保存首页、搜索、城市管理、首次说明截图以及城市数据库/天气缓存副本。迁移后使用相同模拟器和数据检查位置、文字、天气资源和交互。视觉相近与构建成功均不等同于 1:1 验收。

```bash
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug assembleRelease
```

设备测试覆盖分页边界/取消、C/F 与刷新、后台恢复、搜索结果/空态/重试、拖拽取消、预警长列表、说明授权边界、快速按压像素、disabled 状态、深色文字与 Drawable 测量约束。38 项 JVM 测试已通过。模拟器第二轮通过前 14 项（小组件配置、Room、预警列表、城市拖拽取消、公共控件及普通切城），随后宿主内存压力导致设备整体停滞，完整设备测试尚未跑完。用户选择自行真机验证，后续不再扩大模拟器测试范围。最终检查通过：38 项 JVM 单元测试、Debug APK、测试 APK、Lint（0 错误）和包含 R8/资源压缩的 Release APK。两份 APK 均通过 ZIP 完整性检查。用户已完成真机初测，反馈基本没有问题，并确认提交；本轮不将该初测等同于完整设备矩阵的 1:1 验收。

真实平板、折叠屏、多窗口、厂商字库及高刷新率下的触觉/帧节奏仍需设备矩阵验证，不能由单一模拟器替代。

## 官方资料

- [Compose compiler 设置](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [自定义设计系统](https://developer.android.com/develop/ui/compose/designsystems/custom)
- [手势与事件传播](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures)
- [拖拽、滑动与惯性](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling)
- [动画](https://developer.android.com/develop/ui/compose/animation/value-based)
- [交互状态](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions)
- [Insets](https://developer.android.com/develop/ui/compose/system/insets-ui)
- [Dialog](https://developer.android.com/develop/ui/compose/components/dialog)
- [文本段落](https://developer.android.com/develop/ui/compose/text/style-paragraph)
- [可访问性语义](https://developer.android.com/develop/ui/compose/accessibility/semantics)
- [Compose v2 测试 API](https://developer.android.com/develop/ui/compose/testing/migrate-v2)
- [AndroidX Test / Espresso 3.7.0](https://developer.android.com/jetpack/androidx/releases/test)
- [WindowManager 系统扩展的运行时加载](https://source.android.com/docs/core/display/windowmanager-extensions)
