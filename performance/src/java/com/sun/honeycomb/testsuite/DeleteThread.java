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
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.SystemRecord;

public class DeleteThread extends Thread
{
  private NameValueObjectArchive archive;
  private Queue oids;
  private long sleepTime = 0; // msecs
  private long numErrors = 0;
  private long runtimeMillis;
  
    public DeleteThread(NameValueObjectArchive archive,
			long runtimeMillis,
			Queue oids)
    {
        this.archive=archive;
	this.runtimeMillis=runtimeMillis;
        this.oids=oids;
    }

    public void run()
    {
        InputOutput line = null;
	while ((line = (InputOutput) oids.pop()) != null) {
	  
	  try {
		
	    ObjectIdentifier oid = new ObjectIdentifier(line.oid);
	    /* Get uid and filesize from OID file to avoid
	       retrieveMetadata call */
	    String uid = line.uid;
	    long sizeBytes = line.sizeBytes;
	    
	    long t0 = System.currentTimeMillis();
	    archive.delete(oid);
	    long t1 = System.currentTimeMillis();
	    InputOutput.printLine(t0,
				  t1,
				  oid.toString(),
				  uid,
				  sizeBytes,
				  InputOutput.DELETE,
				  true);
	    
	    Thread.sleep(sleepTime);
	  } catch (Throwable throwable) {
	    System.err.println("An unexpected error has occured; total errors " + ++numErrors);
	    throwable.printStackTrace();
	  }
	}
    }
}
