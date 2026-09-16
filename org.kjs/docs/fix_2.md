# Remaining performance fixes

## AR Pro Forma create-from — one header recompute (`CreateFromProformaInv.java`, `POLA_ProformaLineValidator.java`)

Create-from no longer recomputes header totals on every `saveEx()`. The validator skip flag is set around the line loop; after all lines are saved, `recomputeHeader` runs once.

Create-from no longer constructs `WCreateFromWindow` in the parent field initializer. `WCreateFromProformaInv` creates the window once.

## Event log (`KJSValidatorFactory.java`)

Removed `log.info("JEMBO EVENT MANAGER // INITIALIZED")` from every PO event.

## Quotation header update (`POLA_QuotationLineValidator.java`)

`UPDATE C_Quotation ... WHERE C_Quotation_ID=?` in the line transaction. Totals from `SUM(LineNetAmt) GROUP BY C_Tax_ID`. Delete recomputes remaining lines (does not subtract from every quotation).

## Contract header update (`POLA_ContractLineValidator.java`)

`UPDATE C_Contract SET GrandTotal=?, TotalLines=? WHERE C_Contract_ID=?` in the line transaction. `SUM(LineNetAmt)` once. Delete recomputes remaining lines.

## Product Phase — Create JOB from SO/Req (`CreateFromMPS.java`)

Product-phase existence is checked once per save, not once per selected row.

## Product Phase — Create JOB Phase (`CreateFromProductionPlan.java`)

Asset and product dimensions load in two `IN (...)` queries for all selected rows. Removed per-row queries and the unscoped `WHERE Line=?` scan of `KJS_ProductionPlanLine`. Est. dates are set from the calculated duration.

## AR Pro Forma — slow open / per-document load

Callouts were registered on **every** `C_ARProInv` / `C_ARProInvLine` column. They are now only on `C_BPartner_ID`, `C_Order_ID`, `M_Product_ID`, `QtyEntered`, `PriceEntered`.

Product pricing does not run when opening a line that already has a price. Partner/order callouts skip when the row is already filled. Order callout reads five columns instead of loading `MOrder`.

**Run on the DB** (needed for line-tab load): [`sql/15_arproinv_load_indexes.sql`](../sql/15_arproinv_load_indexes.sql)

