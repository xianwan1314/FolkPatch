<div align="center">
<img src="logo.png" width="180" alt="FolkPatch logo">

[![Latest Release](https://img.shields.io/github/v/release/LyraVoid/FolkPatch?label=Release&logo=github)](https://github.com/LyraVoid/FolkPatch/releases/latest)
[![Telegram](https://img.shields.io/badge/Telegram-FolkPatch-blue?logo=telegram)](https://t.me/FolkPatch)
[![License](https://img.shields.io/github/license/LyraVoid/FolkPatch?logo=gnu)](/LICENSE)

</div>

**言語：** [English](./README.md) / [中文](./README_CN.md) / [日本語](./README_JA.md)

FolkPatch は [KernelPatch](https://github.com/LyraVoid/KernelPatch) を基盤とする Root 管理アプリケーションです。KernelPatch による Root、システムモジュール、カーネルモジュール、Lua プラグイン、Shizuku 管理、カスタマイズ可能な最新 UI をひとつにまとめています。

[ドキュメントを読む](https://fp.mysqil.com/)

<table>
  <tr>
    <td><img alt="FolkPatch ホーム画面" src="docs/1.png"></td>
    <td><img alt="FolkPatch モジュール画面" src="docs/2.png"></td>
    <td><img alt="FolkPatch 設定画面" src="docs/3.png"></td>
  </tr>
  <tr>
    <td><img alt="FolkPatch 機能画面" src="docs/4.png"></td>
    <td><img alt="FolkPatch プラグイン画面" src="docs/5.png"></td>
    <td><img alt="FolkPatch カスタマイズ画面" src="docs/6.png"></td>
  </tr>
</table>

---

## 概要

FolkPatch は以下を提供します。

- カーネルを再コンパイルせずに利用できる KernelPatch ベースの Root
- 一括インストールとバックアップに対応する APM システムモジュール
- 自動読み込みに対応する KPM カーネルモジュール
- 軽量な APD Lua プラグイン
- Shizuku サービス管理と起動時の統合
- ネットワーク分離、パス非表示、カーネル偽装、マウント非表示、アンマウント制御
- テーマ、壁紙、カスタムフォント、複数のホームレイアウト
- 英語、中国語、日本語、その他のコミュニティ翻訳

## 動作要件

- ARM64 Android デバイス
- Android カーネル 3.18 から 6.15
- 対応する KernelPatch のインストール

互換性とインストール手順の詳細は[ドキュメント](https://fp.mysqil.com/)を参照してください。

## インターフェース

- ListUI、GridUI、FocusUI、CircleUI、DashboardUI、StatsUI に対応
- 新規インストールでは FocusUI を既定のホームレイアウトとして使用
- システムテーマカラー、カスタムカラー、壁紙、カスタムフォント
- ナビゲーション方式とダッシュボードカードの設定
- 自動更新チェックの無効化

## モジュールと拡張機能

### APM

APM は Magisk に似たシステムモジュール機能を提供し、インストール、削除、有効化、無効化、一括操作、完全バックアップに対応します。

### KPM

KPM は `inline-hook` と `syscall-table-hook` 向けのカーネルモジュール機能を提供し、自動読み込みに対応します。

### APD Lua プラグイン

APD プラグインは `apd` のライフサイクル内で Lua スクリプトを実行します。システムファイルの変更やカーネルモジュールの注入は行いません。設定、クイックアクション、バックグラウンドデーモン、永続ログに対応します。

プラグインドキュメント：

- 英語：https://fp.mysqil.com/en/modules/plugin/
- 中国語：https://fp.mysqil.com/modules/plugin/

## セキュリティ

SuperKey は通常の Root セッションより強い権限を持ちます。弱い鍵や漏洩した鍵は、デバイスが不正に操作される原因になります。強固な鍵を使用し、外部に公開しないでください。

## 翻訳

英語と中国語を参照言語とします。翻訳の修正は対象言語のファイルのみを送信してください。新しい言語を追加する場合は、完成した翻訳ファイルを Pull Request に含めてください。

## ダウンロードとインストール

1. [リリースページ](https://github.com/LyraVoid/FolkPatch/releases/latest)から最新パッケージをダウンロードします。
2. Android デバイスにパッケージをインストールします。
3. アプリ内の案内に従ってセットアップし、モジュールの導入や実行時制御の有効化前に[ドキュメント](https://fp.mysqil.com/)を確認してください。

## 謝辞

- [KernelPatch](https://github.com/LyraVoid/KernelPatch)：Root 実装の基盤
- [Magisk](https://github.com/topjohnwu/Magisk)：magiskpolicy
- [KernelSU](https://github.com/tiann/KernelSU)：アプリ UI とモジュールシステムの参考
- [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)：インターフェース設計の参考
- [APatch](https://github.com/bmax121/APatch)：上流プロジェクト
- [MMRL](https://github.com/MMRLApp/MMRL)：モジュールリポジトリ形式の参考
- [Shizuku](https://github.com/RikkaApps/Shizuku)：内蔵 Shizuku サービス
- [LoliAPI](https://www.loliapi.com/docs/acg/)：壁紙図鑑のオンライン壁紙ソース

## ライセンス

FolkPatch は [GNU General Public License v3](https://www.gnu.org/licenses/gpl-3.0.html) の下で公開されています。

FolkPatch を改変した場合、または他のプロジェクトへ組み込んで配布する場合、プロジェクト全体を GPLv3 で公開する必要があります。バイナリを配布する場合は、完全で読み取り可能なソースコードを提供するか、提供を約束してください。本ソフトウェアは無保証で提供され、上記に違反した場合、GPLv3 の許諾は終了します。

## コミュニティ

- Telegram：[**@FolkPatch**](https://t.me/FolkPatch)
