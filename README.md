# minis-companion

[OpenMinis](https://github.com/OpenMinis/OpenMinis)（开源 AI Agent 应用，即 Minis）的**配套数据管理器**。
Android 版 Minis 没有云同步 —— 本项目给你的 Minis 数据安个家：导出、归档、随处浏览，
以及（规划中的）原生 Android 客户端自动化。

```
Minis app（官方原版，随便升级，本项目的任何部分都不修改它）
   │ ① 沙箱插件导出（minis-sessions-cli，自动定时）
   │ ② 官方备份包 BackupFormat（规划支持，全保真）
   ▼
minis-companion
   ├─ sandbox-plugin/   跑在 Minis 的 Alpine 沙箱里：导出+归档+推送
   ├─ web/              单文件只读聊天镜像（气泡/搜索/工具调用查看器）
   ├─ server/           nginx 配置与部署说明（Basic 认证保护）
   └─ android/          （WIP）原生客户端：定时同步引擎 + 本地数据管理
   ▼
你自己的服务器 / 任何 rclone 远端 → 浏览器随处查看
```

## 为什么不是 fork OpenMinis？

升级安全是设计目标：我们只读官方公开接口（`minis-sessions-cli`）和官方文档化格式
（备份包 `BackupFormat`、Room schema），**不链接、不修改 Minis 任何内部代码**。
官方更新再频繁也不影响本项目的每个组件。本项目与 OpenMinis 官方无隶属关系。

## 现状

- [x] 沙箱导出流水线：`export → archive → push`，一条命令，可挂每日定时
- [x] Web 镜像：聊天气泡还原、标题/摘要搜索、工具调用折叠查看；
      兼容普通浏览器（fetch）与 Minis 内置查看器（minis:// 无 fetch 自动回退 script 注入）
- [x] 服务器部署件：nginx location + Basic 认证 + 权限坑说明
- [ ] Android 客户端 v0.1：WorkManager 定时拾取导出 + 推送同步
- [ ] 官方备份包读取器：解析 `BackupFormat`（chats/skills/memory/providers/...），
      保存完整对话（含 reasoning、token 用量）→ 见 docs/DATA-FORMAT.md

## 快速开始（沙箱插件）

```sh
# 在 Minis 沙箱里
cp -r sandbox-plugin /var/minis/skills/session-manager
python3 /var/minis/skills/session-manager/scripts/run_all.py
# 可选：用 Minis 的 scheduled task 每天自动跑（见 docs/ROADMAP.md §P0）
```

## 自建 Web 镜像

见 [server/DEPLOY.md](server/DEPLOY.md)。要点：静态目录放 nginx 可读路径，
`location ^~ /sessions/` + `auth_basic`，`Cache-Control: no-store`。

## 许可

MIT。数据归你，代码随意用。
