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
 * This is a simple utility that queries the local system cache and dumps
 * all the entries it contains.
 *
 * To run it, simply enter :
 *
 * java -Djava.library.path=/opt/honeycomb/lib/md_caches -jar
 * checkFrags.jar PATH [OID]
 *
 * PATH: is the path to the disk to inspect (e.g. /data/0)
 * OID: is an optional OID to look for
 *
 * If no OID is given, the tool will dump all the OIDs in the system DB
 * If an OID is given, the whole SystemMetadata for that oid will be
 * printed. The OID can be in the dotted or hex notation.
 *
 */

import com.sun.honeycomb.disks.Disk;
import java.io.File;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.disks.DiskId;
import com.sleepycat.db.Db;
import com.sun.honeycomb.common.SystemMetadata;
import com.sleepycat.db.Dbt;
import com.sleepycat.db.Dbc;
import com.sleepycat.db.DbException;
import com.sun.honeycomb.common.NewObjectIdentifier;

public class CheckFrags {

    private String path;
    private String oid;

    private CheckFrags(String _path,
                       String _oid) {
        path = _path;
        oid = _oid;
    }

    private void run()
        throws EMDException, DbException {
        
        BDBSystemCache cache = new BDBSystemCache();
        Disk disk = Disk.getNullDisk(new DiskId(0, 0));
        
        try {
            cache.start();
            cache.registerDisk(path+"/MD_cache", disk);
            BDBCache.PerDiskRecord perDiskRecord = cache.getDiskRecord(disk);
            
            Db db = perDiskRecord.getDb("main");
            Dbt key = null;
            
            if (oid ==null) {
                key = new Dbt();
            } else {
                
                // HACK: until steph finishes all the changes to NewObjectIdentifier
                //       and then there should be a way of converting between exernal and
                //       internal oids
                if (oid.substring(3,4).equals("0")) {
                    oid = oid.substring(0,3) + "1" + oid.substring(4);
                }
                
                NewObjectIdentifier oidobj;
                boolean isHex = oid.indexOf('.') == -1;
                if (isHex) {
                    oidobj = NewObjectIdentifier.fromHexString(oid);
                } else {
                    oidobj = new NewObjectIdentifier(oid);
                }
                                                                     
                key = BDBCache.encodeDbt(oidobj,
                                         BDBCache.SIZE_OBJECTID);
            }

            Dbt data = new Dbt();
            Dbc cursor = null;
            int res;
            
            try {
                if (oid == null) {

                    NewObjectIdentifier oid = null;
                    cursor = db.cursor(null, 0);
                    res = cursor.get(key, data, Db.DB_FIRST);
                    while (res != Db.DB_NOTFOUND) {
                        oid = (NewObjectIdentifier)BDBCache.decodeDbt(key);
                        System.out.println(oid);
                        res = cursor.get(key, data, Db.DB_NEXT);
                    }
            
                } else {

                    SystemMetadata sm = null;
                    res = db.get(null, key, data, 0);
                    sm = (SystemMetadata)BDBCache.decodeDbt(data);
                    
                    System.out.println(sm);
                    System.out.println("Hash: "+sm.getContentHashString());
                    System.out.println("Converted: "+sm.getOID().toString());
                }
            } finally {
                if (cursor != null)
                    try { cursor.close(); } catch (DbException ignored) {}
            }

            if (data.getData() == null) {
                throw new EMDException("Data not found ["+res+"]");
            }
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

        String oid = null;

        if ((arg.length < 1) || (arg.length > 2)) {
            System.out.println("Arguments: [path] [oid]\n\te.g.: /data/0 7c1b3760-99a6-11da-9e8d-0800209fc386.1.1.2.0.2638");
            System.exit(1);
        }

        File rootFile = new File(arg[0]);
        if ((!rootFile.exists()) || (!rootFile.isDirectory())) {
            System.out.println("["+arg[0]+"] is an invalid directory");
            System.exit(1);
        }

        oid = arg.length >=2 ? arg[1] : null;

        try {
            new CheckFrags(arg[0], oid).run();
        } catch (EMDException e) {
            e.printStackTrace();
        } catch (DbException e) {
            e.printStackTrace();
        }
    }
}
