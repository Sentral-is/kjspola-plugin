-- ============================================================================
-- 12_relax_iproductbom_import.sql
-- Make the custom BOM import staging table robust.
--
-- I_Product_BOM is a CLIENT-CUSTOM import table (EntityType 'U'), not part of
-- original iDempiere. These statements adjust the client's own customization,
-- not standard iDempiere schema.
--
--  * Drop the FK m_alternate_id -> m_alternate. Import/staging tables should not
--    enforce FKs; the ImportBOM process validates and flags rows instead. The FK
--    rejected rows whose alternate did not exist yet, leaving blank rows and
--    aborting the file load.
--  * The iDempiere file loader inserts staging rows without a UU (uuid column,
--    default NULL), and the grid delete then fails ("invalid input syntax for
--    type uuid"). Give the column a default and backfill existing NULLs.
--
-- Idempotent; safe to re-run. Run once per environment, as the adempiere user.
-- ============================================================================

ALTER TABLE i_product_bom DROP CONSTRAINT IF EXISTS malternate_iproductbom;

ALTER TABLE i_product_bom ALTER COLUMN i_product_bom_uu SET DEFAULT generate_uuid()::uuid;

UPDATE i_product_bom SET i_product_bom_uu = generate_uuid()::uuid WHERE i_product_bom_uu IS NULL;
