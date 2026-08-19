-- ============================================================================
-- 02_backfill_KJS_BOMLineAlternate.sql
-- One-time data migration: copy the "Alternate" tagging that survived the
-- 6.2 -> 13 upgrade in m_product_bom_old into the new KJS_BOMLineAlternate.
--
-- Mapping key: the upgrade (IDEMPIERE-1250) renamed the old M_Product_BOM table
-- to m_product_bom_old and moved its rows into pp_product_bomline, preserving
-- the UU. So old.m_product_bom_uu == new.pp_product_bomline_uu (text vs uuid).
-- Verified 1:1 coverage on polacup_v13: 105,587 / 105,587.
--
-- Run AFTER 01_create_KJS_BOMLineAlternate.sql (or after the 2Pack created the
-- table). Idempotent: the NOT EXISTS guard makes re-runs insert nothing.
--
-- IDs: backfilled PKs are 1..N. iDempiere issues new tenant IDs at >= 1,000,000,
-- so these never collide with rows created later through the model. (If you AD-
-- register the table and want its sequence to skip the backfilled range too, set
-- its AD_Sequence.CurrentNext above max(KJS_BOMLineAlternate_ID) — not required.)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- PRE-FLIGHT ASSERTIONS  (run first; every count must be 0 before inserting)
-- ---------------------------------------------------------------------------
-- (a) no BOM line maps to more than one legacy alternate  -> would break UNIQUE
SELECT 'multi_alternate_bomlines' AS check_name, count(*) AS must_be_zero FROM (
    SELECT l.pp_product_bomline_id
    FROM   m_product_bom_old o
    JOIN   pp_product_bomline l ON l.pp_product_bomline_uu::text = o.m_product_bom_uu
    WHERE  o.m_alternate_id IS NOT NULL
    GROUP  BY l.pp_product_bomline_id
    HAVING count(*) > 1
) x;

-- (b) every referenced alternate still exists in m_alternate
SELECT 'orphan_alternates' AS check_name, count(DISTINCT o.m_alternate_id) AS must_be_zero
FROM   m_product_bom_old o
WHERE  o.m_alternate_id IS NOT NULL
  AND  NOT EXISTS (SELECT 1 FROM m_alternate a WHERE a.m_alternate_id = o.m_alternate_id);

-- (c) tenant ownership matches between legacy row and current BOM line
SELECT 'tenant_mismatches' AS check_name, count(*) AS must_be_zero
FROM   m_product_bom_old o
JOIN   pp_product_bomline l ON l.pp_product_bomline_uu::text = o.m_product_bom_uu
WHERE  o.m_alternate_id IS NOT NULL
  AND  o.ad_client_id <> l.ad_client_id;

-- Expected source volume (record it, compare to inserted count afterwards)
SELECT 'source_rows_with_alternate' AS check_name, count(*) AS expected_insert
FROM   m_product_bom_old o
JOIN   pp_product_bomline l ON l.pp_product_bomline_uu::text = o.m_product_bom_uu
WHERE  o.m_alternate_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- BACKFILL
-- ---------------------------------------------------------------------------
INSERT INTO KJS_BOMLineAlternate
    (KJS_BOMLineAlternate_ID, AD_Client_ID, AD_Org_ID, IsActive,
     Created, CreatedBy, Updated, UpdatedBy,
     PP_Product_BOMLine_ID, M_Alternate_ID, BOMType, KJS_BOMLineAlternate_UU)
SELECT
    (SELECT COALESCE(MAX(KJS_BOMLineAlternate_ID), 0) FROM KJS_BOMLineAlternate)
        + row_number() OVER (ORDER BY l.pp_product_bomline_id),
    o.ad_client_id, o.ad_org_id, o.isactive,          -- preserve legacy IsActive (7 rows are 'N')
    o.created, o.createdby, o.updated, o.updatedby,    -- preserve legacy audit
    l.pp_product_bomline_id, o.m_alternate_id, o.bomtype, generate_uuid()
FROM   m_product_bom_old o
JOIN   pp_product_bomline l ON l.pp_product_bomline_uu::text = o.m_product_bom_uu
WHERE  o.m_alternate_id IS NOT NULL
  AND  NOT EXISTS (SELECT 1 FROM KJS_BOMLineAlternate a
                   WHERE a.pp_product_bomline_id = l.pp_product_bomline_id);
-- expect: INSERT 0 105587  (on a fresh run against polacup_v13)

-- Fill BOMType on rows inserted before this column existed (idempotent no-op once set).
-- Needed on environments already backfilled by an earlier version of this script.
UPDATE KJS_BOMLineAlternate k
SET    BOMType = o.bomtype
FROM   m_product_bom_old o
JOIN   pp_product_bomline l ON l.pp_product_bomline_uu::text = o.m_product_bom_uu
WHERE  k.pp_product_bomline_id = l.pp_product_bomline_id
  AND  o.m_alternate_id IS NOT NULL
  AND  k.bomtype IS DISTINCT FROM o.bomtype;

-- ---------------------------------------------------------------------------
-- POST-CHECK  (final table count should equal the source_rows_with_alternate above)
-- ---------------------------------------------------------------------------
SELECT 'final_table_count' AS check_name, count(*) AS rows_now FROM KJS_BOMLineAlternate;
