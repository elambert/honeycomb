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



/* Implementation of a thread safe fifo */

#include <malloc.h>
#include <pthread.h>
#include <errno.h>

#include "fifo_mt.h"
#include "trace.h"

/*
 * FIFO implementation :
 * - the last element added is at the end (pointed by last)
 * - the first element to go is at the beginning (pointed by first)
 */

fifo_t *
fifo_init( fifo_t *couple )
{
    fifo_t *result = NULL;
    int err = 0;

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "Creating a new FIFO" );

    result = (fifo_t*)malloc(sizeof(fifo_t));
    if (!result) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "malloc failed to create a new FIFO" );
        return(NULL);
    }

    if ( couple ) {
        result->cond_var = NULL;
        result->couple = couple;
    } else {
        result->cond_var = (pthread_cond_t*)malloc(sizeof(pthread_cond_t));
        if (!result->cond_var) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "malloc failed");
            free(result);
            return(NULL);
        }
        result->couple = NULL;
    }

    err = pthread_mutex_init( &result->mutex, NULL);
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pthread_mutex_init failed [%d]",
                 err );
        free(result->cond_var);
        free(result);
        return(NULL);
    }

    if ( !result->couple ) {
        err = pthread_cond_init( result->cond_var, NULL );
        if (err) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "pthread_cond_init failed [%d]",
                     err );
            (int)pthread_mutex_destroy( &result->mutex );
            free(result->cond_var);
            free(result);
            return(NULL);
        }
    }

    result->first = NULL;
    result->last = NULL;
    result->index = NULL;

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "A new FIFO has been successfully created" );
    
    return(result);
}

cmm_error_t
fifo_add_elem( fifo_t *fifo,
               void *obj )
{
    fifo_elem_t *elem = NULL;
    int err;

    elem = (fifo_elem_t*)malloc(sizeof(fifo_elem_t));
    if (!elem) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "malloc failed in fifo_add_elem" );
        return(CMM_ENOMEM);
    }

    elem->obj = obj;
    elem->next = NULL;

    err = pthread_mutex_lock( &fifo->mutex );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pthread_mutex_lock failed [%d]",
                 err );
        free(elem);
        return(CMM_EOTHER);
    }

    if ( (!fifo->last) ) {
        fifo->first = elem;
        fifo->last = elem;
    } else {
        fifo->last->next = elem;
        fifo->last = elem;
    }
        
    err = pthread_mutex_unlock( &fifo->mutex );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pthread_mutex_unlock failed [%d]",
                 err );
    }

    err = pthread_cond_signal( fifo->couple ? fifo->couple->cond_var : fifo->cond_var );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pthread_cond_signal failed [%d]",
                 err );
    }

    return(CMM_OK);
}

void *
fifo_extract_elem( fifo_t *fifo )
{
    int err;
    fifo_elem_t *elem = NULL;
    void *result = NULL;

    err = pthread_mutex_lock( &fifo->mutex );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pthread_mutex_lock failed [%d]",
                 err );
        return(NULL);
    }

    if (!fifo->first) {
        result = NULL;
    } else {
        elem = fifo->first;
        if (fifo->index == fifo->first) {
            fifo->index = fifo->first->next;
        }
            
        result = elem->obj;

        if (fifo->last == fifo->first) {
            fifo->first = NULL;
            fifo->last = NULL;
        } else {
            fifo->first = fifo->first->next;
        }
        free(elem);
    }
    
    err = pthread_mutex_unlock( &fifo->mutex );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pthread_mutex_unlock failed [%d]",
                 err );
    }

    return(result);
}

/*
 * The following function can be called ONLY when there is no more thread
 * accessing the FIFO
 */

void
fifo_destroy( fifo_t *fifo )
{
    int err;
    fifo_elem_t *elem, *next;
    
    if (fifo->first) {
        elem = fifo->first;
        while (elem) {
            next = elem->next;
            free(elem);
            elem = next;
        }
    }

    if ( !fifo->couple ) {
        err = pthread_cond_destroy( fifo->cond_var );
        if (err) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "pthread_cond_destroy failed [%d]",
                     err );
        }
        free( fifo->cond_var );
        fifo->cond_var = NULL;
    }

    err = pthread_mutex_destroy( &fifo->mutex );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pthread_mutex_destroy failed [%d]",
                 err );
    }

    free( fifo );
}

static cmm_error_t
fifo_block_common( fifo_t *fifo,
                   struct timespec *timeout,
                   int withTimeout )
{
    int err;
    cmm_error_t result = CMM_OK;

    err = pthread_mutex_lock( &fifo->mutex );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pthread_mutex_lock failed [%d]",
                 err );
        return(CMM_EOTHER);
    }
    
    while ( !( ((fifo->couple) && (fifo->couple->first))
               || (fifo->first) )
            && (result != CMM_ETIMEDOUT) ) {
        err = CMM_OK;
        if ( withTimeout ) {
            err = pthread_cond_timedwait( fifo->couple ? fifo->couple->cond_var : fifo->cond_var,
                                          &fifo->mutex,
                                          timeout );
        } else {
            err = pthread_cond_wait( fifo->couple ? fifo->couple->cond_var : fifo->cond_var,
                                     &fifo->mutex );
        }

        result = CMM_OK;
        if (err) {
            if ( (!withTimeout)
                 || ( (withTimeout)
                      && (err != ETIMEDOUT) )) {
                cm_trace(CM_TRACE_LEVEL_ERROR, 
                         "pthread_cond_wait failed [%d]",
                         err );
                result = CMM_EOTHER;
            }
            if ( (withTimeout)
                 && (err == ETIMEDOUT) ) {
                result = CMM_ETIMEDOUT;
            }
        }
    }

    err = pthread_mutex_unlock( &fifo->mutex );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pthread_mutex_unlock failed [%d]",
                 err );
    }

    if ( err == ETIMEDOUT ) {
        return(CMM_ETIMEDOUT);
    }

    return(result);
}

cmm_error_t
fifo_timed_block( fifo_t *fifo,
                  struct timespec timeout )
{
    return( fifo_block_common(fifo, &timeout, 1) );
}

cmm_error_t
fifo_block( fifo_t *fifo )
{
    return( fifo_block_common(fifo, NULL, 0) );
}

static void *
fifo_get_index( fifo_t *fifo,
                int get_first )
{
    int err;
    void *result = NULL;
    
    err = pthread_mutex_lock( &fifo->mutex );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pthread_mutex_lock failed [%d]",
                 err );
        return(NULL);
    }

    if (get_first) {
        fifo->index = fifo->first;
    }

    if (fifo->index) {
        result = fifo->index->obj;
        fifo->index = fifo->index->next;
    } else {
        result = NULL;
    }

    err = pthread_mutex_unlock( &fifo->mutex );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pthread_mutex_unlock failed [%d]",
                 err );
    }

    return(result);
}

void *
fifo_get_first( fifo_t *fifo )
{
    return( fifo_get_index(fifo, 1) );
}

void *
fifo_get_next( fifo_t *fifo )
{
    return( fifo_get_index(fifo, 0) );
}

static fifo_elem_t *
get_previous( fifo_elem_t *first,
              fifo_elem_t *elem )
{
    fifo_elem_t *result;

    if (elem == first) {
        return(NULL);
    }

    result = first;
    while ( result->next != elem ) {
        result = result->next;
    }

    return(result);
}

void
fifo_remove_current( fifo_t *fifo )
{
    fifo_elem_t *previous, *to_be_removed;
    int err;

    err = pthread_mutex_lock( &fifo->mutex );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pthread_mutex_lock failed [%d]",
                 err );
        return;
    }

    to_be_removed = get_previous( fifo->first, fifo->index );
    previous = get_previous( fifo->first, to_be_removed );

    if (!previous) {
        /* The elem to be removed is the first one */
        fifo->first = fifo->first->next;
    } else {
        previous->next = fifo->index;
    }

    /* update the last pointer */
    if (to_be_removed == fifo->last) {
        fifo->last = previous;
    }

    err = pthread_mutex_unlock( &fifo->mutex );
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "pthread_mutex_unlock failed [%d]",
                 err );
    }
}

int
fifo_get_nb_elems( fifo_t *fifo )
{
    fifo_elem_t *cur;
    int result;

    cur = fifo->first;
    result = 0;

    while (cur) {
        ++result;
        cur = cur->next;
    }

    return(result);
}

int
fifo_is_empty( fifo_t *fifo )
{
    return( fifo->first!=NULL ? 0 : 1 );
}
