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
 * same class loader, which could have undesirable side effects depending upon
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
 * Example for Tomcat in <code>$CATALINA_HOME/conf/Catalina/<i>host</i>/MyApp.xml</code>
 * in order to disable property name validation:
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
 * </ul>
 * <h3>
 *   Allow generation of certain types of non-standard JSON.  Could
 *   cause problems for some things that take JSON.  Defaults are for
 *   standard JSON.  Be careful about changing these.
 * </h3>
 * <ul>
 *   <li>quoteIdentifier = true</li>
 *   <li>useECMA6CodePoints = false</li>
 *   <li>allowReservedWordsInIdentifiers = false</li>
 * </ul>
 * <p>
 * It is possible to set the default locale using the name "locale" and
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
 * line using the "-D" flag or possibly programatically if your program has
 * permission to do it and does so before this class is loaded.
 *
 * @see Locale
 * @see JSONConfig
 * @author Bill Davidson
 */
public class JSONConfigDefaults implements JSONConfigDefaultsMBean, Serializable
{
    //private static Log s_log = LogFactory.getLog(JSONConfigDefaults.class);

    // Default flag values.
    private static volatile boolean validatePropertyNames;
    private static volatile boolean detectDataStructureLoops;
    private static volatile boolean escapeBadIdentifierCodePoints;

    private static volatile boolean encodeNumericStringsAsNumbers;
    private static volatile boolean escapeNonAscii;
    private static volatile boolean unEscapeWherePossible;
    private static volatile boolean escapeSurrogates;

    private static volatile boolean quoteIdentifier;
    private static volatile boolean useECMA6CodePoints;
    private static volatile boolean allowReservedWordsInIdentifiers;

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
        validatePropertyNames = true;
        detectDataStructureLoops = true;
        escapeBadIdentifierCodePoints = false;

        encodeNumericStringsAsNumbers = false;
        escapeNonAscii = false;
        unEscapeWherePossible = false;
        escapeSurrogates = false;

        quoteIdentifier = true;
        useECMA6CodePoints = false;
        allowReservedWordsInIdentifiers = false;

        locale = null;
        fmtMap = null;

        String pkgName = JSONConfig.class.getPackage().getName();
        String registerMBeanName = "registerMBean";
        String trueStr = Boolean.TRUE.toString();

        // allow disabling JNDI, MBean and logging from the command line.
        boolean useJNDI = Boolean.parseBoolean(System.getProperty(pkgName+".useJNDI", trueStr));
        boolean registerMBean = Boolean.parseBoolean(System.getProperty(pkgName+'.'+registerMBeanName, trueStr));
        logging = Boolean.parseBoolean(System.getProperty(pkgName+'.'+".logging", trueStr));
        logging = false;

        //if ( logging ){
        //    s_log = LogFactory.getLog(JSONConfigDefaults.class);
        //}

        if ( useJNDI ){
            // Look for defaults in JNDI.
            try{
                Context ctx = JNDIUtil.getEnvContext(pkgName.replaceAll("\\.", "/"));
                registerMBean = JNDIUtil.getBoolean(ctx, registerMBeanName, registerMBean);

                validatePropertyNames = JNDIUtil.getBoolean(ctx, "validatePropertyNames", validatePropertyNames);
                detectDataStructureLoops = JNDIUtil.getBoolean(ctx, "detectDataStructureLoops", detectDataStructureLoops);
                escapeBadIdentifierCodePoints = JNDIUtil.getBoolean(ctx, "escapeBadIdentifierCodePoints", escapeBadIdentifierCodePoints);

                encodeNumericStringsAsNumbers = JNDIUtil.getBoolean(ctx, "encodeNumericStringsAsNumbers", encodeNumericStringsAsNumbers);
                escapeNonAscii = JNDIUtil.getBoolean(ctx, "escapeNonAscii", escapeNonAscii);
                unEscapeWherePossible = JNDIUtil.getBoolean(ctx, "unEscapeWherePossible", unEscapeWherePossible);
                escapeSurrogates = JNDIUtil.getBoolean(ctx, "escapeSurrogates", escapeSurrogates);

                quoteIdentifier = JNDIUtil.getBoolean(ctx, "quoteIdentifier", quoteIdentifier);
                useECMA6CodePoints = JNDIUtil.getBoolean(ctx, "useECMA6CodePoints", useECMA6CodePoints);
                allowReservedWordsInIdentifiers = JNDIUtil.getBoolean(ctx, "allowReservedWordsInIdentifiers", allowReservedWordsInIdentifiers);

                String localeString = JNDIUtil.getString(ctx, "locale", null);
                if ( localeString != null ){
                    String[] loc = localeString.split("[-_]");
                    switch ( loc.length ){
                        case 1: locale = new Locale(loc[0]); break;
                        case 2: locale = new Locale(loc[0], loc[1]); break;
                        default: locale = new Locale(loc[0], loc[1], loc[2]); break;
                    }
                    //locale = Locale.forLanguageTag(localeString); // Java 7
                }
            }catch ( Exception e ){
                // Nothing set in JNDI.  Use code defaults.  Not a problem.
                ResourceBundle bundle = JSONUtil.getBundle(getLocale());
                if ( logging ){
                    //s_log.debug(bundle.getString("badJNDIforConfig"), e);
                }
            }
        }

        if ( registerMBean ){
            // Register an instance with MBean server if one is available.
            ResourceBundle bundle = JSONUtil.getBundle(getLocale());
            try{
                MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

                mBeanName = JNDIUtil.getObjectName(jsonConfigDefaults);
                mBeanServer.registerMBean(jsonConfigDefaults, mBeanName);

                //s_log.debug(String.format(bundle.getString("registeredMbean"), mBeanName));
            }catch ( Exception e ){
                // No MBean server.  Not a problem.
                if ( logging ){
                    //s_log.debug(bundle.getString("couldntRegisterMBean"), e);
                }
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
     * Return the JSONConfigDefaults instance.
     *
     * @return the JSONConfigDefaults instance.
     */
    public static JSONConfigDefaults getInstance()
    {
        return jsonConfigDefaults;
    }

    /**
     * <p>
     * When this package is used by webapps, and you have an MBean server in your
     * environment, then you should create a ServletContextListener and call this
     * in its ServletContextListener.contextDestroyed(ServletContextEvent)
     * method to remove the MBean when the webapp is unloaded or reloaded.
     * </p>
     * <pre><code>
     * public void contextDestroyed( ServletContextEvent sce )
     * {
     *     DefaultJsonConfig.clearMBean();
     * }
     * </code></pre>
     * <p>
     * You should add it to your web.xml for your app like this (assuming
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
                if ( logging ){
                    //s_log.debug(String.format(bundle.getString("unregistered"), mBeanName));
                }
            }catch ( Exception e ){
                if ( logging ){
                    //s_log.error(String.format(bundle.getString("couldntUnregister"), mBeanName), e);
                }
            }finally{
                // don't try again.
                mBeanName = null;
            }
        }
    }

    static boolean getLogging()
    {
        return logging;
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
                fmtMap = new HashMap<Class<? extends Number>,NumberFormat>();
            }
            fmtMap.put(numericClass, fmt);
        }
    }

    /**
     * Remove the requested class from the default number formats.
     *
     * @param numericClass The class.
     */
    public static synchronized void removeNumberFormat( Class<? extends Number> numericClass )
    {
        if ( fmtMap != null ){
            fmtMap.remove(numericClass);
            if ( fmtMap.size() < 1 ){
                fmtMap = null;
            }
        }
    }

    /**
     * @return
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
     * Clear any default number formats.  Accessible via MBean server.
     */
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
    public boolean isEscapeBadIdentifierCodePoints()
    {
        return escapeBadIdentifierCodePoints;
    }

    /**
     * If true, then any bad code points in identifiers will be quoted.
     * Default is false.  Accessible via MBean server.
     *
     * @param dflt the escapeBadIdentifierCodePoints to set
     */
    
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
    
    public boolean isEncodeNumericStringsAsNumbers()
    {
        return encodeNumericStringsAsNumbers;
    }

    /**
     * Set the default flag for encoding of numeric strings as numbers.
     * This will affect all new JSONConfig objects created after this call
     * within the same class loader.  Accessible via MBean server.
     *
     * @param dflt If true, then strings that look like numbers will be encoded
     * as numbers.
     */
    
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
    
    public void setUnEscapeWherePossible( boolean dflt )
    {
        unEscapeWherePossible = dflt;
    }

    /**
     * The default escape surrogates policy.
     *
     * @return the s_defaultEscapeSurrogates
     */
    
    public boolean isEscapeSurrogates()
    {
        return escapeSurrogates;
    }

    /**
     * If true, then surrogates will be escaped.
     *
     * @param dflt the defaultEscapeSurrogates to set
     */
    
    public void setEscapeSurrogates( boolean dflt )
    {
        escapeSurrogates = dflt;
    }

    /**
     * Get the default quote identifier policy.
     *
     * @return The default quote identifier policy.
     */
    
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
    
    public void setQuoteIdentifier( boolean dflt )
    {
        quoteIdentifier = dflt;
    }

    /**
     * Get the default escape ECMA 6 code points policy.
     * Accessible via MBean server.
     *
     * @return The default escape ECMA 6 code points policy.
     */
    
    public boolean isUseECMA6CodePoints()
    {
        return useECMA6CodePoints;
    }

    /**
     * Set the default flag for using ECMA 6 code points to encode
     * Unicode escapes. Accessible via MBean server.
     *
     * @param dflt if true then ECMA 6 code points
     * will be used to encode Unicode escapes as needed.
     */
    
    public void setUseECMA6CodePoints( boolean dflt )
    {
        useECMA6CodePoints = dflt;
    }

    /**
     * Get the default for allowing reserved words in identifiers.
     *
     * @return the defaultAllowReservedWordsInIdentifiers
     */
    
    public boolean isAllowReservedWordsInIdentifiers()
    {
        return allowReservedWordsInIdentifiers;
    }

    /**
     * Set default flag for allowing reserved words in identifiers.
     *
     * @param dflt the defaultAllowReservedWordsInIdentifiers to set
     */
    
    public void setAllowReservedWordsInIdentifiers( boolean dflt )
    {
        allowReservedWordsInIdentifiers = dflt;
    }

    @SuppressWarnings("javadoc")
    private static final long serialVersionUID = 1L;
}
