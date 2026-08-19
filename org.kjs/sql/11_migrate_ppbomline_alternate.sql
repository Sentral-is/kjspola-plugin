-- ============================================================================
-- 11_migrate_ppbomline_alternate.sql   (Phase 2)
-- One-time: copy the alternate onto PP_Product_BOMLine.M_Alternate_ID from the
-- preserved 6.2 table m_product_bom_old (renamed by IDEMPIERE-1250), joined by UU.
--
-- Rules (per review):
--   * fill NULLs only  -> never overwrite alternates edited later in the UI
--   * no isactive filter on the source -> migrate all rows; runtime filters l.IsActive='Y'
--   * recover ~21 rows that carry only a BOMType name (no m_alternate_id) by
--     resolving the name against M_Alternate
-- Run 10_add_ppbomline_alternate.sql first. Idempotent (safe to re-run).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- PRE-FLIGHT ASSERTIONS (must all be 0 before migrating)
-- ---------------------------------------------------------------------------
SELECT 'multi_alternate_bomlines' AS check_name, count(*) AS must_be_zero FROM (
    SELECT l.pp_product_bomline_id
    FROM   m_product_bom_old o
    JOIN   pp_product_bomline l ON l.pp_product_bomline_uu::text = o.m_product_bom_uu
    WHERE  o.m_alternate_id IS NOT NULL
    GROUP  BY l.pp_product_bomline_id HAVING count(*) > 1
) x;

SELECT 'orphan_alternates' AS check_name, count(DISTINCT o.m_alternate_id) AS must_be_zero
FROM   m_product_bom_old o
WHERE  o.m_alternate_id IS NOT NULL
  AND  NOT EXISTS (SELECT 1 FROM m_alternate a WHERE a.m_alternate_id = o.m_alternate_id);

SELECT 'tenant_mismatches' AS check_name, count(*) AS must_be_zero
FROM   m_product_bom_old o
JOIN   pp_product_bomline l ON l.pp_product_bomline_uu::text = o.m_product_bom_uu
WHERE  o.m_alternate_id IS NOT NULL AND o.ad_client_id <> l.ad_client_id;

SELECT 'source_rows' AS check_name, count(*) AS expected_fill
FROM   m_product_bom_old o
JOIN   pp_product_bomline l ON l.pp_product_bomline_uu::text = o.m_product_bom_uu
WHERE  (o.m_alternate_id IS NOT NULL OR o.bomtype IS NOT NULL);

-- ---------------------------------------------------------------------------
-- MIGRATION (fill-nulls only)
-- ---------------------------------------------------------------------------
UPDATE pp_product_bomline l
SET    m_alternate_id = COALESCE(o.m_alternate_id, alt.m_alternate_id)
FROM   m_product_bom_old o
LEFT   JOIN m_alternate alt ON alt.name = o.bomtype
WHERE  o.m_product_bom_uu = l.pp_product_bomline_uu::text
  AND  (o.m_alternate_id IS NOT NULL OR o.bomtype IS NOT NULL)
  AND  l.m_alternate_id IS NULL
  AND  COALESCE(o.m_alternate_id, alt.m_alternate_id) IS NOT NULL;

-- ---------------------------------------------------------------------------
-- POST-CHECK
-- ---------------------------------------------------------------------------
SELECT 'ppbomline_with_alternate' AS check_name, count(m_alternate_id) AS rows_now
FROM   pp_product_bomline;
