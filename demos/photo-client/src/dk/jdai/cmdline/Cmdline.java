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



package dk.jdai.cmdline;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.io.File;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.FileFilter;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.Channels;

import com.drew.imaging.jpeg.JpegProcessingException;

import com.sun.honeycomb.adapter.UID;
import com.sun.honeycomb.adapter.Repository;
import com.sun.honeycomb.adapter.MetadataRecord;
import com.sun.honeycomb.adapter.ResultSet;
import com.sun.honeycomb.adapter.AdapterException;
import com.sun.honeycomb.adapter.AdapterFactory;

import com.sun.honeycomb.exif.ExifExtract;

public class Cmdline {

    /**********************************************************************
     *
     * Threads
     *
     **********************************************************************/
    
    private static final int NB_THREADS = Integer.parseInt(System.getProperty("nThreads", "1"));

    private static long uid = -1;
    private static long gid = -1;

    private static int count = 0;

    private static class SingleThread
        extends Thread {
        private File file = null;
        private Repository repository;
        private boolean running;

        private SingleThread(Repository repository,
                             int i) {
            super("Store-"+i);
            this.repository = repository;
        }

        private synchronized void newUpload(File nFile) {
            file = nFile;
            notifyAll();
        }

        private synchronized boolean isFree() {
            return(file == null);
        }
	
        private synchronized void waitForCompletion() {
            while (file!=null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
        }

        private synchronized void stopThread() {
            running = false;
            notifyAll();
        }

        public void run() {
	    int count = Cmdline.count++;
            setName("Uploader " + count);
            System.err.println("Started thread " + count);
            running = true;

            while (running) {
                synchronized (this) {
                    while ((file == null) && (running)) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }

                if (running) {
                    Cmdline.uploadSingleFile(repository, file);
		
                    synchronized (this) {
                        file = null;
                        notifyAll();
                    }
                }
            }
	  System.err.println("Exited thread " + count);
        }
    }
    
    private static class ThreadPool {
        private SingleThread[] threads;

        private ThreadPool(Repository repository) {
            threads = new SingleThread[NB_THREADS];
            for (int i=0; i<threads.length; i++) {
                threads[i] = new SingleThread(repository, i);
                threads[i].start();
            }
        }

        private void stop() {
            for (int i=0; i<threads.length; i++) {
                threads[i].stopThread();
            }

            for (int i=0; i<threads.length; i++) {
                try {
                    threads[i].join();
                } catch (InterruptedException e) {
                }
            }
        }

        private void newUpload(File file) {
            int index;
            for (index=0; index<threads.length; index++) {
                if (threads[index].isFree()) {
                    break;
                }
            }
            if (index==threads.length) {
                index = 0;
                threads[index].waitForCompletion();
            }
            threads[index].newUpload(file);
        }
    }

    private static void getUidGid() {
        Map fields = new HashMap();
        String line;

        try {
            Process p = Runtime.getRuntime().exec("/usr/bin/id");
            BufferedReader stdout =
                new BufferedReader(new InputStreamReader(p.getInputStream()));

            if ((line = stdout.readLine()) == null)
                return;

            String[] words = line.split(" ");
            for (int i = 0; i < words.length; i++) {
                String[] pair = words[i].split("=");

                // Sometimes, id does not output fields with =. For
                // example, on MacOSX : uid=00000(actualid)
                // gid=00000(actualid) groups=00000(actualid),
                // 81(appserveradm), 79(appserverusr), 80(admin)
                if (pair.length < 2)
                    continue;

                int pos = pair[1].indexOf('(');
                if (pos >= 0)
                    pair[1] = pair[1].substring(0, pos);
                fields.put(pair[0], pair[1]);
            }

            uid = Long.parseLong((String)fields.get("uid"));
            gid = Long.parseLong((String)fields.get("gid"));

        } catch (IOException e) {
        } catch (NumberFormatException e) {
        }
    }

    /**********************************************************************
     *
     * Upload routines
     *
     **********************************************************************/
        
    static Map extractMetadata(File file) throws IOException{
        try {
            return new ExifExtract().parse(file);
        } catch (JpegProcessingException e) {
            IOException ioe = new IOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        }
    }

    public static void uploadSingleFile(Repository repository,
                                        File file) {
        FileInputStream fis = null;

        if (uid < 0)
            getUidGid();

        try {
            Map md = extractMetadata(file);
            md.put("filesystem.uid", Long.toString(uid));
            md.put("filesystem.gid", Long.toString(gid));
            md.put("filesystem.mimetype", "image/jpeg");

            fis = new FileInputStream(file);
            
            MetadataRecord set = repository.createSet();
            
            set.put("filesystem.uid", Long.toString(uid));
            set.put("filesystem.gid", Long.toString(gid));
            Iterator iter = md.keySet().iterator();
            while (iter.hasNext()){
                String name = (String) iter.next();
                String value = (String) md.get(name);
                System.err.println("Setting " + name + " " + value);
                if (value != null && name != null && !"".equals(value) && !"".equals(name))
                    set.put(name, value);

            }
            System.out.println("Storing image ["+file.getAbsolutePath()+"] in thread " + Thread.currentThread());
            set.write("image/jpeg", fis.getChannel());
            System.out.println("Stored image ["+file.getAbsolutePath()+"] in thread " + Thread.currentThread());

        } catch (IOException e) {
            System.err.println("Failed to store the image "+file.getAbsolutePath()+" ["+
                               e.getMessage()+"]");
        } catch (IllegalArgumentException e) {
            System.err.println("Failed to store the image "+file.getAbsolutePath()+" ["+
                               e.getMessage()+"]");
        } catch (AdapterException e) {
            System.err.println("Failed to store the image "+file.getAbsolutePath()+" ["+
                               e.getMessage()+"]");
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static class JpgFileFilter
        implements FileFilter {
        
        public boolean accept(File pathname) {
            return pathname.isDirectory()
                || pathname.getName().toLowerCase().endsWith(".jpg")
                || pathname.getName().toLowerCase().endsWith(".jpeg");
        }
    }

    private static ThreadPool pool = null;
    
    public static void uploadDirectory(Repository repository,
                                       File parent,
                                       int nbLevels) {
        boolean createdPool = false;

        if (pool == null) {
            pool = new ThreadPool(repository);
            createdPool = true;
        }

        int remainingLevels = (nbLevels == -1) ? -1 : nbLevels-1;
        File[] files = parent.listFiles(new JpgFileFilter());
        for (int i=0; i<files.length; i++) {
            if (files[i].isDirectory()) {
                if (remainingLevels != 0) {
                    uploadDirectory(repository, files[i], remainingLevels);
                }
            } else {
               if (NB_THREADS <= 1)
                  uploadSingleFile(repository, files[i]);
               else
                  pool.newUpload(files[i]);
            }
        }

        if (createdPool) {
            pool.stop();
            pool = null;
        }
    }
    
    /**********************************************************************
     *
     * Cmdline main
     *
     **********************************************************************/

    private static void usage() {
        System.out.println("You must give :\n"+
                           "- the cluster IP\n"+
                           "- a root directory or a file to upload or the \"delete\" keyword");
        System.exit(1);
    }

    public static void main(String[] arg) throws Exception{
        if (arg.length == 1) {
            new ExifExtract().parseAll(new File(arg[0]));
            return;
        }
        String repositoryClass = "com.sun.honeycomb.oa.OA";
        if (arg.length == 3) {
            repositoryClass = arg[2];
        }
        if (arg.length != 2) {
            usage();
        }


        try {
            System.out.println("Connecting to " + arg[0]);
            Repository repository = AdapterFactory.makeAdapter(repositoryClass, arg[0]);
            File file = new File(arg[1]);
            if (!file.exists()) {
            
//                 if ("clear".equals(arg[1]) || "delete".equals(arg[1])){
//                     int count = 0;
//                     QueryResultSet rs = oa.query("photo.Make is not NULL", 1000);
//                     while (rs.next()){
//                         count++;
//                         oa.delete(rs.getObjectIdentifier());
//                         System.out.println("Deleted " + count + " " + rs.getObjectIdentifier());
//                     }
//                     System.exit(0);
//                 }
//                 else {
                    System.out.println("File "+arg[1]+" does not exist");
                    System.exit(1);
//                }
            }
            if (file.isDirectory()) {
                uploadDirectory(repository, file, -1);
            } else {
                uploadSingleFile(repository, file);
            }
            
            System.out.println("Upload complete");

        } catch (AdapterException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

    }
}


/*
    private static void usage() {
        System.out.println("You must give :\n"+
                           "- the cluster IP\n"+
                           "- a root directory or a file to upload");
        System.exit(1);
    }

    public static void main(String[] arg) {
        if (arg.length != 2) {
            usage();
        }

        Repository repository = null;

        try {
            System.out.println("Connecting to " + arg[0]);
            repository = AdapterFactory.makeAdapter("com.sun.honeycomb.xam.XSystemAdapter", arg[0]);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        if ("clear".equals(arg[1])){

        }
        else{
            File file = new File(arg[1]);
            if (!file.exists()) {
                System.out.println("File "+arg[1]+" does not exist");
                System.exit(1);
            }

            if (file.isDirectory()) {
                uploadDirectory(repository, file, -1);
            } else {
                uploadSingleFile(repository, file);
            }

            System.out.println("Ingest complete");
        }
    }

*/
