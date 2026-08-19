-- ============================================================================
-- 01_create_KJS_BOMLineAlternate.sql
-- Plugin-owned link table that restores the product-BOM "Alternate" tagging
-- that lived on the old M_Product_BOM table before the iDempiere 6.2 -> 13
-- upgrade turned M_Product_BOM into a VIEW (IDEMPIERE-1250).
--
-- One row per BOM line -> one Alternate (recipe variant).
--   PP_Product_BOMLine_ID  -> pp_product_bomline (the v13 BOM line table)
--   M_Alternate_ID         -> m_alternate        (the recipe-variant master)
--   BOMType                -> original M_Product_BOM.BOMType string (the alternate
--                             name as stored on BOM lines). Used by the JOB Phase
--                             "Create lines from" (CreateFromProductionPlanLine),
--                             which selects BOM by BOMType rather than by ID.
--
-- One table therefore serves both broken buttons:
--   * Create_ProdComplete (LHP)      matches on M_Alternate_ID
--   * Create lines from   (JOB Phase) matches on BOMType
--
-- NOTE ON DEPLOYMENT:
--   * For a PROPER release, this table should be defined in the Application
--     Dictionary (AD_Table / AD_Column) and shipped as the plugin's 2Pack so
--     org.adempiere.plugin.utils.AdempiereActivator creates it automatically
--     (remember to bump the bundle version). See sql/README.md.
--   * This raw DDL is the fallback / dev-DB path: it creates ONLY the physical
--     table, which is all POLA_JOBPHASE_CreateProduction needs for its raw-SQL
--     join. Do NOT run this AND apply a 2Pack that also creates the table, or
--     you will get a "relation already exists" conflict.
--
-- Idempotent: safe to re-run (IF NOT EXISTS).
-- ============================================================================

CREATE TABLE IF NOT EXISTS KJS_BOMLineAlternate (
    KJS_BOMLineAlternate_ID  NUMERIC(10)  NOT NULL,
    KJS_BOMLineAlternate_UU  VARCHAR(36)  DEFAULT NULL,
    AD_Client_ID             NUMERIC(10)  NOT NULL,
    AD_Org_ID                NUMERIC(10)  NOT NULL,
    IsActive                 CHAR(1)      DEFAULT 'Y' NOT NULL,
    Created                  TIMESTAMP    DEFAULT now() NOT NULL,
    CreatedBy                NUMERIC(10)  NOT NULL,
    Updated                  TIMESTAMP    DEFAULT now() NOT NULL,
    UpdatedBy                NUMERIC(10)  NOT NULL,
    PP_Product_BOMLine_ID    NUMERIC(10)  NOT NULL,
    M_Alternate_ID           NUMERIC(10)  NOT NULL,
    BOMType                  VARCHAR(40)  DEFAULT NULL,
    CONSTRAINT kjs_bomlinealternate_key   PRIMARY KEY (KJS_BOMLineAlternate_ID),
    CONSTRAINT kjs_bomlinealt_isactive    CHECK (IsActive IN ('Y','N')),
    -- one alternate per BOM line (matches the 6.2 line-level column semantics)
    CONSTRAINT kjs_bomlinealt_bomline_uq  UNIQUE (PP_Product_BOMLine_ID),
    CONSTRAINT kjs_bomlinealt_uu_uq       UNIQUE (KJS_BOMLineAlternate_UU),
    CONSTRAINT kjs_bomlinealt_bomline_fk  FOREIGN KEY (PP_Product_BOMLine_ID)
        REFERENCES PP_Product_BOMLine (PP_Product_BOMLine_ID) ON DELETE CASCADE,
    CONSTRAINT kjs_bomlinealt_alt_fk      FOREIGN KEY (M_Alternate_ID)
        REFERENCES M_Alternate (M_Alternate_ID)
);

-- For deployments that created the table before BOMType existed (idempotent)
ALTER TABLE KJS_BOMLineAlternate ADD COLUMN IF NOT EXISTS BOMType VARCHAR(40) DEFAULT NULL;

-- Lookup used by POLA_JOBPHASE_CreateProduction (filter by alternate)
CREATE INDEX IF NOT EXISTS kjs_bomlinealt_alt_idx
    ON KJS_BOMLineAlternate (M_Alternate_ID);

-- Lookup used by CreateFromProductionPlanLine (filter by BOMType)
CREATE INDEX IF NOT EXISTS kjs_bomlinealt_bomtype_idx
    ON KJS_BOMLineAlternate (BOMType);
