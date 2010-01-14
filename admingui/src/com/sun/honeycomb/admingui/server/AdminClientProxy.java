/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package com.sun.honeycomb.admingui.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.honeycomb.adm.client.AdminClient;
import com.sun.honeycomb.adm.cli.PermissionException;


public class AdminClientProxy implements InvocationHandler {

    private Object targetObject;
    private Logger LOG;
    /**
     * Returns a new {@link AdminClient} object. 
     *
     * @param targetObject The target object being wrapped.
     * @return A new Proxy object that simulates the AdminClient interface and
     * does the appropriate error logging
     */
    public static Object newProxy(Object targetObject, Logger LOG) {
        return Proxy.newProxyInstance(
            targetObject.getClass().getClassLoader(),
            targetObject.getClass().getInterfaces(),
            new AdminClientProxy(targetObject, LOG));
    }
    
    /**
     * Creates a new instance of AdminClientProxy.
     */
    private AdminClientProxy(Object targetObject, Logger LOG) {
        this.targetObject = targetObject;
        this.LOG = LOG;
    }
    
    public Object invoke(Object proxy, Method method, Object[] args) 
        throws Throwable {
        
        try {
            long startTime = System.currentTimeMillis();

            Object value = method.invoke( targetObject, args );

            long finishTime = System.currentTimeMillis();

            // LOG.log(Level.INFO,
            // targetObject.getClass().getSimpleName() + "." + method.getName()
            // + ": " + (finishTime - startTime) + "ms execution time");

            return value;
        } catch (InvocationTargetException ite) {
            // InvocationTargetException wraps an exception that happened
            // within method.invoke(). In this case we want to throw out
            // the wrapped exception so from the outside the behavior of
            // the proxy appears identical to the wrapped object.
            Throwable t = ite.getCause();
            if (t instanceof PermissionException) {
                LOG.info("apiex: " + method.getName() + ":PermissionException");
            } else
                LOG.log(Level.SEVERE, "apiex: " + method.getName() + ":", t);
            throw t;
        } catch (Exception e) {
            // In this case something went wrong with the operation of the
            // proxy itself. Log a warning and throw out a RuntimeException.
            LOG.warning("Error in proxy execution" + e);
            throw new RuntimeException(e);
        }            
    }
}