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

/* TODO:

   - Implement retrieve

   - StatsServer should only have two threads: 1 for connecting and 1 for
     reading stats off of the Sockets: use select (maybe even 1 thread would
     work).

   - Make clients resilient to servers being up or down.  Should happily connect
     and disconnect and reconnect.

   BUGS:

   - why does the stat server start at 2?

error: invalid number of arguments
Exception in thread "main" java.lang.IndexOutOfBoundsException: Index: 0, Size: 0
        at java.util.ArrayList.RangeCheck(ArrayList.java:507)
        at java.util.ArrayList.get(ArrayList.java:324)
        at Perf.getopt(Perf.java:226)
        at Perf.main(Perf.java:93)

*/
  
public class Perf
{
  private static void printUsage()
  {
    System.err.println("NAME");
    System.err.println("       Perf - measure Honeycomb performance");
    System.err.println();
    System.err.println("SYNOPSIS");
    System.err.println("       java Perf store [OPTIONS] <IP | HOST>");
    System.err.println("       java Perf retrieve [OPTIONS] <IP | HOST>");
    System.err.println("       java Perf stats [OPTIONS]");
    System.err.println();
    System.err.println("       CLASSPATH must include apps.jar and honeycomb.jar");
    System.err.println();
    System.err.println("DESCRIPTION");
    System.err.println();
    System.err.println("       When run in 'store' mode, perf will...");
    System.err.println("         + print Object IDs stored to stdout");
    System.err.println("         + print store stats to stderr");
    System.err.println();
    System.err.println("       When run in 'retrieve' mode, perf will...");
    System.err.println("         + read Object IDs to retrieve from stdin");
    System.err.println("         + print Object IDs retrieved to stdout");
    System.err.println("         + print retrieve stats to stderr");
    System.err.println();
    System.err.println("       When run in 'stats' mode, perf will...");
    System.err.println("         +  collect stats from other perf client and print aggregate stats");
    System.err.println("             to stdout.");
    System.err.println();
    System.err.println("OPTIONS");
    System.err.println("       -c, --count <#>");
    System.err.println("              Number of files to store or retrieve - default, -1 (no limit)");
    System.err.println();
    System.err.println("       -s, --size <bytes>");
    System.err.println("              Size of files to be stored - default, 100 MB");
    System.err.println();
    System.err.println("       -S, --server [=[<IP | HOST>:]<PORT>]");
    System.err.println("              Stats server settings.  By default 'store' and 'retrieve' modes");
    System.err.println("              will only print their stats to stdout.  The default behavior");
    System.err.println("              for 'stats' mode is to listen on port 10101.  Because --stats");
    System.err.println("              takes and optional argument, arguments must be joined directly to");
    System.err.println("              the option string with an '=' (eg. -S=12001).");
    System.err.println();
    System.err.println("       -h, --help");
    System.err.println("              Print this message");
    System.err.println();
    System.err.println("EXAMPLES");
    System.err.println("       perf stats");
    System.err.println("       perf store -S -s 10485760 -c 1000 honeycomb > 10MB.in.oid");
    System.err.println("       cat 10MB.in.oid | perf retrieve -S -c 500 honeycomb 2> 10MB.err.oid");
    System.err.println("       perf stats -S=10215");
    System.err.println("       perf store -S clientA:10215");
  }

  private static final int OP_UNKNOWN=0;
  private static final int OP_STORE=1;
  private static final int OP_RETRIEVE=2;
  private static final int OP_STATS=3;
  private static int op = OP_UNKNOWN;

  private static String address = null;
  private static long count = -1;
  private static long size = 1024*1024*100;
  private static boolean statsEnabled = false;
  private static String statsServer = "localhost";
  private static int statsPort = 10101;
  private static boolean help = false;

  private static int exitcode = 0;

  public static void main(String [] argv)
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
      switch (op)
      {
        case OP_STORE:
          (new Perf()).store(address, count, size, statsEnabled, statsServer, statsPort);
          break;
        case OP_RETRIEVE:
          (new Perf()).retrieve(address, count, statsEnabled, statsServer, statsPort);
          break;
        case OP_STATS:
          (new Perf()).stats(statsPort);
          break;
        default:
          assert false : "invalid op: " + Integer.toString(op);
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
    Getopt options = new Getopt(argv, "c:s:S:h");
    // Loop through the entire commandline.  Don't stop looping until we are done, there is an error,
    // or the help option is selected.
    while (!help && (exitcode == 0) && options.hasMore())        
    {
      Getopt.Option option = options.next();
      switch (option.name())
      {
        case 'c':                   
          String countStr = option.value();
          try
          {
            count = Long.parseLong(countStr);
          }
          catch (Throwable t)
          {
            System.err.println("error: invalid count option - " + option.name());
            exitcode = 1;
          }
          break;
        case 's':
          String sizeStr = option.value();
          try
          {
            size = Long.parseLong(sizeStr);
            if (size < 0)
            {
              System.err.println("error: size option must be positive - " + sizeStr);
              exitcode = 1;
            }
          }
          catch (Throwable t)
          {
            System.err.println("error: invalid size option - " + sizeStr);
            exitcode = 1;
          }
          break;
        case 'S':
          statsEnabled = true;
          String stats = option.value();
          if (stats != null)
          {
            if (stats.charAt(0) == '=')
            {
              stats = stats.substring(1); // strip off optional '=' sign
            }
            if (stats.indexOf(':') == -1)
            {
              boolean isPort = true;
              try
              {
                statsPort = Integer.parseInt(stats);
              }
              catch (NumberFormatException nfe)
              {
                isPort = false;
              } 
              if (!isPort)
              {
                statsServer = stats;
              }
            }
            else
            {
              StringTokenizer t = new StringTokenizer(stats, ":");
              int numTokens = t.countTokens();
              if (numTokens > 2)
              {
                System.err.println("error: invalid --server option - " + stats);
                exitcode = 1;
              }
              else
              {
                assert numTokens == 2;
                statsServer = t.nextToken();
                String port = t.nextToken();
                try
                {
                  statsPort = Integer.parseInt(port);
                }
                catch (NumberFormatException nfs)
                {
                  System.err.println("error: invalid --server option, port " + port);
                  exitcode = 1;
                }
              }
            }
          }
          break;
        case 'P':
          String portStr = option.value();
          try
          {
            statsPort = Integer.parseInt(portStr);
          }
          catch (Throwable t)
          {
            System.err.println("error: invalid size option - " + portStr);
            exitcode = 1;
          }
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

      if ((arguments == null) || (arguments.length < 1))
      {
        help = true;
        exitcode = 1;
      }
      else 
      {
        if (arguments[0].toString().equals("stats"))
        {
          op = OP_STATS;
        }
        else if (arguments[0].toString().equals("store"))
        {
          op = OP_STORE;
        }
        else if (arguments[0].toString().equals("retrieve"))
        {
          op = OP_RETRIEVE;
        }

        if (op == OP_UNKNOWN)
        {
          System.err.println("err: unknown operation - " + arguments[0]);
          exitcode = 1;
        }
        else if (op == OP_STORE || op == OP_RETRIEVE)
        {
          if (arguments.length < 2)
          {
            System.err.println("error: invalid number of arguments");
            exitcode = 1;
          }
          else
          {
            address = arguments[1].toString();
          }
        }
        else
        {
          assert op == OP_STATS : op;
        }
      }
    }
  }

  private static final int BLOCKSIZE = 4096;
  private static final byte [] bytes = new byte[4096];
  static
  {
    // initialize blocks to all zeroes
    for (int i = 0; i < bytes.length; i++)
    {
      bytes[i] = 0;
    }
  }

  private Stats stats;
  private long numClients;

  private Perf()
  {
    this.stats = new Stats();
    this.numClients = 0;
  }

  private void store(String address, long count, long size, boolean statsEnabled, String statsServer, int statsPort)
    throws Throwable
  {
    NameValueObjectArchive a = new NameValueObjectArchive(address);
    if (!Util.ping(a))
    {
      System.err.println("error: unable to connect to '" + address + "'");
      System.exit(1);
    }
    else
    {
      ObjectOutputStream s = null;
      if (statsEnabled)
      {
        s = getOutput(statsServer, statsPort);
      }
      StatsWriter w = new StatsWriter(s);
      Thread t1 = new Thread(w);
      Thread t2 = new Thread(new Store(a, count, size));
      Perf.this.numClients++;
      synchronized (this)
      {
        t1.start();
        t2.start();
      }
      t1.join();
      t2.join();
    }
  }

  private void retrieve(String address, long count, boolean statsEnabled, String statsServer, int statsPort)
    throws Throwable
  {
    NameValueObjectArchive a = new NameValueObjectArchive(address);
    if (!Util.ping(a))
    {
      System.err.println("error: unable to connect to '" + address + "'");
      System.exit(1);
    }
    else
    {
      ObjectOutputStream s = null;
      if (statsEnabled)
      {
        s = getOutput(statsServer, statsPort);
      }
      StatsWriter w = new StatsWriter(s);
      Thread t1 = new Thread(w);
      Thread t2 = new Thread(new Retrieve(a, count));
      Perf.this.numClients++;
      synchronized (this)
      {
        t1.start();
        t2.start();
      }
      t1.join();
      t2.join();
    }
  }

  private void stats(int statsPort)
    throws Throwable
  {
    boolean first = true;
    ServerSocket ss = new ServerSocket(statsPort);
    System.err.println("# listening on port " + Integer.toString(statsPort) + "...");
    List threads = new ArrayList();

    Socket s;
    while ((s = ss.accept()) != null)
    {
      if (first)
      {
        first = false;
        Thread t = new Thread(new StatsPrinter());
        t.start();
        threads.add(t);
      }
      ObjectInputStream r = 
        new ObjectInputStream(new BufferedInputStream(s.getInputStream()));
      Thread t = new Thread(new StatsReader(r));
      t.start();
      threads.add(t);
    }

    Iterator i = threads.iterator();
    while (i.hasNext())
    {
      Thread t = (Thread) i.next();
      t.join();
    }
  }

  private ObjectOutputStream getOutput(String address, int port)
  {
    System.err.println("# connecting to " + address + ":" + Integer.toString(port) + "...");
    ObjectOutputStream w = null;
    while (w == null)
    {
      try
      {
        Socket s = null;
        do
        {
          try
          {
            s = new Socket(address, port);
          }
          catch (ConnectException e) 
          {
            System.err.println(e.getMessage());
            System.exit(1);
          }
        }
        while (s == null);
        w = new ObjectOutputStream(new BufferedOutputStream(s.getOutputStream()));
        w.flush();
      }
      catch (Throwable t)
      {
        t.printStackTrace(System.err);
        Util.sleep(this, 5000);
      }
    }
    return w;
  }

  private class Stats
    implements Serializable
  {
    long wb0, wb1, wf0, wf1;
    long rb0, rb1, rf0, rf1;

    private Stats()
    {
      this(0,0,0,0,0,0,0,0);
    }

    private Stats(long wb0, long wb1, long wf0, long wf1,
                  long rb0, long rb1, long rf0, long rf1)
    {
      this.wb0 = wb0;
      this.wb1 = wb1;
      this.wf0 = wf0;
      this.wf1 = wf1;
      this.rb0 = rb0;
      this.rb1 = rb1;
      this.rf0 = rf0;
      this.rf1 = rf1;
    }

    private Stats(Stats s)
    {
      synchronized (s)
      {
        this.wb0 = s.wb0;
        this.wb1 = s.wb1;
        this.wf0 = s.wf0;
        this.wf1 = s.wf1;
        this.rb0 = s.rb0;
        this.rb1 = s.rb1;
        this.rf0 = s.rf0;
        this.rf1 = s.rf1;
      }
    }

    private long dwb()
    {
      Stats snapshot = new Stats(this);
      return snapshot.wb1 - snapshot.wb0;
    }

    private long dwf()
    {
      Stats snapshot = new Stats(this);
      return snapshot.wf1 - snapshot.wf0;
    }

    private long drb()
    {
      Stats snapshot = new Stats(this);
      return snapshot.rb1 - snapshot.rb0;
    }

    private long drf()
    {
      Stats snapshot = new Stats(this);
      return snapshot.rf1 - snapshot.rf0;
    }

    private String getHeader()
    {
      StringBuffer hdr = new StringBuffer();

      hdr.append("#\n");
      hdr.append("# ");
      hdr.append(Util.fit("Seconds", 8));
      hdr.append("   ");
      hdr.append(Util.fit("Store Bytes", 11));
      hdr.append("   ");
      hdr.append(Util.fit("Read Bytes", 11));
      hdr.append("   ");
      hdr.append(Util.fit("Store Bytes/s", 13));
      hdr.append("   ");
      hdr.append(Util.fit("Read Bytes/s", 13));
      hdr.append("   ");
      hdr.append(Util.fit("Store Files", 11));
      hdr.append("   ");
      hdr.append(Util.fit("Read Files", 11));
      hdr.append("   ");
      hdr.append(Util.fit("Store Files/s", 15));
      hdr.append("   ");
      hdr.append(Util.fit("Read Files/s", 15));
      hdr.append("\n#");

      return hdr.toString();
    }

    private String toString(long t, long dt)
    {
      Stats snapshot = new Stats(this); // thread-safe snapshot

      StringBuffer buf = new StringBuffer();

      buf.append("  ");

      String time = Util.fit(Long.toString(t / 1000), 8);
      buf.append(time);
      buf.append("   ");

      buf.append(Util.fit(Long.toString(snapshot.wb1), 11, false));
      buf.append("   ");

      buf.append(Util.fit(Long.toString(snapshot.rb1), 11, false));
      buf.append("   ");

      String [] _wbps = snapshot.bytesPerSecond(snapshot.wb0, snapshot.wb1, dt);
      buf.append(Util.fit(_wbps[0], 5, false));
      buf.append(" ");
      buf.append(Util.fit(_wbps[1] + "/" + _wbps[2], 7)); 
      buf.append("   ");

      String [] _rbps = snapshot.bytesPerSecond(snapshot.rb0, snapshot.rb1, dt);
      buf.append(Util.fit(_rbps[0], 5, false));
      buf.append(" ");
      buf.append(Util.fit(_rbps[1] + "/" + _rbps[2], 7)); 
      buf.append("   ");

      buf.append(Util.fit(Long.toString(snapshot.wf1), 11, false));
      buf.append("   ");

      buf.append(Util.fit(Long.toString(snapshot.rf1), 11, false));
      buf.append("   ");

      String [] _wfps = filesPerSecond(snapshot.wf0, snapshot.wf1, dt);
      buf.append(Util.fit(_wfps[0], 5, false));
      buf.append(" ");
      buf.append(Util.fit(_wfps[1] + "/" + _wfps[2], 9)); 
      buf.append("   ");

      String [] _rfps = filesPerSecond(snapshot.rf0, snapshot.rf1, dt);
      buf.append(Util.fit(_rfps[0], 5, false));
      buf.append(" ");
      buf.append(Util.fit(_rfps[1] + "/" + _rfps[2], 9)); 

      return buf.toString();
    }

    private String [] bytesPerSecond(long b0, long b1, long dt)
    {
      String [] byteMetrics = {"Bytes", "KB", "MB", "GB", "TB", "PB"};
      String [] timeMetrics = {"s", "min", "hour", "day"};
      int [] timeMults = {1, 60, 60, 24};

      long db = b1 - b0;

      double rate = (dt == 0 ? Double.MAX_VALUE : (((double) db) * 1000) / ((double) dt));

      if (rate < 0)
      {
        rate = 0;
      }
  
      int i = 0;
      if (rate > 0)
      {
        while (i < (byteMetrics.length -1 ) && rate >= 1024)
        {
          i++;
          rate /= 1024;
        }
      }
      String byteMetric = byteMetrics[i];
   
      i = 0;
      if (rate > 0)
      {
        while (rate < 1 && i < (timeMetrics.length - 1))
        {
          i++;
          rate *= timeMults[i];
        }
      }
      String timeMetric = timeMetrics[i];

      return new String [] {
        NumberFormat.getInstance().format(rate), 
        byteMetric, 
        timeMetric
      }; 
    }

    private String [] filesPerSecond(long f0, long f1, long dt)
    {
      String fileMetric = "Files";
      String [] timeMetrics = {"s", "min", "hour", "day"};
      int [] timeMults = {1, 60, 60, 24};

      long df = f1 - f0;

      double rate = (dt == 0 ? Double.MAX_VALUE : (((double) df) * 1000) / ((double) dt));

      if (rate < 0)
      {
        rate = 0;
      }

      int i = 0;
      if (rate > 0)
      {
        while (rate < 1 && i < (timeMetrics.length - 1))
        {
          i++;
          rate *= timeMults[i];
        }
      }
      String timeMetric = timeMetrics[i];

      return new String [] {
        NumberFormat.getInstance().format(rate),
        fileMetric,
        timeMetric
      };
    }
  
    private void writeObject(ObjectOutputStream o)
      throws IOException
    {
      o.writeLong(this.wb0);
      o.writeLong(this.wb1);
      o.writeLong(this.wf0);
      o.writeLong(this.wf1);
      o.writeLong(this.rb0);
      o.writeLong(this.rb1);
      o.writeLong(this.rf0);
      o.writeLong(this.rf1);
    }

    private void readObject(ObjectInputStream i)
      throws IOException
    {
      this.wb0 = i.readLong();
      this.wb1 = i.readLong();
      this.wf0 = i.readLong();
      this.wf1 = i.readLong();
      this.rb0 = i.readLong();
      this.rb1 = i.readLong();
      this.rf0 = i.readLong();
      this.rf1 = i.readLong();
    }
  }

  private class StatsWriter
    implements Runnable
  {
    private ObjectOutputStream statStream;

    StatsWriter(ObjectOutputStream statStream)
    {
      this.statStream = statStream;
    }

    public void run()
    {
      try
      {
        this.writeStats();
      }
      catch (Throwable t)
      {
        t.printStackTrace(System.err);
      }
    }


    private void writeStats()
      throws Throwable
    {
      int i = 0;
      int lines = 0;
      long sleepTime = 1000;
      Stats snapshot = null;

      while (Perf.this.numClients > 0)
      {
        Util.sleep(this, sleepTime);
        i++;

        synchronized (Perf.this.stats)
        {
          snapshot = new Stats(Perf.this.stats);
          Perf.this.stats.wb0 = Perf.this.stats.wb1;
          Perf.this.stats.wf0 = Perf.this.stats.wf1;
          Perf.this.stats.rb0 = Perf.this.stats.rb1;
          Perf.this.stats.rf0 = Perf.this.stats.rf1;
        }

        if ((lines % 20) == 0)
        {
          System.err.println(snapshot.getHeader());
        }
        System.err.println(snapshot.toString(i * sleepTime, sleepTime));
        if (this.statStream != null)
        {
          try
          {
            statStream.writeObject(snapshot);
            statStream.flush();
          }
          catch (SocketException st)
          {
            System.err.println("error: stats server connection closed");
            this.statStream = null;
          }
        }

        lines++;
      }
    }
  }

  private class StatsReader
    implements Runnable
  {
    ObjectInputStream r;

    StatsReader(ObjectInputStream r)
    {
      this.r = r;
    }

    public void run()
    {
      try
      {
        Stats s;
        try
        {
          while ((s = ((Stats) r.readObject())) != null)
          {
            synchronized (Perf.this.stats)
            {
              Perf.this.stats.wb1 += s.dwb();
              Perf.this.stats.wf1 += s.dwf();
              Perf.this.stats.rb1 += s.drb();
              Perf.this.stats.rf1 += s.drf();
            }
          }
        }
        catch (EOFException e) {}
      }
      catch (java.lang.Throwable t)
      {
        t.printStackTrace(System.err);
        System.exit(1);
      }
    }
  }

  private class StatsPrinter
    implements Runnable
  {
    public void run()
    {
      try
      {
        int i = 0; 
        int lines = 0;
        long sleepTime = 1000;
        Stats snapshot = null;

        while (true)
        {
          Util.sleep(this, sleepTime);
          i++;

          synchronized (Perf.this.stats)
          {
            snapshot = new Stats(Perf.this.stats);
            Perf.this.stats.wb0 = Perf.this.stats.wb1;
            Perf.this.stats.wf0 = Perf.this.stats.wf1;
            Perf.this.stats.rb0 = Perf.this.stats.rb1;
            Perf.this.stats.rf0 = Perf.this.stats.rf1;
          }

          if ((lines % 20) == 0)
          {
            System.err.println(snapshot.getHeader());
          }
          System.err.println(snapshot.toString(i * sleepTime, sleepTime));

          lines++;
        }
      }
      catch (Throwable t)
      {
        t.printStackTrace(System.err);
        System.exit(1);
      }
    }
  }

  private class Store
    implements Runnable
  {
    private NameValueObjectArchive a;
    private long count;
    private long size;

    private class Channel
      implements ReadableByteChannel
    {
      private boolean open;
      private int index;
      private long length;
      private long numRead;

      public Channel(long length)
      {
        this.open = true;
        this.index = 0;
        this.length = length;
        this.numRead = 0;
      }

      public int read(ByteBuffer dst)
        throws IOException
      {
        int _numRead = -1;
        if (dst.remaining() > 0 && this.numRead < this.length)
        {
          do
          {
            int l = Math.min(Perf.bytes.length, dst.remaining());
            dst.put(Perf.bytes, 0, l);
            _numRead += l;
            this.numRead += l;
            synchronized (Perf.this.stats)
            {
              Perf.this.stats.wb1 += l;
            }
          } 
          while (dst.remaining() > 0 && this.numRead < this.length);
        }
        return _numRead;
      }

      public void close() 
        throws IOException 
      {
        open = false;
      }

      public boolean isOpen()
      {
        return open;
      }
    }

    Store(NameValueObjectArchive a, long count, long size)
      throws Throwable
    {
      this.a = a;
      this.count = count;
      this.size = size;
    }
  
    public void run()
    {
      try 
      {
	  NameValueRecord metadata = a.createRecord();
        metadata.putAll(new HashMap());
        while (this.count < 0 || this.count-- > 0)
        {
          SystemRecord r = a.storeObject(new Channel(this.size), metadata);
          synchronized (Perf.this.stats)
          {
            Perf.this.stats.wf1 += 1;
          }
          System.out.println(r.getObjectIdentifier());
        }
        Perf.this.numClients--;
      }
      catch (java.lang.Throwable t)
      {
        t.printStackTrace(System.err);
        System.exit(1);
      }
    }
  }

  private class Retrieve
    implements Runnable
  {
    private NameValueObjectArchive a;
    private long count;
    private BufferedReader stdin;

    private class Channel
      implements WritableByteChannel
    {
      private boolean open;
      private long bytes;

      public Channel()
      {
        this.open = true;
        this.bytes = 0;
      }

      public int write(ByteBuffer src)
        throws IOException
      {
        int r = src.remaining();
        synchronized (Perf.this.stats)
        {
          Perf.this.stats.rb1 += r;
        }
        this.bytes += r;
        src.position(src.position() + r);
        return r;
      }

      public void close() 
        throws IOException 
      {
        open = false;
      }

      public boolean isOpen()
      {
        return open;
      }
    }

    Retrieve(NameValueObjectArchive a, long count)
      throws Throwable
    {
      this.a = a;
      this.count = count;
      this.stdin = new BufferedReader(new InputStreamReader(System.in));
    }
  
    public void run()
    {
      try 
      {
        String oid = null;
        while ((oid = stdin.readLine()) != null &&
               (this.count < 0 || this.count-- > 0))
        {
          a.retrieveObject(new ObjectIdentifier(oid), new Channel());
          synchronized (Perf.this.stats)
          {
            Perf.this.stats.rf1 += 1;
          }
          System.out.println(oid);
        }
        Perf.this.numClients--;
      }
      catch (java.lang.Throwable t)
      {
        t.printStackTrace(System.err);
        System.exit(1);
      }
    }
  }
}
