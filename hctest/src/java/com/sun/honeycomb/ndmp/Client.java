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



package com.sun.honeycomb.ndmp;

import java.util.Date;
import java.util.logging.Level;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;


/**
 *
 * Honeycomb NDMP Client and Tape Server
 *
 * This class implements a NDMP Client and Tape Server to automate testing.
 * It's basically a Netvault emulator.
 *
 * The NDMP Client is based on the same automatically generated class
 * as the Data Server which runs in the cluster. The base NDMP class
 * is generated from the XDR specification in the manner of RPCGEN and
 * performs all of the serialization/deserialization and
 * dispatching. The Client overrides certain "reply" methods and
 * generates appropriate requests to the Data Server.
 *
 * The Tape Server justs accepts a connection and uploads a file in
 * the case of a restore, or downloads one for a backup.
 *
 * Since the Client and the Tape Server are independent actors, each
 * gets its own thread.
 *
 * @see http://www.ndmp.org/download/sdk_v4/
*/
public class Client{
    
    static int BUFFER_SIZE = 1024 * 1024;
    int tapePort = 10004;
    boolean halted = false;
    SocketChannel tapeSocketChannel = null;

    abstract class BaseClient extends NDMP {

        String name;
        Thread tapeThread = null;
        FileChannel tapeFileChannel = null;

        BaseClient (String cluster, String name) throws IOException{
            this.name = name;
            Socket s = new Socket(cluster, 10000);
            controlInputStream = new XDRInputStream (s.getInputStream()); 
            controlOutputStream = new XDROutputStream (s.getOutputStream());
            if (Boolean.getBoolean("debug"))
                LOGGER.setLevel(Level.INFO);
            else
                LOGGER.setLevel(Level.WARNING);
        }

        abstract void transferData (SocketChannel sc) throws Exception;


        // This shuld really be "handleReply", but we are piggybacking off 
        // of work done for the Data Server
        void handleRequest(boolean fromServer) throws IOException{
            ndmp_header header = read_ndmp_header();
            if (!NDMP_NO_ERR.equals(header.error_code))
                throw new RuntimeException("Data server signaled error: " + header);
            else
                clientDispatch(header);
        }


        void NDMP_NOT_SUPPORTED_ERR(ndmp_header header) throws IOException{
            NDMP_NOT_SUPPORTED_ERR(header, header.message_code.toString());
        }

        void NDMP_NOT_SUPPORTED_ERR(ndmp_header header, String arg) throws IOException{
            throw new RuntimeException("NYI: " + arg);
        }




        // These methods are shared by backup and restore clients.  They
        // mostly consist of authentication and protocol negotiation.
        // After a connection is established, either the Backup or the
        // Restore subclass will handle the ndmp_data_connect_reply and
        // make the request which results in the Honeycomb Data Server
        // opening a connection to the Tape Server to read or write bytes.

        void handle_ndmp_notify_connection_status_post
            (ndmp_notify_connection_status_post ndmp_notify_connection_status_post, 
             ndmp_header header) throws IOException{
            LOGGER.info(ndmp_notify_connection_status_post + " " + header);
            transitionState(POSTED);
            request(new ndmp_connect_open_request(4), NDMP_CONNECT_OPEN);
        }


        void handle_ndmp_log_message_post(ndmp_log_message_post ndmp_log_message_post, ndmp_header header) throws IOException{
            LOGGER.info(ndmp_log_message_post.toString());
        }
    
        void handle_ndmp_connect_open_reply(ndmp_connect_open_reply ndmp_connect_open_reply, 
                                            ndmp_header header) throws IOException
        {
            LOGGER.info(ndmp_connect_open_reply.toString());
            LOGGER.info("handle_ndmp_connect_open_reply "  + header);
            if (ndmp_connect_open_reply.error != NDMP_NO_ERR)
                throw new RuntimeException("Data Server signaled error in reply: " + ndmp_connect_open_reply);
            transitionState(OPEN);
            request(NDMP_CONFIG_GET_HOST_INFO);
        }
    
        void handle_ndmp_config_get_host_info_reply
            (ndmp_config_get_host_info_reply ndmp_config_get_host_info_reply,
             ndmp_header header) throws IOException
        {
            LOGGER.info(ndmp_config_get_host_info_reply + " "  + header);
            if (ndmp_config_get_host_info_reply.error != NDMP_NO_ERR)
                throw new RuntimeException("Data Server signaled error in reply: " + ndmp_config_get_host_info_reply);
            request(NDMP_CONFIG_GET_SERVER_INFO);
        }
    
        void handle_ndmp_config_get_server_info_reply
            (ndmp_config_get_server_info_reply ndmp_config_get_server_info_reply,
             ndmp_header header) throws IOException
        {
            LOGGER.info(ndmp_config_get_server_info_reply + " "  + header);
            if (ndmp_config_get_server_info_reply.error != NDMP_NO_ERR)
                throw new RuntimeException("Data Server signaled error in reply: " + ndmp_config_get_server_info_reply);
            if (ndmp_config_get_server_info_reply.product_name == "")
                request(new ndmp_connect_client_auth_request(new ndmp_auth_data(NDMP_AUTH_NONE, null)),
                        NDMP_CONNECT_CLIENT_AUTH);
            else
                request(NDMP_CONFIG_GET_CONNECTION_TYPE);
        }


        boolean haveInfo = false;

        void handle_ndmp_config_get_connection_type_reply
            (ndmp_config_get_connection_type_reply ndmp_config_get_connection_type_reply, 
             ndmp_header header) throws IOException
        {
            if (ndmp_config_get_connection_type_reply.error != NDMP_NO_ERR)
                throw new RuntimeException("Data Server signaled error in reply: " + ndmp_config_get_connection_type_reply);
            if (haveInfo){
                LOGGER.info(ndmp_config_get_connection_type_reply + " "  + header);
                transitionState(CONNECTED);
                // Start data transfer
                tapePort++;
                tapeThread = new TapeServer(this);
                tapeThread.start();
                long local = addressToLong(InetAddress.getLocalHost().getAddress());
        
//                 System.out.println((byte)((local >> 24) & 0xff));
//                 System.out.println((byte)((local >> 16) & 0xff));
//                 System.out.println((byte)((local >> 8) & 0xff));
        
                ndmp_pval[] ndmp_pvals = {};
                ndmp_tcp_addr[] tcpAddress = {new ndmp_tcp_addr(local, tapePort, ndmp_pvals)};
                ndmp_addr ndmpAddress = new ndmp_addr(NDMP_ADDR_TCP, tcpAddress);
                request(new ndmp_data_connect_request(ndmpAddress), NDMP_DATA_CONNECT);
            }
            else{
                request(NDMP_CONFIG_GET_BUTYPE_INFO);
            }
        }

        void handle_ndmp_config_get_butype_info_reply
            (ndmp_config_get_butype_attr_reply ndmp_config_get_butype_attr_reply,
             ndmp_header header) throws IOException
        {
            if (ndmp_config_get_butype_attr_reply.error != NDMP_NO_ERR)
                throw new RuntimeException("Data Server signaled error in reply: " + ndmp_config_get_butype_attr_reply);
            haveInfo = true;
            LOGGER.info("handle_ndmp_config_get_butype_info_reply " + ndmp_config_get_butype_attr_reply);
            request(NDMP_CONFIG_GET_CONNECTION_TYPE);
        }

    
        void handle_ndmp_connect_client_auth_reply
            (ndmp_connect_client_auth_reply ndmp_connect_client_auth_reply, 
             ndmp_header header) throws IOException
        {
            LOGGER.info(ndmp_connect_client_auth_reply + " "  + header);
            if (ndmp_connect_client_auth_reply.error != NDMP_NO_ERR)
                throw new RuntimeException("Data Server signaled error in reply: " + ndmp_connect_client_auth_reply);
            request(NDMP_CONFIG_GET_HOST_INFO);
        }
        //ndmp_notify_data_read_post    

        void handle_ndmp_data_stop_reply
            (ndmp_data_stop_reply ndmp_data_stop_reply,
             ndmp_header header) throws IOException
        {
            LOGGER.info(ndmp_data_stop_reply + " " + header);
            if (ndmp_data_stop_reply.error != NDMP_NO_ERR)
                throw new RuntimeException("Data Server signaled error in reply: " + ndmp_data_stop_reply);
            request(NDMP_CONNECT_CLOSE);
            throw new Stopped();
        }


        void handle_ndmp_data_get_state_reply
            (ndmp_data_get_state_reply ndmp_data_get_state_reply,
             ndmp_header header) throws IOException
        {
            LOGGER.info(ndmp_data_get_state_reply + " " + header);
            if (ndmp_data_get_state_reply.error != NDMP_NO_ERR)
                throw new RuntimeException("Data Server signaled error in reply: " + ndmp_data_get_state_reply);
            if (!ndmp_data_get_state_reply.state.equals(NDMP_DATA_STATE_ACTIVE)){
                halted = true;
            }
        }


        // Helper methods

        void handle_ndmp_notify_data_halted_post_common
            (ndmp_notify_data_halted_post ndmp_notify_data_halted_post, 
             ndmp_header header) throws IOException
        {
            halted = true;
            transitionState(HALTED);
            stopThreads();
            System.out.println(ndmp_notify_data_halted_post.reason);
            if (!NDMP_DATA_HALT_SUCCESSFUL.equals(ndmp_notify_data_halted_post.reason)){
                // Abnormal halt
                throw new RuntimeException("Backup halted: " + ndmp_notify_data_halted_post.reason);
            }
        }


        private long sequence = 1;

        void request(request request, ndmp_message message) throws IOException{
            ndmp_header header = new ndmp_header(sequence++, 
                                                 System.currentTimeMillis() / 1000, 
                                                 NDMP_MESSAGE_REQUEST, 
                                                 message,
                                                 0l, 
                                                 NDMP_NO_ERR);
            write_ndmp_header(header);
            request.write();
            controlOutputStream.sendMessage();
        }

        void request(ndmp_message message) throws IOException{
            ndmp_header header = new ndmp_header(sequence++, 
                                                 System.currentTimeMillis() / 1000, 
                                                 NDMP_MESSAGE_REQUEST, 
                                                 message,
                                                 0l, 
                                                 NDMP_NO_ERR);
            write_ndmp_header(header);
            controlOutputStream.sendMessage();
        }

        StateChecker stateChecker = new StateChecker();
        class StateChecker extends Thread{
            StateChecker(){
                super("Generate GET_STATE requests");
            }
            public void run(){
                while (!halted){
                    try{
                        request(NDMP_DATA_GET_STATE);
                        sleep (2000);
                    }
                    catch (InterruptedException ie){}
                    catch (Exception e){
                        if (!halted){
                            System.out.println("State checker exiting with " + e);
                        }
                        return;
                    }
                }
            }
        }

        private class State{
            final String name;
            final State nextState;
            State (String name, State nextState){
                this.name = name;
                this.nextState = nextState;
            }
            public String toString() {return name;}
        }

        final State HALTED = new State(NDMP_DATA_STATE_HALTED.name, null);
        final State ACTIVE = new State(NDMP_DATA_STATE_ACTIVE.name, HALTED);
        final State CONNECTED = new State(NDMP_DATA_STATE_CONNECTED.name, ACTIVE);
        final State OPEN = new State("COMMUNICATION_CHANNEL_OPEN", CONNECTED);
        final State POSTED = new State("DATA_SERVER_RESPONDED", OPEN);
        final State IDLE = new State(NDMP_DATA_STATE_IDLE.name, POSTED);

        private State currentState = IDLE;

        void transitionState(State state){
            if (currentState.nextState != state)
                throw new RuntimeException("Attempt to transition from " + currentState + " to " +
                                           state + ". Expected " + currentState.nextState);
            System.out.println("Moving from " + currentState + " to " + state);
            currentState = state;
        }
        private void checkState(State state){
            if (currentState != state)
                throw new RuntimeException("Bad state " + currentState + ". Expected " + state);
        }

        void stopThreads() throws IOException{
            System.out.println("Stopping threads");
            if (tapeThread != null)
                tapeThread.interrupt();
            if (tapeFileChannel != null)
                tapeFileChannel.close();
            if (tapeSocketChannel != null)
                tapeSocketChannel.close();
            if(stateChecker != null)
                stateChecker.interrupt();
        }
    }




    class Restore extends BaseClient{

        Restore(String cluster, String name) throws IOException{
            super(cluster, name);
        }

        public String toString(){
            return "Restore " + name;
        }

        void handle_ndmp_data_connect_reply
            (ndmp_data_connect_reply ndmp_data_connect_reply, 
             ndmp_header header) throws IOException
        {
            LOGGER.info(ndmp_data_connect_reply + " " + header);
            ndmp_pval[] env = {/* new ndmp_pval("start", start), 
                                  new ndmp_pval("end", end) */};
            ndmp_name ndmp_name = new ndmp_name("", "/", name, "", 0, 0);
            ndmp_name[] nlist = {ndmp_name};
            transitionState(ACTIVE);
            request(new ndmp_data_start_recover_request(env, nlist, "Honeycomb"), 
                    NDMP_DATA_START_RECOVER);
        }

        void handle_ndmp_data_start_recover_reply
            (ndmp_data_start_recover_reply ndmp_data_start_recover_reply,
             ndmp_header header) throws IOException
        {
            LOGGER.info(ndmp_data_start_recover_reply + " " + header);
            // Nothing more to do until recover completes:
            // data flows directly from the Data Server to the Tape Server.
            stateChecker.start();
        }


        void handle_ndmp_notify_data_read_post
            (ndmp_notify_data_read_post ndmp_notify_data_read_post,
             ndmp_header header) throws IOException
        {
            LOGGER.info(ndmp_notify_data_read_post + " " + header);
        }

        void handle_ndmp_notify_data_halted_post
            (ndmp_notify_data_halted_post ndmp_notify_data_halted_post, 
             ndmp_header header) throws IOException
        {
            handle_ndmp_notify_data_halted_post_common(ndmp_notify_data_halted_post, 
                                                       header);
            request(NDMP_DATA_STOP);
        }

        void transferData(SocketChannel sc) throws Exception{
            System.out.println("Tape server restoring from file \"" + name + "\"");
            sc.configureBlocking(true);
            FileInputStream fis = new FileInputStream(name);
            tapeFileChannel = fis.getChannel();
            long total = 0;
            long n = tapeFileChannel.transferTo(0, BUFFER_SIZE, sc);
            while (n > 0 && !halted){
                total += n;
                LOGGER.info("Write " + n + " bytes");
                System.out.print(".");
                n = tapeFileChannel.transferTo(total, BUFFER_SIZE, sc);
            }
            halted = true;
            //request(NDMP_CONNECT_CLOSE);
            fis.close();
            sc.close();
            System.out.println("Restored " + total + " bytes");
        }
    }



    class Backup extends BaseClient{
        String start;
        String end;
        Backup (String cluster, String name, String start, String end) throws Exception{
            super(cluster, name);
            this.start = start;
            this.end = end;
        }

        public String toString(){
            return "Backup " + name;
        }

        void handle_ndmp_data_connect_reply
            (ndmp_data_connect_reply ndmp_data_connect_reply, 
             ndmp_header header) throws IOException
        {
            LOGGER.info(ndmp_data_connect_reply + " " + header);
            ndmp_pval[] env = {new ndmp_pval("start", start), 
                               new ndmp_pval("end", end)};
            transitionState(ACTIVE);
            request(new ndmp_data_start_backup_request("Honeycomb", env), 
                    NDMP_DATA_START_BACKUP);
        }

        void handle_ndmp_data_start_backup_reply
            (ndmp_data_start_backup_reply ndmp_data_start_backup_reply,
             ndmp_header header) throws IOException
        {
            LOGGER.info(ndmp_data_start_backup_reply + " " + header);
            // Nothing more to do until recover completes:
            // data flows directly from the Tape Server to the Data Server.
            stateChecker.start();
        }

        void handle_ndmp_notify_data_halted_post
            (ndmp_notify_data_halted_post ndmp_notify_data_halted_post, 
             ndmp_header header) throws IOException
        {
            handle_ndmp_notify_data_halted_post_common(ndmp_notify_data_halted_post, 
                                                       header);
            request(NDMP_DATA_GET_ENV);
        }

        void handle_ndmp_data_get_env_reply
            (ndmp_data_get_env_reply ndmp_data_get_env_reply,
             ndmp_header header) throws IOException
        {
            LOGGER.info(ndmp_data_get_env_reply + " " + header);
            request(NDMP_DATA_STOP);
        }


        void transferData(SocketChannel sc) throws Exception{
            System.out.println("Tape server backing up to file \"" + name + "\"");
            long n = 0;
            long total = 0;
            sc.configureBlocking(true);
            long start = System.currentTimeMillis();

            if (name == null){        
                ByteBuffer bb = ByteBuffer.allocateDirect(BUFFER_SIZE);
                n = sc.read(bb);
                while (n > 0){
                    total += n;
                    bb.clear();
                    n = sc.read(bb);
                }
            } else {   
                FileOutputStream fos = new FileOutputStream(name);
                tapeFileChannel = fos.getChannel();
                n = tapeFileChannel.transferFrom(sc, 0, BUFFER_SIZE);
                while (n > 0 && !halted){
                    total += n;
                    LOGGER.info("Read " + n + " bytes");
                    System.out.print(".");
                    n = tapeFileChannel.transferFrom(sc, total, BUFFER_SIZE);
                }
                fos.close();
            }
            sc.close();
            // Signal the control thread not to panic if its connection closes
            halted = true;
        
            System.out.println("Backed up " + total + " bytes");
            long delta = (System.currentTimeMillis() - start);
            System.out.println("Done in " + (delta/1000.0) + " seconds");
            System.out.println("Extrapolating " + ((int)(total / delta*1000.0 * 60 * 60 * 24)) + " bytes/day");
        }    
    }
    
    class TapeServer extends Thread{

        private BaseClient client;

        TapeServer(BaseClient client){
            super(client + " tape server");
            this.client = client;
        }

        public void run(){            
            try{
                System.out.println(getName() + " listening on port " + tapePort);
                ServerSocketChannel ssc = ServerSocketChannel.open();
                ssc.socket().bind(new InetSocketAddress(tapePort));
                ssc.configureBlocking(true);

                tapeSocketChannel = ssc.accept();
                long start = System.currentTimeMillis();
                System.out.println("Tape server on port " + tapePort + " accepted request from " + 
                                   tapeSocketChannel.socket().getRemoteSocketAddress());
                client.transferData(tapeSocketChannel);
                System.out.println(this + " done in " + ((System.currentTimeMillis() - start)/1000.0) + " seconds");
            } 
            catch (Exception e) {
                if (!halted){
                    System.out.println(this + " unexpected " + e);
                    e.printStackTrace();
                    /*
                     * XXX: not the best way to do this but shutting down the StateChecker
                     *      and controlThread has proven to be to complicated and therefore
                     *      exiting here is the cleanest way of doing the right thing.
                     */
                    System.exit(-1);
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static class Stopped extends RuntimeException{}

    // Backup
    private Client(String cluster, String fileName, String startDate, String endDate) throws Exception{
        Backup b = new Backup(cluster, fileName, startDate, endDate);
        try{
            b.handleSession();
        }
        catch (Exception e){
            b.stopThreads();
            if (! halted){
                throw e;
            }
        }
    }

    // Restore
    private Client(String cluster, String fileName) throws Exception{
        Restore r = new Restore(cluster, fileName);
        try{
            r.handleSession();
        }
        catch (Exception e){
            // There is a race condition between the data and control
            // threads; as long as one halted normally, don't panic if
            // the other one closes
            r.stopThreads();
            if (! halted){
                throw e;
            }
        }
    }

    public static void backup (String cluster, String fileName, String startDate, String endDate) throws Exception{
        try{
            new Client(cluster, fileName, startDate, endDate);
        }
        catch (Stopped stopped){
            System.err.println("Backup exited normally");
            System.exit(-1);
        }
    }
    
    public static void restore (String cluster, String fileName) throws Exception{
        try{
            new Client(cluster, fileName);
        }
        catch (Stopped stopped){
            System.err.println("Restore exited normally");
            System.exit(-1);
        }
    }
    
    public static void main (String[] argv) throws Exception{
        if (argv.length == 4){
            String cluster = argv[0];
            String name = argv[1];
            String startDate = argv[2];
            String endDate = argv[3];
            backup(cluster, name, startDate, endDate);
        }
        else if (argv.length == 3){
            String cluster = argv[0];
            String startDate = argv[1];
            String endDate = argv[2];
            backup(cluster, null, startDate, endDate);
        }
        else if (argv.length == 2){
            String cluster = argv[0];
            String name = argv[1];
            restore(cluster, name);
        }
        else {
            System.out.println("Usage:");
            
            System.out.println("");
            System.out.println("Backup to a file:");
            System.out.println("  java Client cluster backupName startDate endDate");
            
            System.out.println("");
            System.out.println("Backup, discarding data:");
            System.out.println("  java Client cluster startDate endDate");
            
            System.out.println("");
            System.out.println("Restore from a file:");
            System.out.println("  java Client cluster backupName");
        }
        System.exit(0);
    }
}
