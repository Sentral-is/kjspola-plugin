package org.kjs.pola.form;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Vector;
import java.util.logging.Level;

import org.compiere.apps.IStatusBar;
import org.compiere.grid.CreateFrom;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.GridTab;
import org.compiere.util.AdempiereUserError;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.kjs.pola.model.X_KJS_ProductionPlanLine;
import org.kjs.pola.model.X_KJS_ProductionPlanLink;

public class CreateFromMPS extends CreateFrom
{
    protected int AD_Org_ID;
    protected int M_Product_ID;
    protected String BOMType;
    
    public CreateFromMPS(final GridTab gridTab) {
        super(gridTab);
        this.AD_Org_ID = 0;
        this.M_Product_ID = 0;
    }
    
    public Object getWindow() {
        return null;
    }
    
    public boolean dynInit() throws Exception {
        this.M_Product_ID = (int)this.getGridTab().getValue("M_Product_ID");
        this.AD_Org_ID = (int)this.getGridTab().getValue("AD_Org_ID");
        this.BOMType = (String)this.getGridTab().getValue("BOMType");
        return false;
    }
    
    public void info(final IMiniTable miniTable, final IStatusBar statusBar) {
    }
    
    protected ArrayList<KeyNamePair> loadOrder() {
        final ArrayList<KeyNamePair> list = new ArrayList<KeyNamePair>();
        String sqlStmt = "SELECT col.C_Order_ID,co.DocumentNo FROM C_OrderLine col JOIN C_Order co ON col.C_Order_ID=co.C_Order_ID AND co.Docstatus='CO' AND co.IsSOTrx='Y' WHERE col.C_OrderLine_ID NOT IN (SELECT ppl.C_OrderLine_ID FROM KJS_ProductionPlanLink ppl,KJS_ProductionPlan pp WHERE ppl.KJS_ProductionPlan_ID=pp.KJS_ProductionPlan_ID AND pp.DocStatus='CO' AND ppl.C_OrderLine_ID IS NOT NULL) AND col.KJS_LineType='M' AND col.M_Product_ID=?";
        sqlStmt = String.valueOf(sqlStmt) + " ORDER BY co.DocumentNo DESC ";
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = (PreparedStatement)DB.prepareStatement(sqlStmt, (String)null);
            pstmt.setInt(1, this.M_Product_ID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new KeyNamePair(rs.getInt(1), rs.getString(2)));
            }
        }
        catch (SQLException e) {
            this.log.log(Level.SEVERE, sqlStmt.toString(), (Throwable)e);
            return list;
        }
        finally {
            DB.close(rs, (Statement)pstmt);
            rs = null;
            pstmt = null;
        }
        DB.close(rs, (Statement)pstmt);
        rs = null;
        pstmt = null;
        return list;
    }
    
    protected ArrayList<KeyNamePair> loadRequisition() {
        final ArrayList<KeyNamePair> list = new ArrayList<KeyNamePair>();
        String sqlStmt = "SELECT DISTINCT rl.M_Requisition_ID,r.DocumentNo FROM M_RequisitionLine rl JOIN M_Requisition r ON rl.M_Requisition_ID=r.M_Requisition_ID AND r.DocStatus='CO' AND r.C_DocType_ID IN (1000094,1000095,1000096) LEFT JOIN KJS_ProductionPlanLink ppl ON ppl.M_RequisitionLine_ID=rl.M_RequisitionLine_ID AND ppl.KJS_ProductionPlan_ID IN (SELECT KJS_ProductionPlan_ID FROM KJS_ProductionPlan WHERE DocStatus='CO') LEFT JOIN KJS_ProductionPlan pp ON ppl.KJS_ProductionPlan_ID=pp.KJS_ProductionPlan_ID AND pp.DocStatus='CO' WHERE (rl.Qty - COALESCE(pp.QtyEntered,0)) > 0 AND rl.M_Product_ID=?";
        sqlStmt = String.valueOf(sqlStmt) + " ORDER BY r.DocumentNo DESC ";
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = (PreparedStatement)DB.prepareStatement(sqlStmt, (String)null);
            pstmt.setInt(1, this.M_Product_ID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new KeyNamePair(rs.getInt(1), rs.getString(2)));
            }
        }
        catch (SQLException e) {
            this.log.log(Level.SEVERE, sqlStmt.toString(), (Throwable)e);
            return list;
        }
        finally {
            DB.close(rs, (Statement)pstmt);
            rs = null;
            pstmt = null;
        }
        DB.close(rs, (Statement)pstmt);
        rs = null;
        pstmt = null;
        return list;
    }
    
    protected Vector<Vector<Object>> getProductPhaseData(final int C_Order_ID, final int M_Requisition_ID) {
        final Vector<Vector<Object>> data = new Vector<Vector<Object>>();
        final StringBuilder sql = new StringBuilder("SELECT col.C_OrderLine_ID,co.DocumentNo,prod.M_Product_ID,prod.Name,col.QtyEntered,bp.Name,co.DateOrdered,dt.C_DocType_ID,dt.Name FROM C_OrderLine col JOIN C_Order co ON col.C_Order_ID=co.C_Order_ID AND co.Docstatus='CO' AND co.IsSOTrx='Y' JOIN M_Product prod ON col.M_Product_ID=prod.M_Product_ID JOIN C_BPartner bp ON co.C_BPartner_ID=bp.C_BPartner_ID JOIN C_DocType dt ON co.C_DocType_ID=dt.C_DocType_ID WHERE col.C_OrderLine_ID NOT IN (SELECT ppl.C_OrderLine_ID FROM KJS_ProductionPlanLink ppl,KJS_ProductionPlan pp WHERE ppl.KJS_ProductionPlan_ID=pp.KJS_ProductionPlan_ID AND pp.DocStatus='CO' AND ppl.C_OrderLine_ID IS NOT NULL) AND col.KJS_LineType='M' AND prod.M_Product_ID=? ");
        if (C_Order_ID > 0) {
            sql.append("AND co.C_Order_ID=? ");
        }
        else {
            sql.append("UNION ");
            sql.append("SELECT rl.M_RequisitionLine_ID,r.DocumentNo,prod.M_Product_ID,prod.Name, ");
            sql.append("rl.Qty - COALESCE(pp.QtyEntered,0),bp.Name,r.DateDoc,dt.C_DocType_ID,dt.Name ");
            sql.append("FROM M_RequisitionLine rl ");
            sql.append("JOIN M_Requisition r ON r.M_Requisition_ID=rl.M_Requisition_ID AND r.DocStatus='CO' AND r.C_DocType_ID IN (1000094,1000095,1000096) ");
            sql.append("JOIN M_Product prod ON rl.M_Product_ID=prod.M_Product_ID ");
            sql.append("JOIN C_DocType dt ON r.C_DocType_ID=dt.C_DocType_ID  ");
            sql.append("LEFT JOIN C_BPartner bp ON rl.C_BPartner_ID=bp.C_BPartner_ID ");
            sql.append("LEFT JOIN KJS_ProductionPlanLink ppl ON ppl.M_RequisitionLine_ID=rl.M_RequisitionLine_ID ");
            sql.append("AND ppl.KJS_ProductionPlan_ID IN (SELECT KJS_ProductionPlan_ID FROM KJS_ProductionPlan WHERE DocStatus='CO') ");
            sql.append("LEFT JOIN KJS_ProductionPlan pp ON ppl.KJS_ProductionPlan_ID=pp.KJS_ProductionPlan_ID AND pp.DocStatus='CO' ");
            sql.append("WHERE (rl.Qty - COALESCE(pp.QtyEntered,0)) > 0 AND prod.M_Product_ID=? ");
            if (M_Requisition_ID > 0) {
                sql.append("AND r.M_Requisition_ID=? ");
            }
        }
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = (PreparedStatement)DB.prepareStatement(sql.toString(), (String)null);
            int index = 1;
            pstmt.setInt(index++, this.M_Product_ID);
            if (C_Order_ID > 0) {
                pstmt.setInt(index++, C_Order_ID);
            }
            if (M_Requisition_ID > 0) {
                pstmt.setInt(index++, M_Requisition_ID);
            }
            pstmt.setInt(index++, this.M_Product_ID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                final Vector<Object> line = new Vector<Object>();
                line.add(new Boolean(false));
                KeyNamePair pp = new KeyNamePair(rs.getInt(1), rs.getString(2));
                line.add(pp);
                pp = new KeyNamePair(rs.getInt(3), rs.getString(4));
                line.add(pp);
                line.add(rs.getBigDecimal(5));
                line.add(rs.getString(6));
                line.add(rs.getDate(7));
                pp = new KeyNamePair(rs.getInt(8), rs.getString(9));
                line.add(pp);
                data.add(line);
            }
        }
        catch (SQLException e) {
            this.log.log(Level.SEVERE, sql.toString(), (Throwable)e);
            return data;
        }
        finally {
            DB.close(rs, (Statement)pstmt);
            rs = null;
            pstmt = null;
        }
        DB.close(rs, (Statement)pstmt);
        rs = null;
        pstmt = null;
        return data;
    }
    
    public boolean save(final IMiniTable miniTable, final String trxName) {
        final int KJS_ProductionPlan_ID = (int)this.getGridTab().getValue("KJS_ProductionPlan_ID");
        boolean anySelected = false;
        for (int i = 0; i < miniTable.getRowCount(); ++i) {
            if ((boolean) miniTable.getValueAt(i, 0)) {
                anySelected = true;
                break;
            }
        }
        if (anySelected) {
            final int phaseCount = DB.getSQLValueEx(trxName,
                    "SELECT COUNT(*) FROM KJS_ProductPhaseLine ppl INNER JOIN KJS_ProductPhase pp ON ppl.KJS_ProductPhase_ID=pp.KJS_ProductPhase_ID WHERE pp.M_Product_ID=? AND ppl.BOMType=?",
                    Integer.valueOf(this.M_Product_ID), this.BOMType);
            if (phaseCount <= 0) {
                throw new AdempiereUserError("No Product Phase");
            }
        }
        for (int i = 0; i < miniTable.getRowCount(); ++i) {
            if ((boolean) miniTable.getValueAt(i, 0)) {
                final X_KJS_ProductionPlanLink ppl = new X_KJS_ProductionPlanLink(Env.getCtx(), 0, trxName);
                KeyNamePair pp = (KeyNamePair)miniTable.getValueAt(i, 1);
                final int C_OrderLine_ID = pp.getKey();
                pp = (KeyNamePair)miniTable.getValueAt(i, 2);
                this.M_Product_ID = pp.getKey();
                final BigDecimal Qty = (BigDecimal)miniTable.getValueAt(i, 3);
                pp = (KeyNamePair)miniTable.getValueAt(i, 6);
                final int C_DocType_ID = pp.getKey();
                ppl.setAD_Org_ID(this.AD_Org_ID);
                ppl.setKJS_ProductionPlan_ID(KJS_ProductionPlan_ID);
                if (C_DocType_ID == 1000094 || C_DocType_ID == 1000095 || C_DocType_ID == 1000096) {
                    ppl.setM_RequisitionLine_ID(C_OrderLine_ID);
                }
                else {
                    ppl.setC_OrderLine_ID(C_OrderLine_ID);
                }
                ppl.setM_Product_ID(this.M_Product_ID);
                ppl.setQty(Qty);
                ppl.saveEx(trxName);
            }
        }
        final String sql = "SELECT ppl.Line, p.KJS_Phase_ID, prod.M_Product_ID, p.M_Locator_ID FROM KJS_ProductPhase pp, KJS_ProductPhaseLine ppl, KJS_Phase p, M_Product prod WHERE pp.KJS_ProductPhase_ID=ppl.KJS_ProductPhase_ID AND ppl.KJS_Phase_ID=p.KJS_Phase_ID AND ppl.M_Product_ID=prod.M_Product_ID AND pp.M_Product_ID=? AND ppl.BOMType=?";
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = (PreparedStatement)DB.prepareStatement(sql.toString(), (String)null);
            pstmt.setInt(1, this.M_Product_ID);
            pstmt.setString(2, this.BOMType);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                final X_KJS_ProductionPlanLine ppline = new X_KJS_ProductionPlanLine(Env.getCtx(), 0, trxName);
                final int Line = rs.getInt(1);
                final int KJS_Phase_ID = rs.getInt(2);
                final int M_Product_WIP = rs.getInt(3);
                final int M_Locator_ID = rs.getInt(4);
                ppline.setAD_Org_ID(this.AD_Org_ID);
                ppline.setLine(Line);
                ppline.setKJS_Phase_ID(KJS_Phase_ID);
                ppline.setM_Product_ID(M_Product_WIP);
                ppline.setKJS_ProductionPlan_ID(KJS_ProductionPlan_ID);
                ppline.setM_Locator_ID(M_Locator_ID);
                ppline.saveEx(trxName);
            }
        }
        catch (SQLException e2) {
            this.log.log(Level.SEVERE, sql.toString(), (Throwable)e2);
            return true;
        }
        finally {
            DB.close(rs, (Statement)pstmt);
            rs = null;
            pstmt = null;
        }
        DB.close(rs, (Statement)pstmt);
        rs = null;
        pstmt = null;
        return true;
    }
    
    protected Vector<String> getOISColumnNames() {
        final Vector<String> columnNames = new Vector<String>(10);
        columnNames.add(Msg.getMsg(Env.getCtx(), "Select"));
        columnNames.add(Msg.translate(Env.getCtx(), "DocumentNo"));
        columnNames.add(Msg.translate(Env.getCtx(), "Product"));
        columnNames.add(Msg.translate(Env.getCtx(), "Qty"));
        columnNames.add(Msg.translate(Env.getCtx(), "C_BPartner_ID"));
        columnNames.add(Msg.translate(Env.getCtx(), "DateOrdered"));
        columnNames.add(Msg.translate(Env.getCtx(), "C_DocType_ID"));
        return columnNames;
    }
    
    protected void configureMiniTable(final IMiniTable miniTable) {
        miniTable.setColumnClass(0, (Class)Boolean.class, false);
        miniTable.setColumnClass(1, (Class)String.class, true);
        miniTable.setColumnClass(2, (Class)String.class, true);
        miniTable.setColumnClass(3, (Class)BigDecimal.class, true);
        miniTable.setColumnClass(4, (Class)String.class, true);
        miniTable.setColumnClass(5, (Class)Date.class, true);
        miniTable.setColumnClass(6, (Class)String.class, true);
        miniTable.autoSize();
    }
}
