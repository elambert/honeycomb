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
#include <stdio.h>
#include <unistd.h>
#include <strings.h>
#include <errno.h>
#include <sys/vfs.h>
#include <sys/time.h>
#include <sys/statvfs.h>
#include <syslog.h>
#include <sys/sysinfo.h>
#include <sys/loadavg.h>
#include <kstat.h>
#include <sys/kstat.h>

#ifndef DEBUG
#    include <jni.h>        /* Standard native method stuff */
#    include "jkstat.h"     /* Generated earlier */
#    include "callbacks.h"

#    define getCstring(env, jstr) \
         ((*(env))->GetStringUTFChars(env, jstr, 0))
#    define freeCstring(env, jstr, s) \
         ((*(env))->ReleaseStringUTFChars((env), (jstr), (s)))

#else

     /* Fakes, for use when debugging */
#    include "debug_callbacks.h"

#    define getCstring(env, jstr) (jstr)
#    define freeCstring(env, jstr, s)

#endif

/* forward decls */

static int
fetchKstats(JNIEnv* env, jclass cls, 
            const char* moduleName, int instance, const char* name);

static int
send_kstat(JNIEnv* env, jclass cls, kstat_ctl_t* kc, kstat_t* ksp);

static int
send_named_kstat(JNIEnv* env, jclass cls, kstat_named_t* knamed);

static int
send_timer_kstat(JNIEnv* env, jclass cls, kstat_timer_t* ktimer);

/**********************************************************************/

/*
 * Class:     com.sun.honeycomb.util.Kstat
 * Method:    getKstat
 * Signature: (Ljava/lang/String;ILjava/lang/String;)Z
 */

JNIEXPORT jboolean JNICALL Java_com_sun_honeycomb_util_Kstat_getKstat
(JNIEnv* env, jclass cls, jstring module, jint instance, jstring kname)
{
    const char *moduleName = getCstring(env, module);
    const char *name = getCstring(env, kname);

    int ret = fetchKstats(env, cls, moduleName, instance, name);

    freeCstring(env, module, moduleName);
    freeCstring(env, kname, name);

    return ret == 0;
}

/**********************************************************************/

/*
 * All kstat below here, no JNI
 */

static int
fetchKstats(JNIEnv* env, jclass cls,
            const char* module, int instance, const char* name)
{
    kstat_ctl_t* kc;
    kstat_t* ksp;

    /*
    syslog(LOG_LOCAL0 | LOG_DEBUG,
           "Getting kstat %s:%ld:%s\n", module, instance, name);
    */

    if ((kc = kstat_open()) == 0) {
        perror("kstat_open");
        return -1;
    }

    for (ksp = kc->kc_chain; ksp; ksp = ksp->ks_next) {
        if (module != NULL && strcmp(module, ksp->ks_module) != 0)
            continue;

        if (instance != -1 && instance != ksp->ks_instance)
            continue;

        if (name != NULL && strcmp(name, ksp->ks_name) != 0)
            continue;

        if (send_kstat(env, cls, kc, ksp) < 0) {
            /* Signal an error */
            ;
        }
    }

    return kstat_close(kc);
}

static int
send_kstat(JNIEnv* env, jclass cls, kstat_ctl_t* kc, kstat_t* ksp)
{
    union {
        kstat_named_t knamed;
        kstat_intr_t kintr;
        kstat_timer_t ktimer;
        kstat_io_t kio;
    } *ksdata;

    char* datap;

    if (sendAttrs(env, cls,
                  ksp->ks_crtime, ksp->ks_kid, ksp->ks_module,
                  ksp->ks_instance, ksp->ks_name, ksp->ks_type, ksp->ks_class,
                  ksp->ks_snaptime) < 0)
        return -1;

    /*
     * The docs are a little light on what the semantics are when the
     * third argument is provided to kstat_read() -- all the obvious
     * options seem to lead to memory corruption.
     */
    if (kstat_read(kc, ksp, 0) < 0) {
        perror("kstat_read");
        return -1;
    }

    if (ksp->ks_type == KSTAT_TYPE_RAW)
        return sendRaw(env, cls, ksp->ks_data, ksp->ks_data_size);

    ksdata = ksp->ks_data;
    switch(ksp->ks_type) {

    case KSTAT_TYPE_INTR:
        return sendIntr(env, cls, ksdata->kintr.intrs);

    case KSTAT_TYPE_IO:
        return sendIOStat(env, cls,
                          ksdata->kio.nread, ksdata->kio.nwritten,
                          ksdata->kio.reads, ksdata->kio.writes,
                          ksdata->kio.wtime, ksdata->kio.wlentime,
                          ksdata->kio.wlastupdate,
                          ksdata->kio.rtime, ksdata->kio.rlentime,
                          ksdata->kio.rlastupdate,
                          ksdata->kio.wcnt, ksdata->kio.rcnt);

    case KSTAT_TYPE_NAMED:
    case KSTAT_TYPE_TIMER:
        /* These types have multiple data elements */

        datap = ksp->ks_data;

        for (int i = 0; i < ksp->ks_ndata; i++) {
            if (ksp->ks_type == KSTAT_TYPE_NAMED) {
                send_named_kstat(env, cls, (kstat_named_t*) datap);
                datap += sizeof (kstat_named_t);
            }
            else {
                send_timer_kstat(env, cls, (kstat_timer_t*) datap);
                datap += sizeof (kstat_timer_t);
            }
        }
        return 0;

    } /* case */
}

static int
send_timer_kstat(JNIEnv* env, jclass cls, kstat_timer_t* ktimer)
{
    return sendTimer(env, cls,
                     ktimer->name, ktimer->num_events,
                     ktimer->elapsed_time,
                     ktimer->min_time, ktimer->max_time,
                     ktimer->start_time, ktimer->stop_time);
}

static int
send_named_kstat(JNIEnv* env, jclass cls, kstat_named_t* knamed)
{
    switch (knamed->data_type) {

    case KSTAT_DATA_INT32:
        return sendNamedLong(env, cls, knamed->name, knamed->value.i32);

    case KSTAT_DATA_UINT32:
        return sendNamedLong(env, cls, knamed->name, knamed->value.ui32);

    case KSTAT_DATA_INT64:
        return sendNamedLong(env, cls, knamed->name, knamed->value.i64);

    case KSTAT_DATA_UINT64:
        return sendNamedLong(env, cls, knamed->name, knamed->value.ui64);

    case KSTAT_DATA_STRING:
        return sendNamedString(env, cls, knamed->name,
                               KSTAT_NAMED_STR_PTR(knamed),
                               KSTAT_NAMED_STR_BUFLEN(knamed));

    case KSTAT_DATA_CHAR:
        // TODO: XXX HACK Don't just chop off the last character!
        knamed->value.c[sizeof(knamed->value.c) - 1] = 0;
        return sendNamedString(env, cls, knamed->name,
                               knamed->value.c, sizeof(knamed->value.c));
    }
    return -1;
}
