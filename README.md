# Pholio

セルフホスト型のフォトビューアー Web アプリケーション。自宅 NAS（UGREEN DXP4800 Plus）上の Docker で動作し、Google フォトに近い体験で写真・動画を閲覧できる。

## 特徴

- **3 タブ構成**: ホーム / アルバム / 設定
- **グリッド表示**: 1〜8 列で可変、写真・動画のサムネイル一覧
- **複数選択**: 長押し・ホバーから選択モードに入り、削除（論理削除）やアルバム追加が可能
- **写真詳細**: 拡大表示、横スワイプで前後移動、戻り時のスクロール位置復元
- **アルバム**: 物理フォルダとは独立した、アプリ管理の独自アルバム概念
- **並び替え**: 撮影日時順 / 名前順 / 登録日時順（各昇順降順）に加え、注目機能の **ランダム**
- **論理削除**: 物理ファイルは一切削除・移動しない安全設計

## 技術スタック

| レイヤー | 採用 |
|---|---|
| バックエンド | Ktor (Kotlin) |
| フロントエンド | React + TypeScript + Vite + MUI + Bun |
| DB | SQLite |
| サムネイル生成 | libvips（画像） / ffmpeg（動画） |
| デプロイ | Docker / docker-compose |

## 起動方法

1. `.env.example` を参考に `.env` を作成する。
2. `PHOTOS_PATH` に写真ライブラリの絶対パスを設定する。
3. `DATA_PATH` に Pholio の永続データ保存先を設定する。
4. `PUID` / `PGID` に Docker コンテナ実行ユーザーを設定する。
5. `docker compose up --build -d` を実行する。

```env
PUID=1000
PGID=1000
PHOTOS_PATH=/absolute/path/to/Photos
DATA_PATH=/absolute/path/to/pholio-data
PORT=8080
```

起動後は以下を確認する。

- アプリ: http://localhost:8080/
- Health API: http://localhost:8080/api/v1/health
- OpenAPI JSON: http://localhost:8080/api/v1/openapi.json
- Swagger UI: http://localhost:8080/api/docs

## 運用メモ

- `PHOTOS_PATH` はコンテナ内 `/photos` に読み取り専用で mount される。
- `DATA_PATH` はコンテナ内 `/data` に mount され、`pholio.sqlite3`、サムネイル、ジョブ状態を保存する。
- `SCAN_ON_STARTUP=true` の場合、DB が空なら full scan、既存 DB なら diff scan を自動実行する。
- `THUMBNAIL_WORKERS` の既定値は `2`。
- `preview_lg` サムネイルは lazy 生成する。
- 物理写真は削除・移動・リネームしない。アプリ内の除外は論理削除として扱う。

## 実装済み Backend API

- 写真一覧 / 詳細 / 前後写真 / 原本配信 / サムネイル配信
- 写真のライブラリ除外 / 復元 / 除外済み一覧
- アルバム一覧 / 作成 / 詳細 / 名称変更 / 削除
- アルバムへの写真追加 / 除去 / アルバム内写真一覧
- index status / full・diff scan 開始 / scan cancel

## バックアップ

必須バックアップ対象は `DATA_PATH` 配下。

- `pholio.sqlite3`
- `pholio.sqlite3-wal`
- `pholio.sqlite3-shm`
- 将来の設定・ジョブ状態

`thumbs/` は再生成可能だが、復旧時間を短くしたい場合は一緒にバックアップする。

## 検証

```bash
./gradlew :backend:test
docker run --rm -v "$PWD/frontend:/work" -w /work oven/bun:1.3.14-slim bun run build
./scripts/smoke_docker.sh
./scripts/e2e_docker.sh
```

## ドキュメント

- [詳細設計書](docs/design.md)

## ステータス

実装中。v1 の土台、backend media API、SQLite index、thumbnail queue、フロントエンド実 UI、Docker smoke / e2e を構築済み。

## ライセンス

MIT
