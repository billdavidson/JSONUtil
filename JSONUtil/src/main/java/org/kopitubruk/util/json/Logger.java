package org.kopitubruk.util.json;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * class for logging classes to handle logger initialization code.
 *
 * @author Bill Davidson
 */
class Logger
{
    /**
     * The logger.
     */
    private static Log log = null;

    /**
     * Make sure that the logger is initialized.
     *
     * @return The logger
     */
    static synchronized Log getLog()
    {
        if ( log == null ){
            log = LogFactory.getLog(Logger.class.getPackage().getName());
        }
        return log;
    }
}
