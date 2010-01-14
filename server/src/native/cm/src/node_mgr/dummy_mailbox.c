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



#include <mbox.h>
#include <string.h>
#include <malloc.h>

typedef struct {
    char *tag;
    mb_state_t state;
    mb_state_t expected_state;
} mb_real_id_t;

mb_id_t
mb_create(const char* tag,
	  size_t size)
{
    mb_real_id_t *result = (mb_real_id_t*)malloc(sizeof(mb_real_id_t));
    if (!result) {
	return(MB_INVALID_ID);
    }

    result->tag = strdup(tag);
    result->state = SRV_INIT;
    result->expected_state = SRV_INVALID;

    return((mb_id_t)result);
}

mb_error_t
mb_close(mb_id_t mb_id)
{
    mb_real_id_t *id = (mb_real_id_t*)mb_id;

    free(id->tag);
    free(id);
    
    return(MB_OK);
}

mb_error_t
mb_getstate(mb_id_t mb_id,
	    mb_state_t* state)
{
    mb_real_id_t *id = (mb_real_id_t*)mb_id;

    (*state) = id->state;
    return(MB_OK);
}

mb_error_t
mb_setstate(mb_id_t mb_id,
	    mb_state_t state)
{
    mb_real_id_t *id = (mb_real_id_t*)mb_id;

    id->state = state;
    return(MB_OK);
}

mb_error_t
mb_getexpectedstate(mb_id_t mb_id,
		    mb_state_t* state)
{
    mb_real_id_t *id = (mb_real_id_t*)mb_id;

    (*state) = id->expected_state;
    return(MB_OK);
}

mb_error_t
mb_setexpectedstate(mb_id_t mb_id,
		    mb_state_t state)
{
    mb_real_id_t *id = (mb_real_id_t*)mb_id;

    id->expected_state = state;

    /* Simulate the state transition in the client */
    if (state != SRV_INVALID) {
	id->state = state; 
    }

    return(MB_OK);
}
