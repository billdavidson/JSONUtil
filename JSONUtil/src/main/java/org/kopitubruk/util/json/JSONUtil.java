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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *   specification from ECMAScript 5.1 and the ECMA JSON standard.  The validation
 *   can be disabled for faster performance.  See {@link JSONConfig}.  Leaving it
 *   on during development and testing is probably advisable.
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
 *     The key's toString() must produce valid Javascript identifiers and the
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
 *   <dt>Any other object</dt>
 *   <dd>
 *     Any other object just gets its toString() method called and it's surrounded
 *     by quotes with escapes used as needed according to the ECMA JSON standard.
 *     Usually this will just be for String objects, but anything that has a
 *     toString() that gives you what you want will work.
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
    private static final String NULL = "null";
    private static final String CODE_UNIT_FMT = "\\u%04X";
    private static final String CODE_POINT_FMT = "\\u{%X}";

    /**
     * For strings that are really numbers.  ECMA JSON spec doesn't allow octal,
     * hexadecimal, NaN or Infinity in JSON.  It also doesn't allow for a "+"
     * sign to start a number.
     */
    private static final Pattern JAVASCRIPT_NUMBER_PAT = Pattern.compile("^-?((\\d+(\\.\\d+)?)|(\\.\\d+))([eE][-+]?\\d+)?$");

    /**
     * Check for octal numbers, which aren't allowed in JSON.
     */
    private static final Pattern OCTAL_NUMBER_PAT = Pattern.compile("^-?0[0-7]+$");

    /**
     * Check for escapes.  This is used to pass escape sequences through
     * unchanged.  Some are illegal for JSON, but that's an issue for the
     * person who makes those strings in the first place.
     */
    private static final Pattern ESC_PAT =
            Pattern.compile("^(\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\}|\\\\x\\p{XDigit}{2}|\\\\[0-7]{1,4}|\\\\[bfnrtv\\\\/'\"])");

    /**
     * Parse octal escape.
     */
    private static final Pattern OCTAL_ESC_PAT = Pattern.compile("^\\\\([0-7]{1,4})$");

    /**
     * Parse a hexadecimal escape.
     */
    private static final Pattern HEX_ESC_PAT = Pattern.compile("^\\\\x(\\p{XDigit}{2})$");

    /**
     * Recognize other character escapes.
     */
    private static final Pattern OTHER_ESC_PAT = Pattern.compile("^\\\\[bfnrtv\\\\/'\"]$");

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
     * Parse a Unicode code unit escape.
     */
    private static final Pattern CODE_UNIT_PAT = Pattern.compile("^\\\\u(\\p{XDigit}{4})$");

    /**
     * Parse an ECMA 6 Unicode code point escaoe.
     */
    private static final Pattern CODE_POINT_PAT = Pattern.compile("^\\\\u\\{(\\p{XDigit}+)\\}$");

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
     *   The permitted characters are specified by ECMAScript 5.1, Section 7.6
     * </p>
     * <p>
     *   Permitted starting characters.
     * </p>
     * <ul>
     *   <li>_ - Underscore</li>
     *   <li>$ - Dollar sign</li>
     *   <li>\p{L} - Any Letter</li>
     *   <li>\\u\p{XDigit}{4} - Unicode code unit escape.</li>
     *   <li>\\u\{\p{XDigit}+\} - Unicode code point escape. (ECMA 6)</li>
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
     *   <li>\\u\{\p{XDigit}+\} - Unicode code point escape. (ECMA 6)</li>
     * </ul>
     */
    private static final Pattern VALID_JAVASCRIPT_PROPERTY_NAME_PAT =
            Pattern.compile("^([_\\$\\p{L}]|\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})([_\\$\\p{L}\\p{Nd}\\p{Mn}\\p{Mc}\\p{Pc}\\u200C\\u200D]|\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})*$");

    /**
     * This is the set of reserved words from ECMAScript 5.1 section 7.6.1.1 and
     * 7.6.1.2. It's a bad practice to use these as property names and not
     * permitted by the JSON standard.
     */
    private static final Set<String> RESERVED_WORDS =
            new HashSet<>(Arrays.asList(
                                    /* keywords */
                          "break", "case", "catch", "continue", "debugger",
                          "default", "delete", "do", "else", "finally", "for",
                          "function", "if", "in", "instanceof", "new", "return",
                          "switch", "this", "throw", "try", "typeof", "var",
                          "void", "while", "with",
                                    /* future reserved words */
                          "class", "const", "enum", "export", "extends",
                          "implements", "import",  "interface", "let", "package",
                          "private", "protected", "public", "static",
                          "super", "yield",
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
     * @param jsonConfig A configuration object to use.
     * @return A JSON string representation of the object.
     */
    public static String toJSON( Object obj, JSONConfig jsonConfig )
    {
        Writer json = new StringWriter();
        try{
            toJSON(obj, jsonConfig, json);
        }catch ( IOException e ){
            // won't happen because StringWriter is really just a wrapper around a StringBuffer.
        }
        return json.toString();
    }

    /**
     * Convert an object to JSON and write it to the given {@link Writer}.
     * All options will be default.
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
     * generated.  The downside of that is that you could have an error after
     * data begins being sent, which could result in corrupted partial data being
     * sent to the caller.
     *
     * @param obj An object to be converted to JSON.
     * @param jsonConfig A configuration object to use to set various options.  If null then defaults will be used.
     * @param json Something to write the JSON data to.
     * @throws IOException If there is an error on output.
     */
    public static void toJSON( Object obj, JSONConfig jsonConfig, Writer json ) throws IOException
    {
        JSONConfig cfg = jsonConfig == null ? new JSONConfig() : jsonConfig;
        try{
            appendPropertyValue(obj, json, cfg);
        }catch ( Exception e ){
            // in case the original calling code catches the exception and reuses the JSONConfig.
            cfg.clearObjStack();
            throw e;
        }
    }

    /**
     * <p>
     *   Append the given value to the given writer. There is special handling for
     *   null, {@link Number}s, {@link JSONAble}s, {@link Map}s,
     *   {@link ResourceBundle}s, {@link Iterable}s, {@link Enumeration}s and arrays.
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
     * @param jsonConfig A configuration object.
     * @throws IOException If there is an error on output.
     */
    private static void appendPropertyValue( Object propertyValue, Writer json, JSONConfig jsonConfig ) throws IOException
    {
        if ( propertyValue == null ){
            json.write(NULL);
        }else if ( isRecursible(propertyValue) ){
            appendRecursiblePropertyValue(propertyValue, json, jsonConfig);
        }else{
            appendSimplePropertyValue(propertyValue, json, jsonConfig);
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
     * @param jsonConfig A configuration object to use.
     * @throws IOException If there is an error on output.
     */
    private static void appendRecursiblePropertyValue( Object propertyValue, Writer json, JSONConfig jsonConfig ) throws IOException
    {
        // check for loops.
        int stackIndex = 0;
        List<Object> objStack = null;
        boolean detectDataStructureLoops = jsonConfig.isDetectDataStructureLoops();
        if ( detectDataStructureLoops ){
            objStack = jsonConfig.getObjStack();
            for ( Object o : objStack ){
                // reference comparison.
                if ( o == propertyValue ){
                    throw new DataStructureLoopException(propertyValue, jsonConfig);
                }
            }
            stackIndex = objStack.size();
            objStack.add(propertyValue);
        }

        if ( propertyValue instanceof JSONAble ){
            JSONAble jsonAble = (JSONAble)propertyValue;
            jsonAble.toJSON(jsonConfig, json);
        }else{
            boolean isMap = propertyValue instanceof Map;

            if ( isMap || propertyValue instanceof ResourceBundle ){
                // Maps and ResourceBundles use almost the same logic.
                Map<?,?> map = null;
                ResourceBundle bundle = null;
                Set<?> keys;

                if ( isMap ){
                    map = (Map<?,?>)propertyValue;
                    keys = map.keySet();
                }else{
                    bundle = (ResourceBundle)propertyValue;
                    keys = bundle.keySet();
                }

                // make a Javascript object with the keys as the property names.

                boolean quoteIdentifier = jsonConfig.isQuoteIdentifier();

                Set<String> propertyNames = null;
                if ( jsonConfig.isValidatePropertyNames() ){
                    propertyNames = new HashSet<>(keys.size());
                }

                json.write('{');
                boolean didStart = false;
                for ( Object key : keys ){
                    if ( didStart ){
                        json.write(',');
                    }else{
                        didStart = true;
                    }
                    String propertyName = getEscapedPropertyName(key.toString(), jsonConfig);
                    propertyName = validatePropertyName(propertyName, jsonConfig, propertyNames);
                    // at least some interpreters will choke on surrogate pairs without quotes
                    boolean doQuote = quoteIdentifier || isReservedWord(propertyName) || hasSurrogates(propertyName);
                    if ( doQuote ){
                        json.write('"');
                    }
                    json.write(propertyName);
                    if ( doQuote ){
                        json.write('"');
                    }
                    json.write(':');
                    Object value = isMap ? map.get(key) : bundle.getObject((String)key);
                    appendPropertyValue(value, json, jsonConfig);
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
                        appendPropertyValue(value, json, jsonConfig);
                    }
                }else if ( propertyValue instanceof Enumeration ){
                    Enumeration<?> enumeration = (Enumeration<?>)propertyValue;
                    while ( enumeration.hasMoreElements() ){
                        if ( didStart ){
                            json.write(',');
                        }else{
                            didStart = true;
                        }
                        appendPropertyValue(enumeration.nextElement(), json, jsonConfig);
                    }
                }else{
                    // propertyValue.getClass().isArray() == true
                    Object array = propertyValue;
                    // Don't know the type of the array so can't cast it.  Use reflection.
                    for ( int i = 0, len = Array.getLength(array); i < len; i++ ){
                        if ( i > 0 ){
                            json.write(',');
                        }
                        appendPropertyValue(Array.get(array, i), json, jsonConfig);
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
                throw new LoopDetectionFailureException(stackIndex, jsonConfig);
            }
        }
    }

    /**
     * Append a simple property value to the given JSON buffer. This method is
     * used for numbers, strings and any other object that
     * {@link #appendPropertyValue(Object, Writer, JSONConfig)} doesn't
     * know about.  There is some risk of infinite recursion if the object
     * has a toString() method that references objects above this in the
     * data structure, so be careful about that.
     *
     * @param propertyValue The value to append.
     * @param json Something to write the JSON data to.
     * @param jsonConfig A configuration object to use.
     * @throws IOException If there is an error on output.
     */
    private static void appendSimplePropertyValue( Object propertyValue, Writer json, JSONConfig jsonConfig ) throws IOException
    {
        if ( propertyValue instanceof Number ){
            Number num = (Number)propertyValue;
            NumberFormat fmt = jsonConfig.getNumberFormat(num.getClass());
            String numericString = fmt == null ? num.toString() : fmt.format(num, new StringBuffer(), new FieldPosition(0)).toString();
            if ( isValidJSONNumber(numericString) ){
                json.write(numericString);
            }else{
                // Something isn't a kosher number for JSON, which is more
                // restrictive than Javascript for numbers.
                writeString(numericString, json, jsonConfig);
            }
        }else{
            writeString(propertyValue.toString(), json, jsonConfig);
        }
    }

    /**
     * Write a string to the output using escapes as needed.
     *
     * @param strValue The value to write.
     * @param json Something to write the JSON data to.
     * @param jsonConfig A configuration object to use.
     * @throws IOException If there is an error on output.
     */
    private static void writeString( String strValue, Writer json, JSONConfig jsonConfig ) throws IOException
    {
        if ( jsonConfig.isEncodeNumericStringsAsNumbers() && isValidJSONNumber(strValue) ){
            // no quotes.
            json.write(strValue);
        }else{
            // need to do escapes as required by ECMA JSON spec.
            json.write('"');
            boolean escapeNonAscii = jsonConfig.isEscapeNonAscii();
            boolean escapeSurrogates = jsonConfig.isEscapeSurrogates();
            boolean useECMA6CodePoints = jsonConfig.isUseECMA6CodePoints();
            if ( jsonConfig.isUnEscapeWherePossible() ){
                strValue = unEscape(strValue);
            }
            int i = 0, len = strValue.length();
            while ( i < len ){
                int codePoint = strValue.codePointAt(i);
                int charCount = Character.charCount(codePoint);
                switch ( codePoint ){
                    // escape characters as needed.
                    case '"':  json.append('\\').write('"'); break;
                    case '/':  json.append('\\').write('/'); break;
                    case '\b': json.append('\\').write('b'); break;
                    case '\f': json.append('\\').write('f'); break;
                    case '\n': json.append('\\').write('n'); break;
                    case '\r': json.append('\\').write('r'); break;
                    case '\t': json.append('\\').write('t'); break;
                    case '\\':
                        // check for escapes.
                        Matcher matcher = ESC_PAT.matcher(strValue.substring(i));
                        if ( matcher.find() && matcher.start() == 0 ){
                            // pass it through unchanged.
                            String esc = matcher.group(1);
                            json.write(esc);
                            i += esc.length() - 1;
                        }else{
                            json.append('\\').write('\\');
                        }
                        break;
                    default:
                        boolean doEscape = (escapeNonAscii && codePoint > 127)
                                            || (escapeSurrogates && charCount > 1)
                                            || codePoint < 0x20                     // JSON standard.
                                            || ! Character.isDefined(codePoint)
                                            || FORCE_ESCAPE_PAT.matcher(strValue.substring(i, i+charCount)).find();
                        if ( doEscape ){
                            if ( useECMA6CodePoints && (codePoint < 0x10 || codePoint > 0xFFFF) ){
                                // only very low or very high code points see an advantage.
                                json.write(String.format(CODE_POINT_FMT, codePoint));
                            }else{
                                json.write(String.format(CODE_UNIT_FMT, (int)strValue.charAt(i)));
                                if ( charCount > 1 ){
                                    json.write(String.format(CODE_UNIT_FMT, (int)strValue.charAt(i+1)));
                                }
                            }
                        }else{
                            json.write(strValue.charAt(i));
                            if ( charCount > 1 ){
                                json.write(strValue.charAt(i+1));
                            }
                        }
                        break;
                }
                i += charCount;
            }
            json.write('"');
        }
    }

    /**
     * Get the property name with escaping options applied as needed.
     *
     * @param propertyName The property name.
     * @param jsonConfig The config object with the flags.
     * @return the escaped property name.
     */
    private static String getEscapedPropertyName( String propertyName, JSONConfig jsonConfig )
    {
        String result = propertyName;
        if ( jsonConfig.isUnEscapeWherePossible() ){
            result = unEscape(result);
        }
        if ( jsonConfig.isEscapeNonAscii() ){
            result = escapeNonAscii(result, jsonConfig);
        }else if ( jsonConfig.isEscapeSurrogates() ){
            result = escapeSurrogates(result, jsonConfig);
        }
        return result;
    }

    /**
     * Validate a property name.
     *
     * @param propertyName the property name.
     * @param jsonConfig The JSONConfig obeject to get flags.
     * @param propertyNames The set of property names.  Used to detect duplicate property names.
     * @return the propertyName, which could possibly be modified.
     * @throws BadPropertyNameException if the property name is not a valid Javascript property name.
     * @throws DuplicatePropertyNameException if the name is already in the propertyNames set.
     */
    private static String validatePropertyName( String propertyName, JSONConfig jsonConfig, Set<String> propertyNames )
    {
        if ( jsonConfig.isValidatePropertyNames() ){
            if ( propertyNames.contains(propertyName) ){
                // very unlikely.  two key objects that are not equal would
                // have to produce identical toString() results.
                throw new DuplicatePropertyNameException(propertyName, jsonConfig);
            }
            propertyNames.add(propertyName);
            try{
                checkValidJavascriptPropertyName(propertyName, jsonConfig);
            }catch ( RuntimeException e ){
                if ( jsonConfig.isAllowReservedWordsInIdentifiers() && isReservedWord(propertyName) ){
                    // OK.
                }else if ( jsonConfig.isEscapeBadIdentifierCodePoints() && e instanceof BadPropertyNameException ){
                    // escape bad characters.
                    propertyName = escapeBadIdentifierCodePoints(propertyName, jsonConfig, e);
                }else{
                    jsonConfig.clearObjStack();
                    throw e;
                }
            }
        }
        return propertyName;
    }

    /**
     * Escape bad identifier code points.
     *
     * @param propertyname the name to escape.
     * @param jsonConfig The config object.
     * @param e The BadPropertyNameException that caused this to be called.
     * @return the escaped property name.
     */
    private static String escapeBadIdentifierCodePoints( String propertyName, JSONConfig jsonConfig, RuntimeException e )
    {
        if ( propertyName == null || propertyName.length() < 1 || isReservedWord(propertyName) ){
            // can't escape these.
            jsonConfig.clearObjStack();
            throw e;
        }
        StringBuilder buf = new StringBuilder();
        int i = 0, len = propertyName.length();
        boolean useECMA6Codepoints = jsonConfig.isUseECMA6CodePoints();
        while ( i < len ){
            int codePoint = propertyName.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if ( isValidIdentifierStart(codePoint) ){
                buf.appendCodePoint(codePoint);
            }else if ( i > 0 && isValidIdentifierPart(codePoint) ){
                buf.appendCodePoint(codePoint);
            }else if ( useECMA6Codepoints && (codePoint < 0x10 || codePoint > 0xFFFF) ){
                // ECMA 6 code point escape.
                // only very low or very high code points see an advantage.
                buf.append(String.format(CODE_POINT_FMT, codePoint));
            }else{
                // normal escape.
                buf.append(String.format(CODE_UNIT_FMT, (int)propertyName.charAt(i)));
                if ( charCount > 1 ){
                    buf.append(String.format(CODE_UNIT_FMT, (int)propertyName.charAt(i+1)));
                }
            }
            i += charCount;
        }

        return buf.toString();
    }

    /**
     * Return true if the input looks like a valid JSON number.
     *
     * @param numericString the string.
     * @return true if the string can be treated as a number in JSON.
     */
    private static boolean isValidJSONNumber( String numericString )
    {
        return JAVASCRIPT_NUMBER_PAT.matcher(numericString).matches() && ! OCTAL_NUMBER_PAT.matcher(numericString).matches();
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
            if ( Character.isSurrogate(str.charAt(i)) ){
                return true;
            }
        }
        return false;
    }

    /**
     * Escape surrogate pairs.
     *
     * @param str The input string.
     * @param jsonConfig the config object for flags.
     * @return The escaped string.
     */
    private static String escapeSurrogates( String str, JSONConfig jsonConfig )
    {
        if ( hasSurrogates(str) ){
            StringBuilder buf = new StringBuilder();
            int i = 0, len = str.length();
            boolean useECMA6CodePoints = jsonConfig.isUseECMA6CodePoints();
            while ( i < len ){
                int codePoint = str.codePointAt(i);
                int charCount = Character.charCount(codePoint);
                if ( charCount > 1 ){
                    if ( useECMA6CodePoints ){
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
        }else{
            return str;
        }
    }

    /**
     * Escape non-ascii.
     *
     * @param str The input string.
     * @param jsonConfig The config object for flags.
     * @return The escaped string.
     */
    private static String escapeNonAscii( String str, JSONConfig jsonConfig )
    {
        StringBuilder buf = new StringBuilder();
        int i = 0, len = str.length();
        boolean useECMA6CodePoints = jsonConfig.isUseECMA6CodePoints();
        while ( i < len ){
            int codePoint = str.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if ( codePoint > 127 ){
                if ( useECMA6CodePoints && codePoint > 0xFFFF ){
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
     * Undo escapes in input strings before formatting a string. This will get
     * rid of octal escapes and hex escapes and any unnecessary escapes. If the
     * characters still need to be escaped, then they will be re-escaped by the
     * caller. This will also convert ECMA 6 code point escapes to regular code
     * unit escapes assuming that you haven't enabled ECMA 6 code point escapes
     * on output.
     *
     * @param strValue Input string.
     * @return Unescaped string.
     */
    private static String unEscape( String strValue )
    {
        if ( strValue.indexOf('\\') < 0 ){
            // nothing to do.
            return strValue;
        }

        StringBuilder buf = new StringBuilder();
        int i = 0, len = strValue.length();
        while ( i < len ){
            int codePoint = strValue.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if ( codePoint == '\\' ){
                // check for escapes.
                boolean unEscape = false;
                String esc = null;
                Matcher matcher = ESC_PAT.matcher(strValue.substring(i));
                if ( matcher.find() && matcher.start() == 0 ){
                    unEscape = true;
                    esc = matcher.group(1);
                    matcher = OCTAL_ESC_PAT.matcher(esc);
                    if ( matcher.matches() ){
                        String oct = matcher.group(1);
                        char ch = (char)Integer.parseInt(oct, 8);
                        if ( ch > 0xFF ){
                            unEscape = false;
                        }else{
                            buf.append(ch);
                        }
                    }
                }
                if ( unEscape ){
                    matcher = CODE_UNIT_PAT.matcher(esc);
                    if ( matcher.matches() ){
                        // some risk if escape does bad surrogate pairs.
                        buf.append((char)Integer.parseInt(matcher.group(1),16));
                    }else if ( OTHER_ESC_PAT.matcher(esc).matches() ){
                        if ( esc.equals("\\v") ){
                            // Java doesn't understand \v, which is probably why it isn't allowed in JSON.
                            buf.appendCodePoint(0xB);
                        }else{
                            buf.append(String.format(esc));
                        }
                    }else{
                        matcher = CODE_POINT_PAT.matcher(esc);
                        if ( matcher.matches() ){
                            buf.appendCodePoint(Integer.parseInt(matcher.group(1),16));
                        }else{
                            matcher = HEX_ESC_PAT.matcher(esc);
                            if ( matcher.matches() ){
                                buf.append((char)Integer.parseInt(matcher.group(1),16));
                            }
                            // else octal, already handled.
                        }
                    }
                    i += esc.length() - 1;
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
     * Return the resource bundle for this package.
     *
     * @param locale A locale to use.  May be null.
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
        return new TreeSet<>(RESERVED_WORDS);
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
     * @return true if it's a valid code point to start an identifier.
     */
    static boolean isValidIdentifierStart( int codePoint )
    {
        return codePoint == '_' || codePoint == '$' || Character.isLetter(codePoint);
    }

    /**
     * Return true if the codePoint is a valid code point for part of an
     * identifier but not the start of an identifier.
     *
     * @param codePoint The code point.
     * @return true if the codePoint is a valid code point for part of an
     *         identifier but not the start of an identifier.
     */
    static boolean isValidIdentifierPart( int codePoint )
    {
        return Character.isDigit(codePoint) ||
                ((((1 << Character.NON_SPACING_MARK) | (1 << Character.COMBINING_SPACING_MARK) |
                (1 << Character.CONNECTOR_PUNCTUATION) ) >> Character.getType(codePoint)) & 1) != 0 ||
                codePoint == 0x200C || codePoint == 0x200D;
    }

    /**
     * Checks if the input string represents a valid Javascript property name.
     *
     * @param propertyName A Javascript property name to check.
     * @param jsonConfig A JSONConfig to use for locale.
     * @throws BadPropertyNameException If the propertyName is not a valid Javascript property name.
     */
    public static void checkValidJavascriptPropertyName( String propertyName, JSONConfig jsonConfig ) throws BadPropertyNameException
    {
        if ( propertyName == null || isReservedWord(propertyName) || ! VALID_JAVASCRIPT_PROPERTY_NAME_PAT.matcher(propertyName).matches() ){
            throw new BadPropertyNameException(propertyName, jsonConfig);
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
     * Constructor is private because this class should never be instantiated.
     */
    private JSONUtil()
    {
    }
}
