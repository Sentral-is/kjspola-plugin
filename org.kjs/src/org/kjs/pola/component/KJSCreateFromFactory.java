package org.kjs.pola.component;

import org.kjs.pola.form.KJSCreateFromStatementUI;
import org.kjs.pola.form.WCreateFromShipmentUI;
import org.kjs.pola.form.WCreateFromReceiptUI;
import org.kjs.pola.form.WCreateFromInvoiceUI;
import org.kjs.pola.form.WCreateFromProductionPlanLineUI;
import org.kjs.pola.form.WCreateFromProformaInv;
import org.kjs.pola.form.WCreateFromOrderUI;
import org.kjs.pola.form.WCreateFromMPSUI;
import org.compiere.grid.ICreateFrom;
import org.compiere.model.GridTab;
import org.compiere.grid.ICreateFromFactory;

public class KJSCreateFromFactory implements ICreateFromFactory
{
    public ICreateFrom create(final GridTab mTab) {
        final String tableName = mTab.getTableName();
        if (tableName.equals("KJS_ProductionPlan")) {
            return (ICreateFrom)new WCreateFromMPSUI(mTab);
        }
        
        if (tableName.equals("C_Order")) {
            return (ICreateFrom)new WCreateFromOrderUI(mTab);
        }
        
        if (tableName.equals("KJS_ProductionPlanLine")) {
            return (ICreateFrom)new WCreateFromProductionPlanLineUI(mTab);
        }
        
        if (tableName.equals("C_Invoice")) {
            return (ICreateFrom)new WCreateFromInvoiceUI(mTab);
        }
        
        if(tableName.equals("M_InOut")) {
                  
	        final Boolean isSOTrx = (Boolean)mTab.getValue("IsSOTrx");
	        if (!isSOTrx) {
	            return (ICreateFrom)new WCreateFromReceiptUI(mTab);
	        }else {
	            return (ICreateFrom)new WCreateFromShipmentUI(mTab);
	        }
        }
        
        if (tableName.equals("C_ARProInv")) {
            return (ICreateFrom)new WCreateFromProformaInv(mTab);
        }

        // Bank/Cash Statement: restore 6.2 picker behavior (include Reversed/Voided payments)
        if (tableName.equals("C_BankStatement")) {
            return (ICreateFrom)new KJSCreateFromStatementUI(mTab);
        }

        return null;
    }
}
