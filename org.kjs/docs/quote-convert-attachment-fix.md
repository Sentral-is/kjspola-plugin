# Quote convert — attachment loss on Form Order → Sales Order

Investigation + fix log for: *"you make a Form Order and put an attachment; once it goes to Sales
Order the attachment is gone."* (Reported 2026‑08‑26, PT PPJ, iDempiere 13.)

**Status:** fixed in `CopyOrder` (copy the attachment, don't move it). Verified on `polacup_v13`.

---

## TL;DR

- **What runs:** the **"Quote convert"** button = `AD_Process 231` → `org.kjs.pola.process.CopyOrder`.
  It builds the SO with `MOrder.copyFrom`, links it back via `C_Order.QuotationOrder_ID`, and is
  supposed to carry the attachment across. Core copies **no** attachments, so that carry is custom.
- **Storage matters:** attachments here are stored **in the database** (`AD_Attachment.BinaryData`,
  a zip — magic bytes `PK`), *not* on the filesystem. (`AD_ClientInfo.AD_StorageProvider_ID` is unset
  → default DB storage. A `FileSystem` provider row exists but is unused.)
- **Two separate defects, discovered in order:**
  1. **v13 lookup change** — attachments now resolve by **`Record_ID` AND `Record_UU` together**
     (`PO.getAttachment` → `MAttachment.get`). The 6.2‑era code moved the row with a `Record_ID`‑only
     `UPDATE`, leaving `Record_UU` on the old Form Order → the row matched **neither** document →
     "gone". This is real but was **not** the whole story.
  2. **DB‑blob blanking (the real killer)** — the first fix "moved" the row via the model
     (`setRecord_ID` + `setRecord_UU(null)` + `saveEx()`). `MAttachment.afterSave` **re‑serializes the
     in‑memory entries into `BinaryData` on every save**; because a move never materializes the
     entries, `saveEx()` wrote a **NULL blob and destroyed the file**. The attachment then "vanished
     on open" because it was already empty and iDempiere purges empty attachments.
- **The fix:** **copy** the attachment. Force the entries to load (`getEntryCount`/`getEntry`), build a
  **new** `MAttachment` on the SO, `addEntry` the real entries, `saveEx()`. The Form Order keeps its
  copy; the SO gets an independent, intact copy. No orphaning, no blanking, works under DB *and* file
  storage.

---

## Why the obvious fixes were wrong

| Approach | Result | Why |
|---|---|---|
| Old 6.2 code: `UPDATE ad_attachment SET record_id = <SO>` (raw SQL) | **Orphaned** (invisible, but bytes preserved) | v13 also matches on `Record_UU`, which still pointed at the FO. |
| First fix: `setRecord_ID` + `setRecord_UU(null)` + `saveEx()` (model move) | **Destroyed** (blob blanked → file lost) | `afterSave` → `saveLOBData` re‑serialized unmaterialized entries → wrote a NULL blob. Worse than the original bug. |
| **Final fix: copy entries into a new `MAttachment` on the SO** | **Correct** | Entries are loaded (real bytes), a fresh valid blob is written on the SO; source untouched. |

Key core facts (iDempiere 13, verified in `org.adempiere.base`):
- `PO.getAttachment()` → `MAttachment.get(ctx, AD_Table_ID, Record_ID, get_UUID(), trx)`; the loader's
  WHERE is `AD_Table_ID=? AND Record_ID=? AND Record_UU=?` (both keys when a UUID is supplied).
- `MAttachment.afterSave` calls `saveLOBData` for every non‑`laf` attachment; the DB store writes
  `BinaryData` from the in‑memory `m_items`. A header‑only save with unmaterialized entries blanks it.

---

## The fix (code)

`org.kjs/src/org/kjs/pola/process/CopyOrder.java`, after the new order is saved:

```java
// Copy the Form Order's attachment (with its file contents) onto the new Sales Order.
// A record-level "move" is destructive under DB storage: MAttachment.afterSave re-serializes
// the in-memory entries into BinaryData, and an unmaterialized move writes an empty blob.
MAttachment fromAttach = MAttachment.get(getCtx(), MOrder.Table_ID, from.getC_Order_ID(), get_TrxName());
if (fromAttach != null && fromAttach.getEntryCount() > 0) {           // getEntryCount() forces load
    MAttachment toAttach = new MAttachment(getCtx(), MOrder.Table_ID, newOrder.getC_Order_ID(), get_TrxName());
    for (int i = 0; i < fromAttach.getEntryCount(); i++)
        toAttach.addEntry(fromAttach.getEntry(i));                    // real bytes
    toAttach.setTextMsg(fromAttach.getTextMsg());
    toAttach.saveEx();
}
```

Semantics changed from **move** to **copy** (attachment now exists on both the Form Order and the
Sales Order) — which is also what the client wanted ("it should exist in the SO too").

---

## Diagnostic SQL (reusable)

`AD_Table_ID 259 = C_Order`. Local helper: `psqll() { PGPASSWORD=adempiere psql -h localhost -p 5435 -U adempiere -d polacup_v13 -v ON_ERROR_STOP=1 "$@"; }`  ·  VPS: same with `-p 5432 -d polacup`.

**Is the whole family consistent? (FO, its SO, blob state):**
```sql
WITH fo AS (SELECT c_order_id FROM adempiere.c_order WHERE documentno = 'FO/26/08/XXXX')
SELECT o.documentno, o.docstatus,
       CASE WHEN o.quotationorder_id>0 THEN 'SO(copy)' ELSE 'FO(source)' END AS role,
       a.ad_attachment_id, octet_length(a.binarydata) AS len,
       encode(substring(a.binarydata for 4),'escape') AS magic,
       a.binarydata IS NULL AS blob_null,
       (a.record_uu = o.c_order_uu) AS uu_ok
FROM adempiere.c_order o
LEFT JOIN adempiere.ad_attachment a ON a.ad_table_id=259 AND a.record_id=o.c_order_id
WHERE o.c_order_id = (SELECT c_order_id FROM fo)
   OR o.quotationorder_id = (SELECT c_order_id FROM fo)
ORDER BY role DESC;
```
Healthy result after a conversion: **two rows**, both `magic = PK`, `blob_null = f`, `uu_ok = t`.

**Storage provider (DB vs file):**
```sql
SELECT ci.ad_client_id, ci.ad_storageprovider_id, sp.name, sp.method, sp.folder
FROM adempiere.ad_clientinfo ci
LEFT JOIN adempiere.ad_storageprovider sp ON sp.ad_storageprovider_id = ci.ad_storageprovider_id
WHERE ci.ad_client_id > 0;   -- blank provider = DB storage; method=FileSystem = file storage
```

**Damage scan — attachments whose blob was blanked (content lost):**
```sql
SELECT a.ad_attachment_id, a.record_id, o.documentno, o.docstatus, a.updated
FROM adempiere.ad_attachment a JOIN adempiere.c_order o ON o.c_order_id = a.record_id
WHERE a.ad_table_id = 259 AND a.binarydata IS NULL
ORDER BY a.updated DESC;
-- Ignore ad_attachment_id 100 / order 80002 (2004 demo seed row, always null).
```

**Old‑code orphans (bytes intact, `Record_UU` mismatched) — repairable:** see `../sql/13`.

---

## Deployment

1. Build the bundle (Eclipse PDE export), **bump the bundle version**, and verify the class is present
   and is the *copy* version: `unzip -l org.kjs.pola_<ver>.jar | grep CopyOrder`.
2. On the server: remove the old `org.kjs.pola_*` from `plugins/`, drop in the new one, **restart**
   (Equinox binds the process class at boot; there is no hot‑swap). Confirm `Active` in the Felix
   console and no `Failed to load process class: org.kjs...` in the log.
3. Test: Form Order + attachment → **Quote convert** → open the attachment **on the SO** (it displays
   and survives a refresh) and confirm it's still on the Form Order. Cross‑check with the family query.
4. `../sql/13` (optional) re‑points any *old‑code* orphans; it cannot recover blanked blobs.

**Environments:** local `polacup_v13` @ `localhost:5435`; VPS `polacup` @ `localhost:5432`
(`withsupablocks@210.79.191.109`). VPS is a separate DB from local — they share document‑number
sequences from a common ancestor, so identical `FO/SO` numbers are *different* records.

---

## Recovery for blanked attachments

Blanked = `binarydata IS NULL` from a conversion run under the destructive fix. The content is only
recoverable from a DB dump taken **after** the file was uploaded and **before** the conversion. In
practice, for a known order it is cheaper to **re‑upload** the file to the Sales Order. (On the VPS,
the only casualties were same‑day test conversions, which were deleted outright — no business files
were lost.)

---

## Lessons / gotchas

- **Never move an iDempiere attachment by editing `AD_Attachment.Record_ID` and saving the model** —
  `afterSave` re‑serializes entries and will blank a DB‑stored blob if the entries aren't loaded.
  **Copy the entries** into a new `MAttachment` instead.
- v13 attachments are keyed by **`Record_ID` *and* `Record_UU`**; any code that changes one must change
  both (or, better, avoid record‑surgery entirely and copy).
- Check the **storage provider** before reasoning about attachments (DB zip vs filesystem index).
- Distinguish **orphaned** (bytes intact, wrong keys — repairable) from **blanked** (bytes gone —
  not repairable). `octet_length(binarydata)` / `binarydata IS NULL` tells them apart.
- When debugging, snapshot `binarydata` length **before any UI open** — opening an empty attachment
  deletes the row, which hides the evidence.

## Timeline of the investigation (for reference)

1. Reproduced the "attachment gone" symptom on FO→SO.
2. Found v13's `Record_ID`+`Record_UU` matching; shipped a `record_uu` move fix + `sql/13` backfill.
3. `sql/13` correctly resurfaced old orphans (bytes were intact) — looked solved.
4. New conversions still came up empty; controlled test with a pre‑open snapshot showed the moved
   row's `binarydata` was **NULL** — the model move was destroying the file.
5. Switched to the **copy** approach; verified both FO and SO hold intact, independent blobs.
