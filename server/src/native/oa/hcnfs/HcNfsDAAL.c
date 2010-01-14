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
#include <unistd.h>
#include <malloc.h>
#include <strings.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <errno.h>

#include "HcNfsDAAL.h"
#include "globals.h"
#include "fhdb.h"
#include "mount.h"
#include "nfs.h"
#include "nlm.h"
#include "handle_repos.h"

/*
 * Free the native handle.
 */
static void
free_data(native_data_t *data)
{
    if (data->filehandle.fhandle3_val) {
        free(data->filehandle.fhandle3_val);
        data->filehandle.fhandle3_val = NULL;
    }
    if (data->filename) {
        free(data->filename);
        data->filename = NULL;
    }
    if (data->clnt) {
        destroy_connection(data->clnt);
        data->clnt = NULL;
    }
    
    free(data);
}

/*
 * Flush write-cache buffer
 */
static int
flush_buffer(native_data_t *data, int synchronous)
{
    if (data->buffer_length == 0) {
        return(0);
    }
    
    int result = nfs_write(data->nodeid,
                           data->diskid,
                           &data->clnt,
                           &data->filehandle,
                           data->offset,
                           data->buffer_length,
                           data->buffer,
                           synchronous);
    if (result > 0) {
        data->offset += result;
        data->buffer_length = 0;
    }
    
    return(result);
}

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsMgmt
 * Method:    _buildDB
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_HcNfsMgmt__1buildDB
(JNIEnv *env, jobject this, jint nodeid, jint diskid)
{
    char tmpdb[MAXPATHLEN];
    char finaldb[MAXPATHLEN];
    struct stat st;
    
    hrep_encode_db_name(finaldb, sizeof(finaldb), nodeid, diskid);
    snprintf(tmpdb, sizeof(tmpdb), "%s.tmp", finaldb);
    
    if (stat(finaldb, &st) == 0) {
        /* db should not exists - remove it */
        unlink(finaldb);
    }
    if (stat(tmpdb, &st) == 0) {
        /* tmp db should not exists - remove it */
        unlink(tmpdb);
    }
    
    fhdb_data_t *db = fhdb_init(tmpdb, 1);
    if (db == NULL) {
        return(10);
    }
    
    int res = mount_disk(db, nodeid, diskid);
    fhdb_destroy(db);
    db = NULL;
    
    if (res == 0) {
        res = rename(tmpdb, finaldb);
    }
    if (res != 0) {
        unlink(tmpdb);
    }
    return(res);    
}

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsDAAL
 * Method:    _create
 * Signature: (IIILjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_NfsAccess__1create
(JNIEnv *env , jobject this, jint nodeid, jint diskid, jint mapid, jstring filename)
{
    native_data_t *result = NULL;
    
    result = (native_data_t*)malloc(sizeof(native_data_t));
    if (!result) {
        return(0);
    }
    
    bzero(result, sizeof(native_data_t));
    
    result->nodeid = nodeid;
    result->diskid = diskid;
    result->mapid = mapid;
    result->read_only = 0;
    result->offset = 0;
    result->buffer_length = 0;
    result->size = 0;
    
    result->filehandle.fhandle3_len = 0;
    result->filehandle.fhandle3_val = (char*)malloc(FHSIZE3);
    if (!result->filehandle.fhandle3_val) {
        free_data(result);
        return(0);
    }
    
    /* Get the filename */
    jsize length = (*env)->GetStringUTFLength(env, filename);
    result->filename = (char*)malloc(length+1);
    if (result->filename == NULL) {
        free_data(result);
        return(0);
    }
    const char *utf = (*env)->GetStringUTFChars(env, filename, NULL);
    bcopy(utf, result->filename, length);
    result->filename[length] = '\0';
    (*env)->ReleaseStringUTFChars(env, filename, utf);
    
    /* Create the connection */
    result->clnt = create_nfs_connection(result->nodeid, "tcp");
    if (!result->clnt) {
        free_data(result);
        return(0);
    }
    
    fhdb_data_t *db = hrep_get_db(result->nodeid, result->diskid);
    if (db == NULL) {
        free_data(result);
        return(0);
    }
    
    /* Create the initial file over NFS */
    int res = nfs_create(result->nodeid,
                         result->diskid,
                         &result->clnt,
                         db,
                         TMPMAP,
                         result->filename,
                         &result->filehandle);
    if (res != 0) {
        free_data(result);
        return(0);
    }
    
    return((jlong)result);
}

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsDAAL
 * Method:    _open
 * Signature: (IIILjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_NfsAccess__1open
(JNIEnv *env, jobject this, jint nodeid, jint diskid, jint mapid, jstring filename, jint read_only)
{
    native_data_t *result = NULL;
    
    result = (native_data_t*)malloc(sizeof(native_data_t));
    if (!result) {
        return(0);
    }
    bzero(result, sizeof(native_data_t));    
        
    result->nodeid = nodeid;
    result->diskid = diskid;
    result->mapid = mapid;
    result->read_only = read_only;
    result->offset = 0;
    result->buffer_length = 0;
    result->size = 0;
    
    result->filehandle.fhandle3_len = 0;
    result->filehandle.fhandle3_val = (char*)malloc(FHSIZE3);
    if (!result->filehandle.fhandle3_val) {
        free_data(result);
        return(0);
    }
    
    /* Get the filename */
    jsize length = (*env)->GetStringUTFLength(env, filename);
    result->filename = (char*)malloc(length+1);
    if (result->filename == NULL) {
        free_data(result);
        return(0);
    }
    
    const char *utf = (*env)->GetStringUTFChars(env, filename, NULL);
    bcopy(utf, result->filename, length);
    result->filename[length] = '\0';
    (*env)->ReleaseStringUTFChars(env, filename, utf);
    
    /* Create the connection */
    result->clnt = create_nfs_connection(nodeid, "tcp");
    if (!result->clnt) {
        free_data(result);
        return(0);
    }
    
    /* Get the filehandle */
    fhdb_data_t *db = hrep_get_db(nodeid, diskid);
    if (db == NULL) {
        free_data(result);
        return(0);
    }
    
    fhdb_key_t key;
    key.nodeid = nodeid;
    key.diskid = diskid;
    key.mapid = mapid;
    
    char fhandle_data[FHSIZE3];
    fhandle3 handle;
    handle.fhandle3_len = FHSIZE3;
    handle.fhandle3_val = fhandle_data;
    
    fhdb_get(db, &key, &handle);
    if (handle.fhandle3_len == 0) {
        free_data(result);
        return (0);
    }
    
    result->size = nfs_lookup(result->nodeid,
                              result->diskid,
                              &result->clnt,
                              &handle,
                              &result->filehandle,
                              result->filename);
    
    if (result->size == -1) {
        /* -1 == ENOENT */
        free_data(result);
        return -1;
    }
    if (result->size == -2) {
        free_data(result);
        return 0;
    }
    return((jlong)result);
}

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsDAAL
 * Method:    _delete
 * Signature: (IIILjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_NfsAccess__1delete
(JNIEnv *env, jobject this, jint nodeid, jint diskid, jint mapid, jstring filename) 
{
    char name[MAXPATHLEN];
    CLIENT *clnt;
    
    const char *utf = (*env)->GetStringUTFChars(env, filename, NULL);
    snprintf(name, sizeof(name), "%s", utf);
    (*env)->ReleaseStringUTFChars(env, filename, utf);
    
    /* Create the connection */
    clnt = create_nfs_connection(nodeid, "udp");
    if (clnt == NULL) {
        return(5);
    }
    
    fhdb_data_t *db = hrep_get_db(nodeid, diskid);
    if (db == NULL) {
        destroy_connection(clnt);
        return (10);
    }

    int res = nfs_delete(nodeid, diskid, &clnt, db, TMPMAP, name);
    res &= nfs_delete(nodeid, diskid, &clnt, db, mapid, name);
    destroy_connection(clnt);
    return res;
}

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsDAAL
 * Method:    _commit
 * Signature: (IIILjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_NfsAccess__1commit
(JNIEnv *env, jobject this, jint nodeid, jint diskid, jint mapid, jstring filename) 
{
    char name[MAXPATHLEN];
    CLIENT *clnt;
    
    const char *utf = (*env)->GetStringUTFChars(env, filename, NULL);
    snprintf(name, sizeof(name), "%s", utf);
    (*env)->ReleaseStringUTFChars(env, filename, utf);
    
    /* Create the connection */
    clnt = create_nfs_connection(nodeid, "udp");
    if (clnt == NULL) {
        return(5);
    }
    
    fhdb_data_t *db = hrep_get_db(nodeid, diskid);
    if (db == NULL) {
        destroy_connection(clnt);
        return (10);
    }
    
    int res = nfs_rename(nodeid, diskid, &clnt, db, TMPMAP, name, mapid, name);
    destroy_connection(clnt);
    return res;
}    

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsDAAL
 * Method:    _rollback
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_NfsAccess__1rollback
(JNIEnv *env, jobject this, jint nodeid, jint diskid, jint mapid, jstring filename) 
{
    char name[MAXPATHLEN];
    CLIENT *clnt;
    
    const char *utf = (*env)->GetStringUTFChars(env, filename, NULL);
    snprintf(name, sizeof(name), "%s", utf);
    (*env)->ReleaseStringUTFChars(env, filename, utf);
    
    /* Create the connection */
    clnt = create_nfs_connection(nodeid, "udp");
    if (clnt == NULL) {
        return(5);
    }
    
    fhdb_data_t *db = hrep_get_db(nodeid, diskid);
    if (db == NULL) {
        destroy_connection(clnt);
        return (10);
    }
    
    int res = nfs_rename(nodeid, diskid, &clnt, db, mapid, name, TMPMAP, name);
    destroy_connection(clnt);
    return res;
}

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsDAAL
 * Method:    _close
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_NfsAccess__1close
(JNIEnv * env, jobject this, jlong cookie, jint freeOnError)
{
    native_data_t *data = (native_data_t*)cookie;
    int res = 0;
    if (flush_buffer(data, 0) < 0) {
        res = 1;
    }
    if (!data->read_only) {
        if (nfs_commit(data->nodeid, data->diskid, &data->clnt, &data->filehandle)) {
            res += 2;
        }
    }
    if (res == 0 || freeOnError != 0) {
        free_data(data);
    }
    return res;
}

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsDAAL
 * Method:    _read
 * Signature: (JJLjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jint JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_NfsAccess__1read
(JNIEnv *env, jobject this, jlong cookie, jlong offset, jobject bytebuffer, jint position, jint remaining)
{
    native_data_t *data = (native_data_t*)cookie;
    int result = 0;
    
    char *bytes = (char*)(*env)->GetDirectBufferAddress(env, bytebuffer);

    result = nfs_read(data->nodeid,
                      data->diskid,
                      &data->clnt,
                      data->filehandle,
                      offset,
                      remaining,
                      bytes+position);
    
    if (result > 0) {
        data->offset = offset + result;
    }

    return(result);
}

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsDAAL
 * Method:    _write
 * Signature: (JJLjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jint JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_NfsAccess__1write
(JNIEnv *env, jobject this, jlong cookie, jlong offset, jobject bytebuffer, jint position, jint remaining)
{
    int result = 0;
    native_data_t *data = (native_data_t*)cookie;
    
    char *bytes = (char*)(*env)->GetDirectBufferAddress(env, bytebuffer);

    /* send over the network */
    result = nfs_write(data->nodeid,
                       data->diskid,
                       &data->clnt,
                       &data->filehandle,
                       offset,
                       remaining,
                       bytes+position,
                       0);
    return(result);
}

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsDAAL
 * Method:    _append
 * Signature: (JLjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jint JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_NfsAccess__1append
(JNIEnv *env, jobject this, jlong cookie, jobject bytebuffer, jint position, jint remaining)
{
    int result = 0;
    native_data_t *data = (native_data_t*)cookie;
    
    char *bytes = (char*)(*env)->GetDirectBufferAddress(env, bytebuffer);
    
    if ((data->buffer_length > 0) && (data->buffer_length+remaining > BUFFER_SIZE)) {
        result = flush_buffer(data, 0);
        if (result < 0) {
            return result;
        }
    }
    
    if (data->buffer_length+remaining < BUFFER_SIZE) {
        /* Put in the buffer, don't send over the network */
        bcopy(bytes+position, data->buffer+data->buffer_length, remaining);
        data->buffer_length += remaining;
        result = remaining;
    } else {
        /* Flush the buffer and send over the network */
        result = flush_buffer(data, 0);
        if (result < 0) {
            return result;
        }
        result = nfs_write(data->nodeid, 
                           data->diskid,
                           &data->clnt,
                           &data->filehandle,
                           data->offset,
                           remaining,
                           bytes+position,
                           0);
        if (result > 0) {
            data->offset += result;
        }
    }

    return(result);
}

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsDAAL
 * Method:    _length
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_NfsAccess__1length
(JNIEnv * env, jobject this, jlong cookie)
{
    native_data_t *data = (native_data_t*)cookie;
    jlong size;
    if (data->size == 0) {
        /* we are the only writer, so it's offset. */
        if (flush_buffer(data, 0) < 0) {
            size = -1;
        } else {
            size = data->offset;
        }
    } else {
        size = data->size;
    }
    return(size);
}

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsDAAL
 * Method:    _replace
 * Signature: (JLjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jint JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_NfsAccess__1replace
(JNIEnv * env, jobject this, jlong cookie, jobject bytebuffer, jint position, jint remaining)
{
    char filename[MAXPATHLEN];    
    char fhandle_data[FHSIZE3];

    native_data_t *data = (native_data_t*)cookie;
    
    char *bytes = (char*)(*env)->GetDirectBufferAddress(env, bytebuffer);
    
    fhdb_data_t *db = hrep_get_db(data->nodeid, data->diskid);
    if (db == NULL) {
        return (10);
    }
    
    /* Create the replaced file over NFS */
    fhandle3 filehandle;
    filehandle.fhandle3_len = FHSIZE3;
    filehandle.fhandle3_val = fhandle_data;    
    snprintf(filename, sizeof(filename), "%s.replace", data->filename);
    int res = nfs_create(data->nodeid,
                         data->diskid,
                         &data->clnt,
                         db,
                         TMPMAP,
                         filename,
                         &filehandle);
    if (res != 0) {
        return res;
    }
    
    offset3 offset = 0L;
    do {
        int length;
        if (remaining > BUFFER_SIZE) {
            length = BUFFER_SIZE;
        } else {
            length = remaining;
        }
        res = nfs_write(data->nodeid,
                        data->diskid,
                        &data->clnt,
                        &filehandle,
                        offset,
                        length,
                        bytes+position,
                        0);
        if (res <= 0) {
            break;
        }
        
        position += res;
        remaining -= res;
        offset += res;
        
    } while (remaining != 0);
        
    if (res >= 0) {
        res = nfs_commit(data->nodeid, data->diskid, &data->clnt, &filehandle);
    }
    if (res == 0) {
        res = nfs_rename(data->nodeid,
                         data->diskid,
                         &data->clnt, 
                         db, 
                         TMPMAP,
                         filename,
                         data->mapid,
                         data->filename);
    }
        
    if (res == 0) {
        /*
         * the curent file handle is now staled.
         * reset buffer cache and open mode.
         */
        data->buffer_length = 0;
        data->read_only = 1;

    } else {
        nfs_delete(data->nodeid,
                   data->diskid, 
                   &data->clnt, 
                   db, 
                   TMPMAP, 
                   filename);
    }
    return res;
}

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsDAAL
 * Method:    _trylock
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_NfsAccess__1trylock
(JNIEnv *env, jobject this, jlong cookie)
{
    native_data_t *data = (native_data_t*)cookie;
    CLIENT *clnt = NULL;
    int error;
    
    clnt = create_nlm_connection(data->nodeid, "tcp");
    if (!clnt) {
        return(1);
    }
    error = nfs_trylock(clnt, &data->filehandle, 1);
    destroy_connection(clnt);
    return(error);
}    

/*
 * Class:     com_sun_honeycomb_oa_daal_hcnfs_HcNfsDAAL
 * Method:    _unlock
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_sun_honeycomb_oa_daal_hcnfs_NfsAccess__1unlock
(JNIEnv *env, jobject this, jlong cookie)
{
    native_data_t *data = (native_data_t*)cookie;
    CLIENT *clnt = NULL;
    
    clnt = create_nlm_connection(data->nodeid, "tcp");
    if (!clnt) {
        return;
    }
    nfs_unlock(clnt, &data->filehandle, 1);
    destroy_connection(clnt);
}
