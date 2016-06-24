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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MBean interface for JSONConfigDefaults to expose its methods to view and
 * modify the defaults at run time when this library is used with an MBean
 * server.
 *
 * @author Bill Davidson
 */
public interface JSONConfigDefaultsMBean
{
    /**
     * Reset all defaults to their original unmodified values.  This
     * overrides JNDI and previous MBean changes.
     */
    public void setCodeDefaults();

    /**
     * Set the default locale for new {@link JSONConfig} objects to use.
     *
     * @param languageTag A language tag suitable for use by {@link Locale#forLanguageTag(String)}.
     */
    public void setLocale( String languageTag );

    /**
     * Clear any default number formats.
     *
     * @since 1.4
     */
    public void clearNumberFormats();

    /**
     * Set the date format used for date string generation when
     * encodeDatesAsStrings or encodeDatesAsObjects is true.
     *
     * @param fmtStr passed to the constructor for
     * {@link SimpleDateFormat#SimpleDateFormat(String,Locale)} using
     * the result of {@link JSONConfigDefaults#getLocale()}.
     * @return the format that is created.
     * @since 1.4
     */
    public DateFormat setDateGenFormat( String fmtStr );

    /**
     * Clear date generation format.
     *
     * @since 1.4
     */
    public void clearDateGenFormat();

    /**
     * Add a date parsing format to the list of date parsing formats
     * used by the parser when encodeDatesAsStrings or
     * encodeDatesAsObjects is true.
     *
     * @param fmtStr Passed to
     * {@link SimpleDateFormat#SimpleDateFormat(String,Locale)} using
     * the result of {@link JSONConfigDefaults#getLocale()}.
     * @return The format that gets created.
     * @since 1.4
     */
    public DateFormat addDateParseFormat( String fmtStr );

    /**
     * Clear any date parse formats from the list of formats
     * used by the parser when encodeDatesAsStrings or
     * encodeDatesAsObjects is true.
     */
    public void clearDateParseFormats();

    /**
     * Get the default validate property names policy.
     *
     * @return The default validate property names policy.
     */
    public boolean isValidatePropertyNames();

    /**
     * Set the default flag for validation of property names.
     * This will affect all new {@link JSONConfig} objects created after this call
     * within the same class loader.
     *
     * @param dflt If true, then property names will be validated by default.
     */
    public void setValidatePropertyNames( boolean dflt );

    /**
     * Get the default detect data structure loops policy.
     * Accessible via MBean server.
     *
     * @return The default detect data structure loops policy.
     */
    public boolean isDetectDataStructureLoops();

    /**
     * Set the default flag for detecting data structure loops.  If true,
     * then if a loop in a data structure is found then a
     * {@link DataStructureLoopException} will be thrown.
     * This will affect all new {@link JSONConfig} objects created after this call
     * within the same class loader.
     *
     * @param dflt If true, then the code will detect loops in data structures.
     */
    public void setDetectDataStructureLoops( boolean dflt );

    /**
     * Get the default escape bad identifier code points policy.
     *
     * @return The default escape bad identifier code points policy.
     */
    public boolean isEscapeBadIdentifierCodePoints();

    /**
     * If true, then any bad code points in identifiers will be escaped.
     * Default is false.
     *
     * @param dflt if true, then any bad code points in identifiers will be escaped.
     */
    public void setEscapeBadIdentifierCodePoints( boolean dflt );

    /**
     * Get the full JSON identifier code points policy.
     *
     * @return the fullJSONIdentifierCodePoints
     */
    public boolean isFullJSONIdentifierCodePoints();

    /**
     * If true, then the full set of identifier code points permitted by the
     * JSON standard will be allowed instead of the more restrictive set
     * permitted by the ECMAScript standard. Use of characters not permitted by
     * the ECMAScript standard will cause an error if parsed by Javascript
     * eval().
     *
     * @param dflt If true, then allow all code points permitted by the JSON standard in identifiers.
     */
    public void setFullJSONIdentifierCodePoints( boolean dflt );

    /**
     * Get the default encode numeric strings as numbers policy.
     *
     * @return The default encode numeric strings as numbers policy.
     */
    public boolean isEncodeNumericStringsAsNumbers();

    /**
     * Set the default flag for encoding of numeric strings as numbers.
     *
     * @param dflt If true, then strings that look like valid JSON numbers
     * will be encoded as numbers.
     */
    public void setEncodeNumericStringsAsNumbers( boolean dflt );

    /**
     * Get the default escape non-ASCII policy.
     *
     * @return The default quote non-ASCII policy.
     */
    public boolean isEscapeNonAscii();

    /**
     * Set the default flag for forcing escaping of non-ASCII characters in
     * strings and identifiers. If true, then escapeSurrogates will be forced to
     * false. This will affect all new {@link JSONConfig} objects created after this
     * call within the same class loader.
     *
     * @param dflt If true, then all non-ASCII will be Unicode escaped.
     */
    public void setEscapeNonAscii( boolean dflt );

    /**
     * The default unEscape policy.
     *
     * @return the unEscape policy.
     */
    public boolean isUnEscapeWherePossible();

    /**
     * Set default flag for undoing inline escapes in strings.
     *
     * @param dflt If true then where possible, undo inline escapes in strings.
     */
    public void setUnEscapeWherePossible( boolean dflt );

    /**
     * Get the default escape surrogates policy.
     *
     * @return the escape surrogates policy.
     */
    public boolean isEscapeSurrogates();

    /**
     * Set the default escapeSurrogates policy.
     *
     * @param dflt If true, then surrogates will be escaped in strings and identifiers
     * and escapeNonAscii will be forced to false.
     */
    public void setEscapeSurrogates( boolean dflt );

    /**
     * Get the encode dates as strings policy.
     *
     * @return the encodeDatesAsStrings policy.
     */
    public boolean isEncodeDatesAsStrings();

    /**
     * Set the encodeDatesAsStrings policy.  If you set this to true, then
     * encodeDatesAsObjects will be set to false.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then {@link Date} objects will be encoded as ISO 8601 date
     * strings or a custom date format if you have called
     * {@link JSONConfigDefaults#setDateGenFormat(DateFormat)}.
     */
    public void setEncodeDatesAsStrings( boolean dflt );

    /**
     * Get the default quote identifier policy.
     *
     * @return The default quote identifier policy.
     */
    public boolean isQuoteIdentifier();

    /**
     * Set the default flag for forcing quotes on identifiers.
     * This will affect all new {@link JSONConfig} objects created after this call
     * within the same class loader.
     * <p>
     * Accessible via MBean server.
     *
     * @param dflt If true, then all identifiers will be quoted.
     */
    public void setQuoteIdentifier( boolean dflt );

    /**
     * Get the default escape ECMAScript 6 code points policy.
     *
     * @return The default escape ECMAScript 6 code points policy.
     */
    public boolean isUseECMA6();

    /**
     * If you set this to true, then when JSONUtil generates Unicode
     * escapes, it will use ECMAScript 6 code point escapes if they are shorter
     * than code unit escapes. This is not standard JSON and not yet widely
     * supported by Javascript interpreters. It also allows identifiers to have
     * letter numbers in addition to other letters.  Default is false.
     *
     * @param dflt If true, use EMCAScript 6 code point escapes and allow
     * ECMAScript 6 identifier character set.
     */
    public void setUseECMA6( boolean dflt );

    /**
     * Get the default for allowing reserved words in identifiers.
     *
     * @return the reserverd words in identifiers policy.
     */
    public boolean isAllowReservedWordsInIdentifiers();

    /**
     * Set default flag for allowing reserved words in identifiers.
     *
     * @param dflt If true, then reserved words will be allowed in identifiers.
     */
    public void setAllowReservedWordsInIdentifiers( boolean dflt );

    /**
     * Get the encode dates as objects policy.
     *
     * @return the encodeDatesAsObjects policy.
     */
    public boolean isEncodeDatesAsObjects();

    /**
     * If true, then {@link Date} objects will be encoded as
     * Javascript dates, using new Date(dateString).  If you
     * set this to true, then encodeDatesAsStrings will be
     * set to false.
     *
     * @param dflt If true, then {@link Date} objects will be encoded as
     * Javascript dates.
     */
    public void setEncodeDatesAsObjects( boolean dflt );
}
