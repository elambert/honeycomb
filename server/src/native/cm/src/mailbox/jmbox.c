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
#include "com_sun_honeycomb_cm_ServiceListenerImpl.h"
#include "com_sun_honeycomb_cm_ServiceInfo.h"


static const char* managedServiceCls = "com/sun/honeycomb/cm/ManagedService";
static const char* mboxExceptionCls  = "com/sun/honeycomb/cm/MailboxException";

/*
 * cached methods from ManagedService interface
 */
static jmethodID MID_init;
static jmethodID MID_start;
static jmethodID MID_stop;
static jmethodID MID_destroy;


/*
 * Class:     com_sun_honeycomb_cm_ServiceListenerImpl
 * Method:    mb_init
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jint JNICALL
Java_com_sun_honeycomb_cm_ServiceListenerImpl_mb_1init(JNIEnv    *env, 
                                                       jobject    obj, 
                                                       jstring    mboxTag)
{
    static int  doinit = 1;
    const char *name;
    mb_id_t     mbId;

    if (doinit) {
        /*
         * Cache ManagedService interface callbacks
         */
        jclass cls = (*env)->FindClass(env, managedServiceCls);
        if (cls == NULL) {
            return -1;
        }
	MID_init = (*env)->GetMethodID(env, cls, "init", "()V");
        if (MID_init == NULL) {
            return -1;
        }
        MID_start = (*env)->GetMethodID(env, cls, "start", "()V");
        if (MID_start == NULL) {
            return -1;
        } 
        MID_stop = (*env)->GetMethodID(env, cls, "stop", "()V");
        if (MID_stop == NULL) {
            return -1;
        }
        MID_destroy = (*env)->GetMethodID(env, cls, "destroy", "()V");
        if (MID_destroy == NULL) {
            return -1;
        }
        doinit = 0;
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
        jclass cls = (*env)->FindClass(env, mboxExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno)); 
        }
    }
    return (jint) mbId;
}


/*
 * Class:     com_sun_honeycomb_cm_ServiceListenerImpl
 * Method:    mb_heartbeat
 * Signature: (ILcom/sun/honeycomb/cm/ManagedService;)V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ServiceListenerImpl_mb_1heartbeat(JNIEnv *env, 
                                                            jobject obj, 
                                                            jint    mbId, 
                                                            jobject service)
{
    mb_action_t action;


    if (mb_hbt((mb_id_t) mbId, &action) == MB_ERROR) {
        /*
         * Mailbox error - give up
         * The node mgr will detect lost of heartbeat
         */
    } else if (action != ACT_VOID) {
        /*
         * State change is requested - trigger appropriate callback
         */
        switch (action) {

        case ACT_INIT:
            (*env)->CallVoidMethod(env, service, MID_init, obj);
            break;

        case ACT_STOP:
            (*env)->CallVoidMethod(env, service, MID_stop);
            break;

        case ACT_START:
            (*env)->CallVoidMethod(env, service, MID_start);
            break;
 
        case ACT_DESTROY:
            mb_close((mb_id_t)mbId);
            (*env)->CallVoidMethod(env, service, MID_destroy);
            break;

        default:
            break;
        }
    }
} 

/*
 * Class:     com_sun_honeycomb_cm_ServiceListenerImpl
 * Method:    ready
 * Signature: ()V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ServiceListenerImpl_ready(JNIEnv *env, 
                                                    jobject obj)
{
    jclass   cls;
    jfieldID fid;
    jint     mbId;

    /*
     * Find mailbox id
     */ 
    cls = (*env)->GetObjectClass(env, obj);
    if (cls == NULL) {
        return;
    }
    fid = (*env)->GetFieldID(env, cls, "mboxId", "I");
    if (fid == NULL) {
        return;
    }
    mbId = (*env)->GetIntField(env, obj, fid);
    /*
     * Service reaches READY state
     */
    (void) mb_setstate((mb_id_t) mbId, SRV_READY);
}

/*
 * Class:     com_sun_honeycomb_cm_ServiceListenerImpl
 * Method:    running
 * Signature: ()V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ServiceListenerImpl_running(JNIEnv *env, 
                                                      jobject obj)
{
    jclass   cls;
    jfieldID fid;
    jint     mbId;

    /*
     * Find mailbox id
     */ 
    cls = (*env)->GetObjectClass(env, obj);
    if (cls == NULL) {
        return;
    }
    fid = (*env)->GetFieldID(env, cls, "mboxId", "I");
    if (fid == NULL) {
        return;
    }
    mbId = (*env)->GetIntField(env, obj, fid);
    /*
     * Service is running
     */
    (void) mb_setstate((mb_id_t) mbId, SRV_RUNNING);
}

/*
 * Class:     com_sun_honeycomb_cm_ServiceListenerImpl
 * Method:    disabled
 * Signature: ()V
 */
JNIEXPORT void 
JNICALL Java_com_sun_honeycomb_cm_ServiceListenerImpl_disabled(JNIEnv *env, 
                                                               jobject obj)
{
    jclass   cls;
    jfieldID fid;
    jint     mbId;

    /*
     * Find mailbox id
     */ 
    cls = (*env)->GetObjectClass(env, obj);
    if (cls == NULL) {
        return;
    }
    fid = (*env)->GetFieldID(env, cls, "mboxId", "I");
    if (fid == NULL) {
        return;
    }
    mbId = (*env)->GetIntField(env, obj, fid);
    /*
     * Service puts itself in DISABLED state
     */
    (void) mb_setstate((mb_id_t) mbId, SRV_DISABLED);
}

/*
 * Class:     com_sun_honeycomb_cm_ServiceListenerImpl
 * Method:    write
 * Signature: ([BI)V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ServiceListenerImpl_write(JNIEnv    *env, 
                                                    jobject    obj, 
                                                    jbyteArray arr, 
                                                    jint       offset)
{
    jclass     cls;
    jfieldID   fid;
    jint       mbId;
    jsize      len;
    jbyte     *carr;
    mb_error_t ret;

    /*
     * Find mailbox id and get a copy of the byte array
     */ 
    cls = (*env)->GetObjectClass(env, obj);
    if (cls == NULL) {
        return;
    }
    fid = (*env)->GetFieldID(env, cls, "mboxId", "I");
    if (fid == NULL) {
        return;
    }
    mbId = (*env)->GetIntField(env, obj, fid);
    len  = (*env)->GetArrayLength(env, arr);
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
        jclass cls = (*env)->FindClass(env, mboxExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
}

/*
 * Class:     com_sun_honeycomb_cm_ServiceInfo
 * Method:    mb_open
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL 
Java_com_sun_honeycomb_cm_ServiceInfo_mb_1open(JNIEnv *env, 
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
        jclass cls = (*env)->FindClass(env, mboxExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
    return (jint) mbId;
}

/*
 * Class:     com_sun_honeycomb_cm_ServiceInfo
 * Method:    close
 * Signature: ()V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ServiceInfo_close(JNIEnv *env, 
                                            jobject obj)
{
    jclass     cls;
    jfieldID   fid;
    jint       mbId;

    /*
     * Find mailbox id 
     */ 
    cls = (*env)->GetObjectClass(env, obj);
    if (cls == NULL) {
        return;
    }
    fid = (*env)->GetFieldID(env, cls, "mboxId", "I");
    if (fid == NULL) {
        return;
    }
    mbId = (*env)->GetIntField(env, obj, fid);
    /*
     * Close the mailbox
     */
    (void) mb_close((mb_id_t) mbId);
}

/*
 * Class:     com_sun_honeycomb_cm_ServiceInfo
 * Method:    isActive
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL 
Java_com_sun_honeycomb_cm_ServiceInfo_isActive(JNIEnv *env, 
                                               jobject obj)
{
    jclass     cls;
    jfieldID   fid;
    jint       mbId;
    mb_state_t state;

    /*
     * Find mailbox id 
     */ 
    cls = (*env)->GetObjectClass(env, obj);
    if (cls == NULL) {
        return JNI_FALSE;
    }
    fid = (*env)->GetFieldID(env, cls, "mboxId", "I");
    if (fid == NULL) {
        return JNI_FALSE;
    }
    mbId = (*env)->GetIntField(env, obj, fid);
    /*
     * Check state of mailbox. Return TRUE if running
     */
    if (mb_getstate((mb_id_t) mbId, &state) == MB_ERROR) {
        return JNI_FALSE;
    }
    return (state == SRV_RUNNING)? JNI_TRUE:JNI_FALSE;
}

/*
 * Class:     com_sun_honeycomb_cm_ServiceInfo
 * Method:    read
 * Signature: ([BI)V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ServiceInfo_read(JNIEnv    *env, 
                                           jobject    obj, 
                                           jbyteArray arr, 
                                           jint       offset)
{
    jclass     cls;
    jfieldID   fid;
    jint       mbId;
    jsize      len;
    jbyte     *carr;
    mb_error_t ret;

    /*
     * Find mailbox id and get a copy of the byte array
     */
    cls = (*env)->GetObjectClass(env, obj);
    if (cls == NULL) {
        return;
    }
    fid = (*env)->GetFieldID(env, cls, "mboxId", "I");
    if (fid == NULL) {
        return;
    }
    mbId = (*env)->GetIntField(env, obj, fid);
    len  = (*env)->GetArrayLength(env, arr);
    carr = (jbyte*) malloc(len);
    if (carr == NULL) { 
        jclass cls = (*env)->FindClass(env, mboxExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
        return;
    }
    /*
     * Read the content of the mailbox
     */
    ret = mb_read((mb_id_t)mbId, carr, (off_t)offset, len);
    if (ret == MB_OK) {
        (*env)->SetByteArrayRegion(env, arr, 0, len, carr);
    } else {
        jclass cls = (*env)->FindClass(env, mboxExceptionCls);
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, strerror(errno));
        }
    }
    free(carr);
}

/*
 * Class:     com_sun_honeycomb_cm_ServiceInfo
 * Method:    length
 * Signature: ()J
 */
JNIEXPORT jint JNICALL 
Java_com_sun_honeycomb_cm_ServiceInfo_length(JNIEnv *env, 
                                             jobject obj)
{
    jclass     cls;
    jfieldID   fid;
    jint       mbId;

    /*
     * Find mailbox id 
     */ 
    cls = (*env)->GetObjectClass(env, obj);
    if (cls == NULL) {
        return JNI_FALSE;
    }
    fid = (*env)->GetFieldID(env, cls, "mboxId", "I");
    if (fid == NULL) {
        return JNI_FALSE;
    }
    mbId = (*env)->GetIntField(env, obj, fid);
    /*
     * Get the length of the mailbox
     */
    return mb_len((mb_id_t)mbId);
}

/*
 * Class:     com_sun_honeycomb_cm_ServiceListenerImpl
 * Method:    sync
 * Signature: ()V
 */
JNIEXPORT void JNICALL 
Java_com_sun_honeycomb_cm_ServiceListenerImpl_sync(JNIEnv  *env, 
                                                   jobject obj)
{
    jclass      cls;
    jfieldID    fid;
    jint        mbId;

    /*
     * Find mailbox id 
     */ 
    cls = (*env)->GetObjectClass(env, obj);
    if (cls == NULL) {
        return;
    }
    fid = (*env)->GetFieldID(env, cls, "mboxId", "I");
    if (fid == NULL) {
        return;
    }
    mbId = (*env)->GetIntField(env, obj, fid);
    /*
     * broadcast this mailbox
     */
    mb_broadcast((mb_id_t)mbId);
}
