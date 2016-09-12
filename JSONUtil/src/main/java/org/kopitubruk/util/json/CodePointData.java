/*
 * Copyright 2016 Bill Davidson
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
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Iterate over Code point data in a string. Optionally handles escaping and
 * some unescaping.  Provides the escape/unescape code for the package.
 *
 * @author Bill Davidson
 */
class CodePointData
{
    /**
     * Javascript escapes, including those not permitted in JSON.
     */
    private static final Pattern JAVASCRIPT_ESC_PAT =
            Pattern.compile("(\\\\([bfnrtv\\\\/'\"]|(x\\p{XDigit}{2})|([0-3]?[0-7]{1,2})))");

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
    private static final Pattern ECMA5_ESCAPE_PASS_THROUGH_PAT = CODE_UNIT_PAT;

    /**
     * Escapes to pass through for ECMA6 when escaping bad identifier code points.
     */
    private static final Pattern ECMA6_ESCAPE_PASS_THROUGH_PAT =
            Pattern.compile("(\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})");

    /**
     * Map Javascript character escapes to their characters.
     */
    private static final Map<String,Character> JAVASCRIPT_ESC_MAP;

    /**
     * Map characters to JSON character escapes.
     */
    static final Map<Character,String> JSON_ESC_MAP;

    /**
     * These will break strings in eval() and so they need to be
     * escaped unless full JSON identifier code points is enabled
     * in which case the JSON should not be used with eval().
     */
    static final Set<Character> EVAL_ESC_SET;

    /**
     * Maximum length of a ECMAScript 6 code point escape.
     */
    private static final int MAX_CODE_POINT_ESC_LENGTH = 10;

    /**
     * Exact length of a code unit escape.
     */
    private static final int CODE_UNIT_ESC_LENGTH = 6;

    /**
     * Maximum length of other Javascript escapes (octal or hex).
     */
    private static final int MAX_JS_ESC_LENGTH = 4;

    /**
     * Initialize JSON_ESC_MAP, JAVASCRIPT_ESC_MAP and EVAL_ESC_SET.
     */
    static {
        Map<Character,String> jsonEscMap = new HashMap<>();
        jsonEscMap.put('"', "\\\"");
        jsonEscMap.put('/', "\\/");
        jsonEscMap.put('\b', "\\b");
        jsonEscMap.put('\f', "\\f");
        jsonEscMap.put('\n', "\\n");
        jsonEscMap.put('\r', "\\r");
        jsonEscMap.put('\t', "\\t");
        jsonEscMap.put('\\', "\\\\");
        JSON_ESC_MAP = new HashMap<>(jsonEscMap);

        Map<String,Character> javascriptEscMap = new HashMap<>();
        for ( Entry<Character,String> entry : JSON_ESC_MAP.entrySet() ){
            javascriptEscMap.put(entry.getValue(), entry.getKey());
        }
        // these two are valid in Javascript but not JSON.
        javascriptEscMap.put("\\'", '\'');
        javascriptEscMap.put("\\v", (char)0xB);
        JAVASCRIPT_ESC_MAP = new HashMap<>(javascriptEscMap);

        EVAL_ESC_SET = new HashSet<>(Arrays.asList(
                                        (char)0x2028,       // line separator
                                        (char)0x2029));     // paragraph separator
    }

    // private data and flags.
    private String strValue;
    private EscapeHandler handler;
    private int len;
    private boolean handleEscaping;
    private boolean haveSlash;
    private boolean useECMA6;
    private boolean useSingleLetterEscapes;
    private boolean supportEval;
    private boolean escapeNonAscii;
    private boolean escapeSurrogates;

    /**
     * If this is not null after a run of {@link #nextReady()} then it means
     * that a valid pass through escape has been detected or an escape
     * has been created.
     */
    private String esc;

    /**
     * This holds the char value(s) for the current code point.  Methods
     * passing the code point through will use this for write/append.
     */
    private char[] chars;

    /**
     * The index in the string of the current code point.
     */
    private int index;

    /**
     * The current code point.
     */
    private int codePoint;

    /**
     * The number of chars used by the current code point in the string.
     */
    private int charCount;

    /**
     * Make a CodePointData that handles escapes appropriately.
     *
     * @param strValue The string that will be analyzed.
     * @param cfg The config object.
     * @param useSingleLetterEscapes Use single letter escapes permitted by JSON.
     * @param processInlineEscapes If true, then process inline escapes.
     */
    CodePointData( String strValue, JSONConfig cfg, boolean useSingleLetterEscapes, boolean processInlineEscapes )
    {
        this(strValue, cfg);

        // enable escaping as needed.
        handleEscaping = true;
        this.useSingleLetterEscapes = useSingleLetterEscapes;
        supportEval = ! cfg.isFullJSONIdentifierCodePoints();
        escapeNonAscii = cfg.isEscapeNonAscii();
        escapeSurrogates = cfg.isEscapeSurrogates();

        haveSlash = processInlineEscapes && strValue.indexOf('\\') >= 0;
        if ( haveSlash ){
            handler = new EscapeHandler(this, cfg);
        }
    }

    /**
     * Make a CodePointData that doesn't do any escaping.
     *
     * @param strValue The string that will be analyzed.
     * @param cfg the config object.
     */
    CodePointData( String strValue, JSONConfig cfg )
    {
        // stuff that's common to both.
        this.strValue = strValue;
        chars = new char[2];
        len = strValue.length();
        index = 0;
        charCount = 0;
        useECMA6 = cfg.isUseECMA6();

        // no escaping with this one.
        handleEscaping = false;
    }

    /**
     * Get the current code point.
     *
     * @return The current code point.
     */
    int getCodePoint()
    {
        return codePoint;
    }

    /**
     * Get the number of chars needed to represent the current code point.
     *
     * @return The number of chars needed to represent the current code point.
     */
    int getCharCount()
    {
        return charCount;
    }

    /**
     * Get the index within the string for the current code point.
     *
     * @return The index of the current code point.
     */
    int getIndex()
    {
        return index;
    }

    /**
     * Set the current index within the string for this CodePointData.
     *
     * @param i The desired index.
     */
    void setIndex( int i )
    {
        index = i;
    }

    /**
     * Get the pass through or automatically generated escape for this code
     * point or null if there isn't one.
     *
     * @return The current escape or null if there isn't one.
     */
    String getEsc()
    {
        return esc;
    }

    /**
     * Write the current code point out as chars to the given writer.
     *
     * @param json the writer.
     * @throws IOException If there's an I/O problem.
     */
    void writeChars( Writer json ) throws IOException
    {
        json.write(chars, 0, charCount);
    }

    /**
     * Append the current code point out as chars to the given string builder.
     *
     * @param buf the string builder.
     */
    void appendChars( StringBuilder buf )
    {
        buf.append(chars, 0, charCount);
    }


    /**
     * Set up the next codepoint.
     * <p>
     * This initializes the next code point and its char data.
     * <p>
     * If escape handling is enabled then it will check for pass through
     * escapes and unescape illegal escapes and possibly get single
     * character escapes as appropriate.
     *
     * @return true if there's another code point.
     */
    boolean nextReady()
    {
        if ( (index+charCount) < len ){
            index += charCount;

            // set up code point and char data.
            codePoint = strValue.codePointAt(index);
            charCount = Character.charCount(codePoint);
            chars[0] = strValue.charAt(index);
            if ( charCount > 1 ){
                chars[1] = strValue.charAt(index+1);
            }

            if ( handleEscaping ){
                handleEscaping();
            }
            return true;
        }else{
            return false;
        }
    }

    /**
     * Handle escapes as appropriate.
     */
    private void handleEscaping()
    {
        esc = null;

        if ( haveSlash && codePoint == '\\' ){
            handler.doMatches();            // check for escapes.
        }

        if ( useSingleLetterEscapes && esc == null && codePoint <= '\\' ){
            esc = getEscape(chars[0]);      // single letter escapes for JSON.
        }

        // any other escapes requested or required.
        if ( esc == null && ((escapeNonAscii && codePoint > 127) ||
                             (escapeSurrogates && charCount > 1) ||
                             codePoint < 0x20 ||
                             ! Character.isDefined(codePoint) ||
                             (supportEval && EVAL_ESC_SET.contains(chars[0]))) ){
            esc = getEscapeString();
        }
    }

    /**
     * Get the Unicode escaped version of the current code point.
     *
     * @return the escaped version of the current code point.
     */
    String getEscapeString()
    {
        // Bad code point for an identifier.
        if ( useECMA6 && (codePoint < 0x10 || codePoint > 0xFFFF) ){
            // Use ECMAScript 6 code point escape.
            // only very low or very high code points see an advantage.
            return String.format("\\u{%X}", codePoint);
        }else{
            // Use normal escape.
            if ( charCount > 1 ){
                return String.format("\\u%04X\\u%04X", (int)chars[0], (int)chars[1]);
            }else{
                return String.format("\\u%04X", (int)chars[0]);
            }
        }
    }

    /**
     * Get the region end point for the given region length at
     * the current position.
     *
     * @param regionLength the length of the desired region.
     * @return The end point.
     */
    int end( int regionLength )
    {
        return Math.min(index+regionLength, len);
    }

    /**
     * Take a string containing a Javascript character escape and return its
     * char value.
     *
     * @param esc A string containing a Javascript hex, octal or single character escape.
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
     * Return true if there's a JSON single character escape for this char.
     *
     * @param c The char to check.
     * @return true if there's a JSON single character escape for this char.
     */
    static boolean haveJsonEsc( char c )
    {
        return JSON_ESC_MAP.containsKey(c);
    }

    /**
     * Get the escape pass through pattern for identifiers or strings.
     *
     * @param cfg A configuration object to determine which pattern to use.
     * @param useSingleLetterEscapes If true, then use a pattern that allows JSON single letter escapes.
     * @return The escape pass through pattern.
     */
    static Pattern getEscapePassThroughPattern( JSONConfig cfg, boolean useSingleLetterEscapes )
    {
        Pattern escapePassThroughPat;

        if ( useSingleLetterEscapes || cfg.isFullJSONIdentifierCodePoints() ){
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
     * Get the maximum region length of an escape pass through.
     *
     * @param cfg the config object.
     * @return the maximum region length.
     */
    static int getEscapePassThroughRegionLength( JSONConfig cfg )
    {
        return cfg.isUseECMA6() ? MAX_CODE_POINT_ESC_LENGTH : CODE_UNIT_ESC_LENGTH;
    }

    /**
     * Shorthand to apply a region to a matcher and find out if the desired
     * pattern is in that spot. Using limited regions limits the overhead of
     * using {@link Matcher#find()} which could otherwise be considerable for
     * long strings.
     *
     * @param matcher The matcher.
     * @param start The start of the region.
     * @param end The end of the region.
     * @return true if there is a match at the start of the region.
     */
    static boolean gotMatch( Matcher matcher, int start, int end )
    {
        matcher.region(start, end);
        return matcher.find() && matcher.start() == start;
    }

    /**
     * Undo escapes in input strings before formatting a string. This will get
     * rid of octal escapes and hex escapes and any unnecessary escapes. If the
     * characters still need to be escaped, then they will be re-escaped by the
     * caller.
     *
     * @param strValue Input string.
     * @param cfg The config object for flags.
     * @return Unescaped string.
     */
    static String unEscape( String strValue, JSONConfig cfg )
    {
        if ( strValue.indexOf('\\') < 0 ){
            // nothing to do.
            return strValue;
        }

        Matcher jsEscMatcher = JAVASCRIPT_ESC_PAT.matcher(strValue);
        Matcher codeUnitMatcher = CODE_UNIT_PAT.matcher(strValue);
        Matcher codePointMatcher = CODE_POINT_PAT.matcher(strValue);

        int lastBackSlash = strValue.lastIndexOf('\\');
        StringBuilder buf = new StringBuilder();
        CodePointData cp = new CodePointData(strValue, cfg);
        while ( cp.nextReady() ){
            if ( cp.codePoint == '\\' ){
                if ( gotMatch(jsEscMatcher, cp.index, cp.end(MAX_JS_ESC_LENGTH)) ){
                    String esc = jsEscMatcher.group(1);
                    buf.append(getEscapeChar(esc));
                    cp.index += esc.length() - cp.charCount;
                }else if ( gotMatch(codeUnitMatcher, cp.index, cp.end(CODE_UNIT_ESC_LENGTH)) ){
                    buf.append((char)Integer.parseInt(codeUnitMatcher.group(2),16));
                    cp.index += codeUnitMatcher.group(1).length() - cp.charCount;
                }else if ( gotMatch(codePointMatcher, cp.index, cp.end(MAX_CODE_POINT_ESC_LENGTH)) ){
                    buf.appendCodePoint(Integer.parseInt(codePointMatcher.group(2),16));
                    cp.index += codePointMatcher.group(1).length() - cp.charCount;
                }else{
                    // have '\' but nothing looks like a valid escape; just pass it through.
                    buf.append(cp.chars, 0, cp.charCount);
                }
                if ( cp.index >= lastBackSlash ){
                    // don't need these anymore.
                    jsEscMatcher = null;
                    codeUnitMatcher = null;
                    codePointMatcher = null;
                }
            }else{
                buf.append(cp.chars, 0, cp.charCount);          // not an escape.
            }
        }
        return buf.toString();
    }

    /**
     * Class to encapsulate redundant code for the escape handling for
     * {@link JSONUtil#writeString(String,Writer,JSONConfig)} and
     * {@link JSONUtil#escapeBadIdentiferCodePoints(String,JSONConfig)}
     */
    private static class EscapeHandler
    {
        // various matching variables.
        private Matcher passThroughMatcher;
        private Matcher jsEscMatcher;
        private Matcher codePointMatcher;
        private CodePointData cp;
        private int passThroughRegionLength;
        private int lastBackSlash;
        private boolean useECMA5;

        /**
         * Make an EscapeHandler.
         *
         * @param cp reference to the CodePointData
         * @param cfg the config object.
         * @param passThroughOnly if true, only do pass throughs.
         */
        private EscapeHandler( CodePointData cp, JSONConfig cfg )
        {
            this.cp = cp;
            lastBackSlash = cp.strValue.lastIndexOf('\\');

            // set up the pass through matcher.
            Pattern escapePassThroughPat = getEscapePassThroughPattern(cfg, cp.useSingleLetterEscapes);
            passThroughMatcher = escapePassThroughPat.matcher(cp.strValue);
            passThroughRegionLength = getEscapePassThroughRegionLength(cfg);

            // set up the javascript character escape matcher.
            jsEscMatcher = JAVASCRIPT_ESC_PAT.matcher(cp.strValue);

            useECMA5 = ! cfg.isUseECMA6();
            if ( useECMA5 ){
                // set up the ECMAScript 6 code point matcher,
                // because those will not be matched by the pass through.
                codePointMatcher = CODE_POINT_PAT.matcher(cp.strValue);
            }
        }

        /**
         * Do the matching for escapes.
         */
        private void doMatches()
        {
            // check for escapes.
            if ( gotMatch(passThroughMatcher, cp.index, cp.end(passThroughRegionLength)) ){
                // pass it through unchanged.
                cp.esc = passThroughMatcher.group(1);
                cp.index += cp.esc.length() - cp.charCount;
            }else if ( gotMatch(jsEscMatcher, cp.index, cp.end(MAX_JS_ESC_LENGTH)) ){
                // Any Javascript escapes that didn't make it through the pass through are not allowed.
                String jsEsc = jsEscMatcher.group(1);
                cp.codePoint = cp.chars[0] = getEscapeChar(jsEsc);
                cp.index += jsEsc.length() - cp.charCount;
            }else if ( useECMA5 && gotMatch(codePointMatcher, cp.index, cp.end(MAX_CODE_POINT_ESC_LENGTH)) ){
                /*
                 * Only get here if it wasn't passed through => useECMA6 is
                 * false.  Convert it to an inline codepoint.  Maybe something
                 * later will escape it legally.
                 */
                cp.codePoint = Integer.parseInt(codePointMatcher.group(2),16);
                if ( cp.codePoint > 0xFFFF ){
                    cp.charCount = 2;
                    cp.chars[0] = Character.highSurrogate(cp.codePoint);
                    cp.chars[1] = Character.lowSurrogate(cp.codePoint);
                }else{
                    cp.chars[0] = (char)cp.codePoint;
                }
                cp.index += codePointMatcher.group(1).length() - cp.charCount;
            }

            if ( cp.index >= lastBackSlash ){
                // this handler is no longer needed.
                cp.haveSlash = false;
                cp.handler = null;
            }
        }
    } // class EscapeHandler
}
