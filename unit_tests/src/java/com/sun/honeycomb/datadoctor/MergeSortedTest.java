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



package com.sun.honeycomb.datadoctor;

import java.util.Arrays;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileReader;
import java.io.FileWriter;


/** 
 * Merge the sorted lists contained in the given input files.  (If no
 * input files given we use three hard-coded default lists.)  The
 * result is a sorted list of strings, no duplicates.  An option first
 * argument is the maximum number of newline-separated strings to read
 * from each file. 
 * */
class MergeSortedTest {

    static private void usage(String msg) {
        System.out.println(msg);
        System.out.println("usage: [maxLines] [file1 file2] [file3] ...\n"+
            "each file must contain sorted, newline separated strings");
    }

    // set the output file, otherwise we use the defalt
    static public void setOutputFile(String f) { outputFile = f; };

    static public void main (String[] args) {

        if (args.length == 0) {
            MergeSorted.Result[] merged = MergeSorted.mergeSortedLists(defaultLists);
            System.out.println(arrayToString(merged));
            return;
        }

        if (args.length == 1) {
            usage("need at least 2 files to sort"); return;
        }

        // get optional number of lines to read from each file
        int maxLines = -1;
        int numLists = args.length;
        int a = 0;
        try { 
            maxLines = Integer.parseInt(args[a]);
            numLists--;
            a++;
        } catch (NumberFormatException ignore) {  };

        String[] lists = new String[numLists];
        System.arraycopy(args, a, lists, 0, numLists);
        sortFiles(maxLines, lists);
    }

    static public void sortFiles(String[] lists) {
        sortFiles(-1, lists);
    }

    static public void sortFiles(int maxLines, String[] lists) {

        // open files and read contents
        String[][] givenLists = new String[lists.length][];
        for (int i=0; i < lists.length; i++) {

            // open file
            FileReader fr;
            try { fr = new FileReader(lists[i]);
            } catch (FileNotFoundException e) {
                usage("failed to open file "+lists[i]+":"+e); return;
            }
            StringBuffer sb = new StringBuffer();

            // read 1 character at a time; assume a character-based file
            while (true) {

                int c;
                try { c = fr.read();
                } catch (IOException e) {
                    usage("error reading file "+fr); return; 
                }
                if (c == -1) {
                    break;
                }
                sb.append(String.valueOf((char)c));
            }

            // split into newline-separated strings, then truncate
            // resulting array if it has more strings than we wanted
            String[] allLines = sb.toString().split("\n");
            if (maxLines == -1 || allLines.length <= maxLines) {
                givenLists[i] = allLines;
            } else {
                givenLists[i] = new String[maxLines];
                System.arraycopy(allLines, 0, givenLists[i], 0, maxLines);
            }
        } 

        // print the first few strings in each input file
        String s = "";
        for (int i=0; i < givenLists.length; i++) {
            s += "Input list "+i+":\n";
            for (int j=0; j < givenLists[i].length && j < 5; j++) {
                s += "\t" + givenLists[i][j] + "\n";
            } 
            s += "\t<"+givenLists[i].length+" total lines>\n";
        } 
        System.out.print(s);

        // merge lists, and print elapsed time
        long start = System.currentTimeMillis();
        System.out.print("merging lists...");
        MergeSorted.Result[] merged = MergeSorted.mergeSortedLists(givenLists);
        long finish = System.currentTimeMillis();
        long t = finish-start; 
        System.out.println("done. Elapsed time: "+t+" ms  ("+
               ((t<1000) ? "< 1 second)" : (t/1000)+" seconds)"));

        listToFile(merged, outputFile);
        System.out.println("results saved in "+outputFile);
    }

    /** write the string to given file, separated by newlines */
    static public void listToFile(MergeSorted.Result[] list, String fileName) {

        // write resulting list to output file
        FileWriter fw;
        try { 
            fw = new FileWriter(fileName, false);
            for (int i=0; i < list.length; i++) {
                if (list[i].val == null) {
                    break;
                }
                fw.write(list[i].val, 0, list[i].val.length());
                fw.write("\n", 0, 1);
            } 
            fw.flush();
            fw.close();
        } catch (Exception e) {
            throw new RuntimeException(
                    "cannot write results to "+fileName, e);
        }

    }

    /** print array, stop first "null" value */
    static public String arrayToString(MergeSorted.Result[] a) {
        
        String s = "\n[";
        // assume all nulls are at end of list, don't show them
        for (int i=0; i < a.length && a[i].val != null; i++) {
            s += a[i].val;
            if (i+1 < a.length && a[i+1].val != null) {
                s += " ";
            }
        } 
        s += "]";
        return s;
    }

    // default location for output file
    static private final String OUTPUT_FILE = "/tmp/merged.out";

    // change this via setOutputFile() method
    static private String outputFile = OUTPUT_FILE;

    // the resulting list should have all 26 letters of the alphabet
    static private final String[][] defaultLists = { 
        { "A", "B", "C", "D", "D", "H", "I", "W", "X", "Y" },
        { "A", "B", "J", "L", "M", "M", "N", "O", "P", "R", "S" },
        { "E", "F", "G", "K", "Q", "T", "U", "V", "Z"  }};

}


