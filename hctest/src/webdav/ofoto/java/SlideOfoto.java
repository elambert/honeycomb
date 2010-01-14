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



import java.io.IOException;
import java.util.Vector;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.util.Random;
import java.util.Date;
import java.util.LinkedList;
import java.io.FileOutputStream;
import java.io.PrintStream;


import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.httpclient.*;
import org.apache.webdav.lib.*;
import org.apache.webdav.lib.methods.DepthSupport;


public class SlideOfoto
{
    private static final int nbLevels = 6;
    private static final String rootPath = "/webdav/";
    private static final String ofotoPath = "/webdav/oFotoHashDirs/";
    private static final int batchLength = 2;
    private static final File dir = new File(".");
    private static final int maxRetry = 3;
    private static final int KILO = 1024;
    private static final String file1 = "/tmp/ofotoS5k";
    private static final String file2 = "/tmp/ofotoS15k";
    private static final String file3 = "/tmp/ofotoS50k";
    private static final String file4 = "/tmp/ofotoS900k";

    static private byte [] buf;

    private HttpURL httpURL;
    private String uri = null;
    private String tag = null;
    private boolean all = false;
    private boolean get = false;
    private boolean verbose = false;
    private boolean print = false;
    private int nbThreads = 1;
    private int startDir = 0;
    private int dirsPerThread = 2;
    private int puts = 0;
    private long maxput = 0;
    private long minput = 9999999;
    String worstput = null;
    private long puttime = 0;
    private long t0 = 0;
    private boolean checkPut = false;
    private int puterrors = 0;
    private Getter getter = null;
    private int gets = 0;
    private long maxget = 0;
    private long minget = 9999999;
    private long gettime = 0;
    private int geterrors = 0;
    private LinkedList<String> paths = new LinkedList<String>();

    public SlideOfoto(String [] args) throws Exception {

        parseArguments(args);

        httpURL = uriToHttpURL(uri);

        buf = new byte[KILO];
        Random rand = new Random();
        rand.nextBytes(buf);

        generateFile(file1, 5 * KILO);
        generateFile(file2, 15 * KILO);
        generateFile(file3, 50 * KILO);
        generateFile(file4, 900 * KILO);

        runTest();
    }
    
    private void generateFile(String name, int size) throws Exception {
        int nbIterations = size / KILO;
        FileOutputStream stream = new FileOutputStream(name);
        for (int i = 0; i < nbIterations; i++) {
            stream.write(buf);
        }
    }

    private void parseArguments(String[] args) {

        String newout = null;

        char curOption = 0;

        // parse arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                curOption = args[i].substring(1).charAt(0);
                if (curOption == 'v') {
                    verbose = true;
                } else if (curOption == 'a') {
                    all = true;
                } else if (curOption == 'g') {
                    get = true;
                } else if (curOption == 'p') {
                    print = true;
                } else if (curOption == 'C') {
                    checkPut = true;
                }
                continue;
            }
            switch(curOption) {
            case 'c':
                uri =  args[i];
                break;
            case 't':
                nbThreads = Integer.parseInt(args[i]);
                break;
            case 'T':
                tag = args[i];
                break;
            case 's':
                startDir = Integer.parseInt(args[i]);
                break;
            case 'n':
                dirsPerThread = Integer.parseInt(args[i]);
                break;
            case 'o':
                newout = args[i];
                break;

            default:
                usage();
                break;
            } 
        }
        if (uri == null) {
            usage();
        }
        if (get  &&  all) {
            System.err.println("get & all are incompatible");
            usage();
        }
        if (newout != null) {
            try {
                System.setOut(new PrintStream(new File(newout)));
            } catch (Exception e) {
                System.err.println(newout + ": " + e);
                System.exit(1);
            }
        }
        println("Start ofoto test uri = " + uri +
                           " with nbThreads = " + nbThreads +
                           ", startDir = " + startDir +
                           ", dirsPerThread = " + dirsPerThread +
                           ", tag = " + tag +
                           ", all = " + all +
                           ", get = " + get +
                           ", verbose = " + verbose);
    }
    private static void usage() {
        println("Usage: SlideOfoto  [-agC] [-o outfile]" +
                           " -c http://hostname[:port][/path]" +
                           " -T tag" +
                           " -t nbThreads" +
                           " -s startDir" +
                           " -n dirsPerThread");
        println("\t-a:\tall: put(s) + 1 get thread");
        println("\t-g:\tget only");
        println("\tdefault:\tput only");
        println("\t-C:\tcheck existence b4 each put");
        System.exit(1);
    }


    static public void main(String[] args) throws Exception {
        SlideOfoto demo = new SlideOfoto(args);
    }

    private static void println(String s) {
        System.out.println(s);
    }

    void quit(int code) {
        long t2 = System.currentTimeMillis();
        int errors = puterrors;
        if (puts > 0) {
            System.out.println("puts: " + puts);
            System.out.println("avg put: " + (puttime/puts) + " ms");
            System.out.println("min put: " + minput + " ms");
            System.out.println("max put: " + maxput + " ms: " + worstput);
            if (t0 != 0)
                System.out.println("total puts/sec = " +
                               (((float)puts*1000)/(float)(t2-t0)));
            if (puterrors > 0)
                println("put errors: " + puterrors);
            if (getter != null) {
                endGetter();
                getter.sum();
                errors += getter.errors;
                if (getter.errors > 0)
                    println("get errors: " + getter.errors);
            }
        }
        if (gets > 0) {
            println("gets: " + gets);
            println("avg get: " + (gettime/gets) + " ms");
            println("min get: " + minget + " ms");
            println("max get: " + maxget + " ms");
            if (t0 != 0)
                System.out.println("total gets/sec = " +
                               (((float)gets*1000)/(float)(t2-t0)));
            errors += geterrors;
        }
        if (errors > 0)
            println("ERROR");

        System.exit(code);
    }

    String nextPath() {
        synchronized(paths) {
            if (paths.size() == 0) {
                try {
                    paths.wait();
                } catch (Exception ignore) {}
            }
            if (paths.size() == 0)
                return null;
            return (String) paths.removeFirst();
        }
    }
    void putPath(String path) {
        synchronized(paths) {
            paths.add(path);
            paths.notifyAll();
        }
    }
    void endGetter() {
        synchronized(paths) {
            paths.notifyAll();
        }
    }

    public void runTest() throws Exception {
        Putter[] runners = new Putter[nbThreads];
        Getter2[] getters = new Getter2[nbThreads];

        int cur = startDir;
        int batchNb = 0;
        t0 = System.currentTimeMillis();
        println("START THREADS");
        if (get) {
            for (int i = 0; i < nbThreads; i++) {
                getters[i] = new Getter2(cur, cur + dirsPerThread-1);
                getters[i].start();
                cur += dirsPerThread;
            }
        } else {
            for (int i = 0; i < nbThreads; i++) {
                runners[i] = new Putter(cur, cur + dirsPerThread-1);
                runners[i].start();
                cur += dirsPerThread;
            }
            if (all) {
                getter = new Getter();
                getter.start();
            }
        }
        println("started threads");
        if (get) {
            for (int i = 0; i < nbThreads; i++) {
                try {
                    getters[i].join();
                    gets += getters[i].gets;
                    gettime += getters[i].getTime;
                    geterrors += getters[i].errors;
                    if (getters[i].maxget > maxget)
                        maxget = getters[i].maxget;
                    if (getters[i].minget < minget)
                        minget = getters[i].minget;
                } catch (InterruptedException e) {
                    println("interrupted [" + i + "]: " + e);
                }
            }
        } else {
            for (int i = 0; i < nbThreads; i++) {
                try { 
                    runners[i].join();
                    println((new Date()).toString() + " join " + i);
                    puts += runners[i].puts;
                    puterrors += runners[i].errors;
                    puttime += runners[i].putTime;
                    if (runners[i].maxput > maxput) {
                        maxput = runners[i].maxput;
                        worstput = runners[i].worstput;
                    }
                    if (runners[i].minput < minput)
                        minput = runners[i].minput;
                } catch (InterruptedException e) {
                    println("interrupted [" + i + "]: " + e);
                }
            }
            if (all) {
                getter.done();
                endGetter();
                getter.join();
            }
        }
        quit(0);
    }


    private HttpURL uriToHttpURL(String uri) throws URIException {
        if (!uri.startsWith("http")) {
            System.err.println("needs to be in url form: " + uri);
            System.exit(1);
        }
        return uri.startsWith("https") ? new HttpsURL(uri)
            : new HttpURL(uri);
    }

    private class Putter extends Thread {
        
        private int startIdx;
        private int endIdx;
        int puts = 0;
        int gets = 0;
        long maxput = 0;
        long minput = 999999;
        int errors = 0;
        String worstput = null;
        long putTime = 0;
        private WebdavResource webdavResource = null;

        public Putter(int start, int end) throws Exception {
            startIdx = start;
            endIdx = end;
            webdavResource = new WebdavResource(httpURL, rootPath);
        }
        
        public void run() {
            boolean retry = false;
            int nbRetry = 0;

            println("Starting, start = " + startIdx +
                               ", end = " + endIdx);
            PathIterator it = new PathIterator(nbLevels, startIdx, endIdx);
            String curPath = null;
            int count = 1;
            while ((curPath = it.getNextPath()) != null) {
                if (print) {
                    println(curPath);
                    continue;
                }
                putLoop(file1, curPath + "/" + tag + "_ofoto5k");
                putLoop(file2, curPath + "/" + tag + "_ofoto15k");
                putLoop(file3, curPath + "/" + tag + "_ofoto50k");
                putLoop(file4, curPath + "/" + tag + "_ofoto900k");
                count++;
                if (count % 5 == 0)
                    putPath(curPath);
            }
        }

        void putLoop(String file, String path) {

            boolean retry;
            int nbRetry = 0;
            do {
                try {
                    retry = false;
                    put(file, path);
                } catch (Exception ignore) { 
                    println("... RETRY 'put' calls for " + path);
                    nbRetry++;;
                    if (nbRetry >= maxRetry) {
                        errors++;
                        return;
                        //println("... ERROR too many retry, abort...");
                        //System.exit(-1);
                    }
                    retry = true;
                }
            } while (retry == true);
        }

        //
        // Propfind method -- adapted from slide example --
        //   returns the number of direct descendants.
        //
        public int propfind(String path) throws Exception {
            int nbResponses = 0;
            try {
                path = checkUri(path);
                if (verbose) {
                    println("Getting properties '" + path + "': ");
                }

                Enumeration responses =
                    webdavResource.propfindMethod(path, DepthSupport.DEPTH_1);

                while (responses.hasMoreElements()) {
                    if (verbose) {
                        println("response " + nbResponses++);
                    }
                    ResponseEntity response =
                        (ResponseEntity) responses.nextElement();
            
                    Enumeration properties = response.getProperties();
                    while (properties.hasMoreElements()){
                        Property property = (Property)properties.nextElement();
                        if (verbose) {
                            println("   " + property.getName() +
                                               " : " + 
                                               property.getPropertyAsString());
                        }

                    }
                    if (verbose) {
                        println("");
                    }
                }
            } catch (Exception ex) {
                checkException(ex);
            }
            return nbResponses;
        }

        //
        // Get method -- adapted from slide example --
        //   - replace existing donloaded file if any.
        //
        public void get(String path, String filename) throws Exception {
        
            try {
                // The resource on the remote.
                String src = checkUri(path);
                // The file on the local.
                String dest = (filename!= null)
                    ? filename
                    : URIUtil.getName(src.endsWith("/")
                                      ? src.substring(0, src.length() - 1)
                                      : src);

                if (verbose) {
                    println("get " + src + " " + dest);
                }

                File file = new File(dest);
                // Checking the local file.
                if (file.exists()) {
                    println("File " + filename + " exists, replace..");
                }
                if (verbose) {
                    println("Downloading  '" + src + "' to '" + dest + "': ");
                }
                if (webdavResource.getMethod(src, file)) {
                    gets++;
                    if (verbose) {
                        println("succeeded.");
                    }
                } else {
                    println("failed.");
                    println(webdavResource.getStatusMessage());
                    System.exit(-1);
                }
            } catch (Exception ex) {
                checkException(ex);
            }
        }

        /**
         *  Put method -- adapted from slide example --
         *  - skip if checkPut & destination file exists.
         */
        public void put(String filename, String path)  throws Exception {

            try {
                String src  = filename;
                String dest = checkUri(path); 
                              //getRemoteTargetFileName(filename,  path);
            
                if (checkPut) {
                    try {
                        String currentPath = webdavResource.getPath();

                        webdavResource.setPath(dest);
                        if (webdavResource.exists()) {
                            println("file " + dest + " already exist, skip...");
                            return;
                        }
                        webdavResource.setPath(currentPath);
                    } catch (Exception ex) {
                        println("exists() check failed: " + ex);
                        println("error: skipping " + dest);
                        return;
                    } 
                }
                File file = getFileByPath(src);
            
                if (!file.exists()) {
                    println("Warning: File not exists");
                    System.exit(-1);
                }
                if (verbose) {
                        println("Uploading  '" + file.getCanonicalPath() + 
                                         "' to '" + dest + "' ");
                }
                long tt = System.currentTimeMillis();
                if (webdavResource.putMethod(dest, file)) {
                    tt = System.currentTimeMillis() - tt;
                    putTime += tt;
                    puts++;
                    if (tt > maxput) {
                        maxput = tt;
                        worstput = dest;
                    }
                    if (tt < minput)
                        minput = tt;
                    if (verbose) {
                        println("succeeded.");
                    }
                } else {
                    println("error [" + 
                                       webdavResource.getStatusMessage() + 
                                       "] exiting");
                    errors++;
                    //System.exit(-1);
                }
            } catch (Exception ex) {
                checkException(ex);
            }
        }

        private String getRemoteTargetFileName(String filename, String path) {
        
            String srcPathName = null;
            String target = null;

            // get target filename from last portion of filename
            StringTokenizer st = new StringTokenizer(filename, "/\\");
            while (st.hasMoreTokens()) {
                srcPathName = st.nextToken();
            }
            try {
                
                if (path != null) {
                    target = checkUri(path);
                
                    // check is path a collection ?
                    String currentPath = webdavResource.getPath();
                
                    webdavResource.setPath(target);
                
                    if (webdavResource.exists()) {
                        if (webdavResource.isCollection()) {
                            target += "/" + srcPathName;
                        } 
                    } 
                
                    webdavResource.setPath(currentPath);
                
                } else {
                    target = checkUri(rootPath + "/" + srcPathName);
                }
            } catch (Exception ex) {
            }
            return target;
        }


        private String checkUri(String uri) throws IOException {
            if (webdavResource == null) {
                throw new IOException("Not connected yet.");
            }
            if (uri==null) {
                uri=webdavResource.getPath();
            }
            if (!uri.startsWith("/")) {
                uri = rootPath + uri;
            }
            return normalize(uri);
        }

        private String normalize(String path) {
            if (path == null)
                return null;

            String normalized = path;

            // Normalize the slashes and add leading slash if necessary
            if (normalized.indexOf('\\') >= 0)
                normalized = normalized.replace('\\', '/');
            if (!normalized.startsWith("/"))
                normalized = "/" + normalized;

            // Resolve occurrences of "/./" in the normalized path
            while (true) {
                int index = normalized.indexOf("/./");
                if (index < 0)
                    break;
                normalized = normalized.substring(0, index) +
                    normalized.substring(index + 2);
            }

            // Resolve occurrences of "/../" in the normalized path
            while (true) {
                int index = normalized.indexOf("/../");
                if (index < 0)
                    break;
                if (index == 0)
                    return ("/");  // The only left path is the root.
                int index2 = normalized.lastIndexOf('/', index - 1);
                normalized = normalized.substring(0, index2) +
                    normalized.substring(index + 3);
            }

            // Resolve occurrences of "//" in the normalized path
            while (true) {
                int index = normalized.indexOf("//");
                if (index < 0)
                    break;
                normalized = normalized.substring(0, index) +
                    normalized.substring(index + 1);
            }

            // Return the normalized path that we have completed
            return (normalized);
        }

        private void checkException(Exception ex) throws Exception {
            if (ex instanceof HttpException) {
                if (((HttpException) ex).getReasonCode() ==
                    HttpStatus.SC_METHOD_NOT_ALLOWED) {
                    println("Warning: Not WebDAV-enabled?");
                } else if (((HttpException) ex).getReasonCode() ==
                         HttpStatus.SC_UNAUTHORIZED) {
                    println("Warning: Unauthorized");
                } else {
                    println("Warning: " + ex.getMessage());
                }
                throw (ex);
            } else if (ex instanceof IOException) {
                println("Error: " + ex.getMessage());
                throw (ex);
            } else {
                println("Fatal error: " + ex.getMessage());
                ex.printStackTrace(System.out);
                errors++;
                //System.exit(-1);
            }
        }

        private File getFileByPath(String path) {
            if (path != null) {
                // set a new file if '\' or '/' at the begin of path 
                // or ':' at the 2nd position of path exists.
                // if not: take the old parent entry of file and add a 
                // '/' to path.
                return(path.startsWith("/") ||
                       path.startsWith("\\") || 
                       ((path.length() > 1) && (path.charAt(1) == ':'))  ) ?
                    new File(path) :
                    new File(dir, "/"+path);
            } else {
                return dir;    
            }    
        }
    }

    private class Getter extends Thread {
        
        int gets = 0;
        long maxget = 0;
        long minget = 999999;
        String worstget = null;
        long getTime = 0;
        private WebdavResource webdavResource = null;
        boolean done = false;
        int errors = 0;

        public Getter() throws Exception {
            webdavResource = new WebdavResource(httpURL, rootPath);
        }
        public void done() {
            done = true;
        }
        public void sum() {
            println("gets: " + gets);
            if (gets > 0) {
                println("avg get: " + (getTime / gets) + " ms");
                println("min get: " + minget + " ms");
                println("max get: " + maxget + " ms");
                println("ungot: " + paths.size());
            }
        }

        public void run() {
            boolean retry = false;
            int nbRetry = 0;

            println("Starting getter");

            while (!done) {
                String curPath = nextPath();
                if (curPath == null)
                    break;

                // retrieve one file of each group of 4
                getLoop(curPath + "/" + tag + "_ofoto15k");
                getLoop(curPath + "/" + tag + "_ofoto50k");
            }
            println("gets: " + gets);
            if (gets > 0) {
                println("avg get: " + (getTime / gets) + " ms");
                println("min get: " + minget + " ms");
                println("max get: " + maxget + " ms " + worstget);
                println("ungot: " + paths.size());
            }
        }
        void getLoop(String path) {
            boolean retry = false;
            int nbRetry = 0;
            do {
                try {
                    retry = false;
                    get(path, "/tmp/" + Thread.currentThread().getName());
                } catch (Exception ignore) {
                    nbRetry++;;
                    println("... RETRY 'get' call for " + path);
                    if (nbRetry >= maxRetry) {
                        println("... error too many retry, abort...");
                        errors++;
                        //System.exit(-1);
                    }
                    retry = true;
                }
            } while (retry == true);
        }
        //
        // Propfind method -- adapted from slide example --
        //   returns the number of direct descendants.
        //
        public int propfind(String path) throws Exception {
            int nbResponses = 0;
            try {
                path = checkUri(path);
                if (verbose) {
                    println("Getting properties '" + path + "': ");
                }

                Enumeration responses =
                    webdavResource.propfindMethod(path, DepthSupport.DEPTH_1);

                while (responses.hasMoreElements()) {
                    if (verbose) {
                        println("response " + nbResponses++);
                    }
                    ResponseEntity response =
                        (ResponseEntity) responses.nextElement();
            
                    Enumeration properties = response.getProperties();
                    while (properties.hasMoreElements()){
                        Property property = (Property)properties.nextElement();
                        if (verbose) {
                            println("   " + property.getName() + " : " + 
                                               property.getPropertyAsString());
                        }

                    }
                    if (verbose) {
                        println("");
                    }
                }
            } catch (Exception ex) {
                checkException(ex);
            }
            return nbResponses;
        }

        //
        // Get method -- adapted from slide example --
        //   - replace existing donloaded file if any.
        //
        public void get(String path, String filename) throws Exception {
        
            try {
                // The resource on the remote.
                String src = checkUri(path);
                // The file on the local.
                String dest = (filename!= null)
                    ? filename
                    : URIUtil.getName(src.endsWith("/")
                                      ? src.substring(0, src.length() - 1)
                                      : src);

                if (verbose) {
                    println("get " + src + " " + dest);
                }

                File file = new File(dest);
                // Checking the local file.
                //if (file.exists()) {
                    //println("File " + filename +
                                       //" exists, replace..");
                //}
                if (verbose) {
                    println("Downloading  '" + src +
                                     "' to '" + dest + "': ");
                }

                long tt = System.currentTimeMillis();
                if (webdavResource.getMethod(src, file)) {
                    tt = System.currentTimeMillis() - tt;
                    getTime += tt;
                    gets++;
                    if (tt > maxget) {
                        maxget = tt;
                        worstget = src;
                    }
                    if (tt < minget)
                        minget = tt;
                    if (verbose) {
                        println("succeeded.");
                    }
                } else {
                    println("failed: " + path);
                    println(webdavResource.getStatusMessage());
                    errors++;
                    //System.exit(-1);
                }
            } catch (Exception ex) {
                checkException(ex);
            }
        }


        private String checkUri(String uri) throws IOException {
            if (webdavResource == null) {
                throw new IOException("Not connected yet.");
            }
            if (uri==null) {
                uri=webdavResource.getPath();
            }
            if (!uri.startsWith("/")) {
                uri = rootPath + uri;
            }
            return normalize(uri);
        }

        private String normalize(String path) {
            if (path == null)
                return null;

            String normalized = path;

            // Normalize the slashes and add leading slash if necessary
            if (normalized.indexOf('\\') >= 0)
                normalized = normalized.replace('\\', '/');
            if (!normalized.startsWith("/"))
                normalized = "/" + normalized;

            // Resolve occurrences of "/./" in the normalized path
            while (true) {
                int index = normalized.indexOf("/./");
                if (index < 0)
                    break;
                normalized = normalized.substring(0, index) +
                    normalized.substring(index + 2);
            }

            // Resolve occurrences of "/../" in the normalized path
            while (true) {
                int index = normalized.indexOf("/../");
                if (index < 0)
                    break;
                if (index == 0)
                    return ("/");  // The only left path is the root.
                int index2 = normalized.lastIndexOf('/', index - 1);
                normalized = normalized.substring(0, index2) +
                    normalized.substring(index + 3);
            }

            // Resolve occurrences of "//" in the normalized path
            while (true) {
                int index = normalized.indexOf("//");
                if (index < 0)
                    break;
                normalized = normalized.substring(0, index) +
                    normalized.substring(index + 1);
            }

            // Return the normalized path that we have completed
            return (normalized);
        }

        private void checkException(Exception ex) throws Exception {
            if (ex instanceof HttpException) {
                if (((HttpException) ex).getReasonCode() ==
                    HttpStatus.SC_METHOD_NOT_ALLOWED) {
                    println("Warning: Not WebDAV-enabled?");
                } else if (((HttpException) ex).getReasonCode() ==
                         HttpStatus.SC_UNAUTHORIZED) {
                    println("Warning: Unauthorized");
                } else {
                    println("Warning: " + ex.getMessage());
                }
                throw (ex);
            } else if (ex instanceof IOException) {
                println("Error: " + ex.getMessage());
                throw (ex);
            } else {
                println("Fatal error: " + ex.getMessage());
                ex.printStackTrace(System.out);
                errors++;
                //System.exit(-1);
            }
        }

        private File getFileByPath(String path) {
            if (path != null) {
                // set a new file if '\' or '/' at the begin of path 
                // or ':' at the 2nd position of path exists.
                // if not: take the old parent entry of file and add a 
                // '/' to path.
                return(path.startsWith("/") ||
                       path.startsWith("\\") || 
                       ((path.length() > 1) && (path.charAt(1) == ':'))  ) ?
                    new File(path) :
                    new File(dir, "/"+path);
            } else {
                return dir;    
            }    
        }
    }

    private class Getter2 extends Thread {
        
        private int startIdx;
        private int endIdx;
        int gets = 0;
        long maxget = 0;
        String worstget = null;
        long minget = 999999;
        int errors = 0;
        long getTime = 0;
        private WebdavResource webdavResource = null;

        public Getter2(int start, int end) throws Exception {
            startIdx = start;
            endIdx = end;
            webdavResource = new WebdavResource(httpURL, rootPath);
        }
        
        public void run() {

            // println("Starting, start = " + startIdx + ", end = " + endIdx);
            PathIterator it = new PathIterator(nbLevels, startIdx, endIdx);
            String curPath = null;
            int count = 1;
            while ((curPath = it.getNextPath()) != null) {
                if (print) {
                    println(curPath);
                    continue;
                }
                getLoop(curPath + "/" + tag + "_ofoto5k");
                getLoop(curPath + "/" + tag + "_ofoto15k");
                getLoop(curPath + "/" + tag + "_ofoto50k");
                getLoop(curPath + "/" + tag + "_ofoto900k");
                count++;
            }
        }
        void getLoop(String path) {
            boolean retry = false;
            int nbRetry = 0;
            do {
                try {
                    retry = false;
                    get(path, "/tmp/" + Thread.currentThread().getName());
                } catch (Exception ignore) {
                    nbRetry++;;
                    println("... RETRY 'get' call for " + path);
                    if (nbRetry >= maxRetry) {
                        println("... error too many retry, abort...");
                        errors++;
                        //System.exit(-1);
                    }
                    retry = true;
                }
            } while (retry == true);
        }

        //
        // Get method -- adapted from slide example --
        //   - replace existing donloaded file if any.
        //
        public void get(String path, String filename) throws Exception {
        
            try {
                // The resource on the remote.
                String src = checkUri(path);
                // The file on the local.
                String dest = (filename!= null)
                    ? filename
                    : URIUtil.getName(src.endsWith("/")
                                      ? src.substring(0, src.length() - 1)
                                      : src);

                if (verbose) {
                    println("get " + src + " " + dest);
                }

                File file = new File(dest);
                // Checking the local file.
                //if (file.exists()) {
                    //println("File " + filename +
                                       //" exists, replace..");
                //}
                if (verbose) {
                    println("Downloading  '" + src +
                                     "' to '" + dest + "': ");
                }

                long tt = System.currentTimeMillis();
                if (webdavResource.getMethod(src, file)) {
                    tt = System.currentTimeMillis() - tt;
                    getTime += tt;
                    gets++;
                    if (tt > maxget) {
                        maxget = tt;
                        worstget = src;
                    }
                    if (tt < minget)
                        minget = tt;
                    if (verbose) {
                        println("succeeded.");
                    }
                } else {
                    println("failed: " + path);
                    println(webdavResource.getStatusMessage());
                    errors++;
                    //System.exit(-1);
                }
            } catch (Exception ex) {
                checkException(ex);
            }
        }

        private void checkException(Exception ex) throws Exception {
            if (ex instanceof HttpException) {
                if (((HttpException) ex).getReasonCode() ==
                    HttpStatus.SC_METHOD_NOT_ALLOWED) {
                    println("Warning: Not WebDAV-enabled?");
                } else if (((HttpException) ex).getReasonCode() ==
                         HttpStatus.SC_UNAUTHORIZED) {
                    println("Warning: Unauthorized");
                } else {
                    println("Warning: " + ex.getMessage());
                }
                throw (ex);
            } else if (ex instanceof IOException) {
                println("Error: " + ex.getMessage());
                throw (ex);
            } else {
                println("Fatal error: " + ex.getMessage());
                ex.printStackTrace(System.out);
                errors++;
                //System.exit(-1);
            }
        }
        private String checkUri(String uri) throws IOException {
            if (webdavResource == null) {
                throw new IOException("Not connected yet.");
            }
            if (uri==null) {
                uri=webdavResource.getPath();
            }
            if (!uri.startsWith("/")) {
                uri = rootPath + uri;
            }
            return normalize(uri);
        }

        private String normalize(String path) {
            if (path == null)
                return null;

            String normalized = path;

            // Normalize the slashes and add leading slash if necessary
            if (normalized.indexOf('\\') >= 0)
                normalized = normalized.replace('\\', '/');
            if (!normalized.startsWith("/"))
                normalized = "/" + normalized;

            // Resolve occurrences of "/./" in the normalized path
            while (true) {
                int index = normalized.indexOf("/./");
                if (index < 0)
                    break;
                normalized = normalized.substring(0, index) +
                    normalized.substring(index + 2);
            }

            // Resolve occurrences of "/../" in the normalized path
            while (true) {
                int index = normalized.indexOf("/../");
                if (index < 0)
                    break;
                if (index == 0)
                    return ("/");  // The only left path is the root.
                int index2 = normalized.lastIndexOf('/', index - 1);
                normalized = normalized.substring(0, index2) +
                    normalized.substring(index + 3);
            }

            // Resolve occurrences of "//" in the normalized path
            while (true) {
                int index = normalized.indexOf("//");
                if (index < 0)
                    break;
                normalized = normalized.substring(0, index) +
                    normalized.substring(index + 1);
            }

            // Return the normalized path that we have completed
            return (normalized);
        }
    }

    //
    // Iterator to build the ofoto Path
    //
    private class PathIterator {
        private int nbFields;
        private int fields[];
        private int startIndex;
        private int endIndex;
        private boolean init;

        public PathIterator(int nb, int start, int end) {
            nbFields = nb;
            startIndex = start;
            endIndex = end;
            fields = new int[nbFields];
            reinit();
        }

        public void reinit() {
            for (int i = 0; i < nbFields; i++) {
                fields[i] = startIndex;
            }
            init = true;
        }

        public String getNextPath() {
            if (!init) {
                return null;
            }
            StringBuffer buf = new StringBuffer();
            buf.append(ofotoPath);
            for (int i = 0; i < nbFields; i++) {
                String s = Integer.toHexString(fields[i]);
                if (s.length() == 1)
                    s = "0" + s;
                buf.append("/").append(s);
            }
            for (int i = (nbFields - 1); i >= 0; i--) {
                if (fields[i] >= endIndex) {
                    fields[i] = startIndex;
                    if (i == 0) {
                        init = false;
                    }
                } else {
                    fields[i]++;
                    break;
                }
            }
            return buf.toString();
        }
    }
}
