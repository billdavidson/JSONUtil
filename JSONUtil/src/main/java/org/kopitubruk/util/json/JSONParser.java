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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
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
 * and object that extends {@link Reader}, which may be useful and save memory
 * when reading from files or other input sources.  Common objects that extend
 * {@link Reader} include {@link InputStreamReader}, {@link FileReader} and
 * {@link BufferedReader}.
 *
 * @author Bill Davidson
 * @since 1.2
 */
public class JSONParser
{
    /**
     * Recognize literals
     */
    private static final Pattern LITERAL_PAT = Pattern.compile("^(null|true|false)$");

    /**
     * Recognize octal
     */
    private static final Pattern OCTAL_PAT = Pattern.compile("^0[0-7]*$");

    /**
     * Recognize unquoted id's.  They must conform to the ECMAScript 6 standard.
     * Id's which do not conform must be quoted.
     */
    private static final Pattern UNQUOTED_ID_PAT = JSONUtil.VALID_ECMA6_PROPERTY_NAME_PAT;

    /**
     * Recognize Javascript floating point.
     */
    private static final Pattern JAVASCRIPT_FLOATING_POINT_PAT =
            Pattern.compile("^((?:[-+]?(?:(?:\\d+\\.\\d+|\\.\\d+)(?:[eE][-+]?\\d+)?|Infinity))|NaN)$");

    /**
     * Recognize Javascript integers.
     */
    private static final Pattern JAVASCRIPT_INTEGER_PAT =
            Pattern.compile("^([-+]?(?:\\d+|0[xX][\\da-fA-F]+))$");

    /**
     * Recognize an embedded new Date().
     */
    private static final Pattern NEW_DATE_PAT = Pattern.compile("^(new\\s+Date\\s*\\(\\s*('[^']+'|\"[^\"]+\")\\s*\\))$");

    /**
     * Maximum possible significant digits in a 32 bit floating point number.
     */
    private static final int MAX_PRECISION_FOR_FLOAT = 9;

    /**
     * Maximum possible significant digits in a 64 bit floating point number.
     */
    private static final int MAX_PRECISION_FOR_DOUBLE = 17;

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
                return parseObject(tokens);
            case START_ARRAY:
                return parseArray(tokens);
            default:
                return getValue(token, tokens);
        }
    }

    /**
     * Parse the tokens from the input stream for an object.  This method is recursive
     * via the {@link #getValue(Token, TokenReader)} method.
     *
     * @param tokens The token reader.
     * @return the object that results from parsing.
     * @throws IOException If there's a problem with I/O.
     * @throws ParseException If there's a problem parsing dates.
     */
    private static Map<?,?> parseObject( TokenReader tokens ) throws IOException, ParseException
    {
        Map<String,Object> map = new LinkedHashMap<>();
        Token token = tokens.nextToken();
        while ( token != null ){
            // need an identifier
            if ( token.tokenType == TokenType.STRING || token.tokenType == TokenType.UNQUOTED_ID ){
                // got an identifier.
                String key = CodePointData.unEscape(token.value, tokens.cfg);
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
    }

    /**
     * Parse the tokens from the input stream for an array.  This method is recursive
     * via the {@link #getValue(Token, TokenReader)} method.
     *
     * @param tokens The token reader.
     * @return the object that results from parsing.
     * @throws IOException If there's a problem with I/O.
     * @throws ParseException If there's a problem parsing dates.
     */
    private static Object parseArray( TokenReader tokens ) throws IOException, ParseException
    {
        ArrayList<Object> list = new ArrayList<>();
        Token token = tokens.nextToken();
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

        if ( tokens.cfg.isUsePrimitiveArrays() ){
            // try to make it an array of primitives if possible.
            Object array = getArrayOfPrimitives(list);
            if ( array != null ){
                return array;
            }
        }

        // minimize memory usage.
        list.trimToSize();
        return list;
    }

    /**
     * Gets an array of primitives from a list if possible. This means that all
     * values in the list are non-null and either they are all boolean or all
     * strings with a single char or all numbers. If they are numbers, then the
     * most complex type of them will be the type of the array. In other words,
     * if there's one Double and five Integers, it will be an array of double.
     * <p>
     * An array of primitives can save a lot of memory vs a list of wrappers for
     * primitives. At the very least, you lose the memory overhead of the
     * references to each wrapper object which could be up to 8 bytes per
     * primitive. For numbers that can be bytes, this could be saving up to 15
     * bytes per number vs. making everything Long in a list as the code did
     * before.  Arrays also have slightly less memory overhead than an ArrayList
     * which maintains additional size and modCount ints as well as a reference
     * to its own internal array for up to 16 bytes of additional space required
     * by an ArrayList than an array.
     *
     * @param list The list.
     * @return The array or null if one could not be made.
     * @since 1.9
     */
    private static Object getArrayOfPrimitives( ArrayList<Object> list )
    {
        if ( list.size() < 1 ){
            return null;
        }

        boolean haveNumber = false;
        boolean haveBoolean = false;
        boolean haveChar = false;

        for ( Object obj : list ){
            if ( obj instanceof Number ){
                if ( obj instanceof BigInteger || obj instanceof BigDecimal ){
                    return null;
                }
                haveNumber = true;
            }else if ( obj instanceof Boolean ){
                haveBoolean = true;
            }else if ( obj instanceof String && ((String)obj).length() == 1 ){
                haveChar = true;
            }else{
                // null or not a primitive -- no compatibility.
                return null;
            }
        }

        if ( haveBoolean ){
            if ( haveNumber || haveChar ){
                // boolean is not compatible with other types.
                return null;
            }
            boolean[] booleans = new boolean[list.size()];
            for ( int i = 0; i < booleans.length; i++ ){
                booleans[i] = (Boolean)list.get(i);
            }
            return booleans;
        }

        if ( haveChar ){
            if ( haveNumber ){
                // char is not compatible with other types.
                return null;
            }
            char[] chars = new char[list.size()];
            for ( int i = 0; i < chars.length; i++ ){
                chars[i] = ((String)list.get(i)).charAt(0);
            }
            return chars;
        }

        // all Double or Long

        boolean haveDouble = false;
        boolean haveFloat = false;
        boolean haveLong = false;
        boolean haveInt = false;
        boolean haveShort = false;

        // make everything as small as possible without losing information.
        for ( int i = 0, len = list.size(); i < len; i++ ){
            Number num = (Number)list.get(i);
            if ( num instanceof Double ){
                Double d = (Double)num;
                boolean gotFloat = false;
                if ( d.isInfinite() || d.isNaN() ){
                    list.set(i, Float.valueOf(d.toString()));
                    gotFloat = true;
                }else{
                    BigDecimal bigDec = new BigDecimal(d.toString());
                    if ( bigDec.precision() <= MAX_PRECISION_FOR_FLOAT ){
                        Float f = Float.valueOf(bigDec.floatValue());
                        if ( !f.isInfinite() && bigDec.compareTo(new BigDecimal(f.toString())) == 0 ){
                            list.set(i, f);
                            gotFloat = true;
                        }
                    }
                }
                haveFloat = haveFloat || gotFloat;
                haveDouble = haveDouble || ! gotFloat;
            }else{
                long ln = num.longValue();
                BigDecimal bigInt = BigDecimal.valueOf(ln);
                try{
                    list.set(i, Byte.valueOf(bigInt.byteValueExact()));
                }catch ( ArithmeticException e ){
                    try{
                        list.set(i, Short.valueOf(bigInt.shortValueExact()));
                        haveShort = true;
                    }catch ( ArithmeticException ex ){
                        try{
                            list.set(i, Integer.valueOf(bigInt.intValueExact()));
                            haveInt = true;
                        }catch ( ArithmeticException ey ){
                            haveLong = true;
                        }
                    }
                }
            }
        }

        // make an array of the most complex type in the list and return it.

        if ( haveDouble ){
            double[] doubles = new double[list.size()];
            for ( int i = 0; i < doubles.length; i++ ){
                Number num = (Number)list.get(i);
                if ( num instanceof Float ){
                    doubles[i] = Double.parseDouble(num.toString());    // avoid cast rounding errors.
                }else{
                    doubles[i] = num.doubleValue();
                }
            }
            return doubles;
        }else if ( haveFloat ){
            float[] floats = new float[list.size()];
            for ( int i = 0; i < floats.length; i++ ){
                floats[i] = ((Number)list.get(i)).floatValue();
            }
            return floats;
        }else if ( haveLong ){
            long[] longs = new long[list.size()];
            for ( int i = 0; i < longs.length; i++ ){
                longs[i] = ((Number)list.get(i)).longValue();
            }
            return longs;
        }else if ( haveInt ){
            int[] ints = new int[list.size()];
            for ( int i = 0; i < ints.length; i++ ){
                ints[i] = ((Number)list.get(i)).intValue();
            }
            return ints;
        }else if ( haveShort ){
            short[] shorts = new short[list.size()];
            for ( int i = 0; i < shorts.length; i++ ){
                shorts[i] = ((Number)list.get(i)).shortValue();
            }
            return shorts;
        }else{
            byte[] bytes = new byte[list.size()];
            for ( int i = 0; i < bytes.length; i++ ){
                bytes[i] = ((Number)list.get(i)).byteValue();
            }
            return bytes;
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
                String unesc = CodePointData.unEscape(token.value, cfg);
                if ( cfg.isFormatDates() ){
                    try{
                        return parseDate(unesc, cfg);
                    }catch ( ParseException e ){
                    }
                }
                if ( cfg.isEncodeNumericStringsAsNumbers() ){
                    Matcher matcher = JAVASCRIPT_FLOATING_POINT_PAT.matcher(unesc);
                    if ( matcher.matches() ){
                        return getDecimal(matcher.group(1));
                    }
                    matcher = JAVASCRIPT_INTEGER_PAT.matcher(unesc);
                    if ( matcher.matches() ){
                        return getInteger(matcher.group(1));
                    }
                }
                return unesc;
            case FLOATING_POINT_NUMBER:
                return getDecimal(token.value);
            case INTEGER_NUMBER:
                return getInteger(token.value);
            case LITERAL:
                if ( token.value.equals("null") ){
                    return null;
                }else{
                    return Boolean.valueOf(token.value);
                }
            case DATE:
                return parseDate(CodePointData.unEscape(token.value, tokens.cfg), tokens.cfg);
            case START_OBJECT:
            case START_ARRAY:
                return parseTokens(token, tokens);
            default:
                throw new JSONParserException(TokenType.STRING, token.tokenType, tokens.cfg);
        }
    }

    /**
     * Convert a decimal string into a {@link Double} or if it doesn't fit in a
     * {@link Double} without losing information, then convert it to a
     * {@link BigDecimal}.
     *
     * @param decimalString A string representing a decimal/floating point number.
     * @return A {@link Double} or {@link BigDecimal} as needed to accurately represent the number.
     * @since 1.9
     */
    private static Number getDecimal( String decimalString )
    {
        try{
            // this will work except for NaN and Infinity
            BigDecimal bigDec = new BigDecimal(decimalString);
            // check significant digit count.
            if ( bigDec.precision() <= MAX_PRECISION_FOR_DOUBLE ){
                int scale = bigDec.scale();
                String fmt = '%' + (scale > 0 ? "." + scale : "") + 'e';
                double d = bigDec.doubleValue();
                if ( !Double.isInfinite(d) && bigDec.compareTo(new BigDecimal(String.format(fmt, d))) == 0 ){
                    // no precision loss going to double
                    return Double.valueOf(d);
                }
            }
            // precision loss, maintain precision using BigDecimal
            return bigDec;
        }catch ( NumberFormatException e ){
            // BigDecimal doesn't do NaN or Infinity
            return Double.valueOf(decimalString);
        }
    }

    /**
     * Convert an integer string into a {@link Long} or if it doesn't fit in a
     * {@link Long} without losing information, then convert it to a
     * {@link BigInteger}.
     *
     * @param integerString A string representing an integer number.
     * @return A {@link Long} or {@link BigInteger} as needed to accurately
     *         represent the number.
     * @since 1.9
     */
    private static Number getInteger( String integerString )
    {
        // parse with BigInteger because that will always work at this point.
        BigInteger bigInt;
        if ( integerString.startsWith("0x") || integerString.startsWith("0X") ){
            bigInt = new BigInteger(integerString.substring(2), 16);
        }else if ( OCTAL_PAT.matcher(integerString).matches() ){
            bigInt = new BigInteger(integerString, 8);
        }else{
            bigInt = new BigInteger(integerString);
        }

        try{
            return Long.valueOf(new BigDecimal(bigInt).longValueExact());
        }catch ( ArithmeticException e ){
            // too big to fit in a long.
            return bigInt;
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
            while ( codePoint >= 0 && Character.isWhitespace(codePoint) ){
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
                    return matchOthers(getOtherTokenString(codePoint));
            }
        }

        /**
         * Get a string for any type of token other than string or simple tokens.
         *
         * @param codePoint The current codepoint
         * @return The token
         * @throws IOException If there's an I/O error.
         */
        private String getOtherTokenString( int codePoint ) throws IOException
        {
            StringBuilder buf = new StringBuilder();
            int escapeCount = 0;
            do{
                char ch  = codePoint <= 0xFFFF ? (char)codePoint : 0;
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
            return buf.toString().trim();
        }

        /**
         * Match other possible tokens that could occur in the stream.
         *
         * @param str the string to match.
         * @return the token.
         */
        private Token matchOthers( String str )
        {
            // check for new Date(), numbers, literals and unquoted ids.
            Matcher matcher = NEW_DATE_PAT.matcher(str);
            if ( matcher.matches() ){
                String qs = matcher.group(2);
                return new Token(TokenType.DATE, qs.substring(1, qs.length()-1));
            }
            matcher = JAVASCRIPT_FLOATING_POINT_PAT.matcher(str);
            if ( matcher.matches() ){
                String number = matcher.group(1);
                return new Token(TokenType.FLOATING_POINT_NUMBER, number);
            }
            matcher = JAVASCRIPT_INTEGER_PAT.matcher(str);
            if ( matcher.matches() ){
                String number = matcher.group(1);
                return new Token(TokenType.INTEGER_NUMBER, number);
            }
            matcher = LITERAL_PAT.matcher(str);
            if ( matcher.matches() ){
                String literal = matcher.group(1);
                return new Token(TokenType.LITERAL, literal);
            }
            matcher = UNQUOTED_ID_PAT.matcher(str);
            if ( matcher.matches() ){
                String id = matcher.group(0);
                return new Token(TokenType.UNQUOTED_ID, id);
            }
            throw new JSONParserException(str, charCount, cfg);
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
