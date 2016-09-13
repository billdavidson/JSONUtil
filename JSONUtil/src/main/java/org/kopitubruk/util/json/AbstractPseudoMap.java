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
    @Override
    public abstract int size();

    @Override
    public abstract Object put( Object key, Object value );

    @Override
    public abstract Set<java.util.Map.Entry<Object,Object>> entrySet();

    @Override
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

        @Override
        public Object getKey()
        {
            return key;
        }

        @Override
        public Object getValue()
        {
            return value;
        }

        @Override
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
        @Override
        public abstract Iterator<java.util.Map.Entry<Object,Object>> iterator();

        @Override
        public int size()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEmpty()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean contains( Object o )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object[] toArray()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T[] toArray( T[] a )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean add( java.util.Map.Entry<Object,Object> e )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove( Object o )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll( Collection<?> c )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll( Collection<? extends java.util.Map.Entry<Object,Object>> c )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll( Collection<?> c )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll( Collection<?> c )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear()
        {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean isEmpty()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsKey( Object key )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsValue( Object value )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object get( Object key )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object remove( Object key )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll( Map<? extends Object,? extends Object> m )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Object> keySet()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<Object> values()
    {
        throw new UnsupportedOperationException();
    }
}
