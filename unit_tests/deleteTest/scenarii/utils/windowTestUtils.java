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



// Utils functions used by windowTest.java and overlappingBackups.java unit tests.

OAClient oac = OAClient.getInstance();
chunksize = Constants.MAX_CHUNK_SIZE = 1 * oac.blockSize;
oac.setMaxChunkSize(Constants.MAX_CHUNK_SIZE);

storesize = 1024;
iterations = 5;
numStores = 100;
test = 0;
abc=123;

int[][] mTagMatrix = new int[30][10];
int[][] dTagMatrix = new int[30][10];

int aBase = 1000;
int dBase = 5000;
for (int i=0; i < 30; i++, aBase += 10, dBase += 10) {
   for (int j=0; j < 10; j++) {
      mTagMatrix[i][j] = aBase + j;
      dTagMatrix[i][j] = dBase + j;
   }
}


void verifyMetadata (SystemMetadata sm1, SystemMetadata sm2, boolean shouldPass) {	
	if (isMetadataEqual(sm1, sm2) != shouldPass) {
		ECHO("----------------------");
		ECHO("System Metadata 1:\n" + sm1.toString());
		ECHO("----------------------");
		ECHO("System Metadata 2:\n" + sm2.toString());
		ECHO("----------------------");
		String word = (shouldPass) ? "not " : "";
		throw new RuntimeException ("Stored metadata vs. returned metadata is "+
			word + "equal");
	}
}

boolean isMetadataEqual(SystemMetadata sm1, SystemMetadata sm2) {
	eq = true;
	if (!sm1.equals(sm2)) {
		ECHO("Basic Equals is not equal");
		eq = false;
	}
	if (!sm1.getOID().equals(sm2.getOID())) {
		ECHO("getOID().toHexString() is not equal");
		eq = false;
	}
	if (sm1.getLayoutMapId() != sm2.getLayoutMapId()) {
		ECHO("getLayoutMapId() is not equal");
		eq = false;
	}
	if (sm1.getSize() != sm2.getSize()) {
		ECHO("getSize() is not equal");
		eq = false;
	}
	if (!sm1.getReliability().equals(sm2.getReliability())) {
		ECHO("getReliability() is not equal");
		eq = false;
	}
	if (sm1.getFragDataLen() != sm2.getFragDataLen()) {
		ECHO("getFragDataLen() is not equal");
		eq = false;
	}
	if (!sm1.getLink().equals(sm2.getLink())) {
		ECHO("getLink() is not equal");
		eq = false;
	}
	if (sm1.getCTime() != sm2.getCTime()) {
		ECHO("getCTime() is not equal");
		eq = false;
	}
	if (sm1.getDTime() != sm2.getDTime()) {
		ECHO("getDTime() is not equal");
		eq = false;
	}
	if (sm1.getRTime() != sm2.getRTime()) {
		ECHO("getRTime() is not equal");
		eq = false;
	}
	if (sm1.getETime() != sm2.getETime()) {
		ECHO("getETime() is not equal");
		eq = false;
	}
	if (sm1.getShred() != sm2.getShred()) {
		ECHO("getShred() is not equal");
		eq = false;
	}
	
	if (sm1.getHashAlgorithm() == null || sm2.getHashAlgorithm() == null) {
		ECHO("getHashAlgorithm() is not equal");
		String one = (sm1.getHashAlgorithm() == null) ? "null" : sm1.getHashAlgorithm();
		ECHO(" - Before: " + one);
		String two = (sm2.getHashAlgorithm() == null) ? "null" : sm2.getHashAlgorithm();
		ECHO(" - After:  " + two);
		ECHO("Expected fail: Should not fail once CR 6639109 is fixed");
		//eq = false; //CR 6639109
	} else {
		if (!sm1.getHashAlgorithm().equals(sm2.getHashAlgorithm())) {
			ECHO("getHashAlgorithm() is not equal");
			eq = false;
		}
	}
	A = sm1.getRefcount();
	B = sm2.getRefcount();
	if (A < B)
		ECHO("Acceptable ref-count increase from " + A + " to " + B);
	if (A > B) {
		ECHO("getRefcount() is less than than previous value");
		ECHO(" - Before: " + A.toString());
		ECHO(" - After:  " + B.toString());
		eq = false;
	}
	A = sm1.getMaxRefcount();
	B = sm2.getMaxRefcount();
	if (A < B)
		ECHO("Acceptable max-ref-count increase from " + A + " to " + B);
	if (A > B) {
		ECHO("getMaxRefcount() is less than the previous value");
		ECHO(" - Before: " + A.toString());
		ECHO(" - After:  " + B.toString());
		eq = false;
	}
	if (!sm1.getDeletedRefs().equals(sm2.getDeletedRefs())) {
		ECHO("getDeletedRefs() is not equal");
		ECHO(" - Before: " + sm1.getDeletedRefs());
		ECHO(" - After:  " + sm2.getDeletedRefs());
		ECHO("Expected fail: Should not fail once CR 6639939 is fixed");
		//eq = false; //CR 6639939
	}
	byte[] A = sm1.getContentHash();
	byte[] B = sm2.getContentHash();
	if (A.length != B.length) {
		ECHO("getContentHash() is not equal length");
		eq = false;
	}
	for (int i = 0; i < A.length; i++) {
		if (A[i] != B[i]) {
			ECHO("getContentHash() is not equal");
			ECHO(" - Before: " + A[i].toString());
			ECHO(" - After:  " + B[i].toString());
			eq = false;
		}
	}
	if (sm1.getChecksumAlg() != sm2.getChecksumAlg()) {
		ECHO("getChecksumAlg() is not equal");
		eq = false;
	}
	if (sm1.getNumPreceedingChksums() != (sm2.getNumPreceedingChksums())) {
		ECHO("getNumPreceedingChksums() is not equal");
		ECHO(" - Before: " + sm1.getNumPreceedingChksums());
		ECHO(" - After:  " + sm2.getNumPreceedingChksums());
		ECHO("Expected fail: Should not fail once CR 6639110 is fixed");
		//eq = false;  //CR 6639110
	}
	A = sm1.getMetadataField();
	B = sm2.getMetadataField();
	if (A.length != B.length) {
		ECHO("getMetadataField() is not equal length");
		eq = false;
	}
	for (int i = 0; i < A.length; i++) {
		if (A[i] != B[i]) {
			ECHO("getMetadataField() is not equal");
			ECHO(" - Before: " + A[i].toString());
			ECHO(" - After:  " + B[i].toString());
			eq = false;
		}
	}
	if (sm1.getExtensionModifiedTime() != sm2.getExtensionModifiedTime()) {
		ECHO(" is not equal");
		eq = false;
	}
	return eq;
}

SystemMetadata getSystemCache(int oid, boolean expected) {
	try	{
		SystemMetadata SM = GETCACHEDATA(oid);
		if (expected)
			return SM;
		else
			throw new RuntimeException("Found metadata expected to be deleted.");
	} catch (ArchiveException e) {
		if (expected)
			throw new RuntimeException("Could not find expected metadata.");
		else
			return null;
	}
}

void printSystemMetadata(SystemMetadata sm) {
	if (sm != null) {
		String a = sm.toString();
		ECHO(a);
	} else
		ECHO("Metadata does not exist");
}