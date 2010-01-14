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



package com.sun.honeycomb.hadb;

import com.sun.hadb.adminapi.MemberNotInThisDomainException;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.cm.ManagedServiceException;

public interface HADBMasterInterface
    extends ManagedService.RemoteInvocation, ManagedService {

    public void updateSchema()
        throws ManagedServiceException, HADBServiceException;

    public int getCacheStatus() 
        throws ManagedServiceException;

    public String getEMDCacheStatus() 
        throws ManagedServiceException;

        // FIXME - cleanup this is not needed
    public void clearFailure()
        throws ManagedServiceException;

    public void disableOneHost(int nodeId)
        throws ManagedServiceException, HADBServiceException;

    public void recoverHost(int nodeId)
        throws ManagedServiceException, HADBServiceException, 
        MemberNotInThisDomainException;

    public void recoverHostForMove(int nodeId, int newDrive)
        throws ManagedServiceException, HADBServiceException, 
        MemberNotInThisDomainException;

    public void wipeAndRestartAll()
        throws ManagedServiceException, HADBServiceException;

    /** This exception represents a fatal error in the HADB State Machine */
    public static class HADBServiceException extends Exception {
        HADBServiceException(String msg) {
            super(msg);
        }
        HADBServiceException(Throwable cause) {
            super(cause);
        }
        HADBServiceException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    public static class Proxy extends ManagedService.ProxyObject {
        
        private String jdbcURL;
        private boolean isInitializing;
        private boolean isRunning;
        private String hadbStatus;
        private boolean isDomainCleared;
        private long lastCreateTime;
        
        public Proxy(String _JdbcURL, 
                     boolean _isInitializing,
                     boolean _isRunning,
                     String _hadbStatus,
                     boolean _isDomainCleared,
                     long _lastCreateTime) {
            jdbcURL = _JdbcURL;
            isInitializing = _isInitializing;
            isRunning = _isRunning;
            hadbStatus = _hadbStatus;
            isDomainCleared = _isDomainCleared;
            lastCreateTime = _lastCreateTime;
        }
        
        public String getJdbcURL() {
            return jdbcURL;
        }
        
        public boolean isInitializing() {
            return isInitializing;
        }
        
        public boolean isRunning() {
            return isRunning;
        }
        
        public String getHadbStatus() {
            return hadbStatus;
        }

        public boolean isDomainCleared() {
            return isDomainCleared;
        }

        public long getLastCreateTime() {
            return lastCreateTime;
        }

        // Alert properties
        
        private static final Object[] properties = {
            "JDBC URL", new Integer(AlertType.STRING)
        };

        public int getNbChildren() {
            return 1;
        }

        public AlertProperty getPropertyChild(int index) 
            throws AlertException {
            if ((index < 0) || (index >= (properties.length>>1))) {
                throw new AlertException("index out of bound ["+index+"]");
            }
            
            return(new AlertComponent.AlertProperty((String)properties[index<<1],
                                                    ((Integer)properties[(index<<1)+1]).intValue()));
        }

        public String getPropertyValueString(String property) 
            throws AlertException {
                
            int index = 0;

            for (index=0; index<properties.length; index<<=1) {
                if (((String)properties[index]).equals(property)) {
                    break;
                }
            }

            if (index >= properties.length) {
                throw new AlertException("Unknown property ["+
                                         property+"]");
            }

            switch (index>>1) {
            case 0: {
                if (jdbcURL == null) {
                    return("Not yet initialized");
                } else {
                    return(jdbcURL);
                }
            }
            }

            return(null);
        }
    }
}
