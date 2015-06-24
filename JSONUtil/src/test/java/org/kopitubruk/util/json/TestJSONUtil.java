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
package org.kopitubruk.util.json;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

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
            String pkgName = JSONConfigDefaults.class.getPackage().getName().replaceAll("\\.", "/");

            //Context ctx =
            JNDIUtil.createContext("java:/comp/env/"+pkgName);

            // not needed -- just used to test that the context was usable.
            //ctx.bind("registerMBean", Boolean.FALSE);
        }catch ( NamingException ex ){
            s_log.error("Couldn't create context", ex);
        }
    }

    /**
     * Javascript engine to be used to validate JSON. Nashorn (Java 8) supports
     * ECMAScript 5.1. Java 7 uses Rhino 1.7, which supports something roughly
     * close to ECMAScript 3.
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
                if ( codePoint < 256 ){
                    if ( lastCode ){
                        buf.append('\n');
                    }
                    buf.appendCodePoint(codePoint);
                    lastCode = false;
                }else{
                    buf.append(String.format("\n%d U+%04X %s %d",
                                             i, codePoint,
                                             Character.getName(codePoint),
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
     * Check if the given code point is a valid start character for a
     * Javascript identifier.
     *
     * @param codePoint code point to check.
     * @return true if the codePoint is a valid start character.
     */
    private boolean isValidStart( int codePoint )
    {
        return codePoint == '_' || codePoint == '$' || Character.isLetter(codePoint);
    }

    /**
     * Check if the given code point is a valid part character for a
     * Javascript identifier.
     *
     * @param codePoint code point to check.
     * @return true if the codePoint is a valid part character.
     */
    private boolean isValidPart( int codePoint )
    {
        return Character.isDigit(codePoint) || ((((1 << Character.NON_SPACING_MARK) |
                (1 << Character.COMBINING_SPACING_MARK) |
                (1 << Character.CONNECTOR_PUNCTUATION) ) >> Character.getType(codePoint)) & 1) != 0 ||
                codePoint == 0x200C || codePoint == 0x200D;
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
        ArrayList<Integer> validStart = new ArrayList<>();
        ArrayList<Integer> validPart = new ArrayList<>();

        for ( int i = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if ( isValidStart(i) ){
                validStart.add(i);
                validPart.add(i);
            }else if ( isValidPart(i) ){
                validPart.add(i);
            }
        }
        validStart.trimToSize();
        validPart.trimToSize();

        final int MAX_LENGTH = 3;
        int[] propertyName = new int[MAX_LENGTH];
        int startIndex = 0;
        int partIndex = 0;
        int nameIndex = 0;

        Map<String,Object> jsonObj = new HashMap<>(2);

        int startSize = validStart.size();
        int partSize = validPart.size();
        propertyName[nameIndex++] = validStart.get(startIndex++);
        JSONConfig cfg = new JSONConfig();
        cfg.setQuoteIdentifier(false);
        while ( startIndex < startSize ){
            propertyName[nameIndex++] = validPart.get(partIndex++);
            if ( nameIndex == MAX_LENGTH ){
                jsonObj.clear();
                jsonObj.put(new String(propertyName,0,nameIndex), 0);
                String json = JSONUtil.toJSON(jsonObj, cfg);
                validateJSON(json);    // this makes this test take a long time to run.
                assertEquals("Object stack not cleared.", cfg.getObjStack().size(), 0);
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
        Map<String,Object> jsonObj = new HashMap<>(2);
        int[] codePoints = new int[1];
        JSONConfig cfg = new JSONConfig();

        for ( int i = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if ( ! isValidStart(i) ){
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
        Map<String,Object> jsonObj = new HashMap<>(2);
        JSONConfig cfg = new JSONConfig();

        for ( int i = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if ( ! isValidStart(i) && ! isValidPart(i) && ! (i <= 0xFFFF && Character.isSurrogate((char)i)) ){
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
            assertEquals("Object stack not cleared.", cfg.getObjStack().size(), 0);
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
        Map<String,Object> jsonObj = new HashMap<>(2);
        JSONConfig cfg = new JSONConfig();
        int j = 0;

        for ( int i = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if ( Character.isDefined(i) && ! (i <= 0xFFFF && Character.isSurrogate((char)i)) ){
                // 247650 code points, the last time I checked.
                codePoints[j++] = i;
                if ( j == codePoints.length ){
                    jsonObj.put("x", new String(codePoints,0,j));
                    validateJSON(JSONUtil.toJSON(jsonObj, cfg));
                    assertEquals("Object stack not cleared.", cfg.getObjStack().size(), 0);
                    j = 0;
                }
            }
        }
        if ( j > 0 ){
            jsonObj.put("x", new String(codePoints,0,j));
            validateJSON(JSONUtil.toJSON(jsonObj, cfg));
            assertEquals("Object stack not cleared.", cfg.getObjStack().size(), 0);
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
        Map<String,Object> jsonObj = new HashMap<>();
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
        Map<String,Object> jsonObj = new HashMap<>();
        JSONConfig cfg = new JSONConfig();
        cfg.setUseECMA6CodePoints(true);
        cfg.setEscapeNonAscii(true);
        StringBuilder buf = new StringBuilder();
        int codePoint = 0x1F4A9;
        buf.append("x");
        buf.appendCodePoint(codePoint);
        jsonObj.put("x", buf);
        String json = JSONUtil.toJSON(jsonObj, cfg);
        // Nashorn doesn't understand ECMAScript 6 code point escapes.
        //validateJSON(json);
        assertThat(json, is("{\"x\":\"x\\u{"+String.format("%X", codePoint)+"}\"}"));
        assertEquals("Object stack not cleared.", cfg.getObjStack().size(), 0);
    }

    /**
     * Test that Unicode escape sequences in identifiers work.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testEscapePassThrough() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<>();
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
        Map<String,Object> jsonObj = new HashMap<>();
        String[] strs = { "a\\u0041", "b\\x41", "c\\101", "d\\u{41}", "e\\v"};
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
            assertEquals("Object stack not cleared.", cfg.getObjStack().size(), 0);
        }
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
     * Test a byte value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testByte() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<>();
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
        Map<String,Object> jsonObj = new HashMap<>();
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
        Map<String,Object> jsonObj = new HashMap<>();
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
        Map<String,Object> jsonObj = new HashMap<>();
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
        Map<String,Object> jsonObj = new HashMap<>();
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
        Map<String,Object> jsonObj = new HashMap<>();
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
        Map<String,Object> jsonObj = new HashMap<>();
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
        Map<String,Object> jsonObj = new HashMap<>();
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
        Map<String,Object> jsonObj = new HashMap<>();
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
        Map<String,Object> jsonObj = new HashMap<>();
        float f = 1.23456f;
        jsonObj.put("x", f);
        JSONConfig cfg = new JSONConfig();
        NumberFormat fmt = NumberFormat.getInstance();
        fmt.setMaximumFractionDigits(3);
        cfg.addNumberFormat(Float.class, fmt);
        String json = JSONUtil.toJSON(jsonObj, cfg);
        validateJSON(json);
        assertThat(json, is("{\"x\":1.235}"));
        assertEquals("Object stack not cleared.", cfg.getObjStack().size(), 0);
    }

    /**
     * Test a string value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testString() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<>();
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
        Map<String,Object> jsonObj = new HashMap<>();
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
        Map<String,Object> jsonObj = new HashMap<>();
        StringBuilder buf = new StringBuilder(2);
        buf.appendCodePoint(0x1F4A9);
        jsonObj.put("x", buf);
        String json = JSONUtil.toJSON(jsonObj);
        validateJSON(json);
        assertThat(json, is("{\"x\":\"\uD83D\uDCA9\"}"));
    }

    /**
     * Test a Iterable value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testIterable() throws ScriptException
    {
        Map<String,Object> jsonObj = new HashMap<>();
        List<Integer> it = new ArrayList<>(3);
        it.add(1);
        it.add(2);
        it.add(3);
        jsonObj.put("x", it);
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
        Map<String,Object> jsonObj = new HashMap<>(4);
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
            assertEquals("Object stack not cleared.", cfg.getObjStack().size(), 0);
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

        assertEquals("Object stack not cleared.", cfg.getObjStack().size(), 0);
    }

    /**
     * Test a complex value.
     *
     * @throws ScriptException if the JSON doesn't evaluate properly.
     */
    @Test
    public void testComplex() throws ScriptException
    {
        Map<String,Object> jsonObj = new LinkedHashMap<>();
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
                              Map<String,Object> jsonObj = new LinkedHashMap<>();
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
        assertEquals("Object stack not cleared.", cfg.getObjStack().size(), 0);
    }

    /**
     * Test the oddball case where a map has keys which are not equal
     * but produce the same toString() output.
     */
    @Test
    public void testDuplicateKeys()
    {
        Map<Object,Object> jsonObj = new HashMap<>();
        jsonObj.put(new DupStr(1), 0);
        jsonObj.put(new DupStr(2), 1);
        JSONConfig cfg = new JSONConfig();
        try{
            JSONUtil.toJSON(jsonObj, cfg);
            fail("Expected a DuplicatePropertyNameException to be thrown");
        }catch ( DuplicatePropertyNameException e ){
            assertThat(e.getMessage(), is("Property x occurs twice in the same object."));
            assertEquals("Object stack not cleared.", cfg.getObjStack().size(), 0);
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
