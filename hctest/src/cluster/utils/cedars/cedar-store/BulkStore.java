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



import com.sun.honeycomb.client.caches.NameValueRecord;
import java.io.ByteArrayInputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.Channels;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.common.ArchiveException;
import java.io.IOException;
import com.sun.honeycomb.hctest.TestNVOA;

public class BulkStore {
    
    private static final String definedAttributeNames[] = {
	"study_id",
	"sample_group",
	"sample_id" };
    private static final String definedAttributeValues[] = {
	"study_test",
	"group_test",
	"id_test" };
    private static final String attributeToDefine = "scan_number";

    private static final String clusterAddress = "cedars-data";

    private static final int NB_RECORDS_TO_STORE = 30000;
    private static final int FIRST_VALUE = 0;

    private static final int NB_THREADS = 32;
    private static final int NB_NOTIFICATIONS = 50;

    private static final int NB_DISPLAY_POINTS = 80;

    private NameValueObjectArchive objectArchive;
    private int receivedNotif;
    private int printedPoints;
    private boolean running;
    private Exception error;
    private String errorThread;

    private class StoreThread
	extends Thread {
	private int modulo;

	private StoreThread(int nModulo) {
	    modulo = nModulo;
	}

	public void run() {
	    int current = modulo;
	    int nbStored = 0;
	    int nbNotifs = 0;
	    NameValueRecord md = new NameValueRecord();

	    for (int i=0; i<definedAttributeNames.length; i++) {
		md.put(definedAttributeNames[i],
		       definedAttributeValues[i]);
	    }

	    while (current < NB_RECORDS_TO_STORE) {
		    String value = Integer.toString(current+FIRST_VALUE);
		    md.put(attributeToDefine, value);
		    ByteArrayInputStream inputStream = new ByteArrayInputStream
			(new String("I am entry <"+value+">").getBytes());
 
		    while (true) {
		        ReadableByteChannel channel = 
                                               Channels.newChannel(inputStream);
                        try {
		            objectArchive.storeObject(channel, md);
                            break;
                        } catch (Exception e) {
                            try {
                                Thread.sleep(5000);
                            } catch (Exception ee) {}
                        }
                        try {
                            inputStream.reset();
                        } catch (Exception ee) {
                            inputStream = new ByteArrayInputStream
                              (new String("I am entry <"+value+">").getBytes());
                        }
                    }
		    try {
		        inputStream.close();
                    } catch (Exception e) {}

		    current += NB_THREADS;
		    nbStored++;
		    
		    if (nbStored - (nbNotifs*NB_RECORDS_TO_STORE/(NB_NOTIFICATIONS*NB_THREADS)) > 
			NB_RECORDS_TO_STORE/(NB_NOTIFICATIONS*NB_THREADS)) {
			notification();
			nbNotifs++;
		    }
	    }

	    while (nbNotifs < NB_NOTIFICATIONS) {
	        notification();
	        nbNotifs++;
	    }

	}
    }
    
    private synchronized void notification() {
	if (!running) {
	    return;
	}

	receivedNotif++;
	
	int points = receivedNotif*NB_DISPLAY_POINTS/(NB_THREADS*NB_NOTIFICATIONS);
	while (printedPoints < points) {
	    System.out.print(".");
	    printedPoints++;
	}

	if (receivedNotif == (NB_THREADS*NB_NOTIFICATIONS)) {
	    running = false;
	    notifyAll();
	}
    }

    private synchronized void failed(String thread,
				     Exception e) {
	errorThread = thread;
	error = e;
	running = false;
	notifyAll();
    }

    private BulkStore() {
	objectArchive = new TestNVOA(clusterAddress);
	receivedNotif = 0;
	printedPoints = 0;
	running = false;
	error = null;
	errorThread = null;
    }

    private void start() {
	StoreThread[] threads = new StoreThread[NB_THREADS];
	
	System.out.print(" ");
	for (int i=0; i<NB_DISPLAY_POINTS; i++) {
	    System.out.print(" ");
	}
	System.out.print("]\r[");

	running = true;

	long t1 = System.currentTimeMillis();

	for (int i=0; i<NB_THREADS; i++) {
	    threads[i] = new StoreThread(i);
	    threads[i].start();
	}
	
	synchronized (this) {
	    while (running) {
		try {
		    wait();
		} catch (InterruptedException ignored) {
		}
	    }
	}
        t1 = System.currentTimeMillis() - t1;
        long t_rec = t1 / NB_RECORDS_TO_STORE;

	System.out.println("");

	if (error == null) {
	    System.out.println("All data have been stored ["+
			       NB_RECORDS_TO_STORE+" records at " + t_rec +
				" ms/record, total time " + (t1/(60000)) +
				" minutes]");
	} else {
	    System.out.println("\nThread ["+errorThread+"] failed");
	    error.printStackTrace();
	}
    }
    
    public static void main(String[] arg) {
	BulkStore instance = new BulkStore();
	instance.start();
    }
}
