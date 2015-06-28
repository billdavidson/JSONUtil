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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
 *   standard JSON.  Be careful about changing these.  They should
 *   work fine if the JSON is interpreted by a standard Javascript
 *   eval(), except ECMA 6 code points if your interpreter doesn't
 *   support those.  Going non-default on any of these tends not to
 *   work in strict JSON parsers.
 * </h3>
 * <ul>
 *   <li>quoteIdentifier = true</li>
 *   <li>useECMA6CodePoints = false</li>
 *   <li>allowReservedWordsInIdentifiers = false</li>
 * </ul>
 * <p>
 * You can change the locale being used for error messages and
 * possibly by JSONAble objects for encoding.
 * <p>
 * You can create number formats associated with specific numeric
 * types if you want your numbers encoded in a certain way.
 *
 * @see JSONConfigDefaults
 * @author Bill Davidson
 */
public class JSONConfig implements Serializable
{
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
    private Map<Class<? extends Number>,NumberFormat> fmtMap;

    // various flags.  see their setters.
    private boolean validatePropertyNames;
    private boolean detectDataStructureLoops;
    private boolean escapeBadIdentifierCodePoints;

    private boolean encodeNumericStringsAsNumbers;
    private boolean escapeNonAscii;
    private boolean unEscapeWherePossible;
    private boolean escapeSurrogates;

    private boolean quoteIdentifier;
    private boolean useECMA6CodePoints;
    private boolean allowReservedWordsInIdentifiers;

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
        JSONConfigDefaults dflt = JSONConfigDefaults.getInstance();

        // validation options.
        validatePropertyNames = dflt.isValidatePropertyNames();
        detectDataStructureLoops = dflt.isDetectDataStructureLoops();
        escapeBadIdentifierCodePoints = dflt.isEscapeBadIdentifierCodePoints();

        // various escape options.
        encodeNumericStringsAsNumbers = dflt.isEncodeNumericStringsAsNumbers();
        escapeNonAscii = dflt.isEscapeNonAscii();
        unEscapeWherePossible = dflt.isUnEscapeWherePossible();
        escapeSurrogates = dflt.isEscapeSurrogates();

        // non-standard JSON options.
        quoteIdentifier = dflt.isQuoteIdentifier();
        useECMA6CodePoints = dflt.isUseECMA6CodePoints();
        allowReservedWordsInIdentifiers = dflt.isAllowReservedWordsInIdentifiers();

        synchronized ( JSONConfigDefaults.class ){
            Map<Class<? extends Number>,NumberFormat> defMap = JSONConfigDefaults.getFormatMap();
            fmtMap = defMap != null ? new HashMap<Class<? extends Number>,NumberFormat>(defMap) : null;
        }

        setLocale(locale);

        objStack = detectDataStructureLoops ? new ArrayList<Object>() : null;
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
     * Clear the objStack.
     */
    void clearObjStack()
    {
        if ( objStack != null ){
            objStack.clear();
        }
    }

    /**
     * Get the locale.
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
     * Add a number format for a particular type that extends Number. You can
     * set one format per type that extends Number. All JSON conversions that
     * use this config will use the given format for output of numbers of the
     * given class.
     * <p>
     * This could allow you to limit the number of digits printed for a float
     * or a double for example, getting rid of excess digits caused by rounding
     * problems in floating point numbers.
     *
     * @param clazz The class.
     * @param fmt The number format.
     */
    public synchronized void addNumberFormat( Class<? extends Number> clazz, NumberFormat fmt )
    {
        if ( clazz != null ){
            if ( fmtMap == null ){
                // don't create the map unless it's actually going to be used.
                fmtMap = new HashMap<Class<? extends Number>,NumberFormat>();
            }
            fmtMap.put(clazz, fmt);
        }
    }

    /**
     * Get the number format for the given class.
     *
     * @param numericClass A class.
     * @return A number format or null if one has not been set.
     */
    public synchronized NumberFormat getNumberFormat( Class<? extends Number> numericClass )
    {
        return fmtMap != null ? fmtMap.get(numericClass) : null;
    }

    /**
     * Remove the requested class from the number formats that
     * this config knows about.
     *
     * @param numericClass The class.
     */
    public synchronized void removeNumberFormat( Class<? extends Number> numericClass )
    {
        if ( fmtMap != null ){
            fmtMap.remove(numericClass);
            if ( fmtMap.size() < 1 ){
                fmtMap = null;
            }
        }
    }

    /**
     * Clear all number formats.
     */
    public synchronized void clearNumberFormats()
    {
        fmtMap = null;
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
     * When validation is enabled, only code points which are allowed in
     * identifiers will be permitted as per the ECMAScript 5.1 standard as
     * well as disallowing reserved words as per the JSON spec.  It will also
     * check for duplicate property names in the same object, which is possible
     * because keys in maps are not required to be String objects and it's
     * possible (though not likely) for two objects which are not equal to have
     * the same result from a toString() method.
     * <p>
     * If this is set to false and you have a map with a null key,
     * you will get a {@link NullPointerException}.
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
     * do from a JSONAble object. It could break the detection system.
     *
     * @param detectDataStructureLoops If true then JSONUtil will attempt to detect loops in data structures.
     */
    public void setDetectDataStructureLoops( boolean detectDataStructureLoops )
    {
        if ( detectDataStructureLoops && objStack == null ){
            objStack = new ArrayList<Object>();
        }
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
     * If true, then any bad code points in identifiers will be quoted.
     * Default is false.  This only works if {@link #isValidatePropertyNames()}
     * returns true.  If validation is disabled then bad code points can't be
     * detected and so can't be escaped.
     *
     * @param escapeBadIdentifierCodePoints the escapeBadIdentifierCodePoints to set
     */
    public void setEscapeBadIdentifierCodePoints( boolean escapeBadIdentifierCodePoints )
    {
        this.escapeBadIdentifierCodePoints = escapeBadIdentifierCodePoints;
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
     * look like numbers, then they won't be quoted.  Default is false.
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
     * If you want non-ascii characters encoded as Unicode escapes, you can do
     * that by setting this to true. Default is false. One reason that you might
     * want to do this is when debugging code that is working with code points
     * for which you do not have a usable font.
     *
     * @param escapeNonAscii set to true if you want non-ascii to be Unicode escaped.
     */
    public void setEscapeNonAscii( boolean escapeNonAscii )
    {
        this.escapeNonAscii = escapeNonAscii;
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
     * unmodified, including hex escapes, octal escapes and ECMA 6 code point
     * escapes, all of which are not allowed by the JSON standard. When true,
     * then escapes are converted to UTF-16 before being put through the normal
     * escape process so that unnecessary escapes are removed and escapes that
     * are needed but aren't allowed in the JSON standard are converted to
     * Unicode code unit escapes. This might be useful if you're reading your
     * strings from a file or database that has old style escapes in it.
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
     * If true then all surrogates will be escaped.
     *
     * @param escapeSurrogates the escapeSurrogates to set
     */
    public void setEscapeSurrogates( boolean escapeSurrogates )
    {
        this.escapeSurrogates = escapeSurrogates;
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
     * be just fine with having no quotes. Note that if you use enable using
     * keywords as identifiers and use a keyword as an identifier, that will
     * force quotes anyway. If you use characters that require surrogate pairs,
     * that will also force quotes.
     *
     * @param quoteIdentifier If true, then all identifiers will be quoted. If
     *        false, then only those that really need quotes will be quoted.
     */
    public void setQuoteIdentifier( boolean quoteIdentifier )
    {
        this.quoteIdentifier = quoteIdentifier;
    }

    /**
     * Find out if ECMA 6 code point escapes are enabled.
     *
     * @return The ECMA 6 escape policy.
     */
    public boolean isUseECMA6CodePoints()
    {
        return useECMA6CodePoints;
    }

    /**
     * If you set this to true, then when the JSONUtil generates Unicode
     * escapes, it will use ECMAScript 6 code point escapes if they are shorter
     * than code unit escapes. This is not standard JSON and not yet widely
     * supported by Javascript interpreters. Default is false.
     *
     * @param useECMA6CodePoints If true, use EMCA 6 code point escapes.
     */
    public void setUseECMA6CodePoints( boolean useECMA6CodePoints )
    {
        this.useECMA6CodePoints = useECMA6CodePoints;
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

    @SuppressWarnings("javadoc")
    private static final long serialVersionUID = 1L;
}
