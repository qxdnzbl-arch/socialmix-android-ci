# 可话 Original Production SPEC

Status: Native distribution expansion
Product: 可话
Goal: 正式公开运营、同一账号多端互通的原版可话；Android、iOS、Windows、macOS 均有独立可安装客户端。
Source priority: 本文件 > KEHUA-REVIVAL-AUTHORITY.md > 已核实公开原版资料 > 历史代码。

## 1. 必须达成

- 正式公网生产服务，不是本地原型、截图复刻、静态演示或 PWA 冒充原生客户端。
- Android、iOS、Windows、macOS 使用同一生产后端、同一账号、同一份服务端数据。
- 四端都必须有独立可安装客户端：
  - Android: APK/AAB。
  - iOS: 可签名归档并导出 App Store 可上传 IPA；最终通过 App Store Connect 发布。
  - Windows: 独立安装器。
  - macOS: 独立 .app/.dmg；公开分发版必须签名并公证。
- 一级结构固定为：首页 / 消息 / 我。
- 核心闭环固定为：发布想法 → 产生共鸣 → 点亮前只看内容、不暴露身份 → 点亮后解锁对方身份 → 私聊 → 可发好友申请 → 对方接受后成为好友。
- 同一账号在两个独立客户端会话中必须看到同一份服务端内容。
- 账号必须支持注册、登录、退出、恢复、注销。
- 支持文本发布和单张图片。
- 支持举报、屏蔽。
- 公开客户端不得获得 service_role/secret key，也不得直接获得业务表写权限。
- 不把“编译成功”“模拟器能开”“有安装包文件”单独视为正式公开运营完成。

## 2. 平台实现约束

### Android
- 继续使用已通过 Android 35 模拟器验收的生产客户端。
- 业务数据连接同一 Supabase 生产后端。

### iOS
- 使用 Apple 原生应用容器，能在 Xcode/iOS SDK 上构建。
- App Store 最终包必须使用用户自己的 Apple Developer Team、Distribution Certificate、Provisioning Profile 与 App Store Connect 权限签名/上传。
- App 内不得只是“外部浏览器快捷方式”；必须作为独立 App 运行并保持核心社交流程完整。
- App Store 审核必须额外满足当前 UGC/社交 App 要求：举报、屏蔽、内容治理、违规用户处置、支持联系入口和审核演示账号。

### macOS
- 使用原生 macOS App 容器。
- 正式公网分发版需要 Developer ID 签名与 Apple notarization；未签名 DMG 只能作为构建候选，不算最终公开发行件。

### Windows
- 使用独立 Windows 桌面客户端和安装器。
- 正式公开发行优先使用受信任的 Authenticode 代码签名证书；未签名安装器只能作为构建候选。

## 3. 生产后端

Production URL: https://nvwdtfnhsyfdopaxdylx.supabase.co

所有平台共享 kehua_prod_* 生产 RPC 与同一业务数据库。客户端只使用 publishable key；密码、恢复码与会话逻辑由服务端控制。

## 4. 核心产品行为

### 首页
- 日期、“此刻，说你想说的话～”、输入区域。
- 输入 1–1200 字，可附单张图片，可设仅自己可见。
- 发布后写入生产后端。
- 非私密内容可形成真实用户之间的共鸣候选。
- 有新共鸣时显示“共鸣已到达。请签收～！”。

### 共鸣
- 点亮前不得返回作者用户 ID、昵称或头像。
- 用户可划过/下一条，或点亮。
- 点亮后才显示作者身份，并建立允许双方私聊的关系。

### 消息
- 展示聊天对象、好友、收到的点亮和好友申请。
- 聊天写入同一生产数据库。
- 好友必须由一方申请、另一方接受后成立。

### 我
- 展示/编辑昵称、简介、地区。
- 展示自己的发布和好友。
- 可重新生成恢复码、退出、永久注销。

## 5. 安全与运营边界

- 密码与恢复码只保存安全哈希；恢复码轮换后旧码失效。
- 会话 token 高熵且有过期时间。
- 注册/登录/恢复有服务端限流。
- 业务表不直接授予公开客户端写权限。
- 聊天/资料访问必须满足关系条件并检查屏蔽关系。
- 客户端不得嵌入 service_role、数据库密码、Apple 私钥或 Windows 签名私钥。
- 用户内容需具备举报、屏蔽与可执行的运营处置路径。
- 生产发布前必须提供隐私政策、服务条款、支持联系方式以及 App Store 审核用演示账号。

## 6. 场景验收

### A. 同账号跨端
Given 同一账号已注册
When Android、iOS、Windows、macOS 中任意一端发布内容，并在另一端登录同一账号
Then 另一端读取到同一条服务端内容。

### B. 真实共鸣
Given 账号 A 和账号 B 各发布一条非私密内容
When A 刷新首页
Then A 获得包含 B 内容的服务端共鸣记录。

### C. 点亮前隐私
Given A 有来自 B 的共鸣
When A 尚未点亮
Then 返回体不包含 B 的用户 ID、昵称或头像。

### D. 点亮与聊天
Given A 正查看来自 B 的共鸣
When A 点亮并发送消息
Then A 看见 B 身份，B 的独立客户端能读取消息。

### E. 好友
Given A 与 B 已有可互动关系
When A 发好友申请且 B 接受
Then 双方好友列表基于同一服务端好友关系。

### F. 恢复与注销
Given 用户拥有恢复码
When 重置密码
Then 旧会话与旧恢复码失效；新密码与新恢复码生效。
When 用户确认永久注销
Then 账号级联业务数据删除，旧会话失效。

### G. Windows 安装包
Given CI 在 windows-latest 构建
When 生成 Windows 客户端与安装器
Then 安装器包含生产 UI、生产后端地址和独立桌面可执行文件，且构建产物 SHA256 被记录。

### H. macOS 安装包
Given CI 在 macOS/Xcode 环境构建
When 生成 macOS Release app
Then .app 可被系统识别并打包为 DMG；正式公开版另需 Developer ID 签名与 notarization。

### I. iOS App Store 构建
Given Xcode 项目和 iOS Release target
When CI 无签名构建
Then 必须至少得到可验证的 iOS archive 候选；它不是 App Store 最终 IPA。
Given Apple Developer 签名材料与 App Store Connect 权限
When 执行 Release archive/export/upload
Then 产生可上传 IPA 并成功进入 App Store Connect 处理流程。

### J. 原生端不泄密
Given 任一平台最终安装包
When 静态检查客户端资源
Then 不包含 service_role/secret key、数据库密码、Apple 私钥或代码签名私钥。

## 7. 完成定义

只有以下全部满足，才能称为“正式公开运营多端原版可话”：
1. 生产后端核心 A–F 场景通过。
2. Android 最终 APK/AAB 实装验收通过。
3. Windows 安装器实际构建并安装/启动验收通过，正式分发版完成受信任签名。
4. macOS DMG 实际构建、启动验收通过，正式分发版完成 Developer ID 签名和 notarization。
5. iOS 使用用户 Apple Developer 身份签名，上传 App Store Connect，完成审核所需元数据、隐私/UGC合规与演示账号，并通过 App Store 审核。
6. 四端使用同一生产后端、账号和数据，不以 PWA 代替任何一端的独立安装客户端。
7. 最终交付每个平台的安装件、SHA256、对应构建提交和验收记录。
