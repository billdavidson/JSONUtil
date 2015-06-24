/*
 * Copyright (c) 2015 Bill Davidson
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

/**
 * Provides utilities to convert objects into JSON. The JSONUtil class is an
 * alternative to the org.json package.
 * <p>
 * Instead of creating its own maps for objects or lists for arrays, this package
 * allows you to use any {@link Map} you like, allowing for iterations on your
 * {@link Map} to be predictable by using a {@link TreeMap} or a
 * {@link LinkedHashMap} which can be useful for debugging. You can also use any
 * {@link Iterable} object or {@link Enumeration} to create a Javascript array or
 * even use an actua array of objects or primitives. In many cases it may be
 * possible to use existing data structures without modification.
 * <p>
 * There is also an interface provided called {@link JSONAble} which enables
 * marking classes that can convert themselves to JSON and when those are
 * encountered as the values in a {@link Map}, {@link Iterable},
 * {@link Enumeration} or array, their {@link JSONAble#toJSON(JSONConfig,Writer)}
 * method will be called to add them to the output.
 * <p>
 * {@link Map}s, {@link Iterable}s, {@link Enumeration}s and arrays are traversed,
 * allowing the creation of complex graphs of JSON data with one call.
 * {@link JSONAble}s may also be traversed if the {@link JSONAble} object
 * implements complex data structures within itself and uses this package to
 * generate its own JSON.
 * <p>
 * There is loop detection which attempts to avoid infinite recursion, but there
 * are some ways to get past the detection, particularly with toString() methods
 * in non-{@link JSONAble} objects, or with {@link JSONAble}s which do not properly
 * pass their {@link JSONConfig} object along so care should still be exercised
 * in avoiding loops in data structures.
 * <p>
 * There are a number of configuration options to allow you to disable validation
 * and loop detection, change some of the character escape behavior and even
 * generate certain types of non-standard JSON which may work if you're using
 * a Javascript eval() on it rather than a strict JSON parser.
 */

package org.kopitubruk.util.json;

import java.io.Writer;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
