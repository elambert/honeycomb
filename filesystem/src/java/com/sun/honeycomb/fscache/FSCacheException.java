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



package com.sun.honeycomb.fscache;

import org.mortbay.http.HttpException;
import org.mortbay.http.HttpResponse;

public class FSCacheException 
    extends Exception {
    
    public static final int FSERR_PERM         = 1;
    public static final int FSERR_NOENT        = 2;
    public static final int FSERR_INVAL        = 22;
    public static final int FSERR_NOTSUPP      = 10004;
    public static final int FSERR_SERVERFAULT  = 10006;
    
    private int error;

    public FSCacheException(int nError,
                            String message) {
        super(message);
        error = nError;
    }

    public FSCacheException(Exception e) {
        super(e);
    }
    
    public int getError() { return(error); }
    

    
    public HttpException getHttpException() {
        HttpException newe = null;
        int err = HttpResponse.__500_Internal_Server_Error;

        switch (error) {
        case FSERR_PERM:
            err = HttpResponse.__403_Forbidden;
            break;

        case FSERR_NOENT:
            err = HttpResponse.__404_Not_Found;
            break;
	    
        case FSERR_INVAL:
            err = HttpResponse.__400_Bad_Request;
            break;
	    
        case FSERR_NOTSUPP:
            err = HttpResponse.__501_Not_Implemented ;
            break;
	    
        case FSERR_SERVERFAULT:
            err = HttpResponse.__500_Internal_Server_Error;
            break;
	    
        }
	
        newe = new HttpException(err, getMessage());
        newe.initCause(this);

        return(newe);
    }
}
