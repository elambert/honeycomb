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



package com.sun.honeycomb.hwprofiles;

final class Partition implements java.io.Serializable {

    private int number;
    private String exportPrefix;
    private int type;

    // "A partition descriptor" is a comma-separated string of values:
    // on Linux, (start, size) and on Solaris (tag, start, size).
    // The units on Linux are MB, and on Solaris it's cylinders
    // (where 1 cylinder is about 8 MB)
    //
    // Examples:
    //     ",1024"	start: default; size: 1024MB
    //     ","		start: default; size: default i.e. remaining free space
    //
    //     "root,,128	tag: V_ROOT; start: default; size: 128c = 1024MB
    //     "home,,"     tag: V_HOME; start: default; size: default

    private String partitionCommand;

    Partition() {
        this(-1, "", "", -1);
    }

    Partition(int number, String exportPrefix, 
	      String partitionCommand, int type) {
	this.number = number;
	this.exportPrefix = exportPrefix;
	this.partitionCommand = partitionCommand;
        this.type = type;
    }

    int getNumber() { return number; }
    String getPathPrefix() { return exportPrefix; }

    String getPartitionCommand() { return partitionCommand; }
    int getType() { return type; }

}

// 456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789
