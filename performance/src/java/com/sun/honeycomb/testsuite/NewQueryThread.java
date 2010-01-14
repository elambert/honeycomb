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



import java.util.Random;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.QueryResultSet;

public class NewQueryThread extends Thread
{
  private NameValueObjectArchive archive;
  private long runtimeMillis;
  //    private int resultsGroupSize = 1024;
  private int resultsGroupSize = 2000; //to match blockstore
  private long numErrors = 0;
  private Queue oids;

  public final static int SIMPLE = 0;
  public final static int COMPLEX2 = 1;
  public final static int COMPLEX3 = 2;
  public final static int COMPLEX4 = 3;
  public final static int COMPLEX5 = 4;
  public final static int COMPLEX6 = 5;
  public final static int UNIQUE = 6;
  public final static int ALL = 7;
  public final static int EMPTY = 8;

  private int querytype = 0;

  private Random rand = new Random(System.currentTimeMillis());
  
  int user_count = 0;
  String[] users = {"Ana","Bob","Cathy","Donald","Eugene","Frank","George",
		    "Henry","Irine","Joe","Karol"};
  
  int dir_count = 0; // unused, picking randomly instead
  String[] directories = {"alpha","beta","gamma","delta","epsilon","zeta",
			  "eta","theta","lota","kappa","lambda","mu","nu",
			  "xi","omicron","pi","rho","sigma","tau","upsilon",
			  "phi","chi","psi","omega"};

	
  private long startTime;
  
  public NewQueryThread(NameValueObjectArchive archive,
			String querystring,
			long runtimeMillis,
			Queue oids)
  {
    this.archive=archive;
    this.querytype=stringToQueryType(querystring);
    this.runtimeMillis=runtimeMillis;
    this.oids=oids;
  }

  public void run()
  {
    startTime = System.currentTimeMillis();
    
    long totalfirsts = 0;
    long minfirst = Long.MAX_VALUE;
    long maxfirst = Long.MIN_VALUE;
    long totalfirsttime = 0;
    long totalfirstsquares = 0;
    
    long totalfetches = 0;
    long minfetch = Long.MAX_VALUE;
    long maxfetch = Long.MIN_VALUE;
    long totalfetchtime = 0;
    long totalfetchsquares = 0;

    long totalops = 0;

    String query = null;
    query = generateQuery();
    
    while (query != null &&
	   (runtimeMillis < 0 || 
	    runtimeMillis > (System.currentTimeMillis() - startTime))) {
      try {
	long t0 = System.currentTimeMillis();
	
	QueryResultSet matches = archive.query(query, resultsGroupSize);

	/* Keep running totals/min/max for first op latency */
	long firsttime = System.currentTimeMillis() - t0;
	totalfirsts++;
	totalfirsttime += firsttime;
	totalfirstsquares += firsttime * firsttime;
	if (firsttime < minfirst) {
	  minfirst = firsttime;
	}
	if (firsttime > maxfirst) {
	  maxfirst = firsttime;
	}
	
	t0 = System.currentTimeMillis();

        long batchops = 0;

	while (matches.next()) {
	    batchops++;
	    long t1 = System.currentTimeMillis();
	    
	    long elapsed = t1-t0;
	    
	    /* keep running totals/max/min for what I think are fetches */
	    /* Determine fetches based on if we've gotten the number of
	     results in the resultsGroupSize */
	    if (batchops > resultsGroupSize) {
		if (elapsed < 1) {
		    System.err.println("Not sure this is a fetch");
		}
		else {
		    if (elapsed < minfetch) {
			minfetch = elapsed;
		    }
		    if (elapsed > maxfetch) {
			maxfetch = elapsed;
		    }
		    totalfetches++;
		    totalfetchtime+=elapsed;
		    totalfetchsquares+=elapsed*elapsed;
		    batchops =1;
		}
	    }
		
		/*	  if (elapsed > 5) {
		  if (elapsed < minfetch) {
		  minfetch = elapsed;
		  }
		  if (elapsed > maxfetch) {
		  maxfetch = elapsed;
		  }
		  totalfetches++;
		  totalfetchtime+=elapsed;
		  totalfetchsquares+=elapsed*elapsed;
		  }*/
		
	    totalops++;
	    t0 = System.currentTimeMillis();
	}
	
	if (querytype == EMPTY) {
	  totalops++;
	}
	
	query = generateQuery();
	
      } catch (Throwable throwable) {
	System.err.println("An unexpected error has occured; total errors " + ++numErrors);
	throwable.printStackTrace();
      }
    }

    /* if the loop was broken because time is up, it is possible that 
       the push queue may be in a wait() state, so do a pop if there's 
       anything to pop to get it out of the wait() state */
    if (querytype == UNIQUE) {
      if ((query != null) && !oids.isEmpty()) {
	oids.pop();
      }
    }

    long elapsed = System.currentTimeMillis() - startTime;
    double aveops = totalops/(elapsed/1000.0);

    long threadID = Thread.currentThread().getId();
    
    System.out.println("\n[" + threadID + "] Query type: " + queryTypeToString());
    System.out.println("[" + threadID + "] Average results/sec: " + aveops);
    System.out.println("[" + threadID + "] Total results: " + totalops + ", Total time: " + elapsed + "ms");

    /* Calculate stats for first op */
    double avefirsts = totalfirsttime/totalfirsts;

    System.out.println("[" + threadID + "] First query call latency (average): " + avefirsts + " ms");
    System.out.println("[" + threadID + "] Total first query calls: " + totalfirsts + ", Total time: " + totalfirsttime + "ms");
    System.out.println("[" + threadID + "] Max first call: " + maxfirst + "ms, Min first call: " 
+ minfirst + "ms.");

    if (totalfirsts > 1) {
      double stddevfirsts = 
	java.lang.Math.sqrt((totalfirstsquares - 
			     ((totalfirsttime * totalfirsttime)/
			      totalfirsts))/(totalfirsts - 1));
      System.out.println("[" + threadID + "] Standard Deviation first call: " + stddevfirsts);
    }

    if (querytype != EMPTY && totalfetches > 0) {
      /* Calculate stats for subsequent fetches */
      double avefetches = totalfetchtime/totalfetches; 
      System.out.println("[" + threadID + "] Latency for subsequent fetch (average) " + avefetches + " ms");
      System.out.println("[" + threadID + "] Max fetch latency: " + maxfetch + "ms, Min fetch latency: " +
			 minfetch + "ms");
      System.out.println("[" + threadID + "] Total fetch calls: " + totalfetches + ", Total fetch time: " + totalfetchtime + "ms");

      if (totalfetches > 1) {
	double stddevfetches = 
	  java.lang.Math.sqrt((totalfetchsquares -
			       ((totalfetchtime * totalfetchtime)/
				totalfetches))/(totalfetches - 1));
	
	System.out.println("[" + threadID + "] Standard Deviation fetch: " + stddevfetches);
      }
    }
    
  }
  
  public String generateQuery() {
    if (querytype == SIMPLE) {
      return "perf_qa.user = '" + users[user_count++%users.length] + "'";
    }	
    else if (querytype == EMPTY) {
      return "perf_qa.user = 'Impossible Value'";
    }
    else if (querytype == COMPLEX2) {
      return 
	attr("perf_qafirst.first") + "=" + val(getDir()) + 
	" AND " + attr("perf_qafirst.second") + "=" + val(getDir());
    }
    else if (querytype == COMPLEX3) {
      return 
	attr("perf_qafirst.first") + "=" + val(getDir()) + 
	" AND " + attr("perf_qafirst.second") + "=" + val(getDir()) +
	" AND " + attr("perf_qafirst.third") + "=" + val(getDir());
    }
    else if (querytype == COMPLEX4) {
      return
	attr("perf_qafirst.first") + "=" + val(getDir()) + 
	" AND " + attr("perf_qafirst.second") + "=" + val(getDir()) +
	" AND " + attr("perf_qafirst.third") + "=" + val(getDir()) +
	" AND " + attr("perf_qafirst.fourth") + "=" + val(getDir());
    }
    else if (querytype == COMPLEX5) {
      return 
	attr("perf_qafirst.first") + "=" + val(getDir()) + 
	" AND " + attr("perf_qafirst.second") + "=" + val(getDir()) +
	" AND " + attr("perf_qafirst.third") + "=" + val(getDir()) +
	" AND " + attr("perf_qafirst.fourth") + "=" + val(getDir()) +
	" AND " + attr("perf_qafirst.fifth") + "=" + val(getDir());
    }
    else if (querytype == COMPLEX6) {
      return 
	attr("perf_qafirst.first") + "=" + val(getDir()) + 
	" AND " + attr("perf_qafirst.second") + "=" + val(getDir()) +
	" AND " + attr("perf_qafirst.third") + "=" + val(getDir()) +
	" AND " + attr("perf_qafirst.fourth") + "=" + val(getDir()) +
	" AND " + attr("perf_qafirst.fifth") + "=" + val(getDir()) +
	" AND " + attr("perf_qafirst.sixth") + "=" + val(getDir());
    } 
    else if (querytype == UNIQUE) {
      if ((oids != null) && !oids.isEmpty()) {
	InputOutput line = (InputOutput) oids.pop();
	if (line == null) {
	  return null;
	}
	else {
	  return "system.test.type_string = '" + line.uid + "'";
	}
      }
      else {
	return null;
      }
    }
    else if (querytype == ALL) {
      return "system.test.type_string like '%Stress%'";
    }
    else {
      System.err.println("Invalid query type");
    } 
    return null;
  }
  
  private int  getRandom(int max){    	
    return (Math.abs(rand.nextInt()) % max);
  }
  
  public String getDir() {
    return directories[getRandom(directories.length)];
  }
  
  /* Atrribute in query must be in double quotes
   */
  private String attr(String attrName) {
    return "\"" + attrName + "\"";
  }

  /* Value in query must be in single quotes 
   */
  private String val(String value) {
    return "'" + value + "'";
  }

  public String queryTypeToString() {
    if (querytype == SIMPLE) {
      return "Simple";
    }
    else if (querytype == COMPLEX2) {
      return "Complex2";
    }
    else if (querytype == COMPLEX3) {
      return "Complex3";
    }
    else if (querytype == COMPLEX4) {
      return "Complex4";
    }
    else if (querytype == COMPLEX5) {
      return "Complex5";
    }
    else if (querytype == COMPLEX6) {
      return "Complex6";
    }
    else if (querytype == UNIQUE) {
      return "Unique";
    }
    else if (querytype == ALL) {
      return "All";
    }
    else if (querytype == EMPTY) {
      return "Empty";
    }
    else {
      System.err.println("Invalid query type");
      return null;
    }
  }
  
  public int stringToQueryType(String qry) {
    if (qry.equalsIgnoreCase("SIMPLE")) {
      return SIMPLE;
    }
    if (qry.equalsIgnoreCase("COMPLEX2")) {
      return COMPLEX2;
    }
    if (qry.equalsIgnoreCase("COMPLEX3")) {
      return COMPLEX3;
    }
    if (qry.equalsIgnoreCase("COMPLEX4")) {
      return COMPLEX4;
    }
    if (qry.equalsIgnoreCase("COMPLEX5")) {
      return COMPLEX5;
    }
    if (qry.equalsIgnoreCase("COMPLEX6")) {
      return COMPLEX6;
    }
    else if (qry.equalsIgnoreCase("UNIQUE")) {
      return UNIQUE;
    }
    else if (qry.equalsIgnoreCase("ALL")) {
      return ALL;
    }
    else if (qry.equalsIgnoreCase("EMPTY")) {
      return EMPTY;
    }
    else {
      return 0;
    }
  }
}
