# AR Pro Forma Invoice — header totals calculation follow-up

Follow-up to the earlier header-totals fix in [`fix.md`](fix.md). That change fixed *where* the
validator writes (added `WHERE C_ARProInv_ID=?`). This change fixes *how* it calculates.

File: `org.kjs/src/org/kjs/pola/validator/POLA_ProformaLineValidator.java`

## Background — the original bug

`POLA_ProformaLineValidator` keeps the `C_ARProInv` header amounts in sync with its
`C_ARProInvLine` lines on every line save/delete. The original `UPDATE C_ARProInv SET ...` had
**no `WHERE` clause**, so every proforma line edit rewrote the stored header amounts of **all
~6,687 rows** in the table. On the prod-bound DB, ~6,664 headers ended up carrying one identical
stamped value set (`GrandTotal 6,382,500 / TaxBaseAmt 5,750,000`). The earlier fix scoped the
`UPDATE` and moved it into the line transaction — that stopped the mass-overwrite and the
table-wide write on every edit.

## Two calculation defects that remained (what this change fixes)

Both save and delete summed only the **edited line's tax group**
(`... WHERE C_ARProInv_ID=? AND C_Tax_ID=?`):

1. **Save** then wrote absolute header totals from that single tax group → on a **mixed-tax**
   document the header reflected only one tax.
2. **Delete** subtracted `SUM(LineNetAmt)` over **all** same-tax lines — and because
   `PO_BEFORE_DELETE` fires *before* the row is gone, that sum still included the line being
   deleted and its siblings → deleting one line from a multi-line invoice subtracted **all** of
   them. (2,204 of 6,620 `(invoice, tax)` groups have >1 line.)

## How it was changed

Both `ProformaBeforeSave` and `ProformaBeforeDelete` now delegate to one private
`recomputeHeader(line, excludeLineId)`:

- **All tax groups** — `SELECT C_Tax_ID, SUM(LineNetAmt) ... GROUP BY C_Tax_ID`; tax is calculated
  per group via `MTax.calculateTax(...)` and summed, so mixed-tax and multi-line-same-tax
  documents are correct.
- **Absolute write** for both paths — `UPDATE C_ARProInv SET TaxAmt=?, TaxBaseAmt=?, TotalLines=?,
  GrandTotal=? WHERE C_ARProInv_ID=?` (delete no longer subtracts incrementally).
- **Delete excludes the row being removed** — `excludeLineId` drops it via
  `AND (?=0 OR C_ARProInvLine_ID<>?)`; save passes `0` (include all).
- **Concurrency** — the header row is locked `FOR UPDATE` first, so two concurrent line edits on
  the same document serialize their recompute instead of racing.
- **Edge cases** — `C_Tax_ID` 0/NULL contributes to base with zero tax; a NULL/absent price list
  yields precision 0 (matches `MPriceList.getPricePrecision()` for an unloaded price list).

Compiles clean against the iDempiere 13 runtime jars.

## Important: how much of this is actually user-visible

While investigating, we found the displayed/exported totals are **virtual columns**, not the
stored ones the validator writes:

| Column | Stored / Virtual | Where used |
|---|---|---|
| `TotalLines` | **virtual** — `SELECT sum(linenetamt) ...` | window, grid, Excel → always correct |
| `GrandTotal` | **virtual** — `SELECT sum(linenetamt)*1.11 ...` | window, grid, Excel → always correct |
| `TaxAmt` | physical (stored) | hidden in window; standard print format lists it |
| `TaxBaseAmt` | physical (stored) | hidden in window; standard print format lists it |

The actual proforma printout uses a **Jasper report** (`IK_ARProInvoice` →
`Print_Inv Pro_v1.jrxml`). Its **main detail query computes from the lines** (`sum(qty*price)`) —
verified by extracting the `.jrxml`.

**What is verified vs. not:**
- Verified: `TotalLines`/`GrandTotal` are virtual (always correct in window/grid/Excel);
  `TaxAmt`/`TaxBaseAmt` are physical and hidden in the window; the printout's main query reads the
  lines, not the header.
- **NOT yet verified:** whether the active Jasper references the stored `TaxAmt`/`TaxBaseAmt`
  anywhere else (header band / text fields), and whether any **other** consumer (another report,
  integration, downstream process) reads those columns.

**Consequence (tentative):** the historical corruption in the stored columns appears largely
**cosmetic** — the UI, grid, and Excel export all derive from the lines. It was therefore **not
treated as urgent**, but this has **not been fully confirmed** for the printout or other consumers.
This calculation follow-up is worth having regardless: it keeps the stored columns *correct* rather
than merely *presumed unread* (defensive against a future reader), and makes save/delete symmetric.

## Related data repair (separate repo, NOT applied)

A one-time verify/repair pair to heal the already-corrupted stored headers was written in the docs
repo — `docs/migration-scripts/60_verify_arproinv_totals.sql` and `70_fix_arproinv_totals.sql`
(dry-run validated: 6,671 rows would change; post-check passes). It is **intentionally not
applied**, because the stored columns are effectively unread (above). Apply only if a real consumer
of `TaxAmt`/`TaxBaseAmt` is identified, and in a maintenance window (writers stopped).
