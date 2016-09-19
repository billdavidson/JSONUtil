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
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
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
     * Various escape checkers.
     */
    private static final EscapeChecker ASCII_EVAL_EC = new AsciiEvalEscapeChecker();
    private static final EscapeChecker SURROGATE_EVAL_EC = new SurrogateEvalEscapeChecker();
    private static final EscapeChecker EVAL_EC = new EvalEscapeChecker();
    private static final EscapeChecker ASCII_EC = new AsciiEscapeChecker();
    private static final EscapeChecker SURROGATE_EC = new SurrogateEscapeChecker();
    private static final EscapeChecker BASIC_EC = new BasicEscapeChecker();

    /**
     * Maximum initial queue size.
     */
    private static final int MAX_INITIAL_QUEUE_SIZE = 64;

    /**
     * Initial queue size for keeping track of escapes.
     */
    private static volatile int initialQueueSize = 8;

    /*
     * Arrays of escapes for control characters.
     */
    private static final int NUM_CONTROLS = 32;
    private static final String[] UNICODE_ESC = new String[NUM_CONTROLS];
    private static final String[] SINGLE_ESC = new String[NUM_CONTROLS];
    private static final String[] ECMA6_ESC = new String[NUM_CONTROLS];
    private static final String[] ECMA6_SINGLE_ESC = new String[NUM_CONTROLS];

    /**
     * Initialize JSON_ESC_MAP, JAVASCRIPT_ESC_MAP and EVAL_ESC_SET.
     */
    static {
        JSON_ESC_MAP = new HashMap<>(8);
        JSON_ESC_MAP.put('\b', BS);
        JSON_ESC_MAP.put('\t', TAB);
        JSON_ESC_MAP.put('\n', NL);
        JSON_ESC_MAP.put('\f', FF);
        JSON_ESC_MAP.put('\r', CR);
        JSON_ESC_MAP.put('"', DQ);
        JSON_ESC_MAP.put('/', SL);
        JSON_ESC_MAP.put(BACKSLASH, BK);

        JAVASCRIPT_ESC_MAP = new HashMap<>(10);
        for ( Entry<Character,String> entry : JSON_ESC_MAP.entrySet() ){
            JAVASCRIPT_ESC_MAP.put(entry.getValue(), entry.getKey());
        }
        // these two are valid in Javascript but not JSON.
        JAVASCRIPT_ESC_MAP.put("\\v", (char)0xB);
        JAVASCRIPT_ESC_MAP.put("\\'", '\'');

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
            String esc6 = i < 0x10 ? String.format("\\u{%X}", i) : esc;
            UNICODE_ESC[i] = esc;
            SINGLE_ESC[i] = single != null ? single : esc;
            ECMA6_ESC[i] = esc6;
            ECMA6_SINGLE_ESC[i] = single != null ? single : esc6;
        }
    }

    // private data and flags.
    private String strValue;
    private EscapeHandler handler;
    private EscapeChecker escChecker;
    private String[] controls = null;
    private int nextIndex;
    private int len;
    private int lastEscIndex;
    private int beginIndex;
    private int endIndex;
    private int handleEscaping;
    private int processInlineEscapes;
    private int useECMA6;
    private int useSingleLetterEscapes;
    private int escapeSurrogates;
    private int noEscapes;
    private int isSurrogatePair;
    private int isMalformed;
    private boolean supportEval;
    private boolean escapeNonAscii;
    private boolean manyEscapes;

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
     * @param processInlineEscapes If 1, then process inline escapes.
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
        lastEscIndex = findLastEscape();
        noEscapes = lastEscIndex < 0 ? 1 : 0;
        handleEscaping = noEscapes != 0 ? 0 : 1;

        if ( useSingleLetterEscapes ){
            controls = useECMA6 != 0 ? ECMA6_SINGLE_ESC : SINGLE_ESC;
        }

        if ( handleEscaping != 0 ){
            if ( this.processInlineEscapes != 0 ){
                int lastBackSlash = strValue.lastIndexOf(BACKSLASH);
                this.processInlineEscapes = lastBackSlash >= 0 ? 1 : 0;
                if ( this.processInlineEscapes != 0){
                    handler = new EscapeHandler(cfg, lastBackSlash);
                }
            }
        }else{
            esc = null;
        }
    }

    /**
     * Make a CodePointData that handles escapes appropriately.
     *
     * @param strValue The string that will be analyzed.
     * @param cfg The config object.
     * @param processInlineEscapes If 1, then process inline escapes.
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
        manyEscapes = cfg.isManyEscapes();

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
        chars = new char[2];
        len = strValue.length();
        index = 0;
        nextIndex = 0;
        charCount = 0;
        useECMA6 = cfg.isUseECMA6() ? 1 : 0;

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
    String getEsc()
    {
        return esc;
    }

    /**
     * If 1, then there are no escapes in this string.
     *
     * @return If 1, then there are no escapes in this string.
     */
    boolean isNoEscapes()
    {
        return noEscapes != 0 ? true : false;
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
     * Write a string to the given writer.
     *
     * @param json The writer.
     * @throws IOException if there's an I/O error.
     */
    void writeString( Writer json ) throws IOException
    {
        if ( manyEscapes ){
            writeStringManyEscapes(json);
        }else{
            writeStringFewEscapes(json);
        }
    }

    /**
     * Write the current string to the given Writer using escaping as needed or
     * requested by the configuration options. If there are no code points that
     * need to be escaped then this method just writes the entire string. If
     * there are ode points that need to be escaped, then substrings which don't
     * contain any characters to be escaped are written with a single write for
     * better efficiency.
     * <p>
     * This version performs better when there are lots of escapes than
     * {@link #writeStringFewEscapes(Writer)} but worse when there are few or no
     * escapes.
     *
     * @param json The writer.
     * @throws IOException if there's an I/O error.
     */
    private void writeStringManyEscapes( Writer json ) throws IOException
    {
        lastEscIndex = findLastEscape();

        if ( lastEscIndex < 0 ){
            json.write(strValue);
            return;
        }

        // escaping necessary.
        while ( nextIndex < len ){
            nextCodePoint();

            if ( isSurrogatePair != 0 ){
                if ( escapeSurrogates != 0 || ! Character.isDefined(codePoint) ){
                    writeEscape(json, getEscapeString());
                    continue;
                }
                // else OK
            }else if ( isMalformed != 0 ){
                // bad surrogate pair -- just write the single bad surrogate.
                writeEscape(json, getEscapeString());
                continue;
            }else{
                if ( processInlineEscapes != 0 && chars[0] == BACKSLASH ){
                    esc = null;
                    int newChars = handler.doMatches();
                    if ( esc != null ){
                        writeEscape(json, esc);
                        continue;
                    }else if ( newChars != 0 ){
                        flushCurrentSubstring(json);
                        json.write(chars, 0, charCount);
                        continue;
                    }
                }

                if ( escChecker.needEscape(chars[0]) != 0 ){
                    if ( chars[0] < NUM_CONTROLS ){
                        writeEscape(json, controls[(int)chars[0]]);
                    }else{
                        switch ( chars[0] ){
                            case '"': writeEscape(json, DQ); break;
                            case '/': writeEscape(json, SL); break;
                            case BACKSLASH: writeEscape(json, BK); break;
                            default: writeEscape(json, getEscapeString()); break;
                        }
                    }
                    continue;
                }
            }

            // no escaping needed for this code point.
            if ( beginIndex < 0 ){
                beginIndex = index; // start a new sub string.
            }
            endIndex = nextIndex;
        }

        flushCurrentSubstring(json);
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
        if ( index == lastEscIndex ){
            // just did last escape
            beginIndex = nextIndex;
            endIndex = nextIndex = len;
        }
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
     * Write the current string to the given Writer using escaping as needed or
     * requested by the configuration options. If there are no code points that
     * need to be escaped then this method just writes the entire string. If
     * there are ode points that need to be escaped, then substrings which don't
     * contain any characters to be escaped are written with a single write for
     * better efficiency.
     * <p>
     * This version performs better when there are few or no escapes than
     * {@link #writeStringManyEscapes(Writer)} but worse when there are many
     * escapes.
     *
     * @param json The writer.
     * @throws IOException if there's an I/O error.
     */
    private void writeStringFewEscapes( Writer json ) throws IOException
    {
        Queue<Integer> escapes = findEscapes();

        if ( escapes == null ){
            json.write(strValue);
            return;
        }

        // get the index of the first escape.
        int nextEscape = escapes.poll();
        int escapesRemaining = escapes.size();

        // escaping necessary.
        while ( nextIndex < len ){
            if ( nextIndex < nextEscape ){
                // skip to the next escape
                json.write(strValue, nextIndex, nextEscape - nextIndex);
                nextIndex = nextEscape;
            }else{
                // escape this one.
                nextCodePoint();

                if ( isSurrogatePair != 0 || isMalformed != 0 ){
                    json.write(getEscapeString());
                }else{
                    int doEscape = 1;
                    if ( processInlineEscapes != 0 && chars[0] == BACKSLASH ){
                        esc = null;
                        int newChars = handler.doMatches();
                        if ( esc != null ){
                            json.write(esc);
                            doEscape = 0;
                        }else if ( newChars != 0 ){
                            json.write(chars, 0, charCount);
                            doEscape = 0;
                        }
                    }
                    if ( doEscape != 0 ){
                        if ( chars[0] < NUM_CONTROLS ){
                            json.write(controls[(int)chars[0]]);
                        }else{
                            switch ( chars[0] ){
                                case '"': json.write(DQ); break;
                                case '/': json.write(SL); break;
                                case BACKSLASH: json.write(BK); break;
                                default: json.write(getEscapeString()); break;
                            }
                        }
                    }
                }

                if ( escapesRemaining-- > 0 ){
                    // get the index of the next escape
                    nextEscape = escapes.poll();
                }else{
                    // no more escapes
                    if ( nextIndex < len ){
                        // write the rest of the string.
                        json.write(strValue, nextIndex, len - nextIndex);
                        nextIndex = len;
                    }
                }
            }
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

            if ( handleEscaping != 0 ){
                handleEscaping();
            }

            return true;
        }else{
            return false;
        }
    }

    /**
     * Set up the next code point.
     */
    private void nextCodePoint()
    {
        index = nextIndex;

        charCount = 1;
        codePoint = chars[0] = strValue.charAt(index);
        isMalformed = isSurrogatePair = Character.isSurrogate(chars[0]) ? 1 : 0;
        if ( isSurrogatePair != 0 ){
            isSurrogatePair = ++nextIndex < len ? 1 : 0;
            if ( isSurrogatePair != 0 ){
                chars[1] = strValue.charAt(nextIndex);
                isSurrogatePair = Character.isSurrogatePair(chars[0], chars[1]) ? 1 : 0;
                if ( isSurrogatePair != 0 ){
                    isMalformed = 0;
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
    }

    /**
     * Handle escapes as appropriate.
     */
    private void handleEscaping()
    {
        esc = null;

        if ( index > lastEscIndex ){
            // past last escape -- disable escape checking.
            noEscapes = 1;
            handleEscaping = 0;
            return;
        }

        if ( processInlineEscapes != 0 && chars[0] == BACKSLASH ){
            handler.doMatches();            // check for escapes.
        }

        if ( useSingleLetterEscapes != 0 && esc == null && chars[0] <= MAX_SINGLE_ESC_CHAR ){
            switch ( chars[0] ){
                case '\b': esc = BS; break;
                case '\t': esc = TAB; break;
                case '\n': esc = NL; break;
                case '\f': esc = FF; break;
                case '\r': esc = CR; break;
                case '"': esc = DQ; break;
                case '/': esc = SL; break;
                case BACKSLASH: esc = BK; break;
            }
        }

        // any other escapes requested or required.
        if ( esc == null ){
            if ( isMalformed != 0 ){
                esc = getEscapeString();
            }else if ( isSurrogatePair != 0 ){
                if ( escChecker.needEscape(codePoint) != 0 ){
                    esc = getEscapeString();
                }
            }else if ( escChecker.needEscape(chars[0]) != 0 ){
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
        if ( chars[0] < NUM_CONTROLS ){
            return controls[(int)chars[0]];
        }else if ( useECMA6 != 0 && codePoint >= Character.MIN_SUPPLEMENTARY_CODE_POINT ){
            // Use ECMAScript 6 code point escape.
            // only very low or very high code points see an advantage.
            return String.format("\\u{%X}", codePoint);
        }else{
            // Use normal escape.
            if ( isSurrogatePair != 0 ){
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
     * @return the index of the last escape or -1 if there isn't one.
     */
    private int findLastEscape()
    {
        escChecker = getEscapeChecker();
        int i = len;
        while ( i > 0 ){
            --i;
            char ch1 = strValue.charAt(i);
            if ( Character.isSurrogate(ch1) ){
                int malformed = 1;
                if ( --i >= 0 ){
                    char ch0 = strValue.charAt(i);
                    if ( Character.isSurrogatePair(ch0, ch1) ){
                        malformed = 0;
                        if ( escChecker.needEscape(Character.toCodePoint(ch0, ch1)) != 0 ){
                            return ++i;
                        }
                    }
                }
                if ( malformed != 0 ){
                    return ++i;
                }
            }else if ( escChecker.needEscape(ch1) != 0 ){
                return i;
            }
        }
        return -1;
    }

    /**
     * Find a collection of escapes for this string.
     *
     * @return a queue of escape indexes or null if there are none.
     */
    private Queue<Integer> findEscapes()
    {
        Queue<Integer> escapes = null;
        escChecker = getEscapeChecker();
        int i = 0;
        int n = 0;
        int iqs = 0;
        // loop until the first escape is encountered.
        while ( n < len ){
            i = n;
            char ch0 = strValue.charAt(i);
            if ( Character.isSurrogate(ch0) ){
                int malformed = 1;
                if ( ++n < len ){
                    char ch1 = strValue.charAt(n);
                    if ( Character.isSurrogatePair(ch0, ch1) ){
                        malformed = 0;
                        if ( escChecker.needEscape(Character.toCodePoint(ch0, ch1)) != 0 ){
                            escapes = newEscapes(i, iqs = getInitialQueueSize());
                            ++n;
                            break;
                        }
                    }
                }
                if ( malformed != 0 ){
                    escapes = newEscapes(i, iqs = getInitialQueueSize());
                    break;
                }
            }else if ( escChecker.needEscape(ch0) != 0 ){
                escapes = newEscapes(i, iqs = getInitialQueueSize());
                ++n;
                break;
            }
            ++n;
        }
        if ( n < len ){
            while ( n < len ){
                i = n;
                char ch0 = strValue.charAt(i);
                if ( Character.isSurrogate(ch0) ){
                    int malformed = 1;
                    if ( ++n < len ){
                        char ch1 = strValue.charAt(n);
                        if ( Character.isSurrogatePair(ch0, ch1) ){
                            malformed = 0;
                            if ( escChecker.needEscape(Character.toCodePoint(ch0, ch1)) != 0 ){
                                escapes.add(i);
                            }
                        }
                    }
                    if ( malformed != 0 ){
                        --n;
                        escapes.add(i);
                    }
                }else if ( escChecker.needEscape(ch0) != 0 ){
                    escapes.add(i);
                }
                ++n;
            }
            int size = escapes.size();
            if ( size > iqs && iqs < MAX_INITIAL_QUEUE_SIZE ){
                setInitialQueueSize(Math.min(size, MAX_INITIAL_QUEUE_SIZE));
            }
        }
        return escapes;
    }

    /**
     * Make a new escapes queue and add the first element to it.
     *
     * @param i the first escape index.
     * @param iqs the initial queue size.
     * @return the escapes queue.
     */
    private Queue<Integer> newEscapes( int i, int iqs )
    {
        Queue<Integer> escapes = new ArrayDeque<>(iqs);
        escapes.add(i);
        return escapes;
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
            }else if ( escapeSurrogates != 0 ){
                return SURROGATE_EVAL_EC;
            }else{
                return EVAL_EC;
            }
        }else if ( escapeNonAscii ){
            return ASCII_EC;
        }else if ( escapeSurrogates != 0 ){
            return SURROGATE_EC;
        }else{
            return BASIC_EC;
        }
    }

    /**
     * Get the initial queue size.
     *
     * @return the initial queue size.
     */
    private static synchronized int getInitialQueueSize()
    {
        return initialQueueSize;
    }

    /**
     * Set the initial queue size.
     *
     * @param iqs the new initial queue size
     */
    private static synchronized void setInitialQueueSize( int iqs )
    {
        initialQueueSize = iqs;
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
     * Return 1 if there's a JSON single character escape for this char.
     *
     * @param c The char to check.
     * @return 1 if there's a JSON single character escape for this char.
     */
    static boolean haveJsonEsc( char c )
    {
        return JSON_ESC_MAP.containsKey(c);
    }

    /**
     * Get the escape pass through pattern for identifiers or strings.
     *
     * @param cfg A configuration object to determine which pattern to use.
     * @param useSingleLetterEscapes If 1, then use a pattern that allows JSON single letter escapes.
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
     * @return 1 if there is a match at the start of the region.
     */
    static boolean gotMatch( Matcher matcher, int start, int end )
    {
        matcher.region(start, end);
        return matcher.find() && matcher.start() == start;
    }

    /**
     * Return 1 if the character is one of the non-control characters
     * that has to be escaped or something that breaks eval.
     *
     * @param ch the char to check.
     * @return 1 if the char needs to be escaped based upon this test.
     */
    private static int isControl( char ch )
    {
        return ch < ' ' ? 1 : 0;
    }

    /**
     * Return 1 if the character is a control character or non-ASCII
     *
     * @param ch the char to check.
     * @return 1 if the char needs to be escaped based upon this test.
     */
    private static int isNotAscii( char ch )
    {
        return ch < ' ' || ch > MAX_ASCII ? 1 : 0;
    }

    /**
     * Return 1 if the character is one of the non-control characters
     * that has to be escaped.
     *
     * @param ch the char to check.
     * @return 1 if the char needs to be escaped based upon this test.
     */
    private static int isEsc( char ch )
    {
        switch ( ch ){
            case '"':
            case '/':
            case BACKSLASH:
                return 1;
        }
        return 0;
    }

    /**
     * Return 1 if the character is one of the non-control characters
     * that has to be escaped or something that breaks eval.
     *
     * @param ch the char to check.
     * @return 1 if the char needs to be escaped based upon this test.
     */
    private static int isEvalEsc( char ch )
    {
        switch ( ch ){
            case '"':
            case '/':
            case BACKSLASH:
            case LINE_SEPARATOR:
            case PARAGRAPH_SEPARATOR:
                return 1;
        }
        return 0;
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
         * @param passThroughOnly if 1, only do pass throughs.
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
         * Do the matching for escapes.
         */
        private int doMatches()
        {
            int newChars = 0;

            // check for escapes.
            if ( gotMatch(passThroughMatcher, index, end(passThroughRegionLength)) ){
                // pass it through unchanged.
                esc = passThroughMatcher.group(1);
                nextIndex += esc.length();
            }else if ( gotMatch(jsEscMatcher, index, end(MAX_JS_ESC_LENGTH)) ){
                // Any Javascript escapes that didn't make it through the pass through are not allowed.
                String jsEsc = jsEscMatcher.group(1);
                codePoint = chars[0] = getEscapeChar(jsEsc);
                newChars = 1;
                nextIndex += jsEsc.length();
            }else if ( useECMA5 != 0 && gotMatch(codePointMatcher, index, end(MAX_CODE_POINT_ESC_LENGTH)) ){
                /*
                 * Only get here if it wasn't passed through => useECMA6 is
                 * 0.  Convert it to an inline codepoint.  Maybe something
                 * later will escape it legally.
                 */
                codePoint = Integer.parseInt(codePointMatcher.group(2),16);
                newChars = 1;
                if ( codePoint < Character.MIN_SUPPLEMENTARY_CODE_POINT ){
                    chars[0] = (char)codePoint;
                }else{
                    isSurrogatePair = 1;
                    charCount = 2;
                    chars[0] = Character.highSurrogate(codePoint);
                    chars[1] = Character.lowSurrogate(codePoint);
                }
                nextIndex += codePointMatcher.group(1).length();
            }

            if ( newChars != 0 ){
                if ( isSurrogatePair != 0 ){
                    if ( escChecker.needEscape(codePoint) != 0 ){
                        esc = getEscapeString();
                    }
                }else if ( escChecker.needEscape(chars[0]) != 0 ){
                    if ( chars[0] < NUM_CONTROLS ){
                        esc = controls[(int)chars[0]];
                    }else if ( useSingleLetterEscapes != 0 ){
                        switch ( chars[0] ){
                            case '"': esc = DQ; break;
                            case '/': esc = SL; break;
                            case BACKSLASH: esc = BK; break;
                            default: esc = getEscapeString();
                        }
                    }else{
                        esc = getEscapeString();
                    }
                }
            }

            if ( nextIndex > lastBackSlash ){
                // this handler is no longer needed.
                processInlineEscapes = 0;
                handler = null;
            }

            return newChars;
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
         * @return 1 if the char needs to be escaped.
         */
        int needEscapeImpl( char ch );

        /**
         * Does the actual checking.
         *
         * @return 1 if the codePoint needs to be escaped.
         */
        int needEscapeImpl();

        /**
         * Return 1 if the given char needs to be escaped.
         *
         * @param ch the char.
         * @return 1 if the given char needs to be escaped.
         */
        int needEscape( char ch );

        /**
         * Return 1 if the given code point needs to be escaped.
         *
         * @param cp the code point.
         * @return 1 if the given code point needs to be escaped.
         */
        int needEscape( int cp );
    }

    /**
     * Abstract class for escape checkers.
     */
    private static abstract class AbstractEscapeChecker implements EscapeChecker
    {
        public int needEscape( char ch )
        {
            if ( needEscapeImpl(ch) != 0 ){
                return 1;
            }else if ( Character.isDefined(ch) ){
                return 0;
            }else{
                return 1;
            }
        }

        public int needEscape( int cp )
        {
            if ( needEscapeImpl() != 0 ){
                return 1;
            }else if ( Character.isDefined(cp) ){
                return 0;
            }else{
                return 1;
            }
        }
    }

    /**
     * Checks for controls and other escapes without eval protection.
     */
    private static class BasicEscapeChecker extends AbstractEscapeChecker
    {
        public int needEscapeImpl( char ch )
        {
            if ( isControl(ch) != 0 ){
                return 1;
            }else if ( isEsc(ch) != 0 ){
                return 1;
            }else{
                return 0;
            }
        }

        public int needEscapeImpl()
        {
            return 0;
        }
    }

    /**
     * Checks for controls and other escapes with eval protection.
     */
    private static class EvalEscapeChecker extends AbstractEscapeChecker
    {
        public int needEscapeImpl( char ch )
        {
            if ( isControl(ch) != 0 ){
                return 1;
            }else if ( isEvalEsc(ch) != 0 ){
                return 1;
            }else{
                return 0;
            }
        }

        public int needEscapeImpl()
        {
            return 0;
        }
    }

    /**
     * Checks for controls, non-ASCII and other escapes without eval protection.
     */
    private static class AsciiEscapeChecker extends AbstractEscapeChecker
    {
        public int needEscapeImpl( char ch )
        {
            if ( isNotAscii(ch) != 0 ){
                return 1;
            }else if ( isEsc(ch) != 0 ){
                return 1;
            }else{
                return 0;
            }
        }

        public int needEscapeImpl()
        {
            return 1;
        }
    }

    /**
     * Checks for controls, non-ASCII and other escapes with eval protection.
     */
    private static class AsciiEvalEscapeChecker extends AbstractEscapeChecker
    {
        public int needEscapeImpl( char ch )
        {
            if ( isNotAscii(ch) != 0 ){
                return 1;
            }else if ( isEvalEsc(ch) != 0 ){
                return 1;
            }else{
                return 0;
            }
        }

        public int needEscapeImpl()
        {
            return 1;
        }
    }

    /**
     * Checks for controls, other escapes without eval protection and surrogates.
     */
    private static class SurrogateEscapeChecker extends AbstractEscapeChecker
    {
        public int needEscapeImpl( char ch )
        {
            if ( isControl(ch) != 0 ){
                return 1;
            }else if ( isEsc(ch) != 0 ){
                return 1;
            }else if ( Character.isSurrogate(ch) ){
                return 1;
            }else{
                return 0;
            }
        }

        public int needEscapeImpl()
        {
            return 1;
        }
    }

    /**
     * Checks for controls, other escapes with eval protection and surrogates.
     */
    private static class SurrogateEvalEscapeChecker extends AbstractEscapeChecker
    {
        public int needEscapeImpl( char ch )
        {
            if ( isControl(ch) != 0 ){
                return 1;
            }else if ( isEvalEsc(ch) != 0 ){
                return 1;
            }else if ( Character.isSurrogate(ch) ){
                return 1;
            }else{
                return 0;
            }
        }

        public int needEscapeImpl()
        {
            return 1;
        }
    }
}
