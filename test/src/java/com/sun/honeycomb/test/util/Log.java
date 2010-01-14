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



package com.sun.honeycomb.test.util;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.ArrayList;


public class Log
{
  public static final int QUIET_LEVEL = 0;
  public static final int ERROR_LEVEL = 1;
  public static final int WARN_LEVEL = 2;
  public static final int INFO_LEVEL = 3;
  public static final int DEBUG_LEVEL = 4;
  public static int DEFAULT_LEVEL = INFO_LEVEL;

  public static int parseLevel(String level)
  {
    int l = -1;
    if (level.matches("[Qq][Uu][Ii][Ee][Tt]"))
    {
      l = QUIET_LEVEL;
    }
    else if (level.matches("[Ee][Rr][Rr][Oo][Rr]"))
    {
      l = ERROR_LEVEL;
    }
    else if (level.matches("[Ww][Aa][Rr][Nn]"))
    {
      l = WARN_LEVEL;
    }
    else if (level.matches("[Ii][Nn][Ff][Oo]"))
    {
      l = INFO_LEVEL;
    }
    else if (level.matches("[Dd][Ee][Bb][Uu][Gg]"))
    {
      l = DEBUG_LEVEL;
    }
    return l;
  }

  public static String toString(int level)
  {
    String levelStr = null;
    switch (level)
    {
      case QUIET_LEVEL:
        levelStr = "quiet";
        break;
      case ERROR_LEVEL:
        levelStr = "error";
        break;
      case WARN_LEVEL:
        levelStr = "warn";
        break;
      case INFO_LEVEL:
        levelStr = "info";
        break;
      case DEBUG_LEVEL:
        levelStr = "debug";
        break;
    }
    return levelStr;
  }

  public static final int DEFAULT_INDENT_LEN = 2; // # spaces per indent

  public static Log global;
  static
  {
    global = new Log();
  }


  public static void ERROR(String s)
  {
    synchronized (global)
    {
      global.error(s);
    }
  }

  public static void WARN(String s)
  {
    synchronized (global)
    {
      global.warn(s);
    }
  }

  public static void INFO(String s)
  {
    synchronized (global)
    {
      global.info(new String[] {s});
    }
  }

  public static void INFO(String[] s)
  {
    synchronized (global)
    {
      global.info(s);
    }
  }

  public static void SPACE()
  {
    synchronized (global)
    {
      global.space();
    }
  }

  public static void STEP(String[] lines)
  {
    synchronized (global)
    {
      global.step(lines);
    }
  }

  public static void STEP(String s)
  {
    synchronized (global)
    {
      global.step(new String[] {s});
    }
  }

  public static void SUM(String[] lines)
  {
    synchronized (global)
    {
      global.summary(lines);
    }
  }

  public static void SUM(String s)
  {
    synchronized (global)
    {
      global.summary(new String[] {s});
    }
  }

  public static void DEBUG(String s)
  {
    synchronized (global)
    {
      global.debug(s);
    }
  }

  public static void STDOUT(String s)
  {
    synchronized (global)
    {
      global.stdout(s);
    }
  }

  public static void LOG(int level, String s)
  {
    synchronized (global)
    {
      global.log(level, s);
    }
  }

  public static void LOG(int level, String pfx, String s)
  {
    synchronized (global)
    {
      global.log(level, pfx, s);
    }
  }

  public static void XML(String s)
  {
    synchronized (global)
    {
      global.xml(s);
    }
  }

  public static void PRINTLN(String s)
  {
    synchronized (global)
    {
      global.println(s);
    }
  }

  public static void PRINTLN(String pfx, String s)
  {
    synchronized (global)
    {
      global.println(pfx, s);
    }
  }

  public static void PRINTLN()
  {
    synchronized (global)
    {
      global.println();
    }
  }

  /** Indenting works, but is not used at this time
   */
  public static void indent()
  {
    synchronized (global)
    {
      global.indentLevel++;
    }
  }

  public static void outdent()
  {
    synchronized (global)
    {
      global.indentLevel--;
    }
  }

    /* External caller can tell us to use local or remote syslog
     * for important messages (currently Log.STEP and Log.SUM go there)
     */
    public static void addSyslogDest(String host) 
    {
        synchronized (global) {
            global.addSysLog(host);
        }
    }

  public static String stackTrace(Throwable t)
  {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    return sw.toString();
  }

    public static String toString(Object o) {
        return (o == null ? "null" : o.toString());
    }

  public static void main(String [] argv)
  {
    Log.INFO("Hello World");
    Log.INFO("Hello\nWorld");
    Log.INFO("Test Unicode: \\u0343=\u0343");
    Log.XML("<xml>");
    Log.indent();
    Log.XML("<hello_world></hello_world>");
    Log.indent();
    Log.XML("</xml>");
  }

  public int level;
  public int indentLevel;
  public int indentLen;
  public int lineNum;
   
    private ArrayList logDest; // default list of PrintWriters used for logging
    private ArrayList sumDest; // where to log summary messages
    // sumDest = logDest + addSyslogDest

    private LogArchive archiver; // access to singleton

    /* keep constructors private to ensure singleton property */

    /** Default: log to both stdout and a hidden uniquely named file
     *  This is to support moving logs to an archive at the end of run.
     */    
    private Log()
    {
        init();
        PrintWriter out;

        // set up logging to stdout
        try {
            out = new PrintWriter(new OutputStreamWriter(System.out, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        addDefaultLog(out);
        
        // set up duplicate logging to a file
        try {
            File logFile = archiver.createTempFile();
            out = new PrintWriter(
                      new OutputStreamWriter(
                          new FileOutputStream(logFile),
                          "UTF-8"));
            addDefaultLog(out);
        } catch (Exception e) {
            System.out.println("ERROR: Failed to set up a temporary log file, will log only to stdout");
        } 
        
        // now both logDest and sumDest contain default set of PrintWriter
        // stdout and temp file. sumDest can be extended by calling addSysLog. 
    }

    /* Use this constructor to log only to stdout: new Log(System.out);
     */
    private Log(OutputStream os)
    {
        init();
        PrintWriter out;
        try {
            out = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        addDefaultLog(out);
    }
    
    /* common initialization, call in any constructor 
     */
    private void init() {
        level = DEFAULT_LEVEL;
        indentLevel = 0;
        indentLen = Log.DEFAULT_INDENT_LEN;
        lineNum = 0;
        logDest = new ArrayList();
        sumDest = new ArrayList();
        archiver = LogArchive.getInstance();
    }

    private void addDefaultLog(PrintWriter out) {
        logDest.add(out);
        addSumLog(out);
    }

    private void addSumLog(PrintWriter out) {
        sumDest.add(out);
    }

    // add log destination for summary messages: local or remote syslog
    private void addSysLog(String host) {
        addSumLog(new PrintWriter(new SyslogWriter(host)));
    }


    /* these wrappers are called from static macros */

  public void error(String s)
  {
    log(ERROR_LEVEL, "ERR: " + pad(), s);
  }

  public void warn(String s)
  {
    log(WARN_LEVEL, "WRN: " + pad(), s);
  }

  public void info(String[] lines)
  {
    for (int i = 0; i < lines.length; i++) {
      log(INFO_LEVEL, "INF: " + pad(), lines[i]);
    }
  }

  public void step(String[] lines)
  {
    for (int i = 0; i < lines.length; i++) {
      log(INFO_LEVEL, "SUM: " + pad(), lines[i], sumDest);
    }
  }

  String ruler = "=======================================================";
  public void summary(String[] lines)
  {
    log(INFO_LEVEL, "SUM: ", ruler); // don't pad the ruler, looks better
    step(lines);
    log(INFO_LEVEL, "SUM: ", ruler);
  }

  public void debug(String s)
  {
    log(DEBUG_LEVEL, "DBG: " + pad(), s);
  }

    // log only to stdout, not to all default PrintWriters
    public void stdout(String s) 
    {
        System.out.println(prefix("INF: " + pad()) + s);
    }

  public void log(int level, String s)
  {
    log(level, "", s);
  }

  public void log(int level, String pfx, String s)
  {
      log(level, pfx, s, logDest); // log to default PrintWriters
  }

    public void log(int level, String pfx, String s, ArrayList dest) 
    {
        if (level <= this.level) {
            println(pfx, s, dest);
        }
    }

  public void xml(String s)
  {
    log(QUIET_LEVEL, "XML: " + pad(), s);
  }

  private String pad()
  {
    int len = indentLevel * indentLen;
    char spaces[] = new char[len];
    for (int i = 0; i < spaces.length; i++) {
        spaces[i] = ' ';
    }
    return new String(spaces);
  }

  public void println(String s)
  {
      // use default PrintWriters to log to
      println(s, logDest);
  }

    /* print given string to all PrintWriters in log destination array
     */
    public void println(String s, ArrayList dest) {
        for (int i = 0; i < dest.size(); i++) {
            PrintWriter out = (PrintWriter)dest.get(i);
            if (s != null) {
                out.println(s);
            } else { // null input string => print empty line
                out.println();
            }
            out.flush();
        }
    }

    public void println(String pfx, String s) {
        println(pfx, s, logDest); // print to default PrintWriters
    }

  public void println(String pfx, String s, ArrayList dest)
  {
    // Avoid format errors if we only have a single string to print
      String line = prefix(pfx) + s.replaceAll("\n", "\n" + prefix(pfx));
      println(line, dest);
  }

  public void println()
  {
      println(prefix(""));
  }

    // print empty line
    public void space()
  {
      space(logDest); // use default PrintWriters
  }

    public void space(ArrayList dest) 
    {
      println(null, dest); // null string = print empty line
    }

  /** prefix the message with date
   *  we are not interested in host name, pid, thread name etc. 
   */
  private String prefix(String sfx) 
  {
      return Util.currentTimeStamp() + sfx;
  }

    //Encode the resulting string in Native2Ascii format
    public static String native2ascii(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 0x7f) {
                sb.append(c);
            } else {
                String numStr = Integer.toHexString((int) c).toUpperCase();
                sb.append("\\u");
                if (numStr.length() < 4) 
                    sb.append("000".substring(0,4-numStr.length()));
                sb.append(numStr);
            }
        }
        return sb.toString();
    }

    public class SyslogWriter extends Writer {
        
        private String host;
        private RunCommand shell;

        public SyslogWriter() { 
            this(null); // local syslog
        }

        public SyslogWriter(String remoteHost) {
            host = remoteHost;
            shell = new RunCommand();
        }

        // must override because extending Writer
        public void close() {}
        public void flush() {}
        public void write(char[] cbuf, int off, int len) {
            String s = Log.native2ascii(
                          new String(cbuf, off, len));
            s = s.trim();
            if (s == null || s.length() == 0) {
                // empty string will cause logger to wait for stdin
                return; // so don't even try
            }
            try { // logger -p info <string>
                shell.syslog(host, "QA " + s);
            } catch (Exception e) {
                Log.ERROR("Failed to log to " + (host == null ? "local" : host) + " syslog: " + e);
            }            
        }
        
    }
}
