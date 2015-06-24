/*
 * Copyright (c) 2015 Bill Davidson
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

/**
 * Holds some redundant code/data for the other exceptions thrown
 * by JSONUtil.
 * 
 * @author Bill Davidson
 */
public abstract class JSONException extends IllegalArgumentException
{
    private Locale locale;

    /**
     * Create a LocalizedIllegalArgumentException with the given JSONConfig.
     *
     * @param jsonConfig the config object.
     */
    JSONException( JSONConfig jsonConfig )
    {
        setLocale(jsonConfig.getLocale());
    }

    /**
     * Set the locale to be used by {@link #getLocalizedMessage()}
     *
     * @param locale the locale to set
     */
    public void setLocale( Locale locale )
    {
        this.locale = locale != null ? locale : JSONConfigDefaults.getLocale();
    }

    /* (non-Javadoc)
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage()
    {
        return internalGetMessage(JSONConfigDefaults.getLocale());
    }

    /* (non-Javadoc)
     * @see java.lang.Throwable#getLocalizedMessage()
     */
    @Override
    public String getLocalizedMessage()
    {
        return internalGetMessage(locale);
    }

    /**
     * Actually produces the message for other message methods.
     *
     * @param locale The locale to use when generating the message.
     * @return The message.
     */
    abstract String internalGetMessage( Locale locale );

    @SuppressWarnings("javadoc")
    private static final long serialVersionUID = 1L;
}
