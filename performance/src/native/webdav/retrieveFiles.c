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



#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <time.h>
#include <netdb.h>
#include <unistd.h>
#include <fcntl.h>
#include <strings.h>
#include <signal.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/time.h>
#include <sys/socket.h>
#include <sys/errno.h>
#include <netinet/in.h>
#include <sys/devpoll.h>

#define DEFAULT_CONN_COUNT    100  /* max. number of connection to
                                     http server. Remember to set ulimit
                                     if necessary.*/
#define HTTP_PORT             8080
#define DEFAULT_DURATION      30   /* seconds */
#define REPORT_INTERVAL       10   /* seconds */

#define MAX_PATH_LENGTH       500
#define MAX_REQUEST_LENGTH    1000
#define DEFAULT_USER_AGENT    "Mozilla/4.0 (compatible; MSIE 5.0; Win32)"
#define BUFFER_SIZE           1048576  /* 1 meg */
#define ERROR_FILE            "retrieveFiles.err"

/* connection states */
#define CONN_READY            1
#define SENDING_REQUEST       2
#define SENT_REQUEST          3
#define TRANSFERRING_DATA     4
#define SUSPENDED             5

struct ConnectionInfo {
  int  state;
  char request[MAX_REQUEST_LENGTH];
  int  reqLength;
  int  reqStart;
  struct timeval startTime;
  int  dataRemaining;
};

extern int errno;

struct sockaddr_in serverAddr;
long startTime;
FILE *fdPathFile;
FILE* fdErrFile = NULL;
int connCount = DEFAULT_CONN_COUNT;
struct pollfd *pollFDs;         /* poll structs for sockets */
long totalRequests = 0;
long totalErrors = 0;
long totalOks = 0;
unsigned long fileLength = -1;  /* used only for reporting stats */

void usage();
long getTimeSec();
void printStats();
void doRetrieveFiles(long duration, const char *serverName);
void catchSIGINT(int sigNum);
void getTimeval(struct timeval *tp);
void readData(char *header, int fd);
int checkHeader(char *buffer, int nRead, struct ConnectionInfo *cInfo);
int checkData(char *buffer, int nRead, struct ConnectionInfo *cInfo);
void refreshConnection(int index, struct ConnectionInfo *cInfo, int rinse);

/***********************************************************************/
int main(int argc, char* argv[]) {
  extern char *optarg;
  extern int optind;
  int c;
  long duration = DEFAULT_DURATION;    /* seconds */
  const char* serverName;
  short int port = HTTP_PORT;
  char* endp;
  struct hostent *host;
  const char* pathFile = NULL;

  while ((c = getopt(argc, argv, "p:t:c:h")) != EOF) {
    switch (c) {
    case 'p':
      port = strtol(optarg, &endp, 10);
      if (*endp) {
        fprintf(stderr, "-p: expect numeric argument\n");
        usage();
      }
      break;
    case 't':
      duration = strtol(optarg, &endp, 10);
      if (*endp) {
        fprintf(stderr, "-t: expect numeric argument\n");
        usage();
      }
      break;
    case 'c':
      connCount = strtol(optarg, &endp, 10);
      if (*endp) {
        fprintf(stderr, "-c: expect numeric argument\n");
        usage();
      }
      break;
    case 'h':
    default:
      usage();
    }
  }
  /* Exactly 2 arguments should be left*/
  if (argc - optind != 2) {
    usage();
  }
  serverName = argv[optind];
  pathFile = argv[optind + 1];

  if ((host = gethostbyname(serverName)) == 0) {
    fprintf(stderr, "%s: no such host\n", serverName);
    exit(-1);
  }

  /* Initialize the destination address.*/
  memset((char *) &serverAddr, 0, sizeof(struct sockaddr_in));
  serverAddr.sin_family = AF_INET;
  serverAddr.sin_port = htons((u_short) port);
  memcpy(&serverAddr.sin_addr, host->h_addr, host->h_length);

  if ((fdPathFile = fopen(pathFile, "r")) == NULL) {
    perror(pathFile);
    exit(-1);
  }

  if ((fdErrFile = fopen(ERROR_FILE, "w")) == NULL) {
    perror("error file");
    exit(-1);
  }

  signal(SIGINT, catchSIGINT);

  /* get files */
  doRetrieveFiles(duration, serverName);

  if (fdPathFile != NULL) {
    fclose(fdPathFile);
  }
  if (fdErrFile != NULL) {
    fclose(fdErrFile);
  }
}

/***********************************************************************/
void usage() {
  fprintf(stderr, "Usage: retrieveFiles [-p port] [-t time] [-c connections] server pathfile\n");
  fprintf(stderr, "    -p port        : port to send commands to (default is 8080)\n");
  fprintf(stderr, "    -t time        : duration of test in seconds (default is 30 sec)\n");
  fprintf(stderr, "    -c connections : number of simultaneous connections (default is 100)\n");
  fprintf(stderr, "    server         : server name, ex. dev321-data\n");
  fprintf(stderr, "    pathfile       : file containing full path of files, one per line\n");
  exit(1);
}

/***********************************************************************/
int searchFDs(int fd) {
  int i;
  for (i = 0; i < connCount; i ++) {
    if (pollFDs[i].fd == fd) {
      return i;
    }
  }
  perror("socket fd not found");
  exit(-1);
}

/***********************************************************************/
void createConnection(struct pollfd* pollfdPtr) {
  if ((pollfdPtr->fd = socket(AF_INET, SOCK_STREAM, 0)) == -1) {
    perror("socket() failed");
    exit(-1);
  }
  if (connect(pollfdPtr->fd, (struct sockaddr *) &serverAddr,
                sizeof(serverAddr)) < 0 ) {
    perror("connect() failed");
    exit(-1);
  }

  /* Set socket to non-blocking. */
  if (fcntl(pollfdPtr->fd, F_SETFL, O_NONBLOCK) == -1) {
    perror("fcntl failed");
    exit(-1);
  }
  pollfdPtr->events = (POLLOUT | POLLIN);
  pollfdPtr->revents = 0;
}

/***********************************************************************/
int getNextPath(char *path, int length) {
  if (fgets(path, length, fdPathFile)) {
    /* strip newline */
    if (path[strlen(path) - 1] == '\n') {
      path[strlen(path) - 1] = '\0';
    }
    return 1;
  }
  return 0;
}

/***********************************************************************/
int getRequest(char* request, int length, char* path, const char* serverName) {
  return snprintf(request, length, "GET %s HTTP/1.1\r\nHost: %s\r\nUser-Agent: %s\r\n\r\n",
                  path, serverName, DEFAULT_USER_AGENT);
}

/***********************************************************************/
void doRetrieveFiles(long duration, const char *serverName) {
  int i;
  dvpoll_t dopoll;
  int eventFD;
  int result;
  int index;
  long currentTime, reportTime, startRinseTime;
  int nwrite, nread;
  struct ConnectionInfo *cInfo;
  float retrievesPerSec, errsPerSec;
  int retrieveCount = 0, errCount = 0;
  struct tm* tmPtr;
  char timeString[40];
  char buffer[BUFFER_SIZE];
  char filePath[MAX_PATH_LENGTH];
  struct ConnectionInfo *connInfo;
  int getFile = 0;
  int rinse = 0;
  float retrieveTime = 0;
  float secondsPerRetrieve;
  struct timeval retrieveEndTime;
  int retVal;

  if ((pollFDs = calloc(connCount, sizeof(struct pollfd))) == NULL) {
    perror("calloc failed");
    exit(-1);
  }
  if ((connInfo = calloc(connCount, sizeof(struct ConnectionInfo))) == NULL) {
    perror("calloc failed");
    exit(-1);
  }
  
  for (i = 0; i < connCount; i++) {
    createConnection(&pollFDs[i]);
    connInfo[i].state = CONN_READY;
  }    

  dopoll.dp_timeout = 10; /* check so often to stop the test */
  dopoll.dp_nfds = connCount;
  dopoll.dp_fds = (struct pollfd *) 
    malloc(sizeof(struct pollfd) * connCount);
  if (!dopoll.dp_fds) {
    perror("Failed in dopoll.dp_fds" );
    exit(-1);
  }

  currentTime = startTime = reportTime = getTimeSec();

  /* get length */
  getFile = getNextPath(filePath, MAX_PATH_LENGTH);
  if (!getFile) {
    perror("no file length in input file");
    exit(-1);
  }
  fileLength = strtoul(filePath, 0, 10);
  if (fileLength < 0) {
    perror("invalid file length");
    exit(-1);
  }

  getFile = getNextPath(filePath, MAX_PATH_LENGTH);

  while (1) {

    close(eventFD);
    if ((eventFD = open("/dev/poll", O_RDWR)) < 0) {
      perror("cannot open /dev/poll");
      exit(-1);
    }

    if (write(eventFD, &pollFDs[0],
              sizeof(struct pollfd) * connCount) !=
        sizeof(struct pollfd) * connCount) {
      perror("failed to write all pollFDs");
      close (eventFD);
      exit(-1);
    }

    /* read from the devpoll driver */
    result = ioctl(eventFD, DP_POLL, &dopoll);

    if (result < 0) {
      perror("/dev/poll ioctl DP_POLL failed");
      close (eventFD);
      exit(-1);
    }

    for (i = 0; i < result; i ++) {

      index = searchFDs(dopoll.dp_fds[i].fd);

      cInfo = &connInfo[index];

      if (dopoll.dp_fds[i].revents & POLLOUT) {
        /* write */
        if (cInfo->state == CONN_READY) {
          if (getFile) {
            cInfo->reqLength = getRequest(cInfo->request, MAX_REQUEST_LENGTH,
                                          filePath, serverName);

            if ((nwrite = write(pollFDs[index].fd, cInfo->request, cInfo->reqLength)) < 0) {
              perror("write of request failed");
              exit(-1);
            }
            if (cInfo->startTime.tv_sec == 0) {
              getTimeval(&(cInfo->startTime));
            }
            if (nwrite == cInfo->reqLength) {
              cInfo->state = SENT_REQUEST;
              totalRequests++;
              getFile = getNextPath(filePath, MAX_PATH_LENGTH);
            } else {
              /* partial write */
              cInfo->state = SENDING_REQUEST;
              cInfo->reqStart = nwrite;
            }
          }
        } else if (cInfo->state == SENDING_REQUEST) {
          if ((nwrite = write(pollFDs[index].fd, (cInfo->request + cInfo->reqStart),
                              (cInfo->reqLength - cInfo->reqStart))) < 0) {
            perror("write more header failed");
            exit(-1);
          }
          
          if ((nwrite + cInfo->reqStart) == cInfo->reqLength) {
            cInfo->state = SENT_REQUEST;
            cInfo->reqStart = 0;
            totalRequests++;
            getFile = getNextPath(filePath, MAX_PATH_LENGTH);
          } else {
            /* partial write */
            cInfo->reqStart += nwrite;
            if (cInfo->reqStart > cInfo->reqLength) {
              perror("what?");
              exit(-1);
            }
          }
        } else if ((cInfo->state != SENT_REQUEST)
                   && (cInfo->state != SUSPENDED)
                   && (cInfo->state != TRANSFERRING_DATA)) {
          perror("spurious state");
          exit(-1);
        }
      }

      if (dopoll.dp_fds[i].revents & POLLIN) {
        /* ready for read */
        
        if ((cInfo->state == SENT_REQUEST)
            || (cInfo->state = TRANSFERRING_DATA)) {
          if ((nread = read(pollFDs[index].fd, buffer,
                            BUFFER_SIZE)) == -1) {
            if (errno == EWOULDBLOCK) {
              /* should not see this */
              fprintf(stderr, "EWOULDBLOCK\n");
            } else {
              perror("read failed");
              exit(-1);
            }
          }
          if (nread > 0) {
            if (cInfo->state == SENT_REQUEST) {
              /* We can typically be able to read the whole header in
                 non-blocking mode, so no need to retry read() just to get
                 the first/status line. */
              retVal = checkHeader(buffer, nread, cInfo);
            } else {
              /* read more data */
              retVal = checkData(buffer, nread, cInfo);
            }
            if (retVal < 0) {
              /* error response */
              totalErrors++;
              errCount++;
              fprintf(fdErrFile, "Error: %d\n%s\n", totalErrors, buffer);
              refreshConnection(index, cInfo, rinse);
            } else if (retVal == 0) {
              /* complete response */
              totalOks++;
              retrieveCount++;
              getTimeval(&retrieveEndTime);
              retrieveTime += ((retrieveEndTime.tv_sec - cInfo->startTime.tv_sec)
                + ((retrieveEndTime.tv_usec - cInfo->startTime.tv_usec) / 1000000.0));
              refreshConnection(index, cInfo, rinse);
            }
          }
        }
      }
    }

    poll(NULL,NULL,1);   /* Just a breather before the next
                          * set of packets */
    currentTime = getTimeSec();
    if ((currentTime - reportTime) > REPORT_INTERVAL) {
      tmPtr = localtime (&currentTime);
      strftime (timeString, sizeof (timeString), "%Y-%m-%d %H:%M:%S", tmPtr);
      retrievesPerSec = retrieveCount / (float) (currentTime - reportTime);
      errsPerSec = errCount / (float) (currentTime - reportTime);
      secondsPerRetrieve = (retrieveCount > 0) ? retrieveTime / (float) retrieveCount : 0;
      fprintf(stderr, "%d sec sample:\n", REPORT_INTERVAL);
      fprintf(stderr, "%s retrieves per sec: %.2f, errors per sec: %.2f, seconds per retrieve: %.2f\n",
              timeString, retrievesPerSec, errsPerSec, secondsPerRetrieve);
      reportTime = currentTime;
      retrieveCount = 0;
      errCount = 0;
      retrieveTime = 0;
      fprintf(stderr, "Cumulative stats:\n");
      printStats();
    }

    if (!rinse && (!getFile || (duration < (currentTime - startTime)))) {
      /* wait 10sec for outstanding requests */
      rinse = 1;
      startRinseTime = currentTime;
    }
    if (rinse && ((currentTime - startRinseTime) > 10)) {
      break;
    }
  }

  /* close outstanding connections, even if some retrieves are pending */
  for (i = 0; i < connCount; i++) {
    close(pollFDs[i].fd);
  }
  printStats();
  free(pollFDs);
  free(connInfo);
}


/***********************************************************************/
void refreshConnection(int index, struct ConnectionInfo *cInfo, int rinse) {

  if (close(pollFDs[index].fd) < 0) {
    perror("close() failed");
    exit(-1);
  }
  /* open new connection for the next retrieve */
  if (!rinse) {
    createConnection(&pollFDs[index]);
    cInfo->state = CONN_READY;
    cInfo->startTime.tv_sec = 0;
  } else {
    cInfo->state = SUSPENDED;
  }
}

/***********************************************************************/
int checkData(char *buffer, int nRead, struct ConnectionInfo *cInfo) {
  int remaining;

  remaining = cInfo->dataRemaining - nRead;
  if (remaining > 0) {
    cInfo->dataRemaining = remaining;
  } else if (remaining < 0) {
    perror("negative remaining data count");
    exit(-1);
  }
  return remaining;
}

/***********************************************************************/
int checkHeader(char *buffer, int nRead, struct ConnectionInfo *cInfo) {
  char *hdrEnd;
  long length;
  char *ptr;
  int remaining;

  if (nRead < 30) {
    /* didn't get first couple of lines, consider this an error */
    return -1;
  }

  if (strstr(buffer, "HTTP/1.1 200 OK") != NULL) {
    /* read the content length */
    if ((ptr = strstr(buffer, "Content-Length:")) != NULL) {
      ptr += strlen("Content-Length:");
      length = strtoul(ptr, 0, 10);
    } else {
      /* consider this an error */
      return -1;
    }

    if (length == 0) {
      return length;
    } else if (length < 0) {
      perror("negative length");
      exit(-1);
    }

    /* check transferred data for completeness */
    if ((hdrEnd = strstr(buffer, "\r\n\r\n")) != NULL) {
      hdrEnd += 4;
      /* write(1, buffer, (hdrEnd - buffer)); */
      remaining = length - nRead + (hdrEnd - buffer);
      if (remaining == 0) {
        return 0;
      } else if (remaining > 0) {
        cInfo->dataRemaining = remaining;
        cInfo->state = TRANSFERRING_DATA;
        return remaining;
      } else {
        /* what? */
        perror("negative data length");
        exit(-1);
      }
    } else {
      return -1;
    }
  } else {
    return -1;
  }
}

/***********************************************************************/
void printStats() {
  int i;
  struct tm* tmPtr;
  char timeString[40];
  long endTime = getTimeSec();
  float bps;

  tmPtr = localtime (&startTime);
  strftime (timeString, sizeof (timeString), "%Y-%m-%d %H:%M:%S", tmPtr);
  fprintf(stderr, "Start time: %s\n", timeString);

  tmPtr = localtime (&endTime);
  strftime (timeString, sizeof (timeString), "%Y-%m-%d %H:%M:%S", tmPtr);
  fprintf(stderr, "End time: %s\n", timeString);

  fprintf(stderr, "Requests: %d\nSuccess: %d\nErrors: %d\nPending: %d\n",
          totalRequests, totalOks, totalErrors, (totalRequests - totalOks
                                                 - totalErrors));
  if (fileLength != -1) {
    bps = totalOks * fileLength / ((float) (endTime - startTime));
    fprintf(stderr, "Bytes per sec: %.2f\n", bps);
  }
  fprintf(stderr, "\n");
}

/***********************************************************************/
long getTimeSec() {
  struct timeval tp;
  if (gettimeofday(&tp, NULL) == -1) {
    perror("gettimeofday()");
    exit(-1);
  }
  return tp.tv_sec;
}

/***********************************************************************/
void getTimeval(struct timeval *tp) {
  if (gettimeofday(tp, NULL) == -1) {
    perror("gettimeofday()");
    exit(-1);
  }
}

/***********************************************************************/
void catchSIGINT(int sigNum) {
  int i;
  fflush(stdout);
  printStats();
  for (i = 0; i < connCount; i++) {
    close(pollFDs[i].fd);
  }
  if (fdPathFile != NULL) {
    fclose(fdPathFile);
  }
  if (fdErrFile != NULL) {
    fclose(fdErrFile);
  }
  exit(0);
}

