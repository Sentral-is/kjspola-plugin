# AR Pro Forma — slow switching between records

Investigation log for: *"when I switch between the AR Pro Forma entries it's still slow; in iDempiere 6
it was instant."* (iDempiere 13.)

**Status:** open. The plugin has been ruled out in code; the remaining cost is core per-record work plus
the state of the database. Needs the diagnostics below run against the affected DB to close.

---

## TL;DR

- **No plugin code runs when you switch AR header records.** Verified, see below. The earlier
  performance work (commits `14e61c7`, `3fd449e`, `46a65d3`, `dc70bd5`) addressed *save* and
  *create-from* paths; navigation is a different code path.
- What's left on a record switch is **core iDempiere v13** loading the header row, re-querying the line
  tab, resolving lookup labels, and probing a handful of **shared tables** for the current record
  (`AD_Attachment`, `AD_Archive`, `AD_ChangeLog`, chat, labels).
- Those probes are single-row lookups by `(AD_Table_ID, Record_ID)`. **They are only the culprit if one
  of those tables lost its index or is bloated**, forcing a scan. That is the thing to test, not assume.
- It's a credible suspect *here specifically* because attachments on this install are stored **in the
  database** as zipped blobs in `AD_Attachment.BinaryData` (see
  [`quote-convert-attachment-fix.md`](quote-convert-attachment-fix.md) — `AD_ClientInfo.AD_StorageProvider_ID`
  is unset, so default DB storage). A sequential scan over a table full of blobs is far more expensive
  than over a narrow table, which would fit "6.2 instant, 13 slow".

---

## What is already ruled out (verified in code)

| Suspect | Verdict | Evidence |
|---|---|---|
| Model validators firing on load | **Not involved** | `KJSValidatorFactory.registerTableEvents()` registers only `PO_AFTER_NEW` / `PO_AFTER_CHANGE` / `PO_BEFORE_DELETE`, and only on the **line** tables (`C_ARProInvLine`, `C_QuotationLine`, `C_ContractLine`, `KJS_ProductionPlan`). Reading a record fires none of them. |
| A global model validator | **Does not exist** | `plugin.xml` is empty; there is no `ModelValidator` / `initialize(MClient)` implementation in the bundle. |
| Callouts on every column | **Fixed** | `KJSCalloutFactory` now returns AR callouts only for `C_BPartner_ID`, `C_Order_ID`, `M_Product_ID`, `QtyEntered`, `PriceEntered`, and those return early when the row is already populated. |
| Header totals recompute | **Fixed, and save-only** | `POLA_ProformaLineValidator.recomputeHeader` is scoped (`WHERE C_ARProInv_ID=?`), transaction-aware, and skipped during create-from. It never runs on a read. |

Conclusion: further Java changes in this plugin will not make navigation faster. The next move is
measurement against the database.

---

## Diagnostics

Local helper: `psqll() { PGPASSWORD=adempiere psql -h localhost -p 5435 -U adempiere -d polacup_v13 -v ON_ERROR_STOP=1 "$@"; }`
· VPS: same with `-p 5432 -d polacup`.

### 1. Get the table ids and the change-log flag

```sql
SELECT ad_table_id, tablename, ischangelog FROM adempiere.ad_table
WHERE tablename IN ('C_ARProInv','C_ARProInvLine');
```

Keep the `C_ARProInv` `ad_table_id` for step 4. Note `ischangelog` for step 3.

### 2. Rule the AR tables in or out — **the fork in the road**

Use a real header id from a document that feels slow to open.

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM adempiere.c_arproinvline WHERE c_arproinv_id = <id> ORDER BY line;
```

- **Index Scan, a few ms** → the lines are fine. The cost is core per-record work; continue to step 3.
- **Seq Scan, or hundreds of ms** → [`sql/15_arproinv_load_indexes.sql`](../sql/15_arproinv_load_indexes.sql)
  was never applied to *this* database. Apply it and re-measure before looking at anything else.

### 3. Size up the shared per-record tables

```sql
SELECT relname, n_live_tup, n_dead_tup,
       pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
       last_autovacuum
FROM pg_stat_user_tables
WHERE relname IN ('ad_attachment','ad_archive','ad_changelog','ad_note','ad_chat')
ORDER BY pg_total_relation_size(relid) DESC;
```

- **`n_dead_tup` large relative to `n_live_tup`** → bloat. Run `VACUUM (ANALYZE, VERBOSE) <table>;`.
- **`ad_changelog` large *and* `ischangelog = 'Y'` on `C_ARProInv` from step 1** → every record switch is
  also building record history. Turn change log off for the table in the Application Dictionary; an
  index will not fix this one.

### 4. Confirm the attachment probe is indexed

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT ad_attachment_id FROM adempiere.ad_attachment
WHERE ad_table_id = <C_ARProInv ad_table_id> AND record_id = <id>;
```

A **Seq Scan** here is the answer, and it is the expensive case described in the TL;DR because the table
holds blobs. Fix:

```sql
CREATE INDEX IF NOT EXISTS ad_attachment_record_idx
    ON adempiere.ad_attachment (ad_table_id, record_id);
```

### 5. If steps 2–4 all look healthy

Let the server tell you what it actually ran. Requires `pg_stat_statements`.

```sql
SELECT pg_stat_statements_reset();
```

Switch between five or six AR entries in the UI, then:

```sql
SELECT calls, round(total_exec_time) AS ms, round(mean_exec_time,1) AS avg_ms,
       left(query, 160) AS query
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 25;
```

Whatever is at the top is the cause. Also check for virtual columns, which run their subquery **per row**
of the tab query and cannot be fixed from this plugin:

```sql
SELECT tablename, columnname, columnsql FROM adempiere.ad_column c
JOIN adempiere.ad_table t USING (ad_table_id)
WHERE t.tablename IN ('C_ARProInv','C_ARProInvLine') AND columnsql IS NOT NULL;
```

---

## Notes

- Steps 2 and 4 must be run on the **same database** the slow client is connected to. Local `polacup_v13`
  and the VPS `polacup` are separate databases; an index applied to one says nothing about the other.
- `EXPLAIN (ANALYZE, BUFFERS)` on a `SELECT` is read-only and safe to run on production.
- Run step 2 twice and use the second timing; the first can be dominated by cold cache.
