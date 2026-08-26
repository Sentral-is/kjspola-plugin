-- ============================================================================
-- 13_fix_quotecopy_attachment_uu.sql
-- Re-home C_Order attachments orphaned by the "Quote convert" (CopyOrder) process.
--
-- Background: AD_Process 231 "C_Order QuoteCopy" (org.kjs.pola.process.CopyOrder)
-- converts a Form Order into a Sales Order and MOVES the attachment to the new
-- order. iDempiere 6.2 keyed attachments by Record_ID only, so the old code did
-- `UPDATE ad_attachment SET record_id = <newSO>`. iDempiere 13 (IDEMPIERE UUID
-- keys) added AD_Attachment.Record_UU and now resolves attachments by
-- Record_ID *and* Record_UU together (see PO.getAttachment -> MAttachment.get).
-- The record_id-only move left Record_UU pointing at the old Form Order, so the
-- row matched NEITHER document and the file became invisible on both.
--
-- The plugin code is fixed to set both keys going forward. This script repairs
-- rows already orphaned by past conversions by re-aligning Record_UU to whatever
-- Record_ID already points at (the Sales Order the file was moved to). The
-- binary data is untouched -- only the UUID pointer is corrected.
--
-- Idempotent; safe to re-run. Run once per environment, as the adempiere user.
-- AD_Table_ID 259 = C_Order.
-- ============================================================================

-- Diagnostic: how many C_Order attachment rows are mis-keyed?
SELECT count(*) AS orphaned_c_order_attachments
FROM ad_attachment a
JOIN c_order o ON o.c_order_id = a.record_id
WHERE a.ad_table_id = 259
  AND a.record_uu IS DISTINCT FROM o.c_order_uu;

-- Repair: make Record_UU agree with Record_ID so the file resolves on its order.
UPDATE ad_attachment a
SET record_uu = o.c_order_uu
FROM c_order o
WHERE a.record_id = o.c_order_id
  AND a.ad_table_id = 259
  AND a.record_uu IS DISTINCT FROM o.c_order_uu;

-- Verify: should return 0 after the repair.
SELECT count(*) AS remaining_orphans
FROM ad_attachment a
JOIN c_order o ON o.c_order_id = a.record_id
WHERE a.ad_table_id = 259
  AND a.record_uu IS DISTINCT FROM o.c_order_uu;
