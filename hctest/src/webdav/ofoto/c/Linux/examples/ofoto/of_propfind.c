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
static int dir_level = NE_DEPTH_INFINITE;
static time_t start, end;
static int count = 0;

static void connection_status(void *ud, ne_conn_status status,
                              const char *info);
static void transfer_progress(void *ud, off_t progress, off_t total);

static void out_result(int ret);

/****************************************************************/

static void
usage()
{
    fprintf(stderr, "usage: propfind <hc_host> <path> [only|children]\n");
    fprintf(stderr, "\t(hc_host can be host:port - default is 8080)\n");
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

/*
 *  instantiation of ne_propset_iterator()
 */
int
propset_handler(void *userdata, const ne_propname *pname, const char *value,
                const ne_status *status)
{
    /*printf("%s\n", pname->nspace);*/
    if (strlen(pname->name) < 4)
        printf("    %s\t\t\t%s\n", pname->name, value);
    else if (strlen(pname->name) < 12)
        printf("    %s\t\t%s\n", pname->name, value);
    else
        printf("    %s\t%s\n", pname->name, value);
    return 0;
}

/*
 *  instantiation of ne_props_result()
 */
void
obj_prop_handler(void *userdata, const char *href, 
             const ne_prop_result_set *results)
{
    printf("%s\n", href);
    ne_propset_iterate(results, &propset_handler, NULL);
    count++;
}

static void
propfind(char *remote_path, int depth)
{
    int ret;
    printf("==== %s\n", remote_path);

    ret = ne_simple_propfind(session.sess, remote_path, depth, NULL,
                             &obj_prop_handler, NULL);
    if (ret != NE_OK) {
        out_result(ret);
        quit(1);
    }
}

static void 
checkopt(const char *opt)
{
    if (!strcmp(opt, "only")) {
        dir_level = NE_DEPTH_ZERO;
        return;
    }
    if (!strcmp(opt, "children")) {
        dir_level = NE_DEPTH_ONE;
        return;
    }
    usage();
}

int
main(int argc, char *argv[])
{
    char *cp, *remote_path;

    /*
     *  parse args
     */
    if (argc < 3  ||  argc > 4)
        usage();

    hc_host = argv[1];
    cp = strtok(hc_host, ":");
    cp = strtok(NULL, ":");
    if (cp != NULL)
        port = atoi(cp);
    remote_path = argv[2];
    if (argc > 3)
        checkopt(argv[3]);

    connect_server();

    time(&start);
    propfind(remote_path, dir_level);

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
