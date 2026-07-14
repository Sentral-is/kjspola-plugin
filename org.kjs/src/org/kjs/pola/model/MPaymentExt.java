package org.kjs.pola.model;

import java.math.BigDecimal;
import org.compiere.model.MSysConfig;
import org.compiere.util.Env;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.compiere.util.DB;
import org.compiere.model.MBPartner;
import org.compiere.model.MPaymentAllocate;
import org.compiere.model.MOrder;
import org.compiere.model.MPeriod;
import org.compiere.model.MPaySelectionCheck;
import org.compiere.model.PO;
import org.compiere.model.ModelValidationEngine;
import java.util.logging.Level;
import java.util.Properties;
import org.compiere.model.PaymentInterface;
import org.compiere.process.ProcessCall;
import org.compiere.process.DocAction;
import org.compiere.model.MPayment;

public class MPaymentExt extends MPayment implements DocAction, ProcessCall, PaymentInterface
{
    private static final long serialVersionUID = -8011981477875957664L;
    private String m_processMsg;
    private boolean m_justPrepared;
    
    public MPaymentExt(final Properties ctx, final int C_Payment_ID, final String trxName) {
        super(ctx, C_Payment_ID, trxName);
        this.m_processMsg = null;
        this.m_justPrepared = false;
    }
    
    public String prepareIt() {
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info(this.toString());
        }
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 1);
        if (this.m_processMsg != null) {
            return "IN";
        }
        if (!MPaySelectionCheck.deleteGeneratedDraft(this.getCtx(), this.getC_Payment_ID(), this.get_TrxName())) {
            this.m_processMsg = "Could not delete draft generated payment selection lines";
            return "IN";
        }
        if (!MPeriod.isOpen(this.getCtx(), this.getDateAcct(), this.isReceipt() ? "ARR" : "APP", this.getAD_Org_ID())) {
            this.m_processMsg = "@PeriodClosed@";
            return "IN";
        }
        if (this.isOnline() && !this.isApproved()) {
            if (this.getR_Result() != null) {
                this.m_processMsg = "@OnlinePaymentFailed@";
            }
            else {
                this.m_processMsg = "@PaymentNotProcessed@";
            }
            return "IN";
        }
        if (this.getC_Order_ID() != 0 && this.getC_Invoice_ID() == 0) {
            final MOrder order = new MOrder(this.getCtx(), this.getC_Order_ID(), this.get_TrxName());
            if ("WP".equals(order.getDocStatus())) {
                order.setC_Payment_ID(this.getC_Payment_ID());
                order.setDocAction("WC");
                order.set_TrxName(this.get_TrxName());
                order.setDocStatus("CO");
                order.saveEx(this.get_TrxName());
            }
        }
        final MPaymentAllocate[] pAllocs = MPaymentAllocate.get((MPayment)this);
        if (!this.verifyDocType(pAllocs)) {
            this.m_processMsg = "@PaymentDocTypeInvoiceInconsistent@";
            return "IN";
        }
        if (!this.verifyPaymentAllocateVsHeader(pAllocs)) {
            this.m_processMsg = "@PaymentAllocateIgnored@";
            return "IN";
        }
        if (!this.verifyPaymentAllocateSum(pAllocs)) {
            this.m_processMsg = "@PaymentAllocateSumInconsistent@";
            return "IN";
        }
        if (!this.isReceipt()) {
            final MBPartner bp = new MBPartner(this.getCtx(), this.getC_BPartner_ID(), this.get_TrxName());
            if ("S".equals(bp.getSOCreditStatus())) {
                this.m_processMsg = "@BPartnerCreditStop@ - @TotalOpenBalance@=" + bp.getTotalOpenBalance() + ", @SO_CreditLimit@=" + bp.getSO_CreditLimit();
                return "IN";
            }
            if ("H".equals(bp.getSOCreditStatus())) {
                this.m_processMsg = "@BPartnerCreditHold@ - @TotalOpenBalance@=" + bp.getTotalOpenBalance() + ", @SO_CreditLimit@=" + bp.getSO_CreditLimit();
                return "IN";
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
    
    protected boolean verifyDocType(final MPaymentAllocate[] pAllocs) {
        if (this.getC_DocType_ID() == 0) {
            return false;
        }
        Boolean documentSO = null;
        Label_0528: {
            if (this.getC_Invoice_ID() > 0) {
                final String sql = "SELECT idt.IsSOTrx FROM C_Invoice i INNER JOIN C_DocType idt ON (CASE WHEN i.C_DocType_ID=0 THEN i.C_DocTypeTarget_ID ELSE i.C_DocType_ID END=idt.C_DocType_ID) WHERE i.C_Invoice_ID=?";
                PreparedStatement pstmt = null;
                ResultSet rs = null;
                try {
                    pstmt = (PreparedStatement)DB.prepareStatement(sql, this.get_TrxName());
                    pstmt.setInt(1, this.getC_Invoice_ID());
                    rs = pstmt.executeQuery();
                    if (rs.next()) {
                        documentSO = new Boolean("Y".equals(rs.getString(1)));
                    }
                }
                catch (Exception e) {
                    this.log.log(Level.SEVERE, sql, (Throwable)e);
                    break Label_0528;
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
            else if (this.getC_Order_ID() > 0) {
                final String sql = "SELECT odt.IsSOTrx FROM C_Order o INNER JOIN C_DocType odt ON (o.C_DocType_ID=odt.C_DocType_ID) WHERE o.C_Order_ID=?";
                PreparedStatement pstmt = null;
                ResultSet rs = null;
                try {
                    pstmt = (PreparedStatement)DB.prepareStatement(sql, this.get_TrxName());
                    pstmt.setInt(1, this.getC_Order_ID());
                    rs = pstmt.executeQuery();
                    if (rs.next()) {
                        documentSO = new Boolean("Y".equals(rs.getString(1)));
                    }
                }
                catch (Exception e) {
                    this.log.log(Level.SEVERE, sql, (Throwable)e);
                    break Label_0528;
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
            else if (this.getC_Charge_ID() <= 0 && pAllocs.length > 0) {
                for (final MPaymentAllocate pAlloc : pAllocs) {
                    final String sql2 = "SELECT idt.IsSOTrx FROM C_Invoice i INNER JOIN C_DocType idt ON (i.C_DocType_ID=idt.C_DocType_ID) WHERE i.C_Invoice_ID=?";
                    PreparedStatement pstmt2 = null;
                    ResultSet rs2 = null;
                    Label_0518: {
                        try {
                            pstmt2 = (PreparedStatement)DB.prepareStatement(sql2, this.get_TrxName());
                            pstmt2.setInt(1, pAlloc.getC_Invoice_ID());
                            rs2 = pstmt2.executeQuery();
                            if (rs2.next()) {
                                if (documentSO != null) {
                                    if (documentSO != "Y".equals(rs2.getString(1))) {
                                        return false;
                                    }
                                }
                                else {
                                    documentSO = new Boolean("Y".equals(rs2.getString(1)));
                                }
                            }
                        }
                        catch (Exception e2) {
                            this.log.log(Level.SEVERE, sql2, (Throwable)e2);
                            break Label_0518;
                        }
                        finally {
                            DB.close(rs2, (Statement)pstmt2);
                            rs2 = null;
                            pstmt2 = null;
                        }
                        DB.close(rs2, (Statement)pstmt2);
                        rs2 = null;
                        pstmt2 = null;
                    }
                }
            }
        }
        Boolean paymentSO = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        final String sql3 = "SELECT IsSOTrx FROM C_DocType WHERE C_DocType_ID=?";
        Label_0671: {
            try {
                pstmt = (PreparedStatement)DB.prepareStatement(sql3, this.get_TrxName());
                pstmt.setInt(1, this.getC_DocType_ID());
                rs = pstmt.executeQuery();
                if (rs.next()) {
                    paymentSO = new Boolean("Y".equals(rs.getString(1)));
                }
            }
            catch (Exception e3) {
                this.log.log(Level.SEVERE, sql3, (Throwable)e3);
                break Label_0671;
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
        if (paymentSO == null) {
            return false;
        }
        this.setIsReceipt((boolean)paymentSO);
        return documentSO == null || documentSO == (boolean)paymentSO;
    }
    
    protected boolean verifyPaymentAllocateVsHeader(final MPaymentAllocate[] pAllocs) {
        return pAllocs.length <= 0 || (this.getC_Charge_ID() <= 0 && this.getC_Invoice_ID() <= 0 && this.getC_Order_ID() <= 0);
    }
    
    protected boolean verifyPaymentAllocateSum(final MPaymentAllocate[] pAllocs) {
        BigDecimal sumPaymentAllocates = Env.ZERO;
        if (pAllocs.length > 0) {
            for (final MPaymentAllocate pAlloc : pAllocs) {
                sumPaymentAllocates = sumPaymentAllocates.add(pAlloc.getAmount());
            }
            if (this.getPayAmt().compareTo(sumPaymentAllocates) != 0) {
                return this.isReceipt() && this.getPayAmt().compareTo(sumPaymentAllocates) < 0 && MSysConfig.getBooleanValue("ALLOW_OVER_APPLIED_PAYMENT", false, Env.getAD_Client_ID(Env.getCtx()));
            }
        }
        return true;
    }
}
