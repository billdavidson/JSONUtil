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

import java.io.IOException;
import java.io.Writer;

/**
 * This class provides a way to do indented formatting of the output to make it
 * easier to read for debugging. Each item is on its own line and indented to
 * show nesting.
 * <p>
 * To use this, create the object and set any parameters you like and call
 * {@link JSONConfig#setIndentPadding(IndentPadding)} to add it to your config
 * object and use it when you convert to JSON.
 * <p>
 * This class is NOT thread safe. Do not reuse this object in different threads.
 * If you reuse JSONConfig objects in the same thread, then you should probably
 * call {@link #reset()} before calling JSONUtil's toJSON() methods just to be
 * safe (but not inside {@link JSONAble}s that have a JSONConfig object sent
 * to them).
 *
 * @author Bill Davidson
 * @since 1.7
 */
public class IndentPadding implements Cloneable
{
    // config data
    private int indent;
    private char space;
    private String newLine;

    // operating data.
    private StringBuilder paddingBuf;
    private String padding;
    private int level;

    /**
     * Create an indent padding object.
     */
    public IndentPadding()
    {
        indent = 4;
        space = ' ';
        newLine = "\n";

        reset();
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#clone()
     */
    public IndentPadding clone()
    {
        IndentPadding result = new IndentPadding();

        // copy the config data only.
        result.indent = indent;
        result.space = space;
        result.newLine = newLine;

        return result;
    }

    /**
     * Reset this object's operating data.
     */
    public void reset()
    {
        level = 0;
        paddingBuf = new StringBuilder();
        padding = null;
    }

    /**
     * Get the number of space characters to use for each indent level.
     *
     * @return The number of space characters to use for each indent level.
     */
    public int getIndent()
    {
        return indent;
    }

    /**
     * Set the number of space characters to use for each indent level (default
     * 4).
     *
     * @param indent The number of space characters to use for each indent
     *            level.
     */
    public void setIndent( int indent )
    {
        this.indent = Math.max(indent, 0);
    }

    /**
     * Get the space character for indenting.
     *
     * @return the space character for indenting.
     */
    public char getSpace()
    {
        return space;
    }

    /**
     * Set the space character for indenting (default is space).
     *
     * @param space The space character for indenting.
     */
    public void setSpace( char space )
    {
        if ( this.space != space ){
            this.space = space;
            paddingBuf.setLength(0);
            padding = null;
        }
    }

    /**
     * Increment the level of indent.  The level is the number
     * of indents currently being used.
     */
    public void incrementLevel()
    {
        ++level;
    }

    /**
     * Decrement the level of indent.
     */
    public void decrementLevel()
    {
        if ( level > 0 ){
            --level;
        }
    }

    /**
     * Get the string to use for a new line (default \n)
     *
     * @return the string to use for a new line (default \n)
     */
    public String getNewLine()
    {
        return newLine;
    }

    /**
     * Set the string to use for a new line (default \n).
     *
     * @param newLine The string to use for a new line (default \n)
     */
    public void setNewLine( String newLine )
    {
        this.newLine = newLine;
    }

    /**
     * Get the padding for the current indent level.
     *
     * @return The padding for the current indent level.
     */
    public String getPadding()
    {
        int needLen = level * indent;
        if ( needLen == 0 ){
            paddingBuf.setLength(0);
            if ( padding == null ){
                // first thing.
                padding = paddingBuf.toString();
                return padding;
            }
            padding = null;
        }
        ++needLen;
        if ( padding == null || padding.length() != needLen ){
            if ( paddingBuf.length() == 0 ){
                paddingBuf.append(newLine);
            }
            if ( paddingBuf.length() > needLen ){
                paddingBuf.setLength(needLen);
            }
            while ( paddingBuf.length() < needLen ){
                paddingBuf.append(space);
            }
            padding = paddingBuf.toString();
        }
        return padding;
    }

    /**
     * Increment the padding and return it if applicable.
     *
     * @param cfg The config object.
     * @return The padding.
     */
    public static String incPadding( JSONConfig cfg )
    {
        IndentPadding pad = cfg.getIndentPadding();
        String padding;
        if ( pad != null ){
            pad.incrementLevel();
            padding = pad.getPadding();
        }else{
            padding = "";
        }
        return padding;
    }

    /**
     * Increment the padding, write it out and return it if applicable.
     *
     * @param cfg The config object.
     * @param json The writer.
     * @throws IOException if there's an I/O error.
     */
    public static void incPadding( JSONConfig cfg, Writer json ) throws IOException
    {
        IndentPadding pad = cfg.getIndentPadding();
        if ( pad != null ){
            pad.incrementLevel();
            json.write(pad.getPadding());
        }
    }

    /**
     * Decrement the padding, and write it out if applicable.
     *
     * @param cfg The config object.
     * @param json The writer.
     * @throws IOException if there's an I/O error.
     */
    public static void decPadding( JSONConfig cfg, Writer json ) throws IOException
    {
        IndentPadding pad = cfg.getIndentPadding();
        if ( pad != null ){
            pad.decrementLevel();
            json.write(pad.getPadding());
        }
    }

    /**
     * Decrement the padding and return it.
     *
     * @param cfg The config object.
     * @throws IOException if there's an I/O error.
     */
    public static void decPadding( JSONConfig cfg ) throws IOException
    {
        IndentPadding pad = cfg.getIndentPadding();
        if ( pad != null ){
            pad.decrementLevel();
        }
    }
}
