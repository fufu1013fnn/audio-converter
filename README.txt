# AudioConverter

一个简洁的 Windows 桌面音频转换器，使用 WinForms 编写，底层调用 FFmpeg 完成音频格式转换。

## 功能

- 批量添加或拖放音频文件
- 支持输出 `mp3`、`wav`、`flac`、`aac`、`ogg`、`m4a`
- 可选择输出质量和输出目录
- 支持把 `ffmpeg.exe` 嵌入到最终 EXE 中，做成单文件版

## 构建

本项目使用 Windows 自带的 .NET Framework C# 编译器构建，不需要安装 .NET SDK。

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

如果要构建内置 FFmpeg 的单文件版，请先准备 `ffmpeg.exe`，然后执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -FfmpegExe "C:\path\to\ffmpeg.exe"
```

构建产物会输出到 `dist\AudioConverter.exe`。

## FFmpeg 说明

本程序本身是一个图形界面，实际音频转换由 FFmpeg 完成。

如果发布内置 FFmpeg 的 EXE，请确认使用的 FFmpeg 构建版本和许可证。当前开发测试使用的是 BtbN FFmpeg GPL 构建，因此本项目按 GPL-3.0-or-later 开源。

FFmpeg 官网：https://ffmpeg.org/

## 许可证

本项目使用 GPL-3.0-or-later 许可证。详见 `LICENSE`。
