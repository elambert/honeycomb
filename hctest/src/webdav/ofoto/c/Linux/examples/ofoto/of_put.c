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



#include "neon_config.h"
#include "ofoto.h"

#include <sys/types.h>
#include <sys/time.h>
#include <sys/stat.h>

#include <stdio.h>
#include <ctype.h>
#include <signal.h>
#include <time.h>
#include <fcntl.h>

#ifdef HAVE_STDLIB_H
#include <stdlib.h>
#endif 
#ifdef HAVE_UNISTD_H
#include <unistd.h>
#endif
#ifdef HAVE_STRING_H
#include <string.h>
#endif
#ifdef HAVE_LOCALE_H
#include <locale.h>
#endif


#include <errno.h>

#include <ne_request.h>
#include <ne_auth.h>
#include <ne_basic.h>
#include <ne_string.h>
#include <ne_uri.h>
#include <ne_socket.h>
#include <ne_locks.h>
#include <ne_alloc.h>
#include <ne_redirect.h>

/***************************************************************
	MACROS
 ***************************************************************/
/* boolean */
#define true 1
#define false 0

#define FILENAME_5K   "/mnt/test/ofoto5k"
#define FILENAME_15K  "/mnt/test/ofoto15k"
#define FILENAME_50K  "/mnt/test/ofoto50k"
#define FILENAME_900K "/mnt/test/ofoto900k"

/***************************************************************
	DECLS
 ***************************************************************/

typedef struct ses {
    ne_uri uri;
    ne_session *sess;
    int connected; /* non-zero when connected. */
    int isdav; /* non-zero if real DAV collection */
    ne_lock_store *locks; /* stored locks */
    char *lastwp; /* last working path. */
} session_t;

static session_t session;

static char *hc_host;
static int port = 8080;
static int verbose = 0;
static int validate = 0;
static time_t start, end;
static int count = 0;
static char *f_basename;

static void connection_status(void *ud, ne_conn_status status,
                              const char *info);
static void transfer_progress(void *ud, off_t progress, off_t total);

static void out_result(int ret);

/****************************************************************/

static void
usage()
{
    fprintf(stderr, "usage: of_put <hc_host> <start_dir> <end_dir> <file_basename> <files_per_dir> [validate] [verbose]\n");
    fprintf(stderr, "\t(hc_host can be host:port - default is 8080)\n");
    fprintf(stderr, "\t0 <= start_dir <= end_dir <= 255\n");
    exit(1);
}

static void
err(char * str)
{
    fprintf(stderr, "ERROR: %s\n", str);
    exit(1);
}

static void
quit(int val)
{
    time(&end);
    printf("put %d in %ld seconds\n", count, end-start);
    if (val)
        fprintf(stderr, "ERROR\n");
    exit(val);
}

static void
connect_server()
{
    ne_session *sess;
    ne_server_capabilities caps;
    int ret;

    /* set up the connection */

    ne_sock_init();

    memset(&session, 0, sizeof session);
    session.uri.scheme = ne_strdup("http");
    session.uri.host = hc_host;
    session.uri.port = port; 
    session.uri.path = ne_strdup("/webdav/"); /* always '/'-terminate */

    session.sess = sess = ne_session_create(session.uri.scheme, 
                                     session.uri.host, 
                                     session.uri.port);

    /* make the connection */

    ne_set_useragent(sess, "hctest/");  /* needed? */
/*
not needed to connect
    ne_lockstore_register(session.locks, sess);
    ne_redirect_register(sess);
*/
    /* convenient status */
    ne_set_status(sess, connection_status, NULL);
    ne_set_progress(sess, transfer_progress, NULL);

    /* execute connect */
    ret = ne_options(sess, session.uri.path, &caps);
    
    switch (ret) {
    case NE_OK:
	session.connected = true;
/*
	if (set_path(session.uri.path)) {
	    close_connection();
	}
*/
	break;
    case NE_CONNECT:
        printf("got NE_CONNECT\n");
        quit(1);
	break;
    case NE_LOOKUP:
	puts(ne_get_error(sess));
        quit(1);
	break;
    default:
	printf("Could not open collection (default connect err):\n%s\n",
	       ne_get_error(sess));
        quit(1);
	break;
    }
}

/***********************************************************
        FILES
 ***********************************************************/
                                                              
static int fd5k = -1;
static int fd15k = -1;
static int fd50k = -1;
static int fd900k = -1;

static int
do_open(char *fname)
{
    int fd = open(fname, O_RDONLY);
    if (fd == -1) {
        perror(fname);
        quit(1);
    }
    return fd;
}

static void
init_files()
{
    of_create_file(FILENAME_5K, 5);
    of_create_file(FILENAME_15K, 15);
    of_create_file(FILENAME_50K, 50);
    of_create_file(FILENAME_900K, 900);
                                                                                
    fd5k = do_open(FILENAME_5K);
    fd15k = do_open(FILENAME_15K);
    fd50k = do_open(FILENAME_50K);
    fd900k = do_open(FILENAME_900K);
}

static int
get_fd(int size_code)
{
    int fd;
    off_t pos;

    switch (size_code) {
      case FILE_5K:
        fd = fd5k; break;
      case FILE_15K: 
        fd = fd15k; break;
      case FILE_50K:
        fd = fd50k; break;
      case FILE_900K:
        fd = fd900k; break;
      default:
        err("size_code");
    }
    /* position to read whole file */
    pos = lseek(fd, 0, SEEK_SET);
    if (pos != 0) {
        perror("lseek()");
        exit(1);
    }
    return fd;
}

/* callback for md generator */
static void
put_file(char **md_vals, int size_code)
{
    int fd, ret;
    char remote_path[1024];
    /*
     *  construct path to put
     */
    if (size_code > -1) {
        switch (size_code) {
            case 0:
                sprintf(remote_path, "%s/%s", 
                        "/webdav/oFotoHashDirs", md_vals[0]);
                break;
            case 1:
                sprintf(remote_path, "%s/%s/%s", 
                        "/webdav/oFotoHashDirs", md_vals[0], md_vals[1]);
                break;
            case 2:
                sprintf(remote_path, "%s/%s/%s/%s", 
                        "/webdav/oFotoHashDirs",
                        md_vals[0], md_vals[1], md_vals[2]);
                break;
            case 3:
                sprintf(remote_path, "%s/%s/%s/%s/%s", 
                        "/webdav/oFotoHashDirs",
                        md_vals[0], md_vals[1], md_vals[2], md_vals[3]);
                break;
            case 4:
                sprintf(remote_path, "%s/%s/%s/%s/%s/%s", 
                        "/webdav/oFotoHashDirs",
                        md_vals[0], md_vals[1], md_vals[2], md_vals[3], 
                        md_vals[4]);
                break;
            case 5:
                sprintf(remote_path, "%s/%s/%s/%s/%s/%s/%s", 
                        "/webdav/oFotoHashDirs",
                        md_vals[0], md_vals[1], md_vals[2], md_vals[3], 
                        md_vals[4], md_vals[5]);
                break;
            default:
                return;
        }
        size_code = FILE_5K;
    } else {
        // full path
        sprintf(remote_path, "%s/%s/%s/%s/%s/%s/%s/%s", "/webdav/oFotoHashDirs",
                        md_vals[0], md_vals[1], md_vals[2],
                        md_vals[3], md_vals[4], md_vals[5], md_vals[6]);
    }
    if (hc_host == NULL) {
        printf("%s\n", remote_path);
        return;
    }
    if (verbose)
        printf("%s\n", remote_path);

    /*
     *  get fd for src file & put
     */
    fd = get_fd(size_code);
    ret = ne_put(session.sess, remote_path, fd);
    if (ret != NE_OK) {
        printf("%s\n", remote_path);
        out_result(ret);
        quit(1);
    }
    count++;
}

static void 
checkopt(const char *opt)
{
    if (!strcmp(opt, "verbose")) {
        verbose = 1;
        return;
    }
    if (!strcmp(opt, "validate")) {
        validate = 1;
        return;
    }
    usage();
}

int
main(int argc, char *argv[])
{
    int start_dir, end_dir;
    char *cp;
    int files_per_dir;

    /*
     *  parse args
     */
    if (argc < 6  ||  argc > 8)
        usage();

    hc_host = argv[1];
    cp = strtok(hc_host, ":");
    cp = strtok(NULL, ":");
    if (cp != NULL)
        port = atoi(cp);
    start_dir = atoi(argv[2]);
    if (start_dir < 0  ||  start_dir > MAX_DIR_DENSITY-1)
        usage();
    end_dir = atoi(argv[3]);
    if (end_dir < start_dir  ||  end_dir > MAX_DIR_DENSITY-1)
        usage();
    f_basename = argv[4];
    files_per_dir = atoi(argv[5]);
    //if (files_per_dir < 1)
        //usage();
    if (argc == 7)
        checkopt(argv[6]);
    if (argc == 8) {
        checkopt(argv[6]);
        checkopt(argv[7]);
    }

    /*
     *  init for test or real mode
     */
    time(&start);
    if (!strcmp(hc_host, "print")) {
        hc_host = NULL;
    } else {
        connect_server();
        init_files();
    }

    /*
     *  launch 'gets'
     */
    time(&start);
    of_gen_files(start_dir, end_dir, f_basename, files_per_dir, &put_file);

    /*
     *  clean up
     */
    quit(0);
    return 0;  /* for lint */
}

/****************************************************************
	STATIC
 ****************************************************************/

/* From ncftp.
   This function is (C) 1995 Mike Gleason, (mgleason@NcFTP.com)
 */
static void
sub_timeval(struct timeval *tdiff, struct timeval *t1, struct timeval *t0)
{
    tdiff->tv_sec = t1->tv_sec - t0->tv_sec;
    tdiff->tv_usec = t1->tv_usec - t0->tv_usec;
    if (tdiff->tv_usec < 0) {
        tdiff->tv_sec--;
        tdiff->tv_usec += 1000000;
    }
}

/* Smooth progress bar.
 * Doesn't update the bar more than once every 100ms, since this 
 * might give flicker, and would be bad if we are displaying on
 * a slow link anyway.
 */
static void pretty_progress_bar(off_t progress, off_t total)
{
    int len, n;
    double pc;
    static struct timeval last_call = {0};
    struct timeval this_call;
    
    if (total < 0)
	return;

    if (progress < total && gettimeofday(&this_call, NULL) == 0) {
	struct timeval diff;
	sub_timeval(&diff, &this_call, &last_call);
	if (diff.tv_sec == 0 && diff.tv_usec < 100000) {
	    return;
	}
	last_call = this_call;
    }
    if (progress == 0 || total == 0) {
	pc = 0;
    } else {
	pc = (double)progress / total;
    }
    len = pc * 30;
    printf("\rProgress: [");
    for (n = 0; n<30; n++) {
	putchar((n<len-1)?'=':
		 (n==(len-1)?'>':' '));
    }
    printf("] %5.1f%% of %" NE_FMT_OFF_T " bytes", pc*100, total);
    fflush(stdout);
}
/* Current output state */
static enum out_state {
    out_none, /* not doing anything */
    out_incommand, /* doing a simple command */
    out_transfer_start, /* transferring a file, not yet started */
    out_transfer_plain, /* doing a plain ... transfer */
    out_transfer_pretty /* doing a pretty progress bar transfer */
} out_state;

static void 
connection_status(void *ud, ne_conn_status status, const char *info)
{
/*
    if (get_bool_option(opt_quiet)) {
	return;
    }
*/
    switch (out_state) {
    case out_none:
	switch (status) {
	case ne_conn_namelookup:
	    //printf("Looking up hostname... ");
	    break;
	case ne_conn_connecting:
	    //printf("Connecting to server... ");
	    break;
	case ne_conn_connected:
	    //printf("connected.\n");
	    break;
	case ne_conn_secure:
	    printf("Using secure connection: %s\n", info);
	    break;
	}
	break;
    case out_incommand:
	/* fall-through */
    case out_transfer_start:
	switch (status) {
	case ne_conn_namelookup:
	case ne_conn_secure:
	    /* should never happen */
	    break;
	case ne_conn_connecting:
	    printf(" (reconnecting...");
	    break;
	case ne_conn_connected:
	    printf("done)");
	    break;
	}
	break;
    case out_transfer_plain:
	switch (status) {
	case ne_conn_namelookup:
	case ne_conn_secure:
	    break;
	case ne_conn_connecting:
	    printf("] reconnecting: ");
	    break;
	case ne_conn_connected:
	    printf("okay [");
	    break;
	}
	break;
    case out_transfer_pretty:
	switch (status) {
	case ne_conn_namelookup:
	case ne_conn_secure:
	    break;
	case ne_conn_connecting:
	    printf("\rTransfer timed out, reconnecting... ");
	    break;
	case ne_conn_connected:
	    printf("okay.");
	    break;
	}
	break;	
    }
    fflush(stdout);
}


static void 
transfer_progress(void *ud, off_t progress, off_t total)
{
    switch (out_state) {
    case out_none:
    case out_incommand:
	/* Do nothing */
	return;
    case out_transfer_start:
	if (isatty(STDOUT_FILENO) && total > 0) {
	    out_state = out_transfer_pretty;
	    putchar('\n');
	    pretty_progress_bar(progress, total);
	} else {
	    out_state = out_transfer_plain;
	    printf(" [.");
	}
	break;
    case out_transfer_pretty:
	if (total > 0) {
	    pretty_progress_bar(progress, total);
	}
	break;
    case out_transfer_plain:
	putchar('.');
	fflush(stdout);
	break;
    }
}

static void 
out_result(int ret)
{
    switch (ret) {
    case NE_OK:
	printf("succeeded.\n");
	break;
    case NE_AUTH:
    case NE_PROXYAUTH:
	printf("authentication failed.\n");
	break;
    case NE_CONNECT:
	printf("could not connect to server.\n");
	break;
    case NE_TIMEOUT:
	printf("connection timed out.\n");
	break;
    default:
        if (ret == NE_REDIRECT) {
            const ne_uri *dest = ne_redirect_location(session.sess);
            if (dest) {
                char *uri = ne_uri_unparse(dest);
                printf("redirect to %s\n", uri);
                ne_free(uri);
                break;
            }
        }
        printf("failed:  %s\n", ne_get_error(session.sess));
	break;
    }
}

