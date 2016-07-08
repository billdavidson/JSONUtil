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

import java.lang.reflect.Array;
import java.util.Enumeration;
import java.util.Iterator;

/**
 * Private class to iterate over the different types that can produce a
 * Javascript array. Created because the array code was too redundant.
 *
 * @author Bill Davidson
 * @since 1.8
 */
class JSONArrayData implements Iterable<Object>
{
    private Object obj;

    /**
     * Create an ArrayData.  Input must be an Iterable, Enumeration
     * or array.
     *
     * @param obj The source object for the array to be created.
     */
    JSONArrayData( Object obj )
    {
        this.obj = obj;
    }

    /* (non-Javadoc)
     * @see java.lang.Iterable#iterator()
     */
    @Override
    public Iterator<Object> iterator()
    {
        if ( obj instanceof Iterable ){
            @SuppressWarnings("unchecked")
            Iterable<Object> iterable = (Iterable<Object>)obj;
            return iterable.iterator();
        }else{
            return new Iterator<Object>() {
                private boolean isEnumeration = obj instanceof Enumeration;
                private Enumeration<?> enumeration = isEnumeration ? (Enumeration<?>)obj : null;
                private Object array = isEnumeration ? null : obj;
                private int i = 0;
                private int len = isEnumeration ? 0 : Array.getLength(array);

                @Override
                public boolean hasNext()
                {
                    return isEnumeration ? enumeration.hasMoreElements() : i < len;
                }

                @Override
                public Object next()
                {
                    return isEnumeration ? enumeration.nextElement() : Array.get(array, i++);
                }
            };
        }
    }
}
