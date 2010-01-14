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



package com.sun.honeycomb;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.ObjectExistsException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.protocol.client.ObjectArchive;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Enumeration;

public class FileLoader {

    private static final String SELECT_DIR_SQL = "select path from directory where path=?";
    private static final String INSERT_DIR_SQL = "insert into directory (path) " +
                                                 "values (?)";

    private static final String SELECT_FILE_SQL = "select oid from file where path=?";
    private static final String INSERT_FILE_SQL = "insert into file (path, oid) " +
                                                  "values (?, ?)";
    private static final int MAX_TRIES = 3;
    private static final long UPDATE_INTERVAL = 30000;

    private File root;

    private long totalBytesWritten;
    private long intervalBytesWritten;
    private int totalFilesWritten;
    private int intervalFilesWritten;
    private long totalStartTime;
    private long intervalStartTime;

    private Connection connection;
    private PreparedStatement selectDirStatement;
    private PreparedStatement insertDirStatement;
    private PreparedStatement selectFileStatement;
    private PreparedStatement insertFileStatement;
    private PreparedStatement FileStatement;

    private ObjectArchive archive;

    private boolean verbose = false;

    public FileLoader(final File rootFile,
                      final Connection dbConnection,
                      final ObjectArchive objectArchive) {
        super();

        root = rootFile;
        if (!root.exists()) {
            throw new IllegalArgumentException("the directory or file \"" +
                                               rootFile +
                                               "\" does not exist");
        }

        connection = dbConnection;

        try {
            selectDirStatement = connection.prepareStatement(SELECT_DIR_SQL);
            insertDirStatement = connection.prepareStatement(INSERT_DIR_SQL);
            selectFileStatement = connection.prepareStatement(SELECT_FILE_SQL);
            insertFileStatement = connection.prepareStatement(INSERT_FILE_SQL);
        } catch (SQLException e) {
            throw new RuntimeException("failed to create prepared statement " +
                                       "from SQL: " + e.getMessage());
        }

        archive = objectArchive;
    }

    public void setVerbose(boolean verboseLogging) {
        verbose = verboseLogging;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void load() {
        totalStartTime = System.currentTimeMillis();
        intervalStartTime = totalStartTime;

        load(root);
    }

    private void load(final File fileOrDirectory) {
        boolean isDirectory = fileOrDirectory.isDirectory();
        String path = null;

        logStatisticsIfNecessary();

        try {
            path = fileOrDirectory.getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException("failed to get canonical path for directory " +
                                       fileOrDirectory);
        }

        if (verbose) {
            System.out.println(((isDirectory) ? "" : "    ") +
                               "loading " +
                               path);
        }

        if (isDone(path, isDirectory)) {
            if (verbose) {
                System.out.println(((isDirectory) ? "" : "    ") +
                                   "already done - skipping " +
                                   path);
            }

            return;
        }

        NewObjectIdentifier oid = null;
        if (isDirectory) {
            loadDirectory(fileOrDirectory);
        } else {
            // workaround empty file bug
            if (fileOrDirectory.length() == 0) {
                if (verbose) {
                    System.out.println("        skipping empty file");
                }

                return;
            }

            oid = loadFile(fileOrDirectory);
        }

        markDone(path, oid);
        if (verbose && isDirectory) {
            System.out.println("done with " + path);
        }
    }

    private void loadDirectory(final File directory) {
        File[] contents = directory.listFiles();
        if (contents != null) {
            for (int i = 0; i < contents.length; i++) {
                load(contents[i]);
            }
        }
    }

    private NewObjectIdentifier loadFile(final File file) {
        NewObjectIdentifier oid = null;
        ArchiveException exception = null;

        int tries = 0;
        while (oid == null && tries < MAX_TRIES) {
            FileInputStream fileIn;
            try {
                fileIn = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("file not found: " + file);
            }

            FileChannel channel = fileIn.getChannel();
            try {
                oid = archive.store(channel, file.length());

                if (verbose) {
                    System.out.println("        got oid " + oid);
                }
            } catch (ObjectExistsException e) {
                oid = e.getExistingIdentifier();

                if (verbose) {
                    System.out.println("        object exists - got oid " + oid);
                }
            } catch (ArchiveException e) {
                exception = e;
            } finally {
                try {
                    channel.close();
                    fileIn.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
        }

        if (oid == null) {
            throw new RuntimeException("failed to retrieve OID for file " +
                                       file,
                                       exception);
        }

        intervalBytesWritten += file.length();
        totalBytesWritten += file.length();

        return oid;
    }

    private boolean isDone(final String path, final boolean isDirectory) {
        ResultSet results = null;
        PreparedStatement statement = (isDirectory)
            ? selectDirStatement
            : selectFileStatement;

        try {
            statement.setString(1, path);
            results = statement.executeQuery();

            return results.first();
        } catch (SQLException e) {
            throw new RuntimeException("isDone failed on SQLException: " +
                                       e.getMessage());
        } finally {
            if (results != null) {
                try {
                    results.close();
                } catch (SQLException e) {
                    // do nothing
                }
            }
        }
    }

    private void markDone(final String path, final NewObjectIdentifier oid) {
        PreparedStatement statement = (oid == null)
            ? insertDirStatement
            : insertFileStatement;

        try {
            statement.setString(1, path);
            if (oid != null) {
                statement.setString(2, oid.toString());
            }
            
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("markDone failed on SQLException: " +
                                       e.getMessage());
        }
    }

    private void logStatisticsIfNecessary() {
        long currentTime = System.currentTimeMillis();
        long duration = currentTime - intervalStartTime;

        if (duration > UPDATE_INTERVAL) {
            System.out.println("*** interval done ***");

            float intervalRate = ((float)(intervalBytesWritten / (1024 * 1024))) /
                                 ((float)(duration / 1000));
            System.out.println("    interval - loaded " +
                               intervalBytesWritten +
                               " bytes in " +
                               duration +
                               " ms at " +
                               intervalRate +
                               " MB/s");

            duration = currentTime - totalStartTime;
            float totalRate = ((float)(totalBytesWritten / (1024 * 1024))) /
                              ((float)(duration / 1000));
            System.out.println("    total    - loaded " +
                               totalBytesWritten +
                               " bytes in " +
                               duration +
                               " ms at " +
                               totalRate +
                               " MB/s");

            intervalBytesWritten = 0;

            // reset intervalStartTime to credit for time spent logging
            intervalStartTime = System.currentTimeMillis();

            // increment totalStartTime to credit for time spent logging
            totalStartTime += (intervalStartTime - currentTime);
        }
    }

    public static void main(final String[] args) {
        if (args.length < 4) {
            exitUsage();
        }

        int offset = 0;
        boolean verboseLogging = false;

        if (args[offset].equals("-v")) {
            offset++;
            verboseLogging = true;
        }

        File rootFile = new File(args[offset]);
        offset++;

        try {
            Class.forName(args[offset]);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("JDBC driver not in class path");
        }

        offset++;
        Connection dbConnection;
        try {
            dbConnection = DriverManager.getConnection(args[offset]);
        } catch (SQLException e) {
            throw new IllegalArgumentException("failed to create JDBC connection " +
                                               "with URL \"" +
                                               args[offset] +
                                               "\": " +
                                               e.getMessage());
        }

        offset++;
        String[] hosts = new String[args.length - offset];
        int[] ports = new int[args.length - offset];
        int hostCount = 0;
        int portCount = 0;

        for (int i = offset; i < args.length; i++) {
            try {
                int port = Integer.valueOf(args[i]).intValue();
                ports[portCount++] = port;
            } catch (NumberFormatException e) {
                hosts[hostCount++] = args[i];
            }
        }

        for (int i = 0; i < hostCount; i++) {
            int port = (portCount > 0) ? ports[i] : 8080;
        }

        String[] tmpHosts = new String[hostCount];
        System.arraycopy(hosts, 0, tmpHosts, 0, hostCount);
        hosts = tmpHosts;

        if (portCount > 0) {
            int[] tmpPorts = new int[portCount];
            System.arraycopy(ports, 0, tmpPorts, 0, portCount);
            ports = tmpPorts;
        } else {
            ports = null;
        }

        ObjectArchive oa = new ObjectArchive(hosts, ports);
        oa.setActiveHostTimeout(10000);
        oa.setConnectionTimeout(5000);
        oa.setSocketTimeout(5000);

        FileLoader loader = new FileLoader(rootFile, dbConnection, oa);
        loader.setVerbose(verboseLogging);
        loader.load();
    }

    private static void exitUsage() {
        System.out.println("usage: FileLoader [-v] " +
                           "<path> " +
                           "<JDBC driver class name> " +
                           "<database connection url> " +
                           "<honeycomb server [port]> [...]");
        System.exit(1);
    }
}
