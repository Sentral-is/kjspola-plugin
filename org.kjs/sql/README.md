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
| Import-staging robustness (drop custom FK + UU default) | `sql/12_relax_iproductbom_import.sql` |
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
PGPASSWORD=adempiere psql -h <host> -p <port> -U adempiere -d <db> \
  -v ON_ERROR_STOP=1 -f org.kjs/sql/12_relax_iproductbom_import.sql
```
Confirm: `sql/11` pre-flight checks all `0`, then the row counts match. `sql/12` drops the custom FK
on the `I_Product_BOM` staging table and gives its UU column a default so the import degrades
gracefully (skip + flag bad rows) instead of blocking on missing masters. (Only needed on a DB that
isn't a dump/restore of one already carrying these — the changes travel in the dump.)

**2. Dictionary (iDempiere UI, System Administrator role) — MANDATORY.** Run this **after** step 1
(the physical column already exists, so Synchronize Column just no-ops and registers the metadata).
Required for the Alternate to display/edit on BOM lines **and** for `ImportBOM` (it writes the
alternate through the registered column and fails loudly if the column isn't in the dictionary). The
two raw-SQL buttons work with just the physical column, but everything else needs this step.

*2a. Create the column* — open **Table and Column** (top search box):
1. **Table** tab → Find → `DB Table Name = PP_Product_BOMLine` → open it.
2. **Column** child tab → **New** (`+`).
3. **System Element**: click its lookup, search **`M_Alternate_ID`** (not "alter") and pick the row
   `DB Column Name = M_Alternate_ID`, `Name = Alternate` (element id `1000287`). *Do not pick
   `AlternateType` or `alternate` — those are different text elements.* Selecting it auto-fills the
   greyed-out **DB Column Name** = `M_Alternate_ID`.
4. **Reference**: `Table Direct` — **not** `Table Direct (UU)` (UU expects a uuid column; ours is numeric).
5. **Length** `10`, **Entity Type** `User maintained`, leave **Mandatory** unchecked → **Save**.
6. Click **Synchronize Column** → **OK** (leave Date From empty, Run as Job off). On a DB where `sql/10`
   already ran it reports "already exists" / no change — that's expected.

*2b. Show it on the Components tab* — open **Window, Tab & Field**:
1. **Window** tab → `Name = Product`.
2. **Tab** tab → select **Components** (table `PP_Product_BOMLine`).
3. **Field** tab → **New** → **Column** = `M_Alternate_ID_Alternate`, check **Displayed** + **Show in
   Grid**, **Name** = `Alternate` → **Save**. (Sequence auto-fills ~110; fine, or lower it to sit near
   Product/Line — cosmetic.)
4. *(Optional)* repeat 2b for the **Bill of Materials and Formula** window's Components tab if your team
   edits BOMs there too.

Log out/in (or reopen the Product window) → Product → **Components** should now show the **Alternate**
field.

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

---

## Unrelated — Aging due-date fix (`sql/14`)

`sql/14_fix_aging_duedate_datecollect.sql` is a **separate, DB-only** fix (no plugin Java/dictionary
change) for the **Aging** and **Open Items** reports. The 6.2 → 13 upgrade recreated the stock
`RV_OpenItem` / `RV_OpenItemToDate` views, which compute the invoice due date as `DateInvoiced +
term`; PT PPJ's rule is **`DateCollect + term`** (matching their custom Jasper "Report Aging
(AR/AP)"). The script re-applies the customization via `CREATE OR REPLACE VIEW`, using
`COALESCE(datecollect, dateinvoiced)` so invoices without a collect date keep the stock behavior.
Self-documenting header + built-in verification queries inside the file; idempotent.

**Re-apply after any core upgrade** — a migration that recreates those views reverts this (and can
change their column list, so re-diff against the fresh `pg_get_viewdef` before re-running). Run once
per environment as `adempiere` (prod-via-restore inherits it; a separately-running VPS needs it
applied directly).
