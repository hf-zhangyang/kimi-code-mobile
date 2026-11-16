# Release 签名说明

本目录只提交这份说明；实际的 `keystore.properties` 和 `*.jks` 属于本机私密文件，已在 `.gitignore` 中排除，不要提交或分享。

本地构建 release 包前需要自行放置：

- `signing/keystore.properties`，包含 `storeFile`、`storePassword`、`keyAlias`、`keyPassword` 四项；
- `storeFile` 指向的 keystore 文件（默认 `signing/kimi-pocket-release.jks`）。

缺少上述文件时，Gradle 会回退到 debug 签名。
