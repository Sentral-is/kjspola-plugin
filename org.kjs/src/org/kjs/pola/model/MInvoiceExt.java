package org.kjs.pola.model;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MDocTypeCounter;
import org.compiere.model.MOrg;
import org.compiere.model.MPeriod;
import org.compiere.model.MSysConfig;
import java.util.Iterator;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MPaymentProcessor;
import java.math.BigDecimal;
import org.compiere.model.MPaymentTransaction;
import org.compiere.model.MOrder;
import org.compiere.model.MProject;
import org.compiere.util.Msg;
import java.sql.Timestamp;
import org.compiere.model.MUser;
import org.compiere.util.Env;
import org.compiere.model.MConversionRateUtil;
import org.compiere.model.MClient;
import org.compiere.model.MConversionRate;
import org.compiere.util.DB;
import org.compiere.model.MBPartner;
import org.compiere.model.MRMALine;
import org.compiere.model.MMatchPO;
import org.compiere.model.MOrderLine;
import org.compiere.util.CLogger;
import org.compiere.model.MMatchInv;
import org.compiere.model.MInOutLine;
import org.compiere.model.MPayment;
import org.compiere.model.MDocType;
import org.compiere.model.Query;
import org.compiere.model.MBankAccount;
import java.util.logging.Level;
import org.compiere.model.ModelValidationEngine;
import java.util.Properties;
import org.compiere.model.PO;
import java.util.ArrayList;
import org.compiere.process.DocAction;
import org.compiere.model.MInvoice;

public class MInvoiceExt extends MInvoice implements DocAction
{
    private static final long serialVersionUID = 7021033683791356558L;
    private String m_processMsg;
    private boolean m_justPrepared;
    ArrayList<PO> docsPostProcess;
    
    public MInvoiceExt(final Properties ctx, final int C_Invoice_ID, final String trxName) {
        super(ctx, C_Invoice_ID, trxName);
        this.m_processMsg = null;
        this.m_justPrepared = false;
        this.docsPostProcess = new ArrayList<PO>();
    }
    
    public String completeIt() {
        if (!this.m_justPrepared) {
            final String status = this.prepareIt();
            this.m_justPrepared = false;
            if (!"IP".equals(status)) {
                return status;
            }
        }
        this.setDefiniteDocumentNo();
        this.m_processMsg = ModelValidationEngine.get().fireDocValidate((PO)this, 7);
        if (this.m_processMsg != null) {
            return "IN";
        }
        if (!this.isApproved()) {
            this.approveIt();
        }
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info(this.toString());
        }
        final StringBuilder info = new StringBuilder();
        boolean fromPOS = false;
        if (this.getC_Order_ID() > 0) {
            fromPOS = (this.getC_Order().getC_POS_ID() > 0);
        }
        if ("B".equals(this.getPaymentRule()) && !fromPOS) {
            final String whereClause = "AD_Org_ID=? AND C_Currency_ID=?";
            final MBankAccount ba = (MBankAccount)new Query(this.getCtx(), "C_BankAccount", whereClause, this.get_TrxName()).setParameters(new Object[] { this.getAD_Org_ID(), this.getC_Currency_ID() }).setOnlyActiveRecords(true).setOrderBy("IsDefault DESC").first();
            if (ba == null) {
                this.m_processMsg = "@NoAccountOrgCurrency@";
                return "IN";
            }
            String docBaseType = "";
            if (this.isSOTrx()) {
                docBaseType = "ARR";
            }
            else {
                docBaseType = "APP";
            }
            final MDocType[] doctypes = MDocType.getOfDocBaseType(this.getCtx(), docBaseType);
            if (doctypes == null || doctypes.length == 0) {
                this.m_processMsg = "No document type ";
                return "IN";
            }
            MDocType doctype = null;
            MDocType[] array;
            for (int length = (array = doctypes).length, j = 0; j < length; ++j) {
                final MDocType doc = array[j];
                if (doc.getAD_Org_ID() == this.getAD_Org_ID()) {
                    doctype = doc;
                    break;
                }
            }
            if (doctype == null) {
                doctype = doctypes[0];
            }
            final MPayment payment = new MPayment(this.getCtx(), 0, this.get_TrxName());
            payment.setAD_Org_ID(this.getAD_Org_ID());
            payment.setTenderType("X");
            payment.setC_BankAccount_ID(ba.getC_BankAccount_ID());
            payment.setC_BPartner_ID(this.getC_BPartner_ID());
            payment.setC_Invoice_ID(this.getC_Invoice_ID());
            payment.setC_Currency_ID(this.getC_Currency_ID());
            payment.setC_DocType_ID(doctype.getC_DocType_ID());
            payment.setPayAmt(this.getGrandTotal());
            payment.setIsPrepayment(false);
            payment.setDateAcct(this.getDateAcct());
            payment.setDateTrx(this.getDateInvoiced());
            payment.saveEx();
            payment.setDocAction("CO");
            if (!payment.processIt("CO")) {
                this.m_processMsg = "Cannot Complete the Payment : [" + payment.getProcessMsg() + "] " + payment;
                return "IN";
            }
            payment.saveEx();
            info.append("@C_Payment_ID@: " + payment.getDocumentInfo());
            if (payment.getJustCreatedAllocInv() != null) {
                this.addDocsPostProcess((PO)payment.getJustCreatedAllocInv());
            }
        }
        int matchInv = 0;
        int matchPO = 0;
        final MInvoiceLine[] lines = this.getLines(false);
        for (int i = 0; i < lines.length; ++i) {
            final MInvoiceLine line = lines[i];
            if (!this.isSOTrx() && line.getM_InOutLine_ID() != 0 && line.getM_Product_ID() != 0 && !this.isReversal()) {
                final MInOutLine receiptLine = new MInOutLine(this.getCtx(), line.getM_InOutLine_ID(), this.get_TrxName());
                BigDecimal matchQty = line.getQtyInvoiced();
                if (receiptLine.getMovementQty().compareTo(matchQty) < 0) {
                    matchQty = receiptLine.getMovementQty();
                }
                final MMatchInv inv = new MMatchInv(line, this.getDateInvoiced(), matchQty);
                if (!inv.save(this.get_TrxName())) {
                    this.m_processMsg = CLogger.retrieveErrorString("Could not create Invoice Matching");
                    return "IN";
                }
                ++matchInv;
                this.addDocsPostProcess((PO)inv);
            }
            MOrderLine ol = null;
            if (line.getC_OrderLine_ID() != 0) {
                if (this.isSOTrx() || line.getM_Product_ID() == 0) {
                    if (this.getC_DocType_ID() != 1000089) {
                        ol = new MOrderLine(this.getCtx(), line.getC_OrderLine_ID(), this.get_TrxName());
                        if (line.getQtyInvoiced() != null) {
                            ol.setQtyInvoiced(ol.getQtyInvoiced().add(line.getQtyInvoiced()));
                        }
                        if (!ol.save(this.get_TrxName())) {
                            this.m_processMsg = "Could not update Order Line";
                            return "IN";
                        }
                    }
                }
                else if (!this.isSOTrx() && line.getM_Product_ID() != 0 && !this.isReversal()) {
                    final BigDecimal matchQty = line.getQtyInvoiced();
                    final MMatchPO po = MMatchPO.create(line, (MInOutLine)null, this.getDateInvoiced(), matchQty);
                    if (po != null) {
                        if (!po.save(this.get_TrxName())) {
                            this.m_processMsg = "Could not create PO Matching";
                            return "IN";
                        }
                        ++matchPO;
                        if (!po.isPosted() && po.getM_InOutLine_ID() > 0) {
                            this.addDocsPostProcess((PO)po);
                        }
                        final MMatchInv[] matchInvoices = MMatchInv.getInvoiceLine(this.getCtx(), line.getC_InvoiceLine_ID(), this.get_TrxName());
                        if (matchInvoices != null && matchInvoices.length > 0) {
                            MMatchInv[] array2;
                            for (int length2 = (array2 = matchInvoices).length, k = 0; k < length2; ++k) {
                                final MMatchInv matchInvoice = array2[k];
                                if (!matchInvoice.isPosted()) {
                                    this.addDocsPostProcess((PO)matchInvoice);
                                }
                            }
                        }
                    }
                }
            }
            if (line.getM_RMALine_ID() != 0) {
                final MRMALine rmaLine = new MRMALine(this.getCtx(), line.getM_RMALine_ID(), this.get_TrxName());
                if (rmaLine.getQtyInvoiced() != null) {
                    rmaLine.setQtyInvoiced(rmaLine.getQtyInvoiced().add(line.getQtyInvoiced()));
                }
                else {
                    rmaLine.setQtyInvoiced(line.getQtyInvoiced());
                }
                if (!rmaLine.save(this.get_TrxName())) {
                    this.m_processMsg = "Could not update RMA Line";
                    return "IN";
                }
            }
        }
        if (matchInv > 0) {
            info.append(" @M_MatchInv_ID@#").append(matchInv).append(" ");
        }
        if (matchPO > 0) {
            info.append(" @M_MatchPO_ID@#").append(matchPO).append(" ");
        }
        final MBPartner bp = new MBPartner(this.getCtx(), this.getC_BPartner_ID(), this.get_TrxName());
        DB.getDatabase().forUpdate((PO)bp, 0);
        final BigDecimal invAmt = MConversionRate.convertBase(this.getCtx(), this.getGrandTotal(true), this.getC_Currency_ID(), this.getDateAcct(), this.getC_ConversionType_ID(), this.getAD_Client_ID(), this.getAD_Org_ID());
        if (invAmt == null) {
            this.m_processMsg = MConversionRateUtil.getErrorMessage(this.getCtx(), "ErrorConvertingCurrencyToBaseCurrency", this.getC_Currency_ID(), MClient.get(this.getCtx()).getC_Currency_ID(), this.getC_ConversionType_ID(), this.getDateAcct(), this.get_TrxName());
            return "IN";
        }
        BigDecimal newBalance = bp.getTotalOpenBalance();
        if (newBalance == null) {
            newBalance = Env.ZERO;
        }
        if (this.isSOTrx()) {
            newBalance = newBalance.add(invAmt);
            if (bp.getFirstSale() == null) {
                bp.setFirstSale(this.getDateInvoiced());
            }
            BigDecimal newLifeAmt = bp.getActualLifeTimeValue();
            if (newLifeAmt == null) {
                newLifeAmt = invAmt;
            }
            else {
                newLifeAmt = newLifeAmt.add(invAmt);
            }
            BigDecimal newCreditAmt = bp.getSO_CreditUsed();
            if (newCreditAmt == null) {
                newCreditAmt = invAmt;
            }
            else {
                newCreditAmt = newCreditAmt.add(invAmt);
            }
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("GrandTotal=" + this.getGrandTotal(true) + "(" + invAmt + ") BP Life=" + bp.getActualLifeTimeValue() + "->" + newLifeAmt + ", Credit=" + bp.getSO_CreditUsed() + "->" + newCreditAmt + ", Balance=" + bp.getTotalOpenBalance() + " -> " + newBalance);
            }
            bp.setActualLifeTimeValue(newLifeAmt);
            bp.setSO_CreditUsed(newCreditAmt);
        }
        else {
            newBalance = newBalance.subtract(invAmt);
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("GrandTotal=" + this.getGrandTotal(true) + "(" + invAmt + ") Balance=" + bp.getTotalOpenBalance() + " -> " + newBalance);
            }
        }
        bp.setTotalOpenBalance(newBalance);
        bp.setSOCreditStatus();
        if (!bp.save(this.get_TrxName())) {
            this.m_processMsg = "Could not update Business Partner";
            return "IN";
        }
        if (this.getAD_User_ID() != 0) {
            final MUser user = new MUser(this.getCtx(), this.getAD_User_ID(), this.get_TrxName());
            user.setLastContact(new Timestamp(System.currentTimeMillis()));
            final StringBuilder msgset = new StringBuilder().append(Msg.translate(this.getCtx(), "C_Invoice_ID")).append(": ").append(this.getDocumentNo());
            user.setLastResult(msgset.toString());
            if (!user.save(this.get_TrxName())) {
                this.m_processMsg = "Could not update Business Partner User";
                return "IN";
            }
        }
        if (this.isSOTrx() && this.getC_Project_ID() != 0) {
            final MProject project = new MProject(this.getCtx(), this.getC_Project_ID(), this.get_TrxName());
            BigDecimal amt = this.getGrandTotal(true);
            final int C_CurrencyTo_ID = project.getC_Currency_ID();
            if (C_CurrencyTo_ID != this.getC_Currency_ID()) {
                amt = MConversionRate.convert(this.getCtx(), amt, this.getC_Currency_ID(), C_CurrencyTo_ID, this.getDateAcct(), 0, this.getAD_Client_ID(), this.getAD_Org_ID());
            }
            if (amt == null) {
                this.m_processMsg = MConversionRateUtil.getErrorMessage(this.getCtx(), "ErrorConvertingCurrencyToProjectCurrency", this.getC_Currency_ID(), C_CurrencyTo_ID, 0, this.getDateAcct(), this.get_TrxName());
                return "IN";
            }
            BigDecimal newAmt = project.getInvoicedAmt();
            if (newAmt == null) {
                newAmt = amt;
            }
            else {
                newAmt = newAmt.add(amt);
            }
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("GrandTotal=" + this.getGrandTotal(true) + "(" + amt + ") Project " + project.getName() + " - Invoiced=" + project.getInvoicedAmt() + "->" + newAmt);
            }
            project.setInvoicedAmt(newAmt);
            if (!project.save(this.get_TrxName())) {
                this.m_processMsg = "Could not update Project";
                return "IN";
            }
        }
        if (this.isSOTrx() && !this.isReversal()) {
            final StringBuilder whereClause2 = new StringBuilder();
            whereClause2.append("C_Order_ID IN (");
            whereClause2.append("SELECT C_Order_ID ");
            whereClause2.append("FROM C_OrderLine ");
            whereClause2.append("WHERE C_OrderLine_ID IN (");
            whereClause2.append("SELECT C_OrderLine_ID ");
            whereClause2.append("FROM C_InvoiceLine ");
            whereClause2.append("WHERE C_Invoice_ID = ");
            whereClause2.append(this.getC_Invoice_ID()).append("))");
            final int[] orderIDList = MOrder.getAllIDs("C_Order", whereClause2.toString(), this.get_TrxName());
            final int[] ids = MPaymentTransaction.getAuthorizationPaymentTransactionIDs(orderIDList, this.getC_Invoice_ID(), this.get_TrxName());
            if (ids.length > 0) {
                boolean pureCIM = true;
                final ArrayList<MPaymentTransaction> ptList = new ArrayList<MPaymentTransaction>();
                BigDecimal totalPayAmt = BigDecimal.ZERO;
                int[] array3;
                for (int length3 = (array3 = ids).length, l = 0; l < length3; ++l) {
                    final int id = array3[l];
                    final MPaymentTransaction pt = new MPaymentTransaction(this.getCtx(), id, this.get_TrxName());
                    if (!pt.setPaymentProcessor()) {
                        if (pt.getC_PaymentProcessor_ID() > 0) {
                            final MPaymentProcessor pp = new MPaymentProcessor(this.getCtx(), pt.getC_PaymentProcessor_ID(), this.get_TrxName());
                            this.m_processMsg = String.valueOf(Msg.getMsg(this.getCtx(), "PaymentNoProcessorModel")) + ": " + pp.toString();
                        }
                        else {
                            this.m_processMsg = Msg.getMsg(this.getCtx(), "PaymentNoProcessorModel");
                        }
                        return "IN";
                    }
                    final boolean isCIM = pt.getC_PaymentProcessor_ID() > 0 && pt.getCustomerPaymentProfileID() != null && pt.getCustomerPaymentProfileID().length() > 0;
                    if (pureCIM && !isCIM) {
                        pureCIM = false;
                    }
                    totalPayAmt = totalPayAmt.add(pt.getPayAmt());
                    ptList.add(pt);
                }
                if (this.getGrandTotal().compareTo(totalPayAmt) != 0 && ptList.size() > 0 && pureCIM) {
                    final MPaymentTransaction newSalesPT = MPaymentTransaction.copyFrom((MPaymentTransaction)ptList.get(0), new Timestamp(System.currentTimeMillis()), "S", "", this.get_TrxName());
                    newSalesPT.setIsApproved(false);
                    newSalesPT.setIsVoided(false);
                    newSalesPT.setIsDelayedCapture(false);
                    newSalesPT.setDescription("InvoicedAmt: " + this.getGrandTotal() + " <> TotalAuthorizedAmt: " + totalPayAmt);
                    newSalesPT.setC_Invoice_ID(this.getC_Invoice_ID());
                    newSalesPT.setPayAmt(this.getGrandTotal());
                    for (final MPaymentTransaction pt2 : ptList) {
                        pt2.setDescription("InvoicedAmt: " + this.getGrandTotal() + " <> AuthorizedAmt: " + pt2.getPayAmt());
                        final boolean ok = pt2.voidOnlineAuthorizationPaymentTransaction();
                        pt2.saveEx();
                        if (!ok) {
                            this.m_processMsg = String.valueOf(Msg.getMsg(this.getCtx(), "VoidAuthorizationPaymentFailed")) + ": " + pt2.getErrorMessage();
                            return "IN";
                        }
                    }
                    final boolean ok2 = newSalesPT.processOnline();
                    newSalesPT.saveEx();
                    if (!ok2) {
                        this.m_processMsg = String.valueOf(Msg.getMsg(this.getCtx(), "CreateNewSalesPaymentFailed")) + ": " + newSalesPT.getErrorMessage();
                        return "IN";
                    }
                }
                else {
                    if (this.getGrandTotal().compareTo(totalPayAmt) != 0 && ptList.size() > 0) {
                        this.m_processMsg = "InvoicedAmt: " + this.getGrandTotal() + " <> AuthorizedAmt: " + totalPayAmt;
                        return "IN";
                    }
                    for (final MPaymentTransaction pt3 : ptList) {
                        final boolean ok3 = pt3.delayCaptureOnlineAuthorizationPaymentTransaction(this.getC_Invoice_ID());
                        pt3.saveEx();
                        if (!ok3) {
                            this.m_processMsg = String.valueOf(Msg.getMsg(this.getCtx(), "DelayCaptureAuthFailed")) + ": " + pt3.getErrorMessage();
                            return "IN";
                        }
                    }
                }
            }
        }
        if ("B".equals(this.getPaymentRule()) && this.testAllocation(true)) {
            this.saveEx();
        }
        final String valid = ModelValidationEngine.get().fireDocValidate((PO)this, 9);
        if (valid != null) {
            this.m_processMsg = valid;
            return "IN";
        }
        final MInvoice counter = this.createCounterDoc();
        if (counter != null) {
            info.append(" - @CounterDoc@: @C_Invoice_ID@=").append(counter.getDocumentNo());
        }
        this.m_processMsg = info.toString().trim();
        this.setProcessed(true);
        this.setDocAction("CL");
        return "CO";
    }
    
    private void setDefiniteDocumentNo() {
        if (this.isReversal() && !MSysConfig.getBooleanValue("Invoice_ReverseUseNewNumber", true, this.getAD_Client_ID())) {
            return;
        }
        final MDocType dt = MDocType.get(this.getCtx(), this.getC_DocType_ID());
        if (dt.isOverwriteDateOnComplete()) {
            this.setDateInvoiced(new Timestamp(System.currentTimeMillis()));
            if (this.getDateAcct().before(this.getDateInvoiced())) {
                this.setDateAcct(this.getDateInvoiced());
                MPeriod.testPeriodOpen(this.getCtx(), this.getDateAcct(), this.getC_DocType_ID(), this.getAD_Org_ID());
            }
        }
        if (dt.isOverwriteSeqOnComplete()) {
            final String value = DB.getDocumentNo(this.getC_DocType_ID(), this.get_TrxName(), true, (PO)this);
            if (value != null) {
                this.setDocumentNo(value);
            }
        }
    }
    
    private void addDocsPostProcess(final PO doc) {
        this.docsPostProcess.add(doc);
    }
    
    private MInvoice createCounterDoc() {
        if (this.getRef_Invoice_ID() != 0) {
            return null;
        }
        final MOrg org = MOrg.get(this.getCtx(), this.getAD_Org_ID());
        final int counterC_BPartner_ID = org.getLinkedC_BPartner_ID(this.get_TrxName());
        if (counterC_BPartner_ID == 0) {
            return null;
        }
        final MBPartner bp = new MBPartner(this.getCtx(), this.getC_BPartner_ID(), (String)null);
        final int counterAD_Org_ID = bp.getAD_OrgBP_ID();
        if (counterAD_Org_ID == 0) {
            return null;
        }
        final MBPartner counterBP = new MBPartner(this.getCtx(), counterC_BPartner_ID, (String)null);
        if (this.log.isLoggable(Level.INFO)) {
            this.log.info("Counter BP=" + counterBP.getName());
        }
        int C_DocTypeTarget_ID = 0;
        final MDocTypeCounter counterDT = MDocTypeCounter.getCounterDocType(this.getCtx(), this.getC_DocType_ID());
        if (counterDT != null) {
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine(counterDT.toString());
            }
            if (!counterDT.isCreateCounter() || !counterDT.isValid()) {
                return null;
            }
            C_DocTypeTarget_ID = counterDT.getCounter_C_DocType_ID();
        }
        else {
            C_DocTypeTarget_ID = MDocTypeCounter.getCounterDocType_ID(this.getCtx(), this.getC_DocType_ID());
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("Indirect C_DocTypeTarget_ID=" + C_DocTypeTarget_ID);
            }
            if (C_DocTypeTarget_ID <= 0) {
                return null;
            }
        }
        final MInvoice counter = copyFrom((MInvoice)this, this.getDateInvoiced(), this.getDateAcct(), C_DocTypeTarget_ID, !this.isSOTrx(), true, this.get_TrxName(), true);
        counter.setAD_Org_ID(counterAD_Org_ID);
        counter.setSalesRep_ID(this.getSalesRep_ID());
        counter.saveEx(this.get_TrxName());
        final MInvoiceLine[] counterLines = counter.getLines(true);
        for (int i = 0; i < counterLines.length; ++i) {
            final MInvoiceLine counterLine = counterLines[i];
            counterLine.setInvoice(counter);
            counterLine.setPrice();
            counterLine.setTax();
            counterLine.saveEx(this.get_TrxName());
        }
        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine(counter.toString());
        }
        if (counterDT != null && counterDT.getDocAction() != null) {
            counter.setDocAction(counterDT.getDocAction());
            if (!counter.processIt(counterDT.getDocAction())) {
                throw new AdempiereException("Failed when processing document - " + counter.getProcessMsg());
            }
            counter.saveEx(this.get_TrxName());
        }
        return counter;
    }
}
