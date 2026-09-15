package org.kjs.pola.component;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.kjs.pola.model.I_C_ARProInvLine;
import org.kjs.pola.model.I_C_ContractLine;
import org.kjs.pola.model.I_C_QuotationLine;
import org.kjs.pola.model.I_KJS_ProductionPlan;
import org.kjs.pola.model.X_C_ARProInvLine;
import org.kjs.pola.model.X_C_ContractLine;
import org.kjs.pola.model.X_C_QuotationLine;
import org.kjs.pola.model.X_KJS_ProductionPlan;
import org.kjs.pola.validator.KJSProductionPlanValidator;
import org.kjs.pola.validator.POLA_ContractLineValidator;
import org.kjs.pola.validator.POLA_ProformaLineValidator;
import org.kjs.pola.validator.POLA_QuotationLineValidator;
import org.osgi.service.event.Event;

/**
 * 
 * @author Tegar N
 *
 */

public class KJSValidatorFactory extends AbstractEventHandler{

	private CLogger log = CLogger.getCLogger(KJSValidatorFactory.class);

	
	@Override
	protected void doHandleEvent(Event event) {
		
		String msg = "";
		
		if (event.getTopic().equals(IEventTopics.AFTER_LOGIN)) {
			/*
			LoginEventData eventData = getEventData(event);
			log.info(" topic="+event.getTopic()+" AD_Client_ID="+eventData.getAD_Client_ID()
					+" AD_Org_ID="+eventData.getAD_Org_ID()+" AD_Role_ID="+eventData.getAD_Role_ID()
					+" AD_User_ID="0+eventData.getAD_User_ID());
			 */
		} 

		else  {
			
			if (getPO(event).get_TableName().equals(I_KJS_ProductionPlan.Table_Name)) {
				msg = KJSProductionPlanValidator.executeKJSProductionPlan(event, getPO(event));
			}else if (getPO(event).get_TableName().equals(I_C_QuotationLine.Table_Name)) {
				msg = POLA_QuotationLineValidator.executeQuotationEvent(event, getPO(event));
			}else if (getPO(event).get_TableName().equals(I_C_ContractLine.Table_Name)) {
				msg = POLA_ContractLineValidator.executeContractEvent(event, getPO(event));
			}else if (getPO(event).get_TableName().equals(I_C_ARProInvLine.Table_Name)) {
				msg = POLA_ProformaLineValidator.executeProformaEvent(event, getPO(event));
			}

			logEvent(event, getPO(event), msg);

		}

	}
	@Override
	protected void initialize() {
		
		registerEvent(IEventTopics.AFTER_LOGIN);
	
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, X_KJS_ProductionPlan.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, X_KJS_ProductionPlan.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, X_KJS_ProductionPlan.Table_Name);
		
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, X_C_QuotationLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, X_C_QuotationLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, X_C_QuotationLine.Table_Name);
		
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, X_C_ContractLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, X_C_ContractLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, X_C_ContractLine.Table_Name);
		
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, X_C_ARProInvLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, X_C_ARProInvLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, X_C_ARProInvLine.Table_Name);
		
		
	
	}
	
	private void logEvent (Event event, PO po, String msg) {
		log.fine("EVENT MANAGER // "+event.getTopic()+" po="+po+" MESSAGE ="+msg);
		if (msg.length()  > 0) 
			throw new AdempiereException(msg);	
	}
	

}
