# 极云渡 · 开发交接文档（给 DeepSeek Harness）

> 生成时间：2026-10-04
> 来源：Operit 侧「开发方 AI」与 Owner 的对话进度快照
> 用途：把当前项目状态、铁律、任务台账、关键事实交给 DSH 侧继续推进。
> 阅读顺序建议：先看 §1 现状 → §2 铁律 → §3 任务台账 → §4 当前进行中(B1) → §5 关键事实 → §6 下一步。

---

## 1. 项目概述

| 项 | 值 |
|---|---|
| 项目名 | 极云渡（JieYunDu） |
| 定位 | Android 多网盘解析 / 下载 App（支持 UC、夸克，规划百度、迅雷） |
| 包名 | `com.jieyundu.app` |
| 仓库 | `https://github.com/qiuchen520/JieYunDu`（Public，默认分支 `main`） |
| 许可证 | AGPL-3.0 |
| SDK | minSdk 26 / targetSdk 34 / compileSdk 34 |
| 工作区根 | `/data/data/com.ai.assistance.operit/files/workspace/极云渡`（即本目录） |
| 角色约定 | 用户 = Owner（委托方 + 评审传话人）；AI = 开发方 |

---

## 2. 铁律（必须遵守）

### R1–R8（红线）
- **R3（最关键）**：不得编造接口参数。未获抓包证据前，**不改请求头 / 参数**。
- 其余红线围绕：流程合规、可追溯、不静默改需求等。

### D1–D15（禁止事项，节选重点）
- **D8**：不引入 `material-icons`。
- 不擅自扩大改动范围；不静默同步数据。

### C1–C10（编码风格，节选重点）
- **C3**：取消异常原样抛出。
- **C5**：禁止硬编码中文（文案走 `strings.xml`）。
- **C8**：日志统一走 Timber。
- **C9**：耗时任务调度到 IO。

### 交付流程铁律
1. **每批做完必须停下等 Owner 装机验收**，不自动进入下一批。
2. **一次只做一批**，不混推。
3. **本机不能构建**（JDK17/Gradle 8.2.1/Android SDK 在 `/root/Android`，但 build-tools 为 x86_64、设备 ARM → AAPT2 无法执行）→ **一律走云端 GitHub Actions CI**。
4. 终端环境**无 `git`** → 只能走 **GitHub API** 推送。
5. **验签可跳过**（Owner 原话：「不需要验签的了。我直接安装，如果安装不了，那就是签名不对」）。
6. 文档变更须**双登记**（CHANGELOG + 要求.md），标识可追溯。

---

## 3. 任务台账（批次总表）

| 批次 | 内容 | 状态 |
|---|---|---|
| A1 / UC 三修 | 浏览与取链同源（统一走 `transfer_share/detail`） | ✅ 已交付，**Owner 验证通过** |
| A2 | 下载详细信息 + 准备态文案（commit `f3801a04`） | ✅ 已出包，等 Owner 单独回执 |
| **B1** | **下载页速度恒 `--` 的 bug 修复** | ✅ 一轮已验收（速度/均速/剩余时间）→ 二轮整改中（JYD-DLSPEED2-2026-10-04） |
| B2 | 玻璃化改造：下载页 + 设置页（范围 C + 内容 abc） | ⏸ 待启动 |
| C2 | 后台保活与通知（`DownloadService` 挂空未接线） | ⏸ 待启动（与 B2 同推） |
| B3 | 百度网盘 | ⏸ 空壳 |
| B4 | 迅雷网盘 | ⏸ 空壳 |
| D1→D2→D4→D5→D6→D7 | 技术债，每批清 1–2 条 | ⏸ 排队 |

> Owner 排期总纲：先验 A1/A2 → 修 B1 → B2 → C2 与 B2 同推 → B3/B4 → D 系列按 D1→D2→D4→D5→D6→D7。

---

## 4. 当前进行中：B1（下载速度 `--` bug）

### 4.1 根因（已定论）
下载页列表数据源是 **Room**（`DownloadRepository.observeProgress()`），而：
- Room 仅在 `DownloadEngine` 的 **3 处** `downloadDao.upsert`（start / pause / complete）写入；
- 引擎的实时进度 `publishProgress()`（每 200ms 节流）**只发内存** `MutableStateFlow`，**既不落库、下载页也没订阅**；
- 且 `DownloadEntity.toProgress()` 的速度字段固定 `DownloadProgressState.UNKNOWN_SIZE`（**Room 不存速度字段**）。

⇒ 界面速度恒 `--`，进度条只显示启动/续传时的静态值。

### 4.2 Owner 批准的方案
引擎暴露**实时进度快照 Flow**，UI 层**订阅 + 与 Room 持久化数据合并**显示。

**三项要求：**
1. 下载中实时显示：当前速度 / 已下载·总量 / 剩余时间；
2. 暂停 / 完成显示最终状态；
3. **不为显示速度频繁写 Room**（性能）。

**三项约束：**
- 不动 UI 视觉；
- 不动下载引擎核心逻辑；
- 只加一个 `observeProgress` 通道 + UI 订阅。

**验收标准：** 下载一个文件，速度列**实时变化**，不再一直 `--`。

### 4.3 已落盘的改动（`DownloadEngine.kt`，**增量·未完成**）
1. 新增 `import kotlinx.coroutines.flow.StateFlow`；
2. 在 `runtimes` 旁新增：
   - 私有 `liveProgressFlow: MutableStateFlow<Map<String, DownloadProgressState>>`（内存态、不落库、节流刷新）；
   - 只读 `val liveProgress: StateFlow<Map<String, DownloadProgressState>>`（全量视图，与按任务订阅的 `observeProgress` 并存）；
3. `start()` 中 `downloadDao.upsert(runtime.progress.value)` **之后**插入 `publishLive(runtime.progress.value)`。

### 4.4 ⚠️ 当前工作区处于「不完整态」（重要）
- `publishLive()` **已在 `start()` 被调用，但方法本体尚未定义** → **此刻代码不可编译，禁止推送**。
- **必须补齐后才能提交。**

### 4.5 接入清单（✅ 已全部完成，2026-10-04 由 DSH 侧闭合）
- [x] 定义 `publishLive(progress)`：以「读当前值 + 生成新 Map + CAS 回写」写入，只改内存、不落库；
- [x] `publishProgress()`（每 200ms 节流处）接入 `publishLive` —— UI 实时速度的来源；
- [x] `pause()`、`runTask()` 完成 / 失败分支接入（显示最终态）；
- [x] `cancel()` 经 `removeLive(taskId)` 从 `liveProgressFlow` 移除该任务条目；
- [x] `resume()` / `start()` 重新加入（`resume()` 内部即调 `start()`）；
- [x] `DownloadViewModel` 订阅 `downloadEngine.liveProgress`（items 改为四路 `combine`，以 `mergeLiveProgress()` 用实时快照**覆盖活动态 / 终态**的 Room 数据，savePath 仍取 Room）；
- [x] 实现「**剩余时间**」展示：新增 `domain/util/DownloadTimeFormatter.kt`（`remainingSeconds` / `formatRemaining`）+ `strings.xml` 的 `download_size_remaining_format`，未知时显示 `--`；
- [x] 静态自查（引用一致：`download_size_format` 已无残留引用；新增 `kotlinx.coroutines.flow.update` 导入；新增文件无断链）；
- [x] 双登记文档（`JYD-DLSPEED-2026-10-04`，CHANGELOG + 要求.md + 本文件修订记录）；
- [x] 推送脚本 → 跑 CI → 出包落 `/sdcard/Download/` → 交付报告 → **停下等 Owner 验收**（本轮）。

### 4.6 本轮实际落盘改动（2026-10-04 · DSH 侧）
| 文件 | 改动 |
|---|---|
| `domain/downloader/DownloadEngine.kt` | `publishLive` / `removeLive` 定义 + 5 处接入（start / publishProgress / pause / 完成 / 失败）+ cancel 移除；新增 `flow.update` 导入 |
| `ui/screens/download/DownloadViewModel.kt` | items 改四路 `combine`；新增 `mergeLiveProgress()`（活动态 / 终态取实时快照，其余取 Room） |
| `domain/util/DownloadTimeFormatter.kt`（新增） | 剩余时间换算与格式化（`--` 兜底） |
| `ui/screens/download/components/DownloadItem.kt` | 详情行改「已下载 / 总量 · 剩余 X」；新增 `remainingBytes()` |
| `res/values/strings.xml` | `download_size_format` → `download_size_remaining_format` |
| `app/src/test/.../DownloadTimeFormatterTest.kt`（新增） | 8 个用例覆盖格式化与边界（CI 执行） |

> 实现口径与 `CHANGELOG.md` / `要求.md` 的 `JYD-DLSPEED-2026-10-04` 条目一致。

---

## 5. 关键事实与上下文（推进时直接复用）

### 5.1 数据模型
- `DownloadProgressState(taskId, state, downloadedBytes, totalBytes, speedBytesPerSecond, averageSpeedBytesPerSecond, chunkCount, completedChunks, savePath)`；`percent` 派生；`UNKNOWN_SIZE = -1`；`initial(taskId, chunkCount)`。
- `DownloadEngine`：瞬时速度 = 本区间字节差 ÷ 时长；均速 = 本运行累计字节 ÷ 本运行时长；`pause()`/`completed()` 速度置 0，`cancel()` 置 `UNKNOWN_SIZE`。
- `TaskRuntime`：`sessionBytes(AtomicLong)`、`initialBytes`、`lastEmitAt`、`lastSpeedSampleAt`、`lastSpeedSampleBytes`、`sessionStartAt`。
- `DownloadItem` 右上文本 = `download_speed_pair_format`（`%1$s ·均速 %2$s`）；下方 = `download_size_format`（`%1$s / %2$s`）。
- `FileSizeFormatter.format / formatSpeed`（`UNKNOWN="--"`，`Locale.US`）可复用。
- `strings.xml` 已有 `download_speed_pair_format`、`download_size_format`；**「剩余时间」暂无文案，需新增**。

### 5.2 提交链（最新）
- `f3801a04`：下载详情 + 准备态文案，CI `37169334597` ✅
- `3ccbce66`：UC 三修（浏览与取链同源），CI `37173004007` ✅，APK `/sdcard/Download/极云渡_UC同源修复_debug.apk`
- **B1 改动尚未提交（工作区增量，不可编译态）**

### 5.3 UC 三修根因（已定论、已验收）
`UcParser` 浏览列表走 `v2/detail`，与 `UcShareTransfer` 取链令牌的 `transfer_share/detail` **不同源** → `fid` 对不上 → 取不到 `share_fid_token` → 下载被静默跳过。
**修法 = 统一走 `transfer_share/detail`（同源）。** Owner 已装机验证通过。

### 5.4 签名（固定，勿丢）
- Keystore `jieyundu.jks`：PKCS12 / 别名 `jieyundu` / RSA-2048 / 10000 天 / 口令 `xN0CRewK9sEcsjKFlW4z8FdA`。
- 证书 SHA-256：`52:C3:CC:90:DB:3D:8B:7F:8E:93:D3:07:71:8E:06:2C:79:7B:82:37:55:80:28:DD:01:85:84:DA:85:AA:61:70`
- 备份四处：`/sdcard/Download/jieyundu.jks`、`/sdcard/Download/极云渡-签名密钥备份/jieyundu.jks`、私密仓库 `qiuchen520/NetDiskGo`→`keys/jieyundu.jks`、极云渡仓库 Secrets。

### 5.5 CI 与推送（工程实践）
- 工作流：push `main` 触发 + `workflow_dispatch`；checkout@v4 + setup-java@v4(temurin 17) + setup-gradle@v4(gradle 8.2.1) + 准备签名 keystore + `gradle assembleDebug --stacktrace` + 上传 artifact `jieyundu-debug-apk` + `gradle testDebugUnitTest`。
- 测试：`QuarkParserTest.kt` / `ChunkManagerTest.kt`。
- **无 `git`**：走 GitHub API（Git Data API：GET base commit → POST blobs → POST tree → POST commit → PATCH ref）。
- Token：取自 `/sdcard/Download/Operit/mcp_plugins/mcp_config.json` 的 `pluginMetadata.github.headers.Authorization`。
- 经验：`create_file` 写 `/tmp/*.py`/`*.txt` 必须 `environment=linux`；追加中文文档用「先落临时文件再 `cat >>`」；`edit_file` 同文件连续改要注意上下文漂移。

### 5.6 文档清单（工作区内）
`要求.md`（规格唯一准绳）、`评审清单.md`（验收依据）、`CHANGELOG.md`、`参考实现_源码通读研究.md`（脱敏重写版，决定性）、`解析Bug分析.md`、`交付物索引.md`、`UC下载链路修正要点_交开发方.txt`、`UC取链请求_逐字段对照.txt`、`抓包事实.md`（不透公开库）。

---

## 6. 下一步（给 DSH / 后续自己）

1. **先补完 B1 的 `publishLive()`**（当前不可编译，最优先）。
2. 按 §4.5 清单逐项接入 → 双登记 → 推送 → CI → 出包 → 交付报告 → **停下等 Owner 验收**。
3. B1 验收后 → **B2（玻璃化：下载页 + 设置页）**。
4. C2（通知）与 B2 同推。
5. B3/B4（百度/迅雷）在 B 清完后做。
6. D 系列技术债按 **D1→D2→D4→D5→D6→D7**，每批 1–2 条。

### 待确认 / 未完成项
- [ ] A2（下载详情批 `f3801a04`）未获 Owner 单独回执；
- [ ] B1「剩余时间」的具体展示形态未最终定（Owner 要求①已明确要展示）；
- [ ] 玻璃化改造（C + 内容 abc）方案未落盘；
- [ ] `DownloadService` 挂空未接线（C2 内容）；
- [ ] 游客兜底 `__pugs` 完整游客态、`__puus` 定时刷新未做；
- [ ] `UcTempFolderManager` / `UcTaskPoller` 降级保留待 Owner 拍板去留。

---

## 7. 协作方式说明（给 DSH）

- 本目录即 DSH 工作区（`/data/data/com.ai.assistance.operit/files/workspace/极云渡`）。
- 建议 DSH 也按同样习惯维护本文件 / `CHANGELOG.md`，形成**可追溯日志**。
- 判断「当前进度」以本文件 §3/§4 + `CHANGELOG.md` 最新条目为准。
- 与 Owner 的沟通规则同 §2（R/D/C 铁律 + 每批停下验收）。

> 本文件为交接快照，后续如有更新，请在此增补「修订记录」段落，不要直接覆盖历史。

---

## 8. 修订记录

### 2026-10-04 · DSH 侧接手，闭合 B1（标识 `JYD-DLSPEED-2026-10-04`）
- 接手起点：本文件 §4.4「工作区处于不完整态」——`publishLive()` 已被 `start()` 调用但**方法本体未定义**，
  代码不可编译、禁止推送。
- 本轮动作：
  1. 补齐 `DownloadEngine.publishLive()`（CAS 写内存实时表）与 `removeLive()`，
     按 §4.5 清单逐项接入（start / publishProgress / pause / 完成 / 失败 / cancel）；
  2. `DownloadViewModel` 接入 `downloadEngine.liveProgress`（items 改四路 combine + `mergeLiveProgress()`）；
  3. 新增 `DownloadTimeFormatter`（剩余时间）+ `download_size_remaining_format` 文案 + 列表项展示；
  4. 新增 `DownloadTimeFormatterTest`（8 用例，随 CI 单测执行）；
  5. 双登记：`CHANGELOG.md` + `要求.md` 均追加 `JYD-DLSPEED-2026-10-04` 记录块；
  6. 经 GitHub API 推送 → 触发云端 CI → 下载产物 APK 落 `/sdcard/Download/` → 出交付报告。
- 合规自检：R3（未改任何请求头 / 参数）、C3（取消异常原样抛出，未改动）、C5（新增文案入 strings.xml）、
  C8（新增日志走 Timber）、C9（新增 Flow 仅在既有 IO 作用域内刷新，未新增线程）；
  未动玻璃质感 / 圆角 / Q 弹手感 / 浅色配色；未新增 material-icons（D8）；下载引擎核心下载逻辑未改。
- 状态：**已完成并出包**——首版 `8cedf1d6`（CI 失败：`ACTIVE_STATES` 位于 private 伴生对象，外部不可见）
  → 中断会话补推 `cabc0e53` → 修正版 `7453ce11`；CI `37189131142` **success**
  （Set up / Build Debug APK / Upload APK / Run unit tests 全绿）；
  产物 `/sdcard/Download/极云渡_下载速度修复_debug.apk`（11,904,323 B，CI 固定签名）；
  **B1 待 Owner 装机验收**（验收标准见 §4.2）。
- 编译复盘（供后续会话复用）：`DownloadEngine` 原为 `private companion object`，其成员对外不可见；
  本轮把伴生对象改为 public、内部常量逐个标 `private`，仅公开 `ACTIVE_STATES`。
  首次 CI 即因此失败（`Cannot access 'Companion': it is private in 'DownloadEngine'`），修正后通过。
- 环境备注（供后续 DSH 会话参考）：**本机没有可用沙箱后端**，`workspace-write` 模式下任何 shell 命令都会被拒；
  文件工具新建文件会以**断链符号链接**形式落地（需修复或直接用 shell 写入）。
  因此本轮以「一次性提权命令」执行 shell / 脚本，属环境限制，非代码问题。

---

### 本轮追加的待确认项（并入 §6 清单）
- [ ] B1 装机验收：速度列是否**实时变化**、暂停后是否显示最终态、「剩余 X」文本是否需要微调；
- [ ] A2（`f3801a04`）仍缺 Owner 单独回执。

---

### 2026-10-04 · B1 二轮（装机反馈整改，标识 `JYD-DLSPEED2-2026-10-04`）
Owner 装机反馈两条 + 处理：
1. **整体进度缺失**（看得到均速/剩余时间，看不到「已下载 / 总量」）
   → 真因是**布局被裁**：卡片固定高 88dp/76dp，扣内边距仅剩 40dp，容不下四行内容，
   一轮新增的详情行落在可视区外。本轮改三行布局 + 卡片高度 124dp/104dp，详情行
   「已下载 / 总量 · 速度 · 剩余时间」必定可见。
2. **没有暂停功能** → 引擎 `pause/resume` 早已接通（`DownloadViewModel.toggleTask`），
   缺的是**可见入口**。本轮在行尾加「暂停 / 继续」文字按钮，与整卡点击同源。
附带修复：`publishProgress()` 增加活动态前置判断，避免暂停瞬间在途分片把状态覆盖回「下载中」。
未改：玻璃质感 / 圆角 / 配色 / 引擎下载核心逻辑（分片、续传、限速、重试）。
待确认项（并入 §6）：
- [ ] B1 二轮装机验收四条：进度条 + 已下载/总量同屏；暂停速度归零；继续断点续传；完成满格显示「已完成」；
- [ ] 「已下载 / 总量 · 速度 · 剩余时间」四段在手机窄屏下是否需要再精简（如去掉「均速」残留键）。

- 交付状态：commit `2812fabc`；CI `37190498176` **success**（Build Debug APK / Upload APK /
  testDebugUnitTest 全绿）；产物 `/sdcard/Download/极云渡_整体进度与暂停修复_debug.apk`（11,908,603 B）。
