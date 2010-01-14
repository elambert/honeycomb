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




package com.sun.honeycomb.layout;

import com.sun.honeycomb.disks.DiskId;

class PrintLayout {
    private static LayoutClient lc = LayoutClient.getInstance();
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("ERROR: Please specifiy a mapID");
        }
        
        if (args[0].equals("all")) {
            for (int mapID = 0; mapID < 10000; mapID++) {
                System.out.print(getSingleLayout(mapID));
            }
        } else {         
            int mapID = (new Integer(args[0])).intValue();
            System.out.print(getSingleLayout(mapID));
        }
    }
    
    public static String padd(int num) {
        return "0000".substring(0,4-(""+num).length()) + num;
    }
    
    public static String getSingleLayout(int mapID) {
        Layout layout;
        layout = lc.getLayoutForRetrieve(mapID);
        String result = "";
        
        for (int fragID = 0; fragID < layout.size(); fragID++) {
            DiskId disk = (DiskId)layout.get(fragID);
            if (disk != null)
                result += "MapID: " + padd(mapID) + " Frag: " + fragID + " " + disk.toStringShort() + "\n";
            else 
                result += "MapID: " + padd(mapID) + " Frag: " + fragID + "*** NULL DISK ***" + "\n";
        }
        
        return result;
    }
}
