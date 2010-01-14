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

#define DFL_CONN_COUNT    32    /* max. number of connections.*/
#define DFL_DURATION      3600  /* s */
#define DFL_FILE_SIZE     100   /* bytes */
#define DFL_INTERVAL      360   /* s */

#define POLL_INTERVAL     500	/* timeout for poll */

/* Global: parameters */
FILE* out_file = NULL;
int verbose = 0;
int sequential = 0;
int multi_PRNG = 0;
const char* progname;

static int num_files, num_errors, num_dups;
static long start_time, end_time, elapsed, last_report, svc_time;

static int storeFiles(url_t*, int nconns, long duration, long interval);
static void prHeader(url_t* url, int op, int nconns, unsigned int seed,
                     unsigned long file_length);
static void prStats(time_t current_time);

static unsigned int seed;
static unsigned int get_seed(const char* s);
static int parseOp(const char* s);
static const char* opName(int op);

static void cleanup(int sig) {
    time_t current_time = time(0);

    fprintf(stderr, "[@%ld] Got signal %d\n", current_time, sig);
    fflush(stderr);

    if (sig == SIGPIPE)
        // Ignoring it (we expect EPIPE to be returned by sys calls)
        return;

    elapsed = current_time - last_report;
    prStats(current_time);
    elapsed = current_time - start_time;
    printf("# Mean: %d files in %lds = %.1f/s\n",
           num_files, elapsed, num_files/(float)elapsed);

    conn_terminate();
    exit(0);
}

int main(int argc, char* argv[]) {
    extern char *optarg;
    extern int optind;
    int c, rc;

    /* Defaults */
    unsigned long file_length = DFL_FILE_SIZE;  /* bytes */
    int conn_count = DFL_CONN_COUNT;
    long interval = DFL_INTERVAL;
    unsigned long duration = DFL_DURATION;    /* seconds */

    int op = DFL_OP;
    url_t* url = 0;

    srandom((unsigned int) now());
    progname = argv[0];

    while ((c = getopt(argc, argv, "t:l:o:c:i:S:O:hvsR")) != EOF)
        switch (c) {
        case 't': duration = getint(c, optarg); break;
        case 'l': file_length = getint(c, optarg); break;
        case 'c': conn_count = getint(c, optarg); break;
        case 'i': interval = getint(c, optarg); break;
        case 'o': out_file = open_append_file(optarg); break;
        case 'S': seed = get_seed(optarg); break;
        case 'O': op = parseOp(optarg); break;

        case 'v': verbose++; break;
        case 's': sequential++; break;
        case 'R': multi_PRNG++; break;

        case 'h':
        default:
            usage();
        }
  
    /* Exactly one argument is required */
    if (argc - optind != 1) {
        fprintf(stderr, "No URL pattern.\n");
        usage();
    }
  
    if ((url = pattern_parse(argv[optind])) == 0)
        exit(2);

    signal(SIGINT, cleanup);
    signal(SIGTERM, cleanup);
    signal(SIGQUIT, cleanup);
    signal(SIGKILL, cleanup);

    /* Convert SIGPIPE to EPIPE */
    signal(SIGPIPE, SIG_IGN);

    if ((rc = fsm_init(file_length, op)) != 0)
        exit(rc);

    prHeader(url, op, conn_count, seed, file_length);

    /* put files */
    rc = storeFiles(url, conn_count, interval, duration);

    if (out_file != NULL)
        fclose(out_file);

    exit(rc);
}

void usage() {
    fprintf(stderr,
            "\nUsage: %s [-v] [-s] [-R] [-l length] [-t time] [-c conns] "
                    "[-i interval] \\\n"
            "    [-o outfile] [-S seed] [-O op] URL-pattern\n", progname);
    fprintf(stderr, "Options (defaults in brackets)\n");
    fprintf(stderr, "    -l length  : length of file to send [%d]\n",
            DFL_FILE_SIZE);
    fprintf(stderr, "    -t time    : duration of test [%d]\n",
            DFL_DURATION);
    fprintf(stderr, "    -c number  : number of connections [%d]\n",
            DFL_CONN_COUNT);
    fprintf(stderr, "    -i interv  : print report every so often [%d]\n",
            DFL_INTERVAL);
    fprintf(stderr, "    -S seed    : seed for random numbers (any string)\n");
    fprintf(stderr, "    -O op      : webdav method to use [%s]\n",
            opName(DFL_OP));
    fprintf(stderr, "    -o outfile : file to write paths of stored files\n");
    fprintf(stderr, "    -v         : increase verbosity (may be used multiple times)\n");
    fprintf(stderr, "    -s         : generate values sequentially\n");
    fprintf(stderr, "    -R         : use different PRNG instance for each connection\n");
    fprintf(stderr, "The URL-pattern may include any number of patterns enclosed in {}. "
            "A pattern\n    has an optional format, then either a list "
            "of values or a range.\nExample:\n    "
            "http://dev321-data:8080/webdav/{02x:0-256}/{02x:0,2,4,6,8}/Img-{04x:0-65535}.jpg\n");
    exit(1);
}

static unsigned int get_seed(const char* s)
{
    unsigned int seed = 0;
    unsigned int l = 0;
    int i = 0, c;
    char* p = (char*) &l;

    while ((c = *s++) != 0) {
        p[i++] = c;
        if (i > sizeof(l)) {
            seed ^= l;
            l = i = 0;
        }
    }
    if (i > 0)
        seed ^= l;

    return seed;
}

static int storeFiles(url_t* url, int nconns, long interval, long duration)
{
    int i, rc;

    long current_time = 0;

    /* Init the OS-dependent poll structures */
    if ((rc = pfd_init(nconns)) != 0)
        exit(rc);

    /* Init the connection module */
    if ((rc = conn_init(nconns, seed, (struct sockaddr*)&url->addr)) != 0)
        exit(rc);

    current_time = start_time = now();
    end_time = current_time + duration;

    /* Make the report times aligned to integral multiples of interval */
    last_report = current_time - current_time % interval;

    /* The array that ready file descriptors are returned in */
    pfd_t* pollfds = malloc(sizeof(pfd_t) * nconns);
    if (pollfds == 0) {
        fprintf(stderr, "Couldn't allocate pollfd array\n");
        exit(3);
    }

    while (current_time < end_time) {

        int n_fds = conn_getReadyFiles(pollfds, POLL_INTERVAL);
        if (n_fds < 0)
            return -n_fds;

        if (n_fds > 0 && verbose > 1)
            fprintf(stderr, "Got %d fds\n", n_fds);

        for (i = 0; i < n_fds; i ++) {
            conn_t* conn;

            /* Step the FSM for the ready fd */
            int rc = fsm_step(url, &pollfds[i]);

	    switch(rc) {

            case ST_OK:
                num_files++;
                current_time = now();
                conn = conn_get(pfd_getfd(&pollfds[i]));
                svc_time += current_time - conn->start_time;
                break;

            case ST_DUP:
                num_dups++;
                break;

            case ST_ERROR:
                num_errors++;
                break;

            case ST_CONTINUE:
                break;
            }
        }

        current_time = now();
        elapsed = current_time - last_report;

        if (elapsed >= interval) {
            prStats(current_time);

            last_report = current_time;
            num_files = num_errors = num_dups = 0; 
            svc_time = 0;
        }
    }

    elapsed = current_time - last_report;
    prStats(current_time);

    /* close outstanding connections and clean up */
    conn_terminate();

    return 0;
}

static void prHeader(url_t* url, int op, int conns, unsigned int seed,
                     unsigned long file_length)
{
    printf("# (%u) %s:%d %s x%d",
           seed, url->server, url->port, opName(op), conns);
    if (op == OP_PUT) {
        printf(" %luB", file_length);
        printf("\n# Time     NFiles  Err   Dup  Rate   Mean   Total_files\n");
    }
    else
        printf("\n# Time     NFiles  Err   404  Rate   Mean   Total_files\n");
    fflush(stdout);
}

static void prStats(time_t current_time)
{
    static long total_files = 0;

    float rate = num_files/(float)elapsed;
    float mean = svc_time/(float)num_files;

    total_files += num_files;

    printf("%8ld %6d %4d %5d %5.2f %6.2f  %12ld\n",
           current_time, num_files, num_errors, num_dups, rate, mean,
           total_files);
    fflush(stdout);
}

static int parseOp(const char* s)
{
    if (strcasecmp(s, "put") == 0) return OP_PUT;

    if (strcasecmp(s, "get") == 0) return OP_GET;

    fprintf(stderr, "Method \"%s\" unimplemented.\n", s);
    usage();

    /*NOTREACHED*/
    return -1;
}

static const char* opName(int op) {
    switch (op) {
    case OP_PUT: return "PUT";
    case OP_GET: return "GET";
    default: return "???";
    }
}
