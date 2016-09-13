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

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Package private utility methods for JSONConfig/JSONConfigDefaults that don't
 * need access to static or instance data.
 *
 * @author Bill Davidson
 */
class JSONConfigUtil
{
    /**
     * Add the source collection of formats to the destination list of formats.
     *
     * @param dest The destination list.
     * @param src The source list.
     * @return The new list of formats.
     */
    static List<DateFormat> addDateParseFormats( List<DateFormat> dest, Collection<? extends DateFormat> src )
    {
        List<DateFormat> result = dest;

        if ( src != null ){
            ArrayList<DateFormat> cloneSrc = new ArrayList<DateFormat>(src.size());
            for ( DateFormat fmt : src ){
                if ( fmt != null ){
                    // clone because DateFormat's are not thread safe.
                    cloneSrc.add((DateFormat)fmt.clone());
                }
            }

            if ( cloneSrc.size() > 0 ){
                if ( result == null ){
                    // adjust size if there were nulls.
                    cloneSrc.trimToSize();
                    result = cloneSrc;
                }else{
                    List<DateFormat> tmp = new ArrayList<DateFormat>(result.size() + cloneSrc.size());
                    tmp.addAll(result);
                    tmp.addAll(cloneSrc);
                    result = tmp;
                }
            }
        }

        return result;
    }

    /**
     * Merge two maps of number formats and clone the formats of the source map
     * as they are merged into the destination map.
     *
     * @param dest The destination map to be added to.
     * @param src The source map to add.
     * @return The merged map.
     */
    static Map<Class<? extends Number>,NumberFormat> mergeFormatMaps(
                                Map<Class<? extends Number>,NumberFormat> dest,
                                Map<Class<? extends Number>,NumberFormat> src )
    {
        Map<Class<? extends Number>,NumberFormat> result = dest;

        if ( src != null ){
            if ( result == null ){
                result = new HashMap<Class<? extends Number>,NumberFormat>(src);
                int tableSize = tableSizeFor(result.size());
                List<Class<? extends Number>> badKeys = null;
                // clone the formats.
                for ( Entry<Class<? extends Number>,NumberFormat> entry : result.entrySet() ){
                    if ( entry.getKey() != null && entry.getValue() != null ){
                        entry.setValue((NumberFormat)entry.getValue().clone());
                    }else{
                        // a pox on anyone who causes this to happen.
                        if ( badKeys == null ){
                            badKeys = new ArrayList<Class<? extends Number>>();
                        }
                        badKeys.add(entry.getKey());
                    }
                }
                if ( badKeys != null ){
                    // clean out the bad keys.
                    for ( Class<? extends Number> numericClass : badKeys ){
                        result.remove(numericClass);
                    }
                    if ( result.size() < 1 ){
                        result = null;
                    }else if ( tableSize > tableSizeFor(result.size()) ){
                        result = new HashMap<Class<? extends Number>,NumberFormat>(result);
                    }
                }
            }else{
                for ( Entry<Class<? extends Number>,NumberFormat> entry : src.entrySet() ){
                    // only use good entries.
                    if ( entry.getKey() != null && entry.getValue() != null ){
                        result.put(entry.getKey(), (NumberFormat)entry.getValue().clone());
                    }
                }
            }
        }

        return result;
    }

    /**
     * Add the given class to the given list of automatically reflected
     * classes.  If obj is an array, {@link Iterable} or {@link Enumeration},
     * then the classes represented by the elements in it will be added.
     *
     * @param refClasses The current set of reflected classes.
     * @param obj An object of the types to be added from the reflect set.
     * @return The modified set of reflect classes or null if there are none.
     * @since 1.9
     */
    static Map<Class<?>,JSONReflectedClass> addReflectClass( Map<Class<?>,JSONReflectedClass> refClasses, Object obj )
    {
        return addReflectClassesSafe(refClasses, getReflectClassCollection(obj));
    }

    /**
     * Add the given classes to the given list of automatically reflected classes.
     *
     * @param refClasses The current set of reflected classes.
     * @param classes A collection of objects of the types to be added from the reflect set.
     * @return The modified set of reflect classes or null if there are none.
     * @since 1.9
     */
    static Map<Class<?>,JSONReflectedClass> addReflectClasses( Map<Class<?>,JSONReflectedClass> refClasses, Collection<?> classes )
    {
        return addReflectClassesSafe(refClasses, getReflectClassCollection(classes));
    }

    /**
     * Add the given classes to the given list of automatically reflected classes.
     *
     * @param refClasses The current set of reflected classes.
     * @param classes A collection of objects of the types to be added from the reflect set.
     * @return The modified set of reflect classes or null if there are none.
     * @since 1.9
     */
    private static Map<Class<?>,JSONReflectedClass> addReflectClassesSafe( Map<Class<?>,JSONReflectedClass> refClasses, Collection<?> classes )
    {
        if ( classes.size() < 1 ){
            return refClasses;
        }

        int tableSize;
        if ( refClasses == null ){
            refClasses = new HashMap<Class<?>,JSONReflectedClass>();
            tableSize = DEFAULT_INITIAL_CAPACITY;
        }else{
            tableSize = tableSizeFor(refClasses.size());
        }

        for ( Object obj : classes ){
            JSONReflectedClass refClass = ReflectUtil.ensureReflectedClass(obj);
            refClasses.put(refClass.getObjClass(), refClass);
        }

        if ( tableSize > tableSizeFor(refClasses.size()) ){
            refClasses = trimClasses(refClasses);
        }

        return refClasses;
    }

    /**
     * Remove the given classes from the given list of automatically reflected
     * classes.  If obj is an array, {@link Iterable} or {@link Enumeration},
     * then the classes represented by the elements in it will be removed.
     *
     * @param refClasses The current set of reflected classes.
     * @param obj An object of the type to be removed from the reflect set.
     * @return The modified set of reflect classes or null if there are none left.
     */
    static Map<Class<?>,JSONReflectedClass> removeReflectClass( Map<Class<?>,JSONReflectedClass> refClasses, Object obj )
    {
        return removeReflectClassesSafe(refClasses, getReflectClassCollection(obj));
    }

    /**
     * Remove the given classes from the given list of automatically reflected
     * classes.
     *
     * @param refClasses The current set of reflected classes.
     * @param classes A collection objects of the types to be removed from the reflect set.
     * @return The modified set of reflect classes or null if there are none left.
     * @since 1.9
     */
    static Map<Class<?>,JSONReflectedClass> removeReflectClasses( Map<Class<?>,JSONReflectedClass> refClasses, Collection<?> classes )
    {
        return removeReflectClassesSafe(refClasses, getReflectClassCollection(classes));
    }

    /**
     * Remove the given classes from the given list of automatically reflected
     * classes.
     *
     * @param refClasses The current set of reflected classes.
     * @param classes A collection objects of the types to be removed from the reflect set.
     * @return The modified set of reflect classes or null if there are none left.
     */
    private static Map<Class<?>,JSONReflectedClass> removeReflectClassesSafe( Map<Class<?>,JSONReflectedClass> refClasses, Collection<?> classes )
    {
        if ( refClasses == null || classes.size() < 1 || refClasses.size() < 1 ){
            return refClasses;
        }

        int tableSize = tableSizeFor(refClasses.size());
        for ( Object obj : classes ){
            refClasses.remove(ReflectUtil.getClass(obj));
        }

        if ( tableSize > tableSizeFor(refClasses.size()) ){
            refClasses = trimClasses(refClasses);
        }

        return refClasses;
    }

    /**
     * Trim the given set of classes down to size.
     *
     * @param refClasses The set to trim.
     * @return The trimmed set or null if empty.
     * @since 1.9
     */
    private static Map<Class<?>,JSONReflectedClass> trimClasses( Map<Class<?>,JSONReflectedClass> refClasses )
    {
        return refClasses.size() > 0 ? new HashMap<Class<?>, JSONReflectedClass>(refClasses) : null;
    }

    /**
     * If the given object is an array, {@link Iterable} or {@link Enumeration},
     * then make it into a collection with any nested collections or arrays
     * flattened out and return it. Otherwise, create a List with just the one
     * element and return it.
     *
     * @param obj the object.
     * @return the list.
     */
    private static Collection<?> getReflectClassCollection( Object obj )
    {
        if ( obj == null ){
            return new ArrayList<Object>(0);
        }else if ( obj instanceof Class || obj instanceof JSONReflectedClass ){
            return Arrays.asList(obj);
        }else if ( isArrayType(obj) ){
            Set<Object> objs = new LinkedHashSet<Object>();
            for ( Object element : new JSONArrayData(obj) ){
                if ( element != null ){
                    if ( element instanceof Class || element instanceof JSONReflectedClass ){
                        objs.add(element);
                    }else if ( isArrayType(element) ){
                        objs.addAll(getReflectClassCollection(element));
                    }else{
                        objs.add(ReflectUtil.getClass(element));
                    }
                }
            }
            return objs;
        }else{
            @SuppressWarnings("unchecked")
            List<?> list = Arrays.asList(ReflectUtil.getClass(obj));
            return list;
        }
    }

    /**
     * Return true of the given object is an array type.
     *
     * @param obj the object to check
     * @return true if it's an array type.
     */
    private static boolean isArrayType( Object obj )
    {
        return obj instanceof Iterable || obj instanceof Enumeration || obj.getClass().isArray();
    }

    /**
     * Get the HashMap table size for the given map size.
     *
     * @param size the map size.
     * @return the size of the table it needs.
     */
    static int tableSizeFor( int size )
    {
        if ( size > 0 ){
            int tableSize = 1;
            while ( tableSize < size ){
                tableSize <<= 1;
            }
            return tableSize;
        }else{
            return 0;
        }
    }

    /**
     * Default initial capacity for a HashMap.
     */
    static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * This class should never be instantiated.
     */
    private JSONConfigUtil()
    {
    }
}
