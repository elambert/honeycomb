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
 * Component = disk server
 * Synopsis  = disk statistics
 */

#include <sys/types.h>
#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <sys/vfs.h>
#include <sys/time.h>
#include <linux/kdev_t.h>
#include "disk.h"
#include "trace.h"


#define PARTITIONS	         "/proc/partitions"
#define MAX_ENTRY_LINE	         1024
#define MB		         (1024 * 1024)
#define SMART_ATTRIBUTES         30
#define SECTOR_REALLOCATED_ID    5
#define TEMPERATURE_ID           194
#define PENDING_REALLOCATED_ID   197
#define OFFLINE_UNCORRECTABLE_ID 198


/*
 * SMART structures 
 */

typedef struct {
    unsigned char id;
    unsigned char threshold;
    unsigned char _reserved[10];
} __attribute__ ((packed)) threshold_entry_t;

typedef struct {
    unsigned char  id;
    unsigned short flags;
    unsigned char  cur_value;
    unsigned char  max_value;
    unsigned char  _reserved[7];
} __attribute__ ((packed)) value_entry_t;

typedef struct {
    unsigned short revision;
    value_entry_t  smart_values[SMART_ATTRIBUTES];
    unsigned char  offline_status;
    unsigned char  _reserved1;
    unsigned char  offline_duration;
    unsigned char  _reserved2;
    unsigned char  offline_capability;
    unsigned short smart_capability;
    unsigned char  _reserved3[141];
    unsigned char  checksum;
} __attribute__ ((packed)) values_t;


typedef struct {
    unsigned short    revision;
    threshold_entry_t smart_thresholds[SMART_ATTRIBUTES];
    unsigned char     _reserved[149];
    unsigned char     checksum;
} __attribute__ ((packed)) thresholds_t;



/*
 * Fetch SMART data from /proc filesystem.
 * Returns 0 if OK, 1 if drive prefailure detected,
 * -1 in case of error.
 */
int
disk_smart(disk_t *dsk)
{
    FILE           *fp;
    char           path[MAXPATHLEN];
    unsigned short *buf;
    unsigned int   i;
    int            failure;
    thresholds_t   thresholds;
    values_t       values;


    /*
     * Fetch current smart values for this disk
     */
    snprintf(path, sizeof(path), 
             "/proc/ide/%s/smart_values", 
             (dsk->flags & DISK_FAKE) ? "hda" : dsk->dev_name);

    fp = fopen(path, "r");
    if (fp == NULL) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "disk_smart: failed to open %s", path);
        return (-1);
    }
 
    buf = (unsigned short*) &values;
    for (i = 0; i < sizeof(values) / sizeof(unsigned short); i++) {
         if (fscanf(fp, "%04hx ", &buf[i]) != 1) {
             cm_trace(CM_TRACE_LEVEL_ERROR,
                      "disk_smart: failed to read %s", path);
             fclose(fp);
             return (-1);
         }
    }
    fclose(fp);

    /*
     * Get smart thresholds for this disk
     */
    snprintf(path, sizeof(path), 
             "/proc/ide/%s/smart_thresholds", 
             (dsk->flags & DISK_FAKE) ? "hda" : dsk->dev_name);

    fp = fopen(path, "r");
    if (fp == NULL) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "disk_smart: failed to open %s", path);
        return (-1);
    }
 
    buf = (unsigned short*) &thresholds;
    for (i = 0; i < sizeof(thresholds) / 2; i++) {
         if (fscanf(fp, "%04hx ", &buf[i]) != 1) {
             cm_trace(CM_TRACE_LEVEL_ERROR,
                      "disk_smart: failed to read %s", path);
             fclose(fp);
             return (-1);
         }
    }
    fclose(fp);

    /*
     * Go through every defined attribute and
     * - update statistics
     * - check the overall health of the disk 
     */
    for (i = 0, failure = 0; i < SMART_ATTRIBUTES; i++) {
        value_entry_t     *val = &values.smart_values[i];
        threshold_entry_t *thr = &thresholds.smart_thresholds[i];
        /*
         * Prefailure condition -
         * If the value of the attribute flag is 1 and 
         * the attribute value is less than or equal to
         * its corresponding threshold, an imminent failure
         * is predicted with loss of data.
         */
        if (!val->id || !thr->id || val->id != thr->id) {
            continue;
        }
        if ((val->flags & 1) != 0 && 
            val->cur_value <= thr->threshold &&
            thr->threshold != 0xFE) {
            cm_trace(CM_TRACE_LEVEL_ERROR, "disk %s attribute %d failed",
                     dsk->dev_name, val->id);
            failure = 1;
        }
        if (val->id == SECTOR_REALLOCATED_ID) {
            dsk->stats.sectors_reallocated = val->_reserved[0];
        }
        if (val->id == TEMPERATURE_ID) {
            dsk->stats.temperature = val->_reserved[0];
        }
        if (val->id == OFFLINE_UNCORRECTABLE_ID) {
            dsk->stats.offline_unrecoverable = val->_reserved[0];
        }
        if (val->id == PENDING_REALLOCATED_ID) {
            dsk->stats.pending_reallocated = val->_reserved[0];
        }
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "disk %s SMART: nb failure %d temperature %d "
              "reallocated sect %d pending reallocation %d "
              "unrecoverable error %d\n",
              dsk->dev_name,
              failure,
              dsk->stats.temperature,
              dsk->stats.sectors_reallocated,
              dsk->stats.pending_reallocated,
              dsk->stats.offline_unrecoverable);

    if (failure) {
        return (1);
    }
    return (0);
}


/*
 * Fetch runtime statistics from the /proc filesystem.
 * In the futur (kernel 2.6), we may want to use "sysfs"
*/
int
disk_stat(disk_t *dsk)
{
    char            line[MAX_ENTRY_LINE];
    struct statfs64 stbuf;
    disk_stat_t     *stats;
    FILE            *fp;
    struct timeval  now;
    unsigned int    nsecs;
    unsigned int    nios;

    /*
     * Get available/total size for this disk
     */
    if (statfs64(dsk->exportfs, &stbuf) < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "disk_stat failed for %s", dsk->dev_name);
        return (-1);
    } 

    stats = &dsk->stats;
    stats->total_size = (stbuf.f_blocks  * stbuf.f_bsize) / MB;
    stats->avail_size = (stbuf.f_bavail  * stbuf.f_bsize) / MB;

    /*
     * Fetch extended info from /proc/partition 
     */
    fp = fopen(PARTITIONS, "r");
    if (fp == NULL) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "disk_stat:failed to open partition %s", strerror(errno));
        return (-1);
    }

    if (gettimeofday(&now, NULL) < 0) {
        return (-1);
    }

    while (fgets(line, sizeof(line), fp) != NULL) {

        unsigned int    dev_major;
        unsigned int    dev_minor;
        unsigned int    rd_ios;
        unsigned int    rd_sects;
        unsigned int    rd_ticks;
        unsigned int    wr_ios;
        unsigned int    wr_sects;
        unsigned int    wr_ticks;
        unsigned int    tt_ticks;
        struct timeval  delta;

        /*
         * Read the extended partitions info (CONFIG_BLK_STATS)
         */
        int tt = sscanf(line, 
            "%u %u %*d %*s %u %*u %u %u %u %*u %u %u %*u %*u %*u",
            &dev_major,
            &dev_minor,
            &rd_ios,
            &rd_sects,
            &rd_ticks,
            &wr_ios,
            &wr_sects,
            &wr_ticks);

        if (tt != 8)  {
            continue;
        }
        if (MKDEV(dev_major, dev_minor) != dsk->dev_id) {
            continue;
        }
        /* 
         * found the device  - update statistics
         */
         nios = (rd_ios + wr_ios) - stats->tt_nios;
         if (!nios) {
             nios = 1;
         } 
         tt_ticks = wr_ticks + rd_ticks;

         timersub(&now, &stats->last_update, &delta);
         nsecs = delta.tv_sec;
         if (!nsecs) {
             nsecs = 1;
         }
         stats->read_kBs  = ((float)(rd_sects-stats->rd_sects)) / nsecs / 2;
         stats->write_kBs = ((float)(wr_sects-stats->wr_sects)) / nsecs / 2;
         stats->io_wait   = ((float)(tt_ticks-stats->tt_ticks)) / nios;

         stats->tt_nios     = nios;
         stats->rd_sects    = rd_sects;
         stats->wr_sects    = wr_sects;
         stats->tt_ticks    = tt_ticks;
         stats->last_update = now;

    }

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "disk %s stats: "
             "Available %d/%d Read KB/s %f Write KB/s %f Avg I/O (ms) %f",
             dsk->dev_name,
             stats->avail_size,
             stats->total_size,
             stats->read_kBs,
             stats->write_kBs,
             stats->io_wait); 

    fclose(fp);

    return (0);
}
