/*
 * Copyright 2015-2016 Bill Davidson
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
 * Provides utilities to convert objects into JSON and parse JSON into Java
 * objects. The JSONUtil class is an alternative to the org.json package.
 * <p>
 * Instead of creating its own maps for objects or lists for arrays, this package
 * allows you to use any {@code Map} you like, allowing for iterations on your
 * {@code Map} to be predictable by using a {@code TreeMap} or a
 * {@code LinkedHashMap} which can be useful for debugging. You can also use any
 * {@code Iterable} object or {@code Enumeration} to create a Javascript array or
 * even use an actual array of objects or primitives. In many cases it may be
 * possible to use existing data structures without modification.
 * <p>
 * This package optionally supports reflection so that you don't have to use
 * a {@code Map} at all.  You can choose which types of objects to use
 * reflection on with {@code JSONConfig.addReflectClass(Object)} or
 * {@code JSONConfig.addReflectClasses(Collection)} or you can have it use
 * reflection on all unrecognized objects by using
 * {@code JSONConfig.setReflectUnknownObjects(boolean)}.  The privacy level
 * of fields to show with reflection can be controlled by using
 * {@code JSONConfig.setPrivacyLevel(int)}.  By default only fields which are
 * public or which have public getters will be included, but protected,
 * package private and private can be included by using that setting.
 * <p>
 * There is an interface provided called {@code JSONAble} which enables
 * marking classes that can convert themselves to JSON and when those are
 * encountered as the values in a {@code Map}, {@code Iterable},
 * {@code Enumeration}, array or reflected class, then their
 * {@code JSONAble.toJSON(JSONConfig,Writer)} method will be called to add them
 * to the output.
 * <p>
 * {@code Map}s, {@code Iterable}s, {@code Enumeration}s, arrays and reflected
 * classes are traversed,
 * allowing the creation of complex graphs of JSON data with one call.
 * {@code JSONAble}s may also be traversed if the {@code JSONAble} object
 * implements complex data structures within itself and uses this package to
 * generate its own JSON.
 * <p>
 * There is loop detection which attempts to avoid infinite recursion, but there
 * are some ways to get past the detection, particularly with toString() methods
 * in non-{@code JSONAble} objects, or with {@code JSONAble}s which do not properly
 * pass their {@code JSONConfig} object along so care should still be exercised
 * in avoiding loops in data structures.
 * <p>
 * There are a number of configuration options to allow you to disable validation
 * and loop detection, change some of the character escape behavior and even
 * generate certain types of non-standard JSON which may work if you're using
 * a Javascript eval() on it rather than a strict JSON parser.
 * <p>
 * ECMAScript 6 support is available by enabling it in {@code JSONConfig}.  This
 * causes ECMAScript 6 code point escapes to be recognized as well as generated
 * when it saves characters over code unit escapes.  It also allows a larger set
 * of characters in identifiers as per the ECMAScript 6 standard.
 * <p>
 * There is some support for arbitrary precision numbers.  All Javascript numbers
 * are internally 64-bit double precision floating point and all numbers get
 * converted to that format by default.  If you set
 * {@code JSONConfig.setPreciseFloatingPoint(boolean)} to true then decimal numbers
 * which cannot be accurately represented by 64-bit double precision floating point
 * will be quoted in the output.  Likewise, if you set
 * {@code JSONConfig.setPreciseIntegers(boolean)} to true then integers which
 * cannot be accurately represented by 64-bit double precision floating point
 * will be quoted in the output.  This allows those string representations of those
 * numbers to be fed to an arbitrary precision package constructor to maintain the
 * original precision.  There are several arbitrary precision math packages available
 * for Javascript.  {@code JSONParser} creates {@code BigInteger} and
 * {@code BigDecimal} whenever it has numbers that lose precision in {@code Long}
 * or {@code Double} respectively.
 * <p>
 * This package uses Apache Commons Logging facade in a few places so it should work
 * with whatever logging framework you're using, but most of the messages are debug
 * level so you shouldn't see them unless you enable debug for the package/classes.
 * They are all related to JNDI lookups or MBean access so if you're having trouble
 * with those, you may want to enable debug for this package.
 * <p>
 * The JSONParser class parses JSON data.  It converts Javascript objects into
 * {@code LinkedHashMap}s and Javascript arrays into {@code ArrayList}s.  It's
 * a "loose" parser in that it accepts valid Javascript syntax for objects and
 * arrays, numbers and strings so it is less strict than the JSON standard.
 */

package org.kopitubruk.util.json;

