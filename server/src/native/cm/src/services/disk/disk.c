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
 * Synopsis  = disks subroutines
 */

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <sys/mount.h>
#include <errno.h>
#include <linux/major.h>
#include <linux/kdev_t.h>
#include "mbox.h"
#include "mbdisk.h"
#include "disk.h"
#include "trace.h"


/*
 * Global variables for external commands
 */
static char *exportfs  = "/usr/sbin/exportfs";
static char *mountfs   = "/bin/mount";
static char *umountfs  = "/bin/umount";
static char *xfsadmin  = "/bin/xfs_admin";
static char *diskchk   =  DISK_CHECK;

/*
 * constants
 */
static const time_t short_timeout  = 60;        /* 1mn */
static const time_t long_timeout   = (10 * 60); /* 10mn */

/*
 * Maximum string length of an uuid as returned by 
 * uuid_unparse
 */
#define UUID_MAXLEN 40

/*
 * Where to find defined partitions
 */
#define PARTITIONS  "/proc/partitions"

static int   is_path_exported   (const char*);
static int   execute_cmd        (char *const [], int [2], const time_t);
static int   xfs_get_uuid       (disk_t*);
static dev_t find_device_number (char*);
static int   setup_directories  (disk_t*);

extern char* strcasestr(const char*, const char*);

/* 
 * Disk setup
 * Fill in the disk info structure
 */
int
disk_setup(const char  *devname,
           disk_t      *dsk)
{
    dsk->status = DISK_INIT;
    dsk->flags  = 0;
    dsk->dev_id = 0;
    uuid_clear(dsk->disk_id);
    bzero(&dsk->stats, sizeof(disk_stat_t));

    dsk->dev_name = strdup(devname);
    if (dsk->dev_name == NULL) {
        return (-1);
    }
    dsk->exportfs = malloc(DISKSERVER_MAXPATHLEN);
    if (dsk->exportfs == NULL) {
        free(dsk->dev_name);
        dsk->dev_name = NULL;
        return (-1);
    }
    dsk->partition = malloc(DISKSERVER_MAXPATHLEN);
    if (dsk->partition == NULL) {
        free(dsk->dev_name);
        free(dsk->exportfs);
        dsk->dev_name = NULL;
        dsk->exportfs = NULL;
        return (-1);
    }
    if (strstr(dsk->dev_name, "loop") != NULL) {
        snprintf(dsk->partition, DISKSERVER_MAXPATHLEN,
                "/dev/%s", dsk->dev_name);
        dsk->flags |= DISK_FAKE;
    } else {
        snprintf(dsk->partition, DISKSERVER_MAXPATHLEN,
                "/dev/%s%d", dsk->dev_name, HC_XFS_PARTITION);
    }
    uuid_unparse(dsk->disk_id, dsk->exportfs);
    return (0);
} 

/*
 * Disk initialization
 */
int
disk_init(disk_t *dsk)
{
    struct stat     statbf;
    int             fd;
    int             ret;
    long            nbsectors;
    char            *args[10];
    char            devpath[MAXPATHLEN];


    /* silently unmount the filesystem */
    args[0] = umountfs;
    args[1] = "-l";
    args[2] = dsk->partition;
    args[3] = NULL;
    execute_cmd(args, NULL, short_timeout);

    if (dsk->flags & DISK_FAKE) {
        /*
         * Virtual disk -
         */
        if (xfs_get_uuid(dsk) < 0) {
            /*
             * Initialize filesystem
             */
            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "%s: creating filesystems", dsk->dev_name);

            args[0] = diskchk;
            args[1] = "initfs";
            args[2] = dsk->partition;
            args[3] = NULL;

            if (execute_cmd(args, NULL, long_timeout) < 0) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "%s: filesystem init failed", dsk->dev_name);
                return (-1);
            }
        }
        if (stat("/", &statbf) < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR, "cannot stat local filesystem");
            return (-1);
        }
        dsk->dev_id = statbf.st_dev;

    } else {
        /*
         * Real disk -
         */
        snprintf(devpath, sizeof(devpath), "/dev/%s", dsk->dev_name);
        fd = open(devpath, O_RDWR | O_NONBLOCK);
        if (fd < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "disk_init cannot open %s", dsk->dev_name);
            return (-1);
        }
        ret = fstat(fd, &statbf);
        if (!ret) {
            if (scsi_blk_major(statbf.st_dev)) {
                dsk->dev_id = find_device_number(devpath);
                if (dsk->dev_id == 0) {
                    cm_trace(CM_TRACE_LEVEL_ERROR,
                             "cannot find device id for %s: stats disabled",
                             dsk->dev_name);
                }
            } else {
                dsk->dev_id = statbf.st_dev;
            }
            ret = ioctl(fd, BLKGETSIZE, &nbsectors);
        }
        close(fd);
        if (ret) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "disk_init %s not a block device", dsk->dev_name);
            return (-1);
        } 

        args[0] = diskchk;
        args[1] = "checkpartition";
        args[2] = devpath;
        args[3] = NULL;

        if (execute_cmd(args, NULL, long_timeout) < 0) {
            /*
             * Initialize partitions 
             */
            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "%s: creating partitions", dsk->dev_name);

            args[0] = diskchk;
            args[1] = "initpartition";
            args[2] = devpath;
            args[3] = NULL;
   
            if (execute_cmd(args, NULL, long_timeout) < 0) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "%s: partition init failed", dsk->dev_name);
                return (-1);
            }
            /*
             * Initialize filesystem
             */
            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "%s: creating filesystems", dsk->dev_name);

            args[0] = diskchk;
            args[1] = "initfs";
            args[2] = dsk->partition;
            args[3] = NULL;

            if (execute_cmd(args, NULL, long_timeout) < 0) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "%s: filesystem init failed", dsk->dev_name);
                return (-1);
            }
        }
    }

    /* check filesystem was unmounted cleanly */
    args[0] = diskchk;
    args[1] = "checkfs";
    args[2] = dsk->partition;
    args[3] = NULL;

    if (execute_cmd(args, NULL, long_timeout) < 0) {
	/*
	 * Only log a warning
	 */ 
        cm_trace(CM_TRACE_LEVEL_ERROR,
		 "WARNING: filesystem %s was not unmounted cleanly",
		  dsk->dev_name);
        dsk->flags |= DISK_CORRUPTED;
    }

    /* Get the uuid for this disk */
    if (xfs_get_uuid(dsk) < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "%s: cannot get UUID", dsk->dev_name);
        return (-1);
    }

    /*
     * Build the export path
     */
    ret = snprintf(dsk->exportfs, 
                   DISKSERVER_MAXPATHLEN,
                   "%s/",
                   DISKSERVER_MNTPATH);
    if (ret < 0 || ret >= (DISKSERVER_MAXPATHLEN - UUID_MAXLEN)) {
        return (-1);
    }
    uuid_unparse(dsk->disk_id, &dsk->exportfs[ret]);

    /*
     * be sure we have a valid mount point
     */
    if (mkdir(dsk->exportfs, DISK_OMODE) == -1 && errno != EEXIST) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "disk_init %s cannot create mount point",
                 dsk->dev_name);
        return (-1);
    } 

    if (stat(dsk->exportfs, &statbf) < 0 && !S_ISDIR(statbf.st_mode)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "disk_init %s cannot check mount point",
                 dsk->dev_name);
        return (-1);
    }

    /* 
     * silently unexport the filesystem 
     */
    snprintf(devpath, sizeof(devpath), "*:%s", dsk->exportfs);
    args[0] = exportfs;
    args[1] = "-u";
    args[2] = devpath;
    args[3] = NULL; 

    (void) execute_cmd(args, NULL, short_timeout);

    /*
     * Init first metric values
     */
    disk_stat(dsk);

    return (0);
}

/*
 * Disk start -
 * mount and export the filesystem through NFS
 */
int
disk_start(disk_t *dsk)
{
    char *args[10];
    char  path[MAXPATHLEN];

    /*
     * mount the filesystem 
     */
    args[0] = mountfs;
    args[1] = dsk->partition;
    args[2] = dsk->exportfs;
    args[3] = "-o";
    args[4] = "wsync";
    args[5] = NULL;

    if (execute_cmd(args, NULL, long_timeout) < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "unable to mount disk %s", dsk->dev_name);
        return (-1);
    }

    /*
     * Check and make directories structure on the filesystem
     */
    snprintf(path, sizeof(path), "%s/%s", dsk->exportfs, HC_DIR_SETUP);
    if (access(path, F_OK) < 0) {
        int fd;
        if (setup_directories(dsk) < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "%s: failed to setup directories", dsk->dev_name);
            return (-1);
        }
        fd = open(path, O_WRONLY | O_CREAT, DISK_OMODE);
        if (fd < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "%s: setup directories error: %s",  
                     dsk->dev_name, strerror(errno));
            return (-1);
        }
        close(fd);
    }

    /*
     * export the filesystem 
     */
    snprintf(path, sizeof(path), "*:%s", dsk->exportfs);

    args[0] = exportfs;
    args[1] = "-o";
    args[2] = "rw, no_root_squash, no_wdelay",
    args[3] = path;
    args[4] = NULL;

    (void) execute_cmd(args, NULL, short_timeout);
    /*
     * Workaround - exportfs does not return with a bad status
     * if it fails to export the filesystem. Check it
     * did succeed.
     */
    if (!is_path_exported(dsk->exportfs)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "unable to export filesystem %s", dsk->dev_name);
        return (-1);
    }
  
    return (0);
}

/*
 * Stop disk -
 * unexport and umount the filesystem through NFS
 */
int
disk_stop(disk_t *dsk)
{
    char *args[4];
    char  path[MAXPATHLEN];
    

    bzero(&dsk->stats, sizeof(disk_stat_t));

    /*
     * unexport the filesystem
     */
    snprintf(path, sizeof(path), "*:%s", dsk->exportfs);

    args[0] = exportfs;
    args[1] = "-u";
    args[2] = path;
    args[3] = NULL; 

    (void) execute_cmd(args, NULL, short_timeout);

    /*
     * Workaround - check that the filesystem is indeed
     * no more exported
     */
    if (is_path_exported(dsk->exportfs)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "[disk_stop] cannot unexport %s", dsk->dev_name);
    }

    /*
     * unmount the filesystem 
     */
    args[0] = umountfs;
    args[1] = "-l";
    args[2] = dsk->partition;
    args[3] = NULL;
    if (execute_cmd(args, NULL, short_timeout) < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "[disk_stop] failed to umount disk %s", dsk->dev_name);
        return (-1);
    }

    return (0);
}

/*
 * Helper - check if path is exported by nfs
 * return 1 if path is found, 0 otherwise
 * -1 in case of error.
 */
static int
is_path_exported(const char* path)
{
    int  fds[2];
    char buf[MAXPATHLEN];
    char *args[2];
    int  found;
    int  ret;

    if (pipe(fds) < 0) {
        return (-1);
    }

    found = 0;
    args[0] = exportfs;
    args[1] = NULL;

    ret = execute_cmd(args, fds, short_timeout);
    close(fds[1]);
    if (!ret) {
        int len = read(fds[0], buf, sizeof(buf) - 1);
        if (len > 0) {
            buf[len] = '\0';
            found = (strstr(buf, path) != NULL)? 1 : 0;
        }
    }

    close(fds[0]);
    return (found);
}

/*
 * Helper - fork and exec the given command
 */
int
execute_cmd(char *const args[], int fds[2], time_t timeout)
{
    const long hbt_delay = 100;        /* in ms */

    struct timeval tmstart;
    struct timeval tmcur;
    pid_t          pid;
    pid_t          wpid;
    int            status;

    pid = fork();
    if (pid == 0) {
       /*
        * Child - execute the command
        */
       if (fds != NULL) {
           dup2(fds[1], 1);
           dup2(1, 2); 
       }  else {
           close(2);
           close(1);
       }
       execv(args[0], args);
       cm_trace(CM_TRACE_LEVEL_ERROR, "execv [%s] failed %s", 
               args[0], strerror(errno));
       _exit(1);
    } else if (pid == -1) {
       /*
        * Fork error 
        */
       cm_trace(CM_TRACE_LEVEL_ERROR, "fork failed %s", strerror(errno));
       return (-1);
    }
    /*
     * Father - wait and check for the status 
     */
    gettimeofday(&tmstart, NULL);
    do {
        struct timespec tmdelay;
        wpid = waitpid(pid, &status, WNOHANG);
        if (wpid < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "waitpid failed %s", strerror(errno));
            return (-1);
        } else if (wpid == pid) {
            break;
        }
        tmdelay.tv_sec  = 0;
        tmdelay.tv_nsec = hbt_delay * 1000000;
        nanosleep(&tmdelay, NULL);

        disk_heartbeat();
        gettimeofday(&tmcur, NULL);

    } while ((tmcur.tv_sec - tmstart.tv_sec) < timeout);

    /*
     * Timeout waiting on the command to finish - abort
     */ 
    if (wpid != pid) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "command %s timed out", args[0]);
        kill(pid, SIGKILL);
        return (-1);
    }

    /*
     * Check the exit status
     */
    if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        return (-1);
    }
    return (0);
} 

/*
 * Retrieve the UUID of the given filesystem
 */
static int
xfs_get_uuid(disk_t* dsk)
{
    static const char *uuid_check = "uuid = ";
    static const char *uuid_badfs = \
        "unexpected XFS SB magic number 0x00000000";

    int      fds[2];
    char     buf[MAXPATHLEN];
    char     *args[4];
    int      ret;

    if (pipe(fds) == -1) {
        return (-1);
    }
    args[0] = xfsadmin;
    args[1] = "-u";
    args[2] = dsk->partition;
    args[3] = NULL;

    ret = execute_cmd(args, fds, long_timeout);
    close(fds[1]);
    if (!ret) {
        size_t clen = strlen(uuid_check);
        int len = read(fds[0], buf, sizeof(buf) - 1);
        if (len > 0) {
            char *needle;
            buf[len] = '\0';
            if (strstr(buf, uuid_badfs) != NULL) { 
                ret = -1;
            } else if ((needle = strcasestr(buf, uuid_check)) != NULL) {
                char *eol = strchr(&needle[clen], '\n');
                if (eol) {
                    *eol = '\0';
                }
                ret = uuid_parse(&needle[clen], dsk->disk_id);
            } else {
                ret = -1;
            }
        }
    }
    close(fds[0]); 
    return (ret);
}

/*
 * Find device number (major/minor) of the given device
 */
static dev_t
find_device_number(char *devpath)
{
    char         lnkpath[MAXPATHLEN];
    char         path[MAXPATHLEN];
    char         line[MAXPATHLEN];
    FILE         *fp;
    unsigned int major;
    unsigned int minor;
    int          len;
    int          found = 0;

    len = readlink(devpath, lnkpath, sizeof(lnkpath));
    if (len < 0) {
        return 0;
    }
    lnkpath[len] = '\0';

    fp = fopen(PARTITIONS, "r");
    if (fp == NULL) {
        return 0;
    }
    while (fgets(line, sizeof(line), fp) != NULL) {
        int tt;
        tt = sscanf(line,
              "%u %u %*d %64s %*u %*u %*u %*u %*u %*u %*u %*u %*u %*u %*u",
              &major,
              &minor,
              path);
        if (tt != 3) {
            continue;
        }
        if (!strcmp(path, lnkpath)) {
            found = 1;
            break;
        } 
    }
    fclose(fp);
    if (found) {
        return MKDEV(major, minor);
    }
    return 0;
}

/*
 * Setup initial directories structure on the filesystem
 */
static int
setup_directories(disk_t *dsk)
{
    int i, j;

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "%s: setting up directories structure", 
             dsk->dev_name);

    for (i = 0; i <= 0xFF; i++) {
         char ipath[MAXPATHLEN];
         snprintf(ipath, sizeof(ipath), "%s/%02x", dsk->exportfs, i);
         if (mkdir(ipath, DISK_OMODE) < 0  && errno != EEXIST) {
             return (-1);
         }
         for (j = 0; j <= 0xFF; j++) {
              char jpath[MAXPATHLEN];
              snprintf(jpath, sizeof(jpath), "%s/%02x", ipath, j);
              if (mkdir(jpath, DISK_OMODE) < 0 && errno != EEXIST) {
                  return (-1);
              }
              disk_heartbeat();
         }
    }
    return (0);
}

