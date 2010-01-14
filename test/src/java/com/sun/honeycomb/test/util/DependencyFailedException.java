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

/**
 *  Used in narrow case when the test should be skipped due to failed dependency.
 *
 *  Since dependency check is done in test's setUp() method
 *  which doesn't return anything, throwing an exception is the only way
 *  to advertise that setUp() failed, and other test methods shouldn't run.
 *  Throwing any other exception will create a failed result for the test,
 *  while throwing SkippedTestException will create a skipped result.
 *
 *  Want to be able to check for this and only this exception
 */
public class DependencyFailedException extends HoneycombTestException {
    
    public DependencyFailedException(String msg) {
        super("Failed dependency: " + msg);
    }
}

/* TODO (maybe): Alternative implementation of failed dependencies:
 *
 * Instead of throwing an exception if (!validateDep()), we could 
 * set a dependency-failed flag on the test (ie in the Suite class).
 * When test methods are run, and TestCase objects are created for them,
 * we could check the dep-failed flag and addTag(Tag.MISSINGDEP) if true.
 * Then the call to excludeCase() will return true, and test will be skipped.
 *
 * This approach will create a skipped result for each test method (testcase)
 * which did not run because of the failed dependency. The current approach
 * of throwing DependencyFailedException creates a single skipped result
 * for the <Suite>::setUp testcase.
 *
 * The MISSINGDEP tag approach relies on the assumption that each test method 
 * will call excludeCase() explicitly, which is not the case today.
 * This is why I'm going with the exception instead.
 *
 * [dmehra] 03/28/05
 *
 */
