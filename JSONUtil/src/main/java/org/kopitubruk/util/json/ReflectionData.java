package org.kopitubruk.util.json;

import java.lang.reflect.AccessibleObject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This class encapsulates cached reflection data for a given class when
 * reflected at a given privacy level and a given set of fieldNames if any and a
 * given set of aliases, if any.
 * <p>
 * The names and attributes arrays are generated and not used to look up things
 * in the cache. They are only used to reflect objects.  The other data is used
 * to look up this object in the cache.
 *
 * @author Bill Davidson
 */
class ReflectionData
{
    /*
     * These are used by hashCode and equals for Hashtable lookups.
     */
    private Class<?> clazz;
    private Collection<String> fieldNames;
    private Map<String,String> fieldAliases;
    private int privacyLevel;

    /*
     * These are used to reflect the class.
     */
    private String[] names;
    private AccessibleObject[] attributes;

    /**
     * Create a new ReflectionData for storing reflection data.
     *
     * @param clazz the class being reflected.
     * @param fieldNames the fieldNames being reflected, if specified.
     * @param fieldAliases the aliases map, if any.
     * @param privacyLevel the privacy level for reflection.
     * @param names the names for JSON output
     * @param attributes The list of methods and fields to use for reflection.
     */
    ReflectionData( Class<?> clazz, Collection<String> fieldNames, Map<String,String> fieldAliases, int privacyLevel, String[] names, AccessibleObject[] attributes )
    {
        this.clazz = clazz;
        this.fieldNames = fieldNames;
        this.fieldAliases = fieldAliases;
        this.privacyLevel = privacyLevel;
        this.names = names;
        this.attributes = attributes;
    }

    /**
     * Create a new ReflectionData for looking up other reflection data objects.
     *
     * @param clazz the class being reflected.
     * @param fieldNames the fieldNames being reflected, if specified.
     * @param fieldAliases the aliases map, if any.
     * @param privacyLevel the privacy level for reflection.
     */
    ReflectionData( Class<?> clazz, Collection<String> fieldNames, Map<String,String> fieldAliases, int privacyLevel )
    {
        this.clazz = clazz;
        this.fieldNames = fieldNames;
        this.fieldAliases = fieldAliases;
        this.privacyLevel = privacyLevel;
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
        hashCode = prime * hashCode + ((clazz == null) ? 0 : clazz.hashCode());
        hashCode = prime * hashCode + fieldNamesHashCode();
        hashCode = prime * hashCode + ((fieldAliases == null) ? 0 : fieldAliases.hashCode());
        hashCode = prime * hashCode + privacyLevel;
        return hashCode;
    }

    /**
     * Get hash code for field names.
     *
     * @param fieldNames An array of strings.
     * @return the hash code.
     */
    private int fieldNamesHashCode()
    {
        if ( fieldNames == null ){
            return 0;
        }

        int result = 1;

        for ( Object element : fieldNames ){
            result = 31 * result + element.hashCode();
        }

        return result;
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
        if ( ! fieldNamesEqual(other.fieldNames) )
            return false;
        if ( ! aliasesEqual(other.fieldAliases) ){
            return false;
        }
        return true;
    }

    /**
     * Slightly faster than {@link Arrays#equals(Object)} because it
     * eliminates a null check.
     *
     * @param fnames Second array of strings.
     * @return true if the arrays are equal.
     */
    private boolean fieldNamesEqual( Collection<String> fnames )
    {
        if ( fieldNames == fnames ){
            return true;
        }
        if ( fieldNames == null || fnames == null ){
            return false;
        }

        if ( fnames.size() != fieldNames.size() ){
            return false;
        }

        Iterator<String> local = fieldNames.iterator();
        Iterator<String> other = fnames.iterator();
        while ( local.hasNext() ){
            String lfn = local.next();
            String ofn = other.next();
            if ( ! lfn.equals(ofn) ){
                return false;
            }
        }

        return true;
    }

    /**
     * Compare field aliases
     *
     * @param aliases the aliases from the other object.
     * @return true if they have the same key value pairs.
     */
    private boolean aliasesEqual( Map<String,String> aliases )
    {
        if ( fieldAliases == aliases ){
            return true;
        }
        if ( fieldAliases == null || aliases == null ){
            return false;
        }
        if ( fieldAliases.size() != aliases.size() ){
            return false;
        }
        for ( Entry<String,String> entry : fieldAliases.entrySet() ){
            String key0 = entry.getKey();
            if ( ! aliases.containsKey(key0) ){
                return false;
            }
            String value0 = entry.getValue();
            String value1 = aliases.get(key0);
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
