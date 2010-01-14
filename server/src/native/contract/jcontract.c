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
#include "jcontract.h"     /* Generated earlier */

#include <stdio.h>
#include <sys/types.h>
#include <unistd.h>
#include <libcontract.h>
#include <sys/contract/process.h>
#include <sys/ctfs.h>
#include <sys/stat.h>
#include <errno.h>
#include <fcntl.h>
#include <strings.h>
#include <sys/time.h>
#include <sys/resource.h>


// #define DEBUG

#ifdef DEBUG
static FILE *log_file = NULL;
#endif

/* FIXME -
 * this should not be hardcoded here but defined in the node
 * config xml file per jvm.
 * Limits: 1GB of memory, 1024 open files, no coredump
 */
#define JVM_RLIMIT_AS       (1024*1024*1024)
#define JVM_RLIMIT_NOFILE   (1024)
#define JVM_RLIMIT_CORE     (0)


static int
init_template(int *error_value, char **error_message)
{
        int fd;

        *error_value = 0;
        *error_message = NULL;

        /*
         * doesn't do anything with the contract.
         * Deliver no events, don't inherit, and allow it to be orphaned.
         */
        if ((fd = open64(CTFS_ROOT "/process/template", O_RDWR)) == -1) {
                *error_message = strdup("open64");
        } else if (ct_tmpl_set_critical(fd, 0)) {
                *error_message = strdup("ct_tmpl_set_critical");
        } else if (ct_tmpl_set_informative(fd, 0)) {
                *error_message = strdup("ct_tmpl_set_informative");
        } else if (ct_pr_tmpl_set_fatal(fd, CT_PR_EV_HWERR)) {
                *error_message = strdup("ct_pr_tmpl_set_fatal");
        } else if (ct_pr_tmpl_set_param(fd, CT_PR_PGRPONLY | CT_PR_REGENT)) {
                *error_message = strdup("ct_pr_tmpl_set_param");
        } else if (ct_tmpl_activate(fd)) {
                *error_message = strdup("ct_tmpl_activate");
        } else {
                return (fd);
        }

        /*
         * We know errno is legitimate. Suppress the lint error.
         */
        *error_value = errno;
        (void) close(fd);
        return (-1);
}



JNIEXPORT jboolean JNICALL
Java_com_sun_honeycomb_cm_ServiceManager_openTemplateContract(JNIEnv *env,
                                                              jobject this)
{
  int err;
  char *err_msg;

#ifdef DEBUG
    log_file = fopen("/tmp/CONTRACT.log", "w+");
    if (!log_file) {
        printf("fopen failed\n");
        log_file = stdout;
    }
#endif


  int tmpl_fd = init_template(&err, &err_msg);
  if (tmpl_fd == -1) {
#ifdef DEBUG
    fprintf(log_file, "call %s failed with errno = %d\n", err_msg, err);
    fprintf(log_file, "cannot open template contract file\n");
    fflush(log_file);
#endif    
    return JNI_FALSE;
  } else {
#ifdef DEBUG
    fprintf(log_file, "succeeded to open template contract file\n");
    fflush(log_file);
#endif
    return JNI_TRUE;
  }
}

/*
 * Set the resource limit for this JVM.
 * Call by jvm agent during initialization.
 * Return true if all limits set successfully, false otherwise.
 */
JNIEXPORT jboolean JNICALL
Java_com_sun_honeycomb_cm_ServiceManager_setResourcesLimit(JNIEnv *env,
                                                           jobject this)
{
    struct rlimit lim;
    jboolean succeed;
    
    succeed = JNI_TRUE;
    
    lim.rlim_cur = JVM_RLIMIT_CORE;
    lim.rlim_max = JVM_RLIMIT_CORE;
    if (setrlimit(RLIMIT_CORE, &lim) == -1) {
        succeed = JNI_FALSE;
    }
    
    /*
     * Work in progress -
     * Resource limit is disabled for now. It turns out that 1GB
     * per JVM is too much to catch node deadlock and this cannot be
     * reduced for HADB.
     * Need to plugin limits per JVM through the node config xml.
     
    lim.rlim_cur = JVM_RLIMIT_AS;
    lim.rlim_max = JVM_RLIMIT_AS;
    if (setrlimit(RLIMIT_AS, &lim) == -1) {
        succeed = JNI_FALSE;
    }
    
    lim.rlim_cur = JVM_RLIMIT_NOFILE;
    lim.rlim_max = JVM_RLIMIT_NOFILE;
    if (setrlimit(RLIMIT_NOFILE, &lim) == -1) {
        succeed = JNI_FALSE;
    }
    */
    
    return succeed;
}
