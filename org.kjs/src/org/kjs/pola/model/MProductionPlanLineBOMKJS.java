package org.kjs.pola.model;

import java.sql.ResultSet;
import java.util.Properties;

public class MProductionPlanLineBOMKJS extends X_KJS_ProductionPlanLineBOM{

	private static final long serialVersionUID = 4585399394578316886L;

	public MProductionPlanLineBOMKJS(Properties ctx, int KJS_ProductionPlanLineBOM_ID, String trxName) {
		super(ctx, KJS_ProductionPlanLineBOM_ID, trxName);
	}

	public MProductionPlanLineBOMKJS(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}

}
