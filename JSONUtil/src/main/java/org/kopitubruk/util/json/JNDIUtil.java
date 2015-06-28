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

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Just a little shorthand for JNDI, used by other classes in the package.
 * This is an abridged version of this class for now, which is why it's
 * all package private.
 *
 * @author Bill Davidson
 */
class JNDIUtil
{
    private static Log s_log = LogFactory.getLog(JNDIUtil.class);

    /**
     * Shorthand to look up the java:/comp/env context.
     *
     * @return The env context.
     * @throws NamingException If there's a problem.
     */
    static Context getEnvContext() throws NamingException
    {
        return (Context)new InitialContext().lookup("java:/comp/env");
    }

    /**
     * Shorthand to look up a context inside java:/comp/env.
     *
     * @param path The path string for the JNDI context you want. Note that this
     *        is looked up relative to java:/comp/env so that you should not
     *        include that part.
     * @return The context.
     * @throws NamingException If there's a problem.
     */
    static Context getEnvContext( String path ) throws NamingException
    {
        return (Context)getEnvContext().lookup(path);
    }

    /**
     * Make an ObjectName based upon the class's canonical name.
     *
     * @param obj The object/class.
     * @return The ObjectName.
     * @throws MalformedObjectNameException If it makes a bad name.
     */
    static ObjectName getObjectName( Object obj ) throws MalformedObjectNameException
    {
        Class<?> objClass = obj instanceof Class ? (Class<?>)obj : obj.getClass();

        return new ObjectName(objClass.getPackage().getName() + ":type=" + objClass.getSimpleName());
    }

    /**
     * Shorthand for doing JNDI lookups.
     *
     * @param ctx The context.
     * @param name The name to look up.
     * @param defaultValue The result if you don't find it.
     * @return The value of the variable being looked up or defaultValue if not found.
     */
    static boolean getBoolean( Context ctx, String name, boolean defaultValue )
    {
        Object obj = getObject(ctx, name);
        return obj instanceof Boolean ? (Boolean)obj : defaultValue;
    }

    /**
     * Shorthand for doing JNDI lookups.
     *
     * @param ctx The context.
     * @param name The name to look up.
     * @param defaultValue The result if you don't find it.
     * @return The value of the variable being looked up or defaultValue if not found.
     */
    static String getString( Context ctx, String name, String defaultValue )
    {
        Object obj = getObject(ctx, name);
        return obj instanceof String ? (String)obj : defaultValue;
    }

    /**
     * Shorthand for doing JNDI lookups.  I prefer to return null for
     * things that aren't set rather than catch an exception every
     * time in other code.
     *
     * @param ctx The context.
     * @param name The name to look up.
     * @return The value of the variable being looked up or null if not found.
     */
    static Object getObject( Context ctx, String name )
    {
        Object obj;

        try{
            obj = ctx.lookup(name);
            if ( JSONConfigDefaults.getLogging() && obj != null && s_log.isDebugEnabled() ){
                s_log.debug(name+" = "+obj);
            }
        }catch ( NamingException e ){
            obj = null;
        }
        return obj;
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
        InitialContext ctx = null;
        Context result = null;

        try{
            ctx = new InitialContext();
            ctx.lookup("java:");
        }catch ( NamingException e ){
            /*
             * No initial context.  Create one.  Uses Tomcat's context factory.
             */
            System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.apache.naming.java.javaURLContextFactory");
            System.setProperty(Context.URL_PKG_PREFIXES, "org.apache.naming");
            ctx = new InitialContext();
        }

        /*
         * Each sub context level has to be created separately.
         */
        StringBuilder ctxNameBuf = new StringBuilder(contextName.length());
        boolean didStart = false;
        for ( String part : contextName.split("/") ){
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

        return result != null ? result : (Context)ctx.lookup(contextName);
    }
}
