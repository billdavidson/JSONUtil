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

import java.io.Writer;
import java.util.Arrays;

/**
 * This class is a replacement for a standard StringWriter which has some
 * inefficiency with the {@link #write(String, int, int)} method which creates a
 * substring because {@link StringBuffer} does not provide that method. This
 * gets around that by implementing just those parts of StringBuilder that it
 * needs and doing a {@link String#getChars(int, int, char[], int)} directly
 * into its char buffer. This is mainly a win for strings that contain
 * characters that need to be processed specially in some way and so use that
 * method for copying parts of those strings to the writer. For strings that
 * require no special processing, it will be about the same speed as a normal
 * StringWriter.
 * <p>
 * This class only implements those methods which are used by this package or
 * which are required by inheritance. It is not at all general and is meant to
 * provide maximum performance for this package. It makes certain assumptions
 * about its arguments which are only safe because it is only called by this
 * package.
 *
 * @author Bill Davidson
 */
class StringWriter extends Writer
{
    private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;
    private static final int INITIAL_SIZE = 16;

    private char[] value;
    private int count;

    /**
     * Create a new StringWriter.
     */
    public StringWriter()
    {
        value = new char[INITIAL_SIZE];
        count = 0;
    }

    /**
     * Write a string.
     *
     * @param str the string.
     */
    @Override
    public void write( String str )
    {
        write(str, 0, str.length());
    }

    /**
     * This avoids creating the substring that the normal StringWriter has to do
     * with this method.
     *
     * @param str the string
     * @param off the beginning index
     * @param len the number of chars to write.
     */
    @Override
    public void write( String str, int off, int len )
    {
        ensureCapacity(count + len);
        str.getChars(off, off+len, value, count);
        count += len;
    }

    /**
     * Write a single char.
     *
     * @param c the value of the char
     */
    @Override
    public void write( int c )
    {
        ensureCapacity(count + 1);
        value[count++] = (char)c;
    }

    /**
     * Very specific to this package. Write an array of chars representing a
     * single code point.
     *
     * @param cbuf a char array for a single code point.
     * @param off will always be 0.
     * @param len will always be 1 for BMP code points or 2 for a surrogate pair.
     */
    @Override
    public void write( char[] cbuf, int off, int len )
    {
        ensureCapacity(count + len);
        value[count++] = cbuf[0];
        if ( len == 2 ){
            value[count++] = cbuf[1];
        }
    }

    /**
     * Make the string and return it.
     *
     * @return the string that has been built.
     */
    @Override
    public String toString()
    {
        return new String(value, 0, count);
    }

    private void ensureCapacity( int capacity )
    {
        if ( capacity - value.length > 0 ){
            value = Arrays.copyOf(value, newCapacity(capacity));
        }
    }

    private int newCapacity( int capacity )
    {
        int newCapacity = (value.length << 1) + 2;
        if ( newCapacity - capacity < 0 ){
            newCapacity = capacity;
        }
        return (newCapacity <= 0 || MAX_BUFFER_SIZE - newCapacity < 0) ? maxCapacity(capacity) : newCapacity;
    }

    private int maxCapacity( int capacity )
    {
        if ( Integer.MAX_VALUE - capacity < 0 ){
            throw new OutOfMemoryError();
        }
        return (capacity > MAX_BUFFER_SIZE) ? capacity : MAX_BUFFER_SIZE;
    }

    @Override
    public void flush()
    {
    }

    @Override
    public void close()
    {
    }
}
