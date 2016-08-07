package org.kopitubruk.util.json;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Some reflection utility methods.
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
     * Reflection will attempt to serialize package private, protected and public.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PACKAGE = 1;

    /**
     * Reflection will attempt to serialize protected and public.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PROTECTED = 2;

    /**
     * Reflection will attempt to serialize only public.
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
     * Check that the given privacy level is valid.
     *
     * @param level The level to check.
     * @param cfg The config for the exception.
     * @return The value if valid.
     */
    static int confirmLevel( int level, JSONConfig cfg )
    {
        if ( PERMITTED_LEVELS.contains(level) ){
            return level;
        }else{
            throw new JSONReflectionException(level, cfg);
        }
    }

    /**
     * Use reflection to build a map of the properties of the given object
     *
     * @param propertyValue The object to be appended via reflection.
     * @param cfg A configuration object to use.
     * @return A map representing the object's fields.
     * @throws IOException If there is an error on output.
     */
    static Map<Object,Object> getReflectedObject( Object propertyValue, JSONConfig cfg ) throws IOException
    {
        // add the fields to the object map.
        Map<Object,Object> obj = new LinkedHashMap<>();
        Class<?> clazz = JSONConfigDefaults.getClass(propertyValue);
        Map<String,Method> methods = getMethods(clazz);
        int level = cfg.getReflectionPrivacy();

        for ( Field field : getFields(clazz) ){
            int modifiers = field.getModifiers();
            if ( Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) ){
                continue;       // ignore static and transient fields.
            }
            String name = field.getName();
            Method getter = getGetter(name, methods);
            boolean success = false;
            if ( getter != null ){
                // prefer the argumentless getter over direct access.
                int getterLevel = getLevel(getter.getModifiers());
                if ( getterLevel >= level ){
                    if ( ! getter.isAccessible() ){
                        getter.setAccessible(true);
                    }
                    Object value;
                    try{
                        value = getter.invoke(obj);
                    }catch ( IllegalAccessException|IllegalArgumentException|InvocationTargetException e ){
                        throw new JSONReflectionException(propertyValue, name, e, cfg);
                    }
                    obj.put(name, value);
                    success = true;
                }
            }
            if ( ! success ){
                int fieldLevel = getLevel(modifiers);
                if ( fieldLevel >= level ){
                    // no getter -> direct access.
                    if ( ! field.isAccessible() ){
                        field.setAccessible(true);
                    }
                    Object value;
                    try{
                        value = field.get(propertyValue);
                    }catch ( IllegalArgumentException|IllegalAccessException e ){
                        throw new JSONReflectionException(propertyValue, name, e, cfg);
                    }
                    obj.put(name, value);
                }
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
     * Get all of the methods for a given class.
     *
     * @param clazz The class.
     * @return The methods.
     */
    private static Map<String,Method> getMethods( Class<?> clazz )
    {
        // build a map of the object's properties.
        Map<String,Method> methods = new HashMap<>();

        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Method method : tmpClass.getDeclaredMethods() ){
                String name = method.getName();
                if ( ! methods.containsKey(name) ){
                    if ( name.startsWith("get") && method.getParameterCount() == 0 ){
                        // facilitate looking for parameterless getter.
                        name = name + "__JSONUtil__0";
                    }
                    methods.put(name, method);
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }

        return new HashMap<>(methods);
    }

    /**
     * Get the parameterless getter for the given field.
     *
     * @param fieldName The name of the field.
     * @param methods The methods.
     * @return The parameterless getter for the field or null if there isn't one.
     */
    private static Method getGetter( String fieldName, Map<String,Method> methods )
    {
        // looking for a getter with no parameters.
        String getterName = "get" +
                            fieldName.substring(0,1).toUpperCase() +
                            fieldName.substring(1) +
                            "__JSONUtil__0";
        return methods.get(getterName);
    }
}
