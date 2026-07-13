package org.kjs.pola.model;

import java.sql.ResultSet;
import java.util.List;
import java.util.Properties;

import org.compiere.model.Query;
import org.compiere.util.Util;

public class MProductionPlanLineKJS extends X_KJS_ProductionPlanLine{

	public MProductionPlanLineKJS(Properties ctx, int KJS_ProductionPlanLine_ID, String trxName) {
		super(ctx, KJS_ProductionPlanLine_ID, trxName);
	}
	public MProductionPlanLineKJS(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}

	private static final long serialVersionUID = -294075929524578670L;


    
	public MProductionPlanLineBOMKJS[] getLines (String whereClause, String orderClause)
	{
		StringBuilder whereClauseFinal = new StringBuilder(MProductionPlanLineBOMKJS.COLUMNNAME_KJS_ProductionPlanLine_ID+"=? ");
		if (!Util.isEmpty(whereClause, true))
			whereClauseFinal.append(whereClause);
		if (orderClause.length() == 0)
			orderClause = MProductionPlanLineBOMKJS.COLUMNNAME_Line;
		//
		List<MProductionPlanLineBOMKJS> list = new Query(getCtx(), MProductionPlanLineBOMKJS.Table_Name, whereClauseFinal.toString(), get_TrxName())
										.setParameters(get_ID())
										.setOrderBy(orderClause)
										.list();
		//
		return list.toArray(new MProductionPlanLineBOMKJS[list.size()]);		
	}	//	getLines
}
