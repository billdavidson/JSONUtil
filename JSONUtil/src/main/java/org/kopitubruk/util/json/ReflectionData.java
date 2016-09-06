package org.kopitubruk.util.json;

import java.lang.reflect.AccessibleObject;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This class encapsulates cached reflection data for a given class when
 * reflected at a given privacy level and a given set of fieldNames if any and a
 * given set of aliases, if any.
 * <p>
 * The names and attributes arrays are generated and not used to look up things
 * in the cache. They are only used to reflect objects.
 *
 * @author Bill Davidson
 */
class ReflectionData
{
    /*
     * These are used by hashCode and equals for Hashtable lookups.
     */
    private Class<?> clazz;
    private String[] fieldNames;
    private Map<String,String> fieldAliases;
    private int privacyLevel;

    /*
     * These are used to reflect the class.
     */
    private String[] names;
    private AccessibleObject[] attributes;

    /**
     * Create a new ReflectionData
     *
     * @param clazz the class being reflected.
     * @param fieldNames the fieldNames being reflected, if specified.
     * @param fieldAliases the aliases map, if any.
     * @param privacyLevel the privacy level for reflection.
     * @param names the names for JSON output
     * @param attributes The list of methods and fields to use for reflection.
     */
    ReflectionData( Class<?> clazz, String[] fieldNames, Map<String,String> fieldAliases, int privacyLevel, String[] names, AccessibleObject[] attributes )
    {
        this.clazz = clazz;
        this.fieldNames = fieldNames;
        this.fieldAliases = fieldAliases;
        this.privacyLevel = privacyLevel;
        this.names = names;
        this.attributes = attributes;
    }

    /**
     * The names to use for each attribute.
     *
     * @return the names
     */
    String[] getNames()
    {
        return names;
    }

    /**
     * The attributes; either Method or Field objects.
     *
     * @return the attributes
     */
    AccessibleObject[] getAttributes()
    {
        return attributes;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int hashCode = 1;
        hashCode = prime * hashCode + ((fieldAliases == null) ? 0 : fieldAliases.hashCode());
        hashCode = prime * hashCode + ((clazz == null) ? 0 : clazz.hashCode());
        hashCode = prime * hashCode + Arrays.hashCode(fieldNames);
        hashCode = prime * hashCode + privacyLevel;
        return hashCode;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        ReflectionData other = (ReflectionData)obj;
        if ( clazz != other.clazz )
            return false;
        if ( privacyLevel != other.privacyLevel )
            return false;
        if ( ! Arrays.equals(fieldNames, other.fieldNames) )
            return false;
        if ( ! mapsEqual(fieldAliases, other.fieldAliases) ){
            return false;
        }
        return true;
    }

    /**
     * I don't entirely trust the JDK's Map equals() methods.
     *
     * @param map0 first map to compare.
     * @param map1 second map
     * @return true if they have the same key value pairs.
     */
    private static boolean mapsEqual( Map<String,String> map0, Map<String,String> map1 )
    {
        if ( map0 == map1 ){
            return true;
        }
        if ( map0 == null || map1 == null ){
            return false;
        }
        if ( map0.size() != map1.size() ){
            return false;
        }
        for ( Entry<String,String> entry : map0.entrySet() ){
            String key0 = entry.getKey();
            if ( ! map1.containsKey(key0) ){
                return false;
            }
            String value0 = entry.getValue();
            String value1 = map1.get(key0);
            if ( value0 == value1 ){
                // OK
            }else if ( value0 == null ){
                return false;
            }else if ( ! value0.equals(value1) ){
                return false;
            }
        }
        return true;
    }
}
