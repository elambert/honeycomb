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
 * Component = Mailbox network protocol
 * Synopsis  = network layer of the mailbox distribution
 */

#include <sys/types.h>
#include <stdio.h>
#include <unistd.h>
#include <netdb.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <errno.h>
#include <dirent.h>

#include "trace.h"
#include "conf.h"

/*
 * header sent to sync mailboxes
 */
#define MSG_SYNC "SYNC_NODES"
static char sync_id[10];

/*
 * Real path to local mailboxes
 */
static char local_mbpath[MAXPATHLEN];


/*
 * API - initializes network layer
 * Return the file desc of the multicast socket.
 * -1 in case of error.
 */
int
mb_net_init()
{
    struct sockaddr_in addr;
    struct ip_mreq     mreq;
    int                fd;
    int                ret;
    cmm_nodeid_t       nodeid;
    char               path[MAXPATHLEN];

    /* create the multicast socket */
    fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "mb_net_init: %s", strerror(errno));
        return (-1);
    }

    /* set up destination address */
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons(MBOXD_PORT);

    /* bind to receive address */
    if (bind(fd, (struct sockaddr *) &addr, sizeof(addr)) < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                  "mb_net_init bind: %s", strerror(errno));
        close(fd);
        return (-1);
    }

    /* request to join the multicast group */
    mreq.imr_multiaddr.s_addr = inet_addr(MBOXD_GROUP);
    mreq.imr_interface.s_addr = htonl(INADDR_ANY);
    if (setsockopt(fd, IPPROTO_IP, IP_ADD_MEMBERSHIP, 
                   &mreq, sizeof(mreq)) < 0) {
         cm_trace(CM_TRACE_LEVEL_ERROR, 
                  "mb_net_init setsockopt %s", strerror(errno));
         close(fd);
         return(-1);
    }

    /* build the node id sync request */
    if (cmm_node_getid(&nodeid) != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cannot retrieve nodeid. Exiting");
        close(fd);
        return (-1);
    }
    snprintf(sync_id, sizeof(sync_id), " %d", nodeid);

    /* Get real path to local mailboxes */
    snprintf(path, sizeof(path), "%s/0", MAILBOX_PATH);
    ret = readlink(path, local_mbpath, sizeof(local_mbpath));
    if (ret < 0 || ret >= (int)sizeof(local_mbpath)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cannot get path to local mailboxes",
                 strerror(errno));
        close(fd);
        return(-1);
    }

    local_mbpath[ret] = '\0';
    return (fd);
}

/*
 * API - disconnect network layer
 * Return -1 in case of error.
 */
int
mb_net_disconnect(int mcast_fd)
{
    struct ip_mreq     mreq;

    mreq.imr_multiaddr.s_addr = inet_addr(MBOXD_GROUP);
    mreq.imr_interface.s_addr = htonl(INADDR_ANY);

    if (setsockopt(mcast_fd, IPPROTO_IP, IP_DROP_MEMBERSHIP, 
                   &mreq, sizeof(mreq)) < 0) {
         cm_trace(CM_TRACE_LEVEL_ERROR, 
                  "mb_net_disconnect setsockopt %s", strerror(errno));
    }
    close(mcast_fd);
    return (0);
}

/*
 * API - publishes local mailboxes among the cell
 * Return 0 in case of success. -1 otherwise.
 */
int
mb_net_publish()
{
    struct sockaddr_in addr;
    int                fd;
    DIR               *mbdir;
    struct dirent     *dp;
    struct msghdr      hdr;
    struct iovec       iov[2];


    fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "mb_net_publish: %s", strerror(errno));
        return (-1);
    }

    /* set up destination address */
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = inet_addr(MBOXD_GROUP);
    addr.sin_port = htons(MBOXD_PORT);

    /* open local mailboxes */
    mbdir = opendir(local_mbpath);
    if (mbdir == NULL) {
        close(fd);
        return (-1);
    }

    hdr.msg_name       = (void*) &addr;
    hdr.msg_namelen    = sizeof(addr);
    hdr.msg_iov        = iov;
    hdr.msg_iovlen     = 2;
    hdr.msg_control    = NULL;
    hdr.msg_controllen = 0;
    hdr.msg_flags      = 0;

    while ((dp = readdir(mbdir)) != NULL) {
        /*
         * Multicast this mailbox -
         */
        mb_id_t      mbid;
        mb_handle_t *mbhdl;
        char         mbtag[MAXPATHLEN];

        /* If the entry is . or .. skip */
        if ((!strcmp(dp->d_name, ".")) || (!strcmp(dp->d_name, ".."))) {
            continue;
        }

        /* try to open the mailbox */
        snprintf(mbtag, sizeof(mbtag), "%s/%s", local_mbpath, dp->d_name);
        mbid = mb_open(&mbtag[strlen(MAILBOX_PATH)]);
        if (mbid == MB_INVALID_ID) {
            continue;
        }
        mbhdl = (mb_handle_t*) mbid;

        /* Lock this mailbox */
        if (lockf(mbhdl->fd, F_LOCK, 0) == -1) {
            mb_close(mbid);
            continue;
        }
        iov[0].iov_base = mbtag;
        iov[0].iov_len  = strlen(mbtag) + 1;
        iov[1].iov_base = mbhdl->maddr;
        iov[1].iov_len  = mb_len(mbid) + sizeof(mb_hdr_t);
        (void) sendmsg(fd, &hdr, 0);
        
        /* Unlock the mailbox and close it */
        (void) lockf(mbhdl->fd, F_ULOCK, 0);
        mb_close(mbid);
    }

    close(fd);
    closedir(mbdir);
    return (0);
}

/*
 * API - update the local view of a mailbox
 */
int
mb_net_update(int mcast_fd)
{
    int                 nbytes;
    struct sockaddr     saddr;
    socklen_t           slen;
    char                buf[MBOXD_MAXSIZE];
    char                mtag[MAXPATHLEN];
    struct stat         stbuf;
    mb_hdr_t            *mbhdr;
    int                 mlen;
    size_t              msize;
    int                 mbfd;
    int                 ret;

    slen = sizeof(saddr);
    nbytes = recvfrom(mcast_fd, 
                      buf, sizeof(buf), 
                      0, 
                      &saddr, 
                      &slen);
    if (nbytes < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "mb_net_update: recfrom fails %s",
                 strerror(errno));
        return (-1);
    }

    /* Get and verify the mailbox path */
    ret = snprintf(mtag, sizeof(mtag), "%s", buf);
    cm_trace(CM_TRACE_LEVEL_DEBUG, "mb_net_update(%s)", mtag);

    if (ret < 0 || ret >= (int)sizeof(mtag)) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "mb_net_update: bad msg");
        return (-1);
    }

    /* check for the sync msg */
    if (strstr(mtag, MSG_SYNC) != NULL) {
        if (strstr(mtag, sync_id) != NULL) {
            return (1);
        }
        return (0);
    }

    if (strstr(mtag, MAILBOX_PATH) == NULL) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "mb_net_update: bad mailbox tag");
        return (-1);
    }

    if (strstr(mtag, local_mbpath) != NULL) {
        /* this originated from this node */
        return (0);
    }

    /* 
     * update the mailbox 
     */
    mlen  = strlen(mtag) + 1;
    msize = nbytes - mlen;
    mbhdr = (mb_hdr_t*) &buf[mlen];

    /* check for correct mailbox size */
    if (mbhdr->ttSize != msize) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "mb_net_update: bad mailbox size");
        return (-1);
    }

    /* create the node id directory if needed */
    if (stat(mtag, &stbuf) == -1 && errno == ENOENT) {
        char  path[MAXPATHLEN];
        char *dir;

        strcpy(path, mtag);
        if ((dir = strrchr(path, '/')) == NULL) {
            return (-1);
        }
        *dir = '\0';
        (void) mkdir(path, S_IRWXU|S_IRWXG|S_IRWXO);
    }

    /* open/create the mailbox, lock it and update its content */
    mbfd = open(mtag, O_RDWR | O_CREAT, MAILBOX_MODE);
    if (mbfd == -1) {
        return (-1);
    }
    if (lockf(mbfd, F_LOCK, 0) == -1) {
        close(mbfd);
        return (-1);
    }
    if (write(mbfd, &buf[mlen], msize) != (int)msize) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "mb_net_update: short write %s", strerror(errno));
    }

    /* Unlock the mailbox and close it */
    (void) lockf(mbfd, F_ULOCK, 0);
    close(mbfd);
    
    return (0);
}

/*
 * API - delete all mailboxes for the given node id
 */
int
mb_net_delete(cmm_nodeid_t nodeid)
{
    char path [MAXPATHLEN];
    char npath[MAXPATHLEN];
    DIR           *dir;
    struct dirent *dp;
    
    /* rename the dir first */
    snprintf(path, sizeof(path), "%s/%d", MAILBOX_PATH, nodeid);
    snprintf(npath, sizeof(npath), "%s.old", path);
    if (rename(path, npath) == -1) {
        return (-1);
    }
    
    /* go through the directory and unlink everything */
    dir = opendir(npath);
    if (dir == NULL) {
        return (-1);
    }
    while ((dp = readdir(dir)) != NULL) {
        char        mbox[MAXPATHLEN];
        mb_hdr_t    mbhdr;
        int         fd;

        if (strcmp(dp->d_name, ".") == 0) {
            continue;
        }
        if (strcmp(dp->d_name, "..") == 0) {
            continue;
        }
        snprintf(mbox, sizeof(mbox), "%s/%s", npath,  dp->d_name);
        fd = open(mbox, O_RDWR, MAILBOX_MODE);
        if (fd >= 0) { 
            if (read(fd, &mbhdr, sizeof(mbhdr) == sizeof(mbhdr))) {
                /* mark the state as DISABLED */
                mbhdr.curState = SRV_DISABLED;
                lseek(fd, -1, SEEK_SET);
                write(fd, &mbhdr, sizeof(mbhdr));
            }
            close(fd);
        }
        (void) unlink(mbox);
    }

    /* finally delete this directory */
    closedir(dir);
    if (rmdir(npath) == -1) {
        return (-1);
    }
    return (0);
}

/*
 * API - check and request to sync mailboxes
 */
int
mb_net_sync()
{
    uint32_t      count;
    uint32_t      rcount;
    int           missing;
    int           clen;
    cmm_member_t *members_table;
    char          msg[MAXPATHLEN];

    /* get the actual number of nodes in the cell */
    if (cmm_member_getcount(&count) != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cmm_member_getcount failed [mb_net_sync]");
        return (-1);
    }

    /* get info for all nodes */
    members_table = (cmm_member_t*) malloc(count * sizeof(cmm_member_t));
    if (members_table == NULL) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Not enough memory [mb_net_sync]");
        return (-1);
    }
    if (cmm_member_getall(count, members_table, &rcount) != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cmm_member_getall failed [mb_net_sync]");
        free(members_table);
        return (-1);
    }

    /* 
     * for every nodeid, verify we have its mailboxes.
     * If not, send a request to get them now.
     */
    clen = snprintf(msg, sizeof(msg), "%s", MSG_SYNC);
    for (count = 0, missing = 0; count < rcount; count++) {
         struct stat bufst;
         char        path[MAXPATHLEN];

         cmm_nodeid_t nodeid = members_table[count].nodeid;
         if (cmm_member_isoutofcluster(&members_table[count])) {
             continue;
         }
         snprintf(path, sizeof(path), "%s/%d", MAILBOX_PATH, nodeid);
         if (stat(path, &bufst) == -1 && errno == ENOENT) {
             int ret = snprintf(&msg[clen], sizeof(msg) - clen, " %d", nodeid);
             if (ret < 0 || ret >= ((int) sizeof(msg) - clen)) {
                 break;
             }
             clen += ret;
             missing++;
         }
    }

    free(members_table);
    if (missing) {
        struct sockaddr_in addr;
        int fd = socket(AF_INET, SOCK_DGRAM, 0);
        if (fd < 0) {
            return (-1);
        }
        cm_trace(CM_TRACE_LEVEL_NOTICE, "mb_net_sync(%s)", msg);

        memset(&addr, 0, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = inet_addr(MBOXD_GROUP);
        addr.sin_port = htons(MBOXD_PORT);

        (void)sendto(fd, msg, strlen(msg) + 1, 0, 
                     (struct sockaddr*) &addr, sizeof(addr));
        close(fd);
        return (1);
    }
    return (0);
}
