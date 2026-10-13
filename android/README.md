# android/ — minis-companion 客户端（WIP）

原生 Android 客户端，定位：**数据管理器 + 自动同步引擎**。
不修改、不内嵌 OpenMinis，只消费其官方导出的数据（备份包 / 沙箱插件产物）。

## 规划（对应 docs/ROADMAP.md P1-P2）

- v0.1：SAF 选目录拾取 Minis 官方备份包 → 解析（支持 passphrase）→
  归一化 JSON → WorkManager 每日推送自有服务器（sftp/https）
- v0.2：本地 FTS 全文搜索、会话浏览 UI
- v0.3：增量/去重、Markdown 导出

## 格式参考

见 [../docs/DATA-FORMAT.md](../docs/DATA-FORMAT.md)。
Gradle 工程骨架将在 v0.1 开发分支补齐；CI 已就位（无 gradlew 时占位通过）。
