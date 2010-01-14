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



#include "com_sun_honeycomb_cli_editline_Editline.h"

#include <editline/editline.h>
#include <stdlib.h>
#include <assert.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <termios.h>
#include <stdio.h>

/* These are used for completion */
static JNIEnv    *jEnv;
static jmethodID jCompletionMethodId;
static jobject   jCompletionObj;

rl_compentry_func_t *custom_complete = 0;

/*
 * Class:     com_sun_honeycomb_adm_cli_editline_Editline
 * Method:    initEditlineImpl
 * Signature: (Ljava/lang/String;)V
 */

JNIEXPORT void JNICALL 
    Java_com_sun_honeycomb_adm_cli_editline_Editline_initEditlineImpl
        (JNIEnv *env, jobject this, jstring jappName) {

    const char *appName;

    appName = (*env)->GetStringUTFChars (env, jappName, NULL);

    (*env)->ReleaseStringUTFChars (env, jappName, appName);

    rl_initialize();
}

/*
 * Class:     com_sun_honeycomb_adm_cli_editline_Editline
 * Method:    readlineImpl
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL 
    Java_com_sun_honeycomb_adm_cli_editline_Editline_readlineImpl 
        (JNIEnv *env, jobject this, jstring jprompt) {

    const char *prompt;
    char *input;

    prompt = (*env)->GetStringUTFChars (env, jprompt, NULL);

    if (NULL == prompt) {
        return NULL;
    }

    input = (char*) readline (prompt);

    (*env)->ReleaseStringUTFChars(env, jprompt, prompt);

    if (input == NULL) 
    {
        jclass exceptionClass;
        exceptionClass = (*env)->FindClass (env, "java/io/EOFException");
        if (exceptionClass != NULL) {
            (*env)->ThrowNew (env, exceptionClass, "");
        }
        return NULL;
    } 
    else if (*input) {
        return (*env)->NewStringUTF(env, input);
    } 
    else {
        return NULL;
    }
}

/*
 * Class:     com_sun_honeycomb_adm_cli_editline_Editline
 * Method:    addToHistoryImpl
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL 
    Java_com_sun_honeycomb_adm_cli_editline_Editline_addToHistoryImpl
        (JNIEnv *env, jobject this, jstring jcmd) {

    const char *line;

    line = (*env)->GetStringUTFChars (env, jcmd, NULL);

    if (line == NULL) {
        return;
    }

    add_history ((char*) line);
    (*env)->ReleaseStringUTFChars (env, jcmd, line);

    return;
}

/*
 * Class:     com_sun_honeycomb_adm_cli_editline_Editline
 * Method:    cleanupEditlineImpl
 * Signature: ()V
 */
JNIEXPORT void JNICALL 
    Java_com_sun_honeycomb_adm_cli_editline_Editline_cleanupEditlineImpl
        (JNIEnv *env, jobject this) {
    return;
}

/*
 * Class:     com_sun_honeycomb_adm_cli_editline_Editline
 * Method:    getLineBufferImpl
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL 
    Java_com_sun_honeycomb_adm_cli_editline_Editline_getLineBufferImpl
        (JNIEnv *env, jobject this) {
    return NULL;
}

/*
 * Class:     com_sun_honeycomb_adm_cli_editline_Editline
 * Method:    hasTerminalImpl
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL 
    Java_com_sun_honeycomb_adm_cli_editline_Editline_hasTerminalImpl
        (JNIEnv *env, jobject this) {
    return (jboolean) (isatty (STDIN_FILENO) ? JNI_TRUE : JNI_FALSE);
}

/*
 * This completer function is visible to the native editline library which
 * is modified to call it
 */
const char *completer (char* text, int state) {
    jstring jtext;
    jstring compl;
    const char* line;
    jboolean is_copy;

    jtext = (*jEnv)->NewStringUTF (jEnv, text);
    if (jCompletionMethodId == 0) {
        return ((const char*)NULL);
    }

    compl = (*jEnv)->CallObjectMethod (jEnv, jCompletionObj,
                    jCompletionMethodId, jtext, state);
    if (!compl) {
        return ((const char*)NULL);
    }

    line = (*jEnv)->GetStringUTFChars (jEnv, compl, &is_copy);
    return line;
}

/*
 * Class:     com_sun_honeycomb_adm_cli_editline_Editline
 * Method:    setCompleterImpl
 * Signature: (Lcom/sun/honeycomb/admin/cli/editline/EditlineCompleter;)V
 */
JNIEXPORT void JNICALL 
    Java_com_sun_honeycomb_adm_cli_editline_Editline_setCompleterImpl
        (JNIEnv *env, jobject this, jobject jCompleter) {
    jclass jCompletionClass;

    if (jCompleter != NULL) {
        jEnv = env;
        jCompletionObj = jCompleter;
        jCompletionClass = (*jEnv)->GetObjectClass(jEnv, jCompletionObj);
        jCompletionClass = (*env)->NewGlobalRef(env, jCompletionClass);
        jCompletionObj = (*env)->NewGlobalRef(env, jCompletionObj);
        jCompletionMethodId = (*jEnv)->GetMethodID(jEnv, jCompletionClass, 
            "complete", "(Ljava/lang/String;I)Ljava/lang/String;");
        if (jCompletionMethodId == 0) {
            custom_complete = NULL;
            return;
        }

        custom_complete = (rl_compentry_func_t *) completer;
    }
}

/*
 * Class:     com_sun_honeycomb_adm_cli_editline_Editline
 * Method:    disableEcho
 * Signature: ()V
 */
JNIEXPORT void JNICALL 
    Java_com_sun_honeycomb_adm_cli_editline_Editline_disableEcho
        (JNIEnv *env, jclass clz) {
    struct termios term;
    int fh;

    fh = fileno(stdout);
    tcgetattr (fh, &term);
    term.c_lflag &= (~ECHO);
    tcsetattr (fh, TCSANOW, &term);
}

/*
 * Class:     com_sun_honeycomb_adm_cli_editline_Editline
 * Method:    enableEcho
 * Signature: ()V
 */
JNIEXPORT void JNICALL 
    Java_com_sun_honeycomb_adm_cli_editline_Editline_enableEcho
        (JNIEnv *env, jclass clz) {
    struct termios term;
    int fh;

    fh = fileno(stdout);
    tcgetattr (fh, &term);
    term.c_lflag |= (ECHO);
    tcsetattr (fh, TCSANOW, &term);
}

