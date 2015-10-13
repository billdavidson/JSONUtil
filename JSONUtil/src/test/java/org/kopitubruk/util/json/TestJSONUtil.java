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

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;
import java.util.Vector;

import javax.naming.NamingException;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for JSONUtil. Most of the produced JSON is put through Java's script
 * engine so that it will be tested that it parses without error. However,
 * eval() is more lenient than a strict JSON parser, so it's not a perfect test
 * of JSON standards compliance.
 *
 * @author Bill Davidson
 */
public class TestJSONUtil
{
    private static Log s_log = LogFactory.getLog(TestJSONUtil.class);
    private static final String FAIL_FMT = "Expected a BadPropertyNameException to be thrown for U+%04X";

    /**
     * Create a dummy JNDI initial context in order to avoid having
     * JNDI code throw an exception during the JUnit tests.
     */
    @BeforeClass
    public static void setUpClass()
    {
        try{
            String pkgName = JSONUtil.class.getPackage().getName().replaceAll("\\.", "/");

            //Context ctx =
            JNDIUtil.createContext(JNDIUtil.ENV_CONTEXT+"/"+pkgName);

            // not needed -- just used to test that the context was usable.
            //ctx.bind("registerMBean", Boolean.FALSE);

            // Some of these tests depend upon error messages
            // which need to be in English, so it's forced
            // during the tests.
            JSONConfigDefaults.setLocale(Locale.US);
        }catch ( NamingException ex ){
            s_log.error("Couldn't create context", ex);
        }
    }

    /**
     * Javascript engine to be used to validate JSON. Nashorn (Java 8) supports
     * ECMAScript 5.1. Java 7 uses Rhino 1.7, which supports something roughly
     * close to ECMAScript 3.  Java 6 uses Rhino 1.6r2 which is also close to
     * ECMAScript 3.
     */
    private final ScriptEngine engine = new ScriptEngineManager().getEngineByExtension("js");

    /**
     * Make sure that the JSON parses.
     *
     * @param json A JSON string.
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    private void validateJSON( String json ) throws ScriptException
    {
        // assign it to a variable to make sure it parses.
        try{
            engine.eval("var x="+json+";");
        }catch ( ScriptException e ){
            boolean lastCode = false;
            StringBuilder buf = new StringBuilder();
            for ( int i = 0, j = 0, len = json.length(); i < len && j < 500; j++ ){
                int codePoint = json.codePointAt(i);
                int charCount = Character.charCount(codePoint);
                if ( codePoint < 256 && codePoint >= ' ' ){
                    if ( lastCode ){
                        buf.append('\n');
                    }
                    buf.appendCodePoint(codePoint);
                    lastCode = false;
                }else{
                    buf.append(String.format("\n%d U+%04X %s %d",
                                             i, codePoint,
                                             "" /* Character.getName(codePoint) */,
                                             Character.getType(codePoint)));
                    lastCode = true;
                }
                i += charCount;
            }
            s_log.error(buf.toString(), e);
            throw e;
        }
    }

    /**
     * Test all characters allowed for property names. Every start character
     * gets tested in the start position and most part characters get tested
     * twice but all at least once.
     * <p>
     * This test is slow because it's doing over 100,000 validations through
     * the Javascript interpreter, which is slow.  If you comment out that
     * validation, then this test takes about 1 second on my laptop, but you
     * only test that none of the valid code points cause an exception and
     * not that they won't cause a syntax error.  With the validation on it
     * typically takes about 68 seconds for this to run on my laptop, which
     * is annoying but it provides a better test.
     * <p>
     * It should be noted that some valid characters need the identifier to
     * be in quotes in order for the validation to work properly so the
     * validation tests that those identifiers that need that do get quoted
     * even though I turned identifier quoting off for the tests.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testValidPropertyNames() throws ScriptException
    {

        JSONConfig cfg = new JSONConfig();
        cfg.setQuoteIdentifier(false);

        ArrayList<Integer> validStart = new ArrayList<Integer>();
        ArrayList<Integer> validPart = new ArrayList<Integer>();

        for ( int i = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if ( JSONUtil.isValidIdentifierStart(i, cfg) ){
                validStart.add(i);
            }else if ( JSONUtil.isValidIdentifierPart(i, cfg) ){
                validPart.add(i);
            }
        }
        validStart.trimToSize();
        s_log.debug(validStart.size() + " valid start code points");
        validPart.addAll(validStart);
        Collections.sort(validPart);
        s_log.debug(validPart.size() + " valid part code points");

        final int MAX_LENGTH = 3;
        int[] propertyName = new int[MAX_LENGTH];
        int startIndex = 0;
        int partIndex = 0;
        int nameIndex = 0;

        Map<String,Object> jsonObj = new HashMap<String,Object>(2);

        int startSize = validStart.size();
        int partSize = validPart.size();
        propertyName[nameIndex++] = validStart.get(startIndex++);

        while ( startIndex < startSize ){
            propertyName[nameIndex++] = validPart.get(partIndex++);
            if ( nameIndex == MAX_LENGTH ){
                jsonObj.clear();
                jsonObj.put(new String(propertyName,0,nameIndex), 0);
                String json = JSONUtil.toJSON(jsonObj, cfg);
                validateJSON(json);    // this makes this test take a long time to run.
                nameIndex = 0;
                if ( startIndex < startSize ){
                    // start new string.
                    propertyName[nameIndex++] = validStart.get(startIndex++);
                }
            }
            if ( partIndex == partSize ){
                partIndex = 0;
            }
        }
    }

    /**
     * Test bad code points for the start of characters in names.
     */
    @Test
    public void testBadStartPropertyNames()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>(2);
        int[] codePoints = new int[1];
        JSONConfig cfg = new JSONConfig();

        for ( int i = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if ( ! JSONUtil.isValidIdentifierStart(i, cfg) ){
                codePoints[0] = i;
                testBadIdentifier(codePoints, 0, 1, jsonObj, cfg);
            }
        }
    }

    /**
     * Test bad code points for non-start characters in names.
     */
    @Test
    public void testBadPartPropertyNames()
    {
        int[] codePoints = new int[256];
        int j = 1;
        codePoints[0] = '_';
        Map<String,Object> jsonObj = new HashMap<String,Object>(2);
        JSONConfig cfg = new JSONConfig();

        for ( int i = 0; i <= Character.MAX_CODE_POINT; i++ ){

            if ( ! JSONUtil.isValidIdentifierStart(i, cfg) && ! JSONUtil.isValidIdentifierPart(i, cfg) && ! (i <= 0xFFFF && JSONUtil.isSurrogate((char)i)) ){
                // high surrogates break the test unless they are followed immediately by low surrogates.
                // just skip them.  anyone who sends bad surrogate pairs deserves what they get.
                codePoints[j++] = i;
                if ( j == codePoints.length ){
                    testBadIdentifier(codePoints, 1, j, jsonObj, cfg);
                    j = 1;
                }
            }
        }
        if ( j > 1 ){
            testBadIdentifier(codePoints, 1, j, jsonObj, cfg);
        }
    }

    /**
     * Test a bad identifier.  This is a utility method used by other test methods.
     *
     * @param codePoints A set of code points with bad code points.
     * @param start The index of the first code point to check for.
     * @param end The index just after the last code point to check for.
     * @param jsonObj A jsonObj to use for the test.
     */
    private void testBadIdentifier( int[] codePoints, int start, int end, Map<String,Object> jsonObj, JSONConfig cfg )
    {
        jsonObj.clear();
        try{
            jsonObj.put(new String(codePoints,0,end), 0);
            JSONUtil.toJSON(jsonObj, cfg);
            fail(String.format(FAIL_FMT, codePoints[start]));
        }catch ( BadPropertyNameException e ){
            String message = e.getMessage();
            for ( int i = start; i < end; i++ ){
                assertThat(message, containsString(String.format("Code point U+%04X", codePoints[i])));
            }
        }
    }

    /**
     * Test valid Unicode strings.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testValidStrings() throws ScriptException
    {
        int[] codePoints = new int[32768];
        Map<String,Object> jsonObj = new HashMap<String,Object>(2);
        JSONConfig cfg = new JSONConfig();
        int j = 0;

        for ( int i = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if ( Character.isDefined(i) && ! (i <= 0xFFFF && JSONUtil.isSurrogate((char)i)) ){
                // 247650 code points, the last time I checked.
                codePoints[j++] = i;
                if ( j == codePoints.length/2 ){
                    jsonObj.put("x", new String(codePoints,0,j));
                    validateJSON(JSONUtil.toJSON(jsonObj, cfg));
                    j = 0;
                }
            }
        }
        if ( j > 0 ){
            jsonObj.put("x", new String(codePoints,0,j));
            validateJSON(JSONUtil.toJSON(jsonObj, cfg));
        }
    }

    /**
     * Test that Unicode escape sequences in identifiers work.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testUnicodeEscapeInIdentifier() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        String[] ids = { "a\\u1234", "\\u1234x" };
        for ( String id : ids ){
            jsonObj.clear();
            jsonObj.put(id, 0);

            String json = JSONUtil.toJSON(jsonObj);
            validateJSON(json);
            assertThat(json, is("{\""+id+"\":0}"));
        }
    }

    /**
     * Test that ECMAScript 6 code point escapes.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testECMA6UnicodeEscapeInString() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        JSONConfig cfg = new JSONConfig();
        cfg.setUseECMA6(true);
        cfg.setEscapeNonAscii(true);
        StringBuilder buf = new StringBuilder();
        int codePoint = 0x1F4A9;
        buf.append("x");
        buf.appendCodePoint(codePoint);
        jsonObj.put("x", buf);
        String json = JSONUtil.toJSON(jsonObj, cfg);
        // Nashorn doesn't understand ECMAScript 6 code point escapes.
        //validateJSON(json);
        assertThat(json, is("{\"x\":\"x\\u{1F4A9}\"}"));
    }

    /**
     * Test that Unicode escape sequences in identifiers work.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testEscapePassThrough() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        String[] strs = { "a\\u1234", "b\\x4F", "c\\377", "d\\u{1F4A9}", "e\\nf"};
        for ( String str : strs ){
            jsonObj.clear();
            jsonObj.put("x", str);

            String json = JSONUtil.toJSON(jsonObj);
            if ( str.charAt(0) != 'd' ){
                // Nashorn doesn't understand EMCAScript 6 code point escapes.
                validateJSON(json);
            }
            assertThat(json, is("{\"x\":\""+str+"\"}"));
        }
    }

    /**
     * Test that unescape works.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testUnEscape() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        String[] strs = { "a\\u0041", "b\\x41", "d\\u{41}", "e\\v"};
        JSONConfig cfg = new JSONConfig();
        cfg.setUnEscapeWherePossible(true);
        for ( String str : strs ){
            jsonObj.clear();
            jsonObj.put("x", str);

            String json = JSONUtil.toJSON(jsonObj, cfg);
            char firstChar = str.charAt(0);
            if ( firstChar != 'd' ){
                // Nashorn doesn't understand EMCAScript 6 code point escapes.
                validateJSON(json);
            }
            // \v unescaped will be re-escaped as a Unicode code unit.
            String result = firstChar + (firstChar != 'e' ? "A" : "\\u000B");
            assertThat(json, is("{\"x\":\""+result+"\"}"));
        }

        // test octal unescape.
        for ( int i = 0; i < 256; i++ ){
            jsonObj.clear();
            jsonObj.put("x", String.format("a\\%o", i));
            String result;
            switch ( i ){
                case '"':  result = "a\\\""; break;
                case '/':  result = "a\\/"; break;
                case '\b': result = "a\\b"; break;
                case '\f': result = "a\\f"; break;
                case '\n': result = "a\\n"; break;
                case '\r': result = "a\\r"; break;
                case '\t': result = "a\\t"; break;
                case '\\': result = "a\\\\"; break;
                default:
                    result = i < 0x20 ? String.format("a\\u%04X", i) : String.format("a%c", (char)i);
                    break;
            }
            String json = JSONUtil.toJSON(jsonObj, cfg);
            assertThat(json, is("{\"x\":\""+result+"\"}"));
        }
    }

    /**
     * Test the parser.
     *
     * @throws ParseException for parsing problems.
     */
    @Test
    public void testParser() throws ParseException
    {
        Object obj = JSONParser.parseJSON("{\"foo\":\"b\\\\\\\"ar\",\"a\":5,\"b\":2.37e24,\"c\":Infinity,\"d\":NaN,\"e\":[1,2,3,{\"a\":4}]}");
        String json = JSONUtil.toJSON(obj);
        assertEquals("{\"foo\":\"b\\\\\\\"ar\",\"a\":5,\"b\":2.37E24,\"c\":\"Infinity\",\"d\":\"NaN\",\"e\":[1,2,3,{\"a\":4}]}", json);

        obj = JSONParser.parseJSON("'foo'");
        assertEquals("foo", obj);

        obj = JSONParser.parseJSON("2.37e24");
        assertEquals(2.37e24, obj);

        obj = JSONParser.parseJSON("Infinity");
        assertTrue(Double.isInfinite((Double)obj));

        obj = JSONParser.parseJSON("false");
        assertEquals(Boolean.FALSE, obj);

        obj = JSONParser.parseJSON("null");
        assertEquals(null, obj);

        // parse various forms of date strings.
        JSONConfig cfg = new JSONConfig();
        cfg.setEncodeDatesAsStrings(true);
        DateFormat fmt = cfg.getDateGenFormat();

        Date dt = (Date)JSONParser.parseJSON("new Date(\"2015-09-16T14:08:34.034Z\")", cfg);
        assertEquals("2015-09-16T14:08:34.034Z", fmt.format(dt));

        dt = (Date)JSONParser.parseJSON("\"2015-09-16T14:08:34.034Z\"", cfg);
        assertEquals("2015-09-16T14:08:34.034Z", fmt.format(dt));

        dt = (Date)JSONParser.parseJSON("\"2015-09-16T14:08:34.034+01\"", cfg);
        assertEquals("2015-09-16T13:08:34.034Z", fmt.format(dt));

        dt = (Date)JSONParser.parseJSON("\"2015-09-16T14:08:34.034+01:30\"", cfg);
        assertEquals("2015-09-16T12:38:34.034Z", fmt.format(dt));

        dt = (Date)JSONParser.parseJSON("\"2015-09-16T14:08:34\"", cfg);
        assertEquals("2015-09-16T14:08:34.034Z", fmt.format(dt));

        dt = (Date)JSONParser.parseJSON("\"2015-09-16T14:08:34+01:30\"", cfg);
        assertEquals("2015-09-16T12:38:34.034Z", fmt.format(dt));

        // custom formats.
        DateFormat nfmt = cfg.setDateGenFormat("EEE, d MMM yyyy HH:mm:ss Z");
        nfmt.setTimeZone(TimeZone.getTimeZone("US/Eastern"));
        cfg.addDateParseFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
        dt = (Date)JSONParser.parseJSON("\"2001.07.04 AD at 12:08:56 EDT\"", cfg);
        assertEquals("Wed, 4 Jul 2001 12:08:56 -0400", nfmt.format(dt));

        // test that the old one still works.
        dt = (Date)JSONParser.parseJSON("\"2015-09-16T14:08:34+01:30\"", cfg);
        assertEquals("2015-09-16T12:38:34.034Z", fmt.format(dt));

        try{
            JSONParser.parseJSON("{\"foo\":\"b\\\\\\\"ar\",\"a\":5,\"b\":2.37e24,\"c\":&*^,\"d\":NaN,\"e\":[1,2,3,{\"a\":4}]}");
            fail("Expected JSONParserException for bad data");
        }catch ( JSONParserException e ){
        }
    }

    /**
     * Test using reserved words in identifiers.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testReservedWordsInIdentifiers() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        JSONConfig cfg = new JSONConfig();
        cfg.setAllowReservedWordsInIdentifiers(true);
        Set<String> reservedWords = JSONUtil.getJavascriptReservedWords();
        for ( String reservedWord : reservedWords ){
            jsonObj.clear();
            jsonObj.put(reservedWord, 0);

            // test with allow reserved words.
            String json = JSONUtil.toJSON(jsonObj, cfg);
            validateJSON(json);
            assertThat(json, is("{\""+reservedWord+"\":0}"));

            // test with reserved words disallowed.
            try{
                JSONUtil.toJSON(jsonObj);
                fail("Expected BadPropertyNameException for reserved word "+reservedWord);
            }catch ( BadPropertyNameException e ){
                String message = e.getMessage();
                assertThat(message, is(reservedWord+" is a reserved word."));
            }
        }
    }

    /**
     * Test EscapeBadIdentifierCodePoints
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testEscapeBadIdentifierCodePoints() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        JSONConfig cfg = new JSONConfig();
        cfg.setEscapeBadIdentifierCodePoints(true);
        jsonObj.put("x\u0005", 0);

        String json = JSONUtil.toJSON(jsonObj, cfg);
        validateJSON(json);
        assertThat(json, is("{\"x\\u0005\":0}"));
    }

    /**
     * Test setting default locale.
     */
    @Test
    public void testDefaultLocale()
    {
        Locale loc = new Locale("es","JP");
        Locale oldDefLoc = JSONConfigDefaults.getLocale();
        JSONConfigDefaults.setLocale(loc);
        Locale defLoc = JSONConfigDefaults.getLocale();
        JSONConfigDefaults.setLocale(oldDefLoc);
        assertEquals("Default locale not set", defLoc, loc);
        assertNotEquals(oldDefLoc, loc);
    }

    /**
     * Test dates.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testDate() throws ScriptException
    {
        JSONConfig cfg = new JSONConfig();
        cfg.setEncodeDatesAsObjects(true);
        Map<String,Object> jsonObj = new LinkedHashMap<String,Object>();
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(2015, 8, 16, 14, 8, 34);
        cal.set(Calendar.MILLISECOND, 34);
        jsonObj.put("t", cal.getTime());
        String json = JSONUtil.toJSON(jsonObj, cfg);
        /* Java 6 uses Rhino 1.6r2, which doesn't understand ISO 8601.
        String extra = ";\nvar t = x.t;\n" +
                "java.lang.System.out.println(t.toString());\n" +
                "if ( t.getUTCFullYear() != 2015 ){ throw ('bad year '+t.getUTCFullYear()); }\n"  +
                "if ( t.getUTCMonth() != 8 ){ throw ('bad month '+t.getUTCMonth()); }\n" +
                "if ( t.getUTCDate() != 16 ){ throw ('bad day '+t.getUTCDate()); }\n" +
                "if ( t.getUTCHours() != 14 ){ throw ('bad hours '+t.getUTCHours()); }\n" +
                "if ( t.getUTCMinutes() != 8 ){ throw ('bad minutes '+t.getUTCMinutes()); }\n" +
                "if ( t.getUTCSeconds() != 34 ){ throw ('bad seconds '+t.getUTCSeconds()); }\n" +
                "if ( t.getUTCMilliseconds() != 34 ){ throw ('bad millseconds '+t.getUTCMilliseconds()); }";
        */
        validateJSON(json);
        assertThat(json, is("{\"t\":new Date(\"2015-09-16T14:08:34.034Z\")}"));

        cfg.setEncodeDatesAsStrings(true);
        json = JSONUtil.toJSON(jsonObj, cfg);
        validateJSON(json);
        assertThat(json, is("{\"t\":\"2015-09-16T14:08:34.034Z\"}"));
    }

    /**
     * Test booleans.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testBoolean() throws ScriptException
    {
        Map<String,Object> jsonObj = new LinkedHashMap<String,Object>();
        jsonObj.put("t", true);
        jsonObj.put("f", false);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"t\":true,\"f\":false}"));
    }

    /**
     * Test a byte value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testByte() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        byte b = 27;
        jsonObj.put("x", b);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":27}"));
    }

    /**
     * Test a char value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testChar() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        char ch = '@';
        jsonObj.put("x", ch);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":\"@\"}"));
    }

    /**
     * Test a short value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testShort() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        short s = 275;
        jsonObj.put("x", s);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":275}"));
    }

    /**
     * Test a int value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testInt() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        int i = 100000;
        jsonObj.put("x", i);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":100000}"));
    }

    /**
     * Test a byte value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testLong() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        long l = 68719476735L;
        jsonObj.put("x", l);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":68719476735}"));
    }

    /**
     * Test a float value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testFloat() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        float f = 3.14f;
        jsonObj.put("x", f);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":3.14}"));
    }

    /**
     * Test a double value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testDouble() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        double d = 6.28;
        jsonObj.put("x", d);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":6.28}"));
    }

    /**
     * Test a BigInteger value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testBigInteger() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        BigInteger bi = new BigInteger("1234567890");
        jsonObj.put("x", bi);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":1234567890}"));
    }

    /**
     * Test a BigDecimal value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testBigDecimal() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        BigDecimal bd = new BigDecimal("12345.67890");
        jsonObj.put("x", bd);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":12345.67890}"));
    }

    /**
     * Test a custom number format.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testNumberFormat() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        float f = 1.23456f;
        jsonObj.put("x", f);
        JSONConfig cfg = new JSONConfig();
        NumberFormat fmt = NumberFormat.getInstance();
        fmt.setMaximumFractionDigits(3);
        cfg.addNumberFormat(f, fmt);
        String json = JSONUtil.toJSON(jsonObj, cfg);
        validateJSON(json);
        assertThat(json, is("{\"x\":1.235}"));
    }

    /**
     * Test a string value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testString() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        String s = "bar";
        jsonObj.put("x", s);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":\"bar\"}"));
    }

    /**
     * Test a string with a quote value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testQuoteString() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        String s = "ba\"r";
        jsonObj.put("x", s);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":\"ba\\\"r\"}"));
    }

    /**
     * Test a string with a quote value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testNonBmp() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        StringBuilder buf = new StringBuilder(2);
        buf.appendCodePoint(0x10080);
        jsonObj.put("x", buf);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":\"\uD800\uDC80\"}"));
    }

    /**
     * Test a Iterable value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testIterable() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        jsonObj.put("x", Arrays.asList(1,2,3));
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":[1,2,3]}"));
    }

    /**
     * Test an Enumeration.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testEnumeration() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();

        Vector<Integer> list = new Vector<Integer>(Arrays.asList(1,2,3));
        jsonObj.put("x", list.elements());
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":[1,2,3]}"));
    }

    /**
     * Test a Map value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testLoop() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>(4);
        jsonObj.put("a",1);
        jsonObj.put("b",2);
        jsonObj.put("c",3);
        jsonObj.put("x", jsonObj);

        JSONConfig cfg = new JSONConfig();
        try{
            JSONUtil.toJSON(jsonObj, cfg);
            fail("Expected a DataStructureLoopException to be thrown");
        }catch ( DataStructureLoopException e ){
            assertThat(e.getMessage(), containsString("java.util.HashMap includes itself which would cause infinite recursion."));
        }
    }

    /**
     * Test a resource bundle.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testResourceBundle() throws ScriptException
    {
        String bundleName = getClass().getCanonicalName();
        ResourceBundle bundle = ResourceBundle.getBundle(bundleName);
        JSONConfig cfg = new JSONConfig();

        cfg.setEncodeNumericStringsAsNumbers(false);
        String json = JSONUtil.toJSON(bundle, cfg);
        validateJSON(json);
        //assertThat(json, is("{\"a\":\"1\",\"b\":\"2\",\"c\":\"3\",\"d\":\"4\",\"e\":\"5\",\"f\":\"6\",\"g\":\"7\",\"h\":\"8\",\"i\":\"9\",\"j\":\"10\",\"k\":\"11\",\"l\":\"12\",\"m\":\"13\",\"n\":\"14\",\"o\":\"15\",\"p\":\"16\",\"q\":\"17\",\"r\":\"18\",\"s\":\"19\",\"t\":\"20\",\"u\":\"21\",\"v\":\"22\",\"w\":\"23\",\"x\":\"24\",\"y\":\"25\",\"z\":\"26\"}"));

        cfg.setEncodeNumericStringsAsNumbers(true);
        json = JSONUtil.toJSON(bundle, cfg);
        validateJSON(json);
        //assertThat(json, is("{\"a\":1,\"b\":2,\"c\":3,\"d\":4,\"e\":5,\"f\":6,\"g\":7,\"h\":8,\"i\":9,\"j\":10,\"k\":11,\"l\":12,\"m\":13,\"n\":14,\"o\":15,\"p\":16,\"q\":17,\"r\":18,\"s\":19,\"t\":20,\"u\":21,\"v\":22,\"w\":23,\"x\":24,\"y\":25,\"z\":26}"));
    }

    /**
     * Test a complex value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testComplex() throws ScriptException
    {
        Map<String,Object> jsonObj = new LinkedHashMap<String,Object>();
        jsonObj.put("a",1);
        jsonObj.put("b","x");
        String[] ia = {"1","2","3"};
        List<String> il = Arrays.asList(ia);
        jsonObj.put("c",ia);
        jsonObj.put("d",il);
        Object[] objs = new Object[3];
        objs[0] = null;
        objs[1] = new JSONAble()
                      {
                          @Override
                          public void toJSON( JSONConfig jsonConfig, Writer json ) throws BadPropertyNameException, DataStructureLoopException, IOException
                          {
                              JSONConfig cfg = jsonConfig == null ? new JSONConfig() : jsonConfig;
                              Map<String,Object> jsonObj = new LinkedHashMap<String,Object>();
                              jsonObj.put("a", 0);
                              jsonObj.put("b", 2);
                              int[] ar = {1, 2, 3};
                              jsonObj.put("x", ar);

                              JSONUtil.toJSON(jsonObj, cfg, json);
                          }

						  @Override
						  public String toJSON()
						  {
							  return null;
						  }

						  @Override
						  public String toJSON( JSONConfig jsonConfig )
						  {
							  return null;
						  }

						  @Override
						  public void toJSON( Writer json ) throws IOException
						  {
						  }
                      };
        objs[2] = il;
        jsonObj.put("e", objs);

        JSONConfig cfg = new JSONConfig();
        String json = JSONUtil.toJSON(jsonObj, cfg);
        validateJSON(json);
        assertThat(json, is("{\"a\":1,\"b\":\"x\",\"c\":[\"1\",\"2\",\"3\"],\"d\":[\"1\",\"2\",\"3\"],\"e\":[null,{\"a\":0,\"b\":2,\"x\":[1,2,3]},[\"1\",\"2\",\"3\"]]}"));
    }

    /**
     * Test the oddball case where a map has keys which are not equal
     * but produce the same toString() output.
     */
    @Test
    public void testDuplicateKeys()
    {
        Map<Object,Object> jsonObj = new HashMap<Object, Object>();
        jsonObj.put(new DupStr(1), 0);
        jsonObj.put(new DupStr(2), 1);
        JSONConfig cfg = new JSONConfig();
        try{
            JSONUtil.toJSON(jsonObj, cfg);
            fail("Expected a DuplicatePropertyNameException to be thrown");
        }catch ( DuplicatePropertyNameException e ){
            assertThat(e.getMessage(), is("Property x occurs twice in the same object."));
        }
    }

    /**
     * Used by testDuplicateKeys().
     */
    private class DupStr
    {
        private int x;

        public DupStr( int x )
        {
            this.x = x;
        }

        @Override
        public String toString()
        {
            return "x";
        }

        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + x;
            return result;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals( Object obj )
        {
            if ( this == obj ){
                return true;
            }
            if ( obj == null ){
                return false;
            }
            if ( getClass() != obj.getClass() ){
                return false;
            }
            DupStr other = (DupStr)obj;
            if ( !getOuterType().equals(other.getOuterType()) ){
                return false;
            }
            if ( x != other.x ){
                return false;
            }
            return true;
        }

        private TestJSONUtil getOuterType()
        {
            return TestJSONUtil.this;
        }
    }
}
