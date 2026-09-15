# AR Pro Forma Invoice — performance fixes

## Create from Sales Order (`CreateFromProformaInv.java`)

**Picker (`getSODetail`)** — one query joining `M_Product`, `C_Charge`, and `C_UOM` for display names. Removed per-row `new MProduct(...)` and `new MUOM(...)`.

**Save (`save`)** — one query loads org, charge, product, UOM, tax, and price for all selected `C_OrderLine_ID`s. Removed per-row `new MOrderLine(...)` and `new MProduct(...)`. Qty is taken from the grid. Header `C_Order_ID` is set once after the lines. New lines and the header save use the form `trxName`.

## Header totals (`POLA_ProformaLineValidator.java`)

**Scoped update** — `UPDATE C_ARProInv SET ... WHERE C_ARProInv_ID=?` so only the current document is written.

**Same transaction** — that update uses `ProformaLine.get_TrxName()` instead of `null` (no autocommit).

**SUM instead of line loop** — `SELECT COALESCE(SUM(LineNetAmt),0) FROM C_ARProInvLine WHERE C_ARProInv_ID=? AND C_Tax_ID=?`, then tax is calculated once. Same on save and delete.
