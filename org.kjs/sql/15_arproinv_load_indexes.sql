-- Indexes for AR Pro Forma window load (header -> lines) and header-total SUM by tax.
-- Safe to re-run.

CREATE INDEX IF NOT EXISTS c_arproinvline_header_idx
    ON c_arproinvline (c_arproinv_id);

CREATE INDEX IF NOT EXISTS c_arproinvline_header_tax_idx
    ON c_arproinvline (c_arproinv_id, c_tax_id);

CREATE INDEX IF NOT EXISTS c_arproinv_bpartner_idx
    ON c_arproinv (c_bpartner_id);

CREATE INDEX IF NOT EXISTS c_arproinv_order_idx
    ON c_arproinv (c_order_id);
