# AudioConverter

一个简洁的音频转换器项目，包含 Windows 桌面版和 Android 版。

## Windows 版

Windows 版使用 WinForms 编写，底层调用 FFmpeg 完成音频格式转换。

功能：

- 批量添加或拖放音频文件
- 支持输出 `mp3`、`wav`、`flac`、`aac`、`ogg`、`m4a`
- 可选择输出质量和输出目录
- 支持把 `ffmpeg.exe` 嵌入到最终 EXE 中，做成单文件版

构建：

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

如果要构建内置 FFmpeg 的单文件版，请先准备 `ffmpeg.exe`，然后执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -FfmpegExe "C:\path\to\ffmpeg.exe"
```

构建产物会输出到 `dist\AudioConverter.exe`。

## Android 版

Android 版源码位于 `android/` 目录，使用原生 Android Java 编写。

当前第一版功能：

- 选择多个音频文件
- 使用 Android 系统解码器转换音频
- 输出 `wav`
- 构建 Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`

构建：

```powershell
cd android
..\..\gradle-8.7\bin\gradle.bat assembleDebug
```

也可以直接用 Android Studio 打开 `android/` 目录。

## FFmpeg 说明

Windows 版可以调用或内置 FFmpeg。发布内置 FFmpeg 的 EXE 时，请确认使用的 FFmpeg 构建版本和许可证。当前 Windows 开发测试使用的是 BtbN FFmpeg GPL 构建，因此主项目按 GPL-3.0-or-later 开源。

FFmpeg 官网：https://ffmpeg.org/

## 许可证

本项目使用 GPL-3.0-or-later 许可证。详见 `LICENSE`。
