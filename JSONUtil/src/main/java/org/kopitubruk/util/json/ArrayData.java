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
 */
class ArrayData
{
    private ArrayType arrayType;
    private Iterator<?> iterator;
    private Enumeration<?> enumeration;
    private Object array;
    private int i, len;

    /**
     * Flag the type.
     */
    private enum ArrayType
    {
        ITERABLE,
        ENUMERATION,
        ARRAY
    }

    /**
     * Create an ArrayData.  Input must be an Iterable, Enumeration
     * or array.
     *
     * @param obj The source object for the array to be created.
     */
    ArrayData( Object obj )
    {
        if ( obj instanceof Iterable ){
            iterator = ((Iterable<?>)obj).iterator();
            arrayType = ArrayType.ITERABLE;
        }else if ( obj instanceof Enumeration ){
            enumeration = (Enumeration<?>)obj;
            arrayType = ArrayType.ENUMERATION;
        }else{
            // Don't know type of array, so can't
            // cast it or use Arrays.asList().
            // Use reflection.
            array = obj;
            i = 0;
            len = Array.getLength(array);
            arrayType = ArrayType.ARRAY;
        }
    }

    /**
     * Return true if this object has more elements.
     *
     * @return true if this object has more elements.
     */
    boolean hasNext()
    {
        switch ( arrayType ){
            case ITERABLE:
                return iterator.hasNext();
            case ENUMERATION:
                return enumeration.hasMoreElements();
            default:
                return i < len;
        }
    }

    /**
     * Return the next element from this object.
     *
     * @return the next element from this object.
     */
    Object next()
    {
        switch ( arrayType ){
            case ITERABLE:
                return iterator.next();
            case ENUMERATION:
                return enumeration.nextElement();
            default:
                return Array.get(array, i++);
        }
    }
}
