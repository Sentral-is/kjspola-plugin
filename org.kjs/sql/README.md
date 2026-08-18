# KJS_BOMLineAlternate — deployment (Phase 1 fix for `Create_ProdComplete`)

## Why this exists

The `Create_ProdComplete` button (`org.kjs.pola.process.POLA_JOBPHASE_CreateProduction`) used to
read component BOM from `M_Product_BOM` filtered by the client's custom column `M_Alternate_ID`.
The iDempiere 6.2 → 13 upgrade (IDEMPIERE-1250) turned `M_Product_BOM` into a **view** with no such
column, so the query failed, the old code swallowed the error, and the LHP Line grid came back empty.

Fix: the alternate tagging now lives in a plugin-owned table **`KJS_BOMLineAlternate`**
(`PP_Product_BOMLine_ID → M_Alternate_ID`), and the process joins it. No standard iDempiere table is
modified. The old data survives in `m_product_bom_old` and is copied across by the backfill.

## What ships in this Phase 1

| Piece | File |
|---|---|
| Process fix (query + error handling + zero-result guard) | `src/org/kjs/pola/process/POLA_JOBPHASE_CreateProduction.java` |
| Table creation (physical DDL / dev + fallback) | `sql/01_create_KJS_BOMLineAlternate.sql` |
| One-time data backfill | `sql/02_backfill_KJS_BOMLineAlternate.sql` |

Phase 1 needs **no Java model class** — the process uses raw SQL. The `I_*`/`X_*` model +
`KJSModelFactory` registration are only needed in Phase 2 (maintenance UI / import).

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

## Deploy order (rehearse on `polacup_v13` first, then identical on production)

1. Create the table (A or B) — table must exist before the backfill.
2. Run `sql/02_backfill_KJS_BOMLineAlternate.sql`:
   ```bash
   PGPASSWORD=adempiere psql -h <host> -p <port> -U adempiere -d <db> \
     -f sql/02_backfill_KJS_BOMLineAlternate.sql
   ```
   Check the pre-flight assertions are all `0`, then confirm `INSERT 0 105587` and that
   `final_table_count` equals `source_rows_with_alternate`.
3. Deploy the updated plugin bundle (with the process fix).
4. Verify: open a JOB-linked LHP whose product has backfilled alternate BOM lines → set qty →
   **Create_ProdComplete** → end-product line + component lines appear in LHP Line.

## Production pre-requisite to confirm before go-live

The backfill reads `m_product_bom_old`. It exists only because the DB went through the 6.2 → 13
migration. Confirm on the real production DB that `m_product_bom_old` exists **and** still holds the
alternate data (`SELECT count(*) FROM m_product_bom_old WHERE m_alternate_id IS NOT NULL;` ~105,587)
before relying on the backfill.

## Rollback

Additive and safe: `DROP TABLE KJS_BOMLineAlternate;` and revert the bundle. Note this is a *forward*
migration — reverting to the old bundle restores the original upgrade failure; it is not a fix.
