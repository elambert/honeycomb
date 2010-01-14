package com.sun.honeycomb.util;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.oa.daal.nfs.NfsDAAL;

import java.lang.reflect.Field;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.*;
import java.util.Date;
import java.util.BitSet;
import java.util.Calendar;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.oa.daal.nfs.NfsDAAL;
import com.sun.honeycomb.oa.daal.DAAL;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.oa.checksum.ChecksumAlgorithm;
/*
before:
path, manual: /netdisks/10.123.45.101/data/1/02/14/5494b440-e758-11d9-8283-0800209fc32e.1.2.0.214_2,
size, manual: 507,
date, manual: 1119910262000,
o_oid: 5494b440-e758-11d9-8283-0800209fc32e.1.2.0.214,
o_layoutmapid: 214,
o_size: 654,
o_fragnum: 2,
o_fragdatalen: 0,
o_link: 547fa59f-e758-11d9-8283-0800209fc32e.1.1.0.6408,
o_atime: 1119910254634,
o_rtime: 0,
o_etime: 0,
o_sched: 0,
o_checksumalg: ADLER32,
o_hasshalg: null,
o_contenthashstring: 9b30e25c4079fc4f162b6cbf0d443d31016b530b,
o_deletedstatus: false,
o_version: 1,
o_autoclosetime: -1,
o_deletiontime: -1,
o_shred: 0,
o_refcount: -1,
o_maxrefcount: -1,
o_deletedrefs: {},
o_numpreceedingchecksums: 1,
o_chunksize: 3200,
o_footerchecksum: ae183523,
o_mdfield: 0-0-0-1-0-8-101-120-116-101-110-100-101-100-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0


f_path: /netdisks/10.123.45.102/data/0/tmp-close/81345927-e35f-11d9-aa03-0800209fc354.1.2.0.2150_3,
f_size: 507,
f_time: 2005-06-22 13:54:23,
f_node: 2,
f_disk: 0,
f_oid: 81345927-e35f-11d9-aa03-0800209fc354,
f_version: 1,
f_objecttype: 2,
f_chunknumber: 0,
f_layoutmapid: 2150,
f_fragnum: 3,
o_oid: 81345927-e35f-11d9-aa03-0800209fc354.1.2.0.2150,
o_layoutmapid: 2150,
o_size: 655,
o_fragnum: 3,
o_fragdatalen: 0,
o_link: 7fc6e676-e35f-11d9-aa03-0800209fc354.1.1.0.4363,
o_atime: 2005-06-22 13:52:11,
o_rtime: ,
o_etime: ,
o_sched: 0,
o_checksumalg: ADLER32,
o_hasshalg: null,
o_contenthashstring: 351fd567b72a8a96973ccc0c15a5c81f8fe8ce42,
o_deletedstatus: false,
o_version: 1,
o_autoclosetime: ,
o_deletiontime: ,
o_shred: 0,
o_refcount: -1,
o_maxrefcount: -1,
o_deletedrefs: {},
o_numpreceedingchecksums: 1,
o_chunksize: 3200,
o_footerchecksum: 7e813730,
o_mdfield: 0-0-0-1-0-8-101-120-116-101-110-100-101-100-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0

*/



class FragExplorer {
    public static void printUsage() {
        System.out.println("Usage: FragExplorer [-h] [-v] [-c] [-l numFiles] path");
        System.out.println("  -h this info");
        System.out.println("  -v debugging info (will fail silently with exceptions if this isn't enabled)");
        System.out.println("  -c outputs CSV format");
        System.out.println("  -l limit to processing this many files (for testing)");
        System.out.println("   Field order:");
        System.out.println("    oid,layoutmapid,size,fragnum,fragdatalen,link," +
                           "atime,rtime,etime,sched,checksumalg,hashalg,contenthashstring,"+
                           "deletedstatus(t/f flag),version,autoCloseTime,deletionTime,"+
                           "shred,refcount,maxrefcount,deletedrefs,numpreceedingchecksums,"+
                           "chunksize,footerchecksum,mdfield");

    }
    public static void main(String[] args) {
        int offset=0;
        FragExplorer explorer=new FragExplorer();
        if(args.length  < 1) {
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

        if (args[offset].equals("-l")) {
            offset++;
            fileLimit = Integer.decode(args[offset]).intValue();
            offset++;
        }

        for(int i=offset;i<args.length;i++) {
            File rootFile = new File(args[i]);
            explorer.print(rootFile);
        }
    }

    private static int filesProcessed=0,fileLimit=-1;
    public static boolean csvFormat=false;
    public static boolean verboseLogging = false;
    private File root;

    private String getDate(long seconds) {
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

    private void parsePath(String path) {
        String f_node,f_disk,f_oid,f_version,f_objecttype,f_chunknumber,f_layoutmapid,f_fragnum,f_tmpclose;

        /*
          input: /netdisks/10.123.45.102/data/0/tmp-close/81345927-e35f-11d9-aa03-0800209fc354.1.2.0.2150_3,

          output:
          f_path: /netdisks/10.123.45.101/data/1/02/14/5494b440-e758-11d9-8283-0800209fc32e.1.2.0.214_2,
          f_size: 507,
          f_time: 2005-06-22 13:54:23,
          f_node: 2,
          f_disk: 0,
          f_oid: 81345927-e35f-11d9-aa03-0800209fc354,
          f_version: 1,
          f_objecttype: 2,
          f_chunknumber: 0,
          f_layoutmapid: 2150,
          f_fragnum: 3,
        */


        //good  /netdisks/10.123.45.101/data/1/00/17/18fa07ca-f2e2-11d9-9002-0800209fc32e.1.2.0.17_0
        //bad   /netdisks/10.123.45.101/data/0/tmp-close/bb62fd17-f2fb-11d9-be13-0800209fc37a.1.1.0.4867_2

        Pattern p = Pattern.compile("/");
        String [] splitStrings = p.split(path);
        //[][netdisks][10.123.45.101][data][1][62][21][34ab73c5-e758-11d9-96de-0800209fc356.1.2.0.6221_0]

        if(splitStrings.length >=7) {
            Pattern q = Pattern.compile("\\.");
            String [] splitDots;
            if( splitStrings.length == 7 ) {
                f_tmpclose="true";
                splitDots = q.split(splitStrings[6]);
            } else {
                splitDots = q.split(splitStrings[7]);
                f_tmpclose="false";
            }
            if(splitStrings[2].charAt(11)=='1') {
                f_node=splitStrings[2].substring(11,12);
            } else {
                Character ch=new Character(splitStrings[2].charAt(11));
                f_node = ch.toString();
            }
            f_disk=splitStrings[4];
            f_oid=splitDots[0];
            f_version=splitDots[1];
            f_objecttype=splitDots[2];
            f_chunknumber=splitDots[3];
            String [] underStrings = splitDots[4].split("_");

            f_layoutmapid=underStrings[0];
            f_fragnum=underStrings[1];
            //[3897a1a7-e758-11d9-998c-0800209fc354][1][2][0][3039_4]
            if(csvFormat ==true) {
                System.out.print(f_node+","+f_disk+","+f_oid+","+f_version+","+f_objecttype+","+f_chunknumber+","+f_layoutmapid+","+f_fragnum+","+f_tmpclose);
            } else {
                System.out.println("Node: " + f_node);
                System.out.println("Disk: "+f_disk);
                System.out.println("Oid: "+f_oid);
                System.out.println("Version: "+f_version);
                System.out.println("ObjectType: "+f_objecttype);
                System.out.println("Chunknumber: "+f_chunknumber);
                System.out.println("Layout Map Id:"+f_layoutmapid);
                System.out.println("Fragnum: "+f_fragnum);
                System.out.println("tmp_close: "+f_tmpclose);
            }


        } else {
            System.out.println ("******* Doesn't conform - this many bits:" + splitStrings.length);
            System.out.println("String:"+path);
            System.exit(1);
        }
    }


    private boolean tryFragment(final File ourFrag) {
        FragmentFileSubclass ffs = null;
        SystemMetadata sm = null;
        try {
            ffs = new FragmentFileSubclass(ourFrag);
            sm = ffs.readSystemMetadata();
        } catch (com.sun.honeycomb.oa.DeletedFragmentException dfe) {
        } catch (Throwable t) {
            return false;
        }
        System.out.flush();
        return true;
    }

    private void dumpFragment(final File ourFrag) {
        FragmentFileSubclass ffs = null;
        SystemMetadata sm = null;
        boolean failed=false;
        try {
            //
            // Where am I calling open
            //        return getSystemMetadata();  - use this in the deleted case.
            //
            ffs = new FragmentFileSubclass(ourFrag);
            sm = ffs.readSystemMetadata();
        } catch (com.sun.honeycomb.oa.DeletedFragmentException dfe) {
            // don't care; fragment should be populated anyhow
            sm = ffs.getSystemMetadata();
        } catch (Throwable t) {
            //            System.out.println("Warning - not a fragment, skipping.");
            //
            // For non-fragments.
            //
            if(verboseLogging == true) {
                System.out.print(ourFrag.toString());
                System.out.println("Not a fragment.");
                t.printStackTrace();
            }

            failed=true;
        }
        if(null==sm) {
            failed=true;
            if(csvFormat ==false) {
                System.out.println("sm is null for this fragment, skipping...");
            } else {
                System.out.print(",,,,,,,,,,,,,,,,,,,,,,,,,");
            }
        }
        if(!failed) {

            if(csvFormat ==false) {
                if(sm.getOID() != null )
                    System.out.println("oid: "+sm.getOID());//92211ed7-d92d-11d9-bcd8-0800209fc330.1.1.0.5
                else
                    System.out.println("oid: ");//92211ed7-d92d-11d9-bcd8-0800209fc330.1.1.0.5
                System.out.println("layoutmapid: "+sm.getLayoutMapId());//5
                System.out.println("size:"+sm.getSize());//890573
                //            System.out.println("Data reliability: "+sm.getReliability()); //data(n): 5
                System.out.println("fragdatalen: "+sm.getFragDataLen());//2

                if(sm.getLink()!=null)
                    System.out.println("link: "+sm.getLink());//0
                else
                    System.out.println("link: ");

    // FIXME
           //     System.out.println("atime: "+getDate(sm.getATime()));//00000000-0000-0000-0000-000000000000.1.0
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
                System.out.print(sm.getLayoutMapId()+",");//5
                System.out.print(sm.getSize()+",");//890573
                System.out.print(sm.getFragDataLen()+",");//2
                if(sm.getLink() != null)
                    System.out.print(sm.getLink()+",");//0
                else
                    System.out.print(",");
    // FIXME
        //        System.out.print(getDate(sm.getATime())+",");//00000000-0000-0000-0000-000000000000.1.0
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

    private void print(final File fileOrDirectory) {
        boolean isDirectory = fileOrDirectory.isDirectory();
        String path = null;

        try {
            path = fileOrDirectory.getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException("failed to get canonical path for directory " +
                                       fileOrDirectory);
        }

        if (verboseLogging) {
            System.out.println(((isDirectory) ? "" : "    ") +
                               "loading " +
                               path);
        }


        if (isDirectory) {
            loadDirectory(fileOrDirectory);
        } else {
            String fragname = fileOrDirectory.toString();

            if (tryFragment(fileOrDirectory)) {

                //size, manual: 507,
                //date, manual: 1119910262000,
                if(csvFormat ==true) {
                    System.out.print(fileOrDirectory.toString() +
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
                parsePath(fragname);
                dumpFragment(fileOrDirectory);
                System.out.println();
            }

        }


        if (verboseLogging && isDirectory) {
            System.out.println("done with " + path);
        }
    }

    private void loadDirectory(final File directory) {
        File[] contents = directory.listFiles();
        if (contents != null) {
            for (int i = 0; i < contents.length; i++) {
                print(contents[i]);
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
     */

    public static class FragmentFileSubclass extends FragmentFile {
        private NewObjectIdentifier oid;
        private int fragNum;
        public FragmentFileSubclass(File f) {
            super();
            oid = Common.extractOIDFromFilename(f.getName());
            fragNum = Common.extractFragNumFromFilename(f.getName());
            init(oid, fragNum, null, null);
            Field daalField = ReflectedAccess.getField(FragmentFile.class, "daal");
            Field namefField = ReflectedAccess.getField(NfsDAAL.class, "namef");
            try {
                namefField.set((NfsDAAL) daalField.get(this), f);
            } catch (IllegalAccessException iae) {
                throw new IllegalStateException(iae.getMessage());
            }
        }

        protected DAAL instantiateDAAL(Disk disk, NewObjectIdentifier oid, int fragNum) {
            Disk d = new Disk(new DiskId(100, 0), "foo", ".",
                              "bar", 0, 0, 0, 0, 0, 0, 0, false);
            return new NfsDAAL(d, oid, new Integer(fragNum));
        }

        public FragmentFooter getFragmentFooter() {
            try {
                return super.getFragmentFooter();
            } catch (OAException oae) {
                throw new IllegalStateException(oae.getMessage());
            }
        }
    }
};

