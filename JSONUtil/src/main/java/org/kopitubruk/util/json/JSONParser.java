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

import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Queue;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a JSON parser. It accepts a fairly loose version of JSON. Essentially
 * it tries to allow anything Javascript eval() allows so it lets you use single
 * quotes instead of double quotes if you want and all versions of Javascript
 * numbers are allowed. Unquoted identifiers are also permitted. Escapes in
 * strings are converted to their proper characters and all Javascript escapes
 * are permitted.
 * <p>
 * Javascript objects are converted to {@link LinkedHashMap}s with the
 * identifiers being the keys.
 * <p>
 * Javascript arrays are converted to {@link ArrayList}s.
 * <p>
 * Literal null is just a null value and boolean values are converted to
 * {@link Boolean}'s.
 * <p>
 * Floating point numbers are converted to {@link Double} and integers are
 * converted to {@link Long}.
 * <p>
 * If the {@link JSONConfig#isEncodeDatesAsObjects()} or
 * {@link JSONConfig#isEncodeDatesAsStrings()} returns true, then strings that
 * look like dates will be converted to {@link Date} objects. Date strings
 * should be ISO 8601 extended format that include data down to seconds.
 * Fractions of seconds and time zone offsets are optional.
 * <p>
 * Calls to the new Date(String) constructor from Javascript are converted to
 * {@link Date}s.
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
     * Recognize white space
     */
    private static final Pattern SPACE_PAT = Pattern.compile("(\\s+)(?:\\S.*)?");

    /**
     * Recognize octal
     */
    private static final Pattern OCTAL_PAT = Pattern.compile("0[0-7]*\\b");

    /**
     * Recognize unquoted id's
     */
    private static final Pattern UNQUOTED_ID_PAT =
            Pattern.compile("((?:[_\\$\\p{L}\\p{Nl}]|\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})(?:[_\\$\\p{L}\\p{Nl}\\p{Nd}\\p{Mn}\\p{Mc}\\p{Pc}\\u200C\\u200D]|\\\\u\\p{XDigit}{4}|\\\\u\\{\\p{XDigit}+\\})*)\\b");

    /**
     * Recognize Javascript floating point.
     */
    private static final Pattern JAVASCRIPT_FLOATING_POINT_PAT =
            Pattern.compile("((?:[-+]?(?:(?:\\d+\\.\\d+|\\.\\d+)(?:[eE][-+]?\\d+)?|Infinity))|NaN)\\b");

    /**
     * Recognize Javascript integers.
     */
    private static final Pattern JAVASCRIPT_INTEGER_PAT =
            Pattern.compile("([-+]?(?:\\d+|0x[\\da-fA-F]+))\\b");

    /**
     * Recognize an embedded new Date().
     */
    private static final Pattern NEW_DATE = Pattern.compile("(new\\s+Date\\s*\\(\\s*('[^']+'|\"[^\"]+\")\\s*\\))");

    /**
     * Used to convert time zones because Java 6 doesn't support ISO 8601 time zones.
     */
    private static final Pattern TZ_PAT = Pattern.compile("^(.+)([-+]\\d{2})(?::(\\d{2})|Z)$");

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
     * @throws ParseException
     */
    public static Object parseJSON( String json ) throws ParseException
    {
        return parseJSON(json, null);
    }

    /**
     * Parse a string of JSON data.
     *
     * @param json the string of JSON data.
     * @param cfg The config object.
     * @return The object containing the parsed data.
     * @throws ParseException
     */
    public static Object parseJSON( String json, JSONConfig cfg ) throws ParseException
    {
        JSONConfig jcfg = cfg == null ? new JSONConfig() : cfg;
        JSONCallData cld = new JSONCallData(jcfg);

        try {
            Queue<Token> tokens = tokenize(json, cld);

            if ( tokens.size() < 1 ){
                return null;
            }

            return parseTokens(tokens, tokens.remove(), cld);
        }catch ( RuntimeException e ){
            if ( e instanceof JSONException ){
                throw e;
            }else{
                System.err.println(e.getMessage());
                throw new JSONParserException(e, cld);
            }
        }
    }

    /**
     * Parse the list of tokens.
     *
     * @param tokens the list of tokens.
     * @param nextToken the next token.
     * @param cld the call data.
     * @return The object from the given tokens.
     * @throws ParseException
     */
    private static Object parseTokens( Queue<Token> tokens, Token nextToken, JSONCallData cld ) throws ParseException
    {
        switch ( nextToken.tokenType ){
            case START_OBJECT:
                Map<String,Object> map = new LinkedHashMap<String,Object>();
                nextToken = tokens.remove();
                while ( tokens.size() > 0 ){
                    // need an identifier
                    if ( nextToken.tokenType == TokenType.STRING || nextToken.tokenType == TokenType.UNQUOTED_ID ){
                        // got an identifier.
                        String key = nextToken.value;
                        // need a colon
                        nextToken = tokens.remove();
                        if ( nextToken.tokenType == TokenType.COLON ){
                            // got a colon.  get the value.
                            nextToken = tokens.remove();
                            map.put(key, getValue(nextToken, tokens, cld));
                        }else{
                            throw new JSONParserException(TokenType.COLON, nextToken.tokenType, cld);
                        }
                    }else if ( nextToken.tokenType == TokenType.END_OBJECT ){
                        // empty object; break out of loop.
                        break;
                    }else{
                        System.err.println(nextToken.value);
                        throw new JSONParserException(TokenType.END_OBJECT, nextToken.tokenType, cld);
                    }
                    nextToken = tokens.remove();
                    if ( nextToken.tokenType == TokenType.END_OBJECT ){
                        // end of object; break out of loop.
                        break;
                    }else if ( nextToken.tokenType == TokenType.COMMA ){
                        // next field.
                        nextToken = tokens.remove();
                    }
                }
                // minimize memory usage.
                return new LinkedHashMap<String,Object>(map);
            case START_ARRAY:
                ArrayList<Object> list = new ArrayList<Object>();
                nextToken = tokens.remove();
                while ( tokens.size() > 0 && nextToken.tokenType != TokenType.END_ARRAY ){
                    list.add(getValue(nextToken, tokens, cld));
                    nextToken = tokens.remove();
                    if ( nextToken.tokenType == TokenType.END_ARRAY ){
                        // end of array.
                        break;
                    }else if ( nextToken.tokenType == TokenType.COMMA ){
                        // next item.
                        nextToken = tokens.remove();
                    }
                }
                // minimize memory usage.
                list.trimToSize();
                return list;
            default:
                return getValue(nextToken, tokens, cld);
        }
    }

    /**
     * The the value of the given token.
     *
     * @param token the token to get the value of.
     * @param tokens the list of tokens if the token is the start of an object or array.
     * @param  cld the call data.
     * @return A JSON value.
     * @throws ParseException
     */
    private static Object getValue( Token token, Queue<Token> tokens, JSONCallData cld ) throws ParseException
    {
        switch ( token.tokenType ){
            case STRING:
                return token.value;
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
                return parseDate(token.value, cld);
            case START_OBJECT:
            case START_ARRAY:
                return parseTokens(tokens, token, cld);
            default:
                throw new JSONParserException(TokenType.STRING, token.tokenType, cld);
        }
    }

    /**
     * Convert a JSON string to a list of tokens.
     *
     * @param json The JSON string to be tokenized.
     * @param  cld the call data.
     * @return the list of tokens.
     */
    private static Queue<Token> tokenize( String json, JSONCallData cld )
    {
        Queue<Token> result = new LinkedList<Token>();

        int i = 0;
        int len = json.length();
        JSONConfig cfg = cld.getJSONConfig();
        boolean doDateStrings = cfg.isEncodeDatesAsObjects() || cfg.isEncodeDatesAsStrings();
        while ( i < len ){
            int codePoint = json.codePointAt(i);
            int charCount = Character.charCount(codePoint);

            switch ( codePoint ){
                case '{': result.add(new Token(TokenType.START_OBJECT, null)); break;
                case '}': result.add(new Token(TokenType.END_OBJECT, null)); break;
                case '[': result.add(new Token(TokenType.START_ARRAY, null)); break;
                case ']': result.add(new Token(TokenType.END_ARRAY, null)); break;
                case ',': result.add(new Token(TokenType.COMMA, null)); break;
                case ':': result.add(new Token(TokenType.COLON, null)); break;
                case '"':
                case '\'':
                    // string or quoted identifier
                    char q = json.charAt(i);

                    if ( i+1 < len ){
                        int j = findQuote(json, q, i+1, cld);
                        int k = j-1;
                        if ( json.charAt(k) == '\\' ){
                            // quote might be escaped.
                            boolean notFinished;
                            do {
                                notFinished = false;
                                int slashCount = 0;
                                // count back possible multiple backslashes.
                                while ( json.charAt(k) == '\\' ){
                                    ++slashCount;
                                    --k;
                                }
                                if ( slashCount % 2 != 0 ){
                                    // odd number of slashes, quote is escaped.
                                    notFinished = true;
                                    j = findQuote(json, q, j+1, cld);
                                    k = j-1;
                                }
                                // else, quote not escaped. string is finished.
                            } while ( notFinished );
                        }
                        String str = json.substring(i+1, j);
                        Date dt = null;
                        if ( doDateStrings ){
                            try{
                                dt = parseDate(str, cld);
                            }catch ( ParseException e ){
                            }
                        }
                        if ( dt != null ){
                            result.add(new Token(TokenType.DATE, str));
                        }else{
                            result.add(new Token(TokenType.STRING, JSONUtil.unEscape(str)));
                        }
                        i += str.length()+1;
                    }else{
                        throw new JSONParserException(q, cld);
                    }
                    break;
                default:
                    Matcher matcher = NEW_DATE.matcher(json);
                    if ( matcher.find(i) && matcher.start() == i ){
                        String newDate = matcher.group(1);
                        i += newDate.length() - 1;
                        String qs = matcher.group(2);
                        result.add(new Token(TokenType.DATE, qs.substring(1, qs.length()-1)));
                    }else{
                        matcher = JAVASCRIPT_FLOATING_POINT_PAT.matcher(json);
                        if ( matcher.find(i) && matcher.start() == i ){
                            String number = matcher.group(1);
                            result.add(new Token(TokenType.FLOATING_POINT_NUMBER, number));
                            i += number.length() - 1;
                        }else{
                            matcher = JAVASCRIPT_INTEGER_PAT.matcher(json);
                            if ( matcher.find(i) && matcher.start() == i ){
                                String number = matcher.group(1);
                                result.add(new Token(TokenType.INTEGER_NUMBER, number));
                                i += number.length() - 1;
                            }else{
                                matcher = LITERAL_PAT.matcher(json);
                                if ( matcher.find(i) && matcher.start() == i ){
                                    String literal = matcher.group(1);
                                    result.add(new Token(TokenType.LITERAL, literal));
                                    i += literal.length() - 1;
                                }else{
                                    matcher = SPACE_PAT.matcher(json);
                                    if ( matcher.find(i) && matcher.start() == i ){
                                        // ignore white space outside of strings.
                                        i += matcher.group(1).length() - 1;
                                    }else{
                                        matcher = UNQUOTED_ID_PAT.matcher(json);
                                        if ( matcher.find(i) && matcher.start() == i ){
                                            String id = matcher.group(1);
                                            result.add(new Token(TokenType.UNQUOTED_ID, JSONUtil.unEscape(id)));
                                            i += id.length() - 1;
                                        }else{
                                            throw new JSONParserException(json, i, cld);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
            }
            i += charCount;
        }

        return result;
    }

    /**
     * Find the next quote character in the input stream.
     *
     * @param json The JSON to search.
     * @param q The quote character to look for.
     * @param index The start index for the next quote search.
     * @param  cld the call data.
     * @return The index of the next quote.
     */
    private static int findQuote( String json, char q, int index, JSONCallData cld )
    {
        int j = json.indexOf(q, index);
        if ( j < 0 ){
            throw new JSONParserException(q, cld);
        }
        return j;
    }

    /**
     * Parse a date string. This does a manual parse of ISO 8601 date strings.
     * Oddly, Java does not have the built in ability to parse ISO 8601. If the
     * date cannot be parsed as ISO 8601, then a default DateFormat parsing is
     * attempted.
     *
     * @param dateStr The date string.
     * @return The date.
     * @throws ParseException If DateFormat.parse() fails.
     * @since 1.3
     */
    private static Date parseDate( String inputStr, JSONCallData cld ) throws ParseException
    {
        // try the ISO 8601 formatters.
        String dateStr = fixTimeZone(inputStr);

        for ( DateFormat fmt : cld.getDateFormatters() ){
            try{
                return fmt.parse(dateStr);
            }catch ( ParseException e ){
            }
        }

        // Hail Mary.
        Locale loc = cld.getJSONConfig().getLocale();
        ParseException ex = null;
        int[] styles = { DateFormat.FULL, DateFormat.LONG, DateFormat.MEDIUM, DateFormat.SHORT };
        for ( int style : styles ){
            DateFormat fmt = DateFormat.getDateInstance(style, loc);
            try{
                return fmt.parse(inputStr);
            }catch ( ParseException e ){
                ex = e;
            }
        }

        // none of the styles worked.
        throw ex;
    }

    /**
     * Convert ISO 8601 time zones into RFC 822 time zones because
     * Java 6 SimpleDateFormat doesn't support ISO 8601 time zones.
     *
     * @param inputStr the input string.
     * @return the string with the time zone fixed.
     */
    private static String fixTimeZone( String inputStr )
    {
        Matcher matcher = TZ_PAT.matcher(inputStr);
        if ( matcher.find() ){
            String zone = matcher.group(3);
            if ( zone == null ){
                zone = "";
            }else if ( "Z".equals(zone) ){
                zone = "+0000";
            }
            return matcher.group(1) + matcher.group(2) + zone;
        }else{
            return inputStr;
        }
    }

    /**
     * This class should never be instantiated.
     */
    private JSONParser()
    {
    }
}
