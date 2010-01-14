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


package com.sun.honeycomb.oa.bulk.stream;

import java.text.SimpleDateFormat;

/**
 * Constants class for the Bulk OA Stream.
 *
 */
public class Constants {
    
    // Termination Constants
    public static final String HEADER_TERMINATOR = "";
    public static final int    HEADER_TERMINATOR_LENGTH = 1; // 1 byte for \n
    
    // Job Headers
    public static final String VERSION_HEADER = "Version";
    public static final String CONTENT_DESCRIPTION_HEADER = "Content-Description";
    public static final String CREATION_TIME = "Creation-Time";
    public static final String START_TIME = "Start-Time";
    public static final String END_TIME = "End-Time";
   
    // Default Block Headers
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String CONTENT_LENGTH_HEADER = "Content-Length";
    public static final String ENCODING_TYPE_HEADER = "Encoding-Type";
    
    // Object Block Headers
    public static final String OID_HEADER = "OID";
    public static final String METADATA_LENGTH_HEADER = "Metadata-Length";
    public static final String N_DATA_BLOCKS_HEADER = "N-DataBlocks";
    
    // SysCache Block Headers
    public static final String NUM_CACHES_HEADER    = "Number-Caches";
    public static final String NODE_ID_HEADER       = "Node-Id";
    public static final String DISK_ID_HEADER       = "Disk-Id";
    public static final String CACHE_LENGTH_HEADER  = "Cache-Length";

   
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
}
