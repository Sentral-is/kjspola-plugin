package org.kjs.pola.validator;

import java.math.BigDecimal;

import org.adempiere.base.event.IEventTopics;
import org.compiere.model.PO;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.kjs.pola.model.X_C_ContractLine;
import org.osgi.service.event.Event;

public class POLA_ContractLineValidator {

	private static final String SQL_SUM =
			"SELECT COALESCE(SUM(LineNetAmt),0) FROM C_ContractLine WHERE C_Contract_ID=? AND (?=0 OR C_ContractLine_ID<>?)";

	public static String executeContractEvent(Event event, PO po) {

		String msgContract = "";
		X_C_ContractLine contractLine = (X_C_ContractLine) po;

		if (event.getTopic().equals(IEventTopics.PO_AFTER_CHANGE)
				|| event.getTopic().equals(IEventTopics.PO_AFTER_NEW)) {
			msgContract = ContractBeforeSave(contractLine);
		} else if (event.getTopic().equals(IEventTopics.PO_BEFORE_DELETE)) {
			msgContract = ContractBeforeDelete(contractLine);
		}

		return msgContract;

	}

	public static String ContractBeforeSave(X_C_ContractLine contLine) {
		return recomputeHeader(contLine.getC_Contract_ID(), contLine.get_TrxName(), 0);
	}

	public static String ContractBeforeDelete(X_C_ContractLine contLine) {
		return recomputeHeader(contLine.getC_Contract_ID(), contLine.get_TrxName(),
				contLine.getC_ContractLine_ID());
	}

	private static String recomputeHeader(int cContractId, String trxName, int excludeLineId) {

		BigDecimal total = DB.getSQLValueBDEx(trxName, SQL_SUM,
				Integer.valueOf(cContractId), Integer.valueOf(excludeLineId), Integer.valueOf(excludeLineId));
		if (total == null)
			total = Env.ZERO;

		DB.executeUpdateEx(
				"UPDATE C_Contract SET GrandTotal=?, TotalLines=? WHERE C_Contract_ID=?",
				new Object[] { total, total, Integer.valueOf(cContractId) },
				trxName);

		return "";

	}

}
