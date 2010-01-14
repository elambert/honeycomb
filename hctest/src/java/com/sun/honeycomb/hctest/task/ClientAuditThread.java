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



package com.sun.honeycomb.hctest.task;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.common.*;

import java.util.HashMap;
import java.util.ArrayList;

/**
 *  Thread for handling audit requests run against a
 *  client rmi server.
 */
public class ClientAuditThread extends Waitable {

    private int client;

    AuditDBClient ac;

    public int entries = 0;
    public int skipped = 0;
    public int format_problems = 0;
    public int add_md = 0;
    public long data_retrieved = 0;
    public int retrieve_failures = 0;
    public int bad_data = 0;
    public int delete_failures = 0;
    public int ok_retrieves = 0;
    public int ok_deletes = 0;
    public int suspect_deletes = 0;
    public int no_host_errors = 0;
    public int unexpected_deletes = 0;

    public int unexpected = 0;

    // access to singleton
    protected TestBed testBed; 

    public ClientAuditThread(int client, AuditDBClient ac) {
        testBed = TestBed.getInstance(); // access to singleton
        this.client = client;
        this.ac = ac;
    }

    public void run() {

        while (true) {
            if (stop) {
                Log.INFO("quitting on stop cmd");
                break;
            }
            //
            //  get next oid entry
            //
            //Log.INFO("getting oid info");
            HashMap oid_info;
            try {
                oid_info = ac.getNextObjectRecord();
            } catch (Exception e) {
                Log.ERROR("quitting on: " + e);
                break;
            }
            if (oid_info == null) {
                //Log.INFO("done - no more oid's");
                break;
            }
            //Log.INFO("oid info\n" + oid_info);

            entries++;

            String oid;
            long filesize;
            String sha1;
            boolean deleted, added_md;
            try {
                oid = (String) oid_info.get("oid_ext");
                Long l = (Long) oid_info.get("obj_size");
                filesize = l.longValue();
                sha1 = (String) oid_info.get("sha1");
                Boolean b = (Boolean) oid_info.get("deleted");
                deleted = b.booleanValue();
            } catch (Exception e) {
                Log.ERROR("CAN'T PARSE RECORD - SKIPPING " + oid_info + ": " + e);
                skipped++;
                continue;
            }

            retrieveCheck(oid, deleted, filesize, sha1);
        }
        done = true;
    }

    private void retrieveCheck(String oid, boolean deleted, long filesize, 
                                                                String sha1) {
        //
        //  retrieve
        //
        Log.INFO("retrieving " + oid + " on client " + client);
        CmdResult result;
        try {
            result = testBed.retrieve(client, oid);
        } catch (Exception e) {
            //
            //  should only get this for an error in test
            //  infrastructure
            //
            retrieve_failures++;
            Log.ERROR("RETRIEVE FAILED: " + oid + ": " + e);
            return;
        }

        //
        //  check result
        //

        //Log.INFO("retrieve " + oid + " on client " + client +
        //         " pass=" + result.pass);
        //
        //  XXX check that exception is correct for deleted
        //  beyond the no_host issue - is NoSuchObjectException
        //  always right?
        //
        boolean no_host = false;
        boolean no_object = false;
        boolean unexpected_delete_ex = false;
        boolean unexpected_delete = false;
        Throwable t = null;
        if (result.thrown != null  &&  result.thrown.size() > 0) {
            for (int i=0; i<result.thrown.size(); i++) {
                t = (Throwable) result.thrown.get(i);

                if (t.getMessage().indexOf("no active host available") != -1) {
                    Log.INFO("CmdResult.thrown[" + i + "]: no active host");
                } else if (result.pass) {
                    // succeeded in the end
                    Log.INFO("CmdResult.thrown[" + i + "]: " + t.toString());
                } else if (deleted) {
                    if (!(t instanceof NoSuchObjectException)) {
                        unexpected_delete_ex = true;
                        Log.ERROR("Unexpected delete exception: " + 
                                                              t.toString());
                    }
                } else {
                    if (t instanceof NoSuchObjectException) {
                        Log.ERROR("'Deleted' exception for undeleted: " + 
                                                              t.toString());
                        unexpected_delete = true;
                    } else {
                        // whole stack trace, just in case
                        Log.INFO("CmdResult.thrown[" + i + "]:\n" +
                                                        Log.stackTrace(t));
                    }
                }
            }
            //
            //  look at last exception
            //
            if (t != null) {
                if (t instanceof NoSuchObjectException) {
                    //
                    //  this is a good-news exception if obj was deleted
                    //
                    no_object = true;
                } else if (t.getMessage().indexOf("no active host available") 
                                                                   != -1) {
                    //
                    //  if last exception was no_host, & retrieve failed, test
                    //  was insufficient
                    //
                    no_host = true;
                }
            }
        }
        if (!result.pass) {
            if (no_host) {
                no_host_errors++;
            } else if (deleted) {
                if (!no_object  ||  unexpected_delete_ex)
                     suspect_deletes++;
                ok_deletes++;
            } else {
                if (no_object) {
                    unexpected_deletes++;
                } else {
                    retrieve_failures++;
                    Log.ERROR("UNABLE TO RETRIEVE: " + oid + "  excep: " + 
                                (t == null ? "null" : t.toString()));
                }
            }
        } else {
            data_retrieved += result.filesize;
            if (deleted) {
                delete_failures++;
                Log.ERROR("RETRIEVED DELETED: " + oid);
            } else {
                ok_retrieves++;
                if (!result.datasha1.equals(sha1)) {
                    bad_data++;
                    Log.ERROR("BAD DATA: " + oid + ": " + sha1 + "/" + 
                                                     result.datasha1);
                }
            }
            //
            //  XXX TODO - check md
            //
            //
            //  XXX TODO - whitebox inspection of all fragments
            //  add WHITEBOX tag to test case if this happens.. 
            //  or maybe make a separate prog for whitebox audit
            //
        }
    }
}
