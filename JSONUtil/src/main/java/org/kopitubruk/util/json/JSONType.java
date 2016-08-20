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

import java.util.Enumeration;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Create and provide flags about various recursible JSON types for a given
 * property value.
 *
 * @author Bill Davidson
 * @since 1.9
 */
class JSONType
{
    private boolean isJSONAble;
    private boolean isMapType;
    private boolean isResourceBundle;
    private boolean isArrayType;
    private boolean isReflectType;

    /**
     * Create the JSONType.
     *
     * @param propertyValue The value to check.
     * @param cfg The config object (used to find reflected object types).
     */
    JSONType( Object propertyValue, JSONConfig cfg )
    {
        isJSONAble = propertyValue instanceof JSONAble;
        if ( isJSONAble ){
            isMapType = isResourceBundle = isArrayType = isReflectType = false;
        }else{
            isResourceBundle = propertyValue instanceof ResourceBundle;
            isMapType = isResourceBundle || propertyValue instanceof Map;
            if ( isMapType ){
                isArrayType = isReflectType = false;
            }else{
                Class<?> clazz = null;
                isArrayType = propertyValue instanceof Iterable ||
                              propertyValue instanceof Enumeration ||
                              (clazz = propertyValue.getClass()).isArray();
                isReflectType = isArrayType ? false : cfg.isReflectClass(clazz);
            }
        }
    }

    /**
     * Return true if the type is {@link JSONAble}
     *
     * @return true if the type is {@link JSONAble}
     */
    boolean isJSONAble()
    {
        return isJSONAble;
    }

    /**
     * Return true if the type is {@link ResourceBundle}
     *
     * @return true if the type is {@link ResourceBundle}
     */
    boolean isResourceBundle()
    {
        return isResourceBundle;
    }

    /**
     * Return true if the type is {@link Iterable}, {@link Enumeration} or an
     * array.
     *
     * @return true if the type is {@link Iterable}, {@link Enumeration} or an array.
     */
    boolean isArrayType()
    {
        return isArrayType;
    }

    /**
     * Return true if the type is {@link ResourceBundle} or {@link Map}.
     *
     * @return true if the type is {@link ResourceBundle} or {@link Map}.
     */
    boolean isMapType()
    {
        return isMapType;
    }

    /**
     * Return true if {@link JSONConfig#isReflectClass(Object)} returns true on
     * this value and this value is not one of the other types.
     *
     * @return true if {@link JSONConfig#isReflectClass(Object)} returns true on this value.
     */
    boolean isReflectType()
    {
        return isReflectType;
    }

    /**
     * Force isReflectType to true unless it's another recurisble type.
     */
    void forceReflectType()
    {
        isReflectType = ! (isJSONAble || isMapType || isArrayType);
    }

    /**
     * Return true if the value is any recursible type.
     *
     * @return true if the value is any recursible type.
     */
    boolean isRecursible()
    {
        return isJSONAble || isMapType || isArrayType || isReflectType;
    }
}
