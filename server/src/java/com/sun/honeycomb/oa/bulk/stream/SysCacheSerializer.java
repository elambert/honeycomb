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



package com.sun.honeycomb.oa.bulk.stream;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService.Proxy;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.emd.MDManagedService;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.cache.SystemCacheConstants;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.SysCacheException;
import com.sun.honeycomb.emd.server.ProcessingCenter;
import com.sun.honeycomb.emd.server.SysCache;
import com.sun.honeycomb.oa.bulk.CallbackObject;
import com.sun.honeycomb.oa.bulk.SerializationException;
import com.sun.honeycomb.oa.bulk.Session;
import com.sun.honeycomb.util.Exec;

public class SysCacheSerializer extends ContentSerializer {
        
    protected static Logger LOG = Logger.getLogger(SysCacheSerializer.class.getName());
 
    protected Disk[] _disks = null;
   
    public SysCacheSerializer(Session session) { 
        super(session);
    }
    
    public CallbackObject serialize(StreamWriter writer) throws SerializationException {
        int backedup = 0;
        boolean[] uniqueFailures = new boolean[16];
        
        for (int d = 0; d < _disks.length; d++) {
           
            if (_disks[d] == null || !_disks[d].isEnabled()) {
                // doens't really make a difference to mark the cache since the 
                // disk is inacessible
                writer.writeHeader(Constants.NODE_ID_HEADER, -1);
                writer.writeHeader(Constants.DISK_ID_HEADER, -1);
                writer.writeSeparator();
                writer.writeSeparator();
                continue;
            }
            
            int diskIndex = _disks[d].diskIndex();
            int nodeId = _disks[d].nodeId(); 
            
            MDManagedService remote = MDManagedService.Proxy.getServiceAPI(nodeId);
            ReadableByteChannel channel = null;

            if (remote == null)
                throw new SerializationException("Proxy null for node: " + nodeId);
            
            try {
                writer.writeHeader(Constants.NODE_ID_HEADER, nodeId);
                writer.writeHeader(Constants.DISK_ID_HEADER, diskIndex);
                writer.writeSeparator();
              
                // by syncing the remote cache to the filesystem we are 
                // guaranteed to have all data on disk
                remote.sync(CacheClientInterface.SYSTEM_CACHE,_disks[d]);
                channel = backup(_disks[d].getNodeIpAddr(), _disks[d]);
              
                ByteBuffer dst = ByteBuffer.allocateDirect(1024*64);  
              
              
                int read = -1;
                while ((read = channel.read(dst)) > 0) {
                    
                    while (dst.position() < dst.capacity()) {
                        int readI = channel.read(dst);
                        
                        if (readI == -1) break;
                       
                        read+=readI;
                    } 
                    
                    dst.flip();
                    writer.writeIntAsHex(read); 
                    writer.write(dst, read);
                    dst.clear();
                    writer.writeSeparator();
                }
                
                backedup++;

                writer.writeIntAsHex(0);
                writer.writeSeparator();
                writer.writeSeparator();
            } catch (EMDException e) {
                LOG.log(Level.SEVERE,"Unable to backup system cache for "  + nodeId + ":" + diskIndex,e);
                uniqueFailures[nodeId-101]=true;
            } catch (IOException e) {
                LOG.log(Level.SEVERE,"Unable to backup system cache for "  + nodeId + ":" + diskIndex,e);
                uniqueFailures[nodeId-101]=true;
            } catch (ManagedServiceException e) {
                LOG.log(Level.SEVERE,"Unable to backup system cache for "  + nodeId + ":" + diskIndex,e);
                uniqueFailures[nodeId-101]=true;
            } finally { 
                if (channel != null)
                    try {
                        channel.close();
                    } catch (IOException e) { /* ignore */ }
            }
        }
        
        if (countFailures(uniqueFailures) > SysCache.calcMaxUniqFailures())
            throw new SerializationException("Too many failures on system cache backup, " +
                                             countFailures(uniqueFailures) + 
                                             " failures.");
      
        CallbackObject obj = new CallbackObject(null, CallbackObject.SYS_CACHE_CALLBACK);
        obj.setCacheCount(backedup); 
        return obj; 
    } 
    
    private int countFailures(boolean[] failures) { 
        int uniqFailurecount = 0;
        for(int i = 0; i < failures.length; i++) {
            if (failures[i])
                uniqFailurecount++;
        }
        return uniqFailurecount; 
    }

    public CallbackObject deserialize(StreamReader reader, Map groupHeaders) 
           throws SerializationException {
      
        int restored = 0;
        boolean[] uniqueFailures = new boolean[16];
       
        int cacheCount = Integer.parseInt((String)groupHeaders.get(Constants.NUM_CACHES_HEADER));
     
        for (int d = 0; d < cacheCount; d++) {
            
            Map headers = reader.readHeaders();
            int nodeId = Integer.parseInt((String)headers.get(Constants.NODE_ID_HEADER));
            int diskIndex = Integer.parseInt((String)headers.get(Constants.DISK_ID_HEADER));
           
            if (nodeId == -1 || diskIndex == -1) {
                LOG.warning("Empty cache found, skipping.");
                reader.readSeparator();
                continue;
            }
            
            Disk disk = DiskProxy.getDisk(nodeId, diskIndex);
           
            MDManagedService remote = MDManagedService.Proxy.getServiceAPI(nodeId);
            Proxy proxy = ServiceManager.proxyFor(nodeId);
         
            // proxy can be null at runtime so we must make sure to just handle
            // and count as an error.
            if (proxy == null)
                throw new SerializationException("Proxy null for node: " + nodeId);
            
            String address = proxy.getNode().getAddress();
            PrintStream ps = null;
           
            if (remote == null)
                throw new SerializationException("Proxy null for node: " + nodeId);
                
            try {
                if (_session.optionChosen(Session.REPLAY_BACKUP_OPTION)) { 
                    ps = restore(address, disk);
                } 
                else {
                    LOG.log(Level.INFO, "Not in replay mode...");
                }
                
                int readSize = 1024*64;
                byte[] buf = new byte[readSize];
                ByteBuffer buffer = ByteBuffer.wrap(buf);

                int blockSize = reader.readLineAsIntHex();
                
                while (blockSize != 0) {
                    // read blockSize to read the next block 
                    buffer.limit(blockSize);
                    reader.read(buffer, blockSize);
                    buffer.rewind();
                    
                    // don't restore the cache if we're on any tape other 
                    // than the first tape
                    if (_session.optionChosen(Session.REPLAY_BACKUP_OPTION)) { 
                        ps.write(buffer.array());
                    }
                    
                    buffer.clear();
                    reader.readSeparator();
                    blockSize = reader.readLineAsIntHex();
                }
                
                
                reader.readSeparator();
                reader.readSeparator();
             
                if (ps != null) {
                    ps.close();
                    remote.restart(CacheClientInterface.SYSTEM_CACHE, disk);
                }
                
                // don't restore the cache if we're on any tape other 
                // than the first tape
                if (_session.optionChosen(Session.REPLAY_BACKUP_OPTION)) { 
                    restored++;
                }
                
            } catch (EMDException e) {
                LOG.log(Level.SEVERE,"Unable to restore system cache for "  + nodeId + ":" + diskIndex,e);
                uniqueFailures[nodeId-101]=true;
            } catch (IOException e) {
                LOG.log(Level.SEVERE,"Unable to restore system cache for "  + nodeId + ":" + diskIndex,e);
                uniqueFailures[nodeId-101]=true;
            } catch (ManagedServiceException e) {
                LOG.log(Level.SEVERE,"Unable to restore system cache for "  + nodeId + ":" + diskIndex,e);
                uniqueFailures[nodeId-101]=true;
            } 
        }
     
        if (countFailures(uniqueFailures) > SysCache.calcMaxUniqFailures()) 
            throw new SerializationException("Too many failures on system cache restore, " +
                                             countFailures(uniqueFailures) + 
                                             " failures.");
        
        // mark all the entries as not restored...
        if (_session.optionChosen(Session.REPLAY_BACKUP_OPTION)) { 
            try {
                LOG.info("Setting all entries in system cache to not restored.");
                MetadataClient.getInstance().query(CacheClientInterface.SYSTEM_CACHE, 
                                                   SystemCacheConstants.SYSTEM_QUERY_SETNOTRESTORED,
                                                   -1);
            } catch (ArchiveException e) {
                throw new SerializationException("Unable to set all records to not restored state in system cache for ",e);
            }
        }
      
        CallbackObject obj = new CallbackObject(null, CallbackObject.SYS_CACHE_CALLBACK);
        obj.setCacheCount(restored);
        return obj;
    }

    private String RSH_CMD = "/usr/bin/rsh";
    private String TAR_CMD = "/usr/bin/tar";
    private String GZIP_CMD = "/usr/bin/gzip";
    private String GUNZIP_CMD = "/usr/bin/gunzip";
    
    public ReadableByteChannel backup(String address, Disk disk) throws IOException, EMDException {
        String mdpath = ProcessingCenter.getMDPath(disk);
        String cmd = RSH_CMD + " " + address + " " + 
                     TAR_CMD + " cf - "  + mdpath + "/system/*/log.* " +  mdpath + "/system/*/*.bdb | " + GZIP_CMD;
        return Channels.newChannel(Exec.execReadWithInputStream(cmd, null, LOG));
    }
    
    public PrintStream restore(String address, Disk disk) throws IOException, EMDException {
        String cmd = RSH_CMD + " " + address + " " + GUNZIP_CMD + " | " +
                     TAR_CMD + " xf - ";
                                         
        return Exec.execWrite(cmd,LOG);
    }
    
    public long getContentLength() {
        // we don't know how long the system cache block will be but the extended headers
        // will let you know the number of caches that will exist in the stream and 
        // therefore you can still read the stream quite easily
        return UNKOWN_LENGTH;
    }
    
    public int getCacheCount() {
        if (_disks == null) 
            return 0;
        else
            return _disks.length;
    }
    
    public void init(Object obj) throws SerializationException  {
        // get all of the disks in the cluster including disabled and missing
        // disks
        _disks = DiskProxy.getClusterDisks();
    }
}
