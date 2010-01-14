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



#include <multiload.h>

int pfd_init(int num)
{
    return 0;
}

/* Get the fd from a poll struct */
int pfd_getfd(pfd_t* pfd)
{
    int fd = -1;

#if __sun__
    fd = pfd->fd;
#endif

#if __linux__
    fd = pfd->data.fd;
#endif

    return fd;
}

/* Set the fd into a poll struct */
void pfd_setfd(pfd_t* pfd, int fd)
{
#if __sun__
    pfd->fd = fd;
    pfd->events = POLLOUT | POLLIN;
    pfd->revents = 0;

#endif

#if __linux__
    pfd->data.fd = fd;
    /* Do we want edge-triggered (EPOLLET) or level-triggered? */
    pfd->events = EPOLLIN | EPOLLOUT | EPOLLET;
#endif
}

/* Write all fds to /dev/poll and get the ready files */
int pfd_getReadyFiles(pfd_t* all_fds, pfd_t* buffer,
                      int n_conns, long timeout)
{
    int rc = -1;

#if __sun__
    int olderr;
    dvpoll_t dvpoll;

    dvpoll.dp_timeout = timeout;
    dvpoll.dp_nfds = n_conns;
    dvpoll.dp_fds = buffer;

    int dev_fd = open("/dev/poll", O_RDWR);
    if (dev_fd < 0) {
        perror("/dev/poll open");
        return -3;
    }

    if (verbose > 2)
        fprintf(stderr, "Writing %d fds to /dev/poll\n", n_conns);

    if (write(dev_fd, all_fds, n_conns * sizeof(pfd_t)) < 0) {
        perror("/dev/poll write");
        close(dev_fd);
        return -3;
    }

    rc = ioctl(dev_fd, DP_POLL, &dvpoll);

    /* Want to return errno from the ioctl, not the close */
    olderr = errno;
    close(dev_fd);
    errno = olderr;
#endif

#if __linux__

#endif

    return rc;
}
