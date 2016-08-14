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
 * Exception for wrapping reflection exceptions.
 *
 * @author Bill Davidson
 * @since 1.9
 */
public final class JSONReflectionException extends JSONException
{
    private Object offender = null;
    private String field = null;
    private int level;
    private boolean badObject = false;

    /**
     * Wraps another exception.
     *
     * @param offender The offending object.
     * @param field The field name for which access caused the exception.
     * @param e The exception that this is wrapping.
     * @param cfg the config object
     */
    JSONReflectionException( Object offender, String field, Exception e, JSONConfig cfg )
    {
        super(e, cfg);
        this.offender = offender;
        this.field = field;
    }

    /**
     * Exception for bad privacy levels.
     *
     * @param level the bad level
     * @param cfg the config object
     */
    JSONReflectionException( int level, JSONConfig cfg )
    {
        super(cfg);
        this.level = level;
    }

    /**
     * Recursive reflection exception.
     */
    JSONReflectionException()
    {
        super(new JSONConfig());
        badObject = true;
    }

    /**
     * No such field.
     *
     * @param offender The offending object.
     * @param field The field name for which access caused the exception.
     * @param cfg the config object
     */
    JSONReflectionException( Object offender, String field, JSONConfig cfg )
    {
        super(cfg);
        this.offender = offender;
        this.field = field;
    }

    /**
     * Create and return the loop error message.
     *
     * @param locale the locale.
     * @return The message.
     */
    @Override
    String internalGetMessage( Locale locale )
    {
        ResourceBundle bundle = JSONUtil.getBundle(locale);

        if ( offender != null ){
            if ( this.getCause() != null ){
                String fmt = bundle.getString("reflectionException");
                return String.format(fmt, getClassName(offender), field, getClassName(this.getCause()));
            }else{
                String fmt = bundle.getString("noSuchField");
                return String.format(fmt, getClassName(offender), field);
            }
        }else if ( badObject ){
            return bundle.getString("recursiveReflection");
        }else{
            String fmt = bundle.getString("badPrivacyLevel");
            return String.format(fmt, level);
        }
    }

    private static final long serialVersionUID = 1L;
}
