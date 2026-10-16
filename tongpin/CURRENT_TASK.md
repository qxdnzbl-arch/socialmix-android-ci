# 同频唯一状态锚点（2026-10-17）
ROOT_GOAL: 用户无需中转的单聊天窗口，真实GPT与Claude自动协作，手机双机可用且历史同步，费用安全、长期稳定可维护，最后交付可访问并实测的版本。
KEEP: 严禁修改已有项目main分支或其它应用；不自动充值、不启用付费托管；不泄漏密钥或私密聊天；不使用302.AI账号内无限额密钥。
PROVEN: 已经将server.js、api/*.js和vercel.json的安全修正提交独立公开仓库分支 `tongpin-render-20261017`；修正Vercel函数最长时间180秒，302.AI限额密钥核验强制开关，原始JSON请求体转发。容器本地12项自动测试通过。
NOT VERIFIED: 部署公网HTTPS；真实GPT+Claude调用（302.AI余额0）；账号密钥的限额配置；双机同步；Vercel Hobby长期商业适配（Hobby禁商业）。
BLOCKED: Render Hobby 25服务上限；Railway账户免费资源限制；Vercel Plugin尚需用户显式连接，才能进行已授权的部署操作。
NEXT: 若Vercel Connector已连接，在项目隔离和Hobby条件满足时部署此分支，核实公网UI、未连接时拒绝付费请求。下一阶段才用302.AI有限额密钥测试真实模型和成本，然后设计双机历史同步。
DONE GATE: HTTPS正常、真模型调用记录、两台手机历史同步、安全账单限额真实生效、无隐藏维护债务，才算最终成品。
