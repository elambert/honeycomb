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
 * To test with
 */

#include <sys/types.h>
#include <stdio.h>
#include <strings.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <time.h>
#include <sys/vfs.h>
#include <sys/time.h>
#include <sys/statvfs.h>
#include <syslog.h>
#include <sys/sysinfo.h>
#include <sys/loadavg.h>
#include <kstat.h>
#include <sys/kstat.h>
#include <sys/swap.h>
#include <vm/anon.h>

#include <assert.h>

typedef unsigned long long jlong;
typedef float jfloat;

static size_t ctok(pgcnt_t clicks);


static jlong total_interrupts(kstat_ctl_t *kc, kstat_t *ksp)
{
    long long intrs = 0;

    kstat_named_t *clock = (kstat_named_t *)kstat_data_lookup(ksp, "clk_intr");
    if (clock != NULL)
        intrs = clock->value.ui32;
    printf("Interrupts:\n    clock %lld\n", intrs);

    for (ksp = kc->kc_chain; ksp; ksp = ksp->ks_next) {
        kstat_intr_t *ki;
        int j;

        if (ksp->ks_type != KSTAT_TYPE_INTR)
            continue;
        if (kstat_read(kc, ksp, NULL) < 0)
            continue;

        ki = KSTAT_INTR_PTR(ksp);

        for (j = 0; j < KSTAT_NUM_INTRS; j++) {
            printf("    %s %ld\n", ksp->ks_name, ki->intrs[j]);
            intrs += ki->intrs[j];
        }
    }

    printf("    total %lld\n", intrs);
    return (jlong)intrs;
}

/*
 * Where all the kstat work is done
 */
static int get_values(jlong *mem_total, jlong *mem_free, jlong *mem_cache,
               jlong *mem_buffers,
               jfloat *load_1m, jfloat *load_5m, jfloat *load_15m,
               jlong *time_user, jlong *time_sys, jlong *time_idle,
               jlong *uptime, jfloat *intr_rate) 
{
    /* The resolution of the high-resolution timer */
    static unsigned long long hr_res = 0;

    kstat_ctl_t *kc;
    kstat_t *ksp;
    kstat_named_t *knp;
    jlong intrs;

    double loadavg[3];
    bzero(loadavg, sizeof(loadavg));

    /* Get HR timer's resolution */
    if (hr_res == 0) {
        struct timespec ts;
        if (clock_getres(CLOCK_REALTIME, &ts) < 0) {
            perror("clock_getres");
            return -1;
        }
        hr_res = ts.tv_nsec*100;
    }

    /* Load averages */
    if (getloadavg(loadavg, 3) < 0) {
        perror("getloadavg");
        return -1;
    }
    *load_1m = (jfloat)loadavg[LOADAVG_1MIN];
    *load_5m =  (jfloat)loadavg[LOADAVG_5MIN];
    *load_15m =  (jfloat)loadavg[LOADAVG_15MIN];

    if ((kc = kstat_open()) == 0) {
        perror("kstat_open");
        return -1;
    }

    /* Uptime */

    if ((ksp = kstat_lookup(kc, "unix", 0, "system_misc")) == 0) {
        perror("kstat_lookup unix:0:system_misc");
        return -1;
    }
    if (kstat_read(kc, ksp, NULL) < 0) {
        perror("kstat_read");
        return -1;
    }
    /* The kstat is created at boot time */
    *uptime = (jlong) (0.5 + 1000 * ksp->ks_snaptime/hr_res); /* in ms */

    /* Interrupt rate */
    intrs = total_interrupts(kc, ksp);
    *intr_rate = (jfloat)(1000 * (float)intrs / *uptime);
    printf("Total: %lld/%lldms = %.1f/s\n", intrs, *uptime, *intr_rate);

    /* CPU times (a tick is about 0.1s) */
    {
        int i;
        int num_CPUs = sysconf(_SC_CPUID_MAX) + 1;

        *time_user = *time_sys = *time_idle = 0LL;
        for (i = 0; i < num_CPUs; i++) {
            if ((ksp = kstat_lookup(kc, "cpu", i, "sys")) == 0)
                // No such CPU
                continue;
            if (kstat_read(kc, ksp, 0) < 0) {
                perror("kstat_read");
                return -1;
            }
            knp = kstat_data_lookup(ksp, "cpu_ticks_user");
            *time_user += knp->value.ui64;
            knp = kstat_data_lookup(ksp, "cpu_ticks_kernel");
            *time_sys += knp->value.ui64;
            knp = kstat_data_lookup(ksp, "cpu_ticks_idle");
            *time_idle += knp->value.ui64;
        }
    }


#if 0                           /* VM info: swapctl or kstat? */
    /*
     * VM info from kstat
     *
     * swapfs_minfree is set to physmem/8 (with a minimum of 3.5
     * megabytes) and acts as a limit on the amount of memory used to
     * hold anonymous data.
     *
     * unix:0:system_pages:availrmem - the amount of resident,
     *     unswappable memory in the system
     *
     * The amount of swap space that can be reserved from memory is
     * calculated by subtracting swapfs_minfree from availrmem. The
     * total amount available for reservation is thus
     *     MAX(ani_max - ani_resv, 0) + (availrmem - swapfs_minfree)
     *
     * A reservation failure will prevent a process from starting or
     * growing. Allocations aren't really interesting. The counters
     * provided by the kernel to commands such as vmstat and sar are
     * part of the vminfo kstat structure. These counters accumulate
     * once per second, so average swap usage over a measured interval
     * can be determined. The swap -s command reads the kernel
     * directly to obtain a snapshot of the current anoninfo values,
     * so the numbers will never match exactly. Also, the simple act
     * of running a program changes the values, so you can't get an
     * exact match. The vminfo calculations are as follows:
     *
     *     swap_resv += ani_resv
     *     swap_alloc += MAX(ani_resv, ani_max) - ani_free
     *     swap_avail += MAX(ani_max - ani_resv, 0) + (availrmem - swapfs_minfree)
     *     swap_free += ani_free + (availrmem - swapfs_minfree)
     *
     * See http://sunsite.uakom.sk/sunworldonline/swol-07-1998/swol-07-perf.html
     * for more.
     */
    if ((ksp = kstat_lookup(kc, "unix", 0, "vminfo")) == 0) {
        perror("kstat_lookup unix:0:vminfo");
        return -1;
    }
    else {
        vminfo_t vminfo;
        bzero(&vminfo, sizeof(vminfo));
        if (kstat_read(kc, ksp, &vminfo) < 0) {
            perror("kstat_read");
            return -1;

            *mem_total = (jlong) vminfo.swap_alloc;
            *mem_free = (jlong) vminfo.freemem;
        }
    }
#else
    {
        /*
         * The numbers from kstat seem very fishy... using the older
         * swapctl(2) instead
         */
        struct anoninfo ai;
        pgcnt_t allocated;
        size_t reserved, used, available;

        /*
         * max = total amount of swap space including physical memory
         * ai.ani_max = MAX(anoninfo.ani_resv, anoninfo.ani_max) +
                availrmem - swapfs_minfree;
         * ai.ani_free = amount of unallocated anonymous memory
                (ie. = resverved_unallocated + unreserved)
         * ai.ani_free = anoninfo.ani_free + (availrmem - swapfs_minfree);
         * ai.ani_resv = total amount of reserved anonymous memory
         * ai.ani_resv = anoninfo.ani_resv;
         *
         * allocated = anon memory not free
         * reserved = anon memory reserved but not allocated
         * available = anon memory not reserved
         */
        if (swapctl(SC_AINFO, &ai) == -1) {
            perror("swapctl");
            return -1;
        }
        allocated = ai.ani_max - ai.ani_free;
        reserved = ctok(ai.ani_resv - allocated);
        available = ctok(ai.ani_max - ai.ani_resv);
        used = ctok(ai.ani_resv);

        /*
        printf("* (%lu) alloc %lu  resvd %lu  avail %lu  used %lu\n",
               ctok(ai.ani_resv), ctok(allocated),
               reserved, available, used);
        */

        *mem_free = available;
        *mem_total = available + used;
    }
#endif
    *mem_cache = 0;             /* What does this mean on Solaris? */
    *mem_buffers = 0;           /*             -do-                */

    kstat_close(kc);

    return 0;
}

int main(int argc, char* argv[])
{
    jlong mem_total, mem_free, mem_cache, mem_buffers;
    jfloat load_1m, load_5m, load_15m, intr_rate;
    jlong time_user, time_sys, time_idle, uptime_ms, uptime_s;
    jlong days, hr, min, sec, t;

    double time_total;

    get_values(&mem_total, &mem_free, &mem_cache, &mem_buffers,
               &load_1m, &load_5m, &load_15m,
               &time_user, &time_sys, &time_idle,
               &uptime_ms, &intr_rate);

    t = uptime_s = uptime_ms/1000.0 + 0.5;

    days = t / 86400;
    t %= 86400;
    hr = t / 3600;
    t %= 3600;
    min = t / 60;
    sec = t % 60;
    printf("Up %lld days, %lld:%02lld:%02lld (%.3fs)\n",
           days, hr, min, sec, uptime_ms/1000.0);

    printf("Load: %.2f %.2f %.2f\n", load_1m, load_5m, load_15m);

    time_total = time_user + time_sys + time_idle;
    printf("CPU:  user %.1f%%; sys %.1f%%; idle %.1f%%\n",
           100*time_user/time_total,
           100*time_sys/time_total, 100*time_idle/time_total);
    printf("Swap: total %llu KB; free %llu KB; cache %llu; buffers %llu\n",
           mem_total, mem_free, mem_cache, mem_buffers);
    printf("Interrupts: %.1f/s\n", intr_rate);

    exit(0);
}

/*
 * Implement:
 *      #define ctok(x) ((ctob(x))>>10)
 * in a machine independent way. (Both assume a click > 1k)
 */
static size_t
ctok(pgcnt_t clicks)
{
    static int factor = -1;

    if (factor == -1)
        factor = (int)(sysconf(_SC_PAGESIZE) >> 10);
    return ((size_t)(clicks * factor));
}
