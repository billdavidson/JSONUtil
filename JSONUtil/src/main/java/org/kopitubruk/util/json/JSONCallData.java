/**
 * 
 */
package org.kopitubruk.util.json;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * This object holds data used in a call to
 * {@link JSONUtil#toJSON(Object, JSONConfig, java.io.Writer)}.
 * <p>
 * The stack used to be in JSONConfig. That was a bad place for it because it
 * created thread conflict problems. Moving it out of the config object also
 * removes the risk of the stack not being cleared after an exception, since
 * objects of this class are not reused. This allowed all stack clearing code to
 * be removed, making that code simpler.
 *
 * @author Bill Davidson
 * @since 1.3
 */
class JSONCallData
{
    static TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone("UTC");

    /**
     * The config object.
     */
    private JSONConfig cfg;

    /**
     * Used by JSONUtil to detect data structure loops.
     */
    private List<Object> objStack;
    
    /**
     * A date formatter when using encodeDates.
     */
    private DateFormat dateFormatter = null;
    private DateFormat[] dateFormatters = null;

    /**
     * Create a JSONCallData object.
     */
    JSONCallData( JSONConfig cfg )
    {
        this.cfg = cfg;
        objStack = cfg.isDetectDataStructureLoops() ? new ArrayList<Object>() : null;
    }

    /**
     * Get the object stack. Used only by JSONUtil for data structure loop
     * detection.
     *
     * @return the objStack
     */
    List<Object> getObjStack()
    {
        return objStack;
    }

    /**
     * Get a date formatter that uses ISO 8601 Extended Format as is needed for
     * ECMAScript data string compliance.
     * <p>
     * {@link SimpleDateFormat} is not thread safe so can't use one for the
     * entire library.  Do one per toJSON() call instead.
     *
     * @return The formatter.
     */
    DateFormat getDateFormatter()
    {
        if ( dateFormatter == null ){
            // don't create it until it's needed.
            dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'");
            dateFormatter.setTimeZone(UTC_TIME_ZONE);
        }
        return dateFormatter;
    }

    /**
     * Get the parsing date formatters to parse different accepted
     * forms of ISO 8601.
     * 
     * @return The array of formatters.
     */
    DateFormat[] getDateFormatters()
    {
        if ( dateFormatters == null ){
            // don't create it until it's needed.
            dateFormatters = new DateFormat[4];
            dateFormatters[0] = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZ");
            dateFormatters[1] = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
            dateFormatters[2] = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZ");
            dateFormatters[3] = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            for ( DateFormat fmt : dateFormatters ){
                fmt.setTimeZone(UTC_TIME_ZONE);
            }
        }
        return dateFormatters;
    }

    /**
     * Get the config object.
     *
     * @return the config object.
     */
    JSONConfig getJSONConfig()
    {
        return cfg;
    }
}
