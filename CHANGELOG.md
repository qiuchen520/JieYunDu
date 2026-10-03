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

### 推送与 CI（阶段 8 整改）
- 推送拆分为 3 个提交以保持可追溯：
  1. `e961f0c`：BUGFIX JYD-BUG-03-01（`QuarkParser.kt` + `QuarkParserTest.kt`，2 文件）。
  2. `a3d06e7`：整改第 1 批（玻璃质感与标题栏 + 尺寸 / 颜色令牌，5 文件）。
  3. `bea2b7a`：整改第 2 批（输入区 / 下载空态 / 首页两栏 + 提取码弹窗全链路 + 文案，8 文件）。
- ⚠️**过程留痕**：`bea2b7a` 提交时因操作失误把 `CHANGELOG.md` 内容误置为空文件；
  已在下一提交 `0e9782e`（「修复：恢复 CHANGELOG.md 内容」）立即恢复全文，内容无损。
- CI：run `37102505310`（head `0e9782e`，Build APK）**全绿**；
  artifact `jieyundu-debug-apk` **10,316,116 B（≈9.8 MB）**。
- 状态：**CI 已绿 → 待 Owner 装机验收**（验收点：不再全圆角、玻璃质感清晰、
  无提取码链接点解析自动弹输入框）。

## 阶段 9：其余三家解析器骨架 + 解析器注册
- 交付（《要求.md》第十一部分）：
  - 新增 `domain/parser/baidu/BaiduApi.kt` + `BaiduParser.kt`、
    `domain/parser/uc/UcApi.kt` + `UcParser.kt`、
    `domain/parser/xunlei/XunleiApi.kt` + `XunleiParser.kt`，共 6 个 Kotlin 文件。
  - 三家均为**骨架占位**：接口仅声明与夸克对应的三方法（token / detail / download），
    具体 URL、参数、响应结构以 `// TODO(用户抓包): …` 标注，待逐家抓包后照此填充。
  - `di/AppModule.kt` 为三家各加一行 `@Provides @IntoSet` 注册（连同既有夸克，
    共 4 个 `@IntoSet`），`provideParserRegistry` 逻辑未动。
- 推送：sha **`9f9e952`**；CI run `#20`（`37102866315`）**全绿**，
  artifact `jieyundu-debug-apk` **10,321,935 B（≈9.8 MB）**。
- 状态：**CI 已绿**。

## 夸克接口实测探测报告（2026-10-03，Owner 授权终端 + curl）
> 授权范围：仅探测夸克服务器公开接口（不访问任何现有网盘解析工具的源码），
> 故不违反 R1。测试分享：`https://pan.quark.cn/s/d3f14d92e6a5`（无提取码，标题「极简(直装版)」）。
> 探测脚本落盘于临时目录（`/tmp/qprobe*.sh` / `qcdn.sh` 及其日志），仅用于本次复测。

### 一、token 接口（换取分享凭证）
- 真实 URL：`POST https://drive-pc.quark.cn/1/clouddrive/share/sharepage/token?pr=ucpro&fr=pc`
- 请求体：`{"pwd_id":"<shareId>"}`；带提取码时追加 `"passcode":"<pwd>"`
- 请求头：无特殊（**无需任何 Cookie**）
- 真实响应（节选）：`{"status":200,"code":0,"message":"ok","data":{"stoken":"+dQ6…/M6UQz+Pc=","title":"极简(直装版)","author":{…},"expired_at":4102416000000,…}}`
- 固定参数：`pr=ucpro`、`fr=pc`；动态参数：`pwd_id`、可选 `passcode`
- 签名项：**未遇**（无客户端签名参数）
- 备注：无 `pwd_id` 时返回 `code:0` 但**不含 stoken**。

### 二、detail 接口（列目录 / 文件）
- 真实 URL：`GET https://drive-pc.quark.cn/1/clouddrive/share/sharepage/detail`
- Query：`pr=ucpro&fr=pc&pwd_id&stoken&pdir_fid&force=0&_page=1&_size=50&_sort=file_type:asc,updated_at:desc`
- 请求头：**无需 Cookie**
- 真实响应：`data.list[]`，每项含 `fid / file_name / size / dir / share_fid_token`（完整对象另含
  `pdir_fid / category / status / source / format_type` 等）
- 固定参数：`pr=ucpro`、`fr=pc`、`force=0`、`_page=1`、`_size=50`、`_sort=file_type:asc,updated_at:desc`；
  动态参数：`pwd_id`、`stoken`、`pdir_fid`（`pdir_fid=0` 为分享根目录；进子目录须用该目录 `fid`）
- 实测结构：本次分享根目录仅 1 个文件夹（`dir:true`，`fid:a7d64dae…`）；
  以其 `fid` 作 `pdir_fid` 进入后得 **11 项**（2 文件夹 + 9 文件，含 132B txt 与 793MB apk）。
- 签名项：**未遇**

### 三、download 接口（换取下载直链）
- 真实 URL：`POST https://drive-pc.quark.cn/1/clouddrive/file/download?pr=ucpro&fr=pc`
- 请求体：`{"fids":["<fid>"],"pwd_id":"<shareId>","stoken":"<stoken>"}`
  （**必须带 `pwd_id` + `stoken`**，否则返回 `code:31001 require login [share missing]`）
- 真实响应：`code:0`，`data[]` 为**完整文件对象**，每项同时含 `fid` 与 `download_url`（可按 fid 回填）
- 响应头：下发 `Set-Cookie: __pugs=…; Max-Age=10800; Domain=quark.cn; Path=/`（**CDN 直链必需**）
- 关键约束：**只能传真实文件 fid**；传文件夹 fid 返回 `code:41038`「文件没有被分享」
  （首次探测时根目录只有文件夹，据此发现「须进目录取真实文件才能验证 download 成功路径」）
- 签名项：**未遇**（直链签名由服务端生成，客户端无需计算）

### 四、CDN 直链对照实验（验证 `__pugs` 必要性）
- `download_url` 指向 `dl-guest-zb-u.drive.quark.cn`，含 `auth_key / token / ork / ud / dfi / filename` 等查询参数
- 对照结果：
  - 无 `__pugs` cookie → **`HTTP/1.1 412 Precondition Failed`**（Content-Length 262）
  - 带 `__pugs` cookie → **`HTTP/1.1 206 Partial Content`**（Content-Length 132，
    Content-Range `bytes 0-131/132`）
  - `Range` 头可用
- 结论：CDN 直链**必须携带 `__pugs`**；签名参数由服务端生成，客户端无需计算。

### 五、Cookie 下发链（汇总）
- HOME → `ctoken`
- SHARE → `ctoken` + `web-grey-id(+.sig)`
- TOKEN → `__sdid`（`Domain=.quark.cn`，`Max-Age=2592000`）
- DOWNLOAD → **`__pugs`（CDN 必需）**

### 六、需人工抓包的剩余项
- `NEED_PASSWORD_CODE` / `WRONG_PASSWORD_CODE` 真实取值：测试链接无提取码，未触发；
  试传 `passcode:0000` 仍 `code:0` 返回 stoken，无法反推错误码 → **须人工抓包**。
- 夸克 PC 客户端精确 UA 串：当前用浏览器 UA 已可用，但 App 应伪装夸克 PC 客户端 → **须人工抓包**。

## 夸克接口实测结果落地（代码，2026-10-03）
> 将上节探测结论全部落回代码，共改 7 个文件，分两批推送。

### 第一批（sha `543adf7`）：`QuarkApi.kt` 重写
- `QuarkShareToken`：`(stoken)` → `(stoken, title: String = "")`
- `QuarkFile`：`(fid, file_name, size)` → `(fid, file_name, size, dir: Boolean = false)`
- `QuarkDownloadUrl`：`(download_url)` → `(fid: String = "", download_url: String = "")`
- `QuarkResponse<T>` / `QuarkShareDetail` 不变；三方法签名不变（合规《要求.md》7.6）
- 全部 KDoc 改为实测结论（含「token / detail 无需 Cookie」「`__pugs` 为 CDN 必需」）
- CI run `#21`（`37103477910`）**全绿**

### 第二批（sha `58c5823`）：6 文件
- `QuarkParser.kt`：
  1. 移除 `requestPuusCookie()` 返回 null 即 `Error(CODE_NEED_COOKIE)` 的硬门槛，
     改为 `registerPuusCookie()` best-effort（失败仅 `Timber.w`，不中断），删除 `CODE_NEED_COOKIE`。
  2. token 响应用 `when(code)`：`NEED_PASSWORD_CODE`→NeedPassword、`WRONG_PASSWORD_CODE`→Error、`SUCCESS_CODE`→继续。
  3. `shareTitle = tokenResponse.data.title`（不再占位空串）。
  4. 新增第 4 步：`entries.filterNot{it.dir}.map{it.fid}` → `getDownloadUrl(...)` →
     按 `item.fid to item.download_url` `associate` 成 map，回填各文件直链。
  5. `toFileInfo(...)` 用 `isDirectory = dir`、`downloadUrl = downloadUrl?.takeIf{it.isNotBlank()}`。
  6. 新增常量 `QUARK_PR / QUARK_FR / FORCE_VALUE / FIRST_PAGE / PAGE_SIZE / DETAIL_SORT`、
     `KEY_PR / KEY_FR / KEY_FORCE / KEY_PAGE / KEY_SIZE / KEY_SORT / KEY_FIDS` 与错误码 `CODE_DOWNLOAD_FAILED`。
     `NEED_PASSWORD_CODE=41011` / `WRONG_PASSWORD_CODE=41012` 仍为占位（带 TODO）。
- `NetworkModule.kt`：新增 `ResponseCookieInterceptor`（在 `CookieInterceptor` 之后注册）——
  对 host 为 `quark.cn` 或其子域的响应，取全部 `Set-Cookie`，`substringBefore(';')` 取键值对
  并用 `"; "` join 后 `cookieStore.save("quark.cn", cookie)`。因 `DownloadEngine` 复用全局
  `okHttpClient`，CDN 分片请求（host 命中 `quark.cn` 后缀）会自动注入 `__pugs`。
- `HomeUiState.kt`：`parseErrorLabelRes` 删除 `"QUARK_NEED_COOKIE"` 映射，
  新增 `"QUARK_DOWNLOAD_FAILED" -> R.string.parse_code_download_failed`。
- `strings.xml`：删除 `parse_code_need_cookie`；新增
  `<string name="parse_code_download_failed">获取下载直链失败</string>`。
- `HomeViewModel.kt`：`download(file)` 的 KDoc 更新（夸克解析器已能为真实文件回填 `downloadUrl`；
  无直链条目时仅记日志并跳过）。逻辑本身未动。
- `QuarkParserTest.kt`：删除基于「`__puus` 必需」的两条旧断言；新增 5 条实测校准断言
  （无 Cookie 仍成功 / title 回填 / 直链按 fid 回填 / 文件夹标记且不请求直链 / download 失败返回 Error）；
  `FakeQuarkApi` 增加 `tokenTitle / downloadCode / downloadEntries` 参数与 `lastDownloadBody` 记录。
- CI run `#22`（`37103522188`）**全绿**，artifact **10,326,250 B（≈9.8 MB）**。

- 状态：**CI 已绿**（run#21 / #22）；探测报告见上节。

## Owner 主动规格变更：内嵌网页登录 + 全功能网盘管理（2026-10-03）
> 来源：`pasted_text_4955384569925285437.txt`（Owner 主动规格变更指令）+《抓包事实.md》。
> 性质：规格重大变更，覆盖《要求.md》第三、四、八部分相关条款。
> 处置：已按项目既有机制在《要求.md》末尾“修订记录”区追加
> 【规格变更 JYD-CHANGE-2026-10-03】块（与上次 JYD-ERRATA 同一做法）。

### 变更背景（据《抓包事实.md》）
- 四家网盘“免登录解析”实际跑不通或极不稳定：夸克/UC 缺 `__pugs` → 直链 412/403；
  百度必须有 BDUSS；迅雷必须有 access_token + 设备指纹 + 验证码盾。
- 结论：**“免登录”只能拿列表，真下载必须登录** → 改为内嵌网页登录 + 复用登录态调官方 API。
- 新定位：极云渡 = 多网盘统一客户端（UI 仍为液态玻璃风格）。

### 影响面（已记录，待 Owner 裁定就地改写与否）
- §2 定位、§4.1 解析（游客→登录态）、§3.4/§9 UI（新增“网盘浏览”页）。
- §5 技术栈：新增 `androidx.webkit:webkit`、`androidx.security:security-crypto`（Apache-2.0，D8 例外）。
- §6 文件结构：新增 `domain/login/`、`data/remote/cookie/`、`domain/netdisk/`、
  `domain/transfer/`、`ui/screens/login/`、`ui/screens/netdisk/` 六组模块。
- §8.2 夸克链路：由 6 步更新为 7 步（新增 **save 转存 + task 轮询**；`file/download`
  只认自己网盘 fid）；§8.4 UC 同步 v2。
- §11 阶段：原 7–10 不变，新增**阶段 11–15**。

### 交付阶段调整
【阶段 11】WebView 登录容器 + Cookie 提取（先夸克跑通）
【阶段 12】网盘容量 + 文件列表（夸克浏览 UI）
【阶段 13】临时转存机制（夸克分享链接 → 转存 → 下载 → 清理全链路）
【阶段 14】其余三家复用
【阶段 15】收尾 + 发布

### 红线（不因本变更放宽）
- Cookie 只存本地、加密存储、绝不上传；不得后台静默上传文件列表；提供“清除登录信息”入口；
  README 须写明“数据留在本地”。R4（禁 GPL/AGPL/LGPL）与 D1–D15 不变。

### 收到的抓包事实（新增参考文档）
- `抓包事实.md`（▸ **暂存工作区本地，未推送公开仓库**——含迅雷 CLIENT_SECRET 等客户端凭据常量，
  是否入库待 Owner 定夺）。
- 覆盖：四家域名/接口/字段名/UA/Referer/签名常量，以及两个此前遗漏的关键机制：
  ① 游客令牌 `__pugs`（夸克 dl-guest 缺它 → 412；UC OSS 缺它 → 403）；
  ② Cookie 须随响应持续合并回写 + 90 分钟刷新 `__puus`。
- 该文件可直接填充 `UserAgentProvider.kt` 四处 UA 及 `QuarkApi.kt` 字段名等 `TODO(用户抓包)`。

### 推送与 CI（本次）
- 推送方式：《要求.md》与《CHANGELOG.md》分别单提交，经 GitHub Contents API 直推（base64 直接读取本地文件，
  推送字节数与本地 `wc -c` 一致：要求.md 52221 / CHANGELOG.md 36183，无截断无损坏）。
- 《要求.md》sha `7f23f2c5b299771817576482dde4752858ab4c8c` → CI run `#24` ✅ **全绿**。
- 《CHANGELOG.md》sha `997bfce1288302339591ab34c15720d5b999c723` → CI run `#25`（`37104509356`）
  ✅ **全绿**，artifact `jieyundu-debug-apk` **10,326,247 B（≈9.8 MB）**。
- `抓包事实.md` **未推送**（含迅雷客户端凭据常量，是否入公开仓库待 Owner 定夺）。

### 待 Owner 裁定 / 澄清（已裁定，2026-10-03）
> Owner 裁定如下，已执行：
> 1. **就地改写正文**：《要求.md》§2 / §4.1 / §5 / §6 / §8.2 / §8.4 已直接改写为新规格；
>    末尾“修订记录”块保留，仅作变更历史；**评审以正文为准**。
> 2. **评审清单由评审方更新**：开发方只发《变更通知》，不碰《评审清单.md》；
>    此前“开发方更新 §1”系笔误，以《评审清单.md》§0 为准。
> 3. **`抓包事实.md` 不入公开仓库**（含迅雷 CLIENT_SECRET、captcha_sign 盐、设备指纹常量）：
>    本地留存完整版；另出**脱敏版 `docs/协议参考.md`**（删密钥 / 盐 / 凭据，仅留接口结构与参数名）入库；
>    `.gitignore` 已加入 `抓包事实.md` 防误推。

### 正文改写明细（本次）
- §2 定位：改为“多网盘统一客户端（内嵌网页登录 + 网盘管理 + 分享解析下载）”；核心流程改为“登录 → 浏览/管理 → 转存取链 → 下载”。
- §4.1：由“网盘解析（游客模式）”改为“网盘管理与解析（登录态）”，增列容量/文件操作/转存清理。
- §5：新增 `androidx.webkit:webkit`、`androidx.security:security-crypto`（Apache-2.0，D8 例外）；
  同时按 Owner 早前裁决（方案 A）修正 `skydoves/Cloudy` 条目为“Compose 原生自绘（已移除）”——**该历史遗留勘误至此闭合**。
- §6：新增六组模块（`domain/login`、`domain/netdisk`、`domain/transfer`、`data/remote/cookie`、`ui/screens/login`、`ui/screens/netdisk`）。
- §8.2：夸克链路由 6 步改写为 7 步（新增 save 转存 + task 轮询；`file/download` 只认自己网盘 fid）。
- §8.4：UC 链路更新为 v2（pc-api.uc.cn / share_for_transfer / v2/detail / entry=ft / 游客 UA / Referer）。
- 遗留待补：原指令所写“§3.4”在正文中不存在（第三部分仅 3.1/3.2/3.3）；
  “网盘浏览”页 UI 规格未给出，已在《要求.md》修订记录中标注待 Owner 补 §9。

## 阶段 8 整改复核（阶段 8 遗留项闭合 · 自检 + 残差修正，2026-10-03）
> 依据：Owner「先自检列差异清单 → 圆角 → 平板布局 → 玻璃材质 → 解析按钮 → 提取码弹窗 → 标题栏」指令。
> 方式：逐一读取 12 个关键 UI 源码文件与《要求.md》§9.2 / §9.4 / §9.6 / §10.2 逐条比对。

### 自检结论（现有实现 vs Owner 六项整改指令）
| # | 整改项 | 自检结果 | 结论 |
|---|---|---|---|
| 1 | 圆角全圆 → 高度 × 0.33 | 输入框 `InputCorner 21`(64×0.33)/`Compact 17`(52×0.33)；按钮 `ButtonCorner 18`(56×0.33)/`Compact 16`(48×0.33)；导航条与指示器 `percent = 33`——均已按高度 × 0.33 | ✅ 已达成（**标题栏圆角见本轮修正**） |
| 2 | 平板布局重分配（左 96dp 导航，内容不挤左上角） | `NavigationRail` 宽 `NavBarThicknessVertical = 96dp`；主内容 `ContentTopPadding = 32dp`；`HomeScreen` 平板两栏 `0.6 / 0.4` | ✅ 已达成 |
| 3 | 玻璃材质补齐（高光 + 内阴影 + 细边框 + 透背景渐变） | `GlassPanel` 四项齐全：`drawRefraction`(紫蓝斜向渐变) + `drawEdgeHighlight`(左上 1dp 高光) + `drawInnerShadow`(底部内阴影) + `border`(1dp 白 15%) | ✅ 面板已达成（**导航指示器缺内阴影，见本轮修正**） |
| 4 | 解析按钮不横跨全屏 | `LinkInputCard` 一行：输入框 `weight(1f)` + 右侧自适应宽 `GlassButton` | ✅ 已达成 |
| 5 | 提取码弹窗（不引导用户改链接） | `PasswordDialog`（Compose 原生 `Dialog` + `GlassPanel` + 自动聚焦）；文案「请输入提取码 / 请输入提取码后重新解析」，不含引导改链措辞 | ✅ 已达成 |
| 6 | 顶部标题栏悬浮，左右 24dp | `AppTitleBar` 悬浮，`padding(horizontal = TitleBarMargin = 24dp)` | ✅ 已达成 |

### 本轮残差修正（2 处，均为明文规格缺口）
1. **标题栏圆角按「高度 × 0.33」**（整改项 1 延伸）：原 `AppTitleBar` 固定用 `CardCorner(24dp)`
   （平板 72×0.33≈24 ✓，但手机 64×0.33≈21 ✗ 偏大 3dp）。新增 `TitleBarCorner(24) / TitleBarCornerCompact(21)`，
   `AppTitleBar` 按档位取用。
2. **导航指示器补内阴影**（整改项 3 / §10.2：「指示器内阴影：底部黑色 10%，模糊 4dp」）：
   `TopGlassNavBar` 指示器原本仅有边缘高光（`drawIndicatorHighlight`），缺内阴影。
   新增 `JieYunDuColors.NavIndicatorInnerShadow(0x1A000000)` 与 `drawIndicatorInnerShadow(...)`：
   横向取底边缘、竖向取右边缘，与高光边相对，贴合「上高光、下内阴影」的玻璃观感；线宽取 `InnerShadowBlur(4dp)`。

### 自检中发现的规格冲突（**未擅自决定，报 Owner 裁定**）
- (a) §9.6.2 明文「下方一个**全宽**按钮」与 Owner 整改指令「解析按钮**不横跨全屏**」冲突；实现按 Owner 指令。
  是否将 §9.6.2 正文同步为「右侧自适应」？
- (b) §9.4 明文「平板左栏 40% / 右栏 60%」，实现为 60% / 40%（上一轮整改指令：输入区约 60%）；以哪个为准？
- (c) §9.6.1 / §9.6.3 明文「**圆形**按钮」（设置按钮 48dp、下载按钮 56dp，`CircleShape`）与整改项 1
  「圆角一律高度 × 0.33，严禁全圆」字面冲突；当前保留为圆形。是否也要改为方角？
- (d) §9.6.2 输入卡内 padding 明文 20dp，实现用 `PanelPadding = 24dp`；是否统一为 20dp？

### 遗留待裁定（沿用）
- `JieYunDuNavHost` 手机端为「顶部标题栏 + 顶部横向导航条 + 内容」，§9.5 要求底部 `NavigationBar`——
  「9.6.1 标题栏与 10.2 导航条并存 / 导航条应置底」仍待 Owner 出规格修订（阶段 8 首次登记，本轮重申）。
- 状态：**本轮改动已推送并跟 CI**；后续待 Owner **装机截图验收**（明示「不看截图不往下走」）。

## 阶段 8 整改复核 · Owner 四项裁决执行（2026-10-03）
> 依据 Owner 对(A)(B)(C)(D)四问的裁决 + 手机导航条正式规格修订，逐条落地。

### (a) 解析按钮：不横跨全屏（以 Owner 指令为准）
- 《要求.md》§9.6.2 正文：「下方一个**全宽**按钮」→「**输入框下方或右侧、宽度自适应**的解析按钮」；
  圆角 `20dp` → `18dp`（≈ 高度 56dp × 0.33，与现行圆角规则一致，属连带勘误）。
- 代码无需改（`LinkInputCard` 已是「输入框 `weight(1f)` + 右侧自适应按钮」）。

### (b) 平板两栏比例：回退为 左 40% / 右 60%（澄清「输入卡 60%」的含义）
- 原理解偏差已澄清：Owner 原意是「**输入卡片在左栏内部占 60% 宽**」，非「左栏占主内容 60%」。
- 代码：`HomeScreen` 平板 `START_COLUMN_WEIGHT` 0.6 → **0.4**、`END_COLUMN_WEIGHT` 0.4 → **0.6**；
  左栏内 `LinkInputCard` 宽 = 左栏 × **0.6**（`INPUT_CARD_WIDTH_RATIO`）并**居中**（Owner 允许「右对齐或居中」，取居中）；
  `ParseResultCard` 仍占左栏全宽。
- §9.4 保持原样（Owner 明示「保持 §9.4 原样」）；「输入卡占左栏 60%」这一细则暂未写入正文，如需登记请告知。

### (c) 圆形按钮：保持圆形（不改方）
- `CircleShape` 设置按钮（48dp）/ 下载按钮（56dp）**保持圆形**，代码不动。
- 《要求.md》§10.2 补注：「圆形图标按钮（如设置按钮 48dp、下载按钮 56dp）为圆形，
  不受本“圆角 = 高度 × 0.33”规则约束。」

### (d) 输入卡内边距：统一 20dp（以规格原文为准）
- `Dimens.kt` 新增专用常量 `InputCardPadding = 20dp`（不改 `PanelPadding`，避免波及其它卡片）；
  `LinkInputCard` 改用该常量。
- 与 §9.6.2「内部 padding 20dp」一致。

### 手机导航条位置 —— 正式规格修订已落地
- 《要求.md》§9.5 原文「底部 NavigationBar，高度 80dp」标注**「已作废，以本修订为准」**；
  新规格：手机端导航条为**顶部横向玻璃条，宽度 = 屏宽 − 32dp**，与第十部分 `TopGlassNavBar` 一致；
  标题栏与导航条在顶部**上下相邻**。
- **代码实证已符合，无需改码**：`NavigationBar`（横向 wrapper）已 `padding(horizontal = NavBarLengthInsetHorizontal = 16dp)`
  → 宽度 = 屏宽 − 32dp；`JieYunDuNavHost` 手机端为 `Column { AppTitleBar; NavigationBar; NavContent }`，
  即「标题栏在上、导航条紧贴其下」。
- **取舍说明（Owner 委由开发方判断）**：选**两条相邻**而非「合并为一条」——标题栏为 28sp 标题 + 48dp 圆钮、
  导航条为 64dp 弹簧指示器，合并会挤压标题且混淆两类交互；两条悬浮玻璃条上下相邻，职责清晰、与 §9.6.1 / §10.2 各自规格不冲突。

### 推送与 CI（本次）
- 改动文件：《要求.md》《CHANGELOG.md》《HomeScreen.kt》《LinkInputCard.kt》《Dimens.kt》。
- 状态：见下方「推送与 CI」记录。

## 阶段 8 视觉基准级改造 · 浅色 Area 风（2026-10-03）

> 依据 Owner 裁决：选定「浅色版 Area UI」为视觉基准（原两张深色版作废），
> 整体由深色切换为浅色；所有既有玻璃组件改配色，不新写一套。

### 颜色（Color.kt，常量同名改值，引用方零改名即可编译）
- 背景 #F5F5F7；卡片白 95% #F2FFFFFF；主色 #4A6CF7；主色浅底 #E8EEFF
- 文字主 #1C1C1E / 次 #8E8E93 / 三级 #AEAEB2；边框·分隔 #E5E5EA；输入框底 #F2F2F7
- 进度底 #E5E5EA / 填充 #4A6CF7；外阴影黑 4%；噪声改暗点（黑 1.5%）
- 新增 Primary / PrimaryLight / OnPrimary / Shadow / InputFieldFill 等；GlassRefraction* 置透明停用

### 主题与尺寸
- Theme.kt：darkColorScheme → lightColorScheme
- themes.xml：窗口底色 #FF0A0A0F → #FFF5F5F7；windowLightStatusBar true；父主题改 Material.Light
- Dimens：CardCorner 24 → 16dp；新增 CardElevation 6dp（近似 8dp 模糊）

### 组件改造
- GlassPanel：新增 Modifier.shadow 卡片外阴影（ambient/spot = Shadow）
- GlassBackground：光斑透明度 0.85 → 0.30（角落极淡，避免“聚光灯”抢戏）
- GlassButton：新增 contentColor 参数（默认主文字色，主色按钮传白）
- LinkInputCard / PasswordDialog：输入框底改 InputFieldFill；解析 / 确认按钮改主色蓝底 + 白字
- TopGlassNavBar：选中态改主色蓝字、未选中改灰字 #8E8E93
- ParseResultCard / JieYunDuNavHost：圆形下载 / 设置图标改主色蓝
- DownloadItem：进度条沿用常量，自动变浅灰底 + 蓝填充
- MainActivity：注释「深色背板」→「浅色背板」

- 状态：见「推送与 CI」记录。

## 阶段 8 布局重构 · 单栏流程 + 下载页筛选/删除 + 网盘入口（2026-10-03）
> 依据 Owner 指令：「UI 是好看的，但是布局不对，改布局」，并明确「浅色底保留、
> 顶部标题栏与左侧 Q 滑块仍为玻璃」「这个先做用于体验 UI 是否正常」。
> 视觉沿用 9.1.1 浅色色表；阶段 11（网盘 WebView 登录）留待下一轮。

### 导航（4 项）
- `JieYunDuNavHost`：`JieYunDuTab` 由 3 项扩为 4 项，新增 `NETDISK`（文案 `tab_netdisk`「网盘」）；
  平板竖直 rail 与手机横向导航条同步变 4 项；`NavContent` 新增 NETDISK 分支。
- 新增 `ui/screens/login/NetdiskPickerScreen.kt`：网盘选择页（四家列表 + 登录按钮空壳，
  `onLogin` 占位回调）。导入路径 `com.jieyundu.app.ui.screens.login`。

### 首页（单栏流程）
- `HomeScreen`：改为单栏 `Column { LinkInputCard; ParseResultCard(weight1f) }`；
  下载列表移出首页，由「下载」页承接；保留「需要提取码」弹窗逻辑。
- `LinkInputCard`：重写为「链接输入框 + 右置解析按钮」+ 下方常驻「提取码（选填）」框；
  抽出私有 `InputField`（浅灰底 + 自绘占位）。按钮文案解析中切 `parse_parsing`。
- `HomeUiState` 新增 `inputCode`；`HomeViewModel` 新增 `onCodeChange()`，
  `parse()` 提取码取值改为 `link.password ?: inputCode.ifBlank { null }`（链接携带优先）。
- `ParseResultCard`：重写成功态为「可勾选文件列表 +『下载选中』批量下载」；
  自绘 `SelectionBox` / `CheckGlyph`（D8，不引 material-icons）；
  新增空闲态 `IdleHint`；解析中 / 错误 / 需提取码沿用信息卡。

### 下载页（筛选 + 删除）
- `DownloadViewModel`：`DownloadSessionRegistry` 由「任务→文件名」扩为
  「任务→ `RememberedTask(fileName, savePath)`」，暴露 `tasks` 流并新增 `forget()`；
  `remember()` 新增可选 `savePath`（带默认值，保证单文件提交可编译）。
  新增 `DownloadFilter`（ALL / DOWNLOADING / COMPLETED）+ `filter` 状态 + `onFilterChange()`；
  `items` 改为三流 `combine`（进度 × 登记 × 筛选）；增强 `deleteTask()`。
- 新增 `DownloadFilterBar.kt`：全部 / 下载中 / 已完成 圆角胶囊筛选条。
- `DownloadScreen`：顶部筛选条 + 列表（`weight1f`）；空态 / 筛选空态共用一个空态视图。
- `DownloadItem`：行尾新增 40dp 圆形删除按钮（自绘垃圾桶），新增 `onDelete` 参数（带默认值）。
- 删除语义：先 `engine.cancel`（删分片 + 目标文件 + 清进度），再按 `savePath` 兜底删文件，
  最后幂等 `repository.deleteProgress` + `registry.forget`。
- **已知限制**：Room 进度表不含 savePath，路径仅存于会话内存；进程重启后历史任务无可删文件，
  删除仅移除记录。后续如需跨重启删除，须扩展 Room schema 存路径（属独立改动）。

### 尺寸与文案
- `Dimens.kt`：新增 FilterBarHeight 44 / FilterChipHeight 36 / FilterChipCorner 12 /
  CheckboxSize 20 / DeleteButtonSize 40。
- `strings.xml`：新增 tab_netdisk、netdisk_picker_title/subtitle、netdisk_login_action/hint、
  link_code_hint、parse_result_idle、parse_result_select_hint、action_download、
  download_filter_all/downloading/completed、cd_delete_download 等（均为中文，符合 C5）。

### 规格登记
- 《要求.md》：就地修订 §9.4（单栏 + 4 项导航）、§9.6.2/3/4（提取码框、结果可勾选、筛选+删除）、
  §9.6.5 与 §10.7（页签 4 项）；追加【布局修订 JYD-LAYOUT-2026-10-03】记录块。

### 推送与 CI（本次）
- 改动文件：`strings.xml`、`Dimens.kt`、`HomeUiState.kt`、`HomeViewModel.kt`、
  `JieYunDuNavHost.kt`、`NetdiskPickerScreen.kt`、`LinkInputCard.kt`、`HomeScreen.kt`、
  `ParseResultCard.kt`、`DownloadViewModel.kt`、`DownloadFilterBar.kt`、`DownloadItem.kt`、
  `DownloadScreen.kt`、《要求.md》《CHANGELOG.md》。
- 推送顺序（先定义后调用，避免中间态红）：DownloadViewModel → DownloadFilterBar →
  DownloadItem → DownloadScreen → HomeViewModel → 其余首页/导航文件 → 文档。
- 状态：见「推送与 CI」记录。

## savePath 修复（批次 1 · JYD-SAVEPATH-2026-10-03）
> 背景：旧实现落盘路径仅存于会话内存 `DownloadSessionRegistry`，进程重启后
> 「删除任务」无法定位本地文件。Owner 要求修复，并与阶段 11 分两批推送。
> 本批次不改任何 UI（配色与阶段 8 布局冻结）。
- `DownloadState.kt`：`DownloadProgressState` 新增 `savePath: String? = null`（带默认值，兼容既有构造）。
- `DownloadEntity.kt`：新增 `@ColumnInfo("save_path") savePath`，`toProgress`/`fromProgress` 双向映射；类注释同步。
- `AppDatabase.kt`：version `1 → 2`，新增 `MIGRATION_1_2`
  （`ALTER TABLE download_progress ADD COLUMN save_path TEXT`）。
- `DatabaseModule.kt`：`databaseBuilder(...).addMigrations(AppDatabase.MIGRATION_1_2)`。
- `DownloadEngine.kt`：`start()` 构造进度时写入 `savePath = task.savePath`，并新增
  `downloadDao.upsert(runtime.progress.value)`——启动即落库（顺带修复「新任务不立即出现在列表」）。
  后续 `publishProgress`/`pause`/完成态 `copy()` 自动保留该字段；`cancel()` 删除逻辑不变。
- `DownloadViewModel.kt`：`DownloadListItem.savePath` 改为 `remembered?.savePath ?: progress.savePath`
  （会话登记优先，回退持久化值）；相关 KDoc 同步。
### 验收方式（Owner）
- 下载一个文件 → 退出 App → 重开 → 删除任务 → 本地文件随之消失。
### 已知限制
- 文件名仍不持久化，重启后回退为「未命名任务」占位（不影响文件删除）。
### 规格登记
- 《要求.md》：就地更新 §4.4 下载页实现说明；追加【修订 JYD-SAVEPATH-2026-10-03】记录块。

## 阶段 11 · WebView 登录 + 登录态 Cookie 两级校验（JYD-LOGIN-2026-10-03）
> 依据 Owner 裁决「方案 A 为主 + 拦截兜底」，及《WebView登录与Cookie提取实践.txt》。
> 核心：Cookie 出现 ≠ 登录成功，须经网络校验。本批次不动阶段 8 布局与配色。
- `UserAgentProvider.kt`：填充 `quarkUserAgent`（API/客户端 UA，quark-cloud-drive/2.5.20…）；
  新增 `quarkWebUserAgent`（网页/登录态 UA，Chrome/130 … QuarkPC/6.0.8.649）。两套 UA 不混用。
- `CookieExtractor.kt`：登录判定语义由「任一命中」改为「必需字段全部命中」；补 UC（__puus+__pus）；
  夸克登录页 URL 补 ?fr=pc&platform=pc。
- 新增 `domain/login/LoginValidator.kt`：网络校验（夸克/UC account/info），CancellationException 原样抛出。
- 新增 `ui/screens/login/NetdiskLoginViewModel.kt`：登录目标状态 + 两级校验 + 节流/同凭证重试/在途锁
  + 手动保存/粘贴 + 登出。
- `WebViewLoginScreen.kt`：桌面 UA + 缩放/宽视口；手动「保存」「粘贴 Cookie」兜底；教程与校验提示。
- `NetdiskPickerScreen.kt`：展示登录态并提供「退出登录」。
- `JieYunDuNavHost.kt`：网盘页承载「选择页 ↔ 登录页」状态切换。
- `strings.xml`：新增阶段 11 手动兜底 / 校验 / 教程 / 退出文案。
- 《要求.md》：追加【修订 JYD-LOGIN-2026-10-03】记录块。

## 解析链路修复（JYD-TRANSFER-2026-10-03）+ 顶部标题栏删除（JYD-UI-TITLEBAR-2026-10-03）
> 依据《解析Bug分析.md》评审结论与 Owner 逐条裁定，一次性落地；不改其余三家解析器与阶段 11 登录逻辑。
### 夸克解析链路（P0 / P1）
- 新增 `domain/transfer/`：`ShareTransfer.kt`（转存 save→poll 封装）/ `TaskPoller.kt`（轮询任务完成）/
  `TempFolderManager.kt`（待清理 fid 登记，本批不建目录）。
- `QuarkParser.kt`：链路改为 `token → detail → save（转存）→ task 轮询 → file/download（传本账号新 fid）`；
  token 请求体补 `support_visit_limit_private_share`；握手登记 `__pus` + `__puus`；新增错误码 `QUARK_TRANSFER_FAILED`。
- `QuarkApi.kt`：detail 响应兼容 `detail_info?.list ?: list`（新增 `QuarkDetailInfo` 与 `entries` 取值器）；
  `QuarkFile` 增补 `share_fid_token`；新增 `saveShare` / `getTask` 接口与响应模型
  （`QuarkSaveResult` / `QuarkTask` / `QuarkSaveAs`）；`getDownloadUrl` 路径补 `?pr=ucpro&fr=pc&sys=win32&ve=3.23.2`。
  另：`saveShare` / `getDownloadUrl` 请求体由 `Map<String, Any>` 改为 `@Serializable` 请求模型
  （`QuarkSaveRequest` / `QuarkDownloadRequest`）——本项目 Retrofit 用 kotlinx.serialization 转换器，
  `Any` 无序列化器，`Map<String, Any>` 会在运行期抛 `SerializationException` 使整链不可用。
- `CookieStore.kt`：`save()` 由覆盖改为**合并回写**（新增 `mergeCookie()`），保留 `__pus` + `__puus` + `__pugs`。
- `NetworkModule.kt`：`ResponseCookieInterceptor` 的 KDoc 同步为合并语义（`__pugs` 采集逻辑不变）。
- `HomeViewModel.kt`：注入 `CookieStore` / `UserAgentProvider`；`download()` 填 `DownloadTask.headers`
  （按网盘类型取 Referer + 从 `CookieStore` 取 Cookie）。
- `LinkExtractor.kt`：百度分享 ID 去前导 `1`；新增 `TRAILING_PUNCTUATION` 裁剪链接尾部中文标点 `。，、；)]}"'`。
- `HomeUiState.kt` / `strings.xml`：新增 `QUARK_TRANSFER_FAILED` 的文案映射。
### 顶部标题栏删除（UI）
- `JieYunDuNavHost.kt`：删除私有 `AppTitleBar` / `SettingsGlyph` 及其几何常量；导航条作为内容区上方第一层，
  内容区自然上移；设置入口统一由「设置」Tab 承载；清理随之失效的 import。
- `Dimens.kt`：移除标题栏专用死常量 `HeaderHeight` / `HeaderHeightCompact` / `TitleBarMargin` /
  `TitleBarCorner` / `TitleBarCornerCompact`。
- 未触碰玻璃质感、Q 弹手感、浅色配色与导航弹簧参数。
### 规格登记
- 《要求.md》：追加【修订 JYD-TRANSFER-2026-10-03】、【修订 JYD-UI-TITLEBAR-2026-10-03】。
### 已知限制 / 待抓包
- 转存先落网盘**根目录**（不传 `to_pdir_fid`）；`.极云渡临时` 目录 + 自动清理推迟到阶段 13。
- `NEED_PASSWORD_CODE` / `WRONG_PASSWORD_CODE` 仍为占位值（41011 / 41012），待抓包校准。

## 待办 / 已知项
- 【阶段 7 装机反馈】已按上述「阶段 7 修订」处理（本轮推送）；装机实测结论待 Owner 反馈。
  原三点：①「不需要这么远」②「带点方形」③「不是很 Q弹」——其中 ②③ 已由修订一 / 二落地；
  ① 与修订二方向相反（修订二设计要求「过冲更大」），按 Owner 最新规格执行，① 视为已被覆盖。
- 评审清单 §1、§2 同步（评审方执行）。
- 评审清单 §8 P0「构建日志」：**已具备**（run#6 全绿 + artifact `jieyundu-debug-apk` 可下载）。
- 《要求.md》§5 仍将 `skydoves/Cloudy 1.0.0-alpha01` 列为指定依赖，与当前实现（方案 A 移除）
  已不一致，**待评审方/Owner 出勘误登记**（开发方按铁律不改规格文件）。
- 夸克接口已实测落地（见「夸克接口实测探测报告」与「夸克接口实测结果落地」两节）；
  剩余待抓包项：`NEED_PASSWORD_CODE` / `WRONG_PASSWORD_CODE` 真实取值、夸克 PC 客户端精确 UA 串。
- 百度 / UC / 迅雷三家解析器仍为骨架占位（阶段 9），各接口参数待逐家抓包填充。
- README 已声明本项目为完全独立开发，未参考任何现有网盘解析工具的源码（AI 辅助 · DeepSeek）。
