package org.kjs.pola.component;

import java.util.logging.Level;

import org.adempiere.webui.factory.IFormFactory;
import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.IFormController;
import org.compiere.util.CLogger;
import org.kjs.pola.form.KJSWAllocation;

public class KJSFormFactory implements IFormFactory
{
    private static final CLogger log;
    
    static {
        log = CLogger.getCLogger(KJSFormFactory.class);
    }
    
    public ADForm newFormInstance(final String formName) {
        // Payment Allocation: core form + a read-only Date Collect column on the Invoice grid.
        if (formName.equals("org.compiere.apps.form.VAllocation")) {
            final KJSWAllocation controller = new KJSWAllocation();
            final ADForm adForm = controller.getForm();
            adForm.setICustomForm((IFormController)controller);
            return adForm;
        }
        if (formName.startsWith("org.kjs.jembo.form")) {
            Object form = null;
            Class<?> clazz = null;
            final ClassLoader loader = this.getClass().getClassLoader();
            try {
                clazz = loader.loadClass(formName);
            }
            catch (Exception e) {
                KJSFormFactory.log.log(Level.FINE, "Load form class failed in org.kjs.jembo.form", (Throwable)e);
            }
            if (clazz != null) {
                try {
                	 form = clazz.getDeclaredConstructor().newInstance();
                }
                catch (Exception e) {
                    KJSFormFactory.log.log(Level.FINE, "Form Class Initiated failed in  org.kjs.jembo.form", (Throwable)e);
                }
            }
            if (form != null) {
                if (form instanceof ADForm) {
                    return (ADForm)form;
                }
                if (form instanceof IFormController) {
                    final IFormController controller = (IFormController)form;
                    final ADForm adForm = controller.getForm();
                    adForm.setICustomForm(controller);
                    return adForm;
                }
            }
        }
        return null;
    }
}
