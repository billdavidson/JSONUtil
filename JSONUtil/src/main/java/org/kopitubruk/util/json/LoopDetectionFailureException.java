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

/**
 * Exception if the data structure loop detection breaks.  This
 * should never happen.
 *
 * @author Bill Davidson
 */
public class LoopDetectionFailureException extends JSONException
{
    private int stackIndex;

    /**
     * Copy of the object stack.
     */
    private int objStackLength;

    /**
     * Create a LoopDetectionFailureException
     *
     * @param stackIndex The index that the propertyValue was supposed to be.
     * @param jsonConfig The config object for locale and object stack.
     */
    LoopDetectionFailureException( int stackIndex, JSONConfig jsonConfig )
    {
        super(jsonConfig);
        this.stackIndex = stackIndex;
        objStackLength = jsonConfig.getObjStack().size();
        jsonConfig.clearObjStack();
    }

    /**
     * Create the message.
     *
     * @param locale The locale.
     * @return The message.
     */
    @Override
    String internalGetMessage( Locale locale )
    {
        ResourceBundle bundle = JSONUtil.getBundle(locale);

        if ( (stackIndex+1) != objStackLength ){
            return String.format(bundle.getString("wrongStackSize"), objStackLength, stackIndex+1);
        }else{
            // wrong reference on stack.
            return String.format(bundle.getString("wrongReferenceOnStack"), stackIndex);
        }
    }

    @SuppressWarnings("javadoc")
    private static final long serialVersionUID = 1L;
}
