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

/* Defaults for parameters */
#define DFL_CONN_COUNT      32  /* max. number of connections.*/
#define DFL_DURATION      3600  /* s */
#define DFL_FILE_SIZE      100  /* bytes */
#define DFL_INTERVAL       360  /* s */

#define SLEEP_INTERVAL       5	/* sleep time for main loop */

/* Global: parameters */
FILE* oid_file = NULL;
int loglevel = 0;
int sequential = 0;
const char* progname;
int method = DFL_OP;

int num_threads;
thread_t threads[MAX_THREADS];

static int num_files, num_errors, num_dups, num_mismatch;
static long start_time, end_time, elapsed, last_report;
static float svc_time = 0.0;
static long total_files, total_errors, total_mismatch;

static int storeFiles(url_t*, int nthreads, long duration, long interval,
                      unsigned long file_length);
static void prHeader(url_t* url, int nthreads, unsigned int seed,
                     unsigned long file_length);
static void prStats(time_t current_time);
static void prTrailer(time_t current_time);

static unsigned int seed;
static char seed_string[MAX_SEED_SIZE];
static unsigned int get_seed(const char* s);

static int parseOp(const char* s);
static const char* opName(int op);
static void killThreads();
static void cleanup(int sig);
static void init_tmap();

/* 
 * If a thread encounters an error, it will set
 * failure_encountered. If exit_on_failure is set and a thread
 * encounters a failure, all threads will exit.
 */
int exit_on_failure = 0;
int failure_encountered = 0;

static char u_prng_state[RAND_STATE_SIZE];

/*
 * Since the mutex is intra-process, we don't need mutex_init. This
 * would be the call if we did:
 *     mutex_init(&stats_lock, USYNC_THREAD | LOCK_RECURSIVE, 0);
 */
static mutex_t stats_lock;

void usage() {
    fprintf(stderr,
            "\nUsage: %s [-v] [-s] [-R] [-E] [-l length] [-t time] [-c conns] "
            "\\\n    [-i interval] [-o outfile] [-S seed] [-O op] URL-pattern\n",
            progname);
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
    fprintf(stderr, "    -F OIDfile : file to read/write OIDs of stored files\n");
    fprintf(stderr, "    -v         : increase verbosity (may be used multiple times)\n");
    fprintf(stderr, "    -s         : generate values sequentially\n");
    fprintf(stderr, "    -E         : exit on failure\n");
    fprintf(stderr, "The URL-pattern may include any number of patterns enclosed in {}. "
            "A pattern\nhas an optional format, then either a list "
            "of values or a range.\nExample:\n    "
            "multiload -O get -S test.12a -c 100 -l 1048575 -t 86400 \\\n"
            "        http://dev321-data:8080/webdav/epochAlpha/{0-99999}/file{02x:0-256}/{02x:0,2,4,6,8}/Img-{04x:0-65535}.txt\n"
            "\nSee https://hc-twiki.sfbay.sun.com/twiki/bin/view/Main/MultiLoad for more.\n\n");
    exit(ES_USAGE);
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

    char oid_fname[BUFSIZ];

    url_t* url = 0;

    util_init();

    progname = argv[0];
    oid_fname[0] = 0;

    while ((c = getopt(argc, argv, "t:l:F:c:i:S:O:hvsRE")) != EOF)
        switch (c) {
        case 't': duration = getint(c, optarg); break;
        case 'l': file_length = getint(c, optarg); break;
        case 'c': conn_count = getint(c, optarg); break;
        case 'i': interval = getint(c, optarg); break;
        case 'F': strncpy(oid_fname, optarg, sizeof(oid_fname)); break;
        case 'S': seed = get_seed(optarg); break;
        case 'O': method = parseOp(optarg); break;

        case 'v': loglevel++; break;
        case 's': sequential++; break;
        case 'E': exit_on_failure++; break;

        case 'h':
        default:
            usage();
        }
  
    /* Exactly one argument is required */
    if (argc - optind != 1) {
        fprintf(stderr, "No URL pattern.\n");
        usage();
    }

    if (*oid_fname != 0) {
        oid_file = fopen(oid_fname, (method == OP_GET)? "r" : "w");
        if (oid_file == 0)
            msg("Cannot open OID file \"%s\"", oid_fname);
    }

    /* Parse the URL pattern */
    if ((url = pattern_parse(argv[optind])) == 0)
        exit(ES_USAGE);

    /* Trap all signals */
    signal(SIGINT, cleanup);
    signal(SIGTERM, cleanup);
    signal(SIGQUIT, cleanup);
    signal(SIGKILL, cleanup);

    /* Convert SIGPIPE to EPIPE */
    signal(SIGPIPE, SIG_IGN);

    if (seed == 0)
        seed = now();

    /* Initialise the PRNG and make it the default */
    initstate(seed, u_prng_state, sizeof(u_prng_state));
    setstate(u_prng_state);

    /* Initialize buffers etc. */
    task_init(url, file_length, opName(method), duration);

    /* Reset the PRNG to recover anything task_init may have used */
    initstate(seed, u_prng_state, sizeof(u_prng_state));
    setstate(u_prng_state);

    prHeader(url, conn_count, seed, file_length);

    if (oid_file != NULL)
        /* Save parameters, so user can re-run to generate same URLs */
        fprintf(oid_file, "# \"%s\" \"%s\"\n", seed_string, url->raw_string);

    /* put files */
    rc = storeFiles(url, conn_count, interval, duration, file_length);

    if (oid_file != NULL)
        fclose(oid_file);

    exit(rc);
}

static int storeFiles(url_t* url, int nconns, long interval, long duration,
                      unsigned long file_length)
{
    int i, es = ES_OK;
    long current_time = 0, next_report = 0;

    current_time = start_time = now();
    end_time = current_time + duration;

    /* Make the report times aligned to integral multiples of interval */
    last_report = current_time - current_time % interval;
    next_report = last_report + interval;

    /* Launch the worker threads */
    num_threads = nconns;
    for (i = 0; i < num_threads; i++)
        thr_create(NULL, 0, task_run, (void*)i, 0, &threads[i]);
    init_tmap();

    /* This thread only wakes up periodically to print the stats */
    while (current_time < end_time) {
        if (exit_on_failure && failure_encountered)
            break;

        if (next_report > current_time)
            sleep(min(SLEEP_INTERVAL, next_report - current_time));

        current_time = now();
        if (current_time >= next_report) {
            prStats(current_time);
            next_report = min(next_report + interval, end_time);
        }
    }

    if (exit_on_failure && failure_encountered) {
        msg("Failure(s) encountered; quitting.");
        es = ES_REMOTE;
    }

    if (loglevel)
        msg("Waiting for worker threads to exit...");

    /* Wait for all threads to complete */
    for (i = 0; i < num_threads; i++)
        thr_join(threads[i], 0, 0);

    prTrailer(current_time);
    return es;
}

static unsigned int get_seed(const char* s)
{
    unsigned int seed = 0;
    unsigned int l = 0;
    int i = 0, c;
    char* p = (char*) &l;

    strncpy(seed_string, s, sizeof(seed_string));
    seed_string[sizeof(seed_string) - 1] = 0; /* Just in case */

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

static void killThreads()
{
    int i;
    for (i = 0; i < num_threads; i++)
        thr_kill(threads[i], SIGQUIT);
}

static void cleanup(int sig) {
    time_t current_time = time(0);

    msg("[@%ld] Got signal %d", current_time, sig);

    if (sig == SIGPIPE)
        /* Ignoring it (we expect EPIPE to be returned by sys calls) */
        return;

    killThreads();

    prTrailer(current_time);

    exit(ES_OK);
}

void record_result(int result_type, double elapsed_time, const char* oid,
                  int serial)
{
    mutex_lock(&stats_lock);

    if (oid_file != NULL) {
        if (0 && method == OP_GET) {
            /*
             * OID verification is disabled for now because we won't
             * necessarily get back OIDs in the same order as we
             * stored them. We need to compare oid against the
             * serial'th OID in the list. Each OID is OID_LENGTH
             * chars, plus one EOL character; we can
             * lseek(OID_LENGTH*serial) in oid_file to read it.
             *
             * CAVE! Don't forget that there's a first line that
             * needs to be skipped! lseek(firstline_len + OID_LENGTH*serial)
             */
            char oid_buf[MAX_HEADER_SIZE];
            if (fgets(oid_buf, sizeof(oid_buf), oid_file) == 0) {
                msg("Error! EOF on OID file!");
                fclose(oid_file);
                oid_file = 0;
            }
            else {
                /* Check the OIDs */
                if (strncmp(oid, oid_buf, sizeof(oid_buf)) != 0)
                    result_type = REQ_OID_MISMATCH;
            }
        }
        else if (method == OP_PUT) {
            assert(oid != 0 && strlen(oid) == OID_LENGTH);
            /* XXX -- should first lseek to the right place in the file */
            fprintf(oid_file, "%s\n", oid);
        }
    }

    switch (result_type) {
    case REQ_OK:
        num_files++;
        svc_time += elapsed_time;
        break;

    case REQ_DUP:
        num_dups++;
        break;

    case REQ_ERROR:
        num_errors++;
        break;

    case REQ_OID_MISMATCH:
        num_mismatch++;
        break;
    }

    mutex_unlock(&stats_lock);
}

static void prHeader(url_t* url, int conns, unsigned int seed,
                     unsigned long file_length)
{
    printf("# \"%s\" (%d) %s:%d %s x%d",
           seed_string, seed, url->server, url->port, opName(method), conns);
    if (method == OP_PUT) {
        printf(" %luB", file_length);
        printf("\n# Time     NFiles  Err   Dup  Rate   Mean   Total_files\n");
    }
    else
        printf("\n# Time     NFiles  Err   404  Rate   Mean   Total_files\n");
    fflush(stdout);
}

static void prStats(time_t current_time)
{
    int f, e, m, d;
    long el;
    float t, rate, mean;

    mutex_lock(&stats_lock);

    elapsed = current_time - last_report;

    total_files += num_files;
    total_errors += num_errors;
    total_mismatch += num_mismatch;

    el = elapsed;
    f = num_files;
    e = num_errors;
    d = num_dups;
    m = num_mismatch;
    t = svc_time;

    num_files = num_errors = num_dups = num_mismatch = 0; 
    svc_time = 0.0;

    last_report = current_time;

    mutex_unlock(&stats_lock);

    rate = f/(float)el;
    mean = t/(float)f;

    printf("%8ld %6d %4d %5d %5.2f %6.2f  %12ld\n", current_time,
           f, e + m, d, rate, mean, total_files);

    fflush(stdout);
}

static void prTrailer(time_t current_time)
{
    long t = current_time - start_time;

    prStats(current_time);
    printf("# Mean: %ld files (%ld errors, %ld mismatch) in %lds = %.1f/s\n",
           total_files, total_errors, total_mismatch, t,
           total_files/(float)t);
}

static int parseOp(const char* s)
{
    if (strcasecmp(s, "put") == 0) return OP_PUT;

    if (strcasecmp(s, "get") == 0) return OP_GET;

    msg("Method \"%s\" unimplemented.\n", s);
    usage();

    /*NOTREACHED*/
    return -1;
}

const char* opName(int op) {
    switch (op) {
    case OP_PUT: return "PUT";
    case OP_GET: return "GET";
    default:
        msg("Internal error: unknown operation %d", op);
        exit(ES_INTERNAL);
    }

    /*NOTREACHED*/
    return 0;
}

/*
 * Thread ID lookup stuff: on Linux, pthread_t is a pointer, not a
 * nice small integer the way thread_t is on Solaris. This uses
 * qsort(3)/bsearch(3) to map a pthread_t to its index in
 * threads[].
 */
typedef struct {
    int index;
    thread_t thread;
} tmap_t;

static tmap_t tmaps[MAX_THREADS];

static int cmp(const void* a, const void* b)
{
    tmap_t* t1 = (tmap_t*) a;
    tmap_t* t2 = (tmap_t*) b;
    if (t1->thread < t2->thread)
        return -1;
    return t1->thread > t2->thread;
}

static void init_tmap()
{
#ifdef Linux
    int i;
    for (i = 0; i < num_threads; i++) {
        tmaps[i].index = 1 + i; /* reserve 0 for the main thread */
        tmaps[i].thread = threads[i];
    }
    qsort(tmaps, num_threads, sizeof(tmap_t), cmp);
#endif /* Linux */
}

unsigned int self() {
#ifdef SunOS
    return thr_self() - 1;
#endif

#ifdef Linux
    tmap_t me;
    me.thread = thr_self();
    tmap_t* t =
        bsearch(&me, tmaps, num_threads, sizeof(*tmaps), cmp);
    if (t == 0)
        return 0;
    return t->index;
#endif
}

