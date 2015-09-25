/*
 * Copyright 2015 Bill Davidson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kopitubruk.util.json;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.text.NumberFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.naming.Context;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <p>
 * This class provides a singleton object which is used to change static
 * defaults used by JSONConfig and it is used as an MBean to allow JMX
 * clients with MBean support to view and modify the defaults.
 * <p>
 * Keep in mind that affects all new JSONConfig objects created in the
 * same class loader, including those created by toJSON methods which
 * don't take a JSONConfig, which could have undesirable side effects depending upon
 * your app. Use with care, if at all.  All data in this class is static.  The
 * only reason that there is an instance or instance methods is to support
 * MBean access to the defaults.  A few defaults are only available
 * programmatically.  Those are accessed statically.
 * <p>
 * It is possible to configure the default values of these flags via JNDI
 * such as is typically available in a web application by adding values to
 * your application's environment under java:/comp/env/org/kopitubruk/util/json.
 * That way you can do things like having all of the validation turned on
 * in development and testing servers and turn it off in production servers
 * for faster performance, without changing any code.
 * <p>
 * Example for Tomcat, assuming your app is named "MyApp", in
 * <code>$CATALINA_BASE/conf/Catalina/<i>host</i>/MyApp.xml</code> in order to
 * disable property name validation:
 * <pre>{@code <Context path="/MyApp">
 *   <Environment name="org/kopitubruk/util/json/validatePropertyNames" type="java.lang.Boolean" value="false" override="false" />
 * </Context>}</pre>
 * <p>
 * These are the names and their normal defaults if you don't change them.
 * See the setters for these for descriptions of what they do.
 * <h3>Validation related options.</h3>
 * <ul>
 *   <li>validatePropertyNames = true</li>
 *   <li>detectDataStructureLoops = true</li>
 *   <li>escapeBadIdentifierCodePoints = false</li>
 * </ul>
 * <h3>Safe alternate encoding options.</h3>
 * <ul>
 *   <li>encodeNumericStringsAsNumbers = false</li>
 *   <li>escapeNonAscii = false</li>
 *   <li>unEscapeWherePossible = false</li>
 *   <li>escapeSurrogates = false</li>
     <li>encodeDatesAsStrings = false</li>
 * </ul>
 * <h3>
 *   Allow generation of certain types of non-standard JSON.
 * </h3>
 * <p>
 *   Could cause problems for some things that take JSON.  Defaults are for
 *   standard JSON.  Be careful about changing these.
 * </p>
 * <ul>
 *   <li>quoteIdentifier = true</li>
 *   <li>useECMA6 = false</li>
 *   <li>allowReservedWordsInIdentifiers = false</li>
 *   <li>encodeDatesAsObjects = false</li>
 * </ul>
 * <p>
 * It is possible to set the default locale in JNDI using the name "locale" and
 * a String value that is a valid locale string that will get passed to
 * {@link Locale#forLanguageTag(String)}.
 * <p>
 * It is possible to see and modify the default values of all of the boolean
 * flags at runtime if you have a JMX client with MBean support connected to
 * your server.  It will be in org.kopitubruk.util.json.JSONConfig.  You can disable
 * MBean registration by setting a boolean variable named registerMBean to false
 * in the environment as shown above for the flags.
 * <p>
 * There is some limited logging for access of JNDI and the MBean server.
 * Most of it is debug, so you won't see it unless you have debug logging
 * enabled for this package/class.  It might be useful to enable debug
 * logging for this class if you are having trouble with those.
 * <p>
 * You can disable JNDI lookups, MBean registration or logging by defining
 * boolean system properties for org.kopitubruk.util.json.useJNDI,
 * org.kopitubruk.util.json.registerMBean or org.kopitubruk.util.json.logging
 * respectively as false.  System properties may be set on the java command
 * line using the "-D" flag or possibly programmatically if your program has
 * permission to do it and does so before this class is loaded.
 *
 * @see JSONConfig
 * @author Bill Davidson
 */
public class JSONConfigDefaults implements JSONConfigDefaultsMBean, Serializable
{
    private static Log s_log = null;

    // Default flag values.
    private static volatile boolean validatePropertyNames;
    private static volatile boolean detectDataStructureLoops;
    private static volatile boolean escapeBadIdentifierCodePoints;

    private static volatile boolean encodeNumericStringsAsNumbers;
    private static volatile boolean escapeNonAscii;
    private static volatile boolean unEscapeWherePossible;
    private static volatile boolean escapeSurrogates;
    private static volatile boolean encodeDatesAsStrings;

    private static volatile boolean quoteIdentifier;
    private static volatile boolean useECMA6;
    private static volatile boolean allowReservedWordsInIdentifiers;
    private static volatile boolean encodeDatesAsObjects;

    // Other defaults.
    private static volatile Locale locale;
    private static volatile Map<Class<? extends Number>,NumberFormat> fmtMap;

    // stored for deregistration on unload.
    private static ObjectName mBeanName = null;

    // the singleton, which has no instance data; only MBean methods.
    private static JSONConfigDefaults jsonConfigDefaults;

    // logging.
    private static boolean logging;

    /*
     * Initialize static data.
     */
    static {
        // the singleton.
        jsonConfigDefaults = new JSONConfigDefaults();

        // initial defaults
        jsonConfigDefaults.setCodeDefaults();

        String pkgName = JSONConfigDefaults.class.getPackage().getName();
        String registerMBeanName = "registerMBean";
        String trueStr = Boolean.TRUE.toString();

        // allow disabling JNDI, MBean and logging from the command line.
        boolean useJNDI = Boolean.parseBoolean(System.getProperty(pkgName+".useJNDI", trueStr));
        boolean registerMBean = Boolean.parseBoolean(System.getProperty(pkgName+'.'+registerMBeanName, trueStr));
        logging = Boolean.parseBoolean(System.getProperty(pkgName+".logging", trueStr));

        ResourceBundle bundle = null;
        if ( useJNDI || registerMBean ){
            bundle = JSONUtil.getBundle(getLocale());
            if ( logging ){
                s_log = LogFactory.getLog(JSONConfigDefaults.class);
            }
        }

        if ( useJNDI ){
            // Look for defaults in JNDI.
            try{
                Context ctx = JNDIUtil.getEnvContext(pkgName.replaceAll("\\.", "/"));

                String localeString = JNDIUtil.getString(ctx, "locale", null);
                if ( localeString != null ){
                    setLocale(Locale.forLanguageTag(localeString));
                    if ( logging ){
                        // possibly changed the locale.  redo the bundle.
                        bundle = JSONUtil.getBundle(getLocale());
                    }
                }

                registerMBean = JNDIUtil.getBoolean(ctx, registerMBeanName, registerMBean);

                validatePropertyNames = JNDIUtil.getBoolean(ctx, "validatePropertyNames", validatePropertyNames);
                detectDataStructureLoops = JNDIUtil.getBoolean(ctx, "detectDataStructureLoops", detectDataStructureLoops);
                escapeBadIdentifierCodePoints = JNDIUtil.getBoolean(ctx, "escapeBadIdentifierCodePoints", escapeBadIdentifierCodePoints);

                encodeNumericStringsAsNumbers = JNDIUtil.getBoolean(ctx, "encodeNumericStringsAsNumbers", encodeNumericStringsAsNumbers);
                escapeNonAscii = JNDIUtil.getBoolean(ctx, "escapeNonAscii", escapeNonAscii);
                unEscapeWherePossible = JNDIUtil.getBoolean(ctx, "unEscapeWherePossible", unEscapeWherePossible);
                escapeSurrogates = JNDIUtil.getBoolean(ctx, "escapeSurrogates", escapeSurrogates);

                quoteIdentifier = JNDIUtil.getBoolean(ctx, "quoteIdentifier", quoteIdentifier);
                useECMA6 = JNDIUtil.getBoolean(ctx, "useECMA6CodePoints", useECMA6);
                allowReservedWordsInIdentifiers = JNDIUtil.getBoolean(ctx, "allowReservedWordsInIdentifiers", allowReservedWordsInIdentifiers);
                jsonConfigDefaults.setEncodeDatesAsObjects(JNDIUtil.getBoolean(ctx, "encodeDatesAsObjects", encodeDatesAsObjects));
                jsonConfigDefaults.setEncodeDatesAsStrings(JNDIUtil.getBoolean(ctx, "encodeDatesAsStrings", encodeDatesAsStrings));
            }catch ( Exception e ){
                // Nothing set in JNDI.  Use code defaults.  Not a problem.
                debug(bundle.getString("badJNDIforConfig"), e);
            }
        }

        if ( registerMBean ){
            // Register an instance with MBean server if one is available.
            try{
                MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

                mBeanName = JNDIUtil.getObjectName(jsonConfigDefaults);
                mBeanServer.registerMBean(jsonConfigDefaults, mBeanName);
                debug(String.format(bundle.getString("registeredMbean"), mBeanName));
            }catch ( Exception e ){
                // No MBean server.  Not a problem.
                debug(bundle.getString("couldntRegisterMBean"), e);
            }
        }
    }

    /**
     * This class should only be instantiated by the static block.  All others
     * should use {@link #getInstance()} to get that instance.
     */
    private JSONConfigDefaults()
    {
    }

    /**
     * Return the JSONConfigDefaults singleton instance.
     *
     * @return the JSONConfigDefaults singleton instance.
     */
    public static JSONConfigDefaults getInstance()
    {
        return jsonConfigDefaults;
    }

    /**
     * <p>
     * When this package is used by a webapp, and you have an MBean server in your
     * environment, then you should create a ServletContextListener and call this
     * in its ServletContextListener.contextDestroyed(ServletContextEvent)
     * method to remove the MBean when the webapp is unloaded or reloaded.
     * </p>
     * <pre><code>
     * public void contextDestroyed( ServletContextEvent sce )
     * {
     *     JSONConfigDefaults.clearMBean();
     * }
     * </code></pre>
     * <p>
     * You should add it to your web.xml for your webapp like this (assuming
     * you named it org.myDomain.web.app.AppCleanUp).
     * </p>
     * <pre>{@code <listener>
     *   <listener-class>org.myDomain.web.app.AppCleanUp</listener-class>
     * </listener>}</pre>
     * <p>
     * Note that the logging from this method may not work if the logging is
     * removed/disabled before this method is called.
     */
    public static synchronized void clearMBean()
    {
        if ( mBeanName != null ){
            ResourceBundle bundle = JSONUtil.getBundle(getLocale());
            try{
                MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
                mBeanServer.unregisterMBean(mBeanName);
                debug(String.format(bundle.getString("unregistered"), mBeanName));
            }catch ( Exception e ){
                error(String.format(bundle.getString("couldntUnregister"), mBeanName), e);
            }finally{
                // don't try again.
                mBeanName = null;
            }
        }
    }

    /**
     * Set a default locale for new JSONConfig objects to use.
     *
     * @return the default locale.
     */
    public static synchronized Locale getLocale()
    {
        return locale != null ? locale : Locale.getDefault();
    }

    /**
     * Set a default locale for new JSONConfig objects to use.
     *
     * @param locale the default locale.
     */
    public static synchronized void setLocale( Locale locale )
    {
        JSONConfigDefaults.locale = locale;
    }

    /**
     * Add a default number format for a particular type that extends Number.
     * This will be applied to all new JSONConfig objects that are created after
     * this in the same class loader.
     *
     * @param numericClass The class.
     * @param fmt The number format.
     */
    public static synchronized void addNumberFormat( Class<? extends Number> numericClass, NumberFormat fmt )
    {
        if ( numericClass != null ){
            if ( fmtMap == null ){
                // don't create the map unless it's actually going to be used.
                fmtMap = new HashMap<>();
            }
            fmtMap.put(numericClass, fmt);
        }
    }

    /**
     * Add a default number format for a particular type that extends Number.
     * This will be applied to all new JSONConfig objects that are created after
     * this in the same class loader.
     *
     * @param numericType The object.
     * @param fmt The number format.
     */
    public static void addNumberFormat( Number numericType, NumberFormat fmt )
    {
        if ( numericType != null ){
            addNumberFormat(numericType.getClass(), fmt);
        }
    }

    /**
     * Get the number format for the given class.
     *
     * @param numericClass A class.
     * @return A number format or null if one has not been set.
     */
    public static synchronized NumberFormat getNumberFormat( Class<? extends Number> numericClass )
    {
        return fmtMap != null ? fmtMap.get(numericClass) : null;
    }

    /**
     * Get the number format for the class of the given numeric type.
     *
     * @param num An object that implements {@link Number}.
     * @return A number format or null if one has not been set.
     */
    public static NumberFormat getNumberFormat( Number num )
    {
        return num != null ? getNumberFormat(num.getClass()) : null;
    }

    /**
     * Remove the requested class from the default number formats.
     *
     * @param numericClass The class.
     */
    public static synchronized void removeNumberFormat( Class<? extends Number> numericClass )
    {
        if ( fmtMap != null && numericClass != null ){
            fmtMap.remove(numericClass);
            if ( fmtMap.size() < 1 ){
                fmtMap = null;
            }
        }
    }

    /**
     * Get the format map.
     *
     * @return the format map.
     */
    static synchronized Map<Class<? extends Number>,NumberFormat> getFormatMap()
    {
        return fmtMap;
    }

    /*
     * The rest are all JSONConfigDefaultsMBean interface methods for usage
     * by MBean clients which is why they have to be instance methods even
     * though they are only dealing with static data.
     */

    /**
     * Reset all defaults to their original unmodified values.  This
     * overrides JNDI and previous MBean changes.
     */
    @Override
    public void setCodeDefaults()
    {
        synchronized ( this.getClass() ){
            validatePropertyNames = true;
            detectDataStructureLoops = true;
            escapeBadIdentifierCodePoints = false;

            encodeNumericStringsAsNumbers = false;
            escapeNonAscii = false;
            unEscapeWherePossible = false;
            escapeSurrogates = false;
            encodeDatesAsStrings = false;

            quoteIdentifier = true;
            useECMA6 = false;
            allowReservedWordsInIdentifiers = false;
            encodeDatesAsObjects = false;

            locale = null;
            fmtMap = null;
        }
    }

    /**
     * Clear any default number formats.  Accessible via MBean server.
     */
    @Override
    public void clearNumberFormats()
    {
        synchronized ( this.getClass() ){
            fmtMap = null;
        }
    }

    /**
     * Get the default validate property names policy.
     * Accessible via MBean server.
     *
     * @return The default validate property names policy.
     */
    @Override
    public boolean isValidatePropertyNames()
    {
        return validatePropertyNames;
    }

    /**
     * Set the default flag for validation of property names.
     * This will affect all new JSONConfig objects created after this call
     * within the same class loader.  Accessible via MBean server.
     *
     * @param dflt If true, then property names will be validated by default.
     */
    @Override
    public void setValidatePropertyNames( boolean dflt )
    {
        validatePropertyNames = dflt;
    }

    /**
     * Get the default detect data structure loops policy.
     * Accessible via MBean server.
     *
     * @return The default detect data structure loops policy.
     */
    @Override
    public boolean isDetectDataStructureLoops()
    {
        return detectDataStructureLoops;
    }

    /**
     * Set the default flag for forcing quotes on identifiers.
     * This will affect all new JSONConfig objects created after this call
     * within the same class loader. Accessible via MBean server.
     *
     * @param dflt If true, then all identifiers will be quoted.
     */
    @Override
    public void setDetectDataStructureLoops( boolean dflt )
    {
         detectDataStructureLoops = dflt;
    }

    /**
     * Get the default escape bad identifier code points policy.
     * Accessible via MBean server.
     *
     * @return The default escape bad identifier code points policy.
     */
    @Override
    public boolean isEscapeBadIdentifierCodePoints()
    {
        return escapeBadIdentifierCodePoints;
    }

    /**
     * If true, then any bad code points in identifiers will be escaped.
     * Default is false.  Accessible via MBean server.
     *
     * @param dflt the escapeBadIdentifierCodePoints to set
     */
    @Override
    public void setEscapeBadIdentifierCodePoints( boolean dflt )
    {
        escapeBadIdentifierCodePoints = dflt;
    }

    /**
     * Get the default encode numeric strings as numbers policy.
     * Accessible via MBean server.
     *
     * @return The default encode numeric strings as numbers policy.
     */
    @Override
    public boolean isEncodeNumericStringsAsNumbers()
    {
        return encodeNumericStringsAsNumbers;
    }

    /**
     * Set the default flag for encoding of numeric strings as numbers.
     * Accessible via MBean server.
     *
     * @param dflt If true, then strings that look like numbers will be encoded
     * as numbers.
     */
    @Override
    public void setEncodeNumericStringsAsNumbers( boolean dflt )
    {
        encodeNumericStringsAsNumbers = dflt;
    }

    /**
     * Get the default quote non-ASCII policy.
     * Accessible via MBean server.
     *
     * @return The default quote non-ASCII policy.
     */
    @Override
    public boolean isEscapeNonAscii()
    {
        return escapeNonAscii;
    }

    /**
     * Set the default flag for forcing escaping of non-ASCII characters.
     * This will affect all new JSONConfig objects created after this call
     * within the same class loader.  Accessible via MBean server.
     *
     * @param dflt If true, then all non-ASCII will be Unicode escaped.
     */
    @Override
    public void setEscapeNonAscii( boolean dflt )
    {
        escapeNonAscii = dflt;
    }

    /**
     * The default unEscape policy.
     * Accessible via MBean server.
     *
     * @return the unEscape policy.
     */
    @Override
    public boolean isUnEscapeWherePossible()
    {
        return unEscapeWherePossible;
    }

    /**
     * Set default flag for undoing inline escapes in strings.
     * Accessible via MBean server.
     *
     * @param dflt If true then where possible, undo inline escapes in strings.
     */
    @Override
    public void setUnEscapeWherePossible( boolean dflt )
    {
        unEscapeWherePossible = dflt;
    }

    /**
     * The default escape surrogates policy.
     *
     * @return the s_defaultEscapeSurrogates
     */
    @Override
    public boolean isEscapeSurrogates()
    {
        return escapeSurrogates;
    }

    /**
     * If true, then surrogates will be escaped.
     *
     * @param dflt the defaultEscapeSurrogates to set
     */
    @Override
    public void setEscapeSurrogates( boolean dflt )
    {
        escapeSurrogates = dflt;
    }

    /**
     * Get the encode dates as strings policy.
     *
     * @return the encodeDatesAsStrings policy.
     */
    @Override
    public boolean isEncodeDatesAsStrings()
    {
        return encodeDatesAsStrings;
    }

    /**
     * If true, then {@link Date} objects will be encoded as
     * ISO 8601 date strings.
     *
     * @param dflt the encodeDatesAsStrings to set
     */
    @Override
    public synchronized void setEncodeDatesAsStrings( boolean dflt )
    {
        encodeDatesAsStrings = dflt;
        if ( encodeDatesAsStrings ){
            encodeDatesAsObjects = false;
        }
    }

    /**
     * Get the default quote identifier policy.
     *
     * @return The default quote identifier policy.
     */
    @Override
    public boolean isQuoteIdentifier()
    {
        return quoteIdentifier;
    }

    /**
     * Set the default flag for forcing quotes on identifiers.
     * This will affect all new JSONConfig objects created after this call
     * within the same class loader. Accessible via MBean server.
     *
     * @param dflt If true, then all identifiers will be quoted.
     */
    @Override
    public void setQuoteIdentifier( boolean dflt )
    {
        quoteIdentifier = dflt;
    }

    /**
     * Get the default escape ECMAScript 6 code points policy.
     * Accessible via MBean server.
     *
     * @return The default escape ECMAScript 6 code points policy.
     */
    @Override
    public boolean isUseECMA6()
    {
        return useECMA6;
    }

    /**
     * Set the default flag for using ECMAScript 6 code points to encode
     * Unicode escapes. Accessible via MBean server.
     *
     * @param dflt if true then ECMAScript 6 code points
     * will be used to encode Unicode escapes as needed.
     */
    @Override
    public void setUseECMA6( boolean dflt )
    {
        useECMA6 = dflt;
    }

    /**
     * Get the default for allowing reserved words in identifiers.
     *
     * @return the defaultAllowReservedWordsInIdentifiers
     */
    @Override
    public boolean isAllowReservedWordsInIdentifiers()
    {
        return allowReservedWordsInIdentifiers;
    }

    /**
     * Set default flag for allowing reserved words in identifiers.
     *
     * @param dflt the defaultAllowReservedWordsInIdentifiers to set
     */
    @Override
    public void setAllowReservedWordsInIdentifiers( boolean dflt )
    {
        allowReservedWordsInIdentifiers = dflt;
    }

    /**
     * Get the encode dates as objects policy.
     *
     * @return the encodeDatesAsObjects policy.
     */
    @Override
    public boolean isEncodeDatesAsObjects()
    {
        return encodeDatesAsObjects;
    }

    /**
     * If true, then {@link Date} objects will be encoded as
     * Javascript dates, using new Date().
     *
     * @param dflt the encodeDatesAsObjects to set
     */
    @Override
    public synchronized void setEncodeDatesAsObjects( boolean dflt )
    {
        encodeDatesAsObjects = dflt;
        if ( encodeDatesAsObjects ){
            encodeDatesAsStrings = false;
        }
    }

    /**
     * Shorthand wrapper around the debug logging to make other code less
     * awkward.
     *
     * @param message the message.
     * @param t the throwable.
     */
    private static void debug( String message, Throwable t )
    {
        if ( logging ){
            s_log.debug(message, t);
        }
    }

    /**
     * Shorthand wrapper around the debug logging to make other code less
     * awkward.
     *
     * @param message the message.
     */
    private static void debug( String message )
    {
        if ( logging ){
            s_log.debug(message);
        }
    }

    /**
     * Shorthand wrapper around the error logging to make other code less
     * awkward.
     *
     * @param message the message.
     * @param t the throwable.
     */
    private static void error( String message, Throwable t )
    {
        if ( logging ){
            s_log.error(message, t);
        }
    }

    @SuppressWarnings("javadoc")
    private static final long serialVersionUID = 1L;
}
