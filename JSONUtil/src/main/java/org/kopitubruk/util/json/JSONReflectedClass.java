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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * This class wraps a class to be explicitly reflected and allows you to
 * choose the names of the fields to be reflected, regardless of privacy.
 * This gives you more precise control over what is shown.  It should be
 * created and then sent to {@link JSONConfig#addReflectClass(Object)}
 * or {@link JSONConfig#addReflectClasses(java.util.Collection)} just
 * like any other object for which you wish to use selective reflection.
 * <p>
 * If you do not specify field names (fieldNames is null or empty) then
 * normal reflection will be done.
 * <p>
 * Using this object with explicit field names allows you a few abilities
 * that normal reflection does not.
 * <ul>
 *   <li>
 *     You can specify exactly the fields that you wish to show and exclude
 *     all others.  Privacy settings are ignored.
 *   </li>
 *   <li>
 *     You can specify to include static or volatile fields, which normally
 *     are not allowed.
 *   </li>
 *   <li>
 *     You can use getters that don't have an actual field in the object
 *     but do have zero arguments and have names that look like JavaBeans
 *     compliant getter names.  Just specify a name as if it's the name
 *     of a field in your fieldNames and it will look for the getter that
 *     matches that pseudo field name.
 *   </li>
 * </ul>
 *
 * @author Bill Davidson
 */
public class JSONReflectedClass implements Cloneable
{
    private Class<?> objClass;
    private Set<String> fieldNames;

    /**
     * Create a new JSONReflectedClass
     *
     * @param obj An object of the type to be reflect or its class.
     * @param fieldNames The names of the fields to include in the reflection.
     */
    public JSONReflectedClass( Object obj, Set<String> fieldNames )
    {
        if ( obj == null || obj instanceof JSONReflectedClass ){
            throw new JSONReflectionException();
        }
        objClass = JSONConfigDefaults.getClass(obj);
        this.fieldNames = fieldNames;
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
        objClass = JSONConfigDefaults.getClass(obj);
    }

    /**
     * Get the set of field names to reflect.
     *
     * @return the names of the fields to reflect.
     */
    public Set<String> getFieldNames()
    {
        return fieldNames;
    }

    /**
     * Set the set of field names to reflect.
     *
     * @param fieldNames the set of field names to include in output.
     */
    public void setFieldNames( Set<String> fieldNames )
    {
        this.fieldNames = fieldNames;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public JSONReflectedClass clone()
    {
        JSONReflectedClass result = new JSONReflectedClass(objClass, null);
        result.fieldNames = fieldNames == null ? null : new LinkedHashSet<>(fieldNames);
        return result;
    }

    /* (non-Javadoc)
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
        JSONReflectedClass other = (JSONReflectedClass)obj;
        if ( objClass == null ){
            if ( other.objClass != null )
                return false;
        }else if ( !objClass.equals(other.objClass) )
            return false;
        return true;
    }
}
