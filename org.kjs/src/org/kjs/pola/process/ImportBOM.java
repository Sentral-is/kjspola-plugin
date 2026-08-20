package org.kjs.pola.process;

import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Statement;
import org.adempiere.exceptions.AdempiereException;
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

                // Validate the row first and skip + flag (I_ErrorMsg) anything that can't resolve,
                // rather than saveEx-throwing and aborting the whole import. Good rows still import;
                // bad rows are reported and can be re-run after the data is fixed.
                final int M_Product_ID = imp.getM_Product_ID();
                if (M_Product_ID <= 0) {
                    this.markError(I_Product_BOM_ID, "Product not found: " + imp.get_ValueAsString("Value"));
                    continue;
                }
                final int M_ProductBOM_ID = imp.getM_ProductBOM_ID();
                if (M_ProductBOM_ID <= 0) {
                    this.markError(I_Product_BOM_ID, "BOM component not found: " + imp.get_ValueAsString("BOMValue"));
                    continue;
                }
                // Resolve the alternate by NAME (BOMType) for portability across databases; -1 means it
                // was specified on the row but does not exist on this DB (flag it, do not import).
                final int M_Alternate_ID = this.resolveAlternate(imp);
                if (M_Alternate_ID < 0) {
                    this.markError(I_Product_BOM_ID, "Alternate not found: " + imp.get_ValueAsString("BOMType"));
                    continue;
                }

                try {
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
                    bom.setM_Product_ID(M_ProductBOM_ID);
                    bom.setLine(imp.getLine());
                    bom.setDescription(imp.getDescription());
                    // I_Product_BOM.BOMType holds the client's alternate name, not a native
                    // ComponentType, so set a valid ComponentType and route the alternate to the
                    // custom column below.
                    bom.setComponentType(MPPProductBOMLine.COMPONENTTYPE_Component);
                    bom.setQtyBOM(imp.getBOMQty());
                    bom.setAD_Org_ID(imp.getAD_Org_ID());
                    bom.set_ValueNoCheck("CreatedBy", (Object)imp.getCreatedBy());
                    bom.set_ValueNoCheck("UpdatedBy", (Object)imp.getUpdatedBy());
                    if (M_Alternate_ID > 0 && !bom.set_ValueOfColumnReturningBoolean("M_Alternate_ID", (Object)M_Alternate_ID)) {
                        throw new AdempiereException("PP_Product_BOMLine.M_Alternate_ID not in dictionary - apply the 2Pack first");
                    }
                    prod.setIsBOM(true);
                    prod.saveEx(this.get_TrxName());
                    bom.saveEx(this.get_TrxName());
                    DB.executeUpdate("UPDATE I_Product_BOM SET I_IsImported='Y', I_ErrorMsg=NULL, M_Product_ID=?, Updated=SysDate, Processed='Y' WHERE I_Product_BOM_ID=?",
                            new Object[] { M_Product_ID, I_Product_BOM_ID }, false, this.get_TrxName());
                    this.commitEx();
                    ++no;
                } catch (Exception e) {
                    this.rollback();
                    this.markError(I_Product_BOM_ID, e.getMessage());
                }
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
    
    /**
     * Resolve the alternate for an import row. Prefer the name (BOMType) so the import is portable
     * across databases where the numeric ids differ; fall back to a supplied M_Alternate_ID.
     * @return >0 resolved M_Alternate_ID; 0 = no alternate on the row; -1 = specified but not found.
     */
    private int resolveAlternate(final X_I_Product_BOM imp) {
        final String name = imp.get_ValueAsString("BOMType");
        if (name != null && name.trim().length() > 0) {
            final int id = DB.getSQLValue(this.get_TrxName(),
                    "SELECT M_Alternate_ID FROM M_Alternate WHERE Name=? AND AD_Client_ID=?",
                    name.trim(), imp.getAD_Client_ID());
            return (id > 0) ? id : -1;
        }
        final int id = imp.get_ValueAsInt("M_Alternate_ID");
        if (id > 0) {
            final int found = DB.getSQLValue(this.get_TrxName(),
                    "SELECT M_Alternate_ID FROM M_Alternate WHERE M_Alternate_ID=?", id);
            return (found > 0) ? id : -1;
        }
        return 0;
    }

    /** Flag an import row with an error message and leave it un-imported, without aborting the run. */
    private void markError(final int I_Product_BOM_ID, final String msg) throws SQLException {
        DB.executeUpdate("UPDATE I_Product_BOM SET I_ErrorMsg=?, Updated=SysDate WHERE I_Product_BOM_ID=?",
                new Object[] { (msg == null ? "Import error" : msg), I_Product_BOM_ID }, false, this.get_TrxName());
        this.commitEx();
    }

    public String getImportTableName() {
        return "I_Product_BOM";
    }
    
    public String getWhereClause() {
        final StringBuilder msgreturn = new StringBuilder(" AND AD_Client_ID=").append(this.m_AD_Client_ID);
        return msgreturn.toString();
    }
}