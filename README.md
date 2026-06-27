# UnlockMusic 🎵

Android 解锁加密音乐文件工具，支持网易云音乐（.ncm）、QQ音乐（.qmc）、酷狗音乐（.kgm）、酷我音乐（.kwm）、虾米音乐（.xm）等主流平台加密格式。

## 功能

- 批量解密加密音乐文件
- 支持 .ncm / .qmc / .kgm / .kwm / .xm 等格式
- 自动输出为标准 MP3/FLAC 格式
- Material Design 界面
- 暗黑模式支持

## 技术栈

- Kotlin + Android Jetpack
- ViewBinding + RecyclerView
- Coroutines 异步
- FFmpeg 音频处理

## 构建

确保有 Android SDK 34+、JDK 17+：

```bash
cd "Android Project"
./gradlew assembleRelease
```

APK 生成路径：`Android Project/app/build/outputs/apk/release/app-release-unsigned.apk`

## 免责声明

本项目仅供学习交流，禁止用于商业用途。解密后的文件请在 24 小时内删除。
