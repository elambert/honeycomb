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
#include <ne_props.h>

/***************************************************************
	MACROS
 ***************************************************************/
/* boolean */
#define true 1
#define false 0

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
static int leafdirs = 0;
static time_t start, end;
static int count = 0;

static void connection_status(void *ud, ne_conn_status status,
                              const char *info);
static void transfer_progress(void *ud, off_t progress, off_t total);

/****************************************************************/

static void
usage()
{
    fprintf(stderr, "usage: of_mkdirs <hc_host> <start_dir> <end_dir> [verbose]\n");
    fprintf(stderr, "\t(hc_host can be host:port - default is 8080)\n");
    fprintf(stderr, "\t0 <= start_dir <= end_dir <= 255\n");
    exit(1);
}

static void
quit(int val)
{
    time(&end);
    printf("got %d in %ld seconds\n", count, end-start);

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
        exit(1);
	break;
    case NE_LOOKUP:
	puts(ne_get_error(sess));
        exit(1);
	break;
    default:
	printf("Could not open collection:\n%s\n",
	       ne_get_error(sess));
        exit(1);
	break;
    }
}

static void
mk_dir(char **md_vals, int depth)
{
    /*
     *  construct path to check
     */
    int ret;
    char remote_path[1024];
    switch (depth) {
      case 1:
        sprintf(remote_path, "%s/%s", "/webdav/oFotoHashDirs", md_vals[0]);
        break;
      case 2:
        sprintf(remote_path, "%s/%s/%s", "/webdav/oFotoHashDirs",
                        md_vals[0], md_vals[1]);
        break;
      case 3:
        sprintf(remote_path, "%s/%s/%s/%s", "/webdav/oFotoHashDirs",
                        md_vals[0], md_vals[1], md_vals[2]);
        break;
      case 4:
        sprintf(remote_path, "%s/%s/%s/%s/%s", "/webdav/oFotoHashDirs",
                        md_vals[0], md_vals[1], md_vals[2],
                        md_vals[3]);
        break;
      case 5:
        sprintf(remote_path, "%s/%s/%s/%s/%s/%s", "/webdav/oFotoHashDirs",
                        md_vals[0], md_vals[1], md_vals[2],
                        md_vals[3], md_vals[4]);
        break;
      case 6:
        sprintf(remote_path, "%s/%s/%s/%s/%s/%s/%s", "/webdav/oFotoHashDirs",
                        md_vals[0], md_vals[1], md_vals[2],
                        md_vals[3], md_vals[4], md_vals[5]);
        break;
    }
    if (hc_host == NULL) {
        printf("%s\n", remote_path);
        return;
    }
    if (verbose)
        printf("%s\n", remote_path);

    ret = ne_mkcol(session.sess, remote_path);
    if (ret != NE_OK) {
        if (strstr(ne_get_error(session.sess), "409 Conflict")) {
            if (verbose)
                printf("  already exists\n");
            else
                printf("already exists: %s\n", remote_path);
            return;
        }
        printf("failed:  %s\n", ne_get_error(session.sess));
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
    if (!strcmp(opt, "leafdirs")) {
        leafdirs = 1;
        return;
    }
    usage();
}

int
main(int argc, char *argv[])
{
    int start_dir, end_dir;
    char *cp;

    /*
     *  parse args
     */
    if (argc < 4  ||  argc > 6)
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
    if (argc > 4)
        checkopt(argv[4]);
    if (argc > 5)
        checkopt(argv[5]);

    /*
     *  init for test or real mode
     */
    if (!strcmp(hc_host, "print")) {
        hc_host = NULL;
    } else {
        connect_server();
    }

    /*
     *  launch findprop's
     */
    time(&start);
    of_gen_files(start_dir, end_dir, NULL, ALL_DIRS, &mk_dir);

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
	    printf("Looking up hostname... ");
	    break;
	case ne_conn_connecting:
	    printf("Connecting to server... ");
	    break;
	case ne_conn_connected:
	    printf("connected.\n");
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

