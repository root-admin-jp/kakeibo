# ADR-0009 DBアクセスは MyBatis を使う（jOOQ / JdbcTemplate から変更）

- 状態: Accepted (2026-11-07, 本人の指示「JOOQではなく、mybatisを使って」による)

## 決定
DBアクセスには MyBatis（mybatis-spring-boot-starter 3.0.3）を使う。
- SQLは XML Mapper（`apps/api/src/main/resources/mapper/*.xml`）に置き、Mapperインターフェース（`jp.kakeibo.api.db.*Mapper`）と namespace / メソッド名で対応づける。
- Repository クラスは従来どおりの境界として残し、内部実装だけを Mapper 呼び出しに置き換える。Controller 側の変更はない。
- 列名→プロパティ名は `map-underscore-to-camel-case` で対応づける。生成キーは `useGeneratedKeys` で Row の `id` に書き戻す。

## 置き換える決定
- 当初決定（C / ADR-0002）の jOOQ は採用しない。
- ADR-0008 の JdbcTemplate は本ADRで Superseded とする。

## 理由
- 本人の明示的な指定による。
- jOOQ のコード生成（稼働中MySQLが必要でビルドがDB依存になる）という ADR-0008 の懸念は、MyBatis でも生じない。
- SQLを XML に分離することで、KotlinコードからSQLが見通しやすくなり、動的SQL（取引一覧のフィルタ等）は `<if>` で表現できる。

## 影響
- 依存から `spring-boot-starter-jdbc` の直接利用を外し、`mybatis-spring-boot-starter` に置き換えた（DataSource 自体は starter 経由で従来どおり自動構成される）。
- Row型は MyBatis のマッピング（無引数生成＋プロパティ設定）に合わせ、`var` + デフォルト値に変更した。
- Flyway の DDL がスキーマの正である点は変わらない。
