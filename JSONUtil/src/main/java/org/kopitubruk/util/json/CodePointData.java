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
     * Single letter escapes.  These get used a lot.
     */
    private static final String BS = "\\b";
    private static final String TAB = "\\t";
    private static final String NL = "\\n";
    private static final String FF = "\\f";
    private static final String CR = "\\r";
    private static final String DQ = "\\\"";
    private static final String SL = "\\/";
    private static final String BK = "\\\\";

    /*
     * Arrays of escapes for control characters.
     */
    private static final int NUM_CONTROLS = 16;
    private static final String[] UNICODE_ESC = new String[NUM_CONTROLS];
    private static final String[] SINGLE_ESC = new String[NUM_CONTROLS];
    private static final String[] ECMA6_ESC = new String[NUM_CONTROLS];
    private static final String[] ECMA6_SINGLE_ESC = new String[NUM_CONTROLS];

    /*
     * Array of hex digits used to generate Unicode escapes.
     */
    private static final char[] HEX_DIGITS = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};

    /*
     * Initial buffer patterns for generating escapes.
     */
    private static final char[] INITIAL_ONEBUF  = "\\u....".toCharArray();
    private static final char[] INITIAL_TWOBUF  = "\\u....\\u....".toCharArray();
    private static final char[] INITIAL_ECMA6_5 = "\\u{.....}".toCharArray();
    private static final char[] INITIAL_ECMA6_6 = "\\u{10....}".toCharArray();

    /*
     * Unicode replacement character. Used to replace unmatched surrogates and
     * undefined code points.
     */
    static final char UNICODE_REPLACEMENT_CHARACTER = 0xFFFD;

    /*
     * Initialize static data
     */
    static {
        JSON_ESC_MAP = new HashMap<Character,String>(8);
        JSON_ESC_MAP.put('\b', BS);
        JSON_ESC_MAP.put('\t', TAB);
        JSON_ESC_MAP.put('\n', NL);
        JSON_ESC_MAP.put('\f', FF);
        JSON_ESC_MAP.put('\r', CR);
        JSON_ESC_MAP.put('"', DQ);
        JSON_ESC_MAP.put('/', SL);
        JSON_ESC_MAP.put(BACKSLASH, BK);

        JAVASCRIPT_ESC_MAP = new HashMap<String,Character>(JSON_ESC_MAP.size()+2);
        for ( Entry<Character,String> entry : JSON_ESC_MAP.entrySet() ){
            JAVASCRIPT_ESC_MAP.put(entry.getValue(), entry.getKey());
        }
        // these two are valid in Javascript but not JSON.
        JAVASCRIPT_ESC_MAP.put("\\v", (char)0xB);
        JAVASCRIPT_ESC_MAP.put("\\'", '\'');

        /*
         * Create escapes for control characters based on various flags. Used to
         * speed up escapes for some of the most common escaped characters.  Also
         * handles ECMAScript 6 code point escapes with single digits which are
         * not handled properly by getEscapeString without this.
         */
        for ( char ch = 0; ch < NUM_CONTROLS; ch++ ){
            String single = null;
            int i = ch;
            switch ( ch ){
                case '\b': single = BS; break;
                case '\t': single = TAB; break;
                case '\n': single = NL; break;
                case '\f': single = FF; break;
                case '\r': single = CR; break;
            }
            String esc = String.format("\\u%04X", i);
            String esc6 = String.format("\\u{%X}", i);
            UNICODE_ESC[i] = esc;
            SINGLE_ESC[i] = single != null ? single : esc;
            ECMA6_ESC[i] = esc6;
            ECMA6_SINGLE_ESC[i] = single != null ? single : esc6;
        }
    }

    // private data and flags.
    private JSONConfig cfg;
    private String strValue;                    // the string being processed.
    private EscapeHandler handler;
    private EscapeChecker escChecker;
    private String[] controls;                  // pre-computed escapes for controls.
    private char[] oneBuf = null;
    private char[] twoBuf = null;
    private char[] ecma6_5 = null;
    private char[] ecma6_6 = null;
    private int nextIndex;                      // index in the string of the next code point
    private int len;                            // length of the string.
    private int lastProcessIndex;               // index of the last code point that needs processing in the string.
    private int beginIndex;                     // beginning of current substring that doesn't need escaping.
    private int endIndex;                       // ending of the current substring that doesn't need escaping.
    private int unmatchedSurrogatePolicy;
    private int undefinedCodePointPolicy;

    /*
     * These are integer booleans because integer checks against zero are faster
     * than booleans on Intel architecture JVM's.  I'm guessing because booleans
     * are stored as bit fields.  These are all used inside the critical loop
     * for writeString().
     */
    private int handleEscaping;
    private int processInlineEscapes;
    private int useECMA6;
    private int useSingleLetterEscapes;
    private int escapeSurrogates;
    private int isSupplementary;
    private int isUnmatchedSurrogate;
    private int isDefined;
    private int isReplaced;
    private int didDiscard;
    private int haveCodePoint;

    /*
     * Regular booleans that are only used outside the critical loop.
     */
    private boolean noEscapes;
    private boolean supportEval;
    private boolean escapeNonAscii;

    /**
     * If this is not null after a run of {@link #nextReady()} then it means
     * that a valid pass through escape has been detected or an escape
     * has been created.
     */
    private String escape;

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
        this.useSingleLetterEscapes = useSingleLetterEscapes ? 1 : 0;
        this.processInlineEscapes = processInlineEscapes ? 1 : 0;
        supportEval = ! cfg.isFullJSONIdentifierCodePoints();
        escapeNonAscii = cfg.isEscapeNonAscii();
        escapeSurrogates = cfg.isEscapeSurrogates() ? 1 : 0;

        // check if there is any escaping to be done.
        lastProcessIndex = findLastProcess();
        noEscapes = lastProcessIndex < 0;
        handleEscaping = noEscapes ? 0 : 1;

        if ( handleEscaping != 0 ){
            if ( this.useSingleLetterEscapes != 0 ){
                controls = useECMA6 != 0 ? ECMA6_SINGLE_ESC : SINGLE_ESC;
            }

            if ( this.processInlineEscapes != 0 ){
                int lastBackSlash = strValue.lastIndexOf(BACKSLASH);
                this.processInlineEscapes = lastBackSlash >= 0 ? 1 : 0;
                if ( this.processInlineEscapes != 0 ){
                    handler = new EscapeHandler(cfg, lastBackSlash);
                }
            }
        }else{
            escape = null;
        }
    }

    /**
     * Make a CodePointData that handles escapes appropriately.
     *
     * @param strValue The string that will be analyzed.
     * @param cfg The config object.
     * @param processInlineEscapes If true, then process inline escapes.
     */
    CodePointData( String strValue, JSONConfig cfg, boolean processInlineEscapes )
    {
        this(strValue, cfg);
        beginIndex = -1;

        this.useSingleLetterEscapes = 1;
        this.processInlineEscapes = processInlineEscapes ? 1 : 0;
        supportEval = ! cfg.isFullJSONIdentifierCodePoints();
        escapeNonAscii = cfg.isEscapeNonAscii();
        escapeSurrogates = (escapeNonAscii || cfg.isEscapeSurrogates()) ? 1 : 0;

        controls = useECMA6 != 0 ? ECMA6_SINGLE_ESC : SINGLE_ESC;

        if ( this.processInlineEscapes != 0 ){
            int lastBackSlash = strValue.lastIndexOf(BACKSLASH);
            this.processInlineEscapes = lastBackSlash >= 0 ? 1 : 0;
            if ( this.processInlineEscapes != 0 ){
                handler = new EscapeHandler(cfg, lastBackSlash);
            }
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
        this.cfg = cfg;
        chars = new char[2];
        len = strValue.length();
        index = 0;
        nextIndex = 0;
        charCount = 0;
        useECMA6 = cfg.isUseECMA6() ? 1 : 0;

        useSingleLetterEscapes = 0;
        unmatchedSurrogatePolicy = cfg.getUnmatchedSurrogatePolicy();
        undefinedCodePointPolicy = cfg.getUndefinedCodePointPolicy();

        controls = useECMA6 != 0 ? ECMA6_ESC : UNICODE_ESC;

        // no escaping with this one.
        handleEscaping = 0;
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
    String getEscape()
    {
        return escape;
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
     * Append the current code point out as chars to the given string builder.
     *
     * @param buf the string builder.
     */
    void appendChars( StringBuilder buf )
    {
        buf.append(chars, 0, charCount);
    }

    /**
     * Write the current string to the given Writer using escaping as needed or
     * requested by the configuration options. If there are no code points that
     * need to be escaped then this method just writes the entire string. If
     * there are code points that need to be escaped, then substrings which don't
     * contain any characters to be escaped are written with a single write for
     * better efficiency.
     *
     * @param json The writer.
     * @throws IOException if there's an I/O error.
     */
    void writeString( Writer json ) throws IOException
    {
        lastProcessIndex = findLastProcess();

        if ( lastProcessIndex < 0 ){
            json.write(strValue);   // nothing to process.
            return;
        }

        // escaping necessary.
        while ( nextIndex < len ){
            nextCodePoint();

            if ( haveCodePoint != 0 ){
                // check for escapes.
                if ( processInlineEscapes != 0 && chars[0] == BACKSLASH ){
                    String esc = handler.checkInlineEscape();
                    if ( esc != null ){
                        writeEscape(json, esc);
                    }else{
                        writeChars(json);
                    }
                }else if ( needEscape() ){
                    writeEscape(json, getEscapeString());
                }else if ( isReplaced != 0 ){
                    writeChars(json);
                }else{
                    if ( didDiscard != 0 ){
                        flushCurrentSubstring(json);
                    }
                    if ( beginIndex < 0 ){
                        beginIndex = index; // start a new sub string.
                    }
                    endIndex = nextIndex;   // extend the current substring.
                }
            }
        }

        flushCurrentSubstring(json);
    }

    /**
     * Return true if an escape is needed for the current code point.
     *
     * @return true if an escape is needed.
     */
    private boolean needEscape()
    {
        if ( isSupplementary == 0 ){
            return escChecker.needEscape(chars[0]);
        }else{
            return escChecker.needEscape();
        }
    }

    /**
     * Write the given escape to the given writer.
     *
     * @param json the writer
     * @param esc the escape.
     * @throws IOException if there's an I/O error.
     */
    private void writeEscape( Writer json, String esc ) throws IOException
    {
        flushCurrentSubstring(json);
        json.write(esc);
        checkLastProcess();
    }

    /**
     * Write the chars to the given writer. This is only used when
     * processInlineEscapes is true and an escape was found in the input string
     * that got converted to a code point that doesn't need to be escaped.
     * Because this code point is not in the input string, the current substring
     * needs to be flushed so that this code point can be appended to the
     * output.
     *
     * @param json the writer
     * @throws IOException if there's an I/O error.
     */
    private void writeChars( Writer json ) throws IOException
    {
        flushCurrentSubstring(json);
        json.write(chars, 0, charCount);
        checkLastProcess();
    }

    /**
     * Write the current substring of unescaped chars, if any.
     *
     * @param json The writer.
     * @throws IOException if there's an I/O error.
     */
    private void flushCurrentSubstring( Writer json ) throws IOException
    {
        if ( beginIndex >= 0 ){
            json.write(strValue, beginIndex, endIndex - beginIndex);
            beginIndex = -1;
        }
    }

    /**
     * Check if the current index is the last code point that needs to be
     * modified and if so, set up to write out the rest of the string.
     */
    private void checkLastProcess()
    {
        if ( nextIndex > lastProcessIndex ){
            // did last escape. end escape processing.
            beginIndex = nextIndex;
            endIndex = nextIndex = len;
        }
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
            nextCodePoint();

            if ( haveCodePoint != 0 ){
                if ( handleEscaping != 0 ){
                    escape = getEscapeIfNeeded();
                }
                return true;
            }else{
                return false;
            }
        }else{
            return false;
        }
    }

    /**
     * Set up the next code point.
     */
    private void nextCodePoint()
    {
        haveCodePoint = 0;
        didDiscard = 0;

        while ( haveCodePoint == 0 && nextIndex < len ){
            index = nextIndex;
            haveCodePoint = 1;
            isReplaced = 0;
            charCount = 1;
            codePoint = chars[0] = strValue.charAt(index);
            isUnmatchedSurrogate = isSupplementary = isSurrogate(chars[0]);
            if ( isSupplementary != 0 ){
                if ( index+1 < len ){
                    chars[1] = strValue.charAt(index+1);
                    if ( Character.isSurrogatePair(chars[0], chars[1]) ){
                        isUnmatchedSurrogate = 0;
                        charCount = 2;
                        codePoint = Character.toCodePoint(chars[0], chars[1]);
                        isDefined = isDefined(codePoint);
                        ++nextIndex;
                    }
                }
                if ( isUnmatchedSurrogate != 0 ){
                    handleUnmatchedSurrogate();
                }
            }else{
                isDefined = isDefined(chars[0]);
            }
            if ( isDefined == 0 ){
                handleUndefined();
            }

            ++nextIndex;
        }
    }

    /**
     * Do what needs to be done for an unmatched surrogate.
     */
    private void handleUnmatchedSurrogate()
    {
        switch ( unmatchedSurrogatePolicy ){
            case JSONConfig.REPLACE:
                replaceCodePoint();
                break;
            case JSONConfig.DISCARD:
                didDiscard = 1;
                haveCodePoint = 0;
                break;
            case JSONConfig.EXCEPTION:
                throw new UnmatchedSurrogateException(cfg, strValue, index, chars[0]);
            default:
                isDefined = isDefined(chars[0]);
                isSupplementary = 0;
                break;
        }
    }

    /**
     * Do what needs to be done for an undefined code point.
     */
    private void handleUndefined()
    {
        switch ( undefinedCodePointPolicy ){
            case JSONConfig.REPLACE:
                replaceCodePoint();
                break;
            case JSONConfig.DISCARD:
                didDiscard = 1;
                haveCodePoint = 0;
                break;
            case JSONConfig.EXCEPTION:
                throw new UndefinedCodePointException(cfg, strValue, index, codePoint);
        }
    }

    /**
     * Replace the current code point with the Unicode replacement character.
     */
    private void replaceCodePoint()
    {
        codePoint = chars[0] = UNICODE_REPLACEMENT_CHARACTER;
        charCount = 1;
        isSupplementary = isUnmatchedSurrogate = 0;
        isDefined = isReplaced = 1;
    }

    /**
     * Handle escapes as appropriate.
     *
     * @return an escape if one is needed or null if not.
     */
    private String getEscapeIfNeeded()
    {
        if ( index > lastProcessIndex ){
            // past last escape -- disable escape checking.
            handleEscaping = 0;
            return null;
        }else if ( processInlineEscapes != 0 && chars[0] == BACKSLASH ){
            return handler.checkInlineEscape();
        }else if ( needEscape() ){
            return getEscapeString();
        }else{
            return null;
        }
    }

    /**
     * Get the escaped version of the current code point.
     *
     * @return the escaped version of the current code point.
     */
    String getEscapeString()
    {
        if ( isSupplementary != 0 ){
            if ( useECMA6 != 0 ){
                return makeECMA6Escape(codePoint);
            }else{
                return makeEscape(chars);
            }
        }else if ( chars[0] < NUM_CONTROLS ){
            return controls[(int)chars[0]];
        }else if ( useSingleLetterEscapes != 0 ){
            switch ( chars[0] ){
                case '"': return DQ;
                case '/': return SL;
                case BACKSLASH: return BK;
                default: return makeEscape(chars[0]);
            }
        }else{
            return makeEscape(chars[0]);
        }
    }

    /**
     * Make a code unit escape.
     *
     * @param cp the char.
     * @return the escape.
     */
    private String makeEscape( int cp )
    {
        char[] esc = oneBuf;

        if ( esc == null ){
            esc = oneBuf = copyOf(INITIAL_ONEBUF);
        }

        esc[5] = HEX_DIGITS[cp & 0xF];
        esc[4] = HEX_DIGITS[(cp >> 4) & 0xF];
        esc[3] = HEX_DIGITS[(cp >> 8) & 0xF];
        esc[2] = HEX_DIGITS[cp >> 12];

        return new String(esc);
    }

    /**
     * Make a surrogate pair code unit escape.
     *
     * @param ch the char array.
     * @return the escape.
     */
    private String makeEscape( char[] ch )
    {
        char[] esc = twoBuf;

        if ( esc == null ){
            esc = twoBuf = copyOf(INITIAL_TWOBUF);
        }

        int cp = ch[1];
        esc[11] = HEX_DIGITS[cp & 0xF];
        esc[10] = HEX_DIGITS[(cp >> 4) & 0xF];
        esc[9] = HEX_DIGITS[(cp >> 8) & 0xF];
        esc[8] = HEX_DIGITS[cp >> 12];

        cp = ch[0];
        esc[5] = HEX_DIGITS[cp & 0xF];
        esc[4] = HEX_DIGITS[(cp >> 4) & 0xF];
        esc[3] = HEX_DIGITS[(cp >> 8) & 0xF];
        esc[2] = HEX_DIGITS[cp >> 12];

        return new String(esc);
    }

    /**
     * Make an ECMAScript 6 code point escape.
     *
     * @param codePoint The code point.
     * @return the escape
     */
    private String makeECMA6Escape( int codePoint )
    {
        char[] esc;
        int cp = codePoint;

        if ( (cp >> 20) == 0 ){
            esc = ecma6_5;
            if ( esc == null ){
                esc = ecma6_5 = copyOf(INITIAL_ECMA6_5);
            }
            esc[7] = HEX_DIGITS[cp & 0xF];
            esc[6] = HEX_DIGITS[(cp >> 4) & 0xF];
            esc[5] = HEX_DIGITS[(cp >> 8) & 0xF];
            esc[4] = HEX_DIGITS[(cp >> 12) & 0xF];
            esc[3] = HEX_DIGITS[cp >> 16];
        }else{
            esc = ecma6_6;
            if ( esc == null ){
                esc = ecma6_6 = copyOf(INITIAL_ECMA6_6);
            }
            esc[8] = HEX_DIGITS[cp & 0xF];
            esc[7] = HEX_DIGITS[(cp >> 4) & 0xF];
            esc[6] = HEX_DIGITS[(cp >> 8) & 0xF];
            esc[5] = HEX_DIGITS[(cp >> 12) & 0xF];
        }

        return new String(esc);
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
     * Check if the string contains any characters that need to be processed.
     * This searches this string from the end so that it can record the index of
     * the last character that needs to be processed. This allows the forward
     * looping code that runs after this to stop checking for code points that
     * need to be processed after it gets to the last code point that needs to be
     * processed.
     *
     * @return the index of the last code point that needs to be processed or -1 if there isn't one.
     */
    private int findLastProcess()
    {
        isUnmatchedSurrogate = 0;
        escChecker = getEscapeChecker();
        int needDefined = undefinedCodePointPolicy != JSONConfig.PASS ? 1 : 0;
        int needMatched = unmatchedSurrogatePolicy != JSONConfig.PASS ? 1 : 0;
        int i = len;
        while ( i > 0 ){
            --i;
            char ch1 = strValue.charAt(i);
            if ( JSONUtil.isSurrogate(ch1) ){
                isUnmatchedSurrogate = 1;
                if ( --i >= 0 ){
                    char ch0 = strValue.charAt(i);
                    if ( Character.isSurrogatePair(ch0, ch1) ){
                        isDefined = isDefined(Character.toCodePoint(ch0, ch1));
                        isUnmatchedSurrogate = 0;
                        if ( escChecker.needEscape() || (isDefined == 0 && needDefined != 0) ){
                            return i;
                        }
                    }
                }
                if ( isUnmatchedSurrogate != 0 ){
                    ++i;
                    isDefined = isDefined(ch1);
                    if ( escChecker.needEscape(ch1) || needMatched != 0 ){
                        return i;
                    }else{
                        isUnmatchedSurrogate = 0;
                    }
                }
            }else{
                isDefined = isDefined(ch1);
                if ( escChecker.needEscape(ch1) || (isDefined == 0 && needDefined != 0) ){
                    return i;
                }
            }
        }
        return -1;
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
                return new AsciiEvalEscapeChecker();
            }else if ( escapeSurrogates != 0 ){
                return new SurrogateEvalEscapeChecker();
            }else{
                return new EvalEscapeChecker();
            }
        }else if ( escapeNonAscii ){
            return new AsciiEscapeChecker();
        }else if ( escapeSurrogates != 0 ){
            return new SurrogateEscapeChecker();
        }else{
            return new BasicEscapeChecker();
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
        switch ( c ){
            case '\b':
            case '\t':
            case '\n':
            case '\r':
            case '\f':
            case '"':
            case '/':
            case BACKSLASH:
                return true;
            default:
                return false;
        }
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
     * Make a copy of a char array.
     *
     * @param src the source array.
     * @return a copy of the char array.
     */
    private static char[] copyOf( char[] src )
    {
        char[] dest = new char[src.length];
        System.arraycopy(src, 0, dest, 0, src.length);
        return dest;
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
            default:
                return false;
        }
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
            default:
                return false;
        }
    }

    /**
     * Shorthand for doing boolean isDefined as an int.
     *
     * @param ch the char to check
     * @return 1 if it's defined.  0 if not.
     */
    private static int isDefined( char ch )
    {
        return Character.isDefined(ch) ? 1 : 0;
    }

    /**
     * Shorthand for doing boolean isDefined as an int.
     *
     * @param cp the code point to check
     * @return 1 if it's defined.  0 if not.
     */
    private static int isDefined( int cp )
    {
        return Character.isDefined(cp) ? 1 : 0;
    }

    /**
     * Shorthand for doing boolean isSurrogate as an int.
     *
     * @param ch the char to check
     * @return 1 if it's a surrogate.  0 if not.
     */
    private static int isSurrogate( char ch )
    {
        return JSONUtil.isSurrogate(ch) ? 1 : 0;
    }

    /**
     * Get the high surrogate for the code point.
     *
     * @param cp the code point.
     * @return the high surrogate of the code point
     */
    static char highSurrogate( int cp )
    {
        int[] cps = { cp };
        String str = new String(cps,0,1);
        return str.charAt(0);
    }

    /**
     * Get the low surrogate for the code point.
     *
     * @param cp the code point.
     * @return the low surrogate of the code point
     */
    static char lowSurrogate( int cp )
    {
        int[] cps = { cp };
        String str = new String(cps,0,1);
        return str.length() > 0 ? str.charAt(1) : str.charAt(0);
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
        int lastBackSlash = strValue.lastIndexOf(BACKSLASH);
        if ( lastBackSlash < 0 ){
            return strValue;            // nothing to do.
        }

        Matcher jsEscMatcher = JAVASCRIPT_ESC_PAT.matcher(strValue);
        Matcher codeUnitMatcher = CODE_UNIT_PAT.matcher(strValue);
        Matcher codePointMatcher = CODE_POINT_PAT.matcher(strValue);
        final int jsLen = MAX_JS_ESC_LENGTH;
        final int unLen = CODE_UNIT_ESC_LENGTH;
        final int cpLen = MAX_CODE_POINT_ESC_LENGTH;

        StringBuilder buf = new StringBuilder();
        CodePointData cp = new CodePointData(strValue, cfg);
        while ( cp.nextReady() ){
            if ( cp.codePoint == BACKSLASH ){
                if ( gotMatch(jsEscMatcher, cp.index, cp.end(jsLen)) ){
                    String esc = jsEscMatcher.group(1);
                    buf.append(getEscapeChar(esc));
                    cp.nextIndex += esc.length() - 1;
                }else if ( gotMatch(codeUnitMatcher, cp.index, cp.end(unLen)) ){
                    handleCodeUnitEscape(cp, codeUnitMatcher, buf);
                }else if ( gotMatch(codePointMatcher, cp.index, cp.end(cpLen)) ){
                    handleCodePointEscape(cp, codePointMatcher, buf);
                }else{
                    // have '\' but nothing looks like a valid escape; just pass it through.
                    cp.appendChars(buf);
                }
                if ( cp.nextIndex > lastBackSlash ){
                    // don't need these anymore.
                    jsEscMatcher = null;
                    codeUnitMatcher = null;
                    codePointMatcher = null;
                }
            }else{
                cp.appendChars(buf);            // not an escape.
            }
        }
        return buf.toString();
    }

    /**
     * Handle unescaping a code unit escape.
     *
     * @param cp The CodePointData
     * @param codeUnitMatcher the codeUnitMatcher that has just matched a code unit.
     * @param buf the buffer.
     */
    private static void handleCodeUnitEscape( CodePointData cp, Matcher codeUnitMatcher, StringBuilder buf )
    {
        String esc = codeUnitMatcher.group(1);
        String hexStr = codeUnitMatcher.group(2);
        char ch = (char)Integer.parseInt(hexStr, 16);
        int addIt = 1;
        if ( ! Character.isDefined(ch) ){
            switch ( cp.undefinedCodePointPolicy ){
                case JSONConfig.REPLACE:
                    cp.replaceCodePoint();
                    ch = (char)cp.getCodePoint();
                    break;
                case JSONConfig.DISCARD:
                    addIt = 0;
                    break;
                case JSONConfig.EXCEPTION:
                    throw new UndefinedCodePointException(cp.cfg, cp.strValue, cp.getIndex(), ch);
            }
        }
        if ( addIt != 0 ){
            buf.append(ch);
        }
        cp.nextIndex += esc.length() - 1;
    }

    /**
     * Handle unescaping a code point escape.
     *
     * @param cp The CodePointData
     * @param codePointMatcher the codePointMatcher that has just matched a code point.
     * @param buf the buffer.
     */
    private static void handleCodePointEscape( CodePointData cp, Matcher codePointMatcher, StringBuilder buf )
    {
        String hexStr = codePointMatcher.group(2);
        int codePoint = Integer.parseInt(hexStr, 16);
        int addIt = 1;
        if ( ! Character.isDefined(codePoint) ){
            switch ( cp.undefinedCodePointPolicy ){
                case JSONConfig.REPLACE:
                    cp.replaceCodePoint();
                    codePoint = cp.getCodePoint();
                    break;
                case JSONConfig.DISCARD:
                    addIt = 0;
                    break;
                case JSONConfig.EXCEPTION:
                    throw new UndefinedCodePointException(cp.cfg, cp.strValue, cp.getIndex(), hexStr);
                default:
                    if ( codePoint > Character.MAX_CODE_POINT ){
                        // no way to properly encode this value.
                        cp.replaceCodePoint();
                        codePoint = cp.getCodePoint();
                    }
                    break;
            }
        }
        if ( addIt != 0 ){
            buf.appendCodePoint(codePoint);
        }
        cp.nextIndex += codePointMatcher.group(1).length() - 1;
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
        private int useECMA5;

        /**
         * Make an EscapeHandler.
         *
         * @param cfg the config object.
         * @param lastBackSlash the index of the last backslash in the string.
         */
        private EscapeHandler( JSONConfig cfg, int lastBackSlash )
        {
            this.lastBackSlash = lastBackSlash;

            // set up the pass through matcher.
            Pattern escapePassThroughPat = getEscapePassThroughPattern(cfg, useSingleLetterEscapes != 0);
            passThroughMatcher = escapePassThroughPat.matcher(strValue);
            passThroughRegionLength = getEscapePassThroughRegionLength(cfg);

            // set up the javascript character escape matcher.
            jsEscMatcher = JAVASCRIPT_ESC_PAT.matcher(strValue);

            useECMA5 = useECMA6 == 0 ? 1 : 0;
            if ( useECMA5 != 0 ){
                // set up the ECMAScript 6 code point matcher,
                // because those will not be matched by the pass through.
                codePointMatcher = CODE_POINT_PAT.matcher(strValue);
            }
        }

        /**
         * Do the matching for escapes. If an escape is generated or passed
         * through, it is returned. If not, then the chars array will hold a
         * newly created code point to replace the escape found here and it will
         * not need to be escaped.
         *
         * @return the escape if any, otherwise null and current code point data will be modified.
         */
        private String checkInlineEscape()
        {
            String esc = null;
            String hexStr = null;
            int newChars = 0;

            // check for escapes.
            if ( gotMatch(passThroughMatcher, index, end(passThroughRegionLength)) ){
                // pass it through unchanged.
                esc = passThroughMatcher.group(1);
                nextIndex += esc.length() - 1;    // skip matched escape
            }else if ( gotMatch(jsEscMatcher, index, end(MAX_JS_ESC_LENGTH)) ){
                // Any Javascript escapes that didn't make it through the pass through are not allowed.
                newChars = 1;
                String jsEsc = jsEscMatcher.group(1);
                codePoint = chars[0] = getEscapeChar(jsEsc);
                nextIndex += jsEsc.length() - 1;    // skip matched escape
            }else if ( useECMA5 != 0 && gotMatch(codePointMatcher, index, end(MAX_CODE_POINT_ESC_LENGTH)) ){
                newChars = 1;
                hexStr = handleCodePointEscape();
            }else{
                esc = getEscapeString();     // backslash that doesn't fit a known escape pattern.
            }

            if ( newChars != 0 ){
                if ( isDefined == 0 ){
                    esc = handleUndefined(hexStr);
                }
                if ( esc == null && needEscape() ){
                    esc = getEscapeString();         // new code point that needs escaping
                }
            }

            if ( nextIndex > lastBackSlash ){
                // this handler is no longer needed.
                processInlineEscapes = 0;
                handler = null;
            }

            return esc;
        }

        /**
         * Handle a code point escape. Only get here if it wasn't passed through
         * which means that useECMA6 is false. Convert it to an inline codepoint.
         *
         * @return the hex string for the code point escape.
         */
        private String handleCodePointEscape()
        {
            String hexStr = codePointMatcher.group(2);
            codePoint = Integer.parseInt(hexStr, 16);
            if ( codePoint < Character.MIN_SUPPLEMENTARY_CODE_POINT ){
                chars[0] = (char)codePoint;
                isDefined = isDefined(chars[0]);
            }else if ( codePoint > Character.MAX_CODE_POINT ){
                isDefined = 0;
            }else{
                isSupplementary = 1;
                charCount = 2;
                chars[0] = highSurrogate(codePoint);
                chars[1] = lowSurrogate(codePoint);
                isDefined = isDefined(codePoint);
            }
            nextIndex += codePointMatcher.group(1).length() - 1;    // skip matched escape

            return hexStr;
        }

        /**
         * Handle an undefined code point.
         *
         * @param hexStr the hex characters for the code point.
         * @return an escape
         */
        private String handleUndefined( String hexStr )
        {
            String esc = null;

            switch ( undefinedCodePointPolicy ){
                case JSONConfig.REPLACE:
                    replaceCodePoint();
                    break;
                case JSONConfig.DISCARD:
                    esc = "";
                    break;
                case JSONConfig.EXCEPTION:
                    throw new UndefinedCodePointException(cfg, strValue, index, hexStr);
                default:
                    if ( codePoint > Character.MAX_CODE_POINT ){
                        replaceCodePoint();
                    }
                    break;
            }

            return esc;
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
         * @return true if the char needs to be escaped.
         */
        boolean needEscapeImpl( char ch );

        /**
         * Does the actual checking.
         *
         * @return true if the codePoint needs to be escaped.
         */
        boolean needEscapeImpl();

        /**
         * Return true if the given char needs to be escaped.
         *
         * @param ch the char.
         * @return true if the given char needs to be escaped.
         */
        boolean needEscape( char ch );

        /**
         * Return true if the current code point needs to be escaped.
         *
         * @return true if the current code point needs to be escaped.
         */
        boolean needEscape();
    }

    /**
     * Abstract class for escape checkers.
     */
    private abstract class AbstractEscapeChecker implements EscapeChecker
    {
        public boolean needEscape( char ch )
        {
            if ( isDefined == 0 ){
                return undefinedCodePointPolicy == JSONConfig.ESCAPE;
            }else if ( isUnmatchedSurrogate != 0 ){
                return unmatchedSurrogatePolicy == JSONConfig.ESCAPE;
            }else{
                return needEscapeImpl(ch);
            }
        }

        public boolean needEscape()
        {
            if ( isDefined == 0 ){
                return undefinedCodePointPolicy == JSONConfig.ESCAPE;
            }else{
                return needEscapeImpl();
            }
        }
    }

    /**
     * Checks for controls and other escapes without eval protection.
     */
    private class BasicEscapeChecker extends AbstractEscapeChecker
    {
        public boolean needEscapeImpl( char ch )
        {
            return isControl(ch) || isEsc(ch);
        }

        public boolean needEscapeImpl()
        {
            return false;
        }
    }

    /**
     * Checks for controls and other escapes with eval protection.
     */
    private class EvalEscapeChecker extends AbstractEscapeChecker
    {
        public boolean needEscapeImpl( char ch )
        {
            return isControl(ch) || isEvalEsc(ch);
        }

        public boolean needEscapeImpl()
        {
            return false;
        }
    }

    /**
     * Checks for controls, non-ASCII and other escapes without eval protection.
     */
    private class AsciiEscapeChecker extends AbstractEscapeChecker
    {
        public boolean needEscapeImpl( char ch )
        {
            return isNotAscii(ch) || isEsc(ch);
        }

        public boolean needEscapeImpl()
        {
            return true;
        }
    }

    /**
     * Checks for controls, non-ASCII and other escapes with eval protection.
     */
    private class AsciiEvalEscapeChecker extends AbstractEscapeChecker
    {
        public boolean needEscapeImpl( char ch )
        {
            return isNotAscii(ch) || isEvalEsc(ch);
        }

        public boolean needEscapeImpl()
        {
            return true;
        }
    }

    /**
     * Checks for controls, other escapes without eval protection and surrogates.
     */
    private class SurrogateEscapeChecker extends AbstractEscapeChecker
    {
        public boolean needEscapeImpl( char ch )
        {
            return isControl(ch) || isEsc(ch) || JSONUtil.isSurrogate(ch);
        }

        public boolean needEscapeImpl()
        {
            return true;
        }
    }

    /**
     * Checks for controls, other escapes with eval protection and surrogates.
     */
    private class SurrogateEvalEscapeChecker extends AbstractEscapeChecker
    {
        public boolean needEscapeImpl( char ch )
        {
            return isControl(ch) || isEvalEsc(ch) || JSONUtil.isSurrogate(ch);
        }

        public boolean needEscapeImpl()
        {
            return true;
        }
    }
}
