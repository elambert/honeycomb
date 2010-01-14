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


import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class Scrape {


  static void test() throws Exception{
    GZIPInputStream zis = new GZIPInputStream(new FileInputStream("/home/ds158322/university.gz"));
    int c = zis.read();
    System.err.println(c);
    while (c != -1){
      System.out.print((char) c);
      c = zis.read();
    }
    System.out.flush();
  }



  static HashMap hours = new HashMap();
  static HashMap inserts = new HashMap();
  static HashMap failures = new HashMap();
  static HashMap primaryKey = new HashMap();
  static int offset = 0;
  static int count = 0;


  static void scrape(String[] files) throws Exception{
    for (int i = 0; i < files.length; i++){
      System.err.println(files[i]);
      if (files[i].length() == 1){
        offset = Integer.parseInt(files[i]);
        continue;
      }
      BufferedReader r = (files[i].toLowerCase().endsWith("gz")) ?
        new BufferedReader (new InputStreamReader (new GZIPInputStream (new FileInputStream(files[i])))) :
        new BufferedReader( new FileReader (files[i]));;
      String line = r.readLine();
      while (line != null){
        hours.put(line.substring(0, 9), hours);
        if (line.indexOf("Primary key constraint violation") != -1)
          note (primaryKey, line);
        else if (line.indexOf("insert") != -1)
          note (inserts, line);
        else if (line.indexOf("left") != -1)
          note (failures, line);

        line = r.readLine();
      }
    }
  }
  //Primary key constraint violation
  // Jun  5 12:16:41 hcb101 java: [ID 702911 local0.warning] 470 WARNING [HADBHook.setMetadata] (904718.1) Failed to insert attribute ofoto.dir6 for oid [01000121f70b6fe52511daa9ee00e081596434000003560200000000] - [insert into T3DCD379C values (x'01000121f70b6fe52511daa9ee00e081596434000003560200000000', '52');] - [HADB-E-11939: Primary key constraint violation]

  static void report() throws Exception{

    int len = hours.size();

    String[] keys = new String[len];
    int hour = 0;

    // Collect date/hour strings
    Iterator iter = hours.keySet().iterator();
    while (iter.hasNext()){
      keys[hour++] = (String) iter.next();
    }

    System.err.println("   Time             Inserts Retries Primary key constraint violations");
    System.err.println("====================================================================");
    Arrays.sort(keys, new Comparator(){ 
        public int compare(Object o1, Object o2){
          String s1 = (String) o1;
          String s2 = (String) o2;
          int rank1 = ((Integer)months.get(s1.substring(0,3))).intValue();
          int rank2 = ((Integer)months.get(s2.substring(0,3))).intValue();
          if (rank1 > rank2)
            return 1;
          else if (rank2 > rank1)
            return -1;
          String t1 = s1.substring(4);
          String t2 = s2.substring(4);
          return s1.compareTo(s2);
        }
        public boolean	equals(Object obj) {return false;}
      });

    for (hour = 0; hour < keys.length; hour++){
      int sumInserts = 0;
      int sumRetries = 0;
      int sumViolations = 0;

      int[] vals = (int[]) inserts.get(keys[hour]);
      int[] vals2 = (int[]) failures.get(keys[hour]);
      int[] vals3 = (int[]) primaryKey.get(keys[hour]);

      System.out.print(keys[hour]);

      for (int i = offset; i < 6; i++){
        if (vals != null)
          sumInserts += vals[i];
        if (vals2 != null)
          sumRetries += vals2[i];
        if (vals3 != null)
          sumViolations += vals3[i];
      }

      // wrap around into next hour if offset != 0
      if (hour + 1 < keys.length){
        vals = (int[]) inserts.get(keys[hour + 1]);
        vals2 = (int[]) failures.get(keys[hour + 1]);
        vals3 = (int[]) primaryKey.get(keys[hour + 1]);
        for (int i = 0; i < offset; i++){
          if (vals != null)
            sumInserts += vals[i];
          if (vals2 != null)
            sumRetries += vals2[i];
          if (vals3 != null)
            sumViolations += vals3[i];
          //System.err.print(vals[i] + " ");
        }
      }
      System.out.println(":00" + pad(sumInserts, 12) +  pad(sumRetries, 8) + pad(sumViolations, 10));
    }
  }

  public static void main (String[] argv) throws Exception{
    if (argv.length == 0){
      System.err.println("Report HADB regeneration statistics in one hour time slices by scraping log files.");
      System.err.println("This tool works with text and gzip (it looks for \".gz\").");
      System.err.println("Offset the hourly reporting by supplying a single digit argument.");
      System.err.println("");
      System.err.println("Usage:");
      System.err.println("  java -classpath . scrape 3 messages messages.0 messages.1 messages.2.gz messages.3.gz ");
      System.err.println("");
      System.err.println("To generate hourly statistics starting from 30 minutes after the hour:");
      System.err.println("  java -classpath . scrape 3 messages messages.0 messages.1 messages.2.gz messages.3.gz ");
    }
    else{
      scrape(argv);
      report();
    }
  }

  private static int[] zeros(){
    int[] z = {0, 0, 0, 0, 0, 0};
    return z;
  }

  private static void note(HashMap map, String line){
    String hour = line.substring(0, 9);
    int sixthHour = Integer.parseInt(line.substring(10, 11));
    if (map.get(hour) == null)
      map.put(hour, zeros());
    ((int[]) map.get(hour))[sixthHour]++;
  }


  private static String pad(int day){
    if (day < 10)
      return " " + day;
    else
      return Integer.toString(day);
  }

  private static String pad(int i, int length){
    return pad(Integer.toString(i), length);
  }

  private static String pad(String s, int length){
    StringBuffer sb = new StringBuffer();
    int n = length - s.length();
    for (int i = 0; i < n; i++)
      sb.append(" ");
    sb.append(s);
    return sb.toString();
  }

  public static String nextHour(String start){
    String a = start.substring(0, 7);
    int hour = Integer.parseInt(start.substring(7, 9));
    if (start.substring(7, 9).equals("23")){
      int day = Integer.parseInt(start.substring(4, 6).trim());
      a = start.substring(0, 4) + pad(day + 1) + " ";
    }
    hour = (hour + 1) % 24;
    return a + pad(hour);
  }

  private static HashMap months = new HashMap();
  static{
    String[] m = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    for (int i = 0; i < 12; i++)
      months.put(m[i], new Integer(i));
  }
}

    /*
Jun  1 22:12:04 hcb116 java: [ID 702911 local0.info] 793 INFO [TaskLogger.log] (160215.1) PopulateExtCache(116:3) Info msg. Last known good cycle is 45% complete. Load is 0.90734965
     */
