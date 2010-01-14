package com.sun.honeycomb.ndmp;

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



import java.util.HashMap;
import java.io.IOException;

import java.util.logging.Logger;

/** 
 * This class defines the primitives on which the
 * automatically-generated classes are based. 
 * See ndmp/xdr/RPCGen.java.
 */
abstract class BaseNDMP {


    // These streams are used by the NDMP Data Management Application
    // (client) to negotiate protocol, initiate, monitor, and terminate 
    // backup and restore jobs.

    XDRInputStream controlInputStream;
    XDROutputStream controlOutputStream;
    Logger LOGGER = Logger.getLogger(getClass().getName());

    abstract class message {}

    /** Requests come from the NDMP client (DMA)
     */
    abstract class request extends message {
        //request(){}
        abstract void write () throws IOException;
    }

    /** Replies come from the NDMP Data Server
     */
    abstract class reply extends message {
        abstract void write () throws IOException;
    }

    /** Requests from the NDMP Data Server
     */
    abstract class post extends message {
        abstract void write () throws IOException;
    }

    String read_string() throws IOException{
        return controlInputStream.readString();
    }
  
    long read_u_long() throws IOException{
        return controlInputStream.readUnsignedInt();
    }

    int read_int() throws IOException{
        return controlInputStream.readInt();
    }

    int read_u_int() throws IOException{
        return (int)controlInputStream.readUnsignedInt();
    }
  
    int read_short() throws IOException{
        return controlInputStream.readShort();
    }
  
    int read_u_short() throws IOException{
        return controlInputStream.readUnsignedShort();
    }
  
    int read_u_char() throws IOException{
        return controlInputStream.readByte();
    }  

    byte[] read_opaque_array(int i) throws IOException{
        byte b[] = new byte[i];
        controlInputStream.readByteArray(b);
        return b;
    }
    byte read_opaque() throws IOException{
        //--> Read to null???
        return controlInputStream.readByte();
    }
  



    void write_string(String s) throws IOException{
        controlOutputStream.writeUnsignedInt((long) s.length());
        controlOutputStream.writeString(s);
    }
  
    long read_ndmp_u_quad() throws IOException{
        return controlInputStream.readUnsignedLong();
    }
    void write_ndmp_u_quad(long l) throws IOException{
        controlOutputStream.writeUnsignedLong(l);
    }
    void write_u_long(long l) throws IOException{
        controlOutputStream.writeUnsignedInt(l);
    }
  
    void write_int(int i) throws IOException{
        controlOutputStream.writeInt(i);
    }

    void write_u_int(long i) throws IOException{
        controlOutputStream.writeUnsignedInt(i);
    }
  
    void write_short(int i) throws IOException{
        controlOutputStream.writeShort(i);
    }
  
    void write_u_short(int i) throws IOException{
        controlOutputStream.writeUnsignedShort(i);
    }
  
    void write_u_char(int i) throws IOException{
        controlOutputStream.writeByte(i);
    }  

    //   void write_opaque_array(int i) throws IOException{
    //       byte b[] = new byte[i];
    //       controlOutputStream.writeByteArray(b);
    //       b;
    //   }
    void write_opaque(byte b) throws IOException{
        controlOutputStream.writeByte(b);
    }

    static long addressToLong(byte[] b){
        if (b.length != 4)
            throw new IllegalArgumentException("Inet address cannot be " + b.length + " bytes");
        long l = 0;
        for (int i = 0; i < 4; i++){
            l = l << 8;
            l |= (int) b[i] & 0xff;
        }
        return l;
    }


    boolean ndmp_disconnected = false;


    abstract void handleRequest(boolean fromServer) throws IOException;

    void handleSession() throws IOException{
        handleSession(false);
    }

    // This is the main loop
    void handleSession(boolean fromServer) throws IOException{
        // Read the XDR header. 
        long header = controlInputStream.readUnsignedInt();
        int len = (int) (header & 0x7fffffff);
        boolean last = (header & 0x80000000) == 0;

        while (len > 0){
            //System.err.println("Reading " + len);
            controlInputStream.setReadTo(len);
            handleRequest(fromServer);

            if (ndmp_disconnected){
                LOGGER.info("NDMP session done");
                return;
            }

            // read next request
            controlInputStream.setReadTo(Long.MAX_VALUE);
            header = controlInputStream.readUnsignedInt(); 
            len = (int) (header & 0x7fffffff);
            last = (header & 0x80000000) == 0;
        }
    }



    static String describeSlot(Object slot){
        if (slot == null)
            return "NULL";
        else if (slot instanceof Object[])
            return describeArray((Object[]) slot);
        else
            return slot.toString();
    }

    static String describeArray(Object[] vars){
        if (vars == null)
            return "NULL";
        StringBuffer sb = new StringBuffer("[");
        for (int i = 0; i < vars.length; i++){
            sb.append(" ");
            sb.append(describeSlot(vars[i]));
        }
        sb.append("]");
        return sb.toString();
    }


    static String describeArray(byte[] vars){
        return new String(vars);
    }

    static String describeArray(int[] vars){
        return vars.toString();
    }

}
