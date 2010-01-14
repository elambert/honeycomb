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



#include <jni.h>        /* Standard native method stuff */
#include "statfs.h"     /* Generated earlier */
#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <sys/vfs.h>
#include <sys/time.h>
#include <sys/statvfs.h>
#include <syslog.h>

void setLongField(JNIEnv *env, jclass *cls, jobject *obj, const char *fld,
                  long val) {
    (*env)->SetLongField(env, *obj, 
                         (*env)->GetFieldID(env, *cls, fld, "J"), 
                         val);
}

/*
 * Class:     com_sun_honeycomb_util_posix_StatFS
 * Method:    statfs64
 * Signature: (Ljava/lang/String;Lcom/sun/honeycomb/util/posix/StatFS$Struct;)V
 */
JNIEXPORT void JNICALL Java_com_sun_honeycomb_util_posix_StatFS_statfs64
(JNIEnv * env, jclass class, jstring path, jobject statStruct) {
    struct statvfs stbuf;
    char err_msg[256];
    
    const char *pathcstr = (*env)->GetStringUTFChars(env, path, 0); 
    
    // Solaris' statfs is 64-bit by default
    int result = statvfs(pathcstr, &stbuf);
    
    if(result < 0) {
        /* Construct an error message before deallocating pathcstr */
        extern int errno;
        snprintf(err_msg, sizeof(err_msg), "\"%s\": %s",
                 pathcstr, strerror(errno));
    }

    (*env)->ReleaseStringUTFChars(env, path, pathcstr);

    if (result < 0) {
        openlog("statfs", LOG_CONS, LOG_USER);
        syslog(LOG_ERR, err_msg);
        closelog();

        jclass newExcCls = 
            (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        if(newExcCls == 0) {
            return;
        }
        (*env)->ThrowNew(env, newExcCls, err_msg);
        return;
    }
    
    jclass cls = (*env)->GetObjectClass(env, statStruct);
    if(cls == 0) {
        jclass newExcCls = 
            (*env)->FindClass(env, "java/lang/ClassNotFoundException");
        if(newExcCls == 0) {
            return;
        }
        (*env)->ThrowNew(env, newExcCls, "Failed to find stat struct class");
        return;
    }
    
    //setLongField(env, &cls, &statStruct, "f_type", stbuf.f_type); 
    setLongField(env, &cls, &statStruct, "f_bsize", stbuf.f_frsize); 
    setLongField(env, &cls, &statStruct, "f_blocks", stbuf.f_blocks); 
    setLongField(env, &cls, &statStruct, "f_bfree", stbuf.f_bfree); 
    setLongField(env, &cls, &statStruct, "f_bavail", stbuf.f_bavail); 
    setLongField(env, &cls, &statStruct, "f_files", stbuf.f_files); 
    setLongField(env, &cls, &statStruct, "f_ffree", stbuf.f_ffree); 
    setLongField(env, &cls, &statStruct, "f_namelen", stbuf.f_namemax); 
}

/**********************************************************************/
/*
 * Class:     com_sun_honeycomb_util_posix_StatFS
 * Method:    getCTime
 * Signature: (Ljava/lang/String;)[C
 */
JNIEXPORT jlong JNICALL Java_com_sun_honeycomb_util_posix_StatFS_getCTime
(JNIEnv *env, jclass class, jstring file) {
    jlong ret;
    struct stat stbuf;
    
    const char *fileName = (*env)->GetStringUTFChars(env, file, 0); 
    if (fileName == NULL) {
        return -1;
    }
    if (stat(fileName, &stbuf) == -1) {
        fprintf(stderr, "getCTime: can't access %s\n", fileName);
        ret = -1;
    } else {
        ret = (jlong) stbuf.st_ctime;
    }
    (*env)->ReleaseStringUTFChars(env, file, fileName);
    return ret;
}

/*
 * Class:     com_sun_honeycomb_util_posix_StatFS
 * Method:    getInodeNumber
 * Signature: (Ljava/lang/String;)[C
 */
JNIEXPORT jlong JNICALL Java_com_sun_honeycomb_util_posix_StatFS_getInodeNumber
(JNIEnv *env, jclass class, jstring file) {
    jlong ret;
    struct stat stbuf;
    
    const char *fileName = (*env)->GetStringUTFChars(env, file, 0); 
    if (fileName == NULL) {
        return -1;
    }
    if (stat(fileName, &stbuf) == -1) {
        fprintf(stderr, "getInodeNumber: can't access %s\n", fileName);
        ret = -1;
    } else {
        ret = (jlong) stbuf.st_ino;
    }
    (*env)->ReleaseStringUTFChars(env, file, fileName);
    return ret;
}

/*
 * Class:     com_sun_honeycomb_util_posix_StatFS
 * Method:    createSymLink
 * Signature: (Ljava/lang/String;Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_sun_honeycomb_util_posix_StatFS_createSymLink
(JNIEnv *env, jclass class, jstring target , jstring link) {
    
    const char *linkName = (*env)->GetStringUTFChars(env, link, 0); 
    if (linkName == NULL) {
        return EINVAL;
    }
    const char *targetName = (*env)->GetStringUTFChars(env, target, 0); 
    if (targetName == NULL) {
        (*env)->ReleaseStringUTFChars(env, link, linkName);
        return EINVAL;
    }
    
    struct stat stbuf;
    if (lstat(linkName, &stbuf) == 0) {
        if (unlink(linkName) != 0) {
            fprintf(stderr, "createSymLink failed to unlink %s\n", linkName);
        }
    }
    
    int res = symlink(targetName, linkName);
    
    (*env)->ReleaseStringUTFChars(env, link, linkName);
    (*env)->ReleaseStringUTFChars(env, target, targetName);
    
    if (res != 0) {
        if (errno == 0) {
            /* should never happen */
            errno = ENOTSUP;
        }
        return errno;
    }
    return res;
}


