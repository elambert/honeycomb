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
 * Component = disk probe routine
 * Synopsis  = probe availabe disk on this machine and
 *             build the node configuration table
 */

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <dirent.h>
#include <sys/stat.h>
#include <errno.h>
#include "config.h"
#include "trace.h"
#include "mbdisk.h"


#define MAX_ENTRY_LEN    (1024)
#define MB               (1024*1024)
#define MIN_VDISK_SIZE   (10)      /* in MB */
#define MAX_VDISK_SIZE   (10*1024) /* in MB */


/*
 * remove any entries in the given dir
 */
static void
reset_dir(const char *path)
{
    char entry[MAXPATHLEN];
    DIR           *dir;
    struct dirent *dp;

    dir = opendir(path);
    if (dir == NULL) {
        mkdir(path, S_IRWXU|S_IRWXG|S_IRWXO);
        return;
    }
    while ((dp = readdir(dir)) != NULL) {
        if (!strcmp(dp->d_name, ".") || !strcmp(dp->d_name, "..")) {
            continue;
        }
        snprintf(entry, sizeof(entry), "%s/%s", path, dp->d_name);
        rmdir(entry);
    }
    closedir(dir);
}


/*
 * Probe available disks by reading /proc/ide entries
 */ 
void 
probe_disk(hc_tree_opaque_t *root_node,
           const char       *arg)
{
    const char *ide_dev = "/proc/ide";

    DIR           *dir;
    int            clen = 0;
    struct dirent *dp;
    char           cmd[MAX_ENTRY_LEN];
    int            count = 0;
    hc_service_t  *new_service;


    reset_dir(DISKSERVER_MNTPATH);

    dir = opendir(ide_dev);
    if (dir == NULL) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "probe_disk: cannot find %s", ide_dev);
        return;
    }

    clen = snprintf(cmd, sizeof(cmd), "%s/hcdisk", INSTALL_SBIN);

    while ((dp = readdir(dir)) != NULL) {
        char        dev[MAXPATHLEN];
        struct stat stbuf;
        int         ret;

        snprintf(dev, sizeof(dev), "%s/%s", ide_dev, dp->d_name);
        if (lstat(dev, &stbuf) != 0) {
            continue;
        }
        if (!S_ISLNK(stbuf.st_mode)) {
            continue;
        }
        ret = snprintf(&cmd[clen],
                       sizeof(cmd) - clen,
                       " -d %s",
                       dp->d_name);

        if (ret < 0 || ret >= ((int)sizeof(cmd) - clen)) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "probe_disk: too many disks defined");
            closedir(dir);
            return;
        }
        clen += ret;
        count++;
    }
    closedir(dir);
    if (count == 0) {
        return;
    }
    new_service = hc_service_create_new(root_node,
                                        NULL,
                                        HC_SERVICE_PROCESS,
                                        HC_FILTER_GROUP_USER | HC_FILTER_NODE_ANYNODE,
                                        20,
                                        cmd,
                                        NULL,
                                        NULL,
                                        3,
                                        60,
                                        "DiskServer",
                                        sizeof(mbdisk_t));
    if (new_service == NULL) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "probe_disk: new service allocation failed");
    }
}

/*
 * Fake given number of disks for cluster/single node emulation
 */
void
fake_disk(hc_tree_opaque_t *root_node,
          const char       *arg)
{
    const char *vsetup_cmd = \
        "/sbin/losetup %s %s > /dev/null 2>&1";
    const char *vcreate_cmd = \
        "/bin/dd if=/dev/zero of=%s bs=1k count=%d > /dev/null 2>&1";

    hc_service_t *new_service;
    char          cmd[MAX_ENTRY_LEN];
    int           clen = 0;
    int           count = 0;
    int           i;
    unsigned int  nbdisks;
    size_t        size;

    if (arg == NULL) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                "missing virtual disk argument. Aborting");
        return;
    }
    if (sscanf(arg, "%ux%u", &nbdisks, &size) != 2) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                "bad arguments. Got %s expected <nbdisks>x<size>");
        return;
    }
    if (size < MIN_VDISK_SIZE || size > MAX_VDISK_SIZE) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                "bad disk size argument. Aborting\n"
                "Given size is %d: should be > %d and < %d", 
                size, MIN_VDISK_SIZE, MAX_VDISK_SIZE);
        return;
    }

    reset_dir(DISKSERVER_MNTPATH);
    clen = snprintf(cmd, sizeof(cmd), "%s/hcdisk", INSTALL_SBIN);

    for (i = 0; i < nbdisks; i++) {
        char        dev   [MAXPATHLEN];
        char        path  [MAXPATHLEN];
        char        syscmd[MAXPATHLEN];
        struct stat stbuf;
        int         ret;

        /* Check loopback devices are present in this kernel version */
        snprintf(dev, sizeof(dev), "/dev/loop%d", i);
        if (stat(dev, &stbuf) < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                    "cannot find loopback kernel devices. Aborting");
            return;
        }

        /* Check and create the corresponding file for backing store */
        snprintf(path, sizeof(path), "%s/vdisk%d.%dMB", INSTALL_VDISK, i, size);
        if (stat(path, &stbuf) < 0) {
            cm_trace(CM_TRACE_LEVEL_NOTICE, "creating device file %s", path);
            snprintf(syscmd, sizeof(syscmd), vcreate_cmd, path, (size * 1024));
            if (system(syscmd) < 0) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "cannot create loopback file storage (%s)",
                         strerror(errno));
                continue;
            }
        }

        /* Remove any association on this loopback device */
        snprintf(syscmd, sizeof(syscmd), vsetup_cmd, "-d", dev);
        system(syscmd);

        /* Setup the loopback device */
        snprintf(syscmd, sizeof(syscmd), vsetup_cmd, dev, path);
        if (system(syscmd) < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                    "cannot setup loopback device %s",
                    strerror(errno));
            continue;
        }

        /* Add this entry to the Disk Server */
        ret = snprintf(&cmd[clen], sizeof(cmd) - clen, " -d loop%d", i);
        if (ret < 0 || ret >= ((int)sizeof(cmd) - clen)) {
            cm_trace(CM_TRACE_LEVEL_ERROR, "too many disks defined");
            return;
        }
        clen += ret;
        count++;
    }

    if (count == 0) {
        return;
    }
    new_service = hc_service_create_new(root_node,
                                        NULL,
                                        HC_SERVICE_PROCESS,
                                        HC_FILTER_GROUP_USER | HC_FILTER_NODE_ANYNODE,
                                        20,
                                        cmd,
                                        NULL,
                                        NULL,
                                        3,
                                        60,
                                        "DiskServer",
                                        sizeof(mbdisk_t));
    if (new_service == NULL) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "new service allocation failed");
    }
}
