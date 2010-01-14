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



package com.sun.honeycomb.oa.daal.hcnfs;

import java.nio.ByteBuffer;

import com.sun.honeycomb.oa.daal.DAALException;
import com.sun.honeycomb.oa.FragmentNotFoundException;

/**
 * VFS access 
 */
interface AccessIntf {

    void create() throws DAALException;
    
    void open(boolean read_only) throws DAALException, FragmentNotFoundException;
    
    void close() throws DAALException;
    
    void delete() throws DAALException;
    
    void commit() throws DAALException;
    
    void rollback() throws DAALException;
    
    void replace(ByteBuffer buf) throws DAALException;
    
    int read(ByteBuffer buf, long offset) throws DAALException;
    
    int write(ByteBuffer buf, long offset) throws DAALException;
    
    int append(ByteBuffer buf) throws DAALException;
    
    void lock() throws DAALException;
    
    boolean unlock();
    
    long length() throws DAALException;
    
    boolean isTransient();

    boolean isCommitted();
}
