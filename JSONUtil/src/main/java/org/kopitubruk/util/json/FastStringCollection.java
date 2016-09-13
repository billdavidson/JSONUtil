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

import java.util.Collection;
import java.util.Iterator;

/**
 * Minimal immutable collection of Strings that provides iteration, hashCode()
 * and equals(). For maximum performance, there is no bounds checking or null
 * checking. It is only used by the reflection data caching mechanism for field
 * names which never sends it nulls or empty collections. It is designed to
 * facilitate fast hash lookups with its equals() and hashCode() and the fastest
 * possible iteration.
 *
 * @author Bill Davidson
 */
class FastStringCollection implements Collection<String>
{
    private final String[] array;
    private final int length;

    /**
     * Make a FastStringCollection
     *
     * @param fieldNames The collection to turn into a list.
     */
    FastStringCollection( Collection<String> fieldNames )
    {
        array = fieldNames.toArray(new String[fieldNames.size()]);
        length = array.length;
    }

    public Iterator<String> iterator()
    {
        return new Iterator<String>()
               {
                   private int i = 0;

                   public boolean hasNext()
                   {
                       return i < length;
                   }

                   public String next()
                   {
                       return array[i++];
                   }

                   public void remove()
                   {
                       throw new UnsupportedOperationException();
                   }
               };
    }

    public int hashCode()
    {
        int result = 1;

        for ( int i = 0; i < length; i++ ){
            result = 31 * result + array[i].hashCode();
        }

        return result;
    }

    public boolean equals( Object obj )
    {
        if ( obj == null ){
            return false;
        }

        FastStringCollection other = (FastStringCollection)obj;
        if ( length != other.length ){
            return false;
        }
        for ( int i = 0; i < length; i++ ){
            if ( ! array[i].equals(other.array[i]) ){
                return false;
            }
        }
        return true;
    }

    public int size()
    {
        throw new UnsupportedOperationException();
    }

    public boolean isEmpty()
    {
        throw new UnsupportedOperationException();
    }

    public boolean contains( Object o )
    {
        throw new UnsupportedOperationException();
    }

    public Object[] toArray()
    {
        throw new UnsupportedOperationException();
    }

    public <T> T[] toArray( T[] a )
    {
        throw new UnsupportedOperationException();
    }

    public boolean add( String e )
    {
        throw new UnsupportedOperationException();
    }

    public boolean remove( Object o )
    {
        throw new UnsupportedOperationException();
    }

    public boolean containsAll( Collection<?> c )
    {
        throw new UnsupportedOperationException();
    }

    public boolean addAll( Collection<? extends String> c )
    {
        throw new UnsupportedOperationException();
    }

    public boolean removeAll( Collection<?> c )
    {
        throw new UnsupportedOperationException();
    }

    public boolean retainAll( Collection<?> c )
    {
        throw new UnsupportedOperationException();
    }

    public void clear()
    {
        throw new UnsupportedOperationException();
    }
}