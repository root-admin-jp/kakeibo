#!/usr/bin/env python3
"""Kakeibo 設計書ジェネレータ。

ソース（DDL / Controller / Nuxt画面 / 要件・ADR）を正として、設計書一式を Markdown で生成する。
手書きで直す場所ではなく、生成元（コードとDDL）を直して再生成すること。

生成物:
  README.md                  設計書インデックス + システム概要
  database/table-definition.md  テーブル定義書（全テーブル・全カラム・キー・FK・初期データ）
  database/er-diagram.md        ER図 (Mermaid) + リレーション一覧
  api/api-definition.md         API定義書（エンドポイント一覧 + DTO）
  screens/screen-definition.md  画面定義書（画面一覧・項目・利用API）+ 画面遷移図 (Mermaid)
  requirements/ , adr/ , decisions.md  本体リポジトリの文書スナップショット

使い方: python3 generate.py --source <kakeibo repo root> --out <output dir> [--commit <sha>]
外部ライブラリ不使用 (Python 3 標準のみ)。
"""
import argparse
import re
import shutil
import sys
from pathlib import Path

# ---------------------------------------------------------------- DDL 解析

class Column:
    def __init__(self, name, type_, nullable, default, auto_inc, extra):
        self.name, self.type, self.nullable = name, type_, nullable
        self.default, self.auto_inc, self.extra = default, auto_inc, extra


class Table:
    def __init__(self, name):
        self.name = name
        self.columns = []        # list[Column]
        self.pk = []             # list[str]
        self.uniques = []        # list[(key名, [列,...])]
        self.indexes = []        # list[(index名, [列,...])]
        self.fks = []            # list[(制約名, 列, 参照テーブル, 参照列)]


def split_top_level(body: str):
    """カンマ区切りを、括弧の深さとクォートを考慮して分割する。"""
    parts, depth, cur, in_q = [], 0, "", False
    for ch in body:
        if ch == "'":
            in_q = not in_q
        if not in_q:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            elif ch == "," and depth == 0:
                parts.append(cur.strip())
                cur = ""
                continue
        cur += ch
    if cur.strip():
        parts.append(cur.strip())
    return parts


def parse_column(item: str):
    """カラム定義1件（`name TYPE ...`）を Column に変換する。CREATE TABLE 内と ADD COLUMN で共用。"""
    cm = re.match(r"(\w+)\s+(.+)", item, re.S)
    if not cm:
        return None
    col_name, rest = cm.group(1), cm.group(2).strip()
    # 型: ENUM(...) / VARCHAR(255) / BIGINT UNSIGNED / TIMESTAMP 等を先頭から取る
    tm = re.match(r"([A-Z]+(?:\s+UNSIGNED)?(?:\s*\([^)]*\))?)", rest, re.I)
    # 型名は原文どおり（ENUM値の大小文字を保持）。空白だけ正規化する
    type_ = re.sub(r"\s+", " ", tm.group(1)) if tm else rest.split()[0]
    tail = rest[len(tm.group(1)):] if tm else ""
    nullable = "NOT NULL" not in tail.upper()
    dm = re.search(r"DEFAULT\s+([^\s,]+(?:\s*\([^)]*\))?)", tail, re.I)
    default = dm.group(1) if dm else "-"
    if "DEFAULT CURRENT_TIMESTAMP ON UPDATE" in tail.upper():
        default = "CURRENT_TIMESTAMP (更新時も)"
    return Column(col_name, type_, nullable, default,
                  "AUTO_INCREMENT" in tail.upper(), tail.strip())


def apply_ddl(tables, masters, sql: str):
    """1マイグレーション分の SQL を実効スキーマへ反映する。

    CREATE TABLE の追加に加え、ALTER TABLE の DROP COLUMN / ADD COLUMN も適用する。
    マイグレーションを版順に全て適用することで、V2 以降の変更（テーブル追加・列削除など）
    を含めた現在のスキーマが得られる。
    """
    for m in re.finditer(r"CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\) ENGINE=", sql, re.S):
        name, body = m.group(1), m.group(2)
        t = Table(name)
        for item in split_top_level(body):
            up = item.upper()
            if up.startswith("PRIMARY KEY"):
                t.pk = re.findall(r"\(([^)]+)\)", item)[0].replace(" ", "").split(",")
            elif up.startswith("UNIQUE KEY"):
                km = re.match(r"UNIQUE KEY (\w+) \(([^)]+)\)", item)
                t.uniques.append((km.group(1), km.group(2).replace(" ", "").split(",")))
            elif up.startswith("INDEX") or up.startswith("KEY"):
                km = re.match(r"(?:INDEX|KEY) (\w+) \(([^)]+)\)", item)
                if km:
                    t.indexes.append((km.group(1), km.group(2).replace(" ", "").split(",")))
            elif up.startswith("CONSTRAINT"):
                fm = re.search(r"CONSTRAINT\s+(\w+)\s+FOREIGN\s+KEY\s*\((\w+)\)\s+REFERENCES\s+(\w+)\s*\((\w+)\)", item)
                if fm:
                    t.fks.append((fm.group(1), fm.group(2), fm.group(3), fm.group(4)))
            else:
                col = parse_column(item)
                if col:
                    t.columns.append(col)
        tables[name] = t
    # financial_institutions の初期データ
    im = re.search(r"INSERT INTO financial_institutions \(code, name, kind\) VALUES(.*?)ON DUPLICATE", sql, re.S)
    if im:
        masters.extend(re.findall(r"\('([^']+)',\s*'([^']+)',\s*'([^']+)'\)", im.group(1)))
    # ALTER TABLE（DROP COLUMN / ADD COLUMN のみ対応。その他の変更は無視する）
    for am in re.finditer(r"ALTER TABLE (\w+)\s+(.*?);", sql, re.S):
        t = tables.get(am.group(1))
        if t is None:
            continue
        for action in split_top_level(am.group(2)):
            dm = re.match(r"DROP COLUMN (\w+)", action, re.I)
            if dm:
                col = dm.group(1)
                t.columns = [c for c in t.columns if c.name != col]
                t.pk = [c for c in t.pk if c != col]
                t.uniques = [(k, [c for c in cols if c != col]) for k, cols in t.uniques]
                t.indexes = [(k, [c for c in cols if c != col]) for k, cols in t.indexes]
                t.fks = [fk for fk in t.fks if fk[1] != col]
                continue
            adm = re.match(r"ADD COLUMN (.+)", action, re.I | re.S)
            if adm:
                col = parse_column(adm.group(1).strip())
                if col:
                    t.columns.append(col)
    return tables, masters


def parse_ddl(sql: str):
    tables, masters = {}, []
    apply_ddl(tables, masters, sql)
    return tables, masters


# ---------------------------------------------------------------- API 解析

def parse_controllers(api_src: Path):
    groups = []
    for f in sorted(api_src.rglob("*Controller.kt")):
        text = f.read_text(encoding="utf-8")
        base_m = re.search(r'@RequestMapping\("([^"]+)"\)', text)
        base = base_m.group(1) if base_m else ""
        endpoints = []
        for m in re.finditer(
                r'@(Get|Post|Put|Delete|Patch)Mapping(?:\(([^)]*)\))?[^f]*?fun\s+(\w+)', text, re.S):
            method, args, fun = m.group(1).upper(), m.group(2) or "", m.group(3)
            path_m = re.search(r'"([^"]*)"', args)
            path = (path_m.group(1) if path_m else "")
            produces = re.search(r'produces\s*=\s*\["([^"]+)"\]', args)
            consumes = re.search(r'consumes\s*=\s*\["([^"]+)"\]', args)
            note = produces.group(1) if produces else (consumes.group(1) if consumes else "")
            endpoints.append((method, base + path, fun, note))
        dtos = []
        for m in re.finditer(r"data class (\w+)\(", text):
            # 対応する閉じ括弧までを深さカウントで切り出す（Size(min=..., max=...) 等の入れ子対応）
            depth, i = 1, m.end()
            while i < len(text) and depth:
                if text[i] == "(":
                    depth += 1
                elif text[i] == ")":
                    depth -= 1
                i += 1
            fields = [x.strip() for x in split_top_level(text[m.end():i - 1]) if x.strip()]
            dtos.append((m.group(1), fields))
        groups.append((f.stem.replace("Controller", ""), f.name, endpoints, dtos))
    return groups


# ---------------------------------------------------------------- 画面解析

def parse_screens(web_root: Path):
    screens = []
    pages = web_root / "app" / "pages"
    for f in sorted(pages.glob("*.vue")):
        text = f.read_text(encoding="utf-8")
        route = "/" if f.stem == "index" else f"/{f.stem}"
        hm = re.search(r"<h[12][^>]*>([^<]+)</h[12]>", text)
        name = hm.group(1).strip() if hm else f.stem
        sections = [s.strip() for s in re.findall(r"<h3[^>]*>([^<]+)</h3>", text)]
        apis = []
        # 引用符で囲まれた文字列リテラルを全部拾い、その中の /api/ パスを抽出する
        # （変数経由のパスや三項演算子内のパスも拾うため）
        for lit in re.findall(r"'([^']*)'|\"([^\"]*)\"|`([^`]*)`", text):
            s = next((x for x in lit if x), "")
            for a in re.findall(r"(/api/[^\s'\"`]+)", s):
                a = re.sub(r"\$\{[^}]+\}", "{param}", a).rstrip("`'\")")
                if a not in apis:
                    apis.append(a)
        inputs = []
        for tag in re.findall(r"<input\b[^>]*>", text):
            tm = re.search(r'type="(\w+)"', tag)
            inputs.append(tm.group(1) if tm else "text")
        inputs = sorted(set(inputs))
        selects = len(re.findall(r"<select", text))
        textareas = len(re.findall(r"<textarea", text))
        screens.append(dict(route=route, name=name, file=f"apps/web/app/pages/{f.name}",
                            sections=sections, apis=apis, inputs=inputs,
                            selects=selects, textareas=textareas))
    return screens


# ---------------------------------------------------------------- 生成

def mermaid_type(type_: str) -> str:
    return re.sub(r"[^A-Za-z0-9]+", "_", type_).strip("_")


def gen_table_definition(tables, masters):
    lines = ["# テーブル定義書", "",
             "生成元: `db/migration/V*.sql`（Flyway マイグレーションを版順に全て適用した実効スキーマ）。金額は JPY の整数。金融機関の認証情報は保存しない。",
             "テナント分離: 全業務テーブルは `household_id` で分離し、1テナント = 1オーナーユーザー（ADR-0007）。", "",
             f"テーブル数: {len(tables)}", ""]
    lines.append("| テーブル | カラム数 | 主キー | 備考 |")
    lines.append("|---|---|---|---|")
    remarks = {
        "users": "ログインユーザー", "households": "テナント（1オーナー専用）",
        "financial_institutions": "金融機関マスタ（初期データあり）", "accounts": "口座（残高は取引から算出）",
        "categories": "カテゴリ（2階層: parent_id で親子）", "transactions": "取引（収入/支出/振替、論理削除）",
        "budgets": "予算（カテゴリ×月）", "import_jobs": "CSV取込ジョブ履歴",
        "institution_connections": "金融機関連携（外部参照のみ保持）",
        "login_codes": "OTPログインコード（ハッシュのみ保持、ワンタイム）",
        "passkey_credentials": "パスキー資格情報（公開鍵のみ保持）",
        "webauthn_challenges": "WebAuthnチャレンジ（ワンタイム、短時間で失効）",
    }
    for name, t in tables.items():
        lines.append(f"| [{name}](#{name}) | {len(t.columns)} | {', '.join(t.pk) or '-'} | {remarks.get(name, '')} |")
    lines.append("")
    for name, t in tables.items():
        lines += [f"## {name}", "", f"{remarks.get(name, '')}", "",
                  "| # | カラム | 型 | NULL | デフォルト | その他 |",
                  "|---|---|---|---|---|---|"]
        fk_cols = {fk[1]: f"{fk[2]}.{fk[3]}" for fk in t.fks}
        for i, c in enumerate(t.columns, 1):
            etc = []
            if c.name in t.pk:
                etc.append("PK")
            if c.auto_inc:
                etc.append("AUTO_INCREMENT")
            if c.name in fk_cols:
                etc.append(f"FK → {fk_cols[c.name]}")
            for kname, cols in t.uniques:
                if c.name in cols:
                    etc.append(f"UNIQUE({kname})")
            lines.append(f"| {i} | {c.name} | {c.type} | {'可' if c.nullable else '不可'} | {c.default} | {', '.join(etc)} |")
        if t.indexes:
            lines += ["", "インデックス: " + ", ".join(f"{n}({', '.join(cols)})" for n, cols in t.indexes)]
        lines.append("")
    if masters:
        lines += ["## 初期データ: financial_institutions", "",
                  "| code | 名称 | 種別 |", "|---|---|---|"]
        for code, nm, kind in masters:
            lines.append(f"| {code} | {nm} | {kind} |")
        lines.append("")
    return "\n".join(lines)


def gen_er(tables):
    lines = ["# ER図", "", "生成元: `db/migration/V*.sql`（全マイグレーション適用後）の外部キー制約。", "", "```mermaid", "erDiagram"]
    for name, t in tables.items():
        lines.append(f"    {name} {{")
        fk_cols = {fk[1] for fk in t.fks}
        uniq_cols = {c for _, cols in t.uniques for c in cols}
        for c in t.columns:
            mark = ""
            if c.name in t.pk:
                mark = " PK"
            elif c.name in fk_cols:
                mark = " FK"
            elif c.name in uniq_cols:
                mark = " UK"
            lines.append(f"        {mermaid_type(c.type)} {c.name}{mark}")
        lines.append("    }")
    for name, t in tables.items():
        for fk_name, col, ref_table, ref_col in t.fks:
            lines.append(f'    {ref_table} ||--o{{ {name} : "{fk_name}: {col}"')
    lines += ["```", "", "## リレーション一覧", "", "| 制約名 | 参照元 | 参照先 |", "|---|---|---|"]
    for name, t in tables.items():
        for fk_name, col, ref_table, ref_col in t.fks:
            lines.append(f"| {fk_name} | {name}.{col} | {ref_table}.{ref_col} |")
    lines.append("")
    return "\n".join(lines)


def gen_api(groups):
    total = sum(len(g[2]) for g in groups)
    lines = ["# API定義書", "",
             "生成元: `apps/api` の Controller。認証は JWT (Bearer)。OpenAPI の正は API 起動時の `/swagger-ui.html`、本書は一覧用の生成ドキュメント。",
             f"エンドポイント数: {total}", ""]
    for group, fname, endpoints, dtos in groups:
        lines += [f"## {group} (`{fname}`)", "", "| メソッド | パス | ハンドラ | 備考 |", "|---|---|---|---|"]
        for method, path, fun, note in endpoints:
            lines.append(f"| {method} | `{path}` | {fun} | {note} |")
        lines.append("")
        for dname, fields in dtos:
            lines += [f"DTO `{dname}`: " + ", ".join(f"`{x}`" for x in fields), ""]
    return "\n".join(lines)


def gen_screens(screens):
    lines = ["# 画面定義書", "",
             "生成元: `apps/web/app/pages/*.vue`。共通レイアウト（ヘッダーナビ）は `apps/web/app/layouts/default.vue`。未ログイン時はログイン画面へ誘導する。",
             "", "## 画面一覧", "", "| ルート | 画面名 | 画面ファイル | 主なセクション | 利用API |",
             "|---|---|---|---|---|"]
    for s in screens:
        lines.append(f"| `{s['route']}` | {s['name']} | `{s['file']}` | {', '.join(s['sections']) or '-'} | "
                     + ", ".join(f"`{a}`" for a in s['apis']) + " |")
    lines += ["", "## 画面遷移図", "", "```mermaid", "flowchart LR"]
    lines.append('    login["/login ログイン/登録"] -->|ログイン成功| index["/ ダッシュボード"]')
    for s in screens:
        if s["route"] not in ("/", "/login"):
            lines.append(f'    index -->|ヘッダーナビ| {s["route"][1:]}["{s["route"]} {s["name"]}"]')
    lines += ["```", "", "## 画面詳細"]
    for s in screens:
        lines += ["", f"### {s['route']} {s['name']}", "",
                  f"- 画面ファイル: `{s['file']}`",
                  f"- 入力要素: input type = {', '.join(s['inputs']) or 'なし'}"
                  + (f" / select {s['selects']}件" if s['selects'] else "")
                  + (f" / textarea {s['textareas']}件" if s['textareas'] else ""),
                  "- 利用API: " + (", ".join(f"`{a}`" for a in s['apis']) or "なし")]
    lines.append("")
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--commit", default="")
    args = ap.parse_args()
    src, out = Path(args.source), Path(args.out)
    if out.exists():
        shutil.rmtree(out)
    (out / "database").mkdir(parents=True)
    (out / "api").mkdir(parents=True)
    (out / "screens").mkdir(parents=True)

    # Flyway マイグレーションを版順に全て適用した実効スキーマを使う
    # （V1 だけだと V2 以降のテーブル追加・列削除が設計書に反映されない）
    tables, masters = {}, []
    migration_files = sorted(
        (src / "db/migration").glob("V*.sql"),
        key=lambda p: int(re.match(r"V(\d+)", p.name).group(1)),
    )
    for f in migration_files:
        apply_ddl(tables, masters, f.read_text(encoding="utf-8"))
    if not tables:
        print("DDLからテーブルを解析できませんでした", file=sys.stderr)
        return 1
    groups = parse_controllers(src / "apps/api/src/main/kotlin")
    screens = parse_screens(src / "apps/web")

    (out / "database/table-definition.md").write_text(gen_table_definition(tables, masters), encoding="utf-8")
    (out / "database/er-diagram.md").write_text(gen_er(tables), encoding="utf-8")
    (out / "api/api-definition.md").write_text(gen_api(groups), encoding="utf-8")
    (out / "screens/screen-definition.md").write_text(gen_screens(screens), encoding="utf-8")

    # 本体文書のスナップショット
    req = src / "docs/requirements"
    if req.exists():
        shutil.copytree(req, out / "requirements")
    adr = src / "docs/adr"
    adr_titles = []
    if adr.exists():
        shutil.copytree(adr, out / "adr")
        for f in sorted(adr.glob("*.md")):
            first = f.read_text(encoding="utf-8").splitlines()[0].lstrip("# ").strip()
            adr_titles.append((f.name, first))
    dec = src / "docs/decisions.md"
    if dec.exists():
        shutil.copy(dec, out / "decisions.md")

    commit_note = f"生成元コミット: `{args.commit}`（kakeibo main）" if args.commit else "生成元: kakeibo ローカル作業ツリー"
    idx = ["# Kakeibo 設計書", "",
           "このリポジトリは **自動生成** です。`kakeibo` 本体リポジトリの main が更新されるたびに、",
           "GitHub Actions がソース（DDL・コード・本体文書）から再生成して上書きします。直接編集しないでください。",
           "", commit_note, "",
           "## 設計書一覧", "",
           "| ドキュメント | 内容 | 生成元 |", "|---|---|---|",
           "| [テーブル定義書](database/table-definition.md) | 全テーブル・全カラム・キー・FK・初期データ | DDL (Flyway) |",
           "| [ER図](database/er-diagram.md) | Mermaid ER図 + リレーション一覧 | DDL の FK 制約 |",
           "| [API定義書](api/api-definition.md) | エンドポイント一覧 + DTO | API Controller |",
           "| [画面定義書](screens/screen-definition.md) | 画面一覧・項目・利用API・画面遷移図 | Nuxt pages |",
           "| [要件定義](requirements/requirements.md) | 要件定義書（本体から複写） | docs/requirements |",
           "| [決定事項ログ](decisions.md) | 確定した決定の一覧（本体から複写） | docs/decisions.md |",
           "", "## システム概要", "",
           "- API: Kotlin / Spring Boot 3 / MyBatis (ADR-0009) / REST + OpenAPI、DB: MySQL 8 (Flyway)",
           "- UI: Nuxt 3 + TypeScript + SSR / Tailwind、UIとAPIは分離しモノレポ構成",
           "- 認証: メール + パスワード (JWT)。1ユーザー = 1テナントでデータ分離、共有・招待なし",
           "- 金融機関連携: Phase 1 は CSV 取り込み（MF形式互換）。銀行パスワードを預かる方式は取らない",
           "", "## ADR 一覧", ""]
    for name, title in adr_titles:
        idx.append(f"- [{title}](adr/{name})")
    idx.append("")
    (out / "README.md").write_text("\n".join(idx), encoding="utf-8")
    print(f"generated: tables={len(tables)} endpoints={sum(len(g[2]) for g in groups)} screens={len(screens)} -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
