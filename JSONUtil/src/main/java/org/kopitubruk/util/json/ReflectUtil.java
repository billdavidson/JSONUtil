package org.kopitubruk.util.json;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Pattern;

import javax.management.MBeanException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
    private static Log s_log = null;

    private static ClassLoader classLoader;

    /**
     * Reflection will attempt to serialize all fields including private.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PRIVATE = 0;

    /**
     * Reflection will attempt to serialize package private, protected and
     * public fields or fields that have package private, protected or public get
     * methods that conform to JavaBean naming conventions.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PACKAGE = 1;

    /**
     * Reflection will attempt to serialize protected and public fields or
     * fields that have protected or public get methods that conform to
     * JavaBean naming conventions.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PROTECTED = 2;

    /**
     * Reflection will attempt to serialize only fields that are public
     * or have public get methods that conform to JavaBean naming
     * conventions.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PUBLIC = 3;

    /**
     * Getter name pattern.
     */
    private static final Pattern GETTER = Pattern.compile("^(get|is)\\p{Lu}.*$");

    /**
     * A set of permissible levels for reflection privacy.
     */
    private static final Set<Integer> PERMITTED_LEVELS =
            new HashSet<>(Arrays.asList(PRIVATE, PACKAGE, PROTECTED, PUBLIC));

    /**
     * Primitive number type names.
     */
    private static final Set<Class<?>> PRIMITIVE_NUMBERS =
            new HashSet<>(Arrays.asList(Double.TYPE, Float.TYPE, Long.TYPE, Integer.TYPE, Short.TYPE, Byte.TYPE));

    /**
     * Types that become arrays in JSON.
     */
    private static final Set<Class<?>> ARRAY_TYPES =
            new HashSet<>(Arrays.asList(Iterable.class,Enumeration.class));

    /**
     * Types that become maps/objects in JSON.
     */
    private static final Set<Class<?>> MAP_TYPES =
            new HashSet<>(Arrays.asList(Map.class,ResourceBundle.class));

    /**
     * Cache for fields.
     */
    private static Map<Class<?>,Map<String,Field>> FIELDS;

    /**
     * Cache for getter methods.
     */
    private static Map<Class<?>,Map<String,Method>> GETTER_METHODS;

    /**
     * The field-method compatibility cache.
     */
    private static Map<Class<?>,Map<Field,Method>> FIELD_METHOD_COMPAT;

    /**
     * The field-method incompatibility cache.
     */
    private static Map<Class<?>,Map<Field,Method>> FIELD_METHOD_INCOMPAT;

    /**
     * Minimum getter privacy level
     */
    private static Map<Class<?>,Integer> MIN_GETTER_PRIVACY;

    static {
        // needed for loading classes for reflection.
        classLoader = ReflectUtil.class.getClassLoader();
        clearReflectionCache();
    }

    /**
     * Clear the reflection cache.
     */
    static synchronized void clearReflectionCache()
    {
        FIELDS = null;
        GETTER_METHODS = null;
        FIELD_METHOD_COMPAT = null;
        FIELD_METHOD_INCOMPAT = null;
        MIN_GETTER_PRIVACY = null;
    }

    /**
     * Get the field cache.
     *
     * @return The field cache.
     */
    private static synchronized Map<Class<?>,Map<String,Field>> getFieldCache()
    {
        if ( FIELDS == null ){
            FIELDS = new Hashtable<>(0);
        }
        return FIELDS;
    }

    /**
     * Get the method cache.
     *
     * @return The method cache.
     */
    private static synchronized Map<Class<?>,Map<String,Method>> getMethodCache()
    {
        if ( GETTER_METHODS == null ){
            GETTER_METHODS = new Hashtable<>(0);
        }
        return GETTER_METHODS;
    }

    /**
     * Get the method cache.
     *
     * @return The method cache.
     */
    private static synchronized Map<Class<?>,Integer> getMinGetterCache()
    {
        if ( MIN_GETTER_PRIVACY == null ){
            MIN_GETTER_PRIVACY = new Hashtable<>(0);
        }
        return MIN_GETTER_PRIVACY;
    }

    /**
     * Get the compat cache.
     *
     * @return The compat cache.
     */
    private static synchronized Map<Class<?>,Map<Field,Method>> getFieldMethodCompat()
    {
        if ( FIELD_METHOD_COMPAT == null ){
            FIELD_METHOD_COMPAT = new HashMap<>(0);
        }
        return FIELD_METHOD_COMPAT;
    }

    /**
     * Get the incompat cache.
     *
     * @return The incompat cache.
     */
    private static synchronized Map<Class<?>,Map<Field,Method>> getFieldMethodIncompat()
    {
        if ( FIELD_METHOD_INCOMPAT == null ){
            FIELD_METHOD_INCOMPAT = new HashMap<>(0);
        }
        return FIELD_METHOD_INCOMPAT;
    }

    /**
     * Make sure that the logger is initialized.
     */
    private static synchronized void ensureLogger()
    {
        if ( s_log == null ){
            s_log = LogFactory.getLog(ReflectUtil.class);
        }
    }

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
        Class<?> result = null;
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
            if ( ((JSONReflectedClass)obj).getObjClass() != null  ){
                return (JSONReflectedClass)obj;
            }else{
                return null;
            }
        }else if ( obj != null ){
            return new JSONReflectedClass(getClass(obj), null);
        }else{
            return null;
        }
    }

    /**
     * Get the class object for the given class name.
     *
     * @param className The name of the class.
     * @return The class object for that class.
     * @throws MBeanException If there's an error loading the class.
     * @since 1.9
     */
    static Class<?> getClassByName( String className ) throws MBeanException
    {
        try{
            return classLoader.loadClass(className);
        }catch ( ClassNotFoundException e ){
            ResourceBundle bundle = JSONUtil.getBundle(JSONConfigDefaults.getLocale());
            String msg = String.format(bundle.getString("couldntLoadClass"), className);
            if ( JSONConfigDefaults.isLogging() ){
                ensureLogger();
                if ( s_log.isErrorEnabled() ){
                    s_log.error(msg, e);
                }
            }
            throw new MBeanException(e, msg);   // MBeans should only throw MBeanExceptions.
        }
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
        if ( PERMITTED_LEVELS.contains(privacyLevel) ){
            return privacyLevel;
        }else{
            throw new JSONReflectionException(privacyLevel, cfg);
        }
    }

    /**
     * Use reflection to build a map of the properties of the given object
     *
     * @param propertyValue The object to be appended via reflection.
     * @param cfg A configuration object to use.
     * @return A map representing the object's fields.
     */
    static Map<Object,Object> getReflectedObject( Object propertyValue, JSONConfig cfg )
    {
        // add the fields to the object map.
        Map<Object,Object> obj = new LinkedHashMap<>();

        String name = "getReflectedObject";
        try {
            JSONReflectedClass refClass = cfg.ensureReflectedClass(propertyValue);
            Set<String> fieldNames = refClass.getFieldNames();
            Class<?> clazz = refClass.getObjClass();

            if ( fieldNames == null || fieldNames.size() < 1 ){
                // no field names specified
                int privacyLevel = cfg.getReflectionPrivacy();
                boolean isNotPrivate = privacyLevel != PRIVATE;

                Map<String,Method> getterMethods = getGetterMethods(clazz, privacyLevel, cfg);
                for ( Field field : getFields(clazz, cfg).values() ){
                    int modifiers = field.getModifiers();
                    if ( Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) ){
                        continue;       // ignore static and transient fields.
                    }
                    name = field.getName();
                    Method getter = getGetter(clazz, getterMethods, field, name, cfg);
                    if ( getter != null ){
                        // prefer the argumentless getter over direct access.
                        ensureAccessible(getter);
                        obj.put(name, getter.invoke(propertyValue));
                    }else{
                        if ( isNotPrivate && getLevel(modifiers) < privacyLevel ){
                            continue;
                        }
                        // no getter -> direct access.
                        ensureAccessible(field);
                        obj.put(name, field.get(propertyValue));
                    }
                }
            }else{
                // field names specified -- privacy out the window
                Map<String,Method> getterMethods = getGetterMethods(clazz, PRIVATE, cfg);
                Map<String,Field> fields = getFields(clazz, cfg);
                for ( String fieldName : fieldNames ){
                    name = fieldName;
                    Field field = fields.get(name);
                    Method getter = getGetter(clazz, getterMethods, field, name, cfg);
                    if ( getter != null ){
                        // prefer the argumentless getter over direct access.
                        ensureAccessible(getter);
                        obj.put(name, getter.invoke(propertyValue));
                    }else if ( field != null ){
                        // no getter -> direct access.
                        ensureAccessible(field);
                        obj.put(name, field.get(propertyValue));
                    }else{
                        throw new JSONReflectionException(propertyValue, name, cfg);
                    }
                }
            }
        }catch ( Exception e ){
            if ( e instanceof JSONReflectionException ){
                throw (JSONReflectionException)e;
            }else{
                throw new JSONReflectionException(propertyValue, name, e, cfg);
            }
        }

        return new LinkedHashMap<>(obj);
    }

    /**
     * Get the privacy level for reflection.
     *
     * @param modifiers The reflection modifiers.
     * @return The privacy level.
     */
    private static int getLevel( int modifiers )
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
     * Get all of the fields for a given class.
     *
     * @param clazz The class.
     * @param cfg The config object.
     * @return The fields.
     */
    private static Map<String,Field> getFields( Class<?> clazz, JSONConfig cfg )
    {
        boolean cacheFields = cfg.isCacheReflectionData();
        Map<String,Field> fields;
        Map<Class<?>,Map<String,Field>> theCache = null;

        if ( cacheFields ){
            theCache = getFieldCache();
            fields = theCache.get(clazz);
            if ( fields != null ){
                return fields;
            }
        }

        fields = new LinkedHashMap<>();
        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Field field : tmpClass.getDeclaredFields() ){
                String name = field.getName();
                if ( ! fields.containsKey(name) ){
                    fields.put(name, field);
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }

        fields = new LinkedHashMap<>(fields);
        if ( cacheFields ){
            theCache.put(clazz, fields);
        }

        return fields;
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

        return new HashMap<>(fields);
    }

    /**
     * Get the name of the setter for the given field of the given
     * class.
     *
     * @param clazz The class.
     * @param field The field.
     * @return The setter or null if there isn't one.
     */
    static Method getSetter( Class<?> clazz, Field field )
    {
        String fieldName = field.getName();
        String setterName = makeSetterName(fieldName);

        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Method method : tmpClass.getDeclaredMethods() ){
                if ( setterName.equals(method.getName()) ){
                    return method;
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }

        return null;
    }

    /**
     * Get all of the parameterless getter methods for a given class that
     * are visible with the given privacy level.
     *
     * @param clazz The class.
     * @param privacyLevel The minimum class privacy level
     * @param cfg The config object.
     * @return The methods.
     */
    private static Map<String,Method> getGetterMethods( Class<?> clazz, int privacyLevel, JSONConfig cfg )
    {
        boolean cacheMethods = cfg.isCacheReflectionData();
        Map<String,Method> getterMethods = null;
        Map<String,Method> methodCache = null;
        Map<Class<?>,Map<String,Method>> theCache = null;
        boolean isPrivate = privacyLevel == PRIVATE;

        if ( cacheMethods ){
            theCache = getMethodCache();
            methodCache = theCache.get(clazz);
            if ( methodCache == null ){
                if ( ! isPrivate ){
                    methodCache = new HashMap<>(0);
                }
            }else{
                if ( isPrivate ){
                    return methodCache;
                }else{
                    Map<Class<?>,Integer> minGetterCache = getMinGetterCache();
                    Integer minGetter = minGetterCache.get(clazz);
                    boolean noGetterPrivacy = minGetter == null;
                    if ( !noGetterPrivacy && privacyLevel <= minGetter ){
                        return methodCache;
                    }
                    int g = 0;
                    int m = methodCache.size();
                    int minPrivacy = PUBLIC;
                    // filter by privacy level.
                    getterMethods = new HashMap<>(methodCache.size());
                    for ( Method method : methodCache.values() ){
                        int getterLevel = getLevel(method.getModifiers());
                        if ( getterLevel >= privacyLevel ){
                            getterMethods.put(method.getName(), method);
                            ++g;
                        }
                        if ( noGetterPrivacy && getterLevel < minPrivacy ){
                            minPrivacy = getterLevel;
                        }
                    }
                    if ( noGetterPrivacy ){
                        minGetterCache.put(clazz, minPrivacy);
                    }
                    return g == m ? methodCache : new HashMap<>(getterMethods);
                }
            }
        }

        int g = 0;
        int m = 0;
        int minPrivacy = PUBLIC;
        getterMethods = new HashMap<>();
        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Method method : tmpClass.getDeclaredMethods() ){
                String name = method.getName();
                Class<?> retType = method.getReturnType();
                if ( method.getParameterCount() == 0 && ! getterMethods.containsKey(name) &&
                        ! Void.TYPE.equals(retType) && GETTER.matcher(name).matches() ){
                    if ( name.startsWith("is") && ! (Boolean.class.equals(retType) || Boolean.TYPE.equals(retType)) ){
                        continue;   // "is" prefix only valid getter for booleans.
                    }
                    if ( isPrivate ){
                        getterMethods.put(name, method);
                        if ( cacheMethods ){
                            int getterLevel = getLevel(method.getModifiers());
                            if ( getterLevel < minPrivacy ){
                                minPrivacy = getterLevel;
                            }
                        }
                    }else{
                        int getterLevel = getLevel(method.getModifiers());
                        if ( getterLevel >= privacyLevel ){
                            getterMethods.put(name, method);
                            ++g;
                        }
                        if ( cacheMethods ){
                            methodCache.put(name, method);
                            ++m;
                            if ( getterLevel < minPrivacy ){
                                minPrivacy = getterLevel;
                            }
                        }
                    }
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }
        getterMethods = new HashMap<>(getterMethods);

        if ( cacheMethods ){
            theCache.put(clazz, g == m ? getterMethods : new HashMap<>(methodCache));
            getMinGetterCache().put(clazz, minPrivacy);
        }

        return getterMethods;
    }

    /**
     * Get the getter for the given field.
     *
     * @param clazz The class for this field and getter.
     * @param getterMethods The available getter methods.
     * @param field The field
     * @param name The field name.
     * @param cfg The config object.
     * @return The getter or null if one cannot be found.
     */
    private static Method getGetter( Class<?> clazz, Map<String,Method> getterMethods, Field field, String name, JSONConfig cfg )
    {
        boolean cacheReflectionData = cfg.isCacheReflectionData();

        if ( cacheReflectionData && field != null ){
            // check the cache for previous validations.
            Method getter;
            Map<Class<?>,Map<Field,Method>> compatCache = getFieldMethodCompat();
            synchronized ( compatCache ){
                Map<Field,Method> compat = compatCache.get(clazz);
                getter = compat != null ? compat.get(field) : null;
            }
            if ( getter != null && getterMethods.containsKey(getter.getName()) ){
                return getter;
            }
            Map<Class<?>,Map<Field,Method>> incompatCache = getFieldMethodIncompat();
            synchronized ( incompatCache ){
                Map<Field,Method> incompat = incompatCache.get(clazz);
                getter = incompat != null ? incompat.get(field) : null;
            }
            if ( getter != null ){
                return null;
            }
        }

        Method getter = getterMethods.get(makeGetterName(name));
        if ( getter == null ){
            getter = getterMethods.get(makeIsName(name));
        }
        if ( field == null || getter == null ){
            return getter;
        }

        boolean isCompatible = isCompatible(field, getter);

        if ( cacheReflectionData ){
            if ( isCompatible ){
                Map<Class<?>,Map<Field,Method>> compatCache = getFieldMethodCompat();
                synchronized ( compatCache ){
                    Map<Field,Method> compat = compatCache.get(clazz);
                    if ( compat == null ){
                        compat = new HashMap<>(0);
                        compatCache.put(clazz, compat);
                    }
                    compat.put(field, getter);
                }
            }else{
                Map<Class<?>,Map<Field,Method>> incompatCache = getFieldMethodIncompat();
                synchronized ( incompatCache ){
                    Map<Field,Method> incompat = incompatCache.get(clazz);
                    if ( incompat == null ){
                        incompat = new HashMap<>(0);
                        incompatCache.put(clazz, incompat);
                    }
                    incompat.put(field, getter);
                }
            }
        }

        return isCompatible ? getter : null;
    }

    /**
     * Make a JavaBeans getter name from a field name.
     *
     * @param fieldName the field name.
     * @return the getter name.
     */
    private static String makeGetterName( String fieldName )
    {
        return makeBeanMethodName(fieldName, "get");
    }

    /**
     * Make a JavaBeans "is" getter name from a field name.
     *
     * @param fieldName the field name.
     * @return the getter name.
     */
    private static String makeIsName( String fieldName )
    {
        return makeBeanMethodName(fieldName, "is");
    }

    /**
     * Make a JavaBeans setter name from a field name.
     *
     * @param fieldName the field name.
     * @return the getter name.
     */
    private static String makeSetterName( String fieldName )
    {
        return makeBeanMethodName(fieldName, "set");
    }

    /**
     * Make a bean method name.
     *
     * @param fieldName the name of the field.
     * @param prefix The prefix for the bean method name.
     * @return The bean method name.
     */
    private static String makeBeanMethodName( String fieldName, String prefix )
    {
        int len = fieldName.length();
        StringBuilder buf = new StringBuilder(len+prefix.length());
        buf.append(prefix)
           .append(fieldName.substring(0,1).toUpperCase());
        if ( len > 1 ){
            buf.append(fieldName.substring(1));
        }
        return buf.toString();
    }

    /**
     * Return true if the type returned by the method is compatible in JSON
     * with the type of the field.
     *
     * @param field The field.
     * @param method The method to check the return type of.
     * @return true if they are compatible in JSON.
     */
    private static boolean isCompatible( Field field, Method method )
    {
        Class<?> fieldType = field.getType();
        Class<?> retType = method.getReturnType();

        if ( isType(retType, fieldType) ){
            return true;
        }else if ( isNumber(fieldType) && isNumber(retType) ){
            return true;
        }else if ( isCharSequence(fieldType) && isCharSequence(retType) ){
            return true;
        }else if ( isBoolean(fieldType) && isBoolean(retType) ){
            return true;
        }else if ( isJSONArray(fieldType) && isJSONArray(fieldType) ){
            return true;
        }else if ( isJSONMap(fieldType) && isJSONMap(fieldType) ){
            return true;
        }else if ( isCharacter(fieldType) && isCharacter(retType) ){
            return true;
        }

        return false;
    }

    /**
     * Return true if the given type is a {@link Number} type.
     *
     * @param type the type to check.
     * @return true if the given type is a {@link Number} type.
     */
    private static boolean isNumber( Class<?> type )
    {
        return PRIMITIVE_NUMBERS.contains(type) || isType(type, Number.class);
    }

    /**
     * Return true if the given type is a {@link Boolean} type.
     *
     * @param type the type to check.
     * @return true if the given type is a {@link Boolean} type.
     */
    private static boolean isBoolean( Class<?> type )
    {
        return Boolean.class.equals(type) || Boolean.TYPE.equals(type);
    }

    /**
     * Return true if the given type is a {@link Character} type.
     *
     * @param type the type to check.
     * @return true if the given type is a {@link Character} type.
     */
    private static boolean isCharacter( Class<?> type )
    {
        return Character.class.equals(type) || Character.TYPE.equals(type);
    }

    /**
     * Return true if the given type is a {@link CharSequence} type.
     *
     * @param type the type to check.
     * @return true if the given type is a {@link CharSequence} type.
     */
    private static boolean isCharSequence( Class<?> type )
    {
        return isType(type, CharSequence.class);
    }

    /**
     * Return true if the given type is a JSON array type.
     *
     * @param type the type to check.
     * @return true if the given type is a JSON array type.
     */
    private static boolean isJSONArray( Class<?> type )
    {
        return type.isArray() || isType(type, ARRAY_TYPES);
    }

    /**
     * Return true if the given type is a JSON map type.
     *
     * @param type the type to check.
     * @return true if the given type is a JSON map type.
     */
    private static boolean isJSONMap( Class<?> type )
    {
        return isType(type, MAP_TYPES);
    }

    /**
     * Return true if the given type or any of its super types or interfaces is
     * the same as the given type.
     *
     * @param objType The type to check.
     * @param type The type to check against.
     * @return true if there's a match.
     */
    private static boolean isType( Class<?> objType, Class<?> type )
    {
        return isType(objType, new HashSet<>(Arrays.asList(type)));
    }

    /**
     * Return true if the given type or any of its super types or interfaces is
     * included in the given list of types.
     *
     * @param objType The type to check.
     * @param types The types to check against.
     * @return true if there's a match.
     */
    private static boolean isType( Class<?> objType, Set<Class<?>> types )
    {
        Class<?> tmpClass = objType;
        while ( tmpClass != null ){
            if ( types.contains(tmpClass) ){
                return true;
            }
            tmpClass = tmpClass.getSuperclass();
        }

        for ( Class<?> itfc : getInterfaces(objType) ){
            if ( types.contains(itfc) ){
                return true;
            }
        }

        return false;
    }

    /**
     * Get a complete list of interfaces for a given class including
     * all super interfaces and interfaces of super classes and their
     * super interfaces.
     *
     * @param clazz The class.
     * @return The set of interfaces.
     */
    private static Set<Class<?>> getInterfaces( Class<?> clazz )
    {
        // build a map of the object's properties.
        Set<Class<?>> interfaces = new LinkedHashSet<>();

        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            boolean doInterfaces = true;
            if ( tmpClass.isInterface() ){
                if ( interfaces.contains(tmpClass) ){
                    doInterfaces = false;
                }else{
                    interfaces.add(tmpClass);
                }
            }
            if ( doInterfaces ){
                for ( Class<?> itfc : tmpClass.getInterfaces() ){
                    if ( ! interfaces.contains(itfc) ){
                        interfaces.add(itfc);
                        interfaces.addAll(getInterfaces(itfc));
                    }
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }

        return new LinkedHashSet<>(interfaces);
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
     * This class should never be instantiated.
     */
    private ReflectUtil()
    {
    }
}
