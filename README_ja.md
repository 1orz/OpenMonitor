<div align="center">

<img src="icon/openmonitor_logo.svg" width="128" height="128" alt="OpenMonitor Logo" />

# OpenMonitor

**Jetpack Compose & Material 3 で構築された強力なオープンソース Android システムモニター**

CPU、GPU、バッテリー、FPS、温度、メモリ、プロセスのリアルタイム監視 — ROOT、Shizuku、ADB をサポート。

[![Release](https://img.shields.io/github/v/release/1orz/OpenMonitor?include_prereleases&logo=github&label=Release)](https://github.com/1orz/OpenMonitor/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen?logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![Stars](https://img.shields.io/github/stars/1orz/OpenMonitor?style=flat&logo=github&label=Stars)](https://github.com/1orz/OpenMonitor/stargazers)
[![License](https://img.shields.io/github/license/1orz/OpenMonitor?label=License)](https://github.com/1orz/OpenMonitor/blob/main/LICENSE)

[**ダウンロード**](https://github.com/1orz/OpenMonitor/releases/latest) · [**Issues**](https://github.com/1orz/OpenMonitor/issues) · [**QQチャンネル (中国語)**](https://pd.qq.com/s/ft7tc3xae) · [**QQグループ (中国語)**](https://qm.qq.com/cgi-bin/qm/qr?k=Kx6t5VIe6vwKSyk6gsf2CGCABO3V7qfe&jump_from=webapi&authKey=Qq+pLqZq/1Px+1g/r49xi2QDzcCfxxiH6kK5c2qdTNMvnggkmbbq6Aptw8oKruyz) · [**Telegram (中/英)**](https://t.me/OpenMonitor) · [**ウェブサイト**](https://om.cloudorz.com/)

[**English**](README.md) | [**简体中文**](README_zh-CN.md) | **日本語**

---

</div>

## Star 推移

<div align="center">

[![Star History Chart](https://api.star-history.com/svg?repos=1orz/OpenMonitor&type=Date)](https://star-history.com/#1orz/OpenMonitor&Date)

</div>

---

## 主な特徴

- CPU、GPU、バッテリー、FPS、メモリ、温度、プロセスのリアルタイム監視
- 複数の権限モード：ROOT（Magisk / KernelSU / APatch）、Shizuku、ADB、ベーシック
- フローティングオーバーレイによるアプリ横断のリアルタイム監視
- クイック設定タイルとホームスクリーンウィジェットで即座にアクセス
- FPS 録画、ジャンク検出、セッション再生、画像/CSV エクスポート
- 900 以上の SoC データベース内蔵、Vulkan・OpenGL の完全な列挙
- ハードウェアベースのキーアテステーションとデバイス整合性検証
- セルラーネットワーク解析 — RAT 別の信号、RRC / NAS / IMS シグナリングのキャプチャ、modem 側イベント、PCAP エクスポート
- Material 3 ダイナミックテーマ（システム / ライト / ダーク / AMOLED）
- Rust ベースの特権デーモンによる高性能・低オーバーヘッドのデータ収集
- 英語、簡体字中国語、繁体字中国語、日本語に対応

---

## 目次

- [はじめに](#はじめに)
- [権限モード](#権限モード)
- [機能詳細](#機能詳細)
  - [概要ダッシュボード](#概要ダッシュボード)
  - [CPU 分析](#cpu-分析)
  - [バッテリー](#バッテリー)
  - [FPS 録画](#fps-録画)
  - [プロセスマネージャー](#プロセスマネージャー)
  - [ハードウェア情報](#ハードウェア情報)
  - [セルラーネットワーク](#セルラーネットワーク)
  - [フローティングモニター](#フローティングモニター)
  - [キーアテステーション](#キーアテステーション)
  - [クイック設定タイル・ウィジェット](#クイック設定タイルウィジェット)
- [アーキテクチャ](#アーキテクチャ)
- [技術スタック](#技術スタック)
- [ソースからビルド](#ソースからビルド)
- [プライバシー](#プライバシー)
- [コントリビュート](#コントリビュート)
- [ライセンス](#ライセンス)

---

## はじめに

1. **ダウンロード** — [GitHub Releases](https://github.com/1orz/OpenMonitor/releases/latest) から最新の APK をダウンロードしてインストール。
2. **起動** — 初回起動時にプライバシーポリシーが表示されます。確認して同意してください。
3. **権限モードを選択** — アプリが利用可能な権限フレームワーク（ROOT、Shizuku、ADB）を自動検出し、最適なオプションを推奨します。特権アクセスが利用できない場合でも、ベーシックモードでコア監視機能を提供します。
4. **探索** — ボトムナビゲーションには3つのタブがあります：
   - **機能** — すべての監視モジュールへアクセス（ハードウェア、バッテリー、FPS、プロセス、フローティング、キーアテステーションなど）
   - **概要** — すべての主要指標を一覧できるリアルタイムダッシュボード
   - **設定** — テーマ、言語、権限モード、触覚フィードバックなどの設定

---

## 権限モード

OpenMonitor はデバイスの利用可能な権限に適応し、適切にフォールバックします：

| モード | 仕組み | 利用可能な機能 |
|:-------|:-------|:---------------|
| **ROOT** | Magisk / KernelSU / APatch による完全な特権アクセス。Rust デーモンが libsu 経由で自動起動。 | 全機能 — FPS、プロセス終了、GPU 周波数、完全な温度情報、フローティング、バッテリー電流/電力 |
| **Shizuku** | Shizuku マネージャーによる権限委譲。デーモンは Shizuku UserService として実行。 | FPS 監視、プロセス管理、拡張メトリクス |
| **ADB** | `adb shell` で手動サーバー起動。追加アプリ不要。 | Shizuku と同等。ROOT なしのデバイスに最適 |
| **ベーシック** | 特別な権限は不要。 | CPU 負荷/周波数、メモリ、バッテリー基本情報（読み取り可能な sysfs から） |

> **設定 > システム > 権限モード** からいつでも切り替え可能。

---

## 機能詳細

### 概要ダッシュボード

概要タブでデバイスのリアルタイム状態を包括的に把握：

- **CPU** — 総負荷率（色分け：緑 < 50%、黄 < 80%、赤 >= 80%）、平均周波数（MHz）、温度、オンライン/全コア数、コアごとのミニステータス
- **メモリ** — アークゲージで使用率を表示、物理 RAM 使用量/合計、空きメモリ、Swap 使用量、ZRam 圧縮率
- **GPU** — モデル名、現在/最大周波数、負荷率プログレスバー、アクティブガバナー
- **SoC 情報** — デバイス名、ベンダーロゴ、SoC モデル、製造プロセスバッジ、アーキテクチャ、ABI、CPU クラスタ構成
- **キャッシュ** — L1D / L1I / L2 / L3 キャッシュサイズ、ARM NEON サポート状態
- **システム情報チップ** — 電力（W）、バッテリー残量・電圧、温度、Android バージョン、稼働時間

すべての指標がリアルタイムで更新されます。

### CPU 分析

概要またはハードウェア情報からアクセスできる詳細な CPU コア分析：

- **クラスタビュー** — Little / Big / Prime クラスタごとにグループ化、各クラスタのガバナー、周波数範囲（最小–最大 MHz）、平均負荷
- **コアごとのステータス** — 各コアの番号、マイクロアーキテクチャ名、現在の負荷率、周波数（MHz）、オンライン/オフライン状態
- **SoC 詳細** — ハードウェア ID、アーキテクチャ、ABI、ガバナーポリシー、クラスタ構成と周波数範囲
- **キャッシュ階層** — コア/クラスタごとの L1D / L1I / L2 / L3 サイズ

### バッテリー

履歴追跡付きの詳細なバッテリー分析：

- **ステータスカード** — 現在の容量%、電圧（V）、温度（色分けヘルスインジケーター付き）、残り時間/充電時間の推定
- **履歴チャート** — バッテリー残量の時間推移を折れ線グラフで表示、フィルターチップで時間範囲を選択可能
- **放電分析** — 選択期間のトレンド可視化で消費パターンを把握
- **アプリ別消費ランキング** — アプリアイコン付きで各アプリのバッテリー消費を表示、推定消費%と mAh（使用状況統計の権限が必要 — 未付与の場合は許可を求めるカードが表示）
- **CSV エクスポート** — 共有ボタンからバッテリー履歴をCSVファイルとしてエクスポート

### FPS 録画

プロフェッショナルグレードのフレームレート監視とセッション管理：

**録画の開始**
1. **機能 > FPS 録画** に移動
2. **FPS フロート** スイッチをオンにしてオーバーレイを有効化
3. テストしたいゲームやアプリを開く — オーバーレイがリアルタイム FPS を表示
4. 完了したら OpenMonitor に戻ってセッションを終了

**セッションリスト**
- すべてのセッションがカード形式で表示、アプリアイコン、アプリ名、平均 FPS、時間、タイムスタンプ付き
- FPS 品質の色分け：緑（>= 50 fps）、黄（>= 30 fps）、赤（< 30 fps）
- 長押しで複数選択モード、一括削除対応
- 編集アイコンでセッションのリネームが可能

**セッション詳細と分析**

セッションをタップすると包括的な分析ビューが開きます：

- **デバイス情報** — CPU プラットフォーム、デバイスモデル、OS バージョン
- **セッションヘッダー** — アプリアイコン、名前、バージョン、パッケージ名、画面解像度、タイムスタンプ、時間
- **6つの主要指標**：
  - 最大 / 最小 / 平均 FPS（色分け）
  - FPS 分散（標準偏差）
  - スムーズネス（>= 45 fps のフレーム割合）
  - 5% Low（下位5パーセンタイル）
  - 最大バッテリー温度
  - 平均消費電力（W）
- **アプリ切り替えタイムライン** — 録画中のフォアグラウンドアプリの切り替えを表示する水平スクロールバー
- **インタラクティブチャート**（設定アイコンで切り替え）：
  - FPS と温度（CPU 温度、GPU 温度、CPU/GPU 使用率）
  - フレームタイム（最大フレームタイム、ミリ秒）
  - CPU/GPU 使用率（総 CPU、GPU、クラスタごとの負荷）
  - CPU/GPU 周波数（クラスタごと、GPU 周波数）
  - ジャンクチャート（フレームごとのジャンクとビッグジャンクの差分）
  - 電力/バッテリー（電力 W、バッテリー%）
  - バッテリー電流（mA、最大/最小/平均）
  - 温度（CPU、GPU、バッテリー）
- 各チャートで **個別データシリーズの表示/非表示** が可能
- すべてのチャートが **スクロール・ズーム** 対応

**エクスポートオプション**
- **PNG として保存** — すべてのチャート、デバイス情報、QR コード付きブランドバナーを含む共有可能な画像を生成
- **CSV として保存** — 外部分析用にスプレッドシート形式で生データをエクスポート

### プロセスマネージャー

リアルタイムのプロセス監視と管理機能：

- **検索** — 検索バーで名前によるプロセスフィルタリング
- **フィルターモード** — 「全プロセス」と「アプリのみ」（インストール済みアプリ）の切り替え
- **ソートオプション** — CPU%、メモリ（MB）、名前（アルファベット順）、PID でソート
- **プロセスリスト** — 各エントリにアプリアイコン（またはデフォルトアイコン）、プロセス名、PID、CPU 使用率（色分け：緑 < 5%、黄 5–20%、赤 > 20%）、メモリ（MB）を表示
- **プロセス詳細** — 任意のプロセスをタップで詳細情報を表示
- **プロセス数** — フィルター後のプロセス総数を上部に表示

> ROOT および Shizuku モードでプロセス終了機能が利用可能。

### ハードウェア情報

包括的なデバイスハードウェア仕様：

- **プロセッサ** — ベンダーロゴ、SoC 名、製造プロセスバッジ、コア数、64ビットバッジ、クラスタごとの CPU 構成と周波数範囲、ガバナー、アーキテクチャ、ABI。CPU 分析ページへのリンク付き。
- **GPU** — モデル/レンダラー名、チップ ID、ベンダー、GMEM サイズ、シェーダーコア数、バス幅、L2 キャッシュ（Mali GPU）。**Vulkan 機能** と **OpenGL ES 機能** の閲覧ボタン。
- **ディスプレイ** — 解像度、リフレッシュレート、PPI、物理サイズ（インチ/mm）、アスペクト比、密度バケット、広色域、HDR 対応、パネル名。対応するすべての解像度とリフレッシュレートを一覧表示。
- **メモリ** — RAM サイズラベル（マーケティングサイズに丸め）、合計/使用/空き使用バー。ZRam セクション（利用可能な場合）に圧縮率を表示。
- **ストレージ** — マーケティング容量、ストレージタイプ（例：UFS 3.1）、使用バー、パーティション内訳と使用容量、ファイルシステムタイプ、ブロックサイズ。

**Vulkan 情報画面** — GPU 機能の詳細ビュー：
- デバイスID（名前、ベンダー、デバイスID、タイプ、APIバージョン、ドライバーバージョン）
- メモリヒープと device-local タグ
- 制限（最大画像サイズ、コンピュート呼び出し数、バッファ範囲、異方性）
- コア機能（robust buffer access、ジオメトリ/テッセレーションシェーダー、マルチビューポートなど）
- キューファミリーとフラグ・タイムスタンプビット
- デバイスおよびインスタンスの拡張リスト

### セルラーネットワーク

セルラー無線を深く検査する機能で、RF 状態 / 生シグナリング / modem イベントをカバーします。画面上部に SIM スロットセレクタ（デュアル SIM 端末で切り替え可能）、下に 4 つのサブタブ：

**Device（デバイス）** — RF と modem の状態スナップショット

- **DIAG セッションカード** — modem 直結 DIAG ブリッジ（AF_QIPCRTR / QMI）の到達性。デーモンがオフラインなら自動で折りたたまれ、公開 Telephony API にフォールバック
- **サービングネットワーク** — オペレータ、PLMN、MCC/MNC、現在の RAT（NR5G / LTE / WCDMA / GSM）、LTE CA アクティブフラグ、NR5G ARFCN
- **サービングセル（modem 側）** — LTE Global Cell ID、ECI、TAC、PCI、UE 状態、NR5G NCI、PCI、ARFCN
- **RF バンド / チャネル** — 各 RAT のアクティブなバンドとチャネル
- **Rx チェイン** — Rx0…Rx3 のフェーズ分割、Tx チェインの in-traffic / idle 状態。公開 API では取得不可で、modem DIAG 経由のみ
- **バンド能力** — サポート LTE バンドのビットマスク、完全なサポートバンドリスト、NR5G FR1 バンド
- **Modem アイデンティティ** — Baseband バージョン、Operating Mode、VoLTE / VT / Wi-Fi Calling の状態
- **フィールド可用性診断** — 各フィールドに「利用可能 / ロック / 非対応」チップが付き、タップすると現在の権限モードで解除できない理由が表示されます

**Network（ネットワーク）** — RAT 別サービング + 隣接セル

ページは現在 SIM のサービング RAT に合わせて適応し、**実際にアタッチしている RAT のみ**を表示（NR / LTE / GSM / WCDMA / TD-SCDMA）— 固定の複数タブカルーセルではありません：

- **サービングセル構成** — PCI / EARFCN / NRARFCN / UARFCN / ARFCN、バンド、帯域幅（MHz）、CQI、各 RAT の識別子（TAC / ECI / NCI / LAC / CI / BSIC / PSC）
- **リアルタイム信号** — RSRP / RSRQ / RSSI / SINR（LTE）、SS-RSRP / SS-RSRQ / SS-SINR / CSI-RSRP（NR5G）のライブトレンドチャート
- **隣接セル** — PCI と信号強度付きの検出隣接セル棒グラフ
- **キャリアアグリゲーションマトリクス** — コンポーネントキャリアごとの役割（PCC / SCC）、状態（Activated / Deactivated）、DL/UL 帯域幅、UL チャネル
- **NR 周波数レンジ** — バケットラベル（Low / Mid / High / mmWave）と NSA / SA 状態

**Signaling（シグナリング）** — 生プロトコルトレース

modem からの RRC / NR-RRC / NAS / NR-NAS / IMS シグナリングをライブキャプチャ：

- **ストリームビュー** — メソッド名、方向（UL / DL）、RAT、バイト長を表示するスクロール可能なフレームリスト。ピンアンカー型のオートスクロール（手動で戻すとフォローが一時停止）
- **フィルターチップ** — プロトコルファミリー（IMS / RRC / 5G-RRC / NAS / 5G-NAS / その他）+ 方向 + あいまい検索（メソッド / IE / hex）
- **詳細シート** — チャネル、方向、RAT、Log Code、PCI、EARFCN/ARFCN、完全な PDU バイト。デコーダが配線済みの箇所は ASN.1 ツリービュー、IMS の SIP メッセージは完全なヘッダ付きで描画
- **PCAP 録画** — **Record** をタップして開始、もう一度タップして停止すると `.pcap` を共有 / パスをコピーでき、Wireshark にそのまま渡せます
- **複数選択エクスポート** — 長押しで選択モードに入り、複数行を構造化 JSON アーカイブとして一括出力

**Events（イベント）** — Modem 側状態ログ

- アタッチ / デタッチ / ハンドオーバー / セル再選択 / 通話確立 / IMS 登録のライブエントリ
- 結果チップ：成功 / 失敗 / 進行中
- イベントごとの詳細シート：タイムスタンプ、RAT、カテゴリ、種別、構造化ペイロード

> **権限カバレッジ**：Telephony API データ（PCI、EARFCN、RSRP/RSRQ、サービングセルの基本情報）はどの端末でも動作します。Modem 直結 DIAG（Rx チェイン、バンドビットマスク、Baseband、生シグナリング、modem イベント）は現状 **Qualcomm Snapdragon** 端末の **ROOT** または **Shizuku** モードのみ対応。Device タブのフィールド可用性パネルで、現在のモードで解除されるフィールドが明示されます。
>
> **必要な権限**：初回アクセス時に**電話状態**と**位置情報**を要求します。これらがないと OS がセル ID と信号強度をマスクするため、root でも復元できません。

### フローティングモニター

常に最前面に表示される監視オーバーレイ：

- **複数モニター** — FPS、CPU、メモリ、GPU、温度のオーバーレイを個別に切り替え
- **権限ハンドリング** — オーバーレイ権限が未付与の場合、システム設定への誘導カードを表示
- **クイックアクセス** — クイック設定タイルからも切り替え可能

オーバーレイはすべてのアプリの上に表示され、ゲームやベンチマーク中の監視に最適です。

### キーアテステーション

ハードウェアベースのデバイスセキュリティ検証：

- **信頼バナー** — 証明書チェーンが信頼されているかを明確に表示
- **ブート状態** — Verified / Self-Signed / Failed のステータス、ブートローダーロック状態（色分けアイコン）
- **アテステーション情報** — アテステーションバージョン、セキュリティレベル（TEE / StrongBox / ソフトウェア）、Keymaster バージョン、チャレンジ、ユニークID
- **失効リスト** — デバイス証明書失効ステータス、ネットワーク取得対応、エントリ数とキャッシュ有効期限
- **証明書チェーン** — 証明書ごとのサブジェクト、発行者、有効期限、失効ステータスと理由
- **認可リスト** — フィールドごとの表示、HW/SW バッジでハードウェアまたはソフトウェアレベルを区別
- **オプションメニュー：**
  - 「Attest Key を使用」/「StrongBox を使用」/「Shizuku モード」の切り替え
  - 永続的な Attest Key のリセット
  - 証明書チェーンを `.p7b` ファイルとして保存
  - ファイルから証明書チェーンを読み込み

### クイック設定タイル・ウィジェット

**クイック設定タイル**
- クイック設定パネルに「Monitor」タイルを追加
- タップでフローティングモニターの表示/非表示を切り替え
- アクティブ/非アクティブ状態を表示

**ホームスクリーンウィジェット**
- 3つの主要指標を表示：CPU 負荷%、CPU 温度、メモリ使用率%
- 自動更新、任意の指標をタップで OpenMonitor を起動
- 軽量 — `/proc/stat`、`/sys/class/thermal/`、`/proc/meminfo` から直接読み取り

---

## アーキテクチャ

```
┌──────────────────────────────────────────────────────────────┐
│                           app                                │
│    MainActivity · Splash · Agreement · Settings · Nav3       │
├──────────┬──────────┬──────────┬──────────┬──────────────────┤
│ feature- │ feature- │ feature- │ feature- │    feature-      │
│ overview │ battery  │   fps    │ process  │    hardware      │
├──────────┼──────────┼──────────┼──────────┼──────────────────┤
│ feature- │ feature- │ feature- │ feature- │    feature-      │
│  float   │   cpu    │  signal  │signaling │     events       │
├──────────┴──────────┴──────────┴──────────┼──────────────────┤
│         feature-key-attestation           │     service      │
├───────────────────────────────────────────┴──────────────────┤
│  core-ui   │  core-data  │  core-model  │   core-common      │
├────────────┼─────────────┼──────────────┼────────────────────┤
│            │core-database│core-daemon-api│                   │
├────────────┴─────────────┴──────────────┴────────────────────┤
│             daemon-rust（Rust 特権デーモン）                    │
│  AF_UNIX IPC · sysfs ポーリング · SurfaceFlinger FPS · DIAG  │
│  QMI / AF_QIPCRTR · RRC / NAS PDU キャプチャ · PCAP ライタ     │
└──────────────────────────────────────────────────────────────┘
```

### Rust 特権デーモン (`daemon-rust`)

昇格された権限で実行される軽量な Rust バイナリで、通常のアプリではアクセスできないシステムデータを収集：

- **ダブルフォーク デーモン化** — AF_UNIX ソケット IPC
- **長さプレフィクス JSON** ワイヤプロトコル
- **マルチスレッドサンプリング** — CPU 周波数/負荷、温度ゾーン、GPU 状態、バッテリー電流/電力、SurfaceFlinger FPS
- **Modem DIAG ブリッジ** — AF_QIPCRTR / QMI サービス（信号・セル用 NAS、デバイス情報用 DMS）が Cellular Network の Device タブを支え、RRC / NAS / IMS PDU キャプチャと PCAP ライタが Signaling / Events タブを駆動（Qualcomm Snapdragon のみ）
- **APK v2 証明書ピニング** — クライアント認証
- **起動モード** — ROOT (libsu)、Shizuku (UserService)、ADB (CLI)

### ネイティブレイヤー

- **cpuinfo-bridge** — [cpuinfo](https://github.com/pytorch/cpuinfo) への JNI ブリッジ、詳細な CPU トポロジーとキャッシュ情報を取得
- **vulkan-info** — JNI による Vulkan 機能列挙

---

## 技術スタック

| カテゴリ | 技術 | バージョン |
|:---------|:-----|:-----------|
| 言語 | Kotlin | 2.3 |
| UI | Jetpack Compose + Material 3 | BOM 2026.03 |
| ナビゲーション | Navigation3 (runtime + ui) | 1.1.0 |
| DI | Dagger Hilt | 2.59.2 |
| データベース | Room | 2.8.4 |
| 非同期 | Kotlin Coroutines | 1.10.2 |
| Root アクセス | libsu | 6.0.0 |
| 権限 | Shizuku API | 13.1.5 |
| バックグラウンド | WorkManager | 2.11.2 |
| チャート | Vico | 3.1.0 |
| テーマ | Material Kolor | 4.1.1 |
| アナリティクス | Firebase Analytics | BOM 34.12.0 |
| デーモン | Rust (daemon-rust) | Edition 2021 |
| ネイティブ | cpuinfo + Vulkan Info | arm64-v8a |
| Markdown | multiplatform-markdown-renderer | 0.40.2 |

---

## ソースからビルド

### 前提条件

- JDK 17+
- Android SDK（compile SDK 36）
- CMake 3.22.1（Gradle が自動インストール）
- NDK（ネイティブ cpuinfo・Vulkan モジュール用）

### ビルド

```bash
git clone https://github.com/1orz/OpenMonitor-src.git
cd OpenMonitor-src
./gradlew assembleDebug
```

APK は `app/build/outputs/apk/debug/` に生成されます。

### aarch64 Linux（Termux）

ARM64 Linux デバイスでのビルド：

```bash
bash scripts/setup-aarch64-build-env.sh
./gradlew assembleDebug
```

セットアップスクリプトがプラットフォーム固有のツールチェーン適応（aapt2、CMake、NDK ラッパー）を自動処理します。

---

## プライバシー

- **すべての監視データはデバイスに保存** — システム指標のテレメトリなし
- **Firebase Analytics** はオプションかつ匿名
- **すべての操作は読み取り専用** — OpenMonitor はシステムを変更しません
- **完全オープンソース** — すべてのコードを確認できます

詳細は [プライバシーポリシー](https://om.cloudorz.com/) をご覧ください。

---

## コントリビュート

コントリビューション歓迎！[GitHub](https://github.com/1orz/OpenMonitor/issues) で Issue や Pull Request をお気軽にどうぞ。

---

## ライセンス

詳細は [LICENSE](LICENSE) ファイルをご覧ください。

---

<div align="center">

**Kotlin、Rust、Compose で構築**

</div>
