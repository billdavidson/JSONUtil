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

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Exception for handling loops in the data structures sent to
 * various toJSON methods.
 *
 * @author Bill Davidson
 */
public final class DataStructureLoopException extends JSONException
{
    /**
     * toString() of the offending object.
     */
    private Object offender;

    /**
     * Copy of the object stack.
     */
    private Object[] objStack = null;

    /**
     * Constructor.
     *
     * @param offender The offending object.
     * @param jsonConfig Used to create the error message.
     */
    DataStructureLoopException( Object offender, JSONConfig jsonConfig )
    {
        super(jsonConfig);
        this.offender = offender;
        // freeze a copy of the stack.
        List<Object> stack = jsonConfig.getObjStack();
        objStack = stack.toArray(new Object[stack.size()]);
        jsonConfig.clearObjStack();
    }

    /**
     * Create and return the loop error message.
     *
     * @param locale the locale.
     * @return The message.
     */
    String internalGetMessage( Locale locale )
    {
        ResourceBundle bundle = JSONUtil.getBundle(locale);
        StringBuilder message = new StringBuilder();

        message.append(String.format(bundle.getString("dataStructureLoop"), getClassName(offender)));

        // show the stack and indicate which object is duplicated.
        for ( int i = 0; i < objStack.length; i++ ){
            Object currentObject = objStack[i];
            message.append("\n\t").append(i).append(' ')
                   .append(getClassName(currentObject));
            if ( offender == currentObject ){
                message.append(" <<<");
            }
        }

        return message.toString();
    }

    /**
     * Get the name of the class of the object.
     *
     * @param obj the object
     * @return the class name.
     */
    private String getClassName( Object obj )
    {
        String name = obj.getClass().getCanonicalName();
        if ( name == null ){
            name = obj.getClass().isArray() ? "[array]" : "[unknown]";
        }
        return name;
    }

    @SuppressWarnings("javadoc")
    private static final long serialVersionUID = 1L;
}
