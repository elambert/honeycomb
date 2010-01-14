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



import java.net.InetAddress;
import java.util.Random;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.test.util.RandomUtil;

public class StoreThread extends Thread
{
  private NameValueObjectArchive archive;
  private long minSizeBytes;
  private long maxSizeBytes;
  private long runtimeMillis;
  private long numErrors = 0;

  private Random randSize;
  private Random randId;

  private boolean withMD;
  
  public StoreThread(NameValueObjectArchive archive,
		     long minSizeBytes, 
		     long maxSizeBytes,
		     long runtimeMillis,
		     boolean withMD)
  {
    this.archive=archive;
    this.minSizeBytes=minSizeBytes;
    this.maxSizeBytes=maxSizeBytes;
    this.runtimeMillis=runtimeMillis;
    this.withMD = withMD;
  }
  
  public void run()
  {
    long startTime = System.currentTimeMillis();
    long storeCount = 0;
    StoreChannel storeChannel = new StoreChannel();
    
    randSize = new Random(startTime + (long) this.hashCode());
    randId = new Random(startTime + (long) this.hashCode());
            
    while (runtimeMillis < 0 || runtimeMillis > (System.currentTimeMillis() - startTime)) {
      try {
	long sizeBytes = 
	  (maxSizeBytes == minSizeBytes) ?
	  minSizeBytes :
	  (minSizeBytes + (Math.abs(randSize.nextLong()) % (maxSizeBytes - minSizeBytes)));
	storeChannel.reset(sizeBytes);
	long t0, t1;
	
	SystemRecord systemRecord;
	String uid = null;

	if (withMD) {
	  NameValueRecord metadata = archive.createRecord();
	  uid = ManageMetadata.addMetadata(metadata, startTime, storeCount);
	  
	  t0 = System.currentTimeMillis();
	  systemRecord = archive.storeObject(storeChannel, metadata);
	  t1 = System.currentTimeMillis();
	}
	else {
	  t0 = System.currentTimeMillis();
	  systemRecord = archive.storeObject(storeChannel);
	  t1 = System.currentTimeMillis();
	}
	
	InputOutput.printLine(t0,
			      t1,
			      systemRecord.getObjectIdentifier().toString(),
			      uid,
			      sizeBytes,
			      InputOutput.STORE,
			      true);
	storeCount++;
      } catch (Throwable throwable) {
	System.err.println("An unexpected error has occured; total errors " + ++numErrors);
	throwable.printStackTrace();
      }
    }
  }

}
