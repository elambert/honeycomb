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



/* Common definitions for the @HoneycombProductName@ C API examples */

#ifndef _EXAMPLE_COMMON
#define _EXAMPLE_COMMON

#include <stdio.h>

/* @HoneycombProductName@ header files */
#include "hc.h"
#include "hcclient.h"

/* Application constants */
#define ERRSTR_LEN 4096
#define MAX_LINESIZE 5000

#ifdef O_BINARY
#define FLAG_BINARY O_BINARY
#else
#define FLAG_BINARY 0
#endif

#ifdef    O_LARGEFILE
#define    FLAG_LARGEFILE  O_LARGEFILE
#else
#define FLAG_LARGEFILE 0
#endif

static const int STORAGETEK_PORT = 8080;

static const int RETURN_SUCCESS = 0;
static const int RETURN_COMMANDLINE_ERROR = 1;
static const int RETURN_MAPERROR = 2;
static const int RETURN_IOERROR = 3;
static const int RETURN_MAPINITERROR = 4;
static const int RETURN_STORAGETEK_ERROR = 5;

static const int DEFAULT_MAX_RESULTS = 1000;

void HandleError(hc_session_t *session, hcerr_t res);

#endif
