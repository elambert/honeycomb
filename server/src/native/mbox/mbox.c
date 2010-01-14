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
 * Component = Mailbox service
 * Synopsis  = client native library
 */ 

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/mman.h>
#include <strings.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <netdb.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <dirent.h>
#include <syslog.h>

#include "mbox.h"
#include "conf.h"

/*
 * type of lock operation.
 */
typedef enum {
    waitlock = 1,
    trylock  = 2
} mb_locktype_td;

static mb_handle_t* mbox_init(const char*, int, int);
static int          mbox_net_send(mb_hdr_t*);
static int          mbox_lock(const char* tag, int fd, mb_locktype_td wait);
static int          mbox_unlock(int fd);
static int          check_dir(char*, char*);
static void         set_errno(int);


#define MB_BAD(x) \
  ((x) == NULL || \
  ((unsigned long) (x) & (sizeof(mb_handle_t*) - 1)) || \
  (x)->valid != MB_VALID)


/***************************************
 * API - setup the mailbox directory
 * 
 * IN:
 *  nodeid      - node id for this node
 *  create_link - not 0 if symbolic link
  *               needs to be created
 ***************************************/
int
mb_init_mailboxes(int nodeid, int create_link)
{
    char buffer[MAXPATHLEN];
    char localnode[MAXPATHLEN];
    int err;

    err = check_dir(MAILBOX_PATH, NULL);
    if (err) {
        return 1;
    }
    snprintf(buffer, sizeof(buffer), "%s/%d",
             MAILBOX_PATH, nodeid);

    err = check_dir(buffer, NULL);
    if (err) {
        return(1);
    }

    if (!create_link) {
        return(0);
    }

    /* Create the link */

    snprintf(localnode, sizeof(buffer), "%s/0",
             MAILBOX_PATH);
    
    err = check_dir(localnode, buffer);
    if (err) {
        return(1);
    }
    return(0);
}

/*******************************************
 * API - create a new service mailbox
 * A mailbox is created in SRV_INIT state
 *
 * IN:
 *  name - tag name of the mailbox
 *  size - size of the mailbox
 *******************************************/
mb_id_t
mb_create(const char* tag,
          size_t size)
{
    mb_handle_t* mbhdl;
    mb_hdr_t*    mbhdr;
    char         mbname[MAILBOX_TAG_MAXLEN];
    long         pagesize;
    int          ret;
    struct timeval tv;
    const char   c = 0;

    /*
     * bad tag name length 
    */
    ret = snprintf(mbname, sizeof(mbname), "%s/%s", MAILBOX_PATH, tag);
    if (ret < 0 || ret > (int)sizeof(mbname)) {
        return (MB_INVALID_ID);
    }

    /*
     * Try to allocate the mailbox handle
     */
    mbhdl = (mb_handle_t*) malloc(sizeof(mb_handle_t));
    if (mbhdl == NULL) {
        return (MB_INVALID_ID);
    }

    /*
     * Create the corresponding tmpfs file
     */
 
    /* Create the mailbox file */
    mbhdl->fd = open(mbname, O_RDWR | O_CREAT | O_TRUNC, MAILBOX_MODE);
    if (mbhdl->fd == -1) {
        free(mbhdl);
        return(MB_INVALID_ID);
    }

    // This is more reliable than unsetting/resetting the umask
    fchown (mbhdl->fd, -1, 1000); // TODO: Don't hardcode the grp value
    fchmod (mbhdl->fd, MAILBOX_MODE);
    fcntl (mbhdl->fd, F_SETFD, FD_CLOEXEC);

    size += sizeof(mb_hdr_t);
    pagesize = sysconf(_SC_PAGE_SIZE);
    size = (size & ~(pagesize -1)) + pagesize;
    lseek(mbhdl->fd, size - 1, SEEK_SET);
    write(mbhdl->fd, &c, 1);

    /*
     * Map the mailbox in caller address space
     */
    mbhdl->maddr = mmap(NULL,
                        size,
                        PROT_READ | PROT_WRITE,
                        MAP_SHARED,
                        mbhdl->fd,
                        0);


    if (mbhdl->maddr == MAP_FAILED) {
        close(mbhdl->fd);
        free(mbhdl);
        (void) unlink(mbname);
        return (MB_INVALID_ID);
    }

    /*
     * Initialize mailbox header
     */
    mbhdr = (mb_hdr_t*) mbhdl->maddr;
    mbhdl->valid     = MB_VALID;
    mbhdl->flags     = MB_CREATOR;
    mbhdr->version   = MAILBOX_VER;
    mbhdr->ttSize    = size;
    mbhdr->curSize   = 0;
    mbhdr->curWid    = 0;
    mbhdr->usrClbk   = NULL;
    mbhdr->curAction = ACT_VOID;
    mbhdr->curState  = SRV_INIT;
    mbhdr->rqstState = SRV_INVALID;

    if (gettimeofday(&tv, NULL) == 0) {
        mbhdr->tmStamp = tv.tv_sec;
    }
    strncpy(mbhdr->mboxTag, mbname, sizeof(mbhdr->mboxTag));
    /* Do this for persistent mailbox
     * msync(mbhdl->maddr, mbhdr->ttSize, MS_SYNC); */

    /* multicast this mailbox */
    mbox_net_send(mbhdr);

    return (mb_id_t) (mbhdl);
}


/****************************************************
 * API - remove the given mailbox from the namespace
 *
 * IN:
 *  name - tag name of the mailbox
 ***************************************************/
mb_error_t
mb_unlink(const char* tag)
{
    char mbname[MAXPATHLEN];
    /*
     * Unlink the mailbox
     */
    snprintf(mbname, sizeof(mbname), "%s/%s", MAILBOX_PATH, tag);
    if (unlink(mbname) == -1) {
        return MB_ERROR;
    }
    return MB_OK;
}


/***************************************************
 * API - initialize an existing mailbox
 *
 * IN:
 *  name     - tag name of the mailbox
 *  callback - user callback triggered when a state
 *             change is requested
 ***************************************************/
mb_id_t
mb_init(const char* tag, mb_callback_t usrClbk)
{
    mb_handle_t* mbhdl;

    mbhdl = mbox_init(tag, O_RDWR, PROT_READ | PROT_WRITE);
    if (mbhdl != MB_INVALID_ID) {
        struct timeval tv;
        /*
         * Initialize mailbox header
         */
        mbhdl->flags = MB_OWNER;
        ((mb_hdr_t*)mbhdl->maddr)->usrClbk = usrClbk;
        ((mb_hdr_t*)mbhdl->maddr)->curAction = ACT_VOID;
        ((mb_hdr_t*)mbhdl->maddr)->pidOwner = getpid();
        if (gettimeofday(&tv, NULL) == 0) {
            ((mb_hdr_t*)mbhdl->maddr)->tmStamp = tv.tv_sec;
        } else {
            ((mb_hdr_t*)mbhdl->maddr)->tmStamp = 0;
        }
    }
    return (mb_id_t) (mbhdl);
}


/************************************************
 * API - mailbox heartbeat routine
 * IN:
 *  mb_id  - handle for the mailbox
 * OUT:
 *  act - if not null, contains the pending action 
 ************************************************/
mb_error_t
mb_hbt(mb_id_t mb_id, mb_action_t* action)
{
    mb_handle_t*   mbhdl = (mb_handle_t*) mb_id;
    mb_action_t    act   = ACT_VOID;
    mb_hdr_t*      mbhdr;
    struct timeval tv;
    int            ret;

    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }

    /* 
     * Only owner can heartbeat
     */
    if ((mbhdl->flags & MB_OWNER) == 0) {
        set_errno(EPERM);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;

    /*
     * Try to lock the mailbox
     * don't yell if it is already locked.
     */

    ret = mbox_lock(mbhdr->mboxTag, mbhdl->fd, trylock);
    if (ret > 0) {
        if (action != NULL) {
            *action = ACT_VOID;
        }
        return (MB_OK);

    } else if (ret < 0) {
        return (MB_ERROR);
    }
  
    /*
     * Update client time stamp
     */
    if (gettimeofday(&tv, NULL) == 0) {
        mbhdr->tmStamp = tv.tv_sec;
    }

    /*
     * Check for a pending requested state
     */
    if (mbhdr->curAction == ACT_VOID) {
         switch (mbhdr->rqstState) {

         case SRV_READY:
             if (mbhdr->curState == SRV_INIT) {
                 act = ACT_INIT;
             } else if (mbhdr->curState == SRV_RUNNING) {
                 act = ACT_STOP;
             }
             break;

         case SRV_RUNNING:
             if (mbhdr->curState == SRV_READY) {
                 act = ACT_START;
             }
             break;

         case SRV_DESTROY:
             if (mbhdr->curState != SRV_DISABLED) {
                 act = ACT_DESTROY;
             }
             break;

         default:
             break;
         }
         if (act != ACT_VOID) {
             mbhdr->curAction = act;
             mbhdl->flags |= MB_CALLBACK;
         }
    }

    /*
     * unlock mailbox, update pstate and
     * possibly trigger callback
     */

    (void) mbox_unlock(mbhdl->fd);
    if (action != NULL) {
        *action= act;
    }
    if (mbhdr->usrClbk != NULL && act != ACT_VOID) {
        (mbhdr->usrClbk) (mb_id, act);
    }

    return MB_OK;
}
 

/***************************************
 * API - Open a mailbox for read access
 * IN:
 *  name - tag name of the mailbox
 ***************************************/
mb_id_t
mb_open(const char* tag)
{
    mb_handle_t* mbhdl;

    /*
     * Note: write access is needed to get exclusive lock.
     */
    mbhdl = mbox_init(tag, O_RDWR, PROT_READ | PROT_WRITE);
    if (mbhdl != MB_INVALID_ID) {
        /*
         * Initialize mailbox header
         */
        mbhdl->flags = 0;
    }
    return (mb_id_t) (mbhdl);
}
    

/***********************************
 * API - Close a mailbox
 * IN:
 *  mb_id - handle for the mailbox
 ***********************************/
mb_error_t
mb_close(mb_id_t mb_id)
{
    mb_handle_t* mbhdl;
    mb_hdr_t*    mbhdr;

    mbhdl = (mb_handle_t*) mb_id;
    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;

    if (mbox_lock(mbhdr->mboxTag, mbhdl->fd, waitlock) < 0) {
        return (MB_ERROR);
    }


    munmap(mbhdl->maddr, mbhdr->ttSize);
    mbhdl->valid = 0;
    close(mbhdl->fd);
    free(mbhdl);

    return (MB_OK);
}


/*********************************************
 * API - get current state of a mailbox
 * IN:
 *  mb_id - handle for the mailbox
 * OUT:
 *  state - current mailbox state
 **********************************************/
mb_error_t
mb_getstate(mb_id_t mb_id, mb_state_t* state)
{
    mb_handle_t* mbhdl;
    mb_hdr_t*    mbhdr;

    mbhdl = (mb_handle_t*) mb_id;
    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;
    *state = mbhdr->curState;

    return (MB_OK);
}
    

/*********************************************
 * API - get requested state of a mailbox
 * IN:
 *  mb_id - handle for the mailbox
 * OUT:
 *  state - current mailbox state
 **********************************************/
mb_error_t
mb_getexpectedstate(mb_id_t mb_id, mb_state_t* state)
{
    mb_handle_t* mbhdl;
    mb_hdr_t*    mbhdr;

    mbhdl = (mb_handle_t*) mb_id;
    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;

    *state = mbhdr->rqstState;

    return (MB_OK);
}


/**********************************************
 * API - set current state of a mailbox
 * IN:
 *  mb_id - handle for the mailbox
 *  state - current mailbox state
 ***********************************************/
mb_error_t
mb_setstate(mb_id_t mb_id, mb_state_t state)
{
    mb_handle_t* mbhdl;
    mb_hdr_t*    mbhdr;

    mbhdl = (mb_handle_t*) mb_id;
    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;

    if (mbox_lock(mbhdr->mboxTag, mbhdl->fd, waitlock) < 0) {
        return (MB_ERROR);
    }
    mbhdr->curState = state;
    if (state == SRV_DISABLED ||
        (mbhdl->flags & (MB_OWNER|MB_CALLBACK)) == (MB_OWNER|MB_CALLBACK)) {
        mbhdr->curAction = ACT_VOID;
        mbhdl->flags &= ~MB_CALLBACK;
    }

    /* State changed - multicast this mailbox  */
    /* Do this for persistent mailbox
     * msync(mbhdl->maddr, mbhdr->ttSize, MS_SYNC);
     * mbox_net_send(mbhdr); */

    (void) mbox_unlock(mbhdl->fd);

    return (MB_OK);
}


/***********************************
 * API - set requested state
 * IN:
 *  mb_id - handle for the mailbox
 *  state - current mailbox state
 ***********************************/
mb_error_t
mb_setexpectedstate(mb_id_t mb_id, mb_state_t state)
{
    mb_handle_t* mbhdl;
    mb_hdr_t*    mbhdr;

    mbhdl = (mb_handle_t*) mb_id;
    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;
    mbhdr->rqstState = state;
    /* Do this for persistent mailbox
     * msync(mbhdl->maddr, mbhdr->ttSize, MS_SYNC); */

    return (MB_OK);
}

/***********************************
 * API - get timestamp
 * IN:
 *  mb_id - handle for the mailbox
 * OUT:
 *  timestamp - last timestamp value
 ***********************************/
 
mb_error_t
mb_gettimestamp(mb_id_t mb_id,
                struct timeval *timestamp)
{
    mb_handle_t* mbhdl;
    mb_hdr_t*    mbhdr;

    mbhdl = (mb_handle_t*) mb_id;
    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;

    timestamp->tv_sec = mbhdr->tmStamp;
    timestamp->tv_usec = 0;

    return (MB_OK);
}

/****************************************
 * API - read user data from a mailbox
 * IN:
 *  mb_id  - handle for the mailbox
 *  offset - offset within the mailbox from which 
 *           to start reading
 *  size   - size of the data
 * OUT:
 *  data   - user data
 *  uid    - a unique identifier for this mailbox
 ****************************************/
mb_error_t
mb_read(mb_id_t mb_id, void* data, off_t offset, size_t size, int* uid)
{
    mb_handle_t*      mbhdl;
    mb_hdr_t*         mbhdr;
    unsigned char*    saddr;

    mbhdl = (mb_handle_t*) mb_id;
    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;
    
    if (mbhdr->curState == SRV_DISABLED) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }

    if (offset + size + sizeof(mb_hdr_t) > mbhdr->ttSize) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }

    if (mbox_lock(mbhdr->mboxTag, mbhdl->fd, waitlock) < 0) {
        return (MB_ERROR);
    }
    saddr  = (unsigned char*) mbhdl->maddr;
    saddr += sizeof(mb_hdr_t) + offset;
    memcpy(data, saddr, size);
    *uid = mbhdr->curWid;

    (void) mbox_unlock(mbhdl->fd);

    return (MB_OK);
}
   

/****************************************
 * API - write user data to a mailbox
 * IN:
 *  mb_id  - handle for the mailbox
 *  offset - offset within the mailbox from which to start writing
 *  size   - size of the data
 *  data   - data
 ****************************************/
mb_error_t
mb_write(mb_id_t mb_id, void* data, off_t offset, size_t size)
{
    mb_handle_t*      mbhdl;
    mb_hdr_t*         mbhdr;
    unsigned char*    taddr;

    mbhdl = (mb_handle_t*) mb_id;
    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;

    if (offset + size + sizeof(mb_hdr_t) > mbhdr->ttSize) {
        set_errno(ENOSPC);
        return (MB_ERROR);
    }

    if (mbox_lock(mbhdr->mboxTag, mbhdl->fd, waitlock) < 0) {
        return (MB_ERROR);
    }
    taddr  = (unsigned char*) mbhdl->maddr;
    taddr += sizeof(mb_hdr_t) + offset;
    memcpy(taddr, data, size);
    mbhdr->curSize = size;
    mbhdr->curWid++;

    /* Do this for persistent mailbox
     * msync(mbhdl->maddr, mbhdr->ttSize, MS_SYNC); */

    (void) mbox_unlock(mbhdl->fd);

    return MB_OK;
}


/********************************************
 * API - return the data length of a mailbox
 * IN:
 *  mb_id  - handle for the mailbox
 ********************************************/
size_t
mb_len(mb_id_t mb_id)
{
    mb_handle_t*    mbhdl;
    mb_hdr_t*       mbhdr;

    mbhdl = (mb_handle_t*) mb_id;
    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;
    return mbhdr->curSize;
}

/********************************************
 * API - return the write counter
 * IN:
 *  mb_id  - handle for the mailbox
 ********************************************/
mb_error_t
mb_getversion(mb_id_t mb_id, int *version)
{
    mb_handle_t*    mbhdl;
    mb_hdr_t*       mbhdr;

    mbhdl = (mb_handle_t*) mb_id;
    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;
    if (mbhdr->curState == SRV_DISABLED) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }
    *version = mbhdr->curWid;
    return (MB_OK);
}

/********************************************
 * API - multicast the mailbox
 * IN:
 *  mb_id  - handle for the mailbox
 ********************************************/
mb_error_t
mb_broadcast(mb_id_t mb_id)
{
    mb_handle_t* mbhdl;
    mb_hdr_t*    mbhdr;

    mbhdl = (mb_handle_t*) mb_id;
    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;

    if (mbox_lock(mbhdr->mboxTag, mbhdl->fd, waitlock) < 0) {
        return (MB_ERROR);
    }
    mbox_net_send(mbhdr);
    
    (void) mbox_unlock(mbhdl->fd);

    return (MB_OK);
}

/*********************************************
 * API - multicast all the mailboxes
 *********************************************/
mb_error_t 
mb_net_publish() 
{
    static char lpath[MAILBOX_TAG_MAXLEN] = "\0";
    int                fd;
    struct sockaddr_in addr;
    struct msghdr      hdr;
    struct dirent     *dp;
    DIR               *mbdir;
    struct iovec       iov[2];
    mb_error_t         ret;

    /* initialize actual path to local mailboxes */
    if (lpath[0] == '\0') {
        char path[MAILBOX_TAG_MAXLEN];
        int  ret;
        snprintf(path, sizeof(path), "%s/0", MAILBOX_PATH);       
        ret = readlink(path, lpath, sizeof(lpath));
        if (ret < 0 || ret > (int)sizeof(lpath)) {
            return (-1);
        }
        lpath[ret] = '\0';
    }
     
    fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) {
        return MB_ERROR;
    }

    /* open local mailboxes */
    mbdir = opendir(lpath);
    if (mbdir == NULL) {
        close(fd);
        return MB_ERROR;
    }

    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = inet_addr(MBOXD_GROUP);
    addr.sin_port = htons(MBOXD_PORT);

    hdr.msg_name       = (void*) &addr;
    hdr.msg_namelen    = sizeof(addr);
    hdr.msg_iov        = iov;
    hdr.msg_iovlen     = 2;
    hdr.msg_control    = NULL    ;
    hdr.msg_controllen = 0;
    hdr.msg_flags      = 0;

    ret = MB_OK;
    while ((dp = readdir(mbdir)) != NULL) {
        /*
         * Multicast this mailbox -
         */
        mb_id_t      mbid;
        mb_handle_t *mbhdl;
        mb_hdr_t    *mbhdr;
        char         mbtag[MAILBOX_TAG_MAXLEN];

        /* If the entry is . or .. skip */
        if ((!strcmp(dp->d_name, ".")) || (!strcmp(dp->d_name, ".."))) {
            continue;
        }

        /* try to open the mailbox */
        snprintf(mbtag, sizeof(mbtag), "%s/%s", lpath, dp->d_name);
        mbid = mb_open(&mbtag[strlen(MAILBOX_PATH)]);
        if (mbid == MB_INVALID_ID) {
            continue;
        }
        mbhdl = (mb_handle_t*) mbid;
        mbhdr = (mb_hdr_t*) mbhdl->maddr;

        /* Lock this mailbox */

        if (mbox_lock(mbhdr->mboxTag, mbhdl->fd, waitlock) < 0) {
            mb_close(mbid);
            ret = MB_ERROR;
            break;
        }

        iov[0].iov_base = mbtag;
        iov[0].iov_len  = MAILBOX_TAG_MAXLEN;
        iov[1].iov_base = mbhdl->maddr;
        iov[1].iov_len  = mbhdr->curSize + sizeof(mb_hdr_t);
        (void) sendmsg(fd, &hdr, 0);
        
        /* Unlock the mailbox and close it */

        (void) mbox_unlock(mbhdl->fd);
        mb_close(mbid);
    }

    close(fd);
    closedir(mbdir);
    return ret;
}

/***************************************************************
 * API - mark all the mailboxes for the given node as disabled
 **************************************************************/
mb_error_t 
mb_disable_node(int nodeid) 
{
    char               path[MAXPATHLEN];
    struct dirent     *dp;
    DIR               *mbdir;
    int                ret;

    ret = snprintf(path, sizeof(path), "%s/%d", MAILBOX_PATH, nodeid);
    if (ret < 0 || ret > (int)sizeof(path)) {
        return (MB_ERROR);
    }

    /* open node mailboxes */
    mbdir = opendir(path);
    if (mbdir == NULL) {
        return MB_OK;
    }

    while ((dp = readdir(mbdir)) != NULL) {
        /*
         * Multicast this mailbox -
         */
        mb_handle_t *mbhdl;
        char    mbtag[MAILBOX_TAG_MAXLEN];

        /* If the entry is . or .. skip */
        if ((!strcmp(dp->d_name, ".")) || (!strcmp(dp->d_name, ".."))) {
            continue;
        }

        /* try to open the mailbox */
        ret = snprintf(mbtag, sizeof(mbtag), "%d/%s", nodeid, dp->d_name);
        if (ret < 0 || ret > (int)sizeof(mbtag)) {
            continue;
        }
        mbhdl = mbox_init(mbtag, O_RDWR, PROT_READ | PROT_WRITE);
        if (mbhdl == MB_INVALID_ID) {
            continue;
        }
        ((mb_hdr_t*)mbhdl->maddr)->curState = SRV_DISABLED;
        mb_close(mbhdl);
    }

    closedir(mbdir);
    return MB_OK;
}

/**********************************************
 * API - check for the existence of the mailbox
 **********************************************/
mb_error_t
mb_check(const char* tag) 
{
    char mbtag[MAILBOX_TAG_MAXLEN];
    struct stat stbuf;
    int  ret;

    ret = snprintf(mbtag, sizeof(mbtag), "%s/%s", MAILBOX_PATH, tag);
    if (ret < 0 || ret > (int)sizeof(mbtag)) {
        return MB_ERROR;
    }

    if (stat(mbtag, &stbuf) < 0) {
        return MB_ERROR;
    }
    return MB_OK;
}

/*******************************************************
 * API - process an incoming multicast and
 * update the local view of a remote mailbox
 * IN:
 *  the udp packet sent by mbox_net_send/broadcast API
 *******************************************************/
mb_error_t 
mb_net_copyout(unsigned char buf[], int nbytes)
{
    static char lpath[MAILBOX_TAG_MAXLEN] = "\0";
    char                mtag[MAILBOX_TAG_MAXLEN];
    struct stat         stbuf;
    int                 mlen;
    size_t              msize;
    int                 mbfd;
    int                 ret;
    
    /* Get and verify the mailbox path */
    ret = snprintf(mtag, sizeof(mtag), "%s", buf);
    if (ret < 0 || ret >= (int)sizeof(mtag)) {
        return MB_ERROR;
    }

    if (strstr(mtag, MAILBOX_PATH) == NULL) {
        return MB_ERROR;
    }

    /* initialize actual path to local mailboxes */
    if (lpath[0] == '\0') {
        char path[MAILBOX_TAG_MAXLEN];
        int  ret;
        snprintf(path, sizeof(path), "%s/0", MAILBOX_PATH);       
        ret = readlink(path, lpath, sizeof(lpath));
        if (ret < 0 || ret > (int)sizeof(lpath)) {
            return MB_ERROR;
        }
        lpath[ret] = '\0';
    }

    if (strstr(mtag, lpath) != NULL) {
        /* this originated from this node */
        return (MB_OK);
    }

    /* 
     * update the mailbox 
     */
    mlen  = MAILBOX_TAG_MAXLEN;
    msize = nbytes - mlen;

    /* create the node id directory if needed */
    if (stat(mtag, &stbuf) == -1 && errno == ENOENT) {
        char  path[MAXPATHLEN];
        char *dir;

        strcpy(path, mtag);
        if ((dir = strrchr(path, '/')) == NULL) {
            return MB_ERROR;
        }
        *dir = '\0';
        (void) mkdir(path, S_IRWXU|S_IRWXG|S_IRWXO);
        chmod (path, MAILBOX_DIR_MODE);
    }

    /* open/create the mailbox, lock it and update its content */
    mbfd = open(mtag, O_RDWR | O_CREAT, MAILBOX_MODE);
    if (mbfd == -1) {
        return MB_ERROR;
    }
    fchown (mbfd, -1, 1000); // TODO: don't hardcode the grp value
    fchmod (mbfd, MAILBOX_MODE);

    if (mbox_lock(mtag, mbfd, waitlock) < 0) {
        close(mbfd);
        return MB_ERROR;
    }
    if (write(mbfd, &buf[mlen], msize) == (int)msize) {
        ret = MB_OK;
    } else {
        ret = MB_ERROR;
    }

    /* Unlock the mailbox and close it */

    (void) mbox_unlock(mbfd);
    close(mbfd);

    return ret;
}

/*******************************************************
 * API - return the network length of the given mailbox
 * IN:
 *  the mailbox handle
 *******************************************************/
size_t
mb_net_len(mb_id_t mbid)
{
    mb_handle_t*    mbhdl;
    mb_hdr_t*       mbhdr;

    mbhdl = (mb_handle_t*) mbid;
    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return (-1);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;
    return mbhdr->curSize + sizeof(mb_hdr_t) + MAILBOX_TAG_MAXLEN;
}

/*******************************************************
 * API - return the network length of the given mailbox
 * IN:
 *  the mailbox tag
 *******************************************************/
mb_error_t
mb_net_copyin(mb_id_t mbid, unsigned char buf[], int len) 
{
    mb_handle_t*    mbhdl;
    mb_hdr_t*       mbhdr;
    mb_error_t      ret;

    mbhdl = (mb_handle_t*) mbid;
    if (MB_BAD(mbhdl)) {
        set_errno(EINVAL);
        return MB_ERROR;
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;

    if (mbox_lock(mbhdr->mboxTag, mbhdl->fd, waitlock) < 0) {
        return (MB_ERROR);
    }
    if (len >= mbhdr->curSize + sizeof(mb_hdr_t) + MAILBOX_TAG_MAXLEN) {
        bcopy(mbhdr->mboxTag, buf, MAILBOX_TAG_MAXLEN);
        bcopy(mbhdl->maddr, 
              &buf[MAILBOX_TAG_MAXLEN], 
              mbhdr->curSize + sizeof(mb_hdr_t));
        ret = MB_OK;
    } else {
        ret = MB_ERROR;
    }

    (void) mbox_unlock(mbhdl->fd);
    return ret;
}

/*******************************************************
 * Open an mbox and initialize the corresponding handle
 *******************************************************/
static mb_handle_t*
mbox_init(const char* tag, int oflags, int mflags)
{
    mb_handle_t* mbhdl;
    mb_hdr_t     mbhdr;
    char         mbname[MAXPATHLEN];

    /*
     * Try to allocate the mailbox handle
     */
    mbhdl = (mb_handle_t*) malloc(sizeof(mb_handle_t));
    if (mbhdl == NULL) {
        return (MB_INVALID_ID);
    }

    /*
     * Open the corresponding tmpfs file
     */
    snprintf(mbname, sizeof(mbname), "%s/%s", MAILBOX_PATH, tag);
    mbhdl->fd = open(mbname, oflags, MAILBOX_MODE);
    if (mbhdl->fd == -1) {
        free(mbhdl);
        return(MB_INVALID_ID);
    }

    /*
     * Read the mailbox header 
     */
    if (read(mbhdl->fd, &mbhdr, sizeof(mbhdr)) != sizeof(mbhdr)) {
        close(mbhdl->fd);
        free(mbhdl);
        return(MB_INVALID_ID);
    }
 
    /*
     * Map the mailbox in caller address space
     */
    mbhdl->maddr = mmap(NULL,
                        mbhdr.ttSize,
                        mflags,
                        MAP_SHARED,
                        mbhdl->fd,
                        0);

    if (mbhdl->maddr == MAP_FAILED) {
        close(mbhdl->fd);
        free(mbhdl);
        return (MB_INVALID_ID);
    }

    mbhdl->valid = MB_VALID;
    return mbhdl;
}

/**************************************************************
 * This routine checks that the given directory or link exists.
 * The directory or link is created if it doesn't exist
 * OUT:
 *  - 0 in case of success
 **************************************************************/
static int
check_dir(char *path, char *path_to_link_to)
{
    struct stat buf;
    int err;

    err = lstat(path, &buf);
    if (err) {
        if (errno != ENOENT) {
            return(1);
        }
    }

    if (!err) {
        /* Entry already exists */
       if (path_to_link_to) {
           if (S_ISLNK(buf.st_mode)) {
               return(0);
           }
       } else {
           if (S_ISDIR(buf.st_mode)) {
               return(0);
           }
       }
       return(1);
    }

    /* Create the entry */
    if (path_to_link_to) {
        err = symlink(path_to_link_to, path);
    } else {
        err = mkdir(path, MAILBOX_DIR_MODE);
    }

    return(err);
}

/*************************************************
 * multicast the mailbox among the cell
 * returns 0 if ok, -1 in case of failure
 * Note: Caller must garantee mailbox consistency
 *************************************************/
static int
mbox_net_send(mb_hdr_t *mbhdr)
{
    int                fd;
    struct sockaddr_in addr;
    struct msghdr      hdr;
    struct iovec       iov[2];

    fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) {
        return (-1);
    }

    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = inet_addr(MBOXD_GROUP);
    addr.sin_port = htons(MBOXD_PORT);

    hdr.msg_name       = (void*) &addr;
    hdr.msg_namelen    = sizeof(addr);
    hdr.msg_iov        = iov;
    hdr.msg_iovlen     = 2;
    hdr.msg_control    = NULL    ;
    hdr.msg_controllen = 0;
    hdr.msg_flags      = 0;

    iov[0].iov_base = mbhdr->mboxTag;
    iov[0].iov_len  = MAILBOX_TAG_MAXLEN;
    iov[1].iov_base = (void*) mbhdr;
    iov[1].iov_len  = mbhdr->curSize + sizeof(mb_hdr_t);
    (void) sendmsg(fd, &hdr, 0);

    close(fd);

    return (0);
}


/*************************************************
 * set_errno(error)
 * Set the errno variable to the specified value.
 * In a MT environment, errno is a per thread value
 **************************************************/
static void
set_errno(int err)
{
    errno = err;
}

/**************************************************
 * mbox_lock(const char *tag, int fd, int lcktype)
 * Lock the mailbox corresponding to the given fd.
 * Wait for the lock if lcktype is waitlock.
 * Return 0 in case of success, > 0 if the lock is
 * taken and lcktype is waitlock, < 0 in case of 
 * error with errno set accordingly.
 **************************************************
 */
static int
mbox_lock(const char *tag, int fd, mb_locktype_td lcktype)
{
    int retry = 3;
    
    do {
        struct flock lck;
        int          ret;


        lck.l_type = F_WRLCK;
        lck.l_whence = 0; /* Start offset is from the beginning of the file */
        lck.l_start = 0;
        lck.l_len = 0; /* Whole file */
    
        ret = fcntl(fd, (lcktype == waitlock)? F_SETLKW : F_SETLK, &lck);
        if (ret != -1) {
            /* Solaris man page says anything else than -1 is ok */
            return 0;
        }
        if (lcktype == trylock &&
            (errno == EAGAIN || 
             errno == EACCES || 
             errno == EWOULDBLOCK))
        {
            return 1;
        }
        
        {
            char errmsg[255];
            char errlog[255];
            struct timespec throttle = { 0, 100000000 }; /* 100ms */

            strerror_r(errno, errmsg, sizeof(errmsg));
            snprintf(errlog, sizeof(errlog), 
                     "mbox_lock(%s) failed with %s - retry count %d", 
                     tag,
                     errmsg,
                     retry
                     );
            
            openlog("mbox", LOG_CONS, LOG_USER);
            syslog(LOG_ERR, errlog);
            closelog();
            
            nanosleep(&throttle, NULL);
        }        
    } while ((--retry) > 0);
    
    return -1;
}
    
/**************************************************
 * mbox_unlock(int fd)
 * UnLock the mailbox corresponding to the given fd
 * Return 0 in case of success, < 0 otherwise with
 * errno set accordingly.
 **************************************************
 */
static int
mbox_unlock(int fd)
{
    struct flock lck;
    
    lck.l_type = F_UNLCK;
    lck.l_whence = 0; /* Start offset is from the beginning of the file */
    lck.l_start = 0;
    lck.l_len = 0; /* Whole file */
    
    if (fcntl(fd, F_SETLKW, &lck) != -1) {
        /* Solaris man page says anything else than -1 is ok */
        return 0;
    }
    return -1;
}

