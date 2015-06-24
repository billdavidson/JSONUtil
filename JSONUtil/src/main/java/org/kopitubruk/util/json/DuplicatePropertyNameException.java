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
import java.util.ResourceBundle;

/**
 * Exception thrown when a map produces duplicate property names in the same
 * object, which is extremely unlikely but could happen if the map has two keys
 * which are not equal but produce the same result from their toString() method.
 *
 * @author Bill Davidson
 */
public final class DuplicatePropertyNameException extends JSONException
{
    /**
     * The duplicate property name.
     */
    private String duplicateName;

    /**
     * Constructor.
     *
     * @param duplicateName The duplicated property name.
     * @param jsonConfig Used to get Locale for {@link #getLocalizedMessage()}.
     */
    DuplicatePropertyNameException( String duplicateName, JSONConfig jsonConfig )
    {
        super(jsonConfig);
        this.duplicateName = duplicateName;
        jsonConfig.clearObjStack();
    }

    /**
     * Create and return the duplicate name error message.
     *
     * @param locale the locale.
     * @return The message.
     */
    String internalGetMessage( Locale locale )
    {
        ResourceBundle bundle = JSONUtil.getBundle(locale);

        return String.format(bundle.getString("duplicateName"), duplicateName);
    }

    @SuppressWarnings("javadoc")
    private static final long serialVersionUID = 1L;
}
