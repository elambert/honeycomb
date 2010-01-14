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
#include <unistd.h>
#include <stdio.h>
#include <errno.h>
#include <strings.h>
#include <sys/wait.h>
#include <time.h>
#include <stdlib.h>
#include <signal.h>

void throwException(JNIEnv *env, char *message) {
	char errmsg[128];
        sprintf(errmsg, "errno: %d, error: %s\n", errno, message);
        jclass newExcCls = (*env)->FindClass(env,"java/io/IOException");
        (*env)->ThrowNew(env, newExcCls, errmsg);
}

void cleanUpPipes(int fdin[2], int fdout[2], int fderr[2]){
	if (fdin[1] >= 0) close(fdin[1]);
	if (fdout[1] >= 0) close(fdout[1]);
	if (fderr[1] >= 0) close(fderr[1]);
	if (fdin[0] >= 0) close(fdin[0]);
	if (fdout[0] >= 0) close(fdout[0]);
	if (fderr[0] >= 0) close(fderr[0]);
}

JNIEXPORT void JNICALL 
    Java_com_sun_honeycomb_util_SolarisRuntime_exec
        (JNIEnv *env, jobject this, jobjectArray jcmd, jobjectArray jenv,
         jobject stdin_fd, jobject stdout_fd, jobject stderr_fd) {

	int fdin[2], fderr[2], fdout[2];

	jclass tmpC = (*env)->GetObjectClass(env, stdout_fd);
     	jfieldID field_fd = (*env)->GetFieldID(env, tmpC, "fd", "I");
     	if (field_fd == 0) {
		throwException(env, "Can't find field FileDescriptor.fd");
	}

	tmpC = (*env)->GetObjectClass(env, this);
	jfieldID field_pid = (*env)->GetFieldID(env, tmpC, "pid", "I");
	if (field_pid == 0) {
		throwException(env, "Can't find field SolarisRuntime.pid");
	}
	
	if ((pipe(fdin)<0) || (pipe(fdout)<0) || (pipe(fderr)<0)) {	
       	        int saved_errno = errno;
		cleanUpPipes(fdin,fdout,fderr);
		errno = saved_errno;
		throwException(env,"vfork() Failed");
	}

	if (jcmd == NULL) { 
		throwException(env,"command can not be null.");
	}

	jint len = (*env)->GetArrayLength(env,jcmd);
	jstring jstr;
	const char* aux;
	char* args[len+1];
        int i = 0;
		
        for (i = 0; i < len; i++) {
        	jstr = (jstring)(*env)->GetObjectArrayElement(env, jcmd, i);
                aux = (*env)->GetStringUTFChars(env, jstr, 0);
		args[i] = strdup(aux);
                (*env)->ReleaseStringUTFChars(env,jstr,aux);
        }
	args[i] = NULL;

	char** enviro = NULL;

	if ( jenv != NULL ) {
		len = (*env)->GetArrayLength(env, jenv);
		enviro = (char **)malloc(sizeof(char*)*(len+1));

		if (enviro == NULL)
			throwException(env,"unabled to allocate env array in exec.c");
                
                for (i = 0; i < len; i++) {
               		jstr = (jstring)(*env)->GetObjectArrayElement(env, jenv, i);
                        aux = (*env)->GetStringUTFChars(env, jstr, 0);
			enviro[i] = strdup(aux);
			(*env)->ReleaseStringUTFChars(env,jstr,aux);
                }
       	        enviro[i] = NULL;
	}

	int pid = vfork();
        /* minimize time between vfork() and exec() because parent is blocked during 
           this small period */

	if (pid == 0) {
		/* close up parent side pipes on child side 
		   and point the appropriate out,err and in
		   to the right end of the pipe */
		close(fdin[1]);
       		dup2(fdin [0], STDIN_FILENO);
        	close(fdout[0]);
      		dup2(fdout[1], STDOUT_FILENO);
        	close(fderr[0]);
            	dup2(fderr[1], STDERR_FILENO);
		
		/* Lets execute the command */
		if (jenv == NULL) {
			execv(args[0],args);
		} else {
			execve(args[0],args,enviro); 
		}
		
		/* If we get here then exec failed */
		_exit(-1); 
	} else if (pid < 0) {	    
       	        int saved_errno = errno;
		cleanUpPipes(fdin,fdout,fderr);
		errno = saved_errno;
		throwException(env,"vfork() Failed");
	} else { 	
		/* Set PID on SolarisRuntime for later using it on wait or exitValue functions*/
        	(*env)->SetIntField(env, this, field_pid, pid);
			
		(*env)->SetIntField(env, stdin_fd , field_fd, fdin [1]);
		(*env)->SetIntField(env, stdout_fd, field_fd, fdout[0]);
		(*env)->SetIntField(env, stderr_fd, field_fd, fderr[0]);
		
		/* Cleanup child side of pipes */
		if (fdin [0] >= 0) close(fdin [0]);
     		if (fdout[1] >= 0) close(fdout[1]);
    		if (fderr[1] >= 0) close(fderr[1]);

		/* free up allocated memory */
		if (args != NULL) {
			len = (*env)->GetArrayLength(env,jcmd);
        		for (i = 0; i < len+1; i++) 
				free(args[i]);
		}
	
		if (enviro != NULL) {
			len = (*env)->GetArrayLength(env,jenv);
        		for (i = 0; i < len+1; i++)
				free(enviro[i]);
			free(enviro);
		}
	}
}

JNIEXPORT jint JNICALL
Java_com_sun_honeycomb_util_SolarisRuntime_waitpid
(JNIEnv *env, jobject this, jint testonly) {
    int status;
    int options;    
    jint pid;
    
    jclass tmpC = (*env)->GetObjectClass(env, this);
    jfieldID field_pid = (*env)->GetFieldID(env, tmpC, "pid", "I");
    if (field_pid == 0) {
        throwException(env, "Can't find field SolarisRuntime.pid");
    }
    
    pid = (*env)->GetIntField(env,this,field_pid);
    
	/* Parent Process, lets wait for child */
    if (testonly == 1) {
        int res;
        res = waitpid(pid, &status, WNOHANG);
        if (res < 0 || res == pid) {
            return -1;
        } else {
            return 0;
        }
    } else {
        /* fixme - replace 1 with correct option constant */
        waitpid(pid,&status,1);
        return WEXITSTATUS(status);
    }
}

JNIEXPORT void JNICALL
    Java_com_sun_honeycomb_util_SolarisRuntime_destroy
        (JNIEnv *env, jobject this) {

        jclass tmpC = (*env)->GetObjectClass(env, this);
        jfieldID field_pid = (*env)->GetFieldID(env, tmpC, "pid", "I");
        if (field_pid == 0) {
                throwException(env, "Can't find field SolarisRuntime.pid");
        }
	
        jint pid = (*env)->GetIntField(env,this,field_pid);
        if (pid != 0) {
            int status;
            if (kill(pid, SIGKILL) < 0) {
                throwException(env, "Couldn't send signal to child process");
            }
            
            /* cleanup zombie process */
            waitpid(pid, &status, WNOHANG);
        }
}
