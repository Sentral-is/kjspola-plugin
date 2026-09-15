package org.kjs.pola.process;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.kjs.pola.model.X_KJS_ProductionPlan;
import org.kjs.pola.model.X_KJS_ProductionPlanLine;
import org.kjs.pola.model.X_M_Alternate;

public class POLA_JOB_CreateFromSO extends SvrProcess{
	
	private int p_C_OrderLine_ID = 0;
	private BigDecimal p_QtyOrdered = Env.ZERO;
	private int Record_ID = 0;
	private boolean IsCorrection = false;
	
	@Override
	protected void prepare() {
		final ProcessInfoParameter[] para = this.getParameter();
		for (int i = 0; i < para.length; ++i) {
			 
			final String name = para[i].getParameterName();
	         if (name.equals("C_OrderLine_ID")) {
	        	 p_C_OrderLine_ID = para[i].getParameterAsInt();
	         }else if(name.equals("QtyOrdered")) {
	            p_QtyOrdered = para[i].getParameterAsBigDecimal();
	         }else {
	            this.log.log(Level.SEVERE, "Unknown Parameter: " + name);
	            
	         }
	    }
	Record_ID = getRecord_ID();	
	}

	@Override
	protected String doIt() throws Exception {
		
		X_KJS_ProductionPlan KJSPP = new X_KJS_ProductionPlan(getCtx(), Record_ID, get_TrxName());
		
		
		if(KJSPP.getC_OrderLine_ID() > 0 && KJSPP.getC_Order_ID() > 0) {
			IsCorrection = true;
		}
		
		
		
		if(IsCorrection) {
			
			MOrderLine OrdLine = new MOrderLine(getCtx(), KJSPP.getC_OrderLine_ID(), get_TrxName());
			
			BigDecimal CurrentQtyRL = (BigDecimal) OrdLine.get_Value("QtyJob");
			BigDecimal CurrentQtyPP = (BigDecimal) KJSPP.get_Value("Qty");
			
			if(CurrentQtyRL == null) {
				CurrentQtyRL =  Env.ZERO;
			}
			
			if(CurrentQtyPP == null) {
				CurrentQtyPP = Env.ZERO;
			}
			
			
			if(CurrentQtyRL.compareTo(Env.ZERO)>0) {
				OrdLine.set_ValueNoCheck("QtyJob", CurrentQtyRL.subtract(CurrentQtyPP));
				OrdLine.saveEx();
			}
			
			
		}
		
			KJSPP.setC_OrderLine_ID(p_C_OrderLine_ID);
			MOrderLine OrdLine = new MOrderLine(getCtx(), p_C_OrderLine_ID, get_TrxName());
			KJSPP.setC_Order_ID(OrdLine.getC_Order_ID());
	
			KJSPP.set_ValueNoCheck("Qty", p_QtyOrdered);
			BigDecimal multiplicand = BigDecimal.valueOf(105).divide(Env.ONEHUNDRED);
			BigDecimal rs = p_QtyOrdered.multiply(multiplicand);
			KJSPP.setQtyEntered(rs);
			if(KJSPP.save()){
				
				MOrderLine OL = new MOrderLine(getCtx(), p_C_OrderLine_ID, get_TrxName());
				
				BigDecimal CurrentQtyRL = (BigDecimal) OL.get_Value("QtyJob");
				BigDecimal CurrentQtyPP = p_QtyOrdered;
				
				OL.set_ValueNoCheck("QtyJob", CurrentQtyRL.add(CurrentQtyPP));
				OL.saveEx();
			}
			
			
		createPhase(KJSPP.getM_Product_ID(), (int) KJSPP.get_Value("M_Alternate_ID"), KJSPP);
		
		return "";
		
	}
	
public void createPhase(int M_Product_ID, int M_Alternate_ID,X_KJS_ProductionPlan KJSPP) {
		
		
		StringBuilder SQLGetPhase = new StringBuilder();
		SQLGetPhase.append("SELECT ppl.Line, ppl.KJS_Phase_ID, ppl.M_Product_ID, prod.Value");
		SQLGetPhase.append(" FROM KJS_ProductPhaseLine ppl");
		SQLGetPhase.append(" INNER JOIN KJS_ProductPhase pp ON pp.KJS_ProductPhase_ID = ppl.KJS_ProductPhase_ID");
		SQLGetPhase.append(" LEFT JOIN M_Product prod ON prod.M_Product_ID = ppl.M_Product_ID");
		SQLGetPhase.append(" WHERE pp.M_Product_ID = ?");
		SQLGetPhase.append(" AND ppl.M_Alternate_ID = ?");
		
		MProduct prodKJPSPP = new MProduct(getCtx(), KJSPP.getM_Product_ID(), get_TrxName());
		String parentValue = prodKJPSPP.getValue();
		if (parentValue == null)
			parentValue = "";
		X_M_Alternate alternate = new X_M_Alternate(getCtx(), M_Alternate_ID, get_TrxName());
		BigDecimal up = alternate.getKJS_CavityJumlah();
		
	    PreparedStatement pstmt = null;
	    ResultSet rs = null;
	    	try {
	            pstmt = (PreparedStatement)DB.prepareStatement(SQLGetPhase.toString(), get_TrxName());
	            pstmt.setInt(1, M_Product_ID);
	            pstmt.setInt(2, M_Alternate_ID);
	       
	            rs = pstmt.executeQuery();
	            while (rs.next()) {
	            	
	            	int lineNo = rs.getInt(1);
	            	int KJS_Phase_ID = rs.getInt(2);
	            	int phaseProductId = rs.getInt(3);
	            	String phaseProductValue = rs.getString(4);
	            	if (phaseProductValue == null)
	            		phaseProductValue = "";
	            		
	            		if(alternate.get_Value("AlternateType").toString().toUpperCase().equals("OF")) {
	            		
		            		X_KJS_ProductionPlanLine JobPhase = new X_KJS_ProductionPlanLine(getCtx(), 0, get_TrxName());
		            		JobPhase.setKJS_ProductionPlan_ID(Record_ID);
		            		JobPhase.setAD_Org_ID(KJSPP.getAD_Org_ID());
		            		JobPhase.setLine(lineNo);
		            		JobPhase.setKJS_Phase_ID(KJS_Phase_ID);
		            		JobPhase.set_CustomColumn("M_Alternate_ID", M_Alternate_ID);
		            		JobPhase.setM_Product_ID(phaseProductId);
		            		JobPhase.set_CustomColumn("ProductionQty", KJSPP.getQtyEntered());
		            		            		
		            		if(phaseProductId == KJSPP.getM_Product_ID() || phaseProductValue.equals(parentValue+"-BLC")) {
		            			JobPhase.set_CustomColumn("QtySheet",Env.ONE);
			            		JobPhase.setBOMQty( KJSPP.getQtyEntered());
		            		}else {
		            			JobPhase.set_CustomColumn("QtySheet",up);
			            		JobPhase.setBOMQty( KJSPP.getQtyEntered().divide(up,2,RoundingMode.HALF_UP));	            		
		            		}
		            		JobPhase.saveEx();
	            		
	            		}else if(alternate.get_Value("AlternateType").toString().toUpperCase().equals("FL-RCS")) {	            			
	            			X_KJS_ProductionPlanLine JobPhase = new X_KJS_ProductionPlanLine(getCtx(), 0, get_TrxName());
		            		JobPhase.setKJS_ProductionPlan_ID(Record_ID);
		            		JobPhase.setAD_Org_ID(KJSPP.getAD_Org_ID());
		            		JobPhase.setLine(lineNo);
		            		JobPhase.setKJS_Phase_ID(KJS_Phase_ID);
		            		JobPhase.set_CustomColumn("M_Alternate_ID", M_Alternate_ID);
		            		JobPhase.setM_Product_ID(phaseProductId);
		            		JobPhase.set_CustomColumn("ProductionQty", KJSPP.getQtyEntered());
		            		            		
		            		if(phaseProductValue.equals(parentValue+"-RCF")) {
		            			JobPhase.set_CustomColumn("QtySheet",up);
		            			
		            			BigDecimal gearUp = alternate.getKJS_CavityGearFeed();
		            			BigDecimal gearCons = (BigDecimal) alternate.get_Value("GearConstanta");
		            			BigDecimal a = KJSPP.getQtyEntered().divide(up,2,RoundingMode.HALF_UP);
		            			BigDecimal b = gearCons.multiply(gearUp);
			            		JobPhase.setBOMQty( a.multiply(b));
		            		}if(phaseProductValue.equals(parentValue+"-RCS")) {
		            			JobPhase.set_CustomColumn("QtySheet",up);
		            			
		            			BigDecimal gearUp = alternate.getKJS_CavityGearFeed();
		            			BigDecimal gearCons = (BigDecimal) alternate.get_Value("GearConstanta");
		            			BigDecimal a = KJSPP.getQtyEntered().divide(up,2,RoundingMode.HALF_UP);
		            			BigDecimal b = gearCons.multiply(gearUp);
			            		BigDecimal c = a.multiply(b);
			            		BigDecimal d = (BigDecimal) alternate.get_Value("SlittingUp");
		            			JobPhase.setBOMQty( c.multiply(d));		            		
			            		
		            		}else if(phaseProductId == KJSPP.getM_Product_ID() || phaseProductValue.equals(parentValue+"-BLC")) {
		            			JobPhase.set_CustomColumn("QtySheet",Env.ONE);
			            		JobPhase.setBOMQty( KJSPP.getQtyEntered());
		            		}else {
		            			JobPhase.set_CustomColumn("QtySheet",up);
			            		JobPhase.setBOMQty( KJSPP.getQtyEntered().divide(up,2,RoundingMode.HALF_UP));	            		
		            		}
		            		JobPhase.saveEx();
	            			
	            		}else if(alternate.get_Value("AlternateType").toString().toUpperCase().equals("FL-RCF")) {
	            			X_KJS_ProductionPlanLine JobPhase = new X_KJS_ProductionPlanLine(getCtx(), 0, get_TrxName());
		            		JobPhase.setKJS_ProductionPlan_ID(Record_ID);
		            		JobPhase.setAD_Org_ID(KJSPP.getAD_Org_ID());
		            		JobPhase.setLine(lineNo);
		            		JobPhase.setKJS_Phase_ID(KJS_Phase_ID);
		            		JobPhase.set_CustomColumn("M_Alternate_ID", M_Alternate_ID);
		            		JobPhase.setM_Product_ID(phaseProductId);
		            		JobPhase.set_CustomColumn("ProductionQty", KJSPP.getQtyEntered());
		            		            		
		            		if(phaseProductValue.equals(parentValue+"-RCF")) {
		            			JobPhase.set_CustomColumn("QtySheet",up);
		            			
		            			BigDecimal gearUp = alternate.getKJS_CavityGearFeed();
		            			BigDecimal gearCons = (BigDecimal) alternate.get_Value("GearConstanta");
		            			BigDecimal a = KJSPP.getQtyEntered().divide(up,2,RoundingMode.HALF_UP);
		            			BigDecimal b = gearCons.multiply(gearUp);
			            		JobPhase.setBOMQty( a.multiply(b));
		            		}else if(phaseProductId == KJSPP.getM_Product_ID() || phaseProductValue.equals(parentValue+"-BLC")) {
		            			JobPhase.set_CustomColumn("QtySheet",Env.ONE);
			            		JobPhase.setBOMQty( KJSPP.getQtyEntered());
		            		}else {
		            			JobPhase.set_CustomColumn("QtySheet",up);
			            		JobPhase.setBOMQty( KJSPP.getQtyEntered().divide(up,2,RoundingMode.HALF_UP));	            		
		            		}
		            		JobPhase.saveEx();
	            		}else if(alternate.get_Value("AlternateType").toString().toUpperCase().equals("FL-PB")) {	
	            			
	            			X_KJS_ProductionPlanLine JobPhase = new X_KJS_ProductionPlanLine(getCtx(), 0, get_TrxName());
		            		JobPhase.setKJS_ProductionPlan_ID(Record_ID);
		            		JobPhase.setAD_Org_ID(KJSPP.getAD_Org_ID());
		            		JobPhase.setLine(lineNo);
		            		JobPhase.setKJS_Phase_ID(KJS_Phase_ID);
		            		JobPhase.set_CustomColumn("M_Alternate_ID", M_Alternate_ID);
		            		JobPhase.setM_Product_ID(phaseProductId);
		            		JobPhase.set_CustomColumn("ProductionQty", KJSPP.getQtyEntered());
		            		            		
		            		if(phaseProductValue.equals(parentValue+"-RCF")) {
		            			JobPhase.set_CustomColumn("QtySheet",up);
		            			
		            			BigDecimal gearUp = alternate.getKJS_CavityGearFeed();
		            			BigDecimal gearCons = (BigDecimal) alternate.get_Value("GearConstanta");
		            			BigDecimal a = KJSPP.getQtyEntered().divide(up,2,RoundingMode.HALF_UP);
		            			BigDecimal b = gearCons.multiply(gearUp);
			            		JobPhase.setBOMQty( a.multiply(b));
		            		}else if(phaseProductId == KJSPP.getM_Product_ID()) {
		            			JobPhase.set_CustomColumn("QtySheet",Env.ONE);
			            		JobPhase.setBOMQty( KJSPP.getQtyEntered());
		            		}else {
		            			JobPhase.set_CustomColumn("QtySheet",up);
			            		JobPhase.setBOMQty( KJSPP.getQtyEntered().divide(up,2,RoundingMode.HALF_UP));	            		
		            		}
		            		JobPhase.saveEx();
	            			
	            		}else if(alternate.get_Value("AlternateType").toString().toUpperCase().equals("PB")) {	
	            			
	            			X_KJS_ProductionPlanLine JobPhase = new X_KJS_ProductionPlanLine(getCtx(), 0, get_TrxName());
		            		JobPhase.setKJS_ProductionPlan_ID(Record_ID);
		            		JobPhase.setAD_Org_ID(KJSPP.getAD_Org_ID());
		            		JobPhase.setLine(lineNo);
		            		JobPhase.setKJS_Phase_ID(KJS_Phase_ID);
		            		JobPhase.set_CustomColumn("M_Alternate_ID", M_Alternate_ID);
		            		JobPhase.setM_Product_ID(phaseProductId);
		            		JobPhase.set_CustomColumn("ProductionQty", KJSPP.getQtyEntered());
		            		JobPhase.set_CustomColumn("QtySheet",up);
			            	JobPhase.setBOMQty( KJSPP.getQtyEntered().divide(up,2,RoundingMode.HALF_UP));	            		
		            		JobPhase.saveEx();
	            			
	            		}
	            	
	            	
	            }
	        }
	        catch (SQLException err) {
	            this.log.log(Level.SEVERE, SQLGetPhase.toString(), (Throwable)err);
	            rollback();
	        }
	        finally {
	            DB.close(rs, (Statement)pstmt);
	            rs = null;
	            pstmt = null;
	        }
		
		
		
	}

}
