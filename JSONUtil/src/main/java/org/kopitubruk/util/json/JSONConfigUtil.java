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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
            ArrayList<DateFormat> cloneSrc = new ArrayList<>(src.size());
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
                    List<DateFormat> tmp = new ArrayList<>(result.size() + cloneSrc.size());
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
                result = new HashMap<>(src);
                List<Class<? extends Number>> badKeys = null;
                // clone the formats.
                for ( Entry<Class<? extends Number>,NumberFormat> entry : result.entrySet() ){
                    if ( entry.getKey() != null && entry.getValue() != null ){
                        entry.setValue((NumberFormat)entry.getValue().clone());
                    }else{
                        // a pox on anyone who causes this to happen.
                        if ( badKeys == null ){
                            badKeys = new ArrayList<>();
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
                    }else{
                        result = new HashMap<>(result);
                    }
                }
            }else{
                int size = result.size();
                for ( Entry<Class<? extends Number>,NumberFormat> entry : src.entrySet() ){
                    // only use good entries.
                    if ( entry.getKey() != null && entry.getValue() != null ){
                        result.put(entry.getKey(), (NumberFormat)entry.getValue().clone());
                    }
                }
                if ( result.size() > size ){
                    result = new HashMap<>(result);
                }
            }
        }

        return result;
    }

    /**
     * Add the given class to the given list of automatically reflected
     * classes.
     *
     * @param refClasses The current set of reflected classes.
     * @param obj An object of the types to be added from the reflect set.
     * @return The modified set of reflect classes or null if there are none.
     * @since 1.9
     */
    static Map<Class<?>,JSONReflectedClass> addReflectClass( Map<Class<?>,JSONReflectedClass> refClasses, Object obj )
    {
        return obj == null ? refClasses : JSONConfigUtil.addReflectClasses(refClasses, Arrays.asList(obj));
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
        if ( classes == null || classes.size() < 1 ){
            return refClasses;
        }

        boolean needTrim = false;
        if ( refClasses == null ){
            refClasses = new HashMap<>();
            needTrim = true;
        }

        for ( Object obj : classes ){
            if ( obj != null ){
                JSONReflectedClass refClass = ReflectUtil.ensureReflectedClass(obj);
                if ( refClass != null ){
                    Class<?> clazz = refClass.getObjClass();
                    if ( ! refClasses.containsKey(clazz) ){
                        needTrim = true;
                    }
                    refClasses.put(clazz, refClass);
                }
            }
        }

        if ( needTrim ){
            refClasses = trimClasses(refClasses);
        }

        return refClasses;
    }

    /**
     * Remove the given classes from the given list of automatically reflected
     * classes.
     *
     * @param refClasses The current set of reflected classes.
     * @param obj An object of the type to be removed from the reflect set.
     * @return The modified set of reflect classes or null if there are none left.
     */
    static Map<Class<?>,JSONReflectedClass> removeReflectClass( Map<Class<?>,JSONReflectedClass> refClasses, Object obj )
    {
        return obj == null ? refClasses : removeReflectClasses(refClasses, Arrays.asList(obj));
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
        if ( classes == null || refClasses == null || classes.size() < 1 ){
            return refClasses;
        }

        boolean needTrim = false;
        for ( Object obj : classes ){
            if ( obj != null ){
                JSONReflectedClass refClass = ReflectUtil.ensureReflectedClass(obj);
                if ( refClass != null ){
                    Class<?> clazz = refClass.getObjClass();
                    if ( refClasses.containsKey(clazz) ){
                        refClasses.remove(clazz);
                        needTrim = true;
                    }
                }
            }
        }

        if ( needTrim ){
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
        return refClasses.size() > 0 ? new HashMap<>(refClasses) : null;
    }

    /**
     * This class should never be instantiated.
     */
    private JSONConfigUtil()
    {
    }
}
