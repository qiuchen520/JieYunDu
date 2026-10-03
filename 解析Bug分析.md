# 网盘解析 Bug 分析报告

> 性质：代码评审结论（解析链路）
> 结论口径：以下"正确链路"为**同类网盘工具的通用做法**（协议事实，非某项目专有）
> 适用于：极云渡 当前交付版本

---

## 一句话结论

**夸克解析链路漏了最关键的「转存 + 任务轮询」两步，而且 detail 响应字段路径写错了** —— 即便请求全部成功，也拿不到能用的直链。全项目 `grep` 转存相关代码，**一条都没有**。

---

## 一、正确的解析链路（通用做法）

四家网盘殊途同归，用的都是同一套骨架：

```
① 拿令牌  →  ② 列文件  →  ③ 转存到本账号  →  ④ 轮询任务拿新 fid  →  ⑤ 取直链  →  ⑥ 下载
```

关键在 **第 ③④ 步不能省**。以夸克为例，完整链路：

| 步骤 | 接口 | 关键点 |
|---|---|---|
| 1 握手 | `GET pan.quark.cn` | 取 `__pus` + `__puus`（**必须两个都有**）+ 游客令牌 `__pugs` |
| 2 换令牌 | `POST .../sharepage/token` | 请求体带 `pwd_id` / `passcode` / `support_visit_limit_private_share` |
| 3 列文件 | `GET .../sharepage/detail` | 响应是 **`data.detail_info.list[]`**，每项含 `fid` **和 `share_fid_token`** |
| 4 **转存** | `POST .../sharepage/save` | 传 `pwd_id`/`stoken`/`fid_list`/**`fid_token_list`**/`scene=link` → 得 `task_id` |
| 5 **轮询** | `GET .../task?task_id=` | 轮询到 `finished_at>0` 或 `status==2`，取 **`save_as.save_as_top_fids[0]`** |
| 6 取直链 | `POST .../file/download` | 传 **自己网盘的 fid**，Body 字段是 `fids`（不是 `fid_list`） |
| 7 下载 | CDN 直链 | 必须带 `Referer: pan.quark.cn/` + `__pugs` Cookie |

> 核心认知：**`file/download` 只认自己网盘里的文件。**
> 直接把分享的 fid 丢进去，拿不到直链 —— 这就是"转存"步骤存在的唯一理由。

> 重要结构：`detail` 是**两级结构**（`data.detail_info.list`），
> 而转存入参需要**每个文件的 `share_fid_token`**，不只是 `fid`。

---

## 二、当前项目的 Bug 清单（按严重度）

### 🔴 P0-1　完全没有「转存 + 任务轮询」（致命）

`grep -rniE 'sharepage/save|save_as|转存|TaskPoller|TempFolder' .`
→ **无任何转存相关代码**

`domain/parser/quark/QuarkParser.kt` 的实际流程：

```
token → detail → 直接 file/download（传【分享的 fid】）      ← 错
```

- 要求文档 §8.2 第 4、5 步（转存 / 轮询）在代码里**不存在**；
- 要求文档第六部分列出的 `domain/transfer/{ShareTransfer, TaskPoller, TempFolderManager}.kt`
  **目录都没建**（`ls domain/` 只有 downloader / login / model / parser / util）；
- 后果：**解析到文件列表后，取直链必然失败**
  → 用户表现为"解析失败"或"列出了文件却下载不了"。

**叠加问题**：`QuarkParser.kt:119` 取的是**分享的 `entry.fid`**，
而正确做法必须用**转存后的新 fid**。

---

### 🔴 P0-2　detail 响应字段路径写错

`QuarkParser.kt:116`：

```kotlin
val entries = detailResponse.data.list          // ← data.list 不存在
```

`QuarkApi.kt:123` 定义 `QuarkShareDetail(val list: List<QuarkFile>)`，直接映射到 `data.list`。

但真实响应是 **`data.detail_info.list[]`**（外层还有 `detail_info`）。
由于 Json 配置了 `ignoreUnknownKeys`，`list` 字段缺失
→ 要么解析出空列表，要么抛异常 → **协议错误**。

---

### 🔴 P0-3　`QuarkFile` 缺 `share_fid_token`

```kotlin
data class QuarkFile(val fid, val file_name, val size, val dir)   // 没有 share_fid_token
```

转存接口需要 `fid_token_list`（即每个文件的 `share_fid_token`）。
现在**拿不到这个值**，就算补上转存也传不了参。

---

### 🟠 P1-1　Cookie 只有 `__puus`、丢了 `__pus`，且用覆盖而非合并

- `registerPuusCookie()` 只筛 `startsWith("__puus")` → **丢掉 `__pus`**；
  而登录态判定必须**两个都有**。
- `ResponseCookieInterceptor` 用 `cookieStore.save()` **整体覆盖**同名域 Cookie
  （应该**合并回写**）。download 响应下发的 `__pugs`
  会把之前的 `__puus`/`__pus` 冲掉。

---

### 🟠 P1-2　token 请求体缺字段

`buildTokenBody` 只有 `pwd_id`/`passcode`，
缺夸克要求的 `support_visit_limit_private_share: true`
→ 私密分享可能取不到 stoken。

---

### 🟠 P1-3　`file/download` 缺固定 query 参数

现状 `@POST("1/clouddrive/file/download")` 无任何参数；
正确应为 `?pr=ucpro&fr=pc&sys=win32&ve=3.23.2`。

---

### 🟠 P1-4　下载请求不带 Referer/Cookie

`HomeViewModel.download()` 构造 `DownloadTask` 时**没传 `headers`**，
下载引擎对所有分片请求只加了 `Range`：

```kotlin
// downloader/ 里除 Range 外，没有任何 Referer / Cookie
requestBuilder.header(HEADER_RANGE, buildRangeHeader(chunk, resumeFrom))
```

后果：夸克 CDN 直链 → **HTTP 412**；UC → **403**。
也就是说，前面就算全对，**下载这一步照样挂**。

---

### 🟡 P2　轻量问题

| 位置 | 问题 |
|---|---|
| `LinkExtractor.extractShareId` | 百度需**去掉前导 `1`**（`/s/1xxxx` → `xxxx`），现在原样返回 |
| `LinkExtractor` | 未按事实裁剪链接尾部中文标点 `。，、；)]}"'`（当前正则侥幸不受影响） |
| `LinkExtractor` | 提取码正则 `[A-Za-z0-9]{4}` 可能误吞正文（低风险） |
| `UcParser` / `BaiduParser` / `XunleiParser` | 全是占位，直接返回 `NOT_IMPLEMENTED` —— 用户点这三家链接必然提示"开发中" |

---

## 三、最小修复路径（建议给开发方的顺序）

1. **建 `domain/transfer/`**：
   `ShareTransfer`（save）+ `TaskPoller`（轮询）+ `TempFolderManager`（临时目录 `.极云渡临时`）。
2. **改 `QuarkApi` / `QuarkParser`**：
   - `detail` 响应改成 `data.detail_info.list`；
   - `QuarkFile` 加 `share_fid_token`；
   - 解析流程改成 `token → detail → save → poll → download(新 fid)`；
   - token body 补 `support_visit_limit_private_share`；download 补 `sys/ve`。
3. **Cookie 改合并**：`__pus` + `__puus` 都收，`save()` 改 merge；加 `__pugs`。
4. **下载带 Referer/Cookie**：`DownloadTask.headers` 填夸克 `Referer` + `CookieStore` 取 Cookie。

---

## 四、Bug 速查表

| 编号 | 严重度 | 一句话 | 位置 |
|---|---|---|---|
| P0-1 | 致命 | 缺转存 + 任务轮询，直链拿不到 | `QuarkParser.kt` 全程 |
| P0-2 | 致命 | detail 字段应是 `data.detail_info.list` | `QuarkParser.kt:116`、`QuarkApi.kt:123` |
| P0-3 | 致命 | `QuarkFile` 缺 `share_fid_token` | `QuarkApi.kt:138` |
| P1-1 | 高 | Cookie 丢 `__pus`、覆盖不合并 | `QuarkParser.kt:180`、`NetworkModule.kt:201` |
| P1-2 | 高 | token body 缺 `support_visit_limit_private_share` | `QuarkParser.kt:201` |
| P1-3 | 高 | download 缺 `sys`/`ve` 参数 | `QuarkApi.kt:77` |
| P1-4 | 高 | 下载请求不带 Referer/Cookie → 412/403 | `HomeViewModel.kt:212` |
| P2-1 | 中 | 百度分享 ID 未去前导 `1` | `LinkExtractor.kt:104` |

---

*报告完 —— 按此清单即可逐项定位与修复。*
