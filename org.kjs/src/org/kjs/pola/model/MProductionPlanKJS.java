package org.kjs.pola.model;

import java.io.File;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Properties;

import org.compiere.model.MMovement;
import org.compiere.model.MMovementLine;
import org.compiere.model.MOrderLine;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
import org.compiere.process.DocumentEngine;
import org.compiere.util.DB;
import org.compiere.util.Util;

public class MProductionPlanKJS extends X_KJS_ProductionPlan implements DocAction
{
    private static final long serialVersionUID = 8106460316172293611L;
    private String m_processMsg;
    private boolean m_justPrepared;
    
    public MProductionPlanKJS(final Properties ctx, final int KJS_ProductionPlan_ID, final String trxName) {
        super(ctx, KJS_ProductionPlan_ID, trxName);
        this.m_processMsg = null;
        this.m_justPrepared = false;
    }
    
    public MProductionPlanKJS(final Properties ctx, final ResultSet rs, final String trxName) {
        super(ctx, rs, trxName);
        this.m_processMsg = null;
        this.m_justPrepared = false;
    }
    
    public boolean processIt(final String action) throws Exception {
        this.m_processMsg = null;
        final DocumentEngine engine = new DocumentEngine((DocAction)this, this.getDocStatus());
        return engine.processIt(action, this.getDocAction());
    }
    
    public boolean unlockIt() {
        return true;
    }
    
    public boolean invalidateIt() {
        return true;
    }
    
    public String prepareIt() {
        this.m_justPrepared = true;
        return "IP";
    }
    
    public boolean approveIt() {
        return true;
    }
    
    public boolean rejectIt() {
        return true;
    }
    
    public String completeIt() {
    	m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
        final String set = "UPDATE KJS_ProductionPlanLine SET Processed='Y' WHERE KJS_ProductionPlan_ID=?";
        DB.executeUpdate(set, this.getKJS_ProductionPlan_ID(), this.get_TrxName());
        
		String valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (valid != null)
			return DocAction.STATUS_Invalid;
		
		//Create Bon Produksi
		MProductionPlanLineKJS[] lines = getLines(null, "");
		for(MProductionPlanLineKJS line : lines) {
			MMovement mov = new MMovement(getCtx(), 0, get_TrxName());
			mov.setAD_Org_ID(getAD_Org_ID());
			mov.setC_DocType_ID(1000522);
			mov.setPOReference(line.getKJS_Phase().getName());
			mov.setDescription(getDocumentNo());
			mov.setMovementDate(new Timestamp(System.currentTimeMillis()));
			
			MProductionPlanLineBOMKJS[] boms = line.getLines(null, "");
			for(MProductionPlanLineBOMKJS bom : boms) {

				mov.saveEx(get_TrxName());
				
				MMovementLine ml = new MMovementLine(mov);
				ml.setM_Product_ID(bom.getM_Product_ID());
				ml.set_ValueNoCheck("KJS_ProductionPlan_ID", getKJS_ProductionPlan_ID());
				ml.setM_Locator_ID(1000565);
				ml.setM_LocatorTo_ID(1000566);
				BigDecimal prodQty = (BigDecimal) line.get_Value("ProductionQty");
				ml.setMovementQty(bom.getQty().multiply(prodQty));
				ml.save(get_TrxName());
			}
		}
		setProcessed(true);	
		//
		setDocAction(DOCACTION_Close);
		return DocAction.STATUS_Completed;
    }
    
	public MProductionPlanLineKJS[] getLines (String whereClause, String orderClause)
	{
		StringBuilder whereClauseFinal = new StringBuilder(MProductionPlanLineKJS.COLUMNNAME_KJS_ProductionPlan_ID+"=? ");
		if (!Util.isEmpty(whereClause, true))
			whereClauseFinal.append(whereClause);
		if (orderClause.length() == 0)
			orderClause = MProductionPlanLineKJS.COLUMNNAME_Line;
		//
		List<MProductionPlanLineKJS> list = new Query(getCtx(), MProductionPlanLineKJS.Table_Name, whereClauseFinal.toString(), get_TrxName())
										.setParameters(get_ID())
										.setOrderBy(orderClause)
										.list();
		//
		return list.toArray(new MProductionPlanLineKJS[list.size()]);		
	}	//	getLines
    
    public boolean voidIt() {
        final String set = "UPDATE KJS_ProductionPlanLine SET Processed='Y' WHERE KJS_ProductionPlan_ID=?";
        DB.executeUpdate(set, this.getKJS_ProductionPlan_ID(), this.get_TrxName());
        this.setProcessed(true);
        return true;
    }
    
    public boolean closeIt() {
        return true;
    }
    
    public boolean reverseCorrectIt() {
        return true;
    }
    
    public boolean reverseAccrualIt() {
        return true;
    }
    
    public boolean reActivateIt() {
        return true;
    }
    
    public String getSummary() {
        return null;
    }
    
    public String getDocumentInfo() {
        return null;
    }
    
    public File createPDF() {
        return null;
    }
    
    public String getProcessMsg() {
        return null;
    }
    
    public int getDoc_User_ID() {
        return 0;
    }
    
    public int getC_Currency_ID() {
        return 0;
    }
    
    public BigDecimal getApprovalAmt() {
        return null;
    }
}
