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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a JSON parser. It accepts a fairly loose version of JSON. Essentially
 * it tries to allow anything Javascript eval() allows (within reason) so it
 * lets you use single quotes instead of double quotes if you want and all
 * versions of Javascript numbers are allowed. Unquoted identifiers are also
 * permitted. Escapes in strings are converted to their proper characters and
 * all Javascript escapes are permitted. Identifiers which contain code points
 * which are permitted by the JSON standard but not by the ECMAScript standard
 * must be quoted.
 * <p>
 * Javascript objects are converted to {@link LinkedHashMap}s with the
 * identifiers being the keys.
 * <p>
 * Javascript arrays are converted to {@link ArrayList}s.
 * <p>
 * Literal null is just a null value and boolean values are converted to
 * {@link Boolean}s.
 * <p>
 * Floating point numbers are converted to {@link Double} and integers are
 * converted to {@link Long}.
 * <p>
 * If the {@link JSONConfig#isEncodeDatesAsObjects()} or
 * {@link JSONConfig#isEncodeDatesAsStrings()} returns true, then strings that
 * look like dates will be converted to {@link Date} objects. By default,
 * parsing formats support ISO 8601 extended format that include data down to
 * seconds. Fractions of seconds and time zone offsets are optional. Other
 * formats can be added with calls to
 * {@link JSONConfig#addDateParseFormat(DateFormat)} or its variants and passing
 * the config object to the parser. Custom formats that you add will be tried
 * before the default ISO 8601 formats.
 * <p>
 * Calls to the new Date(String) constructor from Javascript are converted to
 * {@link Date}s.
 * <p>
 * JSON input can be fed to this class either as a {@link String} or as a
 * {@link Reader}, which may be useful and save memory when reading from files
 * or other input sources. If you wish to use an {@link InputStream} instead
 * then you should wrap it in an {@link InputStreamReader} which is a
 * {@link Reader} that will convert the bytes from the {@link InputStream} from
 * bytes to chars using the appropriate character set.
 *
 * @author Bill Davidson
 * @since 1.2
 */
public class JSONParser
{
    /**
     * Recognize literals
     */
    private static final Pattern LITERAL_PAT = Pattern.compile("(null|true|false)\\b");

    /**
     * Recognize octal
     */
    private static final Pattern OCTAL_PAT = Pattern.compile("0[0-7]*\\b");

    /**
     * Recognize unquoted id's
     */
    private static final Pattern UNQUOTED_ID_PAT =
            Pattern.compile("((?:[_\\$\\p{L}\\p{Nl}]|\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})" +
                             "(?:[_\\$\\p{L}\\p{Nl}\\p{Nd}\\p{Mn}\\p{Mc}\\p{Pc}\\u200C\\u200D]|\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})*)\\b");

    /**
     * Recognize Javascript floating point.
     */
    private static final Pattern JAVASCRIPT_FLOATING_POINT_PAT =
            Pattern.compile("((?:[-+]?(?:(?:\\d+\\.\\d+|\\.\\d+)(?:[eE][-+]?\\d+)?|Infinity))|NaN)([,\\s}\\]]|$)");

    /**
     * Recognize Javascript integers.
     */
    private static final Pattern JAVASCRIPT_INTEGER_PAT =
            Pattern.compile("([-+]?(?:\\d+|0x[\\da-fA-F]+))\\b");

    /**
     * Recognize an embedded new Date().
     */
    private static final Pattern NEW_DATE_PAT = Pattern.compile("(new\\s+Date\\s*\\(\\s*('[^']+'|\"[^\"]+\")\\s*\\))");

    /**
     * Types of tokens in a JSON input string.
     */
    enum TokenType
    {
        START_OBJECT,
        END_OBJECT,
        START_ARRAY,
        END_ARRAY,
        COMMA,
        COLON,
        STRING,
        FLOATING_POINT_NUMBER,
        INTEGER_NUMBER,
        LITERAL,
        UNQUOTED_ID,
        DATE
    }

    /**
     * Trivial class to hold a token from the JSON input string.
     */
    private static class Token
    {
        TokenType tokenType;
        String value;

        /**
         * Make a token.
         *
         * @param tt the token type
         * @param val the value
         */
        Token( TokenType tt, String val )
        {
            tokenType = tt;
            value = val;
        }
    }

    /**
     * Parse a string of JSON data.
     *
     * @param json the string of JSON data.
     * @return The object containing the parsed data.
     */
    public static Object parseJSON( String json )
    {
        return parseJSON(json, null);
    }

    /**
     * Parse a string of JSON data.
     *
     * @param json the string of JSON data.
     * @param cfg The config object.
     * @return The object containing the parsed data.
     */
    public static Object parseJSON( String json, JSONConfig cfg )
    {
        try{
            return parseJSON(new StringReader(json), cfg);
        }catch ( IOException e ){
            // will not happen.
            return null;
        }
    }

    /**
     * Parse JSON from an input stream.
     *
     * @param json The input stream.
     * @return The object containing the parsed data.
     * @throws IOException If there's a problem with I/O.
     * @since 1.7
     */
    public static Object parseJSON( Reader json ) throws IOException
    {
        return parseJSON(json, null);
    }

    /**
     * Parse JSON from an input stream.
     *
     * @param json The input stream.
     * @param cfg The config object.
     * @return The object containing the parsed data.
     * @throws IOException If there's a problem with I/O.
     * @since 1.7
     */
    public static Object parseJSON( Reader json, JSONConfig cfg ) throws IOException
    {
        JSONConfig jcfg = cfg == null ? new JSONConfig() : cfg;

        TokenReader tokens = new TokenReader(json, jcfg);
        try {
            return parseTokens(tokens.nextToken(), tokens);
        }catch ( JSONException|IOException e ){
            throw e;
        }catch ( Exception e ){
            throw new JSONParserException(e, jcfg);
        }
    }

    /**
     * Parse the tokens from the input stream.  This method is recursive
     * via the {@link #getValue(Token, TokenReader)} method.
     *
     * @param token The current token to work on.
     * @param tokens The token reader.
     * @return the object that results from parsing.
     * @throws IOException If there's a problem with I/O.
     * @throws ParseException If there's a problem parsing dates.
     */
    private static Object parseTokens( Token token, TokenReader tokens ) throws IOException, ParseException
    {
        if ( token == null ){
            return null;
        }
        switch ( token.tokenType ){
            case START_OBJECT:
                Map<String,Object> map = new LinkedHashMap<>();
                token = tokens.nextToken();
                while ( token != null ){
                    // need an identifier
                    if ( token.tokenType == TokenType.STRING || token.tokenType == TokenType.UNQUOTED_ID ){
                        // got an identifier.
                        String key = JSONUtil.unEscape(token.value, tokens.cfg);
                        // need a colon
                        token = tokens.nextToken();
                        if ( token.tokenType == TokenType.COLON ){
                            // got a colon.  get the value.
                            token = tokens.nextToken();
                            map.put(key, getValue(token, tokens));
                        }else{
                            throw new JSONParserException(TokenType.COLON, token.tokenType, tokens.cfg);
                        }
                    }else if ( token.tokenType == TokenType.END_OBJECT ){
                        break;                                  // empty object; break out of loop.
                    }else{
                        throw new JSONParserException(TokenType.END_OBJECT, token.tokenType, tokens.cfg);
                    }
                    token = tokens.nextToken();
                    if ( token.tokenType == TokenType.END_OBJECT ){
                        break;                                  // end of object; break out of loop.
                    }else if ( token.tokenType == TokenType.COMMA ){
                        token = tokens.nextToken();             // next field.
                    }else{
                        throw new JSONParserException(TokenType.END_OBJECT, token.tokenType, tokens.cfg);
                    }
                }
                // minimize memory usage.
                return new LinkedHashMap<>(map);
            case START_ARRAY:
                ArrayList<Object> list = new ArrayList<>();
                token = tokens.nextToken();
                while ( token != null && token.tokenType != TokenType.END_ARRAY ){
                    list.add(getValue(token, tokens));
                    token = tokens.nextToken();
                    if ( token.tokenType == TokenType.END_ARRAY ){
                        break;                                  // end of array.
                    }else if ( token.tokenType == TokenType.COMMA ){
                        token = tokens.nextToken();             // next item.
                    }else{
                        throw new JSONParserException(TokenType.END_ARRAY, token.tokenType, tokens.cfg);
                    }
                }
                // minimize memory usage.
                list.trimToSize();
                return list;
            default:
                return getValue(token, tokens);
        }
    }

    /**
     * The the value of the given token.
     *
     * @param token the token to get the value of.
     * @param tokens the token reader.
     * @return A JSON value in Java form.
     * @throws ParseException if there's a problem with date parsing.
     * @throws IOException If there's an IO problem.
     */
    private static Object getValue( Token token, TokenReader tokens ) throws ParseException, IOException
    {
        switch ( token.tokenType ){
            case STRING:
                JSONConfig cfg = tokens.cfg;
                String unesc = JSONUtil.unEscape(token.value, cfg);
                if ( cfg.isEncodeDatesAsObjects() || cfg.isEncodeDatesAsStrings() ){
                    try{
                        return parseDate(unesc, cfg);
                    }catch ( ParseException e ){
                    }
                }
                return unesc;
            case FLOATING_POINT_NUMBER:
                return Double.parseDouble(token.value);
            case INTEGER_NUMBER:
                if ( token.value.startsWith("0x") ){
                    return Long.valueOf(token.value.substring(2), 16);
                }else if ( OCTAL_PAT.matcher(token.value).matches() ){
                    return Long.valueOf(token.value, 8);
                }else{
                    return Long.valueOf(token.value);
                }
            case LITERAL:
                if ( token.value.equals(JSONUtil.NULL) ){
                    return null;
                }else{
                    return Boolean.valueOf(token.value);
                }
            case DATE:
                return parseDate(JSONUtil.unEscape(token.value, tokens.cfg), tokens.cfg);
            case START_OBJECT:
            case START_ARRAY:
                return parseTokens(token, tokens);
            default:
                throw new JSONParserException(TokenType.STRING, token.tokenType, tokens.cfg);
        }
    }

    /**
     * Parse a date string. This does a manual parse of any custom parsing
     * formats from the config object followed by ISO 8601 date strings. Oddly,
     * Java does not have the built in ability to parse ISO 8601. If the string
     * cannot be parsed then a ParseException will be thrown.
     *
     * @param dateStr The date string.
     * @param cfg the config object.
     * @return The date.
     * @throws ParseException If DateFormat.parse() fails.
     * @since 1.3
     */
    private static Date parseDate( String inputStr, JSONConfig cfg ) throws ParseException
    {
        ParseException ex = null;

        // try custom formatters, if any, followed by ISO 8601 formatters.
        for ( DateFormat fmt : cfg.getDateParseFormats() ){
            try{
                return fmt.parse(inputStr);
            }catch ( ParseException e ){
                ex = e;
            }
        }

        // none of the formats worked.
        throw ex;
    }

    /**
     * This class should never be instantiated.
     */
    private JSONParser()
    {
    }

    /**
     * This inner class is used to read tokens from the input stream.
     *
     * @since 1.7
     */
    private static class TokenReader
    {
        // simple tokens that can be safely shared by all threads.
        private static final Map<Character,Token> SIMPLE_TOKENS;

        static {
            Map<Character,Token> simpleTokens = new HashMap<>();
            simpleTokens.put('{', new Token(TokenType.START_OBJECT, null));
            simpleTokens.put('}', new Token(TokenType.END_OBJECT, null));
            simpleTokens.put('[', new Token(TokenType.START_ARRAY, null));
            simpleTokens.put(']', new Token(TokenType.END_ARRAY, null));
            simpleTokens.put(',', new Token(TokenType.COMMA, null));
            simpleTokens.put(':', new Token(TokenType.COLON, null));
            SIMPLE_TOKENS = new HashMap<>(simpleTokens);
        }

        // save the extra token when needed.
        private Token extraToken = null;

        // the reader.
        private Reader json;

        // the config object.
        private JSONConfig cfg;

        // the count of characters that have been read.
        private long charCount = 0;

        /**
         * Create a TokenReader.
         *
         * @param json The reader to get the JSON data from.
         * @param cfg the config object.
         */
        private TokenReader( Reader json, JSONConfig cfg )
        {
            this.json = json;
            this.cfg = cfg;
        }

        /**
         * Get the next token.
         *
         * @return The next token.
         * @throws IOException If there's a problem with I/O.
         */
        private Token nextToken() throws IOException
        {
            if ( extraToken != null ){
                // read the next token already.
                Token result = extraToken;
                extraToken = null;
                return result;
            }

            // get to the first non space code point.
            int codePoint = nextCodePoint();
            while ( codePoint >= 0 && Character.isSpaceChar(codePoint) ){
                codePoint = nextCodePoint();
            }
            if ( codePoint < 0 ){
                return null;                // end of input.
            }
            char ch = codePoint <= 0xFFFF ? (char)codePoint : 0;

            // look for the token.
            switch ( ch ){
                case '{':
                case '}':
                case '[':
                case ']':
                case ',':
                case ':':
                    return SIMPLE_TOKENS.get(ch);
                case '"':
                case '\'':
                    // string or quoted identifier or date string.
                    return new Token(TokenType.STRING, getQuotedString(ch));
                default:
                    // something else.  need to go to the next token or end of input
                    // to get this token.
                    String str;
                    {
                        StringBuilder buf = new StringBuilder();
                        int escapeCount = 0;
                        do{
                            ch = codePoint <= 0xFFFF ? (char)codePoint : 0;
                            if ( ch == '\\' ){
                                ++escapeCount;          // track escapes.
                                buf.append(ch);
                            }else if ( (ch == '\'' || ch == '"') && escapeCount % 2 == 0 ){
                                buf.append(ch)
                                   .append(getQuotedString(ch))
                                   .append(ch);
                                escapeCount = 0;
                            }else{
                                extraToken = SIMPLE_TOKENS.get(ch);
                                if ( extraToken != null ){
                                    break;              // ran into next token.  stop.
                                }else{
                                    buf.appendCodePoint(codePoint);
                                    escapeCount = 0;
                                }
                            }
                            codePoint = nextCodePoint();
                        }while ( codePoint >= 0 );

                        // remove trailing whitespace.
                        str = buf.toString().trim();
                    }

                    // check for new Date(), numbers, literals and unquoted ids.
                    Matcher matcher = NEW_DATE_PAT.matcher(str);
                    if ( matcher.find() && matcher.start() == 0 ){
                        String qs = matcher.group(2);
                        return new Token(TokenType.DATE, qs.substring(1, qs.length()-1));
                    }
                    matcher = JAVASCRIPT_FLOATING_POINT_PAT.matcher(str);
                    if ( matcher.find() && matcher.start() == 0 ){
                        String number = matcher.group(1);
                        return new Token(TokenType.FLOATING_POINT_NUMBER, number);
                    }
                    matcher = JAVASCRIPT_INTEGER_PAT.matcher(str);
                    if ( matcher.find() && matcher.start() == 0 ){
                        String number = matcher.group(1);
                        return new Token(TokenType.INTEGER_NUMBER, number);
                    }
                    matcher = LITERAL_PAT.matcher(str);
                    if ( matcher.find() && matcher.start() == 0 ){
                        String literal = matcher.group(1);
                        return new Token(TokenType.LITERAL, literal);
                    }
                    matcher = UNQUOTED_ID_PAT.matcher(str);
                    if ( matcher.find() && matcher.start() == 0 ){
                        String id = matcher.group(1);
                        return new Token(TokenType.UNQUOTED_ID, id);
                    }
                    throw new JSONParserException(str, charCount, cfg);
            }
        }

        /**
         * Get the string from the stream that is enclosed by the given quote
         * which has just been read from the stream.
         *
         * @param q The quote char.
         * @return The string (without quotes).
         * @throws IOException If there's a problem with I/O.
         */
        private String getQuotedString( char q ) throws IOException
        {
            StringBuilder str = new StringBuilder();
            int escapeCount = 0;

            while ( true ){
                int nextChar = json.read();
                if ( nextChar < 0 ){
                    throw new JSONParserException(q, cfg);  // missing close quote.
                }else{
                    ++charCount;
                    char ch = (char)nextChar;
                    if ( ch == '\\' ){
                        ++escapeCount;
                        str.append(ch);
                    }else if ( ch == q ){
                        if ( escapeCount % 2 == 0 ){
                            // even number of slashes -- string is done.
                            break;
                        }else{
                            // odd number of slashes -- quote is escaped.  keep going.
                            str.append(ch);
                            escapeCount = 0;
                        }
                    }else{
                        str.append(ch);
                        escapeCount = 0;
                    }
                }
            }

            return str.toString();
        }

        /**
         * Get the next code point from the input stream.
         *
         * @param json the input stream.
         * @return the code point.
         * @throws IOException If there's a problem with I/O.
         */
        private int nextCodePoint() throws IOException
        {
            int codePoint = json.read();

            if ( codePoint >= 0 ){
                if ( Character.isHighSurrogate((char)codePoint) ){
                    int ch = json.read();
                    if ( ch >= 0 && Character.isLowSurrogate((char)ch) ){
                        codePoint = Character.toCodePoint((char)codePoint, (char)ch);
                    }else{
                        throw new JSONParserException(codePoint, ch, charCount, cfg);
                    }
                }
                charCount += Character.charCount(codePoint);
            }

            return codePoint;
        }
    } // class TokenReader
}
