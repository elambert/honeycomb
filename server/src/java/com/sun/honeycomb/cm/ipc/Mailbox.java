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

 

package com.sun.honeycomb.cm.ipc;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;
import java.net.DatagramSocket;

public final class Mailbox extends OutputStream {

    private static final Logger logger = 
        Logger.getLogger(Mailbox.class.getName());

    public static final String MBOXD_GROUP = "225.0.0.37";
    public static final int    MBOXD_PORT  = 4446;
    public static final int    MBOX_MAXSIZE = (54 * 1024); // 64k
    /*
     * states
     */
    static final int SRV_INVALID  = 0;
    static final int SRV_INIT     = 1;
    static final int SRV_READY    = 2;
    static final int SRV_RUNNING  = 3;
    static final int SRV_DISABLED = 4;
    static final int SRV_DESTROY  = 5;

    /*
     * actions
     */
    static final int ACT_VOID   = 0;
    static final int ACT_INIT   = 1;
    static final int ACT_STOP   = 2;
    static final int ACT_START  = 3;
    static final int ACT_DESTROY= 4;

    static private final int AVERAGE_SIZE = 8192;
    static private final int MAXIMUM_PAYLOAD = 24568;

    static {
        System.loadLibrary("jmbox");
        initIDs(Mailbox.Listener.class);
    }

    private final String tag; // tag of this mailbox
    private int lid;          // local id for this mailbox

    // backing store of this mailbox in the JVM address space.
    // Guarantee mailbox write atomicity.
    private byte  backingstore[];
    private int   count;


    static public interface Listener {
        /**
         * This method is invoked to trigger the initialization
         * of the service. The service acknowledges that it
         * reaches the requested state by setting its state to Ready
         */
        void doInit();

        /**
         * Starts processing incoming requests. This method
         * is invoked by the cluster management to activate the
         * service.
         */
        void doStart();

        /**
         * Stops accepting incoming requests and waits for
         * all current requests to finish.  The service acknowledges 
         * that it reaches the requested state by setting its state 
         * to Ready
         */
        void doStop();

        /**
         * Destroys all resources held by the service.
         */
        void doDestroy();

        /**
         * Check if the thread hadnling the shutdown sequence is stuck;
         * if so, escalate.
         */
        void doCheckShutdownTimeout();
    }

    public Mailbox(String tag, boolean manager) throws IOException {
        this.tag = tag;
        if (manager) {
            lid = create(tag, MAXIMUM_PAYLOAD);
        } else {
            lid = init(tag);
        }
        backingstore = new byte[AVERAGE_SIZE];
        count = 0;
    }

    public boolean stateCheck(Mailbox.Listener service) {
        int action = heartbeat(lid);
        switch (action) {
        case ACT_INIT:
            logger.info("ClusterMgmt - Mailbox triggers INIT for " + tag);
            service.doInit();
            break;
        case ACT_START:
            logger.info("ClusterMgmt - Mailbox triggers START for " + tag);
            service.doStart();
            break;
        case ACT_STOP:
            logger.info("ClusterMgmt - Mailbox triggers STOP for " + tag);
            service.doStop();
            break;
        case ACT_DESTROY:
            logger.info("ClusterMgmt - Mailbox triggers DESTROY for " + tag);
            service.doDestroy();
            break;
        default:
            service.doCheckShutdownTimeout();
            break;
        }
        return !isRunning() && rqstPending(); 
    }

    public void setInit() {
        set_state(lid, SRV_INIT);
    }

    public void setReady() {
        set_state(lid, SRV_READY);
    }

    public void setRunning() {
        set_state(lid, SRV_RUNNING);
    }

    public void setDisabled() {
        set_state(lid, SRV_DISABLED);
    }

    public boolean isInit() {
        int state = get_state(lid);
        return (state == SRV_INIT);
    }

    public boolean isRunning() {
        int state = get_state(lid);
        return (state == SRV_RUNNING);
    }

    public boolean isReady() {
        int state = get_state(lid);
        return (state == SRV_READY);
    }

    public boolean isDisabled() {
        int state = get_state(lid);
        return (state == SRV_DISABLED);
    }

    public void rqstInit() {
        rqstCancel();
        set_state(lid, SRV_INIT);
        set_expectedstate(lid, SRV_READY);
    }

    public void rqstReady() {
        set_expectedstate(lid, SRV_READY);
    }

    public void rqstRunning() {
        set_expectedstate(lid, SRV_RUNNING);
    }

    public void rqstDisable() {
        set_expectedstate(lid, SRV_DISABLED);
    }

    public void rqstDestroy() {
        set_expectedstate(lid, SRV_DESTROY);
    }

    public void rqstCancel() {
        set_expectedstate(lid, SRV_INVALID);
        set_state(lid, SRV_DISABLED);
    }

    public boolean rqstPending() {
        int state = get_expectedstate(lid);
        return (state != SRV_INVALID);
    }

    public boolean rqstIsDestroy() {
        int state = get_expectedstate(lid);
        return (state == SRV_DESTROY);
    }

    public int size() {
        return size(lid);
    }

    /*
     * Output stream
     * for the underlying distributed ipc.
     */

    public void close() {
        sync();
        close(lid);
    }

    public synchronized void sync() {
        write(lid, backingstore, 0, count);        
        // sync(lid); don't broadcast mailbox
        count = 0;
    }

    public synchronized void write(byte[] b, int off, int len) 
        throws IOException {
	if ((off < 0) || (off > b.length) || (len < 0) ||
            ((off + len) > b.length) || ((off + len) < 0)) {
	    throw new IOException();
	} else if (len == 0) {
	    return;
	}
        int c = count + len;
        if (c > backingstore.length) {
            byte buf[] = new byte[Math.max(backingstore.length << 1, c)];
            System.arraycopy(backingstore, 0, buf, 0, count);
            backingstore = buf;
        }
        System.arraycopy(b, off, backingstore, count, len);
        count = c;
    }

    public synchronized void write(int b) {
       int c = count + 1;
	if (c > backingstore.length) {
	    byte buf[] = new byte[Math.max(backingstore.length << 1, c)];
	    System.arraycopy(backingstore, 0, buf, 0, count);
	    backingstore = buf;
	}
	backingstore[count] = (byte)b;
	count = c;
    }

    public String toString() {
        if (isRunning()) {
            return "running";
        } else if (isInit()) {
            return "init";
        } else if (isReady()) {
            return "ready";
        } else if (isDisabled()) {
            return "disabled";
        } else {
            return "???";
        }
    }

    /*
     * native 
     */

    public static native int initIPC(int nodeid, int create_link);
    public static native void disable_node(int nodeid) throws IOException;
    public static native void publish() throws IOException;
    public static native void copyout(byte[] buf, int len);
    public static native byte[] copyin(String tag);
    public static native boolean exists(String tag);

    private static native void initIDs(Class cls);
    private static native int  init(String tag);
    private static native int  create(String tag, int size);
    private static native void close(int lid);
    private static native void sync(int lid);
    private static native int  size(int lid);
    private static native int heartbeat(int lid);
    private static native int get_state(int lid);
    private static native void set_state(int lid, int state);
    private static native int get_expectedstate(int lid);
    private static native void set_expectedstate(int lid, int state);
    private static native void write(int lid, byte[] src, 
                                     int offset, int len);

}
