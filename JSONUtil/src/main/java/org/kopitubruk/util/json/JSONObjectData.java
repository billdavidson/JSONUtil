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
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * This trivial class wraps Maps and ResourceBundles to make them
 * behave the same in the context of JSONUtil.appendRecursiblePropertyValue()
 * to make the code in that a bit less awkward.
 *
 * @author Bill Davidson
 * @param <K> The key for the map.
 * @param <V> The value for the map.
 */
class JSONObjectData<K,V> implements Map<K,V>
{
    boolean isMap = false;
    private Map<?,?> map;
    private ResourceBundle bundle;

    /**
     * @param mapData A map or ResouceBundle to be wrapped.
     */
    public JSONObjectData( Object mapData )
    {
        isMap = mapData instanceof Map;
        if ( isMap ){
            map = (Map<?,?>)mapData;
        }else{
            bundle = (ResourceBundle)mapData;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get( Object key )
    {
        return isMap ? (V)map.get(key) : (V)bundle.getObject((String)key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<K> keySet()
    {
        return isMap ? (Set<K>)map.keySet() : (Set<K>)bundle.keySet();
    }

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
    public V put( K key, V value )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove( Object key )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll( Map<? extends K, ? extends V> m )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<V> values()
    {
        throw new UnsupportedOperationException();

    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet()
    {
        throw new UnsupportedOperationException();
    }
}
