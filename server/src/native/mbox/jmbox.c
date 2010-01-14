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



/*
 * Component = Mailbox service
 * Synopsis  = java binding library
 */

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include "mbox.h"
#include "jmbox.h"


static const char* ioExceptionCls = "java/io/IOException";

/*
 * cached methods from ManagedService interface
 */
static jmethodID MID_init;
static jmethodID MID_start;
static jmethodID MID_stop;
static jmethodID MID_destroy;
static int initialized = 0;


/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    initIPC
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_initIPC(JNIEnv *env, 
                                              jclass notused, 
                                              jint   nodeid, 
                                              jint   link)
{
    return mb_init_mailboxes(nodeid, link);
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_initIDs(JNIEnv *env,
                                              jclass  cls,
                                              jclass  intf)
{
    initialized = 0;
    MID_init = (*env)->GetMethodID(env, intf, "doInit", "()V");
    if (MID_init == NULL) {
        return;
    }
    MID_start = (*env)->GetMethodID(env, intf, "doStart", "()V");
    if (MID_start == NULL) {
        return;
    } 
    MID_stop = (*env)->GetMethodID(env, intf, "doStop", "()V");
    if (MID_stop == NULL) {
        return;
    }
    MID_destroy = (*env)->GetMethodID(env, intf, "doDestroy", "()V");
    if (MID_destroy == NULL) {
        return;
    }
    initialized = 1;
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    init
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_init(JNIEnv *env, 
                                           jclass notused, 
                                           jstring mboxTag)
{
    const char *name;
    mb_id_t     mbId;

    if (!initialized) {
        /*
         * Something went wrong - trows an exception
         */
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, "Mailboxes not initialized"); 
        }
    }
    /*
     * Get the mailbox tag and initializes the mailbox
     */
    name = (*env)->GetStringUTFChars(env, mboxTag, NULL);
    if (name == NULL) {
        return -1;
    }
    mbId = mb_init(name, NULL);
    (*env)->ReleaseStringUTFChars(env, mboxTag, name);

    if (mbId == MB_INVALID_ID) {
        /*
         * Something went wrong - trows an exception
         */
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno)); 
        }
    }
    return (jint) mbId;
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    create
 * Signature: (Ljava/lang/String;I)I
 */
JNIEXPORT jint JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_create(JNIEnv *env, 
                                             jclass notused, 
                                             jstring mboxTag, 
                                             jint size)
{
    const char *name;
    mb_id_t     mbId;

    /*
     * Get the mailbox tag and initializes the mailbox
     */
    name = (*env)->GetStringUTFChars(env, mboxTag, NULL);
    if (name == NULL) {
        return -1;
    }
    mbId = mb_create(name, size);
    (*env)->ReleaseStringUTFChars(env, mboxTag, name);

    if (mbId == MB_INVALID_ID) {
        /*
         * Something went wrong - trows an exception
         */
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno)); 
        }
    }
    return (jint) mbId;
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    write
 * Signature: (I[BII)V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_write(JNIEnv *env, 
                                            jclass notused, 
                                            jint mbId, 
                                            jbyteArray arr, 
                                            jint offset, 
                                            jint len)
{
    jbyte     *carr;
    mb_error_t ret;

    carr = (*env)->GetByteArrayElements(env, arr, NULL);
    if (carr == NULL) {
        return;
    } 
    /*
     * write to the mailbox
     */
    ret = mb_write((mb_id_t)mbId, carr, (off_t) offset, len);
    (*env)->ReleaseByteArrayElements(env, arr, carr, 0);

    if (ret == MB_ERROR) {
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    close
 * Signature: (I)V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_close(JNIEnv *env, 
                                            jclass notused, 
                                            jint mbId)
{
    /*
     * Close the mailbox
     */
    (void) mb_close((mb_id_t) mbId);
}


/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    sync
 * Signature: (I)V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_sync(JNIEnv *env, 
                                           jclass notused, 
                                           jint mbId)
{
    /*
     * broadcast this mailbox
     */
    mb_broadcast((mb_id_t)mbId);
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    size
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_size(JNIEnv *env, 
                                           jclass notused, 
                                           jint mbId)
{
    /*
     * Get the length of the mailbox
     */
    return mb_len((mb_id_t)mbId);
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    heartbeat
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_heartbeat(JNIEnv *env, 
                                                jclass notused, 
                                                jint mbId)
{
    mb_action_t action;

    if (mb_hbt((mb_id_t) mbId, &action) != MB_OK) {
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
        return ACT_VOID;
    }

    return (jint) action;
} 

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    get_state
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_get_1state(JNIEnv *env, 
                                                 jclass notused,
                                                 jint mbId)
{
    mb_state_t state;
    if (mb_getstate((mb_id_t) mbId, &state) != MB_OK) {
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
    return state;
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    set_state
 * Signature: (II)V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_set_1state(JNIEnv *env, 
                                                 jclass notused,
                                                 jint mbId, 
                                                 jint state)
{
    if (mb_setstate((mb_id_t)mbId, state) != MB_OK) {
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    get_expectedstate
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_get_1expectedstate(
                                          JNIEnv *env, 
                                          jclass notused, 
                                          jint mbId)
{
    mb_state_t state;
    if (mb_getexpectedstate((mb_id_t)mbId, &state) != MB_OK) {
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
    return state;
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    set_expectedstate
 * Signature: (II)V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_set_1expectedstate(
                                              JNIEnv *env, 
                                              jclass notused, 
                                              jint mbId, 
                                              jint state)
{
    if (mb_setexpectedstate((mb_id_t)mbId, state) != MB_OK) {
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    exists
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_exists(JNIEnv *env, 
                                             jclass notused, 
                                             jstring tag)
{
    int ret;
    const char *name;
    name = (*env)->GetStringUTFChars(env, tag, NULL);
    if (name == NULL) {
        return JNI_FALSE;
    }
    ret = mb_check(name);
    (*env)->ReleaseStringUTFChars(env, tag, name);
    if (ret != MB_OK) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    disable_node
 * Signature: (I)V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_disable_1node(
                                             JNIEnv *env,
                                             jclass notused, 
                                             jint nodeid)
{
    int ret = mb_disable_node(nodeid);
    if (ret != MB_OK) {
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    publish
 * Signature: ()V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_publish(JNIEnv *env, 
                                              jclass cls)
{
    int ret = mb_net_publish();
    if (ret == MB_ERROR) {
       /*
        * Something wrong - generate an exception
        */
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    copyout
 * Signature: ([BI)V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_copyout(JNIEnv *env, 
                                              jclass cls, 
                                              jbyteArray arr,
                                              jint       len)
{
    jbyte     *carr;
    mb_error_t ret;

    carr = (*env)->GetByteArrayElements(env, arr, NULL);
    if (carr == NULL) {
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    } 
    /*
     * update the local view of the mailbox
     */
    ret = mb_net_copyout((unsigned char*)carr, len);
    (*env)->ReleaseByteArrayElements(env, arr, carr, 0);

    if (ret == MB_ERROR) {
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_Mailbox
 * Method:    copyin
 * Signature: (Ljava/lang/String;)[B
 */
JNIEXPORT jbyteArray JNICALL 
Java_com_sun_honeycomb_cm_ipc_Mailbox_copyin(JNIEnv *env,
                                             jclass class, 
                                             jstring tag)
{
    const char* name;
    mb_id_t     mbid;
    jbyteArray  carr = NULL;
    mb_error_t  ret = MB_ERROR;

    name = (*env)->GetStringUTFChars(env, tag, NULL);
    if (name == NULL) {
        return NULL;
    }
    mbid = mb_open(name);
    if (mbid != MB_INVALID_ID) {
        size_t len = mb_net_len(mbid);
        if (len > 0) {
            carr = (*env)->NewByteArray(env, len);
            if (carr != NULL) {
                unsigned char* buf = malloc(len);
                if (buf != NULL) {
                    ret = mb_net_copyin(mbid, buf, len);
                    if (ret == MB_OK) {
                        (*env)->SetByteArrayRegion(env, carr, 0, len, 
						  (const signed char*) buf);
                    }
                    free(buf);
                }
            }
        }
        mb_close(mbid);
    }
    (*env)->ReleaseStringUTFChars(env, tag, name);
    if (ret != MB_OK) {
        (*env)->DeleteLocalRef(env, carr);
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
    return carr;
}

/** MailboxReader **/

/*
 * Class:     com_sun_honeycomb_cm_MailboxReader
 * Method:    init
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL 
Java_com_sun_honeycomb_cm_ipc_MailboxReader_init(JNIEnv *env,
                                                 jobject obj, 
                                                 jstring mboxTag)
{
    const char* name;
    mb_id_t     mbId;

    /*
     * Get the mailbox tag and open the mailbox
     */
    name = (*env)->GetStringUTFChars(env, mboxTag, NULL);
    if (name == NULL) {
        return -1;
    }
    mbId = mb_open(name);
   (*env)->ReleaseStringUTFChars(env, mboxTag, name);

   if (mbId == MB_INVALID_ID) { 
       /*
        * Something wrong - generate an exception
        */
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
    return (jint) mbId;
}

/*
 * Class:     com_sun_honeycomb_cm_MailboxReader
 * Method:    read
 * Signature: (I[BII)V
 */
JNIEXPORT jint JNICALL 
Java_com_sun_honeycomb_cm_ipc_MailboxReader_read (JNIEnv    *env, 
                                                  jobject    obj,
                                                  jint       mbId,
                                                  jbyteArray arr, 
                                                  jint       offset,
                                                  jint       len)
{
    jbyte     *carr;
    mb_error_t ret;
    int        uid;

    carr = (*env)->GetByteArrayElements(env, arr, 0);
    /*
     * Read the content of the mailbox
     */
    ret = mb_read((mb_id_t)mbId, carr, (off_t)offset, len, &uid);
    (*env)->ReleaseByteArrayElements(env, arr, carr, 0);

    if (ret != MB_OK) {
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
    return uid;
}

/*
 * Class:     com_sun_honeycomb_cm_MailboxReader
 * Method:    size
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL 
Java_com_sun_honeycomb_cm_ipc_MailboxReader_size(JNIEnv *env, 
                                                 jobject obj,
                                                 jint    mbId)
{
    /*
     * Get the length of the mailbox
     */
    return mb_len((mb_id_t)mbId);
}

/*
 * Class:     com_sun_honeycomb_cm_MailboxReader
 * Method:    close
 * Signature: (I)V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ipc_MailboxReader_close(JNIEnv *env, 
                                                  jobject obj,
                                                  jint    mbId)
{
    /*
     * Close the mailbox
     */
    (void) mb_close((mb_id_t) mbId);
}

/*
 * Class:     com_sun_honeycomb_cm_MailboxReader
 * Method:    version
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL 
Java_com_sun_honeycomb_cm_ipc_MailboxReader_version(JNIEnv *env, 
                                                    jobject obj, 
                                                    jint    mbId)
{
    int version;
    
    int ret = mb_getversion((mb_id_t) mbId, &version);
    if (ret != MB_OK) {
        jclass cls = (*env)->FindClass(env, ioExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
    return version;    
}

/*
 * Class:     com_sun_honeycomb_cm_ipc_MailboxReader
 * Method:    isDisabled
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL 
Java_com_sun_honeycomb_cm_ipc_MailboxReader_isDisabled (JNIEnv * env,
                                                        jobject obj, 
                                                        jint mbId)
{
    mb_state_t state;
    int ret = mb_getstate((mb_id_t) mbId, &state);
    if (ret == MB_ERROR) {
        return JNI_TRUE;
    }
    if (state == SRV_DISABLED) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}
