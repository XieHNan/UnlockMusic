<div align="center">
  <h1>UnlockMusic 🎵</h1>
  <p>
    <strong>Android 加密音乐文件解密工具</strong>
  </p>
  <p>
    支持网易云音乐 · QQ音乐 · 酷狗音乐 · 酷我音乐 · 虾米音乐
  </p>
  <p>
    <img src="https://img.shields.io/badge/Android-16%2B-3ddc84?logo=android" alt="Android 16+"/>
    <img src="https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin" alt="Kotlin"/>
    <img src="https://img.shields.io/badge/license-MIT-yellow" alt="License"/>
  </p>
</div>

---

## 📖 简介

**UnlockMusic** 是一款 Android 平台上的加密音乐文件解密工具。它能够将各大主流音乐平台的加密音频文件还原为标准 MP3/FLAC 格式，方便用户在各种设备上自由播放。

> ⚠️ 本工具仅用于学习交流，请在法律允许的范围内使用。解密后的文件请在 24 小时内删除。

---

## ✨ 功能特性

| 特性 | 说明 |
|------|------|
| 🔓 **多格式解密** | 支持 `.ncm` `.qmc` `.kgm` `.kwm` `.xm` 等主流加密格式 |
| 📦 **批量处理** | 一次性选择多个文件，批量解密 |
| 🎧 **在线试听** | 解密后可直接在 App 内播放试听 |
| 📁 **灵活导出** | 支持保存到下载目录 / 音乐目录 / 自定义路径 |
| 🎨 **主题色** | 15 种主题色自由切换 |
| 🌙 **暗黑模式** | 跟随系统自动切换深色/浅色主题 |
| 📂 **原生文件管理器** | 绕过系统隐私密码，直接访问文件 |
| 🏷️ **智能重命名** | 重复文件自动重命名或覆盖 |

---

## 📋 已支持的加密格式

| 平台 | 扩展名 |
|------|--------|
| 网易云音乐 | `.ncm` |
| QQ音乐 | `.qmc0` `.qmc2` `.qmc3` `.qmc4` `.qmc6` `.qmc8` `.qmcflac` `.qmcogg` `.mflac` `.mflac0` `.mgg` `.mgg0` `.mgg1` |
| 酷狗音乐 | `.kgm` `.kgma` |
| 酷我音乐 | `.kwm` |
| 虾米音乐 | `.xm` `.xmal` |

同时也支持常见音频格式（`.mp3` `.flac` `.wav` `.ogg` `.m4a` 等）作为普通文件透传处理。

---

## 🛠️ 技术栈

```
Language:    Kotlin 1.9.22
UI:          ViewBinding + RecyclerView + Material 3 + GridLayout
Async:       Coroutines
Build:       Gradle 8.7 + AGP 8.2.2
Audio:       MediaPlayer 播放
Storage:     MediaStore API (Android 10+) / File API
Min SDK:     Android 6.0 (API 23)
Target SDK:  Android 14 (API 34)
```

---

## 🔨 构建

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17+
- Android SDK 34

### 构建步骤

```bash
# 1. 进入项目目录
cd "Android Project"

# 2. 调试构建
./gradlew assembleDebug

# 3. 发布构建
./gradlew assembleRelease

# APK 路径
# app/build/outputs/apk/debug/app-debug.apk
# app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## 📸 截图

> （项目暂无截图，欢迎贡献！）

---

## 🧩 项目结构

```
Android Project/
├── app/
│   ├── src/main/
│   │   ├── java/com/unlockmusic/android/
│   │   │   ├── decrypt/          # 解密核心（NCM/QMC/KGM/KWM/XM）
│   │   │   ├── model/            # 数据模型
│   │   │   ├── ui/               # 界面（Activity、Adapter）
│   │   │   └── util/             # 工具类（音频处理）
│   │   ├── res/                  # 资源文件（布局、颜色、主题）
│   │   └── AndroidManifest.xml
│   ├── build.gradle              # 模块构建配置
│   └── proguard-rules.pro        # 混淆规则
├── build.gradle                  # 项目级构建配置
├── settings.gradle               # 项目设置
└── gradlew                       # Gradle 启动脚本
```

---

## 📄 许可证

```
MIT License

Copyright (c) 2026 XieHNan

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
