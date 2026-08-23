package org.kjs.pola.model;

import java.io.File;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.exceptions.PeriodClosedException;
import org.compiere.model.MAttributeSetInstance;
import org.compiere.model.MClient;
import org.compiere.model.MLocator;
import org.compiere.model.MOrderLine;
import org.compiere.model.MPeriod;
import org.compiere.model.MProduct;
import org.compiere.model.MProductCategory;
import org.compiere.model.MProduction;
import org.compiere.model.MProductionLine;
import org.compiere.model.MProductionPlan;
import org.compiere.model.MProject;
import org.compiere.model.MProjectLine;
import org.compiere.model.MStorageOnHand;
import org.compiere.model.MSysConfig;
import org.compiere.model.MWarehouse;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
import org.compiere.process.DocumentEngine;
import org.compiere.util.AdempiereUserError;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Util;

public class MProductionExt extends MProduction
{
    private static final long serialVersionUID = 5978716736110436960L;
    private static CLogger m_log;
    private int lineno;
    private int count;
    private String m_processMsg;
    private boolean m_justPrepared;
    
    static {
        MProductionExt.m_log = CLogger.getCLogger((Class)MProduction.class);
    }
    
    public MProductionExt(final Properties ctx, final int M_Production_ID, final String trxName) {
        super(ctx, M_Production_ID, trxName);
        this.m_processMsg = null;
        this.m_justPrepared = false;
        if (M_Production_ID == 0) {
            this.setDocStatus("DR");
            this.setDocAction("PR");
        }
    }
    
    public MProductionExt(final Properties ctx, final ResultSet rs, final String trxName) {
        super(ctx, rs, trxName);
        this.m_processMsg = null;
        this.m_justPrepared = false;
    }
    
    public MProductionExt(final MOrderLine line) {
        super(line.getCtx(), 0, line.get_TrxName());
        this.m_processMsg = null;
        this.m_justPrepared = false;
        this.setAD_Client_ID(line.getAD_Client_ID());
        this.setAD_Org_ID(line.getAD_Org_ID());
        this.setMovementDate(line.getDatePromised());
    }
    
    public MProductionExt(final MProjectLine line) {
        super(line.getCtx(), 0, line.get_TrxName());
        this.m_processMsg = null;
        this.m_justPrepared = false;
        final MProject project = new MProject(line.getCtx(), line.getC_Project_ID(), line.get_TrxName());
        final MWarehouse wh = new MWarehouse(line.getCtx(), project.getM_Warehouse_ID(), line.get_TrxName());
        MLocator M_Locator = null;
        int M_Locator_ID = 0;
        if (wh != null) {
            M_Locator = wh.getDefaultLocator();
            M_Locator_ID = M_Locator.getM_Locator_ID();
        }
        this.setAD_Client_ID(line.getAD_Client_ID());
        this.setAD_Org_ID(line.getAD_Org_ID());
        this.setM_Product_ID(line.getM_Product_ID());
        this.setProductionQty(line.getPlannedQty());
        this.setM_Locator_ID(M_Locator_ID);
        this.setDescription(String.valueOf(project.getValue()) + "_" + project.getName() + " Line: " + line.getLine() + " (project)");
        this.setC_Project_ID(line.getC_Project_ID());
        this.setC_BPartner_ID(project.getC_BPartner_ID());
        this.setC_Campaign_ID(project.getC_Campaign_ID());
        this.setAD_OrgTrx_ID(project.getAD_OrgTrx_ID());
        this.setC_Activity_ID(project.getC_Activity_ID());
        this.setC_ProjectPhase_ID(line.getC_ProjectPhase_ID());
        this.setC_ProjectTask_ID(line.getC_ProjectTask_ID());
        this.setMovementDate(Env.getContextAsDate(this.p_ctx, "#Date"));
    }
    
    public String completeIt() {
        if (!this.m_justPrepared) {
            final String status = this.prepareIt();
            this.m_justPrepared = false;
            if (!"IP".equals(status)) {
                return status;
            }
        }
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 7);
        if (this.m_processMsg != null) {
            return "IN";
        }
        final StringBuilder errors = new StringBuilder();
        int processed = 0;
        if (!this.isHaveEndProduct(this.getLines())) {
            this.m_processMsg = "Production does not contain End Product";
            return "IN";
        }
        if (!this.isUseProductionPlan()) {
            final MProductionLine[] lines = this.getLines();
            errors.append(this.processLines(lines));
            if (errors.length() > 0) {
                this.m_processMsg = errors.toString();
                return "IN";
            }
            processed += lines.length;
        }
        else {
            final Query planQuery = new Query(Env.getCtx(), "M_ProductionPlan", "M_ProductionPlan.M_Production_ID=?", this.get_TrxName());
            final List<MProductionPlan> plans = planQuery.setParameters(new Object[] { this.getM_Production_ID() }).list();
            for (final MProductionPlan plan : plans) {
                final MProductionLine[] lines2 = plan.getLines();
                if (lines2.length > 0) {
                    errors.append(this.processLines(lines2));
                    if (errors.length() > 0) {
                        this.m_processMsg = errors.toString();
                        return "IN";
                    }
                    processed += lines2.length;
                }
                plan.setProcessed(true);
                plan.saveEx();
            }
        }
        final String valid = ModelValidationEngine.get().fireDocValidate((PO)this, 9);
        if (valid != null) {
            this.m_processMsg = valid;
            return "IN";
        }
        final MProductionLine[] lines3 = this.getLines();
        for (int lineIndex = 0; lineIndex < lines3.length; ++lineIndex) {
            final MProductionLine pLine = lines3[lineIndex];
            if (!pLine.isEndProduct()) {
                final String sql = "SELECT COALESCE(SUM(QtyOnHand),0) FROM M_Storage WHERE M_Product_ID=? AND M_Locator_ID=?";
                PreparedStatement pstmt = null;
                ResultSet rs = null;
                try {
                    pstmt = (PreparedStatement)DB.prepareStatement(sql.toString(), (String)null);
                    pstmt.setInt(1, pLine.getM_Product_ID());
                    pstmt.setInt(2, pLine.getM_Locator_ID());
                    rs = pstmt.executeQuery();
                    while (rs.next()) {
                        final BigDecimal QtyOnHand = rs.getBigDecimal(1);
                        final BigDecimal QtyUsed = pLine.getQtyUsed();
                        if (QtyUsed.compareTo(QtyOnHand) > 0) {
                            this.m_processMsg = "Inventory not enough";
                            return "IN";
                        }
                    }
                }
                catch (SQLException e) {
                    this.log.log(Level.SEVERE, sql.toString(), (Throwable)e);
                    continue;
                }
                finally {
                    DB.close(rs, (Statement)pstmt);
                    rs = null;
                    pstmt = null;
                }
                DB.close(rs, (Statement)pstmt);
                rs = null;
                pstmt = null;
            }
        }
        this.setProcessed(true);
        this.setDocAction("CL");
        return "CO";
    }
    
    private boolean isHaveEndProduct(final MProductionLine[] lines) {
        for (final MProductionLine line : lines) {
            if (line.isEndProduct()) {
                return true;
            }
        }
        return false;
    }
    
    protected Object processLines(final MProductionLine[] lines) {
        final StringBuilder errors = new StringBuilder();
        for (int i = 0; i < lines.length; ++i) {
            final String error = lines[i].createTransactions(this.getMovementDate(), false);
            if (!Util.isEmpty(error)) {
                errors.append(error);
            }
            else {
                lines[i].setProcessed(true);
                lines[i].saveEx(this.get_TrxName());
            }
        }
        return errors.toString();
    }
    
    public MProductionLine[] getLines() {
        final ArrayList<MProductionLine> list = new ArrayList<MProductionLine>();
        final String sql = "SELECT pl.M_ProductionLine_ID FROM M_ProductionLine pl WHERE pl.M_Production_ID = ?";
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = (PreparedStatement)DB.prepareStatement(sql, this.get_TrxName());
            pstmt.setInt(1, this.get_ID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new MProductionLine(this.getCtx(), rs.getInt(1), this.get_TrxName()));
            }
        }
        catch (SQLException ex) {
            throw new AdempiereException("Unable to load production lines", (Throwable)ex);
        }
        finally {
            DB.close(rs, (Statement)pstmt);
            rs = null;
            pstmt = null;
        }
        DB.close(rs, (Statement)pstmt);
        rs = null;
        pstmt = null;
        final MProductionLine[] retValue = new MProductionLine[list.size()];
        list.toArray(retValue);
        return retValue;
    }
    
    public void deleteLines(final String trxName) {
        MProductionLine[] lines;
        for (int length = (lines = this.getLines()).length, i = 0; i < length; ++i) {
            final MProductionLine line = lines[i];
            line.deleteEx(true);
        }
    }
    
    public int createLines(final boolean mustBeStocked) {
        this.lineno = 100;
        this.count = 0;
        final MProduct finishedProduct = new MProduct(this.getCtx(), this.getM_Product_ID(), this.get_TrxName());
        final MProductionLine line = new MProductionLine((MProduction)this);
        line.setLine(this.lineno);
        line.setM_Product_ID(finishedProduct.get_ID());
        line.setM_Locator_ID(this.getM_Locator_ID());
        line.setMovementQty(this.getProductionQty());
        line.setPlannedQty(this.getProductionQty());
        line.saveEx();
        ++this.count;
        this.createLines(mustBeStocked, finishedProduct, this.getProductionQty());
        return this.count;
    }
    
    protected int createLines(final boolean mustBeStocked, final MProduct finishedProduct, final BigDecimal requiredQty) {
        int defaultLocator = 0;
        final MLocator finishedLocator = MLocator.get(this.getCtx(), this.getM_Locator_ID());
        final int M_Warehouse_ID = finishedLocator.getM_Warehouse_ID();
        final int asi = 0;
        final String sql = "SELECT M_ProductBom_ID, BOMQty FROM M_Product_BOM WHERE M_Product_ID=" + finishedProduct.getM_Product_ID() + " ORDER BY Line";
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = (PreparedStatement)DB.prepareStatement(sql, this.get_TrxName());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                this.lineno += 10;
                final int BOMProduct_ID = rs.getInt(1);
                final BigDecimal BOMQty = rs.getBigDecimal(2);
                BigDecimal BOMMovementQty = BOMQty.multiply(requiredQty);
                final MProduct bomproduct = new MProduct(Env.getCtx(), BOMProduct_ID, this.get_TrxName());
                if (bomproduct.isBOM() && bomproduct.isPhantom()) {
                    this.createLines(mustBeStocked, bomproduct, BOMMovementQty);
                }
                else {
                    defaultLocator = bomproduct.getM_Locator_ID();
                    if (defaultLocator == 0) {
                        defaultLocator = this.getM_Locator_ID();
                    }
                    if (!bomproduct.isStocked()) {
                        MProductionLine BOMLine = null;
                        BOMLine = new MProductionLine((MProduction)this);
                        BOMLine.setLine(this.lineno);
                        BOMLine.setM_Product_ID(BOMProduct_ID);
                        BOMLine.setM_Locator_ID(defaultLocator);
                        BOMLine.setQtyUsed(BOMMovementQty);
                        BOMLine.setPlannedQty(BOMMovementQty);
                        BOMLine.saveEx(this.get_TrxName());
                        this.lineno += 10;
                        ++this.count;
                    }
                    else if (BOMMovementQty.signum() == 0) {
                        MProductionLine BOMLine = null;
                        BOMLine = new MProductionLine((MProduction)this);
                        BOMLine.setLine(this.lineno);
                        BOMLine.setM_Product_ID(BOMProduct_ID);
                        BOMLine.setM_Locator_ID(defaultLocator);
                        BOMLine.setQtyUsed(BOMMovementQty);
                        BOMLine.setPlannedQty(BOMMovementQty);
                        BOMLine.saveEx(this.get_TrxName());
                        this.lineno += 10;
                        ++this.count;
                    }
                    else {
                        MStorageOnHand[] storages = null;
                        final MProduct usedProduct = MProduct.get(this.getCtx(), BOMProduct_ID);
                        defaultLocator = usedProduct.getM_Locator_ID();
                        if (defaultLocator == 0) {
                            defaultLocator = this.getM_Locator_ID();
                        }
                        if (usedProduct == null || usedProduct.get_ID() == 0) {
                            return 0;
                        }
                        final MClient client = MClient.get(this.getCtx());
                        final MProductCategory pc = MProductCategory.get(this.getCtx(), usedProduct.getM_Product_Category_ID());
                        String MMPolicy = pc.getMMPolicy();
                        if (MMPolicy == null || MMPolicy.length() == 0) {
                            MMPolicy = client.getMMPolicy();
                        }
                        storages = MStorageOnHand.getWarehouse(this.getCtx(), M_Warehouse_ID, BOMProduct_ID, 0, (Timestamp)null, "F".equals(MMPolicy), true, 0, this.get_TrxName());
                        MProductionLine BOMLine2 = null;
                        int prevLoc = -1;
                        int previousAttribSet = -1;
                        for (int sl = 0; sl < storages.length; ++sl) {
                            BigDecimal lineQty = storages[sl].getQtyOnHand();
                            if (lineQty.signum() != 0) {
                                if (lineQty.compareTo(BOMMovementQty) > 0) {
                                    lineQty = BOMMovementQty;
                                }
                                final int loc = storages[sl].getM_Locator_ID();
                                final int slASI = storages[sl].getM_AttributeSetInstance_ID();
                                final int locAttribSet = new MAttributeSetInstance(this.getCtx(), asi, this.get_TrxName()).getM_AttributeSet_ID();
                                if (locAttribSet == 0 && previousAttribSet == 0 && prevLoc == loc) {
                                    BOMLine2.setQtyUsed(BOMLine2.getQtyUsed().add(lineQty));
                                    BOMLine2.setPlannedQty(BOMLine2.getQtyUsed());
                                    BOMLine2.saveEx(this.get_TrxName());
                                }
                                else {
                                    BOMLine2 = new MProductionLine((MProduction)this);
                                    BOMLine2.setLine(this.lineno);
                                    BOMLine2.setM_Product_ID(BOMProduct_ID);
                                    BOMLine2.setM_Locator_ID(loc);
                                    BOMLine2.setQtyUsed(lineQty);
                                    BOMLine2.setPlannedQty(lineQty);
                                    if (slASI != 0 && locAttribSet != 0) {
                                        BOMLine2.setM_AttributeSetInstance_ID(slASI);
                                    }
                                    BOMLine2.saveEx(this.get_TrxName());
                                    this.lineno += 10;
                                    ++this.count;
                                }
                                prevLoc = loc;
                                previousAttribSet = locAttribSet;
                                BOMMovementQty = BOMMovementQty.subtract(lineQty);
                                if (BOMMovementQty.signum() == 0) {
                                    break;
                                }
                            }
                        }
                        if (BOMMovementQty.signum() == 0) {
                            continue;
                        }
                        if (mustBeStocked) {
                            throw new AdempiereUserError("Not enough stock of " + BOMProduct_ID);
                        }
                        if (previousAttribSet == 0 && prevLoc == defaultLocator) {
                            BOMLine2.setQtyUsed(BOMLine2.getQtyUsed().add(BOMMovementQty));
                            BOMLine2.setPlannedQty(BOMLine2.getQtyUsed());
                            BOMLine2.saveEx(this.get_TrxName());
                        }
                        else {
                            BOMLine2 = new MProductionLine((MProduction)this);
                            BOMLine2.setLine(this.lineno);
                            BOMLine2.setM_Product_ID(BOMProduct_ID);
                            BOMLine2.setM_Locator_ID(defaultLocator);
                            BOMLine2.setQtyUsed(BOMMovementQty);
                            BOMLine2.setPlannedQty(BOMMovementQty);
                            BOMLine2.saveEx(this.get_TrxName());
                            this.lineno += 10;
                            ++this.count;
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            throw new AdempiereException("Failed to create production lines", (Throwable)e);
        }
        finally {
            DB.close(rs, (Statement)pstmt);
        }
        DB.close(rs, (Statement)pstmt);
        return this.count;
    }
    
    protected boolean beforeDelete() {
        this.deleteLines(this.get_TrxName());
        return true;
    }
    
    public boolean processIt(final String processAction) {
        this.m_processMsg = null;
        final DocumentEngine engine = new DocumentEngine((DocAction)this, this.getDocStatus());
        return engine.processIt(processAction, this.getDocAction());
    }
    
    public boolean unlockIt() {
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info("unlockIt - " + this.toString());
        }
        this.setProcessing(false);
        return true;
    }
    
    public boolean invalidateIt() {
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info(this.toString());
        }
        this.setDocAction("PR");
        return true;
    }
    
    public String prepareIt() {
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info(this.toString());
        }
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 1);
        if (this.m_processMsg != null) {
            return "IN";
        }
        MPeriod.testPeriodOpen(this.getCtx(), this.getMovementDate(), "MMP", this.getAD_Org_ID());
        if (this.getIsCreated().equals("N")) {
            this.m_processMsg = "Not created";
            return "IN";
        }
        if (!this.isUseProductionPlan()) {
            this.m_processMsg = this.validateEndProduct(this.getM_Product_ID());
            if (!Util.isEmpty(this.m_processMsg)) {
                return "IN";
            }
        }
        else {
            final Query planQuery = new Query(this.getCtx(), "M_ProductionPlan", "M_ProductionPlan.M_Production_ID=?", this.get_TrxName());
            final List<MProductionPlan> plans = planQuery.setParameters(new Object[] { this.getM_Production_ID() }).list();
            for (final MProductionPlan plan : plans) {
                this.m_processMsg = this.validateEndProduct(plan.getM_Product_ID());
                if (!Util.isEmpty(this.m_processMsg)) {
                    return "IN";
                }
            }
        }
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 8);
        if (this.m_processMsg != null) {
            return "IN";
        }
        this.m_justPrepared = true;
        if (!"CO".equals(this.getDocAction())) {
            this.setDocAction("CO");
        }
        return "IP";
    }
    
    public String validateEndProduct(final int M_Product_ID) {
        String msg = this.isBom(M_Product_ID);
        if (!Util.isEmpty(msg)) {
            return msg;
        }
        if (!this.costsOK(M_Product_ID)) {
            msg = "Excessive difference in standard costs";
            if (MSysConfig.getBooleanValue("MFG_ValidateCostsDifferenceOnCreate", false, this.getAD_Client_ID())) {
                return msg;
            }
            this.log.warning(msg);
        }
        return null;
    }
    
    protected String isBom(final int M_Product_ID) {
        final String bom = DB.getSQLValueString(this.get_TrxName(), "SELECT isbom FROM M_Product WHERE M_Product_ID = ?", M_Product_ID);
        if ("N".compareTo(bom) == 0) {
            return "Attempt to create product line for Non Bill Of Materials";
        }
        final int materials = DB.getSQLValue(this.get_TrxName(), "SELECT count(M_Product_BOM_ID) FROM M_Product_BOM WHERE M_Product_ID = ?", M_Product_ID);
        if (materials == 0) {
            return "Attempt to create product line for Bill Of Materials with no BOM Products";
        }
        return null;
    }
    
    protected boolean costsOK(final int M_Product_ID) throws AdempiereUserError {
        final MProduct product = MProduct.get(this.getCtx(), M_Product_ID);
        product.getCostingMethod(MClient.get(this.getCtx()).getAcctSchema());
        final int costelement_id = 1000004;
        final int client_id = 1000001;
        final int AD_Org_ID = this.getAD_Org_ID();
        final StringBuilder update = new StringBuilder("UPDATE M_Cost set CurrentCostPrice = COALESCE((select Sum (b.BOMQty * c.currentcostprice)").append(" FROM M_Product_BOM b INNER JOIN M_Cost c ON (b.M_PRODUCTBOM_ID = c.M_Product_ID) ").append(" WHERE b.M_Product_ID = ").append(M_Product_ID).append(" AND M_CostElement_ID = ").append(costelement_id).append(" AND c.AD_Org_ID = ").append(AD_Org_ID).append("),0),").append(" FutureCostPrice = COALESCE((select Sum (b.BOMQty * c.futurecostprice) FROM M_Product_BOM b ").append(" INNER JOIN M_Cost c ON (b.M_PRODUCTBOM_ID = c.M_Product_ID) ").append(" WHERE b.M_Product_ID = ").append(M_Product_ID).append(" AND M_CostElement_ID = ").append(costelement_id).append(" AND c.AD_Org_ID = ").append(AD_Org_ID).append("),0)").append(" WHERE M_Product_ID = ").append(M_Product_ID).append(" AND AD_Client_ID = ").append(client_id).append(" AND M_CostElement_ID = ").append(costelement_id).append(" AND AD_Org_ID = ").append(AD_Org_ID).append(" AND M_PRODUCT_ID IN (SELECT M_PRODUCT_ID FROM M_PRODUCT_BOM)");
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = (PreparedStatement)DB.prepareStatement(update.toString(), (String)null);
            pstmt.execute();
        }
        catch (SQLException e) {
            this.log.log(Level.SEVERE, update.toString(), (Throwable)e);
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
    
    public boolean approveIt() {
        return true;
    }
    
    public boolean rejectIt() {
        return true;
    }
    
    public boolean voidIt() {
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info(this.toString());
        }
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 2);
        if (this.m_processMsg != null) {
            return false;
        }
        if ("CL".equals(this.getDocStatus()) || "RE".equals(this.getDocStatus()) || "VO".equals(this.getDocStatus())) {
            this.m_processMsg = "Document Closed: " + this.getDocStatus();
            this.setDocAction("--");
            return false;
        }
        if ("DR".equals(this.getDocStatus()) || "IN".equals(this.getDocStatus()) || "IP".equals(this.getDocStatus()) || "AP".equals(this.getDocStatus()) || "NA".equals(this.getDocStatus())) {
            this.setIsCreated("N");
            if (!this.isUseProductionPlan()) {
                this.deleteLines(this.get_TrxName());
                this.setProductionQty(BigDecimal.ZERO);
            }
            else {
                final Query planQuery = new Query(Env.getCtx(), "M_ProductionPlan", "M_ProductionPlan.M_Production_ID=?", this.get_TrxName());
                final List<MProductionPlan> plans = planQuery.setParameters(new Object[] { this.getM_Production_ID() }).list();
                for (final MProductionPlan plan : plans) {
                    plan.deleteLines(this.get_TrxName());
                    plan.setProductionQty(BigDecimal.ZERO);
                    plan.setProcessed(true);
                    plan.saveEx();
                }
            }
            this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 10);
            if (this.m_processMsg != null) {
                return false;
            }
            this.setProcessed(true);
            this.setDocAction("--");
            return true;
        }
        else {
            boolean accrual = false;
            try {
                MPeriod.testPeriodOpen(this.getCtx(), this.getMovementDate(), "MMP", this.getAD_Org_ID());
            }
            catch (PeriodClosedException ex) {
                accrual = true;
            }
            if (accrual) {
                return this.reverseAccrualIt();
            }
            return this.reverseCorrectIt();
        }
    }
    
    public boolean closeIt() {
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info(this.toString());
        }
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 3);
        if (this.m_processMsg != null) {
            return false;
        }
        this.setProcessed(true);
        this.setDocAction("--");
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 11);
        return this.m_processMsg == null;
    }
    
    public boolean reverseCorrectIt() {
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info(this.toString());
        }
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 5);
        if (this.m_processMsg != null) {
            return false;
        }
        final MProduction reversal = this.reverse(false);
        if (reversal == null) {
            return false;
        }
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 13);
        if (this.m_processMsg != null) {
            return false;
        }
        this.m_processMsg = reversal.getDocumentNo();
        return true;
    }
    
    protected MProduction reverse(final boolean accrual) {
        Timestamp reversalDate = accrual ? Env.getContextAsDate(this.getCtx(), "#Date") : this.getMovementDate();
        if (reversalDate == null) {
            reversalDate = new Timestamp(System.currentTimeMillis());
        }
        MPeriod.testPeriodOpen(this.getCtx(), reversalDate, "MMP", this.getAD_Org_ID());
        MProduction reversal = null;
        reversal = this.copyFrom(reversalDate);
        StringBuilder msgadd = new StringBuilder("{->").append(this.getDocumentNo()).append(")");
        reversal.addDescription(msgadd.toString());
        reversal.setReversal_ID(this.getM_Production_ID());
        reversal.saveEx(this.get_TrxName());
        if (!reversal.processIt("CO")) {
            this.m_processMsg = "Reversal ERROR: " + reversal.getProcessMsg();
            return null;
        }
        reversal.closeIt();
        reversal.setProcessing(false);
        reversal.setDocStatus("RE");
        reversal.setDocAction("--");
        reversal.saveEx(this.get_TrxName());
        msgadd = new StringBuilder("(").append(reversal.getDocumentNo()).append("<-)");
        this.addDescription(msgadd.toString());
        this.setProcessed(true);
        this.setReversal_ID(reversal.getM_Production_ID());
        this.setDocStatus("RE");
        this.setDocAction("--");
        return reversal;
    }
    
    protected MProduction copyFrom(final Timestamp reversalDate) {
        final MProduction to = new MProduction(this.getCtx(), 0, this.get_TrxName());
        PO.copyValues((PO)this, (PO)to, this.getAD_Client_ID(), this.getAD_Org_ID());
        to.set_ValueNoCheck("DocumentNo", (Object)null);
        to.setDocStatus("DR");
        to.setDocAction("CO");
        to.setMovementDate(reversalDate);
        to.setIsComplete(false);
        to.setIsCreated("Y");
        to.setProcessing(false);
        to.setProcessed(false);
        to.setIsUseProductionPlan(this.isUseProductionPlan());
        if (this.isUseProductionPlan()) {
            to.saveEx();
            final Query planQuery = new Query(Env.getCtx(), "M_ProductionPlan", "M_ProductionPlan.M_Production_ID=?", this.get_TrxName());
            final List<MProductionPlan> fplans = planQuery.setParameters(new Object[] { this.getM_Production_ID() }).list();
            for (final MProductionPlan fplan : fplans) {
                final MProductionPlan tplan = new MProductionPlan(this.getCtx(), 0, this.get_TrxName());
                PO.copyValues((PO)fplan, (PO)tplan, this.getAD_Client_ID(), this.getAD_Org_ID());
                tplan.setM_Production_ID(to.getM_Production_ID());
                tplan.setProductionQty(fplan.getProductionQty().negate());
                tplan.setProcessed(false);
                tplan.saveEx();
                final MProductionLine[] flines = fplan.getLines();
                MProductionLine[] array;
                for (int length = (array = flines).length, i = 0; i < length; ++i) {
                    final MProductionLine fline = array[i];
                    final MProductionLine tline = new MProductionLine(tplan);
                    PO.copyValues((PO)fline, (PO)tline, this.getAD_Client_ID(), this.getAD_Org_ID());
                    tline.setM_ProductionPlan_ID(tplan.getM_ProductionPlan_ID());
                    tline.setMovementQty(fline.getMovementQty().negate());
                    tline.setPlannedQty(fline.getPlannedQty().negate());
                    tline.setQtyUsed(fline.getQtyUsed().negate());
                    tline.saveEx();
                }
            }
        }
        else {
            to.setProductionQty(this.getProductionQty().negate());
            to.saveEx();
            final MProductionLine[] flines2 = this.getLines();
            MProductionLine[] array2;
            for (int length2 = (array2 = flines2).length, j = 0; j < length2; ++j) {
                final MProductionLine fline2 = array2[j];
                final MProductionLine tline2 = new MProductionLine(to);
                PO.copyValues((PO)fline2, (PO)tline2, this.getAD_Client_ID(), this.getAD_Org_ID());
                tline2.setM_Production_ID(to.getM_Production_ID());
                tline2.setMovementQty(fline2.getMovementQty().negate());
                tline2.setPlannedQty(fline2.getPlannedQty().negate());
                tline2.setQtyUsed(fline2.getQtyUsed().negate());
                tline2.saveEx();
            }
        }
        return to;
    }
    
    public void addDescription(final String description) {
        final String desc = this.getDescription();
        if (desc == null) {
            this.setDescription(description);
        }
        else {
            final StringBuilder msgd = new StringBuilder(desc).append(" | ").append(description);
            this.setDescription(msgd.toString());
        }
    }
    
    public boolean reverseAccrualIt() {
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info(this.toString());
        }
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 6);
        if (this.m_processMsg != null) {
            return false;
        }
        final MProduction reversal = this.reverse(true);
        if (reversal == null) {
            return false;
        }
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 14);
        if (this.m_processMsg != null) {
            return false;
        }
        this.m_processMsg = reversal.getDocumentNo();
        return true;
    }
    
    public boolean reActivateIt() {
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info("reActivateIt - " + this.toString());
        }
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 4);
        if (this.m_processMsg != null) {
            return false;
        }
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 12);
        return this.m_processMsg != null && false;
    }
    
    public String getSummary() {
        return this.getDocumentNo();
    }
    
    public String getDocumentInfo() {
        return this.getDocumentNo();
    }
    
    public File createPDF() {
        return null;
    }
    
    public String getProcessMsg() {
        return this.m_processMsg;
    }
    
    public int getDoc_User_ID() {
        return this.getCreatedBy();
    }
    
    public int getC_Currency_ID() {
        return MClient.get(this.getCtx()).getC_Currency_ID();
    }
    
    public BigDecimal getApprovalAmt() {
        return BigDecimal.ZERO;
    }
    
    protected boolean beforeSave(final boolean newRecord) {
        if (this.getM_Product_ID() > 0) {
            if (this.isUseProductionPlan()) {
                this.setIsUseProductionPlan(false);
            }
        }
        else if (!this.isUseProductionPlan()) {
            this.setIsUseProductionPlan(true);
        }
        
        // Compare Qty Producttion
        if(get_ValueAsInt("KJS_ProductionPlanLine_ID") > 0) {
	        String sql1 =" SELECT coalesce(SUM(ProductionQty),0) FROM M_Production WHERE KJS_ProductionPlanLine_ID = " + get_ValueAsInt("KJS_ProductionPlanLine_ID") +
	        			 " AND DocStatus NOT IN ('VO', 'RE') AND M_Production_ID != ? ";
	        BigDecimal QtyLHP = DB.getSQLValueBD(get_TrxName(), sql1, getM_Production_ID());
	        
	        String sql2 = " SELECT coalesce(ProductionQty,0) FROM KJS_ProductionPlanLine WHERE KJS_ProductionPlanLine_ID = ?"; 
	        BigDecimal QtyJob = DB.getSQLValueBD(get_TrxName(), sql2, get_ValueAsInt("KJS_ProductionPlanLine_ID"));
	        
	        BigDecimal qty_allow = QtyJob.add(QtyJob.multiply(BigDecimal.valueOf(0.1)));
	        if((QtyLHP.add(getProductionQty())).compareTo(qty_allow) > 0)
	        	throw new AdempiereException("Total Qty Produksi LHP telah melebihi Qty Production JOB Phase (+ 10%)");
        }
        return true;
    }
}
