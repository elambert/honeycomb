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



import com.sun.honeycomb.emd.MetadataClient;
import java.util.HashMap;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.emd.cache.CacheClientInterface;

public class MdLoad 
    implements Runnable {

    private static final String[] attributes = new String[] {
        "ofoto.dir1",
        "ofoto.dir2",
        "ofoto.dir3",
        "ofoto.dir4",
        "ofoto.dir5",
        "ofoto.dir6",
        "ofoto.fname"
    };
    
    private static final int MAX_VALUE = 10;
    private static final int TRACE_FRQ = 5;
    private static final int NB_THREADS = 4;

    private static int firstLevelValue = -1;

    private static int nbDone = 0;
    private static long size = -1;
    private static long startTime = 0;
    
    public static void main(String[] arg) {
        if (arg.length == 1) {
            firstLevelValue = Integer.parseInt(arg[0]);
        }
        

        System.out.println("Size: "+size());
        startTime = System.currentTimeMillis();
        Thread[] t = new Thread[NB_THREADS];
        
        for (int i=0; i<t.length; i++) {
            t[i] = new Thread(new MdLoad(i));
            t[i].start();
        }

        while (nbDone < size()) {
            synchronized (MdLoad.class) {
                try {
                    MdLoad.class.wait();
                } catch (InterruptedException e) {
                }
            }
        }

        System.out.println("Upload completed");
    }

    private static void update() {
        synchronized (MdLoad.class) {
            nbDone++;
            if ((nbDone % (size()*TRACE_FRQ/100)) == 0) {
                long ETA = size()*(System.currentTimeMillis()-startTime)/nbDone;


                System.out.println((nbDone*100/size())+"% - Total time: "+(ETA/1000)+"s. ETA: "+((ETA-System.currentTimeMillis()+startTime)/1000)+"s.");
                
            }
            if (nbDone == size()) {

                MdLoad.class.notify();
            }
        }
    }
    
    private int modulo;
    private int[] counters;

    private MdLoad(int _modulo) {
        modulo = _modulo;
        counters = new int[attributes.length];
        for (int i=0; i<counters.length; i++) {
            if ((i==0) && (firstLevelValue >= 0)) {
                counters[i] = firstLevelValue;
            } else {
                counters[i] = 0;
            }
        }
    }

    private boolean next() {
        boolean valid = false;
        int index = counters.length-1;
        while ((!valid) && (index>=0)) {
            if ((index == 0) && (firstLevelValue >= 0)) {
                return(false);
            }
            counters[index]++;
            if (counters[index]==MAX_VALUE) {
                counters[index] = 0;
                index--;
            } else {
                valid = true;
            }
        }
        return(valid);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<counters.length; i++) {
            sb.append(counters[i]);
            sb.append(" ");
        }
        return(sb.toString());
    }

    public synchronized static long size() {
        if (size < 0) {
            size = 1;
            for (int i=0; i<attributes.length; i++) {
                if ((i != 0) || (firstLevelValue == -1))
                    size *= MAX_VALUE;
            }
        }
        return(size);
    }

    public void run() {
        MetadataClient client = MetadataClient.getInstance();
        HashMap map = new HashMap(attributes.length+4);
        map.put("system.object_size", "45");
        map.put("system.object_hash_alg", "sha1");
        long counter = 0;
        long startTime = System.currentTimeMillis();
        long ops = 0;

        do {
            if ((counter % NB_THREADS) == modulo) {
                for (int i=0; i<attributes.length; i++) {
                    map.put(attributes[i], Integer.toString(counters[i]));
                }
                NewObjectIdentifier oid = new NewObjectIdentifier(0, (byte)1, 0);
                map.put("system.object_id", oid.toHexString());
                map.put("system.object_ctime", Long.toString(System.currentTimeMillis()));
                
                //client.existsExtCache(CacheClientInterface.EXTENDED_CACHE, oid);
                client.setMetadata("extended", oid, map);
                ops++;
                long endTime = System.currentTimeMillis();
                if(endTime - startTime > 5000) {
                    System.out.println(endTime-startTime + "  " + ops);
                    startTime = endTime;
                    ops=0;
                }
                update();
            }
            counter++;
        } while (next());
    }
}
