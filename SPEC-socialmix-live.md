# SocialMix Phase 1 SPEC

## One-Sentence Concept
在保留 B 版清爽、简洁结构的前提下，把账号、加好友和一对一文字聊天接到真实 Supabase 后端，让两台手机上的两个真实账号可以成为好友并互发消息。

## Target User And Use Scenario
用户注册自己的账号后，通过对方的唯一账号搜索并发送好友申请；对方接受后，双方进入联系人并可以开始真实一对一聊天。

## Core User Path
注册/登录 → 联系人 → 添加朋友 → 搜索唯一账号 → 发送好友申请 → 对方接受 → 成为好友 → 打开聊天 → 双方互发文字消息。

## Platform & Technical Constraints
- Android 原生 Jetpack Compose。
- 延续现有 B 版的轻、白、绿色、低信息密度视觉。
- 使用现有 Supabase 项目作为账号、好友关系、会话和消息后端。
- 客户端只使用 Supabase publishable key；不放 service_role/secret key。
- 遵守现有 RLS 权限。

## Phase 1 Scope
- 邮箱 + 密码注册和登录。
- 注册时设置唯一账号和昵称。
- 登录状态本地保存。
- “我”页展示真实昵称与账号。
- “联系人”入口真正可点击。
- 联系人页展示真实好友和收到的好友申请。
- 通过完整唯一账号搜索用户并发送好友申请。
- 接受/拒绝好友申请。
- 接受后自动建立一对一会话。
- 从联系人或消息页进入真实聊天。
- 真实发送、读取文字消息；聊天页通过 Supabase Realtime 接收新消息，历史消息持久化在 messages 表。
- 退出登录。

## Explicitly Out Of Scope
- 图片、视频、语音消息。
- 文件上传空间。
- 群聊。
- 推荐流。
- 朋友圈/小红书式发帖真实后端。
- 点赞、评论、收藏。
- 通讯录导入、手机号搜索。
- 二维码加好友。
- 在线状态、已读回执、正在输入。
- 推送通知。

## Assumptions
- 添加好友第一版只按“完整唯一账号”搜索，不做模糊推荐。
- Supabase 邮箱确认策略沿用当前项目设置；若项目要求确认邮箱，注册后先确认再登录。
- 本阶段实时聊天使用 Supabase Realtime 接收新消息；进入聊天时先从 messages 表读取历史记录，网络短暂异常时仍保留定时刷新兜底。

## Scenario Acceptance Tests
### Scenario: 注册真实账号
Given: 未登录用户打开应用
When: 输入昵称、唯一账号、邮箱和密码并注册
Then: Supabase Auth 创建用户，profiles 自动生成对应昵称/账号；若返回会话则进入应用，否则明确提示先完成邮箱确认。

### Scenario: 两个账号成为好友
Given: A、B 两个已登录真实账号且互非好友
When: A 按 B 的完整账号搜索并发送申请，B 在联系人页接受
Then: friend_requests 变 accepted，friendships 出现关系，系统创建唯一 direct conversation 和两条 conversation_members。

### Scenario: 真实互发文字消息
Given: A、B 已成为好友
When: A、B 打开同一聊天，A 发送一条文字消息
Then: messages 中新增记录；B 无需手动刷新即可看到新消息并可回复；双方退出聊天再进入后仍能看到历史消息。

### Scenario: 权限边界
Given: C 不是 A/B 好友且不是该会话成员
When: C 尝试读取 A/B 会话或消息
Then: RLS 阻止读取。

### Scenario: 退出登录
Given: 用户已登录
When: 在“我”页点击退出登录
Then: 本地会话被清除并回到登录页，不能继续读取好友和聊天数据。

## Handoff
请根据这份 SPEC 实现 Phase 1。只做 Phase 1，不做 Explicitly Out Of Scope 的内容。完成后按 Scenario Acceptance Tests 自测，并说明哪些场景通过、哪些未通过。
