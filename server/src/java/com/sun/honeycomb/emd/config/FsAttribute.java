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



package com.sun.honeycomb.emd.config;

import java.io.PrintStream;
import java.io.IOException;
import java.io.Writer;

public class FsAttribute {
    public static final String TAG_ATTRIBUTE = "attribute";
    public static final String ATT_NAME = "name";
    public static final String ATT_UNSET = "unset";

    private Field field;
    private String unsetString;

    private FsView fsView;
    
    public FsAttribute(Field field,
                       String unsetString,
                       FsView fsView) {
        this.field = field;
        this.unsetString = unsetString;
        this.fsView = fsView;
    }

    public void export(Writer out,
                       String prefix) 
        throws IOException {
        out.write(prefix + "<" + TAG_ATTRIBUTE + " " + ATT_NAME + 
                "=\"" + fsView.dequalifyField(field) + "\"");
        if (unsetString != null) {
            out.write(" " + ATT_UNSET + "=\"" + unsetString + "\"");
        }
        out.write("/>\n");
    }

    public Field getField() {
        return field;
    }

    public String getUnsetString() {
        return unsetString;
    }
}
