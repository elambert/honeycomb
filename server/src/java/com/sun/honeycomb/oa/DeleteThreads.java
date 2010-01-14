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



package com.sun.honeycomb.oa;

import com.sun.honeycomb.common.NewObjectIdentifier;

/**
    The base class, OAThreads, holds a list of code pools and shares 
    a static list of thread pools among its children.
*/

public class DeleteThreads
    extends OAThreads {
    
    protected static class DeleteRunnableCode
        implements OAThreads.RunnableCode {
        private Exception exception;
        private FragmentFile fragFile;
        
        private DeleteRunnableCode() {
        }
        
        private void initializeArgs(FragmentFile nFragFile) {
            fragFile = nFragFile;
            exception = null;
        }

        public void checkStatus()
            throws Exception {
            if (exception != null) {
                throw exception;
            }
        }

        public void run() {
            exception = null;
            
            fragFile.deleteContextFiles();
        }
    }
    
    public synchronized void init(OAThreadPool pool,
                                  NewObjectIdentifier oid,
                                  boolean recovery,
                                  int recoverFrag,
                                  FragmentFile[] fragFiles)
        throws OAException {
            
        pool.initInitializedFlags();

        for(int f=0; f<pool.threads.length; f++) {
            // If recovering, skip non-recovery fragments
            if(recovery && f != recoverFrag) {
                continue;
            }
                
            // Skip bad fragment files
            if ((fragFiles[f] == null) || (fragFiles[f].bad())) {
                LOG.warning("OID [" + oid + "] fragment [" + f +
                            "] is no good");
                continue;
            }
                
            // Create a new fragment deleter and start its thread
            ((DeleteRunnableCode)pool.threads[f].code).initializeArgs(fragFiles[f]);
                
            pool.initialized[f] = true;
        }
    }

    protected RunnableCode buildCode() {
        return(new DeleteRunnableCode());
    }
    
    public DeleteThreads() {
        super("Delete");
    }
}
