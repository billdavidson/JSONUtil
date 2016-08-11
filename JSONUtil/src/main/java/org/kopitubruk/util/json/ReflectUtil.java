package org.kopitubruk.util.json;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Some reflection utility constants.
 *
 * @author Bill Davidson
 * @since 1.9
 */
public class ReflectUtil
{
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
     * methods that conform to Java bean naming conventions.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PACKAGE = 1;

    /**
     * Reflection will attempt to serialize protected and public fields or
     * fields that have protected or public get methods that conform to Java
     * bean naming conventions.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PROTECTED = 2;

    /**
     * Reflection will attempt to serialize only fields that are public
     * or have public get methods that conform to Java bean naming
     * conventions.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PUBLIC = 3;

    /**
     * A set of permissible levels for reflection privacy.
     */
    private static final Set<Integer> PERMITTED_LEVELS =
            new HashSet<>(Arrays.asList(PRIVATE, PACKAGE, PROTECTED, PUBLIC));

    /**
     * Primitive number type names.
     */
    private static final Set<String> PRIMITIVE_NUMBERS =
            new HashSet<>(Arrays.asList("double","float","long","int","short","byte"));

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

        String name = "getGetterMethods";
        try {
            Class<?> clazz = JSONConfigDefaults.getClass(propertyValue);
            int privacyLevel = cfg.getReflectionPrivacy();
            Map<String,Method> getterMethods = getGetterMethods(clazz, privacyLevel);
            for ( Field field : getFields(clazz) ){
                int modifiers = field.getModifiers();
                if ( Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) ){
                    continue;       // ignore static and transient fields.
                }
                name = field.getName();
                Method getter = getGetter(field, getterMethods);
                if ( getter != null ){
                    // prefer the argumentless getter over direct access.
                    if ( ! getter.isAccessible() ){
                        getter.setAccessible(true);
                    }
                    obj.put(name, getter.invoke(propertyValue));
                }else{
                    // no getter -> direct access.
                    int fieldLevel = getLevel(modifiers);
                    if ( fieldLevel >= privacyLevel ){
                        if ( ! field.isAccessible() ){
                            field.setAccessible(true);
                        }
                        obj.put(name, field.get(propertyValue));
                    }
                }
            }
        }catch ( Exception e ){
            throw new JSONReflectionException(propertyValue, name, e, cfg);
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
     * @return The fields.
     */
    private static Collection<Field> getFields( Class<?> clazz )
    {
        // build a map of the object's properties.
        Map<String,Field> fields = new LinkedHashMap<>();

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

        return fields.values();
    }

    /**
     * Get all of the parameterless getter methods for a given class that
     * are visible with the given privacy level.
     *
     * @param clazz The class.
     * @param privacyLevel the minimum class privacy level
     * @return The methods.
     */
    private static Map<String,Method> getGetterMethods( Class<?> clazz, int privacyLevel )
    {
        // build a map of the object's properties.
        Map<String,Method> getterMethods = new HashMap<>();

        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Method method : tmpClass.getDeclaredMethods() ){
                String name = method.getName();
                int getterLevel = getLevel(method.getModifiers());
                Class<?>[] parameterTypes = method.getParameterTypes();
                if ( parameterTypes.length == 0 && getterLevel >= privacyLevel &&
                                name.startsWith("get") && ! getterMethods.containsKey(name) ){
                    getterMethods.put(name, method);
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }

        return new HashMap<>(getterMethods);
    }

    /**
     * Get the parameterless getter for the given field.
     *
     * @param fieldName The name of the field.
     * @param getterMethods The methods.
     * @return The parameterless getter for the field or null if there isn't one.
     */
    private static Method getGetter( Field field, Map<String,Method> getterMethods )
    {
        // looking for a getter with no parameters.  uses bean naming convention.
        String fieldName = field.getName();
        String getterName = "get" +
                            fieldName.substring(0,1).toUpperCase() +
                            (fieldName.length() > 1 ? fieldName.substring(1) : "");

        Method getter = getterMethods.get(getterName);

        if ( getter != null && isCompatible(field.getType(), getter.getReturnType()) ){
            return getter;
        }

        // no getter method or types not compatible for JSON
        return null;
    }

    /**
     * Return true of the type returned by the method is compatible in JSON
     * with the type of the field.
     *
     * @param fieldType The type of the field.
     * @param retType The return type of the method.
     * @return True if they are compatible in JSON.
     */
    private static boolean isCompatible( Class<?> fieldType, Class<?> retType )
    {
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
        return (type.isPrimitive() && PRIMITIVE_NUMBERS.contains(type.getCanonicalName())) || isType(type, Number.class);
    }

    /**
     * Return true if the given type is a {@link Boolean} type.
     *
     * @param type the type to check.
     * @return true if the given type is a {@link Boolean} type.
     */
    private static boolean isBoolean( Class<?> type )
    {
        return Boolean.class.equals(type) || (type.isPrimitive() && "boolean".equals(type.getCanonicalName()));
    }

    /**
     * Return true if the given type is a {@link Character} type.
     *
     * @param type the type to check.
     * @return true if the given type is a {@link Character} type.
     */
    private static boolean isCharacter( Class<?> type )
    {
        return Character.class.equals(type) || (type.isPrimitive() && "char".equals(type.getCanonicalName()));
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
        List<Class<?>> typeList = new ArrayList<>(1);
        typeList.add(type);
        return isType(objType, new HashSet<>(typeList));
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

        if ( clazz.isInterface() ){
            Class<?> tmpClass = clazz;
            while ( tmpClass != null ){
                if ( tmpClass.isInterface() && ! interfaces.contains(tmpClass) ){
                    interfaces.add(tmpClass);
                    for ( Class<?> itfc : tmpClass.getInterfaces() ){
                        if ( ! interfaces.contains(itfc) ){
                            interfaces.add(itfc);
                            interfaces.addAll(getInterfaces(itfc));
                        }
                    }
                }
                tmpClass = tmpClass.getSuperclass();
            }
        }else{
            Class<?> tmpClass = clazz;
            while ( tmpClass != null ){
                for ( Class<?> itfc : tmpClass.getInterfaces() ){
                    if ( ! interfaces.contains(itfc) ){
                        interfaces.add(itfc);
                        interfaces.addAll(getInterfaces(itfc));
                    }
                }
                tmpClass = tmpClass.getSuperclass();
            }
        }

        return new LinkedHashSet<>(interfaces);
    }
}
