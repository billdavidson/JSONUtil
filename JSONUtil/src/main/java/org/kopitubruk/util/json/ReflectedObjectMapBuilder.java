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

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * This class builds a map of a reflected object.
 *
 * @author Bill Davidson
 */
class ReflectedObjectMapBuilder
{
    /*
     * Data specific to the object being reflected.
     */
    private Object propertyValue;
    private JSONConfig cfg;
    private JSONReflectedClass refClass = null;
    private Class<?> clazz;
    private Collection<String> fieldNameList;
    private Set<String> fieldNames;
    private FastStringCollection fnames;
    private Map<String,Field> fields;
    private Map<String,Method> getterMethods;
    private ReflectionData reflectionData;
    private Map<ReflectionData,ReflectionData> reflectionDataCache;
    private int privacyLevel;
    private boolean cacheReflectionData;
    private boolean isFieldsSpecified;
    private boolean isPrivate;

    /**
     * Create a ReflectedObjectBuilder for the given object.
     *
     * @param propertyValue The object to be reflected.
     * @param cfg the config object.
     */
    ReflectedObjectMapBuilder( Object propertyValue, JSONConfig cfg )
    {
        this.propertyValue = propertyValue;
        this.cfg = cfg;
    }

    /**
     * Use reflection to build a map of the properties of the given object
     *
     * @return A map representing the object's fields.
     */
    Map<Object,Object> buildReflectedObjectMap()
    {
        String name = "buildReflectedObjectMap()";

        try {
            Map<Object,Object> obj;

            if ( reflectionData == null ){
                List<Member> attributeList = null;
                List<String> nameList = null;
                obj = new DynamicPseudoMap();
                if ( cacheReflectionData ){
                    attributeList = new ArrayList<Member>();
                    nameList = new ArrayList<String>();
                }

                // populate the object map.
                for ( String fieldName : fieldNameList ){
                    name = refClass.getFieldAlias(fieldName);
                    Field field = fields.get(fieldName);
                    // prefer getter if possible.
                    Method getter = getGetter(field, fieldName);
                    if ( getter != null ){
                        ReflectUtil.ensureAccessible(getter);
                        obj.put(name, getter.invoke(propertyValue));
                        if ( cacheReflectionData ){
                            nameList.add(name);
                            attributeList.add(getter);
                        }
                    }else if ( field != null && isVisible(field) ){
                        // no valid getter.  go with direct access.
                        ReflectUtil.ensureAccessible(field);
                        obj.put(name, field.get(propertyValue));
                        if ( cacheReflectionData ){
                            nameList.add(name);
                            attributeList.add(field);
                        }
                    }else if ( isFieldsSpecified ){
                        // field name specified but field/pseudo-field does not exist.
                        throw new JSONReflectionException(propertyValue, fieldName, cfg);
                    }
                }

                if ( cacheReflectionData ){
                    addReflectionData(attributeList, nameList);
                }
            }else{
                // Use cached reflection data for better performance.
                String[] names = reflectionData.getNames();
                Member[] attributes = reflectionData.getAttributes();
                obj = new FixedPseudoMap(attributes.length);

                // populate the object map.
                for ( int i = 0; i < attributes.length; i++ ){
                    Member attribute = attributes[i];
                    name = names[i];
                    if ( attribute instanceof Method ){
                        obj.put(name, ((Method)attribute).invoke(propertyValue));
                    }else{
                        obj.put(name, ((Field)attribute).get(propertyValue));
                    }
                }
            }
            return obj;
        }catch ( JSONReflectionException e ){
            throw e;
        }catch ( Exception e ){
            throw new JSONReflectionException(propertyValue, name, e, cfg);
        }
    }

    /**
     * Initialize data for the reflection.
     */
    void init()
    {
        try {
            refClass = cfg.ensureReflectedClass(propertyValue);
            clazz = refClass.getObjClass();
            fieldNames = refClass.getFieldNamesRaw();
            isFieldsSpecified = fieldNames != null;
            privacyLevel = isFieldsSpecified ? ReflectUtil.PRIVATE : cfg.getReflectionPrivacy();
            cacheReflectionData = cfg.isCacheReflectionData();
            if ( cacheReflectionData ){
                fnames = isFieldsSpecified ? new FastStringCollection(fieldNames) : null;
                TreeMap<String,String> fieldAliases = refClass.getFieldAliasesTreeMap();
                reflectionDataCache = getReflectionDataCache();
                reflectionData = reflectionDataCache.get(new ReflectionData(clazz, privacyLevel, fnames, fieldAliases));
            }else{
                reflectionData = null;
            }
            if ( reflectionData == null ){
                // data needed if caching is disabled or hasn't happened yet.
                isPrivate = privacyLevel == ReflectUtil.PRIVATE;
                if ( isFieldsSpecified ){
                    initSpecifiedFields();
                }else{
                    initFields();
                }
                initGetterMethods();
            }
        }catch ( Exception e ){
            throw new JSONReflectionException(propertyValue, "init()", e, cfg);
        }
    }

    /**
     * Initialize the map of the specified fields for the class.
     */
    private void initSpecifiedFields()
    {
        Set<String> allFieldNames = new HashSet<String>();
        fields = new HashMap<String, Field>(fieldNames.size());
        fieldNameList = cacheReflectionData ? fnames : fieldNames;

        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Field field : tmpClass.getDeclaredFields() ){
                String name = field.getName();
                if ( ! allFieldNames.contains(name) ){
                    allFieldNames.add(name);
                    if ( fieldNames.contains(name) ){
                        // only get the fields that were specified.
                        fields.put(name, field);
                    }
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }
    }

    /**
     * Initialize the map of the fields for the class and fieldNames.
     */
    private void initFields()
    {
        Set<String> allFieldNames = new HashSet<String>();
        fieldNameList = new ArrayList<String>();
        fields = new HashMap<String,Field>();

        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Field field : tmpClass.getDeclaredFields() ){
                String name = field.getName();
                if ( ! allFieldNames.contains(name) ){
                    allFieldNames.add(name);
                    if ( ReflectUtil.isSerializable(field) ){
                        fieldNameList.add(name);
                        fields.put(name, field);    // direct access or check against getter return type.
                    }
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }
    }

    /**
     * Initialize the map of getter methods for the class.
     */
    private void initGetterMethods()
    {
        getterMethods = new HashMap<String,Method>();

        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Method method : tmpClass.getDeclaredMethods() ){
                if ( method.getParameterTypes().length != 0 ){
                    continue;   // no parameters in bean getter.
                }
                Class<?> retType = method.getReturnType();
                if ( Void.TYPE == retType ){
                    continue;   // getters must return something.
                }
                String name = method.getName();
                if ( getterMethods.containsKey(name) ){
                    continue;
                }
                if ( ReflectUtil.isGetterName(name, retType) && isVisible(method) ){
                    getterMethods.put(name, method);
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }
    }

    /**
     * Get the getter for the given field.
     *
     * @param field The field
     * @param name The field name.
     * @return The getter or null if one cannot be found.
     */
    private Method getGetter( Field field, String name )
    {
        Method getter = getterMethods.get(ReflectUtil.makeBeanMethodName(name,"get"));
        if ( getter == null ){
            getter = getterMethods.get(ReflectUtil.makeBeanMethodName(name,"is"));
        }
        if ( field == null || getter == null ){
            return getter;
        }else{
            return ReflectUtil.isCompatibleInJSON(field, getter) ? getter : null;
        }
    }

    /**
     * Return true if the given field or method is visible with the current
     * privacy level.
     *
     * @param member the field or method.
     * @return true if the field or method is visible.
     */
    private boolean isVisible( Member member )
    {
        return isPrivate || ReflectUtil.getPrivacyLevel(member.getModifiers()) >= privacyLevel;
    }

    /**
     * Add reflection data to the cache.
     *
     * @param attributeList The list of reflected attributes.
     * @param nameList The list of names.
     */
    private void addReflectionData( List<Member> attributeList, List<String> nameList )
    {
        FastStringCollection cachedFieldNames = isFieldsSpecified ? fnames : null;
        TreeMap<String,String> fieldAliases = refClass.getFieldAliasesTreeMap();
        if ( fieldAliases != null ){
            fieldAliases = new TreeMap<String,String>(fieldAliases); // make sure it can't be changed.
        }
        String[] names = nameList.toArray(new String[nameList.size()]);
        Member[] attributes = attributeList.toArray(new Member[attributeList.size()]);

        reflectionData = new ReflectionData(clazz, privacyLevel, cachedFieldNames, fieldAliases, names, attributes);
        reflectionDataCache.put(reflectionData, reflectionData);
    }

    /*
     * static cache for reflection information.
     */
    private static volatile Map<ReflectionData,ReflectionData> REFLECTION_DATA_CACHE;

    /**
     * Clear the reflection cache.
     */
    static synchronized void clearReflectionCache()
    {
        REFLECTION_DATA_CACHE = null;
    }

    /**
     * Get the reflection data cache.
     *
     * @return reflection data cache.
     */
    private static synchronized Map<ReflectionData,ReflectionData> getReflectionDataCache()
    {
        if ( REFLECTION_DATA_CACHE == null ){
            REFLECTION_DATA_CACHE = new Hashtable<ReflectionData,ReflectionData>(0);
        }
        return REFLECTION_DATA_CACHE;
    }

    static {
        clearReflectionCache();
    }
}
