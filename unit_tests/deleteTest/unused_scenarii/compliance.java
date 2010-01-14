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



import com.sun.honeycomb.delete.Constants;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.DeletedObjectException;

int  storesize = 10;
int  oid       = 0;

int  loopCount = 20; // Number of times to loop over operations

String HOLD_TAG = "Hold me";
String HOLD_TAG_256K;

StringBuffer foo = new StringBuffer();
int howMany = (256*1024)/HOLD_TAG.length();
for (int i = 0; i < howMany; i++) {
    foo.append (HOLD_TAG);
}
HOLD_TAG_256K = foo.toString();

void setRetDate(int oid, long retainUntil) {
    setRetDate (oid, retainUntil, true);
}

void setRetDate(int oid, long retainUntil, boolean shouldPass) {
    boolean threw = false;
    try {
        SETRETENTION (oid, retainUntil);
    } catch (ArchiveException ae) {
        if (shouldPass) {
            throw ae;
        }
        threw = true;
    }

    if (!shouldPass && !threw) {
        throw new RuntimeException (
            "Expected Exception not raised when setting invalid retention time");
    }
}

void verifyRetDate(int oid, long retainUntil) {
    long storedDate = GETRETENTION(oid).longValue();
    if (storedDate != retainUntil) {
        throw new RuntimeException ("stored retention date (" 
                    + storedDate + ") != expected value (" + retainUntil + ")");
    } 
}

void setHold(oid, String holdTag) {
    setHold(oid, holdTag, true);
}

void setHold(oid, String holdTag, boolean shouldPass) {
    boolean threw = false;
    try {
        ADDHOLD(oid, holdTag);
    } catch (ArchiveException ae) {
        if (shouldPass) {
            throw ae;
        }
        threw = true;
    }

    if (!shouldPass && !threw) {
        throw new RuntimeException (
            "Expected exception not raised when setting legal hold");
    }
}

void verifyHold(int oid, String holdTag) {
    verifyHold(oid, holdTag, true);
}

void verifyHold(int oid, String holdTag, boolean shouldPass) {
    boolean foundHold = false;
    String[] holds = GETHOLDS(oid);

    if (holds == null) {
        if (shouldPass) {
            throw new RuntimeException ("Expected legal hold '" 
                                        + holdTag + "' not found");
        } else if (!shouldPass) {
            return;
        }
    }

    for (int i = 0; i < holds.length; i++) {
        if (holds[i].equals(holdTag)) {
            foundHold = true;
            break;
        }
    }

    if (shouldPass && !foundHold) {
        throw new RuntimeException ("Expected legal hold '" 
            + holdTag + "' not found");
    }

    if (!shouldPass && foundHold) {
        throw new RuntimeException ("Unexpectedly found " +
                                    "legal hold '" + holdTag +
                                    "' where it should not exist");
    }
}

void removeHold(int oid, String holdTag) {
    removeHold (oid, holdTag, true);
}

void removeHold(int oid, String holdTag, boolean shouldPass) {
    boolean threw = false;
    try {
        RMHOLD (oid, holdTag);
    } catch (ArchiveException ae) {
        if (shouldPass) {
            throw ae;
        }
        threw = true;
    }

    if (!shouldPass && !threw) {
        throw new RuntimeException (
            "Expected exception not raised when removing legal hold: '" 
                + holdTag + "'");
    }
}

/* *********************************************** */

ECHO("0.1: Get default retention date.");
STORE(++oid, storesize);
verifyRetDate (oid, 0);
ECHO ("    * Success");

/* *********************************************** */

ECHO("1a: Set/Get Future retention date");
retDate = System.currentTimeMillis()+(30*Constants.SECONDS);
STORE(++oid, storesize);
setRetDate (oid, retDate);
verifyRetDate (oid, retDate);
ECHO ("    * Success");

/* *********************************************** */

ECHO("1b: Set past retention date");
retDate = System.currentTimeMillis()-(30*Constants.SECONDS); // 30s in the past
STORE(++oid, storesize);
setRetDate (oid, retDate, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO("1c: Extend retention date into the future");
retDate = System.currentTimeMillis()+(30*Constants.SECONDS);
STORE(++oid, storesize);
setRetDate (oid, retDate);
retDate = retDate+(30*Constants.DAYS); //extend
setRetDate (oid, retDate);
verifyRetDate (oid, retDate);
ECHO ("    * Success");

/* *********************************************** */

ECHO("1d: Extend retention date into the past");
retDate = System.currentTimeMillis()+(30*Constants.SECONDS); // retain for 30s
STORE(++oid, storesize);
setRetDate(oid, retDate);
retDate = retDate-(30*Constants.MINUTES); // 30s from now, minus 30m
setRetDate (oid, retDate, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO("1e: Set indefinite retention date");
STORE(++oid, storesize);
setRetDate(oid, -1);
verifyRetDate (oid, -1);
ECHO ("    * Success");

/* *********************************************** */

ECHO("1f: Extend existing infinite retention date into the future");
retDate=System.currentTimeMillis()+(30*Constants.MONTHS);
setRetDate(oid, retDate);
verifyRetDate(oid, retDate);
ECHO ("    * Success");

/* *********************************************** */

ECHO("1g: Extend existing infinite retention date into the past");
STORE(++oid, storesize);
setRetDate(oid, -1);
retDate = System.currentTimeMillis()-(30*Constants.MINUTES);
setRetDate (oid, retDate, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO("1h: Set retention date far into the future");
STORE(++oid, storesize);

ECHO ("    - 1 Year");
retDate = System.currentTimeMillis()+(1*Constants.YEARS);
setRetDate(oid, retDate);
verifyRetDate (oid, retDate);

ECHO ("    - 10 Years");
retDate = System.currentTimeMillis()+(10*Constants.YEARS);
setRetDate(oid, retDate);
verifyRetDate (oid, retDate);

ECHO ("    - 100 Years");
retDate = System.currentTimeMillis()+(100*Constants.YEARS);
setRetDate(oid, retDate);
verifyRetDate (oid, retDate);

ECHO("     - Long.MAX_VALUE seconds");
setRetDate(oid, Long.MAX_VALUE);
verifyRetDate (oid, Long.MAX_VALUE);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("2a: Set future retention date and delete object");
STORE(++oid, storesize);
setRetDate (oid, System.currentTimeMillis()+(30*Constants.SECONDS));
DELETE (oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("2b: Extend existing retention date and delete object");
setRetDate (oid, System.currentTimeMillis()+(30*Constants.DAYS));
DELETE (oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("2c. Set INF retention date and delete the object");
STORE(++oid, storesize);
setRetDate (oid, -1);
DELETE (oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("2d: Convert existing retention date and delete object");
setRetDate (oid, System.currentTimeMillis()+(10*Constants.HOURS));
DELETE (oid, false);
ECHO ("    * Success");

/* *********************************************** */
ECHO ("3a. Set retention date in the future, wait for its expiration, delete");
STORE(++oid, storesize);
setRetDate (oid, System.currentTimeMillis()+(2*Constants.SECONDS));
Thread.sleep(5*Constants.SECONDS);
DELETE (oid, true);
ECHO ("    * Success");

/* *********************************************** */
ECHO ("3b. Set retention date, extend it, wait for expiration, delete");
STORE(++oid, storesize);
setRetDate (oid, System.currentTimeMillis()+(1*Constants.SECONDS));
setRetDate (oid, System.currentTimeMillis()+(3*Constants.SECONDS));
Thread.sleep(5*Constants.SECONDS);
DELETE (oid, true);
ECHO ("    * Success");

/* *********************************************** */
ECHO ("3c. Set INF retention date, convert future date, expire, delete");
STORE(++oid, storesize);
setRetDate (oid, -1);
setRetDate (oid, System.currentTimeMillis()+(2*Constants.SECONDS));
Thread.sleep(5*Constants.SECONDS);
DELETE (oid, true);
ECHO ("    * Success");

/* *********************************************** */
ECHO ("3d. Set future retention date, expire, delete, delete again");
STORE(++oid, storesize);
setRetDate (oid, System.currentTimeMillis()+(2*Constants.SECONDS));
Thread.sleep(5*Constants.SECONDS);
DELETE (oid, true);
boolean threw = false;
try {
    DELETE (oid, true);
}catch (DeletedObjectException de) {
   threw = true;
}
if (!threw) {
    throw new RuntimeException (
        "Delete of previously deleted object succeeded, where it should fail");
}
ECHO ("    * Success");

/* *********************************************** */

ECHO ("4a. Set legal hold on an object");
STORE (++oid, storesize);
setHold (oid, HOLD_TAG);
verifyHold (oid, HOLD_TAG);
ECHO ("    * Success");

/* *********************************************** */

//ECHO ("4b. Set legal hold on an object using 'invalid' text");
//STORE (++oid, storesize);
//setHold (oid, "'''''", false);
//ECHO ("    * Success");

/* *********************************************** */

ECHO ("4c. Set many holds on an object");
STORE (++oid, storesize);
for (int i = 0; i < loopCount; i++) {
    setHold (oid, HOLD_TAG+i);
    verifyHold (oid, HOLD_TAG+i);
}
ECHO ("    * Success");

/* *********************************************** */

ECHO ("4d. Set legal hold, remove it");
STORE (++oid, storesize);
setHold (oid, HOLD_TAG);
verifyHold (oid, HOLD_TAG);
removeHold(oid, HOLD_TAG);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("4e. Attempt removing a non-existing legal hold");
STORE (++oid, storesize);
removeHold(oid, HOLD_TAG, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("4f. Set legal hold in OA unit test with a large tag size (256k)");
STORE (++oid, storesize);
setHold (oid, HOLD_TAG_256K);
verifyHold (oid, HOLD_TAG_256K);
removeHold(oid, HOLD_TAG_256K);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("4g. Set many legal holds in OA with a large tag size");
STORE (++oid, storesize);
for (int i = 0; i < loopCount; i++) {
    setHold (oid, HOLD_TAG_256K+i);
    verifyHold (oid, HOLD_TAG_256K+i);
}
ECHO ("    * Success");

/* *********************************************** */

ECHO ("5a. Set legal hold, remove it, delete the object");
STORE (++oid, storesize);
setHold (oid, HOLD_TAG);
verifyHold (oid, HOLD_TAG);
removeHold(oid, HOLD_TAG);
DELETE (oid, true);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("5b. Set many holds, remove all, delete the object");
STORE (++oid, storesize);
for (int i = 0; i < loopCount; i++) {
    setHold (oid, HOLD_TAG+i);
    verifyHold (oid, HOLD_TAG+i);
}
for (int i = 0; i < loopCount; i++) {
    removeHold (oid, HOLD_TAG+i);
}
DELETE (oid, true);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("5c. Set/remove hold repeatedly");
STORE (++oid, storesize);
for (int i = 0; i < loopCount; i++) {
    setHold (oid, HOLD_TAG+i);
    verifyHold (oid, HOLD_TAG+i);
    removeHold (oid, HOLD_TAG+i);
}
ECHO ("    * Success");

/* *********************************************** */

ECHO ("5d. Set hold, remove it, delete, attempt to delete again");
STORE (++oid, storesize);
setHold (oid, HOLD_TAG);
verifyHold (oid, HOLD_TAG);
removeHold(oid, HOLD_TAG);
DELETE (oid, true);
boolean threw = false;
try {
    DELETE (oid, true);
}catch (DeletedObjectException de) {
   threw = true;
}
if (!threw) {
    throw new RuntimeException (
        "Delete of previously deleted object succeeded, where it should fail");
}
ECHO ("    * Success");

/* *********************************************** */

ECHO ("6a. Set legal hold on an object, attempt delete");
STORE (++oid, storesize);
setHold (oid, HOLD_TAG);
DELETE(oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("6b. Set many legal holds, attempt delete");
STORE (++oid, storesize);
for (int i = 0; i < loopCount; i++) {
    setHold (oid, HOLD_TAG_256K+i);
}
DELETE(oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("6c. Set 2 legal holds, remove one of them, attempt delete");
STORE (++oid, storesize);
setHold (oid, HOLD_TAG);
setHold (oid, HOLD_TAG+"2");
removeHold (oid, HOLD_TAG);
DELETE(oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("6d. Set many legal holds, remove all but one, attempt delete");
STORE (++oid, storesize);
for (int i = 0; i < loopCount; i++) {
    setHold (oid, HOLD_TAG+i);
}
for (int i = 1; i < loopCount; i++) {
    removeHold (oid, HOLD_TAG+i);
}
DELETE (oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("6e. Set many legal holds, remove one, delete until success");
STORE (++oid, storesize);
for (int i = 0; i < loopCount; i++) {
    setHold (oid, HOLD_TAG+i);
}
for (int i = 0; i < loopCount; i++) {
    removeHold (oid, HOLD_TAG+i);
    if (i == (loopCount - 1)) {
        DELETE (oid, true);
    } else {
        DELETE (oid, false);
    }
}
ECHO ("    * Success");

/* *********************************************** */

ECHO ("7a. Set future retention date, add hold, attempt delete");
STORE(++oid, storesize);
setRetDate (oid, System.currentTimeMillis()+(1*Constants.YEARS)); 
setHold (oid, HOLD_TAG);
DELETE (oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("7b. Set future retention date, add hold. Expire. Delete"); 
STORE(++oid, storesize);
setRetDate (oid, System.currentTimeMillis()+(5*Constants.SECONDS));
setHold (oid, HOLD_TAG);
Thread.sleep(10*Constants.SECONDS);
DELETE (oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("7c. Set future retention date, add hold. Expire, remove hold, delete.");
STORE(++oid, storesize);
setRetDate (oid, System.currentTimeMillis()+(5*Constants.SECONDS));
setHold (oid, HOLD_TAG);
Thread.sleep(10*Constants.SECONDS);
removeHold (oid, HOLD_TAG);
DELETE (oid, true);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("7d. Set INF retention date, add hold, attempt delete");
STORE(++oid, storesize);
setRetDate (oid, -1);
setHold (oid, HOLD_TAG);
DELETE (oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("7e. Set INF retention date, add hold, remove hold, attempt delete");
STORE(++oid, storesize);
setRetDate (oid, -1);
setHold (oid, HOLD_TAG);
removeHold (oid, HOLD_TAG);
DELETE (oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("7f. Set INF retention date, add hold. Convert INF to future date, ");
ECHO ("    remove hold, wait for retention to expire. Delete");
STORE(++oid, storesize);
setRetDate (oid, -1);
setHold (oid, HOLD_TAG);
setRetDate (oid, System.currentTimeMillis()+(5*Constants.SECONDS));
removeHold (oid, HOLD_TAG);
Thread.sleep(10*Constants.SECONDS);
DELETE (oid, true);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("7g. Set future retention date. Add many holds. Remove some holds. ");
ECHO ("    Wait for retention to expire. Remove remaining holds. Delete");
STORE(++oid, storesize);
setRetDate (oid, System.currentTimeMillis()+(10*Constants.SECONDS));
for (int i = 0; i < loopCount; i++) {
    setHold (oid, HOLD_TAG+i);
}
for (int i = 0; i < (loopCount/2); i++) {
    removeHold (oid, HOLD_TAG+i);
}
Thread.sleep(10*Constants.SECONDS);
for (int i = (loopCount/2); i < loopCount; i++) {
    removeHold (oid, HOLD_TAG+i);
}
DELETE (oid, true);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("7h. Set future retention date, add hold, remove hold, expire, delete, ");
ECHO ("    attempt to delete again (get correct DeletedObjectException back).");
STORE(++oid, storesize);
setRetDate (oid, System.currentTimeMillis()+(5*Constants.SECONDS));
setHold (oid, HOLD_TAG);
removeHold (oid, HOLD_TAG);
Thread.sleep(10*Constants.SECONDS);
DELETE (oid, true);
DELETE (oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("8.1. Get retention date for a given object");
STORE(++oid, storesize);
retDate = System.currentTimeMillis()+(7*Constants.DAYS);
setRetDate (oid, retDate);
long storedDate = GETRETENTION(oid).longValue();
if (retDate != storedDate) {
    throw new RuntimeException ("GETRETENTION failed to return expected value");
}
ECHO ("    * Success");

/* *********************************************** */

ECHO ("8.2. Get all legal holds for a given object");
STORE(++oid, storesize);
for (int i = 0; i < 10; i++) {
    setHold(oid, HOLD_TAG+i);
}
String[] holds = GETHOLDS(oid);
if (holds == null || holds.length != 10) {
    throw new RuntimeException ("Not all expected holds present");
    // TODO: add more validation here
}
ECHO ("    * Success");

/* *********************************************** */

ECHO ("8.3. Get all objects that are under a given legal hold");
NewObjectIdentifier[] objs = GETHELD (HOLD_TAG);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("9.1. Hide two object fragments & set retention date & verify & recover frags & verify");
retDate = System.currentTimeMillis()+(30*Constants.SECONDS);
STORE(++oid, storesize);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 3);
setRetDate(oid, retDate);
verifyRetDate(oid, retDate);
FRAGRESTORE(oid, 0);
FRAGRESTORE(oid, 3);
verifyRetDate(oid, retDate);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("9.2. Hide two object fragments & set retention date & verify & recover frags & verify");
retDate = System.currentTimeMillis()+(30*Constants.SECONDS);
STORE(++oid, storesize);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 3);
setRetDate(oid, retDate);
verifyRetDate(oid, retDate);
RECOVER(oid,0);
RECOVER(oid,3);
verifyRetDate(oid, retDate);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("9.3. Hide two object fragments & set retention date & verify & extend retention date & recover frags & verify");
retDate = System.currentTimeMillis()+(30*Constants.SECONDS);
STORE(++oid, storesize);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 3);
setRetDate(oid, retDate);
verifyRetDate(oid, retDate);
retDate = retDate + (30*Constants.SECONDS);
setRetDate(oid, retDate);
RECOVER(oid,0);
RECOVER(oid,3);
verifyRetDate(oid, retDate);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("9.4. Hide two object fragments & set INF retention date & verify & extend retention date & recover frags & verify");
STORE(++oid, storesize);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 3);
setRetDate(oid, -1);
verifyRetDate(oid, -1);
retDate = System.currentTimeMillis()+(30*Constants.SECONDS);
setRetDate(oid, retDate);
RECOVER(oid,0);
RECOVER(oid,3);
verifyRetDate(oid, retDate);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("9.5. Set retention time & hide two object fragments & verify");
retDate = System.currentTimeMillis()+(30*Constants.SECONDS);
STORE(++oid, storesize);
setRetDate(oid, retDate);
verifyRetDate(oid, retDate);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 3);
verifyRetDate(oid, retDate);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("9.6. Set retention time & hide two object fragments & attempt delete");
retDate = System.currentTimeMillis()+(30*Constants.SECONDS);
STORE(++oid, storesize);
setRetDate(oid, retDate);
verifyRetDate(oid, retDate);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 3);
verifyRetDate(oid, retDate);
DELETE (oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("9.7. Set retention time & hide two object fragments & attempt delete & wait for expiration & delete");
retDate = System.currentTimeMillis()+(2*Constants.SECONDS);
STORE(++oid, storesize);
setRetDate(oid, retDate);
verifyRetDate(oid, retDate);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 3);
verifyRetDate(oid, retDate);
DELETE (oid, false);
Thread.sleep(5*Constants.SECONDS);
DELETE (oid, true);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("9.7. Set retention time & hide two object fragments & attempt delete & recover frags & wait for expiration & delete");
retDate = System.currentTimeMillis()+(2*Constants.SECONDS);
STORE(++oid, storesize);
setRetDate(oid, retDate);
verifyRetDate(oid, retDate);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 3);
verifyRetDate(oid, retDate);
DELETE (oid, false);
RECOVER(oid,0);
RECOVER(oid,3);
Thread.sleep(5*Constants.SECONDS);
DELETE (oid, true);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("10.1. Hide two object fragments & add legal hold & verify & attempt delete & unhide frags & verify & attempt delete");
STORE(++oid, storesize);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 3);
setHold(oid, HOLD_TAG);
verifyHold(oid, HOLD_TAG);
DELETE(oid, false);
FRAGRESTORE(oid, 0);
FRAGRESTORE(oid, 3);
verifyHold(oid, HOLD_TAG);
DELETE(oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("10.2. Hide two object fragments & add legal hold & verify & attempt delete & recover frags & verify & attempt delete");
STORE(++oid, storesize);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 3);
setHold(oid, HOLD_TAG);
verifyHold(oid, HOLD_TAG);
DELETE(oid, false);
RECOVER(oid,0);
RECOVER(oid,3);
verifyHold(oid, HOLD_TAG);
DELETE(oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("10.3. Add legal hold & hide two object fefs & verify & attempt delete & recover fefs & verify & attempt delete");
STORE(++oid, storesize);
setHold(oid, HOLD_TAG);
FEFREMOVE(oid, 0);
FEFREMOVE(oid, 3);
verifyHold(oid, HOLD_TAG);
DELETE(oid, false);
FEFRECOVER(oid);
FEFRECOVER(oid);
verifyHold(oid, HOLD_TAG);
DELETE(oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("10.4. Add legal hold & hide two object frags+fefs & verify & attempt delete & recover frags+fefs & verify & attempt delete");
STORE(++oid, storesize);
setHold(oid, HOLD_TAG);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 3);
FEFREMOVE(oid, 0);
FEFREMOVE(oid, 3);
verifyHold(oid, HOLD_TAG);
DELETE(oid, false);
RECOVER(oid,0);
RECOVER(oid,3);
FEFRECOVER(oid);
FEFRECOVER(oid);
verifyHold(oid, HOLD_TAG);
DELETE(oid, false);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("10.5. Add legal hold & hide two object frags+fefs & remove legal hold & delete");
STORE(++oid, storesize);
setHold(oid, HOLD_TAG);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 3);
FEFREMOVE(oid, 0);
FEFREMOVE(oid, 3);
removeHold(oid, HOLD_TAG);
DELETE(oid, true);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("10.6. Add legal hold & hide two object fefs & remove legal hold & delete");
STORE(++oid, storesize);
setHold(oid, HOLD_TAG);
FEFREMOVE(oid, 0);
FEFREMOVE(oid, 3);
removeHold(oid, HOLD_TAG);
DELETE(oid, true);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("10.7. Add legal hold & hide two object frags+fefs & remove legal hold & recover frags & delete");
STORE(++oid, storesize);
setHold(oid, HOLD_TAG);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 3);
FEFREMOVE(oid, 0);
FEFREMOVE(oid, 3);
removeHold(oid, HOLD_TAG);
RECOVER(oid,0);
RECOVER(oid,3);
FEFRECOVER(oid);
DELETE(oid, true);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("10.8. Add legal hold & hide two object fefs & remove legal hold & recover fefs & delete");
STORE(++oid, storesize);
setHold(oid, HOLD_TAG);
FEFREMOVE(oid, 0);
FEFREMOVE(oid, 3);
removeHold(oid, HOLD_TAG);
FEFRECOVER(oid);
DELETE(oid, true);
ECHO ("    * Success");

/* *********************************************** */

ECHO ("10.9. Hide four object fragments & set retention date (expect fail)");
retDate = System.currentTimeMillis()+(30*Constants.SECONDS);
STORE(++oid, storesize);
FRAGREMOVE(oid, 0);
FRAGREMOVE(oid, 2);
FRAGREMOVE(oid, 5);
FRAGREMOVE(oid, 6);
setRetDate(oid, retDate, false);
ECHO ("    * Success");

/* *********************************************** */

//ECHO ("10.10. Hide four object fragments & add a legal hold");
//retDate = System.currentTimeMillis()+(30*Constants.SECONDS);
//STORE(++oid, storesize);
//FRAGREMOVE(oid, 1);
//FRAGREMOVE(oid, 3);
//FRAGREMOVE(oid, 4);
//FRAGREMOVE(oid, 5);
//setHold(oid, HOLD_TAG, false);
//ECHO ("    * Success");

/* *********************************************** */
