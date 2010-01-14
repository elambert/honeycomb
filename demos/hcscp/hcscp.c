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



#include <hcnvoaez.h>

#include "includes.h"

#include "atomicio.h"
#include "xmalloc.h"
#include "progressmeter.h"

#define HC_IP "dev317-data"
#define HC_PORT 8080

static int errs = 0;
static int remin, remout;
static int iamrecursive = 0;

/* This is used to store the pid of ssh_program */
static pid_t do_cmd_pid = -1;

/* This is set to non-zero to enable verbose mode. */
static int verbose_mode = 0;

/* Name of current file being transferred. */
static char *curfile;

/* This is set to zero if the progressmeter is not desired. */
static int showprogress = 1;

static int pflag = 0;

static hc_session_t *the_session;

typedef struct {
	size_t cnt;
	char *buf;
} BUF;

typedef struct {
    int fd;
    long long size;
    long nbread;
} data_stream_t;

static void
killchild(int signo)
{
	if (do_cmd_pid > 1) {
		kill(do_cmd_pid, signo ? signo : SIGTERM);
		waitpid(do_cmd_pid, NULL, 0);
	}

	if (signo)
		_exit(1);
	exit(1);
}

static void
run_err(const char *fmt,...)
{
	static FILE *fp;
	va_list ap;

	++errs;
	if (fp == NULL && !(fp = fdopen(remout, "w")))
		return;
	(void) fprintf(fp, "%c", 0x01);
	(void) fprintf(fp, "scp: ");
	va_start(ap, fmt);
	(void) vfprintf(fp, fmt, ap);
	va_end(ap);
	(void) fprintf(fp, "\n");
	(void) fflush(fp);
}

static void
run_log(const char *fmt,...)
{
	static FILE *fp;
	va_list ap;

	++errs;
	if (fp == NULL && !(fp = fdopen(remout, "w")))
		return;
	(void) fprintf(fp, "%c", 0x01);
	va_start(ap, fmt);
	(void) vfprintf(fp, fmt, ap);
	va_end(ap);
	(void) fprintf(fp, "\n");
	(void) fflush(fp);
}

static BUF *
allocbuf(BUF *bp, int fd, int blksize)
{
	size_t size;
#ifdef HAVE_STRUCT_STAT_ST_BLKSIZE
	struct stat stb;

	if (fstat(fd, &stb) < 0) {
		run_err("fstat: %s", strerror(errno));
		return (0);
	}
	size = roundup(stb.st_blksize, blksize);
	if (size == 0)
		size = blksize;
#else /* HAVE_STRUCT_STAT_ST_BLKSIZE */
	size = blksize;
#endif /* HAVE_STRUCT_STAT_ST_BLKSIZE */
	if (bp->cnt >= size)
		return (bp);
	if (bp->buf == NULL)
		bp->buf = xmalloc(size);
	else
		bp->buf = xrealloc(bp->buf, size);
	memset(bp->buf, 0, size);
	bp->cnt = size;
	return (bp);
}

static void
lostconn(int signo)
{
	if (signo)
		_exit(1);
	else
		exit(1);
}

static int
response(void)
{
	char ch, *cp, resp, rbuf[2048];

	if (atomicio(read, remin, &resp, sizeof(resp)) != sizeof(resp))
		lostconn(0);

	cp = rbuf;
	switch (resp) {
	case 0:		/* ok */
		return (0);
	default:
		*cp++ = resp;
		/* FALLTHROUGH */
	case 1:		/* error, followed by error msg */
	case 2:		/* fatal error, "" */
		do {
			if (atomicio(read, remin, &ch, sizeof(ch)) != sizeof(ch))
				lostconn(0);
			*cp++ = ch;
		} while (cp < &rbuf[sizeof(rbuf) - 1] && ch != '\n');

		++errs;
		if (resp == 1)
			return (-1);
		exit(1);
	}
	/* NOTREACHED */
}

static longn
data_source(void *stream,
            char *buf,
            long n)
{
    data_stream_t *data_stream = (data_stream_t*)stream;
    int res;
    long max = data_stream->size - data_stream->nbread;

    if (max == 0) {
      return 0;
    }

    res = atomicio(read, data_stream->fd, buf, n < max ? n : max);
    
    *retbytes = res;
    data_stream->nbread += res;

    /*     if (verbose_mode) */
    /*         fprintf(stderr, "data_source %ld %ld %ld\n", */
    /*                 res, data_stream->nbread, data_stream->size); */
    
    if (res == 0) {
      return 0;
    }
    return res;
}

static void
populate_md(char *filename,
            char *values[])
{
    char *s;
    int index;

    index = strlen(filename)-1;
    while ((index >= 0) && (filename[index] != '.')) {
        index--;
    }

    if (index == -1) {
        // No extension
        values[0] = filename;
        values[1] = "???";
    } else {
        filename[index] = '\0';
        values[0] = filename;
        values[1] = filename+index+1;
    }

    values[2] = cuserid(NULL);
}

static void
sink()
{
	enum {
		YES, NO, DISPLAYED
	} wrerr;
	off_t i;
	size_t j, count;
	int first, mask, mode, ofd, omode;
    data_stream_t data_stream;
	int setimes, wrerrno = 0;
	char ch, *cp, *why, *vect[1], buf[2048];
	struct timeval tv[2];
    hcerr_t hcerr;
    hc_system_record_t system_record;
    int32_t response_code;
    char *hcerrstr;
    char *filename_copy;

    char *md_names[] = { "scp.filename", "scp.extension", "scp.user" };
    char *md_values[3];
    hc_nvr_t *nvr;



#define	atime	tv[0]
#define	mtime	tv[1]
#define	SCREWUP(str)	{ why = str; goto screwup; }

	setimes = 0;
	mask = umask(0);

	(void) atomicio(vwrite, remout, "", 1);
	for (first = 1;; first = 0) {
		cp = buf;
		if (atomicio(read, remin, cp, 1) != 1)
			return;
		if (*cp++ == '\n')
			SCREWUP("unexpected <newline>");
		do {
			if (atomicio(read, remin, &ch, sizeof(ch)) != sizeof(ch))
				SCREWUP("lost connection");
			*cp++ = ch;
		} while (cp < &buf[sizeof(buf) - 1] && ch != '\n');
		*cp = 0;
		if (verbose_mode)
			fprintf(stderr, "\nSink: %s", buf);

		if (buf[0] == '\01' || buf[0] == '\02') {
			if (buf[0] == '\02')
				exit(1);
			++errs;
			continue;
		}

		if (buf[0] == 'E') {
			(void) atomicio(vwrite, remout, "", 1);
			return;
		}
		if (ch == '\n')
			*--cp = 0;

		cp = buf;
		if (*cp == 'T') {
			setimes++;
			cp++;
			mtime.tv_sec = strtol(cp, &cp, 10);
			if (!cp || *cp++ != ' ')
				SCREWUP("mtime.sec not delimited");
			mtime.tv_usec = strtol(cp, &cp, 10);
			if (!cp || *cp++ != ' ')
				SCREWUP("mtime.usec not delimited");
			atime.tv_sec = strtol(cp, &cp, 10);
			if (!cp || *cp++ != ' ')
				SCREWUP("atime.sec not delimited");
			atime.tv_usec = strtol(cp, &cp, 10);
			if (!cp || *cp++ != '\0')
				SCREWUP("atime.usec not delimited");
			(void) atomicio(vwrite, remout, "", 1);
			continue;
		}
		if (*cp != 'C' && *cp != 'D') {
			/*
			 * Check for the case "rcp remote:foo\* local:bar".
			 * In this case, the line "No match." can be returned
			 * by the shell before the rcp command on the remote is
			 * executed so the ^Aerror_message convention isn't
			 * followed.
			 */
			if (first) {
				run_err("%s", cp);
				exit(1);
			}
			SCREWUP("expected control record");
		}
		mode = 0;
		for (++cp; cp < buf + 5; cp++) {
			if (*cp < '0' || *cp > '7')
				SCREWUP("bad mode");
			mode = (mode << 3) | (*cp - '0');
		}
		if (*cp++ != ' ')
			SCREWUP("mode not delimited");

        data_stream.size = 0;
		for (data_stream.size = 0; isdigit(*cp);)
			data_stream.size = data_stream.size * 10 + (*cp++ - '0');

		if (*cp++ != ' ')
			SCREWUP("size not delimited");
		if ((strchr(cp, '/') != NULL) || (strcmp(cp, "..") == 0)) {
			run_err("error: unexpected filename: %s", cp);
			exit(1);
		}

		curfile = cp;
		if (buf[0] == 'D') {
            run_err("Warning: ignored directory %s\n", curfile);
            sink();
            continue;
		}
		omode = mode;
		mode |= S_IWRITE;

        if (0) {
        bad:			run_err("BAD: %s", strerror(errno));
        }

        /*
         * Honeycomb specific code
         */

		(void) atomicio(vwrite, remout, "", 1);

        data_stream.fd = remin;
		data_stream.nbread = 0;

        filename_copy = xstrdup(curfile);
        if (filename_copy == NULL) {
            fprintf(stderr, "Couldn't duplicate %s\n",
                    curfile);
            exit(1);
        }
        populate_md(filename_copy, md_values);

		if (showprogress)
            start_progress_meter(curfile, data_stream.size, &data_stream.nbread);

	hcerr = hc_nvr_create_from_string_arrays(&nvr,md_names,md_values,3);

        hcerr = hc_store_both_ez(the_session,
                                 data_source, &data_stream,
				 nvr,
                                 &system_record);

        xfree(filename_copy);
        filename_copy = NULL;

        if (hcerr != HCERR_OK) {
	  hc_session_get_status(the_session,&response_code, &hcerrstr);
	  run_err("Honeycomb error [%d] [%s]", response_code, hcerrstr);
	  exit(1);
        }

        if (showprogress)
            stop_progress_meter();

        run_log("%s",system_record.oid);

        /*
         * End of Honeycomb specific code
         */

        (void) response();
        //        (void) atomicio(vwrite, remout, "", 1);
    }
 screwup:
    run_err("protocol error: %s", why);
    exit(1);
}

static char*
md_get(char *key,
       char **names,
       char **values,
       int length)
{
    int index;

    for (index=0; index<length; index++) {
        if (!strcmp(key, names[index])) {
            break;
        }
    }
    if (index == length) {
        return(NULL);
    }
    return(values[index]);
}

static void
md_free(char **names,
        char **values,
        int length)
{
    int i;

    for (i=0; i<length; i++) {
        xfree(names[i]);
        xfree(values[i]);
    }
    xfree(names);
    xfree(values);
}

static long
data_writer(void *stream,
            char *buf,
            long n)
{
    data_stream_t *data_stream = (data_stream_t*)stream;
    int res;

    res = atomicio(vwrite, remout, buf, n);
    data_stream->nbread += res;
    
    return n;
}

static void
source(int argc,
       char *argv[])
{
    data_stream_t data_stream;
	size_t result;
	int haderr, indx;
	char *name, buf[2048];
	int len;
    char **md_names;
    char **md_values;
    int md_length;
    hcerr_t hcerr;
    hc_oid oid;
    int32_t response_code;
    char *hcerrstr;
    int i;
    char *ctime;
    hc_nvr_t *nvr;

	for (indx = 0; indx < argc; ++indx) {
		name = argv[indx];
        data_stream.nbread = 0;
		len = strlen(name);
		while (len > 1 && name[len-1] == '/')
			name[--len] = '\0';
		if (strchr(name, '\n') != NULL) {
			run_err("%s: skipping, filename contains a newline",
                    name);
			continue;
		}

        /*
         * Honeycomb retrieve of name
         */

        snprintf(oid, OID_HEX_CHRSTR_LENGTH, "%s", name);
        if (verbose_mode)
            fprintf(stderr, "Retrieving [%s]\n",
                    oid);

        /* Get the metadata */

        hcerr = hc_retrieve_metadata_to_strings_ez(the_session,
						   &oid,
						   &nvr);
        if (hcerr != HCERR_OK) {
	  hc_session_get_status(the_session,&response_code, &hcerrstr);
            run_err("Failed to retrieve metadata %d [%s]",
                    response_code, hcerrstr);
            exit(1);
        }
	res = hc_nvr_convert_to_string_arrays(nvr,&md_names,&md_values,&md_length);
        if (hcerr != HCERR_OK) {
            run_err("Failed to convert received metadata %d ",
                    response_code);
            exit(1);
        }




        if (verbose_mode) {
            for (int i=0; i<md_length; i++) {
                fprintf(stderr, "%s = %s\n",
                        md_names[i], md_values[i]);
            }
        }

        ctime = md_get("system.object_ctime", md_names, md_values, md_length);
        sscanf(md_get("system.object_size", md_names, md_values, md_length),
               "%lld", &data_stream.size);

		if (pflag) {
            
			(void) snprintf(buf, sizeof buf, "T%s 0 %s 0\n",
                            ctime, ctime);

			(void) atomicio(vwrite, remout, buf, strlen(buf));
			if (response() < 0)
				continue;
		}
        
		snprintf(buf, sizeof buf, "C%04o %lld %s\n",
                 (u_int) (S_IRUSR | S_IRGRP | S_IROTH),
                 data_stream.size, oid);

		if (verbose_mode) {
			fprintf(stderr, "Sending file modes: %s", buf);
		}
		(void) atomicio(vwrite, remout, buf, strlen(buf));
		if (response() < 0)
			continue;

		if (showprogress)
			start_progress_meter(oid, data_stream.size, &data_stream.nbread);

		hcerr = hc_retrieve_ez(the_session,
				       data_writer, &data_stream,
				       &oid);
		if (hcerr != HCERR_OK) {
		  hc_session_get_status(the_session,&response_code, &hcerrstr);
		  run_err("Failed to retrieve data %d [%s]",
			  response_code, hcerrstr);
		  exit(1);
		}

		if (showprogress)
			stop_progress_meter();

		if (!haderr)
			(void) atomicio(vwrite, remout, "", 1);
		else
			run_err("%s: %s", name, strerror(haderr));
		(void) response();
	}
}
    
int
main(int argc,
     char *argv[])
{
    int tflag = 0;
    int fflag = 0;
    char c;
    hcerr_t hcerr;

    iamrecursive = 0;

    while ((c = getopt(argc, argv, "tfrqd")) != EOF) {
        switch (c) {
        case 't':
            tflag = 1;
            break;

        case 'f':
            fflag = 1;
            break;

        case 'r':
            iamrecursive = 1;
            break;

        case 'q':
            showprogress = 0;
            break;

        case 'd':
            /* Target should be a directory . Ignored */
            break;
        }
    }

	argc -= optind;
	argv += optind;

    if ((!tflag) && (!fflag)) {
        fprintf(stderr, "One of the -t or the -f switch has to be specified\n");
        return(1);
    }
    if ((tflag) && (fflag)) {
        fprintf(stderr, "Either the -t or the -f flag can be specified\n");
        return(1);
    }

    hcerr = hc_init(xmalloc, xfree, xrealloc);
    if (hcerr != HCERR_OK) {
        fprintf(stderr, "Failed to initialize the honeycomb lib");
        return(1);
    }
    hcerr = hc_session_create_ez(HC_IP, HC_PORT,&the_session);
    if (hcerr != HCERR_OK) {
        hc_session_get_status(the_session,&response_code, &hcerrstr);
        fprintf(stderr, "Failed to initialize the honeycomb session %d [%s]",
		hcerr,hcerrstr);
        return(1);
    }
    

    if (fflag) {
		(void) response();
		source(argc, argv);
    }

    if (tflag) {
        sink();
    }

    hc_session_free(the_session);

    return(errs != 0);
}
