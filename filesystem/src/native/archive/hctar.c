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
 * Glue between the archivers module and libarchive (it's named
 * hctar for historical reasons).
 */
#include <stdio.h>
#include <unistd.h>
#include <strings.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <fcntl.h>

#ifndef SDEBUG
#    include <jni.h>
#    include "hctar.h"

#    define getCstring(env, jstr) \
         ((*(env))->GetStringUTFChars(env, jstr, 0))
#    define freeCstring(env, jstr, s) \
         ((*(env))->ReleaseStringUTFChars((env), (jstr), (s)))

#else

#    define getCstring(env, jstr) (jstr)
#    define freeCstring(env, jstr, s)

#endif

/* Our tar header subset, internal use only */
typedef struct {
    char name[256];
    size_t size;
    off_t hdr_offset;
    off_t data_offset;
    uid_t uid;
    gid_t gid;
    mode_t mode;
    time_t atime;
    time_t mtime;
    int reg_file;
    int compressed;
} tar_header;

/**********************************************************************
 * libarchive glue
 **********************************************************************/

#define ARC_BLOCKSIZE 10240

#include <archive.h>
#include <archive_entry.h>

typedef struct archive* arc_handle_t;
typedef struct archive_entry* file_ptr_t;

static arc_handle_t arxive_new()
{
    arc_handle_t ar = archive_read_new();
    archive_read_support_compression_all(ar);
    archive_read_support_format_all(ar);
    return ar;
}

static void arxive_close(arc_handle_t ar)
{
    archive_read_finish(ar);
}

static int arxive_is_compressed(arc_handle_t ar)
{
    return archive_compression(ar) == ARCHIVE_COMPRESSION_NONE;
}

static int arxive_skip_data(arc_handle_t ar)
{
    return archive_read_data_skip(ar) == 0;
}

static arc_handle_t arxive_open(int fd)
{
    arc_handle_t ar;

    if ((ar = arxive_new()) == 0)
        return 0;

    if (llseek(fd, 0, SEEK_SET) < 0)
        return 0;

    if (archive_read_open_fd(ar, fd, ARC_BLOCKSIZE) < 0)
        return 0;

    return ar; 
}

static int arxive_is_open(arc_handle_t ar)
{
    return archive_client_data(ar) != 0;
}

static arc_handle_t arxive_open_null()
{
    return arxive_new();
}

static int arxive_start_stream(arc_handle_t ar, int fd)
{
    return archive_read_open_fd(ar, fd, ARC_BLOCKSIZE);
}

/* For sequentially stepping through all files in the archive */
static int arxive_get_next_header(arc_handle_t ar, tar_header* hdr)
{
    char buffer[ARC_BLOCKSIZE];
    off_t o = 0;
    size_t s = sizeof(buffer);
    const char* p = buffer;

    file_ptr_t entry;
    if (archive_read_next_header(ar, &entry) != ARCHIVE_OK)
        return -1;

    hdr->compressed = archive_compression(ar);
    hdr->size = archive_entry_size(entry);
    hdr->hdr_offset = archive_read_header_position(ar);
    hdr->data_offset = archive_read_data_position(ar);
    hdr->atime = archive_entry_atime(entry);
    hdr->mtime = archive_entry_mtime(entry);
    hdr->mode = archive_entry_mode(entry);
    hdr->uid = archive_entry_uid(entry);
    hdr->gid = archive_entry_gid(entry);
    hdr->reg_file = S_ISREG(hdr->mode);
    strncpy(hdr->name, archive_entry_pathname(entry), sizeof(hdr->name) - 1);

    return 0;
}

static int arxive_read_block(arc_handle_t ar, const void** chunkData, 
                             size_t* chunkSize, off_t* chunkOffset)
{
    return archive_read_data_block(ar, chunkData, chunkSize, chunkOffset);
}

static int arxive_read_data(arc_handle_t ar, unsigned char* buf, size_t len)
{
    return archive_read_data(ar, (void*)buf, len);
}

/**********************************************************************
 * JNI glue
 *
 *    private native long openArchive(FileDescriptor f);
 *    private native void getStream(long handle, Object parent);
 *    private native void startReading(long handle, long cookie);
 *    private native void closeArchive(long handle);
 *
 *    private native boolean compressed(long handle);
 *    private native HCXArchive.Stat nextHeader(long handle);
 *    private native FileChunk nextChunk(long handle);
 *    private native FileChunk readData(long handle, int maxBytes);
 *    private native boolean skipData(long handle);
 *
 **********************************************************************/

#define handle2jlong(h) ((jlong)(h))
#define jlong2handle(l) ((arc_handle_t)(l))

static jobject newFileDescriptor(JNIEnv* env, jclass cls, int fd_write);

/*
 * Class:	com.sun.honeycomb.archivers.LibArchive
 * Sig:		long openArchive(FileDescriptor fd)
 */
JNIEXPORT jlong JNICALL Java_com_sun_honeycomb_archivers_LibArchive_openArchive
(JNIEnv* env, jclass cls, jobject fd_object)
{
    arc_handle_t ar;
    int fd;
   
    if (fd_object == NULL)
        return handle2jlong(arxive_open_null());

    /*
     * WARNING! Undocumented: FileDescriptor has an int field
     * named "fd" that is the fd of the file
     */
    jclass fdClass = (*env)->GetObjectClass(env, fd_object);
    if (fdClass == 0)
        return 0;
    jfieldID field_fd = (*env)->GetFieldID(env, fdClass, "fd", "I");
    if (field_fd == 0)
        return 0;
    fd = (*env)->GetIntField(env, fd_object, field_fd);

    ar = arxive_open(fd);

    return handle2jlong(ar);
}

/*
 * Class:	com.sun.honeycomb.archivers.LibArchive
 * Sig:		void getStream(long handle, Object parent)
 */
JNIEXPORT jboolean JNICALL Java_com_sun_honeycomb_archivers_LibArchive_getStream
(JNIEnv* env, jclass cls, jlong handle, jobject parent)
{
    /* This can only be called for a yet-unopened archive object */

    arc_handle_t ar = jlong2handle(handle);
    if (arxive_is_open(ar))
        return 0;

    /*
     * Set up the pipe, and return the two ends; the read end as an fd
     * for use by libarchive, and the write end as a FileDescriptor
     * for the source of the archive. Return the two value to caller
     * by calling parent->setCookie(long cookie, FileDescriptor fd)
     */

    int fds[2];
    if (pipe(fds) < 0)
        return 0;

    jobject fd_obj = newFileDescriptor(env, cls, fds[1]);
    jlong cookie = fds[0];

    jclass parent_class = (*env)->GetObjectClass(env, parent);
    if (parent_class == 0)
        return 0;
    jmethodID callback =
        (*env)->GetMethodID(env, parent_class, "setCookie",
                            "(JLjava/io/FileDescriptor;)V");
    if (callback == 0)
        return 0;

    (*env)->CallVoidMethod(env, parent, callback, cookie, fd_obj);
    return 1;
}

/*
 * Class:	com.sun.honeycomb.archivers.LibArchive
 * Sig:		void startReading(long handle, long cookie)
 */
JNIEXPORT void JNICALL Java_com_sun_honeycomb_archivers_LibArchive_startReading
(JNIEnv* env, jclass cls, jlong handle, jlong cookie)
{
    int fd = (int) cookie;
#if 0
    fprintf(stderr, "    libarchive reads from fd %d\n", fd);
    unsigned char buffer[3000];
    int nread;
    while ((nread = read(fd, buffer, sizeof(buffer))) >= 0) {
        int i;
        if (nread == 0)
            break;
        fprintf(stderr, "[%d] ", nread);
        for (i = 0; i < nread; i+=2)
            fprintf(stderr, "%02x%02x", buffer[i+1], buffer[i]);
        fputs("\n", stderr);
    }
#else
    arxive_start_stream(jlong2handle(handle), fd);
#endif
}

/*
 * Class:	com.sun.honeycomb.archivers.LibArchive
 * Sig:		void closeArchive(long handle)
 */
JNIEXPORT void JNICALL Java_com_sun_honeycomb_archivers_LibArchive_closeArchive
(JNIEnv* env, jclass cls, jlong handle)
{
    arxive_close(jlong2handle(handle));
}

/*
 * Class:	com.sun.honeycomb.archivers.LibArchive
 * Sig:		boolean compressed(long handle)
 */
JNIEXPORT jboolean JNICALL Java_com_sun_honeycomb_archivers_LibArchive_compressed
(JNIEnv* env, jclass cls, jlong handle)
{
    return arxive_is_compressed(jlong2handle(handle));
}
 
/*
 * Class:	com.sun.honeycomb.archivers.LibArchive
 * Sig:		boolean skipData(long handle)
 */
JNIEXPORT jboolean JNICALL Java_com_sun_honeycomb_archivers_LibArchive_skipData
(JNIEnv* env, jclass cls, jlong handle)
{
    return arxive_skip_data(jlong2handle(handle));
}
 
/*
 * Class:	com.sun.honeycomb.archivers.LibArchive
 * Sig:		HCXArchive.Stat nextHeader(long handle)
 */
JNIEXPORT jobject JNICALL Java_com_sun_honeycomb_archivers_LibArchive_nextHeader
(JNIEnv* env, jclass cls, jlong h)
{
    jclass statClass;
    jmethodID constr;
    jobject result;

    jstring fname;
    jlong size, index, uid, gid, mtime, atime, mode;

    tar_header hdr;

    if (arxive_get_next_header(jlong2handle(h), &hdr) < 0)
        return 0;

    size = hdr.size;
    index = hdr.data_offset;
    uid = hdr.uid;
    gid = hdr.gid;
    atime = mtime = 1000 * (jlong)hdr.mtime;
    mode = hdr.mode;

    fname = (*env)->NewStringUTF(env, hdr.name);

    /* Get class */
    statClass =
        (*env)->FindClass(env, "com/sun/honeycomb/archivers/HCXArchive$Stat");
    if (statClass == 0)
        return 0;

    /* Get constructor */
    constr = (*env)->GetMethodID(env, statClass, "<init>",
                                 "(Ljava/lang/String;JJJJJJJ)V");
    if (constr == 0)
        return 0;

    /* Make a new object and return it */

    result = (*env)->NewObject(env, statClass, constr,
                               fname,
                               size, index, uid, gid, mtime, atime, mode);

    (*env)->DeleteLocalRef(env, statClass);
    return result;
}

/*
 * Class:	com.sun.honeycomb.archivers.LibArchive
 * Sig:		LibArchive.FileChunk nextChunk(long handle)
 */
JNIEXPORT jobject JNICALL Java_com_sun_honeycomb_archivers_LibArchive_nextChunk
(JNIEnv* env, jclass cls, jlong h)
{
    unsigned char* buf;

    jclass chunkClass;
    jmethodID constr;
    jobject result;

    arc_handle_t ar = jlong2handle(h);
    jbyteArray jb;
    jlong offset;

    size_t chunkSize;
    off_t chunkOffset;
    const void* chunkData;

    /* Get size of the block, and its offset */
    if (arxive_read_block(ar, &chunkData, &chunkSize, &chunkOffset) < 0)
        return 0;

    offset = chunkOffset;

    /* Allocate byte[] for data */
    if ((jb = (*env)->NewByteArray(env, chunkSize)) == 0)
        return 0;

    /* Copy data to byte array */
    buf = (unsigned char*) (*env)->GetByteArrayElements(env, jb, 0);
    memcpy(buf, chunkData, chunkSize);
    (*env)->ReleaseByteArrayElements(env, jb, (jbyte*) buf, 0);

    /* Get class */
    chunkClass =
        (*env)->FindClass(env, "com/sun/honeycomb/archivers/LibArchive$FileChunk");
    if (chunkClass == 0)
        return 0;
    
    /* Get constructor */
    constr = (*env)->GetMethodID(env, chunkClass, "<init>",
                                 /* I don't know why the LibArchive class is here */
                                 "(Lcom/sun/honeycomb/archivers/LibArchive;[BI)V");
    if (constr == 0)
        return 0;

    /* Make a new object and return it */

    result = (*env)->NewObject(env, chunkClass, constr, 0, jb, offset);

    (*env)->DeleteLocalRef(env, chunkClass);
    return result;
}

/*
 * Class:	com.sun.honeycomb.archivers.LibArchive
 * Sig:		LibArchive.FileChunk readData(long handle, int maxBytes)
 *
 * If using this interface, the caller must keep track of position in
 * the file -- this is strictly sequential, à la read(2)
 */
JNIEXPORT jobject JNICALL Java_com_sun_honeycomb_archivers_LibArchive_readData
(JNIEnv* env, jclass cls, jlong h, jint nbytes)
{
    unsigned char* buf;

    jclass chunkClass;
    jmethodID constr;
    jobject result;

    arc_handle_t ar = jlong2handle(h);
    jbyteArray jb;

    int nread;

    jb = (*env)->NewByteArray(env, nbytes);
    buf = (unsigned char*) (*env)->GetByteArrayElements(env, jb, 0);

    if ((nread = arxive_read_data(ar, buf, nbytes)) <= 0)
        return 0;

    if (nread == nbytes) {
        /* We're done */
        (*env)->ReleaseByteArrayElements(env, jb, (jbyte*) buf, 0);
    }
    else {
        /* Resize the byte array to be size nread */
        unsigned char* buf_2;
        jbyteArray jb_2;

        /* Create new array and copy in bytes */
        jb_2 = (*env)->NewByteArray(env, nread);
        buf_2 = (unsigned char*) (*env)->GetByteArrayElements(env, jb_2, 0);
        memcpy(buf_2, buf, nread);
        (*env)->ReleaseByteArrayElements(env, jb_2, (jbyte*) buf_2, 0);

        /* Deallocate old byte array */
        (*env)->ReleaseByteArrayElements(env, jb, (jbyte*) buf, 0);
        (*env)->DeleteLocalRef(env, jb);

        jb = jb_2;
    }

    /* Get class */
    chunkClass =
        (*env)->FindClass(env, "com/sun/honeycomb/archivers/LibArchive$FileChunk");
    if (chunkClass == 0)
        return 0;
    
    /* Get constructor */
    constr = (*env)->GetMethodID(env, chunkClass, "<init>",
                                 /* I don't know why the LibArchive class is here */
                                 "(Lcom/sun/honeycomb/archivers/LibArchive;[B)V");
    if (constr == 0)
        return 0;

    /* Make a new object and return it */

    result = (*env)->NewObject(env, chunkClass, constr, 0, jb);

    (*env)->DeleteLocalRef(env, jb);
    (*env)->DeleteLocalRef(env, chunkClass);
    return result;
}

/********************************************************************
 * Warning: HACK!
 *
 * We need to make a FileDescriptor out of fd_write so the caller can
 * make a FileOutputStream (isa OutputStream) from it. But
 * FileDescriptor does not have a constructor that takes an fd. How,
 * then, to do it?
 *
 * That's where the lousy hack comes in: first make a new
 * FileOutputStream("/dev/null"). Get its FileDescriptor object, and
 * inside it overwrite the field "fd" with fd_write. Now we have a
 * FileDescriptor that's hooked up to the write end of the pipe; make
 * a new FileOutputStream from it and voilà! We have an OutputStream
 * that's connected to libarchive.
 *
 ********************************************************************/

static jobject newFileDescriptor(JNIEnv* env, jclass cls, int fd_write)
{
    /* fos_ = FileOutputStream, fd_ = FileDescriptor */

    jstring s_devnull;

    jclass fos_class;
    jmethodID fos_constr;

    jobject fos_devnull;
    jmethodID fos_getFD;

    jclass fd_class;
    jobject fd_obj;
    jfieldID fd_fd;

    /* Make string for /dev/null */
    if ((s_devnull = (*env)->NewStringUTF(env, "/dev/null")) == 0)
        return 0;

    /* Get FileOutputStream class and constructor */
    if ((fos_class = (*env)->FindClass(env, "java/io/FileOutputStream")) == 0)
        return 0;

    if ((fos_constr = (*env)->GetMethodID(env, fos_class, "<init>",
                                          "(Ljava/lang/String;)V")) == 0)
        return 0;

    if ((fos_getFD = (*env)->GetMethodID(env, fos_class, "getFD",
                                         "()Ljava/io/FileDescriptor;")) == 0)
        return 0;

    if ((fd_class =  (*env)->FindClass(env, "java/io/FileDescriptor")) == 0)
        return 0;

    if ((fd_fd =  (*env)->GetFieldID(env, fd_class, "fd", "I")) == 0)
        return 0;

    /* FileOutputStream object */
    fos_devnull = (*env)->NewObject(env, fos_class, fos_constr, s_devnull);
    if (fos_devnull == 0)
        return 0;

    /* Get the FileDescriptor object */
    if ((fd_obj = (*env)->CallObjectMethod(env, fos_devnull, fos_getFD)) == 0)
        return 0;

    /* Change its fd value */
    (*env)->SetIntField(env, fd_obj, fd_fd, fd_write);
    

    (*env)->DeleteLocalRef(env, fos_devnull);

    return fd_obj;
}
