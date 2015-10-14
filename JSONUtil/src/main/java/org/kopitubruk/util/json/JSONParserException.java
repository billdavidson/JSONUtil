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

import java.util.Locale;
import java.util.ResourceBundle;

import static org.kopitubruk.util.json.JSONParser.TokenType;

/**
 * Exception for problems parsing JSON.
 *
 * @author Bill Davidson
 * @since 1.2
 */
public class JSONParserException extends JSONException
{
    private String badData = null;
    private Character quote = null;
    private TokenType expectedTokenType = null;
    private TokenType tokenType = null;
    private int index = 0;
    private Exception e = null;

    /**
     * Constructor for bad data in JSON string.
     *
     * @param bd The start of the bad data.
     * @param idx The exact index of the bad data.
     * @param cfg The config object.
     */
    JSONParserException( String bd, int idx, JSONConfig cfg )
    {
        super(cfg);
        badData = bd;
        index = idx;
    }

    /**
     * Constructor for unclosed quote.
     *
     * @param q the quote character.
     * @param cfg The config object.
     */
    JSONParserException( char q, JSONConfig cfg )
    {
        super(cfg);
        quote = q;
    }

    /**
     * Constructor for an unexpected token type.
     *
     * @param ett expected token type.
     * @param tt actual token type.
     * @param cfg The config object.
     */
    JSONParserException( TokenType ett, TokenType tt, JSONConfig cfg )
    {
        super(cfg);
        expectedTokenType = ett;
        tokenType = tt;
    }

    /**
     * Wrapper for other RuntimeExceptions thrown by Java API.
     *
     * @param e the exception
     * @param cfg The config object.
     */
    JSONParserException( Exception e, JSONConfig cfg )
    {
        super(e, cfg);
        this.e = e;
    }

    /* (non-Javadoc)
     * @see org.kopitubruk.util.json.JSONException#internalGetMessage(java.util.Locale)
     */
    @Override
    String internalGetMessage( Locale locale )
    {
        ResourceBundle bundle = JSONUtil.getBundle(locale);

        if ( quote != null ){
            return String.format(bundle.getString("unclosedQuote"), quote);
        }else if ( expectedTokenType != null ){
            switch ( expectedTokenType ){
                case COLON:
                    return String.format(bundle.getString("expectedColon"), String.valueOf(tokenType));
                case END_OBJECT:
                    return String.format(bundle.getString("expectedIdentifier"), String.valueOf(tokenType));
                default:
                    return String.format(bundle.getString("expectedValue"), String.valueOf(tokenType));
            }
        }else if ( e != null ){
            return e.getLocalizedMessage();
        }else{
            String str = badData == null ? "" : badData;
            int pos = 10;
            if ( str.length() > 30 ){
                int begin = index - 10;
                int end = index + 20;
                if ( begin < 0 ){
                    end -= begin;
                    pos += begin;
                    begin = 0;
                }
                end = Math.min(end, str.length());
                str = str.substring(begin, end);
            }else{
                pos = index;
            }
            StringBuilder buf = new StringBuilder(Math.max(pos-1, 0));
            for ( int i = 0; i < pos; i++ ){
                buf.append(' ');
            }
            return String.format(bundle.getString("unrecognizedData"), str, buf.toString());
        }
    }

    private static final long serialVersionUID = 1L;
}
