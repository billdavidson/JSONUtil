/*
 * Copyright 2015-2016 Bill Davidson
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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.naming.Context;

import org.apache.commons.logging.Log;

/**
 * <p>
 * This class provides a singleton object which is used to change static
 * defaults used by {@link JSONConfig} and it is used as an MBean to
 * allow JMX clients with MBean support to view and modify the defaults.
 * <p>
 * Keep in mind that changes made with this class affect all new
 * {@link JSONConfig} objects created in the same class loader,
 * including those created by toJSON() methods which don't take a
 * {@link JSONConfig}, which could have undesirable side effects depending
 * upon your app. Use with care, if at all.  All data in this class
 * is static.  The only reason that there is an instance or instance
 * methods is to support MBean access to the defaults.  A few defaults
 * are only available programmatically.  Those are accessed statically.
 * <p>
 * It is possible to configure most of the default values
 * via JNDI such as is typically available in a web application
 * by adding values to your application's environment under
 * java:/comp/env/org/kopitubruk/util/json.  That way you can do things
 * like having all of the validation turned on in development and testing
 * servers and turn it off in production servers for faster performance,
 * without changing any code.
 * <p>
 * Example for Tomcat, assuming your app is named "MyApp", and your host
 * is named "host", put this into your
 * <code>$CATALINA_BASE/conf/Catalina/<i>host</i>/MyApp.xml</code> file in
 * order to set the appName to "myApp", disable property name validation
 * and enable using full JSON identifier code points by default:
 * <pre>{@code <Context path="/MyApp">
 *   <Environment name="org/kopitubruk/util/json/appName" type="java.lang.String" value="MyApp" override="false" />
 *   <Environment name="org/kopitubruk/util/json/validatePropertyNames" type="java.lang.Boolean" value="false" override="false" />
 *   <Environment name="org/kopitubruk/util/json/fullJSONIdentifierCodePoints" type="java.lang.Boolean" value="true" override="false" />
 * </Context>}</pre>
 * <p>
 * These are the names and their normal defaults if you don't change them.
 * See their setters for these for descriptions of what they do.
 * <h3>Validation related options.</h3>
 * <ul>
 *   <li>validatePropertyNames = true</li>
 *   <li>detectDataStructureLoops = true</li>
 *   <li>escapeBadIdentifierCodePoints = false</li>
 *   <li>fullJSONIdentifierCodePoints = false</li>
 * </ul>
 * <h3>Safe alternate encoding options.</h3>
 * <ul>
 *   <li>encodeNumericStringsAsNumbers = false</li>
 *   <li>escapeNonAscii = false</li>
 *   <li>unEscapeWherePossible = false</li>
 *   <li>escapeSurrogates = false</li>
 *   <li>passThroughEscapes = false</li>
 *   <li>encodeDatesAsStrings = false</li>
 *   <li>reflectUnknownObjects = false</li>
 *   <li>preciseIntegers = false</li>
 *   <li>smallNumbers = false</li>
 *   <li>usePrimitiveArrays = false</li>
 *   <li>cacheReflectionData = false</li>
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
 * {@link Locale#forLanguageTag(String)}.  If you don't do this, then the
 * default locale will be whatever is provided by the JVM.
 * <p>
 * You can set the default date generation format in JNDI using the name
 * "dateGenFormat" and a String value that will be passed to
 * {@link SimpleDateFormat#SimpleDateFormat(String, Locale)} using the locale
 * from {@link #getLocale()}.
 * <p>
 * You can set any number of default date parsing formats using names that start
 * with "dateParseFormat" followed by some positive base 10 integer (as in
 * "dateParseFormat7") using String values as with "dateGenFormat" described
 * above.  They will be added in numeric order of the name.
 * <p>
 * Classes to use for automatic reflection can be set up via JNDI. This class
 * will look for String variables named "reflectClass" followed by some positive
 * base 10 integer (like "reflectClass5").  You can have any number of
 * reflectClasses. The names merely need to be unique and follow that pattern.
 * The class names need to load with {@link ClassLoader#loadClass(String)} or
 * they will not be added to the list of reflected classes.  If the class
 * names are followed by a comma and strings separated by commas, those
 * strings will be taken as field names to use for {@link JSONReflectedClass}.
 * Field names which do not look like valid Java identifier names will be
 * silently discarded.
 * These classes will be added to all JSONConfig objects that are created
 * in the same class loader.
 * <p>
 * Number formats and date formats are cloned when they are added because they
 * are not thread safe.  They are cloned again when applied to a new
 * {@link JSONConfig} for the same reason.  Once you add a format, you can't
 * modify it except by replacing it entirely.
 * <p>
 * It is possible to see and modify the default values of all
 * of the boolean flags at runtime if you have a JMX client
 * with MBean support connected to your server.  It will be in
 * org.kopitubruk.util.json.JSONConfigDefaults.  You can disable MBean
 * registration by setting a boolean variable named registerMBean to
 * false in the environment as shown above for the flags.  See also the
 * {@link #clearMBean()} method for information on how to remove the
 * MBean from your server when your app is unloaded or reloaded.
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
 * permission to do it and does so before this class is loaded.  If logging
 * is not set via a system property at all, then JDNI can also be used to
 * disable logging by setting the boolean named "org/kopitubruk/util/json/logging".
 * <p>
 * You can also set the appName on the command line if you didn't set it in
 * JNDI as in "-Dorg.kopitubruk.util.json.appName=MyApp".  The appName is used
 * in the MBean ObjectName and is recommended when this library is used with
 * multiple apps in the same web tier container because it allows you to have
 * different MBeans for different apps at the same time.  The appName may also
 * be set via JNDI as a String and that's probably a better way to do it.
 *
 * @see JSONConfig
 * @author Bill Davidson
 */
public class JSONConfigDefaults implements JSONConfigDefaultsMBean, Serializable
{
    // Default flag values.
    private static volatile boolean validatePropertyNames;
    private static volatile boolean detectDataStructureLoops;
    private static volatile boolean escapeBadIdentifierCodePoints;
    private static volatile boolean fullJSONIdentifierCodePoints;

    private static volatile boolean encodeNumericStringsAsNumbers;
    private static volatile boolean escapeNonAscii;
    private static volatile boolean unEscapeWherePossible;
    private static volatile boolean escapeSurrogates;
    private static volatile boolean passThroughEscapes;
    private static volatile boolean encodeDatesAsStrings;
    private static volatile boolean reflectUnknownObjects;
    private static volatile boolean preciseNumbers;
    private static volatile boolean smallNumbers;
    private static volatile boolean usePrimitiveArrays;
    private static volatile boolean cacheReflectionData;

    private static volatile boolean quoteIdentifier;
    private static volatile boolean useECMA6;
    private static volatile boolean allowReservedWordsInIdentifiers;
    private static volatile boolean encodeDatesAsObjects;

    // Other defaults.
    private static volatile Locale locale;
    private static volatile Map<Class<? extends Number>,NumberFormat> numberFormatMap;
    private static volatile DateFormat dateGenFormat;
    private static volatile List<DateFormat> dateParseFormats;
    private static volatile IndentPadding indentPadding = null;
    private static volatile Map<Class<?>,JSONReflectedClass> reflectClasses = null;
    private static volatile int reflectionPrivacy;

    // stored for deregistration on unload.
    private static ObjectName mBeanName = null;

    // the singleton, which has no instance data; only MBean methods.
    private static JSONConfigDefaults jsonConfigDefaults;

    // logging.
    private static boolean logging;

    // patterns for JNDI variable names.
    private static final Pattern DATE_PARSE_FORMAT_PAT = Pattern.compile("^dateParseFormat(\\d+)$");
    private static final Pattern REFLECT_CLASS_PAT = Pattern.compile("^reflectClass\\d+$");

    /*
     * Initialize static data.
     */
    static {
        /*
         * The singleton.
         */
        jsonConfigDefaults = new JSONConfigDefaults();

        String pkgName = jsonConfigDefaults.getClass().getPackage().getName();

        try{
            /*
             * System properties.
             */
            String appName = System.getProperty(pkgName+'.'+"appName", null);
            boolean useJNDI = Boolean.parseBoolean(System.getProperty(pkgName+".useJNDI", "true"));
            boolean registerMBean = Boolean.parseBoolean(System.getProperty(pkgName+'.'+"registerMBean", "true"));

            String loggingProperty = System.getProperty(pkgName+'.'+"logging");
            if ( loggingProperty != null ){
                logging = Boolean.parseBoolean(loggingProperty);
            }else{
                logging = true;
            }

            /*
             * JNDI
             */
            if ( useJNDI ){
                Map<String,String> results = new HashMap<>();
                results.put("appName", appName);
                registerMBean = initJNDI(loggingProperty, registerMBean, appName, results);
                appName = results.get("appName");
            }

            /*
             * MBean
             */
            if ( registerMBean ){
                initMBean(appName);
            }
        }finally{
            Logger.freeLog(jsonConfigDefaults.getClass());
        }
    }

    /**
     * Do JNDI Initializations of defaults.
     *
     * @param loggingProperty System property for logging flag.
     * @param registerMBean boolean system property for registering the mbean.
     * @param appName the appName if any
     * @param results a map to hold additional results
     * @return the value of registerMBean which might have changed during the JNDI lookups.
     */
    private static boolean initJNDI( String loggingProperty, boolean registerMBean, String appName, Map<String,String> results )
    {
        JNDIUtil.setLogging(logging);

        try{
            Context ctx = JNDIUtil.getEnvContext(jsonConfigDefaults.getClass().getPackage().getName().replaceAll("\\.", "/"));

            Map<String,Object> jndiData = JNDIUtil.getJNDIVariables(ctx);

            if ( loggingProperty == null ){
                // logging was not set by a system property. allow JNDI override.
                logging = JNDIUtil.getBoolean(jndiData, "logging", logging);
                JNDIUtil.setLogging(logging);
            }

            registerMBean = JNDIUtil.getBoolean(jndiData, "registerMBean", registerMBean);

            if ( registerMBean && appName == null ){
                appName = JNDIUtil.getString(jndiData, "appName", null);
                results.put("appName", appName);
            }

            // locale.
            String languageTag = JNDIUtil.getString(jndiData, "locale", null);
            if ( languageTag != null ){
                jsonConfigDefaults.setLocale(languageTag);
            }

            loadDateFormatsFromJNDI(jndiData);
            loadReflectClassesFromJNDI(jndiData);
            setFlagsFromJNDI(jndiData);

            reflectionPrivacy = JNDIUtil.getInt(jndiData, "reflectionPrivacy", reflectionPrivacy);
            try{
                ReflectUtil.confirmPrivacyLevel(reflectionPrivacy, new JSONConfig());
            }catch ( JSONReflectionException ex ){
                if ( logging ){
                    Log log = Logger.getLog(jsonConfigDefaults.getClass());
                    if ( log.isDebugEnabled() ){
                        log.debug(ex.getLocalizedMessage(), ex);
                    }
                }
                reflectionPrivacy = ReflectUtil.PUBLIC;
            }
        }catch ( Exception e ){
            // Nothing set in JNDI.  Use code defaults.  Not a problem.
            if ( logging ){
                Log log = Logger.getLog(jsonConfigDefaults.getClass());
                if ( log.isDebugEnabled() ){
                    ResourceBundle bundle = JSONUtil.getBundle(getLocale());
                    log.debug(bundle.getString("badJNDIforConfig"), e);
                }
            }
        }

        return registerMBean;
    }

    /**
     * Load date formats from JNDI, if any.
     *
     * @param jndiData A map of JNDI data.
     */
    private static void loadDateFormatsFromJNDI( Map<String,Object> jndiData )
    {
        // date generation format.
        String dgf = JNDIUtil.getString(jndiData, "dateGenFormat", null);
        if ( dgf != null ){
            jsonConfigDefaults.setDateGenFormat(dgf);
        }

        // date parse formats.
        List<String> fmtNums = new ArrayList<>();
        for ( Entry<String,Object> entry : jndiData.entrySet() ){
            if ( entry.getValue() instanceof String ){
                Matcher matcher = DATE_PARSE_FORMAT_PAT.matcher(entry.getKey());
                if ( matcher.matches() ){
                    fmtNums.add(matcher.group(1));
                }
            }
        }
        if ( fmtNums.size() > 0 ){
            // using the strings so that people can use "001", "002" etc if they wish.
            Collections.sort(fmtNums,
                             new Comparator<String>()
                             {
                                 @Override
                                 public int compare( String o1, String o2 )
                                 {
                                     return new Integer(o1).compareTo(new Integer(o2));
                                 }
                             });
            for ( String num : fmtNums ){
                String dpf = JNDIUtil.getString(jndiData, "dateParseFormat" + num, null);
                if ( dpf != null ){
                    jsonConfigDefaults.addDateParseFormat(dpf);
                }
            }
        }
    }

    /**
     * Load reflection classes from JNDI, if any.
     *
     * @param jndiData A map of JNDI data.
     */
    private static void loadReflectClassesFromJNDI( Map<String,Object> jndiData )
    {
        List<JSONReflectedClass> classes = new ArrayList<>();
        for ( Entry<String,Object> entry : jndiData.entrySet() ){
            Object value = entry.getValue();
            if ( value instanceof String && REFLECT_CLASS_PAT.matcher(entry.getKey()).matches() ){
                String[] parts = ((String)value).split(",");
                try{
                    Class<?> clazz = ReflectUtil.getClassByName(parts[0]);
                    List<String> fieldNames = JSONConfigUtil.getFieldNames(parts);
                    classes.add(new JSONReflectedClass(clazz, fieldNames));
                }catch ( ClassNotFoundException e ){
                    if ( logging ){
                        Log log = Logger.getLog(jsonConfigDefaults.getClass());
                        if ( log.isDebugEnabled() ){
                            ResourceBundle bundle = JSONUtil.getBundle(JSONConfigDefaults.getLocale());
                            String msg = String.format(bundle.getString("couldntLoadClass"), parts[0]);
                            log.debug(msg, e);
                        }
                    }
                }
            }
        }
        addReflectClasses(classes);
    }

    /**
     * Initialize the boolean flags from JNDI. There are so many flags now that
     * I use reflection to look up the setter so that I don't have to change
     * this method every time I add a new flag. It's just at class load time and
     * it's not that many things really so performance is not really a big issue
     * in this case.
     *
     * @param jndiData A map of JNDI data.
     * @throws IllegalAccessException reflection problem.
     * @throws IllegalArgumentException reflection problem.
     * @throws InvocationTargetException reflection problem.
     */
    private static void setFlagsFromJNDI( Map<String,Object> jndiData ) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException
    {
        // get list of booleans from JNDI
        List<Entry<String,Object>> booleans = new ArrayList<>();
        for ( Entry<String,Object> entry : jndiData.entrySet() ){
            if ( entry.getValue() instanceof Boolean ){
                booleans.add(entry);
            }
        }
        if ( booleans.size() < 1 ){
            return;     // nothing to do.
        }

        // going to need to check against fields.
        Class<?> clazz = jsonConfigDefaults.getClass();
        Map<String,Field> fields = ReflectUtil.getFields(clazz, Boolean.TYPE);

        for ( Entry<String,Object> entry : booleans ){
            Field field = fields.get(entry.getKey());
            if ( field != null ){
                // it's a field.  check if it changed from default.
                ReflectUtil.ensureAccessible(field);
                boolean currentValue = field.getBoolean(jsonConfigDefaults);
                boolean jndiValue = (Boolean)entry.getValue();
                if ( jndiValue != currentValue ){
                    // it's changed.  check if there is a setter.
                    Method setter = ReflectUtil.getSetter(clazz, field);
                    if ( setter != null ){
                        // there's a setter.  use it.
                        ReflectUtil.ensureAccessible(setter);
                        setter.invoke(jsonConfigDefaults, jndiValue);
                    }
                    // no setter means I didn't intend for this to be used with JNDI.
                }
            }
        }
    }

    /**
     * Initialize the JMX MBean for this class.
     *
     * @param appName The name of the app.
     */
    private static void initMBean( String appName )
    {
        try{
            // Register an instance with MBean server if one is available.
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

            mBeanName = JMXUtil.getObjectName(jsonConfigDefaults, appName);
            mBeanServer.registerMBean(jsonConfigDefaults, mBeanName);
            if ( logging ){
                Log log = Logger.getLog(jsonConfigDefaults.getClass());
                if ( log.isDebugEnabled() ){
                    ResourceBundle bundle = JSONUtil.getBundle(getLocale());
                    log.debug(String.format(bundle.getString("registeredMbean"), mBeanName));
                }
            }
        }catch ( Exception e ){
            // No MBean server.  Not a problem.
            if ( logging ){
                Log log = Logger.getLog(jsonConfigDefaults.getClass());
                if ( log.isDebugEnabled() ){
                    ResourceBundle bundle = JSONUtil.getBundle(getLocale());
                    log.debug(bundle.getString("couldntRegisterMBean"), e);
                }
            }
        }
    }

    /**
     * Find out if logging is enabled.
     *
     * @return the logging policy.
     */
    static boolean isLogging()
    {
        return logging;
    }

    /**
     * Apply defaults to the given {@link JSONConfig} object.
     *
     * @param cfg The config object to initialize with defaults.
     * @param loc The locale.
     * @since 1.5
     */
    static synchronized void initJSONConfig( JSONConfig cfg, Locale loc )
    {
        cfg.setLocale(loc);

        // formats
        cfg.addNumberFormats(getNumberFormatMap());
        cfg.setDateGenFormat(getDateGenFormat());
        cfg.addDateParseFormats(getDateParseFormats());
        cfg.setReflectionPrivacy(reflectionPrivacy);

        if ( reflectClasses != null ){
            Collection<JSONReflectedClass> refClasses = reflectClasses.values();
            List<JSONReflectedClass> refCopy = new ArrayList<>(refClasses.size());
            for ( JSONReflectedClass refClass : refClasses ){
                refCopy.add(refClass.clone());
            }
            cfg.addReflectClasses(refCopy);
        }

        // validation options.
        cfg.setValidatePropertyNames(validatePropertyNames);
        cfg.setDetectDataStructureLoops(detectDataStructureLoops);
        cfg.setEscapeBadIdentifierCodePoints(escapeBadIdentifierCodePoints);
        cfg.setFullJSONIdentifierCodePoints(fullJSONIdentifierCodePoints);

        // various alternate encoding options.
        cfg.setEncodeNumericStringsAsNumbers(encodeNumericStringsAsNumbers);
        cfg.setEscapeNonAscii(escapeNonAscii);
        cfg.setUnEscapeWherePossible(unEscapeWherePossible);
        cfg.setEscapeSurrogates(escapeSurrogates);
        cfg.setPassThroughEscapes(passThroughEscapes);
        cfg.setEncodeDatesAsStrings(encodeDatesAsStrings);
        cfg.setReflectUnknownObjects(reflectUnknownObjects);
        cfg.setPreciseNumbers(preciseNumbers);
        cfg.setSmallNumbers(smallNumbers);
        cfg.setUsePrimitiveArrays(usePrimitiveArrays);
        cfg.setCacheReflectionData(cacheReflectionData);

        // non-standard JSON options.
        cfg.setQuoteIdentifier(quoteIdentifier);
        cfg.setUseECMA6(useECMA6);
        cfg.setAllowReservedWordsInIdentifiers(allowReservedWordsInIdentifiers);
        cfg.setEncodeDatesAsObjects(encodeDatesAsObjects);

        // indent padding, if any.
        if ( indentPadding == null ){
            cfg.setIndentPadding(indentPadding);
        }else{
            cfg.setIndentPadding(indentPadding.clone());
        }
    }

    /**
     * Get the number format map.
     *
     * @return the number format map.
     */
    static Map<Class<? extends Number>,NumberFormat> getNumberFormatMap()
    {
        return numberFormatMap;
    }

    /**
     * Get the date string generation format.
     *
     * @return the dateFormat
     * @since 1.4
     */
    static DateFormat getDateGenFormat()
    {
        return dateGenFormat;
    }

    /**
     * Get the list of date parsing formats used by the parser when
     * encodeDatesAsStrings or encodeDatesAsObjects is true.
     *
     * @return the list of date parsing formats.
     * @since 1.4
     */
    static List<DateFormat> getDateParseFormats()
    {
        return dateParseFormats;
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
     * public class AppCleanUp implements ServletContextListener
     * {
     *     public void contextDestroyed( ServletContextEvent sce )
     *     {
     *         JSONConfigDefaults.clearMBean();
     *     }
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
            try{
                MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
                mBeanServer.unregisterMBean(mBeanName);
                if ( logging ){
                    Log log = Logger.getLog(jsonConfigDefaults.getClass());
                    if ( log.isDebugEnabled() ){
                        ResourceBundle bundle = JSONUtil.getBundle(getLocale());
                        log.debug(String.format(bundle.getString("unregistered"), mBeanName));
                    }
                }
            }catch ( Exception e ){
                if ( logging ){
                    Log log = Logger.getLog(jsonConfigDefaults.getClass());
                    if ( log.isErrorEnabled() ){
                        ResourceBundle bundle = JSONUtil.getBundle(getLocale());
                        log.error(String.format(bundle.getString("couldntUnregister"), mBeanName), e);
                    }
                }
            }finally{
                // don't try again.
                mBeanName = null;
            }
        }
    }

    /**
     * Get the default indent padding object.
     *
     * @return the padding object.
     * @since 1.7
     */
    public static synchronized IndentPadding getIndentPadding()
    {
        return indentPadding;
    }

    /**
     * Set the padding object.
     *
     * @param indentPadding the default indent padding object.
     * @since 1.7
     */
    public static synchronized void setIndentPadding( IndentPadding indentPadding )
    {
        JSONConfigDefaults.indentPadding = indentPadding;
    }

    /**
     * This class should only be instantiated by the static block.  All others
     * should use {@link #getInstance()} to get that instance.
     */
    private JSONConfigDefaults()
    {
        setCodeDefaults();
    }

    /**
     * Reset all defaults to their original unmodified values.  This
     * overrides JNDI and previous MBean changes.
     * <p>
     * Accessible via MBean server.
     */
    @Override
    public void setCodeDefaults()
    {
        synchronized ( this.getClass() ){
            validatePropertyNames = true;
            detectDataStructureLoops = true;
            escapeBadIdentifierCodePoints = false;
            fullJSONIdentifierCodePoints = false;

            encodeNumericStringsAsNumbers = false;
            escapeNonAscii = false;
            unEscapeWherePossible = false;
            escapeSurrogates = false;
            passThroughEscapes = false;
            encodeDatesAsStrings = false;
            reflectUnknownObjects = false;
            preciseNumbers = false;
            smallNumbers = false;
            usePrimitiveArrays = false;
            cacheReflectionData = false;

            quoteIdentifier = true;
            useECMA6 = false;
            allowReservedWordsInIdentifiers = false;
            encodeDatesAsObjects = false;

            locale = null;
            numberFormatMap = null;
            dateGenFormat = null;
            dateParseFormats = null;
            indentPadding = null;
            reflectClasses = null;
            reflectionPrivacy = ReflectUtil.PUBLIC;
        }
    }

    /**
     * Set the default locale for new {@link JSONConfig} objects to use.
     * <p>
     * Accessible via MBean server.
     *
     * @param languageTag A language tag suitable for use by {@link Locale#forLanguageTag(String)}.
     */
    @Override
    public void setLocale( String languageTag )
    {
        if ( languageTag != null ){
            setLocale(Locale.forLanguageTag(languageTag));
        }else{
            setLocale((Locale)null);
        }
    }

    /**
     * Set a default locale for new {@link JSONConfig} objects to use.
     *
     * @param loc the default locale.
     */
    public static synchronized void setLocale( Locale loc )
    {
        locale = loc;
    }

    /**
     * Get the default locale for new {@link JSONConfig} objects.  If a
     * default locale has not been set, then the locale returned
     * by {@link Locale#getDefault()} will be returned.
     *
     * @return the default locale.
     */
    public static synchronized Locale getLocale()
    {
        return locale != null ? locale : Locale.getDefault();
    }

    /**
     * Get the number format for the given class.
     *
     * @param numericClass A class.
     * @return A number format or null if one has not been set.
     */
    public static synchronized NumberFormat getNumberFormat( Class<? extends Number> numericClass )
    {
        return numberFormatMap != null ? numberFormatMap.get(numericClass) : null;
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
     * Add a default number format for a particular type that extends Number.
     * This will be applied to all new {@link JSONConfig} objects that are created after
     * this in the same class loader.  The format is cloned for thread safety.
     *
     * @param numericClass The class.
     * @param fmt The number format.
     */
    public static void addNumberFormat( Class<? extends Number> numericClass, NumberFormat fmt )
    {
        if ( numericClass != null ){
            if ( fmt == null ){
                removeNumberFormat(numericClass);
            }else{
                Map<Class<? extends Number>,NumberFormat> numFmtMap = new HashMap<>(2);
                numFmtMap.put(numericClass, fmt);
                // handles null checking and cloning.
                addNumberFormats(numFmtMap);
            }
        }
    }

    /**
     * Add a default number format for a particular type that extends Number.
     * This will be applied to all new {@link JSONConfig} objects that are created after
     * this in the same class loader.  The format is cloned for thread safety.
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
     * Add a map of number formats to the current map of number formats.
     * The formats are cloned for thread safety.
     *
     * @param numFmtMap The input map.
     * @since 1.4
     */
    public static synchronized void addNumberFormats( Map<Class<? extends Number>,NumberFormat> numFmtMap )
    {
        numberFormatMap = JSONConfigUtil.mergeFormatMaps(numberFormatMap, numFmtMap);
    }

    /**
     * Remove the requested class from the default number formats.
     *
     * @param numericClass The class.
     */
    public static synchronized void removeNumberFormat( Class<? extends Number> numericClass )
    {
        if ( numberFormatMap != null && numericClass != null ){
            int size = numberFormatMap.size();
            numberFormatMap.remove(numericClass);
            if ( numberFormatMap.size() < 1 ){
                numberFormatMap = null;
            }else if ( numberFormatMap.size() < size ){
                numberFormatMap = new HashMap<>(numberFormatMap);
            }
        }
    }

    /**
     * Remove the requested class from the default number formats.
     *
     * @param num An object that implements {@link Number}.
     */
    public static void removeNumberFormat( Number num )
    {
        if ( num != null ){
            removeNumberFormat(num.getClass());
        }
    }

    /**
     * Clear any default number formats.
     * <p>
     * Accessible via MBean server.
     *
     * @since 1.4
     */
    @Override
    public void clearNumberFormats()
    {
        synchronized ( this.getClass() ){
            numberFormatMap = null;
        }
    }

    /**
     * Set the date format used for date string generation when
     * encodeDatesAsStrings or encodeDatesAsObjects is true.
     * <p>
     * Accessible via MBean server.
     *
     * @param fmtStr passed to the constructor for
     * {@link SimpleDateFormat#SimpleDateFormat(String,Locale)} using
     * the result of {@link #getLocale()}.
     * @return the format that is created.
     * @since 1.4
     */
    @Override
    public DateFormat setDateGenFormat( String fmtStr )
    {
        DateFormat fmt = null;
        if ( fmtStr != null ){
            fmt = new SimpleDateFormat(fmtStr, getLocale());
            setDateGenFormat(fmt);
        }else{
            setDateGenFormat((DateFormat)null);
        }
        return fmt;
    }

    /**
     * Set the date string generation format used when encodeDatesAsStrings
     * or encodeDatesAsObjects is true.
     *
     * @param fmt the dateFormat to set
     * @since 1.4
     */
    public static synchronized void setDateGenFormat( DateFormat fmt )
    {
        dateGenFormat = fmt;
    }

    /**
     * Clear date generation format. This means that if any special date
     * generation handling is enabled, then it will use the default ISO 8601
     * format.
     * <p>
     * Accessible via MBean server.
     *
     * @since 1.4
     */
    @Override
    public void clearDateGenFormat()
    {
        synchronized ( this.getClass() ){
            dateGenFormat = null;
        }
    }

    /**
     * Add a date parsing format to the list of date parsing formats
     * used by the parser when encodeDatesAsStrings or
     * encodeDatesAsObjects is true.
     * <p>
     * Accessible via MBean server.
     *
     * @param fmtStr Passed to
     * {@link SimpleDateFormat#SimpleDateFormat(String,Locale)} using
     * the result of {@link #getLocale()}.
     * @return The format that gets created.
     * @since 1.4
     */
    @Override
    public DateFormat addDateParseFormat( String fmtStr )
    {
        DateFormat fmt = new SimpleDateFormat(fmtStr, getLocale());
        addDateParseFormat(fmt);
        return fmt;
    }

    /**
     * Add a date parsing format to the list of parsing formats used
     * by the parser when encodeDatesAsStrings or encodeDatesAsObjects
     * is true.  When parsing date strings, they will be used in the
     * same order that they were added.
     *
     * @param fmt A date parsing format.
     * @since 1.4
     */
    public static void addDateParseFormat( DateFormat fmt )
    {
        if ( fmt != null ){
            addDateParseFormats(Arrays.asList(fmt));
        }
    }

    /**
     * Add a collection of date parsing format to the list of date
     * parsing formats used by the parser when encodeDatesAsStrings
     * or encodeDatesAsObjects is true.
     *
     * @param fmts A collection of date parsing formats.
     * @since 1.4
     */
    public static synchronized void addDateParseFormats( Collection<? extends DateFormat> fmts )
    {
        dateParseFormats = JSONConfigUtil.addDateParseFormats(dateParseFormats, fmts);
    }

    /**
     * Clear any date parse formats from the list of formats used by the parser
     * when encodeDatesAsStrings or encodeDatesAsObjects is true.
     * <p>
     * Accessible via MBean server.
     */
    @Override
    public void clearDateParseFormats()
    {
        synchronized ( this.getClass() ){
            dateParseFormats = null;
        }
    }

    /**
     * Get the reflection privacy level.
     * <p>
     * Accessible via MBean server.
     *
     * @return the reflection privacy level.
     * @since 1.9
     */
    @Override
    public int getReflectionPrivacy()
    {
        return reflectionPrivacy;
    }

    /**
     * Set the privacy level for reflection. Default is
     * {@link ReflectUtil#PUBLIC}.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt the level to set
     * @throws MBeanException If the privacy level is not allowed.
     * @see ReflectUtil#PRIVATE
     * @see ReflectUtil#PACKAGE
     * @see ReflectUtil#PROTECTED
     * @see ReflectUtil#PUBLIC
     * @since 1.9
     */
    @Override
    public void setReflectionPrivacy( int dflt ) throws MBeanException
    {
        try{
            synchronized ( this.getClass() ){
                reflectionPrivacy = ReflectUtil.confirmPrivacyLevel(dflt, new JSONConfig());
            }
        }catch ( JSONReflectionException e ){
            if ( logging ){
                Log log = Logger.getLog(jsonConfigDefaults.getClass());
                if ( log.isErrorEnabled() ){
                    log.error(e.getLocalizedMessage(), e);
                }
                Logger.freeLog(jsonConfigDefaults.getClass());
            }
            throw new MBeanException(e);   // MBeans should only throw MBeanExceptions.
        }
    }

    /**
     * Return true if the given class is in the set of classes being
     * automatically reflected.
     *
     * @param refClass The reflected class.
     * @return true if the class is automatically reflected.
     * @since 1.9
     */
    public static synchronized boolean isReflectClass( JSONReflectedClass refClass )
    {
        return reflectClasses == null || refClass == null ? false : reflectClasses.containsKey(refClass.getObjClass());
    }

    /**
     * Return true if objects with the same class given object are in the set of
     * classes being automatically reflected.
     *
     * @param obj An object to check
     * @return true if objects of the same type are reflected.
     * @since 1.9
     */
    public static boolean isReflectClass( Object obj )
    {
        return obj == null ? false : isReflectClass(ensureReflectedClass(obj));
    }

    /**
     * Get the {@link JSONReflectedClass} for the given object or create a dummy
     * one if there isn't one.  Creating one does not affect the results of the
     * isReflectClass() methods.  If you didn't add one then it isn't stored.
     *
     * @param obj The class to look up.
     * @return the reflected class object.
     */
    public static JSONReflectedClass ensureReflectedClass( Object obj )
    {
        JSONReflectedClass result = null;
        if ( obj instanceof JSONReflectedClass ){
            result = (JSONReflectedClass)obj;
        }else{
            result = getReflectedClass(obj);
            if ( result == null ){
                result = ReflectUtil.ensureReflectedClass(obj);
            }
        }
        return result;
    }

    /**
     * Return the JSONReflectedClass for this object if it is stored as a default.
     *
     * @param obj The class to look up.
     * @return the reflected class object or null if not found.
     */
    public static synchronized JSONReflectedClass getReflectedClass( Object obj )
    {
        return reflectClasses == null || obj == null ? null : reflectClasses.get(ReflectUtil.getClass(obj));
    }

    /**
     * Add the given class to the set of classes to be reflected.
     * <p>
     * Accessible via MBean server. This method primarily exists for JMX MBean
     * use. If you wish to use reflection with fields, you can append the field
     * names to the class name, separated by commas before each field name.
     * Field names which do not look like valid Java identifier names will be
     * silently discarded.
     *
     * @param className The name of the class suitable for {@link ClassLoader#loadClass(String)}
     * followed optionally by a comma separated list of field names.
     * @throws MBeanException If there's a problem loading the class.
     * @since 1.9
     */
    @Override
    public void addReflectClassByName( String className ) throws MBeanException
    {
        String[] parts = className.split(",");
        try{
            Class<?> clazz = ReflectUtil.getClassByName(parts[0]);
            List<String> fieldNames = JSONConfigUtil.getFieldNames(parts);
            addReflectClass(new JSONReflectedClass(clazz, fieldNames));
        }catch ( ClassNotFoundException e ){
            ResourceBundle bundle = JSONUtil.getBundle(JSONConfigDefaults.getLocale());
            String msg = String.format(bundle.getString("couldntLoadClass"), className);
            if ( logging ){
                Log log = Logger.getLog(jsonConfigDefaults.getClass());
                if ( log.isErrorEnabled() ){
                    log.error(msg, e);
                }
                Logger.freeLog(jsonConfigDefaults.getClass());
            }
            throw new MBeanException(e, msg);   // MBeans should only throw MBeanExceptions.
        }
    }

    /**
     * Add the class of the given object to the set of classes that
     * automatically get reflected. Note that default reflected classes can also
     * be added via JNDI.
     *
     * @param obj The object whose class to add to the reflect list.
     * @since 1.9
     */
    public static synchronized void addReflectClass( Object obj )
    {
        reflectClasses = JSONConfigUtil.addReflectClass(reflectClasses, obj);
    }

    /**
     * Add the classes of all of the given objects to the list of classes that
     * automatically get reflected. Note that default reflected classes can also
     * be added via JNDI.
     *
     * @param classes The objects to reflect.
     * @since 1.9
     */
    public static synchronized void addReflectClasses( Collection<?> classes )
    {
        reflectClasses = JSONConfigUtil.addReflectClasses(reflectClasses, classes);
    }

    /**
     * Remove the given class from the set of classes to be reflected.
     *
     * @param className The name of the class suitable for
     * (@link {@link ClassLoader#loadClass(String)}}.
     * @throws MBeanException If there's a problem loading the class.
     * @since 1.9
     */
    @Override
    public void removeReflectClassByName( String className ) throws MBeanException
    {
        try{
            removeReflectClass(ReflectUtil.getClassByName(className));
        }catch ( ClassNotFoundException e ){
            ResourceBundle bundle = JSONUtil.getBundle(JSONConfigDefaults.getLocale());
            String msg = String.format(bundle.getString("couldntLoadClass"), className);
            if ( logging ){
                Log log = Logger.getLog(jsonConfigDefaults.getClass());
                if ( log.isErrorEnabled() ){
                    log.error(msg, e);
                }
                Logger.freeLog(jsonConfigDefaults.getClass());
            }
            throw new MBeanException(e, msg);   // MBeans should only throw MBeanExceptions.
        }
    }

    /**
     * Remove the given class from the list of automatically reflected
     * classes.
     *
     * @param obj An object of the type to be removed from the reflect list.
     * @since 1.9
     */
    public static synchronized void removeReflectClass( Object obj )
    {
        reflectClasses = JSONConfigUtil.removeReflectClass(reflectClasses, obj);
    }

    /**
     * Remove the classes of the given objects from the set of classes
     * that automatically get reflected.
     *
     * @param classes The classes to remove.
     * @since 1.9
     */
    public static synchronized void removeReflectClasses( Collection<?> classes )
    {
        reflectClasses = JSONConfigUtil.removeReflectClasses(reflectClasses, classes);
    }

    /**
     * Clear all reflection classes, disabling all default automatic selective
     * reflection.
     *
     * @since 1.9
     */
    @Override
    public void clearReflectClasses()
    {
        synchronized ( this.getClass() ){
            reflectClasses = null;
        }
    }

    /**
     * Clear the reflection cache, if any.
     *
     * @since 1.9
     */
    @Override
    public void clearReflectionCache()
    {
        ReflectUtil.clearReflectionCache();
    }

    /**
     * Get a string with newline separated list of classes that get reflected.
     *
     * @return A string with newline separated list of classes that get reflected.
     * @since 1.9
     */
    @Override
    public String listReflectedClasses()
    {
        List<JSONReflectedClass> refClasses = null;
        synchronized ( this.getClass() ){
            if ( reflectClasses != null ){
                refClasses = new ArrayList<>(reflectClasses.values());
            }
        }
        StringBuilder result = new StringBuilder();
        if ( refClasses != null ){
            List<String> classes = new ArrayList<>(refClasses.size());
            for ( JSONReflectedClass refClass : refClasses ){
                Class<?> clazz = refClass.getObjClass();
                StringBuilder buf = new StringBuilder(clazz.getCanonicalName());
                String[] fieldNames = refClass.getFieldNamesRaw();
                if ( fieldNames != null ){
                    for ( String fieldName : fieldNames ){
                        buf.append(',').append(fieldName);
                    }
                }
                classes.add(buf.toString());
            }
            Collections.sort(classes);
            for ( String className : classes ){
                result.append(className).append('\n');
            }
        }
        return result.toString();
    }

    /**
     * Get the default validate property names policy.
     * <p>
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
     * This will affect all new {@link JSONConfig} objects created after this call
     * within the same class loader.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then property names will be validated by default.
     */
    @Override
    public void setValidatePropertyNames( boolean dflt )
    {
        synchronized ( this.getClass() ){
            validatePropertyNames = dflt;
        }
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
     * Set the default flag for detecting data structure loops.  If true,
     * then if a loop in a data structure is found then a
     * {@link DataStructureLoopException} will be thrown.
     * This will affect all new {@link JSONConfig} objects created after this call
     * within the same class loader.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then the code will detect loops in data structures.
     */
    @Override
    public void setDetectDataStructureLoops( boolean dflt )
    {
        synchronized ( this.getClass() ){
            detectDataStructureLoops = dflt;
        }
    }

    /**
     * Get the default escape bad identifier code points policy.
     * <p>
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
     * Default is false.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt if true, then any bad code points in identifiers will be escaped.
     */
    @Override
    public void setEscapeBadIdentifierCodePoints( boolean dflt )
    {
        synchronized ( this.getClass() ){
            escapeBadIdentifierCodePoints = dflt;
        }
    }

    /**
     * Get the full JSON identifier code points policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return the fullJSONIdentifierCodePoints
     */
    @Override
    public boolean isFullJSONIdentifierCodePoints()
    {
        return fullJSONIdentifierCodePoints;
    }

    /**
     * If true, then the full set of identifier code points permitted by the
     * JSON standard will be allowed instead of the more restrictive set
     * permitted by the ECMAScript standard. Use of characters not permitted by
     * the ECMAScript standard will cause an error if parsed by Javascript
     * eval().
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then allow all code points permitted by the JSON standard in identifiers.
     */
    @Override
    public void setFullJSONIdentifierCodePoints( boolean dflt )
    {
        synchronized ( this.getClass() ){
            fullJSONIdentifierCodePoints = dflt;
            if ( fullJSONIdentifierCodePoints ){
                quoteIdentifier = true;
            }
        }
    }

    /**
     * Get the default encode numeric strings as numbers policy.
     * <p>
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
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then strings that look like valid JSON numbers
     * will be encoded as numbers.
     */
    @Override
    public void setEncodeNumericStringsAsNumbers( boolean dflt )
    {
        synchronized ( this.getClass() ){
            encodeNumericStringsAsNumbers = dflt;
        }
    }

    /**
     * Get the default escape non-ASCII policy.
     * <p>
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
     * Set the default flag for forcing escaping of non-ASCII characters in
     * strings and identifiers. If true, then escapeSurrogates will be forced to
     * false. This will affect all new {@link JSONConfig} objects created after this
     * call within the same class loader.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then all non-ASCII will be Unicode escaped.
     */
    @Override
    public void setEscapeNonAscii( boolean dflt )
    {
        synchronized ( this.getClass() ){
            escapeNonAscii = dflt;
            if ( escapeNonAscii ){
                escapeSurrogates = false;
            }
        }
    }

    /**
     * Get the default unEscape policy.
     * <p>
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
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true then where possible, undo inline escapes in strings.
     */
    @Override
    public void setUnEscapeWherePossible( boolean dflt )
    {
        synchronized ( this.getClass() ){
            unEscapeWherePossible = dflt;
        }
    }

    /**
     * Get the default escape surrogates policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return the escape surrogates policy.
     */
    @Override
    public boolean isEscapeSurrogates()
    {
        return escapeSurrogates;
    }

    /**
     * Set the default escapeSurrogates policy.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then surrogates will be escaped in strings and identifiers
     * and escapeNonAscii will be forced to false.
     */
    @Override
    public void setEscapeSurrogates( boolean dflt )
    {
        synchronized ( this.getClass() ){
            escapeSurrogates = dflt;
            if ( escapeSurrogates ){
                escapeNonAscii = false;
            }
        }
    }

    /**
     * Get the pass through escapes policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return The pass through escapes policy.
     */
    @Override
    public boolean isPassThroughEscapes()
    {
        return passThroughEscapes;
    }

    /**
     * If true, then escapes in strings will be passed through unchanged.
     * If false, then the backslash that starts the escape will be escaped.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then pass escapes through.
     */
    @Override
    public void setPassThroughEscapes( boolean dflt )
    {
        synchronized ( this.getClass() ){
            passThroughEscapes = dflt;
        }
    }

    /**
     * Get the encode dates as strings policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return the encodeDatesAsStrings policy.
     */
    @Override
    public boolean isEncodeDatesAsStrings()
    {
        return encodeDatesAsStrings;
    }

    /**
     * Set the encodeDatesAsStrings policy.  If you set this to true, then
     * encodeDatesAsObjects will be set to false.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then {@link Date} objects will be encoded as ISO 8601 date
     * strings or a custom date format if you have called
     * {@link #setDateGenFormat(DateFormat)}.
     */
    @Override
    public synchronized void setEncodeDatesAsStrings( boolean dflt )
    {
        synchronized ( this.getClass() ){
            encodeDatesAsStrings = dflt;
            if ( encodeDatesAsStrings ){
                encodeDatesAsObjects = false;
            }
        }
    }

    /**
     * Get the reflection of unknown objects policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return the reflectUnknownObjects policy.
     * @since 1.9
     */
    @Override
    public boolean isReflectUnknownObjects()
    {
        return reflectUnknownObjects;
    }

    /**
     * Set the reflection encoding policy.  If true, then any time that an
     * unknown object is encountered, this package will attempt to use
     * reflection to encode it.  Default is false.  When false, then unknown
     * objects will have their toString() method called.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then attempt to use reflection
     * to encode objects which are otherwise unknown.
     * @since 1.9
     */
    @Override
    public void setReflectUnknownObjects( boolean dflt )
    {
        synchronized ( this.getClass() ){
            reflectUnknownObjects = dflt;
        }
    }

    /**
     * Get the preciseIntegers policy.
     *
     * @return The preciseIntegers policy.
     * @since 1.9
     */
    @Override
    public boolean isPreciseNumbers()
    {
        return preciseNumbers;
    }

    /**
     * If true then numbers which are not exactly representable by a 64 bit
     * double precision floating point number will be quoted in the output. If
     * false, then they will be unquoted, and precision in such will likely be
     * lost in the interpreter.
     *
     * @param dflt If true then quote numbers that lose precision in 64-bit floating point.
     * @since 1.9
     */
    @Override
    public void setPreciseNumbers( boolean dflt )
    {
        synchronized ( this.getClass() ){
            preciseNumbers = dflt;
        }
    }

    /**
     * Get the smallNumbers policy.
     *
     * @return The smallNumbers policy.
     * @since 1.9
     */
    @Override
    public boolean isSmallNumbers()
    {
        return smallNumbers;
    }

    /**
     * If true then {@link JSONParser} will attempt to minimize the
     * storage used for all numbers.  Decimal numbers will be reduced
     * to floats instead of doubles if it can done without losing
     * precision.  Integer numbers will be reduced from long to int
     * or short or byte if they fit.
     *
     * @param dflt If true then numbers will be made to use as little memory as possible.
     * @since 1.9
     */
    @Override
    public void setSmallNumbers( boolean dflt )
    {
        synchronized ( this.getClass() ){
            smallNumbers = dflt;
        }
    }

    /**
     * The primitive arrays policy.
     *
     * @return the usePrimitiveArrays policy.
     * @since 1.9
     */
    @Override
    public boolean isUsePrimitiveArrays()
    {
        return usePrimitiveArrays;
    }

    /**
     * If true, then when {@link JSONParser} encounters a JSON array of non-null
     * wrappers of primitives and those primitives are all compatible with each
     * other, then instead of an {@link ArrayList} of wrappers for those
     * primitives it will create an array of those primitives in order to save
     * memory.
     * <p>
     * This works for booleans and numbers. It will also convert an array of
     * single character strings into an array of chars. Arrays of numbers will
     * attempt to use the least complex type that does not lose information. You
     * could easily end up with an array of bytes if all of your numbers are
     * integers in the range -128 to 127. This option is meant to save as much
     * memory as possible.
     *
     * @param dflt if true, then the parser will create arrays of primitives as applicable.
     * @since 1.9
     */
    @Override
    public void setUsePrimitiveArrays( boolean dflt )
    {
        synchronized ( this.getClass() ){
            usePrimitiveArrays = dflt;
        }
    }

    /**
     * Get the the cacheReflectionData policy.
     *
     * @return the cacheReflectionData policy.
     * @since 1.9
     */
    @Override
    public boolean isCacheReflectionData()
    {
        return cacheReflectionData;
    }

    /**
     * If true, then when an object is reflected its reflection data
     * will be cached to improve performance on subsequent reflections
     * of objects of its class.
     *
     * @param dflt if true, then cache reflection data.
     * @since 1.9
     */
    @Override
    public void setCacheReflectionData( boolean dflt )
    {
        cacheReflectionData = dflt;
        if ( cacheReflectionData == false ){
            ReflectUtil.clearReflectionCache();
        }
    }

    /**
     * Get the default quote identifier policy.
     * <p>
     * Accessible via MBean server.
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
     * This will affect all new {@link JSONConfig} objects created after this call
     * within the same class loader.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then all identifiers will be quoted.
     */
    @Override
    public void setQuoteIdentifier( boolean dflt )
    {
        synchronized ( this.getClass() ){
            quoteIdentifier = fullJSONIdentifierCodePoints || dflt;
        }
    }

    /**
     * Get the default escape ECMAScript 6 code points policy.
     * <p>
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
     * If you set this to true, then when JSONUtil generates Unicode
     * escapes, it will use ECMAScript 6 code point escapes if they are shorter
     * than code unit escapes. This is not standard JSON and not yet widely
     * supported by Javascript interpreters. It also allows identifiers to have
     * letter numbers in addition to other letters.  Default is false.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, use EMCAScript 6 code point escapes and allow
     * ECMAScript 6 identifier character set.
     */
    @Override
    public void setUseECMA6( boolean dflt )
    {
        synchronized ( this.getClass() ){
            useECMA6 = dflt;
        }
    }

    /**
     * Get the default for allowing reserved words in identifiers.
     * <p>
     * Accessible via MBean server.
     *
     * @return the reserverd words in identifiers policy.
     */
    @Override
    public boolean isAllowReservedWordsInIdentifiers()
    {
        return allowReservedWordsInIdentifiers;
    }

    /**
     * Set default flag for allowing reserved words in identifiers.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then reserved words will be allowed in identifiers.
     */
    @Override
    public void setAllowReservedWordsInIdentifiers( boolean dflt )
    {
        synchronized ( this.getClass() ){
            allowReservedWordsInIdentifiers = dflt;
        }
    }

    /**
     * Get the encode dates as objects policy.
     * <p>
     * Accessible via MBean server.
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
     * Javascript dates, using new Date(dateString).  If you
     * set this to true, then encodeDatesAsStrings will be
     * set to false.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then {@link Date} objects will be encoded as
     * Javascript dates.
     */
    @Override
    public synchronized void setEncodeDatesAsObjects( boolean dflt )
    {
        synchronized ( this.getClass() ){
            encodeDatesAsObjects = dflt;
            if ( encodeDatesAsObjects ){
                encodeDatesAsStrings = false;
            }
        }
    }

    private static final long serialVersionUID = 1L;
}
