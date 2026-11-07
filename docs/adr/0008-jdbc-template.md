# ADR-0008 DBアクセスは当面 JdbcTemplate とし、jOOQコード生成は保留する

- 状態: Superseded by ADR-0009 (2026-11-07, 本人の指示で MyBatis へ変更。当初は Accepted として記録)

## 決定
DBアクセスには Spring JDBC (JdbcTemplate) を使う。ADR-0002 で挙げた jOOQ は、コード生成に稼働中のMySQLが必要で、ローカル/CIのビルドがDB起動に依存してしまうため、MVPでは採用を見送る。

## 理由
- MVPのクエリは CRUD + 月次集計が中心で、明示的なSQLの方が見通しがよい。
- FlywayのDDLがスキーマの正であり続ける点は変わらない。
- ビルドをDBなしで再現可能に保ち、コンパイル検証を容易にする。

## 影響
- クエリが複雑化した時点で jOOQ (コード生成込み) への移行を再検討する。その場合も Repository 境界の内側で置き換える。
