# 路线图

## P0 — 沙箱插件 + Web 镜像（已上线）
- export_sessions.py：全量会话导出（含工具调用），双格式（json+js）
- archive_snapshot.py：按日归档到 shared/sessions-archive/
- run_all.py：导出→归档→推送→状态，一条命令
- 定时：Minis scheduled task 每日 09:05 调 run_all
- web/index.html：只读镜像（气泡/搜索/工具调用），fetch + script 注入双通道

## P1 — Android 客户端 v0.1（进行中）
- 读取官方备份包（BackupFormat JSONL，支持 passphrase 解密）→ 全保真归档
- WorkManager 每日定时：拾取备份包 → 归一化 → 推送自有服务器（sftp/https）
- 极简 UI：同步状态 + 最近会话列表 + 手动触发
- 不申请多余权限：SAF（用户选目录）+ INTERNET + POST_NOTIFICATIONS

## P2 — 数据管理器
- 本地全文搜索（SQLite FTS）
- 去重与增量（以官方备份 manifest 为准）
- 导出 Markdown / JSON
- 多设备归档合并

## P3 — 顺手补官方缺口
- 监听 Minis 备份完成广播（如有）或 BackupHistory 文件变化 → 备份完成即自动同步
- WebDAV 直投（rclone 网关化，手机本地直推，不过服务器）

## 红线
- 永不写 Minis 内部数据库；永不要求 fork/修改官方 app
- 依赖仅限公开 CLI、文档化格式、官方导出的备份包
