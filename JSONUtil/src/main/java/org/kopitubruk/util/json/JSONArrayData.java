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
 * Package private class to iterate over the different types that can produce a
 * Javascript array. Created because the array code was too redundant.
 *
 * @author Bill Davidson
 * @since 1.7.1
 */
class JSONArrayData implements Iterable<Object>
{
    private Object obj;

    /**
     * Create a JSONArrayData. Input must be an {@link Iterable},
     * {@link Enumeration} or array.
     *
     * @param obj The source object for the array to be created. This must be an
     *            {@link Iterable}, {@link Enumeration} or an array.
     */
    JSONArrayData( Object obj )
    {
        this.obj = obj;
    }

    /* (non-Javadoc)
     * @see java.lang.Iterable#iterator()
     */
    public Iterator<Object> iterator()
    {
        if ( obj instanceof Iterable ){
            @SuppressWarnings("unchecked")
            Iterable<Object> iterable = (Iterable<Object>)obj;  // gag.
            return iterable.iterator();
        }else if ( obj instanceof Enumeration ){
            return new Iterator<Object>() {
                private Enumeration<?> enumeration = (Enumeration<?>)obj;

                //@Override
                public boolean hasNext()
                {
                    return enumeration.hasMoreElements();
                }

                //@Override
                public Object next()
                {
                    return enumeration.nextElement();
                }

                //@Override
                public void remove()
                {
                    throw new UnsupportedOperationException();
                }
            };
        }else{          // obj.getClass().isArray() == true
            return new Iterator<Object>() {
                private Object array = obj;
                private int i = 0;
                private int len = Array.getLength(array);

                //@Override
                public boolean hasNext()
                {
                    return i < len;
                }

                //@Override
                public Object next()
                {
                    // With array of primitives, this will box the primitive.
                    return Array.get(array, i++);
                }

                //@Override
                public void remove()
                {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
