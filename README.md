# 极云渡 (JieYunDu)

网盘分享链接解析 + 高速下载工具，液态玻璃 UI，平板优先。

## 状态

开发中。当前进度：阶段 6 / 10。

## 支持网盘

- [x] 夸克（骨架已就位，参数待抓包补全）
- [ ] UC（占位）
- [ ] 百度（占位）
- [ ] 迅雷（占位）

## 构建

本项目通过 GitHub Actions 云端编译，无需本地安装 Android SDK。

推送到 main 分支后，在仓库的 Actions 页面可下载 debug APK。

CI 使用官方 `gradle/actions/setup-gradle`（固定 Gradle 8.2.1），不依赖
`gradle-wrapper.jar`——该二进制文件未随本次交付的文本接口入库。
（Gradle 官方无 8.2.2 发行版；8.2.2 为 AGP 版本号，勿混淆。）

本地构建（可选）：自备 Gradle 8.2.1 后执行

    gradle assembleDebug

## 开源协议

AGPL-3.0

## 贡献指南

欢迎提交 Issue 与 Pull Request。**提交 PR 前需先签署 CLA（贡献者许可协议）**——
CLA 是「贡献门槛」，与项目自身的 AGPL-3.0 是两回事：贡献者保留版权，同时授予本项目
所有者永久、免费、可再许可的权利（详见 [`docs/CLA.md`](docs/CLA.md)）。

签署方式：在 PR 评论区回复

    I have read the CLA Document and I hereby sign the CLA

完整流程见 [`CONTRIBUTING.md`](CONTRIBUTING.md)。

## 致谢与声明

本项目的架构设计、功能规格、验收标准、文档体系由作者独立完成。
代码实现环节采用 AI 辅助开发（DeepSeek）。
作者负责需求定义、阶段裁决、文档维护与质量把关。

根据现行著作权法，AI 不构成版权主体，本项目著作权归作者所有。

## 开发过程披露

本项目为完全独立开发，未参考任何现有网盘解析工具的源码。
开发方式为 AI 辅助（DeepSeek），由 Owner 定义规格与裁决。
本项目全部代码均按《要求.md》第七部分给定签名从零独立编写。
