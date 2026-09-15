# LHP Complete — "no BOM Products" for BOMs hidden by the M_Product_BOM view

Symptom: completing an LHP (`M_Production`) failed with
**"Attempt to create product line for Bill Of Materials with no BOM Products - <DocNo>"**
even though the LHP already had its end-product + component lines populated.

File: `org.kjs/src/org/kjs/pola/model/MProductionExt.java` (method `isBom`)

## Background — the 6.2 → 13 view breakage

IDEMPIERE-1250 replaced the flat `M_Product_BOM` **table** with a **view** over `PP_Product_BOM`
(recipe header) + `PP_Product_BOMLine` (recipe lines). The view only exposes lines whose **header**
satisfies `bomtype='A' AND bomuse='A' AND isactive='Y'`. A recipe whose header is **inactive** or has
**`bomuse='M'` (Manufacturing, not Master)** is therefore invisible to the view, even though its lines
physically exist in `PP_Product_BOMLine`.

## Root cause — create and complete read different sources

The two LHP steps disagreed on where they read the recipe:

- **Create** — `POLA_JOBPHASE_CreateProduction` was already fixed (Phase 2) to read
  `PP_Product_BOMLine` **directly** (filtered by `M_Alternate_ID`), bypassing the view. → line
  creation always succeeds.
- **Complete** — `MProductionExt.completeIt() → prepareIt() → validateEndProduct() → isBom()` still
  counted via the `M_Product_BOM` **view**:
  ```sql
  SELECT count(M_Product_BOM_ID) FROM M_Product_BOM WHERE M_Product_ID = ?
  ```
  For a hidden-header product the view returns **0**, so completion was rejected.

`MProductionExt` is the model for `M_Production` (registered in `KJSModelFactory`), so the standard
document **Complete/Proses** action runs this check — the error is *not* thrown by the Create button.
The `" - <DocNo>"` suffix on the message is appended by iDempiere's document engine, confirming the
completion path.

**Not every LHP is affected** — only products whose recipe header is inactive or `bomuse='M'`. On
`polacup_prod260902`, **337** products were in this state (292 under inactive headers, 45 under
`bomuse='M'`). Products with a normal `Master/active` header (e.g. SPK/26-2143) always completed fine,
which is why the LHP feature "worked at the beginning."

## How it was changed

`isBom()` now performs the existence check against `PP_Product_BOMLine` directly — the same source the
create path uses — with no header-flag gating:

```java
final int materials = DB.getSQLValue(this.get_TrxName(),
    "SELECT count(*) FROM PP_Product_BOMLine l "
  + "JOIN PP_Product_BOM b ON b.PP_Product_BOM_ID = l.PP_Product_BOM_ID "
  + "WHERE b.M_Product_ID = ? AND l.IsActive = 'Y'", M_Product_ID);
```

- Header filters (`bomtype/bomuse/isactive`) are omitted deliberately (a comment in the code says so,
  to stop a future edit "correcting" it back to the view).
- `M_Alternate_ID` is not filtered here — `isBom()` only receives `M_Product_ID`, and this is a
  generic "does this end product have any component" existence check; any active line suffices.
- The preceding `isbom='N'` guard is unchanged.

Safety: only removes the false-negative where lines genuinely exist but a header flag hid them. A
product with truly no active lines still returns 0 and errors correctly.

## Deliberately out of scope

- **`createLines()`** (same class) still reads the view. The LHP/JOB flow does not use it (creation
  goes through `POLA_JOBPHASE_CreateProduction`); other production paths (`MOrderLine`/`MProjectLine`
  constructors) do. It reads `BOMQty` and recurses on phantom BOMs — it selects the actual component
  set, so switching its source could change which components normal (non-JOB) productions generate.
  Left as a known view-reference; grep `MProductionExt` for `M_Product_BOM` before assuming it's clean.
- **`costsOK()`** also references the view, but runs a cost-rollup `UPDATE`, logs SQL errors at
  `SEVERE`, and always returns `true` — it never blocks completion. Left unchanged.
- **No data migration.** The code fix covers all 337 hidden-header products and any future ones. The
  data alternative (flipping headers to `isactive='Y'`/`bomuse='A'`) was rejected as fragile/narrower.

## Verification

Compiles clean against the iDempiere 13 runtime jars.

SQL parity on a hidden-header product (SPK/26-2250 end product `SL21M0610075TEKA`, id 1083251, header
`bomuse='M'`): old view count = **0** (was the error), new `PP_Product_BOMLine` count = **1** (passes).

End-to-end on `polacup_prod260902`: LHP **SPK/26-2250** (`M_Production_ID` 1170101), previously blocked,
completed after deploy — `DocStatus=CO`, `Processed=Y`, both lines processed, and inventory
transactions posted (`P+` 16 of the end product, `P-` 747 of the component). The "no BOM Products"
error no longer appears.

## Deploy notes

Eclipse PDE Export, **bump the bundle version**, remove the old jar from the server's `plugins/`
(OSGi may otherwise serve the stale one), and verify the class shipped:
`unzip -l org.kjs_*.jar | grep MProductionExt`. Restart the server.
