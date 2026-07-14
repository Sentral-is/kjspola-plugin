package org.kjs.pola.process;

import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Statement;
import org.compiere.model.MProduct;
import org.compiere.model.Query;
import org.eevolution.model.MPPProductBOM;
import org.eevolution.model.MPPProductBOMLine;
import org.kjs.pola.model.X_I_Product_BOM;
import org.compiere.model.PO;
import org.compiere.model.ModelValidationEngine;
import org.compiere.util.DB;
import org.compiere.process.ProcessInfoParameter;
import java.util.logging.Level;
import java.math.BigDecimal;
import org.adempiere.process.ImportProcess;
import org.compiere.process.SvrProcess;

public class ImportBOM extends SvrProcess implements ImportProcess
{
    private int m_AD_Client_ID;
    
    public ImportBOM() {
        this.m_AD_Client_ID = 0;
    }
    
    protected void prepare() {
        final ProcessInfoParameter[] para = this.getParameter();
        for (int i = 0; i < para.length; ++i) {
            final String name = para[i].getParameterName();
            if (name.equals("AD_Client_ID")) {
                this.m_AD_Client_ID = ((BigDecimal)para[i].getParameter()).intValue();
            }
            else {
                this.log.log(Level.SEVERE, "Unknown Parameter: " + name);
            }
        }
    }
    
    protected String doIt() throws Exception {
        StringBuilder sql = null;
        int no = 0;
        final String clientCheck = this.getWhereClause();
        sql = new StringBuilder("UPDATE I_Product_BOM ").append("SET AD_Client_ID = COALESCE (AD_Client_ID, ").append(this.m_AD_Client_ID).append("),").append(" AD_Org_ID = COALESCE (AD_Org_ID, 0),").append(" IsActive = COALESCE (IsActive, 'Y'),").append(" Created = COALESCE (Created, SysDate),").append(" CreatedBy = COALESCE (CreatedBy, 0),").append(" Updated = COALESCE (Updated, SysDate),").append(" UpdatedBy = COALESCE (UpdatedBy, 0),").append(" I_ErrorMsg = ' ',").append(" I_IsImported = 'N' ").append("WHERE I_IsImported<>'Y' OR I_IsImported IS NULL");
        no = DB.executeUpdate(sql.toString(), this.get_TrxName());
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info("Reset=" + no);
        }
        ModelValidationEngine.get().fireImportValidate((ImportProcess)this, (PO)null, (PO)null, 10);
        sql = new StringBuilder("UPDATE I_Product_BOM i ").append("SET M_Product_ID=(SELECT M_Product_ID FROM M_Product p").append(" WHERE i.Value=p.Value AND i.AD_Client_ID=p.AD_Client_ID) ").append("WHERE M_Product_ID IS NULL").append(" AND I_IsImported='N'").append(clientCheck);
        no = DB.executeUpdate(sql.toString(), this.get_TrxName());
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info("Product Existing Value=" + no);
        }
        sql = new StringBuilder("UPDATE I_Product_BOM i ").append("SET M_ProductBOM_ID=(SELECT M_Product_ID FROM M_Product p").append(" WHERE i.BOMValue=p.Value AND i.AD_Client_ID=p.AD_Client_ID) ").append("WHERE M_ProductBOM_ID IS NULL").append(" AND I_IsImported='N'").append(clientCheck);
        no = DB.executeUpdate(sql.toString(), this.get_TrxName());
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info("Product Existing Value=" + no);
        }
        this.commitEx();
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        StringBuilder sqlimport = null;
        sqlimport = new StringBuilder("SELECT * FROM I_Product_BOM WHERE I_IsImported='N'").append(clientCheck);
        PreparedStatement pstmt_setImported = null;
        try {
            pstmt = (PreparedStatement)DB.prepareStatement(sqlimport.toString(), this.get_TrxName());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                final X_I_Product_BOM imp = new X_I_Product_BOM(this.getCtx(), rs, this.get_TrxName());
                final int I_Product_BOM_ID = imp.getI_Product_BOM_ID();
                final int M_Product_ID = imp.getM_Product_ID();
                final MProduct prod = new MProduct(this.getCtx(), M_Product_ID, this.get_TrxName());
                MPPProductBOM header = new Query(this.getCtx(), MPPProductBOM.Table_Name, "M_Product_ID=?", this.get_TrxName())
                        .setParameters(M_Product_ID)
                        .setClient_ID()
                        .first();
                if (header == null) {
                    header = new MPPProductBOM(this.getCtx(), 0, this.get_TrxName());
                    header.setAD_Org_ID(imp.getAD_Org_ID());
                    header.setM_Product_ID(M_Product_ID);
                    header.setValue(prod.getValue());
                    header.setName(prod.getName());
                    header.saveEx(this.get_TrxName());
                }
                final MPPProductBOMLine bom = new MPPProductBOMLine(header);
                bom.setM_Product_ID(imp.getM_ProductBOM_ID());
                bom.setLine(imp.getLine());
                bom.setDescription(imp.getDescription());
                bom.setComponentType(imp.getBOMType());
                bom.setQtyBOM(imp.getBOMQty());
                bom.setAD_Org_ID(imp.getAD_Org_ID());
                bom.set_ValueNoCheck("CreatedBy", (Object)imp.getCreatedBy());
                bom.set_ValueNoCheck("UpdatedBy", (Object)imp.getUpdatedBy());
                prod.setIsBOM(true);
                prod.saveEx(this.get_TrxName());
                bom.saveEx(this.get_TrxName());
                pstmt_setImported = (PreparedStatement)DB.prepareStatement("UPDATE I_Product_BOM SET I_IsImported='Y', M_Product_ID=?, Updated=SysDate, Processed='Y' WHERE I_Product_BOM_ID=?", this.get_TrxName());
                pstmt_setImported.setInt(1, M_Product_ID);
                pstmt_setImported.setInt(2, I_Product_BOM_ID);
                no = pstmt_setImported.executeUpdate();
                this.commitEx();
            }
        }
        finally {
            DB.close(rs, (Statement)pstmt);
            rs = null;
            pstmt = null;
            DB.close((Statement)pstmt_setImported);
            pstmt_setImported = null;
        }
        DB.close(rs, (Statement)pstmt);
        rs = null;
        pstmt = null;
        DB.close((Statement)pstmt_setImported);
        pstmt_setImported = null;
        sql = new StringBuilder("UPDATE I_Product_BOM ").append("SET I_IsImported='N', Updated=SysDate ").append("WHERE I_IsImported<>'Y'").append(clientCheck);
        no = DB.executeUpdate(sql.toString(), this.get_TrxName());
        this.addLog(0, (Timestamp)null, new BigDecimal(no), "@Errors@");
        return "";
    }
    
    public String getImportTableName() {
        return "I_Product_BOM";
    }
    
    public String getWhereClause() {
        final StringBuilder msgreturn = new StringBuilder(" AND AD_Client_ID=").append(this.m_AD_Client_ID);
        return msgreturn.toString();
    }
}