package org.kjs.pola.component;

import java.util.logging.Level;

import org.adempiere.base.IProcessFactory;
import org.compiere.process.ProcessCall;
import org.compiere.util.CLogger;

/**
 *
 * @author Tegar N
 *
 */

public class KJSProcessFactory implements IProcessFactory {

	private CLogger log = CLogger.getCLogger(KJSProcessFactory.class);

	@Override
	public ProcessCall newProcessInstance(String className) {
		ProcessCall process = null;
		try {
			Class<?> clazz = getClass().getClassLoader().loadClass(className);
			process = (ProcessCall) clazz.getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			// This catch-all factory is consulted for every process className, including
			// ones it does not own; only our own classes failing to load is a real problem
			// worth surfacing (otherwise the failure is hidden and iDempiere falls back to
			// DefaultProcessFactory, which reports a misleading ClassNotFoundException).
			if (className != null && className.startsWith("org.kjs")) {
				log.log(Level.SEVERE, "Failed to load process class: " + className, e);
			}
		}
		return process;
	}

}
