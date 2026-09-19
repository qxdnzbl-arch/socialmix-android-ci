# 可话 Original 1.13.3 — Source-Grounded SPEC

Status: ACTIVE — replaces all self-invented revival UI/specs
Target: the original 可话 product behavior and visual language, rebuilt for formal public operation and shared multi-platform data.
Rule: no historical local implementation may define the product. Every user-visible screen/flow must be traceable to original 可话 evidence or explicit user confirmation.

## 0. Product authority

Priority from highest to lowest:

1. User-confirmed original 可话 screenshots/behavior.
2. Current official App Store listing and official App Store screenshots for app id 1501478660.
3. Current Microsoft Store listing XP9MRN8C3VGV5H and its screenshots.
4. Current Android 1.13.3 package evidence: com.app.tideswing, current store listings, verified APK metadata where available.
5. Multiple independent walkthroughs/screenshots of the original app.
6. Historical articles/reviews only as corroboration.
7. Existing repo code last. Existing code may supply infrastructure, never product definition.

The UI currently present on kehua-firebase-auth-prod is explicitly rejected as a product reference. Its recovery-code UI, profile rows, home copy, message empty state and invented auth are not original requirements.

## 1. Verified original identity

- Product: 可话
- App Store subtitle: 说你想说的话
- iOS App Store id: 1501478660
- Current verified public version baseline: 1.13.3 (2025-08-25)
- Developer on Apple: Beijing TideSwing Technologies Co., Ltd.
- Android package: com.app.tideswing
- Product premise: record real thoughts/feelings; each record receives several possible 共鸣; content comes before identity; people who mutually understand each other can see more and interact privately.
- No public feed/广场 is part of the core original experience.

## 2. Visual source set

### Current official App Store screenshot references

These are immutable visual references, not decorative inspiration:

- https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/a1/c0/26/a1c02662-ddff-c602-5ecd-ee1999f2ff78/c1396ded-94c8-4478-ad24-f242552f6d24_5.png/1242x2208.png
- https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/60/53/e2/6053e2e8-1220-b10d-6e23-f43d9d8556a1/c8b1ec9f-6cce-426c-9c10-83ca10a94b12_6.png/1242x2208.png
- https://is1-ssl.mzstatic.com/image/thumb/PurpleSource211/v4/05/e6/97/05e697ca-1827-0469-c0c4-bf2329e5c2a1/3bbe07b2-5a09-4f7f-bcc7-ff03018862d5_20230811-184749.png/1242x2208.png
- https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/00/9e/d3/009ed38c-06c8-cc6b-5b41-699e493d426a/d2fd9404-965a-4f5a-b17a-f2fe6958d3f4_9.png/1242x2208.png

### Current/historical Android screenshot references

- https://img.downkuai.com/uppic/20241107/317e37dbc55dfa2313b7e756cd9c25c9.png
- https://img.downkuai.com/uppic/20241107/29c6f44f2407d671c529d77aed88a364.png
- https://img.downkuai.com/uppic/20241107/66c7358589585f807e8ed1fa7d8d3de0.png
- https://img.downkuai.com/uppic/20241107/463ab5aecf4060a4a1ca5b4ea624dbe6.png
- https://img.downkuai.com/uppic/20211109/aaa2ea5f246adc630bab084de0f1c043.jpg
- https://img.downkuai.com/uppic/20211109/28dae1ea9fc49c9aafb77a9cfc34e6e5.jpg
- https://img.downkuai.com/uppic/20211109/08f29962343eccc12233bb156358a172.jpg

### Known exact original visual/copy facts

- Marketing headline: 说想说的话
- Marketing subtitle: 记录你真实的想法和感受
- Home writing prompt shown in original marketing: 此刻，说你想说的话～
- Current official 1.13.3 home screenshot uses the exact heading “此刻，说你想说的话～”, a full-screen blurred pastel/photo background, a large rounded composer and a three-icon bottom navigation without text labels.
- Original interaction uses a red/pink insertion caret/primary accent.
- Current official resonance viewer is full-screen: close control at top-left, item count at top-right, matched content shown before identity, and a bottom sun-shaped “点亮” control; after 点亮, identity and relationship context appear.
- Current official 消息 page has a horizontal strip of circular friend/latest-dynamic avatars with pink rings above the conversation list, plus top-right controls and the same three-icon bottom navigation.
- Current 1.13.3 marketing/screenshots demonstrate video dynamics in addition to text/image records.
- Original has separate light/dark visual modes. The Microsoft Store screenshot explicitly presents 深色模式 with “无论何时，说你想说的话”.
- The implementation must be visually checked against the reference screenshots, not merely described as “grey-purple/pink”.

## 3. Core original flow

### A. Publish
1. User opens 首页.
2. User writes a real thought in the main editor; original walkthroughs describe a red blinking caret.
3. User publishes.
4. The system later presents: “共鸣已到达。请签收～！”

### B. 共鸣
1. User opens received 共鸣.
2. System presents matched content rather than a public feed.
3. User can pass/leave or press 点亮.
4. Original product behavior intentionally makes some missed encounters non-recoverable: if the user leaves without 点亮/回应/加好友, later interaction may no longer be available.

### C. 点亮 → chat
1. User presses 点亮 on a 共鸣.
2. A private interaction/chat path opens.
3. Since version 1.10.0, private chat and dynamic-under-post chat are merged into one chat box.
4. If both parties interact, the conversation remains reachable through 消息.

### D. Add friend
1. User first publishes and receives a 共鸣.
2. User 点亮 the other person.
3. In chat, user taps the other person's avatar/profile.
4. From that profile, user chooses 加好友.
5. After the other side accepts, the private chat may show: “我们现在是好友啦，可以开始聊天了～”
6. Friends' latest dynamics can be surfaced at the top of 消息.

### E. Profile / settings
- “我” is a first-level tab, but account-management rows must not be invented on the profile.
- Original walkthrough: 我 → top-right 设置.
- Account deletion: 设置 → 账号与安全 → 注销账号 → 开始注销.
- Feedback: 设置 → 给可话反馈.
- Historical/current product supports avatar and profile background.
- Historical/current product supports profile-visibility controls, including stranger visibility of all vs latest ten dynamics in cited walkthroughs.

### F. Authentication
- Original product uses phone-number registration/login and SMS verification.
- Overseas numbers are supported according to developer responses; country/region prefix handling matters.
- Do not use “account + password + recovery code” as the public original flow unless the user explicitly requests a non-original replacement.
- Production implementation may add secure backend/session infrastructure, but user-visible auth must preserve the original phone-verification shape unless current binary evidence shows a changed flow.

## 4. Explicitly forbidden inventions

- “我的恢复码 / 重新生成” profile row.
- Direct profile-page “注销账号” row when the original flow places deletion under 设置 → 账号与安全.
- Rewording the current official heading “此刻，说你想说的话～” into invented copy.
- Public feed/广场.
- Generic social-media follower/like counters not evidenced by original.
- Arbitrary platform-specific redesigns.
- Calling a native shell around rejected UI “original 可话”.
- Claiming pixel fidelity without screenshot comparison.

## 5. Production/multi-platform requirement

Final product must be formally usable and share the same production account/data across:
- Android independent installable
- iPhone/iPad App Store app
- Windows independent installable
- macOS independent installable

All clients must implement the same original product model and connect to one production backend. A PWA is optional but cannot substitute for any requested native installable.

## 6. Acceptance scenarios

### Scenario 1 — Original home visual
Given a clean signed-in account
When 首页 is opened
Then the editor/prompt, spacing, typography, background treatment, navigation and light/dark behavior match the verified original reference set within screenshot-comparison tolerance; no rejected self-invented recovery/profile UI appears.

### Scenario 2 — Publish to resonance
Given user A and user B have valid accounts
When both publish eligible non-private thoughts
Then A can receive a server-side 共鸣 notification using the original interaction shape and enter the 共鸣 viewer without a public feed.

### Scenario 3 — Content before identity
Given A is viewing B's 共鸣
When A has not completed the original reveal/interaction condition
Then the UI/API does not expose identity that the original product keeps hidden.

### Scenario 4 — 点亮 and chat
Given A is viewing B's 共鸣
When A presses 点亮
Then the original private-interaction/chat path opens and the conversation persists according to original rules.

### Scenario 5 — Add friend
Given A has lit/responded to B
When A opens B's profile from the interaction and chooses 加好友 and B accepts
Then both share one server-side friendship and the original friend-state/chat behavior appears.

### Scenario 6 — Original settings hierarchy
Given user opens 我
When user opens the top-right 设置
Then feedback/account/security/privacy features are in original-style settings hierarchy, and 注销账号 is under 账号与安全 rather than an invented profile shortcut.

### Scenario 7 — Phone auth
Given a logged-out user
When user chooses phone login and submits a valid country/region phone number
Then the production SMS verification path works; invalid/rate-limited/expired codes return recoverable feedback without exposing secrets.

### Scenario 8 — Cross-platform sync
Given the same account is logged in on two independent platform clients
When one client publishes, chats, changes friendship state or edits profile
Then the other client reads the same production server state.

### Scenario 9 — Native packaging
Given release CI
When Android/iOS/Windows/macOS builds run
Then each requested platform creates a real installable/archivable native app from the same accepted original UI/logic baseline; rejected UI cannot be packaged.

### Scenario 10 — Public operation
Given production release
When an external user installs/registers/uses the product
Then auth, storage, moderation/report/block, privacy/legal/support and production backend are operational; no service-role secret/private signing key is embedded in clients.

## 7. Completion definition

“原版可话已复活并正式多端运营” may be stated only when:
1. Reference evidence set is locally archived and indexed.
2. User-visible screens are rebuilt from that evidence, not from the rejected UI.
3. Screenshot regression passes against the original reference set.
4. Core publish → 共鸣 → 点亮 → chat → friend flow passes with at least two real test accounts.
5. Phone/SMS auth path is production-operational.
6. Android install/runtime test passes.
7. Windows installer install/runtime test passes.
8. macOS signed/notarized app install/runtime test passes.
9. iOS signed App Store archive is uploaded and passes App Store review.
10. Cross-platform same-account/data tests pass.
11. Final artifacts, SHA256, source commit and acceptance evidence are delivered.

Until these are true, intermediate builds are candidates only, never “original 可话 final”.
