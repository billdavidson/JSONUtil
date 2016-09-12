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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Some reflection utility constants to be used with
 * {@link JSONConfig#setReflectionPrivacy(int)} and
 * {@link JSONConfigDefaults#setReflectionPrivacy(int)}
 *
 * @author Bill Davidson
 * @since 1.9
 */
public class ReflectUtil
{
    /**
     * This needs to be saved at class load time so that the correct class
     * loader is used if someone tries to load a class via a JMX client.
     */
    private static ClassLoader classLoader = ReflectUtil.class.getClassLoader();

    /**
     * Reflection will attempt to serialize all fields including private.  Value is 0.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PRIVATE = 0;

    /**
     * Reflection will attempt to serialize package private, protected and
     * public fields or fields that have package private, protected or public
     * get methods that conform to JavaBean naming conventions.    Value is 1.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PACKAGE = 1;

    /**
     * Reflection will attempt to serialize protected and public fields or
     * fields that have protected or public get methods that conform to JavaBean
     * naming conventions.  Value is 2.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PROTECTED = 2;

    /**
     * Reflection will attempt to serialize only fields that are public or have
     * public get methods that conform to JavaBean naming conventions.  Value is 3.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PUBLIC = 3;

    /**
     * The minimum value for privacy level.
     */
    private static final int MIN_PRIVACY_LEVEL = PRIVATE;

    /**
     * The maximum value for privacy level.
     */
    private static final int MAX_PRIVACY_LEVEL = PUBLIC;

    /**
     * Getter name pattern.
     */
    private static final Pattern GETTER_PAT = Pattern.compile("^(get|is)\\p{Lu}.*$");

    /**
     * Primitive number types and the number class which includes all number
     * wrappers and BigDecimal and BigInteger.
     */
    private static final Class<?>[] NUMBERS =
            { Number.class, Integer.TYPE, Double.TYPE, Long.TYPE, Float.TYPE, Short.TYPE, Byte.TYPE };

    /**
     * Boolean types.
     */
    static final Class<?>[] BOOLEANS = { Boolean.class, Boolean.TYPE };

    /**
     * String types.
     */
    private static final Class<?>[] STRINGS = { CharSequence.class, Character.class, Character.TYPE };

    /**
     * Types that become arrays in JSON.
     */
    private static final Class<?>[] ARRAY_TYPES = { Iterable.class, Enumeration.class };

    /**
     * Types that become maps/objects in JSON.
     */
    private static final Class<?>[] MAP_TYPES = { Map.class, ResourceBundle.class, JSONObject.class };

    /**
     * Get the class of the given object or the object if it's a class object.
     *
     * @param obj The object
     * @return The object's class.
     * @since 1.9
     */
    static Class<?> getClass( Object obj )
    {
        if ( obj == null ){
            throw new JSONReflectionException();
        }
        Class<?> result;
        if ( obj instanceof Class ){
            result = (Class<?>)obj;
        }else if ( obj instanceof JSONReflectedClass ){
            result = ((JSONReflectedClass)obj).getObjClass();
        }else{
            result = obj.getClass();
        }
        return result;
    }

    /**
     * Get the {@link JSONReflectedClass} version of this object class.
     *
     * @param obj The object.
     * @return the {@link JSONReflectedClass} version of this object class.
     */
    static JSONReflectedClass ensureReflectedClass( Object obj )
    {
        if ( obj instanceof JSONReflectedClass ){
             return (JSONReflectedClass)obj;
        }else if ( obj != null ){
            return new JSONReflectedClass(getClass(obj));
        }else{
            return null;
        }
    }

    /**
     * Get the class object for the given class name.
     *
     * @param className The name of the class.
     * @return The class object for that class.
     * @throws ClassNotFoundException If there's an error loading the class.
     * @since 1.9
     */
    static Class<?> getClassByName( String className ) throws ClassNotFoundException
    {
        return classLoader.loadClass(className);
    }

    /**
     * Check that the given privacy level is valid.
     *
     * @param privacyLevel The privacy level to check.
     * @param cfg The config for the exception.
     * @return The value if valid.
     * @throws JSONReflectionException if the privacyLevel is invalid.
     */
    static int confirmPrivacyLevel( int privacyLevel, JSONConfig cfg ) throws JSONReflectionException
    {
        if ( privacyLevel >= MIN_PRIVACY_LEVEL && privacyLevel <= MAX_PRIVACY_LEVEL ){
            return privacyLevel;
        }else{
            throw new JSONReflectionException(privacyLevel, cfg);
        }
    }

    /**
     * Get the privacy level for reflection.
     *
     * @param modifiers The reflection modifiers.
     * @return The privacy level.
     */
    static int getPrivacyLevel( int modifiers )
    {
        if ( Modifier.isPrivate(modifiers) ){
            return PRIVATE;
        }else if ( Modifier.isProtected(modifiers) ){
            return PROTECTED;
        }else if ( Modifier.isPublic(modifiers) ){
            return PUBLIC;
        }else{
            return PACKAGE;
        }
    }

    /**
     * Get all of the instance fields for a given class that match the given
     * type.
     *
     * @param clazz The class.
     * @param type The type to match.
     * @return The fields.
     */
    static Map<String,Field> getFields( Class<?> clazz, Class<?> type )
    {
        // build a map of the object's properties.
        Map<String,Field> fields = new HashMap<>();

        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Field field : tmpClass.getDeclaredFields() ){
                int modifiers = field.getModifiers();
                if ( Modifier.isTransient(modifiers) ){
                    continue;       // ignore transient fields.
                }
                if ( type.equals(field.getType()) ){
                    String name = field.getName();
                    if ( ! fields.containsKey(name) ){
                        fields.put(name, field);
                    }
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }

        return fields;
    }

    /**
     * Get the name of the setter for the given field of the given class.
     *
     * @param clazz The class.
     * @param field The field.
     * @return The setter or null if there isn't one.
     */
    static Method getSetter( Class<?> clazz, Field field )
    {
        String fieldName = field.getName();
        String setterName = makeBeanMethodName(fieldName,"set");

        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Method method : tmpClass.getDeclaredMethods() ){
                if ( setterName.equals(method.getName()) && method.getParameterCount() == 1 ){
                    return method;
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }

        return null;
    }

    /**
     * Make a bean method name.
     *
     * @param fieldName the name of the field.
     * @param prefix The prefix for the bean method name.
     * @return The bean method name.
     */
    static String makeBeanMethodName( String fieldName, String prefix )
    {
        int len = fieldName.length();
        StringBuilder buf = new StringBuilder(len+prefix.length());
        buf.append(prefix);
        int codePoint = fieldName.codePointAt(0);
        int charCount = Character.charCount(codePoint);
        if ( Character.isLowerCase(codePoint) ){
            codePoint = Character.toUpperCase(codePoint);
        }
        buf.appendCodePoint(codePoint);
        if ( len > charCount ){
            buf.append(fieldName.substring(charCount));
        }
        return buf.toString();
    }

    /**
     * Return true if the name looks like a getter.
     *
     * @param name the name
     * @param retType the return type for checking "is" getters.
     * @return true if it looks like a getter.
     */
    static boolean isGetterName( String name, Class<?> retType )
    {
        if ( GETTER_PAT.matcher(name).matches() ){
            if ( name.startsWith("is") ){
                // "is" prefix only valid getter for booleans.
                return isType(BOOLEANS, retType);
            }
            return true;
        }else{
            return false;
        }
    }

    /**
     * Return true if it's OK to serialize this field.
     *
     * @param field the field.
     * @return true if it's OK to serialize this field.
     */
    static boolean isSerializable( Field field )
    {
        int modifiers = field.getModifiers();
        if ( Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) ){
            return false;
        }
        return true;
    }

    /**
     * Make sure that the given object is accessible.
     *
     * @param obj the object
     */
    static void ensureAccessible( AccessibleObject obj )
    {
        if ( ! obj.isAccessible() ){
            obj.setAccessible(true);
        }
    }

    /**
     * Return true if the type returned by the method is compatible in JSON
     * with the type of the field.
     *
     * @param field The field.
     * @param method The method to check the return type of.
     * @return true if they are compatible in JSON.
     */
    static boolean isCompatibleInJSON( Field field, Method method )
    {
        Class<?> fieldType = field.getType();
        Class<?> methodType = method.getReturnType();

        if ( fieldType == methodType ){
            return true;    // can't get more compatible than the exact same type.
        }else{
            Class<?>[] methodTypes = getTypes(methodType);

            if ( isType(methodTypes, fieldType) ){
                // fieldType is a super class or interface of methodType
                return true;
            }

            // check for JSON level compatibility, which is much looser.
            Class<?>[] fieldTypes = getTypes(fieldType);
            Class<?>[] t1, t2;
            // check the shorter list first.
            if ( fieldTypes.length < methodTypes.length ){
                t1 = fieldTypes;  t2 = methodTypes;
            }else{
                t1 = methodTypes; t2 = fieldTypes;
            }

            if ( isJSONNumber(t1) )       return isJSONNumber(t2);
            else if ( isJSONString(t1) )  return isJSONString(t2);
            else if ( isJSONBoolean(t1) ) return isJSONBoolean(t2);
            else if ( isJSONArray(t1) )   return isJSONArray(t2);
            else if ( isJSONMap(t1) )     return isJSONMap(t2);
            else return false;
        }
    }

    /**
     * Return true if the given type is a {@link Number} type.
     *
     * @param objTypes the type to check.
     * @return true if the given type is a {@link Number} type.
     */
    private static boolean isJSONNumber( Class<?>[] objTypes )
    {
        return isType(objTypes, NUMBERS);
    }

    /**
     * Return true if the given type is a {@link Boolean} type.
     *
     * @param objTypes the type to check.
     * @return true if the given type is a {@link Boolean} type.
     */
    private static boolean isJSONBoolean( Class<?>[] objTypes )
    {
        return isType(objTypes, BOOLEANS);
    }

    /**
     * Return true if the given type is a {@link CharSequence} type.
     *
     * @param objTypes the type to check.
     * @return true if the given type is a {@link CharSequence} type.
     */
    private static boolean isJSONString( Class<?>[] objTypes )
    {
        return isType(objTypes, STRINGS);
    }

    /**
     * Return true if the given type is a JSON array type.
     *
     * @param objTypes the type to check.
     * @return true if the given type is a JSON array type.
     */
    private static boolean isJSONArray( Class<?>[] objTypes )
    {
        if ( objTypes[0].isArray() ){
            return true;
        }
        return isType(objTypes, ARRAY_TYPES);
    }

    /**
     * Return true if the given type is a JSON map type.
     *
     * @param objTypes the type to check.
     * @return true if the given type is a JSON map type.
     */
    private static boolean isJSONMap( Class<?>[] objTypes )
    {
        return isType(objTypes, MAP_TYPES);
    }

    /**
     * Return true if the objTypes or any of its super types or interfaces is
     * the same as the given type.
     *
     * @param objTypes The type to check.
     * @param type The type to check against.
     * @return true if there's a match.
     */
    private static boolean isType( Class<?>[] objTypes, Class<?> type )
    {
        for ( Class<?> objType : objTypes ){
            if ( objType == type ){
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if the objTypes or any of its super types or interfaces is
     * the same as the given types. This used to be done with HashSets but the
     * sets are so small that these linear reference checks are actually a
     * little faster in the end and they have the bonus of using less memory.
     * These reference checks work because the class loader will only create one
     * object per unique class loaded so using "==" does effectively the same
     * thing as calling .equals(Object) only it's faster.
     *
     * @param objType The type to check.
     * @param type The type to check against.
     * @return true if there's a match.
     */
    private static boolean isType( Class<?>[] objTypes, Class<?>[] types )
    {
        for ( Class<?> type : types ){
            for ( Class<?> objType : objTypes ){
                if ( objType == type ){
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get a collection of types represented by the given type including
     * all super types and interfaces.
     *
     * @param objType the original type.
     * @return the type and all its super types and interfaces.
     */
    private static Class<?>[] getTypes( Class<?> objType )
    {
        Set<Class<?>> types = new LinkedHashSet<>();
        Class<?> tmpClass = objType;
        while ( tmpClass != null ){
            if ( ! "java.lang.Object".equals(tmpClass.getCanonicalName()) ){
                types.add(tmpClass);
            }
            tmpClass = tmpClass.getSuperclass();
        }
        getInterfaces(objType, types);
        return types.toArray(new Class<?>[types.size()]);
    }

    /**
     * Get a complete list of interfaces for a given class including
     * all super interfaces.
     *
     * @param clazz The class.
     * @param The set of interfaces.
     */
    private static void getInterfaces( Class<?> clazz, Set<Class<?>> interfaces )
    {
        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Class<?> itfc : clazz.getInterfaces() ){
                if ( interfaces.add(itfc) ){
                    getInterfaces(itfc, interfaces);
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }
    }

    /**
     * This class should never be instantiated.
     */
    private ReflectUtil()
    {
    }
}
