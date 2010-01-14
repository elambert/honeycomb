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



package mp3;

import config.Config;
import org.scilla.util.mp3.*;
import org.scilla.util.mp3.id3v2.*;
import java.util.Map;
import java.util.Iterator;
import java.util.HashMap;
import java.io.File;
import gui.MainFrameInterface;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import javax.swing.JOptionPane;
import java.util.LinkedList;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.client.NameValueRecord;

public class Upload 
    implements Runnable {

    private static final int NB_THREADS = 16;
    private static final int NB_RETRIES = 3;

    /**********************************************************************
     *
     * UploadThread class
     *
     **********************************************************************/

    private class UploadThread
        extends Thread {
        private LinkedList tasks;
        private NameValueObjectArchive oa;

        private String namespace = null;
        
        private UploadThread(LinkedList newTasks) {
            super();
            tasks = newTasks;
            oa = null;
        }

        private void setNamespace(String s) {
            namespace = s;
        }
        
        private void addMetadata(Map metadata,
                                 String key,
                                 String value) {

            if (value == null)
                return;

            if (namespace != null && namespace.length() > 0)
                key = namespace + "." + key;
                
            if ( !metadata.containsKey(key)
                 || metadata.get(key)==null ) {
                value = value.replace(',', ' ');
                if (value.length() == 0) {
                    value = "0";
                }
                metadata.put(key, value);
            }
        }
        
        private void putFrame(Map metadata,
                              ID3v2 id3v2,
                              String tag,
                              String type) 
            throws java.io.UnsupportedEncodingException {
            TextFrame frame = (TextFrame)id3v2.getFrame(type);
            if (frame != null) {
                addMetadata(metadata, tag, frame.getText());
            }
        }

        private void readID3v1(Map metadata,
                               ID3v1 tag) {
            addMetadata(metadata, "title", tag.getTitle());
            addMetadata(metadata, "artist", tag.getArtist());
            addMetadata(metadata, "album", tag.getAlbum());
            addMetadata(metadata, "date", tag.getYear());
            addMetadata(metadata, "type", "mp3");
        }

        private void readID3v2(Map metadata,
                               ID3v2 tag) 
            throws java.io.UnsupportedEncodingException {
            putFrame(metadata, tag, "title", "TIT2");
            putFrame(metadata, tag, "artist", "TPE1");
            putFrame(metadata, tag, "album", "TALB");
            putFrame(metadata, tag, "date", "TDAT");
            addMetadata(metadata, "type", "mp3");
        }
    
        private void readVorbisComment(Map metadata,
                                       VorbisComment vc) {
            addMetadata(metadata, "title", vc.get("title"));
            addMetadata(metadata, "artist", vc.get("artist"));
            addMetadata(metadata, "album", vc.get("album"));
            addMetadata(metadata, "date", vc.get("date"));
            addMetadata(metadata, "type", "ogg");
        }

        private Map getMetadata(File file) {
            ID3v1 id3v1 = null;
            ID3v2 id3v2 = null;
            VorbisComment vc = null;
            String mimetype = null;

            HashMap metadata = new HashMap();

            setNamespace("mp3");

            try {
                // If it's not an ogg vorbis file, an exception will
                // be thrown and no values are inserted into metadata
                vc = new VorbisComment(file);
                readVorbisComment(metadata, vc);
                //mimetype = "audio/x-vorbis";
                mimetype = "application/ogg";
            } catch (Exception e) {
            }

            try {
                id3v2 = new ID3v2(file);
                readID3v2(metadata, id3v2);
                mimetype = "audio/x-mp3";
            } catch (Exception e) {
            }
            
            try {
                id3v1 = new ID3v1(file);
                readID3v1(metadata, id3v1);
                mimetype = "audio/x-mp3";
            } catch (IOException e) {
            }

            // Put default values
            addMetadata(metadata, "title", "Unknown");
            addMetadata(metadata, "artist", "Unknown");
            addMetadata(metadata, "album", "Unknown");
            addMetadata(metadata, "date", "0");
            
            // POSIX attributes
            PosixMetadata pm = new PosixMetadata(file);
            setNamespace("filesystem");
            addMetadata(metadata, "uid", pm.getUID());
            addMetadata(metadata, "gid", pm.getGID());
            addMetadata(metadata, "mode", pm.getMode());
            addMetadata(metadata, "mimetype", mimetype);

            return(metadata);
        }
        
        private void singleFile(File file) 
            throws ArchiveException, IOException {

            FileInputStream stream = new FileInputStream(file);

            try {
                Map metadata = getMetadata(file);

                NameValueRecord record = oa.createRecord();
                record.putAll(metadata);
	    
                printMetadata((String)metadata.get("artist"),
                              (String)metadata.get("album"),
                              (String)metadata.get("title"));
	    
                oa.storeObject(stream.getChannel(),
                               record);
		
            } finally {
                stream.close();
            }
        }

        private String toStr(Map m) {
            String s = "";
            for (Iterator i = m.keySet().iterator(); i.hasNext(); ) {
                String key = (String) i.next();
                String value = (String) m.get(key);
                s += " " + key + "=\"" + value + "\"";
            }
            return s.substring(1);
        }
	
        public void run() {

            boolean running = true;
            File currentFile = null;

            try {

            while (running) {
		
                if (oa == null) {
                    try {
                        oa = new NameValueObjectArchive(Config.getClusterIP());
                    } catch (ArchiveException e) {
                        e.printStackTrace();
                        printError("Failed to connect to honeycomb ["+
                                   e.getMessage()+"]");
                        oa = null;
                        running = false;
                    } catch (IOException e) {
                        e.printStackTrace();
                        printError("Failed to connect to honeycomb ["+
                                   e.getMessage()+"]");
                        oa = null;
                        running = false;
                    }
                }

                if (currentFile == null) {
                    synchronized (tasks) {
                        if (tasks.size() == 0) {
                            running = false;
                        } else {
                            currentFile = (File)tasks.remove(0);
                        }
                    }
                }
	    
                if (running) {
		    
                    long startTime = System.currentTimeMillis();
                    long length = currentFile.length();
                    int nbTries = 0;

                    while ((currentFile != null) && (nbTries < NB_RETRIES)) {
                        try {
                            singleFile(currentFile);
                            updateSpeed(length);
                            currentFile = null;
                            madeProgress();
                        } catch (ArchiveException e) {
                            e.printStackTrace();
                            nbTries++;
                        } catch (IOException e) {
                            e.printStackTrace();
                            nbTries++;
                        }
                    }
		    
                    if (nbTries == NB_RETRIES) {
                        printError("Failed to upload ["+
                                   currentFile.getAbsolutePath()+"]");
                        running = false;
                    }
                }
            } } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**********************************************************************
     * 
     * Main class
     *
     **********************************************************************/
    
    private int taskSize;
    private LinkedList tasks;
    private int currentProgress;
    private MainFrameInterface gui;
    private long totalLength;
    private long startTime;

    private static boolean initialized = false;

    public Upload(MainFrameInterface nGui) {
        gui = nGui;
        currentProgress = 0;
        taskSize = 0;
        tasks = null;
        totalLength = 0;
        startTime = 0;

        if (!initialized)
            init();
    }
    
    private synchronized void madeProgress() {
        currentProgress++;
        if (taskSize > 0) {
            gui.setProgress(currentProgress*100/taskSize);
        }
    }

    private synchronized void updateSpeed(long length) {
        totalLength += length;
	
        gui.setSpeed(totalLength / (System.currentTimeMillis()-startTime));
    }

    private synchronized void printError(String msg) { gui.error(msg); }

    private synchronized void printMetadata(String artist,
                                            String album,
                                            String title) {
        gui.setMetadata(artist, album, title);
    }
    
    public void run() {
        taskSize = tasks.size();
	
        gui.setStatus("Starting the upload");
	
        startTime = System.currentTimeMillis();

        Thread[] threads = new Thread[NB_THREADS];
        for (int i=0; i<NB_THREADS; i++) {
            threads[i] = new UploadThread(tasks);
            threads[i].start();
        }
	
        gui.setStatus("All the "+NB_THREADS+" threads have been started");

        for (int i=0; i<NB_THREADS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException ignored) {
                ignored.printStackTrace();
            }
        }

        gui.setStatus("Upload completed");
    }

    public void uploadFiles(LinkedList nTasks) {
        tasks = nTasks;
        new Thread(this).start();
    }

    public void uploadFiles(File[] files) {
        tasks = new LinkedList();
        for (int i=0; i<files.length; i++) {
            tasks.add(files[i]);
        }
        new Thread(this).start();
    }

    // inner classes cannot have static declarations -- bah!
    private static long uid = 99, gid = 99, mode = 0440;
    /** A simple wrapper for POSIX attributes, and interface to stat(2) */
    private class PosixMetadata {
        PosixMetadata(File f) {
            // stat(2) the file and read uid, gid, mode
        }
        public String getUID() { return Long.toString(uid); }
        public String getGID() { return Long.toString(gid); }
        public String getMode() { return Long.toString(mode); }
    }

    private static synchronized void init() {
        if (initialized)
            return;
        initialized = true;

        String line = null;
        try {
            Process p = Runtime.getRuntime().exec("/usr/bin/id");
            BufferedReader stdout =
                new BufferedReader(new InputStreamReader(p.getInputStream()));
            line = stdout.readLine();
        } catch (Exception ignored) {}
        if (line != null) {
            Map fields = new HashMap();
            String[] words = line.split(" ");
            for (int i = 0; i < words.length; i++) {
                String[] pair = words[i].split("=");

                // Sometimes, id does not output fields with =. For example, on MacOSX :
                // uid=00000(actualid) gid=00000(actualid) groups=00000(actualid), 81(appserveradm), 79(appserverusr), 80(admin)
                if (pair.length > 1) {
                    int pos = pair[1].indexOf('(');
                    if (pos >= 0)
                        pair[1] = pair[1].substring(0, pos);
                    fields.put(pair[0], pair[1]);
                }
            }
            try {
                uid = Long.parseLong((String)fields.get("uid"));
            } catch (Exception ignored) {}
            try {
                gid = Long.parseLong((String)fields.get("gid"));
            } catch (Exception ignored) {}
        }
    }

    public static void main(String[] args) {
        init();
        System.out.println("uid = " + uid);
        System.out.println("gid = " + gid);
        System.out.println("mode = " + mode);
    }

}



