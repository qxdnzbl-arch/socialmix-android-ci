# 可话｜2025 原版多端互通 SPEC

## 1. One-Sentence Concept
复原 2025 年仍在公开分发的「可话」1.13.x 核心体验，并让 Web 与 Android 使用同一账号、同一云端数据和同一核心交互。

## 2. Target User And Use Scenario
用户在手机或电脑上打开可话，写下真实想法，等待系统匹配共鸣；点亮感兴趣的共鸣后进入聊天，聊得来可加好友；换设备登录同一账号后继续看到同一资料、记录、好友、聊天和共鸣状态。

## 3. Core User Path
登录 → 首页在红色光标处写下想说的话 → 发表 → 收到“共鸣已到达。请签收～！” → 查看共鸣 → 点亮 → 聊天 → 进入对方主页 → 加好友 → 继续跨端聊天。

## 4. Platform & Technical Constraints
- Web：公开 HTTPS 入口。
- Android：独立可安装 APK，不能把浏览器链接当作 APK 交付。
- Web 与 Android 共用同一 Supabase Auth / Database / Realtime 数据源。
- 原版依据只使用公开可验证的 2025 官方/应用商店页面与同期公开截图，不使用用户上传截图。
- UI 与文案以 2025 年 1.13.x 可核实证据为准；证据不足处不伪称“完全一致”。

## 5. Phase 1 Scope
Phase 1 即可交付核心版，必须同时满足：
- 2025 可话首页表达体验：极简页面、红色闪动光标、记录真实想法。
- 登录、退出、个人资料。
- 发布文字记录；支持仅自己状态。
- 共鸣生成/读取、共鸣列表、点亮。
- 点亮后进入同一会话聊天。
- 好友申请、接受/拒绝、好友关系。
- 我的记录、点亮状态、好友最新动态。
- Web 与 Android 同账号、同数据、同聊天。
- 公网可访问。
- Android APK 可安装、可启动、能渲染真实界面。
- 深色模式。
- 设置入口与“给可话反馈”等原版可核实入口。

## 6. Explicitly Out Of Scope
- 伪造原开发团队身份、商标授权或官方运营身份。
- 在没有公开证据时臆造原版页面。
- 用静态截图或热区伪装真实软件。
- 将单纯网页链接包装成“多端互通软件”。

## 7. Scenario Acceptance Tests

### Scenario: 首次打开
Given: 未登录用户首次打开 Web 或 Android
When: 页面完成加载
Then: 能看到可话品牌、登录入口；Android 不是空白页/错误页。

### Scenario: 核心表达
Given: 已登录用户在首页
When: 输入一段文字并发表
Then: 记录成功写入云端，并进入等待共鸣状态。

### Scenario: 共鸣到聊天
Given: 用户收到共鸣
When: 打开共鸣并点亮
Then: 创建/打开对应会话，可发送消息。

### Scenario: 好友链路
Given: 已与对方建立会话
When: 发起好友申请并由对方接受
Then: 双方好友关系同步。

### Scenario: 多端同步
Given: 同一账号在两个独立客户端登录
When: A 端修改资料/发记录或继续聊天
Then: B 端能够读取相同更新；好友与聊天数据一致。

### Scenario: Android 可安装可启动
Given: 构建出的 APK
When: 安装到真实 Android 运行环境并启动
Then: 进程正常存活，WebView/原生容器渲染可话登录界面，不出现空白页或错误页。

### Scenario: 公网访问
Given: 任意外部网络浏览器
When: 打开正式 URL
Then: HTTPS 200，页面包含 2025 可话核心界面文案与交互入口。

## 8. Launch / Public Access Checklist
- 公网 HTTPS 正常。
- Supabase RLS / Auth 正常。
- 不暴露 service role 或私有凭据。
- Web 与 Android 指向同一生产数据源。
- Android CI 构建、安装、启动、渲染通过。
- 两个独立客户端多端同步自测通过。
- 2025 原版依据来自公开来源并记录在开发提交中。

## 9. Handoff
请根据这份 SPEC.md 实现并维护可话 2025 原版多端互通版本。不要用截图热区或静态演示冒充软件。完成后必须按上面 7 个场景逐项实测；任何一项未通过都不能称为最终交付。
