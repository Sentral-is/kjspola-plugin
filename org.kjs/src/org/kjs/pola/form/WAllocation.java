package org.kjs.pola.form;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;

import org.adempiere.webui.component.Button;
import org.adempiere.webui.component.Checkbox;
import org.adempiere.webui.component.DocumentLink;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.ListModelTable;
import org.adempiere.webui.component.ListboxFactory;
import org.adempiere.webui.component.Panel;
import org.adempiere.webui.component.Row;
import org.adempiere.webui.component.Rows;
import org.adempiere.webui.component.Textbox;
import org.adempiere.webui.component.WListbox;
import org.adempiere.webui.editor.WDateEditor;
import org.adempiere.webui.editor.WSearchEditor;
import org.adempiere.webui.editor.WTableDirEditor;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.adempiere.webui.event.WTableModelEvent;
import org.adempiere.webui.event.WTableModelListener;
import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.CustomForm;
import org.adempiere.webui.panel.IFormController;
import org.adempiere.webui.window.Dialog;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.Lookup;
import org.compiere.model.MAllocationHdr;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.compiere.util.TrxRunnable;
import org.compiere.util.Util;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zul.Borderlayout;
import org.zkoss.zul.Center;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Hlayout;
import org.zkoss.zul.North;
import org.zkoss.zul.Separator;
import org.zkoss.zul.South;
import org.zkoss.zul.Space;

public class WAllocation extends Allocation implements IFormController, EventListener<Event>, WTableModelListener, ValueChangeListener
{
    private CustomForm form;
    private Borderlayout mainLayout;
    private Panel parameterPanel;
    private Panel allocationPanel;
    private Grid parameterLayout;
    private Label bpartnerLabel;
    private WSearchEditor bpartnerSearch;
    private WListbox invoiceTable;
    private WListbox paymentTable;
    private Borderlayout infoPanel;
    private Panel paymentPanel;
    private Panel invoicePanel;
    private Label paymentLabel;
    private Label invoiceLabel;
    private Borderlayout paymentLayout;
    private Borderlayout invoiceLayout;
    private Label paymentInfo;
    private Label invoiceInfo;
    private Grid allocationLayout;
    private Label differenceLabel;
    private Textbox differenceField;
    private Button allocateButton;
    private Button refreshButton;
    private Label currencyLabel;
    private WTableDirEditor currencyPick;
    private Checkbox multiCurrency;
    private Label chargeLabel;
    private WTableDirEditor chargePick;
    private Label DocTypeLabel;
    private WTableDirEditor DocTypePick;
    private Label allocCurrencyLabel;
    private Hlayout statusBar;
    private Label dateLabel;
    private WDateEditor dateField;
    private Checkbox autoWriteOff;
    private Label organizationLabel;
    private WTableDirEditor organizationPick;
    private Panel southPanel;
    
    public WAllocation() {
        this.form = new CustomForm();
        this.mainLayout = new Borderlayout();
        this.parameterPanel = new Panel();
        this.allocationPanel = new Panel();
        this.parameterLayout = GridFactory.newGridLayout();
        this.bpartnerLabel = new Label();
        this.bpartnerSearch = null;
        this.invoiceTable = ListboxFactory.newDataTable();
        this.paymentTable = ListboxFactory.newDataTable();
        this.infoPanel = new Borderlayout();
        this.paymentPanel = new Panel();
        this.invoicePanel = new Panel();
        this.paymentLabel = new Label();
        this.invoiceLabel = new Label();
        this.paymentLayout = new Borderlayout();
        this.invoiceLayout = new Borderlayout();
        this.paymentInfo = new Label();
        this.invoiceInfo = new Label();
        this.allocationLayout = GridFactory.newGridLayout();
        this.differenceLabel = new Label();
        this.differenceField = new Textbox();
        this.allocateButton = new Button();
        this.refreshButton = new Button();
        this.currencyLabel = new Label();
        this.currencyPick = null;
        this.multiCurrency = new Checkbox();
        this.chargeLabel = new Label();
        this.chargePick = null;
        this.DocTypeLabel = new Label();
        this.DocTypePick = null;
        this.allocCurrencyLabel = new Label();
        this.statusBar = new Hlayout();
        this.dateLabel = new Label();
        this.dateField = new WDateEditor();
        this.autoWriteOff = new Checkbox();
        this.organizationLabel = new Label();
        this.southPanel = new Panel();
        Env.setContext(Env.getCtx(), this.form.getWindowNo(), "IsSOTrx", "Y");
        try {
            super.dynInit();
            this.dynInit();
            this.zkInit();
            this.calculate();
            this.southPanel.appendChild((Component)new Separator());
            this.southPanel.appendChild((Component)this.statusBar);
        }
        catch (Exception e) {
            WAllocation.log.log(Level.SEVERE, "", (Throwable)e);
        }
    }
    
    private void zkInit() throws Exception {
        this.form.appendChild((Component)this.mainLayout);
        this.mainLayout.setWidth("99%");
        this.mainLayout.setHeight("100%");
        this.dateLabel.setText(Msg.getMsg(Env.getCtx(), "Date"));
        this.autoWriteOff.setSelected(false);
        this.autoWriteOff.setText(Msg.getMsg(Env.getCtx(), "AutoWriteOff", true));
        this.autoWriteOff.setTooltiptext(Msg.getMsg(Env.getCtx(), "AutoWriteOff", false));
        this.parameterPanel.appendChild((Component)this.parameterLayout);
        this.allocationPanel.appendChild((Component)this.allocationLayout);
        this.bpartnerLabel.setText(Msg.translate(Env.getCtx(), "C_BPartner_ID"));
        this.paymentLabel.setText(" " + Msg.translate(Env.getCtx(), "C_Payment_ID"));
        this.invoiceLabel.setText(" " + Msg.translate(Env.getCtx(), "C_Invoice_ID"));
        this.paymentPanel.appendChild((Component)this.paymentLayout);
        this.invoicePanel.appendChild((Component)this.invoiceLayout);
        this.invoiceInfo.setText(".");
        this.paymentInfo.setText(".");
        this.chargeLabel.setText(" " + Msg.translate(Env.getCtx(), "C_Charge_ID"));
        this.DocTypeLabel.setText(" " + Msg.translate(Env.getCtx(), "C_DocType_ID"));
        this.differenceLabel.setText(Msg.getMsg(Env.getCtx(), "Difference"));
        this.differenceField.setText("0");
        this.differenceField.setReadonly(true);
        this.differenceField.setStyle("text-align: right");
        this.allocateButton.setLabel(Util.cleanAmp(Msg.getMsg(Env.getCtx(), "Process")));
        this.allocateButton.addActionListener((EventListener)this);
        this.refreshButton.setLabel(Util.cleanAmp(Msg.getMsg(Env.getCtx(), "Refresh")));
        this.refreshButton.addActionListener((EventListener)this);
        this.refreshButton.setAutodisable("self");
        this.currencyLabel.setText(Msg.translate(Env.getCtx(), "C_Currency_ID"));
        this.multiCurrency.setText(Msg.getMsg(Env.getCtx(), "MultiCurrency"));
        this.multiCurrency.addActionListener((EventListener)this);
        this.allocCurrencyLabel.setText(".");
        this.organizationLabel.setText(Msg.translate(Env.getCtx(), "AD_Org_ID"));
        North north = new North();
        north.setStyle("border: none");
        this.mainLayout.appendChild((Component)north);
        north.appendChild((Component)this.parameterPanel);
        Rows rows = null;
        Row row = null;
        this.parameterLayout.setWidth("80%");
        rows = this.parameterLayout.newRows();
        row = rows.newRow();
        row.appendCellChild(this.bpartnerLabel.rightAlign());
        this.bpartnerSearch.getComponent().setHflex("true");
        row.appendCellChild((Component)this.bpartnerSearch.getComponent(), 2);
        this.bpartnerSearch.showMenu();
        final Hbox box = new Hbox();
        box.appendChild(this.dateLabel.rightAlign());
        box.appendChild((Component)this.dateField.getComponent());
        row.appendCellChild((Component)box);
        row.appendCellChild(this.organizationLabel.rightAlign());
        this.organizationPick.getComponent().setHflex("true");
        row.appendCellChild((Component)this.organizationPick.getComponent(), 1);
        this.organizationPick.showMenu();
        row = rows.newRow();
        row.appendCellChild(this.currencyLabel.rightAlign(), 1);
        this.currencyPick.getComponent().setHflex("true");
        row.appendCellChild((Component)this.currencyPick.getComponent(), 1);
        this.currencyPick.showMenu();
        row.appendCellChild((Component)this.multiCurrency, 1);
        row.appendCellChild((Component)this.autoWriteOff, 2);
        row.appendCellChild((Component)new Space(), 1);
        South south = new South();
        south.setStyle("border: none");
        this.mainLayout.appendChild((Component)south);
        south.appendChild((Component)this.southPanel);
        this.southPanel.appendChild((Component)this.allocationPanel);
        this.allocationPanel.appendChild((Component)this.allocationLayout);
        this.allocationLayout.setHflex("min");
        rows = this.allocationLayout.newRows();
        row = rows.newRow();
        row.appendCellChild(this.differenceLabel.rightAlign());
        row.appendCellChild(this.allocCurrencyLabel.rightAlign());
        this.differenceField.setHflex("true");
        row.appendCellChild((Component)this.differenceField);
        row.appendCellChild(this.chargeLabel.rightAlign());
        this.chargePick.getComponent().setHflex("true");
        row.appendCellChild((Component)this.chargePick.getComponent());
        row.appendCellChild(this.DocTypeLabel.rightAlign());
        this.chargePick.showMenu();
        this.DocTypePick.getComponent().setHflex("true");
        row.appendCellChild((Component)this.DocTypePick.getComponent());
        this.DocTypePick.showMenu();
        this.allocateButton.setHflex("true");
        row.appendCellChild((Component)this.allocateButton);
        row.appendCellChild((Component)this.refreshButton);
        this.paymentPanel.appendChild((Component)this.paymentLayout);
        this.paymentPanel.setWidth("100%");
        this.paymentPanel.setHeight("100%");
        this.paymentLayout.setWidth("100%");
        this.paymentLayout.setHeight("100%");
        this.paymentLayout.setStyle("border: none");
        this.invoicePanel.appendChild((Component)this.invoiceLayout);
        this.invoicePanel.setWidth("100%");
        this.invoicePanel.setHeight("100%");
        this.invoiceLayout.setWidth("100%");
        this.invoiceLayout.setHeight("100%");
        this.invoiceLayout.setStyle("border: none");
        north = new North();
        north.setStyle("border: none");
        this.paymentLayout.appendChild((Component)north);
        north.appendChild((Component)this.paymentLabel);
        south = new South();
        south.setStyle("border: none");
        this.paymentLayout.appendChild((Component)south);
        south.appendChild(this.paymentInfo.rightAlign());
        Center center = new Center();
        this.paymentLayout.appendChild((Component)center);
        center.appendChild((Component)this.paymentTable);
        this.paymentTable.setWidth("99%");
        this.paymentTable.setHeight("99%");
        center.setStyle("border: none");
        north = new North();
        north.setStyle("border: none");
        this.invoiceLayout.appendChild((Component)north);
        north.appendChild((Component)this.invoiceLabel);
        south = new South();
        south.setStyle("border: none");
        this.invoiceLayout.appendChild((Component)south);
        south.appendChild(this.invoiceInfo.rightAlign());
        center = new Center();
        this.invoiceLayout.appendChild((Component)center);
        center.appendChild((Component)this.invoiceTable);
        this.invoiceTable.setWidth("99%");
        this.invoiceTable.setHeight("99%");
        center.setStyle("border: none");
        center = new Center();
        this.mainLayout.appendChild((Component)center);
        center.appendChild((Component)this.infoPanel);
        this.infoPanel.setHflex("1");
        this.infoPanel.setVflex("1");
        this.infoPanel.setStyle("border: none");
        this.infoPanel.setWidth("100%");
        this.infoPanel.setHeight("100%");
        north = new North();
        north.setStyle("border: none");
        north.setHeight("49%");
        this.infoPanel.appendChild((Component)north);
        north.appendChild((Component)this.paymentPanel);
        north.setSplittable(true);
        center = new Center();
        center.setStyle("border: none");
        this.infoPanel.appendChild((Component)center);
        center.appendChild((Component)this.invoicePanel);
        this.invoicePanel.setHflex("1");
        this.invoicePanel.setVflex("1");
    }
    
    @Override
    public void dynInit() throws Exception {
        int AD_Column_ID = 3505;
        final MLookup lookupCur = MLookupFactory.get(Env.getCtx(), this.form.getWindowNo(), 0, AD_Column_ID, 19);
        (this.currencyPick = new WTableDirEditor("C_Currency_ID", true, false, true, (Lookup)lookupCur)).setValue((Object)new Integer(this.m_C_Currency_ID));
        this.currencyPick.addValueChangeListener((ValueChangeListener)this);
        AD_Column_ID = 839;
        final MLookup lookupOrg = MLookupFactory.get(Env.getCtx(), this.form.getWindowNo(), 0, AD_Column_ID, 19);
        (this.organizationPick = new WTableDirEditor("AD_Org_ID", true, false, true, (Lookup)lookupOrg)).setValue((Object)Env.getAD_Org_ID(Env.getCtx()));
        this.organizationPick.addValueChangeListener((ValueChangeListener)this);
        AD_Column_ID = 3499;
        final MLookup lookupBP = MLookupFactory.get(Env.getCtx(), this.form.getWindowNo(), 0, AD_Column_ID, 30);
        (this.bpartnerSearch = new WSearchEditor("C_BPartner_ID", true, false, true, (Lookup)lookupBP)).addValueChangeListener((ValueChangeListener)this);
        this.statusBar.appendChild((Component)new Label(Msg.getMsg(Env.getCtx(), "AllocateStatus")));
        this.statusBar.setVflex("min");
        final Calendar cal = Calendar.getInstance();
        cal.setTime(Env.getContextAsDate(Env.getCtx(), "#Date"));
        cal.set(11, 0);
        cal.set(12, 0);
        cal.set(13, 0);
        cal.set(14, 0);
        this.dateField.setValue((Object)new Timestamp(cal.getTimeInMillis()));
        this.dateField.addValueChangeListener((ValueChangeListener)this);
        AD_Column_ID = 61804;
        final MLookup lookupCharge = MLookupFactory.get(Env.getCtx(), this.form.getWindowNo(), 0, AD_Column_ID, 19);
        (this.chargePick = new WTableDirEditor("C_Charge_ID", false, false, true, (Lookup)lookupCharge)).setValue((Object)new Integer(this.m_C_Charge_ID));
        this.chargePick.addValueChangeListener((ValueChangeListener)this);
        AD_Column_ID = 212213;
        final MLookup lookupDocType = MLookupFactory.get(Env.getCtx(), this.form.getWindowNo(), 0, AD_Column_ID, 19);
        (this.DocTypePick = new WTableDirEditor("C_DocType_ID", false, false, true, (Lookup)lookupDocType)).setValue((Object)new Integer(this.m_C_DocType_ID));
        this.DocTypePick.addValueChangeListener((ValueChangeListener)this);
    }
    
    public void onEvent(final Event e) {
        WAllocation.log.config("");
        if (e.getTarget().equals(this.multiCurrency)) {
            this.loadBPartner();
        }
        else if (e.getTarget().equals(this.allocateButton)) {
            this.allocateButton.setEnabled(false);
            final MAllocationHdr allocation = this.saveData();
            this.loadBPartner();
            this.allocateButton.setEnabled(true);
            if (allocation != null) {
                final DocumentLink link = new DocumentLink(allocation.getDocumentNo(), allocation.get_Table_ID(), allocation.get_ID());
                this.statusBar.appendChild((Component)link);
            }
        }
        else if (e.getTarget().equals(this.refreshButton)) {
            this.loadBPartner();
        }
    }
    
    public void tableChanged(final WTableModelEvent e) {
        final boolean isUpdate = e.getType() == 0;
        if (!isUpdate) {
            this.calculate();
            return;
        }
        final int row = e.getFirstRow();
        final int col = e.getColumn();
        if (row < 0) {
            return;
        }
        final boolean isInvoice = e.getModel().equals(this.invoiceTable.getModel());
        final boolean isAutoWriteOff = this.autoWriteOff.isSelected();
        final String msg = this.writeOff(row, col, isInvoice, (IMiniTable)this.paymentTable, (IMiniTable)this.invoiceTable, isAutoWriteOff);
        final ListModelTable model = isInvoice ? this.invoiceTable.getModel() : this.paymentTable.getModel();
        model.updateComponent(row);
        if (msg != null && msg.length() > 0) {
            Dialog.warn(this.form.getWindowNo(), "AllocationWriteOffWarn");
        }
        this.calculate();
    }
    
    public void valueChange(final ValueChangeEvent e) {
        final String name = e.getPropertyName();
        final Object value = e.getNewValue();
        if (WAllocation.log.isLoggable(Level.CONFIG)) {
            WAllocation.log.config(String.valueOf(name) + "=" + value);
        }
        if (value == null && (!name.equals("C_Charge_ID") || !name.equals("C_DocType_ID"))) {
            return;
        }
        if (name.equals("AD_Org_ID")) {
            this.m_AD_Org_ID = (int)value;
            this.loadBPartner();
        }
        else if (name.equals("C_Charge_ID")) {
            this.m_C_Charge_ID = (int)((value != null) ? value : 0);
            this.setAllocateButton();
        }
        else if (name.equals("C_DocType_ID")) {
            this.m_C_DocType_ID = (int)((value != null) ? value : 0);
        }
        if (name.equals("C_BPartner_ID")) {
            this.bpartnerSearch.setValue(value);
            this.m_C_BPartner_ID = (int)value;
            this.loadBPartner();
        }
        else if (name.equals("C_Currency_ID")) {
            this.m_C_Currency_ID = (int)value;
            this.loadBPartner();
        }
        else if (name.equals("Date") && this.multiCurrency.isSelected()) {
            this.loadBPartner();
        }
    }
    
    private void setAllocateButton() {
        if (this.totalDiff.signum() == 0 ^ this.m_C_Charge_ID > 0) {
            this.allocateButton.setEnabled(true);
        }
        else {
            this.allocateButton.setEnabled(false);
        }
        if (this.totalDiff.signum() == 0) {
            this.chargePick.setValue((Object)null);
            this.m_C_Charge_ID = 0;
        }
    }
    
    private void loadBPartner() {
        this.checkBPartner();
        Vector<Vector<Object>> data = this.getPaymentData(this.multiCurrency.isSelected(), this.dateField.getValue(), (IMiniTable)this.paymentTable);
        Vector<String> columnNames = this.getPaymentColumnNames(this.multiCurrency.isSelected());
        this.paymentTable.clear();
        this.paymentTable.getModel().removeTableModelListener((WTableModelListener)this);
        final ListModelTable modelP = new ListModelTable((Collection)data);
        modelP.addTableModelListener((WTableModelListener)this);
        this.paymentTable.setData(modelP, (List)columnNames);
        this.setPaymentColumnClass((IMiniTable)this.paymentTable, this.multiCurrency.isSelected());
        data = this.getInvoiceData(this.multiCurrency.isSelected(), this.dateField.getValue(), (IMiniTable)this.invoiceTable);
        columnNames = this.getInvoiceColumnNames(this.multiCurrency.isSelected());
        this.invoiceTable.clear();
        this.invoiceTable.getModel().removeTableModelListener((WTableModelListener)this);
        final ListModelTable modelI = new ListModelTable((Collection)data);
        modelI.addTableModelListener((WTableModelListener)this);
        this.invoiceTable.setData(modelI, (List)columnNames);
        this.setInvoiceColumnClass((IMiniTable)this.invoiceTable, this.multiCurrency.isSelected());
        this.calculate(this.multiCurrency.isSelected());
        this.calculate();
        this.statusBar.getChildren().clear();
    }
    
    public void calculate() {
        this.allocDate = null;
        this.paymentInfo.setText(this.calculatePayment((IMiniTable)this.paymentTable, this.multiCurrency.isSelected()));
        this.invoiceInfo.setText(this.calculateInvoice((IMiniTable)this.invoiceTable, this.multiCurrency.isSelected()));
        if (this.allocDate != null) {
            this.dateField.setValue((Object)this.allocDate);
        }
        this.allocCurrencyLabel.setText(this.currencyPick.getDisplay());
        this.totalDiff = this.totalPay.subtract(this.totalInv);
        this.differenceField.setText(this.format.format(this.totalDiff));
        this.setAllocateButton();
    }
    
    private MAllocationHdr saveData() {
        if (this.m_AD_Org_ID > 0) {
            Env.setContext(Env.getCtx(), this.form.getWindowNo(), "AD_Org_ID", this.m_AD_Org_ID);
        }
        else {
            Env.setContext(Env.getCtx(), this.form.getWindowNo(), "AD_Org_ID", "");
        }
        try {
            final MAllocationHdr[] allocation = { null };
            Trx.run((TrxRunnable)new TrxRunnable() {
                public void run(final String trxName) {
                    WAllocation.this.statusBar.getChildren().clear();
                    allocation[0] = WAllocation.this.saveData(WAllocation.this.form.getWindowNo(), WAllocation.this.dateField.getValue(), (IMiniTable)WAllocation.this.paymentTable, (IMiniTable)WAllocation.this.invoiceTable, trxName);
                }
            });
            return allocation[0];
        }
        catch (Exception e) {
            Dialog.error(this.form.getWindowNo(), "Error", e.getLocalizedMessage());
            return null;
        }
    }
    
    public ADForm getForm() {
        return (ADForm)this.form;
    }
    
 // $FF: synthetic method
    static Hlayout access$0(WAllocation var0) {
       return var0.statusBar;
    }

    // $FF: synthetic method
    static CustomForm access$1(WAllocation var0) {
       return var0.form;
    }

    // $FF: synthetic method
    static WDateEditor access$2(WAllocation var0) {
       return var0.dateField;
    }

    // $FF: synthetic method
    static WListbox access$3(WAllocation var0) {
       return var0.paymentTable;
    }

    // $FF: synthetic method
    static WListbox access$4(WAllocation var0) {
       return var0.invoiceTable;
    }
}
