# 极云渡 · 变更日志 / 交付说明（开发方维护）

> 辅助文档：记录各阶段交付摘要、变更与缺陷修复日志，便于追溯。
> 原文件名「交付说明.md」，按阶段 5 评审报告 P2 建议改名为 CHANGELOG.md。
> 不参与 APK 打包。

---

## 阶段 1：项目骨架
- 交付：`settings.gradle.kts`、根 `build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、
  `app/build.gradle.kts`、`AndroidManifest.xml`、`JieYunDuApp.kt`、`strings.xml`、`themes.xml`、
  图标资源、gradle wrapper（jar + properties + gradlew/gradlew.bat）。
- 依赖裁决：1–4 放行；第 5 项由 junit 改为 `kotlin-test`（Apache-2.0）。

## 阶段 2：核心接口与模型
- 交付：`NetdiskType` / `ShareLink` / `ParseResult` / `FileInfo` / `DownloadEntry` /
  `NetdiskParser` / `ParserRegistry` / `LinkExtractor` / `FileSizeFormatter`。
- 裁决登记：`NetdiskType.kt` 的 4 个中文 `displayName` 为 C5 唯一豁免，已写入 KDoc。

## 阶段 3：夸克解析器（参数待抓包）
- 交付：`QuarkApi.kt`、`QuarkParser.kt`、`QuarkParserTest.kt`。网盘参数以
  `// TODO(用户抓包): …` 占位。

## 阶段 4：下载引擎 + 前台服务 + DI（Network/App）
- 交付：`Chunk` / `DownloadTask` / `ChunkManager` / `DownloadState` / `DownloadEngine` /
  `DownloadService` / `NotificationHelper` / `NetworkModule` / `AppModule` + `ChunkManagerTest`。
- 同步修改：`AndroidManifest.xml` 注册 `DownloadService`；`strings.xml` 补通知文案。

### BUGFIX LOG（stage 4）
- BUG-4-01（续传被截断）：`executeChunkRequest` 原用 `FileOutputStream(partFile, false)`
  覆盖写，续传会把已落盘分片截断为零。修复：改为追加模式 `FileOutputStream(partFile, true)`。
- BUG-4-02（重试重复追加）：`Range` 头原在重试循环外计算，重试仍从旧偏移请求，导致重复追加。
  修复：每轮重试按 `partFile.length()` 重算断点并重建请求。

## 阶段 5：数据层（含勘误闭合）
- 依据【修订 JYD-ERRATA-2026-10-03】闭合三处：
  1. 修订二：`DownloadProgressState` 补 `taskId`（并调整 `initial(taskId, chunkCount)`）。
  2. 修订一：`DownloadProgressPort` 改为 `upsert / query / delete`，删除 `NoOpDownloadProgressPort`
     及其 TODO；`AppModule` 临时端口绑定已回收。
  3. 修订三：新增 `data/remote/UserAgentProvider.kt`；`NetworkModule` 改为注入该 Provider。
- 交付：`AppDatabase` / `DownloadDao`+`DownloadEntity` / `HistoryDao`+`HistoryEntity` /
  `FavoriteDao`+`FavoriteEntity` / `DatabaseModule` / `UserAgentProvider` / 三个 Repository。
- 关键实现：`DownloadDao` 为 `abstract class` 且 `implements DownloadProgressPort`。

### 待披露技术债
- `kotlin-test` 在 Android 单元测试会解析到 `kotlin-test-junit` 变体，JUnit4（EPL-1.0）可能
  作为传递依赖进入 test classpath（不进 APK）。需在跑通 `testDebugUnitTest` 后写入扫描报告。

## 阶段 6：液态玻璃基础组件 + 最小 MainActivity + CI
- CI（加急指令）：新增 `.github/workflows/build.yml`、`.gitignore`、`README.md`（骨架）、
  `.github/PULL_REQUEST_TEMPLATE.md`。
- 主题：`Color.kt` / `Type.kt` / `Shape.kt` / `Dimens.kt` / `Theme.kt`。
- 玻璃：`GlassBackground` / `GlassPanel` / `GlassButton` / `GlassCard` / `GlassDivider`。
- 最小 `MainActivity`（深色背板 + 一块玻璃 + 占位文字）；`AndroidManifest.xml` 入口 Activity
  已为 LAUNCHER；`themes.xml` `windowBackground` 改为 `#FF0A0A0F`。
- 工程改动：`settings.gradle.kts` 增加 JitPack 仓库（Cloudy 发布于 JitPack，缺失会导致
  依赖解析失败）。

### Cloudy 实测结论（本阶段）
- 结论：**未实测 → 固化 §9.8 降级路径**。
- 原因：本轮无本地编译环境（Owner 已改云端编译），且 JitPack 在本机不可达，无法对
  `Modifier.cloudy()/liquidGlass()` 的真实 API 与可见性做验证。
- 处置：玻璃观感改由 Compose 原生绘制实现（渐变背板 + 半透明白底 + 高光描边 + 内阴影 +
  噪声），不引用 Cloudy API，以保障 CI 首跑绿色；Cloudy 依赖仍按 §5 保留在声明中，
  其真实接入待 CI 可用后单独立项验证。
- 状态：**等待 GitHub Actions 验证**（P0 构建日志由 Owner 回填评审清单 §8）。

#### 后续（CI run#3 后，Owner 裁决 2026-10-03 · 方案 A）
- CI 实跑证明 cloudy 确实阻断构建（详见下节“CI 实跑结果”run#3）。
- Owner 裁决：**方案 A —— 删除 cloudy 依赖，改走 §9.8 降级路径**。
- 已执行：`libs.versions.toml` 删除 `cloudy` 版本与库声明；`app/build.gradle.kts` 删除
  `implementation(libs.cloudy)`；`settings.gradle.kts` 移除仅因 cloudy 而加的 JitPack 仓库。
- 代码自始未引用任何 Cloudy API，故删除依赖不需要改动任何 Kotlin 源码。
- 状态：**已推送（run#4）。删除后依赖解析即通过；后续 run#4–#6 暴露并修复了 2 个
   Kotlin 编译问题（`GlassPanel.drawOutline` 的 import），最终 run#6 全绿。详见下节。**

## 阶段 6 交付后整改（依据《阶段 6 交付后整改指令（整合版）》）

### 硬伤 1 修复：`__puus` Cookie 通道（方案 B）
- 决策：**方案 B（OkHttp Interceptor 全局注入）**。理由：QuarkApi 三方法签名在《要求.md》
  7.6 已定死，方案 A（`@Header` 参数）/ 方案 C（`@HeaderMap`）都会改动该签名 → 违规；
  方案 B 不动签名，且 UC 与夸克同源可复用，职责清晰（Parser 只负责拿 Cookie）。
- 新增：`data/remote/CookieStore.kt`（内存态 Cookie 仓库，按域名后缀存取，线程安全）。
- 修改：`di/NetworkModule.kt` 新增私有 `CookieInterceptor` 并注册进 OkHttpClient；
  `provideOkHttpClient` 增加注入 `CookieStore`。
- 修改：`QuarkParser` 注入 `CookieStore`，取得 `__puus` 后登记到后缀 `quark.cn`
  （同时覆盖 pan.quark.cn 握手与 drive-pc.quark.cn 接口）。
- 结果：Cookie 已有真实通道送达后续接口请求，不再是「拿到却发不出去」。

### 硬伤 2 修复：QuarkParserTest 真实断言
- 测试替身 `FakeQuarkApi` + 短路拦截器构造假首页响应，不触网、不新增 MockWebServer 依赖。
- 新增断言：A 无 Cookie → `Error(QUARK_NEED_COOKIE)`；B 有 Cookie → `Success` 且文件字段正确；
  C `__puus` 落入 CookieStore（`findForHost("drive-pc.quark.cn")` 命中）；
  D 服务端非成功码 → `Error` 且 code 映射为风控码。
- CI 追加 `./gradlew testDebugUnitTest` 步骤（置于 APK 上传之后），使测试编译与执行可被云端验证。

### 遗留 2 闭合：R7 四行头自检（阶段 6 新增 11 个 Kotlin 文件）
| 文件 | 文件行 | 职责行 | 依赖行 | 协议行 |
|---|---|---|---|---|
| `ui/theme/Color.kt` | ✓ | ✓ | ✓ | ✓ |
| `ui/theme/Type.kt` | ✓ | ✓ | ✓ | ✓ |
| `ui/theme/Shape.kt` | ✓ | ✓ | ✓ | ✓ |
| `ui/theme/Dimens.kt` | ✓ | ✓ | ✓ | ✓ |
| `ui/theme/Theme.kt` | ✓ | ✓ | ✓ | ✓ |
| `ui/glass/GlassBackground.kt` | ✓ | ✓ | ✓ | ✓ |
| `ui/glass/GlassPanel.kt` | ✓ | ✓ | ✓ | ✓ |
| `ui/glass/GlassButton.kt` | ✓ | ✓ | ✓ | ✓ |
| `ui/glass/GlassCard.kt` | ✓ | ✓ | ✓ | ✓ |
| `ui/glass/GlassDivider.kt` | ✓ | ✓ | ✓ | ✓ |
| `ui/MainActivity.kt` | ✓ | ✓ | ✓ | ✓ |

结论：11/11 文件 R7 四行头齐全（依据工具扫描结果逐一核对）。

### 遗留 1：Cloudy 依赖可用性
- 现状：JitPack 在本机不可达；经 CI 实跑确认 **cloudy 确实阻断构建**（见下）。
- 处置：先推送看 CI；若绿则保留；若红且错误指向 cloudy，**贴日志交 Owner 裁决，不擅自注释**。
- 结论：**已确认红，且根因指向 cloudy → 已按指令停止并上报；Owner 已裁决「方案 A：删除 cloudy」（见上一节“后续”）。**

## CI 实跑结果（最终回填，2026-10-03）
仓库 `qiuchen520/JieYunDu`，`.github/workflows/build.yml` 触发（每次 push main 触发一轮）。

| run | head sha | 结果 | 说明 |
|---|---|---|---|
| #1 | `3c9358b` | ❌ Set up Gradle | `Error: Gradle version 8.2.2 does not exist`（**真 bug**：Gradle 无 8.2.2 发行版，8.2.2 是 AGP 版本号） |
| #2 | `c407d14` | ❌ Build Debug APK | `Invalid catalog definition: alias 'androidx-compose-material3-window-size-class' ... contains a reserved name`（**真 bug**：目录别名含 Gradle 保留字 `class`） |
| #3 | `e36d0d3` | ❌ Build Debug APK | `:app:checkDebugAarMetadata` 31 条 AAR 元数据错误，要求 compileSdk≥35 / AGP≥8.6（**根因：cloudy**） |
| #4 | `b47f9d1` | ❌ Build Debug APK | 删除 cloudy 后依赖解析即通过，转出 Kotlin 编译错误 `GlassPanel.kt Unresolved reference: drawOutline`（**真 bug**：缺 import） |
| #5 | `3e946ba` | ❌ Build Debug APK | 补 import 但包名写错（`androidx.compose.ui.graphics.drawscope.drawOutline`），仍 unresolved（**真 bug**：正确包名为 `androidx.compose.ui.graphics`） |
| #6 | `9622dff` | ✅ **全绿** | `BUILD SUCCESSFUL`；`testDebugUnitTest` 亦 `BUILD SUCCESSFUL` |

### run#6 成功详情
- 步骤：Set up / Checkout / JDK17 / Gradle → **Build Debug APK ✔** → **Upload APK ✔** → **Run unit tests ✔**。
- artifact：**`jieyundu-debug-apk`，10,177,231 字节（≈9.7 MB）**，未过期，可从 Actions 页面下载。
- 单元测试：`QuarkParserTest` 编译并执行成功（仅 3 条无害警告 `No cast needed`），
  **硬伤 2 的 4 条真实断言已在云端真实跑通**。

### 六个 run 的根因与修复（全部为 CI 才暴露的真问题）
1. **Gradle 版本号张冠李戴**：把 AGP 的 `8.2.2` 当成 Gradle 发行版号。修复：统一改 `8.2.1`
   （`gradle-wrapper.properties` + workflow + README）。
2. **版本目录别名保留字**：别名含 `class` → 非法。修复：别名改 `androidx-compose-material3-window-size`，
   `app/build.gradle.kts` 引用同步。
3. **cloudy 阻断构建**：POM 依赖 `kotlin-stdlib:2.4.0` + `org.jetbrains.compose.*:1.11.1`，
   把 androidx 链拉高（core-ktx 1.19.0 / lifecycle 2.11.0 / compose 1.11.4 / transition 1.6.0）。
   修复：按 Owner 裁决（方案 A）删除 cloudy + 移除 JitPack 仓库。
4. **`drawOutline` 未解析**：`GlassPanel.kt` 漏 import。修复：补 import（并纠正包名）。

### 待披露技术债（阶段 10 扫描报告用）
- `kotlin-test` 在 Android 单元测试会解析到 `kotlin-test-junit` 变体，JUnit4（EPL-1.0）可能作为
  传递依赖进入 **test classpath**（不进 APK）；run#6 已跑通 `testDebugUnitTest`，可据此撰写扫描报告。
- 已移除的 `cloudy` 若后续需重新引入，须先解决其对新版 androidx / Kotlin 2.4 的依赖，
  与本项目 compileSdk34 / AGP8.2.2 / Kotlin1.9.22 档位兼容，详见上方 run#3。

## 阶段 7：Q 弹导航栏（双方向玻璃条）
- 交付（sha `c9bc597`，5 新 + 4 改）：
  - 新增 `ui/adaptive/WindowSizeHelper.kt`（`LocalConfiguration.screenWidthDp ≥ 600` 判平板；
    因第五部分技术栈未列 `material3-adaptive`，按 D8 弃用 `currentWindowAdaptiveInfo()`）。
  - 新增 `ui/navigation/TopGlassNavBar.kt`（双方向核心：spring 驱动指示器 offset/scale，
    拖拽跟手 + 松手吸附最近页签，仅 spring 无 tween）。
  - 新增 `ui/navigation/NavigationRail.kt`（平板 wrapper，竖直）、`NavigationBar.kt`（手机 wrapper，横向）、
    `JieYunDuNavHost.kt`（手写状态导航，未引入 `androidx.navigation`——D8；内容 `Crossfade`
    与指示器弹簧分离——D6）。
  - 修改 `Color.kt`（+NavIndicatorFill / NavIndicatorHighlight）、`Dimens.kt`（+5 导航常量）、
    `strings.xml`（+3 页签；占位文案改中性版「阶段 6 占位 · 界面开发中」）、`MainActivity.kt`（接线 NavHost）。
- 追加架构要求（sha `4000e4d`）：弹簧参数提取为 `data class NavSpringPreset` + `val JellyPreset`，
  删除 4 个硬编码 spring；`preset` 一路透传（NavHost → Rail/Bar → TopGlassNavBar），
  为阶段 11「预设切换」预留扩展点。行为与写死完全一致；未新增文件 / 设置 UI / 持久化。
- 规格取舍说明（《要求.md》两处自相矛盾，已在交付说明中声明取舍）：
  1. 9.4「平板用左 Rail」vs 10.7「平板用横向条」→ 取 9.4，即平板竖直 + 手机横向（Owner 裁决）。
  2. 9.6.1 标题栏 72dp vs 10.2 导航条 72dp。
- 状态：**待 Owner 装机实测双方向手感**（平板竖直 / 手机横向）。

## 阶段 7 修订：指示器圆角 + Q 弹参数（依据 Owner 装机反馈修订）
### 修订一（10.2 圆角改方）
- 外层玻璃条圆角：`高度 / 2`（胶囊）→ `短边 × 0.33`。
- 指示器玻璃圆角：`指示器高度 / 2`（胶囊）→ `短边 × 0.33`。
- 实现：统一 `RoundedCornerShape(percent = 33)`（percent 语义 = 短边 × 33%，外层 / 内层比例一致）。
- ⚠️**连续圆角（squircle）未实现**：Compose 原生 `RoundedCornerShape` 只能画普通圆角；
  `SmoothRoundedCornerShape` 属第三方库（未列入第五部分技术栈，D8 禁用）；自绘 Path squircle 成本高。
  按 Owner「做不到就先用普通圆角并标注」的指示，**回退普通圆角**，标注「**连续圆角待优化**」。
- ⚠️**平板数值差异（请 Owner 核对）**：新规格表按「平板横向条、高 72dp」测算圆角 24dp；
  但本项目平板为**竖直条、宽 96dp**（阶段 7 双方向裁决，`NavBarThicknessVertical = 96dp`）。
  按「短边 × 33%」得平板外层 **31.68dp**、指示器 **29.04dp**，与表列 24 / 21dp 不同。
  已按「外层 / 内层圆角比一致」原则实现，未硬编码表内数值。
### 修订二（10.3 更 Q 弹）
`JellyPreset`（即 `NavSpringPreset` 数据类默认值）新旧对照：

| 用途 | 旧 dampingRatio / stiffness | 新 dampingRatio / stiffness |
|---|---|---|
| 松手吸附 | 0.55 / 380 | **0.48 / 420** |
| 点击切换 | 0.62 / 420 | **0.52 / 480** |
| 宽度变化 | 0.70 / 500 | **0.60 / 550** |
| 按下缩放 | 0.40 / 800 | **0.38 / 850** |

- 架构不动：仅改 `JellyPreset` 默认值与圆角构造；`TopGlassNavBar` 内部逻辑、`preset` 透传链均不变。
- 装机验收：①内外层圆角变方；②拖拽松手过冲幅度明显变大；③不出现「晃三下以上」。
  过晃则回调 0.50/400；仍不够弹则降 0.45/450（数值由 Owner 实测定，仅改数值不动逻辑）。
- 《要求.md》10.2 / 10.3 同步修订由 Owner 执行。

## 阶段 8：主界面（首页 / 下载 / 设置）
- 交付清单（《要求.md》第十一部分）：`MainActivity.kt`、`HomeScreen/HomeViewModel/HomeUiState`、
  `LinkInputCard`、`ParseResultCard`、`DownloadScreen/DownloadViewModel`、`DownloadItem`、
  `SettingsScreen/SettingsViewModel`，共 11 个 Kotlin 文件；另因规范要求（C5/C6/C7）同时扩展了
  4 个既有资源/主题文件，并改写 `JieYunDuNavHost.kt` 以承载真实页面。
- 新增文件：
  - `ui/screens/home/HomeUiState.kt`：UI 状态 + **网盘类型 / 解析错误码 → strings.xml 的唯一映射点**
    （`uiLabelRes()` / `parseErrorLabelRes()`）。据此落实阶段 2 约定：UI 不再使用 `NetdiskType.displayName`。
  - `ui/screens/home/HomeViewModel.kt`：`LinkExtractor` 提链 → `ParserRegistry` 路由 → `Parser.parse`；
    解析结果写回状态；文件投递 `DownloadEngine`。
  - `ui/screens/home/HomeScreen.kt`：平板两栏（左 40% 输入 + 结果 / 右 60% 复用 `DownloadScreen`），
    手机单栏纵向滚动（9.4 / 9.5）。
  - `ui/screens/home/components/LinkInputCard.kt`：`BasicTextField` 自绘占位（不用 Material `TextField`，R5/D3），
    全宽解析按钮走 `GlassButton`（白 15% 底 + 白 30% 边，9.6.2）。
  - `ui/screens/home/components/ParseResultCard.kt`：解析中 / 成功列表 / 需提取码 / 失败四态；
    圆形下载按钮 56dp 白 20%，图标为 **Canvas 自绘**（不引入 material-icons-extended，遵守 D8）。
  - `ui/screens/download/DownloadViewModel.kt`：含 `DownloadSessionRegistry`（会话内 taskId→文件名）
    与 `DownloadListItem`；列表 = `DownloadRepository.observeProgress()` × 会话文件名；操作转发引擎。
  - `ui/screens/download/DownloadScreen.kt`：`LazyColumn` + 空态；被首页平板右栏复用。
  - `ui/screens/download/components/DownloadItem.kt`：88/76dp 卡片，**自绘进度条**（6dp / 圆角 3dp，
    不用 Material `LinearProgressIndicator`，R5/D3），错开 50ms 淡入 + 上移 12dp（9.7，用 spring 非 tween）。
  - `ui/screens/settings/SettingsViewModel.kt` / `SettingsScreen.kt`：支持网盘、默认并发（8 / 上限 32）、版本与协议。
- 既有文件修改（均为落实 UI 规格所必需）：
  - `JieYunDuNavHost.kt`（改写）：内容区由页签占位改为真实 `HomeScreen/DownloadScreen/SettingsScreen`；
    新增私有 `AppTitleBar`（9.6.1：玻璃面板 72/64dp + 左侧应用名 + 右侧 48dp 圆形设置按钮，
    设置图标同样 Canvas 自绘）并接到 `SELECTED→SETTINGS`。**弹簧参数与 `preset` 透传链未动。**
  - `strings.xml`：新增阶段 8 全部文案（输入提示、按钮、四态、错误码映射、网盘展示名、下载状态、
    设置项），并删除阶段 6 的两条占位串。
  - `Color.kt`：新增 `ButtonFill / ButtonBorder / IconButtonFill / TextMuted / TextFaint`
    （对应 9.6.2 白 15%/30%、9.6.3 白 20%、9.6.4 白 70%/50%）。
  - `Dimens.kt`：新增 `DownloadButtonSize(56) / DownloadItemHeight(88) / DownloadItemHeightCompact(76) /
    ProgressBarHeight(6) / ProgressBarCorner(3)`。
  - `Type.kt`：新增 `labelMedium(13sp)`（9.6.4 速度文字）。
  - `GlassButton.kt`：新增可选参数 `fillColor / borderColor`（默认值不变，向后兼容），供 9.6.2 按钮配色。
- 规格偏差与取舍（据实登记，未静默）：
  1. `ProgressBarCorner = 3dp` 低于 9.2 的「圆角不小于 16dp」，但为 9.6.4 组件明文规格，取组件规格。
  2. 9.6.1 的独立「顶部标题栏」与阶段 7 的顶部横向导航条在手机上并存（标题栏在上、导航条在下）；
     平板为「左 Rail + 上标题栏 + 内容」。此为两节规格叠加的最直接实现，若 Owner 认为手机顶部过挤，
     请出规格修订（裁掉其一或合并）。
  3. 9.4 右栏要求「下载列表 + 文件管理」；**文件管理**（4.3 历史 / 搜索 / 收藏 / 分类）未列入阶段 8
     交付清单，本阶段未实现，留待后续阶段。
  4. `DownloadEntry` / `HistoryEntity` 未被本阶段 UI 直接消费：会话内文件名由
     `DownloadSessionRegistry` 提供（Room 进度表不含文件名），应用重启后历史任务名回退为「未命名任务」。
  5. 解析结果里的 `downloadUrl` 目前由 `QuarkParser` 返回 `null`（直链接口待抓包），
     故 `HomeViewModel.download` 在无直链时**记录日志并跳过**，不产生脏任务。
- 推送：sha **`f7e99ae`**（18 个文件单次提交：11 新 + 6 改 + 本 CHANGELOG）；
  CI run `37100802996`（Build APK）**全绿**，artifact `jieyundu-debug-apk` 10,302,138 B。
- 状态：**CI 已绿 → 待 Owner 装机验收**。

## 阶段 8 整改（Owner 装机反馈：UI 重构 + 提取码弹窗）
### BUGFIX JYD-BUG-03-01：夸克无密码链接不再误判
- 现象：`https://pan.quark.cn/s/{shareId}`（无 `?pwd=`）被解析器直接判为「需要提取码」，根本不发请求。
- 根因：`QuarkParser.parse()` 中 `if (pwd.isNullOrBlank()) return NeedPassword(type)` 提前返回。
- 修复：删除该提前返回；无提取码时 `buildTokenBody` **不带** `passcode` 字段直接请求，
  仅当服务器返回「需要提取码」码时才返回 `NeedPassword`；链接带 `?pwd=` 时正常携带。
- 新增占位常量 `NEED_PASSWORD_CODE = 41011` / `WRONG_PASSWORD_CODE = 41012`
  （均带 `// TODO(用户抓包)`，真实取值待抓包）。
- 测试更新：原「无密码 → NeedPassword」断言删除，改为 A/B/C 三条新断言（不带 passcode 请求且成功、
  服务器要求才弹窗、带密码必携带）。

### 整改一：UI 全局重构
- 圆角：一律「高度 × 0.33」，严禁全圆。新增
  `ButtonCorner(18) / ButtonCornerCompact(16) / InputCorner(21) / InputCornerCompact(17)`；
  按钮圆角 20 → 18dp；输入框首次显式给圆角；顶部标题栏圆角 `MinCorner(16)` → `CardCorner(24)`
  （≈ 72 × 0.33）；导航条维持 `percent = 33`（即短边 × 0.33）。
- 平板布局：主内容区顶部内边距 `ContentTopPadding = 32dp`；输入区 `weight 0.4 → 0.6`、
  右栏 `0.6 → 0.4`，不再挤在左上角。
- 空态：`DownloadScreen` 空态改为「居中 Canvas 自绘灰色下载图标（64dp）+ 文案」。
- 玻璃质感：`GlassPanel` 新增 `drawRefraction`——低透明紫 / 蓝斜向渐变铺底（新增
  `GlassRefractionPurple / GlassRefractionBlue`）以透出背板；保留左上 1dp 高光（60% → 0%）、
  底部黑色 8% 内阴影、1dp 白色 15% 边框。
- 背板：`GlassBackground` 光斑透明度 0.55 → 0.85，并对光斑层加 `Modifier.blur(48dp)`
  （API 31+ 生效，低版本自动忽略）。
- 解析按钮：不再横跨全屏；改为「输入框（自适应 1f）+ 右侧自适应宽度按钮」的一行布局。
- 顶部标题栏：改悬浮样式，左右各留 24dp（`TitleBarMargin`）。

### 整改二：提取码交互（产品体验硬伤）
- 不再引导用户改链接；`parse_need_password_hint` 文案改为中性「请输入提取码后重新解析」。
- `HomeUiState` 新增 `passwordPrompt / passwordErrorRes`；`HomeViewModel` 收敛出
  `startParse(link, parser, password)`，把 `NeedPassword` 转为弹窗状态，新增
  `submitPassword(pwd) / dismissPasswordPrompt()`。
- 新增液态玻璃弹窗 `ui/screens/home/components/PasswordDialog.kt`（Compose 原生 `Dialog` +
  `GlassPanel` + `BasicTextField` 自动聚焦 + 取消 / 确定，**不使用 Material AlertDialog**）。
- 密码错误：解析器返回 `QUARK_WRONG_PASSWORD` → 弹窗保留并显示「提取码错误，请重试」。
- 新增文案：`password_dialog_title / password_dialog_hint / password_error_retry /
  action_cancel / action_confirm`。

## 待办 / 已知项
- 【阶段 7 装机反馈】已按上述「阶段 7 修订」处理（本轮推送）；装机实测结论待 Owner 反馈。
  原三点：①「不需要这么远」②「带点方形」③「不是很 Q弹」——其中 ②③ 已由修订一 / 二落地；
  ① 与修订二方向相反（修订二设计要求「过冲更大」），按 Owner 最新规格执行，① 视为已被覆盖。
- 评审清单 §1、§2 同步（评审方执行）。
- 评审清单 §8 P0「构建日志」：**已具备**（run#6 全绿 + artifact `jieyundu-debug-apk` 可下载）。
- 《要求.md》§5 仍将 `skydoves/Cloudy 1.0.0-alpha01` 列为指定依赖，与当前实现（方案 A 移除）
  已不一致，**待评审方/Owner 出勘误登记**（开发方按铁律不改规格文件）。
- 夸克 UA 等 27 条 `// TODO(用户抓包):` 待抓包数据。
- README 已声明本项目为完全独立开发，未参考任何现有网盘解析工具的源码（AI 辅助 · DeepSeek）。
