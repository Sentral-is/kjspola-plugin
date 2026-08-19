# BOM Alternate — deployment guide

Fixes the client's "Alternate" (recipe-variant) handling that broke when the iDempiere 6.2 → 13
upgrade (IDEMPIERE-1250) turned `M_Product_BOM` into a **view** and dropped the custom columns:
`Create_ProdComplete` (LHP), `Create lines from` (JOB Phase), BOM alternate **display/edit**, and
**Import BOM**.

**This is the current, canonical approach — there is no earlier step to run first.** (The older
side-table approach is deprecated; see the bottom section, needed only for rollback.)

## How it works

The alternate is a first-class column **`PP_Product_BOMLine.M_Alternate_ID`** (Table Direct →
`M_Alternate`), shown/edited on the Product **Components** tab and set by `ImportBOM`. It is the single
canonical selector; both production buttons select BOM lines by
`PP_Product_BOMLine.M_Alternate_ID = KJS_ProductionPlan.M_Alternate_ID`, reading `PP_Product_BOMLine`
directly.

## What ships

| Piece | File |
|---|---|
| LHP process (`Create_ProdComplete`) | `src/org/kjs/pola/process/POLA_JOBPHASE_CreateProduction.java` |
| JOB Phase form (`Create lines from`) | `src/org/kjs/pola/form/CreateFromProductionPlanLine.java` |
| Import (`Import BOM`) sets the alternate | `src/org/kjs/pola/process/ImportBOM.java` |
| Add column + index | `sql/10_add_ppbomline_alternate.sql` |
| One-time data migration | `sql/11_migrate_ppbomline_alternate.sql` |
| Dictionary: `AD_Column` M_Alternate_ID + `AD_Field` on Components tab | created in the UI (below); optionally packaged as a 2Pack |

## Deploy runbook (per environment)

Connect as the **`adempiere`** user (matches the app schema/ownership). Environments:

| Environment | host | port | db |
|---|---|---|---|
| Local dev (WSL) | `localhost` | `5435` | `polacup_v13` |
| VPS | `localhost` | `5432` | `polacup` |

**0. Backup (recommended)**
```bash
sudo -u postgres pg_dump -Fc <db> -f ~/<db>_before_bomalt_$(date +%Y%m%d).dump
```

**1. Column + data (SQL)** — idempotent, safe to re-run:
```bash
cd <plugin-root>            # e.g. ~/kjspola-plugin  (git pull for latest scripts)
PGPASSWORD=adempiere psql -h <host> -p <port> -U adempiere -d <db> \
  -v ON_ERROR_STOP=1 -f org.kjs/sql/10_add_ppbomline_alternate.sql
PGPASSWORD=adempiere psql -h <host> -p <port> -U adempiere -d <db> \
  -v ON_ERROR_STOP=1 -f org.kjs/sql/11_migrate_ppbomline_alternate.sql
```
Confirm: `sql/11` pre-flight checks all `0`, then the row counts match.

**2. Dictionary (iDempiere UI, System Administrator role)** — makes the field show and lets `ImportBOM`
write it:
- *Table and Column* → table `PP_Product_BOMLine` → new **Column**: System Element `M_Alternate_ID`,
  Reference **Table Direct**, Length `10`, Entity Type **User maintained** → Save → **Synchronize Column**.
- *Window, Tab & Field* → Window `Product` → tab `Components` → new **Field**: Column `M_Alternate_ID`,
  **Displayed** + **Show in Grid** → Save.

> The `AD_Column` must exist before **Import BOM** runs — the new `ImportBOM` writes the alternate via
> the registered column and fails loudly if it is missing. The two buttons only need the physical
> column + data (step 1).

**3. Deploy the plugin bundle** — build (PDE Export), **bump the bundle version**, drop into `plugins/`,
restart.

**4. Verify**
- Product → Components tab shows/edits **Alternate**
- **Create lines from** fills the JOB BOM; **Create_ProdComplete** fills LHP lines
- **Import BOM** carries the alternate

### Prod shortcut (dump/restore)
The dictionary records (AD_Column/AD_Field), the physical column, and the migrated data all live in the
database. So if prod is created by **dump/restore from `polacup_v13`** (where steps 1–2 are done), prod
inherits them automatically — you only deploy the bundle there. A **separate running** environment (the
VPS) must run steps 1–2 itself.

### Prerequisite to confirm on any target DB
`sql/11` reads `m_product_bom_old` (left behind by the 6.2→13 migration). Confirm it exists with data:
`SELECT count(*) FROM m_product_bom_old WHERE m_alternate_id IS NOT NULL;` (~105k).

---

## Deprecated — `KJS_BOMLineAlternate` side table (rollback only)

The first version of this fix stored the alternate in a plugin-owned side table
`KJS_BOMLineAlternate`. It is **superseded** by the column above and is **not part of a fresh
deployment**. Its scripts are kept in `sql/deprecated/` (`01_create_…`, `02_backfill_…`) only so a
rollback to the old bundle can recreate/read it.

**Retiring it (do only after every environment runs the new bundle and you won't roll back):**
```bash
# canary — rename first (rollback = rename back, no data loss)
PGPASSWORD=adempiere psql -h <host> -p <port> -U adempiere -d <db> \
  -c "ALTER TABLE KJS_BOMLineAlternate RENAME TO KJS_BOMLineAlternate_deprecated;"
# ...observe a few days, then:
PGPASSWORD=adempiere psql -h <host> -p <port> -U adempiere -d <db> \
  -c "DROP TABLE KJS_BOMLineAlternate_deprecated;"
```
Trade-off: renaming ends easy rollback — the old bundle looks for `KJS_BOMLineAlternate` by its
original name, so you'd rename it back before redeploying the old bundle. Verified safe to drop:
nothing references it (no plugin code, not in the dictionary, no views/functions/FKs).
