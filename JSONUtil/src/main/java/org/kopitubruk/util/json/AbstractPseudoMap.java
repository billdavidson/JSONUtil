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
import java.util.Map;
import java.util.Set;

/**
 * Parent class for FixedPseudoMap and AbstractPseudoMap to encapsulate
 * redundant code. I tried extending AbstractMap, AbstractMap.SimpleEntry and
 * AbstractSet and they appeared to hurt performance compared to simply
 * implementing the interfaces here.
 *
 * @author Bill Davidson
 */
abstract class AbstractPseudoMap implements Map<Object,Object>
{
    public abstract int size();

    public abstract Object put( Object key, Object value );

    public abstract Set<java.util.Map.Entry<Object,Object>> entrySet();

    public abstract void clear();

    /**
     * An Entry for this Map.  Hides the type parameters which is nice
     * when creating arrays of these things.
     */
    protected class Entry implements Map.Entry<Object,Object>
    {
        private Object key;
        private Object value;

        protected Entry( Object key, Object value )
        {
            this.key = key;
            this.value = value;
        }

        public Object getKey()
        {
            return key;
        }

        public Object getValue()
        {
            return value;
        }

        public Object setValue( Object value )
        {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Entry set that only needs an iterator method.
     */
    protected abstract class EntrySet implements Set<Map.Entry<Object,Object>>
    {
        public abstract Iterator<java.util.Map.Entry<Object,Object>> iterator();

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

        public boolean add( java.util.Map.Entry<Object,Object> e )
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

        public boolean addAll( Collection<? extends java.util.Map.Entry<Object,Object>> c )
        {
            throw new UnsupportedOperationException();
        }

        public boolean retainAll( Collection<?> c )
        {
            throw new UnsupportedOperationException();
        }

        public boolean removeAll( Collection<?> c )
        {
            throw new UnsupportedOperationException();
        }

        public void clear()
        {
            throw new UnsupportedOperationException();
        }
    }

    public boolean isEmpty()
    {
        throw new UnsupportedOperationException();
    }

    public boolean containsKey( Object key )
    {
        throw new UnsupportedOperationException();
    }

    public boolean containsValue( Object value )
    {
        throw new UnsupportedOperationException();
    }

    public Object get( Object key )
    {
        throw new UnsupportedOperationException();
    }

    public Object remove( Object key )
    {
        throw new UnsupportedOperationException();
    }

    public void putAll( Map<? extends Object,? extends Object> m )
    {
        throw new UnsupportedOperationException();
    }

    public Set<Object> keySet()
    {
        throw new UnsupportedOperationException();
    }

    public Collection<Object> values()
    {
        throw new UnsupportedOperationException();
    }
}
