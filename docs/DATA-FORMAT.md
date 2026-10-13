# 数据格式参考

本项目只消费**公开、文档化**的数据面，保证 Minis 升级不破坏读取器。

## 1. 沙箱 CLI（当前导出器使用）

`minis-sessions-cli`：

- `list --limit 100 --compact -q`
  → `{count, sessions:[{session_id,title,preview,started_at,last_active,message_count}]}`
- `messages --id <sid> --tools --full --compact -q`
  → `{session_id,total,count,messages:[{message_id,role,created_at,text,tool_calls[],tool_results[]}]}`

注意：`-q` 时输出本身就是 data 字段；`--tools` 的 input/output 各有截断上限（600/50000）。

## 2. 导出文件布局（web/）

```
data/index.json            {generated_at, count, sessions:[...]}
data/index.js              window.MIRROR_INDEX = <同上>;   ← minis:// 查看器回退用
sessions/<id>.json         {id,title,started_at,count,messages:[{role,at,text,calls:[{name,input,output,ok}]}]}
sessions/<id>.js           window.MIRROR_SESSION = <同上>;
```

工具调用 input/output 有截断（500/1200 字符），全文请走备份包读取器（§3）。

## 3. 官方备份包 BackupFormat（规划中的全保真源）

源码位置（OpenMinis main）：`src/android/app/src/main/java/com/openminis/app/backup/`

- 分类 `BackupCategory`：`chats / shared_files / skills / memory / providers /
  mcp_servers / voice_corrections / environment_variables`
- **跨平台同一套格式**（iOS `BackupCategory` raw value 对齐），Android/iOS 互迁就靠它
- 可选加密（passphrase，KDF 参数在 manifest.encryption）
- 投递目的地：rclone 远端（sftp/webdav，分块上传）或 SAF 本地文件夹（LocalFolderDelivery）
- 记录为 JSONL envelope（`BackupRecordEnvelope<T>`），文件树走 blob store（CHATS 等带文件目录的分类受 §3.4 大小上限约束）

读取器路线：用户在 Minis 里手动导出备份包到本地文件夹 →
companion 拾取 → 解 manifest/JSONL → 归一化成本项目的 session JSON（含 reasoning、tokenUsage、partsJson 富文本）。

## 4. Room 数据库（只读参考，不直连）

schema：`src/android/app/schemas/com.openminis.app.data.db.AppDatabase/14.json`

- `sessions`：id,title,modelId,createdAt,updatedAt,category,lastMessage,pinnedAt,folderId,parentSessionId,...（16 列）
- `messages`：id,sessionId,role,**partsJson**,createdAt,tokenUsage,sortOrder,reasoningContent,errorInfo,modelId,...（15 列）

导入恢复走官方 `BackupImporter`，本项目永不直接写这个库。
