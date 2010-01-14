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
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Date;
import java.util.BitSet;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TimeZone;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.oa.checksum.ChecksumAlgorithm;
import com.sun.honeycomb.common.NewObjectIdentifier;


class PathData{
    String f_node,f_disk,f_oid,f_version,f_objecttype,f_chunknumber,f_layoutmapid,f_fragnum,f_tmpclose,f_cellid;    
}



class FragExplorer {

    public static void printUsage() {
        System.err.println("Usage: FragExplorer [-h] [-v] [-c] [-d date] [-e date] [-n date] [-l numFiles] [-o oidfile] nodenumber path");
        System.err.println("  -h this info");
        System.err.println("  -d Start date appended to each CSV line in format: 2005-15-09 13:16:37");
        System.err.println("  -n Only return files newer than [date]");
        System.err.println("  -e Only return files older than [date]");
        System.err.println("  -v debugging info (will fail silently with exceptions if this isn't enabled)");
        System.err.println("  -c outputs CSV format");
        System.err.println("  -l limit to processing this many files (for testing)");
        System.err.println("   Field order:");
        System.err.println("    oid,capturedate,layoutmapid,size,fragnum,fragdatalen,link," + 
                           "ctime,rtime,etime,sched,checksumalg,hashalg,contenthashstring,"+
                           "deletedstatus(t/f flag),version,autoCloseTime,deletionTime,"+
                           "shred,refcount,maxrefcount,deletedrefs,numpreceedingchecksums,"+
                           "chunksize,footerchecksum,mdfield");

    }

    public static void main(String[] args) {

        int offset=0;

        FragExplorer explorer=new FragExplorer();

        while( (offset < args.length) && (args[offset].charAt(0) == '-')) {
            if(args.length  < 2) {
                printUsage();
                System.exit(1);
            }
        
            if (args[offset].equals("-h")) {
                offset++;
                printUsage();
                System.exit(0);
            }
            if (args[offset].equals("-c")) {
                offset++;
                explorer.csvFormat=true;
            }
        
            if (args[offset].equals("-v")) {
                offset++;
                explorer.verboseLogging = true;
            }

            if (args[offset].equals("-d")) {
                offset++;
                startDate = args[offset];
                startDate += " ";
                offset++;
                startDate += args[offset];
                offset++;
            }

            if (args[offset].equals("-n")) {

                offset++;
                String newerThanString = args[offset];
                newerThanString += " ";
                offset++;
                newerThanString += args[offset];
                offset++;


                try {
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    df.setTimeZone(TimeZone.getDefault());
                    newerThan = df.parse(newerThanString);
                } catch (ParseException t) {
                    System.err.println ("can't parse short date: " +
                                        t + "\n");
                }

                System.err.println("files must be more recent than: " + newerThan);
            }
            
            if (args[offset].equals("-l")) {
                offset++;
                fileLimit = Integer.decode(args[offset]).intValue();
                offset++;
            }

            if (args[offset].equals("-o")) {
                offset++;
                useOidFile=true;
                explorer.loadOidList(args[offset]);
                offset++;
            }
            if (args[offset].equals("-e")) {
                offset++;
                String olderThanString = args[offset];
                olderThanString += " ";
                offset++;
                olderThanString += args[offset];
                offset++;


                try {
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    df.setTimeZone(TimeZone.getDefault());
                    olderThan = df.parse(olderThanString);
                } catch (ParseException t) {
                    System.err.println ("can't parse short date: " +
                                        t + "\n");
                }

                System.err.println("files must be older than: " + olderThan);

            }
        }
        if(startDate==null && explorer.csvFormat==true) {
            System.err.println("Date must be set to use CSV output.");
            System.exit(1);
        }
        
        nodeNumber=Integer.parseInt(args[offset]);
        offset++;

        if(useOidFile!= true) {
            //
            // First arguement should be node number
            //
            for(int i=offset;i<args.length;i++) {
                File rootFile = new File(args[i]);
                System.err.println("Starting with rootfile: " + args[i]);
                explorer.processFile(rootFile);
            }
        } else {
            explorer.exploreList();
        }
        Date now = new Date();
        System.err.println("Done @" + now.toString());
    }


    private static boolean useOidFile=false;
    private ArrayList useOids;
    private static int nodeNumber;
    private static String startDate = null;
    private static Date newerThan = null;
    private static Date olderThan = null;
    private static int filesProcessed=0,fileLimit=-1;
    private static int filesPrinted=0;
    private static final int PROGRESS_METER=100;
    public static boolean csvFormat=false;
    public static boolean verboseLogging = false;
    private FragmentFileSubclass ffs = null;
    private SystemMetadata sm = null;

    public String getExternalOidFromInternal(String oid_s) {
        String err = "";

        NewObjectIdentifier hexoid = null;

        // try the conversion from hex oid
        try {
            hexoid = new NewObjectIdentifier(oid_s);
        } catch (Throwable t) {
            System.err.println ("external oid 2 conversion failed for hex oid: " +oid_s + " " +
                                t + "\n");
            t.printStackTrace();
            return new String ("Conversaion failure");
        }

        return hexoid.toHexString();

    }


    public String getInternalOidFromExternal(String oid_s) {
        String err = "";

        NewObjectIdentifier hexoid = null;

        // try the conversion from hex oid
        try {
            hexoid = NewObjectIdentifier.fromHexString(oid_s);
        } catch (Throwable t) {
            System.err.println ("internal oid conversion failed for oid: " +oid_s + " " +
                                t + "\n");
            t.printStackTrace();
            return new String ("Conversaion failure");
        }
        //        String[] fields = (hexoid.toString()).split("\\.");
        //        return fields[0];
        return hexoid.toString();

    }

    private String getDate(long milliseconds) {
        if (milliseconds<=0)
            return new String();
        Date nDate = new Date(milliseconds);

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

    private PathData parsePath(String path) {
        PathData pathData = new PathData();


        /*
          from NewObjectIdentifier.toString()
0           return uid.toString() + "." + 
1           cellId + "." +
2           version + "." +
3           objectType + "." +
4           chunkNumber + "." +
5           layoutMapId;
        */

        try {        
            Pattern p = Pattern.compile("/");
            String [] splitStrings = p.split(path);
        
            if(splitStrings.length >=7) {
                Pattern q = Pattern.compile("\\.");
                String [] splitDots;

                if( splitStrings.length == 7 ) {
                    pathData.f_tmpclose="true";
                    splitDots = q.split(splitStrings[6]);
                } else {
                    splitDots = q.split(splitStrings[7]);
                    pathData.f_tmpclose="false";
                }

                if(splitStrings[2].charAt(11)=='1') {             
                    pathData.f_node=splitStrings[2].substring(11,13);
                } else {
                    Character ch=new Character(splitStrings[2].charAt(12));
                    pathData.f_node = ch.toString();
                }

                pathData.f_disk=splitStrings[4];
                pathData.f_oid=splitDots[0];
                pathData.f_cellid=splitDots[1];
                pathData.f_version=splitDots[2];
                pathData.f_objecttype=splitDots[3];
                pathData.f_chunknumber=splitDots[4];

                String [] underStrings = splitDots[5].split("_");
                pathData.f_layoutmapid=underStrings[0];
                pathData.f_fragnum=underStrings[1];


            
            } else {
                return (PathData)null;
            }
            return pathData;
        } catch (Throwable t) {
            t.printStackTrace();
            return (PathData)null;
        }
    }



    private boolean tryFragment(final File ourFrag) {
        ffs=null;
        sm=null;

        try {
            ffs = new FragmentFileSubclass(ourFrag);
            sm = ffs.readSystemMetadata();
        } catch (com.sun.honeycomb.oa.DeletedFragmentException dfe) {
            // don't care; fragment should be populated anyhow
            sm = ffs.getSystemMetadata();
        } catch (Exception t) {
            t.printStackTrace();
            return false;
        }
        System.out.flush();
        return true;
    }

    public void loadOidList(String oidFilename) {
        useOids = new ArrayList();
        boolean value;
        value=true;
        try {
            File oidFile = new File (oidFilename);
            BufferedReader bfr = new BufferedReader(new FileReader(oidFile));

            String line;
            while ( (line = bfr.readLine()) != null){
                if((line.length() > 1)) {
                    String oid = line;
                    if(line.length() > 36) {
                        // it's external;convert
                        oid=getInternalOidFromExternal(oid);
                    } 
                    // internal, no-op.
                
                    useOids.add(oid);
                }
            }
        } catch (Throwable t) {
            System.err.println("Error loading oid file: " + oidFilename + " " + t.toString());
        }

    }

    private boolean checkDate(long milliseconds) {
        //
        // Sometimes this isn't set; we should always include those.
        //
        if (milliseconds <=1 ) {
            return true;
        }

        boolean inRange = 
            (olderThan == null || milliseconds < olderThan.getTime()) &&
            (newerThan == null || milliseconds > newerThan.getTime());

        /*
        System.err.println(
                           "checkDate("
                           + (olderThan == null ? "null" : Long.toString(olderThan.getTime()))
                           + " < "
                           + Long.toString(milliseconds)
                           + " < "
                           + (newerThan == null ? "null" : Long.toString(newerThan.getTime()))
                           + ")->"
                           + Boolean.toString(inRange)
                           );
        */

        return inRange;
    }

    //
    // Unlike the rest of the funcitons, exploreList don't work on 
    // a non-running cluster.
    //
    private void exploreList() {
        
        //
        // Decode oid into layouts here
        //
        Iterator i = useOids.iterator();
        while (i.hasNext()) {
            String curOid = (String) i.next();

            NewObjectIdentifier oid = null;
        
            
            try {
                oid = new NewObjectIdentifier(curOid);
            } catch (Throwable t) {
                System.err.println ("external 3 oid conversion failed for hex oid: " +curOid + " " + t.toString());
                System.exit(1);
            }

            
            Layout layout= LayoutClient.getInstance().getLayoutForRetrieve(oid.getLayoutMapId());
            Iterator j=layout.iterator();

            //
            // Once you have a loop that gives node/disk, do check to see if your
            // node has any segments we're interested in
            // 
            
            int fragnum=0;
            while (j.hasNext()) {
                DiskId curDiskId = (DiskId) j.next();

                if(curDiskId.nodeId() == nodeNumber+100) {
                    String path=new String();

                    path += "/netdisks/10.123.45." + (nodeNumber+100);
                    path += "/data/"+curDiskId.diskIndex();
                    path += "/";
                    String[] fields = oid.toString().split("\\.");

                    //
                    //  leading zeros trimmed.
                    //
                    int indexInt=Integer.parseInt(fields[5]);                   
                    int zeroCount=4-fields[5].length();
                    int zeroRemainingCount=zeroCount;

                    for (int charCount=0;charCount < 4;charCount++){
                        if(zeroRemainingCount > 0) {
                            path += "0";
                            zeroRemainingCount--;                           
                        } else {
                            path += fields[5].substring(charCount-zeroCount,(charCount-zeroCount)+1);                
                        }
                        if(charCount==1 || charCount == 3) {
                            path += "/";
                        }
                    }
                    //
                    // Frag number
                    //
                    path += oid.toString();
                    path += "_" + fragnum;

                    File nfile = new File(path);
                    processFile(nfile);
                    
                }
                fragnum++;
            }            
        }
    }

    private static Pattern filter = Pattern.compile("^/netdisks/10\\.123\\.45\\.1[0-9]{2}/data/[0123](/([0-9]{2}(/([0-9]{2}(/(.*)?)?)?)?)?)?$");

    private void processFile(final File fileOrDirectory) {
        Matcher matcher = filter.matcher(fileOrDirectory.toString());
        if (!matcher.matches()) {
            if (verboseLogging) {
                System.err.println("skippping " + fileOrDirectory.toString());
            }
            return;
        }

        boolean isDirectory = fileOrDirectory.isDirectory();
        String path = null;
        filesProcessed+=1;

        try {
            path = fileOrDirectory.getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException("failed to get canonical path for directory " +
                                       fileOrDirectory);
        }


        /*
        if (isDirectory && (path.indexOf("MD_cache") != -1 ||
                            path.indexOf("lost+found") != -1 ||
                            path.indexOf("oaserver") != -1 ||
                            path.indexOf("hadb") != -1 ||
                            path.indexOf("tmp-close") != -1 ||
                            path.indexOf("tmp-timeout") != -1)) {
            if (verboseLogging) {
                System.err.println("skipping " + path);
            }
            return;
        }
        */
        
        if (verboseLogging) {
            System.err.println(((isDirectory) ? "" : "    ") +
                               "loading " +
                               path);
        }

        if (isDirectory) {
            loadDirectory(fileOrDirectory);
        } else {

            String fragname = fileOrDirectory.toString();
            PathData pathData = parsePath(fragname);

            if (null != pathData) {

                //
                // has the slightly ugly - but efficent -
                // side effect of populating the data required by
                // printFooterData.
                //
                if (tryFragment(fileOrDirectory)) {
                    Long ctime_Long=((Long)sm.get(SystemMetadata.FIELD_CTIME));
                    if(checkDate(ctime_Long.longValue())) {

                        if(csvFormat ==true) {
                            System.out.print(fileOrDirectory.toString() +                                        
                                             "," +
                                             startDate +
                                             "," +
                                             fileOrDirectory.length() +
                                             "," +
                                             getDate(fileOrDirectory.lastModified())
                                             +",");
                        
                        } else {
                            System.out.println("Fragment path: " +fileOrDirectory.toString());
                            System.out.println("Fragment size: " +fileOrDirectory.length());
                            System.out.println("Last modified date: " +getDate(fileOrDirectory.lastModified()));
                        }
                        printPath(pathData);
                        printFooterData(fileOrDirectory);
                        System.out.println();                
                    }

                }

                if (++filesPrinted % PROGRESS_METER == 0) {
                    System.err.println("[" + getDatetime() + "] printed " + Integer.toString(filesPrinted));
                }

            }
        }


        if (verboseLogging && isDirectory) {
            System.err.println("done with " + path);
        }
    }

    private static String getDatetime()
    {

        return formatDatetime(System.currentTimeMillis());
        /*
        DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
        DateFormat tf = DateFormat.getTimeInstance(DateFormat.LONG);
        Date date = new Date();
        return df.format(date) + " " + tf.format(date);
        */
    }

    private static String formatDatetime(long millis)
    {
        DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
        DateFormat tf = DateFormat.getTimeInstance(DateFormat.LONG);
        Date date = new Date(millis);
        return df.format(date) + " " + tf.format(date);
    }


    private void loadDirectory(final File directory) {
        File[] contents = directory.listFiles();
        if (contents != null) {
            for (int i = 0; i < contents.length; i++) {
                processFile(contents[i]);
                filesProcessed = filesProcessed+1;
                if ( (fileLimit != -1) && 
                     (filesProcessed >= fileLimit)) {
                    System.exit(0);
                }
            }
        }
    }


    /*
     * Hack to call a private, non-static constructor from static context
     * 10/23/06 - FIXME this hack does not work anymore. The same hack
     * is everywhere in the test framework.
     */

    public static class FragmentFileSubclass extends FragmentFile {
        public FragmentFileSubclass(File f) {
            super();
            //namef = f;
        }

        public FragmentFooter getFragmentFooter() {
            return(fragmentFooter);
        }
    }


    private void printPath(PathData pathData) {
        if(csvFormat ==true) {
            System.out.print(pathData.f_node+","+
                             pathData.f_disk+","+
                             pathData.f_oid+","+
                             pathData.f_version+","+
                             pathData.f_objecttype+","+
                             pathData.f_chunknumber+","+
                             pathData.f_layoutmapid+","+
                             pathData.f_cellid+","+
                             pathData.f_fragnum+","+
                             pathData.f_tmpclose);
        } else {
            System.out.println("Node: " + pathData.f_node);
            System.out.println("Disk: "+pathData.f_disk);
            System.out.println("Oid: "+pathData.f_oid);
            System.out.println("Version: "+pathData.f_version);
            System.out.println("ObjectType: "+pathData.f_objecttype);
            System.out.println("Chunknumber: "+pathData.f_chunknumber);
            System.out.println("Layout Map Id:"+pathData.f_layoutmapid);
            System.out.println("Cell id: "+pathData.f_cellid);
            System.out.println("Fragnum: "+pathData.f_fragnum);
            System.out.println("tmp_close: "+pathData.f_tmpclose);
        } 

    }



    private void printFooterData(final File ourFrag) {
        if(null==ffs || null==sm) {
            System.err.println("This shouldn't be possible. call printFooterData only after populating this.");
            System.exit(1);
        }



        if(csvFormat ==false) {
            if(sm.getOID() != null )
                System.out.println("oid: "+sm.getOID());//92211ed7-d92d-11d9-bcd8-0800209fc330.1.1.0.5           
            else
                System.out.println("oid: ");//92211ed7-d92d-11d9-bcd8-0800209fc330.1.1.0.5                           
            System.out.println("External OID: " + getExternalOidFromInternal(sm.getOID().toString()));
            System.out.println("layoutmapid: "+sm.getLayoutMapId());//5
            System.out.println("size:"+sm.getSize());//890573
            //            System.out.println("Data reliability: "+sm.getReliability()); //data(n): 5
            System.out.println("fragdatalen: "+sm.getFragDataLen());//2

            if(sm.getLink()!=null)
                System.out.println("link: "+sm.getLink());//0
            else
                System.out.println("link: ");

            System.out.println("ctime: "+getDate( ((Long)sm.get(SystemMetadata.FIELD_CTIME)).longValue()  ));
            System.out.println("rtime: "+getDate(sm.getRTime()));//                
            System.out.println("etime: "+getDate(sm.getETime()));
            System.out.println("sched: "+sm.getShred());


            System.out.println("checksumalg: "+ChecksumAlgorithm.getName(sm.getChecksumAlg()));

            if(sm.getHashAlgorithm()!=null)
                System.out.println("hashalg: "+sm.getHashAlgorithm());
            else
                System.out.println("hashalg: ");
            if(sm.getContentHashString()!=null)
                System.out.println("contenthashstring: "+sm.getContentHashString());           
            else
                System.out.println("contenthashstring: ");
            FragmentFooter footer=ffs.getFragmentFooter();
            System.out.print("deleted status: ");
            if(footer.isDeleted()==true) {
                System.out.println("true");
            } else {
                System.out.println("false");
            }
            System.out.println("version: "+footer.version);
            System.out.println("autoCloseTime: "+getDate(footer.autoCloseTime));
            System.out.println("deletionTime: "+getDate(footer.deletionTime));
            System.out.println("shred: "+footer.shred);
            System.out.println("refCount: "+footer.refCount);
            System.out.println("maxRefCount: "+footer.maxRefCount);

            if(null!=footer.deletedRefs)
                System.out.println("deletedRefs: "+footer.deletedRefs);
            else
                System.out.println("deletedRefs: ");

            System.out.println("numPreceedingChecksums: "+footer.numPreceedingChecksums);
            System.out.println("chunksize: "+footer.chunkSize);                     
            System.out.println("footerChecksum: "+Integer.toHexString(footer.footerChecksum));
            System.out.print("mdfield: ");
            for(int i=0;i< footer.metadataField.length;i++) {
                System.out.print(footer.metadataField[i]);
                if(i+1!=footer.metadataField.length) {
                    System.out.print("-");
                }
            }
            System.out.println("");
        } else {
            System.out.print(",");
            if(sm.getOID()!=null)
                System.out.print(sm.getOID()+",");//92211ed7-d92d-11d9-bcd8-0800209fc330.1.1.0.5                      
            else
                System.out.print(",");
            System.out.print(getExternalOidFromInternal(sm.getOID().toString())+",");
            System.out.print(sm.getLayoutMapId()+",");//5                   
            System.out.print(sm.getSize()+",");//890573
            System.out.print(sm.getFragDataLen()+",");//2
            if(sm.getLink() != null)
                System.out.print(sm.getLink()+",");//0
            else
                System.out.print(",");
            
            System.out.print(getDate( ((Long)sm.get(SystemMetadata.FIELD_CTIME)).longValue())   +",");
            System.out.print(getDate(sm.getRTime())+",");                    
            System.out.print(getDate(sm.getETime())+",");
            System.out.print(sm.getShred()+",");
            System.out.print(ChecksumAlgorithm.getName(sm.getChecksumAlg())+",");

            if(sm.getHashAlgorithm() != null)
                System.out.print(sm.getHashAlgorithm()+",");
            else
                System.out.print(",");

            if(sm.getContentHashString() != null)
                System.out.print(sm.getContentHashString()+",");           
            else
                System.out.print(",");

            FragmentFooter footer=ffs.getFragmentFooter();

            if(footer.isDeleted()==true) {
                System.out.print("true,");
            } else {
                System.out.print("false,");
            }


            System.out.print(footer.version+",");
            System.out.print(getDate(footer.autoCloseTime)+",");
            System.out.print(getDate(footer.deletionTime)+",");
            System.out.print(footer.shred+",");
            System.out.print(footer.refCount+",");
            System.out.print(footer.maxRefCount+",");
            BitSet drefs = footer.deletedRefs;
            if(drefs.length() > 0) {
                for(int i=FragmentFooter.DELETED_REFS_BLOOM_BITLENGTH-1;i>=0;i--) {
                    if(drefs.get(i)==true) {
                        System.out.print("1");
                    } else {
                        System.out.print("0");
                    }
                }

                System.out.print(",");
                /*
                System.out.print("{ ");
                boolean printed=false;             
                for(int i=0;i<drefs.length();i++) {
                    if(drefs.get(i)==true) {
                        if( printed == false) {

                            printed=true;
                        } else {
                            System.out.print(" | ");
                        }
                        
                        System.out.print(i);
                    }
                }
                System.out.print(" },");
                */
            } else {
                System.out.print(",");
            }
           
            //                                System.out.print(footer.deletedRefs+",");
            System.out.print(footer.numPreceedingChecksums+",");
            System.out.print(footer.chunkSize+",");                     
            System.out.print(Integer.toHexString(footer.footerChecksum)+",");
            if(footer.metadataField != null ) 
                for(int i=0;i< footer.metadataField.length;i++) {
                    System.out.print(footer.metadataField[i]);
                    if(i+1!=footer.metadataField.length) {
                        System.out.print("-");
                    }
                }
        }

    }

}
