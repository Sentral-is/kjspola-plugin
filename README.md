# kjspola-plugin

iDempiere customization plugin (`org.kjs`, package `org.kjs.pola`) for PT Pola Paperindo
Jayatama. It plugs custom models, callouts, processes, forms, document/accounting logic, and
validators into iDempiere via the standard factory extension points (`OSGI-INF/`), including the
custom production-planning layer (`KJS_ProductionPlan` / JOB, `KJS_ProductionPlanLine` / JOB Phase,
LHP = `M_Production`, and related BOM/phase tables).

## Modules

- `org.kjs` — the plugin bundle (source under `org.kjs/src`).
- `org.kjs.feature` — the feature/target definition used to build and export the plugin.

## Deployment & DB migrations

Some fixes ship with a table definition and/or a one-time data migration in addition to the plugin
code. When that's the case, the SQL and step-by-step deployment notes live next to the scripts:

- [org.kjs/sql/README.md](org.kjs/sql/README.md) — **KJS_BOMLineAlternate** (restores the
  `Create_ProdComplete` LHP-line generation broken by the iDempiere 6.2 → 13 upgrade, where
  `M_Product_BOM` became a view and dropped the custom `M_Alternate_ID` column). Covers the table
  creation (2Pack or SQL), the one-time backfill from `m_product_bom_old`, the apply order, and the
  production pre-requisite check.
