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
import com.sun.honeycomb.emd.common.EMDException;
import java.text.NumberFormat;
import java.util.Set;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Enumeration;
import java.nio.ByteBuffer;
import java.util.ListIterator;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import com.sun.honeycomb.common.SystemMetadata;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import com.sun.honeycomb.disks.DiskId;
import com.sleepycat.db.Dbt;
import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.coding.Decoder;
import com.sleepycat.db.Db;
import com.sleepycat.db.Dbc;
import com.sleepycat.db.DbException;
import com.sleepycat.db.DbEnv;
import com.sleepycat.bdb.bind.tuple.TupleOutput;
import java.util.Date;
import java.util.Calendar;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.common.NewObjectIdentifier;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Hashtable;

class DumpMetadata {
    private static boolean useOidFile=false;
    private static Hashtable useOids;
    private static Date newerThan = null;
    private static Date olderThan = null;
    private static String startDate = null;
    private static String shortName =
        DumpMetadata.class.getName().replaceAll("[A-Za-z_0-9]*[.]","");

    static final String USAGE =
        "Usage: "+shortName+" </netdisks/10.123.45.1??/data/?/> <node> <disk>\n";

    private static boolean checkMatching(NewObjectIdentifier oid) {
        //    private boolean useOidFile=false;
        //    private String oidFilename;

        if (!useOidFile) {
            return true;
        } 

        if(useOids.containsKey(oid.getUID().toString())) {
            return true;
        }

        return false;
    }

    public static void printUsage(String error) {
        if(error != null) {
            System.out.println(error+"\n");
        }
        System.out.println(USAGE);
        System.out.println("  -d Start date added to second field of each CSV entry in the format: 2005-15-09 13:16:37");
        System.out.println("  -n Only return files newer than [date]");
        System.out.println("  -e Only return files older than [date]");
        System.out.println(" -o [oidfile] path to the oid of interest file. limits to only the interesting OIDs");
    }

    public static Codable decodeDbt(Dbt dbt) {
        ByteBuffer buffer = ByteBuffer.wrap(dbt.getData());
        Codable result = new ByteBufferCoder(buffer).decodeCodable();
        return(result);
    }

    public static void verify() {
        Db db=null;
        try {
            db = new Db(null, 0);
            FileOutputStream newfile = new FileOutputStream("./outfile");
            db.verify("/export/home/jr152025/dev/unit_tests/src/java/com/sun/honeycomb/db/./MD_cache/system/system.bdb",
                      null,
                      newfile,Db.DB_AGGRESSIVE |Db.DB_PRINTABLE|Db.DB_SALVAGE
                     );
            newfile.close();
        } catch (DbException e) {
            System.err.println("That sucked. Coudln't get dbname." +
                               e.getMessage()+"]");
        } catch (FileNotFoundException e) {
            System.err.println("File not found: [" +
                               e.getMessage()+"]");
        } catch (IOException e) {
            System.err.println("IO exception: [" +
                               e.getMessage()+"]");
        }
    }
    
    public static String getInternalOidFromExternal(String oid_s) {
        String err = "";

        NewObjectIdentifier hexoid = null;

        // try the conversion from hex oid
        try {
            hexoid = NewObjectIdentifier.fromHexString(oid_s);
        } catch (Throwable t) {
            System.out.println ("internal oid conversion failed for oid: " +oid_s + " " +
                                t + "\n");
            t.printStackTrace();
            return new String ("Conversaion failure");
        }
        String[] fields = (hexoid.toString()).split("\\.");
        return fields[0];

    }


    public static void loadOidList(String oidFilename) {
        System.err.println("Dumpmetadata loading oidfile from oidFilename:" + oidFilename);
        useOids = new Hashtable();
        boolean value;
        value=true;
        try {
            File oidFile = new File (oidFilename);
            BufferedReader bfr = new BufferedReader(new FileReader(oidFile));

            String line;
            while ((line = bfr.readLine()) != null) {
                String oid = line;
                if(line.length() > 36) {
                    // it's external;convert
                    // public String getExternalOidFromInternal(String oid_s) {
                    System.err.println("attempting 4 to convert: \"" + oid+"\"");
                    oid=getInternalOidFromExternal(oid);
                } 
                // internal, no-op.
                
                useOids.put(oid,oid);
            }
        } catch (Throwable t) {
            System.out.println("Error loading oid file: " + oidFilename + " " + t.toString());
        }
        System.err.println("Done loading OID list");
    }


    private static String getDate(long seconds) {
        if (seconds<=0)
            return new String();
        Date nDate = new Date(seconds);

        Calendar cal = Calendar.getInstance();
        cal.setTime(nDate);
        StringBuffer finalDate = new StringBuffer();
        finalDate.append(cal.get(Calendar.YEAR)
                         +"-"
                         +(cal.get(Calendar.MONTH)+1) 
                         +"-"
                         + cal.get(Calendar.DAY_OF_MONTH)
                         +" "
                         +cal.get(Calendar.HOUR)
                         +":"
                         +cal.get(Calendar.MINUTE)
                         +":"
                         +cal.get(Calendar.SECOND)
                         );

        return (finalDate.toString());

        // 2005-6-28 10:34:21

    }

    public static void printSystemMetadata(SystemMetadata md) {
        System.out.print(md.getOID()+",");
        System.out.print(startDate + ",");
        System.out.print(md.getLayoutMapId()+",");
        System.out.print(md.getSize()+",");
        System.out.print(md.getReliability().getDataFragCount()+",");
        System.out.print(md.getReliability().getRedundantFragCount()+",");
        System.out.print(md.getReliability().getTotalFragCount()+",");

        System.out.print(md.getLink().getUID()+",");
        //System.out.print(getDate(md.getATime())+",");
        System.out.print(getDate(md.getRTime())+",");
        System.out.print(getDate(md.getETime())+",");
        System.out.print(md.getShred()+",");
        System.out.print(md.getContentHashString());
    }
    public static void printOid(NewObjectIdentifier oid) {
        System.out.print(oid.getUID()+",");
        System.out.print(oid.getVersion()+",");
        System.out.print(oid.getLayoutMapId()+",");
        System.out.print(oid.getObjectType()+",");
        System.out.print(oid.getChunkNumber()+",");
        System.out.print(oid.getRuleId()+",");
        System.out.print(oid.getSilolocation());        
    }
    private static boolean checkDate(long milliseconds) {
        //
        // Sometimes this isn't set; we should always include those.
        //
        if (milliseconds <=1 ) {
            return true;
        }

        boolean inRange = 
            (olderThan == null || milliseconds < olderThan.getTime()) &&
            (newerThan == null || milliseconds > newerThan.getTime());

        return inRange;
    }

    public static void dumpDb(String diskPath,int disk,int node) {
        System.err.println("Dumping: "+diskPath);
        int offset=0;
        int layout=-1;
        DbEnv dbEnv=null;

        try {
            dbEnv = new DbEnv(0);
        } catch (DbException e) {
            System.out.println("Failed to initialize Berkeley DB for cache " +
                               e.getMessage()+"]");
        }

        Db db=null;


        try {            
            int dbEnvFlags = Db.DB_INIT_MPOOL | Db.DB_PRIVATE | Db.DB_THREAD | Db.DB_CREATE | Db.DB_INIT_TXN | Db.DB_RECOVER;

            dbEnv.open(diskPath + "/MD_cache/system", 
                       dbEnvFlags, 
                       0);
            db = new Db(dbEnv, 0);
            db.open(null,  
                    "system.bdb",
                    null, 
                    Db.DB_BTREE, 
                    0,
                    0);


        } catch (DbException e) {
            System.err.println("That sucked. Coudln't open MD_cache. [" +
                               e.getMessage()+"]");
        } catch (FileNotFoundException e) {
            System.err.println("File not found: [" +
                               e.getMessage()+"]");
        }

        if(null==db) {
            System.err.println("This databse couldn't be opened: " + diskPath);
        } else {      
            try {            
                Dbt key = new Dbt();
                Dbt pkey = new Dbt();
                Dbt data = new Dbt();
                Dbc cursor = db.cursor(null, 0);
                int errorCode;
                
                errorCode = cursor.get(key,data, Db.DB_FIRST);
                
                while (errorCode != Db.DB_NOTFOUND) {
                    
                    NewObjectIdentifier oid = (NewObjectIdentifier)decodeDbt(key);                                       
                    SystemMetadata systemMetadata = (SystemMetadata)decodeDbt(data);
                    //System.out.print(getDate(md.getCTime())+",");
                    //if(!checkDate(systemMetadata.getATime())) {
                        //System.err.println("checkdate failed.");
                    //}
                    if(systemMetadata != null 
                       && checkDate(((Long) systemMetadata.get(SystemMetadata.FIELD_CTIME)).longValue()) 
                       && checkMatching(oid) ) {
                        printOid(oid);
                        System.out.print(",");
                        printSystemMetadata(systemMetadata);
                        System.out.print(",");
                        System.out.print(disk);
                        System.out.print(",");
                        System.out.print(node);
                        
                        System.out.println("");
                    }

                    errorCode = cursor.get(key,data, Db.DB_NEXT);
                }
                
            } catch (DbException e) {
                System.err.println("That sucked. Coudln't get dbname." +
                                   e.getMessage()+"]");
            }
        }

    }


    public static void main(String[] args) {
        int offset=0;
        if(args.length  < 1) {
            printUsage(null);
            System.exit(1);
        }

        if (args[0].equals("-h")) {
            printUsage(null);
            System.exit(0);
        }
        while( (offset < args.length) && (args[offset].charAt(0) == '-')) {
            if(args.length  < 1) {
                printUsage(null);
                System.exit(1);
            }
            if (args[offset].equals("-d")) {
                offset++;
                startDate = args[offset++];
                startDate += " ";
                startDate += args[offset++];
            }

            if (args[offset].equals("-n")) {
                offset++;
                String newerThanString = args[offset++];
                newerThanString += " ";
                newerThanString += args[offset++];

                try {
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    newerThan = df.parse(newerThanString);
                } catch (ParseException t) {
                    System.out.println ("can't parse short date: " +
                                        t + "\n");
                }
                System.err.println("files must be more recent than: " + newerThan);
            }
            
            if (args[offset].equals("-e")) {
                offset++;
                String olderThanString = args[offset++];
                olderThanString += " ";
                olderThanString += args[offset++];

                try {
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    olderThan = df.parse(olderThanString);
                } catch (ParseException t) {
                    System.out.println ("can't parse short date: " +
                                        t + "\n");
                }
                System.err.println("files must be older than: " + olderThan);
            }

            if (args[offset].equals("-o")) {
                offset++;
                useOidFile=true;
                loadOidList(args[offset++]);
            }

        }

        if(startDate==null) {
            System.err.println("Date must be set to use CSV output.");
            printUsage(null);
            System.exit(1);
        }

        String drivePath=args[offset++];
        int node=Integer.parseInt(args[offset++]);
        int disk=Integer.parseInt(args[offset++]);
        File f = new File(drivePath);
        dumpDb(drivePath,disk,node);
        Date now = new Date();
        System.err.println("Done @" + now.toString());
    }     
}






