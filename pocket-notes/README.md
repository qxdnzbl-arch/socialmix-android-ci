# 随手存

一个完全离线的 Android 图文收纳软件。默认分类：理发、提示词、日常，可新增和重命名分类。

支持：新建编辑、图文保存、搜索、常用置顶、一键复制、理发大字展示、图片缩放、草稿恢复、回收站、含图片的 ZIP 备份和合并导入。无需网络、账号、广告或第三方服务。

Android 8.0 及以上。纯 Java 原生界面，没有外部依赖。数据保存在应用私有目录，图片从选择器复制，原相册文件不受影响。

使用 Android SDK 35 与 Build Tools 35.0.0，运行 `bash build.sh`。安装包在 `out/Suishoucun.apk`。更新必须沿用 `build/pocket-signing.jks`，不能重新生成签名。

测试通过独立 instrumentation APK 执行；发行 APK 不包含测试代码和测试数据。
