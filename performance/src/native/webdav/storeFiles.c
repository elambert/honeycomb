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
#include <assert.h>
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
#define DEFAULT_FILE_SIZE     10   /* bytes */
#define REPORT_INTERVAL       10   /* seconds */

#define RECV_BUFFER_LENGTH    1000
#define MAX_HEADER_LENGTH     1000
#define MAX_PATH_LENGTH       1000
#define ERROR_FILE            "storeFiles.err"

/* connection states */
#define CONN_READY            1
#define SENDING_HEADER        2
#define SENT_HEADER           3
#define SENDING_DATA          4
#define SENT_DATA             5

struct ConnectionInfo {
  int  state;
  int hdrLength;
  int hdrStart;
  int dataStart;
  char path[MAX_PATH_LENGTH];
  char header[MAX_HEADER_LENGTH];
  struct timeval startTime;
};

extern int errno;

struct sockaddr_in serverAddr;
unsigned long fileLength = DEFAULT_FILE_SIZE;  /* bytes */
long startTime;
FILE* fdOutFile = NULL;
FILE* fdErrFile = NULL;
int connCount = DEFAULT_CONN_COUNT;
struct pollfd *pollFDs;         /* poll structs for sockets */
long totalRequests = 0;
long totalErrors = 0;
long totalOks = 0;
long totalDups = 0;

void usage();
void doStoreFiles(unsigned long length, unsigned long duration,
                  const char *serverName, int dirDepth, char *fsview);
long getTimeSec();
void getTimeval(struct timeval *tp);
void printStats();
void catchSIGINT(int sigNum);

/***********************************************************************/
int main(int argc, char* argv[]) {
  extern char *optarg;
  extern int optind;
  int c;
  unsigned long duration = DEFAULT_DURATION;    /* seconds */
  const char* serverName;
  short int port = HTTP_PORT;
  char* endp;
  struct hostent *host;
  const char* outFile = NULL;
  char* fsview = "oFotoHashDirs";
  int dirDepth = 6;

  while ((c = getopt(argc, argv, "p:t:l:o:c:v:d:h")) != EOF) {
    switch (c) {
    case 'p':
      port = strtol(optarg, &endp, 10);
      if (*endp) {
        fprintf(stderr, "-p: expect numeric argument\n");
        usage();
      }
      break;
    case 't':
      duration = strtoul(optarg, &endp, 10);
      if (*endp) {
        fprintf(stderr, "-t: expect numeric argument\n");
        usage();
      }
      break;
    case 'l':
      fileLength = strtoul(optarg, &endp, 10);
      if (*endp) {
        fprintf(stderr, "-l: expect numeric argument\n");
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
    case 'o':
      outFile = optarg;
      break;
    case 'v':
      fsview = optarg;
      break;
    case 'd':
      dirDepth = strtol(optarg, &endp, 10);
      if (*endp) {
        fprintf(stderr, "-d: expect numeric argument\n");
        usage();
      }
      break;
    case 'h':
    default:
      usage();
    }
  }
  /* Exactly one arguments should be left*/
  if (argc - optind != 1) {
    usage();
  }
  serverName = argv[optind];

  if ((host = gethostbyname(serverName)) == 0) {
    fprintf(stderr, "%s: no such host\n", serverName);
    exit(-1);
  }

  /* Initialize the destination address.*/
  memset((char *) &serverAddr, 0, sizeof(struct sockaddr_in));
  serverAddr.sin_family = AF_INET;
  serverAddr.sin_port = htons((u_short) port);
  memcpy(&serverAddr.sin_addr, host->h_addr, host->h_length);

  if ((outFile != NULL)
      && (fdOutFile = fopen(outFile, "a")) == NULL) {
    perror(outFile);
    exit(-1);
  }

  if ((fdErrFile = fopen(ERROR_FILE, "w")) == NULL) {
    perror("error file");
    exit(-1);
  }

  signal(SIGINT, catchSIGINT);

  /* put files */
  doStoreFiles(fileLength, duration, serverName, dirDepth, fsview);

  if (fdOutFile != NULL) {
    fclose(fdOutFile);
  }
  if (fdErrFile != NULL) {
    fclose(fdErrFile);
  }
}

/***********************************************************************/
void usage() {
  fprintf(stderr, "Usage: storeFiles [-p port] [-l length] [-t time] [-o outfile] [-c num_conn] server\n");
  fprintf(stderr, "    -p port      : port to send commands to (default is 8080)\n");
  fprintf(stderr, "    -l length    : Length of file to send (default is 10 bytes)\n");
  fprintf(stderr, "    -t time      : duration of test in seconds (default is 30 sec)\n");
  fprintf(stderr, "    -o outfile   : file to write paths of stored files\n");
  fprintf(stderr, "    -c num_conn  : number of simultaneous connections (default is 100)\n");
  fprintf(stderr, "    -v view      : name of file system view (default: oFotoHashDirs)\n");
  fprintf(stderr, "    -d dir_depth : depth of directory tree (metadata count, default: 6)\n");
  fprintf(stderr, "    server       : server name, ex. dev321-data\n");
  exit(1);
}

/***********************************************************************/
void getPath(char *buf, int dirDepth, char *fsview) {
  int i;
  if (dirDepth > 300) {
      /* adjust buffer size if you want more than 300 dir depth */
      perror("too many directories");
      exit(-1);
  }
  int offset = sprintf(buf, "/webdav/%s", fsview);
  assert(offset > 0);
  int dir;
  int nwritten;
  for (i = 0; i < dirDepth; i++) {
    dir = (random() % 100); /* upto 100 dirs in each level */
    nwritten = sprintf(buf + offset, "/%02d", dir);
    assert(nwritten == 3);
    offset += 3;
  }
  sprintf(buf + offset, "/%06d", (random() % 1000000));
}

/***********************************************************************/
int getHeader(char *header, int length, char *path, unsigned long fileLength,
              const char *serverName) {
  return snprintf(header, length,
                  "PUT %s HTTP/1.0\r\nHost: %s\r\nContent-length: %d\r\n"
                  "Content-type: text/plain\r\n\r\n", path, serverName, fileLength);
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
void doStoreFiles(unsigned long fileLength, unsigned long duration,
                  const char *serverName, int dirDepth, char *fsview) {
  char *data;
  char recvBuffer[RECV_BUFFER_LENGTH];
  int i, j, n;
  dvpoll_t dopoll;
  int eventFD;
  int result;
  int index;
  long currentTime, reportTime;
  int nwrite, nread;
  struct ConnectionInfo *cInfo;
  float storesPerSec, errsPerSec;
  int storeCount = 0, errCount = 0;
  struct tm* tmPtr;
  char timeString[40];
  struct ConnectionInfo *connInfo;
  float storeTime = 0;
  float secondsPerStore;
  struct timeval storeEndTime;

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
    
  srandom((unsigned int) getTimeSec());

  /* Populate a buffer to write. */
  if ((data = calloc(fileLength, sizeof(char))) == NULL) {
    perror("allocating data to send");
    exit(-1);
  }
  for (i = 0; i < fileLength; i++) {
    data[i] = 'a';
  }

  dopoll.dp_timeout = 10; /* check so often to stop the test */
  dopoll.dp_nfds = connCount;
  dopoll.dp_fds = (struct pollfd *) 
    malloc(sizeof(struct pollfd) * connCount);
  if (!dopoll.dp_fds) {
    perror("Failed in dopoll.dp_fds" );
    exit(-1);
  }

  if (fdOutFile != NULL) {
    fprintf(fdOutFile, "%d\n", fileLength);
  }

  currentTime = startTime = reportTime = getTimeSec();

  while (duration > (currentTime - startTime)) {

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
        /* ready for write */
        if (cInfo->state == CONN_READY) {
          getPath(cInfo->path, dirDepth, fsview);
          cInfo->hdrLength = getHeader(cInfo->header, MAX_HEADER_LENGTH,
                                       cInfo->path, fileLength, serverName);

          if ((nwrite = write(pollFDs[index].fd, cInfo->header, cInfo->hdrLength)) == -1) {
            perror("write header failed");
            exit(-1);
          }
          if (cInfo->startTime.tv_sec == 0) {
            getTimeval(&(cInfo->startTime));
          }
          if (nwrite == cInfo->hdrLength) {
            cInfo->state = SENT_HEADER;
          } else {
            /* partial write */
            cInfo->state = SENDING_HEADER;
            cInfo->hdrStart = nwrite;
          }
            
        } else if (cInfo->state == SENDING_HEADER) {
          if ((nwrite = write(pollFDs[index].fd, (cInfo->header + cInfo->hdrStart),
                              (cInfo->hdrLength - cInfo->hdrStart))) == -1) {
            perror("write more header failed");
            exit(-1);
          }
          
          if ((nwrite + cInfo->hdrStart) == cInfo->hdrLength) {
            cInfo->state = SENT_HEADER;
            cInfo->hdrStart = 0;
          } else {
            /* partial write */
            cInfo->hdrStart += nwrite;
            if (cInfo->hdrStart > cInfo->hdrLength) {
              perror("what?");
              exit(-1);
            }
          }

        } else if (cInfo->state == SENT_HEADER) {
          /* send file */
          if ((nwrite = write(pollFDs[index].fd, data, fileLength)) == -1) {
            perror("write data failed");
            exit(-1);
          }
          if (nwrite == fileLength) {
            cInfo->state = SENT_DATA;
            totalRequests++;
          } else {
            /* partial write */
            cInfo->state = SENDING_DATA;
            cInfo->dataStart = nwrite;
          }
            
        } else if (cInfo->state == SENDING_DATA) {
          if ((nwrite = write(pollFDs[index].fd, (data + cInfo->dataStart),
                              (fileLength - cInfo->dataStart))) == -1) {
            perror("write more data failed");
            exit(-1);
          }
          
          if ((nwrite + cInfo->dataStart) == fileLength) {
            cInfo->state = SENT_DATA;
            cInfo->dataStart = 0;
            totalRequests++;
          } else {
            /* partial write */
            cInfo->dataStart += nwrite;
            if (cInfo->dataStart > fileLength) {
              perror("what?(data)");
              exit(-1);
            }
          }
        } else if (cInfo->state != SENT_DATA) {
          perror("spurious state");
          exit(-1);
        }
      } 
      
      if (dopoll.dp_fds[i].revents & POLLIN) {
        /* ready for read */
        if (cInfo->state == SENT_DATA) {

          if ((nread = read(pollFDs[index].fd, recvBuffer,
                           RECV_BUFFER_LENGTH)) == -1) {
            if (errno == EWOULDBLOCK) {
              fprintf(stderr, "EWOULDBLOCK returned\n");
            } else {
              perror("read failed");
              exit(-1);
            }
          }
          if (nread > 0) {
            /*
            fprintf(stderr, "\nRESPONSE:\n");
            write(1, recvBuffer, nread);
            */
            recvBuffer[nread] = '\0';
            if (strstr(recvBuffer, "HTTP/1.1 200 OK") != NULL) {
              totalOks++;
              storeCount++;
              getTimeval(&storeEndTime);
              storeTime += ((storeEndTime.tv_sec - cInfo->startTime.tv_sec)
                + ((storeEndTime.tv_usec - cInfo->startTime.tv_usec) / 1000000.0));
              if (fdOutFile != NULL) {
                fprintf(fdOutFile, "%s\n", cInfo->path);
              }
            } else if  (strstr(recvBuffer, "already+exists") != NULL) {
              /* duplicate file, ignore */
              totalDups++;
            } else {
              totalErrors++;
              errCount++;
              fprintf(fdErrFile, "Error: %d\n%s\n", totalErrors, recvBuffer);
            }

            if (close(pollFDs[index].fd) < 0) {
              perror("close() failed");
              exit(-1);
            }
            /* open new connection. webdav server closes connection
               after a response - evidently to force sender to use a new
               input port */
            createConnection(&pollFDs[index]);
            cInfo->state = CONN_READY;
            cInfo->startTime.tv_sec = 0;
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
      storesPerSec = storeCount / (float) (currentTime - reportTime);
      errsPerSec = errCount / (float) (currentTime - reportTime);
      secondsPerStore = (storeCount > 0) ? storeTime / (float) storeCount : 0;
      fprintf(stderr, "%d sec sample:\n", REPORT_INTERVAL);
      fprintf(stderr, "%s stores per sec: %.2f, errors per sec: %.2f, seconds per store: %.2f\n",
              timeString, storesPerSec, errsPerSec, secondsPerStore);
      reportTime = currentTime;
      storeCount = 0;
      errCount = 0;
      storeTime = 0;
      fprintf(stderr, "Cumulative stats:\n");
      printStats();
    }
  }

  fprintf(stderr, "Final Stats\n-----------\n");
  printStats();
  /* close outstanding connections, even if requests are pending */
  for (i = 0; i < connCount; i++) {
    if (close(pollFDs[i].fd) < 0) {
      perror("close() failed");
      exit(-1);
    }
  }
  free(data);
  free(pollFDs);
  free(connInfo);
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

  fprintf(stderr, "Filesize: %d bytes\nRequests: %d\nSuccess: %d\nErrors: %d\nPending: %d\n",
          fileLength, totalRequests, totalOks, totalErrors,
          (totalRequests - totalOks - totalErrors));
  bps = totalOks * fileLength / ((float) (endTime - startTime));
  fprintf(stderr, "Bytes per sec: %.2f\n\n", bps);
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
  if (fdOutFile != NULL) {
    fclose(fdOutFile);
  }
  if (fdErrFile != NULL) {
    fclose(fdErrFile);
  }
  exit(0);
}
