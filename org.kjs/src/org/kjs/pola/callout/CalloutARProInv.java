package org.kjs.pola.callout;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.util.DB;
import org.compiere.util.Env;

public class CalloutARProInv implements IColumnCallout{

	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue) {
		if (mField.getColumnName().equals("C_BPartner_ID")) {
			return this.BPartnerCallout(ctx, WindowNo, mTab, mField, value, oldValue);
		}else if(mField.getColumnName().equals("C_Order_ID")) {
			return this.OrderCallout(ctx, WindowNo, mTab, mField, value, oldValue);
		}
		return null;
	}
	
	
	public String BPartnerCallout(final Properties ctx, final int WindowNo, final GridTab mTab, final GridField mField,final Object value, final Object oldValue) {
		if (value == null) {
			return "";
		}
		// Only skip when the value did not actually change (e.g. same partner re-selected).
		// Do NOT infer "loading" from oldValue==null: that also happens on a first-time edit.
		if (value.equals(oldValue)) {
			return "";
		}

		int C_BPartner_ID = (int) value;

		StringBuilder SQLGetBPLoc = new StringBuilder();
		SQLGetBPLoc.append("SELECT C_BPartner_Location_ID");
		SQLGetBPLoc.append(" FROM C_BPartner_Location");
		SQLGetBPLoc.append(" WHERE AD_Client_ID = ?");
		SQLGetBPLoc.append(" AND C_BPartner_ID = ?");
		SQLGetBPLoc.append(" AND IsActive = 'Y'");
		SQLGetBPLoc.append(" ORDER BY IsBillTo DESC, C_BPartner_Location_ID");

		int C_BPartner_Location_ID = DB.getSQLValueEx(null, SQLGetBPLoc.toString(),new Object[] { Env.getAD_Client_ID(ctx), C_BPartner_ID });

		mTab.setValue("C_BPartner_Location_ID", C_BPartner_Location_ID);

		return null;
	}
	
	
	public String OrderCallout(final Properties ctx, final int WindowNo, final GridTab mTab, final GridField mField,final Object value, final Object oldValue) {
		if (value == null) {
			return "";
		}
		// Only skip when the value did not actually change (e.g. same order re-selected).
		// Do NOT infer "loading" from oldValue==null: that also happens on a first-time edit.
		if (value.equals(oldValue)) {
			return "";
		}

		int C_Order_ID = (int) value;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = DB.prepareStatement(
					"SELECT DateOrdered, C_BPartner_ID, C_BPartner_Location_ID, M_PriceList_ID, C_Currency_ID FROM C_Order WHERE C_Order_ID=?",
					null);
			pstmt.setInt(1, C_Order_ID);
			rs = pstmt.executeQuery();
			if (rs.next()) {
				Timestamp dateOrdered = rs.getTimestamp(1);
				mTab.setValue("DateOrdered", dateOrdered);
				mTab.setValue("C_BPartner_ID", Integer.valueOf(rs.getInt(2)));
				mTab.setValue("C_BPartner_Location_ID", Integer.valueOf(rs.getInt(3)));
				mTab.setValue("M_PriceList_ID", Integer.valueOf(rs.getInt(4)));
				mTab.setValue("C_Currency_ID", Integer.valueOf(rs.getInt(5)));
			}
		} catch (SQLException e) {
			return e.getLocalizedMessage();
		} finally {
			DB.close(rs, pstmt);
		}

		return null;
	}

}
