package org.kjs.pola.process;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MProduction;
import org.compiere.model.MProductionLine;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.kjs.pola.model.X_KJS_ProductionPlan;
import org.kjs.pola.model.X_KJS_ProductionPlanLine;

public class POLA_JOBPHASE_CreateProduction extends SvrProcess {
	
	
	private BigDecimal p_QtyProcess = Env.ZERO;
	private int p_KJS_ProductionPlanLine_ID = 0;
	private int p_KJS_ProductionPlan_ID = 0;
	private MProduction LHP = null;
	private int p_M_Production_ID = 0;

	@Override
	protected void prepare() {
	
		final ProcessInfoParameter[] para = this.getParameter();
			for (int i = 0; i < para.length; ++i) {
			 
			final String name = para[i].getParameterName();
	         if (name.equals("QtyProcess")) {
	        	 p_QtyProcess = para[i].getParameterAsBigDecimal();
	         }else {
	            this.log.log(Level.SEVERE, "Unknown Parameter: " + name);
	            
	         }
	    }
			
			LHP = new MProduction(getCtx(), getRecord_ID(), get_TrxName());
			p_KJS_ProductionPlan_ID = (int) LHP.get_Value("KJS_ProductionPlan_ID");
			p_KJS_ProductionPlanLine_ID = (int) LHP.get_Value("KJS_ProductionPlanLine_ID");
			
			p_M_Production_ID = getRecord_ID();

	}

	@Override
	protected String doIt() throws Exception {
		X_KJS_ProductionPlanLine JobPhase = new X_KJS_ProductionPlanLine(getCtx(), p_KJS_ProductionPlanLine_ID, get_TrxName());
		X_KJS_ProductionPlan JobParent = new X_KJS_ProductionPlan(getCtx(), p_KJS_ProductionPlan_ID, get_TrxName());
		
		MProduction production = new MProduction(getCtx(), p_M_Production_ID, get_TrxName());
		
//		production.setAD_Org_ID(JobPhase.getAD_Org_ID());
////		production.set_CustomColumn("KJS_ProductionPlan_ID", JobPhase.getKJS_ProductionPlan_ID());
////		production.set_CustomColumn("KJS_ProductionPlanLine_ID", JobPhase.getKJS_ProductionPlanLine_ID());
//		production.set_CustomColumn("KJS_ProductAsset_ID" , JobPhase.getKJS_ProductAsset_ID());
//		production.setMovementDate(JobParent.getDateDoc());
//		production.setDatePromised(JobParent.getDateDoc());
//		production.setDescription((String) JobParent.get_Value("Description"));
//		production.setM_Product_ID(JobPhase.getM_Product_ID());
//		production.setM_Locator_ID(JobPhase.getM_Locator_ID());
		production.setProductionQty(p_QtyProcess);
////		production.setC_OrderLine_ID(JobPhase.getC_OrderLine_ID());
////		production.setC_BPartner_ID(prodPlanning.getC_BPartner_ID());
//		production.setDocStatus("DR");
//		production.setIsCreated("Y");
		production.saveEx();
		
		int line  = 10;
		
		MProductionLine productionLine = new MProductionLine(getCtx(), 0, get_TrxName());
		productionLine.setAD_Org_ID(production.getAD_Org_ID());
		productionLine.setM_Production_ID(production.getM_Production_ID());
		productionLine.setLine(line);
		productionLine.setM_Product_ID(production.getM_Product_ID());
		productionLine.setIsActive(true);
		productionLine.setPlannedQty(production.getProductionQty());
		productionLine.setM_Locator_ID(production.getM_Locator_ID());
		productionLine.setDescription(production.getDescription());
		productionLine.saveEx();
		
		
		StringBuilder SQLUpdate = new StringBuilder();
		SQLUpdate.append("UPDATE M_ProductionLine");
		SQLUpdate.append(" SET MovementQty = "+production.getProductionQty());
		SQLUpdate.append(" ,IsEndProduct = 'Y'");
		SQLUpdate.append(" WHERE M_ProductionLine_ID = "+productionLine.getM_ProductionLine_ID());
		DB.executeUpdate(SQLUpdate.toString(), true, get_TrxName());
		
//        final StringBuilder sqlBOM = new StringBuilder("SELECT prod.M_Product_ID,prod.Value,pplb.Qty,asi.M_AttributeSetInstance_ID,asi.Description FROM KJS_ProductionPlanLineBOM pplb JOIN M_Product prod ON pplb.M_Product_ID=prod.M_Product_ID LEFT JOIN M_AttributeSetInstance asi ON pplb.M_AttributeSetInstance_ID=asi.M_AttributeSetInstance_ID WHERE KJS_ProductionPlanLine_ID=?");

		// Phase 2: M_Alternate_ID is the canonical alternate, stored as a column on PP_Product_BOMLine.
		// Read the BOM lines directly (not the M_Product_BOM view, which hides lines under inactive
		// headers that were active in 6.2). Component = l.M_Product_ID, qty = l.QtyBOM, parent product
		// = the header's b.M_Product_ID.
		StringBuilder SQLGetBOM = new StringBuilder();
		SQLGetBOM.append("SELECT l.M_Product_ID, l.QtyBOM");
		SQLGetBOM.append(" FROM PP_Product_BOMLine l");
		SQLGetBOM.append(" JOIN PP_Product_BOM b ON b.PP_Product_BOM_ID = l.PP_Product_BOM_ID");
		SQLGetBOM.append(" WHERE b.M_Product_ID = ?");
		SQLGetBOM.append(" AND l.M_Alternate_ID = ?");
		SQLGetBOM.append(" AND l.IsActive = 'Y'");
		SQLGetBOM.append(" ORDER BY l.Line");

        PreparedStatement pstmt = null;
	    ResultSet rs = null;
	    int componentCount = 0;
	    	try {
	            pstmt = (PreparedStatement)DB.prepareStatement(SQLGetBOM.toString(), (String)null);
                pstmt.setInt(1, JobPhase.getM_Product_ID());
                pstmt.setInt(2, JobParent.get_ValueAsInt("M_Alternate_ID"));

	            rs = pstmt.executeQuery();
	            while (rs.next()) {
	            	componentCount++;
	            	line = line+10;
	                final MProductionLine pl = new MProductionLine(Env.getCtx(), 0, this.get_TrxName());
                    final int M_ProductBOM_ID = rs.getInt(1);
                    final BigDecimal Qty = rs.getBigDecimal(2).multiply(production.getProductionQty());
                    pl.setAD_Org_ID(production.getAD_Org_ID());
                    pl.setM_Product_ID(M_ProductBOM_ID);
                    pl.setM_Locator_ID(production.getM_Locator_ID());
                    pl.setPlannedQty(Qty);
                    pl.setQtyUsed(Qty);
                    pl.setM_Production_ID(production.getM_Production_ID());
                    pl.setLine(line);
                    pl.saveEx(this.get_TrxName());

	            }
	        }
	        catch (SQLException err) {
	            // Surface the failure instead of swallowing it: the old code logged and called
	            // rollback() but still returned success, so the LHP Line grid came back empty with
	            // no error shown. SvrProcess rolls the transaction back when we throw.
	            throw new AdempiereException("Failed to create LHP component lines", err);
	        }
	        finally {
	            DB.close(rs, (Statement)pstmt);
	            rs = null;
	            pstmt = null;
	        }

	    	// A properly JOB-linked production with a chosen alternate must resolve to components.
	    	// Zero here means the alternate has no BOM mapping (bad/missing data) -> fail loudly
	    	// rather than silently create only the end-product line and mark it created.
	    	// Deliberately excludes the unlinked-production case (no JOB phase / no alternate).
	    	if (p_KJS_ProductionPlanLine_ID > 0
	    			&& JobParent.get_ValueAsInt("M_Alternate_ID") > 0
	    			&& componentCount == 0) {
	    		throw new AdempiereException("No active BOM components found for the selected alternate");
	    	}

	    	production.setIsCreated("Y");
	    	production.saveEx();
		
	    	
	    BigDecimal Current = (BigDecimal) JobPhase.get_Value("QtyToDeliver");	
	    	
	    JobPhase.set_CustomColumn("QtyToDeliver", Current.add(p_QtyProcess));
	    JobPhase.saveEx();
		
		return null;
	}

}
