package org.kjs.pola.validator;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.adempiere.base.event.IEventTopics;
import org.adempiere.exceptions.DBException;
import org.compiere.model.MPriceList;
import org.compiere.model.MTax;
import org.compiere.model.PO;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.kjs.pola.model.X_C_ARProInv;
import org.kjs.pola.model.X_C_ARProInvLine;
import org.osgi.service.event.Event;

public class POLA_ProformaLineValidator {

	// Sum LineNetAmt per tax for a proforma. When excludeLineId > 0 the given line is dropped
	// (used on delete, which fires BEFORE the row is physically removed).
	private static final String SQL_SUM_BY_TAX =
			"SELECT C_Tax_ID, COALESCE(SUM(LineNetAmt),0) "
			+ "FROM C_ARProInvLine WHERE C_ARProInv_ID=? AND (?=0 OR C_ARProInvLine_ID<>?) "
			+ "GROUP BY C_Tax_ID";

	public static String executeProformaEvent(Event event, PO po) {

		String msgQuo = "";
		X_C_ARProInvLine ProformaLine = (X_C_ARProInvLine) po;

		if (event.getTopic().equals(IEventTopics.PO_AFTER_CHANGE)
				|| event.getTopic().equals(IEventTopics.PO_AFTER_NEW)) {
			msgQuo = ProformaBeforeSave(ProformaLine);
		} else if (event.getTopic().equals(IEventTopics.PO_BEFORE_DELETE)) {
			msgQuo = ProformaBeforeDelete(ProformaLine);
		}

		return msgQuo;

	}

	public static String ProformaBeforeSave(X_C_ARProInvLine ProformaLine) {
		// PO_AFTER_NEW / PO_AFTER_CHANGE: the saved line is already persisted -> include all lines.
		return recomputeHeader(ProformaLine, 0);
	}

	public static String ProformaBeforeDelete(X_C_ARProInvLine ProformaLine) {
		// PO_BEFORE_DELETE: the line still exists -> exclude it from the recompute.
		return recomputeHeader(ProformaLine, ProformaLine.getC_ARProInvLine_ID());
	}

	/**
	 * Recompute the proforma header totals from ALL of its lines and write them (absolute) to
	 * C_ARProInv, scoped by C_ARProInv_ID. Tax is calculated per tax group via MTax.calculateTax,
	 * so mixed-tax and multi-line-same-tax documents come out correct. Passing excludeLineId &gt; 0
	 * drops that one line (used on delete, before the row is gone).
	 *
	 * The header row is locked FOR UPDATE first, so two concurrent line edits on the same document
	 * serialize their recompute instead of racing (last-writer-wins on stale totals).
	 */
	private static String recomputeHeader(X_C_ARProInvLine ProformaLine, int excludeLineId) {

		int cARProInvId = ProformaLine.getC_ARProInv_ID();
		String trxName = ProformaLine.get_TrxName();

		// Serialize concurrent recomputes on the same document.
		DB.getSQLValueEx(trxName, "SELECT C_ARProInv_ID FROM C_ARProInv WHERE C_ARProInv_ID=? FOR UPDATE",
				cARProInvId);

		X_C_ARProInv proforma = new X_C_ARProInv(ProformaLine.getCtx(), cARProInvId, trxName);
		MPriceList priceList = new MPriceList(ProformaLine.getCtx(), proforma.getM_PriceList_ID(), trxName);
		boolean taxIncluded = priceList.isTaxIncluded();
		int precision = priceList.getPricePrecision();

		BigDecimal totalBase = Env.ZERO;
		BigDecimal totalTax = Env.ZERO;

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = DB.prepareStatement(SQL_SUM_BY_TAX, trxName);
			pstmt.setInt(1, cARProInvId);
			pstmt.setInt(2, excludeLineId);
			pstmt.setInt(3, excludeLineId);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				int cTaxId = rs.getInt(1);
				BigDecimal groupBase = rs.getBigDecimal(2);
				if (groupBase == null)
					groupBase = Env.ZERO;
				totalBase = totalBase.add(groupBase);
				// C_Tax_ID 0/NULL = no tax on the line -> contributes to base with zero tax.
				if (cTaxId > 0) {
					MTax tax = new MTax(ProformaLine.getCtx(), cTaxId, trxName);
					totalTax = totalTax.add(tax.calculateTax(groupBase, taxIncluded, precision));
				}
			}
		} catch (SQLException e) {
			throw new DBException(e, SQL_SUM_BY_TAX);
		} finally {
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}

		BigDecimal taxBase;
		BigDecimal grandTotal;
		if (taxIncluded) {
			taxBase = totalBase.subtract(totalTax);
			grandTotal = totalBase;
		} else {
			taxBase = totalBase;
			grandTotal = totalBase.add(totalTax);
		}

		DB.executeUpdateEx(
				"UPDATE C_ARProInv SET TaxAmt=?, TaxBaseAmt=?, TotalLines=?, GrandTotal=? WHERE C_ARProInv_ID=?",
				new Object[] { totalTax, taxBase, totalBase, grandTotal, Integer.valueOf(cARProInvId) },
				trxName);

		return "";

	}

}
