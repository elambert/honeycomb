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



package com.sun.honeycomb.hctest.cases;

import java.util.*;
import java.io.*;
import java.nio.channels.*;

import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.hctest.cases.interfaces.HCFileSizeCases;

/**
 *  The <code>HCTestRange</code> class tests the Range Retrieve function
 *  utilizing the retrieveObject API, and HCRangeReadableByteChannel and
 *  HCRangeWritableByteChannel classes.
 *
 *  The StoreFile SDK example program was used as a starting point for this test
 */
public class HCTestRange
{
  /**
   * The <code>Commandline</code> class provides a container to store all
   * commandline related information.
   */
    private static class Commandline
    {
        public static String honeycombServerAddress = null;
        public static String metadataFilename = null;
        public static Map metadata = new HashMap();
        public static boolean help = false;
        public static boolean verbose = false;
        public static long loFirstByte = 0;
        public static long hiLastByte = 1073741823L;
        public static long minSize = 1;
    }   // class Commandline
  
    /**
    * Main execution entry point.
    */
    public static void main(String [] argv)
    {         
        int exitCode = 0;
       
        // Don't bother to do anything if we have no commandline
        if (argv.length == 0)
        {
            printUsage();
            System.exit(1);
        }   // if there was no commandline
        
        // Parse the commandline storing the results in the Commandline static class
        exitCode = parseCommandline(argv);

        // If an error occurred or help requested while loading the 
        // command line, exit
        if ((exitCode != 0) || Commandline.help)
        {
            printUsage();
            System.exit(exitCode);
        }   // if there was an error or help was requested

        if (Commandline.hiLastByte < Commandline.loFirstByte)
        {
            System.out.println("Largest value of lastByte must not be less than smallest value of firstByte!\n");
            printUsage();
            System.exit(1);
        }

        // Load metadata file
        if (Commandline.metadataFilename != null)
        {
            exitCode = loadmetadataFile();

            if (exitCode != 0)
            {
                System.exit(exitCode);
            }   // if there was an exitCode returned
        }   // if commandline has a metadata File
  
        try
        {
            long firstByte, lastByte;
            // Create a NameValueObjectArchive as the main entry point into Honeycomb
            NameValueObjectArchive archive = new NameValueObjectArchive(Commandline.honeycombServerAddress);

            // Create a NameValueRecord and put all the metadata entered from the commandline in it
            NameValueRecord newRecord = archive.createRecord();
            newRecord.putAll(Commandline.metadata);

            HCRangeReadableByteChannel myrange =
                new HCRangeReadableByteChannel(Commandline.hiLastByte + 1,
                                               Commandline.verbose);
            SystemRecord srResult = archive.storeObject(myrange, newRecord);
            System.out.println(srResult.toString());

            // Get the OID associated with the file and metadata we just stored
            ObjectIdentifier metaOid = srResult.getObjectIdentifier();
//          ObjectIdentifier dataOid = srResult.getLinkIdentifier();

            // Loop using various "interesting" sizes
            ArrayList sizeList1 = HCFileSizeCases.getSizeList();
            Iterator li1 = sizeList1.iterator();
            firstByte = Commandline.loFirstByte;
            // TO DO negative test case for 0 length
            while (li1.hasNext()) {
                firstByte = (((Long)li1.next()).longValue());
                if (firstByte < Commandline.loFirstByte) {
                    continue;
                }
                if (firstByte > Commandline.hiLastByte) {
                    break;
                }
                ArrayList sizeList2 = HCFileSizeCases.getSizeList();
                Iterator li2 = sizeList2.iterator();
                lastByte = (((Long)li2.next()).longValue()) - 1;
                while (li2.hasNext()) {
                  try {
                    lastByte = (((Long)li2.next()).longValue()) - 1;
                    if ((firstByte > lastByte) ||
                        ((lastByte - firstByte +1) < Commandline.minSize)) {
                        continue;
                    }
                    if (lastByte > Commandline.hiLastByte) {
                        break;
                    }
                    // Retrieve the data and compare with what was stored
                    HCRangeWritableByteChannel myWriteChannel =
                       new HCRangeWritableByteChannel(firstByte, lastByte,
                                                      Commandline.verbose);
                    long ret = archive.retrieveObject(metaOid, myWriteChannel,
                                                      firstByte, lastByte);
                    boolean pass = myWriteChannel.getPass();
                    if (ret != (lastByte - firstByte +1)) {
                        System.out.println("FAIL firstByte = " + firstByte +
                                           "  lastByte = " + lastByte);
                        System.out.println("  retrieveObject return code expected: "
                          + (lastByte - firstByte +1) + "  received:" + ret);
                    } else if (pass == true) {
                        System.out.println("PASS firstByte = " + firstByte +
                                           "  lastByte = " + lastByte);
                    } else {
                        System.out.println("FAIL firstByte = " + firstByte +
                                           "  lastByte = " + lastByte);
                        System.exit(1);
                    } 
                  }
                  catch (ArchiveException ar)
                  {
                      // Store failed due to an error returned from the cluster
                      System.out.println(
                        "An Archive Exception occurred when accessing the Honeycomb archive.");
                      ar.printStackTrace(System.err);
                      System.out.println("FAIL firstByte = " + firstByte +
                                           "  lastByte = " + lastByte);
                      System.exit(1);
                  }
                  catch (IOException io)
                  {
                      // Store failed due to a communications problem
                      System.out.println(
                        "An IO Exception occurred when accessing the Honeycomb archive.");
                      io.printStackTrace(System.err);
                      System.out.println("FAIL firstByte = " + firstByte +
                                           "  lastByte = " + lastByte);
                      System.exit(1);
                  }
                  catch (IllegalArgumentException ila)
                  {
                      System.out.println(
                        "An Illegal Argument Exception occurred.");
                      ila.printStackTrace(System.err);
                      System.out.println("FAIL firstByte = " + firstByte +
                                           "  lastByte = " + lastByte);
                      System.exit(1);
                  }
                }
            }
            // Delete the object
            // Need to uncomment the dataOid line to free up space when using
            // the emulator until CR 6403951 is fixed
//          archive.delete(dataOid);
            archive.delete(metaOid);
        }
        catch (ArchiveException ar)
        {
            // Store failed due to an error returned from the cluster
            System.out.println("An Archive Exception occurred when accessing the Honeycomb archive.");
            ar.printStackTrace(System.err);
            exitCode = 1;
        }
        catch (IOException io)
        {
            // Store failed due to a communications problem
            System.out.println("An IO Exception occurred when accessing the Honeycomb archive.");
            io.printStackTrace(System.err);
            exitCode = 1;
        }
        catch (IllegalArgumentException ila)
        {
            System.out.println("An Illegal Argument Exception occurred.  Please verify that your metadata is valid.");
            ila.printStackTrace(System.err);
            exitCode = 1;
        }

        System.exit(exitCode);
    }   // main

    /**
    * parseCommandline takes a the commandline specified by <code>argv</code> and parses 
    * it storing the results in the Commandline container class.  Returns an <code>int</code> 
    * specifying an error.
    */
    private static int parseCommandline(String [] argv)
    {
        int exitCode = 0;
       
        Getopt options = new Getopt(argv, "m:f:hvF:L:s:");
        // Loop through the entire commandline.  Don't stop looping until we are done, there is an error,
        // or the help option is selected.
        while (!Commandline.help && (exitCode == 0) && options.hasMore()) 
        {
             Getopt.Option option = options.next();
             switch (option.name())
             {
                // Metadata option.  User is defining a name-value pair of metadata in the format <name>=<value>
                case 'm':
                    // Get the name/value pair string
                    String metadataPair = option.value();
                    if (metadataPair.indexOf('=') != -1)
                    {
                        // Break the metadata pair up into its name value components
                        StringTokenizer split = new StringTokenizer(metadataPair,"=");
                        String name = null;
                        String value = null;
                        if (split.hasMoreTokens())
                            name = split.nextToken();
                        if (split.hasMoreTokens())
                            value = split.nextToken();
                        if (name == null || value == null)
                        {
                            // Metadata not in correct format
                            System.err.println("error: invalid metadata spec. " + metadataPair);
                            exitCode = 1;
                        }   // if the name or value is null
                        else
                        {
                            // Add metadata to the metadata map
                            Commandline.metadata.put(name, value);
                        }   // else the name and values is not null
                    }   // if an "=" exists in the metadataPair string
                    break;

                // File metadata option.  User is specifying a file path to a
                // file containing name-value metadata pairs.
                case 'f':
                    Commandline.metadataFilename = option.value();
                    break;

                // smallest value of firstByte to test, default 0
                case 'F':
                    Commandline.loFirstByte =
                                       Long.valueOf(option.value()).longValue();
                    break;

                // Help option
                case 'h':
                    Commandline.help = true;
                    return exitCode;

                // largest value of lastByte to test, default 1G
                case 'L':
                    Commandline.hiLastByte =
                                       Long.valueOf(option.value()).longValue();
                    break;

                // smallest size of range to retrieve, default 1
                case 's':
                    Commandline.minSize =
                                       Long.valueOf(option.value()).longValue();
                    break;

                // Verbose option
                case 'v':
                    Commandline.verbose = true;
                    break;

                default:
                    if (option.noSwitch() == true)
                    {
                        if (Commandline.honeycombServerAddress == null)
                        {
                            Commandline.honeycombServerAddress = option.value();
                            break;
                        }  // if server address not parsed yet
                    }  // if no switch in front of the option
                   
                    // Either we have an invalid switch or an extra parameter
                    Commandline.help = true;
                    exitCode = 1;
                    break; 
            }   // switch
        }   // while processing commandline
    
        if (Commandline.honeycombServerAddress == null)            
        {
            // User did not specify correct number of arguments.  Give them help.
            Commandline.help = true;
            exitCode = 1;
        }  // if not all required parameters were entered        
    
        return exitCode;
    } // parseCommandline
 
    /**
    * Loads a metadata record from a metadata file specified on the commandline.  The metadata file is expected
    * to contain one name/value pair per line.
    * Name value pairs are specified as: <name>=<value>
    * The metadata record is loaded into Commandline's metadata map.
    */  
    private static int loadmetadataFile()
    {
        int exitCode = 0;

        try
        {
            LineNumberReader metadataFile = new LineNumberReader(new FileReader(Commandline.metadataFilename));
            String line = null;
            while (exitCode == 0 && (line = metadataFile.readLine()) != null)
            {
                if (addMetadata(line) == false)
                {
                    System.err.println("error: invalid metadata spec. " + line);
                    exitCode = 1;
                }   // if error adding metadata
            }   // while no error and more file to read
        }  // try
        catch (FileNotFoundException fnfe)
        {
            System.out.println("error: metadata file not found.  " + Commandline.metadataFilename);
            exitCode = 1;
        }   // catch FileNotFoundException
        catch (IOException ioe)
        {
            System.out.println("error: while reading metadata file.  " + Commandline.metadataFilename);
            System.out.println("error: " + ioe.getMessage());
            exitCode = 1;
        }  // catch IOException

        return exitCode;
    }  // loadmetadataFile
  
  
    /**
    * Takes a name value pair specified by the string <code>metadata</code> and adds it to the metadata map.
    * Name value pairs are specified as: <name>=<value>
    * 
    * addMetadata returns a <code>boolean</code> which is true on success and false on failure.
    */
    private static boolean addMetadata(String metadata)
    {
    // Break the metadata pair up into its name value components
    StringTokenizer split = new StringTokenizer(metadata,"=");
    String name = null;
    String value = null;
    if (split.hasMoreTokens())
    {
            name = split.nextToken();
    }    // if hasMoreTokens
    if (split.hasMoreTokens())
    {
            value = split.nextToken();
    }    // if hasMoreTokens

    if (name == null || value == null)
    {
            // Metadata not in correct format.
            return false;
    }    // if name or value null
    else
    {
            Commandline.metadata.put(name, value);
    }    // else name and value not null
  
    return true;
    }    // addMetadata
  
    /**
    * printUsage prints out the command line help to standard output.
    */
    private static void printUsage()
    {
    System.out.println("NAME");
    System.out.println("       HCTestRange - tests the Range Retrieve function");
    System.out.println();
    System.out.println("SYNOPSIS");
    System.out.println("       java HCTestRange [OPTIONS] <IP | HOST>");
    System.out.println();
    System.out.println("DESCRIPTION");
    System.out.println("       Tests the Range Retrieve function utilizing the retrieveObject API,");
    System.out.println("       and HCRangeReadableByteChannel and HCRangeWritableByteChannel classes.");
    System.out.println();
    System.out.println("OPTIONS");
    System.out.println("       -f   FILE");
    System.out.println("              Use metadata from a FILE that contains a list of <name>=<value> lines.");
    System.out.println();
    System.out.println("       -F   NUMBER");
    System.out.println("              smallest value of firstByte to test, default 0");
    System.out.println();
    System.out.println("       -h");
    System.out.println("              print this message");
    System.out.println();
    System.out.println("       -L   NUMBER");
    System.out.println("              largest value of lastByte to test, default 1073741823 (1G - 1)");
    System.out.println();
    System.out.println("       -m   \"<name>=<value>\"");
    System.out.println("              Any number of --metadata options can be specified.  Each option");
    System.out.println("              specifies a single (name,value) pair.");
    System.out.println();
    System.out.println("       -s   NUMBER");
    System.out.println("              smallest size of range to retrieve, default 1");
    System.out.println();
    System.out.println("       -v");
    System.out.println("              Verbose, show additional information.");
    System.out.println();
    System.out.println("EXAMPLES");
    System.out.println("       java HCTestRange honeycomb");
    System.out.println("       java HCTestRange 10.152.0.12");
    }    // printUsage 
}   // Class HCTestRange
