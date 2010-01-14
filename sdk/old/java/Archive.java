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



import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.text.*;

// Import archive-related packages
import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.*;

public class Archive
{

  private static void printUsage()
  {
    System.err.println("NAME");
    System.err.println("       Archive - manage file archives");
    System.err.println();
    System.err.println("SYNOPSIS");
    System.err.println("       java Archive list [OPTIONS] <IP | HOST> [<NAME>]");
    System.err.println("       java Archive create [OPTIONS] <IP | HOST> <NAME> <SOURCE1> [SOURCE2] ...");
    System.err.println("       java Archive extract [OPTIONS] <IP | HOST> <NAME> <DEST>");
    System.err.println("       java Archive delete [OPTIONS] <IP | HOST> <NAME>");
    System.err.println();
    System.err.println("DESCRIPTION");
    System.err.println("       The archive utility allows you to list, create, extract and delete named");
    System.err.println("       file archives.");
    System.err.println();
    System.err.println("       list");
    System.err.println("              Print the set of archives in Honeycomb or print the contents of a");
    System.err.println("              specific archive.");
    System.err.println();
    System.err.println("       create");
    System.err.println("              Create and archive from local files and directories");
    System.err.println();
    System.err.println("       extract");
    System.err.println("              Write archived files to a local directory");
    System.err.println();
    System.err.println("       delete");
    System.err.println("              Delete an archive from Honeycomb");
    System.err.println();
    System.err.println("OPTIONS");
    System.err.println("       -f, --force");
    System.err.println("              If a destination file already exists, remove it and extract the");
    System.err.println("              archived file in its place.  Do not prompt for confirmation when");
    System.err.println("              deleting an archive.");
    System.err.println();
    System.err.println("       -h, --help");
    System.err.println("              Print this message");
    System.err.println();
    System.err.println("EXAMPLES");
    System.err.println("       java Archive list honeycomb");
    System.err.println("       java Archive create honeycomb src.1 ~/src/");
    System.err.println("       java Archive list honeycomb src.1");
    System.err.println("       java Archive extract honeycomb src.1 ~/src/archives/1/");
    System.err.println("       java Archive delete honeycomb src.1");
  }

  private static final int OP_UNKNOWN=0;
  private static final int OP_CREATE=1;
  private static final int OP_EXTRACT=2;
  private static final int OP_LIST=3;
  private static final int OP_DELETE=4;
  private static int op = OP_UNKNOWN;

  private static String address = null;
  private static String archive = null;
  private static List sources = null;
  private static String dest = null;

  private static boolean force = false;
  private static boolean help = false;

  private static int maxResults = 1000; // don't use too much client memory

  private static int exitcode = 0;

  public static void main(java.lang.String [] argv)
  {
    getopt(argv);
    if (help)
    {
      printUsage();
      System.exit(0);
    }
    if (exitcode != 0)
      System.exit(exitcode);

    try
    {
      NameValueObjectArchive a = new NameValueObjectArchive(address);
      if (!Util.ping(a))
      {
        System.err.println("error: unable to connect: " + address);
        exitcode = 1;
      }
      else
      {
        switch (op)
        {
          case OP_CREATE:
            (new Archive()).create(a, archive, sources);
            break;
          case OP_EXTRACT:
            (new Archive()).extract(a, archive, dest);
            break;
          case OP_LIST:
            if (archive != null)
            {
              (new Archive()).listFiles(a, archive);
            }
            break;
          case OP_DELETE:
            (new Archive()).delete(a, archive);
            break;
          default:
            assert false : "invalid op: " + Integer.toString(op);
        }
      }
    }
    catch (Throwable t)
    {
      t.printStackTrace(System.err);
      exitcode = 1;
    }

    System.exit(exitcode);
  }

  private static void getopt(String [] argv)
  {      
    Getopt options = new Getopt(argv, "f:h");
    // Loop through the entire commandline.  Don't stop looping until we are done, there is an error,
    // or the help option is selected.
    while (!help && (exitcode == 0) && options.hasMore())        
    {
      Getopt.Option option = options.next();
      switch (option.name())
      {
         case 'f':
            force = true;
            break;
         case 'h':       
            help = true;
            break;
         default:
            assert false : "unhandled option";
      }
    }

    if (!help && exitcode == 0)
    {
      String[] arguments = options.remaining();

      if ((arguments == null) || (arguments.length < 2))
      {
        System.err.println("error: invalid number of arguments");
        help = true;
        exitcode = 1;
      }
      else
      {
        address = arguments[1];

        if (arguments[0].equals("create"))
        {
          op = OP_CREATE;
        }
        else if (arguments[0].equals("extract"))
        {
          op = OP_EXTRACT;
        }
        else if (arguments[0].equals("list"))
        {
          op = OP_LIST;
        }
        else if (arguments[0].equals("delete"))
        {
          op = OP_DELETE;
        }
        else
        {
          op = OP_UNKNOWN;
          System.err.println("err: unknown operation - " + arguments[0]);
          exitcode = 1;
        }
      }

      if (exitcode == 0)
      {
        if (op == OP_LIST)
        {
          if (arguments.length >= 3)
          {
            archive = arguments[2];
          }
          if (arguments.length > 3)
          {
            System.err.println("warn: extra args supplied for list");
          }
        }
        else if (op == OP_CREATE)
        {
          if (arguments.length < 4)
          {
            System.err.println("error: no source(s) specified");
            exitcode = 1;
          }
          else
          {
            archive = arguments[2];
            sources = new ArrayList();
            for (int i = 3; i < arguments.length ; i++)
            {
              sources.add(arguments[i]);
            }
          }
        }
        else if (op == OP_EXTRACT)
        {
          if (arguments.length < 4)
          {
            System.err.println("error: no destination specified");
            exitcode = 1;
          }
          else
          { 
            archive = arguments[2];
            dest = arguments[3];
            if (!dest.endsWith(File.separator))
            {
              dest += File.separator;
            }
          }
          if (arguments.length > 4)
          {
            System.err.println("warn: extra args supplied for extract");
          }
        }
        else if (op == OP_DELETE)
        {
          if (arguments.length < 3)
          {
            System.err.println("error: no archive specified");
            exitcode = 1;
          }
          else
          {
            archive = arguments[2];
          }
          if (arguments.length > 3)
          {
            System.err.println("warn: extra args supplied for delete");
          }
        }
        else
        {
          assert false;
        }
      }
    }
  }

  private static final String MDATTR_NAME = "archive";
  private static final String MDATTR_USER = "user";
  private static final String MDATTR_VIEW_FILEPATH = "view_filepath";
  private static final String MDATTR_SYSTEM_FILEPATH = "system_filepath";
  private static final String MDATTR_FILENAME = "filename";
  private static final String MDATTR_FILESIZE = "filesize";
  private static final String MDATTR_STOREDATE = "storedate";

  private Archive ()
  {
  }

  private void create(NameValueObjectArchive a, String name, List sources)
  {
    Map metadata = new HashMap();
    metadata.put(MDATTR_NAME, name);
    metadata.put(MDATTR_USER, System.getProperty("user.name"));

    for (int i = 0; i < sources.size(); i++)
    {
      File f = new File(sources.get(i).toString());
      storeSource(a, f, metadata);
    }
  }

  private void extract(NameValueObjectArchive a, String name, String dest)
  {
    try
    {
      String query = archiveQuery(name);
      QueryResultSet rs = a.query(query, maxResults);
      while (rs.next())
      {
        ObjectIdentifier oid = rs.getObjectIdentifier();
        // get the metadata for the oid
        NameValueRecord r = a.retrieveMetadata(oid);
        String filepath = r.getString(MDATTR_SYSTEM_FILEPATH);
        String filename = r.getString(MDATTR_FILENAME);
        StringBuffer fileb = new StringBuffer();
        fileb.append(dest);
        fileb.append(filepath);
        fileb.append(filename);
        String file = fileb.toString();
        boolean extractIt = true;
        File dir = new File(dest + filepath);
        if (!dir.exists() && !dir.mkdirs())
        {
          System.out.println(" ERROR : CANNOT MAKE PARENT DIRECTORY");
          extractIt = false;
        }

        if (extractIt && !force)
        {
          File f = new File(file);
          if (f.exists())
          {
            System.err.print("extract: overwrite '");
            System.err.print(file);
            System.err.print("'? ");
            System.err.flush();

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            String response = in.readLine();
            if (!response.matches("^[Yy]([Ee][Ss])?$"))
            {
              extractIt = false;
            }
          }
        }

        if (extractIt)
        {
          System.out.print(file);
          System.out.flush();
          FileOutputStream fos = new FileOutputStream(file);
          FileChannel fc = fos.getChannel();
          a.retrieveObject(oid, fc);
          fos.close();
          System.out.println();
        }
      }
    }
    catch (Throwable t)
    {
      t.printStackTrace(System.err);
      System.err.println("error: extract: " + t.getMessage());
      exitcode = 1;
    }
  }

  private void delete(NameValueObjectArchive a, String name)
  {
    try
    {
      String query = archiveQuery(name);
      QueryResultSet rs = a.query(query, maxResults);
      while (rs.next())
      {
        ObjectIdentifier oid = rs.getObjectIdentifier();
        // get the metadata for the oid
        NameValueRecord r = a.retrieveMetadata(oid);
        System.out.print(r.getString(MDATTR_SYSTEM_FILEPATH));
        System.out.print(r.getString(MDATTR_FILENAME));
        System.out.flush();

        a.delete(oid);

        System.out.println();
      }
    }
    catch (Throwable t)
    {
      t.printStackTrace(System.err);
      System.err.println("error: delete: " + t.getMessage());
      exitcode = 1;
    }
  }

  private void listFiles(NameValueObjectArchive a, String name)
  {
    try
    {
      StringBuffer query = new StringBuffer();
      query.append("\"");
      query.append(MDATTR_NAME);
      query.append("\"");
      query.append(" = ");
      query.append("'");
      query.append(name);
      query.append("'");

      QueryResultSet rs = a.query(query.toString(), maxResults);
      while (rs.next())
      {
        ObjectIdentifier oid = rs.getObjectIdentifier();
        NameValueRecord r = a.retrieveMetadata(oid);
        System.out.print(r.getString(MDATTR_SYSTEM_FILEPATH));
        System.out.println(r.getString(MDATTR_FILENAME));
      }
    }
    catch (Throwable t)
    {
      t.printStackTrace(System.err);
      System.err.println("error: list files: " + t.getMessage());
      exitcode = 1;
    }
  }

  private void storeSource(NameValueObjectArchive a, File source, Map amd)
  {
    try
    {
      if (source.isFile())
      {
        try
        {
          String viewPath = filePath(source, true);
          String systemPath = filePath(source, false);

          NameValueRecord r = a.createRecord();
          r.put(MDATTR_FILENAME, source.getName());
          r.put(MDATTR_VIEW_FILEPATH, viewPath);
          r.put(MDATTR_SYSTEM_FILEPATH, systemPath);
          r.put(MDATTR_FILESIZE, new Long(source.length()).toString());
          r.put(MDATTR_STOREDATE, Util.nowString());

          FileInputStream fos = new FileInputStream(source.getAbsolutePath());
          FileChannel fc = fos.getChannel();
          SystemRecord sr = a.storeObject(fc, r);

          System.out.print(systemPath);
          System.out.println(source.getName());
        }
        catch (SecurityException se)
        {
          System.err.println("error: " + source.getName() + ": " + se.getMessage());
          exitcode = 1;
        }
        catch (java.io.FileNotFoundException fnfe)
        {
          System.err.println("error: " + source.getName() + ": " + " not found.");
          exitcode = 1;
        }
        catch (com.sun.honeycomb.common.ArchiveException ae)
        {
          System.err.println("error: " + source.getName() + ": " + ae.getMessage());
          exitcode = 1;
        }
        catch (java.io.IOException ioe)
        {
          System.err.println("error: " + source.getName() + ": " + ioe.getMessage());
          exitcode = 1;
        }
        catch (Throwable t)
        {
          System.out.println(t.getClass().toString());
          System.err.println("error: " + source.getName() + ": " + t.getMessage());
          t.printStackTrace(System.out);
          exitcode = 1;
        }
      }
      else if (source.isDirectory())
      {
        try
        {
          java.io.File [] ls = source.listFiles();
          for (int i = 0; i < ls.length; i++)
          {
            storeSource(a, ls[i], amd);
          }
        }
        catch (SecurityException se)
        {
          System.err.println("error: " + source.getName() + ": " + se.getMessage());
          exitcode = 1;
        }
      }
      else
      {
        assert false;
      }
    }
    catch (Throwable t)
    {
      System.err.println("error: " + source.getName() + ": " + t.getMessage());
      exitcode = 1;
    }
  }

  private String filePath(File source, boolean view)
  {
    StringBuffer sb = new StringBuffer();

    if (view)
    {
      sb.append("/");
    }

    File parent = source.getParentFile();
    if (parent != null)
    {
      sb.append(parent.getPath());
      sb.append(File.separator);
    }

    String path = sb.toString();

    if (view)
    {
      path = path.replace(File.separatorChar, ':');
    }

    return path;
  }

  private String archiveQuery(String archive)
  {
    StringBuffer query = new StringBuffer();
    query.append("\"");
    query.append(MDATTR_NAME);
    query.append("\" = '");
    query.append(archive);
    query.append("'");
    return query.toString();
  }
}
