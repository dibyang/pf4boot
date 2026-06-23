# 安全报告

如果你发现 pf4boot 的安全问题，请不要创建公开 Issue。

## 报告方式

优先通过 GitHub 私有安全公告提交：

https://github.com/xdob/pf4boot/security/advisories/new

如果无法使用 GitHub Security Advisories，请联系项目维护者并避免在公开渠道披露漏洞细节。报告中请尽量包含：

- 受影响版本、模块和运行模式。
- 可复现步骤或最小复现工程。
- 预期影响，例如远程代码执行、权限绕过、敏感信息泄露、供应链信任链绕过或拒绝服务。
- 已知缓解方式或临时规避措施。

## 处理预期

维护者会优先确认问题是否可复现，并评估影响范围。修复发布前，请不要公开 PoC、利用细节或可直接复现的攻击步骤。

## 安全范围示例

- 插件包签名、checksum、trust manifest 或 repository index 校验绕过。
- 插件类加载隔离或宿主 API 边界绕过。
- 管理接口鉴权、幂等记录、部署替换或回滚路径绕过。
- 插件启停、JPA reload、动态 bean/mapping 清理中的权限或资源泄露问题。

普通功能缺陷、文档问题和兼容性问题请通过 GitHub Issue 提交。

