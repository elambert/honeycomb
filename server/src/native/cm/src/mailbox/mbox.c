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
#include <string.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <netdb.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include "mbox.h"
#include "conf.h"

static mb_handle_t* _mbox_init(const char*, int, int);
static int          _mb_net_send(mb_hdr_t*);
static int          check_dir(char*, char*);
static void         _set_errno(int);

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
mb_init_mailboxes(cmm_nodeid_t nodeid,
                  int create_link)
{
    char buffer[MAXPATHLEN];
    char localnode[MAXPATHLEN];
    int err;

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
    char         mbname[MAXPATHLEN];
    char*        ptag;
    const char   c = 0;

    /*
     * bad tag name length 
    */
    if (strlen(tag) >= MAILBOX_TAG_MAXLEN) {
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
    
    snprintf(mbname, sizeof(mbname), "%s/%s",
             MAILBOX_PATH, tag);
    mbhdl->fd = open(mbname, O_RDWR | O_CREAT | O_TRUNC, MAILBOX_MODE);
    if (mbhdl->fd == -1) {
        free(mbhdl);
        return(MB_INVALID_ID);
    }
    size += sizeof(mb_hdr_t);
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
    mbhdr->usrClbk   = NULL;
    mbhdr->curState  = SRV_INIT;
    mbhdr->rqstState = SRV_INVALID;

    ptag = rindex(mbname, '/');
    if (ptag == NULL) {
        ptag = mbname;
    }
    strncpy(mbhdr->mboxTag, ptag, sizeof(mbhdr->mboxTag));

    /* multicast this mailbox */
    _mb_net_send(mbhdr);

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

    mbhdl = _mbox_init(tag, O_RDWR, PROT_READ | PROT_WRITE);
    if (mbhdl != MB_INVALID_ID) {
        /*
         * Initialize mailbox header
         */
        mbhdl->flags = MB_OWNER;
        ((mb_hdr_t*)mbhdl->maddr)->usrClbk = usrClbk;
        ((mb_hdr_t*)mbhdl->maddr)->tmStamp = 0;
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


    if (MB_BAD(mbhdl)) {
        _set_errno(EINVAL);
        return (MB_ERROR);
    }

    /* 
     * Only owner can heartbeat
     */
    if ((mbhdl->flags & MB_OWNER) == 0) {
        _set_errno(EPERM);
        return (MB_ERROR);
    }

    /*
     * lock the mailbox
     */
    if (lockf(mbhdl->fd, F_LOCK, 0) == -1) {
        return (MB_ERROR);
    }
  
    /*
     * Update client time stamp
     */
    mbhdr = (mb_hdr_t*) mbhdl->maddr;
    if (gettimeofday(&tv, NULL) == 0) {
        mbhdr->tmStamp = tv.tv_sec;
    }

    /*
     * Check for a pending requested state
     */
    if ((mbhdl->flags & MB_CALLBACK) == 0) {
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
             act = ACT_DESTROY;
             break;

         default:
             break;
         }
         if (act != ACT_VOID) {
             mbhdl->flags |= MB_CALLBACK;
         }
    }

    /*
     * unlock mailbox, update pstate and
     * possibly trigger callback
     */
    (void) lockf(mbhdl->fd, F_ULOCK, 0);
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

    mbhdl = _mbox_init(tag, O_RDWR, PROT_READ);
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
        _set_errno(EINVAL);
        return (MB_ERROR);
    }
    if (lockf(mbhdl->fd, F_LOCK, 0) == -1) {
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;
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
        _set_errno(EINVAL);
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
        _set_errno(EINVAL);
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
        _set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;

    if (lockf(mbhdl->fd, F_LOCK, 0) == -1) {
        return (MB_ERROR);
    }
    mbhdr->curState = state;
    if ((mbhdl->flags & (MB_OWNER|MB_CALLBACK)) == (MB_OWNER|MB_CALLBACK)) {
         mbhdl->flags &= ~MB_CALLBACK;
    }
    /* State changed - multicast this mailbox  */
    _mb_net_send(mbhdr);
    (void) lockf(mbhdl->fd, F_ULOCK, 0);

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
        _set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;

    mbhdr->rqstState = state;

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
        _set_errno(EINVAL);
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
 ****************************************/
mb_error_t
mb_read(mb_id_t mb_id, void* data, off_t offset, size_t size) 
{
    mb_handle_t*      mbhdl;
    mb_hdr_t*         mbhdr;
    unsigned char*    saddr;

    mbhdl = (mb_handle_t*) mb_id;
    if (MB_BAD(mbhdl)) {
        _set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;

    if (offset + size + sizeof(mb_hdr_t) > mbhdr->ttSize) {
        _set_errno(EINVAL);
        return (MB_ERROR);
    }
    if (lockf(mbhdl->fd, F_LOCK, 0) == -1) {
        return (MB_ERROR);
    }
    saddr  = (unsigned char*) mbhdl->maddr;
    saddr += sizeof(mb_hdr_t) + offset;
    memcpy(data, saddr, size);
    (void) lockf(mbhdl->fd, F_ULOCK, 0);

    return MB_OK;
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
        _set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;

    if (offset + size + sizeof(mb_hdr_t) > mbhdr->ttSize) {
        _set_errno(ENOSPC);
        return (MB_ERROR);
    }
    if (lockf(mbhdl->fd, F_LOCK, 0) == -1) {
        return (MB_ERROR);
    }
    taddr  = (unsigned char*) mbhdl->maddr;
    taddr += sizeof(mb_hdr_t) + offset;
    memcpy(taddr, data, size);
    (void) lockf(mbhdl->fd, F_ULOCK, 0);

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
        _set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;
    return mbhdr->ttSize - sizeof(mb_hdr_t);
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
        _set_errno(EINVAL);
        return (MB_ERROR);
    }
    mbhdr = (mb_hdr_t*) mbhdl->maddr;

    if (lockf(mbhdl->fd, F_LOCK, 0) == -1) {
        return (MB_ERROR);
    }
    _mb_net_send(mbhdr);
    (void) lockf(mbhdl->fd, F_ULOCK, 0);

    return (MB_OK);
}

/*******************************************************
 * Open an mbox and initialize the corresponding handle
 *******************************************************/
static mb_handle_t*
_mbox_init(const char* tag, int oflags, int mflags)
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
_mb_net_send(mb_hdr_t *mbhdr)
{
    static char lpath[MAXPATHLEN] = "\0";
    char               mbtag[MAXPATHLEN];
    int                fd;
    struct sockaddr_in addr;
    struct msghdr      hdr;
    struct iovec       iov[2];


    /* initialize actual path to local mailboxes */
    if (lpath[0] == '\0') {
        char path[MAXPATHLEN];
        int  ret;
        snprintf(path, sizeof(path), "%s/0", MAILBOX_PATH);       
        ret = readlink(path, lpath, sizeof(lpath));
        if (ret < 0 || ret > (int)sizeof(lpath)) {
            return (-1);
        }
        lpath[ret] = '\0';
    }
    snprintf(mbtag, sizeof(mbtag), "%s/%s", lpath, mbhdr->mboxTag);
     
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

    iov[0].iov_base = mbtag;
    iov[0].iov_len  = strlen(mbtag) + 1;
    iov[1].iov_base = (void*) mbhdr;
    iov[1].iov_len  = mbhdr->ttSize;
    (void) sendmsg(fd, &hdr, 0);
 
    close(fd);

    return (0);
}

/*************************************************
 * _set_errno(error)
 * Set the errno variable to the specified value.
 * In a MT environment, errno is a per thread value
 **************************************************/
static void
_set_errno(int err)
{
    errno = err;
}

