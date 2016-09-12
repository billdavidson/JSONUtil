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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class provides a way to make an list of properties to be used to create
 * JSON objects. It's an alternative to using {@link Map}s. It works effectively
 * like using a {@link LinkedHashMap} provided you don't need it to check for
 * duplicate keys. It has less CPU and memory overhead than a {@link Map}.
 * Properties will be included in the JSON in the same order that they are added
 * to the list.
 * <p>
 * For performance, this does no checking for duplicate property names.
 * Properties are merely added to a list. If you use the fixed size constructor
 * then the list is backed by an array. If you use the other constructor then
 * the list is backed by an {@link ArrayList}.
 * <p>
 * The performance gain vs. a {@link Map} is admittedly small and difficult to
 * measure unless you are encoding objects that have large numbers of fields to
 * be serialized.
 *
 * @author Bill Davidson
 */
public class JSONObject
{
    private AbstractPseudoMap pseudoMap;

    /**
     * Create a dynamically sized JSONObject backed by an {@link ArrayList}.
     */
    public JSONObject()
    {
        pseudoMap = new DynamicPseudoMap();
    }

    /**
     * Create a fixed size JSONObject backed by an array.
     *
     * @param size The size of the property array.
     */
    public JSONObject( int size )
    {
        pseudoMap = new FixedPseudoMap(size);
    }

    /**
     * Add a property to the property list.
     *
     * @param name The name of the property.
     * @param value The value of the property.
     */
    public void addProperty( Object name, Object value )
    {
        pseudoMap.put(name, value);
    }

    /**
     * Get the number of elements in this property list.
     *
     * @return The number of elements in this property list.
     */
    public int size()
    {
        return pseudoMap.size();
    }

    /**
     * Clear the property list.
     */
    public void clear()
    {
        pseudoMap.clear();
    }

    /**
     * Get the pseudo map that this is wrapping.
     *
     * @return the map.
     */
    AbstractPseudoMap getMap()
    {
        return pseudoMap;
    }
}
