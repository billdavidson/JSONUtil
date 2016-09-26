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

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Exception used when undefined code points are encountered in the input and
 * the value of {@link JSONConfig#getUndefinedCodePointPolicy()} is
 * {@link JSONConfig#EXCEPTION}.
 *
 * @author Bill Davidson
 */
public class UndefinedCodePointException extends JSONException
{
    private String strValue;
    private int position;
    private int codePoint;

    /**
     * Make a UndefinedCodePointException
     *
     * @param cfg the config object.
     * @param strValue the string that contains the undefined code point.
     * @param position the position within the string value of the undefined code point
     * @param codePoint the undefined code point.
     */
    UndefinedCodePointException( JSONConfig cfg, String strValue, int position, int codePoint )
    {
        super(cfg);
        this.strValue = strValue;
        this.position = position;
        this.codePoint = codePoint;
    }

    /**
     * Make a UndefinedCodePointException
     *
     * @param cfg the config object.
     * @param strValue the string that contains the undefined code point.
     * @param position the position within the string value of the undefined code point
     * @param codePointString the hex string of the undefined code point.
     */
    UndefinedCodePointException( JSONConfig cfg, String strValue, int position, String codePointString )
    {
        super(cfg);
        this.strValue = strValue;
        this.position = position;
        codePoint = Integer.parseInt(codePointString, 16);
    }

    /**
     * Get the string value that contained this undefined code point.
     *
     * @return the string value that contained this undefined code point.
     */
    public String getStrValue()
    {
        return strValue;
    }

    /**
     * Get the position within the string of the code point that caused this exception.
     *
     * @return the position within the string of the code point that caused this exception.
     */
    public int getPosition()
    {
        return position;
    }

    /**
     * Get the code point that caused this exception.
     *
     * @return the code point that caused this exception.
     */
    public int getCodePoint()
    {
        return codePoint;
    }

    /* (non-Javadoc)
     * @see org.kopitubruk.util.json.JSONException#internalGetMessage(java.util.Locale)
     */
    @Override
    String internalGetMessage( Locale locale )
    {
        ResourceBundle bundle = JSONUtil.getBundle(locale);

        return String.format(bundle.getString("undefinedCodePoint"), codePoint, position);
    }

    private static final long serialVersionUID = 1L;
}
