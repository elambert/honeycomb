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



#include <sys/types.h>
#include <string.h>
#include <stdio.h>
#include <db.h>
#include <strings.h>
#include <malloc.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>

#include "fhdb.h"

typedef struct {
    DB *database;
    int writeMode;
    pthread_mutex_t lock;
    time_t ctime;
    char *filename;
} _fhdb_data_t;

fhdb_data_t *
fhdb_init(char *filename, int writeMode)
{
    int error = 0;
    _fhdb_data_t *result = NULL;
    int flags = 0;
    struct stat st;

    result = (_fhdb_data_t*)malloc(sizeof(_fhdb_data_t));
    bzero(result, sizeof(_fhdb_data_t));
    
    if (pthread_mutex_init(&result->lock, NULL)) {
        free(result);
        return(NULL);
    }

    result->writeMode = writeMode;

    error = db_create(&result->database, NULL, 0);
    if (error) {
        pthread_mutex_destroy(&result->lock);
        free(result);
        return(NULL);
    }

    flags = DB_THREAD;
    if (writeMode) {
        flags |= DB_CREATE;
    } else {
        flags |= DB_RDONLY;
    }

    error = result->database->open(result->database, 
                                   NULL, 
                                   filename, 
                                   NULL,
                                   DB_BTREE, 
                                   flags, 
                                   0);
    if (error) {
        log_error("db->open failed [%d: %s]", error, db_strerror(error));
        pthread_mutex_destroy(&result->lock);
        free(result);
        return(NULL);
    }
    
    if (filename != NULL) {
        result->filename = strdup(filename);
        if (result->filename == NULL) {
            log_error("failed to allocate mem %d", errno);
            fhdb_destroy((fhdb_data_t*)result);
            return(NULL);
        }
    
        if (stat(result->filename, &st) != 0) {
            log_error("failed to stat %s: %d", result->filename, errno);
            fhdb_destroy((fhdb_data_t*)result);
            return(NULL);
        }
    }

    result->ctime = st.st_ctime;

    return((fhdb_data_t*)result);
}

void
fhdb_destroy(fhdb_data_t *_data)
{
    _fhdb_data_t *data = (_fhdb_data_t*)_data;

    pthread_mutex_destroy(&data->lock);

    if (data->database != NULL) {
        data->database->close(data->database, 0);
        data->database = NULL;
    }
    if (data->filename != NULL) {
        free(data->filename);
        data->filename = NULL;
    }
    free(data);
}

int
fhdb_isvalid(fhdb_data_t *_data)
{
    struct stat st;
    _fhdb_data_t *data = (_fhdb_data_t*)_data;

    int res = stat(data->filename, &st);
    if (res != 0 || st.st_ctime != data->ctime) {
        log_error("invalid %s: %s", data->filename,
                  (res != 0)? "db does not exists" : "db out of date");
        return 0;
    }
    return 1;
}

int
fhdb_set(fhdb_data_t *_db, fhdb_key_t *_key, fhandle3 *filehandle)
{
    _fhdb_data_t *db = (_fhdb_data_t*)_db;
    DBT key, data;
    int error = 0;

    bzero(&key, sizeof(DBT));
    bzero(&data, sizeof(DBT));

    key.data = _key;
    key.size = sizeof(fhdb_key_t);

    data.data = filehandle->fhandle3_val;
    data.size = filehandle->fhandle3_len;

    pthread_mutex_lock(&db->lock);

    error = db->database->put(db->database, NULL, &key, &data, 0);

    pthread_mutex_unlock(&db->lock);

    if (error) {
        log_error("db->put failed [%d: %s]", error, db_strerror(error));
    }

    return(error);
} 

void
fhdb_get(fhdb_data_t *_data,
         fhdb_key_t *_key,
         fhandle3 *filehandle)
{
    int error = 0;
    DBT key, data;
    _fhdb_data_t *dbdata = (_fhdb_data_t*)_data;

    filehandle->fhandle3_len = 0;

    bzero(&key, sizeof(DBT));
    bzero(&data, sizeof(DBT));

    key.data = _key;
    key.size = sizeof(fhdb_key_t);

    data.data = filehandle->fhandle3_val;
    data.ulen = FHSIZE3;
    data.flags = DB_DBT_USERMEM;

    error = dbdata->database->get(dbdata->database, NULL, &key, &data, 0);
    if (error) {
        log_error("db->get failed [%d: %s]", error, db_strerror(error));
        return;
    }
    
    filehandle->fhandle3_len = data.size;
}
