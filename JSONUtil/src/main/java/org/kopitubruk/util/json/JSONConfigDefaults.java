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

import static org.kopitubruk.util.json.JSONConfigUtil.tableSizeFor;

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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.naming.Context;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
 * Because the appName tends to remain consistent across all deployments, it
 * might be preferable to put it into your webapp's web.xml.  This should work
 * with all JEE web tier containers. There is a different syntax for adding
 * context environment variables in web.xml:
 * <pre>{@code <env-entry>
 *   <env-entry-name>org/kopitubruk/util/json/appName</env-entry-name>
 *   <env-entry-type>java.lang.String</env-entry-type>
 *   <env-entry-value>MyApp</env-entry-value>
 * </env-entry>}</pre>
 * <p>
 * You can use this method for any other JNDI environment variables provided
 * that you always want them the same on all deployments.
 * <p>
 * These are the names and their normal defaults if you don't change them.
 * See their setters for these for descriptions of what they do.
 * <h3>Validation related options.</h3>
 * <ul>
 *   <li>validatePropertyNames = true</li>
 *   <li>detectDataStructureLoops = true</li>
 *   <li>escapeBadIdentifierCodePoints = false</li>
 *   <li>fullJSONIdentifierCodePoints = false</li>
 *   <li>fastStrings = false</li>
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
 * silently discarded.  If the field names are of the form "name=alias"
 * then a field called "name" will be represented as "alias" in the output.
 * Aliases do not effect field selection so you can have aliases without
 * selection.  If you want to specify only field "foo" and you want it called
 * "bar" then you will need to specify "foo,foo=bar" in the string.
 * These reflect classes will be added to all JSONConfig objects that are created
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
    private static volatile boolean fastStrings;

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
    private static volatile int unmatchedSurrogatePolicy;
    private static volatile int undefinedCodePointPolicy;

    // stored for deregistration on unload.
    private static ObjectName mBeanName = null;

    // the singleton, which has no instance data; only MBean methods.
    private static JSONConfigDefaults jsonConfigDefaults;

    // logging.
    private static boolean logging;
    private static LogFactory logFactory = null;
    private static Log log = null;

    /**
     * Make sure that the logger is there.
     */
    private static synchronized void ensureLogger()
    {
        if ( log == null ){
            logFactory = LogFactory.getFactory();
            log = logFactory.getInstance(JSONConfigDefaults.class);
        }
    }

    /**
     * Release the logger.
     */
    private static synchronized void releaseLogger()
    {
        if ( log != null ){
            logFactory.release();
            log = null;
            logFactory = null;
        }
    }

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

        releaseLogger();
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
                jsonConfigDefaults.setLocaleLanguageTag(languageTag);
            }else{
                languageTag = JNDIUtil.getString(jndiData, "defaultLocale", null);
                if ( languageTag != null ){
                    jsonConfigDefaults.setLocaleLanguageTag(languageTag);
                }
            }

            loadDateFormatsFromJNDI(jndiData);
            loadReflectClassesFromJNDI(jndiData);
            setFlagsFromJNDI(jndiData);

            try{
                jsonConfigDefaults.setReflectionPrivacy(JNDIUtil.getInt(jndiData, "reflectionPrivacy", reflectionPrivacy));
            }catch ( JSONReflectionException ex ){
                if ( logging ){
                    ensureLogger();
                    if ( log.isDebugEnabled() ){
                        log.debug(ex.getLocalizedMessage(), ex);
                    }
                }
                reflectionPrivacy = ReflectUtil.PUBLIC;
            }


            if ( jndiData.containsKey("badCharacterPolicy") ){
                setBadCharacterPolicy(JNDIUtil.getInt(jndiData, "badCharacterPolicy", JSONConfig.REPLACE));
            }
            jsonConfigDefaults.setUndefinedCodePointPolicy(JNDIUtil.getInt(jndiData, "undefinedCodePointPolicy", undefinedCodePointPolicy));
            jsonConfigDefaults.setUnmatchedSurrogatePolicy(JNDIUtil.getInt(jndiData, "unmatchedSurrogatePolicy", unmatchedSurrogatePolicy));
        }catch ( Exception e ){
            // Nothing set in JNDI.  Use code defaults.  Not a problem.
            if ( logging ){
                ensureLogger();
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
                String className = (String)value;
                try{
                    classes.add(new JSONReflectedClass(className));
                }catch ( ClassNotFoundException e ){
                    if ( logging ){
                        ensureLogger();
                        if ( log.isDebugEnabled() ){
                            log.debug(getClassNotFoundExceptionMsg(e, className, false), e);
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
     * it's not that many things really so performance is not a big issue in
     * this case.
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
        if ( appName == null ){
            appName = String.format("%X", new Random().nextLong());
        }

        try{
            // Register an instance with MBean server if one is available.
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

            mBeanName = JMXUtil.getObjectName(jsonConfigDefaults, appName);
            try{
                ObjectInstance instance = mBeanServer.getObjectInstance(mBeanName);
                if ( instance != null ){
                    mBeanServer.unregisterMBean(mBeanName);
                }
            }catch ( Exception ex ){
            }

            mBeanServer.registerMBean(jsonConfigDefaults, mBeanName);
            if ( logging ){
                ensureLogger();
                if ( log.isDebugEnabled() ){
                    ResourceBundle bundle = JSONUtil.getBundle(getLocale());
                    log.debug(String.format(bundle.getString("registeredMbean"), mBeanName));
                }
            }
        }catch ( Exception e ){
            // Probably no MBean server.  Not a problem.
            if ( logging ){
                ensureLogger();
                if ( log.isDebugEnabled() ){
                    ResourceBundle bundle = JSONUtil.getBundle(getLocale());
                    log.debug(bundle.getString("couldntRegisterMBean"), e);
                }
            }
        }
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
                    ensureLogger();
                    if ( log.isDebugEnabled() ){
                        ResourceBundle bundle = JSONUtil.getBundle(getLocale());
                        log.debug(String.format(bundle.getString("unregistered"), mBeanName));
                    }
                }
            }catch ( Exception e ){
                if ( logging ){
                    ensureLogger();
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
        releaseLogger();
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
        synchronized ( getClass() ){
            validatePropertyNames = true;
            detectDataStructureLoops = true;
            escapeBadIdentifierCodePoints = false;
            fullJSONIdentifierCodePoints = false;
            fastStrings = false;

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
            unmatchedSurrogatePolicy = JSONConfig.REPLACE;
            undefinedCodePointPolicy = JSONConfig.REPLACE;
        }
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

        // other non-booleans
        cfg.setReflectionPrivacy(reflectionPrivacy);
        cfg.setUndefinedCodePointPolicy(undefinedCodePointPolicy);
        cfg.setUnmatchedSurrogatePolicy(unmatchedSurrogatePolicy);

        Map<Class<?>,JSONReflectedClass> refClasses = null;
        if ( reflectClasses != null ){
            refClasses = new HashMap<>(reflectClasses.size());
        }
        if ( refClasses != null ){
            for ( Entry<Class<?>,JSONReflectedClass> entry : reflectClasses.entrySet() ){
                refClasses.put(entry.getKey(), entry.getValue().clone());
            }
        }
        cfg.setReflectClasses(refClasses);

        // validation options.
        cfg.setValidatePropertyNames(validatePropertyNames);
        cfg.setDetectDataStructureLoops(detectDataStructureLoops);
        cfg.setEscapeBadIdentifierCodePoints(escapeBadIdentifierCodePoints);
        cfg.setFullJSONIdentifierCodePoints(fullJSONIdentifierCodePoints);
        cfg.setFastStrings(fastStrings);

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
     * Get the result of {@link Locale#toLanguageTag()} from the current default
     * locale.
     * <p>
     * Accessible via MBean server.
     *
     * @return The string form of the default locale.
     */
    @Override
    public String getLocaleLanguageTag()
    {
        return getLocale().toLanguageTag();
    }

    /**
     * Set the default locale for new {@link JSONConfig} objects to use by
     * specifying a IETF BCP 47 language tag suitable for use by
     * {@link Locale#forLanguageTag(String)}.
     * <p>
     * Accessible via MBean server.
     *
     * @param languageTag A IETF BCP 47 language tag suitable for use by {@link Locale#forLanguageTag(String)}.
     */
    @Override
    public void setLocaleLanguageTag( String languageTag )
    {
        if ( languageTag != null ){
            setLocale(Locale.forLanguageTag(languageTag.replaceAll("_", "-")));
        }else{
            setLocale((Locale)null);
        }
    }

    /**
     * Set the default locale for new {@link JSONConfig} objects to use.
     *
     * @param languageTag A language tag suitable for use by {@link Locale#forLanguageTag(String)}.
     * @deprecated Use {@link #setLocaleLanguageTag(String)} instead.
     */
    @Deprecated
    public void setLocale( String languageTag )
    {
        setLocaleLanguageTag(languageTag);
    }

    /**
     * Get the default locale for new {@link JSONConfig} objects.  If a
     * default locale has not been set, then the locale returned
     * by {@link Locale#getDefault()} will be returned.
     *
     * @return the default locale.
     * @see JSONConfig#getLocale()
     */
    public static synchronized Locale getLocale()
    {
        return locale != null ? locale : Locale.getDefault();
    }

    /**
     * Set a default locale for new {@link JSONConfig} objects to use.
     *
     * @param loc the default locale.
     * @see JSONConfig#setLocale(Locale)
     */
    public static synchronized void setLocale( Locale loc )
    {
        locale = loc;
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
     * Get the number format for the given class.
     *
     * @param numericClass A class.
     * @return A number format or null if one has not been set.
     * @see JSONConfig#getNumberFormat(Class)
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
     * @see JSONConfig#getNumberFormat(Number)
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
     * @see JSONConfig#addNumberFormat(Class, NumberFormat)
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
     * @see JSONConfig#addNumberFormat(Number, NumberFormat)
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
     * @see JSONConfig#addNumberFormats(Map)
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
     * @see JSONConfig#removeNumberFormat(Class)
     */
    public static synchronized void removeNumberFormat( Class<? extends Number> numericClass )
    {
        if ( numberFormatMap != null && numericClass != null ){
            int size = numberFormatMap.size();
            numberFormatMap.remove(numericClass);
            if ( numberFormatMap.size() < 1 ){
                numberFormatMap = null;
            }else if ( tableSizeFor(size) > tableSizeFor(numberFormatMap.size()) ){
                numberFormatMap = new HashMap<>(numberFormatMap);
            }
        }
    }

    /**
     * Remove the requested class from the default number formats.
     *
     * @param num An object that implements {@link Number}.
     * @see JSONConfig#removeNumberFormat(Number)
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
     * @see JSONConfig#clearNumberFormats()
     * @since 1.4
     */
    @Override
    public void clearNumberFormats()
    {
        synchronized ( getClass() ){
            numberFormatMap = null;
        }
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
     * Set the date format used for date string generation when
     * encodeDatesAsStrings or encodeDatesAsObjects is true.
     * <p>
     * Accessible via MBean server.
     *
     * @param fmtStr passed to the constructor for
     * {@link SimpleDateFormat#SimpleDateFormat(String,Locale)} using
     * the result of {@link #getLocale()}.
     * @return the format that is created so that it can be modified.
     * @see JSONConfig#setDateGenFormat(String)
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
     * @see JSONConfig#setDateGenFormat(DateFormat)
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
     * @see JSONConfig#clearDateGenFormat()
     * @since 1.4
     */
    @Override
    public void clearDateGenFormat()
    {
        synchronized ( getClass() ){
            dateGenFormat = null;
        }
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
     * Add a date parsing format to the list of date parsing formats
     * used by the parser when encodeDatesAsStrings or
     * encodeDatesAsObjects is true.
     * <p>
     * Accessible via MBean server.
     *
     * @param fmtStr Passed to
     * {@link SimpleDateFormat#SimpleDateFormat(String,Locale)} using
     * the result of {@link #getLocale()}.
     * @return The format that gets created so that it can be modified.
     * @see JSONConfig#addDateParseFormat(String)
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
     * @see JSONConfig#addDateParseFormat(DateFormat)
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
     * @see JSONConfig#addDateParseFormats(Collection)
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
     * @see JSONConfig#clearDateParseFormats()
     */
    @Override
    public void clearDateParseFormats()
    {
        synchronized ( getClass() ){
            dateParseFormats = null;
        }
    }

    /**
     * Get the default indent padding object.
     *
     * @return the padding object.
     * @see JSONConfig#getIndentPadding()
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
     * @see JSONConfig#setIndentPadding(IndentPadding)
     * @since 1.7
     */
    public static synchronized void setIndentPadding( IndentPadding indentPadding )
    {
        JSONConfigDefaults.indentPadding = indentPadding;
    }

    /**
     * Get the reflection privacy level.
     * <p>
     * Accessible via MBean server.
     *
     * @return the reflection privacy level.
     * @see ReflectUtil#PRIVATE
     * @see ReflectUtil#PACKAGE
     * @see ReflectUtil#PROTECTED
     * @see ReflectUtil#PUBLIC
     * @see JSONConfig#getReflectionPrivacy()
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
     * @see JSONConfig#setReflectionPrivacy(int)
     * @since 1.9
     */
    @Override
    public void setReflectionPrivacy( int dflt ) throws MBeanException
    {
        int privacyLevel;
        try{
            privacyLevel = ReflectUtil.confirmPrivacyLevel(dflt, new JSONConfig());
            synchronized ( getClass() ){
                reflectionPrivacy = privacyLevel;
            }
        }catch ( JSONReflectionException e ){
            synchronized ( getClass() ){
                if ( logging ){
                    ensureLogger();
                    if ( log.isErrorEnabled() ){
                        log.error(e.getLocalizedMessage(), e);
                    }
                    releaseLogger();
                }
                throw new MBeanException(e);   // MBeans should only throw MBeanExceptions.
            }
        }
    }

    /**
     * Return true if the given class is in the set of classes being
     * automatically reflected.
     *
     * @param obj An object to check
     * @return true if objects of the given type are reflected.
     * @see JSONConfig#isReflectClass(Object)
     * @since 1.9
     */
    public static boolean isReflectClass( Object obj )
    {
        return getReflectedClass(obj) != null;
    }

    /**
     * Get the {@link JSONReflectedClass} for the given object if it is stored.
     * The main reason that you might want to use this is to modify the fields
     * or aliases that are reflected in the class.
     *
     * @param obj The class to look up.
     * @return the reflected class object or null if not found.
     * @see JSONConfig#getReflectedClass(Object)
     */
    public static synchronized JSONReflectedClass getReflectedClass( Object obj )
    {
        return reflectClasses == null || obj == null ? null : reflectClasses.get(ReflectUtil.getClass(obj));
    }

    /**
     * Add the given class to the set of classes to be reflected.
     * <p>
     * Accessible via MBean server.
     * <p>
     * This method primarily exists for JMX MBean use.
     * <p>
     * If you wish to use reflection with fields, you can append the field names
     * to the class name, separated by commas before each field name. Field
     * names which do not look like valid Java identifier names will be silently
     * discarded.  For example, if you want to reflect a class called
     * "org.example.Widget" and it has fields called "a", "b" and "c" but you
     * only want "a" and "c", then you can pass "org.example.Widget,a,c" to this
     * method.
     * <p>
     * If you wish to use custom field names with reflection you can use name=alias
     * pairs separated by commas as with the field names.  For example, if you
     * want to reflect a class called "org.example.Widget" and it has a field called
     * "foo" but you want that field encoded as "bar" you can pass
     * "org.example.Widget,foo=bar" to this method.
     *
     * @param className The name of the class suitable for
     *            {@link ClassLoader#loadClass(String)} followed optionally by a
     *            comma separated list of field names and/or field aliases.
     * @throws MBeanException If there's a problem loading the class.
     * @see JSONConfig#addReflectClassByName(String)
     * @since 1.9
     */
    @Override
    public void addReflectClassByName( String className ) throws MBeanException
    {
        try{
            addReflectClass(new JSONReflectedClass(className));
        }catch ( ClassNotFoundException e ){
            throw new MBeanException(e, getClassNotFoundExceptionMsg(e, className, logging));
        }
    }

    /**
     * Add the class of the given object to the set of classes that
     * automatically get reflected. Note that default reflected classes can also
     * be added via JNDI. If the object is an array, {@link Iterable} or
     * {@link Enumeration}, then all objects in it will be added.
     *
     * @param obj The object whose class to add to the reflect list.
     * @see JSONReflectedClass
     * @see JSONConfig#addReflectClass(Object)
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
     * @see JSONReflectedClass
     * @see JSONConfig#addReflectClasses(Collection)
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
        String[] parts = className.split(",");
        try{
            removeReflectClass(ReflectUtil.getClassByName(parts[0].trim()));
        }catch ( ClassNotFoundException e ){
            throw new MBeanException(e, getClassNotFoundExceptionMsg(e, parts[0], logging));
        }
    }

    /**
     * Get the exception message for a {@link ClassNotFoundException} and log the
     * exception if appropriate.
     *
     * @param e the exception.
     * @param className the class name that it failed to load.
     * @param isLogging if true then also log the message and exception.
     * @return The message.
     */
    private static String getClassNotFoundExceptionMsg( ClassNotFoundException e, String className, boolean isLogging )
    {
        ResourceBundle bundle = JSONUtil.getBundle(getLocale());
        String msg = String.format(bundle.getString("couldntLoadClass"), className);
        if ( isLogging ){
            synchronized ( jsonConfigDefaults.getClass() ){
                ensureLogger();
                if ( log.isErrorEnabled() ){
                    log.error(msg, e);
                }
                releaseLogger();
            }
        }
        return msg;
    }

    /**
     * Remove the given class from the list of automatically reflected
     * classes.  If the object is an array, {@link Iterable} or {@link Enumeration},
     * then all objects in it will be removed.
     *
     * @param obj An object of the type to be removed from the reflect list.
     * @see JSONConfig#removeReflectClass(Object)
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
     * @see JSONConfig#removeReflectClasses(Collection)
     * @since 1.9
     */
    public static synchronized void removeReflectClasses( Collection<?> classes )
    {
        reflectClasses = JSONConfigUtil.removeReflectClasses(reflectClasses, classes);
    }

    /**
     * Clear all reflection classes, disabling all default automatic selective
     * reflection.
     * <p>
     * Accessible via MBean server.
     *
     * @see JSONConfig#clearReflectClasses()
     * @since 1.9
     */
    @Override
    public void clearReflectClasses()
    {
        synchronized ( getClass() ){
            reflectClasses = null;
        }
    }

    /**
     * Clear the reflection cache, if any.
     * <p>
     * Accessible via MBean server.
     *
     * @since 1.9
     */
    @Override
    public void clearReflectionCache()
    {
        ReflectedObjectMapBuilder.clearReflectionCache();
    }

    /**
     * Get a string with newline separated list of classes that get reflected.
     * <p>
     * Accessible via MBean server.
     *
     * @return A string with newline separated list of classes that get reflected.
     * @since 1.9
     */
    @Override
    public String listReflectedClasses()
    {
        List<JSONReflectedClass> refClasses = null;
        synchronized ( getClass() ){
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
                Collection<String> fieldNames = refClass.getFieldNamesRaw();
                if ( fieldNames != null ){
                    for ( String fieldName : fieldNames ){
                        buf.append(',').append(fieldName);
                    }
                }
                Map<String,String> customNames = refClass.getFieldAliases();
                if ( customNames != null ){
                    for ( Entry<String,String> entry : customNames.entrySet() ){
                        buf.append(',').append(entry.getKey()).append('=').append(entry.getValue());
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
     * Get the default policy for unmatched surrogates.
     * <p>
     * Accessible via MBean server.
     *
     * @return the default policy for unmatched surrogates.
     * @see JSONConfig#REPLACE
     * @see JSONConfig#DISCARD
     * @see JSONConfig#EXCEPTION
     * @see JSONConfig#ESCAPE
     * @see JSONConfig#PASS
     * @see JSONConfig#getUnmatchedSurrogatePolicy()
     */
    @Override
    public int getUnmatchedSurrogatePolicy()
    {
        return unmatchedSurrogatePolicy;
    }

    /**
     * Tell JSONUtil what to do by default when it encounters unmatched surrogates in strings
     * and identifiers.  The permitted values are:
     * <ul>
     *   <li>{@link JSONConfig#REPLACE} - Replace with Unicode replacement character U+FFFD (default)</li>
     *   <li>{@link JSONConfig#DISCARD} - Discard them.</li>
     *   <li>{@link JSONConfig#EXCEPTION} - Throw a {@link UndefinedCodePointException}</li>
     *   <li>{@link JSONConfig#ESCAPE} - Include them but escape them</li>
     *   <li>{@link JSONConfig#PASS} - Pass them through unmodified.</li>
     * </ul>
     * Any other value will be ignored.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt the default unmatchedSurrogatePolicy to set
     * @see JSONConfig#setUnmatchedSurrogatePolicy(int)
     */
    @Override
    public void setUnmatchedSurrogatePolicy( int dflt )
    {
        switch ( dflt )
        {
            case JSONConfig.REPLACE:
            case JSONConfig.DISCARD:
            case JSONConfig.EXCEPTION:
            case JSONConfig.ESCAPE:
            case JSONConfig.PASS:
                synchronized ( getClass() ){
                    unmatchedSurrogatePolicy = dflt;
                }
                break;
        }
    }

    /**
     * Get the default policy for undefined code points.
     * <p>
     * Accessible via MBean server.
     *
     * @return the policy for undefined code points.
     * @see JSONConfig#REPLACE
     * @see JSONConfig#DISCARD
     * @see JSONConfig#EXCEPTION
     * @see JSONConfig#ESCAPE
     * @see JSONConfig#PASS
     * @see JSONConfig#getUndefinedCodePointPolicy()
     */
    @Override
    public int getUndefinedCodePointPolicy()
    {
        return undefinedCodePointPolicy;
    }

    /**
     * Tell JSONUtil what to do by default when it encounters undefined code points in strings
     * and identifiers.  The permitted values are:
     * <ul>
     *   <li>{@link JSONConfig#REPLACE} - Replace with Unicode replacement character U+FFFD (default)</li>
     *   <li>{@link JSONConfig#DISCARD} - Discard them.</li>
     *   <li>{@link JSONConfig#EXCEPTION} - Throw a {@link UndefinedCodePointException}</li>
     *   <li>{@link JSONConfig#ESCAPE} - Include them but escape them</li>
     *   <li>{@link JSONConfig#PASS} - Pass them through unmodified.</li>
     * </ul>
     * Any other value will be ignored.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt the default undefinedCodePointPolicy to set
     * @see JSONConfig#setUndefinedCodePointPolicy(int)
     */
    @Override
    public void setUndefinedCodePointPolicy( int dflt )
    {
        switch ( dflt )
        {
            case JSONConfig.REPLACE:
            case JSONConfig.DISCARD:
            case JSONConfig.EXCEPTION:
            case JSONConfig.ESCAPE:
            case JSONConfig.PASS:
                synchronized ( getClass() ){
                    undefinedCodePointPolicy = dflt;
                }
                break;
        }
    }

    /**
     * Convenience method to call both {@link #setUnmatchedSurrogatePolicy(int)}
     * and {@link #setUndefinedCodePointPolicy(int)} using the same value.
     *
     * @param badCharacterPolicy the badCharacterPolicy to set
     * @see JSONConfig#REPLACE
     * @see JSONConfig#DISCARD
     * @see JSONConfig#EXCEPTION
     * @see JSONConfig#ESCAPE
     * @see JSONConfig#PASS
     * @see JSONConfig#setBadCharacterPolicy(int)
     */
    public static synchronized void setBadCharacterPolicy( int badCharacterPolicy )
    {
        jsonConfigDefaults.setUnmatchedSurrogatePolicy(badCharacterPolicy);
        jsonConfigDefaults.setUndefinedCodePointPolicy(badCharacterPolicy);
    }

    /**
     * Get the default validate property names policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return The default validate property names policy.
     * @see JSONConfig#isValidatePropertyNames()
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
     * @see JSONConfig#setValidatePropertyNames(boolean)
     */
    @Override
    public void setValidatePropertyNames( boolean dflt )
    {
        synchronized ( getClass() ){
            validatePropertyNames = dflt;
        }
    }

    /**
     * Get the default detect data structure loops policy.
     * Accessible via MBean server.
     *
     * @return The default detect data structure loops policy.
     * @see JSONConfig#isDetectDataStructureLoops()
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
     * @see JSONConfig#setDetectDataStructureLoops(boolean)
     */
    @Override
    public void setDetectDataStructureLoops( boolean dflt )
    {
        synchronized ( getClass() ){
            detectDataStructureLoops = dflt;
        }
    }

    /**
     * Get the default escape bad identifier code points policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return The default escape bad identifier code points policy.
     * @see JSONConfig#isEscapeBadIdentifierCodePoints()
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
     * @see JSONConfig#setEscapeBadIdentifierCodePoints(boolean)
     */
    @Override
    public void setEscapeBadIdentifierCodePoints( boolean dflt )
    {
        synchronized ( getClass() ){
            escapeBadIdentifierCodePoints = dflt;
        }
    }

    /**
     * Get the full JSON identifier code points policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return the fullJSONIdentifierCodePoints
     * @see JSONConfig#isFullJSONIdentifierCodePoints()
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
     * @see JSONConfig#setFullJSONIdentifierCodePoints(boolean)
     */
    @Override
    public void setFullJSONIdentifierCodePoints( boolean dflt )
    {
        synchronized ( getClass() ){
            fullJSONIdentifierCodePoints = dflt;
            if ( fullJSONIdentifierCodePoints ){
                quoteIdentifier = true;
            }
        }
    }

    /**
     * Get the fastStrings policy.
     *
     * @return the fastStrings policy
     * @see JSONConfig#isFastStrings()
     */
    @Override
    public boolean isFastStrings()
    {
        return fastStrings;
    }

    /**
     * If true, then string values will be copied to the output with no escaping
     * or validation.
     * <p>
     * Only use this if you know that you have no unescaped characters in the
     * range U+0000-U+001F or unescaped backslash or forward slash or double
     * quote in your strings. If you want your JSON to be parsable by Javascript
     * eval() then you also need to make sure that you don't have U+2028 (line
     * separator) or U+2029 (paragraph separator).
     * <p>
     * That said, if you are encoding a lot of large strings, this can improve
     * performance by eliminating the check for characters that need to be
     * escaped.
     *
     * @param dflt If true, then string values will be copied as is with no escaping
     *            or validation.
     * @see JSONConfig#setFastStrings(boolean)
     */
    @Override
    public void setFastStrings( boolean dflt )
    {
        synchronized ( getClass() ){
            fastStrings = dflt;
        }
    }

    /**
     * Get the default encode numeric strings as numbers policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return The default encode numeric strings as numbers policy.
     * @see JSONConfig#isEncodeNumericStringsAsNumbers()
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
     * @see JSONConfig#setEncodeNumericStringsAsNumbers(boolean)
     */
    @Override
    public void setEncodeNumericStringsAsNumbers( boolean dflt )
    {
        synchronized ( getClass() ){
            encodeNumericStringsAsNumbers = dflt;
        }
    }

    /**
     * Get the default escape non-ASCII policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return The default quote non-ASCII policy.
     * @see JSONConfig#isEscapeNonAscii()
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
     * @see JSONConfig#setEscapeNonAscii(boolean)
     */
    @Override
    public void setEscapeNonAscii( boolean dflt )
    {
        synchronized ( getClass() ){
            escapeNonAscii = dflt;
            if ( escapeNonAscii ){
                escapeSurrogates = false;
            }
        }
    }

    /**
     * Get the default escape surrogates policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return the escape surrogates policy.
     * @see JSONConfig#isEscapeSurrogates()
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
     * @see JSONConfig#setEscapeSurrogates(boolean)
     */
    @Override
    public void setEscapeSurrogates( boolean dflt )
    {
        synchronized ( getClass() ){
            escapeSurrogates = dflt;
            if ( escapeSurrogates ){
                escapeNonAscii = false;
            }
        }
    }

    /**
     * Get the default unEscape policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return the unEscape policy.
     * @see JSONConfig#isUnEscapeWherePossible()
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
     * @see JSONConfig#setUnEscapeWherePossible(boolean)
     */
    @Override
    public void setUnEscapeWherePossible( boolean dflt )
    {
        synchronized ( getClass() ){
            unEscapeWherePossible = dflt;
        }
    }

    /**
     * Get the pass through escapes policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return The pass through escapes policy.
     * @see JSONConfig#isPassThroughEscapes()
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
     * @see JSONConfig#setPassThroughEscapes(boolean)
     */
    @Override
    public void setPassThroughEscapes( boolean dflt )
    {
        synchronized ( getClass() ){
            passThroughEscapes = dflt;
        }
    }

    /**
     * Get the encode dates as strings policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return the encodeDatesAsStrings policy.
     * @see JSONConfig#isEncodeDatesAsStrings()
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
     * @see JSONConfig#setEncodeDatesAsStrings(boolean)
     */
    @Override
    public synchronized void setEncodeDatesAsStrings( boolean dflt )
    {
        synchronized ( getClass() ){
            encodeDatesAsStrings = dflt;
            if ( encodeDatesAsStrings ){
                encodeDatesAsObjects = false;
            }
        }
    }

    /**
     * Get the default reflection of unknown objects policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return the reflectUnknownObjects policy.
     * @see JSONConfig#isReflectUnknownObjects()
     * @since 1.9
     */
    @Override
    public boolean isReflectUnknownObjects()
    {
        return reflectUnknownObjects;
    }

    /**
     * Set the default unknown object reflection encoding policy. If true, then
     * any time that an unknown object is encountered, this package will attempt
     * to use reflection to encode it. Default is false. When false, then
     * unknown objects will have their toString() method called.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then attempt to use reflection to encode objects
     *            which are otherwise unknown.
     * @see #addReflectClass(Object)
     * @see #addReflectClasses(Collection)
     * @see #addReflectClassByName(String)
     * @see JSONConfig#setReflectUnknownObjects(boolean)
     * @since 1.9
     */
    @Override
    public void setReflectUnknownObjects( boolean dflt )
    {
        synchronized ( getClass() ){
            reflectUnknownObjects = dflt;
        }
    }

    /**
     * Get the default preciseIntegers policy.
     *
     * @return The preciseIntegers policy.
     * @see JSONConfig#isPreciseNumbers()
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
     * @see JSONConfig#setPreciseNumbers(boolean)
     * @since 1.9
     */
    @Override
    public void setPreciseNumbers( boolean dflt )
    {
        synchronized ( getClass() ){
            preciseNumbers = dflt;
        }
    }

    /**
     * Get the default smallNumbers policy.
     *
     * @return The smallNumbers policy.
     * @see JSONConfig#isSmallNumbers()
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
     * @see JSONConfig#setSmallNumbers(boolean)
     * @since 1.9
     */
    @Override
    public void setSmallNumbers( boolean dflt )
    {
        synchronized ( getClass() ){
            smallNumbers = dflt;
        }
    }

    /**
     * The default primitive arrays policy.
     *
     * @return the usePrimitiveArrays policy.
     * @see JSONConfig#isUsePrimitiveArrays()
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
     * @see JSONConfig#setUsePrimitiveArrays(boolean)
     * @since 1.9
     */
    @Override
    public void setUsePrimitiveArrays( boolean dflt )
    {
        synchronized ( getClass() ){
            usePrimitiveArrays = dflt;
        }
    }

    /**
     * Get the default cacheReflectionData policy.
     *
     * @return the cacheReflectionData policy.
     * @see JSONConfig#isCacheReflectionData()
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
     * @see JSONConfig#setCacheReflectionData(boolean)
     * @since 1.9
     */
    @Override
    public void setCacheReflectionData( boolean dflt )
    {
        synchronized ( getClass() ){
            cacheReflectionData = dflt;
            if ( cacheReflectionData == false ){
                ReflectedObjectMapBuilder.clearReflectionCache();
            }
        }
    }

    /**
     * Get the default quote identifier policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return The default quote identifier policy.
     * @see JSONConfig#isQuoteIdentifier()
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
     * @see JSONConfig#setQuoteIdentifier(boolean)
     */
    @Override
    public void setQuoteIdentifier( boolean dflt )
    {
        synchronized ( getClass() ){
            quoteIdentifier = fullJSONIdentifierCodePoints || dflt;
        }
    }

    /**
     * Get the default escape ECMAScript 6 code points policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return The default escape ECMAScript 6 code points policy.
     * @see JSONConfig#isUseECMA6()
     */
    @Override
    public boolean isUseECMA6()
    {
        return useECMA6;
    }

    /**
     * If you set this to true, then by default when JSONUtil generates Unicode
     * escapes, it will use ECMAScript 6 code point escapes if they are shorter
     * than code unit escapes. This is not standard JSON and not yet widely
     * supported by Javascript interpreters. It also allows identifiers to have
     * letter numbers in addition to other letters.  Default is false.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, use EMCAScript 6 code point escapes and allow
     * ECMAScript 6 identifier character set.
     * @see JSONConfig#setUseECMA6(boolean)
     */
    @Override
    public void setUseECMA6( boolean dflt )
    {
        synchronized ( getClass() ){
            useECMA6 = dflt;
        }
    }

    /**
     * Get the default for allowing reserved words in identifiers.
     * <p>
     * Accessible via MBean server.
     *
     * @return the reserverd words in identifiers policy.
     * @see JSONConfig#isAllowReservedWordsInIdentifiers()
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
     * @see JSONConfig#setAllowReservedWordsInIdentifiers(boolean)
     */
    @Override
    public void setAllowReservedWordsInIdentifiers( boolean dflt )
    {
        synchronized ( getClass() ){
            allowReservedWordsInIdentifiers = dflt;
        }
    }

    /**
     * Get the encode dates as objects policy.
     * <p>
     * Accessible via MBean server.
     *
     * @return the encodeDatesAsObjects policy.
     * @see JSONConfig#isEncodeDatesAsObjects()
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
     * @see JSONConfig#setEncodeDatesAsObjects(boolean)
     */
    @Override
    public synchronized void setEncodeDatesAsObjects( boolean dflt )
    {
        synchronized ( getClass() ){
            encodeDatesAsObjects = dflt;
            if ( encodeDatesAsObjects ){
                encodeDatesAsStrings = false;
            }
        }
    }

    private static final long serialVersionUID = 1L;
}
