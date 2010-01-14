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



#include "fscache_priv.h"

static DB_ENV *dbEnv = NULL;
static DB *mainDB = NULL;

/* Secondary indexes */
static DB *parentIndex = NULL;  /* parent -> {children's paths} */
static DB *atimeIndex = NULL;   /* atime -> path */
static DB *hcIndex = NULL;      /* index -> path */

typedef union {
    DBC *cursor;
    DB_TXN *txn;
    jlong pointer;
} mem_pointer;

static long n_keys = 0;

/**********************************************************************
 *
 * Utils
 *
 **********************************************************************/

/* Network to host byte-order for 64-bit ints */
static jlong ntohll(void* buf)
{
    jlong val;
    unsigned char *p = (unsigned char*) buf;
    unsigned char *q = (unsigned char*) &val;

    for (int i = 0; i < 8; i++) {
#if   __BYTE_ORDER == __LITTLE_ENDIAN
        q[i] = p[7-i];
#else
        q[i] = p[i];
#endif
    }

    return val;
}

/* To compare two 64-bit timestamps */
static int atime_compare_fcn(DB *db, const DBT *key1, const DBT *key2)
{
    uint64_t t1 = ntohll(key1->data);
    uint64_t t2 = ntohll(key2->data);

    return (int)(t1 - t2);
}

/* Convert a DB pointer to a jlong for passing back to java */
jlong txn2long(DB_TXN* txn) {
    mem_pointer tmp;
    tmp.txn = txn;
    return tmp.pointer;
}
jlong cursor2long(DBC* cursor) {
    mem_pointer tmp;
    tmp.cursor = cursor;
    return tmp.pointer;
}

/* Convert a jlong to a DB pointer for converting from java */
DB_TXN* long2txn(jlong l) {
    mem_pointer tmp;
    tmp.pointer = l;
    return tmp.txn;
}
DBC* long2cursor(jlong l) {
    mem_pointer tmp;
    tmp.pointer = l;
    return tmp.cursor;
}


/**********************************************************************
 *
 * Index callbacks
 *
 **********************************************************************/

/* Extract atime from data and return it in result */
static int atime_callback(DB *db, const DBT *key, const DBT *data,
                          DBT *result)
{
    char* obj = (char *) data->data;
    int ftype = obj[FTYPE_OFFSET];

    if (ftype != DIRECTORYTYPE)
        return DB_DONOTINDEX;

    memset(result, 0, sizeof(DBT));
    result->data = &obj[ATIME_OFFSET];
    result->size = ATIME_LEN;

    char* name = get_string(key->data, key->size);
    log_debug("\t%-20s\tatime %lld\n", name, ntohll(result->data));
    free(name);

    return 0;
}

/* Extract OID from data and return it in result */
static int oid_callback(DB *db, const DBT *key, const DBT *data,
                        DBT *result)
{
    char* obj = (char *) data->data;
    int ftype = obj[FTYPE_OFFSET];

    if (ftype != FILELEAFTYPE)
        return DB_DONOTINDEX;

    memset(result, 0, sizeof(*result));
    result->data = &obj[OID_OFFSET];
    result->size = OID_LEN;

    char* name = get_string(key->data, key->size);
    char* oid = get_hex_string(result->data, result->size);
    log_debug("\t%-20s\tOID 0x%s\n", name, oid);
    free(name);
    free(oid);

    return 0;
}

/* Extract parent path from path (key) and return it in result */
static int parent_callback(DB *db, const DBT *key, const DBT *data,
                           DBT *result)
{
    char* obj = (char *) data->data;

    /* All paths start with a '/' */
    char* p = (char*) key->data;
    if (p[1] == 0)              /* root */
        return DB_DONOTINDEX;

    short namelen = ntohs(*(short*) &obj[NAMELEN_OFFSET]);
    if (namelen <= 0)
        return DB_DONOTINDEX;

    char* name = &obj[NAME_OFFSET];

    p = &name[namelen - 1];
    while (p > name && *p != '/')
        p--;
    memset(result, 0, sizeof(*result));
    result->data = name;
    result->size = p - name;

    char* keystr = get_string(key->data, key->size);
    char* parent = get_string(result->data, result->size);
    log_debug("\t%-20s\tparent %s\n", keystr, parent);
    free(parent);
    free(keystr);

    return 0;
}

/* Extract "HC index" */
static int hc_callback(DB *db, const DBT *key, const DBT *data,
                       DBT *result)
{
    char* obj = (char *) data->data;
    int ftype = obj[FTYPE_OFFSET];

    if (ftype == FILELEAFTYPE)
        return DB_DONOTINDEX;

    memset(result, 0, sizeof(*result));
    result->data = &obj[HC_INDEX_OFFSET];
    result->size = HC_INDEX_LEN;

    char* name = get_string(key->data, key->size);
    int index = ntohl(* (int *) result->data);
    log_debug("\t%-20s\tindex %d\n", name, ftype, index);
    free(name);

    return 0;
}


/**********************************************************************
 *
 * Helpers
 *
 **********************************************************************/


static jlong doCreateCursor(int ctype)
{
    DB* index = NULL;
    DBC* cursor = NULL;

    switch (ctype) {
    case CURSOR_MAIN: index = mainDB; break;
    case CURSOR_CHILDREN: index = parentIndex; break;
    case CURSOR_ATIME: index = atimeIndex; break;
    }

    int err = index->cursor(index, NULL, &cursor, 0);
    if (err) {
        log_err("Failed to create a cursor: %s\n", db_strerror(err));
        return 0;
    }

    jlong c = cursor2long(cursor);
    log_debug("Cursor 0x%llx allocated\n", c);
    return c;
}


static void doFreeResources(jlong c)
{
    DBC* cursor = long2cursor(c);

    log_debug("Cursor 0x%llx freed\n", c);

    cursor->c_close(cursor);
}

static int doCursorGet(void* bytes, int length, DBT* data,
                       jlong c, int first)
{
    int err;
    int flags = DB_NEXT;

    DBT key;
    memset(&key, 0, sizeof(key));
    memset(data, 0, sizeof(DBT));

    DBC* cursor = long2cursor(c);

    if (bytes != NULL) {
        key.data = bytes;
        key.size = length;
   
        if (first)
            flags = DB_SET;
        else
            flags = DB_NEXT_DUP;
    }

    err = cursor->c_get(cursor, &key, data, flags);

    switch (err) {
    case ENOENT:
        log_debug("    doCursorGet: 0x%llx ENOENT\n", c);
        break;

    case 0:
    case DB_NOTFOUND:
        break;

    default:
        log_err("    doCursorGet: 0x%llx error %d, %s\n",
                c, err, db_strerror(err));
    }

    return err;
}

/*
 * From the sleepycat docs: "For the Btree Access Method, the number
 * of keys in the database. If the DB_FAST_STAT flag is not specified,
 * the count will be exact. Otherwise, the count will be the last
 * saved value unless it has never been calculated, in which case it
 * will be 0."
 */
static int get_nkeys(long *n, int flags)
{
    int err;

    if (n == 0)
        return -1;

    DB_BTREE_STAT* st;
    if ((err = mainDB->stat(mainDB, (void *)&st, flags)) != 0)
        return err;

    *n = st->bt_nkeys;

    free(st);
    return 0;
}

/**********************************************************************
 *
 * JNI routines implementation
 *
 **********************************************************************/

JNIEXPORT jint JNICALL
Java_com_sun_honeycomb_fscache_BDBNativeFileCache_doInit
(JNIEnv *env, jobject this, jstring _path, jboolean useSyslog)
{
    int err = 0;
    int db_open_flags = DB_CREATE | DB_THREAD;
  
    const char *path = (*env)->GetStringUTFChars(env, _path, NULL);

    if (!useSyslog)
        set_logging_options(LOG_DEBUG, stderr);

    err = db_env_create(&dbEnv, 0);
    if (err) {
        log_err("libfscache initialization error (db_env_create) [%s]\n",
                db_strerror(err));
    }

    if (!err) {
        err = dbEnv->set_tmp_dir(dbEnv, path);
        if (err) {
            log_err("libfscache initialization error (DBENV->set_tmp_dir) " 
                    "[%s]\n", db_strerror(err));
        }
    }

    if (!err) {
        int env_flags = DB_INIT_MPOOL | DB_CREATE | DB_THREAD |
            DB_PRIVATE | DB_INIT_TXN;

        if ((err = dbEnv->open(dbEnv, (char*)path, env_flags, 0)) != 0)
            log_err("libfscache initialization error (DBENV->open) [%s]\n",
                    db_strerror(err));
    }


    // Database creation...
    if (!err) {
        err = db_create(&mainDB, dbEnv, 0);

        if (!err) {
            err = db_create(&parentIndex, dbEnv, 0);
        }
        if (!err) {
            err = parentIndex->set_flags(parentIndex, DB_DUPSORT);
        }

        if (!err) {
            err = db_create(&atimeIndex, dbEnv, 0);
        }

        if (!err) {
            err = atimeIndex->set_bt_compare(atimeIndex, &atime_compare_fcn);
        }

        if (!err) {
            err = atimeIndex->set_flags(atimeIndex, DB_DUPSORT);
        }
        if (err) {
            log_err("libfscache initialization error (db_create) [%s]\n",
                    db_strerror(err));
        }
    }
  
    // Database open 
    if (!err) {
        err = mainDB->open(mainDB,
                           NULL, NULL, NULL,
                           DB_BTREE, db_open_flags, 0);
        if (err) {
            log_err("libfscache initialization error (DB->open) [%s]\n",
                    db_strerror(err));
        }
    }
    if (!err) {
        err = parentIndex->open(parentIndex,
                                NULL, NULL, NULL,
                                DB_BTREE, db_open_flags, 0);
        if (err) {
            log_err("libfscache initialization error (DB->open) [%s]\n",
                    db_strerror(err));
        }
    }
    if (!err) {
        err = atimeIndex->open(atimeIndex, NULL, NULL, NULL,
                               DB_BTREE, db_open_flags, 0);
        if (err) {
            log_err("libfscache initialization error (DB->open) [%s]\n",
                    db_strerror(err));
        }
    }
    
    // Setting for secondary index.
    if (!err) {
        if (!err) {
            err = mainDB->associate(mainDB, NULL, parentIndex,
                                    parent_callback, 0);
        }
        if (err) {
            log_err("libfscache initialization error (DB->associate) [%s]\n",
                    db_strerror(err));
        }
    }
    if (!err) {
        err = mainDB->associate(mainDB, NULL, atimeIndex,
                                atime_callback, 0);
        if (err) {
            log_err("libfscache initialization error (DB->associate) [%s]\n",
                    db_strerror(err));
        }
    }
    if (err) {
        log_err("libfscache initialization error (DB->associate) [%s]\n",
                db_strerror(err));
    }

    (*env)->ReleaseStringUTFChars(env, _path, path);

    if (err == 0) {
        log_info("The fscache library has been initialized [%d]\n", err);
    } else {
        log_err("The fscache library has been initialized [%d]\n", err);      
    }
    return(err);
}


JNIEXPORT jboolean JNICALL
Java_com_sun_honeycomb_fscache_BDBNativeFileCache_doDelEntry
(JNIEnv *env, jobject this, jlong transaction, jbyteArray path)
{
    int err = 0;
    int pathLength = (*env)->GetArrayLength(env, path);
    jbyte *pathBytes = (*env)->GetByteArrayElements(env, path, NULL);

    DB_TXN *txn = long2txn(transaction);

    DBT key;
    memset(&key, 0, sizeof(key));
    key.data = pathBytes;
    key.size = pathLength;

    char* c_path = get_string(pathBytes, pathLength);
    log_debug("Remove %s\n", c_path);
    free(c_path);

    int flags = 0;    
  
    err = mainDB->del(mainDB, txn, &key, flags);
  
    (*env)->ReleaseByteArrayElements(env, path, pathBytes, JNI_ABORT);

    if (err == 0) {
        n_keys--;
        log_info("  now %d entries\n", n_keys);
        return JNI_TRUE;
    }

    jclass dbException = 
        (*env)->FindClass(env, "com/sleepycat/db/DbException");
    (*env)->ThrowNew(env, dbException, db_strerror(err));
    return JNI_FALSE;
}

int doAddEntry(JNIEnv *env, jobject this,
               jlong transaction, jbyteArray path, jbyteArray file,
               int overwrite)
{
    int pathLength = (*env)->GetArrayLength(env, path);
    int fileLength = (*env)->GetArrayLength(env, file);

    DB_TXN *txn = long2txn(transaction);

    jbyte *pathBytes = (*env)->GetByteArrayElements(env, path, NULL);
    jbyte *fileBytes = (*env)->GetByteArrayElements(env, file, NULL);

    DBT key, data;
    int err = 0;
    int flags = overwrite ? 0 : DB_NOOVERWRITE;
    jboolean result = JNI_TRUE;
  
    char* c_path = get_string(pathBytes, pathLength);
    log_debug("Insert %s (%d; now %d)\n", c_path, overwrite, n_keys);
    free(c_path);

    memset(&key, 0, sizeof(key));
    memset(&data, 0, sizeof(data));

    key.data = pathBytes;
    key.size = pathLength;

    data.data = fileBytes;
    data.size = fileLength;

    err = mainDB->put(mainDB, txn, &key, &data, flags);
  
    (*env)->ReleaseByteArrayElements(env, path, pathBytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, file, fileBytes, JNI_ABORT);

    if (err == 0)
        return result;
  
    if (overwrite || err != DB_KEYEXIST) {
        log_err("\tinsert: %s\n", db_strerror(err));

        jclass dbException =
            (*env)->FindClass(env, "com/sleepycat/db/DbException");
        (*env)->ThrowNew(env, dbException, db_strerror(err));
    }

    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_sun_honeycomb_fscache_BDBNativeFileCache_doAddEntry
(JNIEnv *env, jobject this,
 jlong transaction, jbyteArray path, jbyteArray file)
{
    int rc = doAddEntry(env, this, transaction, path, file, 0);
    if (rc)
        n_keys++;
    return rc;
}

JNIEXPORT jboolean JNICALL
Java_com_sun_honeycomb_fscache_BDBNativeFileCache_doUpdateEntry
(JNIEnv *env, jobject this,
 jlong transaction, jbyteArray path, jbyteArray file)
{
    return doAddEntry(env, this, transaction, path, file, 1);
}

JNIEXPORT jbyteArray JNICALL
Java_com_sun_honeycomb_fscache_BDBNativeFileCache_doResolvePath
(JNIEnv *env, jobject this, jbyteArray path)
{
    DBT key, data;
    jbyte *bytes = (*env)->GetByteArrayElements(env, path, NULL);
    int length = (*env)->GetArrayLength(env, path);
    int err = 0;
    jbyteArray result = NULL;

    memset(&key, 0, sizeof(key));
    memset(&data, 0, sizeof(data));
    key.data = bytes;
    key.size = length;
    data.flags = DB_DBT_MALLOC;

    err = mainDB->get(mainDB, NULL, &key, &data, 0);

    char* c_path = get_string(bytes, length);

    (*env)->ReleaseByteArrayElements(env, path, bytes, JNI_ABORT);
    bytes = NULL;

    if (err != 0) {
        if (err != DB_NOTFOUND)
            log_err("Fetch \"%s\" failed: %s\n", c_path,
                    db_strerror(err));
        free(c_path);
        return NULL;
    }
  
    result = (*env)->NewByteArray(env, data.size);
    (*env)->SetByteArrayRegion(env, result, 0, data.size, data.data);
  
    free(data.data);
    free(c_path);

    return result;
}

 

JNIEXPORT jlong JNICALL Java_com_sun_honeycomb_fscache_BDBNativeFileCache_createTransaction
(JNIEnv *env, jobject this)
{
    mem_pointer txn;
    int err = 0;
  
    txn.txn = NULL;

    err = dbEnv->txn_begin(dbEnv, NULL, &txn.txn, DB_TXN_NOSYNC);
    if (err) {
        jclass dbException = (*env)->FindClass(env, "com/sleepycat/db/DbException");
        (*env)->ThrowNew(env, dbException, db_strerror(err));
        return(0);
    }
  
    return(txn.pointer);
}
 
JNIEXPORT void JNICALL Java_com_sun_honeycomb_fscache_BDBNativeFileCache_commitTransaction
(JNIEnv *env, jobject this, jlong t)
{
    mem_pointer txn;
    int err = 0;
  
    txn.pointer = t;
    err = txn.txn->commit(txn.txn, DB_TXN_NOSYNC);
    if (err) {
        jclass dbException =
            (*env)->FindClass(env, "com/sleepycat/db/DbException");
        (*env)->ThrowNew(env, dbException, db_strerror(err));
    }
}


JNIEXPORT jlong JNICALL
Java_com_sun_honeycomb_fscache_BDBNativeFileCache_getSize
(JNIEnv *env, jobject this)
{
    jlong ret = 0;
    int err;

    long n = 0L;

#if 1
    /*
     * We're counting all the puts and dels, so why do we need to
     * query the DB?
     */
    n = n_keys;
#else
    /* Basically returns the same value are the last calculateSize() */
    if ((err = get_nkeys(&n, DB_FAST_STAT)) != 0) {
        jclass dbException =
            (*env)->FindClass(env, "com/sleepycat/db/DbException");
        (*env)->ThrowNew(env, dbException, db_strerror(err));
        return 0;
    }
#endif
    return (jlong) n;
}

JNIEXPORT jlong JNICALL
Java_com_sun_honeycomb_fscache_BDBNativeFileCache_calculateSize
(JNIEnv *env, jobject this)
{
    jlong ret = 0;
    int err;

    long n_keys = 0L;

    if ((err = get_nkeys(&n_keys, 0)) != 0) {
        jclass dbException =
            (*env)->FindClass(env, "com/sleepycat/db/DbException");
        (*env)->ThrowNew(env, dbException, db_strerror(err));
        return 0;
    }

    return (jlong) n_keys;
}


/**********************************************************************
 *
 * Cursor reads
 *
 **********************************************************************/

JNIEXPORT jbyteArray JNICALL
Java_com_sun_honeycomb_fscache_BDBNativeFileCache_doCursorGet
(JNIEnv *env, jobject this, jbyteArray path, jlong cursor, jboolean first)
{
    jbyteArray result = NULL;
    int rc = 0;
    static int cnt = 0;

    DBT data;
    memset(&data, 0, sizeof(DBT));

    void* bytes = 0;
    int length = 0;

    if (path != NULL) {
        bytes = (*env)->GetByteArrayElements(env, path, NULL);
        length = (*env)->GetArrayLength(env, path);
    }

    rc = doCursorGet(bytes, length, &data, cursor, first);

    if (bytes != NULL)
        (*env)->ReleaseByteArrayElements(env, path, bytes, JNI_ABORT);

    if (rc != 0 && rc != DB_NOTFOUND) {
        jclass dbException =
                (*env)->FindClass(env, "com/sleepycat/db/DbException");
        (*env)->ThrowNew(env, dbException, db_strerror(rc));
        return NULL;
    }

    if (data.size == 0)
        return NULL;

    result = (*env)->NewByteArray(env, data.size);
    (*env)->SetByteArrayRegion(env, result, 0, data.size, data.data);
    return result;
}

/**********************************************************************
 *
 * Cursor create/free JNI calls
 *
 **********************************************************************/

JNIEXPORT void JNICALL
Java_com_sun_honeycomb_fscache_DbcNativeCache_doFreeCursor
(JNIEnv *env, jobject this, jlong _cursor)
{
    doFreeResources(_cursor);
}

JNIEXPORT jlong JNICALL
Java_com_sun_honeycomb_fscache_DbcNativeCache_doCreateCursor
(JNIEnv *env, jobject this, jint cursor_type)
{
    return doCreateCursor(cursor_type);
}


/***********************************************************************/
