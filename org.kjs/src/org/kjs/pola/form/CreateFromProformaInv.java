package org.kjs.pola.form;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.logging.Level;

import org.adempiere.webui.apps.form.WCreateFromWindow;
import org.adempiere.webui.component.ListModelTable;
import org.compiere.apps.IStatusBar;
import org.compiere.grid.CreateFrom;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.GridTab;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.kjs.pola.model.X_C_ARProInv;
import org.kjs.pola.model.X_C_ARProInvLine;
import org.kjs.pola.validator.POLA_ProformaLineValidator;

public class CreateFromProformaInv extends CreateFrom{
	
	protected WCreateFromWindow window;
	protected int p_WindowNo = getGridTab().getWindowNo();

	public CreateFromProformaInv(GridTab gridTab) {
		super(gridTab);
		if (log.isLoggable(Level.INFO)) log.info(gridTab.toString());
	}
	
	public CLogger log = CLogger.getCLogger(CreateFrom.class);


	@Override
	public Object getWindow() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean dynInit() throws Exception {
		log.config("");
		setTitle("Generate Proforma Line From Sales Order");
		return true;
	}

	@Override
	public void info(IMiniTable miniTable, IStatusBar statusBar) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean save(IMiniTable miniTable, String trxName) {
		
		int C_ARProInv_ID = Env.getContextAsInt(Env.getCtx(), p_WindowNo, "C_ARProInv_ID");
		X_C_ARProInv Inv = new X_C_ARProInv(Env.getCtx(), C_ARProInv_ID, trxName);

		ArrayList<Integer> selectedIds = new ArrayList<Integer>();
		HashMap<Integer, BigDecimal> qtyByOrderLine = new HashMap<Integer, BigDecimal>();
		for (int i = 0; i < miniTable.getRowCount(); i++) {
			if (((Boolean)miniTable.getValueAt(i, 0)).booleanValue()) {
				KeyNamePair OrderLinepair = (KeyNamePair) miniTable.getValueAt(i, 1);
				Integer C_OrderLine_ID = Integer.valueOf(OrderLinepair.getKey());
				selectedIds.add(C_OrderLine_ID);
				qtyByOrderLine.put(C_OrderLine_ID, (BigDecimal) miniTable.getValueAt(i, 4));
			}
		}
		if (selectedIds.isEmpty()) {
			return true;
		}

		StringBuilder sql = new StringBuilder();
		sql.append("SELECT ol.C_OrderLine_ID, ol.C_Order_ID, ol.AD_Org_ID, ol.C_Charge_ID, ol.M_Product_ID,");
		sql.append(" COALESCE(p.C_UOM_ID, ol.C_UOM_ID), ol.Line, ol.C_Tax_ID, ol.PriceEntered");
		sql.append(" FROM C_OrderLine ol");
		sql.append(" LEFT JOIN M_Product p ON p.M_Product_ID = ol.M_Product_ID");
		sql.append(" WHERE ol.C_OrderLine_ID IN (");
		for (int i = 0; i < selectedIds.size(); i++) {
			if (i > 0)
				sql.append(",");
			sql.append("?");
		}
		sql.append(")");

		int C_Order_ID = 0;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			POLA_ProformaLineValidator.setSkipHeaderRecompute(true);
			pstmt = DB.prepareStatement(sql.toString(), trxName);
			for (int i = 0; i < selectedIds.size(); i++) {
				pstmt.setInt(i + 1, selectedIds.get(i).intValue());
			}
			rs = pstmt.executeQuery();
			while (rs.next()) {
				int C_OrderLine_ID = rs.getInt(1);
				C_Order_ID = rs.getInt(2);
				int AD_Org_ID = rs.getInt(3);
				int C_Charge_ID = rs.getInt(4);
				int M_Product_ID = rs.getInt(5);
				int C_UOM_ID = rs.getInt(6);
				int lineNo = rs.getInt(7);
				int C_Tax_ID = rs.getInt(8);
				BigDecimal PriceEntered = rs.getBigDecimal(9);
				BigDecimal QtyOrdered = qtyByOrderLine.get(Integer.valueOf(C_OrderLine_ID));
				if (QtyOrdered == null)
					QtyOrdered = Env.ZERO;
				if (PriceEntered == null)
					PriceEntered = Env.ZERO;

				X_C_ARProInvLine invLine = new X_C_ARProInvLine(Env.getCtx(), 0, trxName);
				invLine.setAD_Org_ID(AD_Org_ID);
				invLine.setC_ARProInv_ID(Inv.getC_ARProInv_ID());

				if (C_Charge_ID > 0) {
					invLine.setC_Charge_ID(C_Charge_ID);
				}

				if (M_Product_ID > 0) {
					invLine.setM_Product_ID(M_Product_ID);
					invLine.setC_UOM_ID(C_UOM_ID);
				}

				invLine.setLine(lineNo);
				invLine.setC_Tax_ID(C_Tax_ID);
				invLine.setC_OrderLine_ID(C_OrderLine_ID);
				invLine.setQtyEntered(QtyOrdered);
				invLine.setPriceEntered(PriceEntered);
				invLine.setPriceActual(PriceEntered);
				invLine.setLineNetAmt(PriceEntered.multiply(QtyOrdered));
				invLine.saveEx();
			}
		} catch (SQLException e) {
			log.log(Level.SEVERE, sql.toString(), e);
			return false;
		} finally {
			POLA_ProformaLineValidator.setSkipHeaderRecompute(false);
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}

		POLA_ProformaLineValidator.recomputeHeader(Env.getCtx(), C_ARProInv_ID, trxName, 0);

		if (Inv.get_ValueAsInt("C_Order_ID") <= 0 && C_Order_ID > 0) {
			Inv.set_CustomColumn("C_Order_ID", C_Order_ID);
			Inv.saveEx();
		}

		return true;
	}
	
	protected Vector<Vector<Object>> getSODetailData (int C_Order_ID)
	{
		
		return getSODetail (C_Order_ID);
	}
	
	protected Vector<Vector<Object>> getSODetail (int C_Order_ID)
	{
		/**
		 *  Selected        	- 0
		 *  Line             	- 1
		 *  M_Product_ID        - 2
		 *  C_UOM_ID    		- 3
		 *  Qty    				- 4
		 *  PriceActual 		- 5
		 *  LineNetAmt       	- 6
		
		 */
		if (log.isLoggable(Level.CONFIG)) log.config("C_Order_ID=" + C_Order_ID);

		Vector<Vector<Object>> data = new Vector<Vector<Object>>();
		StringBuilder SQLGetPRDetail = new StringBuilder();
		SQLGetPRDetail.append("SELECT ol.Line, ol.M_Product_ID, COALESCE(p.Name, ch.Name),");
		SQLGetPRDetail.append(" ol.C_UOM_ID, u.UOMSymbol, ol.QtyOrdered, ol.PriceActual, ol.C_OrderLine_ID");
		SQLGetPRDetail.append(" FROM C_OrderLine ol");
		SQLGetPRDetail.append(" LEFT JOIN M_Product p ON p.M_Product_ID = ol.M_Product_ID");
		SQLGetPRDetail.append(" LEFT JOIN C_Charge ch ON ch.C_Charge_ID = ol.C_Charge_ID");
		SQLGetPRDetail.append(" LEFT JOIN C_UOM u ON u.C_UOM_ID = ol.C_UOM_ID");
		SQLGetPRDetail.append(" WHERE ol.C_Order_ID = ?");
		SQLGetPRDetail.append(" ORDER BY ol.Line");
		
		
		if (log.isLoggable(Level.FINER)) log.finer(SQLGetPRDetail.toString());
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(SQLGetPRDetail.toString(), null);
			pstmt.setInt(1, C_Order_ID);
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				Vector<Object> line = new Vector<Object>();
				line.add(Boolean.FALSE);           //  0-Selection
				Integer lineNo = Integer.valueOf(rs.getInt(1));
				BigDecimal qty = rs.getBigDecimal(6);
				BigDecimal price = rs.getBigDecimal(7);
				
				
				KeyNamePair LinePair = new KeyNamePair(rs.getInt(8), lineNo.toString());
				line.add(LinePair);  																//  1-Line
				String prodName = rs.getString(3);
				if (prodName == null)
					prodName = "";
				KeyNamePair ProdPair = new KeyNamePair(rs.getInt(2), prodName);
				line.add(ProdPair);                           										//  2-Product
				String uomSymbol = rs.getString(5);
				if (uomSymbol == null)
					uomSymbol = "";
				KeyNamePair UOMPair = new KeyNamePair(rs.getInt(4), uomSymbol);
				line.add(UOMPair);                          					 					//  3-uom	
				line.add(qty);																		//  4-qty
				line.add(price);                           											//  5-price
			
				data.add(line);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, SQLGetPRDetail.toString(), e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null; pstmt = null;
		}
		return data;
	}   //  LoadOrder
	
	protected Vector<String> getOISColumnNames()
	{
		//  Header Info
	    Vector<String> columnNames = new Vector<String>(7);
	    columnNames.add(Msg.getMsg(Env.getCtx(), "Select"));
	    columnNames.add(Msg.translate(Env.getCtx(), "Line"));
	    columnNames.add("Product Name");
	    columnNames.add("Units of Measure(UOM)");
	    columnNames.add("Qty Ordered");
	    columnNames.add("Unit Product Price");
	  
	    return columnNames;
	}
	
	protected void configureMiniTable (IMiniTable miniTable)
	{
		miniTable.setColumnClass(0, Boolean.class, false);     			//  Selection
		miniTable.setColumnClass(1, KeyNamePair.class, true);     		//  Line
		miniTable.setColumnClass(2, KeyNamePair.class, true);          	//  product
		miniTable.setColumnClass(3, KeyNamePair.class, true);  			//  uom
		miniTable.setColumnClass(4, BigDecimal.class, false);   		//  qty ordered
		miniTable.setColumnClass(5, BigDecimal.class, true); 			//  price actual
	
		//  Table UI
		miniTable.autoSize();
		
	}
	
	protected void loadTableOIS (Vector<?> data)
	{
		window.getWListbox().clear();
		
		//  Remove previous listeners
		window.getWListbox().getModel().removeTableModelListener(window);
		//  Set Model
		ListModelTable model = new ListModelTable(data);
		model.addTableModelListener(window);
		window.getWListbox().setData(model, getOISColumnNames());
		//
		
		configureMiniTable(window.getWListbox());
	}

}
