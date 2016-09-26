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

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Generate a BigObject test class.
 *
 * @author Bill Davidson
 */
public class GenerateBigObject
{
    //private static final int BOUND = Character.MAX_CODE_POINT+1;
    private static final int BOUND = Character.MIN_SUPPLEMENTARY_CODE_POINT;
    private static final int MAX_ID_LENGTH = 2;
    private static final int MAX_VAL_LENGTH = 1024;
    private static final int MAX_FIELDS = 1024;

    /**
     * Generate fields for the BigObject class.
     *
     * @param args arguments.
     * @throws IOException if there's an IO error.
     */
    public static void main( String[] args ) throws IOException
    {
        PrintWriter bigObj = new PrintWriter(
                new OutputStreamWriter(
                        new BufferedOutputStream(
                                new FileOutputStream("src/test/java/org/kopitubruk/util/json/BigObject.java")),
                        Charset.forName("UTF-8")));

        bigObj.println("\n" +
                "\n" +
                "package org.kopitubruk.util.json;\n" +
                "\n" +
                "/**\n" +
                " * Generated code - Do not modify.\n" +
                " * Used for performance testing.\n" +
                " */\n" +
                "public class BigObject\n" +
                "{");

        JSONConfig cfg = new JSONConfig();
        cfg.setEscapeNonAscii(true);
        Random rand = new Random();
        Set<String> ids = new LinkedHashSet<String>();
        String type = "String";
        String value = "1";

        for ( int i = 0; i < MAX_FIELDS; i++ ){
            String id;
            do {
                id = getId(rand);
            }while ( ids.contains(id) || JSONUtil.isReservedWord(id) );
            ids.add(id);
            value = getValue(rand, cfg);
            bigObj.println("    private "+type+" "+id+" = "+value+";");
        }

        for ( String id : ids ){
            String getterName = ReflectUtil.makeBeanMethodName(id, "get");
            bigObj.println("    /**");
            bigObj.println("     * @return the "+id);
            bigObj.println("     */");
            bigObj.println("    public "+type+" "+getterName+"()");
            bigObj.println("    {");
            bigObj.println("        return "+id+";");
            bigObj.println("    }");
        }

        bigObj.println("}");

        bigObj.close();

        Arrays.sort(escCounts);
        System.err.println("min:" +escCounts[0]);
        System.err.println("max:" +escCounts[escIndex-1]);
        System.err.println("median: " +escCounts[escIndex/2]);    // cheap and close enough
        double mean = 0;
        for ( int i = 0; i < escIndex; i++ ){
            mean += escCounts[i];
        }
        mean /= escIndex;
        System.err.println("mean: " +mean);
        double variance = 0;
        for ( int i = 0; i < escIndex; i++ ){
            double diff = mean-escCounts[i];
            variance += diff*diff;
        }
        variance /= escIndex;
        System.err.println("standard deviation: " +Math.sqrt(variance));
    }

    private static String getId( Random rand )
    {
        StringBuilder id = new StringBuilder();
        id.append(getIdentiferStart(rand));
        while ( id.length() < MAX_ID_LENGTH ){
            id.append(getIdentiferPart(rand));
        }
        return id.toString();
    }

    private static int escCounts[] = new int[MAX_FIELDS];
    private static int escIndex = 0;

    private static String getValue( Random rand, JSONConfig cfg )
    {
        Set<Character> badChars = new HashSet<>(StringProcessor.JSON_ESC_MAP.keySet());
        List<Character> escs = new ArrayList<>(badChars);
        escs.remove(Character.valueOf('/'));
        badChars.addAll(Arrays.asList(StringProcessor.LINE_SEPARATOR, StringProcessor.PARAGRAPH_SEPARATOR));

        int[] value = new int[MAX_VAL_LENGTH];
        int escBnd = 48;
        char ch0 = 0;
        int escCount = 0;
        int nextEsc = rand.nextInt(escBnd);
        for ( int i = 0; i < MAX_VAL_LENGTH; i++ ){
            int ch;

            if ( i == nextEsc ){
                ch = escs.get(rand.nextInt(escs.size()));
                nextEsc = i + 1 + rand.nextInt(escBnd);
                ++escCount;
            }else{
                do{
                    ch = rand.nextInt(BOUND);
                    if ( ch < Character.MIN_SUPPLEMENTARY_CODE_POINT ){
                        ch0 = (char)ch;
                        if ( Character.isSurrogate(ch0) ){
                            ch = 0;
                        }
                    }else{
                        ch0 = 0;
                    }
                }while ( ch < ' ' || badChars.contains(ch0) || ! Character.isDefined(ch) );
            }

            value[i] = ch;
        }
        escCounts[escIndex++] = escCount;
        System.err.println(escCount);
        return JSONUtil.toJSON(new String(value,0,MAX_VAL_LENGTH), cfg);
    }

    private static char getIdentiferStart( Random rand )
    {
        char start;

        do{
            start = (char)rand.nextInt(127);
        }while ( start <= ' ' || ! Character.isLowerCase(start) || ! Character.isJavaIdentifierStart(start) );

        return start;
    }

    private static char getIdentiferPart( Random rand )
    {
        char part;

        do{
            part = (char)rand.nextInt(127);
        }while ( part <= ' ' || ! Character.isJavaIdentifierPart(part) );

        return part;
    }
}
