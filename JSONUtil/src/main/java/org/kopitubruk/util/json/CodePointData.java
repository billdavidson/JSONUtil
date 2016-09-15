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
import java.util.HashMap;
import java.util.Map;
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
     * Unicode line separator - breaks Javascript eval().
     */
    static final char LINE_SEPARATOR = 0x2028;

    /**
     * Unicode paragraph separator - breaks Javascript eval().
     */
    static final char PARAGRAPH_SEPARATOR = 0x2029;

    /**
     * Maximum valid ASCII code point.
     */
    static final char MAX_ASCII = 0x7F;

    /**
     * This gets used a lot and the encoding is ugly.
     */
    private static final char BACKSLASH = '\\';

    /**
     * Maximum char that corresponds to single letter escapes.
     */
    private static final char MAX_SINGLE_ESC_CHAR = BACKSLASH;

    /*
     * Various escape checkers.
     */
    private static final EscapeChecker ASCII_EVAL_EC = new AsciiEvalEscapeChecker();
    private static final EscapeChecker SURROGATE_EVAL_EC = new SurrogateEvalEscapeChecker();
    private static final EscapeChecker EVAL_EC = new EvalEscapeChecker();
    private static final EscapeChecker ASCII_EC = new AsciiEscapeChecker();
    private static final EscapeChecker SURROGATE_EC = new SurrogateEscapeChecker();
    private static final EscapeChecker BASIC_EC = new BasicEscapeChecker();

    /**
     * Initialize JSON_ESC_MAP, JAVASCRIPT_ESC_MAP and EVAL_ESC_SET.
     */
    static {
        Map<Character,String> jsonEscMap = new HashMap<>();
        jsonEscMap.put('\b', "\\b");
        jsonEscMap.put('\t', "\\t");
        jsonEscMap.put('\n', "\\n");
        jsonEscMap.put('\f', "\\f");
        jsonEscMap.put('\r', "\\r");
        jsonEscMap.put('"', "\\\"");
        jsonEscMap.put('/', "\\/");
        jsonEscMap.put(BACKSLASH, "\\\\");
        JSON_ESC_MAP = new HashMap<>(jsonEscMap);

        Map<String,Character> javascriptEscMap = new HashMap<>();
        for ( Entry<Character,String> entry : JSON_ESC_MAP.entrySet() ){
            javascriptEscMap.put(entry.getValue(), entry.getKey());
        }
        // these two are valid in Javascript but not JSON.
        javascriptEscMap.put("\\v", (char)0xB);
        javascriptEscMap.put("\\'", '\'');
        JAVASCRIPT_ESC_MAP = new HashMap<>(javascriptEscMap);
    }

    // private data and flags.
    private String strValue;
    private EscapeHandler handler;
    private EscapeChecker escChecker;
    private int nextIndex;
    private int len;
    private int lastEscIndex;
    private boolean handleEscaping;
    private boolean haveSlash;
    private boolean useECMA6;
    private boolean useSingleLetterEscapes;
    private boolean supportEval;
    private boolean escapeNonAscii;
    private boolean escapeSurrogates;
    private boolean noEscapes;
    private boolean isSurrogatePair;
    private boolean isMalformed;

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
        this.useSingleLetterEscapes = useSingleLetterEscapes;
        supportEval = ! cfg.isFullJSONIdentifierCodePoints();
        escapeNonAscii = cfg.isEscapeNonAscii();
        escapeSurrogates = cfg.isEscapeSurrogates();
        escChecker = null;

        lastEscIndex = len;
        int lastBackSlash = strValue.lastIndexOf(BACKSLASH);
        haveSlash = lastBackSlash >= 0;
        if ( haveSlash ){
            noEscapes = false;
            if ( processInlineEscapes ){
                handler = new EscapeHandler(cfg, lastBackSlash);
            }else{
                haveSlash = false;
            }
        }else{
            // check if there is any escaping to be done.
            noEscapes = haveNoEscapes(strValue);
        }
        handleEscaping = ! noEscapes;
        if ( handleEscaping  ){
            if ( escChecker == null ){
                escChecker = getEscapeChecker();
            }
        }else{
            esc = null;
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
        nextIndex = 0;
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
        nextIndex = i;
        index = nextIndex - charCount;
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
     * If true, then there are no escapes in this string.
     *
     * @return If true, then there are no escapes in this string.
     */
    boolean isNoEscapes()
    {
        return noEscapes;
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
        if ( nextIndex < len ){
            index = nextIndex;

            // set up code point and char data.
            charCount = 1;
            codePoint = chars[0] = strValue.charAt(index);
            isMalformed = isSurrogatePair = Character.isSurrogate(chars[0]);
            if ( isSurrogatePair ){
                isSurrogatePair = ++nextIndex < len;
                if ( isSurrogatePair ){
                    chars[1] = strValue.charAt(nextIndex);
                    isSurrogatePair = Character.isSurrogatePair(chars[0], chars[1]);
                    if ( isSurrogatePair ){
                        isMalformed = false;
                        charCount = 2;
                        codePoint = Character.toCodePoint(chars[0], chars[1]);
                    }else{
                        --nextIndex;
                    }
                }else{
                    --nextIndex;
                }
            }
            ++nextIndex;

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

        if ( index > lastEscIndex ){
            handler = null;
            handleEscaping = false;
            return;
        }

        if ( haveSlash && chars[0] == BACKSLASH ){
            handler.doMatches();            // check for escapes.
        }

        if ( useSingleLetterEscapes && esc == null && chars[0] <= MAX_SINGLE_ESC_CHAR ){
            esc = getEscape(chars[0]);      // single letter escapes for JSON.
        }

        // any other escapes requested or required.
        if ( esc == null ){
            if ( isMalformed ){
                esc = getEscapeString();
            }else if ( isSurrogatePair ){
                if ( escChecker.needEscape(codePoint, chars[0]) ){
                    esc = getEscapeString();
                }
            }else if ( escChecker.needEscape(chars[0]) ){
                esc = getEscapeString();
            }
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
            if ( isSurrogatePair ){
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
     * Check if the string contains any characters that need to be escaped.
     * This searches this string from the end so that it can record the
     * index of the last character that needs to be escaped.
     *
     * @param strValue The string
     */
    private boolean haveNoEscapes( String strValue )
    {
        escChecker = getEscapeChecker();
        int i = len;
        while ( i > 0 ){
            --i;
            char ch1 = strValue.charAt(i);
            if ( Character.isSurrogate(ch1) ){
                boolean malformed = true;
                if ( --i >= 0 ){
                    char ch0 = strValue.charAt(i);
                    if ( Character.isSurrogatePair(ch0, ch1) ){
                        malformed = false;
                        if ( escChecker.needEscape(Character.toCodePoint(ch0, ch1), ch0) ){
                            lastEscIndex = i;
                            return false;
                        }
                    }
                }
                if ( malformed ){
                    lastEscIndex = ++i;
                    return false;
                }
            }else if ( escChecker.needEscape(ch1) ){
                lastEscIndex = i;
                return false;
            }
        }
        escChecker = null;
        return true;
    }

    /**
     * Get an escape checker appropriate for the current flags.
     *
     * @return An escape checker appropriate for the current flags.
     */
    private EscapeChecker getEscapeChecker()
    {
        if ( supportEval ){
            if ( escapeNonAscii ){
                return ASCII_EVAL_EC;
            }else if ( escapeSurrogates ){
                return SURROGATE_EVAL_EC;
            }else{
                return EVAL_EC;
            }
        }else if ( escapeNonAscii ){
            return ASCII_EC;
        }else if ( escapeSurrogates ){
            return SURROGATE_EC;
        }else{
            return BASIC_EC;
        }
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
     * Return true if the character is one of the non-control characters
     * that has to be escaped or something that breaks eval.
     *
     * @param ch the char to check.
     * @return true if the char needs to be escaped based upon this test.
     */
    private static boolean isControl( char ch )
    {
        return ch < ' ';
    }

    /**
     * Return true if the character is a control character or non-ASCII
     *
     * @param ch the char to check.
     * @return true if the char needs to be escaped based upon this test.
     */
    private static boolean isNotAscii( char ch )
    {
        return ch < ' ' || ch > MAX_ASCII;
    }

    /**
     * Return true if the character is one of the non-control characters
     * that has to be escaped.
     *
     * @param ch the char to check.
     * @return true if the char needs to be escaped based upon this test.
     */
    private static boolean isEsc( char ch )
    {
        switch ( ch ){
            case '"':
            case '/':
            case BACKSLASH:
                return true;
        }
        return false;
    }

    /**
     * Return true if the character is one of the non-control characters
     * that has to be escaped or something that breaks eval.
     *
     * @param ch the char to check.
     * @return true if the char needs to be escaped based upon this test.
     */
    private static boolean isEvalEsc( char ch )
    {
        switch ( ch ){
            case '"':
            case '/':
            case BACKSLASH:
            case LINE_SEPARATOR:
            case PARAGRAPH_SEPARATOR:
                return true;
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
     * @param cfg The config object for flags.
     * @return Unescaped string.
     */
    static String unEscape( String strValue, JSONConfig cfg )
    {
        if ( strValue.indexOf(BACKSLASH) < 0 ){
            // nothing to do.
            return strValue;
        }

        Matcher jsEscMatcher = JAVASCRIPT_ESC_PAT.matcher(strValue);
        Matcher codeUnitMatcher = CODE_UNIT_PAT.matcher(strValue);
        Matcher codePointMatcher = CODE_POINT_PAT.matcher(strValue);

        int lastBackSlash = strValue.lastIndexOf(BACKSLASH);
        StringBuilder buf = new StringBuilder();
        CodePointData cp = new CodePointData(strValue, cfg);
        while ( cp.nextReady() ){
            if ( cp.codePoint == BACKSLASH ){
                if ( gotMatch(jsEscMatcher, cp.index, cp.end(MAX_JS_ESC_LENGTH)) ){
                    String esc = jsEscMatcher.group(1);
                    buf.append(getEscapeChar(esc));
                    cp.nextIndex += esc.length();
                }else if ( gotMatch(codeUnitMatcher, cp.index, cp.end(CODE_UNIT_ESC_LENGTH)) ){
                    buf.append((char)Integer.parseInt(codeUnitMatcher.group(2),16));
                    cp.nextIndex += codeUnitMatcher.group(1).length();
                }else if ( gotMatch(codePointMatcher, cp.index, cp.end(MAX_CODE_POINT_ESC_LENGTH)) ){
                    buf.appendCodePoint(Integer.parseInt(codePointMatcher.group(2),16));
                    cp.nextIndex += codePointMatcher.group(1).length();
                }else{
                    // have '\' but nothing looks like a valid escape; just pass it through.
                    buf.append(cp.chars, 0, cp.charCount);
                }
                if ( cp.nextIndex > lastBackSlash ){
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
    private class EscapeHandler
    {
        // various matching variables.
        private Matcher passThroughMatcher;
        private Matcher jsEscMatcher;
        private Matcher codePointMatcher;
        private int passThroughRegionLength;
        private int lastBackSlash;
        private boolean useECMA5;

        /**
         * Make an EscapeHandler.
         *
         * @param cfg the config object.
         * @param lastBackSlash the index of the last backslash in the string.
         * @param passThroughOnly if true, only do pass throughs.
         */
        private EscapeHandler( JSONConfig cfg, int lastBackSlash )
        {
            this.lastBackSlash = lastBackSlash;

            // set up the pass through matcher.
            Pattern escapePassThroughPat = getEscapePassThroughPattern(cfg, useSingleLetterEscapes);
            passThroughMatcher = escapePassThroughPat.matcher(strValue);
            passThroughRegionLength = getEscapePassThroughRegionLength(cfg);

            // set up the javascript character escape matcher.
            jsEscMatcher = JAVASCRIPT_ESC_PAT.matcher(strValue);

            useECMA5 = ! cfg.isUseECMA6();
            if ( useECMA5 ){
                // set up the ECMAScript 6 code point matcher,
                // because those will not be matched by the pass through.
                codePointMatcher = CODE_POINT_PAT.matcher(strValue);
            }
        }

        /**
         * Do the matching for escapes.
         */
        private void doMatches()
        {
            // check for escapes.
            if ( gotMatch(passThroughMatcher, index, end(passThroughRegionLength)) ){
                // pass it through unchanged.
                esc = passThroughMatcher.group(1);
                nextIndex += esc.length();
            }else if ( gotMatch(jsEscMatcher, index, end(MAX_JS_ESC_LENGTH)) ){
                // Any Javascript escapes that didn't make it through the pass through are not allowed.
                String jsEsc = jsEscMatcher.group(1);
                codePoint = chars[0] = getEscapeChar(jsEsc);
                nextIndex += jsEsc.length();
            }else if ( useECMA5 && gotMatch(codePointMatcher, index, end(MAX_CODE_POINT_ESC_LENGTH)) ){
                /*
                 * Only get here if it wasn't passed through => useECMA6 is
                 * false.  Convert it to an inline codepoint.  Maybe something
                 * later will escape it legally.
                 */
                codePoint = Integer.parseInt(codePointMatcher.group(2),16);
                if ( codePoint > 0xFFFF ){
                    charCount = 2;
                    chars[0] = Character.highSurrogate(codePoint);
                    chars[1] = Character.lowSurrogate(codePoint);
                }else{
                    chars[0] = (char)codePoint;
                }
                nextIndex += codePointMatcher.group(1).length();
            }

            if ( nextIndex > lastBackSlash ){
                // this handler is no longer needed.
                haveSlash = false;
                handler = null;
            }
        }
    } // class EscapeHandler

    /**
     * This interface defines an escape checker.
     */
    private interface EscapeChecker
    {
        /**
         * Does the actual checking.
         *
         * @param ch the char
         * @return true of the char needs to be escaped.
         */
        boolean needEscapeImpl( char ch );

        /**
         * Return true if the given char needs to be escaped.
         *
         * @param ch the char.
         * @return true if the given char needs to be escaped.
         */
        boolean needEscape( char ch );

        /**
         * Return true if the given code point needs to be escaped.
         *
         * @param cp the code point.
         * @param ch the char
         * @return true if the given code point needs to be escaped.
         */
        boolean needEscape( int cp, char ch );
    }

    /**
     * Abstract class for escape checkers.
     */
    private static abstract class AbstractEscapeChecker implements EscapeChecker
    {
        public boolean needEscape( char ch )
        {
            return needEscapeImpl(ch) || ! Character.isDefined(ch);
        }

        public boolean needEscape( int cp, char ch )
        {
            return needEscapeImpl(ch) || ! Character.isDefined(cp);
        }
    }

    /**
     * Checks for controls and other escapes without eval protection.
     */
    private static class BasicEscapeChecker extends AbstractEscapeChecker
    {
        public boolean needEscapeImpl( char ch )
        {
            return isControl(ch) || isEsc(ch);
        }
    }

    /**
     * Checks for controls and other escapes with eval protection.
     */
    private static class EvalEscapeChecker extends AbstractEscapeChecker
    {
        public boolean needEscapeImpl( char ch )
        {
            return isControl(ch) || isEvalEsc(ch);
        }
    }

    /**
     * Checks for controls, non-ASCII and other escapes without eval protection.
     */
    private static class AsciiEscapeChecker extends AbstractEscapeChecker
    {
        public boolean needEscapeImpl( char ch )
        {
            return isNotAscii(ch) || isEsc(ch);
        }
    }

    /**
     * Checks for controls, non-ASCII and other escapes with eval protection.
     */
    private static class AsciiEvalEscapeChecker extends AbstractEscapeChecker
    {
        public boolean needEscapeImpl( char ch )
        {
            return isNotAscii(ch) || isEvalEsc(ch);
        }
    }

    /**
     * Checks for controls, other escapes without eval protection and surrogates.
     */
    private static class SurrogateEscapeChecker extends AbstractEscapeChecker
    {
        public boolean needEscapeImpl( char ch )
        {
            return isControl(ch) || isEsc(ch) || Character.isSurrogate(ch);
        }
    }

    /**
     * Checks for controls, other escapes with eval protection and surrogates.
     */
    private static class SurrogateEvalEscapeChecker extends AbstractEscapeChecker
    {
        public boolean needEscapeImpl( char ch )
        {
            return isControl(ch) || isEvalEsc(ch) || Character.isSurrogate(ch);
        }
    }
}
