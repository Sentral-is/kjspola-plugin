-- ============================================================================
-- 14_fix_aging_duedate_datecollect.sql
-- Restore collect-based due dates in the Aging / Open Items reports.
--
-- Background: PT PPJ's rule is "due date = Date Collect + payment term" (their
-- own custom Jasper "Report Aging (AR/AP)" computes datecollect + netdays). The
-- stock iDempiere views RV_OpenItem / RV_OpenItemToDate compute the due date as
-- paymenttermduedate(term, DATEINVOICED). In 6.2 these views were customized to
-- use DATECOLLECT; the 6.2 -> 13 upgrade recreated the STOCK views and silently
-- reverted the behavior to DateInvoiced-based (yellow vs. green in the client's
-- spreadsheet). C_Invoice.DateCollect is a client custom column (added 2023).
--
-- Fix: in the FIRST UNION branch of each view (the non-payment-schedule branch),
-- feed COALESCE(datecollect, dateinvoiced) into paymenttermduedate/duedays for
-- BOTH the displayed duedate AND daysdue (daysdue drives the aging buckets, so
-- they must move together). The COALESCE falls back to dateinvoiced when no
-- collect date is set (~2/3 of open invoices), preserving stock behavior there.
-- dateinvoiced is mandatory, so the result is never null.
--
-- NOT changed: the SECOND UNION branch (payment schedule) keeps the explicitly
-- entered ips.duedate; discountdate stays dateinvoiced-based (PPJ uses no
-- early-payment discounts). All other columns are reproduced verbatim from the
-- stock definition captured on iDempiere 13 / PostgreSQL 17.
--
-- Blast radius (verified): only two consumers read these views -- AD_Process 238
-- "Aging" (via T_Aging) and AD_Process 145 "Open Items". The custom Jasper
-- (AD_Process 1000047) reads c_invoice directly and is unaffected. No dependent
-- views/matviews and no DB functions reference these views.
--
-- Idempotent; safe to re-run. Run once per environment, as the adempiere user.
--
-- !! RE-APPLY AFTER ANY CORE UPGRADE !! A future iDempiere migration that
-- recreates RV_OpenItem/RV_OpenItemToDate will revert this AND may change the
-- view's column list. Before re-running, diff this file's body against the fresh
-- stock definition (pg_get_viewdef) and re-sync all columns except the two
-- duedate/daysdue expressions.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Pre-check: how many open invoices will shift (collect-based <> invoiced-based)?
-- ----------------------------------------------------------------------------
SELECT count(*) AS invoices_that_will_shift
FROM c_invoice i
WHERE i.docstatus IN ('CO','CL')
  AND i.ispaid = 'N'
  AND i.datecollect IS NOT NULL
  AND paymenttermduedate(i.c_paymentterm_id, i.datecollect::timestamptz)
      <> paymenttermduedate(i.c_paymentterm_id, i.dateinvoiced::timestamptz);

-- ----------------------------------------------------------------------------
-- RV_OpenItem  (used by AD_Process 145 "Open Items" and 238 "Aging")
--   first branch: duedate/daysdue -> COALESCE(datecollect, dateinvoiced)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW rv_openitem AS
 SELECT i.ad_org_id,
    i.ad_client_id,
    i.documentno,
    i.c_invoice_id,
    i.c_order_id,
    i.c_bpartner_id,
    i.issotrx,
    i.dateinvoiced,
    i.dateacct,
    p.netdays,
    paymenttermduedate(i.c_paymentterm_id, COALESCE(i.datecollect, i.dateinvoiced)::timestamp with time zone) AS duedate,
    paymenttermduedays(i.c_paymentterm_id, COALESCE(i.datecollect, i.dateinvoiced)::timestamp with time zone, statement_timestamp()) AS daysdue,
    adddays(i.dateinvoiced::timestamp with time zone, p.discountdays) AS discountdate,
    currencyround(i.grandtotal * p.discount / 100::numeric, i.c_currency_id, 'N'::character varying) AS discountamt,
    i.grandtotal,
    invoicepaid(i.c_invoice_id, i.c_currency_id, 1::numeric) AS paidamt,
    invoiceopen(i.c_invoice_id, 0::numeric) AS openamt,
    i.c_currency_id,
    i.c_conversiontype_id,
    i.c_paymentterm_id,
    i.ispayschedulevalid,
    NULL::numeric AS c_invoicepayschedule_id,
    i.invoicecollectiontype,
    i.c_campaign_id,
    i.c_project_id,
    i.c_activity_id,
    i.c_invoice_ad_orgtrx_id AS ad_orgtrx_id,
    i.ad_user_id,
    i.c_bpartner_location_id,
    i.c_charge_id,
    i.c_doctype_id,
    i.c_doctypetarget_id,
    i.c_dunninglevel_id,
    i.chargeamt,
    i.c_payment_id,
    i.created,
    i.createdby,
    i.dateordered,
    i.dateprinted,
    i.description,
    i.docaction,
    i.docstatus,
    i.dunninggrace,
    i.generateto,
    i.isactive,
    i.isapproved,
    i.isdiscountprinted,
    i.isindispute,
    i.ispaid,
    i.isprinted,
    i.c_invoice_isselfservice AS isselfservice,
    i.istaxincluded,
    i.istransferred,
    i.m_pricelist_id,
    i.m_rma_id,
    i.paymentrule,
    i.poreference,
    i.posted,
    i.processedon,
    i.processing,
    i.ref_invoice_id,
    i.reversal_id,
    i.salesrep_id,
    i.sendemail,
    i.totallines,
    i.updated,
    i.updatedby,
    i.user1_id,
    i.user2_id
   FROM rv_c_invoice i
     JOIN c_paymentterm p ON i.c_paymentterm_id = p.c_paymentterm_id
  WHERE i.ispaid = 'N'::bpchar AND invoiceopen(i.c_invoice_id, 0::numeric) <> 0::numeric AND i.ispayschedulevalid <> 'Y'::bpchar AND (i.docstatus = ANY (ARRAY['CO'::bpchar, 'CL'::bpchar]))
UNION
 SELECT i.ad_org_id,
    i.ad_client_id,
    i.documentno,
    i.c_invoice_id,
    i.c_order_id,
    i.c_bpartner_id,
    i.issotrx,
    i.dateinvoiced,
    i.dateacct,
    daysbetween(ips.duedate::timestamp with time zone, i.dateinvoiced::timestamp with time zone) AS netdays,
    ips.duedate,
    daysbetween(statement_timestamp(), ips.duedate::timestamp with time zone) AS daysdue,
    ips.discountdate,
    ips.discountamt,
    ips.dueamt AS grandtotal,
    invoicepaid(i.c_invoice_id, i.c_currency_id, 1::numeric) AS paidamt,
    invoiceopen(i.c_invoice_id, ips.c_invoicepayschedule_id) AS openamt,
    i.c_currency_id,
    i.c_conversiontype_id,
    i.c_paymentterm_id,
    i.ispayschedulevalid,
    ips.c_invoicepayschedule_id,
    i.invoicecollectiontype,
    i.c_campaign_id,
    i.c_project_id,
    i.c_activity_id,
    i.c_invoice_ad_orgtrx_id AS ad_orgtrx_id,
    i.ad_user_id,
    i.c_bpartner_location_id,
    i.c_charge_id,
    i.c_doctype_id,
    i.c_doctypetarget_id,
    i.c_dunninglevel_id,
    i.chargeamt,
    i.c_payment_id,
    i.created,
    i.createdby,
    i.dateordered,
    i.dateprinted,
    i.description,
    i.docaction,
    i.docstatus,
    i.dunninggrace,
    i.generateto,
    i.isactive,
    i.isapproved,
    i.isdiscountprinted,
    i.isindispute,
    i.ispaid,
    i.isprinted,
    i.c_invoice_isselfservice AS isselfservice,
    i.istaxincluded,
    i.istransferred,
    i.m_pricelist_id,
    i.m_rma_id,
    i.paymentrule,
    i.poreference,
    i.posted,
    i.processedon,
    i.processing,
    i.ref_invoice_id,
    i.reversal_id,
    i.salesrep_id,
    i.sendemail,
    i.totallines,
    i.updated,
    i.updatedby,
    i.user1_id,
    i.user2_id
   FROM rv_c_invoice i
     JOIN c_invoicepayschedule ips ON i.c_invoice_id = ips.c_invoice_id
  WHERE i.ispaid = 'N'::bpchar AND invoiceopen(i.c_invoice_id, ips.c_invoicepayschedule_id) <> 0::numeric AND i.ispayschedulevalid = 'Y'::bpchar AND (i.docstatus = ANY (ARRAY['CO'::bpchar, 'CL'::bpchar])) AND ips.isvalid = 'Y'::bpchar;

-- ----------------------------------------------------------------------------
-- RV_OpenItemToDate  (used by 238 "Aging" when "Account Date" = Yes)
--   first branch: duedate/daysdue -> COALESCE(datecollect, dateinvoiced)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW rv_openitemtodate AS
 SELECT i.ad_org_id,
    i.ad_client_id,
    i.documentno,
    i.c_invoice_id,
    i.c_order_id,
    i.c_bpartner_id,
    i.issotrx,
    i.dateinvoiced,
    i.dateacct,
    p.netdays,
    paymenttermduedate(i.c_paymentterm_id, COALESCE(i.datecollect, i.dateinvoiced)::timestamp with time zone) AS duedate,
    paymenttermduedays(i.c_paymentterm_id, COALESCE(i.datecollect, i.dateinvoiced)::timestamp with time zone, statement_timestamp()) AS daysdue,
    adddays(i.dateinvoiced::timestamp with time zone, p.discountdays) AS discountdate,
    currencyround(i.grandtotal * p.discount / 100::numeric, i.c_currency_id, 'N'::character varying) AS discountamt,
    i.grandtotal,
    i.c_currency_id,
    i.c_conversiontype_id,
    i.c_paymentterm_id,
    i.ispayschedulevalid,
    NULL::numeric AS c_invoicepayschedule_id,
    i.invoicecollectiontype,
    i.c_campaign_id,
    i.c_project_id,
    i.c_activity_id
   FROM rv_c_invoice i
     JOIN c_paymentterm p ON i.c_paymentterm_id = p.c_paymentterm_id
  WHERE i.ispayschedulevalid <> 'Y'::bpchar AND (i.docstatus = ANY (ARRAY['CO'::bpchar, 'CL'::bpchar]))
UNION
 SELECT i.ad_org_id,
    i.ad_client_id,
    i.documentno,
    i.c_invoice_id,
    i.c_order_id,
    i.c_bpartner_id,
    i.issotrx,
    i.dateinvoiced,
    i.dateacct,
    daysbetween(ips.duedate::timestamp with time zone, i.dateinvoiced::timestamp with time zone) AS netdays,
    ips.duedate,
    daysbetween(statement_timestamp(), ips.duedate::timestamp with time zone) AS daysdue,
    ips.discountdate,
    ips.discountamt,
    ips.dueamt AS grandtotal,
    i.c_currency_id,
    i.c_conversiontype_id,
    i.c_paymentterm_id,
    i.ispayschedulevalid,
    ips.c_invoicepayschedule_id,
    i.invoicecollectiontype,
    i.c_campaign_id,
    i.c_project_id,
    i.c_activity_id
   FROM rv_c_invoice i
     JOIN c_invoicepayschedule ips ON i.c_invoice_id = ips.c_invoice_id
  WHERE i.ispayschedulevalid = 'Y'::bpchar AND (i.docstatus = ANY (ARRAY['CO'::bpchar, 'CL'::bpchar])) AND ips.isvalid = 'Y'::bpchar;

-- ----------------------------------------------------------------------------
-- Verify 1 (parity): every non-schedule open item's duedate must now equal
-- paymenttermduedate(term, COALESCE(datecollect, dateinvoiced)). Expect 0.
-- ----------------------------------------------------------------------------
SELECT count(*) AS rows_not_collect_based
FROM rv_openitem oi
JOIN c_invoice i ON i.c_invoice_id = oi.c_invoice_id
WHERE oi.c_invoicepayschedule_id IS NULL
  AND oi.duedate
      <> paymenttermduedate(oi.c_paymentterm_id, COALESCE(i.datecollect, i.dateinvoiced)::timestamptz);

-- ----------------------------------------------------------------------------
-- Verify 2 (stock preserved): where datecollect IS NULL the duedate must still
-- equal the dateinvoiced-based value. Expect 0.
-- ----------------------------------------------------------------------------
SELECT count(*) AS null_collect_rows_that_changed
FROM rv_openitem oi
JOIN c_invoice i ON i.c_invoice_id = oi.c_invoice_id
WHERE oi.c_invoicepayschedule_id IS NULL
  AND i.datecollect IS NULL
  AND oi.duedate
      <> paymenttermduedate(oi.c_paymentterm_id, i.dateinvoiced::timestamptz);

-- ----------------------------------------------------------------------------
-- Verify 3 (spot check): the screenshot invoice should read collect-based.
-- Expect duedate = date_collect + 30  (e.g. 2026-08-05 -> 2026-09-04).
-- ----------------------------------------------------------------------------
SELECT i.documentno,
       i.dateinvoiced::date AS date_invoiced,
       i.datecollect::date  AS date_collect,
       oi.duedate::date     AS report_duedate
FROM rv_openitem oi
JOIN c_invoice i ON i.c_invoice_id = oi.c_invoice_id
WHERE i.documentno = 'PPJ/API/2607/0435';
