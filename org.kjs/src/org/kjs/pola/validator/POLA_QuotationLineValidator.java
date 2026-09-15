package org.kjs.pola.validator;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import org.adempiere.base.event.IEventTopics;
import org.adempiere.exceptions.DBException;
import org.compiere.model.MPriceList;
import org.compiere.model.MTax;
import org.compiere.model.PO;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.kjs.pola.model.X_C_Quotation;
import org.kjs.pola.model.X_C_QuotationLine;
import org.osgi.service.event.Event;

public class POLA_QuotationLineValidator {

	private static final String SQL_SUM_BY_TAX =
			"SELECT C_Tax_ID, COALESCE(SUM(LineNetAmt),0) "
			+ "FROM C_QuotationLine WHERE C_Quotation_ID=? AND (?=0 OR C_QuotationLine_ID<>?) "
			+ "GROUP BY C_Tax_ID";

	public static String executeQuotationEvent(Event event, PO po) {

		String msgQuo = "";
		X_C_QuotationLine quoLine = (X_C_QuotationLine) po;

		if (event.getTopic().equals(IEventTopics.PO_AFTER_CHANGE)
				|| event.getTopic().equals(IEventTopics.PO_AFTER_NEW)) {
			msgQuo = QuotationBeforeSave(quoLine);
		} else if (event.getTopic().equals(IEventTopics.PO_BEFORE_DELETE)) {
			msgQuo = QuotationBeforeDelete(quoLine);
		}

		return msgQuo;

	}

	public static String QuotationBeforeSave(X_C_QuotationLine quoLine) {
		return recomputeHeader(quoLine.getCtx(), quoLine.getC_Quotation_ID(),
				quoLine.get_TrxName(), 0);
	}

	public static String QuotationBeforeDelete(X_C_QuotationLine quoLine) {
		return recomputeHeader(quoLine.getCtx(), quoLine.getC_Quotation_ID(),
				quoLine.get_TrxName(), quoLine.getC_QuotationLine_ID());
	}

	private static String recomputeHeader(Properties ctx, int cQuotationId, String trxName, int excludeLineId) {

		X_C_Quotation quo = new X_C_Quotation(ctx, cQuotationId, trxName);
		MPriceList priceList = new MPriceList(ctx, quo.getM_PriceList_ID(), trxName);
		boolean taxIncluded = priceList.isTaxIncluded();
		int precision = priceList.getPricePrecision();

		BigDecimal totalBase = Env.ZERO;
		BigDecimal totalTax = Env.ZERO;

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = DB.prepareStatement(SQL_SUM_BY_TAX, trxName);
			pstmt.setInt(1, cQuotationId);
			pstmt.setInt(2, excludeLineId);
			pstmt.setInt(3, excludeLineId);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				int cTaxId = rs.getInt(1);
				BigDecimal groupBase = rs.getBigDecimal(2);
				if (groupBase == null)
					groupBase = Env.ZERO;
				totalBase = totalBase.add(groupBase);
				if (cTaxId > 0) {
					MTax tax = new MTax(ctx, cTaxId, trxName);
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
				"UPDATE C_Quotation SET TaxAmt=?, TaxBaseAmt=?, TotalLines=?, GrandTotal=? WHERE C_Quotation_ID=?",
				new Object[] { totalTax, taxBase, totalBase, grandTotal, Integer.valueOf(cQuotationId) },
				trxName);

		return "";

	}

}
