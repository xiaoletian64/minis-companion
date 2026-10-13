# session-manager — Minis 会话数据管理器

配套开源项目 **minis-companion**（github.com/xiaoletian64/minis-companion）的 Minis 侧插件。
只做数据导出/归档/推送，不碰 Minis 内部实现，官方升级零影响。

## 能力

1. **全量导出**：`python3 scripts/export_sessions.py` — 沙箱 `minis-sessions-cli`
   拉全部会话（含工具调用），写 `www/`（index + 每会话 JSON/JS 双格式）。
2. **归档快照**：`python3 scripts/archive_snapshot.py` — 把当次导出按日期存到
   `/var/minis/shared/sessions-archive/YYYY-MM-DD/`，保留历史版本。
3. **推送到 124**：`scripts/push_124.sh` — tar 推 `/www/wwwroot/minis-sessions/`，
   网页入口 https://erweima.zhangyue.site/sessions/（zy/lovemhy）。
4. **状态报告**：`python3 scripts/status.py` — 会话数/最新会话/归档占用。

## 一键流水线

```sh
python3 /var/minis/skills/session-manager/scripts/run_all.py
```

= 导出 + 归档 + 推送 + 打印一行状态。minis-scheduled 任务 42363d20（每日 09:05）调用的就是它。

## 设计边界（重要）

- 数据源只用 `minis-sessions-cli` 公开 CLI，不用任何内部 DB 路径。
- 更细的数据（reasoning/tokenUsage/富文本 parts）未来由配套客户端经
  官方备份包（BackupFormat，跨平台 JSONL）读取，见 minis-companion 仓库 docs/DATA-FORMAT.md。
- 导出文件含全部对话内容，仅推自有服务器 124（同机 wxdesk_htpasswd 保护）。
