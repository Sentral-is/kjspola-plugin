# KJS_BOMLineAlternate — deployment (fix for `Create_ProdComplete` + `Create lines from`)

## Why this exists

Two buttons read component BOM from `M_Product_BOM`, filtered by custom columns the client added:

- **`Create_ProdComplete`** (LHP → `POLA_JOBPHASE_CreateProduction`) filtered by `M_Alternate_ID`.
- **`Create lines from`** (JOB Phase → `CreateFromProductionPlanLine`) filtered by `BOMType`
  (which held the alternate's name as text).

The iDempiere 6.2 → 13 upgrade (IDEMPIERE-1250) turned `M_Product_BOM` into a **view** over
`pp_product_bom`/`pp_product_bomline` that carries neither custom column (and recomputes `BOMType`
to just `'O'`/`'P'`). So `Create_ProdComplete` errored + came back empty, and `Create lines from`
silently returned no BOM.

Fix: both custom columns are preserved per BOM line in a plugin-owned table **`KJS_BOMLineAlternate`**
(`PP_Product_BOMLine_ID → M_Alternate_ID` **and** `BOMType`), backfilled 1:1 from the preserved
`m_product_bom_old`. Both buttons read `PP_Product_BOMLine` **directly** (not the `M_Product_BOM`
view, which hides lines under inactive headers) joined to this table. No standard iDempiere table is
modified.

## What ships

| Piece | File |
|---|---|
| LHP process fix (`Create_ProdComplete`) | `src/org/kjs/pola/process/POLA_JOBPHASE_CreateProduction.java` |
| JOB Phase form fix (`Create lines from`) | `src/org/kjs/pola/form/CreateFromProductionPlanLine.java` |
| Table creation (`M_Alternate_ID` + `BOMType`) | `sql/01_create_KJS_BOMLineAlternate.sql` |
| One-time data backfill | `sql/02_backfill_KJS_BOMLineAlternate.sql` |

Both fixes need **no Java model class** — they use raw SQL. The `I_*`/`X_*` model +
`KJSModelFactory` registration are only needed later for a maintenance UI / import handling.

## Two ways to create the table

**A. Proper release — Application Dictionary + 2Pack (recommended for production)**
1. In iDempiere, create the table in *Table and Column*: `AD_Table` `KJS_BOMLineAlternate` with the
   columns in `01_create_...sql` (`M_Alternate_ID`, `PP_Product_BOMLine_ID` as Table Direct; the
   standard audit/UU/ID columns are added automatically). Run **Synchronize Column** to create the
   physical table.
2. **Pack Out** that table into a 2Pack and drop it at `org.kjs/META-INF/2Pack.zip`.
3. Add `META-INF/2Pack.zip` to `bin.includes` in `build.properties`.
4. **Bump the bundle version** in `META-INF/MANIFEST.MF` — `AdempiereActivator` only applies a 2Pack
   whose version it has not imported yet. Without a bump, an already-installed instance never gets
   the table.
   On startup the activator then creates the table automatically on every environment.

**B. Fallback / dev — plain SQL**
Run `sql/01_create_KJS_BOMLineAlternate.sql`. Creates only the physical table (enough for the
button, which queries by raw SQL). Do **not** combine A and B on the same DB — the 2Pack would try
to create a table that already exists.

## Applying the SQL (rehearse on `polacup_v13` first, then identical on production)

Run from the plugin root. Connect as the **`adempiere`** user (matches the app's schema/ownership),
`-v ON_ERROR_STOP=1` so it halts on the first error. Both scripts are **idempotent** — safe to re-run,
and they handle both a fresh install and an already-deployed table.

```bash
cd <plugin-root>            # e.g. ~/kjspola-plugin

# 1. create the table (or add the BOMType column if the table already exists)
PGPASSWORD=adempiere psql -h <host> -p <port> -U adempiere -d <db> \
  -v ON_ERROR_STOP=1 -f org.kjs/sql/01_create_KJS_BOMLineAlternate.sql

# 2. pre-flight checks + backfill (M_Alternate_ID and BOMType)
PGPASSWORD=adempiere psql -h <host> -p <port> -U adempiere -d <db> \
  -v ON_ERROR_STOP=1 -f org.kjs/sql/02_backfill_KJS_BOMLineAlternate.sql
```

Connection per environment:

| Environment | host | port | db |
|---|---|---|---|
| Local dev (WSL) | `localhost` | `5435` | `polacup_v13` |
| VPS test/prod   | `localhost` | `5432` | `polacup` |

Confirm in the output: script 1 → `CREATE TABLE` / `ALTER TABLE` / `CREATE INDEX`; script 2 →
pre-flight checks all `0`, then `INSERT 0 <N>`, then `final_table_count = <N>` (matching
`source_rows_with_alternate`). On a re-run the `INSERT` is `0` and the `UPDATE` a no-op — that's the
idempotency working, not an error.

Then deploy the updated plugin bundle (both fixes) and restart iDempiere. Verify in the UI:
- **Create lines from** on a JOB Phase (with an Alternate) → the BOM tab fills.
- **Create_ProdComplete** on a JOB-linked LHP → end-product + component lines appear in LHP Line.

## Production pre-requisite to confirm before go-live

The backfill reads `m_product_bom_old`. It exists only because the DB went through the 6.2 → 13
migration. Confirm on the real production DB that `m_product_bom_old` exists **and** still holds the
alternate data (`SELECT count(*) FROM m_product_bom_old WHERE m_alternate_id IS NOT NULL;` ~105,587)
before relying on the backfill.

## Rollback

Additive and safe: `DROP TABLE KJS_BOMLineAlternate;` and revert the bundle. Note this is a *forward*
migration — reverting to the old bundle restores the original upgrade failure; it is not a fix.
