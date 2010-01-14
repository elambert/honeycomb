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



/* Several communication utilities for CMM */


#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <errno.h>
#include <poll.h>
#include <strings.h>
#include <string.h>
#include <ctype.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <stdio.h>
#include <fcntl.h>

#include "comm.h"
#include "trace.h"
#include "cmm_parameters.h"

int
GetHostAddress(char* host, int port,
               struct sockaddr_in* addr)
{
    bzero((char*) addr, sizeof (struct sockaddr_in));
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
                return 0;
            }
        } else {
            addr->sin_family = hp->h_addrtype;
            (void) memcpy ((char*) &addr->sin_addr,
                           hp->h_addr, hp->h_length);
        }
        endhostent();
    }
    addr->sin_port = htons(port);
    return 1;
}

/*
 * The ConnectToServer routine implements a connect with timeout
 */

int
ConnectToServer(struct sockaddr_in* addr)
{
    int fd;
    int err;
    struct pollfd fds[1];
    int socket_err;
    socklen_t serrlen = sizeof(socket_err);

    /* Create the socket */

    if ((fd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        return -1;
    }

    /* Set the socket non blocking */
    
    if (fcntl(fd, F_SETFL, O_NONBLOCK) == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "fcntl failed. Couldn't set the socket non blocking");
        close(fd);
        return(-1);
    }

    /* Try to connect */
    
    err = connect(fd, (struct sockaddr*) addr,
                  sizeof(struct sockaddr_in));
    if (err == -1) {
        switch (errno) {
        case EINPROGRESS :
            break;

        case EWOULDBLOCK :
            /* Shouldn't happen */
            break;

        case EINTR :
            break;

        default:
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "select failed [%d - %s]",
                     errno, strerror(errno));
            close(fd);
            return(-1);
        }
    }

    fds[0].fd = fd;
    fds[0].events = POLLOUT;
    fds[0].revents = 0;
    
    err = poll(fds, 1, HEARTBEAT_INTERVAL);
    if (err < 0) {
        cm_trace(CM_TRACE_LEVEL_DEBUG, 
                 "poll failed. Cannot connect to server");
        close(fd);
        return(-1);
    }

    if (err == 0) {
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "poll timed out. Cannot connect to server");
        close(fd);
        return(-1);
    }

    /* Check that the socket is connected */
    
    if (fds[0].revents & POLLOUT) {
        if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &socket_err, 
                       &serrlen) == -1) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "getsockopt failed. Connection failed");
            close(fd);
            return(-1);
        }

        if (socket_err == 0) {
            cm_trace(CM_TRACE_LEVEL_DEBUG,
                     "Connect succeeded");
            return(fd);
        }
    }

    /* An error occurred */

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "Couldn't connect the socket to the server within the timeout");
    close(fd);
    return(-1);
}

int
BindServer( int port,
            int nbClients )
{
    struct sockaddr_in	addr;
    struct hostent      *h;
    struct linger       linger;
    int                 serverSocket;
    
    serverSocket = socket( AF_INET, SOCK_STREAM, 0);
    if (serverSocket == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "socket failed [%d]",
		 errno );
        return(-1);
    }
    
    linger.l_onoff = 1;
    linger.l_linger = 0;
    if ( setsockopt(serverSocket, SOL_SOCKET, SO_LINGER, &linger, sizeof(linger)) ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "setsockopt error [%d]", errno);
        close(serverSocket);
        return(-1);
    }

    h = gethostbyname("localhost");
    if (!h) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "gethostbyname failed [%d]", errno);
        close(serverSocket);
        return(-1);
    }
    
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons( port );
    
    if( bind( serverSocket,
              (struct sockaddr*) &addr,
              sizeof(struct sockaddr_in) )) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "Cannot bind port [%d]", errno);
        close(serverSocket);
        return(-1);
    }

    if (listen( serverSocket, nbClients )) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "Error in listen [%d]", errno);
        close(serverSocket);
        return(-1);
    }

    cm_trace(CM_TRACE_LEVEL_DEBUG,
	     "Socket server is listening on port %d",
	     port );
    
    return(serverSocket);
}

int
fdContainsInfo( int fd )
{
    struct pollfd fds[1];
    int res;

    fds[0].fd = fd;
    fds[0].events = POLLIN;
    fds[0].revents = 0;
    
    res = poll( fds, 1, 100);
    if (errno == EINTR) /* interrupted */
	{
	    if (fds[0].revents & POLLIN) return (1); 
	    else return (0);
	}

    switch (res) {
    case 0 :
        /* Timed out */
        return(0);

    case 1 :
        if ( (fds[0].revents & POLLERR)
             || (fds[0].revents & POLLHUP) ) {
            /* The socket has been disconnected */
            return(2);
        }

        if (fds[0].revents & POLLIN) {
            return(1);
        }

        return(-1);

    default:
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "poll failed in comm [%d]",
		 res );
        return(-1);
    }
}

void
get_ip_from_hostname( char *hostname,
                      char *buffer )
{
    struct hostent* hp;
    struct in_addr in;

    if ((hp = gethostbyname(hostname)) == NULL) {
        if (isdigit(hostname[0])) {
            sprintf( buffer, "%s", hostname );
        } else {
            endhostent();
            buffer[0] = '\0';
            return;
        }
    }

    (void) memcpy(&in.s_addr, hp->h_addr_list[0], sizeof(in.s_addr));
    
    sprintf( buffer, "%s", inet_ntoa(in) );
}
