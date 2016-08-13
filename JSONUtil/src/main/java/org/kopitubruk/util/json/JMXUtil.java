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

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * Utilities for JMX.
 *
 * @author Bill Davidson
 */
class JMXUtil
{
    /**
     * Make an ObjectName based upon the class's canonical name.
     *
     * @param obj The object/class.
     * @param appName The name of the app, if known.
     * @return The ObjectName.
     * @throws MalformedObjectNameException If it makes a bad name.
     */
    static ObjectName getObjectName( Object obj, String appName ) throws MalformedObjectNameException
    {
        Class<?> objClass = obj instanceof Class ? (Class<?>)obj : obj.getClass();
        StringBuilder name = new StringBuilder();

        name.append(objClass.getPackage().getName())
            .append(":type=")
            .append(objClass.getSimpleName());
        if ( appName != null ){
            name.append(",appName=")
                .append(appName);
        }

        return new ObjectName(name.toString());
    }

    /**
     * This class should never be instantiated.
     */
    private JMXUtil()
    {
    }
}
