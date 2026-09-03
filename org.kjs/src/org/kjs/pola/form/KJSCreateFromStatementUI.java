package org.kjs.pola.form;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Vector;
import java.util.logging.Level;

import org.compiere.model.GridTab;
import org.compiere.util.DB;
import org.compiere.util.KeyNamePair;

/**
 * Bank/Cash Statement "Create lines from" override.
 *
 * Restores the iDempiere 6.2 behavior where <b>Reversed ('RE')</b> and
 * <b>Voided ('VO')</b> payments are selectable in the picker. iDempiere 13 core
 * (org.compiere.grid.CreateFromStatement#getBankAccountData) tightened the filter
 * to {@code DocStatus IN ('CO','CL')}, silently hiding reversed/voided payments so
 * their offsetting bank statement lines can no longer be created. 6.2 used
 * {@code DocStatus IN ('CO','CL','RE','VO')} (CreateFromBatch.getSQLWhere).
 *
 * Only the payment branch of the query is widened; the deposit-batch branch is
 * left as core provides it. Everything else (columns, params, deposit-batch UNION)
 * is a verbatim copy of the core method so behavior is otherwise identical.
 *
 * Wired in via {@link org.kjs.pola.component.KJSCreateFromFactory} for table
 * {@code C_BankStatement}.
 */
public class KJSCreateFromStatementUI extends org.adempiere.webui.apps.form.WCreateFromStatementUI
{
	public KJSCreateFromStatementUI(GridTab tab)
	{
		super(tab);
	}

	@Override
	protected Vector<Vector<Object>> getBankAccountData(Integer BankAccount, Integer BPartner, String DocumentNo,
			Timestamp DateFrom, Timestamp DateTo, BigDecimal AmtFrom, BigDecimal AmtTo, Integer DocType, String TenderType, String AuthCode, Integer Currency)
	{
		Vector<Vector<Object>> data = new Vector<Vector<Object>>();

		StringBuilder sql = new StringBuilder();
		sql.append("WITH Payments AS ( ");
		sql.append("SELECT p.DateTrx as DateTrx, p.C_Payment_ID, NULL AS C_DepositBatch_ID, p.DocumentNo, p.C_Currency_ID, c.ISO_Code, p.PayAmt,");
		sql.append(" currencyConvert(p.PayAmt,p.C_Currency_ID,ba.C_Currency_ID,p.DateAcct,p.C_ConversionType_ID,p.AD_Client_ID,p.AD_Org_ID) AS ConvAmount, bp.Name,");
		sql.append(" p.Processed, p.C_BankAccount_ID, p.C_DocType_ID, p.TenderType, p.R_AuthCode, p.C_BPartner_ID ");
		sql.append("FROM C_BankAccount ba");
		sql.append(" INNER JOIN C_Payment_v p ON (p.C_BankAccount_ID=ba.C_BankAccount_ID)");
		sql.append(" INNER JOIN C_Currency c ON (p.C_Currency_ID=c.C_Currency_ID)");
		sql.append(" LEFT OUTER JOIN C_BPartner bp ON (p.C_BPartner_ID=bp.C_BPartner_ID) ");
		sql.append(" WHERE (p.C_DepositBatch_ID = 0 OR p.C_DepositBatch_ID IS NULL) ");
		sql.append(" AND p.IsReconciled = 'N'");
		// KJS: restore 6.2 behavior - include Reversed ('RE') and Voided ('VO') payments
		sql.append(" AND p.DocStatus IN ('CO','CL','RE','VO') AND p.PayAmt<>0");
		sql.append(" AND NOT EXISTS (SELECT 1 FROM C_BankStatementLine l WHERE p.C_Payment_ID=l.C_Payment_ID AND l.StmtAmt <> 0)");

		// Add Deposit Batch in selection
		sql.append("UNION ALL ");
		sql.append("SELECT db.DateDeposit AS DateTrx, NULL AS C_Payment_ID, db.C_DepositBatch_ID, db.DocumentNo, p.C_Currency_ID, c.ISO_Code, SUM(p.PayAmt) AS PayAmt,");
		sql.append(" SUM(currencyConvert(p.PayAmt,p.C_Currency_ID,ba.C_Currency_ID,p.DateAcct,p.C_ConversionType_ID,p.AD_Client_ID,p.AD_Org_ID)) AS ConvAmount, NULL As Name,");
		sql.append(" p.Processed, p.C_BankAccount_ID, p.C_DocType_ID, NULL AS TenderType, NULL AS R_AuthCode, NULL AS C_BPartner_ID ");
		sql.append(" FROM C_BankAccount ba");
		sql.append(" INNER JOIN C_DepositBatch db ON (db.C_BankAccount_ID=ba.C_BankAccount_ID)");
		sql.append(" INNER JOIN C_DepositBatchLine dbl ON (dbl.C_DepositBatch_ID = db.C_DepositBatch_ID)");
		sql.append(" INNER JOIN C_Payment_v p ON (p.C_Payment_ID=dbl.C_Payment_ID)");
		sql.append(" INNER JOIN C_Currency c ON (p.C_Currency_ID=c.C_Currency_ID)");
		sql.append(" WHERE db.DocStatus IN ('CO','CL') AND db.DepositAmt<>0");
		sql.append(" AND NOT EXISTS (SELECT 1 FROM C_BankStatementLine l WHERE p.C_Payment_ID=l.C_Payment_ID AND l.StmtAmt <> 0)");
		sql.append(" AND NOT EXISTS (SELECT 1 FROM C_BankStatementLine l WHERE db.C_DepositBatch_ID=l.C_DepositBatch_ID AND l.StmtAmt <> 0)");
		sql.append(" GROUP BY db.C_DepositBatch_ID,db.DocumentNo,p.C_Currency_ID, c.ISO_Code, db.DateDeposit, p.Processed, p.C_BankAccount_ID, p.C_DocType_ID ");

		sql.append(") SELECT DateTrx, C_Payment_ID, C_DepositBatch_ID, DocumentNo, C_Currency_ID, ISO_Code, PayAmt, ConvAmount, Name FROM Payments p ");
		sql.append(getSQLWhere(BPartner, DocumentNo, DateFrom, DateTo, AmtFrom, AmtTo, DocType, TenderType, AuthCode, Currency, 0));
		sql.append(" ORDER BY DateTrx");

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql.toString(), getTrxName());
			setParameters(pstmt, BankAccount, BPartner, DocumentNo, DateFrom, DateTo, AmtFrom, AmtTo, DocType, TenderType, AuthCode, Currency, 0);
			rs = pstmt.executeQuery();
			while(rs.next())
			{
				Vector<Object> line = new Vector<Object>(7);
				line.add(Boolean.FALSE);       //  0-Selection
				line.add(rs.getTimestamp(1));       //  1-DateTrx
				if (rs.getInt(2) > 0)
					line.add(new KeyNamePair(rs.getInt(2), rs.getString(4)));
				else
					line.add(null); 				// 2-C_Payment_ID

				if (rs.getInt(3) > 0)
					line.add(new KeyNamePair(rs.getInt(3), rs.getString(4)));
				else
					line.add(null);					// 3-DepositBatch
				line.add(new KeyNamePair(rs.getInt(5), rs.getString(6))); //  4-Currency
				line.add(rs.getBigDecimal(7));      //  5-PayAmt
				line.add(rs.getBigDecimal(8));      //  6-Conv Amt
				line.add(rs.getString(9));      	//  7-BParner
				data.add(line);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql.toString(), e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null; pstmt = null;
		}

		return data;
	}
}
