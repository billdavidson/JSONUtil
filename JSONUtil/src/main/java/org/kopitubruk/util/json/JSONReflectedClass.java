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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * This class wraps a class to be explicitly reflected and allows you to choose
 * the names of the fields to be reflected, regardless of privacy. This gives
 * you more precise control over what is shown. It should be created and then
 * sent to {@link JSONConfig#addReflectClass(Object)} or
 * {@link JSONConfig#addReflectClasses(java.util.Collection)} just like any
 * other object for which you wish to use selective reflection.
 * <p>
 * If you do not specify field names (fieldNames is null or empty) then normal
 * reflection will be done.
 * <p>
 * Using this object with explicit field names allows you a few abilities that
 * normal reflection does not.
 * <ul>
 *   <li>
 *     You can specify exactly the fields that you wish to show and exclude
 *     all others. Privacy settings are ignored.
 *   </li>
 *   <li>
 *     You can specify to include static or transient fields, which normally
 *     are not allowed.
 *   </li>
 *   <li>
 *     You can use getters that don't have an actual field in the object but
 *     do have zero arguments and have names that look like JavaBeans
 *     compliant getter names. Just specify a name as if it's the name of a
 *     field in your fieldNames and it will look for the getter that matches
 *     that pseudo field name.
 *   </li>
 * </ul>
 * <p>
 * You can also specify a customNames map so that field names are aliased
 * in the JSON output to a custom name that you specify with the map.  Any
 * unmapped names will be left as is.
 *
 * @author Bill Davidson
 */
public class JSONReflectedClass implements Cloneable
{
    private Class<?> objClass;
    private String[] fieldNames;
    private Map<String,String> fieldAliases;

    /**
     * Create a new JSONReflectedClass
     *
     * @param obj An object of the type to be reflect or its class.
     */
    public JSONReflectedClass( Object obj )
    {
        this(obj, null, null);
    }

    /**
     * Create a new JSONReflectedClass
     *
     * @param obj An object of the type to be reflect or its class.
     * @param fieldNames The names of the fields to include in the reflection.
     *            Internally, this gets converted to a {@link Set} which you can
     *            access via {@link #getFieldNames()}.
     */
    public JSONReflectedClass( Object obj, Collection<String> fieldNames )
    {
        this(obj, fieldNames, null);
    }

    /**
     * Create a new JSONReflectedClass
     *
     * @param obj An object of the type to be reflect or its class.
     * @param fieldAliases Map from object field names to custom names for output.
     */
    public JSONReflectedClass( Object obj, Map<String,String> fieldAliases )
    {
        this(obj, null, fieldAliases);
    }

    /**
     * Create a new JSONReflectedClass
     *
     * @param obj An object of the type to be reflect or its class.
     * @param fieldNames The names of the fields to include in the reflection.
     *            Internally, this gets converted to a {@link Set} which you can
     *            access via {@link #getFieldNames()}.
     * @param fieldAliases Map from object field names to custom names for output.
     */
    public JSONReflectedClass( Object obj, Collection<String> fieldNames, Map<String,String> fieldAliases )
    {
        setObjClass(obj);
        setFieldNames(fieldNames);
        setFieldAliases(fieldAliases);
    }

    private JSONReflectedClass()
    {
    }

    /**
     * Get the class being reflected.
     *
     * @return the objClass
     */
    public Class<?> getObjClass()
    {
        return objClass;
    }

    /**
     * Set the object class.
     *
     * @param obj An object of the type to be reflect or its class.
     */
    public void setObjClass( Object obj )
    {
        objClass = ReflectUtil.getClass(obj);
    }

    /**
     * Get a copy of the set of field names to reflect. Modifications to the
     * returned {@link Set} will not affect reflection. You must use
     * {@link #setFieldNames(Collection)} in order to change the set of field
     * names to reflect for this object.
     *
     * @return a copy of the list of field names to reflect.
     */
    public Set<String> getFieldNames()
    {
        if ( fieldNames == null ){
            return null;
        }
        Set<String> result = new LinkedHashSet<String>(fieldNames.length);
        for ( String fieldName : fieldNames ){
            result.add(fieldName);
        }
        return result;
    }

    /**
     * Package private version of {@link #getFieldNames()} that gives direct
     * access for performance. The methods that use this are smart enough to not
     * modify the array, which is effectively internally immutable once it is
     * created. It can only be replaced entirely -- not modified. That way any
     * changes will have to go through validation to be changed while normal
     * performance is maximized because there is no need to revalidate the
     * contents after the call to {@link #setFieldNames(Collection)}
     *
     * @return the list of field names to reflect.
     */
    String[] getFieldNamesRaw()
    {
        return fieldNames;
    }

    /**
     * Set the set of field names to reflect. This silently discards any names
     * that are not valid Java identifiers.
     *
     * @param fieldNames The field names to include in reflected JSON output.
     */
    public void setFieldNames( Collection<String> fieldNames )
    {
        if ( fieldNames == null ){
            this.fieldNames = null;
        }else{
            // the LinkedHashSet preserves order and removes dups.
            Set<String> ids = new LinkedHashSet<String>(fieldNames.size());
            for ( String id : fieldNames ){
                if ( id != null ){
                    String tid = id.trim();     // ignore whitespace, if any.
                    if ( isValidJavaIdentifier(tid) ){
                        ids.add(tid);
                    }
                    // else invalid identifiers are silently discarded.
                }
                // else null is silently discarded.
            }
            this.fieldNames = ids.size() > 0 ? ids.toArray(new String[ids.size()]) : null;
        }
    }

    /**
     * Get the field aliases map.
     *
     * @return the fieldAliases
     */
    public Map<String,String> getFieldAliases()
    {
        return fieldAliases;
    }

    /**
     * Set the custom names map. Makes a copy of the map, trimming the keys and
     * values and discarding keys that are not valid Java identifiers and values
     * that don't have at least one character.
     *
     * @param fieldAliases the fieldAliases to set
     */
    public void setFieldAliases( Map<String,String> fieldAliases )
    {
        if ( fieldAliases == null ){
            this.fieldAliases = null;
        }else{
            this.fieldAliases = new LinkedHashMap<String,String>(fieldAliases.size());
            for ( Entry<String,String> entry : fieldAliases.entrySet() ){
                String key = entry.getKey();
                String fieldName = key == null ? "" : key.trim();
                if ( isValidJavaIdentifier(fieldName) ){
                    String value = entry.getValue();
                    String alias = value == null ? "" : value.trim();
                    if ( alias.length() > 0 ){
                        this.fieldAliases.put(fieldName, alias);
                    }
                }
            }
            if ( this.fieldAliases.size() < 1 ){
                this.fieldAliases = null;
            }else if ( this.fieldAliases.size() < fieldAliases.size() ){
                this.fieldAliases = new LinkedHashMap<String,String>(this.fieldAliases);
            }
        }
    }

    /**
     * Get the custom version of the name, if any.
     *
     * @param name The name to look up.
     * @return The custom version of the name.
     */
    String getAlias( String name )
    {
        if ( fieldAliases == null ){
            return name;
        }
        String result = fieldAliases.get(name);
        return result == null ? name : result;
    }

    /**
     * Return true if the given string is a valid Java identifier.
     *
     * @param id The identifier.
     * @return true if the given string is a valid Java identifier.
     */
    private boolean isValidJavaIdentifier( String id )
    {
        int i = 0;
        int len = id.length();
        while ( i < len ){
            int codePoint = id.codePointAt(i);
            if ( i > 0 && Character.isJavaIdentifierPart(codePoint) ){
                // OK
            }else if ( i == 0 && Character.isJavaIdentifierStart(codePoint) ){
                // OK
            }else{
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return i > 0;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#clone()
     */
    @Override
    public JSONReflectedClass clone()
    {
        JSONReflectedClass result = new JSONReflectedClass();
        result.objClass = objClass;
        result.fieldNames = fieldNames;
        result.fieldAliases = fieldAliases == null ? null : new LinkedHashMap<String,String>(fieldAliases);
        return result;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((objClass == null) ? 0 : objClass.hashCode());
        return result;
    }

    /*
     * (non-Javadoc)
     *
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
        JSONReflectedClass other = (JSONReflectedClass)obj;
        if ( objClass == null ){
            if ( other.objClass != null )
                return false;
        }else if ( !objClass.equals(other.objClass) )
            return false;
        return true;
    }
}
