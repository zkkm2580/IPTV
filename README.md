# IPTV 直播电视播放器 (LibVLC 核心版)

基于 VideoLAN 官方底层动态链接库 **LibVLC** 构建的高性能、全格式 Android IPTV 电视直播播放器，专为智能电视盒子、车机系统与移动设备打造。

## 🌟 核心特性

- 🚀 **VideoLAN LibVLC 底层解码核心**：
  - APK 内嵌完整 LibVLC 动态链接库与解码架构（包含 `libvlc.so`）；
  - **全格式广电解封装**：原生支持 MPEG-TS、UDP / RTP 局域网组播流、HLS (`.m3u8`)、RTMP 与 RTSP 协议；
  - **全套专有音频硬解**：全面支持 MediaCodec 硬件加速及 AC3、E-AC3、MP2、DTS 等格式，杜绝有画无音；
  - **画幅比例与自愈重连**：支持 16:9、4:3、全屏拉伸及原始比例自由切换，网络断流自动重连。

- 📑 **取消硬编码，纯 M3U 播放列表驱动**：
  - 彻底移除硬编码写死频道，完全根据用户自定义输入的 M3U/M3U8 源地址或本地文件动态生成；
  - 逐行提取 `#EXTINF` 扩展标签属性（`tvg-id`, `tvg-name`, `tvg-logo`, `group-title`），多级分类展示。

- 🌐 **电信/联通光猫专网组播 udpxy 代理**：
  - 内置 udpxy 组播代理支持，可配置网关代理地址（如 `http://192.168.1.1:4022`）；
  - 自动将运营商专网电视 `rtp://239.x.x.x` 或 `udp://` 转为标准 HTTP 流播放。

- 📅 **TvgProvider 实时 EPG 电子节目单**：
  - 异步后台下载并解析 XMLTV（`.xml` 或 `.xml.gz`）；
  - 在 OSD 界面与选台抽屉中实时显示当前正在播出的节目名称与播出时间进度。

- 🎮 **电视盒子遥控器深度交互**：
  - 遥控器上下键切台、OK 键呼出分类抽屉、菜单键呼出设置；
  - 触屏手势操作与沉浸式 5 秒淡出 OSD 节目状态显示。

- 🚀 **GitHub Actions 云端自动化构建 (CI/CD)**：
  - 每次代码提交均会自动触发 Gradle 编译并发布 APK 至 GitHub Releases。

## 📥 下载安装

前往 [Releases 页面](https://github.com/zkkm2580/IPTV/releases) 下载最新的 `IPTV直播电视-LibVLC.apk`。
