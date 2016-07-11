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
 * safe (but not inside {@link JSONAble}s that have a JSONConfig object sent to
 * them and use JSONUtil to generate their JSON).
 *
 * @author Bill Davidson
 * @since 1.7
 */
public class IndentPadding implements Cloneable
{
    // config data
    private String indent;
    private String newLine;

    // operating data.
    private StringBuilder paddingBuf;
    private String padding;
    private int level;

    /**
     * Create an indent padding object using four spaces and a standard newline.
     */
    public IndentPadding()
    {
        this("    ", "\n");
    }

    /**
     * Create an indent padding object with the given indent and newLine
     * strings.
     *
     * @param indent The indent string to use for one level.
     * @param newLine The new line string.
     */
    public IndentPadding( String indent, String newLine )
    {
        this.indent = indent;
        this.newLine = newLine;

        reset();
    }

    // http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.kopitubruk.util%22%20AND%20a%3A%22JSONUtil%22
    // http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.kopitubruk.util%22%20AND%20a%3A%22JSONUtil%22

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#clone()
     */
    @Override
    public IndentPadding clone()
    {
        return new IndentPadding(indent, newLine);
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
     * Get the spacing for one indent level.
     *
     * @return the spacing for one indent level.
     */
    public String getIndent()
    {
        return indent;
    }

    /**
     * Set the spacing for one indent level (default is 4 spaces).
     *
     * @param indent The spacing for one indent level.
     */
    public void setIndent( String indent )
    {
        if ( !this.indent.equals(indent) ){
            this.indent = indent;
            paddingBuf.setLength(0);
            padding = null;
        }
    }

    /**
     * Increment the level of indent. The level is the number of indents
     * currently being used.
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
     * <p>
     * Set the string to use for a new line (default \n). A string is used so
     * that you can use \r\n or other new line sequences if you like.
     * <p>
     * For example, if you wanted a platform specific newLine, you could do
     * this:
     * <pre><code>
     *    setNewLine(String.format("%n"));
     * </code></pre>
     *
     * @param newLine The string to use for a new line (default \n)
     */
    public void setNewLine( String newLine )
    {
        this.newLine = newLine;
        paddingBuf.setLength(0);
        padding = null;
    }

    /**
     * Get the padding for the current indent level.
     *
     * @return The padding for the current indent level.
     */
    public String getPadding()
    {
        int needLen = level * indent.length();
        if ( needLen == 0 ){
            paddingBuf.setLength(0);
            if ( padding == null ){
                // first thing.
                padding = paddingBuf.toString();
                return padding;
            }
            padding = null;
        }
        needLen += newLine.length();
        if ( padding == null || padding.length() != needLen ){
            if ( paddingBuf.length() == 0 ){
                paddingBuf.append(newLine);
            }
            if ( paddingBuf.length() > needLen ){
                paddingBuf.setLength(needLen);
            }
            while ( paddingBuf.length() < needLen ){
                paddingBuf.append(indent);
            }
            padding = paddingBuf.toString();
        }
        return padding;
    }

    /**
     * Write the padding,
     *
     * @param cfg The config object.
     * @param json The writer.
     * @throws IOException if there's an I/O error.
     */
    static void appendPadding( JSONConfig cfg, Writer json ) throws IOException
    {
        IndentPadding pad = cfg.getIndentPadding();
        if ( pad != null ){
            json.write(pad.getPadding());
        }
    }

    /**
     * Increment the padding.
     *
     * @param cfg The config object.
     */
    static void incPadding( JSONConfig cfg )
    {
        IndentPadding pad = cfg.getIndentPadding();
        if ( pad != null ){
            pad.incrementLevel();
        }
    }

    /**
     * Increment the padding and write it out.
     *
     * @param cfg The config object.
     * @param json The writer.
     * @throws IOException if there's an I/O error.
     */
    static void incAppendPadding( JSONConfig cfg, Writer json ) throws IOException
    {
        IndentPadding pad = cfg.getIndentPadding();
        if ( pad != null ){
            pad.incrementLevel();
            json.write(pad.getPadding());
        }
    }

    /**
     * Increment padding and write padding if option is true.
     *
     * @param cfg The config object.
     * @param json The writer.
     * @param option flag that controls whether to do the operation or not.
     * @throws IOException if there's an I/O error.
     */
    static void incAppendPadding( JSONConfig cfg, Writer json, boolean option ) throws IOException
    {
        if ( option ){
            incAppendPadding(cfg, json);
        }
    }

    /**
     * Decrement padding and write padding if option is true.
     *
     * @param cfg The config object.
     * @param json The writer.
     * @param option flag that controls whether to do the operation or not.
     * @throws IOException if there's an I/O error.
     */
    static void decAppendPadding( JSONConfig cfg, Writer json, boolean option ) throws IOException
    {
        if ( option ){
            decAppendPadding(cfg, json);
        }
    }

    /**
     * Decrement the padding, and write it out if applicable.
     *
     * @param cfg The config object.
     * @param json The writer.
     * @throws IOException if there's an I/O error.
     */
    static void decAppendPadding( JSONConfig cfg, Writer json ) throws IOException
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
    static void decPadding( JSONConfig cfg ) throws IOException
    {
        IndentPadding pad = cfg.getIndentPadding();
        if ( pad != null ){
            pad.decrementLevel();
        }
    }

    /**
     * If the given config object has a padding object, then reset it.
     *
     * @param cfg The config object.
     */
    static void reset( JSONConfig cfg )
    {
        IndentPadding pad = cfg.getIndentPadding();
        if ( pad != null ){
            pad.reset();
        }
    }
}
