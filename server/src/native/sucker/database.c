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



#include <db.h>
#include <stdio.h>
#include <strings.h>
#include <sys/errno.h>

#include "database.h"

int
database_init(db_handle_t *handle,
              char *home,
              char *filename,
              int write_mode)
{
    int err, flags;

    bzero(handle, sizeof(db_handle_t));
    
    err = pthread_mutex_init(&handle->lock, NULL);
    if (err) {
        fprintf(stderr, "pthread_mutex_init failed [%d]\n",
                err);
        return(err);
    }
    
    err = db_env_create(&handle->env, 0);
    if (err) {
        fprintf(stderr, "db_env_create failed [%d]\n", err);
        database_close(handle);
        return(err);
    }
    
    err = handle->env->open(handle->env, home,
                            DB_INIT_LOCK | DB_INIT_MPOOL | DB_PRIVATE | DB_THREAD | DB_CREATE,
                            0);
    if (err) {
        fprintf(stderr, "DB_ENV->open failed [%d]\n",
                err);
        database_close(handle);
        return(err);
    }

    err = db_create(&handle->main, handle->env, 0);
    if (err) {
        fprintf(stderr, "db_create failed [%d]\n", err);
        database_close(handle);
        return(err);
    }

    flags = DB_THREAD;
    if (write_mode) {
        flags |= DB_CREATE | DB_EXCL;
    } else {
        flags |= DB_RDONLY;
    }
    
    err = handle->main->open(handle->main, NULL,
                             filename==NULL ? "main.db" : filename,
                             NULL, DB_BTREE,
                             flags, 0);
    if (err) {
        fprintf(stderr, "DB->open failed [%d]\n", err);
        if (err == EEXIST) {
            fprintf(stderr, "The database already exists\n");
        }
        database_close(handle);
        return(err);
    }

    return(0);
}

void
database_close(db_handle_t *handle)
{
    pthread_mutex_destroy(&handle->lock);

    if (handle->cursor) {
        handle->cursor->c_close(handle->cursor);
        handle->cursor = NULL;
    }
    
    if (handle->main) {
        handle->main->close(handle->main, 0);
        handle->main = NULL;
    }
    
    if (handle->env) {
        handle->env->close(handle->env, 0);
        handle->env = NULL;
    }
}

int
db_insert_record(db_handle_t *handle,
                 db_record_t *record)
{
    DBT key, data;
    int err;

    bzero(&key, sizeof(DBT));
    bzero(&data, sizeof(DBT));

    key.data = record->oid;
    key.size = sizeof(record->oid);
    
    data.data = record;
    data.size = sizeof(db_record_t);

    pthread_mutex_lock(&handle->lock);
    err = handle->main->put(handle->main, NULL, &key, &data, 0);
    pthread_mutex_unlock(&handle->lock);

    if (err) {
        fprintf(stderr, "DB->put failed for oid %s [%d]\n",
                record->oid, err);
        return(err);
    }

    return(0);
}

int
db_get_next(db_handle_t *handle,
            db_record_t *record)
{
    int err;
    DBT key, data;

    bzero(&key, sizeof(DBT));
    bzero(&data, sizeof(DBT));

    if (!handle->cursor) {
        err = handle->main->cursor(handle->main,
                                   NULL, &handle->cursor,
                                   DB_READ_UNCOMMITTED);
        if (err) {
            fprintf(stderr, "DB->cursor failed [%d]\n",
                    err);
            handle->cursor = NULL;
            return(-1);
        }
    }
    
    err = handle->cursor->c_get(handle->cursor,
                                &key, &data, DB_NEXT);
    if (err == DB_NOTFOUND) {
        // Last record
        handle->cursor->c_close(handle->cursor);
        handle->cursor = NULL;
        return(0);
    }
    
    if (err) {
        fprintf(stderr, "Failed to read the next record from the DB [%d]\n",
                err);
        return(-1);
    }
    
    bcopy(data.data, record, sizeof(db_record_t));
    return(1);
}

int
compare_records(db_record_t *a,
                db_record_t *b)
{
    int res;

    res = strcmp(a->oid, b->oid);
    if (res) {
        return(res);
    }

    // Same OID
    if (a->fragment < b->fragment) {
        return(-1);
    } else if (a->fragment > b->fragment) {
        return(1);
    } else {
        return(0);
    }
}

