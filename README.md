# IPTV 直播电视播放器 (Android)

基于 Google 官方现代化 AndroidX Media3 (ExoPlayer) 打造的开源高性能 IPTV 电视直播播放器，完美适配智能电视机顶盒、车载大屏与安卓移动设备。

## 🌟 核心特性

- 📺 **广电级流媒体解码内核**：
  - 基于 AndroidX Media3 构建，低时延高画质；
  - 完美支持 HLS (`.m3u8`)、HTTP-TS、RTMP、RTSP 等各类主流 IPTV 电视直播源；
  - 网络波动断流自愈：发生缓冲阻断或网络中断时自动重连。

- 📑 **专业 M3U / M3U8 播放列表支持**：
  - 支持 `#EXTM3U` 与 `#EXTINF` 扩展属性解析（`tvg-name`, `tvg-logo`, `group-title`）；
  - 支持多分类自动聚类分组（央视频道、各省卫视、高清影视等）；
  - 内置高质量央视卫视直播源预设，并支持云端 M3U 列表动态同步。

- 🎮 **电视盒子遥控器深度交互**：
  - 针对红外/蓝牙遥控器优化：方向键上/下直接切台；
  - 按 OK / 回车键呼出分类与频道双层抽屉选台菜单；
  - 底部沉浸式 OSD 节目信息栏（5 秒自动渐隐）。

- 🚀 **GitHub Actions 云端自动化构建 (CI/CD)**：
  - 每次代码提交均会自动触发 Gradle 编译并发布 APK 至 GitHub Releases。

## 📥 下载安装

前往 [Releases 页面](https://github.com/zkkm2580/IPTV/releases) 下载最新的 `IPTV直播电视.apk`。
