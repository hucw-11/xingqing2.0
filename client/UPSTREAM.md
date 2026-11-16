# 心晴客户端来源

本目录基于 [上课](https://github.com/qiqqqqq517/shangkeschedule)，固定 commit `afd1389cfaef2be15cef0aea7ea4efa158f1659e`，许可见 [LICENSE](LICENSE)。保留原作者版权与代码包路径。导入日期：2026-11-16。

原始文件的 SHA-256 见 `../docs/upstream-import-manifest.json`。该清单记录导入前内容；心晴适配文件允许与其不同，差异由 Git 记录。

本批改动：

- Android applicationId 为 `com.hucw.xinqing2`，版本 0.3.0 / code 3；内部 namespace 保留上游，以减少无关改名。
- 应用显示名称、Windows包名和窗口标题改为心晴；桌面数据隔离到 `%APPDATA%/Xinqing2/UpstreamBase`，不覆盖旧心晴或原版上课。
- 新安装默认进入原版 AgendaScreen，保留日期面板、月份/日期轴、课程/日程/待办列表及创建交互。已有明确启动偏好仍可读取。
- 不提供原版应用更新或推广弹窗；上游许可证和版权保留。无关功能入口逐步收敛，代码先保留。
- 使用仓库根部的 CI；没有导入上游网站、发布工具、代理指令或密钥配置。

仓库根部旧 `shared/androidApp/desktopApp` 是迁移前实现，不是当前构建入口；保留供规则和测试移植。新构建必须从 `client/` 执行。

目前尚未实现心晴新增日记、心情统计、AI、双向同步及微信云调度。原版 WebDAV 功能仍是备份/恢复，不代表双向同步。
