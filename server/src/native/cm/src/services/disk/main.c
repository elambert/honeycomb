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
 * Synopsis  = state machine of the disk server
 */

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <signal.h>
#include <sys/time.h>
#include <string.h>
#include <sys/poll.h>
#include <sys/socket.h>
#include <ctype.h>
#include <netdb.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <errno.h>
#include "hcdisk_adm.h"
#include "mbox.h"
#include "trace.h"
#include "mbdisk.h"
#include "disk.h"


static const cm_trace_level_t log_level =
#ifdef DEBUG
  CM_TRACE_LEVEL_DEBUG;
#else
  CM_TRACE_LEVEL_NOTICE;
#endif

/*
 * Interval for heartbeating in seconds
 */
static const long hbt_disk_interval = 2;

/*
 * Interval for getting disk statistics 
 * (in seconds)
 */
static const long disk_stat_interval = 5;

/*
 * Interval for fetching disk SMART values
 * (in seconds)
 */
static const long disk_smart_interval = 60;

/*
 * Disks managed
 */
static int    nb_disks;
static disk_t disks[DISKSERVER_MAXDISKS];
static cmm_memberaddr_t node_ip;

/*
 * Mailbox handle and name
 */
static mb_id_t mbox_id;
static char   *mbox_name;


/*
 * Mailbox update routine 
 */
static void 
update_mailbox()
{
   int      i;
   int      nb;
   mbdisk_t mbdisk;

   for (i = 0, nb = 0; i < nb_disks; i++) {
       /* Commented out since Layout now handles these alright
        * Fill up the mbdisk structure
        */
       
       /*
         if (uuid_is_null(disks[i].disk_id)) {
         continue;
         }
         if (disks[i].status == DISK_INIT) {
         continue;
         }
       */
       
       memcpy(mbdisk.dsks[nb].disk_id, disks[i].disk_id, sizeof(uuid_t));
       if (disks[i].status == DISK_OK) {
	   mbdisk.dsks[nb].disk_status = MBDISK_OK;
       } else {
	   mbdisk.dsks[nb].disk_status = MBDISK_BAD;
       }
       mbdisk.dsks[nb].disk_index  = i;
       mbdisk.dsks[nb].disk_size   = disks[i].stats.total_size;
       mbdisk.dsks[nb].avail_size  = disks[i].stats.avail_size;
       mbdisk.dsks[nb].readkbs     = disks[i].stats.read_kBs;
       mbdisk.dsks[nb].writekbs    = disks[i].stats.write_kBs;
       mbdisk.dsks[nb].iotime      = disks[i].stats.io_wait;
       mbdisk.dsks[nb].temperature = disks[i].stats.temperature;
       mbdisk.dsks[nb].bad_sectors = disks[i].stats.sectors_reallocated;
       mbdisk.dsks[nb].pending_sectors = disks[i].stats.pending_reallocated;

       snprintf(mbdisk.dsks[nb].disk_devname,
                sizeof(mbdisk.dsks[nb].disk_devname),
                "%s",
                disks[i].dev_name);

       snprintf(mbdisk.dsks[nb].mount_point, 
		sizeof(mbdisk.dsks[nb].mount_point),
		"%s:%s", 
		node_ip, 
		disks[i].exportfs);

       nb++;
    }
    mbdisk.nb_entries = nb;
    /*
     * Write the mailbox
     */
    if (mbdisk_write(mbox_id, &mbdisk) == -1) { 
       cm_trace(CM_TRACE_LEVEL_ERROR,
                "cannot update mailbox. Service disabled");
       mb_setstate(mbox_id, SRV_DISABLED);
    } 
}

/*
 * Mailbox Callback -
 * State machine of the disk server
 */
static void
disk_callback(mb_id_t mbid, mb_action_t action)
{
    int i;
    mb_state_t new_state;


    switch (action) {
    case ACT_INIT:
        /*
         * Disks initialization
         */
        for (i = 0; i < nb_disks; i++) {
             if (disk_init(&disks[i]) == -1) {
                 cm_trace(CM_TRACE_LEVEL_ERROR,
                          "disk %s failed to initialize ", disks[i].dev_name);
                 disks[i].status = DISK_BAD;
             } else {
                 cm_trace(CM_TRACE_LEVEL_NOTICE, "disk %s is ready", 
                          disks[i].dev_name);
                 disks[i].status = DISK_INIT;
             }
        }
        new_state = SRV_READY;
        break;

    case ACT_START:
        /*
         * Start the disks
         */
        for (i = 0; i < nb_disks; i++) { 
            if (disks[i].status != DISK_INIT) {
               /* Disk should be in init state */
               continue;
            }
            if (disk_start(&disks[i]) == -1) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "disk %s failed to start", disks[i].dev_name);
                disks[i].status = DISK_BAD;
            } else if (disk_smart(&disks[i]) == 1) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                          "disk %s failed to start. SMART error",
                             disks[i].dev_name);
                disk_stop(&disks[i]);
                disks[i].status = DISK_BAD;
            } else {
                disk_stat(&disks[i]);
                cm_trace(CM_TRACE_LEVEL_NOTICE, "disk %s is started", 
                         disks[i].dev_name);
                disks[i].status = DISK_OK;
            }
        }
        new_state = SRV_RUNNING;
        break;


    case ACT_STOP:
        /*
         * Stop the disks
         */
        for (i = 0; i < nb_disks; i++) { 
            if (disks[i].status != DISK_OK) {
               /* disk should be in running state */  
               continue;
            }
            if (disk_stop(&disks[i]) == -1) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "disk %s failed to stop", disks[i].dev_name);
                disks[i].status = DISK_BAD;
            } else {
                cm_trace(CM_TRACE_LEVEL_NOTICE, "disk %s is stopped", 
                         disks[i].dev_name);
                disks[i].status = DISK_STOP;
            }
        }
        new_state = SRV_READY;
        break;

    case ACT_DESTROY:
        /*
         * Destroy the disks server 
         */
        cm_trace(CM_TRACE_LEVEL_DEBUG, "disk server exits");
        exit(0);

    default:
        new_state = SRV_DISABLED;   
        cm_trace(CM_TRACE_LEVEL_ERROR, "unknown callback action %d", action);
        break;
    }

    /*
     * Update DiskServer mailbox
     */
    update_mailbox();
    mb_setstate(mbid, new_state);
}

/*
 * build a in_addr socket
 */
static int
get_host_address(char* host,
                 int port,
                 struct sockaddr_in* addr)
{
    bzero((char*)addr, sizeof (struct sockaddr_in));

    if (host == NULL) {
        addr->sin_addr.s_addr = INADDR_ANY;
        addr->sin_family = AF_INET;
    } else {
        struct hostent* hp;
        if ((hp = gethostbyname(host)) == NULL) {
            if (isdigit(host[0])) {
                addr->sin_addr.s_addr = inet_addr(host);
                addr->sin_family = AF_INET;
            } else {
                endhostent();
                return(1);
            }
        } else {
            addr->sin_family = hp->h_addrtype;
            (void) memcpy((char*) &addr->sin_addr,
                          hp->h_addr, hp->h_length);
        }
        endhostent();
    }
    addr->sin_port = htons(port);
    return(0);
}

static void
disk_poll()
{
    static time_t tmlast_stat_update  = 0;
    static time_t tmlast_smart_update = 0;
    int    update = 0;
    int    brdcast = 0;
    time_t tmcur;
    int    i;

    tmcur = time(NULL);
    disk_heartbeat();

    if (tmcur - tmlast_stat_update >= disk_stat_interval) { 
        for (i = 0; i < nb_disks; i++) {
             if (disks[i].status != DISK_OK) {
                 continue;
             }
             disk_stat(&disks[i]);
             disk_heartbeat();
        }
        update = 1;
        tmlast_stat_update = tmcur;
    }
#if 0
    /*
     * SMART is disabled for now
     */
    if (tmcur - tmlast_smart_update >= disk_smart_interval) {
        for (i = 0; i < nb_disks; i++) {
             if (disks[i].status != DISK_OK) {
                 continue;
             }
             if (disk_smart(&disks[i]) == 1) {
                 disk_stop(&disks[i]);
                 disks[i].status = DISK_BAD;
                 brdcast = 1;
             }
             disk_heartbeat();
        }
        update = 1;
        tmlast_smart_update = tmcur;
    }
#endif

    if (update) {
        update_mailbox();
        if (brdcast) {
            mb_broadcast(mbox_id);
        }
    }
}

/*
 * Process administrative commands
 */
static void
disk_adm(int socket)
{
    socklen_t           slen;
    struct sockaddr_in  addr;
    hcdisk_adm_cmd_t    cmd;
    hcdisk_adm_status_t status;
    int                 client;
    int                 ret;
    int                 i;

    slen = sizeof(addr);
    client = accept(socket, (struct sockaddr*) &addr, &slen);
    if (client < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "[disk_adm] accept failed: %s", strerror(errno));
        return;
    }

    ret = read(client, &cmd, sizeof(cmd));
    if (ret < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "[disk_adm] failed to read request: %s", strerror(errno));
        close(client);
        return;
    }
    cmd.params[HCDISK_ADM_MAXPARAMS - 1] = '\0'; 
    status = HCDISK_ADM_ERR;
     
    switch (cmd.operation) {
 
    case HCDISK_ADM_START:
         /*
          * start the specified disks
          */
         for (i = 0; i < nb_disks; i++) {
             if (!strstr(cmd.params, disks[i].dev_name)) {
                 continue;
             }
             if (disks[i].status == DISK_OK) {
                 cm_trace(CM_TRACE_LEVEL_NOTICE,
                          "[disk_adm] disk %s already started. Skipping",
                          disks[i].dev_name);
                 continue;
             }
             if (disk_start(&disks[i]) < 0) {
                 cm_trace(CM_TRACE_LEVEL_ERROR,
                          "[disk_adm] failed to start disk %s", 
                          disks[i].dev_name);
             } else {
                 disks[i].status = DISK_OK;
                 cm_trace(CM_TRACE_LEVEL_NOTICE,
                          "[disk_adm] disk %s started", disks[i].dev_name);
                 status = HCDISK_ADM_OK;
             }
         }
         break;

    case HCDISK_ADM_STOP:
         /*
          * stop the specified disks
          */
         for (i = 0; i < nb_disks; i++) {
             if (!strstr(cmd.params, disks[i].dev_name)) {
                 continue;
             }
             if (disks[i].status != DISK_OK) {
                 cm_trace(CM_TRACE_LEVEL_NOTICE,
                          "[disk_adm] disk %s already stopped. Skipping",
                          disks[i].dev_name);
                 continue;
             }
             if (disk_stop(&disks[i]) < 0) {
                 cm_trace(CM_TRACE_LEVEL_ERROR,
                          "[disk_adm] fail to stop disk %s", 
                          disks[i].dev_name);
             } else {
                 disks[i].status = DISK_BAD;
                 cm_trace(CM_TRACE_LEVEL_NOTICE,
                          "[disk_adm] disk %s stopped", disks[i].dev_name);
                 status = HCDISK_ADM_OK;
             } 
         }
         break;

    case HCDISK_ADM_SMART:
         /*
          * run SMART on the specified disks.
          */
         for (i = 0; i < nb_disks; i++) {
             if (!strstr(cmd.params, disks[i].dev_name)) {
                 continue;
             }
             if (disks[i].status != DISK_OK) {
                 cm_trace(CM_TRACE_LEVEL_NOTICE,
                          "[disk_adm] disk %s is not running. Skipping",
                          disks[i].dev_name);
                 continue;
             }
             disk_smart(&disks[i]);
             status = HCDISK_ADM_OK;
         }
         break;

    default:
         cm_trace(CM_TRACE_LEVEL_ERROR,
                  "[disk_adm] unknown command %d", cmd.operation);
         break;
    }

    write(client, &status, sizeof(status));
    close(client);
    update_mailbox();
    mb_broadcast(mbox_id);
}

/*
 * Mailbox heartbeat routine
 */
void
disk_heartbeat(void)
{
    mb_hbt(mbox_id, NULL);
}


static void
usage(const char* prgname)
{
    fprintf(stderr, "%s -d <device>\n", prgname);
    fprintf(stderr, "\t<device>: block device for this disk\n");
}


int
main(int argc, char **argv)
{
    cmm_nodeid_t       nodeid;
    cmm_member_t       cmminfo;
    struct sockaddr_in addr;
    int c;
    int sadm;

    cm_openlog("DiskServer", log_level);

    /* retrieve IP address for this node */
    if (cmm_node_getid(&nodeid) != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "cannot retrieve nodeid. Exiting");
        return (1);
    }
    if (cmm_member_getinfo(nodeid, &cmminfo) != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "cannot retrieve nodeid info. Exiting");
        return (1);
    }
    strncpy(node_ip, cmminfo.addr, sizeof(node_ip));

    while ((c = getopt(argc, argv, "d:")) != -1) {
        switch (c) {
        case 'd':
            /*
             * New disk to manage
             */
            if (nb_disks >= DISKSERVER_MAXDISKS) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "too many disks defined. Exiting");
                return (1);
            }
            if (disk_setup(argv[optind-1], &disks[nb_disks]) < 0) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "disk_setup failed for %s", argv[optind-1]);
            } else { 
                nb_disks++;
            } 
            break;

        default:
            usage(argv[0]);
            return (1);
       } 
    }

    mbox_name = getenv("HC_MAILBOX");
    if (mbox_name == NULL || nb_disks == 0) { 
        usage(argv[0]);
        return (1);
    }
  
    /*
     * Initializes & update the DiskServer mailbox
     */
    mbox_id= mb_init(mbox_name, disk_callback);
    if (mbox_id == MB_INVALID_ID) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "mailbox %s initialization failed. Exiting",
                 mbox_name);
        return (1);
    }

    /*
     * Initializes administrative connection
     */
    sadm = socket(AF_INET, SOCK_STREAM, 0);
    if (sadm < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "cannot create adm socket: %s. Exiting",
                 strerror(errno));
        mb_close(mbox_id);
        return (1);
    }
    if (get_host_address(NULL, HCDISK_ADM_PORT, &addr)) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "get_host_address failed");
        close(sadm);
        mb_close(mbox_id);
        return (1);
    }
    if (bind(sadm, (struct sockaddr*) &addr, sizeof(addr)) < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "bind failed for adm socket: %s. Exiting",
                 strerror(errno));
        close(sadm);
        mb_close(mbox_id);
        return (1);
    }
    if (listen(sadm, 1) < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "listen failed for adm socket: %s",
                 strerror(errno));
        close(sadm);
        mb_close(mbox_id);
        return (1);
    }
   
    /*
     * Start heartbeating, periodically checking the disks
     * and processing adm commands.
     */
    cm_trace(CM_TRACE_LEVEL_DEBUG, "disk server is initialized");
    while (1) {
        int ret;
        struct pollfd fds[1];
     
        fds[0].fd = sadm;
        fds[0].events = POLLIN;
        fds[0].revents = 0;

        ret = poll(fds, 1, hbt_disk_interval * 1000);
        if (ret == 0) {
            disk_poll();
        } else if (ret > 0) {
            disk_adm(sadm);
        }
    }

    /* NEVER REACHED */
    return 0;
}
