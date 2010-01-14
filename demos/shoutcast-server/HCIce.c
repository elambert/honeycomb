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



#include <jni.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <strings.h>
#include <malloc.h>

#include <shout/shout.h>

static shout_t *shout = NULL;
static int total_bytes = 0;
static char *song = NULL;

static void _setSong() {
    shout_metadata_t *metadata = NULL;

    if (song == NULL) {
        return;
    }
    
    metadata = shout_metadata_new();
    if (!metadata) {
        printf("Failed to allocate MD\n");
        exit(1);
    }
    
    if (shout_metadata_add(metadata, "song", song) != SHOUTERR_SUCCESS) {
        printf("Failed to set MD\n");
        exit(1);
    }
    
    if (shout_set_metadata(shout, metadata) != SHOUTERR_SUCCESS) {
        printf("Failed to associate MD\n");
        exit(1);
    }
    
    shout_metadata_free(metadata);
}

static void reinit_stream() {
    int err;
    int nbTries; 

    if (shout_get_connected(shout) == SHOUTERR_CONNECTED) {
        printf ("Disconnecting the stream\n");
        shout_close(shout);
    }
    
    nbTries = 0; 

    do {
        err = shout_open(shout);
        if ( err != SHOUTERR_SUCCESS) {
            printf ("Failed to connect to server [%s]\n",
                    shout_get_error(shout));
            nbTries++; 

/*             if (nbTries < 10) { */
                printf ("Retrying ... [%d]\n", nbTries);
                sleep(2);
/*             } else { */
/*                 printf("Too many retries. Exiting ...\n"); */
/*                 exit(1); */
/*             } */
        }
    } while (err != SHOUTERR_SUCCESS);

    _setSong(); 

    printf("Connected to server\n");
}

JNIEXPORT void JNICALL Java_HCIce_setSong(JNIEnv *env,
                                          jobject this,
                                          jstring jsong)
{
    int length;
    char *_song = NULL;

    if (song != NULL) {
        free(song);
        song = NULL;
    }

    length = (*env)->GetStringUTFLength(env, jsong);
    _song = (char*)(*env)->GetStringUTFChars(env, jsong, NULL);
    
    song = (char*)malloc((length+1)*sizeof(char));
    if (!song) {
        printf("Failed to allocate the song name\n");
        exit(1);
    }

    bcopy(_song, song, length);
    song[length] = '\0';

    _setSong();

    (*env)->ReleaseStringUTFChars(env, jsong, _song);
}

JNIEXPORT void JNICALL Java_HCIce__1init(JNIEnv *env,
                                         jobject this)
{
    shout_init();
    if (!(shout = shout_new())) {
        printf("shout_new failed\n");
        exit(1);
    }

	if (shout_set_host(shout, "127.0.0.1") != SHOUTERR_SUCCESS) {
		printf("Error setting hostname: %s\n", shout_get_error(shout));
		exit(1);
	}

/* 	if (shout_set_protocol(shout, SHOUT_PROTOCOL_HTTP) != SHOUTERR_SUCCESS) { */
	if (shout_set_protocol(shout, SHOUT_PROTOCOL_ICY) != SHOUTERR_SUCCESS) {
		printf("Error setting protocol: %s\n", shout_get_error(shout));
		exit(1);
	}

	if (shout_set_port(shout, 8000) != SHOUTERR_SUCCESS) {
		printf("Error setting port: %s\n", shout_get_error(shout));
		exit(1);
	}

	if (shout_set_user(shout, "source") != SHOUTERR_SUCCESS) {
		printf("Error setting user: %s\n", shout_get_error(shout));
		exit(1);
	}

	if (shout_set_password(shout, "honeycomb") != SHOUTERR_SUCCESS) {
		printf("Error setting password: %s\n", shout_get_error(shout));
		exit(1);
	}
    
/* 	if (shout_set_mount(shout, "/honeycomb.mp3") != SHOUTERR_SUCCESS) { */
/* 		printf("Error setting mount: %s\n", shout_get_error(shout)); */
/* 		exit(1); */
/* 	} */

	if (shout_set_format(shout, SHOUT_FORMAT_MP3) != SHOUTERR_SUCCESS) {
		printf("Error setting format: %s\n", shout_get_error(shout));
		exit(1);
	}

    if (shout_set_name(shout, "Honeycomb stream") != SHOUTERR_SUCCESS) {
        printf("Error setting the name [%s]\n",
               shout_get_error(shout));
        exit(1);
    }
}

JNIEXPORT void JNICALL Java_HCIce__1close(JNIEnv *env,
                                          jobject this)
{
    shout_free(shout);
    shout = NULL;
	shout_shutdown();
}

JNIEXPORT void JNICALL Java_HCIce__1initBroadcast(JNIEnv *env,
                                                  jobject this)
{
    total_bytes = 0;
    reinit_stream();
}

JNIEXPORT void JNICALL Java_HCIce__1endBroadcast(JNIEnv *env,
                                                 jobject this)
{
    shout_sync(shout);
}

JNIEXPORT void JNICALL Java_HCIce__1broadcast(JNIEnv *env,
                                              jobject this,
                                              jbyteArray array,
                                              jint length)
{
    jbyte *b;
    int err = SHOUTERR_SUCCESS;

    total_bytes += length;
/*     printf("Sending %d - %d bytes\n", length, total_bytes);   */
    
    b = (*env)->GetByteArrayElements(env, array, NULL);

    shout_sync(shout);

    do {
        err = shout_send(shout,
                         (const unsigned char*)b,
                         length);
        if (err != SHOUTERR_SUCCESS) {
            printf("Failed to send bytes to the shout server [%s]\n",
                   shout_get_error(shout));
            reinit_stream();
        }
    } while (err != SHOUTERR_SUCCESS);
}
