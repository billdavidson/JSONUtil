/*
 * Copyright 2015 Bill Davidson
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

import java.io.IOException;
import java.io.Writer;

/**
 * This interface specifies a method which implementing objects will use to
 * provide a JSON representation of themselves. When
 * {@link JSONUtil#toJSON(Object, JSONConfig)} encounters one of these objects
 * as one of its property values, it will call this method in those objects to
 * convert them to JSON.
 *
 * @author Bill Davidson
 */
public interface JSONAble
{

    /**
     * Convert this object to a string of JSON data.
     * <p>
     * This version does not use a {@link JSONConfig} and so can break loop
     * detection.  In many common cases, that's not a problem and this
     * is just more convenient.
     * <p>
     * The default implementation calls {@link #toJSON(JSONConfig)} to
     * actually do the work.
     *
     * @return A string of JSON data.
     */
    /* Java 8 version *
    default String toJSON()
    {
        return toJSON((JSONConfig)null);
    }
    * */
    // Java 7 version.
    public String toJSON();

    /**
     * Convert this object to a string of JSON data.
     * <p>
     * Implementations which use {@link JSONUtil} to generate their JSON should
     * pass the supplied {@link JSONConfig} to
     * {@link JSONUtil#toJSON(Object, JSONConfig)} or
     * {@link JSONUtil#toJSON(Object, JSONConfig, Writer)} in order to preserve
     * data structure loop detection.  If this is not done, then loops may not
     * be detected properly.
     * <p>
     * The default implementation calls {@link #toJSON(JSONConfig, Writer)} to
     * actually do the work.
     *
     * @param jsonConfig A configuration object to use to optionally set encoding options.
     * @return A string of JSON data.
     */
    /* Java 8 version *
    default String toJSON( JSONConfig jsonConfig )
    {
        JSONConfig cfg = jsonConfig == null ? new JSONConfig() : jsonConfig;
        Writer json = new StringWriter();
        try{
            toJSON(cfg, json);
        }catch ( IOException e ){
            // won't happen with the StringWriter.
        }
        cfg = null;
        return json.toString();
    }
    * */
    // Java 7 version.
    public String toJSON( JSONConfig jsonConfig );

    /**
     * Write to the given writer as JSON data using all
     * defaults for the configuration.
     * 
     * @param json json A writer for the output.
     * @throws IOException  If there is an error on output.
     */
    /* Java 8 version *
    default void toJSON( Writer json ) throws IOException
    {
        toJSON(new JSONConfig(), json);
    }
    * */
    // Java 7 version.
    public void toJSON( Writer json ) throws IOException;

    /**
     * Write this object to the given writer as JSON data. This is the one that
     * gets called by JSONUtil's toJSON methods so if you include JSONAbles
     * inside other structures that you will be passing to JSONUtil's toJSON
     * methods then this one really needs to be implemented and not just
     * "stubbed".
     * <p>
     * Implementations which use {@link JSONUtil} to generate their JSON should
     * pass the supplied {@link JSONConfig} to
     * {@link JSONUtil#toJSON(Object, JSONConfig)} or
     * {@link JSONUtil#toJSON(Object, JSONConfig, Writer)} in order to preserve
     * data structure loop detection. If this is not done, then loops may not be
     * detected properly.
     *
     * @param jsonConfig A configuration object to use to optionally set encoding options.
     * @param json A writer for the output.
     * @throws IOException If there is an error on output.
     */
    public void toJSON( JSONConfig jsonConfig, Writer json ) throws IOException;
}
