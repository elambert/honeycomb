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



%#include "globals.h"

%#define LM_MAXSTRLEN	1024

enum nlm4_stats {
   NLM4_GRANTED = 0,
   NLM4_DENIED = 1,
   NLM4_DENIED_NOLOCKS = 2,
   NLM4_BLOCKED = 3,
   NLM4_DENIED_GRACE_PERIOD = 4,
   NLM4_DEADLCK = 5,
   NLM4_ROFS = 6,
   NLM4_STALE_FH = 7,
   NLM4_FBIG = 8,
   NLM4_FAILED = 9
};

struct nlm4_lock {
   string caller_name<LM_MAXSTRLEN>;
   netobj fh;              /* identify a file */
   netobj oh;              /* identify owner of a lock */
   long svid;             /* generated from pid for svid */
   unsigned hyper l_offset;
   unsigned hyper l_len;
};

struct nlm4_lockargs {
   netobj cookie;
   bool block;
   bool exclusive;
   struct nlm4_lock alock;
   bool reclaim;           /* used for recovering locks */
   long state;            /* specify local status monitor state */
};

struct nlm4_testargs {
   netobj cookie;
   bool exclusive;
   struct nlm4_lock alock;
};

struct nlm4_unlockargs {
   netobj cookie;
   struct nlm4_lock alock;
};

struct nlm4_stat {
   nlm4_stats stat;
};

struct nlm4_res {
   netobj cookie;
   nlm4_stat stat;
};

struct nlm4_holder {
   bool exclusive;
   long svid;
   netobj oh;
   unsigned hyper l_offset;
   unsigned hyper l_len;
};

union nlm4_testrply switch (nlm4_stats stat) {
   case NLM4_DENIED:
      struct nlm4_holder holder;
   default:
      void;
};
struct nlm4_testres {
   netobj cookie;
   nlm4_testrply stat;
};

program NLM_PROGRAM {
   version NLM_V4 {
      nlm4_testres
         NLMPROC4_TEST(nlm4_testargs)         = 1;
      nlm4_res
         NLMPROC4_LOCK(nlm4_lockargs)         = 2;
      nlm4_res
         NLMPROC4_UNLOCK(nlm4_unlockargs)     = 4;
   } = 4;
} = 100021;
