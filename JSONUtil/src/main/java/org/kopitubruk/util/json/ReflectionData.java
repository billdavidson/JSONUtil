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

import java.lang.reflect.Member;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;

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
    private int privacyLevel;
    private FastStringCollection fieldNames;
    private TreeMap<String,String> fieldAliases;

    /*
     * These are used to reflect the class.
     */
    private String[] names;
    private Member[] attributes;

    /**
     * Create a new ReflectionData for storing reflection data.
     *
     * @param clazz the class being reflected.
     * @param privacyLevel the privacy level for reflection.
     * @param fieldNames the fieldNames being reflected, if specified.
     * @param fieldAliases the aliases map, if any.
     * @param names the names for JSON output
     * @param attributes The list of methods and fields to use for reflection.
     */
    ReflectionData( Class<?> clazz, int privacyLevel, FastStringCollection fieldNames, TreeMap<String,String> fieldAliases, String[] names, Member[] attributes )
    {
        this(clazz, privacyLevel, fieldNames, fieldAliases);
        this.names = names;
        this.attributes = attributes;
    }

    /**
     * Create a new ReflectionData for looking up other reflection data objects.
     *
     * @param clazz the class being reflected.
     * @param privacyLevel the privacy level for reflection.
     * @param fieldNames the fieldNames being reflected, if specified.
     * @param fieldAliases the aliases map, if any.
     */
    ReflectionData( Class<?> clazz, int privacyLevel, FastStringCollection fieldNames, TreeMap<String,String> fieldAliases )
    {
        this.clazz = clazz;
        this.privacyLevel = privacyLevel;
        this.fieldNames = fieldNames;
        this.fieldAliases = fieldAliases;
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
    Member[] getAttributes()
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
        hashCode = prime * hashCode + clazz.hashCode();
        hashCode = prime * hashCode + privacyLevel;
        hashCode = prime * hashCode + ((fieldNames == null) ? 0 : fieldNames.hashCode());
        hashCode = prime * hashCode + aliasesHashCode();
        return hashCode;
    }

    /**
     * No null checks on the entries which should be faster than the
     * AbstractMap.hashCode.
     *
     * @return the hashCode.
     */
    private int aliasesHashCode()
    {
        if ( fieldAliases == null ){
            return 0;
        }else{
            int result = 1;

            for ( Entry<String,String> entry : fieldAliases.entrySet() ){
                result = 31 * result + entry.getKey().hashCode();
                result = 31 * result + entry.getValue().hashCode();
            }

            return result;
        }
    }

    /**
     * This is only ever used in Hashtable lookups so certain assumptions can be
     * made allowing some normal checks to be skipped.
     */
    @Override
    public boolean equals( Object obj )
    {
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
     * Check of the field names are equal.
     *
     * @param fnames The other object's field names.
     * @return true if they are equal.
     */
    private boolean fieldNamesEqual( FastStringCollection fnames )
    {
        if ( fieldNames == fnames ){
            return true;
        }else if ( fieldNames == null ){
            return false;
        }else{
            return fieldNames.equals(fnames);
        }
    }

    /**
     * Compare field aliases
     *
     * @param aliases the aliases from the other object.
     * @return true if they have the same key value pairs.
     */
    private boolean aliasesEqual( TreeMap<String,String> aliases )
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

        /*
         * TreeMap allows certain assumptions.
         *
         * This should be faster than the AbstractMap.equals() which does lookups and null checks.
         */
        Iterator<Entry<String,String>> iter0 = fieldAliases.entrySet().iterator();
        Iterator<Entry<String,String>> iter1 = aliases.entrySet().iterator();
        while ( iter0.hasNext() ){
            // TreeMap.Entry.equals() does null checks.  This doesn't.
            Entry<String,String> entry0 = iter0.next();
            Entry<String,String> entry1 = iter1.next();

            if ( ! entry0.getKey().equals(entry1.getKey()) ){
                return false;
            }
            if ( ! entry0.getValue().equals(entry1.getValue()) ){
                return false;
            }
        }
        return true;
    }
}
