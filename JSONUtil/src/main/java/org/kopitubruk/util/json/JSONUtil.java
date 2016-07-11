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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * <p>
 *   This class converts certain common types of objects into JSON using the
 *   static methods {@link #toJSON(Object)}, {@link #toJSON(Object,JSONConfig)},
 *   {@link #toJSON(Object,Writer)} and {@link #toJSON(Object,JSONConfig,Writer)}.
 *   Collections are traversed and encoded, allowing complex data graphs to
 *   be encoded in one call. It's more flexible than the org.json library and in
 *   many cases may allow the use of existing data structures directly without
 *   modification which should reduce both memory and CPU usage.
 * </p>
 * <p>
 *   There are a number of configuration options available by passing a
 *   {@link JSONConfig} object to the toJSON methods.  In most cases, the defaults
 *   are adequate so you don't need to pass a {@link JSONConfig} object if you
 *   don't want to.  You can also change the defaults for some of the options
 *   so that you don't have to pass the object even if you want non-default
 *   behavior.  Keep in mind that doing that affects all new {@link JSONConfig}
 *   objects that are created after that in the same class loader.
 * </p>
 * <p>
 *   This implementation validates property names by default using the
 *   specification from ECMAScript and ECMA JSON standards.  The validation
 *   can be disabled for faster performance.  See {@link JSONConfig}.  Leaving it
 *   on during development and testing is probably advisable.  Note that the
 *   ECMAScript standard for identifiers is more strict than the JSON standard
 *   for identifiers.  If you need full JSON identifiers, then you should
 *   enable fullJSONIdentifierCodePoints in your {@link JSONConfig}.  Keep
 *   in mind that JSON generated that way may not evaluate properly in
 *   Javascript eval().
 * </p>
 * <p>
 *   There is some effort to detect loops in the data structure that would cause
 *   infinite recursion and throw an exception.  This detection is not perfect and
 *   there are some ways to fool it, so try to be careful and not make loops in
 *   your data structures.
 * </p>
 * <h3>
 *   Top level objects which can be sent to the toJSON methods:
 * </h3>
 * <dl>
 *   <dt>{@link Map}s</dt>
 *   <dd>
 *     In most cases, this will be what you will send to the toJSON method. The
 *     {@link Map} becomes a Javascript object with the property names being the
 *     result of the key's toString() method and the values being property values.
 *     The key's toString() must produce valid Javascript/JSON identifiers and the
 *     values can be almost anything.  Note that this is different than
 *     the org.json library which requires the keys to actually be Strings.  If your
 *     key's toString() method does not produce valid Javascript/JSON identifiers
 *     then you can use {@link JSONConfig#setEscapeBadIdentifierCodePoints(boolean)}
 *     to make it so that they are valid identifiers.
 *   </dd>
 *   <dt>{@link ResourceBundle}</dt>
 *   <dd>
 *     Converted to a Javascript object with the keys being the property names
 *     and the values being from {@link ResourceBundle#getObject(String)}.
 *   </dd>
 *   <dt>{@link Iterable}s, {@link Enumeration}s and arrays</dt>
 *   <dd>
 *     These are encoded as Javascript arrays. These can also be top level if you
 *     want an array as your top level structure.  Note that {@link Collection}
 *     is a sub-interface of {@link Iterable}, so all collections are covered by
 *     this.
 *   </dd>
 *   <dt>{@link JSONAble}s</dt>
 *   <dd>
 *     These are objects that know how to convert themselves to JSON. This just
 *     calls their {@link JSONAble#toJSON(JSONConfig,Writer)} method. It is possible
 *     to use these as top level objects or as values inside other objects but it's
 *     kind of redundant to use them as top level.
 *   </dd>
 * </dl>
 * <h3>
 *   Other objects which can commonly be values in {@link Map}s, {@link Iterable}s,
 *   {@link Enumeration}s and arrays.
 * </h3>
 * <dl>
 *   <dt>{@link Number}s</dt>
 *   <dd>
 *     Anything that implements the {@link Number} interface, which is all of the
 *     wrapper objects for primitive numbers and {@link BigInteger} and
 *     {@link BigDecimal}. They normally have their toString() methods called.
 *     Primitive versions of these are also allowed in arrays and will be converted
 *     to their wrapper objects.  These get whatever their object's toString() method
 *     gives.  It is possible to set number formats for any number type in the
 *     JSONConfig. If those are set then they will be used instead of toString().
 *   </dd>
 *   <dt>Boolean's</dt>
 *   <dd>
 *     Encoded as boolean literals.
 *   </dd>
 *   <dt>{@link Date}s</dt>
 *   <dd>
 *     If {@link JSONConfig#isEncodeDatesAsStrings()} returns true, then {@link Date}s
 *     will be encoded as ISO 8601 date strings, suitable for handing to new Date(String)
 *     in Javascript.  The date format can be changed to something else by
 *     {@link JSONConfig#setDateGenFormat(java.text.DateFormat)}.
 *     <p>
 *     If {@link JSONConfig#isEncodeDatesAsObjects()} returns true, then {@link Date}s
 *     will be encoded as a call to the Date constructor in Javascript using an ISO 8601
 *     date string.  This works with Javascript eval().  It probably won't work in most
 *     strict JSON parsers.  The date format can be changed to something else by
 *     {@link JSONConfig#setDateGenFormat(java.text.DateFormat)}.
 *   </dd>
 *   <dt>Any other object</dt>
 *   <dd>
 *     Any other object just gets its toString() method called and it's surrounded
 *     by quotes with escapes used as needed according to the ECMA JSON standard and
 *     escape options from JSONConfig. Usually this will just be for String objects,
 *     but anything that has a toString() that gives you what you want will work.
 *   </dd>
 *   <dt>null</dt>
 *   <dd>
 *     Encoded as the Javascript literal null.
 *   </dd>
 * </dl>
 *
 * @see JSONAble
 * @see JSONConfig
 * @see JSONConfigDefaults
 * @author Bill Davidson
 */
public class JSONUtil
{
    /*
     * Multi-use string.
     */
    static final String NULL = "null";

    /**
     * For strings that are really numbers.  ECMA JSON spec doesn't allow octal,
     * hexadecimal, NaN or Infinity in JSON.  It also doesn't allow for a "+"
     * sign to start a number.
     */
    private static final Pattern JSON_NUMBER_PAT = Pattern.compile("^-?(?:(?:\\d+(?:\\.\\d+)?)|(?:\\.\\d+))(?:[eE][-+]?\\d+)?$");

    /**
     * Check for octal numbers, which aren't allowed in JSON.
     */
    private static final Pattern OCTAL_NUMBER_PAT = Pattern.compile("^-?0[0-7]+$");

    /**
     * <p>
     *   Regular expression which should cover all valid Javascript property
     *   names. Notice the \p{x} notation, which uses
     *   <a href="http://unicode.org/reports/tr18/#General_Category_Property">
     *   Unicode Regular Expressions</a> which are supported by Java regular
     *   expressions. This allows the code to support all valid Unicode characters
     *   which are allowed to be used in Javascript identifiers. The last I
     *   checked that was over 103,000 code points covering pretty much every
     *   conceivable language. About 101,000 of those are covered just by matching
     *   \p{L} (any letter).
     * </p>
     * <p>
     *   The permitted characters are specified by ECMAScript 5.1, Section 7.6.
     * </p>
     * <p>
     *   Permitted starting characters.
     * </p>
     * <ul>
     *   <li>_ - Underscore</li>
     *   <li>$ - Dollar sign</li>
     *   <li>\p{L} - Any Letter</li>
     *   <li>\\u\p{XDigit}{4} - Unicode code unit escape.</li>
     * </ul>
     * <p>
     *   Permitted subsequent characters.
     * </p>
     * <ul>
     *   <li>_ - Underscore</li>
     *   <li>$ - Dollar sign</li>
     *   <li>\p{L} - Any Letter</li>
     *   <li>\p{Nd} - Any decimal digit</li>
     *   <li>\p{Mn} - Non-Spacing Mark</li>
     *   <li>\p{Mc} - Spacing Combining Mark</li>
     *   <li>\p{Pc} - Connector Punctuation</li>
     *   <li>&#92;u200C - Zero Width Non-Joiner</li>
     *   <li>&#92;u200D - Zero Width Joiner</li>
     *   <li>\\u\p{XDigit}{4} - Unicode code unit escape.</li>
     * </ul>
     */
    private static final Pattern VALID_ECMA5_PROPERTY_NAME_PAT =
            Pattern.compile("^(?:[_\\$\\p{L}]|\\\\u\\p{XDigit}{4})" +
                             "(?:[_\\$\\p{L}\\p{Nd}\\p{Mn}\\p{Mc}\\p{Pc}\\u200C\\u200D]|\\\\u\\p{XDigit}{4})*$");

    /**
     * ECMAScript 6 version of VALID_ECMA5_PROPERTY_NAME_PAT.
     * <p>
     *   Adds permitted starting and subsequent characters not allowed in
     *   ECMAScript 5 identifiers.
     * </p>
     * <ul>
     *   <li>\p{Nl} - Any Letter Number</li>
     *   <li>\\u\{\p{XDigit}+\} - Unicode code point escape.</li>
     * </ul>
     */
    private static final Pattern VALID_ECMA6_PROPERTY_NAME_PAT =
            Pattern.compile("^(?:[_\\$\\p{L}\\p{Nl}]|\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})" +
                             "(?:[_\\$\\p{L}\\p{Nl}\\p{Nd}\\p{Mn}\\p{Mc}\\p{Pc}\\u200C\\u200D]|\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})*$");

    /**
     * This pattern is for full JSON identifier names. This is much more
     * permissive than the ECMAScript 5 standard. Property names that use
     * characters not permitted by the ECMAScript standard will not work with
     * Javascript eval() but will work with Javascript JSON.parse().
     */
    private static final Pattern VALID_JSON5_PROPERTY_NAME_PAT =
            Pattern.compile("^([^\u0000-\u001F\\p{Cn}\"/\\\\]|\\\\[bfnrt\\\\/\"]|\\\\u\\p{XDigit}{4})+$");

    /**
     * This pattern is for full JSON identifier names. This is much more
     * permissive than the ECMAScript 6 standard. Property names that use
     * characters not permitted by the ECMAScript standard will not work with
     * Javascript eval() but will work with Javascript JSON.parse().
     */
    private static final Pattern VALID_JSON6_PROPERTY_NAME_PAT =
            Pattern.compile("^([^\u0000-\u001F\\p{Cn}\"/\\\\]|\\\\[bfnrt\\\\/\"]|\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})+$");

    /**
     * This is the set of reserved words from ECMAScript 6. It's a bad practice
     * to use these as property names and not permitted by the JSON standard.
     * They will work as property names with Javascript eval() but not with a
     * strict JSON parser.
     */
    private static final Set<String> RESERVED_WORDS =
            new HashSet<String>(Arrays.asList(
                                    /* keywords for ECMAScript 5.1 */
                          "break", "case", "catch", "continue", "debugger",
                          "default", "delete", "do", "else", "finally", "for",
                          "function", "if", "in", "instanceof", "new", "return",
                          "switch", "this", "throw", "try", "typeof", "var",
                          "void", "while", "with",
                                    /* future reserved words for ECMAScript 5.1 */
                          "class", "const", "enum", "export", "extends",
                          "implements", "import",  "interface", "let", "package",
                          "private", "protected", "public", "static",
                          "super", "yield",
                                    /* future reserved words for ECMAScript 6 */
                          "await",
                                    /* literals */
                          "true", "false", NULL, "undefined", "Infinity", "NaN"));

    /**
     * Convert an object to JSON and return it as a {@link String}. All options
     * will use defaults.
     *
     * @param obj An object to be converted to JSON.
     * @return A JSON string representation of the object.
     */
    public static String toJSON( Object obj )
    {
        return toJSON(obj, (JSONConfig)null);
    }

    /**
     * Convert an object to JSON and return it as a {@link String}.
     *
     * @param obj An object to be converted to JSON.
     * @param cfg A configuration object to use.
     * @return A JSON string representation of the object.
     */
    public static String toJSON( Object obj, JSONConfig cfg )
    {
        Writer json = new StringWriter();
        try{
            toJSON(obj, cfg, json);
        }catch ( IOException e ){
            // won't happen because StringWriter is really just a wrapper around a StringBuffer.
        }
        return json.toString();
    }

    /**
     * Convert an object to JSON and write it to the given {@link Writer}. All
     * options will be default. Using a {@link Writer} may be preferable in
     * servlets, particularly if the data is large because it can use a lot less
     * memory than a {@link java.lang.StringBuffer} by sending the data to the
     * browser via a {@link java.io.BufferedWriter} on the output stream as it
     * is being generated. The downside of that is that you could have an error
     * after data begins being sent, which could result in corrupted partial
     * data being sent to the caller.
     *
     * @param obj An object to be converted to JSON.
     * @param json Something to write the JSON data to.
     * @throws IOException If there is an error on output.
     */
    public static void toJSON( Object obj, Writer json ) throws IOException
    {
        toJSON(obj, null, json);
    }

    /**
     * Convert an object to JSON and write it to the given {@link Writer}. Using
     * a {@link Writer} may be preferable in servlets, particularly if the data
     * is large because it can use a lot less memory than a
     * {@link java.lang.StringBuffer} by sending the data to the browser via a
     * {@link java.io.BufferedWriter} on the output stream as it is being
     * generated. The downside of that is that you could have an error after
     * data begins being sent, which could result in corrupted partial data
     * being sent to the caller.
     *
     * @param obj An object to be converted to JSON.
     * @param cfg A configuration object to use to set various options. If null then defaults will be used.
     * @param json Something to write the JSON data to.
     * @throws IOException If there is an error on output.
     */
    public static void toJSON( Object obj, JSONConfig cfg, Writer json ) throws IOException
    {
        JSONConfig jcfg = cfg == null ? new JSONConfig() : cfg;
        try{
            appendPropertyValue(obj, json, jcfg);
        }catch ( IOException e ){
            // in case the original calling code catches the exception and reuses the JSONConfig.
            jcfg.clearObjStack();
            throw e;
        }catch ( RuntimeException e ){
            // in case the original calling code catches the exception and reuses the JSONConfig.
            jcfg.clearObjStack();
            IndentPadding.reset(jcfg);
            throw e;
        }
    }

    /**
     * <p>
     *   Append the given value to the given writer. There is special handling for
     *   null, {@link Number}s, {@link JSONAble}s, {@link Map}s,
     *   {@link ResourceBundle}s, {@link Iterable}s, {@link Enumeration}s and arrays.
     *   Booleans and null are encoded as Javascript literals.
     *   All other objects just get their toString() methods called, surrounded by
     *   double quotes with internal double quotes escaped.
     * </p>
     * <p>
     *   This method is recursively called on values when handling {@link Map}s,
     *   {@link Iterable}s, {@link Enumeration}s, {@link ResourceBundle}s and arrays.
     * </p>
     *
     * @param propertyValue The value to append.
     * @param json Something to write the JSON data to.
     * @param cfg A configuration object to use to set various options.
     * @throws IOException If there is an error on output.
     */
    private static void appendPropertyValue( Object propertyValue, Writer json, JSONConfig cfg ) throws IOException
    {
        if ( propertyValue == null ){
            json.write(NULL);
        }else if ( isRecursible(propertyValue) ){
            appendRecursiblePropertyValue(propertyValue, json, cfg);
        }else{
            appendSimplePropertyValue(propertyValue, json, cfg);
        }
    }

    /**
     * Return true if the object is recurisble.
     *
     * @param propertyValue The value to check.
     * @return true if the object is recurisble.
     */
    private static boolean isRecursible( Object propertyValue )
    {
        return propertyValue instanceof Iterable ||
               propertyValue instanceof Map ||
               propertyValue instanceof JSONAble ||
               propertyValue instanceof Enumeration ||
               propertyValue instanceof ResourceBundle ||       // typically not recursed but code same as Map.
               propertyValue.getClass().isArray();
    }

    /**
     * Append a recursible property value to the given JSON writer.  This method
     * will be called if and only if isRecursible(propertyValue) returns true.
     *
     * @param propertyValue The value to append.
     * @param json Something to write the JSON data to.
     * @param cfg A configuration object to use to set various options.
     * @throws IOException If there is an error on output.
     */
    private static void appendRecursiblePropertyValue( Object propertyValue, Writer json, JSONConfig cfg ) throws IOException
    {
        // check for loops.
        DataStructureLoopDetector loopDetector = new DataStructureLoopDetector(cfg, propertyValue);

        if ( propertyValue instanceof JSONAble ){
            JSONAble jsonAble = (JSONAble)propertyValue;
            jsonAble.toJSON(cfg, json);
        }else if ( propertyValue instanceof Map || propertyValue instanceof ResourceBundle ){
            appendObjectPropertyValue(propertyValue, json, cfg);
        }else{
            appendArrayPropertyValue(propertyValue, json, cfg);
        }
        loopDetector.popDataStructureStack();
    }

    /**
     * Append a value that will be a JSON object. That is, a {@link Map} or
     * {@link ResourceBundle}
     *
     * @param propertyValue A {@link Map} or {@link ResourceBundle}.
     * @param json Something to write the JSON data to.
     * @param cfg A configuration object to use to set various options.
     * @throws IOException If there is an error on output.
     */
    private static void appendObjectPropertyValue( Object propertyValue, Writer json, JSONConfig cfg ) throws IOException
    {
        Map<?,?> map = getJSONObjectMap(propertyValue);
        Set<String> propertyNames = cfg.isValidatePropertyNames() ? new HashSet<String>(map.size()) : null;
        boolean didStart = false;
        boolean quoteIdentifier = cfg.isQuoteIdentifier();
        boolean havePadding = cfg.getIndentPadding() != null;

        // make a Javascript object with the keys used to generate the property names.
        json.write('{');
        IndentPadding.incPadding(cfg);
        for ( Entry<?,?> property : map.entrySet() ){
            String propertyName = getPropertyName(property.getKey(), cfg, propertyNames);
            Object value = property.getValue();
            boolean extraIndent = havePadding && isRecursible(value);

            if ( didStart ){
                json.write(',');
            }else{
                didStart = true;
            }
            IndentPadding.appendPadding(cfg, json);
            appendPropertyName(propertyName, json, quoteIdentifier);
            IndentPadding.incAppendPadding(cfg, json, extraIndent);
            appendPropertyValue(value, json, cfg);      // recurse on the value.
            IndentPadding.decAppendPadding(cfg, json, extraIndent);
        }
        IndentPadding.decAppendPadding(cfg, json);
        json.write('}');
    }

    /**
     * Append a value that will be a JSON array. That is, a {@link Iterable},
     * {@link Enumeration} or array.
     *
     * @param propertyValue an {@link Iterable}, {@link Enumeration} or array to append.
     * @param json Something to write the JSON data to.
     * @param cfg A configuration object to use to set various options.
     * @throws IOException If there is an error on output.
     */
    private static void appendArrayPropertyValue( Object propertyValue, Writer json, JSONConfig cfg ) throws IOException
    {
        boolean didStart = false;

        json.write('[');
        IndentPadding.incPadding(cfg);
        for ( Object value : new JSONArrayData(propertyValue) ){
            if ( didStart ){
                json.write(',');
            }else{
                didStart = true;
            }
            IndentPadding.appendPadding(cfg, json);
            appendPropertyValue(value, json, cfg);      // recurse on the value.
        }
        IndentPadding.decAppendPadding(cfg, json);
        json.write(']');
    }

    /**
     * Get the data for a JSON object as a Map, even if it's a ResourceBundle.
     *
     * @param mapData A Map or ResourceBundle.
     * @return The map.
     */
    private static Map<?,?> getJSONObjectMap( Object mapData )
    {
        if ( mapData instanceof Map ){
            return (Map<?,?>)mapData;
        }else{
            // make it a map so that code can use an EntrySet.
            ResourceBundle bundle = (ResourceBundle)mapData;
            Map<Object,Object> result = new LinkedHashMap<Object,Object>();
            Enumeration<String> enr = bundle.getKeys();
            while ( enr.hasMoreElements() ){
                String key = enr.nextElement();
                result.put(key, bundle.getObject(key));
            }
            return new LinkedHashMap<Object,Object>(result);
        }
    }

    /**
     * Write a property name to the output.  Includes the colon afterwards.
     *
     * @param propertyName the property name.
     * @param json the writer.
     * @param quoteIdentifier if true, then force quotes
     * @throws IOException if there's an I/O error.
     */
    private static void appendPropertyName( String propertyName, Writer json, boolean quoteIdentifier ) throws IOException
    {
        boolean doQuote = quoteIdentifier ||
                                isReservedWord(propertyName) ||
                                hasSurrogates(propertyName);

        if ( doQuote ){
            json.write('"');
        }

        json.write(propertyName);

        if ( doQuote ){
            json.write('"');
        }
        json.write(':');
    }

    /**
     * Append a simple property value to the given JSON buffer. This method is
     * used for numbers, strings and any other object that
     * {@link #appendPropertyValue(Object, Writer, JSONConfig)} doesn't know
     * about. There is some risk of infinite recursion if the object has a
     * toString() method that references objects above this in the data
     * structure, so be careful about that.
     *
     * @param propertyValue The value to append.
     * @param json Something to write the JSON data to.
     * @param cfg A configuration object to use to set various options.
     * @throws IOException If there is an error on output.
     */
    private static void appendSimplePropertyValue( Object propertyValue, Writer json, JSONConfig cfg ) throws IOException
    {
        if ( propertyValue instanceof Number ){
            Number num = (Number)propertyValue;
            NumberFormat fmt = cfg.getNumberFormat(num);
            String numericString = fmt == null ? num.toString()
                                               : fmt.format(num, new StringBuffer(), new FieldPosition(0)).toString();
            if ( isValidJSONNumber(numericString) ){
                json.write(numericString);
            }else{
                // Something isn't a kosher number for JSON, which is more
                // restrictive than ECMAScript for numbers.
                writeString(numericString, json, cfg);
            }
        }else if ( propertyValue instanceof Boolean ){
            // boolean values go literal -- no quotes.
            json.write(propertyValue.toString());
        }else if ( propertyValue instanceof Date && cfg.isFormatDates() ){
            if ( cfg.isEncodeDatesAsObjects() ){
                json.write("new Date(");
            }
            writeString(cfg.getDateGenFormat().format((Date)propertyValue), json, cfg);
            if ( cfg.isEncodeDatesAsObjects() ){
                json.write(')');
            }
        }else{
            // Use the toString() method for the value and write it out as a string.
            writeString(propertyValue.toString(), json, cfg);
        }
    }

    /**
     * Write a string to the output using escapes as needed.
     *
     * @param strValue The value to write.
     * @param json Something to write the JSON data to.
     * @param cfg A configuration object to use.
     * @throws IOException If there is an error on output.
     */
    private static void writeString( String strValue, Writer json, JSONConfig cfg ) throws IOException
    {
        if ( cfg.isEncodeNumericStringsAsNumbers() && isValidJSONNumber(strValue) ){
            // no quotes.
            json.write(strValue);
        }else{
            boolean useSingleLetterEscapes = true;
            if ( cfg.isUnEscapeWherePossible() ){
                strValue = CodePointData.unEscape(strValue, cfg);
            }

            json.write('"');
            CodePointData cp = new CodePointData(strValue, cfg, useSingleLetterEscapes);
            while ( cp.next() ){
                if ( cp.getEsc() != null ){
                    json.write(cp.getEsc());                     // valid escape.
                }else{
                    json.write(cp.getChars(), 0, cp.getCharCount());  // Pass it through -- usual case.
                }
            }
            json.write('"');
        }
    }

    /**
     * Get the property name with escaping options applied as needed
     * and validate the property name.
     *
     * @param key The map/bundle key to become the property name.
     * @param cfg The config object with the flags.
     * @param propertyNames The set of property names.  Used to detect duplicate property names.
     * @return the escaped validated property name.
     */
    private static String getPropertyName( Object key, JSONConfig cfg, Set<String> propertyNames )
    {
        String propertyName = key == null ? null : key.toString();

        if ( propertyName == null || propertyName.length() == 0 ){
            throw new BadPropertyNameException(propertyName, cfg);
        }

        /*
         * NOTE: I used to have unescape where possible here but after thinking
         * it through, I realized that it can unescape something that needed to
         * be Unicode escaped making a valid property name into an invalid one.
         */

        // handle escaping options.
        if ( cfg.isEscapeBadIdentifierCodePoints() ){
            propertyName = escapeBadIdentifierCodePoints(propertyName, cfg);
        }else if ( cfg.isEscapeNonAscii() ){
            propertyName = escapeNonAscii(propertyName, cfg);
        }else if ( cfg.isEscapeSurrogates() ){
            propertyName = escapeSurrogates(propertyName, cfg);
        }

        // handle validation.
        if ( cfg.isValidatePropertyNames() ){
            if ( propertyNames.contains(propertyName) ){
                // very unlikely.  two key objects that are not equal would
                // have to produce identical toString() results.
                throw new DuplicatePropertyNameException(propertyName, cfg);
            }
            checkValidJavascriptPropertyName(propertyName, cfg);
            propertyNames.add(propertyName);
        }

        return propertyName;
    }

    /**
     * Escape bad identifier code points if the config object requires it.
     *
     * @param propertyName the name to escape.
     * @param cfg The config object.
     * @return the escaped property name.
     */
    private static String escapeBadIdentifierCodePoints( String propertyName, JSONConfig cfg )
    {
        StringBuilder buf = new StringBuilder(propertyName.length());
        boolean useSingleLetterEscapes = cfg.isFullJSONIdentifierCodePoints();

        CodePointData cp = new CodePointData(propertyName, cfg, useSingleLetterEscapes);
        while ( cp.next() ){
            if ( cp.getEsc() != null ){
                buf.append(cp.getEsc());                     // have valid escape
            }else if ( cp.getIndex() > 0 && isValidIdentifierPart(cp.getCodePoint(), cfg) ){
                buf.append(cp.getChars(), 0, cp.getCharCount());
            }else if ( cp.getIndex() == 0 && isValidIdentifierStart(cp.getCodePoint(), cfg) ){
                buf.append(cp.getChars(), 0, cp.getCharCount());
            }else{
                buf.append(cp.getEscapeString());       // Bad code point for an identifier.
            }
        }

        return buf.toString();
    }

    /**
     * Escape non-ascii if the config object requires it.
     *
     * @param str The input string.
     * @param cfg The config object for flags.
     * @return The escaped string.
     */
    private static String escapeNonAscii( String str, JSONConfig cfg )
    {
        StringBuilder buf = new StringBuilder(str.length());
        CodePointData cp = new CodePointData(str, cfg);
        while ( cp.next() ){
            if ( cp.getCodePoint() > 127 ){
                buf.append(cp.getEscapeString());
            }else{
                buf.append(cp.getChars()[0]);
            }
        }
        return buf.toString();
    }

    /**
     * Escape surrogate pairs if the config object requires it.
     *
     * @param str The input string.
     * @param cfg the config object for flags.
     * @return The escaped string.
     */
    private static String escapeSurrogates( String str, JSONConfig cfg )
    {
        StringBuilder buf = new StringBuilder(str.length());
        CodePointData cp = new CodePointData(str, cfg);
        while ( cp.next() ){
            if ( cp.getCharCount() > 1 ){
                buf.append(cp.getEscapeString());
            }else{
                buf.append(cp.getChars()[0]);
            }
        }
        return buf.toString();
    }

    /**
     * Return true of if the given input contains UTF-16 surrogates.
     *
     * @param str the input string.
     * @return true if the input string contains UTF-16 surrogates. Otherwise false.
     */
    private static boolean hasSurrogates( String str )
    {
        for ( int i = 0, len = str.length(); i < len; i++ ){
            if ( isSurrogate(str.charAt(i)) ){
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if the given character is a UTF-16 surrogate.
     * <p>
     * Replacement since this method wasn't in the JRE until JDK 7.
     *
     * @param ch the char to test.
     * @return true if ch is a surrogate.
     */
    static boolean isSurrogate( char ch )
    {
        return ch >= Character.MIN_SURROGATE && ch < (1 + Character.MAX_SURROGATE);
    }

    /**
     * Return true if the input looks like a valid JSON number.
     *
     * @param numericString the string.
     * @return true if the string can be treated as a number in JSON.
     */
    private static boolean isValidJSONNumber( String numericString )
    {
        return JSON_NUMBER_PAT.matcher(numericString).matches() && ! OCTAL_NUMBER_PAT.matcher(numericString).matches();
    }

    /**
     * Return the resource bundle for this package.
     *
     * @param locale A locale to use.
     * @return The resource bundle.
     */
    static ResourceBundle getBundle( Locale locale )
    {
        String bundleName = JSONUtil.class.getPackage().getName() + ".JSON";
        return ResourceBundle.getBundle(bundleName, locale);
    }

    /**
     * Get the list of reserved words.
     *
     * @return the set of reserved words.
     */
    public static Set<String> getJavascriptReservedWords()
    {
        // sorts them and preserves the original Set.
        return new TreeSet<String>(RESERVED_WORDS);
    }

    /**
     * Check if the given string is a reserved word.
     *
     * @param name The name to check.
     * @return true if the name is a reserved word.
     */
    public static boolean isReservedWord( String name )
    {
        return RESERVED_WORDS.contains(name);
    }

    /**
     * Return true if the codePoint is a valid code point to start an
     * identifier.
     *
     * @param codePoint The code point.
     * @param cfg config object used for the ECMA 6 flag.
     * @return true if it's a valid code point to start an identifier.
     */
    static boolean isValidIdentifierStart( int codePoint, JSONConfig cfg )
    {
        if ( cfg.isFullJSONIdentifierCodePoints() ){
            return isValidIdentifierPart(codePoint, cfg);
        }else{
            return codePoint == '_' || codePoint == '$' ||
                    (cfg.isUseECMA6() ? Character.isUnicodeIdentifierStart(codePoint)
                                      : Character.isLetter(codePoint));
        }
    }

    /**
     * Return true if the codePoint is a valid code point for part of an
     * identifier but not necessarily the start of an identifier.
     *
     * @param codePoint The code point.
     * @param cfg config object used for the ECMA 6 flag.
     * @return true if the codePoint is a valid code point for part of an
     *         identifier but not the start of an identifier.
     */
    static boolean isValidIdentifierPart( int codePoint, JSONConfig cfg )
    {
        if ( cfg.isFullJSONIdentifierCodePoints() ){
            return codePoint >= ' ' &&
                    Character.isDefined(codePoint) &&
                    ! (codePoint <= '\\' && CodePointData.haveJsonEsc((char)codePoint));
        }else{
            return cfg.isUseECMA6() ? Character.isUnicodeIdentifierPart(codePoint)
                                    : (isValidIdentifierStart(codePoint, cfg) ||
                                       Character.isDigit(codePoint) ||
                        ((((1 << Character.NON_SPACING_MARK) | (1 << Character.COMBINING_SPACING_MARK) |
                        (1 << Character.CONNECTOR_PUNCTUATION) ) >> Character.getType(codePoint)) & 1) != 0 ||
                        codePoint == 0x200C || codePoint == 0x200D);
        }
    }

    /**
     * Checks if the input string represents a valid Javascript property name.
     *
     * @param propertyName A Javascript property name to check.
     * @param cfg A JSONConfig to use for locale and identifier options.  If null, defaults will be used.
     * @throws BadPropertyNameException If the propertyName is not a valid Javascript property name.
     */
    public static void checkValidJavascriptPropertyName( String propertyName, JSONConfig cfg ) throws BadPropertyNameException
    {
        JSONConfig jcfg = cfg != null ? cfg : new JSONConfig();
        Pattern validationPat = getPropertyNameValidationPattern(jcfg);

        if ( propertyName == null ||
                (isReservedWord(propertyName) && !jcfg.isAllowReservedWordsInIdentifiers()) ||
                ! validationPat.matcher(propertyName).matches() ){
            throw new BadPropertyNameException(propertyName, jcfg);
        }
    }

    /**
     * Checks if the input string represents a valid Javascript property name.
     *
     * @param propertyName A Javascript property name to check.
     * @throws BadPropertyNameException If the propertyName is not a valid Javascript property name.
     */
    public static void checkValidJavascriptPropertyName( String propertyName ) throws BadPropertyNameException
    {
        checkValidJavascriptPropertyName(propertyName, null);
    }

    /**
     * Get the appropriate validation pattern given the config flags.
     *
     * @param cfg A JSONConfig to use to decide which validation pattern to use.
     * @return A Pattern used for validating property names.
     */
    private static Pattern getPropertyNameValidationPattern( JSONConfig cfg )
    {
        Pattern validationPat;

        if ( cfg.isFullJSONIdentifierCodePoints() ){
            // allows a lot of characters not allowed in ECMAScript identifiers.
            validationPat = cfg.isUseECMA6() ? VALID_JSON6_PROPERTY_NAME_PAT : VALID_JSON5_PROPERTY_NAME_PAT;
        }else{
            // requires ECMAScript compliant identifiers.
            validationPat = cfg.isUseECMA6() ? VALID_ECMA6_PROPERTY_NAME_PAT : VALID_ECMA5_PROPERTY_NAME_PAT;
        }

        return validationPat;
    }

    /**
     * Constructor is private because this class should never be instantiated.
     */
    private JSONUtil()
    {
    }
}
