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

## 待办 / 已知项
- 评审清单 §1、§2 同步（评审方执行）。
- 评审清单 §8 P0「构建日志」：**已具备**（run#6 全绿 + artifact `jieyundu-debug-apk` 可下载）。
- 《要求.md》§5 仍将 `skydoves/Cloudy 1.0.0-alpha01` 列为指定依赖，与当前实现（方案 A 移除）
  已不一致，**待评审方/Owner 出勘误登记**（开发方按铁律不改规格文件）。
- 夸克 UA 等 27 条 `// TODO(用户抓包):` 待抓包数据。
- R1 边界已在 README 如实披露。
