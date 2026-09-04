package org.kjs.pola.form;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;

import org.compiere.minigrid.IMiniTable;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;

/**
 * Payment Allocation form for PT PPJ: core iDempiere 13 allocation plus a
 * read-only <b>Date Collect</b> column on the Invoice grid.
 *
 * <p>Why a subclass of core (not the plugin's own {@code WAllocation}): the AD_Form
 * "Payment Allocation" is wired to core {@code org.compiere.apps.form.VAllocation}
 * → core {@code org.adempiere.webui.apps.form.WAllocation}. The plugin's own
 * allocation form is not registered in iDempiere 13's form factory, and its only
 * customization ({@code invoiceopenkjs}) is inert in v13 (identical results to core).
 * So we keep 100% of core 13 behavior and add only the requested column.
 *
 * <p>Wired in via {@link org.kjs.pola.component.KJSFormFactory} for form name
 * {@code org.compiere.apps.form.VAllocation}.
 *
 * <p>The column is appended as the <b>last</b> Invoice-grid column, after core's
 * own (which vary by isMultiCurrency and the "Same Business Partner" toggle), so no
 * existing column index shifts and core's amount/edit logic is untouched. All three
 * grid builders stay consistent: names, row data, and column classes each get one
 * trailing entry keyed off {@code super.getInvoiceColumnNames(...).size()}.
 */
public class KJSWAllocation extends org.adempiere.webui.apps.form.WAllocation
{
	private static final CLogger log = CLogger.getCLogger(KJSWAllocation.class);

	public KJSWAllocation()
	{
		super();
	}

	@Override
	public Vector<String> getInvoiceColumnNames(boolean isMultiCurrency, boolean sameBP)
	{
		Vector<String> names = super.getInvoiceColumnNames(isMultiCurrency, sameBP);
		names.add(Msg.translate(Env.getCtx(), "DateCollect"));
		return names;
	}

	@Override
	public void setInvoiceColumnClass(IMiniTable invoiceTable, boolean isMultiCurrency, boolean sameBP)
	{
		super.setInvoiceColumnClass(invoiceTable, isMultiCurrency, sameBP);
		// DateCollect sits right after core's columns; its index == core column count.
		int dateCollectIdx = super.getInvoiceColumnNames(isMultiCurrency, sameBP).size();
		invoiceTable.setColumnClass(dateCollectIdx, Timestamp.class, true); // read-only
		invoiceTable.autoSize();
	}

	@Override
	public Vector<Vector<Object>> getInvoiceData(boolean isMultiCurrency, Timestamp date, boolean sameBP, String trxName)
	{
		Vector<Vector<Object>> data = super.getInvoiceData(isMultiCurrency, date, sameBP, trxName);
		if (data == null || data.isEmpty())
			return data;

		// Collect invoice ids from the document key/name pair column
		// (MInvoice.UNPAID_INVOICE_DOCUMENT_KEY_NAME_PAIR == index 2).
		StringBuilder ids = new StringBuilder();
		for (Vector<Object> row : data)
		{
			Object key = row.get(2);
			if (key instanceof KeyNamePair)
			{
				if (ids.length() > 0)
					ids.append(",");
				ids.append(((KeyNamePair) key).getKey());
			}
		}

		// One query: C_Invoice_ID -> DateCollect (ids are ints, no injection risk)
		Map<Integer, Timestamp> collectByInvoice = new HashMap<Integer, Timestamp>();
		if (ids.length() > 0)
		{
			String sql = "SELECT C_Invoice_ID, DateCollect FROM C_Invoice WHERE C_Invoice_ID IN (" + ids + ")";
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			try
			{
				pstmt = DB.prepareStatement(sql, trxName);
				rs = pstmt.executeQuery();
				while (rs.next())
					collectByInvoice.put(rs.getInt(1), rs.getTimestamp(2));
			}
			catch (SQLException e)
			{
				log.log(Level.SEVERE, sql, e);
			}
			finally
			{
				DB.close(rs, pstmt);
			}
		}

		// Append DateCollect (may be null) as the last column of each row
		for (Vector<Object> row : data)
		{
			Object key = row.get(2);
			Timestamp dc = (key instanceof KeyNamePair) ? collectByInvoice.get(((KeyNamePair) key).getKey()) : null;
			row.add(dc);
		}
		return data;
	}
}
