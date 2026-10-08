<div align="center">
<img src="logo.png" width="180" alt="FolkPatch logo">

[![Latest Release](https://img.shields.io/github/v/release/LyraVoid/FolkPatch?label=Release&logo=github)](https://github.com/LyraVoid/FolkPatch/releases/latest)
[![Telegram](https://img.shields.io/badge/Telegram-FolkPatch-blue?logo=telegram)](https://t.me/FolkPatch)
[![License](https://img.shields.io/github/license/LyraVoid/FolkPatch?logo=gnu)](/LICENSE)

</div>

**语言：** [English](./README.md) / [中文](./README_CN.md) / [日本語](./README_JA.md)

FolkPatch 是基于 [KernelPatch](https://github.com/LyraVoid/KernelPatch) 构建的 Root 管理工具，集成了 KernelPatch Root、系统模块、内核模块、Lua 插件、Shizuku 管理以及可配置的现代化界面。

[阅读完整文档](https://fp.mysqil.com/)

<table>
  <tr>
    <td><img alt="FolkPatch 首页" src="docs/1.png"></td>
    <td><img alt="FolkPatch 模块界面" src="docs/2.png"></td>
    <td><img alt="FolkPatch 设置界面" src="docs/3.png"></td>
  </tr>
  <tr>
    <td><img alt="FolkPatch 功能界面" src="docs/4.png"></td>
    <td><img alt="FolkPatch 插件界面" src="docs/5.png"></td>
    <td><img alt="FolkPatch 自定义界面" src="docs/6.png"></td>
  </tr>
</table>

---

## 功能概览

FolkPatch 提供：

- 基于 KernelPatch 的 Root 实现，无需重新编译内核
- APM 系统模块，支持批量安装与备份
- KPM 内核模块，支持自动加载
- 轻量级 APD Lua 插件
- Shizuku 服务管理与开机启动
- 网络隔离、路径隐藏、内核伪装、挂载隐藏与卸载策略
- 主题、壁纸、自定义字体与多种首页布局
- 英语、中文、日语及其他社区翻译

## 前置要求

- ARM64 Android 设备
- Android 内核版本 3.18 至 6.15
- 已安装受支持的 KernelPatch

完整兼容性与安装步骤请参阅[文档](https://fp.mysqil.com/)。

## 界面

- 支持 ListUI、GridUI、FocusUI、CircleUI、DashboardUI 和 StatsUI
- 新安装默认使用 FocusUI
- 支持系统主题色、自定义配色、壁纸和字体
- 支持导航模式与仪表盘卡片配置
- 支持关闭自动更新检查

## 模块与扩展

### APM

APM 提供类 Magisk 的系统模块能力，支持安装、卸载、启用、禁用、批量操作和完整备份。

### KPM

KPM 提供内核模块支持，适用于 `inline-hook` 和 `syscall-table-hook`，并支持自动加载。

### APD Lua 插件

APD 插件在 `apd` 生命周期内运行 Lua 脚本，不修改系统文件，也不注入内核模块。插件支持配置、快捷操作、后台守护任务和持久化日志。

插件文档：

- 英文：https://fp.mysqil.com/en/modules/plugin/
- 中文：https://fp.mysqil.com/modules/plugin/

## 安全提示

SuperKey 的权限高于普通 Root 会话。弱密钥或已泄露的密钥可能导致设备被未授权控制。请使用强密钥并妥善保管。

## 翻译

英语和中文是参考语言。翻译修正请只提交对应语言的文件。新增语言时，请在 Pull Request 中提供完整翻译文件。

## 下载与安装

1. 从[发布页面](https://github.com/LyraVoid/FolkPatch/releases/latest)下载最新安装包。
2. 在 Android 设备上安装该包。
3. 按应用内引导完成部署，并在安装模块或启用运行时控制前阅读[文档](https://fp.mysqil.com/)。

## 开源致谢

- [KernelPatch](https://github.com/LyraVoid/KernelPatch)：核心 Root 实现
- [Magisk](https://github.com/topjohnwu/Magisk)：magiskpolicy
- [KernelSU](https://github.com/tiann/KernelSU)：应用 UI 与模块系统参考
- [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)：界面设计参考
- [APatch](https://github.com/bmax121/APatch)：上游项目
- [MMRL](https://github.com/MMRLApp/MMRL)：模块仓库格式参考
- [Shizuku](https://github.com/RikkaApps/Shizuku)：内置 Shizuku 服务
- [LoliAPI](https://www.loliapi.com/docs/acg/)：壁纸图鉴的在线壁纸来源

## 许可证

FolkPatch 遵循 [GNU General Public License v3](https://www.gnu.org/licenses/gpl-3.0.html)。

若修改 FolkPatch 或将 FolkPatch 集成到其他项目并分发，整个项目必须按 GPLv3 开源。分发二进制文件时，必须提供或承诺提供完整且可读的源代码。本软件按原样提供，不附带任何担保；违反上述条款将终止 GPLv3 授权。

## 社区

- Telegram：[**@FolkPatch**](https://t.me/FolkPatch)
