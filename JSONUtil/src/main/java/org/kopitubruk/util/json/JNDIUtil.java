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

import java.util.HashMap;
import java.util.Map;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.apache.commons.logging.Log;

/**
 * Just a little shorthand for JNDI, used by other classes in the package.
 * This is an abridged version of this class for now, which is why it's
 * all package private.
 *
 * @author Bill Davidson
 */
class JNDIUtil
{
    private static boolean logging;

    private static final String ENV_CONTEXT = "java:/comp/env";

    private static final String TOMCAT_URL_PREFIXES = "org.apache.naming";
    private static final String TOMCAT_CONTEXT_FACTORY = ".java.javaURLContextFactory";

    static {
        logging = Boolean.parseBoolean(System.getProperty(JNDIUtil.class.getPackage().getName()+'.'+"logging", "true"));
    }

    /**
     * Set the logging flag.
     *
     * @param logging if true, then generate debug logging for JNDI reads.
     */
    static synchronized void setLogging( boolean logging )
    {
        JNDIUtil.logging = logging;
    }

    /**
     * Shorthand to look up the java:/comp/env context.
     *
     * @return The env context.
     * @throws NamingException If there's a problem.
     */
    static Context getEnvContext() throws NamingException
    {
        return (Context)new InitialContext().lookup(ENV_CONTEXT);
    }

    /**
     * Shorthand to look up a context relative to java:/comp/env.
     *
     * @param path The path string for the JNDI context you want. Note that this
     *        is looked up relative to java:/comp/env so you should not
     *        include that part.
     * @return The context.
     * @throws NamingException If there's a problem.
     */
    static Context getEnvContext( String path ) throws NamingException
    {
        return (Context)getEnvContext().lookup(path);
    }

    /**
     * Get all JNDI data for the given context.
     *
     * @param ctx The context.
     * @return A map of names to values.
     * @throws NamingException If there's a problem.
     */
    static Map<String,Object> getJNDIVariables( Context ctx ) throws NamingException
    {
        Map<String,Object> jndiVariables = new HashMap<String,Object>();
        NamingEnumeration<Binding> bindings = ctx.listBindings("");

        if ( bindings.hasMore() ){
            Log log = null;
            boolean doLogging = logging;
            if ( doLogging ){
                log = Logger.getLog(JNDIUtil.class);
                doLogging = log.isDebugEnabled();
                if ( ! doLogging ){
                    Logger.freeLog(JNDIUtil.class);
                    log = null;
                }
            }

            while ( bindings.hasMore() ){
                Binding binding = bindings.next();
                String name = binding.getName();
                Object obj = binding.getObject();
                if ( obj != null ){
                    if ( doLogging ){
                        log.debug(name+" = "+obj);
                    }
                    jndiVariables.put(name, obj);
                }
            }

            if ( doLogging ){
                Logger.freeLog(JNDIUtil.class);
            }
        }

        return jndiVariables;
    }

    /**
     * Get a boolean from a map or return the default value if it's not there.
     *
     * @param jndiVariables A map of JNDI variables to look things up.
     * @param name The name to look up.
     * @param defaultValue A default to return if it doesn't exist.
     * @return The value or the default if the value isn't in the map.
     */
    static boolean getBoolean( Map<String,Object> jndiVariables, String name, boolean defaultValue )
    {
        Object value = jndiVariables.get(name);
        return value instanceof Boolean ? (Boolean)value : defaultValue;
    }

    /**
     * Get a String from a map or return the default value if it's not there.
     *
     * @param jndiVariables A map of JNDI variables to look things up.
     * @param name The name to look up.
     * @param defaultValue A default to return if it doesn't exist.
     * @return The value or the default if the value isn't in the map.
     */
    static String getString( Map<String,Object> jndiVariables, String name, String defaultValue )
    {
        Object value = jndiVariables.get(name);
        return value instanceof String ? (String)value : defaultValue;
    }

    /**
     * Get a String from a map or return the default value if it's not there.
     *
     * @param jndiVariables A map of JNDI variables to look things up.
     * @param name The name to look up.
     * @param defaultValue A default to return if it doesn't exist.
     * @return The value or the default if the value isn't in the map.
     */
    static int getInt( Map<String,Object> jndiVariables, String name, int defaultValue )
    {
        Object value = jndiVariables.get(name);
        return value instanceof Integer ? (Integer)value : defaultValue;
    }

    /**
     * Create the given context. This is primarily meant for use by JUnit tests
     * which don't have a JNDI context available.
     *
     * @param contextName The full name: "java:/comp/env/..."
     * @param factory the context factory.
     * @param prefixes The prefixes for your factory.
     * @return The final subcontext.
     * @throws NamingException If there's a problem.
     */
    static Context createContext( String contextName, String factory, String prefixes ) throws NamingException
    {
        InitialContext ctx = null;
        Context result = null;
        String[] parts = contextName.split("/");

        try{
            ctx = new InitialContext();
            ctx.lookup(parts[0]);
        }catch ( NamingException e ){
            /*
             * No initial context.  Create one with the given factory/prefixes.
             */
            System.setProperty(Context.INITIAL_CONTEXT_FACTORY, factory);
            System.setProperty(Context.URL_PKG_PREFIXES, prefixes);
            ctx = new InitialContext();
        }

        /*
         * Each sub context level has to be created separately.
         */
        StringBuilder ctxNameBuf = new StringBuilder(contextName.length());
        boolean didStart = false;
        for ( String part : parts ){
            if ( didStart ){
                ctxNameBuf.append('/');
            }else{
                didStart = true;
            }
            ctxNameBuf.append(part);
            String ctxName = ctxNameBuf.toString();
            try{
                ctx.lookup(ctxName);
            }catch ( NamingException e ){
                result = ctx.createSubcontext(ctxName);
            }
        }

        return result != null && result.toString().equals(contextName) ? result : (Context)ctx.lookup(contextName);
    }

    /**
     * Create the given context. This is primarily meant for use by JUnit tests
     * which don't have a JNDI context available. If you use this, you will need
     * Tomcat's catalina.jar in your runtime class path because it uses Tomcat's
     * javaURLContextFactory.
     *
     * @param contextName The full name: "java:/comp/env/..."
     * @return The final subcontext.
     * @throws NamingException If there's a problem.
     */
    static Context createContext( String contextName ) throws NamingException
    {
        return createContext(contextName, TOMCAT_URL_PREFIXES + TOMCAT_CONTEXT_FACTORY, TOMCAT_URL_PREFIXES);
    }

    /**
     * Create the given context under java:/comp/env/. This is primarily meant
     * for use by JUnit tests which don't have a JNDI context available. If you
     * use this, you will need Tomcat's catalina.jar in your runtime class path
     * because it uses Tomcat's javaURLContextFactory.
     *
     * @param contextName The name relative to "java:/comp/env/"
     * @return The final subcontext.
     * @throws NamingException If there's a problem.
     */
    static Context createEnvContext( String contextName ) throws NamingException
    {
        return createContext(ENV_CONTEXT+'/'+contextName);
    }
}
