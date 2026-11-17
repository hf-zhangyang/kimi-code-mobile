# KimiCode（Android）

> 桌面上的代码，装进口袋。

KimiCode 是一个 Android 随身客户端，用于连接 **Kimi Code Desktop 的官方远程控制服务**。在电脑上开启远程控制后，手机扫描一次二维码（或粘贴配对链接），之后从桌面图标直接进入自己的 Kimi Code 工作区——查看会话、继续对话、审批工具调用，不再依赖浏览器标签页。

安装包约 5.8 MB，不含原生库，不限制 CPU 架构；最低支持 Android 8.0（API 26）。

## 功能特性

**配对与连接**

- 三种配对方式：摄像头扫描二维码、从相册识别二维码截图、手动粘贴 HTTPS 配对链接。
- 相册识别使用系统图片选择器，应用内异步解码，不申请相册 / 存储权限。
- 扫码后先显示连接域名供确认，不直接加载不明二维码。
- 配对链接保存在应用私有空间的加密存储中（EncryptedSharedPreferences），重启后自动恢复；旧版明文数据首次读取时自动迁移并清除明文副本。
- 连接失败时提供错误页与「重新连接」按钮。

**远程控制页**

- 全屏 WebView，无多余原生顶栏，页面内容即全部界面。
- 顶部加载进度条；支持页面内返回、远程页面的文件选择上传。
- Kimi 登录在应用内弹窗完成（手机号 / 企业 SSO），不跳外部浏览器；登录成功自动返回，也可点「完成并返回」；弹窗已适配状态栏，不与系统图标重叠。

**安全与隐私**

- 仅允许 HTTPS；拦截 HTTP 链接，SSL 证书错误直接停止连接。
- 配对信息禁止进入系统备份；完整配对令牌不在界面显示。
- 关闭 WebView 调试与文件 / content 访问；不申请麦克风、存储、通讯录、定位权限。
- 语音输入交给系统输入法，App 内不集成语音模块。

## 使用方式

1. 电脑打开 **Kimi Code Desktop 1.1.0+**，进入「设置 → 远程控制」并开启，保持电脑运行并联网。
2. 手机安装 APK（侧载，需允许「安装未知应用」）。
3. 打开 App，选择「扫描二维码」「从相册识别二维码」或「粘贴配对链接」，确认域名后保存。
4. 之后直接打开 App 即进入工作区；需要登录时在应用内弹窗完成。
5. 如需更换配对：当前版本没有应用内换配对入口，请清除应用数据或卸载重装后重新扫码。

## 构建

环境要求：JDK 17、Android SDK 35、Gradle 8.13（AGP 8.7.3）。

```bash
gradle testDebugUnitTest assembleRelease
```

- **Windows 中文路径注意**：AGP 会拒绝含非 ASCII 字符的项目路径。可先 `subst K: "项目目录"` 映射出纯 ASCII 盘符，在 `K:\` 下构建，完成后 `subst K: /d` 解除。
- `local.properties` 需配置 `sdk.dir` 指向本机 Android SDK（该文件不入库）。
- **签名**：release 默认读取 `signing/keystore.properties` 与其中的 keystore 文件；两者不入库，缺失时回退 debug 签名。本机侧载使用固定自签名密钥，保证覆盖升级签名一致；密钥放置方式见 [signing/README.md](signing/README.md)。

## 项目结构

```
app/src/main/java/com/kimicode/pocket/
├── MainActivity.kt        # 配对页 / 远程页 / 登录弹窗 / 扫码与相册识别入口
├── PairingStore.kt        # 配对链接加密存储与明文迁移
├── PairingValidator.kt    # 配对链接校验（HTTPS、官方域名）
├── LoginFlow.kt           # 登录完成页判定
└── GalleryQrDecoder.kt    # 相册二维码异步解码
app/src/main/res/layout/   # activity_pairing / activity_remote
app/src/test/              # LoginFlow、PairingValidator 单元测试
signing/                   # 签名说明（密钥本身不入库）
```

## 边界与说明

- 这是第三方配对客户端，**不是 Moonshot AI 官方应用**；控制能力由 Kimi Code Desktop 官方远程控制服务提供。
- 电脑端需保持运行并联网；桌面端重建远程链接后，旧配对会失效，需按上文方式重新配对。
- 卸载 App 会删除本机保存的配对链接。
- 当前使用本地固定测试签名，适合侧载体验，未上架应用商店。

## 第三方组件

- 二维码扫描：JourneyApps ZXing Android Embedded 4.3.0（Apache License 2.0）。
- 加密存储：AndroidX Security Crypto / Tink。
- 完整许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 许可

本项目自身代码以 [MIT License](LICENSE) 开源，可自由使用、修改与分发。第三方组件保留其原有许可，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
