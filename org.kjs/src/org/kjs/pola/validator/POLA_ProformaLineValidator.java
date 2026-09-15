package org.kjs.pola.validator;

import java.math.BigDecimal;

import org.adempiere.base.event.IEventTopics;
import org.compiere.model.MPriceList;
import org.compiere.model.MTax;
import org.compiere.model.PO;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.kjs.pola.model.X_C_ARProInv;
import org.kjs.pola.model.X_C_ARProInvLine;
import org.osgi.service.event.Event;

public class POLA_ProformaLineValidator {

	private static final String SQL_SUM_LINENET =
			"SELECT COALESCE(SUM(LineNetAmt),0) FROM C_ARProInvLine WHERE C_ARProInv_ID=? AND C_Tax_ID=?";

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

		X_C_ARProInv proforma = new X_C_ARProInv(ProformaLine.getCtx(), ProformaLine.getC_ARProInv_ID(),ProformaLine.get_TrxName());
		MPriceList priceList = new MPriceList(ProformaLine.getCtx(), proforma.getM_PriceList_ID(),ProformaLine.get_TrxName());
		MTax tax = new MTax(ProformaLine.getCtx(), ProformaLine.getC_Tax_ID(), ProformaLine.get_TrxName());

		BigDecimal taxBaseAmt = DB.getSQLValueBDEx(ProformaLine.get_TrxName(), SQL_SUM_LINENET,
				Integer.valueOf(ProformaLine.getC_ARProInv_ID()), Integer.valueOf(ProformaLine.getC_Tax_ID()));
		if (taxBaseAmt == null)
			taxBaseAmt = Env.ZERO;

		BigDecimal taxAmt = tax.calculateTax(taxBaseAmt, priceList.isTaxIncluded(), priceList.getPricePrecision());
		BigDecimal taxBase;
		BigDecimal grandTotal;
		if (priceList.isTaxIncluded()) {
			taxBase = taxBaseAmt.subtract(taxAmt);
			grandTotal = taxBaseAmt;
		} else {
			taxBase = taxBaseAmt;
			grandTotal = taxBaseAmt.add(taxAmt);
		}

		DB.executeUpdateEx(
				"UPDATE C_ARProInv SET TaxAmt=?, TaxBaseAmt=?, TotalLines=?, GrandTotal=? WHERE C_ARProInv_ID=?",
				new Object[] { taxAmt, taxBase, taxBaseAmt, grandTotal, Integer.valueOf(ProformaLine.getC_ARProInv_ID()) },
				ProformaLine.get_TrxName());

		return "";

	}

	public static String ProformaBeforeDelete(X_C_ARProInvLine ProformaLine) {

		X_C_ARProInv proforma = new X_C_ARProInv(ProformaLine.getCtx(), ProformaLine.getC_ARProInv_ID(),ProformaLine.get_TrxName());
		MPriceList priceList = new MPriceList(ProformaLine.getCtx(), proforma.getM_PriceList_ID(),ProformaLine.get_TrxName());
		MTax tax = new MTax(ProformaLine.getCtx(), ProformaLine.getC_Tax_ID(), ProformaLine.get_TrxName());

		BigDecimal taxBaseAmt = DB.getSQLValueBDEx(ProformaLine.get_TrxName(), SQL_SUM_LINENET,
				Integer.valueOf(ProformaLine.getC_ARProInv_ID()), Integer.valueOf(ProformaLine.getC_Tax_ID()));
		if (taxBaseAmt == null)
			taxBaseAmt = Env.ZERO;

		BigDecimal taxAmt = tax.calculateTax(taxBaseAmt, priceList.isTaxIncluded(), priceList.getPricePrecision());
		BigDecimal taxBase;
		BigDecimal grandTotal;
		if (priceList.isTaxIncluded()) {
			taxBase = taxBaseAmt.subtract(taxAmt);
			grandTotal = taxBaseAmt;
		} else {
			taxBase = taxBaseAmt;
			grandTotal = taxBaseAmt.add(taxAmt);
		}

		DB.executeUpdateEx(
				"UPDATE C_ARProInv SET TaxAmt=TaxAmt-?, TaxBaseAmt=TaxBaseAmt-?, TotalLines=TotalLines-?, GrandTotal=GrandTotal-? WHERE C_ARProInv_ID=?",
				new Object[] { taxAmt, taxBase, taxBaseAmt, grandTotal, Integer.valueOf(ProformaLine.getC_ARProInv_ID()) },
				ProformaLine.get_TrxName());

		return "";

	}

}
