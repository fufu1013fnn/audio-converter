# AudioConverter Android

安卓版音频转换器，使用原生 Android Java 编写。不依赖第三方 FFmpeg 库，使用 Android 系统解码器和编码器完成轻量本地转换。

## 功能

- 选择多个音频文件
- 输出 `wav`、`m4a`
- `m4a` 使用 AAC 编码，支持 `128k`、`192k`、`256k`
- 支持系统可解码的输入格式，例如常见 `mp3`、`m4a`、`aac`、`ogg`、`wav`
- 使用临时 PCM 文件流式转换，减少内存占用
- 转换结果保存到应用专属音乐目录

## 打开方式

用 Android Studio 打开本目录：

```text
E:\gggg\audio-converter-android
```

首次同步会下载 Android Gradle Plugin。

## 构建

如果本机有 Gradle Wrapper，可执行：

```powershell
.\gradlew assembleDebug
```

当前环境没有 Android SDK/Gradle，因此这里先提供完整源码项目。

## 许可证提示

当前版本没有内置 FFmpeg，因此 APK 体积较小。后续如果接入 FFmpeg AAR，可进一步扩展 MP3、FLAC、OGG 等输出格式。
