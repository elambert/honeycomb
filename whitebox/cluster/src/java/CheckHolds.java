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



/*
 * This is a simple utility that queries the local legal hold cache
 * and dumps all the entries it contains.
 *
 * To run it, simply enter :
 *
 * java -Djava.library.path=/opt/honeycomb/lib/md_caches -jar
 * checkHolds.jar PATH [LEGAL HOLD]
 *
 * PATH: is the path to the disk to inspect (e.g. /data/0)
 * LEGAL HOLD: is an optional legal hold string to look for
 *
 * If no LEGAL HOLD is given, the tool will dump all the legal holds
 * in the system DB If a legal hold is given, the search will be
 * restricted to results which match that LEGAL HOLD.
 *
 */

import com.sun.honeycomb.disks.Disk;
import java.io.File;
import java.io.IOException;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.disks.DiskId;
import com.sleepycat.db.Db;
import com.sun.honeycomb.common.SystemMetadata;
import com.sleepycat.db.Dbt;
import com.sleepycat.db.Dbc;
import com.sleepycat.db.DbException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import java.io.UnsupportedEncodingException;

public class CheckHolds {

    private String path;
    private String hold;
    private String oid;
    private NewObjectIdentifier nOid;

    private CheckHolds(String _path,
                       String _hold,
                       String _oid) {
        path = _path;
        hold = _hold;
        oid = _oid;
        nOid = null;
    }

    private void run()
        throws EMDException, DbException {
        
        BDBSystemCache cache = new BDBSystemCache();
        Disk disk = Disk.getNullDisk(new DiskId(0, 0));

        try {
            cache.start();
            cache.registerDisk(path + "/MD_cache", disk);
            BDBCache.PerDiskRecord perDiskRecord = cache.getDiskRecord(disk);

            Dbt key = new Dbt();
            
            if (hold != null) {
                try {
                    byte[] bytes = hold.getBytes("UTF8");
                    key.setData(bytes);
                    key.setSize(bytes.length);
                } catch (IOException e) {
                    EMDException newe =
                        new EMDException("Failed to encode the entry to " +
                                         "the index [" + e.getMessage() + "]");
                    newe.initCause(e);
                    throw newe;
                }
            }

            if (oid != null) {
                
                // HACK: until steph finishes all the changes to NewObjectIdentifier
                //       and then there should be a way of converting between exernal and
                //       internal oids
                if (oid.substring(3,4).equals("0")) {
                    oid = oid.substring(0,3) + "1" + oid.substring(4);
                }

                if (oid.indexOf('.') >= 0)
                    // It's a string
                    nOid = new NewObjectIdentifier(oid);
                else
                    nOid = NewObjectIdentifier.fromHexString(oid);
            }

            // Debug
            System.out.println("Seaching hold db for legal hold [" + hold +
                               "] and oid [" + nOid + "]");

            Db db = perDiskRecord.getDb("hold");
            Dbt data = null;

            if (nOid == null) {
                data = new Dbt();
            } else {
                data = BDBCache.encodeDbt(nOid, BDBCache.SIZE_OBJECTID);
            }

            Dbc cursor = null;
            int res;

            try {
                String legalHold = null;
                NewObjectIdentifier oid = null;
                cursor = db.cursor(null, 0);

                if (nOid == null) {
                    res = cursor.get(key, data, Db.DB_SET);
                } else {
                    res = cursor.get(key, data, Db.DB_GET_BOTH);
                }

                while (res != Db.DB_NOTFOUND) {
                    legalHold = new String((byte[])key.getData(), "UTF8");
                    oid = (NewObjectIdentifier)BDBCache.decodeDbt(data);
                    System.out.println("[" + legalHold + "] " + oid);
                    res = cursor.get(key, data, Db.DB_NEXT_DUP);
                }
            } finally {
                if (cursor != null)
                    try { cursor.close(); } catch (DbException ignored) {}
            }

            if (data.getData() == null) {
                throw new EMDException("Data not found [" + res + "]");
            }

        } catch (UnsupportedEncodingException uee) {
            uee.printStackTrace();
        } finally {
            try {
                cache.unregisterDisk(disk);
                cache.stop();
            } catch (EMDException e2) {
                e2.printStackTrace();
            }
        }
    }

    public static void main(String[] arg) {

        String legalHold = null;
        String holdOid = null;

        if ((arg.length < 1) || (arg.length > 3)) {
            System.out.println("Arguments: <PATH> [<LEGAL HOLD> | <LEGAL HOLD> <OID>]\n");
            System.out.println("\tExample: /data/0 'Dogs vs. Cats'");
            System.out.println("\t         /data/0 'Dogs vs. Cats' 0200012923607de13b744111db8bbf00e081598321000007320200000000");
            System.exit(1);
        }

        File rootFile = new File(arg[0]);
        if ((!rootFile.exists()) || (!rootFile.isDirectory())) {
            System.out.println("["+arg[0]+"] is an invalid directory");
            System.exit(1);
        }
        
        legalHold = arg.length >=2 ? arg[1] : null;
        holdOid = arg.length >=3 ? arg[2] : null;

        try {
            new CheckHolds(arg[0], legalHold, holdOid).run();
        } catch (EMDException e) {
            e.printStackTrace();
        } catch (DbException e) {
            e.printStackTrace();
        }
    }
}
