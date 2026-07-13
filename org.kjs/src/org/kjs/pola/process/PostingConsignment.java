package org.kjs.pola.process;

import java.util.logging.Level;

import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MLocator;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MProduction;
import org.compiere.model.MProductionLine;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.compiere.util.Env;

public class PostingConsignment extends SvrProcess
{
    private int M_Production_ID;
    private int SalesRep_ID;
    
    public PostingConsignment() {
        this.M_Production_ID = 0;
        this.SalesRep_ID = 0;
    }
    
    protected void prepare() {
        final ProcessInfoParameter[] para = this.getParameter();
        for (int i = 0; i < para.length; ++i) {
            final String name = para[i].getParameterName();
            if (para[i].getParameter() != null) {
                if (name.equals("SalesRep_ID")) {
                    this.SalesRep_ID = para[i].getParameterAsInt();
                }
                else {
                    this.log.log(Level.SEVERE, "Unknown Parameter: " + name);
                }
            }
        }
        this.M_Production_ID = this.getRecord_ID();
    }
    
    protected String doIt() throws Exception {
        try {
            final MProduction LHP = new MProduction(this.getCtx(), this.M_Production_ID, this.get_TrxName());
            final StringBuilder getDocType = new StringBuilder();
            getDocType.append("SELECT C_DocType_ID");
            getDocType.append(" FROM C_DocType");
            getDocType.append(" WHERE DocBaseType ='POO'");
            getDocType.append(" AND AD_Client_ID = " + Env.getAD_Client_ID(this.getCtx()));
            final int C_DOcType_ID = DB.getSQLValue(this.get_TrxName(), getDocType.toString());
            final MProductionLine[] lines = LHP.getLines();
            int lineNo = 10;
            MProductionLine[] array;
            for (int length = (array = lines).length, i = 0; i < length; ++i) {
                final MProductionLine line = array[i];
                final MProduct product = new MProduct(this.getCtx(), line.getM_Product_ID(), this.get_TrxName());
                if (product.get_ValueAsBoolean("IsConsignment")) {
                    final StringBuilder getBP = new StringBuilder();
                    getBP.append("SELECT C_BPartner_ID ");
                    getBP.append(" FROM C_BPartner_Product ");
                    getBP.append(" WHERE M_Product_ID = " + line.getM_Product_ID());
                    final int C_BPartner_ID = DB.getSQLValue(this.get_TrxName(), getBP.toString());
                    if (C_BPartner_ID <= 0) {
                        return "Product Partner Belum Terdefinisi";
                    }
                    final StringBuilder getBPLoc = new StringBuilder();
                    getBPLoc.append("SELECT C_BPartner_Location_ID ");
                    getBPLoc.append(" FROM C_BPartner_Location ");
                    getBPLoc.append(" WHERE C_BPartner_ID = " + C_BPartner_ID);
                    final int C_BPartner_Location_ID = DB.getSQLValue(this.get_TrxName(), getBPLoc.toString());
                    final MOrder PO = new MOrder(this.getCtx(), 0, this.get_TrxName());
                    PO.setAD_Org_ID(LHP.getAD_Org_ID());
                    PO.setC_DocTypeTarget_ID(C_DOcType_ID);
                    PO.setC_DocType_ID(C_DOcType_ID);
                    PO.setDateOrdered(LHP.getMovementDate());
                    PO.setDatePromised(LHP.getMovementDate());
                    PO.setC_BPartner_ID(C_BPartner_ID);
                    PO.setC_BPartner_Location_ID(C_BPartner_Location_ID);
                    PO.setPaymentRule("P");
                    PO.setDescription("LHP = " + LHP.getDocumentNo());
                    PO.setDeliveryRule("A");
                    PO.setDeliveryViaRule("P");
                    PO.setDocStatus("DR");
                    PO.setDocAction("CO");
                    PO.setSalesRep_ID(this.SalesRep_ID);
                    final StringBuilder getPriceList = new StringBuilder();
                    getPriceList.append("SELECT description::numeric ");
                    getPriceList.append(" FROM AD_Param ");
                    getPriceList.append(" WHERE value = 'AutoPostPO_PriceList'");
                    final Integer M_PriceList_ID = DB.getSQLValue(this.get_TrxName(), getPriceList.toString());
                    final StringBuilder getCurrency = new StringBuilder();
                    getCurrency.append("SELECT description::numeric ");
                    getCurrency.append(" FROM AD_Param ");
                    getCurrency.append(" WHERE value = 'AutoPostPO_Currency'");
                    final Integer C_Currency_ID = DB.getSQLValue(this.get_TrxName(), getCurrency.toString());
                    final StringBuilder getPayterm = new StringBuilder();
                    getPayterm.append("SELECT description::numeric ");
                    getPayterm.append(" FROM AD_Param ");
                    getPayterm.append(" WHERE value = 'AutoPostPO_PaymentTerm'");
                    final Integer C_PaymentTerm_ID = DB.getSQLValue(this.get_TrxName(), getPayterm.toString());
                    PO.setM_PriceList_ID((int)M_PriceList_ID);
                    PO.setC_Currency_ID((int)C_Currency_ID);
                    PO.setC_PaymentTerm_ID((int)C_PaymentTerm_ID);
                    PO.setIsSOTrx(false);
                    final MLocator loc = new MLocator(this.getCtx(), line.getM_Locator_ID(), this.get_TrxName());
                    PO.setM_Warehouse_ID(loc.getM_Warehouse_ID());
                    PO.saveEx();
                    final MOrderLine POLine = new MOrderLine(this.getCtx(), 0, this.get_TrxName());
                    POLine.setAD_Org_ID(PO.getAD_Org_ID());
                    POLine.setC_Order_ID(PO.getC_Order_ID());
                    POLine.setC_BPartner_ID(C_BPartner_ID);
                    POLine.setC_BPartner_Location_ID(C_BPartner_Location_ID);
                    POLine.setDateOrdered(PO.getDateOrdered());
                    POLine.setDatePromised(PO.getDatePromised());
                    POLine.setLine(lineNo);
                    POLine.setM_Product_ID(line.getM_Product_ID());
                    POLine.setQtyEntered(line.getPlannedQty());
                    POLine.setQtyOrdered(line.getPlannedQty());
                    POLine.setC_UOM_ID(product.getC_UOM_ID());
                    final StringBuilder getLineTax = new StringBuilder();
                    getLineTax.append("SELECT description::numeric ");
                    getLineTax.append(" FROM AD_Param ");
                    getLineTax.append(" WHERE value = 'AutoPostPOLine_Tax'");
                    final Integer C_Tax_ID = DB.getSQLValue(this.get_TrxName(), getLineTax.toString());
                    POLine.setC_Tax_ID((int)C_Tax_ID);
                    POLine.saveEx();
                    lineNo += 10;
                    if (PO != null && PO.processIt("CO")) {
                        PO.saveEx();
                        final StringBuilder getMRDocType = new StringBuilder();
                        getMRDocType.append("SELECT description::numeric ");
                        getMRDocType.append(" FROM AD_Param ");
                        getMRDocType.append(" WHERE value = 'AutoPostMR_DocType'");
                        final Integer MRDocType_ID = DB.getSQLValue(this.get_TrxName(), getMRDocType.toString());
                        final MInOut MR = new MInOut(this.getCtx(), 0, this.get_TrxName());
                        MR.setAD_Org_ID(PO.getAD_Org_ID());
                        MR.setC_DocType_ID((int)MRDocType_ID);
                        MR.setMovementDate(LHP.getMovementDate());
                        MR.setDescription(PO.getDescription());
                        MR.setC_Order_ID(PO.getC_Order_ID());
                        MR.setC_BPartner_ID(C_BPartner_ID);
                        MR.setC_BPartner_Location_ID(C_BPartner_Location_ID);
                        MR.setM_Warehouse_ID(loc.getM_Warehouse_ID());
                        MR.setSalesRep_ID(PO.getSalesRep_ID());
                        MR.setMovementType("V+");
                        MR.setIsSOTrx(false);
                        MR.setDocStatus("DR");
                        MR.saveEx();
                        System.out.println(loc.getM_Warehouse_ID());
                        System.out.println(loc.getM_Locator_ID());
                        final MInOutLine MRLine = new MInOutLine(this.getCtx(), 0, this.get_TrxName());
                        MRLine.setAD_Org_ID(MR.getAD_Org_ID());
                        MRLine.setM_InOut_ID(MR.getM_InOut_ID());
                        MRLine.setLine(POLine.getLine());
                        MRLine.setM_Product_ID(POLine.getM_Product_ID());
                        MRLine.setM_Warehouse_ID(loc.getM_Warehouse_ID());
                        MRLine.setM_Locator_ID(loc.getM_Locator_ID());
                        MRLine.setQtyEntered(POLine.getQtyEntered());
                        MRLine.setMovementQty(POLine.getQtyEntered());
                        MRLine.setC_UOM_ID(POLine.getC_UOM_ID());
                        MRLine.setC_OrderLine_ID(POLine.getC_OrderLine_ID());
                        MRLine.saveEx();
                        if (MR != null) {
                            MR.processIt("CO");
                            MR.saveEx();
                        }
                    }
                }
            }
            LHP.set_ValueOfColumnReturningBoolean("posted_cons", (Object)true);
            LHP.saveEx();
        }
        catch (Exception ex) {
            this.rollback();
        }
        return "";
    }
}