# Product Phase — performance fixes

## Import (`ImportProductPhase.java`)

Header lookup is once per product, cached in the import loop. Removed per-row `COUNT(*)`, per-row `SELECT KJS_ProductPhase_ID`, and `commitEx()` after every line. The import `UPDATE` statement is prepared once. New `KJS_ProductPhase` / `KJS_ProductPhaseLine` records are created with ID `0` (not from the import `ResultSet`).

## Create JOB from SO (`POLA_JOB_CreateFromSO.createPhase`)

One join loads phase line, phase, and product value:

```sql
SELECT ppl.Line, ppl.KJS_Phase_ID, ppl.M_Product_ID, prod.Value
FROM KJS_ProductPhaseLine ppl
INNER JOIN KJS_ProductPhase pp ON pp.KJS_ProductPhase_ID = ppl.KJS_ProductPhase_ID
LEFT JOIN M_Product prod ON prod.M_Product_ID = ppl.M_Product_ID
WHERE pp.M_Product_ID = ?
AND ppl.M_Alternate_ID = ?
```

Removed per-row `new X_KJS_ProductPhaseLine`, `new MProduct`, and `new X_M_Alternate`. Parent product and alternate load once. Query uses parameters and the process `trxName`.
