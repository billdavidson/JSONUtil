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
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
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
 *     the org.json library which requires the keys to actually be Strings.
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
 *     in Javascript.
 *     <p>
 *     If {@link JSONConfig#isEncodeDatesAsObjects()} returns true, then {@link Date}s
 *     will be encoded as a call to the Date constructor in Javascript using an ISO 8601
 *     date string.  This works with Javascript eval().  It probably won't work in most
 *     strict JSON parsers.
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
     * Multi-use strings.
     */
    static final String NULL = "null";
    private static final String CODE_UNIT_FMT = "\\u%04X";
    private static final String CODE_POINT_FMT = "\\u{%X}";

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
     * Some characters break lines which breaks quotes.  Recognize them so that they
     * can be escaped properly.
     * <ul>
     *   <li>\p{Zl} - Line Separator</li>
     *   <li>\p{Zp} - Paragraph Separator</li>
     * </ul>
     */
    private static final Pattern FORCE_ESCAPE_PAT = Pattern.compile("[\\p{Zl}\\p{Zp}]");

    /**
     * Javascript escapes, including those not permitted in JSON.
     */
    private static final Pattern JAVASCRIPT_ESC_PAT = Pattern.compile("(\\\\([bfnrtv\\\\/'\"]|(x\\p{XDigit}{2})|([0-3]?[0-7]{1,2})))");

    /**
     * Parse a Unicode code unit escape.
     */
    private static final Pattern CODE_UNIT_PAT = Pattern.compile("(\\\\u(\\p{XDigit}{4}))");

    /**
     * Parse an ECMAScript 6 Unicode code point escaoe.
     */
    private static final Pattern CODE_POINT_PAT = Pattern.compile("(\\\\u\\{(\\p{XDigit}+)\\})");

    /**
     * Escapes to pass through for ECMA5 when escaping bad identifier code points.
     */
    private static final Pattern JSON5_ESCAPE_PASS_THROUGH_PAT =
            Pattern.compile("(\\\\u\\p{XDigit}{4}|\\\\[bfnrt\\\\/\"])");

    /**
     * Escapes to pass through for ECMA6 when escaping bad identifier code points.
     */
    private static final Pattern JSON6_ESCAPE_PASS_THROUGH_PAT =
            Pattern.compile("(\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\}|\\\\[bfnrt\\\\/\"])");

    /**
     * Escapes to pass through for ECMA5 when escaping bad identifier code points.
     */
    private static final Pattern ECMA5_ESCAPE_PASS_THROUGH_PAT =
            Pattern.compile("(\\\\u\\p{XDigit}{4})");

    /**
     * Escapes to pass through for ECMA6 when escaping bad identifier code points.
     */
    private static final Pattern ECMA6_ESCAPE_PASS_THROUGH_PAT =
            Pattern.compile("(\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})");

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
            Pattern.compile("^(?:[_\\$\\p{L}]|\\\\u\\p{XDigit}{4})(?:[_\\$\\p{L}\\p{Nd}\\p{Mn}\\p{Mc}\\p{Pc}\\u200C\\u200D]|\\\\u\\p{XDigit}{4})*$");

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
            Pattern.compile("^(?:[_\\$\\p{L}\\p{Nl}]|\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})(?:[_\\$\\p{L}\\p{Nl}\\p{Nd}\\p{Mn}\\p{Mc}\\p{Pc}\\u200C\\u200D]|\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})*$");

    /**
     * This pattern is for full JSON identifier names. This is much more
     * permissive than the ECMAScript 5 standard. Property names that use
     * characters not permitted by the ECMAScript standard will not work with
     * Javascript eval() but will work with Javascript JSON.parse().
     */
    private static final Pattern VALID_JSON5_PROPERTY_NAME_PAT = Pattern.compile("^([^\u0000-\u001F\\p{Cn}\"/\\\\]|\\\\[bfnrt\\\\/\"]|\\\\u\\p{XDigit}{4})+$");

    /**
     * This pattern is for full JSON identifier names. This is much more
     * permissive than the ECMAScript 6 standard. Property names that use
     * characters not permitted by the ECMAScript standard will not work with
     * Javascript eval() but will work with Javascript JSON.parse().
     */
    private static final Pattern VALID_JSON6_PROPERTY_NAME_PAT = Pattern.compile("^([^\u0000-\u001F\\p{Cn}\"/\\\\]|\\\\[bfnrt\\\\/\"]|\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})+$");

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
     * Map Javascript character escapes to their code points.
     */
    private static final Map<String,Character> JAVASCRIPT_ESC_MAP;

    /**
     * Map characters that go to JSON character escapes.
     */
    private static final Map<Character,String> JSON_ESC_MAP;

    /**
     * Initialize JSON_ESC_MAP && JAVASCRIPT_ESC_MAP.
     */
    static {
        Map<Character,String> jsonMap = new HashMap<Character,String>();
        jsonMap.put('"', "\\\"");
        jsonMap.put('/', "\\/");
        jsonMap.put('\b', "\\b");
        jsonMap.put('\f', "\\f");
        jsonMap.put('\n', "\\n");
        jsonMap.put('\r', "\\r");
        jsonMap.put('\t', "\\t");
        jsonMap.put('\\', "\\\\");
        JSON_ESC_MAP = new HashMap<Character,String>(jsonMap);

        Map<String,Character> jsMap = new HashMap<String,Character>();
        for ( Entry<Character,String> entry : JSON_ESC_MAP.entrySet() ){
            jsMap.put(entry.getValue(), entry.getKey());
        }
        // these two are valid in Javascript but not JSON.
        jsMap.put("\\'", '\'');
        jsMap.put("\\v", (char)0xB);
        JAVASCRIPT_ESC_MAP = new HashMap<String,Character>(jsMap);
    }

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
        int stackIndex = 0;
        List<Object> objStack = null;
        boolean detectDataStructureLoops = cfg.isDetectDataStructureLoops();
        if ( detectDataStructureLoops ){
            objStack = cfg.getObjStack();
            for ( Object o : objStack ){
                // reference comparison.
                if ( o == propertyValue ){
                    throw new DataStructureLoopException(propertyValue, cfg);
                }
            }
            stackIndex = objStack.size();
            objStack.add(propertyValue);
        }

        if ( propertyValue instanceof JSONAble ){
            JSONAble jsonAble = (JSONAble)propertyValue;
            jsonAble.toJSON(cfg, json);
        }else{
            boolean isMap = propertyValue instanceof Map;

            if ( isMap || propertyValue instanceof ResourceBundle ){
                // Maps and ResourceBundles use almost the same logic.
                Map<?,?> map = null;
                ResourceBundle bundle = null;
                Set<?> keys;
                Set<String> propertyNames = null;

                if ( isMap ){
                    map = (Map<?,?>)propertyValue;
                    keys = map.keySet();
                }else{
                    bundle = (ResourceBundle)propertyValue;
                    keys = enumerationToSet(bundle.getKeys());
                }

                boolean quoteIdentifier = cfg.isQuoteIdentifier();
                if ( cfg.isValidatePropertyNames() ){
                    propertyNames = new HashSet<String>(keys.size());
                }

                // make a Javascript object with the keys as the property names.
                json.write('{');
                boolean didStart = false;
                for ( Object key : keys ){
                    if ( didStart ){
                        json.write(',');
                    }else{
                        didStart = true;
                    }
                    // apply any escapes and do validation as per the config flags.
                    String propertyName = getPropertyName(key, cfg, propertyNames);
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
                    Object value = isMap ? map.get(key) : bundle.getObject((String)key);
                    // recurse on the value.
                    appendPropertyValue(value, json, cfg);
                }
                json.write('}');
            }else{
                // make an array.
                json.write('[');
                boolean didStart = false;
                if ( propertyValue instanceof Iterable ){
                    Iterable<?> iterable = (Iterable<?>)propertyValue;
                    for ( Object value : iterable ){
                        if ( didStart ){
                            json.write(',');
                        }else{
                            didStart = true;
                        }
                        // recurse on the value.
                        appendPropertyValue(value, json, cfg);
                    }
                }else if ( propertyValue instanceof Enumeration ){
                    Enumeration<?> enumeration = (Enumeration<?>)propertyValue;
                    while ( enumeration.hasMoreElements() ){
                        if ( didStart ){
                            json.write(',');
                        }else{
                            didStart = true;
                        }
                        // recurse on the value.
                        appendPropertyValue(enumeration.nextElement(), json, cfg);
                    }
                }else{
                    // propertyValue.getClass().isArray() == true
                    Object array = propertyValue;
                    // Don't know the type of the array so can't cast it.  Use reflection.
                    for ( int i = 0, len = Array.getLength(array); i < len; i++ ){
                        if ( i > 0 ){
                            json.write(',');
                        }
                        // recurse on the value.
                        appendPropertyValue(Array.get(array, i), json, cfg);
                    }
                }
                json.write(']');
            }
        }

        if ( detectDataStructureLoops ){
            // remove this value from the stack.
            if ( objStack.size() == (stackIndex+1) && objStack.get(stackIndex) == propertyValue ){
                // current propertyValue is the last value in the list.
                objStack.remove(stackIndex);
            }else{
                // this should never happen.
                throw new LoopDetectionFailureException(stackIndex, cfg);
            }
        }
    }

    /**
     * This converts an enumeration to a set.
     * <p>
     * Java5 can't get a key set from a resource bundle.  
     *
     * @param enr the enumeration.
     * @return the set.
     */
    private static Set<String> enumerationToSet( Enumeration<String> enr )
    {
        List<String> result = new ArrayList<String>();
        while ( enr.hasMoreElements() ){
            result.add(enr.nextElement());
        }
        return new LinkedHashSet<String>(result);
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
        }else if ( propertyValue instanceof Date && cfg.isEncodeDatesAsObjects() ){
            // non-standard JSON but should work with Javascript eval().
            Date date = (Date)propertyValue;
            json.write("new Date(");
            writeString(cfg.getDateGenFormat().format(date), json, cfg);
            json.write(')');
        }else if ( propertyValue instanceof Date && cfg.isEncodeDatesAsStrings() ){
            Date date = (Date)propertyValue;
            writeString(cfg.getDateGenFormat().format(date), json, cfg);
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
            // need to do escapes as required by ECMA JSON spec.
            json.write('"');
            boolean escapeNonAscii = cfg.isEscapeNonAscii();
            boolean escapeSurrogates = cfg.isEscapeSurrogates();
            boolean useECMA6 = cfg.isUseECMA6();
            // shouldn't use eval() with full JSON id code points.
            boolean ckForce = ! cfg.isFullJSONIdentifierCodePoints();
            if ( cfg.isUnEscapeWherePossible() ){
                strValue = unEscape(strValue);
            }
            Matcher passThroughMatcher = null;
            Matcher jsEscMatcher = null;
            Matcher codePointMatcher = null;
            if ( strValue.indexOf('\\') >= 0 ){
                // only create escape matchers if there are escapes.
                boolean forceString = true;
                Pattern escapePassThroughPat = getEscapePassThroughPattern(cfg, forceString);
                passThroughMatcher = escapePassThroughPat.matcher(strValue);
                jsEscMatcher = JAVASCRIPT_ESC_PAT.matcher(strValue);
                codePointMatcher = CODE_POINT_PAT.matcher(strValue);
            }
            int i = 0;
            int len = strValue.length();
            while ( i < len ){
                int codePoint = strValue.codePointAt(i);
                int charCount = Character.charCount(codePoint);
                char char0 = strValue.charAt(i);
                char char1 = charCount > 1 ? strValue.charAt(i+1) : 0;
                boolean notDone = true;
                if ( codePoint == '\\' ){
                    // check for escapes.
                    if ( passThroughMatcher.find(i) && passThroughMatcher.start() == i ){
                        // pass it through unchanged.
                        String esc = passThroughMatcher.group(1);
                        json.write(esc);
                        i += esc.length() - 1;
                        notDone = false;
                    }else if ( jsEscMatcher.find(i) && jsEscMatcher.start() == i ){
                        // Hex and octal escapes are not permitted for JSON.
                        // Single character escapes are passed through above except
                        // \v and \' which are not valid in JSON which are handled here.
                        String esc = jsEscMatcher.group(1);
                        codePoint = char0 = getEscapeChar(esc);
                        i += esc.length() - 1;
                    }else if ( codePointMatcher.find(i) && codePointMatcher.start() == i ){
                        // only get here if it wasn't passed through => useECMA6 is false
                        // convert it to an inline codepoint or other escape as needed.
                        codePoint = Integer.parseInt(codePointMatcher.group(2),16);
                        if ( codePoint > 0xFFFF ){
                            charCount = 2;
                            StringBuilder t = new StringBuilder();
                            t.appendCodePoint(codePoint);
                            char0 = t.charAt(0);
                            char1 = t.charAt(1);
                        }else{
                            char0 = (char)codePoint;
                        }
                        i += codePointMatcher.group(1).length() - charCount;
                    }
                }
                if ( notDone && codePoint <= '\\' ){
                    String esc = getEscape((char)codePoint);
                    if ( esc != null ){
                        // escapes required by the JSON standard.
                        json.write(esc);
                        notDone = false;
                    }
                }
                if ( notDone ){
                    // check if it needs to be escaped.
                    boolean doEscape = (escapeNonAscii && codePoint > 127)
                            || (escapeSurrogates && charCount > 1)
                            || codePoint < 0x20                     // JSON standard.
                            || ! Character.isDefined(codePoint)
                            || (ckForce && FORCE_ESCAPE_PAT.matcher(strValue.substring(i, i+charCount)).find());
                    if ( doEscape ){
                        // escape it.
                        if ( useECMA6 && (codePoint < 0x10 || codePoint > 0xFFFF) ){
                            // only very low or very high code points see an advantage.
                            json.write(String.format(CODE_POINT_FMT, codePoint));
                        }else{
                            // normal escape.
                            json.write(String.format(CODE_UNIT_FMT, (int)char0));
                            if ( charCount > 1 ){
                                json.write(String.format(CODE_UNIT_FMT, (int)char1));
                            }
                        }
                    }else{
                        // Pass it through -- usual case.
                        json.write(char0);
                        if ( charCount > 1 ){
                            json.write(char1);
                        }
                    }
                }
                i += charCount;
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
        propertyName = escapeNonAscii(propertyName, cfg);
        propertyName = escapeSurrogates(propertyName, cfg);
        propertyName = escapeBadIdentifierCodePoints(propertyName, cfg);

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
        if ( ! cfg.isEscapeBadIdentifierCodePoints() || ! hasBadIdentifierCodePoints(propertyName, cfg) ){
            return propertyName;
        }

        StringBuilder buf = new StringBuilder();
        int i = 0;
        int len = propertyName.length();
        boolean useECMA6 = cfg.isUseECMA6();
        boolean isFullJSONIdentifierCodePoints = cfg.isFullJSONIdentifierCodePoints();
        Matcher passThroughMatcher = null;
        Matcher jsEscMatcher = null;
        Matcher codePointMatcher = null;
        if ( propertyName.indexOf('\\') >= 0 ){
            // only create escape matchers if there are escapes.
            boolean forceString = false;
            Pattern escapePassThroughPat = getEscapePassThroughPattern(cfg, forceString);
            passThroughMatcher = escapePassThroughPat.matcher(propertyName);
            jsEscMatcher = JAVASCRIPT_ESC_PAT.matcher(propertyName);
            codePointMatcher = CODE_POINT_PAT.matcher(propertyName);
        }

        while ( i < len ){
            int codePoint = propertyName.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            int char0 = propertyName.charAt(i);
            int char1 = charCount > 1 ? propertyName.charAt(i+1) : 0;
            boolean notDone = true;
            if ( codePoint == '\\' ){
                // check for escapes to pass through.
                if ( passThroughMatcher.find(i) && passThroughMatcher.start() == i ){
                    // It's a valid escape.  Pass it through.
                    String esc = passThroughMatcher.group(1);
                    buf.append(esc);
                    i += esc.length() - 1;
                    notDone = false;
                }else if ( jsEscMatcher.find(i) && jsEscMatcher.start() == i ){
                    // also fix bad escapes.
                    String esc = jsEscMatcher.group(1);
                    codePoint = char0 = getEscapeChar(esc);
                    i += esc.length() - 1;
                }else if ( codePointMatcher.find(i) && codePointMatcher.start() == i ){
                    // only get here if it wasn't passed through.
                    codePoint = Integer.parseInt(codePointMatcher.group(2),16);
                    if ( codePoint > 0xFFFF ){
                        charCount = 2;
                        StringBuilder t = new StringBuilder();
                        t.appendCodePoint(codePoint);
                        char0 = t.charAt(0);
                        char1 = t.charAt(1);
                    }else{
                        char0 = (char)codePoint;
                    }
                    i += codePointMatcher.group(1).length() - charCount;
                }
            }
            if ( notDone && isFullJSONIdentifierCodePoints && codePoint <= '\\' ){
                String esc = getEscape((char)codePoint);
                if ( esc != null ){
                    // escapes required by the JSON standard.
                    buf.append(esc);
                    notDone = false;
                }
            }
            if ( notDone ){
                // escapes should be handled.  if not, then it's a bad escape.
                if ( i == 0 && isValidIdentifierStart(codePoint, cfg) ){
                    buf.appendCodePoint(codePoint);
                }else if ( i > 0 && isValidIdentifierPart(codePoint, cfg) ){
                    buf.appendCodePoint(codePoint);
                }else{
                    // Bad code point for an identifier.
                    if ( useECMA6 && (codePoint < 0x10 || codePoint > 0xFFFF) ){
                        // Use ECMAScript 6 code point escape.
                        // only very low or very high code points see an advantage.
                        buf.append(String.format(CODE_POINT_FMT, codePoint));
                    }else{
                        // Use normal escape.
                        buf.append(String.format(CODE_UNIT_FMT, char0));
                        if ( charCount > 1 ){
                            buf.append(String.format(CODE_UNIT_FMT, char1));
                        }
                    }
                }
            }
            i += charCount;
        }

        return buf.toString();
    }

    /**
     * Return true of if the given input contains bad identifier code points.
     *
     * @param propertyName the input string.
     * @return true if the input string contains bad identifier code points. Otherwise false.
     */
    private static boolean hasBadIdentifierCodePoints( String propertyName, JSONConfig cfg )
    {
        int i = 0;
        int len = propertyName.length();
        Matcher passThroughMatcher = null;
        if ( propertyName.indexOf('\\') >= 0 ){
            // only create matcher if there are escapes.
            boolean forceString = false;
            Pattern escapePassThroughPat = getEscapePassThroughPattern(cfg, forceString);
            passThroughMatcher = escapePassThroughPat.matcher(propertyName);
        }

        while ( i < len ){
            int codePoint = propertyName.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if ( codePoint == '\\' ){
                // check for escapes to pass through.
                if ( passThroughMatcher.find(i) && passThroughMatcher.start() == i ){
                    // It's a valid escape.  Pass it through.
                    String esc = passThroughMatcher.group(1);
                    i += esc.length();
                }else{
                    // bad escape.
                    return true;
                }
            }else if ( i == 0 && isValidIdentifierStart(codePoint, cfg) ){
                i += charCount;
            }else if ( i > 0 && isValidIdentifierPart(codePoint, cfg) ){
                i += charCount;
            }else{
                // Bad code point for an identifier.
                return true;
            }
        }
        return false;
    }

    /**
     * Get the escape pass through pattern for identifiers or strings.
     *
     * @param cfg A configuration object to determine which pattern to use.
     * @param forceString If true, then force a String/JSON pattern which allows more escapes.
     * @return The escape pass through pattern.
     */
    static Pattern getEscapePassThroughPattern( JSONConfig cfg, boolean forceString )
    {
        Pattern escapePassThroughPat;

        if ( forceString || cfg.isFullJSONIdentifierCodePoints() ){
            // JSON standard allows most string escapes in identifiers.
            escapePassThroughPat = cfg.isUseECMA6() ? JSON6_ESCAPE_PASS_THROUGH_PAT
                                                    : JSON5_ESCAPE_PASS_THROUGH_PAT;
        }else{
            // ECMAScript only allows Unicode escapes in identifiers.
            escapePassThroughPat = cfg.isUseECMA6() ? ECMA6_ESCAPE_PASS_THROUGH_PAT
                                                    : ECMA5_ESCAPE_PASS_THROUGH_PAT;
        }

        return escapePassThroughPat;
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
     * Escape surrogate pairs.
     *
     * @param str The input string.
     * @param cfg the config object for flags.
     * @return The escaped string.
     */
    private static String escapeSurrogates( String str, JSONConfig cfg )
    {
        if ( ! cfg.isEscapeSurrogates() || ! hasSurrogates(str) ){
            return str;
        }

        StringBuilder buf = new StringBuilder();
        int i = 0;
        int len = str.length();
        boolean useECMA6 = cfg.isUseECMA6();
        while ( i < len ){
            int codePoint = str.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if ( charCount > 1 ){
                if ( useECMA6 ){
                    buf.append(String.format(CODE_POINT_FMT, codePoint));
                }else{
                    buf.append(String.format(CODE_UNIT_FMT, (int)str.charAt(i)));
                    buf.append(String.format(CODE_UNIT_FMT, (int)str.charAt(i+1)));
                }
            }else{
                buf.appendCodePoint(codePoint);
            }
            i += charCount;
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
        if ( ! cfg.isEscapeNonAscii() || ! hasNonAscii(str) ){
            return str;
        }

        StringBuilder buf = new StringBuilder();
        int i = 0;
        int len = str.length();
        boolean useECMA6 = cfg.isUseECMA6();
        while ( i < len ){
            int codePoint = str.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if ( codePoint > 127 ){
                if ( useECMA6 && codePoint > 0xFFFF ){
                    buf.append(String.format(CODE_POINT_FMT, codePoint));
                }else{
                    buf.append(String.format(CODE_UNIT_FMT, (int)str.charAt(i)));
                    if ( charCount > 1 ){
                        buf.append(String.format(CODE_UNIT_FMT, (int)str.charAt(i+1)));
                    }
                }
            }else{
                buf.appendCodePoint(codePoint);
            }
            i += charCount;
        }
        return buf.toString();
    }

    /**
     * Return true of if the given input contains non-ASCII characters.
     *
     * @param str the input string.
     * @return true if the input string contains non-ASCII characters. Otherwise false.
     */
    private static boolean hasNonAscii( String str )
    {
        for ( int i = 0, len = str.length(); i < len; i++ ){
            if ( str.charAt(i) > 127 ){
                return true;
            }
        }
        return false;
    }

    /**
     * Undo escapes in input strings before formatting a string. This will get
     * rid of octal escapes and hex escapes and any unnecessary escapes. If the
     * characters still need to be escaped, then they will be re-escaped by the
     * caller.
     *
     * @param strValue Input string.
     * @return Unescaped string.
     */
    static String unEscape( String strValue )
    {
        if ( strValue.indexOf('\\') < 0 ){
            // nothing to do.
            return strValue;
        }

        Matcher jsEscMatcher = JAVASCRIPT_ESC_PAT.matcher(strValue);
        Matcher codeUnitMatcher = CODE_UNIT_PAT.matcher(strValue);
        Matcher codePointMatcher = CODE_POINT_PAT.matcher(strValue);
        StringBuilder buf = new StringBuilder();
        int i = 0;
        int len = strValue.length();
        while ( i < len ){
            int codePoint = strValue.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if ( codePoint == '\\' ){
                // check for escapes.
                if ( jsEscMatcher.find(i) && jsEscMatcher.start() == i ){
                    String esc = jsEscMatcher.group(1);
                    buf.append(getEscapeChar(esc));
                    i += esc.length() - 1;
                }else if ( codeUnitMatcher.find(i) && codeUnitMatcher.start() == i ){
                    buf.append((char)Integer.parseInt(codeUnitMatcher.group(2),16));
                    i += codeUnitMatcher.group(1).length() - 1;
                }else if ( codePointMatcher.find(i) && codePointMatcher.start() == i ){
                    buf.appendCodePoint(Integer.parseInt(codePointMatcher.group(2),16));
                    i += codePointMatcher.group(1).length() - 1;
                }else{
                    // have '\' but nothing looks like a valid escape, just pass it through.
                    buf.appendCodePoint(codePoint);
                }
            }else{
                // not an escape.
                buf.appendCodePoint(codePoint);
            }
            i += charCount;
        }
        return buf.toString();
    }

    /**
     * Take a string containing a Javascript character escape and return its
     * char value.
     *
     * @param esc A string containing a Javascript hex, octal or character escape.
     * @return The char represented by the given escape.
     */
    private static char getEscapeChar( String esc )
    {
        char result;

        char c = esc.charAt(1);
        if ( c == 'x' ){
            result = (char)Integer.parseInt(esc.substring(2), 16); // hex escape
        }else if ( Character.isDigit(c) ){
            result = (char)Integer.parseInt(esc.substring(1), 8);  // octal escape
        }else{
            result = JAVASCRIPT_ESC_MAP.get(esc);                  // other character escape
        }
        return result;
    }

    /**
     * Return the JSON character escape for the given char or null if there isn't one.
     *
     * @param c The char to be escaped.
     * @return The escape if there is one.
     */
    static String getEscape( char c )
    {
        return JSON_ESC_MAP.get(c);
    }
    
    /**
     * Get the chars that the JSON standard requires to be escaped.
     * @return the chars that the JSON standard requires to be escaped.
     */
    static Set<Character> getJsonEscapeChars()
    {
        // TreeSet sorts it.
        return new TreeSet<Character>(JSON_ESC_MAP.keySet());
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
            return codePoint >= ' '
                   && Character.isDefined(codePoint)
                   && ! (codePoint <= '\\' && JSON_ESC_MAP.containsKey((char)codePoint));
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
            return codePoint >= ' '
                   && Character.isDefined(codePoint)
                   && ! (codePoint <= '\\' && JSON_ESC_MAP.containsKey((char)codePoint));
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
