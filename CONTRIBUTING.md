# 贡献指南（Contributing to 极云渡 / JieYunDu）

感谢你有兴趣为本项目贡献代码或文档。为了让贡献可以被合法地纳入与再分发，
**提交 Pull Request 之前，你必须先签署贡献者许可协议（CLA）。**

> 请注意：**CLA 是「贡献门槛」，与项目自身的开源协议（AGPL-3.0）是两回事。**
> 签署 CLA 不会改变你对自己贡献的版权归属——你**保留版权**，只是额外授予本项目
> 所有者一份许可（详见下方说明）。

---

## 一、签署 CLA（提交 PR 前必做）

1. 阅读 CLA 全文：[`docs/CLA.md`](docs/CLA.md)。
2. 打开你的 Pull Request，在**评论区**回复下列语句（逐字，区分大小写与标点）：

   ```
   I have read the CLA Document and I hereby sign the CLA
   ```

3. 仓库的 CLA Assistant 会自动校验并在 `signatures` 分支记录你的签署信息
   （GitHub 用户名、签署时间等）。记录完成后，你的 PR 即可正常评审合并。

> 首次提交 PR 时，机器人会**自动在你的 PR 下留言**提示签署；签署完成即解除拦截。
> 如签署状态需要重新校验，可在 PR 评论中回复 `recheck`。

### CLA 采用什么模板？授权方式是什么？

- 模板：**Apache Individual Contributor License Agreement（ICLA）V2.2**。
- 授权方式：**版权许可（非转让）**。
  - 你**保留**自己贡献的版权；
  - 你授予本项目所有者一份**永久的、免费的、可再许可（sublicense）的**权利；
  - 因而所有者将来可对项目进行**闭源收费 / 双版本（开源 + 闭源）分发**，
    而无需逐一联系贡献者签字确认。
- 本项目自身仍以 **AGPL-3.0** 发布（见仓库根 `LICENSE`），CLA **不改变**这一点。

> 现有贡献者（目前仅 Owner 一人）**不强制**补签。

---

## 二、提交 Pull Request

1. Fork 仓库并基于 `main` 创建分支。
2. 遵循项目既有代码风格与文档规范（见 `要求.md`、`评审清单.md`）。
3. 使用仓库的 [PR 模板](.github/PULL_REQUEST_TEMPLATE.md)，逐项勾选自检清单。
4. 提交 PR 并按上文签署 CLA，等待评审。

---

## 三、行为准则

- 就事论事，尊重不同意见。
- 不提交含敏感信息（密钥、Cookie、个人隐私）的内容。
- 不引入 GPL / AGPL / LGPL 等与项目分发策略冲突的第三方依赖。

---

如对 CLA 有疑问，可在 PR 或 Issue 中提出。
