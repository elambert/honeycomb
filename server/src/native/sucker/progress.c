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



#include <stdio.h>
#include <pthread.h>

#include "progress.h"

#define PROGRESS_LENGTH 40

struct {
    int max_steps;
    int nb_steps_done;
    int position;
    pthread_mutex_t lock;
} _global_data;

void
progress_init(int max_steps)
{
    int i;
    int err;

    if (max_steps == -1) {
        _global_data.max_steps = -1;
        return;
    }

    err = pthread_mutex_init(&_global_data.lock, NULL);
    if (err) {
        fprintf(stderr, "pthread_mutex_init failed [%d]\n",
                err);
        return;
    }

    _global_data.max_steps = max_steps;
    _global_data.nb_steps_done = 0;
    _global_data.position = 0;
    
    printf("[");
    for (i=0; i<PROGRESS_LENGTH; i++) {
        printf (" ");
    }
    printf("]\r[");
    fflush(stdout);
}


void
progress_destroy()
{
    if (_global_data.max_steps == -1) {
        return;
    }
    
    pthread_mutex_destroy(&_global_data.lock);
    
    printf("\n");
    _global_data.max_steps = -1;
}

void
progress_step()
{
    int position_goal;

    if (_global_data.max_steps == -1) {
        return;
    }

    pthread_mutex_lock(&_global_data.lock);

    _global_data.nb_steps_done++;
    position_goal = _global_data.nb_steps_done*PROGRESS_LENGTH/_global_data.max_steps;
    while (_global_data.position < position_goal) {
        printf(".");
        _global_data.position++;
        fflush(stdout);
    }

    pthread_mutex_unlock(&_global_data.lock);
}
