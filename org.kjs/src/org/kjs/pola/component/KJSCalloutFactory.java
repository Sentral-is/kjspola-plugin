package org.kjs.pola.component;

import org.kjs.pola.callout.CalloutARProInv;
import org.kjs.pola.callout.CalloutARProInvLine;
import org.kjs.pola.callout.CalloutContract;
import org.kjs.pola.callout.CalloutContractLine;
import org.kjs.pola.callout.CalloutOrder;
import org.kjs.pola.callout.CalloutOrderLine;
import org.kjs.pola.callout.CalloutProductionSingle;
import org.kjs.pola.callout.CalloutQuotation;
import org.kjs.pola.callout.CalloutQuotationLine;
import org.adempiere.base.IColumnCallout;
import org.adempiere.base.IColumnCalloutFactory;

public class KJSCalloutFactory implements IColumnCalloutFactory {
	public IColumnCallout[] getColumnCallouts(final String tableName, final String columnName) {
		if (tableName.equalsIgnoreCase("M_Production")) {
			return new IColumnCallout[] { (IColumnCallout) new CalloutProductionSingle() };
		}else if(tableName.equalsIgnoreCase("C_Contract")) {
			return new IColumnCallout[] { (IColumnCallout) new CalloutContract() };
		}else if(tableName.equalsIgnoreCase("C_ContractLine")) {
			return new IColumnCallout[] { (IColumnCallout) new CalloutContractLine() };
		}else if(tableName.equalsIgnoreCase("C_Quotation")) {
			return new IColumnCallout[] { (IColumnCallout) new CalloutQuotation() };
		}else if(tableName.equalsIgnoreCase("C_QuotationLine")) {
			return new IColumnCallout[] { (IColumnCallout) new CalloutQuotationLine() };
		}else if(tableName.equalsIgnoreCase("C_Order")) {
			return new IColumnCallout[] { (IColumnCallout) new CalloutOrder()};
		}else if(tableName.equalsIgnoreCase("C_OrderLine")) {
			return new IColumnCallout[] { (IColumnCallout) new CalloutOrderLine() };
		}else if(tableName.equalsIgnoreCase("C_ARProInv")) {
			if ("C_BPartner_ID".equals(columnName) || "C_Order_ID".equals(columnName)) {
				return new IColumnCallout[] { (IColumnCallout) new CalloutARProInv() };
			}
			return null;
		}else if(tableName.equalsIgnoreCase("C_ARProInvLine")) {
			if ("M_Product_ID".equals(columnName) || "QtyEntered".equals(columnName)
					|| "PriceEntered".equals(columnName)) {
				return new IColumnCallout[] { (IColumnCallout) new CalloutARProInvLine()};
			}
			return null;
		}
		
		return null;
	}
}
