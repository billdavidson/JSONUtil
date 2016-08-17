package org.kopitubruk.util.json;

import java.util.HashMap;
import java.util.Map;

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
    private static volatile Map<Class<?>,Log> LOGGERS = null;

    /**
     * Make sure that the logger is initialized.
     * @param clazz The class to create the logger for.
     *
     * @return The logger
     */
    static synchronized Log getLog( Class<?> clazz )
    {
        if ( LOGGERS == null ){
            LOGGERS = new HashMap<Class<?>,Log>();
        }
        Log log = LOGGERS.get(clazz);
        if ( log == null ){
            if ( clazz == null ){
                log = LogFactory.getLog(Logger.class.getPackage().getName());
            }else{
                log = LogFactory.getLog(clazz);
            }
            LOGGERS.put(clazz, log);
        }
        return log;
    }

    /**
     * Free the specified logger.
     *
     * @param clazz The class for the logger.
     */
    static synchronized void freeLog( Class<?> clazz )
    {
        if ( LOGGERS == null ){
            return;
        }
        if ( LOGGERS.remove(clazz) != null && LOGGERS.size() < 1 ){
            LOGGERS = null;
        }
    }
}
