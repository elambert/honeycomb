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



import com.sun.honeycomb.delete.Constants;

ECHO("Unit Test of NewObjectIdentifier class");

n1=new com.sun.honeycomb.common.NewObjectIdentifier(
                    "d00ef48a-fe93-11db-a77b-00e0815b0e74.1.1.5953.2.2.0.8054");

n2=new com.sun.honeycomb.common.NewObjectIdentifier(
                    "ba654c9b-fe93-11db-a980-00e0815b0c53.1.1.5953.2.2.0.8054");

if (n1.equals(n2))  {
    throw new RuntimeException(
                "NewObjectIdentifier.equals Failed: n1 cannot be equal to n2.");
}

if (!n1.equals(n1)) { 
    throw new RuntimeException(
                       "NewObjectIdentifier.equals Failed: n1 is equal to n1.");
}

if (n1.compareTo(n2) == 0) {
    throw new RuntimeException(
             "NewObjectIdentifier.compareTo Failed: n1 cannot be equal to n2.");
}
    
if (n1.compareTo(n1) != 0) {
    throw new RuntimeException(
                       "NewObjectIdentifier.compareTo Failed: n1 is equal n1.");
}
