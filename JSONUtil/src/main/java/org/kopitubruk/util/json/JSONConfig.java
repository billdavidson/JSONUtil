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
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

/**
 * A configuration object for JSONUtil to control various encoding options.
 * <p>
 * It is possible to change the defaults by using the
 * {@link JSONConfigDefaults} class.  See that class for details.
 * <p>
 * These are the names of the boolean flags and their normal defaults if you
 * don't change them.  See the setters for these for descriptions of what they
 * do.
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
 *   <li>preciseNumbers = false</li>
 *   <li>smallNumbers = false</li>
 *   <li>usePrimitiveArrays = false</li>
 *   <li>cacheReflectionData = false</li>
 * </ul>
 * <h3>
 *   Allow generation of certain types of non-standard JSON.
 * </h3>
 * <p>
 *   These could cause problems for some things that take JSON.  Defaults
 *   are for standard JSON.  Be careful about changing these.  They should
 *   work fine if the JSON is interpreted by a standard Javascript
 *   eval(), except ECMAScript 6 code points if your interpreter doesn't
 *   support those.  Going non-default on any of these tends not to
 *   work in strict JSON parsers such as JQuery.
 * </p>
 * <ul>
 *   <li>quoteIdentifier = true</li>
 *   <li>useECMA6 = false</li>
 *   <li>allowReservedWordsInIdentifiers = false</li>
 *   <li>encodeDatesAsObjects = false</li>
 * </ul>
 * <p>
 * You can change the locale being used for error messages and
 * log messages and possibly by JSONAble objects for encoding.
 * <p>
 * You can create number formats associated with specific numeric
 * types if you want your numbers encoded in a certain way.
 * <p>
 * If you've enabled special date handling options, you can change
 * the way date strings are generated and the way that they are
 * parsed.
 * <p>
 * This class also keeps track of classes which will be automatically
 * reflected when encountered.
 * <p>
 * This class NOT thread safe.  There are issues with the loop
 * detection logic and any custom number or date formats used.  Never
 * share objects of this class between threads.
 *
 * @see JSONConfigDefaults
 * @author Bill Davidson
 */
public class JSONConfig implements Serializable, Cloneable
{
    private static TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone("UTC");

    /**
     * A locale used for error messages and localization by {@link JSONAble}s if
     * applicable.
     */
    private Locale locale;

    /**
     * Used by JSONUtil to detect data structure loops.
     */
    private List<Object> objStack;

    /**
     * Optional number formats mapped from different number types.
     */
    private Map<Class<? extends Number>,NumberFormat> numberFormatMap = null;

    /**
     * A date formatter when encoding Dates.
     */
    private DateFormat dateGenFormat;

    /**
     * Custom date parsing formats.
     */
    private List<DateFormat> customDateParseFormats = null;

    /**
     * Custom date parsing formats plus ISO 8601 parsing formats.
     */
    private List<DateFormat> dateParseFormats = null;

    /**
     * The set of classes that reflection should be used for.
     */
    private Map<Class<?>,JSONReflectedClass> reflectClasses = null;

    /**
     * Indent padding object.
     */
    private IndentPadding indentPadding = null;

    /**
     * The privacy level for reflection.
     */
    private int reflectionPrivacy = ReflectUtil.PUBLIC;

    // various flags.  see their setters.
    private boolean validatePropertyNames;
    private boolean detectDataStructureLoops;
    private boolean escapeBadIdentifierCodePoints;
    private boolean fullJSONIdentifierCodePoints;

    private boolean encodeNumericStringsAsNumbers;
    private boolean escapeNonAscii;
    private boolean unEscapeWherePossible;
    private boolean escapeSurrogates;
    private boolean passThroughEscapes;
    private boolean encodeDatesAsStrings;
    private boolean reflectUnknownObjects;
    private boolean preciseNumbers;
    private boolean smallNumbers;
    private boolean usePrimitiveArrays;
    private boolean cacheReflectionData;

    private boolean quoteIdentifier;
    private boolean useECMA6;
    private boolean allowReservedWordsInIdentifiers;
    private boolean encodeDatesAsObjects;

    /**
     * Create a JSONConfig
     */
    public JSONConfig()
    {
        this(null);
    }

    /**
     * Create a JSONConfig with the given locale.
     *
     * @param locale the locale to set.
     */
    public JSONConfig( Locale locale )
    {
        JSONConfigDefaults.initJSONConfig(this, locale);

        objStack = detectDataStructureLoops ? new ArrayList<>() : null;
    }

    /**
     * Only used by {@link #clone()}. Initializes nothing since clone() will
     * initialize everything itself. Avoids the overhead from the normal
     * constructor including synchronizing on the entire
     * {@link JSONConfigDefaults} class.
     *
     * @param placeHolder dummy argument to get a different signature.
     */
    private JSONConfig( boolean placeHolder )
    {
    }

    /**
     * Return a clone of this object.  Note that this is unsynchronized,
     * so code accordingly.  This is a deep clone so any date or number formats
     * defined for this instance will also be cloned.
     *
     * @return a clone of this object.
     */
    @Override
    public JSONConfig clone()
    {
        JSONConfig result = new JSONConfig(true);

        result.objStack = objStack == null ? null : new ArrayList<>();
        result.locale = locale;

        // NumberFormat and DateFormat are not thread safe so clone them.

        if ( numberFormatMap != null ){
            result.numberFormatMap = new HashMap<>(numberFormatMap);
            for ( Entry<?,NumberFormat> entry : result.numberFormatMap.entrySet() ){
                entry.setValue((NumberFormat)entry.getValue().clone());
            }
        }else{
            result.numberFormatMap = null;
        }

        result.dateGenFormat = dateGenFormat == null ? null : (DateFormat)dateGenFormat.clone();

        if ( customDateParseFormats != null ){
            result.customDateParseFormats = new ArrayList<>(customDateParseFormats.size());
            for ( DateFormat fmt : customDateParseFormats ){
                result.customDateParseFormats.add((DateFormat)fmt.clone());
            }
        }else{
            result.customDateParseFormats = null;
        }

        if ( reflectClasses == null ){
            result.reflectClasses = null;
        }else{
            Collection<JSONReflectedClass> refClasses = reflectClasses.values();
            List<JSONReflectedClass> refCopy = new ArrayList<>(refClasses.size());
            for ( JSONReflectedClass refClass : refClasses ){
                refCopy.add(refClass.clone());
            }
            result.addReflectClasses(refCopy);
        }

        result.indentPadding = indentPadding == null ? null : indentPadding.clone();

        // this will just be regenerated on the next call if needed.
        result.dateParseFormats = null;

        result.reflectionPrivacy = reflectionPrivacy;

        // validation options.
        result.validatePropertyNames = validatePropertyNames;
        result.detectDataStructureLoops = detectDataStructureLoops;
        result.escapeBadIdentifierCodePoints = escapeBadIdentifierCodePoints;
        result.fullJSONIdentifierCodePoints = fullJSONIdentifierCodePoints;

        // "safe" alternate encoding options.
        result.encodeNumericStringsAsNumbers = encodeNumericStringsAsNumbers;
        result.escapeNonAscii = escapeNonAscii;
        result.unEscapeWherePossible = unEscapeWherePossible;
        result.escapeSurrogates = escapeSurrogates;
        result.passThroughEscapes = passThroughEscapes;
        result.encodeDatesAsStrings = encodeDatesAsStrings;
        result.reflectUnknownObjects = reflectUnknownObjects;
        result.preciseNumbers = preciseNumbers;
        result.smallNumbers = smallNumbers;
        result.usePrimitiveArrays = usePrimitiveArrays;
        result.cacheReflectionData = cacheReflectionData;

        // non-standard JSON.
        result.quoteIdentifier = quoteIdentifier;
        result.useECMA6 = useECMA6;
        result.allowReservedWordsInIdentifiers = allowReservedWordsInIdentifiers;
        result.encodeDatesAsObjects = encodeDatesAsObjects;

        return result;
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
     * Clear the object stack.
     */
    void clearObjStack()
    {
        if ( objStack != null ){
            objStack.clear();
        }
    }

    /**
     * Get the locale for this instance.
     *
     * @return the locale
     */
    public Locale getLocale()
    {
        return locale;
    }

    /**
     * Set the locale.  This can be used for error messages or {@link JSONAble}'s
     * that can use a locale.
     *
     * @param locale the locale to set
     */
    public void setLocale( Locale locale )
    {
        this.locale = locale != null ? locale : JSONConfigDefaults.getLocale();
    }

    /**
     * Get a copy of the number format map.
     *
     * @return A copy of the number format map.
     */
    Map<Class<? extends Number>,NumberFormat> getNumberFormats()
    {
        return numberFormatMap;
    }

    /**
     * Get the number format for the given class.
     *
     * @param numericClass A class.
     * @return A number format or null if one has not been set.
     */
    public NumberFormat getNumberFormat( Class<? extends Number> numericClass )
    {
        return numberFormatMap != null ? numberFormatMap.get(numericClass) : null;
    }

    /**
     * Get the number format for the class of the given numeric type.
     *
     * @param num An object that implements {@link Number}.
     * @return A number format or null if one has not been set.
     */
    public NumberFormat getNumberFormat( Number num )
    {
        return num != null ? getNumberFormat(num.getClass()) : null;
    }

    /**
     * Add a number format for a particular type that extends Number. You can
     * set one format per type that extends Number. All JSON conversions that
     * use this config will use the given format for output of numbers of the
     * given class.
     * <p>
     * This could allow you to limit the number of digits printed for a float
     * or a double for example, getting rid of excess digits caused by rounding
     * problems in floating point numbers.
     *
     * @param numericClass The class.
     * @param fmt The number format.
     */
    public void addNumberFormat( Class<? extends Number> numericClass, NumberFormat fmt )
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
     * Add a number format for a particular type that extends Number. You can
     * set one format per type that extends Number. All JSON conversions that
     * use this config will use the given format for output of numbers of the
     * given class.
     * <p>
     * This could allow you to limit the number of digits printed for a float
     * or a double for example, getting rid of excess digits caused by rounding
     * problems in floating point numbers.
     *
     * @param numericType The object.
     * @param fmt The number format.
     */
    public void addNumberFormat( Number numericType, NumberFormat fmt )
    {
        if ( numericType != null ){
            addNumberFormat(numericType.getClass(), fmt);
        }
    }

    /**
     * Add a map of number formats to the current map of number formats.
     *
     * @param numFmtMap The input map.
     * @since 1.4
     */
    public void addNumberFormats( Map<Class<? extends Number>,NumberFormat> numFmtMap )
    {
        numberFormatMap = JSONConfigUtil.mergeFormatMaps(numberFormatMap, numFmtMap);
    }

    /**
     * Remove the requested class from the number formats that
     * this config knows about.
     *
     * @param numericClass The class.
     */
    public void removeNumberFormat( Class<? extends Number> numericClass )
    {
        if ( numberFormatMap != null && numericClass != null ){
            int size = numberFormatMap.size();
            numberFormatMap.remove(numericClass);
            if ( numberFormatMap.size() < 1 ){
                numberFormatMap = null;
            }else if ( numberFormatMap.size() < size ){
                // minimize memory usage.
                numberFormatMap = new HashMap<>(numberFormatMap);
            }
        }
    }

    /**
     * Remove the requested class from the number formats that
     * this config knows about.
     *
     * @param num An object that implements {@link Number}.
     */
    public void removeNumberFormat( Number num )
    {
        if ( num != null ){
            removeNumberFormat(num.getClass());
        }
    }

    /**
     * Clear all number formats.
     */
    public void clearNumberFormats()
    {
        numberFormatMap = null;
    }

    /**
     * Get the date formatter for generating date strings when encodeDatesAsStrings
     * or encodeDatesAsObjects are true. If you did not set the date formatter
     * with {@link #setDateGenFormat(DateFormat)}, and a default format has not
     * been set, then it will return an ISO 8601 extended format
     * formatter: yyyy-MM-dd'T'HH:mm:ss.sss'Z'.
     *
     * @return The formatter.
     */
    DateFormat getDateGenFormat()
    {
        if ( dateGenFormat == null ){
            // don't create it until it's needed.
            dateGenFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'");
            dateGenFormat.setTimeZone(UTC_TIME_ZONE);
        }
        return dateGenFormat;
    }

    /**
     * Set the date string generation format used when encodeDatesAsStrings or
     * encodeDatesAsObjects are true. If you do not set the date formatter, it
     * will use an ISO 8601 extended format formatter will be used when formatting
     * dates.  The format is yyyy-MM-dd'T'HH:mm:ss.sss'Z'
     *
     * @param fmt the dateFormat to set
     * @since 1.4
     */
    public void setDateGenFormat( DateFormat fmt )
    {
        dateGenFormat = fmt == null ? null : (DateFormat)fmt.clone();
    }

    /**
     * Set the date string generation format.
     *
     * @param fmtStr passed to the constructor
     * {@link SimpleDateFormat#SimpleDateFormat(String,Locale)}
     * using the locale from this config object.
     * @return The format that was created.
     * @since 1.4
     */
    public DateFormat setDateGenFormat( String fmtStr )
    {
        DateFormat fmt = null;
        if ( fmtStr != null ){
            fmt = new SimpleDateFormat(fmtStr, locale);
            setDateGenFormat(fmt);
        }else{
            dateGenFormat = null;
        }
        return fmt;
    }

    /**
     * Clear date generation format.
     *
     * @since 1.4
     */
    public void clearDateGenFormat()
    {
        dateGenFormat = null;
    }

    /**
     * Get the date parsing formats to parse different accepted forms of ISO
     * 8601. If any custom date formats were added, then those will be
     * prepended to the list and tried before the ISO 8601 formats when parsing.
     *
     * @return The list of date parse formats.
     */
    List<DateFormat> getDateParseFormats()
    {
        if ( dateParseFormats == null ){
            // don't create it until it's needed.
            String dateTime = "yyyy-MM-dd'T'HH:mm:ss";
            String offset = "XXX";
            String millis = ".SSS";

            // The ISO 8601 formats.
            List<DateFormat> isoFmts = Arrays.asList(
                    new SimpleDateFormat(dateTime+millis+offset),
                    new SimpleDateFormat(dateTime+millis),
                    new SimpleDateFormat(dateTime+offset),
                    new SimpleDateFormat(dateTime));

            for ( DateFormat fmt : isoFmts ){
                fmt.setTimeZone(UTC_TIME_ZONE);
            }

            if ( customDateParseFormats == null ){
                // It's just the ISO 8601 formats.
                dateParseFormats = isoFmts;
            }else{
                // Custom formats followed by ISO 8601 formats.
                dateParseFormats = new ArrayList<>(customDateParseFormats.size() + isoFmts.size());
                dateParseFormats.addAll(customDateParseFormats);
                dateParseFormats.addAll(isoFmts);
            }
        }
        return dateParseFormats;
    }

    /**
     * Add a date parsing format to the list of parsing formats.  When
     * parsing date strings, they will be tried in the same order that
     * they were added until one works.
     *
     * @param fmt A date parsing format.
     * @since 1.4
     */
    public void addDateParseFormat( DateFormat fmt )
    {
        if ( fmt != null ){
            addDateParseFormats(Arrays.asList(fmt));
        }
    }

    /**
     * Add a date parsing format to the list of parsing formats.  When
     * parsing date strings, they will be tried in the same order that
     * they were added until one works.
     *
     * @param fmtStr Passed to {@link SimpleDateFormat#SimpleDateFormat(String,Locale)}
     * using the locale from this config object to create a DateFormat.
     * @return The format that gets created.
     * @since 1.4
     */
    public DateFormat addDateParseFormat( String fmtStr )
    {
        DateFormat fmt = new SimpleDateFormat(fmtStr, locale);
        addDateParseFormat(fmt);
        return fmt;
    }

    /**
     * Add a collection of date parsing formats to the list of date parsing
     * formats.
     *
     * @param fmts A collection of date parsing formats.
     * @since 1.4
     */
    public void addDateParseFormats( Collection<? extends DateFormat> fmts )
    {
        customDateParseFormats = JSONConfigUtil.addDateParseFormats(customDateParseFormats, fmts);

        // make sure that custom formats get included in the future.
        dateParseFormats = null;
    }

    /**
     * Clear any date parse formats.
     *
     * @since 1.4
     */
    public void clearDateParseFormats()
    {
        customDateParseFormats = null;
        dateParseFormats = null;
    }

    /**
     * Get the padding object.
     *
     * @return the padding object.
     * @since 1.7
     */
    public IndentPadding getIndentPadding()
    {
        return indentPadding;
    }

    /**
     * Set the padding object.
     *
     * @param indentPadding the padding object.
     * @since 1.7
     */
    public void setIndentPadding( IndentPadding indentPadding )
    {
        this.indentPadding = indentPadding;
    }

    /**
     * Get the reflection privacy level.
     *
     * @return the reflection privacy level.
     * @since 1.9
     */
    public int getReflectionPrivacy()
    {
        return reflectionPrivacy;
    }

    /**
     * Set the privacy level for reflection. Default is
     * {@link ReflectUtil#PUBLIC}.
     *
     * @param reflectionPrivacy the level to set
     * @see ReflectUtil#PRIVATE
     * @see ReflectUtil#PACKAGE
     * @see ReflectUtil#PROTECTED
     * @see ReflectUtil#PUBLIC
     * @since 1.9
     */
    public void setReflectionPrivacy( int reflectionPrivacy )
    {
        this.reflectionPrivacy = ReflectUtil.confirmPrivacyLevel(reflectionPrivacy, this);
    }

    /**
     * Return true if the given class is in the set of classes being
     * automatically reflected.
     *
     * @param refClass The class.
     * @return true if the class is automatically reflected.
     * @since 1.9
     */
    public boolean isReflectClass( JSONReflectedClass refClass )
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
    public boolean isReflectClass( Object obj )
    {
        return obj == null ? false : isReflectClass(ReflectUtil.ensureReflectedClass(obj));
    }

    /**
     * Get the {@link JSONReflectedClass} for the given object or create a dummy
     * one if there isn't one.  Creating one does not affect the results of the
     * isReflectClass() methods.  If you didn't add one then it isn't stored.
     *
     * @param obj The class to look up.
     * @return the reflected class object.
     */
    public JSONReflectedClass ensureReflectedClass( Object obj )
    {
        JSONReflectedClass result = null;
        if ( reflectClasses == null ){
            result = ReflectUtil.ensureReflectedClass(obj);
        }else if ( obj instanceof JSONReflectedClass ){
            result = (JSONReflectedClass)obj;
        }else{
            result = reflectClasses.get(ReflectUtil.getClass(obj));
            if ( result == null ){
                result = ReflectUtil.ensureReflectedClass(obj);
            }
        }
        return result;
    }

    /**
     * Get the {@link JSONReflectedClass} for the given object if it is stored.
     *
     * @param obj the class being reflected.
     * @return The JSONReflectedClass object or null if one is not stored.
     */
    public JSONReflectedClass getReflectedClass( Object obj )
    {
        return reflectClasses.get(ReflectUtil.getClass(obj));
    }

    /**
     * Add the class of the given object to the set of classes that
     * automatically get reflected.
     *
     * @param obj The object whose class to add to the reflect list.
     * @since 1.9
     */
    public void addReflectClass( Object obj )
    {
        reflectClasses = JSONConfigUtil.addReflectClass(reflectClasses, obj);
    }

    /**
     * Add the classes of all of the given objests to the list of classes
     * that automatically get reflected.
     *
     * @param classes The objects to reflect.
     * @since 1.9
     */
    public void addReflectClasses( Collection<?> classes )
    {
        reflectClasses = JSONConfigUtil.addReflectClasses(reflectClasses, classes);
    }

    /**
     * Remove the given class from the list of automatically reflected
     * classes.
     *
     * @param obj An object of the type to be removed from the reflect list.
     * @since 1.9
     */
    public void removeReflectClass( Object obj )
    {
        reflectClasses = JSONConfigUtil.removeReflectClass(reflectClasses, obj);
    }

    /**
     * Remove the given classes from the list of automatically reflected
     * classes.
     *
     * @param classes A collection objects of the types to be removed from
     * the reflect list.
     * @since 1.9
     */
    public void removeReflectClasses( Collection<?> classes )
    {
        reflectClasses = JSONConfigUtil.removeReflectClasses(reflectClasses, classes);
    }

    /**
     * Clear all reflection classes, disabling all automatic reflection.
     *
     * @since 1.9
     */
    public void clearReflectClasses()
    {
        reflectClasses = null;
    }

    /**
     * Check if property names will be validated.
     *
     * @return true if property names are set to be validated.
     */
    public boolean isValidatePropertyNames()
    {
        return validatePropertyNames;
    }

    /**
     * If true, then property names will be validated. Default is true. Setting
     * this to false will speed up generation of JSON but will not make sure
     * that property names are valid. If execution speed of generating JSON is
     * an issue for you, then you may want to do most of your development and
     * testing with this set to true but switch to false when you release.
     * <p>
     * When validation is enabled and fullJSONIdentifierCodePoints is false, then
     * only code points which are allowed in identifiers will be permitted as per
     * the ECMAScript 5 or 6 standard as well as disallowing reserved words as per
     * the JSON spec.  If fullJSONIdentifierCodePoints is true, then all code points
     * permitted by the ECMA JSON standard will be permitted, though many of those
     * are not permitted by the ECMAScript standard and will break if evaluated by
     * a Javascript eval().
     * <p>
     * It will also check for duplicate property names in the same object, which is
     * possible because keys in maps are not required to be String objects and it's
     * possible (though not likely) for two objects which are not equal to have
     * the same result from a toString() method.
     *
     * @param validatePropertyNames Set to false to disable property name validation.
     */
    public void setValidatePropertyNames( boolean validatePropertyNames )
    {
        this.validatePropertyNames = validatePropertyNames;
    }

    /**
     * Return true if data structure loops will be detected.
     *
     * @return true if data structure loops will be detected.
     */
    public boolean isDetectDataStructureLoops()
    {
        return detectDataStructureLoops;
    }

    /**
     * Enable or disable data structure loop detection. Default is true. Do not
     * change this in the middle of a toJSON call, which you could theoretically
     * do from a JSONAble object. It could break the detection system and if you
     * have a loop, you could get recursion until the stack overflows, which
     * would be bad.
     *
     * @param detectDataStructureLoops If true then JSONUtil will attempt to detect loops in data structures.
     */
    public void setDetectDataStructureLoops( boolean detectDataStructureLoops )
    {
        this.detectDataStructureLoops = detectDataStructureLoops;
    }

    /**
     * Find out if bad identifier code points will be escaped.
     *
     * @return the the identifier escape policy.
     */
    public boolean isEscapeBadIdentifierCodePoints()
    {
        return escapeBadIdentifierCodePoints;
    }

    /**
     * If true, then any bad code points in identifiers will be escaped.
     * Default is false.
     *
     * @param escapeBadIdentifierCodePoints the escapeBadIdentifierCodePoints to set
     */
    public void setEscapeBadIdentifierCodePoints( boolean escapeBadIdentifierCodePoints )
    {
        this.escapeBadIdentifierCodePoints = escapeBadIdentifierCodePoints;
    }

    /**
     * Get the full JSON identifier code points policy.
     *
     * @return the fullJSONIdentifierCodePoints
     */
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
     *
     * @param fullJSONIdentifierCodePoints If true, then allow all code points permitted by the JSON standard in identifiers.
     */
    public void setFullJSONIdentifierCodePoints( boolean fullJSONIdentifierCodePoints )
    {
        this.fullJSONIdentifierCodePoints = fullJSONIdentifierCodePoints;
        if ( fullJSONIdentifierCodePoints ){
            quoteIdentifier = true;
        }
    }

    /**
     * If true, then strings will be checked for number patterns and if they
     * look like numbers, then they won't be quoted.
     *
     * @return the isEncodeNumericStringsAsNumbers
     */
    public boolean isEncodeNumericStringsAsNumbers()
    {
        return encodeNumericStringsAsNumbers;
    }

    /**
     * If true, then strings will be checked for number patterns and if they
     * look like numbers, then they won't be quoted.  In {@link JSONParser},
     * strings will examined and if they look like numbers, then they will be
     * parsed as numbers in the output.  Default is false.
     *
     * @param encodeNumericStringsAsNumbers the encodeNumericStringsAsNumbers to set
     */
    public void setEncodeNumericStringsAsNumbers( boolean encodeNumericStringsAsNumbers )
    {
        this.encodeNumericStringsAsNumbers = encodeNumericStringsAsNumbers;
    }


    /**
     * Check if non-ascii characters are to be encoded as Unicode escapes.
     *
     * @return true if non-ascii characters should be encoded with Unicode escapes.
     */
    public boolean isEscapeNonAscii()
    {
        return escapeNonAscii;
    }

    /**
     * If you want non-ascii characters encoded as Unicode escapes in strings
     * and identifiers, you can do that by setting this to true. Default is
     * false. One reason that you might want to do this is when debugging code
     * that is working with code points for which you do not have a usable font.
     * If true, then escapeSurrogates will be forced to false (it would be
     * redundant).
     *
     * @param escapeNonAscii set to true if you want non-ascii to be Unicode escaped.
     */
    public void setEscapeNonAscii( boolean escapeNonAscii )
    {
        this.escapeNonAscii = escapeNonAscii;
        if ( escapeNonAscii ){
            escapeSurrogates = false;
        }
    }

    /**
     * The unEscape policy.
     *
     * @return the unEscape policy.
     */
    public boolean isUnEscapeWherePossible()
    {
        return unEscapeWherePossible;
    }

    /**
     * If true then where possible, undo inline escapes in strings.
     * Default is false. When false, escapes in strings are passed through
     * unmodified, including hex escapes, octal escapes and ECMAScript 6 code point
     * escapes, all of which are not allowed by the JSON standard. When true,
     * then escapes are converted to UTF-16 before being put through the normal
     * escape process so that unnecessary escapes are removed and escapes that
     * are needed but aren't allowed in the JSON standard are converted to
     * Unicode code unit escapes. This might be useful if you're reading your
     * strings from a file or database that has old style escapes in it.
     * <p>
     * Note that this does not apply to property names.  It is only applied
     * to string values.
     *
     * @param unEscapeWherePossible If true then where possible, undo inline
     *        escapes in strings.
     */
    public void setUnEscapeWherePossible( boolean unEscapeWherePossible )
    {
        this.unEscapeWherePossible = unEscapeWherePossible;
    }

    /**
     * Return the escape surrogates policy.
     *
     * @return the escape surrogates policy.
     */
    public boolean isEscapeSurrogates()
    {
        return escapeSurrogates;
    }

    /**
     * Get the pass through escapes policy.
     *
     * @return The pass through escapes policy.
     */
    public boolean isPassThroughEscapes()
    {
        return passThroughEscapes;
    }

    /**
     * If true, then escapes in strings will be passed through unchanged.
     * If false, then the backslash that starts the escape will be escaped.
     *
     * @param passThroughEscapes If true, then pass escapes through.
     */
    public void setPassThroughEscapes( boolean passThroughEscapes )
    {
        this.passThroughEscapes = passThroughEscapes;
    }

    /**
     * If true then all surrogates will be escaped in strings and identifiers
     * and escapeNonAscii will be forced to false.
     *
     * @param escapeSurrogates the escapeSurrogates to set
     */
    public void setEscapeSurrogates( boolean escapeSurrogates )
    {
        this.escapeSurrogates = escapeSurrogates;
        if ( escapeSurrogates ){
            this.escapeNonAscii = false;
        }
    }

    /**
     * Get the encode dates as strings policy.
     *
     * @return the encodeDatesAsStrings policy.
     */
    public boolean isEncodeDatesAsStrings()
    {
        return encodeDatesAsStrings;
    }

    /**
     * If true, then {@link Date} objects will be encoded as ISO 8601 date
     * strings or a custom date format if you have called
     * {@link #setDateGenFormat(DateFormat)}. If you set this to true, then
     * encodeDatesAsObjects will be set to false.
     *
     * @param encodeDatesAsStrings the encodeDatesAsStrings to set
     */
    public void setEncodeDatesAsStrings( boolean encodeDatesAsStrings )
    {
        this.encodeDatesAsStrings = encodeDatesAsStrings;
        if ( encodeDatesAsStrings ){
            encodeDatesAsObjects = false;
        }
    }

    /**
     * Get the reflection of unknown objects policy.
     *
     * @return the reflectUnknownObjects policy.
     */
    public boolean isReflectUnknownObjects()
    {
        return reflectUnknownObjects;
    }

    /**
     * Set the reflection encoding policy.  If true, then any time that an
     * unknown object is encountered, this package will attempt to use
     * reflection to encode it.  Default is false.  When false, then unknown
     * objects will have their toString() method called.
     *
     * @param reflectUnknownObjects If true, then attempt to use reflection
     * to encode objects which are otherwise unknown.
     * @since 1.9
     */
    public void setReflectUnknownObjects( boolean reflectUnknownObjects )
    {
        this.reflectUnknownObjects = reflectUnknownObjects;
    }

    /**
     * Get the preciseNumbers policy.
     *
     * @return The preciseNumbers policy.
     * @since 1.9
     */
    public boolean isPreciseNumbers()
    {
        return preciseNumbers;
    }

    /**
     * If true then integer numbers which are not exactly representable by a 64
     * bit double precision floating point number will be quoted in the output.
     * If false, then they will be unquoted, and precision will
     * likely be lost in the interpreter.
     *
     * @param preciseNumbers If true then quote integer numbers that lose precision in 64-bit floating point.
     * @since 1.9
     */
    public void setPreciseNumbers( boolean preciseNumbers )
    {
        this.preciseNumbers = preciseNumbers;
    }

    /**
     * Get the smallNumbers policy.
     *
     * @return The smallNumbers policy.
     * @since 1.9
     */
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
     * @param smallNumbers If true then numbers will be made to use as little memory as possible.
     * @since 1.9
     */
    public void setSmallNumbers( boolean smallNumbers )
    {
        this.smallNumbers = smallNumbers;
    }

    /**
     * The primitive arrays policy.
     *
     * @return the usePrimitiveArrays policy.
     * @since 1.9
     */
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
     * attempt to use the least complex type that does not lose information.
     * You could easily end up with an array of bytes if all of your numbers are
     * integers in the range -128 to 127. This option is meant to save as much
     * memory as possible.
     *
     * @param usePrimitiveArrays if true, then the parser will create arrays of
     *            primitives as applicable.
     * @since 1.9
     */
    public void setUsePrimitiveArrays( boolean usePrimitiveArrays )
    {
        this.usePrimitiveArrays = usePrimitiveArrays;
    }

    /**
     * Get the the cacheReflectionData policy.
     *
     * @return the cacheReflectionData policy.
     * @since 1.9
     */
    public boolean isCacheReflectionData()
    {
        return cacheReflectionData;
    }

    /**
     * If true, then when an object is reflected its reflection data
     * will be cached to improve performance on subsequent reflections
     * of objects of its class.
     *
     * @param cacheReflectionData if true, then cache reflection data.
     * @since 1.9
     */
    public void setCacheReflectionData( boolean cacheReflectionData )
    {
        this.cacheReflectionData = cacheReflectionData;
    }

    /**
     * Find out what the identifier quote policy is.
     *
     * @return If true, then all identifiers will be quoted.
     */
    public boolean isQuoteIdentifier()
    {
        return quoteIdentifier;
    }

    /**
     * Control whether identifiers are quoted or not. By setting this to false,
     * you save two characters per identifier, except for oddball identifiers
     * that contain characters that must be quoted. Default is true, because
     * that's what the ECMA JSON spec says to do. It might have issues in other
     * things that read JSON and need 100% compliance with the spec. In
     * particular, JQuery does NOT like it when you leave out the quotes. It
     * wants the quotes according to the JSON spec. Javascript eval() tends to
     * be just fine with having no quotes. Note that if you enable using
     * keywords as identifiers and use a keyword as an identifier, that will
     * force quotes anyway. If you use characters that require surrogate pairs,
     * that will also force quotes.
     *
     * @param quoteIdentifier If true, then all identifiers will be quoted. If
     *        false, then only those that really need quotes will be quoted.
     */
    public void setQuoteIdentifier( boolean quoteIdentifier )
    {
        this.quoteIdentifier = fullJSONIdentifierCodePoints || quoteIdentifier;
    }

    /**
     * Find out if ECMAScript 6 code point escapes are enabled.
     *
     * @return The ECMAScript 6 policy.
     */
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
     *
     * @param useECMA6 If true, use EMCAScript 6 code point escapes and allow
     * ECMAScript 6 identifier character set.
     */
    public void setUseECMA6( boolean useECMA6 )
    {
        this.useECMA6 = useECMA6;
    }

    /**
     * Get the allowReservedWordsInIdentifiers policy.
     *
     * @return the allowReservedWordsInIdentifiers policy.
     */
    public boolean isAllowReservedWordsInIdentifiers()
    {
        return allowReservedWordsInIdentifiers;
    }

    /**
     * If true then reserved words will be allowed in identifiers even when
     * identifier validation is enabled.  Default is false.
     *
     * @param allowReservedWordsInIdentifiers the allowReservedWordsInIdentifiers to set
     */
    public void setAllowReservedWordsInIdentifiers( boolean allowReservedWordsInIdentifiers )
    {
        this.allowReservedWordsInIdentifiers = allowReservedWordsInIdentifiers;
    }

    /**
     * Get the encode dates policy.
     *
     * @return the encodeDates policy.
     */
    public boolean isEncodeDatesAsObjects()
    {
        return encodeDatesAsObjects;
    }

    /**
     * If true, then {@link Date} objects will be encoded as
     * Javascript dates, using new Date(dateString).  If you
     * set this to true, then encodeDatesAsStrings will be
     * set to false.
     *
     * @param encodeDatesAsObjects the encodeDates to set
     */
    public void setEncodeDatesAsObjects( boolean encodeDatesAsObjects )
    {
        this.encodeDatesAsObjects = encodeDatesAsObjects;
        if ( encodeDatesAsObjects ){
            encodeDatesAsStrings = false;
        }
    }

    /**
     * Find out if special date formatting is enabled.
     *
     * @return true if encodeDatesAsObjects or encodeDatesAsStrings is true.
     */
    public boolean isFormatDates()
    {
        return encodeDatesAsStrings || encodeDatesAsObjects;
    }

    private static final long serialVersionUID = 1L;
}
