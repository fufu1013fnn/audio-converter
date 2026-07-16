param(
    [string]$FfmpegExe = ""
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Source = Join-Path $ProjectRoot "AudioConverter.cs"
$Dist = Join-Path $ProjectRoot "dist"
$Output = Join-Path $Dist "AudioConverter.exe"
$Compiler = Join-Path $env:WINDIR "Microsoft.NET\Framework64\v4.0.30319\csc.exe"

if (!(Test-Path $Compiler)) {
    $Compiler = Join-Path $env:WINDIR "Microsoft.NET\Framework\v4.0.30319\csc.exe"
}

if (!(Test-Path $Compiler)) {
    throw "Cannot find .NET Framework C# compiler."
}

if (!(Test-Path $Source)) {
    throw "Cannot find AudioConverter.cs."
}

New-Item -ItemType Directory -Force -Path $Dist | Out-Null

$args = @(
    "/nologo",
    "/target:winexe",
    "/platform:x64",
    "/out:$Output",
    "/reference:System.dll",
    "/reference:System.Core.dll",
    "/reference:System.Drawing.dll",
    "/reference:System.Windows.Forms.dll"
)

if ($FfmpegExe) {
    if (!(Test-Path $FfmpegExe)) {
        throw "Cannot find ffmpeg.exe: $FfmpegExe"
    }
    $args += "/resource:$FfmpegExe,AudioConverterApp.ffmpeg.exe"
}

$args += $Source
& $Compiler @args

Write-Host "Built: $Output"
