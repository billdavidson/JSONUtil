package org.kopitubruk.util.json;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import org.kopitubruk.util.json.JSONParser.Token;
import org.kopitubruk.util.json.JSONParser.TokenType;

/**
 * This class is used to read JSON tokens from the input stream.
 *
 * @since 1.7
 */
class JSONTokenReader
{
    // simple tokens that can be safely shared by all threads.
    private static final Map<Character,Token> SIMPLE_TOKENS;

    static {
        Map<Character,Token> simpleTokens = new HashMap<Character,Token>();
        simpleTokens.put('{', new Token(TokenType.START_OBJECT, null));
        simpleTokens.put('}', new Token(TokenType.END_OBJECT, null));
        simpleTokens.put('[', new Token(TokenType.START_ARRAY, null));
        simpleTokens.put(']', new Token(TokenType.END_ARRAY, null));
        simpleTokens.put(',', new Token(TokenType.COMMA, null));
        simpleTokens.put(':', new Token(TokenType.COLON, null));
        SIMPLE_TOKENS = new HashMap<Character,Token>(simpleTokens);
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
    JSONTokenReader( Reader json, JSONConfig cfg )
    {
        this.json = json;
        this.cfg = cfg;
    }

    /**
     * The the config that this JSONTokenReader was created with.
     *
     * @return the cfg
     */
    JSONConfig getJSONConfig()
    {
        return cfg;
    }

    /**
     * Get the next token.
     *
     * @return The next token.
     * @throws IOException If there's a problem with I/O.
     */
    Token nextToken() throws IOException
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
        Matcher matcher = JSONParser.NEW_DATE_PAT.matcher(str);
        if ( matcher.matches() ){
            String qs = matcher.group(2);
            return new Token(TokenType.DATE, qs.substring(1, qs.length()-1));
        }
        matcher = JSONParser.JAVASCRIPT_FLOATING_POINT_PAT.matcher(str);
        if ( matcher.matches() ){
            String number = matcher.group(1);
            return new Token(TokenType.FLOATING_POINT_NUMBER, number);
        }
        matcher = JSONParser.JAVASCRIPT_INTEGER_PAT.matcher(str);
        if ( matcher.matches() ){
            String number = matcher.group(1);
            return new Token(TokenType.INTEGER_NUMBER, number);
        }
        matcher = JSONParser.LITERAL_PAT.matcher(str);
        if ( matcher.matches() ){
            String literal = matcher.group(1);
            return new Token(TokenType.LITERAL, literal);
        }
        matcher = JSONParser.UNQUOTED_ID_PAT.matcher(str);
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
}
