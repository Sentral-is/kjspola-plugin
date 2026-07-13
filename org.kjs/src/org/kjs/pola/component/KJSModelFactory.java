package org.kjs.pola.component;

import java.lang.reflect.Constructor;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.base.IModelFactory;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.kjs.pola.model.MCostExt;
import org.kjs.pola.model.MInvoiceExt;
import org.kjs.pola.model.MPaymentExt;
import org.kjs.pola.model.MProductExt;
import org.kjs.pola.model.MProductionExt;
import org.kjs.pola.model.MProductionPlanKJS;
import org.kjs.pola.model.MProductionPlanLineBOMKJS;
import org.kjs.pola.model.MProductionPlanLineKJS;
import org.kjs.pola.model.X_C_ARProInv;
import org.kjs.pola.model.X_C_ARProInvLine;
import org.kjs.pola.model.X_C_Contract;
import org.kjs.pola.model.X_C_ContractLine;
import org.kjs.pola.model.X_C_Quotation;
import org.kjs.pola.model.X_C_QuotationLine;
import org.kjs.pola.model.X_I_KJS_ProductionPlan;
import org.kjs.pola.model.X_I_ProductPhase;
import org.kjs.pola.model.X_I_Product_BOM;
import org.kjs.pola.model.X_KJS_Phase;
import org.kjs.pola.model.X_KJS_ProductAsset;
import org.kjs.pola.model.X_KJS_ProductPhase;
import org.kjs.pola.model.X_KJS_ProductPhaseLine;
import org.kjs.pola.model.X_KJS_ProductionPlanLine;
import org.kjs.pola.model.X_KJS_ProductionPlanLineBOM;
import org.kjs.pola.model.X_KJS_ProductionPlanLink;
import org.kjs.pola.model.X_KJS_QC;
import org.kjs.pola.model.X_M_Alternate;

public class KJSModelFactory implements IModelFactory
{
    CLogger log;
    
    public KJSModelFactory() {
        this.log = CLogger.getCLogger(KJSModelFactory.class);
    }
    
    public Class<?> getClass(final String tableName) {
        if (tableName.equals("KJS_Phase")) {
            return X_KJS_Phase.class;
        }
        if (tableName.equals("KJS_ProductAsset")) {
            return X_KJS_ProductAsset.class;
        }
        if (tableName.equals("C_Invoice")) {
            return MInvoiceExt.class;
        }
        if (tableName.equals("KJS_ProductionPlan")) {
            return MProductionPlanKJS.class;
        }
        if (tableName.equals("KJS_ProductionPlanLine")) {
            return MProductionPlanLineKJS.class;
        }
        if (tableName.equals("KJS_ProductionPlanLineBOM")) {
            return MProductionPlanLineBOMKJS.class;
        }
        if (tableName.equals("KJS_ProductionPlanLink")) {
            return X_KJS_ProductionPlanLink.class;
        }
        if (tableName.equals("KJS_ProductPhase")) {
            return X_KJS_ProductPhase.class;
        }
        if (tableName.equals("KJS_ProductPhaseLine")) {
            return X_KJS_ProductPhaseLine.class;
        }
        if (tableName.equals("M_Production")) {
            return MProductionExt.class;
        }
        if (tableName.equals("M_Cost")) {
            return MCostExt.class;
        }
        if (tableName.equals("M_Product")) {
            return MProductExt.class;
        }
        if (tableName.equals("I_Product_BOM")) {
            return X_I_Product_BOM.class;
        }
        if (tableName.equals("I_ProductPhase")) {
            return X_I_ProductPhase.class;
        }
        if (tableName.equals("C_Payment")) {
            return MPaymentExt.class;
        }
        if (tableName.equals("KJS_QC")) {
            return X_KJS_QC.class;
        }
        if (tableName.equals("M_Alternate")) {
            return X_M_Alternate.class;
        }
        if (tableName.equals("C_Quotation")) {
            return X_C_Quotation.class;
        }
        if (tableName.equals("C_QuotationLine")) {
            return X_C_QuotationLine.class;
        }
        if (tableName.equals("C_Contract")) {
            return X_C_Contract.class;
        }
        if (tableName.equals("C_ContractLine")) {
            return X_C_ContractLine.class;
        }
        if (tableName.equals("C_ARProInv")) {
            return X_C_ARProInv.class;
        }
        if (tableName.equals("C_ARProInvLine")) {
            return X_C_ARProInvLine.class;
        }
        if (tableName.equals("I_KJS_ProductionPlan")) {
            return X_I_KJS_ProductionPlan.class;
        }
        return null;
    }
    
    public PO getPO(final String tableName, final int Record_ID, final String trxName) {
        final Class<?> clazz = this.getClass(tableName);
        if (clazz == null) {
            return null;
        }
        PO model = null;
        Constructor<?> constructor = null;
        try {
            constructor = clazz.getDeclaredConstructor(Properties.class, Integer.TYPE, String.class);
            model = (PO)constructor.newInstance(Env.getCtx(), Record_ID, trxName);
        }
        catch (Exception ex) {
            if (this.log.isLoggable(Level.WARNING)) {
                this.log.warning(String.format("Plugin: %s -> Class can not be instantiated for table: %s", "KJS", tableName));
            }
        }
        return model;
    }
    
    public PO getPO(final String tableName, final ResultSet rs, final String trxName) {
        final Class<?> clazz = this.getClass(tableName);
        if (clazz == null) {
            return null;
        }
        PO model = null;
        Constructor<?> constructor = null;
        try {
            constructor = clazz.getDeclaredConstructor(Properties.class, ResultSet.class, String.class);
            model = (PO)constructor.newInstance(Env.getCtx(), rs, trxName);
        }
        catch (Exception ex) {
            if (this.log.isLoggable(Level.WARNING)) {
                this.log.warning(String.format("Plugin: %s -> Class can not be instantiated for table: %s", "KJS", tableName));
            }
        }
        return model;
    }
}
