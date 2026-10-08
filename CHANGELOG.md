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

## 阶段 13：文件夹展开（含返回上一级路径栈）+ `.极云渡临时` 目录机制
> 依据 Owner 裁定（一次做完、一次推 CI）与《抓包事实.md》§6.1⑥ / §9.1 / §10.2 / §10.3；
> 不动其余三家解析器、不动 UI 视觉基准。规格登记见【修订 JYD-BROWSE-2026-10-03】/【修订 JYD-TEMP-2026-10-03】。
### 架构调整：浏览与下载解耦
- `QuarkParser.kt`：解析阶段只做「握手 → token → detail（根目录）」并返回 `pwdId` / `stoken` + 根条目；
  实现新接口 `ShareBrowser`（`listChildren`），点文件夹以该 fid 为 `pdir_fid` 再调 detail；不再依赖 `ShareTransfer`。
- 新增 `domain/parser/ShareBrowser.kt`：统一「列出某目录直接子项」接口（流程 A / 流程 B 共用，UI 只换底层实现）。
- 新增 `domain/transfer/ShareDownloadPreparer.kt`：统一「转存 → 取链 / 清理」接口（`prepare` / `cleanupAfterDownload`）。
### 临时目录机制
- `TempFolderManager.kt`（重写）：`ensureTempFolderFid`（查→建）/ `findTempFolderFid` / `recordPendingCleanup` /
  `deleteFromTemp`（删文件 + 空目录删除）/ `cleanupAll`（只删不建）。
- `ShareTransfer.kt`（重写）：实现 `ShareDownloadPreparer`；`saveAndCollectFids(pwdId, stoken, files, toPdirFid)`
  新增转存目标；`prepare` 转存到临时目录（建目录失败回退根目录 `0`）→ 轮询取新 fid → `file/download(新 fid)`。
- `QuarkApi.kt`：新增 `listFiles`（`file/sort`）/ `createFolder`（`file`）/ `deleteFiles`（`file/delete`，`action_type=2`）；
  新增 `QuarkFileList` / `QuarkCreateFolderRequest` / `QuarkCreateFolderResult` / `QuarkDeleteRequest` / `QuarkDeleteResult`；
  `QuarkSaveRequest` 增补 `pdir_fid` / `to_pdir_fid`。
### 模型 / DI / UI
- `ParseResult.Success` 增补 `pwdId` / `stoken`；`FileInfo` 增补 `shareFidToken`。
- `AppModule.kt`：新增 `provideShareBrowser`（QuarkParser）与 `provideShareDownloadPreparer`（ShareTransfer）绑定。
- `HomeUiState.kt`：新增 `ShareContext` / `BrowseLevel`、`stack` / `isLoadingDir` / `dirErrorRes` 与派生属性
  `currentLevel` / `canNavigateUp`。
- `HomeViewModel.kt`（重写）：`startParse` 建立分享上下文与根级路径栈；新增 `openFolder`（压栈）/ `navigateUp`（弹栈）/
  `download`（转存取链 → 投递引擎 → 完成后清理临时文件）；`buildDownloadHeaders`（Referer + Cookie）。
- `ParseResultCard.kt`：文件夹条目显示「文件夹」而非 `0 B`、点击进入；新增面包屑与「返回上一级」；勾选状态按目录层隔离。
- `HomeScreen.kt`：接线目录层 / 面包屑 / 返回上一级。
- `SettingsViewModel.kt` / `SettingsScreen.kt`：新增「临时文件清理」入口（`cleanupAll`）。
- `strings.xml`：新增文件夹展开、返回、空目录、加载中、加载失败、面包屑分隔、转存中、临时清理等文案。
### 测试
- `QuarkParserTest.kt`（重写）：同步 `QuarkParser` 新构造函数（去掉 `shareTransfer`）；移除解析阶段直链断言；
  新增 `parse_fillsPwdIdAndStoken` / `parse_fillsShareFidToken` / `parse_detailFails_returnsError` /
  `listChildren_requestsGivenPdirFid`；`FakeQuarkApi` 补齐 `listFiles` / `createFolder` / `deleteFiles`。
### 已知限制 / 待抓包
- 流程 B（个人网盘浏览 B1）下一批单独启动；回收站（B3）本批不做（夸克/UC/百度缺口）。
- `NEED_PASSWORD_CODE` / `WRONG_PASSWORD_CODE` 仍为占位值，待抓包校准。
## 阶段 13 装机反馈修复（JYD-CRASH / JYD-HOME-UI-2026-10-03）
> 依据 Owner 装机反馈（点击「下载选中」闪退 / 无法上下滑动 / 下载按钮沉底）与《修复追加指令》。
> 修复顺序按 Owner 要求：先加崩溃捕获与导出 → Owner 复现取日志 → 精准修闪退 → 修滑动 → 修底部按钮。
> 不触碰玻璃质感、圆角、浅色配色与 Q 弹导航；不动其余三家解析器。
### 崩溃捕获 + 日志导出（新增，让闪退可被记录）
- 新增 `util/CrashReporter.kt`：`install()` 链式注册 `Thread.setDefaultUncaughtExceptionHandler`
  （写日志后再交回原处理器，不吞异常）；`record()` 维护最近 100 行环形缓冲；
  `writeCrashLog()` 落盘至 `files/crash_logs/crash_yyyyMMdd_HHmmss.txt`（时间 / 版本 / Android / 设备 / 线程 + 堆栈 + 最近日志）；
  `pruneOldLogs()` 最多保留 10 条；`prepareShareFile()` 复制最新日志到 `cache/crash_logs/jieyundu_crash_<stamp>.txt`；
  `hasLogs()` / `clear()`。
- 新增 `util/RecentLogTree.kt`：`Timber.Tree` 子类，把每条日志格式化后交 `CrashReporter.record`（Debug/Release 均挂载）。
- `JieYunDuApp.kt`：`onCreate` 内 `Timber.plant(RecentLogTree())` + `CrashReporter.install(this)`；Debug 下另挂 `DebugTree`。
- 新增 `res/xml/file_paths.xml`：仅暴露 `cache-path` 的 `crash_logs/`（最小暴露面）。
- `AndroidManifest.xml`：新增 `FileProvider`（authorities `${applicationId}.fileprovider`，`exported=false`，`grantUriPermissions=true`）。
- `SettingsViewModel.kt`：注入 `@ApplicationContext Context`；新增 `crashState` / `refreshCrashLogs` / `exportCrashLog` /
  `consumeCrashShareFile` / `clearCrashLogs` 与 `CrashLogState` 数据类。
- `SettingsScreen.kt`：新增「崩溃日志」卡片（导出 / 清空）+ `LaunchedEffect(crashState)`：无日志 Toast「暂无崩溃日志」、
  有日志走 `FileProvider` + `ACTION_SEND` 系统分享（`shareCrashLog`）。
- `strings.xml`：新增崩溃日志相关文案。
### 闪退修复（根因：异常未捕获）
- 根因：`HomeViewModel.download()` 原先只 `catch (IOException)`；Retrofit 非 2xx 抛 `HttpException`、
  响应体异常抛 `SerializationException`（均为运行时异常）→ 逃逸协程 → 崩溃。
- `HomeViewModel.kt`：`download()` 签名改为 `download(files: List<FileInfo>)`，逐文件 `try` 且统一 `catch (Exception)` 兜底
  （`CancellationException` 原样抛出，C3），`finally` 复位 `isPreparingDownload` 并按 `hasFailure` 置 `downloadErrorRes`；
  `startParse()` / `openFolder()` 同样补 `catch (Exception)` 兜底；新增 `CODE_UNKNOWN`。
### 首页 UI 修复（列表可滚动 + 下载按钮底部悬浮）
- `ParseResultCard.kt`：`SuccessCard` 以 `fillMaxSize` 的 `Column` 承载头部，文件列表改为 `LazyColumn`（`weight(1f)` 占满余下高度，
  可上下滑动）；下载按钮移出列尾、以 `BoxScope.align(BottomCenter)` 悬浮于卡片底部（不随列表滚动）；
  按钮文案显示已选数量「下载选中 (N)」，未选中时置灰（透明度 0.45）且点击无效；列表底部预留 `Dimens.DownloadButtonInset`
  避免末行被遮挡；卡片内展示「正在转存…」与下载失败文案。
- `Dimens.kt`：新增 `DownloadButtonInset`（60dp）。
- `HomeUiState.kt`：新增 `isPreparingDownload` / `downloadErrorRes`。
- `HomeScreen.kt`：接线上述两状态。
- `strings.xml`：新增 `parse_download_failed`（补 `HomeViewModel` 既有引用）/ `parse_download_count`。
### 已知限制 / 待办
- 崩溃日志仅存本机、不上传；导出走系统分享。
- QQ/微信等第三方分享目标能否读取 `content://` 取决于其是否支持 `ACTION_SEND` 文件流。
## 阶段 B1：网盘管理（流程 B）+ 运行日志导出 + 下载错误码可读化（JYD-MANAGE-2026-10-03）
> 依据 Owner 指令：做网盘管理、四大网盘逐步支持、并更新崩溃/日志链路。本批为 B1（先夸克）。
> 抓包依据：《抓包事实.md》§10.1（容量）/ §10.2（个人目录列表）；不臆造字段（R3）。
### 网盘管理（流程 B：个人网盘浏览）
- 新增 `domain/parser/PersonalBrowser.kt`：统一「浏览本账号个人网盘」接口
  （`listPersonalChildren(pdirFid)` / `fetchQuota()`），与流程 A 的 `ShareBrowser` 对称。
- 新增 `domain/model/QuotaInfo.kt`：`used` / `total` / `usedInTrash` + 派生 `remaining`。
- `QuarkApi.kt`：新增 `getMember`（容量，§10.1）与 `QuarkMember` 响应模型。
- `QuarkParser.kt`：实现 `PersonalBrowser`——`listPersonalChildren` 走 `file/sort`（§10.2），
  `fetchQuota` 走 `member`（§10.1）。
- `AppModule.kt`：新增 `providePersonalBrowser`（当前绑定夸克）。
- 新增 `ui/screens/login/NetdiskBrowserViewModel.kt` + `NetdiskBrowserScreen.kt`：
  容量卡 + 面包屑 + 目录列表（LazyColumn 可滚动）+ 进入文件夹 / 返回上一级 / 返回列表；
  未实现管理的网盘给出「开发中」提示。
- `NetdiskPickerScreen.kt`：已登录网盘新增「管理」入口（原「退出登录」保留）。
- `JieYunDuNavHost.kt`：`NetdiskSection` 增加第三态（管理页），由 `NetdiskBrowserState.open` 驱动。
- `strings.xml`：新增网盘管理相关文案。
### 运行日志导出（让「被捕获的错误」也能取证）
- `CrashReporter.kt`：新增 `prepareRuntimeLogShareFile(context)`——把内存中最近 100 行 Timber
  日志缓冲落盘为 `cache/crash_logs/jieyundu_runlog_<stamp>.txt`（不依赖是否发生过崩溃）。
- `SettingsViewModel.kt` / `SettingsScreen.kt`：新增「导出运行日志」按钮 + `RuntimeLogState`；
  复用 FileProvider 系统分享（分享函数泛化为 `shareLogFile(context, file, titleRes)`）。
- `strings.xml`：新增运行日志相关文案。
> 用途：「下载启动失败 / 解析失败」这类错误**不触发崩溃**，此前只能看到笼统提示；现在可一键
> 导出运行日志，看到真实业务码 / 堆栈。
### 下载错误码可读化（根因修复）
- 根因：`QuarkResponse<T>.data` 原为非空；服务端业务失败返回 `"data":` 时会抛
  `SerializationException`，把真实业务码一并吞掉，调用方只能得到笼统「失败」。
- `QuarkApi.kt`：`QuarkResponse.data` 改为可空（`T? = null`），`code` 缺省 `-1`。
- `QuarkParser.kt` / `TaskPoller.kt` / `ShareTransfer.kt` / `TempFolderManager.kt`：全部改为
  先判 `code`、再读 `data?.xxx`，业务失败得到明确错误码而非异常。
- `QuarkParserTest.kt`：`FakeQuarkApi` 补 `getMember` 实现。
### 已知限制（本批边界）
- 网盘管理当前仅夸克可用；UC / 百度 / 迅雷在 B2–B4 接入（其 UA 仍是空串待填，见 §5）。
- 个人网盘文件的「下载」按钮本批未加（先看文件；下载链路复用现有引擎，下一批接）。
- 夸克 / UC / 百度的「回收站列表」仍未抓包（《抓包事实.md》§10.4），不臆造。
### CI 修复（B1 追加，JYD-CI-2026-10-03）
- `NetdiskBrowserScreen.kt`：面包屑原在 `joinToString` 的 lambda 内调用 `stringResource`，该 lambda
  非 `@Composable` 上下文，导致 `compileDebugKotlin` 报
  `@Composable invocations can only happen from the context of a @Composable function`（117:33）。
  改为在可组合体内提前求值 `val rootLabel = stringResource(...)` 后传入 lambda。
- CI：#37120866025 `build` 失败（本因）→ 修复后 #37121018106 ✅。
- 交付提交：`e0b2bd67abd91e57486cc6c2e01d3604cc91cd68`（父 `80925faeb3874b6c6f66c172c6f9fe797c318599`）。

## 阶段 B1.1：装机反馈修复（JYD-FIX-2026-10-03）
> 依据 Owner 装机反馈：① 夸克网盘管理页「容量」文字重叠；② 提供运行日志
> （`jieyundu_runlog_20261003_200100.txt`）。

### ① 容量卡文字重叠（已修复）
- 根因：`GlassCard` 的内容槽类型是 `BoxScope.() -> Unit`（即 **Box**），`QuotaCard` 直接传入
  3 个 `Text` 子项，Box 把它们叠放在同一位置 → 视觉重叠。
- 修复：`QuotaCard` 内容自带 `Column`（`Arrangement.spacedBy(Dimens.SpaceSm)` + `fillMaxWidth`）。
- 复核：`NetdiskPickerScreen` / `SettingsScreen` 的同类卡片本就包了 `Column` / `Row`，未受影响。
- 未改动玻璃质感、圆角、Q 弹手感与浅色配色。

### ② 下载失败真因（日志实证）
- Owner 日志显示：读接口**全部 200**（`file/sort`、`member`、`sharepage/token`、`sharepage/detail`），
  仅 `POST /1/clouddrive/share/sharepage/save` 返回 **HTTP 401**，随后抛
  `retrofit2.HttpException: HTTP 401`。
- 结论：失败点是**转存（save）**这一步——不是解析、不是取链、不是下载引擎；性质是鉴权层面拒绝。
- 待确认（不臆造，R3）：401 的服务端返回体此前未被记录，具体业务码未知，已补日志（见 ③）。

### ③ 失败可观测性（新增）
- `ShareTransfer.callWithHttpLog(step, block)`：HTTP 非 2xx 时记录步骤名、状态码与响应体前 500 字；
  异常**原样抛出**（C3）。
- `HomeViewModel`：单独识别 401 → 提示走新增文案 `parse_download_auth_failed`
  （「登录态已失效，请在网盘页退出后重新登录」）；其余失败仍为 `parse_download_failed`。

### 已知限制（本批边界）
- 401 的具体成因（Cookie 缺失 / 过期 / 客户端标识校验）**尚未定论**，需下一份运行日志确认。
- 未改动任何未获抓包证实的请求头或参数。
- 观察到一处无害瑕疵：`file/sort` 的 URL 出现重复的 `pr/fr`（接口注解与 `QueryMap` 各带一次），
  服务端正常响应，留待 B2 与 UC 共用参数构造器时一并收敛。→ **已在 B1.2 修复，见下。**

## 阶段 B1.2：pr/fr 去重 + 转存 URL 对齐抓包 + 写操作 Cookie 诊断（JYD-FIX2-2026-10-03）
> 依据 Owner 反馈：① `file/sort` 的 `pr=ucpro&fr=pc` 被拼了两遍；② 继续攻坚
> `sharepage/save` 的 HTTP 401（下载失败真凶）。

### ① `pr/fr` 重复（已修复）
- 根因：`QuarkApi.listFiles` 注解已固定携带 `?pr=ucpro&fr=pc`，
  `QuarkParser.buildPersonalListParams` 的 `QueryMap` 又带了一次 → URL 出现两份。
- 修复：从 `QueryMap` 中移除 `pr` / `fr`，由注解统一提供（§10.2 要求的参数仍全部在）。
- 复核：`TempFolderManager.buildListParams` 本就不带 `pr/fr`（依赖注解），行为不变；
  `buildMemberParams` / `buildDetailParams` 的注解不含查询串，保持原样。

### ② 转存 URL 对齐抓包（非确诊性修复）
- 《抓包事实.md》§1 记录的转存 URL 为 `.../sharepage/save?pr=ucpro&fr=pc`，
  而 `QuarkApi.saveShare` 注解此前**漏写**这两个固定参数，现补齐。
- 声明：这是**对齐抓包**，**不等于**已确诊 401 的根因；未获证据前不改动任何请求体字段。

### ③ 写操作 Cookie 诊断（新增，为 401 取证）
- `NetworkModule.CookieInterceptor`：非 GET 请求额外记录本次携带的 Cookie **名**
  （`cookieNames=__pus,__puus,...`，**不含值**，避免日志泄露凭证）。
- 目的：若服务端 401 的响应体为空，`cookieNames` 是判断「`__puus` 有没有被送出去」的唯一信号。
- 与 B1.1 的 `ShareTransfer.callWithHttpLog` 配合，一次复现即可拿到两组证据。

### 已知限制（本批边界）
- 401 根因**仍未确诊**，本批只做「对齐抓包 + 补齐取证」，不做任何猜测性修改。
- 《抓包事实.md》§7.2 的 `__puus` 定期刷新（90 分钟 / 剥掉再请求重下发）仍未实现，属独立改动。
- 不改玻璃质感 / 圆角 / Q 弹手感 / 浅色配色。

## 阶段 B1.3：按《评审清单》§11 调整（JYD-REVIEW-2026-10-03）
> 依据 Owner 转达的评审清单新增章节 §11（运行日志故障定位）。

### 对 §11.1（P2：`pr/fr` 重复）——采纳评审方建议的写法
- B1.2 采用的是「从 `QueryMap` 移除、由注解提供」；评审方建议改为反向：
  「从 `listFiles` 路径移除、统一由参数传入（与 `detail`/`member` 一致）」。
- 本批**采纳评审方建议**，理由：固定参数只有一处来源，且与 `detail`/`member`/`save`
  的既有写法统一，结构性杜绝「路径带一次 + 参数又带一次」的重复。
- 改动：`QuarkApi.listFiles` 路径去掉 `?pr=ucpro&fr=pc`；
  `QuarkParser.buildPersonalListParams` 与 `TempFolderManager.buildListParams` 均显式提供 `pr`/`fr`。
- 代价（已在代码 KDoc 标注）：新增 `file/sort` 调用方**必须**记得带 `pr`/`fr`，
  故两处 builder 的 KDoc 都写明该约束。

### 对 §11.2（P0：`save` 401）——评审方「建议优先修」项已在 B1.2 完成
- 评审方指出 `QuarkApi.saveShare` 漏 `?pr=ucpro&fr=pc`，是全链路唯一「鉴权接口 + 参数不一致」处。
- 该改动已于 B1.2 落地（提交 `f2e83b297e1d4dfdb2a1d85e7dbafd7da62a581a`，CI `37122219378` ✅）。
- 取证手段亦已就位：`ShareTransfer.callWithHttpLog`（响应体）+ Cookie 名诊断（B1.2 ③）。
- 声明：仍**不宣称**已修复 401；是否消除须以下一份装机日志为准。

### 对 §11.3（握手 302 的观察）——确认属实，本批不动
- 确认：握手 `GET https://pan.quark.cn/` 会跳转到 `/list`，`registerHandshakeCookies`
  读到的是重定向后的响应头；若 `__pus`/`__puus` 在首跳下发则可能取不到。
- 处置：该步骤为 best-effort 且不阻断解析（token/detail 游客可用），按评审意见列为观察项，
  本批**不改**——避免在没有日志证据前扩大改动面。

### 对 §11.4（待办）的回应
- [x] 开发方：修 `saveShare` 缺参（P0）——B1.2 完成。
- [x] 开发方：去重 `pr/fr`（P2）——B1.2 + B1.3 完成（写法按评审建议统一）。
- [ ] 评审方：待新日志核对 401 是否消失、响应体是否被记录。

### 已知限制（本批边界）
- 与 B1.2 相同：401 根因未确诊；§7.2 `__puus` 定期刷新未实现。
- 不改玻璃质感 / 圆角 / Q 弹手感 / 浅色配色。
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

【修订 JYD-SIGN-2026-10-03】固定 App 签名（解决「每次安装丢登录」）
--------------------------------------------------------------------------------
  依据：Owner 反馈「每次安装都要重新登录夸克」；目标：软件使用同一签名。
  根因（已实证）：app/build.gradle.kts 无 signingConfigs，CI 每次在全新虚拟机现生成
    默认 debug.keystore → 每个 APK 签名不同（实测 B1=3e32…、B1.1=136d…、B1.3=8e5c…）
    → 新包无法覆盖旧包 → 必须卸载 → 加密存储中的登录态被清空 → 每次重登。
  修复：
    · 生成固定密钥 jieyundu.jks（PKCS12，别名 jieyundu，RSA-2048，10000 天，
      SHA-256=52:C3:CC:90:DB:3D:8B:7F:8E:93:D3:07:71:8E:06:2C:79:7B:82:37:55:80:28:DD:01:85:84:DA:85:AA:61:70）。
    · app/build.gradle.kts 新增 signingConfigs.fixed，从环境变量读取，debug/release 共用；
      无环境变量时回退默认 debug 签名（不影响本机 / 无密钥构建）。
    · .github/workflows/build.yml 构建前从 Secrets 还原密钥并注入环境变量。
    · 密钥备份四处：私有仓库 qiuchen520/NetDiskGo（keys/）、本机 /sdcard/Download/ 两份、
      极云渡仓库 Secrets 一份（供 CI）。
  一次性代价：现有安装均为随机签名，需卸载当前 App 一次；此后升级即可覆盖安装、保留登录。
  约束：不改玻璃质感 / 圆角 / Q 弹手感 / 浅色配色；未改任何业务逻辑。
================================================================================


================================================================================
【阶段 B2 · UC 网盘支持 + 三项新功能】2026-10-03
--------------------------------------------------------------------------------
一、UC 网盘支持（照夸克同构落地）
  · 接口层 UcApi.kt、解析器 UcParser.kt、转存三件套
    （UcTaskPoller / UcTempFolderManager / UcShareTransfer）。
  · 多网盘路由器 NetdiskServiceRouter（按 NetdiskType 取分享浏览器 / 个人网盘浏览器 / 转存器）。
  · 网络层 NetworkModule：新增 UC BaseUrl（https://pc-api.uc.cn/）与独立 Retrofit；
    UA 拦截器按目标 host 选 UC/夸克 UA；响应 Cookie 拦截器按 host 分别登记 quark.cn / uc.cn。
  · UserAgentProvider 补 UC 三套 UA；AppModule 改为 @IntoSet 多绑定 + 路由器。
  · UcParser：容量走 1/clouddrive/member；列表走 1/clouddrive/file/sort（§2/§10.2 冲突已在代码登记）。
  · 未闭合（R3）：UC 提取码「需要 / 错误」业务码为占位（41011/41012），待用户抓包。
二、功能①：下载目录设置（默认 A3，改目录才用 A1）
  · 新增 AppSettingsStore（SharedPreferences：并发数 / 目录模式 / 自定义路径）。
  · 默认 PUBLIC_DOWNLOADS：引擎写应用私有目录 → 完成后 PublicDownloadsPublisher 发布到
    公共 Download/极云渡/（API29+ 走 MediaStore，无需权限；API26-28 直写公共目录 + 媒体扫描）。
  · CUSTOM：用户主动「更改目录」时按需申请「所有文件访问」（Android 11+）或写外部存储权限（<29），
    经 SAF 目录选择器 + DocumentTreePathResolver 还原真实路径，引擎直接写入该路径。
  · DownloadViewModel.deleteTask 兼容 content:// 位置（MediaStore 记录）。
  · 已知限制：默认模式发布后删除私有副本，进程重启后会话登记丢失，列表回退到私有 savePath
    （已删）→ 分享 / 安装 / 删除可能失效，需重新下载。此处如实登记，不静默。
三、功能②：下载线程 32–512（默认 64）
  · DownloadTask：MIN=32 / MAX=512 / DEFAULT=64；新增档位集合 {32,64,128,256,512}。
  · DownloadEngine 新增专用缓存线程池调度器（突破 Dispatchers.IO 的 64 并行上限），分片数即并发数；
    NetworkModule 抬高连接池（512 空闲连接）与 Dispatcher 上限（512 / 主机）。
  · ChunkManager 收敛区间改为 32..512（仍不超过文件字节数）。
  · 设置页新增档位选择器；ChunkManagerTest 同步到新范围。
  · 风险提示：512 并发大概率触发 CDN 限速 / 封 IP。
四、功能③：下载完成条目「分享 / 安装」
  · DownloadItem 行尾新增分享按钮；APK 条目额外显示安装按钮（均自绘图标，遵守 D8）。
  · DownloadScreen：分享走 ACTION_SEND；安装走 ACTION_VIEW（application/vnd.android.package-archive）。
    content:// 直接用；真实路径经 FileProvider 换取 Uri。
  · Manifest 增 REQUEST_INSTALL_PACKAGES；file_paths.xml 增私有 Download 与外部存储根白名单。
约束：未改动玻璃质感 / 圆角 / Q 弹手感 / 浅色配色。
================================================================================


================================================================================
【B2 收尾 · 评审清单 §12 占位码废弃 + 抓包事实 §9.3 实证修正 + 方案 A 诊断日志】2026-10-03
--------------------------------------------------------------------------------
一、评审清单 §12：废弃自造占位码 41011 / 41012（改为「按上下文判定」）
  · QuarkParser / UcParser：token 失败判定改为——服务端 code != 成功码时，
    依「本次请求是否携带提取码」归类：未携带 → NeedPassword；已携带仍失败 → 提取码错误码。
    不再依赖任何数字占位码（响应同时带 status 与 code，无「提取码」专用数字码）。
  · 删除常量 NEED_PASSWORD_CODE = 41011 / WRONG_PASSWORD_CODE = 41012 及 when(code) 分派；
    类头 KDoc 同步订正为「不使用提取码专用数字码」。
  · HomeViewModel：提取码错误识别改为集合 {QUARK_WRONG_PASSWORD, UC_WRONG_PASSWORD}，
    修复「UC 分享提取码错误时不弹窗重试」的 B2 遗漏 bug。
  · QuarkParserTest 同步：以 FAILURE_CODE(400) 取代占位码，删除 RISK_CONTROL_CODE，
    「需要提取码 / 提取码错误」两条用例改名并按新逻辑断言。
  · 本批订正：CHANGELOG 各处「41011/41012 占位 · 待抓包校准」（第 254 / 389 / 701 / 736 行）
    与 要求.md 第 1669 行原文均作废——该占位码系自造哨兵，无对应协议事实，非「待抓包」项。
二、评审清单 §11：订正为「已在 B1.2/B1.3 修复」的陈旧条目
  · §11.1 `file/sort` 的 pr/fr 重复：QuarkApi.listFiles 路径已去 pr/fr，由
    QuarkParser.buildPersonalListParams 提供（B1.3），已修。
  · §11.2 `save` 缺 ?pr=ucpro&fr=pc：QuarkApi.saveShare 已补（B1.3），已修。
  · §11.3 握手 302（pan.quark.cn → /list）：仍为观察项，未处置（best-effort，不阻断）。
三、抓包事实 §9.3 实证修正（UC 分享链路，Owner 补录原样实录）
  · 接口层 UcApi：getShareDetail 路径补 &ve=2.5.20（与游客 UA uc-cloud-drive/2.5.20 一致）。
  · save 字段语义修正：pdir_fid = 分享内「源目录」fid（根为 0）、to_pdir_fid = 转存目标；
    此前两者同填转存目标（临时目录），与抓包不符——疑似 403 / code 41020「转存文件 token
    校验异常」根因（§6.1③ 旧文与 §9.3③ 冲突，以 §9.3 为准，代码内已登记）。
  · ShareDownloadPreparer.prepare 新增 sourcePdirFid 参数；
    UcShareTransfer.prepare / saveAndCollectFids 适配，pdir_fid = sourcePdirFid。
  · HomeViewModel.startDownload 传当前浏览层级 stack.last().pdirFid 作为源目录 fid。
  · 夸克侧（R3）：§6.1③ 记为两字段同值、§9.3③ 已实证（UC 取源目录）；夸克无专项抓包，
    本批**不改其请求取值**，在 ShareTransfer 内以 TODO(用户抓包) 登记歧义，待夸克 save 报文到手再定。
四、方案 A（诊断日志）
  · UcShareTransfer / ShareTransfer 在 save 前加 Timber.d，打印 pdir_fid / to_pdir_fid /
    fid 数 / token 数 / 空 token 数（不泄露内容），供装机后与抓包逐字比对。
  · 背景：0.1.0 装机日志已能记录服务端失败响应体（callWithHttpLog 生效），
    UC save 实测 403 / code 41020；本包用于验证修正后是否转为成功。
约束：未改动玻璃质感 / 圆角 / Q 弹手感 / 浅色配色；未改任何 UI。
================================================================================

================================================================================
【开源协作基础设施 · 新增 CLA（贡献者许可协议）】2026-10-03
--------------------------------------------------------------------------------
一、新增 CONTRIBUTING.md（仓库根）
  · 贡献指南：提交 PR 前须签署 CLA；写明签署方式（PR 评论回复指定语句）。
  · 说明 CLA 采用 Apache Individual CLA 模板，授权方式为「版权许可（非转让）」。
二、新增 .github/workflows/cla.yml
  · 采用 contributor-assistant/github-action@v2.6.1。
  · 触发：issue_comment（created）+ pull_request_target（opened/closed/synchronize）。
  · 首次 PR 自动提示签署；签署记录写入 signatures 分支（signatures/version1/cla.json）。
  · 免签名单 allowlist=qiuchen520,bot*。
三、新增 docs/CLA.md
  · 收录 Apache ICLA V2.2 全文（英文为准），受益主体由 Apache 基金会替换为本项目所有者；
    附中文参考译文。授权方式：版权许可（非转让）——贡献者保留版权，另授予所有者永久、
    免费、可再许可权利，便于日后闭源收费 / 双版本分发。
四、README 增加「贡献指南」章节，指向 CONTRIBUTING.md。
约束：**未改动**项目自身协议（AGPL-3.0 / LICENSE 原样保留）；CLA 仅为贡献门槛。
现有贡献者（目前仅 Owner 一人）不强制补签。
================================================================================
【规格变更 JYD-SETTINGS-2026-10-03】阶段 C · 设置页功能扩充（分四批交付）
--------------------------------------------------------------------------------
Owner 指令（2026-10-03）：按截图条目扩充设置页；**明确允许新增深色模式**。
交付纪律：逐批交付（C1→C2→C3→C4），每批完成后停下等装机验收，不自动推进。

【C1 · 下载增强】（本批实现）
1. 最大同时下载任务数
   · 默认 1；档位 {1, 2, 3, 5}；范围收敛 1..5。
   · 作用域：全局（跨任务）。达到上限时新任务进入 PENDING 排队，
     前序任务进入终态（完成 / 失败 / 暂停 / 取消）后自动补位。
2. 下载速度限制
   · 默认「不限速」；档位 {不限速, 1, 2, 5, 10} MB/s。
   · 作用域：全局（所有任务与其全部分片共享同一令牌桶，按字节匀出带宽）。
3. 失败自动重试
   · 默认 3 次；档位 {0, 1, 3, 5}；范围收敛 0..5。
   · 作用域：任务级——整任务失败后按已落盘 `.part` 断点续传重试，重试耗尽才置 FAILED。
   · 既有「分片级内部重试」不变（两者叠加：分片先内部重试，仍失败则整任务重试）。
   · 重试期间不得向观察者发布 FAILED（避免前台服务提前停止）。
4. 约束：不改动玻璃质感 / 圆角 / Q 弹手感 / 浅色配色。

【C2 · 后台保活与通知】（待做，届时细化）
1. 锁屏后保持下载：开关（默认开）；下载期间持有 PARTIAL_WAKE_LOCK，
   并给出「加入忽略电池优化白名单」入口。
2. 通知栏下载进度：开关（默认开）；关闭时不发布进度通知（前台服务仍运行）。

【C3 · 检查更新】（待做，届时细化）
1. 设置页「检查更新」：查询 GitHub Releases，比较版本号，有新版本可跳转。

【C4 · 主题与外观】（待做，届时细化；Owner 已解禁深色模式）
1. 主题模式：跟随系统 / 浅色 / 深色（默认跟随系统）。
2. 深色配色须保持既有「玻璃质感 / 圆角」规范；浅色方案不变。

--------------------------------------------------------------------------------
【实现记录 JYD-SETTINGS-2026-10-03 · C1】
本批落地的代码改动（待 CI 编译验证）：
· domain/downloader/DownloadTask.kt
    - 新增同时任务数常量（默认 1 / 范围 1..5 / 档位 {1,2,3,5}）；
    - 新增失败重试常量（默认 3 / 范围 0..5 / 档位 {0,1,3,5}）；
    - 新增限速常量（默认 0=不限速 / 档位 {0,1,2,5,10} MB/s）。
· domain/downloader/DownloadEngine.kt
    - 新增全局调度闸门：scheduleMutex + pendingQueue + runningTaskCount，
      start() 经 schedule() 决定「立即执行 / 排队」，任务进入终态后 releaseSlotAndDispatchNext()
      从队首补位；ACTIVE_STATES = {PENDING, DOWNLOADING}（不含 PAUSED，保证 resume 可用）。
    - 新增任务级失败重试：runTask() 外层循环按 currentMaxTaskRetries() 重试，
      重试期间保持 DOWNLOADING、不发布 FAILED，已落盘 .part 作为断点。
    - 新增全局 SpeedLimiter（令牌桶 / 时间预约模型），在所有分片的写盘循环中按
      currentSpeedLimitBytesPerSecond() 申请配额，实现 App 总出口限速。
    - 新增 DownloadSettingsPort 端口接口（同文件定义，data 层实现）。
· data/settings/AppSettingsStore.kt
    - 实现 DownloadSettingsPort；新增 maxConcurrentTasks / maxTaskRetries /
      speedLimitBytesPerSecond 三个 StateFlow、setter 与持久化键。
· di/AppModule.kt
    - 新增 provideDownloadSettingsPort，把 AppSettingsStore 绑定为 DownloadSettingsPort。
· ui/screens/settings/SettingsViewModel.kt / SettingsScreen.kt
    - 暴露三组档位与当前值；设置页新增三张卡片（同时任务数 / 下载限速 / 失败重试），
      复用统一 OptionChip（视觉与既有筛选胶囊一致）。
· res/values/strings.xml
    - 新增 C1 相关文案（无硬编码中文，符合 C5）。
约束遵守：未改动玻璃质感 / 圆角 / Q 弹手感 / 浅色配色；未新增第三方依赖（D8 不触碰）。

================================================================================
【实现记录 JYD-UC-FIX-2026-10-03 · UC 下载链路修正（P0）】
本批落地的代码改动（待 CI 编译验证）：
· domain/parser/uc/UcApi.kt
    - UcDownloadRequest 增加 pwd_id / stoken / fids_token 三字段（严格对齐评审方 §13）；
    - getDownloadUrl KDoc 订正为「分享直连取链，无需转存」。
· domain/transfer/UcShareTransfer.kt（重写）
    - prepare() 去掉 saveShare / awaitSavedFids，改为直连取链：
      api.getDownloadUrl(UcDownloadRequest(fids=listOf(file.fid), pwd_id, stoken,
      fids_token=listOf(file.shareFidToken)))；
    - 删除 saveAndCollectFids 与 FileInfo.toUcFile；
    - cleanupAfterDownload 改为空操作（无转存副本）；
    - 构造函数仅保留 UcApi（移除 UcTaskPoller / UcTempFolderManager 注入）。
· 废弃（降级保留备用，待 Owner 拍板）：
    - domain/transfer/UcTaskPoller.kt、UcTempFolderManager.kt 不再被引用，类文件保留。
· 文档同步：
    - 要求.md §8.4 UC 流程第 5 步订正为「直接取直链（无需转存）」；新增本规格变更记录；
    - 评审清单.md §13.5 开发项勾选。
背景：运行日志实证 UC save 恒返回 403 / code 41020「转存文件 token 校验异常」，
      重登 UC 后依旧 → 判定问题不在登录态 / pdir_fid 语义，而在「UC 无需转存」这一前提。
约束遵守：只改 UC，未动夸克 ShareTransfer；未改玻璃质感 / 圆角 / Q 弹手感 / 浅色配色。
================================================================================
================================================================================
【实现记录 JYD-UC-FIX2-2026-10-04 · UC 取链令牌来源修正（P0 · 二修）】
本批落地的代码改动（待 CI 编译验证）：
· domain/parser/uc/UcApi.kt
    - 新增 `transferShareDetail(@QueryMap)`（GET transfer_share/detail，带
      Origin/Referer=https://fast.uc.cn 的 @Headers）；
    - 新增响应模型 `UcTransferShareDetail`（兼容 detail_info.list / list / file_list 三键）。
· domain/transfer/UcShareTransfer.kt（重写）
    - prepare() 先取令牌再取链：fetchShareFidToken() 走 transfer_share/detail 取
      share_fid_token，再调 file/download（fids/pwd_id/stoken/fids_token）；
    - buildTransferDetailParams() 组装 11 个查询参数；
    - 失败点新增日志步骤 transfer-detail；cleanupAfterDownload 仍为空操作。
· di/NetworkModule.kt
    - UC 侧 UA 由 ucUserAgent（云盘客户端 1.6.1）改为 ucWebUserAgent（网页 Chrome 120），
      依据《UC取链请求_逐字段对照.txt》：token / transfer_share/detail / file/download
      均以普通 Chrome UA 下发。
· 保留（未删）：
    - domain/transfer/UcTaskPoller.kt、UcTempFolderManager.kt 仍降级保留备用（Owner 要求）。
背景：首修后下载端点仍 41020，逐字段对照确认 fids_token 取错接口（v2/detail 应为
      transfer_share/detail）。
约束遵守：只改 UC，未动夸克 ShareTransfer；未改玻璃质感 / 圆角 / Q 弹手感 / 浅色配色。
================================================================================
================================================================================
【实现记录 JYD-DLINFO-2026-10-04 · 下载详细信息 + 准备态文案修正（P0）】
来源：Owner 反馈「二修后不再报下载失败，但一直显示在转存中；且希望下载页展示详细信息
      （一共有多少 / 已下载多少 / 平均每秒），不要只有进度条」。
排查结论：
· 「一直转存中」根因 = HomeViewModel.download() 把 isPreparingDownload 保持到**整个下载结束**
  才在 finally 复位，期间首页卡片（ParseResultCard）固定渲染 parse_download_preparing
  「正在转存并获取直链…」→ 观感像卡在转存，实际下载可能正常进行（状态/文案误导）。
· 下载页 DownloadItem 已展示「已下载 / 总量」与**瞬时速度**，但缺「平均速度」（Owner 要的
  “平均每秒”）。
本批落地的代码改动：
· domain/downloader/DownloadState.kt
    - DownloadProgressState 新增 averageSpeedBytesPerSecond:Long（默认 UNKNOWN_SIZE），
      用于承载本次运行的平均速度；initial() 同步补字段。
· domain/downloader/DownloadEngine.kt
    - TaskRuntime 新增 sessionStartAt（本次运行起点）；
    - start() 初始化 averageSpeedBytesPerSecond=0，记录 sessionStartAt；
    - publishProgress() 计算平均速度 = 本运行累计写入字节 ÷ 本运行已进行时长；
    - pause()/completed() 置 0，cancel() 置 UNKNOWN_SIZE。
· ui/screens/download/components/DownloadItem.kt
    - 列表项右上速度文本改为「瞬时 · 均速 X」（复用 FileSizeFormatter.formatSpeed）。
· ui/screens/home/HomeViewModel.kt
    - startDownload() 只在「取链准备」阶段置 isPreparingDownload=true；取链失败与
      downloadEngine.start() 投递后立即复位，避免整个下载过程都顶着“准备中”。
· res/values/strings.xml
    - 新增 download_speed_pair_format = "%1$s ·均速 %2$s"；
    - parse_download_preparing 文案由「正在转存并获取直链…」改为「正在获取直链并开始下载…」。
背景：UC 二修后链路已不再报错，剩余为「状态误导 + 详情不足」体验问题。
约束遵守：仅动下载/首页相关；未改玻璃质感 / 圆角 / Q 弹手感 / 浅色配色；未新增 material-icons；
         中文文案入 strings.xml（C5）；日志走 Timber（C8）。
待办：装机复验——下载页是否出现「已下载 / 总量 · 均速」；首页是否不再长期停在“准备中”。
================================================================================

================================================================================
【实现记录 JYD-UC-FIX3-2026-10-04 · UC 浏览列表与取链令牌同源化（P0 · 三修）】
来源：Owner 交付《参考实现_源码通读研究.md》（脱敏重写版），要求重读 §9.3 并据其修 UC；
      根因指示为「D1 走了转存链路、D2 fids_token 取自 v2/detail」。
排查结论（三修真正关键差距）：
· 首修（FIX）去转存、二修（FIX2）改 fids_token 来源后，取链链路本身已正确；但**浏览列表**
  （UcParser.fetchEntries）仍走 `sharepage/v2/detail`，而取链令牌走 `transfer_share/detail`——
  两接口不同源，返回条目的 fid 可能对不上，导致 UcShareTransfer 在 transfer_share/detail 中
  按 fid 匹配 share_fid_token 落空 → prepare() 返回 null → 下载被**静默跳过**（表现为点了下载没反应）。
本批落地的代码改动（只动 UC）：
· domain/parser/uc/UcApi.kt
    - 新增 `object UcTransferDetailQuery`：共享的 transfer_share/detail 查询参数构造器
      （pwd_id / pdir_fid / fetch_file_list=1 / passcode / _page=1 / _size=50 /
      _fetch_total=1 / _fetch_task=1 / _fetch_share=1 / _sort= / stoken），避免两处各写一套漂移；
    - 删除已是孤儿、且正是「v2/detail 陷阱」源头的三个声明：
      `getShareDetail()` 方法、`UcShareDetailRequest`、`UcShareDetail`（已确认全项目无引用）；
    - 文件级 KDoc 订正：UC 分享走「免转存型」链路，不再写「列表走 v2/detail」。
· domain/parser/uc/UcParser.kt
    - fetchEntries() 由 `api.getShareDetail(UcShareDetailRequest(...))`（v2/detail POST）改为
      `api.transferShareDetail(UcTransferDetailQuery.build(...))`——**浏览与取链同源**；
    - 第 1 步握手 Cookie 采集新增 `__pugs`（PUGS_COOKIE_NAME，游客兜底 Cookie，登录取到亦无害）；
    - 删除不再使用的 PAGE_SIZE 常量；KDoc 步骤 4 / listChildren 说明改述为 transfer_share/detail。
· domain/transfer/UcShareTransfer.kt
    - fetchShareFidToken() 改用共享 `UcTransferDetailQuery.build(...)`；
    - 删除本地重复的 buildTransferDetailParams() 及其参数常量（KEY_*、ONE_VALUE、FIRST_PAGE、
      PAGE_SIZE、EMPTY_PASSCODE、EMPTY_SORT）。
背景：二修后 Owner 反馈「不再报失败，但一直显示转存中」；进一步定位到浏览与取链不同源，
      使令牌匹配落空、下载被静默跳过（UI 无反馈）。三修即把两处统一到 transfer_share/detail。
约束遵守：只改 UC，未动夸克 ShareTransfer；未动 UI；中文入 strings.xml（C5）；日志走 Timber（C8）。
待办：装机复验【粘贴 UC 分享链接 → 解析 → 展开 → 勾选 → 下载 → 成功】。
================================================================================

================================================================================
【实现记录 JYD-DLSPEED-2026-10-04 · 下载页速度恒「--」修复（P0 · B1 批）】
来源：Owner 反馈——下载页速度列恒为 `--`，进度条只停在启动 / 续传时的静态值。
排查结论（根因）：
· 下载页列表数据源为 Room（DownloadRepository.observeProgress），而 Room 只在引擎的
  「start / pause / complete」三处 downloadDao.upsert 写入；
· 引擎的实时进度 publishProgress()（每 200ms 节流）**只发内存 MutableStateFlow**，
  既不落库、下载页也没订阅；
· DownloadEntity.toProgress() 的速度字段固定为 DownloadProgressState.UNKNOWN_SIZE
  （Room 不保存速度字段）→ 界面速度恒 `--`。
方案（Owner 已批准）：引擎暴露实时进度快照 Flow，UI 订阅并与 Room 持久化数据合并显示。
本批落地的代码改动：
· domain/downloader/DownloadEngine.kt
    - 新增私有内存实时表 liveProgressFlow + 只读 liveProgress: StateFlow<Map<String,
      DownloadProgressState>>（与按任务订阅的 observeProgress 并存，互不影响）；
    - 新增 publishLive(progress)：以「读当前值 → 生成新 Map → CAS 回写」写入
      （多分片协程并发调用时不丢更新），**只改内存、不写库**；
    - publishProgress()（200ms 节流处）末尾接入 publishLive → 实时速度的来源；
    - start() 落库后接入（新任务立即出现在列表且带实时值）；
    - pause() 接入（暂停即发布最终态：速度归零）；runTask() 完成 / 失败分支接入
      （终态即时可见）；cancel() 接入 removeLive()（实时表移除条目，避免残留脏数据）。
· ui/screens/download/DownloadViewModel.kt
    - items 由三路 combine 改为四路：Room 进度 × **引擎实时快照** × 会话登记 × 筛选档；
    - 新增 mergeLiveProgress()：活动态（PENDING / DOWNLOADING）与终态（COMPLETED / FAILED）
      一律用实时快照覆盖 Room 画面，其余（进程重启后无运行态、已取消）保留 Room 数据；
      savePath 仍以 Room 记录为准。
· domain/util/DownloadTimeFormatter.kt（新增）
    - Owner 要求①「剩余时间」：remainingSeconds(剩余字节, 瞬时速度) = 剩余字节 ÷ 速度
      （速度非正 / 剩余未知 → -1）；formatRemaining(秒) 输出 `3 min 20 s` / `2 h 30 min` /
      `1 d 1 h`，未知输出 `--`；纯 ASCII、不涉中文（C5）。
· ui/screens/download/components/DownloadItem.kt
    - 详情行改走 download_size_remaining_format，展示「已下载 / 总量 · 剩余 X」；
      新增私有 remainingBytes(item) 计算剩余字节（总量未知 → -1 → `--`）；
      余下尾部状态文案位置与样式不变（不动 UI 视觉基准）。
· res/values/strings.xml
    - 新增 download_size_remaining_format = "%1$s / %2$s · 剩余 %3$s"（替代 download_size_format）。
· app/src/test/java/com/jieyundu/app/domain/util/DownloadTimeFormatterTest.kt（新增）
    - 覆盖 formatRemaining / remainingSeconds 的正常与边界分支（不触网，随 CI 单测执行）。
性能（Owner 要求③）：实时刷新只写内存 MutableStateFlow，落库仍只发生在 start / pause /
complete 三刻，未新增任何下载过程中的 Room 写入。
约束遵守：未改 UI 视觉基准（仅详情行文案内容变化）；未动下载引擎核心下载逻辑（分片 / 续传 /
限速 / 重试）；只新增一个实时进度通道 + UI 订阅；未新增 material-icons（D8）；
中文入 strings.xml（C5）；日志走 Timber（C8）。
待办：装机复验——下载一个文件，速度列**实时变化**、不再一直 `--`；暂停后显示最终态。
================================================================================

================================================================================
【实现记录 JYD-DLSPEED2-2026-10-04 · 整体进度 + 显式暂停/继续（B1 二轮 · 装机反馈）】
来源：Owner 装机反馈（B1 一轮）两条：
      ①「整体进度缺失」——看得到均速/剩余时间，但看不到「已下载多少 / 总共多少」；
      ②「没有暂停功能」——下载项里必须有暂停/继续按钮。
排查结论（关键）：
· ① 的真因是**布局被裁**，不是数据缺失：下载项高度固定 88dp（平板）/ 76dp（手机），
  扣掉 24dp（平板，PanelPadding）或 16dp（手机）内边距后仅剩 40dp / 44dp，
  而内容需要 4 行（文件名 / 进度条 / 详情 / 状态）≈ 60dp+；一轮新增的
  `download_size_remaining_format` 详情行落在卡片下缘之外被裁掉 → 用户看不到「已下载 / 总量」。
· ② 引擎方法早已齐备且接线正确（DownloadViewModel.toggleTask → pause/resume），
  但入口只有「点击整张卡片」这一个隐式手势，没有可见按钮 → 用户认为没有暂停功能。
本批落地的代码改动：
· ui/theme/Dimens.kt
    - DownloadItemHeight 88dp → **124dp**（平板）；DownloadItemHeightCompact 76dp → **104dp**（手机），
      为三行信息留足高度；
    - 新增 ToggleButtonHeight = 36dp（暂停 / 继续文字按钮高度）。
· ui/screens/download/components/DownloadItem.kt（重写列表项布局，按 Owner 给的骨架）
    - 三行结构：① 文件名 + 状态文案；② 进度条（6dp / 圆角 3dp，继续自绘）+ 百分比；
      ③ 详情行「已下载 / 总量 · 速度 · 剩余时间」；
    - 行距按档位取 SpaceSm（平板）/ SpaceXs（手机），手机档位更紧凑；
    - 新增 `ToggleButton`：暂停 / 继续**文字按钮**（浅主色底 + 主色字，沿用既有配色），
      仅 PENDING / DOWNLOADING / PAUSED 展示；下载中显示「暂停」，否则显示「继续」；
    - 新增 `onToggle` 参数；分享 / 安装 / 删除按钮与错开淡入动画保持不变。
· ui/screens/download/DownloadScreen.kt
    - 传入 `onToggle = { viewModel.toggleTask(item) }`（与整卡点击同源，避免两套逻辑漂移）。
· ui/screens/download/DownloadViewModel.kt
    - `toggleTask` KDoc 补全「引擎真正停止 / 断点续传」的接线说明（方法体未变）。
· domain/downloader/DownloadEngine.kt
    - `publishProgress()` 增加活动态前置判断：pause() 取消协程到真正停下的窗口内，
      在途分片仍可能回调本方法；若不拦截，会把刚发布的「已暂停」覆盖回 DOWNLOADING，
      表现为「点了暂停又跳回下载中」。
· res/values/strings.xml
    - 新增 download_detail_format = "%1$s / %2$s · %3$s · 剩余 %4$s"（取代 download_size_remaining_format）；
    - 新增 download_action_pause = "暂停"、download_action_resume = "继续"。
关于「进度条」组件的说明：Owner 建议用 LinearProgressIndicator；本实现继续使用 9.6.4 既有的
      自绘进度条（高 6dp、圆角 3dp、底色 ProgressTrack、填充 ProgressFill），视觉规格与建议一致，
      同时满足《要求.md》9.9「不要用 Material 默认组件直接堆叠」的既有约定；未新增任何颜色。
暂停语义确认（引擎既有逻辑，本批未改核心下载逻辑）：
· pause() → runtime.job.cancel()，分片协程在 `currentCoroutineContext().ensureActive()` 处抛出
  CancellationException（C3 原样抛出，不转 FAILED），请求线程随即结束 → **引擎真正停止请求**；
  已落盘的 `.part` 分片保留，进度落库；
· resume() → start() → `ChunkManager.readPartProgress` 读回各分片已落盘长度作为 `resumeFrom`，
  Range 头从断点写起 → **不重下已有部分**；
· cancel() 会删除 `.part` 分片与目标文件（删除任务语义，未改）。
约束遵守：未改玻璃质感 / 圆角 / 配色（进度条与按钮均复用既有色常量）；未动引擎下载核心逻辑
（仅加一个活动态前置判断）；仅新增 UI 元素 + 接线；中文入 strings.xml（C5）；日志走 Timber（C8）。
待办：装机复验四条（进度条 + 已下载/总量 + 速度 + 剩余时间；暂停速度归零；继续断点续传；完成满格显示已完成）。
================================================================================

================================================================================
【实现记录 JYD-B2GLASS-C2-2026-10-04 · B2 整页玻璃化 + C2 后台保活与通知】
来源：Owner 指令「B1 验收通过，开 B2 + C2；两批同推，一次出包」；
      B2 范围经 Owner 当面确认 = **整页玻璃化**（下载页 + 设置页），配色 / 圆角 / 视觉基准不动。
一、B2 · 整页玻璃化（下载页 + 设置页）
· 新增 ui/components/GlassChip.kt
    - 玻璃质感档位胶囊（两个重载：直接文案 / 字符串资源）；
    - 未选中 = 玻璃面板（半透明白底 + 边缘高光 + 极淡内阴影）+ 次级文字色；
    - 选中 = 仍为主色蓝底 + 白字（**不改配色**），但改用 GlassPanel，保留玻璃边缘高光 / 内阴影；
    - 圆角沿用 Dimens.FilterChipCorner（非全圆，符合 9.2 / 9.6.4）。
· ui/glass/GlassPanel.kt
    - 新增可选 fillColor / borderColor 参数（默认值与原硬编码色一致），使胶囊等元素可复用玻璃
      材质而无需引入新颜色；既有调用点行为不变。
· ui/screens/download/components/DownloadFilterBar.kt
    - 整条筛选条改为一块玻璃面板（contentPadding = SpaceSm），内部三枚档位胶囊改用 GlassChip；
    - 删除原「纯色扁平」FilterChip 实现。
· ui/screens/download/DownloadScreen.kt
    - 空态由裸 Box 改为玻璃面板，空态图标 / 文案居中，与列表卡片同材质。
· ui/screens/settings/SettingsScreen.kt
    - 删除私有 OptionChip / ChunkOptionChip（纯色扁平），全部档位改用 GlassChip（含限速 / 重试 /
      同时任务数 / 并发分片数四处）；
    - 新增「后台与通知」玻璃卡片（C2 设置项，见下）；
    - 清理随之不再使用的导入。
二、C2 · 后台保活与通知（《要求.md》C2 第 1、2 条）
· 新增 service/DownloadWakeLockManager.kt
    - 下载期间持有 PARTIAL_WAKE_LOCK：懒持有（仅在任务运行时）、可重入（计数）、可失败
      （系统拒绝只记日志）；带 10 分钟超时兜底，避免异常路径锁泄漏。
· data/settings/AppSettingsStore.kt
    - 新增 keepDownloadingOnLock（默认开）与 downloadNotificationEnabled（默认开）两个开关，
      沿用既有 SharedPreferences + StateFlow 模式。
· service/DownloadService.kt（原为「挂空未接线」，本批接线）
    - 进度来源改为引擎实时快照 liveProgress（与下载页同源，避免 observeProgress 在任务未注册时
      返回静态 PENDING 导致通知不动）；60 秒无该任务更新则结束前台观察，防止通知常驻；
    - 按 keepDownloadingOnLock 申请 / 释放唤醒锁（finally + onDestroy 双保险，幂等）；
    - 按 downloadNotificationEnabled 决定通知详略：关闭时不刷进度、不发完成通知，但仍以静默
      常驻通知维持前台服务（系统硬性要求「前台服务必须有通知」，无法真正零通知）；
    - startForeground 全程 try/catch（Android 12+ 后台启动限制 / 机型差异），失败只降级不崩溃；
    - 新增 DownloadService.start(context, task) 静态入口，内部 catch 启动异常。
· service/NotificationHelper.kt
    - 新增 buildSilentForegroundNotification()（关闭进度通知时的静默常驻通知）；
    - notify(...) 新增 enabled 重载（关闭时直接跳过）。
· ui/screens/home/HomeViewModel.kt
    - 投递引擎后调用 DownloadService.start(appContext, task) —— C2 接线点。
· ui/screens/settings/SettingsViewModel.kt + SettingsScreen.kt
    - 两个开关接入设置页；新增「加入忽略电池优化白名单」入口（优先直达授权弹窗，厂商 ROM 回退
      电池优化设置列表；均不可用只记日志），并提供 batteryOptimizationExempt 状态显示。
· AndroidManifest.xml
    - 新增 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限（仅用于拉起系统授权弹窗）；
      WAKE_LOCK / FOREGROUND_SERVICE / FOREGROUND_SERVICE_DATA_SYNC / POST_NOTIFICATIONS 早已声明。
· res/values/strings.xml
    - 新增 settings_background_title / settings_keep_downloading_title+hint /
      settings_download_notification_title+hint / settings_battery_optimization_hint+action+done /
      notification_download_silent_text。
约束遵守：未改玻璃质感基准 / 圆角规格 / 配色常量（未新增任何颜色）；未动引擎下载核心逻辑
（分片、续传、限速、重试一行未改）；中文入 strings.xml（C5）；日志走 Timber（C8）；
耗时与 IO 仍在其既有作用域内（C9）；取消异常一律原样抛出（C3）。
待办：装机复验（见《要求.md》同标识条目的验收清单）。
================================================================================

================================================================================
【实现记录 JYD-DELSAFE-2026-10-04 · P0 删除安全边界（数据安全 · 单独出包）】
来源：Owner《Bug 修复指令（4 项）》【P0】删除安全边界——删除操作可能误删用户网盘里已保存的文件。
审计结论（先查证，再动手）：
· 全项目「网盘侧删除」调用点只有两处：TempFolderManager.deleteFromTemp（夸克）与
  UcTempFolderManager.deleteFromTemp/cleanupAll；二者均只由「转存副本清理」链路触发
  （ShareTransfer.cleanupAfterDownload → 传入的 newFid 来自 save 返回并已 recordPendingCleanup）；
· 个人网盘（自己的文件）下载链路根本不经过 ShareDownloadPreparer，因此**没有正在生效的误删路径**；
· 但该保护是**隐式**的——安全性依赖「每个调用点都记得先登记 fid」。一旦将来新增调用点忘记登记，
  就可能删到用户文件。本批把该性质固化为**单点硬边界**。
本批落地的改动：
· domain/transfer/TempFolderGuard.kt（新增，纯逻辑 + 一个并发集合）
    - `isInTempFolder(name, pdirFid, tempFolderFid)`：路径含临时目录标识，或父目录 fid 命中临时目录 fid；
    - `mayDeleteFromTemp(...)`：网盘侧删除的**唯一放行判定**，满足任一即放行——
      ① 转存流程登记过的 fid；② 路径在临时目录内；③ 目录 fid 比对命中；
      ④ 临时目录自身且**确认目录为空**；
    - **默认拒绝**：fid 为空、父目录未知且未登记、路径不含标识，一律返回 false；
    - `register/registerAll/unregister/isRegistered`：维护「转存副本」可信集合。
· domain/transfer/TempFolderManager.kt（夸克）
    - `deleteFromTemp`：删除前过守卫，拒绝时记 `Timber.w` 并返回 false（不再发删除请求）；
    - `cleanupAll`：逐条过守卫，未通过者跳过并记日志（即便集合混入非转存 fid 也不会误删）；
    - `deleteTempFolderIfEmpty`：临时目录自身也过守卫（仅空目录放行）；
    - `recordPendingCleanup` / `ensureTempFolderFid` / `findTempFolderFid`：同步登记到守卫；
      删除成功后 `unregister`。
· domain/transfer/UcTempFolderManager.kt（UC）：同上五处对等改动。
· app/src/test/java/com/jieyundu/app/domain/transfer/TempFolderGuardTest.kt（新增，11 个用例）
    - 覆盖指令要求的三类：临时目录内 → 允许；用户自己网盘的文件 → 禁止；路径相似但不匹配 → 禁止；
    - 另覆盖：空 fid 默认拒绝、相似父目录 fid 拒绝、临时目录自身仅空目录可删。
修复过程中的一处自纠：守卫首版 `matchesTempPath` 只识别 `/.极云渡临时/` 与结尾形态，
漏了**相对路径** `.极云渡临时/xxx`（该形态在单测里会被判为「禁止」而误拒）；
已补 `startsWith(标识 + "/")` 分支，使实现与 KDoc / 单测一致。
指令第 3 条（个人网盘下载后**只删本地文件、网盘源文件一个都不碰**）：
· 个人网盘下载不经过 ShareDownloadPreparer（无转存、无 newFid），`cleanupAfterDownload` 从未被调用；
· UC 侧 `cleanupAfterDownload` 本就为空操作（直连取链不产生转存副本）；
· 本批新增的守卫进一步保证：即便将来误调用，未登记的 fid 也一律拒绝。
约束遵守：未改 UI 视觉（玻璃 / 圆角 / 配色未动）；未改引擎下载核心逻辑；未改请求头 / 参数（R3）；
日志走 Timber（C8）；中文入 strings.xml 之外的安全边界说明写在 KDoc（无 UI 文案新增）。
待办：**单独出包验收**——① 从自己网盘下载文件，完成后网盘里该文件仍在；
② 从分享链接转存下载，完成后临时目录里该文件已删。
================================================================================

================================================================================
【实现记录 JYD-P1-2026-10-04 · 任务名持久化 + 重启续传 + 转存登记持久化（P1 三项）】
来源：Owner 装机复现——① 下载→暂停→划掉后台→重开，任务显示「未命名」；
      ② 同一任务点「继续」没反应；③ 追加：把转存登记持久化（上批自提的边界）。
本批三项一起实现、一起出包。
一、P1-1 任务名持久化
· data/local/DownloadEntity.kt：新增 `file_name` 列（NOT NULL DEFAULT ''），老数据迁移后为空串，
  UI 仍回退「未命名任务」；新增 `url` / `headers` 两列（见 P1-2）。
· data/local/DownloadDao.kt：**关键防回退**——进度每 200ms 刷新一次，原来的
  `@Insert(REPLACE)` 会把未提供的列覆盖成默认值，等于每 200ms 把任务名清空一次。
  改为两条显式写路径：
  - `upsertTaskEntity`（任务存档）：按主键存在与否走 UPDATE 或 INSERT(IGNORE)，写任务名 / 直链 / 请求头；
  - `updateProgressColumns`（进度刷新）：**只更新进度列**，绝不触碰任务名等列。
· domain/downloader/DownloadEngine.kt：`start()` 落库改为写「任务存档」
  （`DownloadCheckpointPort.upsertTask`），任务名 / 直链 / 请求头随之持久化。
二、P1-2 重启后续传
· domain/downloader/DownloadTaskRecord.kt（并入 DownloadEngine.kt）：任务存档模型
  （taskId / url / fileName / fileSize / savePath / chunkCount / headers），与 DownloadTask 一一对应。
· domain/downloader/DownloadEngine.kt
    - 新增端口 `DownloadCheckpointPort`（upsertTask / loadTask），由 Room DAO 实现；
    - `resume()` 改为返回 `EngineActionResult`（密封接口）：Resumed / Restarted / NoOp /
      NoCheckpoint / MissingPartFiles——**入口必须有明确结果**，杜绝「点了没反应」；
    - 新增 `resumeFromCheckpoint()`：内存运行态不存在时（进程重启）按存档重建任务；
      分片不存在 → MissingPartFiles（UI 提示「文件已损坏，请重新下载」）；
    - 新增 `restart()`：清空该任务全部分片后从零开始（重启后同样可用）；
    - 抽出 `prepareRuntime()`：新任务 / 普通续传 / 重启续传共用同一套分片与断点计算，避免两套行为；
    - 新增续传日志：savePath / `.part` 是否存在 / 已下载字节数。
· domain/downloader/ChunkManager.kt：新增 `hasPartFiles()` / `deleteAllPartFiles()`
  （按「目标名 + .part + 全数字」逐项比对，避免文件名含 `.` 时正则转义出错）。
· JieYunDuApp.kt：启动期把库里残留的「等待中 / 下载中」统一纠正为「已暂停」
  （否则界面显示下载中却永远不动，与「点了没反应」观感一致）。
· UI（不改视觉基准）：DownloadItem 新增「重新下载」文字按钮（仅暂停 / 失败态展示，
  与「继续」并列）；DownloadViewModel 新增 `restartTask()` 与一次性反馈消息通道；
  DownloadScreen 用 Toast 呈现结果（成功 / 文件已损坏 / 缺少续传信息）。
三、P1-3 转存登记持久化
· data/local/TransferRecordEntity.kt + TransferRecordDao.kt（新增）：`transfer_records` 表
  （fid 主键、file_name、dir_fid、netdisk_type、created_at + dir_fid 索引）。
· domain/transfer/TransferRecordPort.kt（新增）：端口 + TransferRecord 模型（domain 不依赖 data）。
· domain/transfer/TempFolderManager.kt / UcTempFolderManager.kt：
  转存登记时写库（`transferRecords.upsert`）、删除成功后同步移除登记（`delete`）、
  `cleanupAll` 成功后清理对应登记；写库失败只记日志、不阻断下载（D15）。
· JieYunDuApp.kt：启动期从 `transfer_records` 水合删除守卫（`TempFolderGuard.registerAll`），
  使「手动清理」在重启后同样能识别并清理重启前产生的临时副本。
四、数据库升级
· data/local/AppDatabase.kt：版本 2 → 3，`MIGRATION_2_3` 增加 `file_name` / `url` / `headers`
  三列（均带默认值，老数据不崩）并建 `transfer_records` 表与其索引；
  DatabaseModule 注册新迁移与三个端口绑定（DownloadProgressPort / DownloadCheckpointPort /
  TransferRecordPort）。
五、单元测试（随 CI 单测执行）
· data/local/DownloadEntityTest.kt（新增 6 例）：任务存档往返（含任务名 / 直链 / 请求头）、
  缺直链或落盘路径时返回 null、请求头编解码（含 `:` 与 `=` 的值、空值、畸形行）。
· domain/transfer/TransferRecordGuardTest.kt（新增 4 例）：水合后重启前副本变为可删、
  持久化目录 fid 被识别为临时目录、未登记的用户文件仍拒绝、注销后权限回收。
实现过程中的两处自纠（写进记录，避免后续重犯）：
1. 初版把「任务名」寄托在进度快照里并沿用 REPLACE 写入 → 进度每 200ms 刷新会把任务名清空。
   改为「存档写任务列 + 进度只写进度列」两条显式路径；
2. 初版给 `download_progress` 加了 `transfer_fid` 列，但既无写入方也无读取方（P1-3 的关联
   由 `transfer_records` 表自身承担）→ 删除该冗余列，避免 schema 与迁移虚增。
约束遵守：未改 UI 视觉（仅新增一个与既有按钮同款的文字按钮）；未动已完成功能
（分片 / 续传 / 限速 / 重试核心逻辑未改，仅把「入口」从 Unit 改为带结果）；
未改任何请求头 / 参数（R3）；中文入 strings.xml（C5）；日志走 Timber（C8）。
待办：装机验收（见《要求.md》同标识条目）。
================================================================================

================================================================================
【实现记录 JYD-P1B-2026-10-04 · 「重新下载后任务名变未命名」修复（P1 追加）】
来源：Owner 装机反馈——点「重新下载」后任务名变成「未命名」；其余功能正常。
排查结论（真因与 Owner 推测不同，值得记录）：
· 不是「重下把 file_name 清了」：全项目只有 `DownloadDao.updateTaskColumns` 会写 `file_name`，
  且引擎传的是 `task.fileName`；DAO 的进度写入只更新进度列（P1 已防回退），重下路径不会清名。
· 真因是 **UI 从未读取持久化的名字**：`DownloadListItem.fileName` 只取 `sessionRegistry`
  （**进程内内存态**），进程重启后该表为空 → 界面回落「未命名任务」。
  即 P1-1 把名字存进了 Room，但界面的取值链路里没有它。
· 为何只在「重新下载」暴露：Owner 的验收流程（暂停 → 划掉后台 → 重开 → **继续**）走
  `resumeFromCheckpoint`，引擎会用存档里的名字**重写一行**，之后显示正常；而点「重新下载」时
  若不经该重写路径、又要靠 Room 的值显示，就只剩空注册表可查 → 显示「未命名」。
本批落地的改动：
· domain/downloader/DownloadState.kt：`DownloadProgressState` 新增 `fileName: String?`（默认 null）
  ——作为**持久化任务名到 UI 的读取通道**（写库时进度刷新仍不写该列，契约不变）。
· data/local/DownloadEntity.kt：`toProgress()` 把 `file_name` 一并交给 UI（空串视为无值）。
· domain/downloader/DownloadEngine.kt：内存进度快照带上 `task.fileName`（实时快照也有名字）。
· ui/screens/download/DownloadViewModel.kt：
    - 新增纯函数 `resolveTaskName(persistedName, sessionName)`，明确优先级
      **持久化（Room）> 会话登记 > null**；
    - `items` 组装改用该函数（此前只取会话登记）。
· data/local/DownloadDao.kt：`upsertTask` 增加**防御性保留**——本次未提供（空值）的任务名 /
  直链 / 请求头一律保留库中已有值，只有调用方明确给出新值（重新解析换了直链 / 换了 fid）才覆盖。
  这样「重新下载」在实现层也被锁死为「只清 `.part` 分片 + 进度，不动任务事实」。
· 新增单测 app/src/test/.../download/TaskNameResolutionTest.kt（5 例）：持久化名优先、
  会话登记为空（重启场景）时仍显示持久化名、回落会话名、空白名回落、两者皆无返回 null。
指令第 2 条（url / fid 变了才清空重来）：当前实现不存在「清空任务存档」的动作——
  存档是**覆盖写**，调用方给出新 url / headers 时自然被替换，未给出时按上一条保留既有值；
  「重新下载」只删除 `.part` 分片与重置进度，不触碰任务名 / 直链 / 请求头。
约束遵守：未改 UI 视觉；未动引擎分片 / 限速 / 重试核心逻辑；未改请求参数（R3）。
================================================================================

================================================================================
【实现记录 JYD-BROWSER-2026-10-04 · 网盘管理页「删除 / 下载到本地」入口恢复】
来源：Owner 指令——网盘管理页浏览正常，但删除、下载到本地两个功能入口丢失；发布 0.1 前最后一项。
排查结论：
· 底层**已有**能力：`QuarkApi.deleteFiles` / `UcApi.deleteFiles`（§10.3）与
  `QuarkApi.getDownloadUrl`（§10.4）均在，`PersonalBrowser` 接口只暴露了
  `listPersonalChildren` / `fetchQuota`；`NetdiskBrowserScreen` 的行内也没有任何操作入口。
  ⇒ 属「业务层能力在、UI 入口缺、接口未暴露」，本批补齐三处。
· 个人网盘下载的参数边界（R3，重要）：夸克 `file/download` 只认 `fids`（本账号 fid），
  个人文件可直接取链；**UC 的 `file/download` 请求体强制要求 `pwd_id` / `stoken` /
  `fids_token` 三个分享态字段**，个人文件的取链报文至今没有抓包依据，故 UC 侧**明确返回
  「不支持」并提示用户**，绝不猜测参数（宁可少一个入口，也不编造接口）。
本批落地的改动：
· domain/parser/PersonalBrowser.kt：新增 `deletePersonalFile(fid)` 与
  `fetchPersonalDownloadUrl(fid)` 两个能力（接口注释写明语义、安全责任边界与 R3 依据）。
· domain/parser/quark/QuarkParser.kt：
    - `deletePersonalFile`：`QuarkDeleteRequest`（`action_type = 2` 移入回收站）→ 成功码判定；
    - `fetchPersonalDownloadUrl`：`QuarkDownloadRequest(fids = [fid])` → 取 `download_url`。
· domain/parser/uc/UcParser.kt：
    - `deletePersonalFile`：`UcDeleteRequest` 同构实现（UC 路径不带 `uc_param_str=`）；
    - `fetchPersonalDownloadUrl`：**恒返回 null** 并记日志（原因见上，待抓包事实补齐后实现即可打通，
      UI 无需改动）。
· domain/transfer/TempFolderGuard.kt：新增**用户主动删除**的显式出口
  `mayDeleteUserInitiated(fid, userInitiated)`。
    - 语义划分（必须分清）：`mayDeleteFromTemp` 管「App 自己 / 顺带」的清理，只允许临时目录内；
      本方法管「用户在管理页明确选中并确认删除自己的文件」，因此不限制在临时目录内；
    - 但仍**共用同一处判定入口**（避免两处各判、规则漂移），且只有 `userInitiated = true`
      （UI 确认弹窗之后）才放行，并写审计日志便于日后排查误删投诉。
· ui/screens/login/NetdiskBrowserViewModel.kt：
    - 新增动作状态 `NetdiskActionState`（待删除条目 + 一次性提示）；
    - `requestDelete` / `cancelDelete` / `confirmDelete`：**先确认、再删除**；
      `confirmDelete` 的链路 = 用户确认 → `mayDeleteUserInitiated` 放行 → 解析器删除 →
      重新列出当前目录刷新；
    - `downloadToLocal`：取链 → 复用既有下载链路（`DownloadEngine` 分片下载 +
      `DownloadSessionRegistry` 登记 + `DownloadService` 前台保活 + 完成后按 A3 发布到公共目录），
      与首页下载**同一套策略**（落盘目录解析 / 发布逻辑与 `HomeViewModel` 对齐）；
    - UC 取链为 null 时提示「该网盘暂不支持直接下载自己网盘的文件」。
· ui/screens/login/NetdiskBrowserScreen.kt：
    - 行尾新增「…」操作菜单（文字指示符，D8：不引入 material-icons），展开为
      「下载到本地 / 删除」两个按钮；
    - 新增删除**确认弹窗**（`AlertDialog`），文案说明「可从网盘回收站恢复」；
    - 结果用 Toast 一次性提示（删除成功/失败、下载已开始/失败/不支持）。
· ui/navigation/JieYunDuNavHost.kt：接线动作状态与五个回调。
· res/values/strings.xml：新增 11 条文案（入口、确认弹窗、结果提示）。
· 浏览功能未动（列表 / 面包屑 / 返回上一级 / 容量卡保持原样）。
约束遵守：未改玻璃质感 / 圆角 / 配色（菜单按钮与弹窗沿用既有 GlassButton 与色表）；
未动已完成功能；删除仅走既有 `file/delete`（未新增 / 未改任何请求字段，R3 只读既有接口）；
中文入 strings.xml（C5）；日志走 Timber（C8）。
待办：装机验收（管理页可选下载 / 删除；删除后网盘里确实没了；UC 下载给出明确提示）。
================================================================================

================================================================================
【发布准备 JYD-RELEASE-2026-10-04 · 0.1.0 发布准备（不发布，仅准备）】
来源：Owner 指令——0.1 功能面已齐（P0 安全边界 / P1 三项 / B2+C2 / 网盘管理页均验收通过），
      做发布准备，**但不推送 Release**，Owner 确认后手动发。
本批落地的改动：
· app/build.gradle.kts：版本号已是要求值（`versionCode = 1` / `versionName = "0.1.0"`），本批未改；
  `release` 构建类型已挂固定签名（`SIGN_KEYSTORE_PATH` 存在时用 `fixed`，否则回退 debug 签名）。
· .github/workflows/build.yml：新增**手动触发构建类型**（`workflow_dispatch` 输入 `build_type`，
  可选 debug / release，默认 debug）。debug 走产物 `jieyundu-debug-apk`，
  release 走 `jieyundu-release-apk`；push / PR 行为不变（固定 debug）。
· .gitignore：新增 `*.jks` / `*.keystore` / `*.p12` / `*.pem` / `signing.properties` /
  `keystore.properties` 忽略规则（密钥绝不入库的护栏）。
· README.md：更新为当前版本 0.1、夸克 / UC 已支持、百度 / 迅雷开发中、
  安装方式（GitHub Releases 下载）、系统要求、已知限制（UC 个人文件下载暂不支持等）。
· docs/ReleaseNotes_0.1.0.md（新增）：Release notes 草稿（支持网盘 / 主要功能 / 已知问题 /
  系统要求 / 安装说明），供 Owner 确认后粘贴到 GitHub Release。
安全事项（诚实登记 · 风险台账）：
· 本批按 Owner 指令**未更换签名口令**，也**未轮换 keystore**。
· 事实：`DSH_交接文档.md` 中记录了 keystore 明文口令，该文件在**公开仓库**的 git 历史中；
  仓库为 Public，故该口令在 git 历史中可见。
· 仓库中**不含任何 keystore 文件**（`.jks` / `.keystore` / `.p12` 均未入库），已核查确认；
  即：拿到口令并不等于拿到密钥，但口令本身已公开。
· Owner 裁决（原话「不需要换口令，千万不要换口令」）：保持现状，**不轮换**。
  本批仅补上 `.gitignore` 护栏，避免将来把密钥文件本身误提交。
· 影响面提示（供 Owner 日后决策，不构成本批动作）：签名密钥轮换会使新旧包签名不同，
  已安装用户需卸载重装（丢登录态）；首个公开版发布前是成本最低的轮换时机。
约束遵守：未改任何源码逻辑；未改版本号取值（本就符合要求）；未推送任何 Release；
未新增任何含口令 / 密钥的文件。
待办（Owner 手动）：确认 Release notes 与 README 后，在 GitHub 创建 Release 并上传 release APK。
================================================================================

================================================================================
【实现记录 JYD-BAIDU-2026-10-05 · B3-1 百度网盘：分享解析与浏览】
来源：Owner 指令「抓包事实.md 已升级为详细版，准备开始百度构建」（发布 0.1.0 之后新一批）。
依据：本机《抓包事实.md》（**未入库**，含厂商凭据）§1 / §3 / §6.2 / §11.3；
      全程遵守 R3——只实现有抓包证据的端点与参数，未推测任何字段。
本批范围（B3-1）：**分享链接解析 + 分享目录逐级展开**。
  下载链路（转存 → 高速直链 → 下载 → 清理）**不在本批**，见下方「下一批」。
本批落地的改动：
· domain/parser/baidu/BaiduApi.kt（新增）
    - 仅声明两个已有证据的端点：
      `POST share/verify`（提取码校验，换 `randsk`；`Referer` 含分享短码，故用参数动态传）
      与 `GET rest/2.0/xpan/share?method=list`（分享列表）；
    - 请求体 / 查询参数逐字段对齐 §11.3 #2 / #3；`root` 常量显式区分
      `ROOT_TOP_LEVEL = 1` / `ROOT_SUB_DIRECTORY = 0`（文档明确警告「容易写反」）；
    - `sekey` 用 `@Query(encoded = true)`：`randsk` 本身已是 URL 编码形态，避免二次编码；
    - 响应模型：`errno` 成功判定（百度用 `errno == 0`，**不是** code）；`isdir` 用 `JsonElement`
      承载（抓包为字符串 "1"/"0"，兼容数字形态，避免类型漂移导致序列化异常）。
· domain/parser/baidu/BaiduParser.kt（由占位骨架改为实现）
    - 实现 `NetdiskParser`（`match` / `parse`）与 `ShareBrowser`（`listChildren`）；
    - 短码归一化直接复用 `LinkExtractor.extractShareId`（**其内部已实现百度去前导 `1`**，
      见 §1：`/s/1xxxx` 的真实短码不含 `1`）；
    - 目录以**路径**为 id（§11.3 #3：目录项用 `path`、文件项用 `fs_id`）；
    - 异常处理沿用 UC 同款：`CancellationException` 原样抛出（C3）、`IOException` → 网络码、
      `SerializationException` → 协议码；
    - **未带提取码时的失败归类**：百度「需要提取码 / 码错误」的 `errno` 取值现有抓包未覆盖，
      故沿用 UC 的「按请求上下文归类」策略（未带码→提示输入；已带码→判为码错误），
      并标 `TODO(用户抓包)`，**不猜测 errno 数值**。
· data/remote/UserAgentProvider.kt
    - 补百度**两套 UA**（§3）：`baiduWebUserAgent`（Chrome 124）与
      `baiduNetdiskUserAgent`（`netdisk;12.24.6;…`）；原空串字段 `baiduUserAgent`
      取值改为客户端 UA（直链下载用），保持既有引用点不变。
· di/NetworkModule.kt
    - 新增百度 BaseUrl 与 `provideBaiduApi`；
    - **UA 选择升级为「host + 路径」二维**：百度在同一个 host 上混用两套 UA，
      故按「CDN 主机 `d.pcs.baidu.com` 或客户端接口前缀（`/api/list`、`/api/filemanager`、
      `/api/create`、`/api/quota`）」判定客户端 UA，其余走网页 UA；同时支持方法级
      `@Headers` 显式指定 UA 时不再被覆盖（保留扩展点）；
    - Cookie 域新增 `baidu.com`（注入 + 响应采集）——`share/verify` 下发的
      `BDCLND` 由此自动登记，子目录列表（`root=0`）必须带它才不会 `errno=2`（§6.2 坑①）。
· di/AppModule.kt：把 `BaiduParser` 以 `ShareBrowser` 身份多绑定注册（首页文件夹展开用）。
· ui/screens/home/HomeUiState.kt：补 `BAIDU_*` 错误码 → 文案映射（链接无效 / 详情失败 /
  提取码错误 / 网络 / 协议），使百度解析失败的提示与夸克、UC 一致。
· app/src/test/java/com/jieyundu/app/domain/util/BaiduLinkTest.kt（新增 7 例）
    - 锁死百度短码规则：`/s/1aBcDeF123` → `aBcDeF123`（去前导 `1`）、下划线/连字符、
      类型判定、`?pwd=` 与中文关键字取码、非百度链接不被归一化、空文本无链接。
下一批（B3-2，待 Owner 确认后做）：转存 `share/transfer`（§11.3 #6）→
  高速直链 `locatedownload`（#7，抓包常量为设备指纹参数）→ 复用现有下载引擎 →
  下载完成后按 `TempFolderGuard` 安全边界清理转存副本；另含百度个人网盘浏览/管理。
约束遵守：未改 UI 视觉；未动既有夸克 / UC 逻辑；未推测任何接口参数（R3）；
  中文入 strings.xml（本批未新增文案，复用既有映射）；日志走 Timber（C8）；
  《抓包事实.md》仍在 .gitignore 内，**未入库**，其内容只转化为协议常量与字段名。
待办：装机复验——粘贴百度分享链接（含/不含提取码）→ 解析出文件列表 → 展开子目录。
================================================================================

================================================================================
【实现记录 JYD-BAIDU-ERRNO-2026-10-05 · 百度 errno 两阶段判定修正 + 服务端消息透传】
来源：Owner 交付百度 / UC 的 errno 语义对照（来自其参考实现），指出 B3-1 里
      百度失败判定用了 UC 风格（按请求上下文归类）——**两家风格不可抄混**。
修正前（B3-1 首版，错误做法）：
· 未带提取码 + 列分享失败 → 一律提示「需要提取码」；已带码失败 → 判为「提取码错误」。
· 问题：把 errno=2（子目录缺 BDCLND）与 -6（未登录）都误当成提取码问题，
  用户会白找密码；且 -12 这个真正的「提取码错误」专属码未被识别。
本批落地的改动：
· domain/parser/baidu/BaiduErrnoRules.kt（新增，纯逻辑可单测）——按两阶段建规则：
    - **阶段一 `share/verify`**：`errno == -12` → 提取码错误（专属码）；
      403 → 分享已失效；31066 → 文件不存在；其余非 0 → 提取码校验失败（不冒充 -12）。
    - **阶段二 `xpan/share?method=list`（组合判定，无专属码）**：
      `sekey` 为空且 `errno != 0` 才算「需要提取码」；其中
      `errno == -6` → 「需要提取码**或**需要登录」（避免用户白找密码）；
      `errno == 2` 且为**子目录** → 「子目录认证失败」（**绝不算提取码问题**）；
      403 → 分享已失效；31066 → 文件不存在；已带 sekey 仍失败 → 列表获取失败。
· domain/parser/baidu/BaiduParser.kt
    - 接入上述规则；「需要提取码」分支返回 `ParseResult.NeedPassword`（交 UI 弹输入），
      其余失败返回带 code 的 `ParseResult.Error`；
    - **服务端消息透传**：失败详情优先取 `err_msg` / `show_msg`，并附 errno，
      形如 `服务端原文 (errno=-12)`（括号用 ASCII，符合 C5 不得出现中文字面量）；
    - 子目录列表失败按分类结果记日志（`reason=BAIDU_SUB_DIR_AUTH_FAILED errno=2`），
      不再让「errno=2」看起来像密码问题。
· domain/parser/baidu/BaiduApi.kt：响应体补 `err_msg` / `show_msg` 字段（透传来源）。
· ui/screens/home/HomeUiState.kt + res/values/strings.xml：新增 5 条百度语义文案映射
  （提取码校验失败 / 需要提取码或登录 / 子目录认证失败 / 分享已失效 / 文件不存在）；
  `-12` 复用既有「提取码错误，请重试」。
· ui/screens/home/components/ParseResultCard.kt：**打开服务端消息透传通道**——
  此前 UI 只按 code 映射文案、`ParseResult.Error.message` 被丢弃；现在当 message 与 code
  不同（即服务端原文）时另起一行展示，用户可见精确原因。
· app/src/test/.../baidu/BaiduErrnoRulesTest.kt（新增 11 例）：-12/-6/2/403/31066、
  组合判定（无 sekey 才算需要提取码）、已带 sekey 失败不得提示提取码、
  子目录 errno=2 必须归为认证失败、公共分享无 sekey 直接成功。
实现中的一处自纠（已修）：首版把 verify 失败结果暂存在 `companion object` 的
  `@Volatile var` 上——解析器是 `@Singleton`，那是**单例上的全局可变状态**，
  两次并发解析会互相串错误。改为局部 `VerifyOutcome` 返回值，解析器保持无状态。
UC 侧未改动：Owner 明确 UC 是「status != 200 + 透传 message」风格、无「需要提取码」专属码，
  与百度不是一回事；UC 现有实现已被验收，本批不碰（对齐其透传风格可作为后续单独清理项）。
约束遵守：未改 UI 视觉（仅错误卡多一行服务端原文）；未动夸克 / UC 逻辑；
  未推测任何 errno 数值（规则全部来自 Owner 提供的事实）；中文入 strings.xml（C5）；
  日志走 Timber（C8）。《抓包事实.md》仍未入库。
待办：装机复验——① 公共分享（无码）解析成功；② 加密分享不带码 → 提示输入提取码；
  ③ 密码错误 → 「提取码错误，请重试」+ 服务端原文与 `(errno=-12)`；
  ④ 未登录解析加密分享 → 提示「需要提取码，或需要登录百度网盘」。
================================================================================

================================================================================
【实现记录 JYD-BAIDU-COOKIE-2026-10-05 · 百度 Cookie 无法保存 / 无法自动保存（修复）】
来源：Owner 反馈——「无法保存百度饼干，也没办法自动保存」。
排查结论（两个真实原因，均在登录链路，与 B3-1 解析无关）：
根因①（主因）**百度没有登录校验端点** → Cookie 永远不被落库。
· `LoginValidator.validationUrlOf(BAIDU)` 原为 `null`（注释「待阶段 9 实现时接入」），
  而 `validate()` 在 `url == null` 时**直接返回 false**；
· `NetdiskLoginViewModel.submit()` 的落库条件是 `if (passed) { cookieStore.save(...) }`
  → 自动保存（轮询 BDUSS 出现即提交）与手动保存、粘贴 Cookie 全部被这道校验拦死，
  表现为「字段明明识别到了（`BDUSS` 判定已通过）却始终存不进」，且**没有任何提示**。
根因② WebView 登录页 UA 写死为夸克网页 UA。
· `NetdiskLoginViewModel.webUserAgent` 直接返回 `userAgentProvider.quarkWebUserAgent`，
  与网盘类型无关 → 百度登录页以**夸克 UA** 打开（登录链路可能异常、Cookie 指纹不一致）。
本批落地的改动：
· domain/login/LoginValidator.kt
    - `validationUrlOf(BAIDU)` 接入**只读探测**端点
      `GET https://pan.baidu.com/api/gettemplatevariable?clienttype=0&app_id=250528&web=1&fields=%5B%22bdstoken%22%5D`
      （《抓包事实.md》§11.3 #1：已登录返回 `result.bdstoken` / `result.username`，未登录不带）；
      fields 的方括号与引号按 URL 规范百分号编码；
    - 新增 `markerOf(type)`：百度判据为 `"bdstoken"`，夸克 / UC 维持既有 `"data"`；
    - 新增 `userAgentOf(type)`：百度用**网页 UA**（§11.3 #1 要求 Cookie + UA_WEB）；
      夸克 / UC **刻意保持既有取值**（两家登录链路已验收，避免回归）；
    - `validationUrlOf` / `markerOf` / `userAgentOf` 由 private 提升为 internal，便于单测锁定
      （防止将来又把百度端点摘掉）。
· data/remote/UserAgentProvider.kt：新增 `webUserAgentOf(type)` 作为「按网盘取网页 UA」的
  **单一来源**（夸克 / UC / 百度）；迅雷尚未抓包 → 返回空串（WebView 保持默认 UA，
  不拿别家 UA 顶替）。
· ui/screens/login/NetdiskLoginViewModel.kt：`webUserAgent` 属性改为
  `webUserAgentFor(type)` 方法，按类型取 UA。
· ui/screens/login/WebViewLoginScreen.kt：登录 WebView 改为传
  `viewModel.webUserAgentFor(type)`。
· domain/login/CookieExtractor.kt：百度的 Cookie 采集 URL 由仅 `pan.baidu.com`
  扩为 `pan.baidu.com` + `yun.baidu.com`（列表 / 配额接口走 yun 子域，避免漏采）。
· app/src/test/.../login/LoginValidationConfigTest.kt（新增 7 例）
    - **回归锁定**：百度必须有校验端点、且以 `bdstoken` 探测、带 `app_id=250528`；
      夸克 / UC 端点不变；迅雷无 Cookie 校验端点；
    - 判据：百度 `"bdstoken"`、其他 `"data"`；
    - UA：百度校验用网页 UA（且不得含 `quark-cloud-drive`）；WebView UA 按类型区分。
说明：不改「Cookie 出现 ≠ 登录成功」的两级校验设计——百度现在有了只读探测端点，
  校验语义保持完整；本批只是把**缺失的端点补上**，不是绕过校验。
约束遵守：未改 UI 视觉；未动夸克 / UC 登录行为（端点、判据、UA 均保持原值）；
  中文入 strings.xml（本批无新增文案）；日志走 Timber（C8）。
待办：装机复验——① 百度页登录后应**自动保存**并切回网盘列表（登录态标记为已登录）；
  ② 「保存登录态」按钮应成功；③ 「粘贴 Cookie」应成功；④ 重启 App 后百度仍为已登录。
================================================================================

================================================================================
【实现记录 JYD-DEBT1-2026-10-05 · 债批次 1（并发真 bug + 去重 + 死代码 + 引擎单测）】
来源：Owner 询问「是不是有一堆屎山代码，下次赶紧优化」→ 我用实测数据体检后排出债批次，
      Owner 选定「先做债批次（推荐）」。
体检结论（数据说话，非主观评价）：
· 架构本身分层清晰（domain / data / ui + 端口 + DI），近期 bug（百度 Cookie）属**缺功能**
  而非烂代码；但确实积了可量化债：并发缺陷 1 处、重复 2 类、0 引用死代码 2 个文件、
  最大文件（引擎 ~1000 行）零单测。
本批落地：
一、修 `DownloadService` **多任务并发真 bug**（功能缺陷，非风格问题）
    - 原实现只观察「最近一次启动的任务」，且**任一任务终态**就 `stopSelf` + 释放唤醒锁
      → 当「同时下载任务数 > 1」时，第一个任务下完会把前台保活与唤醒锁一起收掉，
      其余任务在后台裸奔；通知也只反映一个任务（Owner 验收 C2 时为单任务，故未暴露）。
    - 现在：`observeJobs`（taskId → 协程）+ `taskNames` 跟踪**多个**任务；
      **全部任务终态后才**撤前台 / `stopSelf` / 释放唤醒锁；收尾幂等（防取消自身触发的
      `finally` 重复收尾）；每次 `startForegroundService` 都刷新前台（满足系统 5 秒时限）。
    - 通知改为**聚合**：显示最近进展的任务，多任务时附「另有 N 个任务」
      （新增文案 `notification_download_more_tasks`；暂停 / 失败不撤通知，避免抹掉其它任务进度）。
二、抽 `ui/screens/download/DownloadLauncher.kt`（**消除我上批引入的重复**）
    - 首页下载与网盘管理页下载各持一份「解析落盘目录 → 建任务 → 登记会话 → 投递引擎 →
      启动前台服务 → 等待完成 → 发布公共目录」，约 70 行 × 2；现收敛为单一实现，
      两处只表达差异（URL / 文件名 / 大小 / 请求头）。
    - `HomeViewModel` 因此**去掉 5 个已死的注入依赖**（downloadEngine / downloadSessionRegistry /
      appSettingsStore / publicDownloadsPublisher / appContext），依赖面显著收窄；
      `NetdiskBrowserViewModel` 构造函数由 6 个依赖收敛为 2 个（netdiskRouter + launcher）。
    - 顺带清理两文件重构后产生的未用导入。
三、清死代码
    - **删除 `UcTaskPoller.kt`**（全项目 0 引用；UC 自【修订 JYD-UC-FIX-2026-10-03】改直连取链后
      转存链路整体闲置）。
    - `UcTempFolderManager.kt` **未删**（Owner 未拍板）：改为在类 KDoc 顶部标注「已无生产调用方、
      降级闲置、去留待 Owner 一句话」，避免下一个人误以为它还在用。
四、补 `DownloadEngineTest`（**引擎长期零单测**，本批补 5 例）
    - 用**假 OkHttp 响应拦截器**（不触网、不引 MockWebServer）驱动真实引擎路径；
    - 用例：① 未知大小完成 + **任务名写入存档**（P1-1 回归）；② **进程重启后续传**——
      预置 `.part0`=「AB」，断言实际发出 `Range: bytes=2-` 且成品为「ABC」（**不重下已有部分**）；
      ③ 无分片 → `MissingPartFiles`（提示重新下载）；④ 无存档 → `NoCheckpoint`；
      ⑤ `restart()` 清空分片 → 断言 `Range: bytes=0-` 且成品不含旧内容（**真从头下**）。
    - 关键点：**用 Range 请求头作为「是否真续传」的直接证据**，而不是只看状态。
过程中的一处自纠（已修）：HomeViewModel 改为调用 `downloadLauncher` 后，我漏了把
  `downloadLauncher` 加进其构造函数、且 `NetdiskBrowserViewModel` 漏了 import——推送前自查
  时发现并补上（否则直接编译失败）。
约束遵守：未改 UI 视觉；未改引擎下载核心逻辑（仅补测试与复用入口）；未动夸克 / UC / 百度
  协议实现；中文入 strings.xml（C5）；日志走 Timber（C8）。
待办：① Owner 对 `UcTempFolderManager` 去留给一句话；② 装机复验多任务并发（设「同时下载任务数」
  ≥2，两个任务同时下，确认通知聚合显示且前台保活不中断）；③ 债批次 2 候选：两个临时目录管理器
  合并（约 72% 重复）、`DownloadEngine` 拆分文件（~1000 行）。
================================================================================

================================================================================
【实现记录 JYD-DEBT2-2026-10-07 · 债批次 2（死代码删除 + 引擎契约拆分 + 卫生清理）】
来源：Owner 拍板「先做债批次 2」，并要求用「最少代码解决真问题」的口径总体审视代码。
一、删死代码（= 债批次 2 候选里的「两个临时目录管理器合并」）
    · 删除 domain/transfer/UcTempFolderManager.kt（340 行，全项目 0 生产调用方）。
      依据：UC 自【修订 JYD-UC-FIX-2026-10-03】改直连取链免转存，
      UcShareTransfer.cleanupAfterDownload 已是空操作；转存链路整体闲置
      （UcTaskPoller 已于 JYD-DEBT1 删除）。将来若恢复 UC 转存，从版本历史取回。
    · 临时目录机制因此只剩夸克一份实现（TempFolderManager）；
      TempFolderGuard 的 KDoc 里「与 TempFolderManager / UcTempFolderManager 保持一致」
      同步改为只提 TempFolderManager。
二、拆 DownloadEngine（1019 行 → 865 行）
    · 新增同包 domain/downloader/DownloadContracts.kt（对外契约面）：
      端口 DownloadProgressPort / DownloadCheckpointPort / DownloadSettingsPort +
      值类型 DownloadTaskRecord / EngineActionResult。
    · 引擎文件只留实现：任务生命周期、调度闸门、分片请求与重试、进度/测速发布、
      内部类 SpeedLimiter 与 TaskRuntime。
    · 拆分方式为**同包搬迁**：包名、类名、方法签名、构造参数全不变 →
      所有调用方（DownloadDao / AppSettingsStore / DownloadViewModel / 两个测试）
      import 与行为零改动，本批无任何逻辑改动。
    · 规格登记：《要求.md》§7.5 的注「端口定义（嵌套于 DownloadEngine.kt 内，不新增文件）」
      已同步修订为「置于同包 DownloadContracts.kt」，并在文末新增
      【规格变更 JYD-DEBT2-2026-10-07】（双登记）。
三、本批未改、登记为债批次 3 候选（有证据，不猜）
    · 个人网盘列表参数构造重复三处：QuarkParser.buildPersonalListParams、
      UcParser.buildPersonalListParams、TempFolderManager.buildListParams——三者同构
      （仅 pr/fr 常量与文件头不同，约 30 行 × 3）。因涉及**已验收的协议参数构造**，
      为不碰已验证链路，本批不动；若要收，可抽一个 (pr, fr, pdirFid) 共享构造器。
四、卫生清理（Owner 已批准）
    · 删除源码树内残留的编辑器临时目录
      （app/src/main/java/com/jieyundu/app/domain/util/.DownloadTimeFormatter.kt.<pid>.<uuid>.tmpdir/，
      2 个重复文件，未被 .gitignore 覆盖，误提交会污染仓库）。
      根因：本机 DSH 文件写入后端会在目标旁留下 .l2s.*.tmpdir 与悬空符号链接，
      本次交付期间复现过一次（DownloadContracts.kt 曾落成目录），已就地修复为真实文件。
    · 补齐 28 个文件的末尾换行，使本地工作区与 GitHub HEAD 逐字节一致（原差异仅为 EOF 换行）。
五、约束遵守
    · 未改下载核心逻辑与续传语义；未动夸克 / UC / 百度协议实现；未改 UI 与视觉；
      未新增依赖；本批无新增中文文案（C5 不适用）；日志仍走 Timber（C8）。
六、推送 / CI
    · 已推送：commit e066e2dd（走 GitHub Data API，沿用既定推送约定）。
    · CI：run 37595744647 **success**（build debug + 上传产物 + `testDebugUnitTest` 全部通过）。
待办：① Owner 装机复验（本批为纯重构 + 删死代码，预期行为零变化）；
      ② 债批次 3 是否开工（列表参数去重）；
      ③ 推送通道（GitHub 凭据）确认。
================================================================================

================================================================================
【实现记录 JYD-DEBT3-2026-10-07 · 债批次 2 续：把 1000 行级 DownloadEngine 拆到内聚单元】
来源：Owner 指令「里面有一个 1000 行之多的引擎文件，优化完整；先优化整个项目」。
上一批（JYD-DEBT2）已把「对外契约」拆到 DownloadContracts.kt，引擎仍余 865 行且混装
四类职责。本批按「一个文件一个职责」继续拆，**零行为改动、零调用方改动**（同包搬迁）。
拆分结果（行数为拆分后实测）：
    · domain/downloader/DownloadEngine.kt        661 行 —— 只剩引擎门面与调度：
      任务生命周期（start / pause / resume / restart / cancel）、并发调度闸门
      （schedule / launchTask / releaseSlotAndDispatchNext）、任务级重试与收尾、
      进度节流与实时表发布（publishProgress / publishLive / removeLive）、
      运行态准备（prepareRuntime）、重启续传（resumeFromCheckpoint）。
    · domain/downloader/ChunkDownloader.kt       126 行 —— 单分片下载：
      Range 断点构造、每轮重试按临时文件长度重算断点（BUG-4-01/02 语义不变）、
      追加落盘、全局限速配额申请；以 `onBytesRead` 回调把「本次运行累计字节」交回引擎
      （本类不持有进度状态、不落库）。
    · domain/downloader/DownloadTaskRuntime.kt    58 行 —— 运行态纯数据容器
      （状态流 / 进度流 / 分片 / 测速采样点）；原 private class TaskRuntime，
      改名 DownloadTaskRuntime 并放宽为同包 internal。
    · domain/downloader/DownloadSpeedLimiter.kt   57 行 —— 全局限速器
      （令牌桶 / 时间预约）；原 private class SpeedLimiter，放宽为同包 internal。
    · domain/downloader/DownloadContracts.kt     176 行 —— 端口与契约类型（前一批拆出，未动）。
    对照：拆分前 1019 行单文件 → 现在最大 661 行，四类职责各归其位。
不做的事（避免无收益重构）：
    · 未把「引擎门面」再细分为 Scheduler / Lifecycle 两个类：调度只有 3 个小方法、
      且与 start/pause 共享 scheduleMutex 与 runtimes 状态，拆开只会引入互相引用的
      两个类而没有收益；
    · 未新增任何接口 / 工厂 / 包装层：三个新类都是 `internal class`，由引擎直接 new。
    · 未改下载核心逻辑：分片、断点、续传、重试、限速、进度语义逐字保留。
规格登记：《要求.md》文末新增【修订 JYD-DEBT3-2026-10-07】。
约束遵守：未改协议实现（夸克 / UC / 百度）、未改 UI、未新增依赖、无新增文案（C5 不适用）、
          日志走 Timber（C8）、取消异常原样抛出（C3）。
待办：① 装机复验下载（大文件续传 / 暂停继续 / 多任务并发 / 限速档位）；
      ② 未编译验证前不得视为已交付（本批靠 CI 验证）。
================================================================================

================================================================================
【实现记录 JYD-DEBT4-2026-10-07 · 架构拆分 A 段：wire 数据结构与 Retrofit 接口分家】
来源：Owner 目标「全项目优化，把所有架构全部拆分，做成一个清晰、明确的整体应用」。
问题：QuarkApi.kt（436 行）/ UcApi.kt（498 行）把「Retrofit 端点声明」与「16-17 个
      @Serializable 请求/响应数据结构」混在一个文件里——读接口要先翻过 250 行 DTO，
      改 DTO 又要穿过接口 KDoc，两件事互相干扰。
本批改动（**纯声明搬迁，同包，零调用方改动**）：
    · quark/QuarkApi.kt   436 → 181 行：只剩 `interface QuarkApi` 与各端点 KDoc。
    · quark/QuarkModels.kt  新增 264 行：17 个 wire 结构（QuarkResponse / QuarkFile /
      QuarkSaveRequest / QuarkDeleteResult …），按原顺序原样搬迁，KDoc 逐字保留。
    · uc/UcApi.kt         498 → 194 行：只剩 `interface UcApi`。
    · uc/UcModels.kt      新增 314 行：17 个 wire 结构 + 分享详情查询构造器
      `UcTransferDetailQuery`（同源化令牌那次新增的共享构造器，属「构造请求参数」，
      与 DTO 同类，一并归位）。
    · 两个 Api 文件的 R7 四行头「职责」改为只描述端点，并注明数据结构见同包 Models 文件。
校验：两包共 36 个顶层声明，逐名核对**无重复、无遗漏**；Api 文件内 `@Serializable` 计数为 0。
约束：未改任何端点路径 / 参数 / 字段名（协议零改动）；未新增依赖；未改 UI。
待办：架构拆分后续段（B：解析器内部拆分；C：UI 屏幕拆分）另行登记。
================================================================================

================================================================================
【实现记录 JYD-DEBT5-2026-10-07 · 债批次 4：死代码清除（domain/data/service 全线，-357 行）】
来源：Owner 目标「全项目优化，把架构全部拆分清楚」+ domain/data 层 ponytail-audit 清单；
      每条删除均**先全库 grep 核实引用**（含 app/src/test），只删「零引用 ∧ 无行为影响」项。
一、UC 免转存后的整条备用链（-115 行）
    · UcApi 删 `saveShare` / `getTask` / `createFolder` 三个端点（免转存已验收、0 调用）；
    · UcModels 删随之失效的 5 个模型：UcTask / UcSaveAs / UcSaveRequest /
      UcCreateFolderRequest / UcCreateFolderResult（`UcSaveResult` 保留——deleteFiles 仍用它）；
    · 接口 KDoc 同步改为「不声明 save/task/createFolder；恢复 UC 转存时从版本历史取回」。
二、零引用成员（-40 行）
    · UserAgentProvider：`baiduUserAgent`（`baiduNetdiskUserAgent` 的别名，0 读取）、
      `ucGuestUserAgent`（游客 UA，0 读取）；
    · FileInfo.`isDirectLinkReady`、ShareLink.`hasPassword`、QuotaInfo.`usedInTrash`（连带 KDoc）；
    · TransferRecordDao.`deleteAll`、AppSettingsStore.`defaultDirectoryLabelRes`（连带
      `androidx.annotation.StringRes` 导入）、ParserRegistry.`findParser(url)`。
三、可见性收窄（对外零调用，仅同类内部使用）
    · TempFolderGuard.`isInTempFolder`、TempFolderManager.`pendingCleanupFids` / `remove`、
      NotificationHelper 的两参 `notify` 重载 → 全部改 `private`。
四、删不可达与重复逻辑
    · LinkExtractor：删 `TRAILING_PUNCTUATION` + `trimTrailingPunctuation`（-25 行）——
      四条 URL 正则的字符类均为 `[A-Za-z0-9_\-]`，不可能匹配到 `。）]、},"` 等标点，
      该分支恒不可达；`extractAll` 直接用 `match.value`。
    · ChunkManager：抽出私有谓词 `partFilesOf()`，`hasPartFiles` 与 `deleteAllPartFiles`
      不再各写一份「前缀 + 其后全为数字」扫描（-10 行，行为不变）。
五、删零引用文件（**规格残留，已双登记**）
    · `ui/glass/GlassDivider.kt`：0 个 Composable 调用（同名颜色常量 JieYunDuColors.GlassDivider
      是另一回事，保留）。
    · `domain/model/DownloadEntry.kt`：被 `data/local/DownloadEntity.kt` 取代，0 代码引用；
      `DownloadTask` 的 KDoc 引用已改指 DownloadEntity。
六、本批**未动**（需 Owner 拍板，见待办）
    · 收藏 / 历史整条链路（2 Repository + 2 DAO + 2 Entity + 2 表）：确认为 0 注入点，
      但《要求.md》4.3 明确要求「下载历史 / 搜索 / 收藏」——属**未接线功能**而非死代码，
      且删除需 bump 数据库版本 + 迁移，故不动，等 Owner 一句话。
    · `DownloadRepository`（纯转发包装，仅 DownloadViewModel 一个调用方）：《要求.md》6
      将其列为数据层交付物，保留；若要精简请 Owner 拍板。
    · `QuarkShareDetail.list` / `UcTransferShareDetail.file_list` 兼容兜底分支：无抓包证据
      但属防御式解析，不在本批删除范围（避免动已验证解析链路）。
七、校验
    · 推送前自检脚本（括号平衡 + 孤儿注释行 + KDoc 后无声明）全库通过：114 个 .kt、0 问题；
    · 被删符号全库反查：0 残留引用。
推送 / CI：commit 7dca683c；CI run 37760826112 **失败**——`UserAgentProvider.kt` 与
    `ParserRegistry.kt` 被删除脚本截断（脚本对「无括号的单行声明」定位错误，把声明之后的
    内容一并删掉）。已从上一提交 abd9a0fd 取回原文并用**精确字符串替换**重做这两处删除，
    与 D6 同批推送修复；同时加固推送前自检口径（后续所有删除一律用精确字符串 + 反查）。
待办：① 装机复验（本批为纯删除 / 可见性收窄，预期行为零变化）；
      ② Owner 拍板收藏 / 历史链路与 DownloadRepository。
================================================================================

================================================================================
【实现记录 JYD-DEBT6-2026-10-07 · 债批次 5：UI 层扁平化与死代码清除（-约 300 行）】
来源：Owner 目标「把架构全部拆分清楚」+ UI 层 ponytail-audit 清单（逐条 grep 核实）。
一、导航层扁平化（删 2 个纯转发 wrapper）
    · 删除 `ui/navigation/NavigationRail.kt` / `NavigationBar.kt`：两者各自只做
      「改 orientation + 加内边距」后转调 `TopGlassNavBar`，各仅 1 个调用方。
      `JieYunDuNavHost` 现在直接调用 `TopGlassNavBar` 并显式给出方向与内边距。
二、删「只有一个实例」的弹簧预设抽象
    · 删除 `NavSpringPreset` 数据类与 `JellyPreset` 单例（KDoc 自述为「阶段 11 预留扩展点」），
      以及贯穿 `NavHost → TopGlassNavBar` 的 `preset` 形参；
      弹簧值改为 `TopGlassNavBar.kt` 内 8 个私有常量（调手感仍只需改一处，符合 10.3）。
三、删无效绘制与死常量
    · `GlassPanel` 删除 `drawRefraction()` 及其调用：它用的两个颜色（`GlassRefractionPurple/Blue`）
      早已被置为 `Color(0x00000000)`，整段渐变完全透明、零绘制效果；两个颜色常量一并删除。
    · `Dimens` 删 6 个零引用常量：IconButtonSize / GlassBlurRadius / GlassBlurRadiusCompact /
      DownloadButtonSize / ContentTopPadding / FilterBarHeight。
    · `strings.xml` 删 7 条零引用文案：parse_result_empty、cd_open_settings、cd_download_file、
      download_speed_pair_format、settings_default_chunk_value、webview_login_success、cd_close_login。
      （注：cd_* 三条本意是 contentDescription，当前手绘图标无 a11y 标注 —— 已记入「缺失项」，
      不保留死资源。）
四、组件 API 精简
    · `GlassChip` 删除 `labelRes` 重载（唯一调用方改为 `label = stringResource(...)`）、
      删除从不被传值的 `horizontalPadding` 形参（内边距固定 `Dimens.SpaceLg`）。
    · `HomeUiState` 删除零引用派生属性 `canParse`。
五、接通一处「写好了没人调用」的功能（真实缺口，非删除）
    · `SettingsViewModel.refreshBatteryOptimizationStatus()` 原先 0 调用，KDoc 自述「系统设置页
      返回后调用」——C2 第 1 条的白名单状态因此**永远不会刷新**。现于 `SettingsScreen`
      用 `LocalLifecycleOwner` + `LifecycleEventObserver(ON_RESUME)` 真正接通：
      从系统电池优化页返回即重新读取真实状态。
六、不采纳的审计建议（与规格冲突）
    · 「删 GlassCard 的可点击 + 按压缩放分支」：9.6.3 明确要求「卡片整体可点击，
      点击时缩放 0.98」，属规格行为，保留。
    · 「把 45 个单次使用的手绘图标几何常量内联」：可读性损失大于收益，保留命名常量。
七、校验
    · 推送前自检（括号平衡 / 孤儿注释 / KDoc 后无声明）：112 个 .kt、0 问题；
    · 被删符号全库反查：`NavSpringPreset` / `JellyPreset` / `GlassRefraction*` /
      `horizontalPadding` / `Dimens.<六常量>` / `canParse` / `NavigationRail` / `NavigationBar(`
      在 .kt 中仅剩本批的说明性 KDoc 提及。
待办：① 装机复验（导航 Q 弹手感、设置页白名单刷新、筛选条与胶囊外观应无变化）；
      ② 手绘图标的 contentDescription（a11y）列入缺失项清单；
      ③ 引擎之外的大文件拆分（SettingsScreen 445 行单函数、DownloadItem 手绘图标外移）
      作为下一段（D7）执行。
================================================================================

================================================================================
【实现记录 JYD-DEBT7-2026-10-07 · 债批次 6：两个最大 UI 文件的职责拆分】
来源：Owner 目标「把所有架构全部拆分，做成一个清晰、明确的整体应用」+ UI 层审计
      「职责混杂该拆」的两条判定（SettingsScreen 445 行单函数、DownloadItem 混装图标）。
一、设置页：606 行单文件 → 247 行编排 + 5 个组件文件（新增 ui/screens/settings/components/）
    · `SettingsScreen.kt`（247 行）只做**编排**：ViewModel、生命周期（白名单刷新）、
      目录选择与权限启动器、Toast 反馈，然后按顺序调用 10 张卡。
    · `components/SettingsCard.kt`：卡片容器（原 private SettingsCard）+ **新增
      `SettingsOptionRow`** —— 把「分片数 / 同时任务数 / 限速 / 重试次数」四张卡里
      逐字重复的「说明 + 一排胶囊」结构收敛为一处实现（原 4 处各约 25 行，泛型 <T>
      同时容纳 Int 与 Long 档位）。
    · `components/DownloadSettingCards.kt`：支持范围卡 + 四张档位卡（各自订阅自己的
      StateFlow，不再由页面统一收集后逐层传递）。`BYTES_PER_MB` 随限速卡迁移。
    · `components/StorageCards.kt`：下载目录卡（权限流程由页面注入回调）+ 临时文件清理卡。
    · `components/BackgroundCard.kt`：后台与通知卡（C2）+ `openBatteryOptimizationSettings`
      （原为页面私有函数，随卡迁移并放宽为 internal）。
    · `components/LogAndAboutCards.kt`：崩溃/运行日志卡 + 关于卡。
    · 行为零变化：文案、控件、顺序、间距、回调语义逐字保留；各卡现在自己 collectAsState。
二、下载项：630 行 → 436 行 + `ui/icons/DownloadGlyphs.kt`（216 行）
    · 三个自绘图标（ShareGlyph / InstallGlyph / TrashGlyph）及 25 个几何常量自
      `DownloadItem.kt` 迁出到 `ui/icons/`（纯绘制、无业务状态），可见性 private → internal。
    · `DownloadItem.kt` 只剩「一条下载项长什么样」：行布局、进度、详情、暂停/继续、
      删除/分享/安装按钮与状态映射；连带清理 3 个因此未用的导入（Canvas/Offset/StrokeCap）。
三、校验
    · 推送前自检（括号平衡 / 孤儿注释 / KDoc 后无声明）：118 个 .kt、0 问题；
    · 逐个文件核对未用导入（本轮清掉 4 处）与跨文件符号引用（组件均 internal、同模块）。
待办：① 装机复验设置页（10 张卡与拆分前应完全一致：档位可点、目录可改、日志可导出、
      白名单状态从系统页返回后刷新）；② 下载项三个行尾图标（删除/分享/安装）外观与点击区域不变。
================================================================================

================================================================================
【实现记录 JYD-DEBT8-2026-10-07 · 债批次 7：个人网盘查询参数四处重复收敛为一处】
来源：Domain 层审计「个人网盘列表参数构造重复三处」（逐条 grep 核实；实施时又发现第 4 处）。
问题：`QuarkParser.buildPersonalListParams`、`UcParser.buildPersonalListParams`、
      `TempFolderManager.buildListParams` 三处产出的查询参数**字段完全相同**（仅 pr/fr 取值
      不同：ucpro/pc 与 UCBrowser/pc）；`TaskPoller` 还各自重写了一份 pr/fr 常量。
      协议参数一旦调整就要改四处，且极易只改一处造成漂移。
本批改动：
    · 新增 `domain/parser/PersonalListQuery.kt`：唯一构造器 `build(pr, fr, pdirFid)` +
      四个平台常量（QUARK_PR/QUARK_FR/UC_PR/UC_FR），字段集依《抓包事实.md》§10.2。
    · `QuarkParser` / `UcParser`：`buildPersonalListParams` 改为调用共享构造器；
      各自 companion 里的 pr/fr 常量删除，三处/两处引用改指共享常量。
    · `TempFolderManager`：`buildListParams` 改为调用共享构造器，并删除本地重复的
      pr/fr/分页/排序/键名共 17 个常量（原先只为该方法存在）。
    · `TaskPoller`：pr/fr 改指共享常量，删除本地两份重复常量。
    · 行为零变化：产出的 map 键值逐字段一致；协议、UI、数据库均未触动。
修复（D7 遗留）：CI run 37762739944 报 `StorageCards.kt:63 Unresolved reference:
    requestCustomDirectory`——拆分设置页时把页面局部 lambda 名带进了组件；已改为组件形参
    `onRequestDirectory`，并扫查其余四个组件文件确认无同类跨层引用。
校验：推送前自检 119 个 .kt / 0 问题；全库反查确认无「字符串里出现类名」的替换误伤
    （本轮曾把 `"QUARK_PROTOCOL_ERROR"` 误替换，已修复并列为检查项）。
待办：装机复验个人网盘浏览（夸克 / UC 列目录）与「临时文件清理」；
      确认协议错误码文案未变（QUARK_PROTOCOL_ERROR / UC_PROTOCOL_ERROR 原值不变）。
================================================================================
