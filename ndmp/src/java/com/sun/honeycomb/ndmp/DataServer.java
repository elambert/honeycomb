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

import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.oa.bulk.Session;
import com.sun.honeycomb.oa.bulk.Callback;
import com.sun.honeycomb.oa.bulk.CallbackObject;
import com.sun.honeycomb.oa.bulk.BackupRestore;
import com.sun.honeycomb.oa.bulk.Session;
import com.sun.honeycomb.oa.bulk.ReportableException;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.emd.common.QueryMap;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.config.ClusterProperties;

/**
 *
 * Honeycomb NDMP Data Server
 *
 * This class implements the Honeycomb NDMP Data Server. It overrides
 * methods int the generated NDMP class for the desired functionality.
 * The generated NDMP class also provides the dispatching loop.
 *
 *
 * @see http://www.ndmp.org/download/sdk_v4/
*/
public class DataServer extends NDMP implements Runnable{

    private final OutputStream out;
    private final InputStream in;

    public DataServer (InputStream in, OutputStream out, int dataPort, NDMPService callback){
        this.in = in;
        this.out = out;
        controlInputStream = new XDRInputStream (in); 
        controlOutputStream = new XDROutputStream (out);
        service = callback;
        DATA_PORT = dataPort;
    }

    // Callback for switch config
    private final NDMPService service;

    Session session = null;
    Thread dataThread = null;

    // We listen on this port for incoming connections (from the Tape Server)
    private final int DATA_PORT;

    final private static String HOSTNAME;
    final private static String OS = "SunOS";
    final private static String OS_VERSION = "5.10";
    final private static String HONEYCOMB_BACKUP_TYPE = "honeycomb";

    static{
        String name = "unknown";
        try{
            name = InetAddress.getLocalHost().getHostName();
        }
        catch (java.net.UnknownHostException e){}
        HOSTNAME = name;
    }

    final static String START_PARAM = "start";
    final static String END_PARAM = "end";
    final static String FORCE_PARAM = "force";

    Date startTime = null;
    Date endTime = null;
    boolean forceBackup;

    static ClusterProperties props = ClusterProperties.getInstance();

    final static String DATE_FORMAT_STRING = 
        props.getProperty("honeycomb.ndmp.DateFormat", "MM/dd/yyyy HH:mm:ss");
    final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_STRING);

    final static boolean GENERATE_FILE_HISTORY = 
        props.getPropertyAsBoolean("honeycomb.ndmp.GenerateFileHistory", false);

    final boolean PROCEED_AFTER_ERRORS = 
        props.getPropertyAsBoolean("honeycomb.ndmp.ProceedAfterErrors", false);

    long logEvery = 
        props.getPropertyAsInt("honeycomb.ndmp.LogNth", 100);

    int OUT_PORT = 
        props.getPropertyAsInt("honeycomb.ndmp.OutboundPort", 10001);

    final static String LOG_TERMINATION = 
        (props.getPropertyAsBoolean("honeycomb.ndmp.AddNewlineToLogMessages", true)) ? "\n" : "";

    final static boolean TWO_PHASE_ABORT = 
        props.getPropertyAsBoolean("honeycomb.ndmp.TwoPhaseAbort", true);

    private boolean isAuthenicated = false;
    long countObjects = 0;
    long offset = 0;


    // Backup session state from/to client
    ndmp_pval[] backupEnv;


    // State Machine

    static class Activity{}
    private final static Activity BACKING_UP = new Activity();
    private final static Activity RESTORING = new Activity();
    private  Activity activity = null;

    // These are defined by the spec
    private ndmp_data_state state = NDMP_DATA_STATE_IDLE;

    private ndmp_error error = NDMP_NO_ERR;
    private ndmp_data_operation operation = NDMP_DATA_OP_NOACTION;
    private ndmp_data_halt_reason halt_reason = NDMP_DATA_HALT_NA;

//     void transitionState(ndmp_data_state oldState, ndmp_data_state newState){
//         if (state != oldState)
//             throw new RuntimeException("bad state " + state);
//         transitionState(newState);
//     }


    void transitionState(ndmp_data_state newState){
        transitionState(newState, null);
    }
    void transitionState(ndmp_data_state newState, Activity activity){
        state = newState;
        this.activity = activity;
    }



    // These methods correspond to messages defined in the spec
    // Their argument classes, marshalling/unmarshalling, and dispatching 
    // are in the NDMP class which is generated from the XDR spec in the manner of RCPGen


    // Posts originate from the Data Server (Honeycomb). 

    /**
     * Initial post from Data Server indicating it's up
     * NDMP_REFUSED NDMP_SHUTDOWN NDMP_CONNECTED
     */
    void ndmp_notify_connection_status() throws IOException {
        ndmp_notify_connection_status_post post = new ndmp_notify_connection_status_post(NDMP_CONNECTED, 4, "");
        post(post, NDMP_NOTIFY_CONNECTION_STATUS);
    }


    static final long HONEYCOMB_LOG_ID = 0;
    static final String HONEYCOMB_PRODUCT_NAME = "StorageTek 5800";
    void ndmp_log_message_post (String message, ndmp_log_type log_type) throws IOException {

        ndmp_log_message_post post = new ndmp_log_message_post(log_type,
                                                               HONEYCOMB_LOG_ID,
                                                               HONEYCOMB_PRODUCT_NAME + " " + message + LOG_TERMINATION,
                                                               NDMP_NO_ASSOCIATED_MESSAGE,
                                                               0l);
        if (LOGGER.isLoggable(Level.INFO))
            LOGGER.info("NDMP Post log" + post);
        post(post, NDMP_LOG_MESSAGE);
    }



    ////// Replies to Requests from the client

    void handle_ndmp_connect_open_request(ndmp_connect_open_request ndmp_connect_open_request, 
                                          ndmp_header header) throws IOException{
        // --> probably should accept v 3 also
        if (ndmp_connect_open_request.protocol_version == 4){
            ndmp_connect_open_reply reply = new ndmp_connect_open_reply(NDMP_NO_ERR);
            respond(header, reply);
        }
        else {
            NDMP_NOT_SUPPORTED_ERR(header, "Bad version: " + ndmp_connect_open_request.protocol_version);
        }
    }

    void handle_ndmp_config_get_host_info_request(ndmp_header header) throws IOException{
        ndmp_config_get_host_info_reply reply = new ndmp_config_get_host_info_reply (NDMP_NO_ERR, HOSTNAME, OS, OS_VERSION, HOSTNAME);
        respond(header, reply);
    }

    private static ndmp_auth_type[] auth = {NDMP_AUTH_NONE /*, NDMP_AUTH_TEXT */};

    void handle_ndmp_config_get_server_info_request(ndmp_header header) throws IOException{
        ndmp_config_get_server_info_reply reply;
        if (isAuthenicated)
            reply = new ndmp_config_get_server_info_reply(NDMP_NO_ERR, "Sun Microsystems", HONEYCOMB_PRODUCT_NAME, "1.1", auth);
        else
            reply = new ndmp_config_get_server_info_reply(NDMP_NO_ERR, "", "", "", auth);
        respond(header, reply);
    }


    void handle_ndmp_connect_client_auth_request(ndmp_connect_client_auth_request ndmp_connect_client_auth_request, 
                                                 ndmp_header header) throws IOException{
        isAuthenicated = true;
        respond(header, new ndmp_connect_client_auth_reply(NDMP_NO_ERR));
    }


    void handle_ndmp_data_listen_request(ndmp_data_listen_request ndmp_data_listen_request, 
                                         ndmp_header header) throws IOException{

        if (NDMP_ADDR_TCP.equals(ndmp_data_listen_request.addr_type)){
            transitionState(NDMP_DATA_STATE_LISTEN);
            
            if (LOGGER.isLoggable(Level.INFO))
                LOGGER.info("NDMP Data Server listening " + ndmp_data_listen_request.addr_type);
            // --> Tell server to listen
            
            long tcpAddress = addressToLong(InetAddress.getLocalHost().getAddress());
            ndmp_tcp_addr portAddress = new ndmp_tcp_addr(tcpAddress, DATA_PORT, null);
            ndmp_addr ndmpAddress = new ndmp_addr(NDMP_ADDR_TCP, portAddress);
            respond(header, new ndmp_data_listen_reply(NDMP_NO_ERR, ndmpAddress));
        }
        else{
            NDMP_NOT_SUPPORTED_ERR(header, "Rejecting request to listen on unsupported protocol " + 
                                   ndmp_data_listen_request.addr_type);
        }
    }


    void handle_ndmp_data_get_state_request(ndmp_header header) throws IOException{
        //ndmp_data_get_state_reply(long unsupported,
        //       ndmp_error error,
        //       ndmp_data_operation operation,
        //       ndmp_data_state state,
        //       ndmp_data_halt_reason halt_reason,
        //       ndmp_u_quad bytes_processed,
        //       ndmp_u_quad est_bytes_remain,
        //       long est_time_remain,
        //       ndmp_addr data_connection_addr,
        //       ndmp_u_quad read_offset,
        //       ndmp_u_quad read_length) {

        //                   Note: If the Data Server does not support the est_bytes_remain 
        //                   variable, it MUST assert the NDMP_DATA_STATE_EST_BYTES_REMAIN_UNS 
        //                   bit in the NDMP_DATA_GET_STATE reply unsupported field. 
        int unsupported = NDMP_DATA_STATE_EST_TIME_REMAIN_UNS | NDMP_DATA_STATE_EST_BYTES_REMAIN_UNS;

        byte[] b = tapeServer.getAddress().getAddress();
        long longAddress = addressToLong(b);
        ndmp_pval[] ndmp_pval = {};
        ndmp_tcp_addr[] tcpAddress = {new ndmp_tcp_addr(longAddress, tapeServer.getPort(), ndmp_pval)};
        ndmp_addr address = new ndmp_addr(NDMP_ADDR_TCP, tcpAddress);

        long est = 0;
        if (session != null)
            offset = session.getBytesProcessed();
        ndmp_data_get_state_reply reply = new ndmp_data_get_state_reply(unsupported,
                                                                        error,
                                                                        operation,
                                                                        state,
                                                                        halt_reason,
                                                                        offset,
                                                                        est,
                                                                        est,
                                                                        address,
                                                                        est,
                                                                        est);
        respond(header, reply);
    }

    void handle_ndmp_config_get_butype_info_request(ndmp_header header) throws IOException{
        ndmp_pval[] vars = {new ndmp_pval("start", "enter start time"), 
                            new ndmp_pval("end", "enter end time"), 
                            new ndmp_pval("force", "N"), 
                            new ndmp_pval("TYPE", HONEYCOMB_BACKUP_TYPE)/*,
                            //new ndmp_pval("HIST", "y"),
                            new ndmp_pval("PATHNAME_SEPARATOR", "\\") */};
        
        if (LOGGER.isLoggable(Level.INFO))
            LOGGER.info("NDMP Claiming Honeycomb to get_butype_info_request");
        ndmp_butype_info[] info = {new ndmp_butype_info(HONEYCOMB_BACKUP_TYPE, vars, 
                                                        NDMP_BUTYPE_BACKUP_INCREMENTAL | 
                                                        NDMP_BUTYPE_BACKUP_FH_FILE | 
                                                        NDMP_BUTYPE_RECOVER_INCREMENTAL)  
                                   /*, new ndmp_butype_info("dump", vars, NDMP_BUTYPE_BACKUP_INCREMENTAL) */
        };
        ndmp_config_get_butype_attr_reply reply = new ndmp_config_get_butype_attr_reply(NDMP_NO_ERR, info);
        respond(header, reply);
    }

    class Aborted extends RuntimeException{

    }

    void handle_ndmp_data_abort_request(ndmp_header header) throws IOException{
        if (state == NDMP_DATA_STATE_ACTIVE ||
            state == NDMP_DATA_STATE_CONNECTED ||
            state == NDMP_DATA_STATE_LISTEN){

            transitionState(NDMP_DATA_STATE_HALTED);
            respond(header, new ndmp_data_abort_reply(NDMP_NO_ERR));
             if (dataThread != null)
                 dataThread.interrupt();
            throw new Aborted();
        }
        else{
            respond(header, new ndmp_data_abort_reply(NDMP_ILLEGAL_STATE_ERR));
        }
    }

    void handle_ndmp_connect_close_request(ndmp_header header) throws IOException{
        // All NDMP requests (except NDMP_CONNECT_CLOSE) from the DMA to the 
        // NDMP Server have associated NDMP reply messages that MUST be returned 
        // by the server to indicate success or failure. 
        ndmp_disconnected = true;
    }


    InetSocketAddress tapeServer = null;
    
    void handle_ndmp_data_connect_request(ndmp_data_connect_request ndmp_data_connect_request, 
                                          ndmp_header header)
        throws IOException{
        updateHeader(header);

        if (!ndmp_data_connect_request.addr.ndmp_addr_type.equals(NDMP_ADDR_TCP)){
            header.error_code = NDMP_CONNECT_ERR;
            write_ndmp_header(header);
            controlOutputStream.sendMessage();
        }
        ndmp_tcp_addr[] addrs = (ndmp_tcp_addr[]) ndmp_data_connect_request.addr.body;
        tapeServer = decodeIP(addrs[0].ip_addr, addrs[0].port);
        transitionState(NDMP_DATA_STATE_CONNECTED);
        respond(header, new ndmp_data_connect_reply(NDMP_NO_ERR));
    }

    void handle_ndmp_data_start_backup_request(ndmp_data_start_backup_request ndmp_data_start_backup_request, 
                                               ndmp_header header) throws IOException{

        String butype_name = ndmp_data_start_backup_request.butype_name;
        backupEnv = ndmp_data_start_backup_request.env;
        //describeVars(backupEnv);
        if (state == NDMP_DATA_STATE_CONNECTED){
            startTime = null;
            endTime = null;
            forceBackup = false;

            String name = null;
            String val = null;

            try {                
                for (int i = 0; i < backupEnv.length; i++){
                    name = backupEnv[i].name;
                    val = backupEnv[i].value;
                    if (START_PARAM.equals(name)){
                        startTime = DATE_FORMAT.parse(val);
                    }
                    else if (END_PARAM.equals(name)){
                        endTime = DATE_FORMAT.parse(val);
                    }
                    else if (FORCE_PARAM.equals(name)){
                        forceBackup = "Y".equalsIgnoreCase(val);
                    }
                    else {
                        System.setProperty(name, val);
                    }
                }
            }
            catch (ParseException pe){
                Object[] args = {name, "\"" + val + "\"", DATE_FORMAT_STRING};
                String str = MessageFormat.format(service.getLocalizedString(UNABLE_TO_PARSE_DATE_FROM_0_ENVIRONMENT_VARIABLE_1_Expected_date_in_form_3), args);
                LOGGER.log(ExtLevel.EXT_SEVERE, str, pe);
                ndmp_log_message_post(str, NDMP_LOG_ERROR);
                respond(header, new ndmp_data_start_backup_reply(NDMP_ILLEGAL_ARGS_ERR));
                return;
            }

            if (START_PARAM == null){
                Object[] args = {START_PARAM};
                String message = MessageFormat.format(service.getLocalizedString(BACKUP_REQUEST_MISSING_REQUIRED_0_ENVIRONMENT_VARIABLE), args);
                reportError(message);
                respond(header, new ndmp_data_start_backup_reply(NDMP_ILLEGAL_ARGS_ERR));
            }
            else if (END_PARAM == null){
                Object[] args = {END_PARAM};
                String message = MessageFormat.format(service.getLocalizedString(BACKUP_REQUEST_MISSING_REQUIRED_0_ENVIRONMENT_VARIABLE), args);
                reportError(message);
                respond(header, new ndmp_data_start_backup_reply(NDMP_ILLEGAL_ARGS_ERR));
            }
            else if ((endTime.getTime() - startTime.getTime()) < 1000 * 60){
                Object[] args = {END_PARAM};
                String message = "Backup range too short: " + startTime + " to " + endTime;
                reportError(message);
                respond(header, new ndmp_data_start_backup_reply(NDMP_ILLEGAL_ARGS_ERR));
            }
            else{
                transitionState(NDMP_DATA_STATE_ACTIVE, BACKING_UP);
                service.setStatus(BACKUP);
                respond(header, new ndmp_data_start_backup_reply(NDMP_NO_ERR));
                Object[] dates = {startTime, endTime};
                String message = MessageFormat.format(service.getLocalizedString(BACKUP_REQUESTED_BACKUP_FROM__0_TO_1), dates);
                logExternal(message);
                dataThread = new Backup();
                dataThread.start();
            }
        }
        else{
            respond(header, new ndmp_data_start_backup_reply(NDMP_ILLEGAL_STATE_ERR));
        }
    }

    void handle_ndmp_data_start_recover_request(ndmp_data_start_recover_request ndmp_data_start_recover_request,
                                                ndmp_header header) throws IOException{
        String butype_name = ndmp_data_start_recover_request.butype_name;
        backupEnv = ndmp_data_start_recover_request.env;
        
        //ndmp_name[] nlist;
        if (state == NDMP_DATA_STATE_CONNECTED){
            transitionState(NDMP_DATA_STATE_ACTIVE, RESTORING);
            service.setStatus(RESTORE);
            String message = service.getLocalizedString(RESTORE_REQUESTED);
            logExternal(message);
            dataThread = new Restore();
            dataThread.start();
            respond(header, new ndmp_data_start_recover_reply(NDMP_NO_ERR));
        }
        else{
            respond(header, new ndmp_data_start_recover_reply(NDMP_ILLEGAL_STATE_ERR));
        }
    }

    void handle_ndmp_data_stop_request(ndmp_header header) throws IOException{
        if (state == NDMP_DATA_STATE_HALTED){
            transitionState(NDMP_DATA_STATE_IDLE);
            service.setObjectsProcessed(0);
            service.setStatus(INACTIVE);
            respond(header, new ndmp_data_stop_reply(NDMP_NO_ERR));
        }
        else{
            respond(header, new ndmp_data_stop_reply(NDMP_ILLEGAL_STATE_ERR));
        }
    }

    private ndmp_addr_type tcpAddress[] = {NDMP_ADDR_TCP};
    private ndmp_addr_type noAddress[] = {};

    void handle_ndmp_config_get_connection_type_request(ndmp_header header) throws IOException{
        //ndmp_addr_type[]
        respond(header, new ndmp_config_get_connection_type_reply(NDMP_NO_ERR, tcpAddress));
    }

    ndmp_class_list[] emptyClassList = {};
    int[] versions = {};
    ndmp_class_list[] defaultClassList = {new ndmp_class_list(0, versions)};

    void handle_ndmp_config_get_ext_list_request(ndmp_header header) throws IOException{
        respond(header, new ndmp_config_get_ext_list_reply(NDMP_NO_ERR, emptyClassList));
    }

    void handle_ndmp_data_get_env_request(ndmp_header header) throws IOException{
        if (/*service.getActivity().equals(service.getLocalizedString(BACKUP))*/ true){
            respond(header, new ndmp_data_get_env_reply(NDMP_NO_ERR, backupEnv));
        }
        else{
            ndmp_pval[] vars = {new ndmp_pval(START_PARAM, DATE_FORMAT.format(startTime)), 
                                new ndmp_pval(END_PARAM, DATE_FORMAT.format(endTime))};
            respond(header, new ndmp_data_get_env_reply(NDMP_NO_ERR, vars));
        }
    }





    // Utility methods


    private int messageCount = 0;
    void post(post post, 
              ndmp_message message) throws IOException{
        ndmp_header header = new ndmp_header(messageCount++,
                                             System.currentTimeMillis() / 1000,
                                             NDMP_MESSAGE_REQUEST,
                                             message,
                                             0,
                                             NDMP_NO_ERR);
        write_ndmp_header(header);
        post.write();
        controlOutputStream.sendMessage();
        
        if (LOGGER.isLoggable(Level.INFO)){
            LOGGER.info("NDMP Message:");
            LOGGER.info("NDMP POST " + message.name);
            LOGGER.info(post.toString());
        }
    }

    String describeVars(ndmp_pval[] vars){
        StringBuffer sb = new StringBuffer("\n vars: ");
        for (int i = 0; i < vars.length; i++)
            sb.append(vars[i]);
        sb.append("\n");
        return sb.toString();
    }

    void NDMP_NOT_SUPPORTED_ERR(ndmp_header header) throws IOException{
        NDMP_NOT_SUPPORTED_ERR(header, header.message_code.toString());
    }

    void NDMP_NOT_SUPPORTED_ERR(ndmp_header header, String arg) throws IOException{
        
        if (LOGGER.isLoggable(Level.INFO))
            LOGGER.info("NDMP Punting on " + arg);
        updateHeader(header);
        header.error_code = NDMP_NOT_SUPPORTED_ERR;
        write_ndmp_header(header);
        controlOutputStream.sendMessage();
    }

    void updateHeader(ndmp_header header) {
        header.reply_sequence = header.sequence;
        //header.sequence = header.sequence + 1;
        header.time_stamp = System.currentTimeMillis() / 1000;
        header.message_type = NDMP_MESSAGE_REPLY;
        header.error_code = NDMP_NO_ERR;
    }

    void respond(ndmp_header header, reply reply) throws IOException{
        respond(header, reply, false);
    }

    void respond(ndmp_header header, reply reply, boolean debug) throws IOException{        
        
        if (LOGGER.isLoggable(Level.INFO)){
            LOGGER.info("NDMP Message:");
            LOGGER.info("NDMP Reply " + header.message_code);
            LOGGER.info(reply.toString());
        }
        updateHeader(header);
        
        if (LOGGER.isLoggable(Level.INFO))
            LOGGER.info("header " + header);
        write_ndmp_header(header);
        if (debug){
            controlOutputStream.sendMessage(debug);
            
            if (LOGGER.isLoggable(Level.INFO))
                LOGGER.info("====================================================");
            write_ndmp_header(header);
        }
        if (reply != null)
            reply.write();
        controlOutputStream.sendMessage(debug);
    }

    class FailedException extends RuntimeException{}
    class HaltedGracefully extends FailedException{}

    class WrappedException extends RuntimeException{
        WrappedException(Exception e){
            super(e);
        }
        WrappedException(String reason, Exception e){
            super(reason, e);
        }
    }


    private class BackupRestoreCallback implements Callback{
        String opVerb;
        String opNoun;
        // Section of the bulk stream are we processing
        Object currentType = null;
        BackupRestoreCallback(String opVerb, String opNoun){
            this.opVerb = opVerb;
            this.opNoun = opNoun;
        }
        private String describeType(Object type){
            if (type == null)
                return "None";
            if (type.equals(CallbackObject.OBJECT_CALLBACK))
                return "objects";
            else if (type.equals(CallbackObject.SYS_CACHE_CALLBACK))
                return "system caches";
            else if (type.equals(CallbackObject.CLUSTER_CONFIG_CALLBACK))
                return "cluster configuration data";
            else if (type.equals(CallbackObject.SILO_CONFIG_CALLBACK))
                return "silo configuration data";
            else if (type.equals(CallbackObject.SCHEMA_CONFIG_CALLBACK))
                return "schema";
            else if (type.equals(CallbackObject.SESSION_COMPLETED))
                return "EOF";
            else throw new IllegalArgumentException("Unknown type constant " + type);
        }

        //  Hook for OA to notify us of progress
        public void callback(CallbackObject cb){
            offset = cb.getStreamOffset();
            String name = null;
            Object type = cb.getCallbackType();
            service.setBytesProcessed(offset);
            //if (LOGGER.isLoggable(Level.FINEST))
            //LOGGER.info(offset + " offset for " + describeType(type));
            if (!type.equals(currentType)){
                currentType = type;
                LOGGER.info("NDMP " + opVerb +" " + describeType(currentType));
            }
            try{
                cb.checkStatus();
                if (CallbackObject.SESSION_COMPLETED.equals(currentType)){
                    LOGGER.info("NDMP normal exit "  + opVerb + " " + countObjects + " objects, " + offset + " bytes");
                }
                else{
                    if (CallbackObject.OBJECT_CALLBACK.equals(cb.getCallbackType())){
                        SystemMetadata sm = cb.getObjectCallback();
                        if (GENERATE_FILE_HISTORY)
                            noteFH(sm);
                        countObjects++;
                        name = sm.getOID().toExternalHexString();
                        //System.out.println("DataServer: " + countObjects + " " + name + " " + offset);
                        if (logEvery != 0 && countObjects % logEvery == 0){
                            ndmp_log_message_post("NDMP " + opVerb + " " + name + " " + 
                                                  new Date(sm.getCTime()), NDMP_LOG_NORMAL);
                            LOGGER.info("NDMP " + opVerb + " " + countObjects + " objects, " + offset + " bytes");
                        }
                        service.setObjectsProcessed(countObjects);
                    }
                }
            }
            catch (InterruptedException e){
                if (state != NDMP_DATA_STATE_HALTED){
                    fail(e);
                }
                else {
                    throw new HaltedGracefully();
                }
            }
            catch (Exception e){
                fail(e);
            }
        }

        private void fail(Exception e){
            String message = e.getMessage();
            if (message == null) message = e.toString() + " " + describeType(currentType);;
            reportError(e, message);
            throw new FailedException();
        }
            

        // File history
        int fhi = 0;
        int fhGroupSize = 1;
        ndmp_file files[] = new ndmp_file[50];

        void noteFH(SystemMetadata sm) throws IOException{
            String name = "/backup/" + sm.getOID();
            ndmp_file_name[] names = {new ndmp_file_name(NDMP_FS_OTHER, name)};

            // NDMP_FILE_STAT_GROUP_UNS | NDMP_FILE_STAT_ATIME_UNS | NDMP_FILE_STAT_CTIME_UNS
            long unsupported = 0;
            ndmp_fs_type fs_type = NDMP_FS_OTHER; // NDMP_FS_UNIX
            ndmp_file_type ftype = NDMP_FILE_REG;
            long mtime = sm.getCTime();
            long atime = sm.getRTime();
            long ctime = sm.getCTime();
            long owner = 0;
            long group = 0;
            long fattr = 0;
            long size = sm.getSize();
            long links = 0;
            ndmp_file_stat[] stats = {new ndmp_file_stat(unsupported, fs_type, ftype,
                                                         mtime, atime, ctime, 
                                                         owner, group, 
                                                         fattr, size, links)};
            long node = 0;
            long fh_info = offset;
            fh_info = 32768;
            //ndmp_file[] files = {new ndmp_file(names, stats, node, fh_info)};

            if (fhi == fhGroupSize){
                post(new ndmp_fh_add_file_post(files), NDMP_FH_ADD_FILE);
                fhi = 0;
            }
            files[fhi] = new ndmp_file(names, stats, node, fh_info);
        }
    }

    private abstract class Worker extends Thread {
        String name;
        Worker(String name){
	    super(name);
            this.name = name;
        }

        abstract void doWork() throws Exception;

        private void postReportingError(post post, 
                                        ndmp_message message) {
            try{
                post(post, message);
            }
            catch (IOException ioe){
                reportError(ioe);
            }
        }

        void workSafely(Channel channel){
            try{
                doWork();
                post (new ndmp_notify_data_halted_post(NDMP_DATA_HALT_SUCCESSFUL), NDMP_NOTIFY_DATA_HALTED);
            }
            catch (HaltedGracefully ok){
                postReportingError (new ndmp_notify_data_halted_post(NDMP_DATA_HALT_ABORTED), NDMP_NOTIFY_DATA_HALTED);
            }
            catch (FailedException failed){
                // This was already reported
                postReportingError (new ndmp_notify_data_halted_post(NDMP_DATA_HALT_INTERNAL_ERROR), NDMP_NOTIFY_DATA_HALTED);
            }
            catch (Exception e){
                reportError(e);
                postReportingError (new ndmp_notify_data_halted_post(NDMP_DATA_HALT_INTERNAL_ERROR), NDMP_NOTIFY_DATA_HALTED);
            }
            // NDMP_DATA_HALT_CONNECT_ERROR
            finally{
                countObjects = offset = 0;
                service.setObjectsProcessed(0);
                service.setBytesProcessed(0);
                session = null;
                transitionState(NDMP_DATA_STATE_HALTED);
                operation = NDMP_DATA_OP_NOACTION;
                try{
                    if (channel != null && channel.isOpen())
                        channel.close();
                } catch (Exception e){LOGGER.log(Level.SEVERE, "NDMP close " + name + " channel failed", e);}
            }
        }
    }
    private class NullWritable implements WritableByteChannel{
        public int write(ByteBuffer src){
            int n = src.remaining();
            src.clear();
            //src.flip();
            return n;
        }
        boolean open = true;
        public void close(){open = false;}
        public boolean isOpen(){return open;}
    }

    private class Backup extends Worker {
        WritableByteChannel backupChannel = null;
        Backup(){
            super ("NDMP Backup " + new Date());
        }
    
        final private static String BACKUP_DATA = "/data/3/backup";

        public void run(){
            service.setStatus(BACKUP_STARTED);
            try{
                if (new File("/fakebackup").exists()){
                    LOGGER.info("NDMP backing up to " + BACKUP_DATA);
                    FileOutputStream fos = new FileOutputStream(BACKUP_DATA);
                    backupChannel = fos.getChannel();
                }
                else if (new File("/discard").exists()){
                    LOGGER.info("NDMP discarding backup");
                    backupChannel = new NullWritable();
                }
                else{
                    if (LOGGER.isLoggable(Level.INFO))
                        LOGGER.info("NDMP backing up to " + tapeServer + " " + OUT_PORT);
                    SocketChannel sc = service.openSocketChannel(tapeServer, OUT_PORT);
                    backupChannel = new BufferedChannelWriter(sc);
                }
            }
            catch (Exception ioe){
                reportError(ioe);
                return;
            }

            workSafely(backupChannel);
        }

        void doWork() throws Exception{
            long backupStartTime = System.currentTimeMillis();
        
            if (LOGGER.isLoggable(Level.INFO)){
                LOGGER.info("Starting NDMP backup " + new Date() + " from " + startTime + " to " + endTime);
            }

            session = BackupRestore.startBackupSession(startTime.getTime(),
                                                       endTime.getTime(),
                                                       backupChannel,
                                                       new BackupRestoreCallback("backed up", "backup"),
                                                       forceBackup);
        
            if (LOGGER.isLoggable(Level.INFO))
                LOGGER.info("Finished NDMP backup " + countObjects + " objects " + 
                            new Date() + " " + ((System.currentTimeMillis() - backupStartTime) / 1000.0));

            Object args[] = {new Long(countObjects), new Long(offset)};
            String message = MessageFormat.format(service.getLocalizedString(BACKUP_FINISHED_0_OBJECTS_1_BYTES_BACKED_UP), args);
            logExternal(message);
        }

    }


    private class Restore extends Worker{

        ReadableByteChannel recoverChannel = null;

        Restore(){
            super ("NDMP Restore " + new Date());
        }
        public void run(){
            service.setStatus(RESTORE_STARTED);
            nioRestore();
        }


        void OARestore(ReadableByteChannel recoverChannel) throws Exception{
            session = BackupRestore.startRestoreSession(recoverChannel,
                                                        new BackupRestoreCallback("restored", "restore"));
        }

        void nioRestore(){
            try{
                operation = NDMP_DATA_OP_RECOVER;
                ndmp_notify_data_read_post post = new 
                    ndmp_notify_data_read_post(0l, Long.MAX_VALUE);
                post(post, NDMP_NOTIFY_DATA_READ);
                if (new File("/tmp/fakerestore").exists() && new File("/tmp/backup").exists()){
                    FileInputStream fis = new FileInputStream("/tmp/restore");
                    System.err.println("Restoring from /tmp/restore");
                    recoverChannel = fis.getChannel();
                }
                else{
                    if (LOGGER.isLoggable(Level.INFO))
                        LOGGER.info("NDMP restoring from " + tapeServer + " " + OUT_PORT);
                    recoverChannel = service.openSocketChannel(tapeServer, OUT_PORT);
                    ((SocketChannel)recoverChannel).configureBlocking(true);
                }
            }
            catch (Exception e){
                reportError(e);
                return;
            }
            workSafely(recoverChannel);
        }

        void doWork() throws Exception{
            
                if (LOGGER.isLoggable(Level.INFO))
                    LOGGER.info("NDMP started restore session.startSession() " + new Date());
                long t = System.currentTimeMillis();

                // Two debugging hooks
                if (new File("/tmp/capturerestore").exists()){
                    FileOutputStream fos = new FileOutputStream("/tmp/restore");
                    java.nio.channels.FileChannel fc = fos.getChannel();
                    long n = fc.transferFrom(recoverChannel, 0, 10000000l);
                    System.out.println("NDMP fake restore wrote " + n + " bytes");
                    long total = n;
                    service.setBytesProcessed(total);
                    while (n != 0){
                        n = fc.transferFrom(recoverChannel, total, 10000000l);
                        total += n;
                        service.setBytesProcessed(total);
                        System.out.println("NDMP fake restore wrote " + n + " bytes");
                    }
                    System.out.println("NDMP closing fake restore " + total + " bytes");
                    fc.close();
                    fos.close();
                }
                else if (new File("/tmp/discardrestore").exists()){
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate (1000000);
                    long n = recoverChannel.read(bb);
                    long total = 0;
                    while (n != 0){
                        System.out.println("NDMP read " + n + " total of " + total + " bytes in " +
                                ((System.currentTimeMillis() - t) / 1000.0) + " seconds");
                        total += n;
                        bb.clear();
                        n = recoverChannel.read(bb);
                    }
                    LOGGER.info("NDMP finished restoring " + total + " bytes in " +
                                ((System.currentTimeMillis() - t) / 1000.0) + " seconds");
                }
                else{
                    // Perform restore (non-debugging path)
                    OARestore(recoverChannel);
                }
            
                if (LOGGER.isLoggable(Level.INFO))
                    LOGGER.info("NDMP finished restoring " + countObjects + " objects, " + offset + "bytes");

                Object args[] = {new Long(countObjects), new Long(offset)};
                String message = MessageFormat.format(service.getLocalizedString(RESTORE_FINISHED_0_OBJECTS_1_BYTES_RESTORED), args);
                logExternal(message);
        }

    }



    /////////////////// Utility methods


    ndmp_file_name makeName(){
        return null;
    }

    void handleRequest(boolean fromServer) throws IOException{
        ndmp_header header = read_ndmp_header();
        if (fromServer)
                System.out.println(readReply(header));
            else
                dispatch(header);
    }

    /** 
     * Log to external log and NDMP client
     */
    private void logExternal(String message) throws IOException{
        LOGGER.log(ExtLevel.EXT_INFO, message);
        ndmp_log_message_post(message, NDMP_LOG_NORMAL);
    }


    /** 
     * Report error to NDMP client, external log, alerts
     */
    private void reportError(Exception e){
        String message = e.getMessage();
        if (message != null) 
            reportError(e, message);
        else
            reportError(e, e.toString());
    }

    private void reportError(Exception e, String message){
        try{
            LOGGER.log(Level.SEVERE, message, e);
            reportError(message);
        }
        // Don't error out here!
        catch(Exception ne){
            LOGGER.log(Level.SEVERE, "Exception reporting exception " + message + e, ne);
        }
    }

    private void reportError(String errorString){
        String key = REQUEST_FAILED_WITH_ERR_0;
        if (activity == BACKING_UP){
            key = BACKUP_FAILED_WITH_ERR_0;
        }
        else if (activity == RESTORING) {
            key = RESTORE_FAILED_WITH_ERR_0;
        }
        try{
            Object[] args = {errorString};
            String str = MessageFormat.format(service.getLocalizedString(key), args);
            LOGGER.log(ExtLevel.EXT_SEVERE, str + " bytes processed: " + offset);
            ndmp_log_message_post(str, NDMP_LOG_ERROR);
            alert(str);
        }
        catch (IOException ioe){LOGGER.log(Level.SEVERE, errorString, ioe);}
    }

    private void alert(String message){
        service.alert(message);
    }

    public void run() {
        try {
            Thread.currentThread().setName("NDMP session " + new Date());
            ndmp_notify_connection_status();
            ndmp_log_message_post("accepted request", NDMP_LOG_NORMAL);
            String message = service.getLocalizedString(ACCEPTED);
            LOGGER.log(ExtLevel.EXT_INFO, message);
            handleSession();
        }
        //catch (Aborted ioe){
        catch (IOException ioe){
//             if (ndmp_disconnected){
//                 // Can we gracefully abort the control channel read?
//                 if (LOGGER.isLoggable(Level.INFO))
//                     LOGGER.info("NDMP Noticed that connection was closed");
//             }
//             else 
            if (ndmp_disconnected){
                // The Bakbone DMA only sends a NDMP_CONNECT_CLOSE if a backup 
                // operation was initiated, not if pre-connected messages were exchanged
                // (usually to decide whether to add Honeycomb to a list of tape servers)
                // 
                // Spec: When the DMA finishes using the connection it SHOULD send an 
                // NDMP_CONNECT_CLOSE message prior to closing the TCP connection. The 
                // NDMP Server SHOULD not close the connection until requested to do so 
                // by the DMA. If forced to close the connection due to a local error or 
                // shutdown it SHOULD first send an NDMP_NOTIFY_CONNECTION_STATUS 
                // request containing an NDMP_SHUTDOWN reason code. 

                if (LOGGER.isLoggable(Level.INFO))
                    LOGGER.log(Level.SEVERE, "NDMP Connection closed " + state, ioe);
            }
            else {
                LOGGER.log(ExtLevel.EXT_SEVERE, "NDMP Connection closed without NDMP_CONNECT_CLOSE request");
                LOGGER.log(Level.SEVERE, "NDMP Connection closed without NDMP_CONNECT_CLOSE request" + state, ioe);
            }
        }
        catch (Exception e){
            reportError(e, "NDMP unexpected exception caught in top level loop " + state + " " + e);
        }
        finally{
            if (state == NDMP_DATA_STATE_ACTIVE || state == NDMP_DATA_STATE_HALTED){
                service.setObjectsProcessed(0);
                service.setStatus(INACTIVE);
            }
            transitionState(NDMP_DATA_STATE_IDLE);
            try{
                if (dataThread != null)
                    dataThread.interrupt();
                in.close();
                out.close();
            }
            catch (IOException ioe){
                LOGGER.log(Level.SEVERE, "NDMP exception closing control channel", ioe);
            }
        }
    }

    /* 
     * Internationalized externally visible strings
     * External logging
     * Alerts
     * NDMP Client log
     */
    final static String INACTIVE = "info.ndmp.idle";
    final static String BACKUP = "info.ndmp.backup";
    final static String RESTORE = "info.ndmp.restore";
    private final static String ACCEPTED = "info.ndmp.accepted";
    final static String N_OBJECTS_N_BYTES_PROCESSED = "info.ndmp.objectsBytesProcessed";
    private final static String BACKUP_REQUESTED_BACKUP_FROM__0_TO_1 = "info.ndmp.backupRequest";
    private final static String BACKUP_STARTED = "info.ndmp.backupStarted";
    private final static String BACKUP_FINISHED_0_OBJECTS_1_BYTES_BACKED_UP = "info.ndmp.backupFinished";
    private final static String RESTORE_REQUESTED = "info.ndmp.restoreRequest";
    private final static String RESTORE_STARTED = "info.ndmp.restoreStarted";
    private final static String RESTORE_FINISHED_0_OBJECTS_1_BYTES_RESTORED = "info.ndmp.restoreFinished";
    private final static String REQUEST_FAILED_WITH_ERR_0 = "err.ndmp.error";
    private final static String BACKUP_FAILED_WITH_ERR_0 = "err.ndmp.backupError";
    private final static String RESTORE_FAILED_WITH_ERR_0 = "err.ndmp.restoreError";
    private final static String UNABLE_TO_PARSE_DATE_FROM_0_ENVIRONMENT_VARIABLE_1_Expected_date_in_form_3 = "err.ndmp.parseFields";
    private final static String BACKUP_REQUEST_MISSING_REQUIRED_0_ENVIRONMENT_VARIABLE = "err.ndmp.missingField";
    
    private static InetSocketAddress decodeIP(long ip, int port) throws java.net.UnknownHostException{
        byte[] b = {(byte)((ip >> 24) & 0xff),
                    (byte)((ip >> 16) & 0xff),
                    (byte)((ip >> 8) & 0xff),
                    (byte)(ip & 0xff)};
        return new InetSocketAddress(InetAddress.getByAddress(b), port);
    }
}



//              Log  Alert  External/Netvault log
// ===========================================
// Errors        X     X             X
// Start/stop    X                   X
// Progress      X                   X
// Info          X



// Log to external
// Add default cluster properties
// Check state machine/


// fix report error
// Confirm reporting of offset
// Alerts
// CLI madness

