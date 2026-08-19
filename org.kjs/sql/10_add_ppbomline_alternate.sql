-- ============================================================================
-- 10_add_ppbomline_alternate.sql   (Phase 2)
-- Make M_Alternate_ID a first-class column on the standard v13 BOM-line table
-- PP_Product_BOMLine, so the alternate can be displayed/edited on the Components
-- tab and set by ImportBOM. This supersedes the Phase-1 side table
-- KJS_BOMLineAlternate (which is kept for a rollback window; see README).
--
-- PROPER deployment: the AD_Column (M_Alternate_ID, Table Direct -> M_Alternate)
-- is created in the dictionary and shipped in the plugin 2Pack; Synchronize
-- Column then creates the physical column. This script is the idempotent
-- physical fallback (dev / pre-2Pack) and the index. Safe to run either way.
-- ============================================================================

ALTER TABLE pp_product_bomline
    ADD COLUMN IF NOT EXISTS M_Alternate_ID numeric(10) DEFAULT NULL;

-- Lookup for the two production buttons: header product + line alternate, active lines only
CREATE INDEX IF NOT EXISTS ppbomline_alt_idx
    ON pp_product_bomline (pp_product_bom_id, m_alternate_id)
    WHERE isactive = 'Y';
