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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * This class implements a fake map of fixed size that doesn't do any real
 * mapping. Its purpose is to provide an ordered collection of key-value pairs
 * via the entrySet() method so that it can be used to build JSON objects using
 * the same code that builds them from real maps. It is meant to be faster than
 * doing normal put and iteration operations and to save memory compared to
 * normal maps. The gains tend to be effectively marginal but they are
 * measurable with objects that have large numbers of fields to be serialized.
 *
 * @author Bill Davidson
 */
class FixedPseudoMap extends AbstractPseudoMap
{
    private final Map.Entry<Object,Object>[] entries;
    private int size;

    /**
     * Make a fixed size pseudo map.
     *
     * @param mapSize the size to make it.
     */
    FixedPseudoMap( int mapSize )
    {
        entries = new Entry[mapSize];
        size = 0;
    }

    /**
     * Create an entry for this key-value pair and add it to the list.
     *
     * @param key The key
     * @param value The value
     * @return null because this isn't a real map.
     */
    @Override
    public Object put( Object key, Object value )
    {
        entries[size++] = new Entry(key, value);
        return null;
    }

    /**
     * Get the entry set. If this map is dynamic, then it will be converted to
     * fixed and no more put operations will be allowed.
     *
     * @return The entry set.
     */
    @Override
    public Set<Map.Entry<Object,Object>> entrySet()
    {
        return new EntrySet();
    }

    /**
     * Get the number of entries in this pseudo map.
     *
     * @return the number of entries in this pseudo map.
     */
    @Override
    public int size()
    {
        return size;
    }

    /**
     * Empty the list.
     */
    @Override
    public void clear()
    {
        size = 0;
    }

    /**
     * An entry set for this pseudo map.
     */
    private class EntrySet extends AbstractPseudoMap.EntrySet
    {
        @Override
        public Iterator<Map.Entry<Object,Object>> iterator()
        {
            return new Iterator<Map.Entry<Object,Object>>()
                       {
                           private int i = 0;

                           public boolean hasNext()
                           {
                               return i < size;
                           }

                           public Map.Entry<Object,Object> next()
                           {
                               return entries[i++];
                           }

                           public void remove()
                           {
                               throw new UnsupportedOperationException();
                           }
                       };
        }
    }
}
