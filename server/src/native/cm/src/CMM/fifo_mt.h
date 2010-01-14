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



#ifndef _FIFO_MT_H_
#define _FIFO_MT_H_

#include <pthread.h>

#include "cmm.h"

typedef struct fifo_elem_t {
    void                *obj;
    struct fifo_elem_t  *next;
} fifo_elem_t;

typedef struct fifo_t {
    pthread_mutex_t mutex;
    struct fifo_t *couple;
    pthread_cond_t *cond_var;
    fifo_elem_t *index;
    fifo_elem_t *first;
    fifo_elem_t *last;
} fifo_t;

/*
 * The 2 following functions create and destroy a FIFO
 */

fifo_t *fifo_init( fifo_t *couple );
void fifo_destroy( fifo_t *fifo );

/*
 * To add / remove an element
 */

cmm_error_t fifo_add_elem( fifo_t *fifo,
                           void *elem );

void *fifo_extract_elem( fifo_t *fifo );

/*
 * The following calls are to go through the list of elements
 */

void *fifo_get_first( fifo_t *fifo );
void *fifo_get_next( fifo_t *fifo );
void fifo_remove_current( fifo_t *fifo );
int fifo_get_nb_elems( fifo_t *fifo );

/*
 * The following call block until there is one element in the FIFO
 */

cmm_error_t fifo_block( fifo_t *fifo );
cmm_error_t fifo_timed_block( fifo_t *fifo,
                              struct timespec timeout );

/*
 * The next routine checks if there are elements in the FIFO
 */

int fifo_is_empty( fifo_t *fifo );

#endif /* _FIFO_MT_H_ */
