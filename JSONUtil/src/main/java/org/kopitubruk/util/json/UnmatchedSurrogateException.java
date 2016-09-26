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
 * Exception used when unmatched surrogates are encountered in the input and the
 * value of {@link JSONConfig#getUnmatchedSurrogatePolicy()} is
 * {@link JSONConfig#EXCEPTION}.
 *
 * @author Bill Davidson
 */
public class UnmatchedSurrogateException extends JSONException
{
    private String strValue;
    private int position;
    private char unmatchedSurrogate;

    /**
     * Make a UnmatchedSurrogateException
     *
     * @param cfg the config object.
     * @param strValue the string that contains the unmatched surrogate.
     * @param position the position within the string value of the unmatched surrogate.
     * @param unmatchedSurrogate the unmatched surrogate.
     */
    UnmatchedSurrogateException( JSONConfig cfg, String strValue, int position, char unmatchedSurrogate )
    {
        super(cfg);
        this.strValue = strValue;
        this.position = position;
        this.unmatchedSurrogate = unmatchedSurrogate;
    }

    /**
     * Get the string value that contained this unmatched surrogate.
     *
     * @return the string value that contained this unmatched surrogate.
     */
    public String getStrValue()
    {
        return strValue;
    }

    /**
     * Get the position within the string of the unmatched surrogate that caused this exception.
     *
     * @return the position within the string of the unmatched surrogate that caused this exception.
     */
    public int getPosition()
    {
        return position;
    }

    /**
     * Get the unmatched surrogate that caused this exception.
     *
     * @return the unmatched surrogate that caused this exception.
     */
    public char getUnmatchedSurrogate()
    {
        return unmatchedSurrogate;
    }

    /* (non-Javadoc)
     * @see org.kopitubruk.util.json.JSONException#internalGetMessage(java.util.Locale)
     */
    @Override
    String internalGetMessage( Locale locale )
    {
        ResourceBundle bundle = JSONUtil.getBundle(locale);

        return String.format(bundle.getString("unmatchedSurrogate"), (int)unmatchedSurrogate, position);
    }

    private static final long serialVersionUID = 1L;
}
