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





package com.sun.honeycomb.suitcase;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.*;
import java.util.Date;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Hashtable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.oa.checksum.ChecksumAlgorithm;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.oa.OAClient;
import java.util.Properties;



/*
 * Extract data invariants; n,m, 
 * OA_FRAGMENT_SIZE
 * OA_MAX_CHUNK_SIZE 
*/
class ClusterParameterExtractor {



    public static void main(String[] args) {

        int offset=0;


        System.err.println("ClusterParamaterExtractor");
        OAClient client = OAClient.getInstance();
        System.err.println("OA_FRAGMENT_SIZE:" + client.OA_FRAGMENT_SIZE);
        System.err.println("OA_MAX_CHUNK_SIZE:" + client.OA_MAX_CHUNK_SIZE);
        System.err.println("blockSize:" + client.blockSize);
        System.err.println("OA_WRITE_UNIT:" + client.OA_WRITE_UNIT);
        System.out.println(client.OA_FRAGMENT_SIZE + "," +
                           client.OA_MAX_CHUNK_SIZE+ "," +
                           client.blockSize+ "," +
                           client.OA_WRITE_UNIT);
        Date now = new Date();
        System.err.println("Done @" + now.toString());
    }
}
