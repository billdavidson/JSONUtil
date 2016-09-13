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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class provides a way to make an list of properties to be used to create
 * JSON objects. It's an alternative to using {@link Map}s. It works effectively
 * like using a {@link LinkedHashMap} provided you don't need it to check for
 * duplicate keys.
 * <p>
 * You add properties as key-value pairs using the {@link #add(Object,Object)}
 * method which returns this JsonObject so that you can do adds in series for
 * convenience.
 * <p>
 * This class implements {@link JSONAble} and so has all of its toJSON methods
 * available for convenience.
 * <p>
 * For performance, this object does no checking for duplicate property names.
 * Properties are merely added to a list. If you use the fixed size constructor
 * then the list is backed by an array. If you use the other constructor then
 * the list is backed by an {@link ArrayList}. With the fixed size version there
 * is no size checking so if you try to add more properties than you the size
 * you gave to the constructor, then you will get an
 * {@link ArrayIndexOutOfBoundsException}.
 * <p>
 * The performance gain vs. a {@link Map} is admittedly small and difficult to
 * measure unless you are encoding objects that have large numbers of fields to
 * be serialized.
 * <p>
 * The fixed size version does save memory if you size it with the exact number
 * of properties that you need to store because the only storage used is the
 * array, the number of items added so far and the key value pairs whereas
 * {@link Map}s tend to have a fair amount of extra storage to facilitate faster
 * lookups. Even the dynamically sized version may save memory depending upon
 * how much extra space you end up with in the {@link ArrayList}.
 *
 * @author Bill Davidson
 */
public class JsonObject implements JSONAble
{
    /**
     * Pseudo map that's backing this JsonObject
     */
    private AbstractPseudoMap pseudoMap;

    /**
     * Create a dynamically sized JSONObject backed by an {@link ArrayList}.
     */
    public JsonObject()
    {
        pseudoMap = new DynamicPseudoMap();
    }

    /**
     * Create a fixed size JSONObject backed by an array.
     *
     * @param size The size of the property array.
     */
    public JsonObject( int size )
    {
        pseudoMap = new FixedPseudoMap(size);
    }

    /**
     * Add a property to the property list.
     *
     * @param name The name of the property.
     * @param value The value of the property.
     * @return This JsonObject allowing you to do adds in series.
     */
    public JsonObject add( Object name, Object value )
    {
        pseudoMap.put(name, value);
        return this;
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
     * Return the JSON encoding of this object using the default configuration
     * options.
     *
     * @return the JSON encoding of this object.
     */
    @Override
    public String toString()
    {
        return JSONUtil.toJSON(pseudoMap);
    }

    /**
     * Return the JSON encoding of this object using the default configuration
     * options.
     */
    @Override
    public String toJSON()
    {
        return JSONUtil.toJSON(pseudoMap);
    }

    /**
     * Return the JSON encoding of this object using the given configuration
     * options.
     *
     * @param jsonConfig A configuration object to use to optionally set encoding options.
     */
    @Override
    public String toJSON( JSONConfig jsonConfig )
    {
        return JSONUtil.toJSON(pseudoMap, jsonConfig);
    }

    /**
     * Write this JSON encoding of this object to the given {@link Writer} using
     * the given configuration options.
     *
     * @param json A writer for the output.
     * @throws IOException If there is an error on output.
     */
    @Override
    public void toJSON( Writer json ) throws IOException
    {
        JSONUtil.toJSON(pseudoMap, json);
    }

    /**
     * Write this JSON encoding of this object to the given {@link Writer} using
     * the given configuration options.
     *
     * @param jsonConfig A configuration object to use to optionally set encoding options.
     * @param json A writer for the output.
     * @throws IOException If there is an error on output.
     */
    @Override
    public void toJSON( JSONConfig jsonConfig, Writer json ) throws IOException
    {
        JSONUtil.toJSON(pseudoMap, jsonConfig, json);
    }
}
