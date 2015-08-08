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

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exception for handling bad Javascript property identifiers for
 * {@link JSONUtil}.
 *
 * @author Bill Davidson
 */
public final class BadPropertyNameException extends JSONException
{
    /**
     * The bad property name.
     */
    private String propertyName;
    private JSONConfig jsonConfig;

    /**
     * Create a new BadIdentifierCharacterException
     *
     * @param propertyName The offending property name.
     * @param jsonConfig Used to get a locale for messages.
     */
    BadPropertyNameException( String propertyName, JSONConfig jsonConfig )
    {
        super(jsonConfig);
        this.propertyName = propertyName;
        this.jsonConfig = jsonConfig.clone();
        jsonConfig.clearObjStack();
    }

    /**
     * Describe the problems with this would be Javascript property identifier
     * in the message.
     *
     * @param locale If non-null, use for the bundle for error messages.
     * @return The message.
     */
    String internalGetMessage( Locale locale )
    {
        ResourceBundle bundle = JSONUtil.getBundle(locale);

        if ( propertyName == null || propertyName.length() < 1 ){
            return bundle.getString("zeroLengthPropertyName");
        }

        if ( JSONUtil.isReservedWord(propertyName) ){
            return String.format(bundle.getString("reservedWord"), propertyName);
        }

        // HashSet discards duplicates.
        Set<Integer> badCodePoints = new LinkedHashSet<>();
        boolean badStart = false;

        Pattern unicodeEscapePat = jsonConfig.isUseECMA6() ? JSONUtil.CODE_UNIT_OR_POINT_PAT
                                                           : JSONUtil.FREE_CODE_UNIT_PAT;

        /*
         * Find the bad code points.
         */
        int i = 0;
        StringBuilder codePointList = new StringBuilder();
        while ( i < propertyName.length() ){
            int codePoint = propertyName.codePointAt(i);
            int cc = Character.charCount(codePoint);
            if ( JSONUtil.isValidIdentifierStart(codePoint, jsonConfig) ){
                // OK for start or any other character.
            }else if ( i > 0 && JSONUtil.isValidIdentifierPart(codePoint, jsonConfig) ){
                // OK as long as not the starting character.
            }else if ( codePoint == '\\' ){
                // check for Unicode escape.
                Matcher matcher = unicodeEscapePat.matcher(propertyName.substring(i));
                if ( matcher.find() && matcher.start() == 0 ){
                    // Skip the escape.
                    i += matcher.group(1).length() - 1;
                }else{
                    // backslash is only allowed for Unicode escapes.
                    badCodePoints.add(codePoint); 
                    if ( i == 0 ){
                        badStart = true;
                    }
                }
            }else{
                // bad character.
                badCodePoints.add(codePoint);
                if ( i == 0 ){
                    badStart = true;
                }
            }
            if ( i > 0 ){
                codePointList.append(' ');
            }
            codePointList.append(String.format("%04X", codePoint));
            i += cc;
        }

        StringBuilder message = new StringBuilder();

        message.append(String.format(bundle.getString("invalidPropertyName"),
                                     propertyName.replaceAll("[\\p{Cntrl}\\p{Co}\\p{Cn}]", "."),
                                     codePointList.toString()));
        codePointList = null;

        if ( badCodePoints.size() > 0 ){
            // list the bad code points.
            String mfmt = bundle.getString("badCodePoint");
            boolean firstCodePoint = badStart;
            for ( int badCodePoint : badCodePoints ){
                String fmt;
                if ( firstCodePoint ){
                    fmt = bundle.getString("badStart");
                    firstCodePoint = false;
                }else{
                    fmt = mfmt;
                }
                message.append(String.format(fmt, badCodePoint, Character.getName(badCodePoint)));
            }
        }
        return message.toString();
    }

    @SuppressWarnings("javadoc")
    private static final long serialVersionUID = 1L;
}
