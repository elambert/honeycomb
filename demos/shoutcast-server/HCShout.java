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



import java.util.logging.Logger;
import java.io.FileInputStream;
import com.sun.honeycomb.protocol.client.ObjectArchive;
import java.util.logging.Level;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import com.sun.honeycomb.common.ObjectMetadata;
import java.util.Map;
import com.sun.honeycomb.common.ObjectIdentifierList;


public class HCShout {
    private static final Logger LOG = Logger.getLogger("HCShout");

    private static HCIce ice = null;

    private static void startNewBroadcast(ObjectArchive oa,
                                          ObjectIdentifier oid,
                                          String name) {
        try {

            WritableByteChannel writableChannel;

            if (ice == null) {
                ice = new HCIce();
            }

            Thread thread = new Thread(ice);
            thread.start();

            writableChannel = ice.getWritableByteChannel(name);

            oa.retrieve(writableChannel,
                        oid);
            writableChannel.close();

            while (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException ignored) {
                }
            }

        } catch (IOException e) {
            LOG.log(Level.SEVERE,
                    "Broadcast failed",
                    e);
        } catch (ArchiveException e) {
            LOG.log(Level.SEVERE,
                    "Broadcast failed",
                    e);
        }
    }

    public static void main(String[] arg) {
        if (arg.length != 1) {
            System.out.println("Enter the IP of the cluster as a parameter");
            System.exit(1);
        }

        System.out.println("Using honeycomb cluster at "+arg[0]);

        ObjectIdentifier lastOid = new ObjectIdentifier("0");
        ObjectIdentifier[] oids;
 
        String[] addresses = new String[1];
        addresses[0] = arg[0];
        ObjectArchive oa = new ObjectArchive(addresses);
        boolean running = true;

        while (running) {
            ObjectIdentifierList results = null;

            try {
                results = oa.query("\"title\"!=\"null\" AND \"object_id\">\""
                                   +lastOid.toString()+"\"",
                                   5);
            } catch (ArchiveException e) {
                LOG.log(Level.SEVERE,
                        "Unable to get oids",
                        e);
            }

            if (results == null) {
                lastOid = new ObjectIdentifier("0");
                continue;
            }

            oids = new ObjectIdentifier[results.size()];
            results.getList().toArray(oids);
            lastOid = oids[oids.length-1];

            for (int i=0; i<oids.length; i++) {
                ObjectIdentifier oid = new ObjectIdentifier(oids[i]);
                String title = "Unknown";

                try {
                    // Get the metadata
                    ObjectMetadata metadata = oa.getMetadata(oid);
                    Map map = metadata.getExtendedMetadata().getMap();
                
                    title = map.get("artist") + " - " + map.get("title");

                } catch (ArchiveException e) {
                    LOG.log(Level.SEVERE,
                            "Failed to get the metadata",
                            e);
                }
                
                LOG.info("Broadcasting "+title+" oid ["+oid+"]");
                startNewBroadcast(oa, oid, title);
            }
        }

        try {
            ice.close();
        } catch (IOException e) {
            LOG.log(Level.SEVERE,
                    "Failed to close the connection",
                    e);
        }
            
        LOG.info("Exiting ...");
    }
}
