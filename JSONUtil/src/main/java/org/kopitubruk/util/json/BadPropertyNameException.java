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

import static org.kopitubruk.util.json.CodePointData.getEscapePassThroughPattern;
import static org.kopitubruk.util.json.CodePointData.getEscapePassThroughRegionLength;
import static org.kopitubruk.util.json.CodePointData.gotMatch;
import static org.kopitubruk.util.json.JSONUtil.getBundle;
import static org.kopitubruk.util.json.JSONUtil.isValidIdentifierPart;
import static org.kopitubruk.util.json.JSONUtil.isValidIdentifierStart;

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
    private JSONConfig cfg;

    /**
     * Create a new BadIdentifierCharacterException
     *
     * @param propertyName The offending property name.
     * @param cfg the config object.
     */
    BadPropertyNameException( String propertyName, JSONConfig cfg)
    {
        super(cfg);
        this.propertyName = propertyName;
        this.cfg = cfg.clone();
    }

    /**
     * Describe the problems with this would be Javascript property identifier
     * in the message.
     *
     * @param locale If non-null, use for the bundle for error messages.
     * @return The message.
     */
    @Override
    String internalGetMessage( Locale locale )
    {
        ResourceBundle bundle = getBundle(locale);

        if ( propertyName == null || propertyName.length() < 1 ){
            return bundle.getString("zeroLengthPropertyName");
        }

        if ( JSONUtil.isReservedWord(propertyName) ){
            return String.format(bundle.getString("reservedWord"), propertyName);
        }

        // HashSet discards duplicates.
        Set<Integer> badCodePoints = new LinkedHashSet<>();
        boolean badStart = false;
        boolean useSingleLetterEscapes = cfg.isFullJSONIdentifierCodePoints();
        Pattern escapePassThroughPat = getEscapePassThroughPattern(cfg, useSingleLetterEscapes);
        Matcher passThroughMatcher = escapePassThroughPat.matcher(propertyName);
        int passThroughRegionLength = getEscapePassThroughRegionLength(cfg);

        /*
         * Find the bad code points.
         */
        StringBuilder codePointList = new StringBuilder();
        CodePointData cp = new CodePointData(propertyName, cfg);
        while ( cp.nextReady() ){
            if ( cp.getCodePoint() == '\\' ){
                // check for valid escapes.
                if ( gotMatch(passThroughMatcher, cp.getIndex(), cp.end(passThroughRegionLength)) ){
                    // Skip the escape.
                    cp.setIndex(passThroughMatcher.group(1).length());
                }else{
                    // bad backslash.
                    badCodePoints.add(cp.getCodePoint());
                    if ( cp.getIndex() == 0 ){
                        badStart = true;
                    }
                }
            }else if ( cp.getIndex() == 0 && isValidIdentifierStart(cp.getCodePoint(), cfg) ){
                // OK for start character.
            }else if ( cp.getIndex() > 0 && isValidIdentifierPart(cp.getCodePoint(), cfg) ){
                // OK.
            }else{
                // bad character.
                badCodePoints.add(cp.getCodePoint());
                if ( cp.getIndex() == 0 ){
                    badStart = true;
                }
            }
            if ( cp.getIndex() > 0 ){
                codePointList.append(' ');
            }
            codePointList.append(String.format("%04X", cp.getCodePoint()));
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

    private static final long serialVersionUID = 1L;
}
