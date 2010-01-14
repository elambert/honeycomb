/*
 *****************************************************************
 *
 * Component =   Development tools
 *
 * Synopsis  =   "Super" source tree merge program
 *
 * The contents of this file are subject to the Sun Public
 * License Lite Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License.
 * A copy of the License is available at http://www.sun.com
 * 
 * The Original Code is ChorusOS ver. 5.1. The Initial Developer of the Original Code
 * is Sun Microsystems, Inc. Portions created by Sun Microsystems Inc.
 * are Copyright(C) 1998 - 2002 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * Parts of the source code are also subject to the GNU General Public License
 * (GPL) (located at http://www.gnu.org/licenses/gpl.html) the Mozilla Public
 * License (located at http://www.mozilla.org/MPL/MPL-1.1.html), the FreeBSD
 * license (located at http://www.freebsd.org/copyright/freebsd-license.html),
 * and the licenses contained in the "Read Me" file accompanying the
 * Original Code.
 * 
 * Contributor(s):
 * 
 *
 ****************************************************************
 *
 * #ident  "@(#)mkmerge.c 1.106     02/07/05 SMI"
 *
 ****************************************************************
 */

#include <sys/types.h>
#include <sys/stat.h>
#include <sys/utsname.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <strings.h>
#include <unistd.h>
#include <dirent.h>
#include <string.h>
#include <errno.h>
#include <ctype.h>

#ifdef _WIN32
#define chdir losechdir
#endif

#if !defined(_STDC_) && !defined(__STDC__)
#define const
#endif

#ifndef O_BINARY
#define O_BINARY 0
#endif

/* Version number */
#define VERSION 15

#define PRIO_SHIFT 8

/* file names */
#define F_DEFINES 1
char f_defines[] = "defines.lst";
char f_defines_tmp[] = "defines.tmp";
#define F_OPTIONS 2
char f_options[] = "options.lst";
char f_options_tmp[] = "options.tmp";
#define F_PROFILE 4
char f_profile[] = "profile";
char f_profile_tmp[] = "profile.tmp";
#define F_EXPORTS 8
char f_exports[] = "exports.lst";
char f_exports_tmp[] = "exports.tmp";

char f_merge[] = "merge.rf";

/* files and directories to ignore in the merge process */
char *f_ignore[] = 
{
    f_merge,
    "makefile",
    "makefile.mo",
    "makefile.ms",
    "config.makefile",
    "CVS",
    "RCS",
    "SCCS",
    0
};

/* extensions to ignore in the merge process */
char *f_ext_ignore[256];
char *f_ext_ignore_init[] =
{
    ".orig",
    ".old",
    ".rej",
    ".sum",
    "~",
    "#",
    0
};

/* for getopt */
extern int optind, opterr, optopt;
extern char *optarg;

/* '-f' forbidden after ... */
char fforbid;

/* save arguments */
char *cmd;

/* sessions */
struct session {
    struct session *next;
    struct session *parent;
    char *name;
};
struct session *sessions;
struct session *cursession;

/* defined flags */
#define MAX_FLAGS 256
struct flag {
    char *name;
    char *over;
    char *tag;
    struct session *session;
    int used;
    struct flag* next; /* used to chain flags in mg */
    int	off; /* used to signal that the flag has been redefined as off */
};
struct flag Flags[MAX_FLAGS];
char *flagcwd;
int nflag;

/* sources directories */
#define MAX_DIRS 256
char *dirs[MAX_DIRS];
int ndir;

/* destinations directories */
#define MAX_LEN 4096
char *dest, *exdir;
int destl;

/* include path */
char incpath[MAX_LEN];
char startdir[MAX_LEN];

/* stacks of directories */
#define MAX_CUR 65536
char srcstack[MAX_CUR];
char tgtstack[MAX_CUR];
char *cursrc, *curtgt;

/* tokens in merge.rf files */
#define MAX_TOKENS 32768
char *tokens[MAX_TOKENS];
int ntoken;

/* only merge in this directory */
char *window;
int lwindow;

/* mg files */
struct mem {
    struct mem *next;
    void *addr;
};
struct movefile {
    struct movefile *next;
    char *name;
    char *newname;
};
struct strlist {
    struct strlist *next;
    char *name;
    int val;
};
struct strlistlist {
    struct strlist *list;
    char *name;
    struct strlistlist *next;
};
struct command {
    char *command;
    char *dir;
    struct command *next;
};
struct command *commands;
enum ter { False, True, Undef };
enum ter valid[256];
int pvalid;
struct mg {
    int present;
    int exit;
    int prio;
    struct mg *next;
    struct mem *mem;
    char *name;
    int line;
    int hidden;
    struct movefile *movefile;
    struct movefile *forks;
    struct strlist *ignore;
    struct strlist *subdirs;
    struct flag	*local;
};
struct mg *chain;
struct strlistlist *lists;
struct strlist *partdir;
int prio;

/* allocs */
#define A_NULL		0
#define A_EXPORT	1
#define A_IMPORT	2
#define A_HIDE		4
#define A_GROUP		8
#define A_RETRY		128

struct alloc {
    unsigned int cksum;
    struct alloc *next;
    char *source;
    char *abssource;
    char *target;
    int prio;
    int flag;
};
struct alloc *alloc;
struct alloc **talloc;
int nalloc;

/* no symlink stuff */
#define HASHSIZE 65536
char hhints[HASHSIZE];
struct export {
    struct export *next;
    char *source;
    char *target;
    int fromsplit;
    int copylater;
};
struct export *exports;

/* verbose level */
#define V_ARGS		1
#define V_CONFLICTS	2
#define V_DIR		4
#define V_ECHO		8
#define V_GROUP		16
#define V_HIDE		32
#define V_LINK		64
#define V_MG		128
#define V_PARTIAL	256
#define V_STATS		512
#define V_UNUSED	1024
#define V_ZAP		2048
#define V_SOURCE	4096
#define V_END		8192
char verb_opt[] = "acdeghlmpsuzS";
int vflag = V_ECHO | V_CONFLICTS;
char verb_def[] = "ce";

/* copy flags */
#define C_SYMLINK	0
#define C_COPY		1
#define C_HARDLINK	2

/* global flags */
int f_append, f_cont, f_copy, f_export, f_show, f_relative, f_noslink, f_purge;
/* use .exe suffix on hosts that require it */
int f_extexe;

/* statistics */
int fcreated;
int nbrslink, nbrhlink, nbrcopy;
int nbrdir, nbrlok, nbrcreat, nbrleft, nbrconflicts;

/* hash table for groups */
struct gfile {
    char *name;
    int prio;
    struct gfile *next;
};
struct group {
    char *name;
    int used;
    struct gfile *files;
    struct group *next;
};
struct group *hg[HASHSIZE];

/* dico entry */
struct dict_entry {
    char *name;
    void (*fun)();
    int argmin;
    int argmax;
    int immediate;
    char *syntax;
    char *help;
};
extern struct dict_entry dict[];

/* function syntax and help */
extern char scomp[];
extern char sdefine[];
extern char secho[];
extern char selse[];
extern char sendif[];
extern char serror[];
extern char sexit[];
extern char sexport[];
extern char sexportas[];
extern char sexportexe[];
extern char sfork[];
extern char sgroup[];
extern char shide[];
extern char sif[];
extern char signore[];
extern char simport[];
extern char smove[];
extern char soption[];
extern char sprio[];
extern char srename[];
extern char slocal[];
extern char sset[];
extern char ssubdirs[];
extern char sthen[];
extern char hcomp[];
extern char hdefine[];
extern char hecho[];
extern char helse[];
extern char hendif[];
extern char herror[];
extern char hexit[];
extern char hexport[];
extern char hexportas[];
extern char hexportexe[];
extern char hfork[];
extern char hgroup[];
extern char hhide[];
extern char hif[];
extern char hignore[];
extern char himport[];
extern char hmove[];
extern char hoption[];
extern char hprio[];
extern char hrename[];
extern char hlocal[];
extern char hset[];
extern char hsubdirs[];
extern char hthen[];

/* forward declarations */
extern char *base();
extern char *cmdcmp();
extern char *plural();
extern char *abstorel();
extern int allowdir();
extern int bazname();
extern int compar_src();
extern int compar_tgt();
extern int docopy();
extern int dolink();
extern int expandfile();
extern int flagcmp();
extern int isvalid();
extern int newname();
extern int tokcmp();
extern int verifylink();
extern unsigned int hash();
extern void *mallocmg();
extern void absdirs();
extern void acomp();
extern void addalloc();
extern void addexport();
extern void adefine();
extern void aecho();
extern void aelse();
extern void aendif();
extern void aerror();
extern void aexit();
extern void aexport();
extern void aexportas();
extern void aexportexe();
extern void afork();
extern void agroup();
extern void ahide();
extern void aif();
extern void aignore();
extern void aimport();
extern void allocrel();
extern void amove();
extern void analysemg();
extern void analysetokens();
extern void aoption();
extern void aprio();
extern void arename();
extern void alocal();
extern void aset();
extern void asubdirs();
extern void athen();
extern void closemg();
extern void combine();
extern void conflicts();
extern void domerge();
extern void dumpexports();
extern void enterdir();
extern void expandgroups();
extern void leavedir();
extern void mkdirp();
extern void prune();
extern void purge();
extern void tokenize();
extern void update();
extern void usage();
extern void walk();

/* Display usage informations */

void usage()
{
    printf ("Usage:\n");
    printf ("\t%s [options] [profile_files]\n", cmd);
    printf ("Options:\n");
    printf ("\t-a\t\t\tleave true files untouched\n");
    printf ("\t-A\t\t\tuse absolute symbolic links\n");
    printf ("\t-c\t\t\tmerge with copies\n");
    printf ("\t-[d|D] flag[,flag]*\tdefine flags\n");
    printf ("\t-E\t\t\tonly perform export phase\n");
    printf ("\t-f file\t\t\tread profile file\n");
    printf ("\t-h\t\t\tshow this help\n");
    printf ("\t-H\t\t\tdescribe the mkmerge language\n");
    printf ("\t-k\t\t\tcontinue after conflict (default)\n");
    printf ("\t-K\t\t\tstop after conflict\n");
    printf ("\t-l\t\t\tmerge with hard links (default on win32)\n");
    printf ("\t-L\t\t\tmerge with symbolic links (default on unix)\n");
    printf ("\t-n\t\t\tdont link anything\n");
    printf ("\t-p dir\t\t\tspecify a partial source directory\n");
    printf ("\t-q[%s]\treset verbose flags\n", verb_opt);
    printf ("\t-r\t\t\talways use relative symbolic links (default)\n");
    printf ("\t-s dir[,dir]*\t\tspecify source directories\n");
    printf ("\t-t dir\t\t\tspecify the target directory\n");
    printf ("\t-u\t\t\tupdate the current directory\n");
    printf ("\t-U\t\t\tupdate the current merged tree\n");
    printf ("\t-v[%s]\tset verbose flags (default: %s)\n",
	    verb_opt, verb_def);
    printf ("\t\ta : display arguments");
    printf ("\t\tc : display conflicts\n");
    printf ("\t\td : display directories created");
    printf ("\te : execute echo commands\n");
    printf ("\t\tg : display groups");
    printf ("\t\th : display hidden files\n");
    printf ("\t\tl : display links created");
    printf ("\tm : display merge.rf status\n");
    printf ("\t\tp : display low priority files");
    printf ("\ts : display stats\n");
    printf ("\t\tu : display unused flags\n");
    printf ("\t-z\t\t\texport by symbolic links instead of copies (default on unix)\n");
    printf ("\t-Z\t\t\texport by copies instead of symbolic links (default on win32)\n");
}


/* Display a short man page */

void man()
{
    int i, l;
    printf ("\n");
    printf ("A merge.rf file is composed of lines that are evaluated ");
    printf ("one line at a time.\n\n");
    printf ("The status of the merge.rf file can have 3 values : \n\t");
    printf ("UNDEFINED (at the beginning of the file), ");
    printf ("TRUE or FALSE.\n\n");
    printf ("This status evolves as the file is analysed.\n\n");
    printf ("If a file ends with a status that is either UNDEFINED or TRUE, ");
    printf ("then its contents is recursively merged. ");
    printf ("Otherwise it is pruned.\n\n");
    printf ("Instruction flaged with a '*' are evaluated only if ");
    printf ("the status of the current   file is not FALSE.\n\n");
    for (i = 0; dict[i].name; ++i) {
	if (dict[i].name[0] < 'a') continue;
	printf ("%s %s", dict[i].name, dict[i].syntax);
	l = strlen(dict[i].name) + strlen(dict[i].syntax);
	while (l++ < 25) printf (" ");
	printf ("%c ", dict[i].immediate ? ' ' : '*');
	printf ("%s\n", dict[i].help);
    }
}

/* very simple hash function */

unsigned int hash(p)
    char *p;
{
    unsigned int s;

    s = 0;
    while (*p) {
	s = (5*s) ^ *p;
	p++;
    }
    s %= HASHSIZE;
    if (!s) s = 1;
    return s;
}

/*
 * directory manipulation routines
 */

/* Enter a directory */

void enterdir(s,t)
    char *s, *t;
{
    int l;

    l = strlen(cursrc);
    cursrc[l] = '/';
    strcpy(&cursrc[l+1], s);
    l = strlen(curtgt);
    strcpy(&curtgt[l+1], curtgt);
    curtgt = &curtgt[l+1];
    curtgt[l] = '/';
    strcpy(&curtgt[l+1], t);
    strcpy(flagcwd, t);
}

/* Leave a directory */

void leavedir()
{
    int j;

    j = bazname(cursrc) - 1;
    cursrc[j] = 0;
    curtgt -= 2;
    while (*curtgt) curtgt--;
    curtgt++;
}

/* 
 * Yes I know, it is a hack. 
 * But I'm too lazy to find a better way to do this
 * with the stupid C compiler on SunOS.
 */
#define panic for(printf("%s: ",cmd);;printf("\n"),exit(1)) printf

/* secure fopen */

FILE* fopen2(char *name, char *mode)
{
    FILE *f;
    f = fopen(name, mode);
    if (f == 0) panic ("can not open file '%s' with mode '%s'. errno = %d",
                       name, mode, errno);
    return f;
}

/* String copy routine */

char *copy(s)
    char *s;
{
    char *p;

    if (!s) return s;
    p = (char*)malloc(1+strlen(s));
    strcpy (p, s);
    return p;
}


/* Return "var" in "var=value" */

char *base(s)
    char *s;
{
    char *p, *q;

    p = copy(s);
    q = strchr(p, '=');
    if (q) *q = 0;
    return p;
}


/* Portable basename */

int bazname(s)
    char *s;
{
    int i, j;

    i = j = 0;
    while (s[i]) {
	if (s[i] == '/') j = i+1;
	i++;
    }
    return j;
}


/* Portable mkdirp */

void mkdirp(path)
     char *path;
{
    int i, rc;
    struct stat statb;
    char c;

    if (path == 0 || *path == 0) return;
    i = 0;
    do {
	i++;
	if (path[i] == '/' || path[i] == 0) {
	    c = path[i];
	    path[i] = 0;
	    rc = mkdir (path, 0777);
            if (rc == 0) {
                nbrdir++;
            } else {
                rc = stat(path, &statb);
                if (!rc && !S_ISDIR((unsigned int)statb.st_mode)) {
                        /* found something to remove */
                    unlink(path);
                    rc = mkdir (path, 0777);
                    if (rc == 0) nbrdir++;
                }
            }
	    path[i] = c;
	}
    } while (path[i]);
    if (rc && errno != EEXIST) {
	panic("mkdir '%s' failed with errno = %d", path, errno);
    }
}

/* Turn all user provided directories into absolute path names */

void absdirs()
{
    char cwd[MAX_LEN];
    char wd[MAX_LEN];
    struct strlist *p;

    if (!getcwd(cwd, MAX_LEN)) panic("error in getcwd");

    if (!f_show) {
	mkdirp (exdir);
	if (chdir(exdir)) panic("chdir to '%s' failed", exdir);
    }
    if (!getcwd(wd, MAX_LEN)) panic("error in getcwd");
    exdir = copy(wd);
    chdir (cwd);

    for (p = partdir; p; p = p->next) {
	if (chdir(p->name)) panic("chdir to '%s' failed", p->name);
	if (!getcwd(wd, MAX_LEN)) panic("error in getcwd");
	p->name = copy(wd);
	chdir (cwd);
    }

    if (!f_show) {
	mkdirp (dest);
	if (chdir(dest)) panic("chdir to '%s' failed", dest);
    }
    if (!getcwd(wd, MAX_LEN)) panic("error in getcwd");
    dest = copy(wd);
    destl = strlen(dest);
    chdir (dest);
}


/* Handle the '-d' option */

void optd(s)
    char *s;
{
    int d, i, j, l, sn;

    i = 0;
    sn = nflag;
    Flags[nflag].name = 0;
    do {
	d = s[i];
	if (d != ',' && d >= ' ' && Flags[nflag].name == 0) {
	    Flags[nflag].name = &s[i];
	}
	if (d == ',' || d < ' ') {
	    s[i] = 0;
	    if (Flags[nflag].name) {
		Flags[nflag].name = copy(Flags[nflag].name);
                Flags[nflag].session = cursession;
                if (s[i-1] == ')') {
                    char *p;
                    p = Flags[nflag].name;
                    while (*p && *p != '(') p++;
                    if (*p) {
                        *p = 0;
                        Flags[nflag].tag = ++p;
                        while (*p && *p != ')') p++;
                        *p = 0;
                    }
                }
                ++nflag;
		Flags[nflag].name = 0;
	    }
	}
	i++;
    } while (d);
    for (i = sn; i < nflag; ++i) {
	l = strlen(Flags[i].name);
	for (j = 0; j < i; ++j) {
	    if (Flags[j].name) {
		char *u, *v;
		u = base(Flags[j].name);
		v = base(Flags[i].name);
		if (!strcmp(Flags[i].name,Flags[j].name) ||
		    (!strcmp(u, v) && strcmp(u, "tree"))) {
		    Flags[j].name = 0;
		}
		free(v);
		free(u);
	    }
	}
        if (Flags[i].name && !strcmp(&Flags[i].name[l-4],"=off")) {
            Flags[i].name = 0;
        }
        if (Flags[i].name && !strcmp(&Flags[i].name[l-3],"=no")) {
            Flags[i].name = 0;
        }
        if (Flags[i].name && !strcmp(&Flags[i].name[l-4],"=yes")) {
            Flags[i].name[l-4] = 0;
        }
        if (Flags[i].name && !strcmp(&Flags[i].name[l-3],"=on")) {
            Flags[i].name[l-3] = 0;
        }
    }
    for (i = j = 0; i < nflag; ++i) {
	if (Flags[i].name) Flags[j++] = Flags[i];
    }
    nflag = j;
}


/* Handle the '-t' option */

void optt(s)
    char *s;
{
    dest = copy(s);
}


/* Handle the '-p' option */

void optp(s)
    char *s;
{
    struct strlist *p;

    p = (struct strlist *)malloc(sizeof(*p));
    p->next = partdir;
    p->name = copy(s);
    partdir = p;
}


/* Handle the '-s' option */

void opts(s)
    char *s;
{
    char cwd[MAX_LEN], wd[MAX_LEN], nwd[MAX_LEN];
    int i, k, d;
    char *dir;

    if (!getcwd(cwd, MAX_LEN)) panic("error in getcwd");
    dir = 0;
    i = 0;
    do {
	d = s[i];
	if (d != ',' && d >= ' ' && dir == 0) {
	    dir = &s[i];
	}
	if (d == ',' || d < ' ') {
	    s[i] = 0;
	    if (dir) {
		if (dir[0] == '.' && dir[1] == '/') {
		    chdir(incpath);
		} else {
		    chdir(startdir);
		}
		if (chdir(dir)) panic("chdir to '%s' failed", dir);
		getcwd(wd, MAX_LEN);
		for (k = 0; k < ndir; ++k) {
		    if (!strcmp(wd, dirs[k])) break;
		}
		if (k == ndir) {
		    chdir (cwd);
		    dirs[ndir++] = copy(wd);
		    strcpy (nwd, "tree=");
		    k = bazname(wd);
		    strcat (nwd, &wd[k]);
		    optd (nwd);
		}
	    }
	    dir = 0;
	}
	i++;
    } while (d);
    chdir(cwd);
}

/* tag definition */

void tag(s)
    char *s;
{
    cursession->name = copy(s);
}

/* Handle flag definition found in a profile file */

void pit(s)
    char *s;
{
    int i,j;
    for (i = 0; s[i]; ++i) {
	if (s[i] < '-' || s[i] == '=') break;
    }
    if (s[i] == '=') {
	for (j = i+1; s[j]; ++j) {
	    if (s[j] < '-') break;
	}
    } else {
	j = i;
    }
    for (i = j; s[i] && s[i] != '#'; i++) {
        if (s[i] > ' ') panic("syntax error in '%s'\n", s);
    }
    s[j] = 0;
    optd(s);
}


/* Handle the '-f' option */

void optf(s)
    char *s;
{
    FILE *f;
    char buf[MAX_LEN];
    char *p, *arg;
    char *incpathsaved;
    void (*fun)();
    struct strlist *ql;
    struct strlistlist *ll;
    int follow;
    struct session *sessionsaved;

    sessionsaved = cursession;
    cursession = (struct session *) malloc(sizeof(*cursession));
    cursession->next = sessions;
    cursession->parent = sessionsaved;
    cursession->name = 0;
    sessions = cursession;
    follow = (s[0] == '.' && s[1] == '/');
    incpathsaved = copy(incpath);
    if (!follow) strcpy(incpath, startdir);
    combine(incpath, s);
    s = incpath;
    f = fopen2(s, "r");
    incpath[bazname(incpath)-1] = 0;

    ll = 0;
    while (fgets(buf, MAX_LEN, f)) {
	if (buf[0] == '#' && ll == 0) continue;
	if (buf[0] == '[') {
	    for (p = buf+1; *p && *p != ']';) {
		++p;
	    }
	    *p = 0;
	    if (!strcmp(buf+1, f_profile)) {
		ll = 0;
		continue;
	    }
	    ll = lists;
	    while (ll) {
		if (!strcmp(&buf[1], ll->name)) break;
		ll = ll->next;
	    }
	    if (ll == 0) {
		ll = (struct strlistlist *) malloc(sizeof(*ll));
		ll->next = lists;
		ll->name = copy(&buf[1]);
		ll->list = 0;
		lists = ll;
	    }
	    continue;
	}
	if (ll) {
	    ql = (struct strlist *)malloc(sizeof(struct strlist));
	    ql->next = ll->list;
	    ql->name = copy(buf);
	    ll->list = ql;
	    continue;
	}
	fun = pit;
	arg = buf;
	if ((p = cmdcmp("MERGE_DIR", buf))) { fun = optt; arg = p; }
	if ((p = cmdcmp("FLAG", buf))) { fun = optd; arg = p; }
	if ((p = cmdcmp("TREE", buf))) { fun = opts; arg = p; }
	if ((p = cmdcmp("INCLUDE", buf))) { fun = optf; arg = p; }
	if ((p = cmdcmp("TAG", buf))) { fun = tag; arg = p; }
	(*fun)(arg); 
    }
    fclose(f);
    strcpy(incpath, incpathsaved);
    free(incpathsaved);
    cursession = sessionsaved;
}


/* Clean the universe */

void init()
{
    int i;

    f_show = f_export = f_append = 0;
    f_cont = 1;
    f_relative = 1;
    f_purge = 0;
#ifdef _WIN32    
    f_extexe = 1;
    f_copy = C_HARDLINK;
    f_noslink = 1;
#else
    f_extexe = 0;
    f_copy = C_SYMLINK;
    f_noslink = 0;
#endif
    ndir = 0;
    dest = 0;
    partdir = 0;
    chain = 0;
    alloc = 0;
    nalloc = 0;
    prio = 0;
    fcreated = 0;
    lists = 0;
    nbrslink = nbrhlink = nbrcopy = 0;
    nbrdir = nbrcreat = nbrlok = nbrconflicts = 0;
    dest = exdir = 0;
    commands = 0;
    exports = 0;
    fforbid = 0;
    for (i = 0; i < HASHSIZE; ++i) hg[i] = 0;
    for (i = 0; i < HASHSIZE; ++i) hhints[i] = 0;
    for (i = 0; i < MAX_FLAGS; ++i) {
        Flags[i].name = Flags[i].over = 0;
        Flags[i].used = 0;
        Flags[i].session = 0;
    }
    flagcwd = (char *)malloc(MAX_LEN);
    strcpy(flagcwd, "cwd=");
    Flags[0].name = flagcwd;
    while (*flagcwd) ++flagcwd;
    nflag = 1;
    window = 0;
    cursession = sessions = 0;
}


/*
 * Sort flags in the profile file.
 * host=... should come first, then target=... and devsys=...
 */

int deford(s)
    char *s;
{
    if (!strncmp(s, "host=", 5)) return -5;
    if (!strncmp(s, "target=", 7)) return -4;
    if (!strncmp(s, "devsys=", 7)) return -3;
    if (!strncmp(s, "target", 6)) return -2;
    if (!strncmp(s, "devsys", 6)) return -1;
    return 0;
}

int compar_flag(a, b)
    const void *a, *b;
{
    struct flag *p, *q;
    int ap, aq;

    p = (struct flag *) a;
    q = (struct flag *) b;
    ap = deford(p->name);
    aq = deford(q->name);
    if (ap != aq) {
	return ap-aq;
    } else {
	return strcmp(p->name, q->name);
    }
}

/* Move a file if modified */

int moveifmod(f1, f2)
    char *f1, *f2;
{
    FILE *f, *g;
    int fc, gc, cpy;
    
    f = fopen(f1, "r");
    g = fopen(f2, "r");
    cpy = (g == 0);
    
    while (f && g) {
	fc = getc(f);
	gc = getc(g);
	if (fc != gc) {
	    cpy = 1;
	    break;
	}
	if (fc < 0) {
	    cpy = 0;
	    break;
	}
    }
    if (f) fclose(f);
    if (g) fclose(g);
    if (cpy) {
	rename(f1, f2);
    } else {
	unlink(f1);
    }
    return cpy;
}


/* Write the "profile" file */

void dumprofile()
{
    if (!f_show && !partdir) {
        int i;
        char *v, *tag;
	FILE *f;
        struct session *sp;

	fcreated |= F_PROFILE;
	f = fopen2(f_profile_tmp, "w");
	fprintf (f, "[profile]\n");
	fprintf (f, "MERGE_DIR=%s\n", dest);
	if (strcmp(dest, exdir)) {
	    fprintf (f, "EXPORT_DIR=%s\n", exdir);
	}
	for (i = 0; i < ndir; ++i) {
	    fprintf (f, "TREE=%s\n", dirs[i]);
	}
	qsort(Flags, nflag, sizeof(struct flag), compar_flag);
	for (i = 0; i < nflag; ++i) {
            if (!Flags[i].used) continue;
	    if (!strncmp(Flags[i].name, "version=", 8)) continue;
	    if (!strncmp(Flags[i].name, "cwd=", 4)) continue;
	    v = "";
	    if (!strchr(Flags[i].name, '=')) v = "=on";
            sp = Flags[i].session;
            tag = Flags[i].tag;
            if (!tag) {
                while (sp && !sp->name) sp = sp->parent;
                if (sp && sp->name && sp->name[0]) {
                    tag = sp->name;
                }
            }
            if (tag) {
                fprintf (f, "FLAG=%s%s(%s)\n", Flags[i].name, v, tag);
            } else {
                fprintf (f, "FLAG=%s%s\n", Flags[i].name, v);
            }
	}
	fclose (f);
    }
}


/* Function to dump a string list */

void dumplist(f1, f2, p)
    FILE *f1, *f2;
    struct strlist *p;
{
    if (p) {
	dumplist(f1, f2, p->next);
	if (f1) fputs (p->name, f1);
	fputs (p->name, f2);
    }
}


/* Set the mkmerge version */

void findversion()
{
    char buf[80];

    sprintf (buf, "version=%d", VERSION);
    optd(buf);
}


/* Try to find the host type */

void findhost()
{
    char *host;
    struct utsname u;

    uname (&u);
    host = 0;
    if (!strcmp(u.sysname, "SunOS")) {
	if (u.release[0] == '5') {
            if (u.machine[0] == 'i') {
                host="host=solx86";
            } else {
                host="host=solaris";
            }
	} else {
	   host="host=sunos";
	}
    }
    if (!strcmp(u.sysname, "Linux")) host="host=linux";
    if (!strcmp(u.sysname, "HP-UX")) host="host=hpux";
    if (!strcmp(u.sysname, "AIX")) host="host=AIX";
    if (!strcmp(u.sysname, "SCO_SV")) host="host=sco";
    if (!strncmp(u.sysname, "CYGWIN32", 8)) host="host=win32";
    if (!strncmp(u.sysname, "CYGWIN_NT", 8)) host="host=win32";
    if (!host && !strcmp(u.release,"4.0")) host="host=svr4";
    if (!host && !strcmp(u.release,"4.2MP")) host="host=unixware";
    if (host) optd(copy(host));
}


/* The game begins here */

int main(argc, argv)
     int argc;
     char **argv;
{
    int c, i, j, k, l1, l2, nooptf;
    struct strlist *p, *q;
    char *s;
    cmd = argv[0];
    nooptf = 0;
    
    if (argc < 2) {
	usage();
	exit(0);
    }

    init();
    
    findversion();
    findhost();
    getcwd(startdir, MAX_LEN);
    strcpy(incpath, startdir);
    while ((c = getopt(argc, argv, "-f:D:d:v:q:s:t:p:ArclLnKkhHEauUxXzZP")) != EOF) {
	switch (c) {
	case 'f':
	case 1:
	    if (fforbid) {
		panic("'-f' not allowed after '-%c'", fforbid);
	    } 
	    optf(optarg);
	    break;
	case 'D':
	case 'd':
	    optd(optarg);
	    fforbid = c;
	    break;
	case 'q':
	case 'v':
	    if (c == 'q') vflag = ~vflag;
	    for (i = 0; optarg[i]; ++i) {
		char *vf = strchr(verb_opt, optarg[i]);
		if (vf) {
		    vflag |= (1 << (vf-verb_opt));
		} else {
		    vflag = -1;
		}
	    }
	    if (c == 'q') vflag = ~vflag;
	    break;
	case 'p':
	    optp(optarg);
	    break;
	case 'A':
	    f_relative = 0;
	    break;
	case 'r':
	    f_relative = 1;
	    break;
	case 't':
	    optt(optarg);
	    fforbid = c;
	    break;
	case 'E':
	    f_export = 1;
	    break;
	case 'n':
	    f_show = 1;
	    break;
	case 'K':
	    f_cont = 0;
	    break;
	case 'k':
	    f_cont = 1;
	    break;
	case 's':
	    opts(optarg);
	    fforbid = c;
	    break;
	case 'a':
	    f_append = 1;
	    break;
	case 'c':
	    f_copy = C_COPY;
	    break;
	case 'l':
	    f_copy = C_HARDLINK;
	    break;
	case 'L':
	    f_copy = C_SYMLINK;
	    break;
	case 'H':
	    man();
	    exit(0);
	case 'u':
	case 'U':
	    nooptf = 1;
	    update(c == 'u');
	    break;
	case 'P':
	    f_purge = 1;
	    break;
	case 'X':
	    f_extexe = 1;
	    break;
	case 'x':
	    f_extexe = 0;
	    break;
	case 'Z':
	    f_noslink = 1;
	    break;
	case 'z':
	    f_noslink = 0;
	    break;
	default:
	    usage();
	    exit(0);
	}
    }

    if (optind != argc) {
	fprintf(stderr, "do not understand :");
	for (i = optind; i < argc; ++i) {
	    fprintf(stderr, " %s", argv[i]);
	}
	fprintf(stderr, "\n");
	usage();
	exit(1);
    }

    if (f_show && !dest) dest = "...";
    if (!dest) panic("no destination directory");
    if (!exdir) exdir = dest;

    if (ndir == 0) panic("no tree to merge");

    absdirs();

    if (partdir) {
	q = partdir;
	partdir = 0;
	while (q) {
	    l1 = strlen(q->name);
	    for (p = partdir; p; p = p->next) {
		l2 = strlen(p->name);
		if (!strncmp(p->name, q->name, l2)) {
		    if (l1 == l2 || q->name[l2] == '/') break;
		}
	    }
	    if (p == 0) optp(q->name);
	    q = q->next;
	}
    }

    for (i = 0; f_ext_ignore_init[i]; ++i) {
	f_ext_ignore[i] = f_ext_ignore_init[i];
    }
    if ((s = getenv("MKMERGE_IGNORE"))) {
	j = 0;
	while(1) {
	    k = j;
	    while (s[k] && s[k] != ':') k++;
	    c = s[k];
	    if (j != k) {
		s[k] = 0;
		f_ext_ignore[i++] = copy(&s[j]);
		s[k] = c;
	    }
	    if (c == 0) break;
	    j = k+1;
	}
    }
    f_ext_ignore[i] = 0;
#ifdef DEBUG
    for (i = 0; f_ext_ignore[i]; ++i) {
	printf ("ignoring %s\n", f_ext_ignore[i]);
    }
#endif
    
    if (((vflag & (V_SOURCE|V_END)) == V_SOURCE)) {
	for (i = 0; i < ndir; ++i) {
	    printf ("%s\n", dirs[i]);
	}
        exit(0);
    }
    if (vflag & V_ARGS) {
	printf ("merge directory : '%s'\n", dest);
	printf ("source director%s :\n", plural(ndir,"y","ies"));
	for (i = 0; i < ndir; ++i) {
	    printf ("\t'%s'\n", dirs[i]);
	}
	for (i = 0; i < nflag; ++i) {
	    if (i == 0) {
		printf ("flag%s : ",plural(nflag,"","s"));
	    } else {
		printf (",");
	    }
	    printf ("%s", Flags[i].name);
	}
	if (nflag) printf ("\n");
    }

    if (vflag & (V_ARGS | V_PARTIAL)) {
	if (partdir) {
	    printf ("partial source director%s :\n", 
		    plural(partdir->next != NULL,"y","ies"));
	    for (p = partdir; p; p = p->next) {
		printf ("\t'%s'\n", p->name);
	    }
	}
    }

    if (!f_show) {
        unlink(f_defines_tmp);
        unlink(f_options_tmp);
        unlink(f_profile_tmp);
        unlink(f_exports_tmp);
    }

    srcstack[0] = tgtstack[0] = 0;
    for (i = 0; i < ndir; ++i) {
	cursrc = srcstack+1;
	curtgt = tgtstack+1;
	strcpy (cursrc, dirs[i]);
	strcpy (curtgt, dest);
	walk ();
    }

    expandgroups();

    if (vflag & V_UNUSED) {
	for (i = 0; i < nflag; ++i) {
	    if (Flags[i].used == 0 && strncmp(Flags[i].name,"tree=", 5)) {
		printf ("Warning : useless flag '%s'\n", Flags[i].name);
	    }
	}
	for (i = 0; i < HASHSIZE; ++i) {
	    struct group *g;
	    g = hg[i];
	    while (g) {
		if (!g->used) {
		    printf ("Warning : unused group '%s'\n", g->name);
		}
		g = g->next;
	    }
	}
    }

    conflicts();
    if (f_purge) purge();
    domerge();

    for (i = 0; i < nflag; ++i) {
	if (Flags[i].over) Flags[i].name = Flags[i].over;
    }

    dumpexports();
    dumprofile();

    moveifmod(f_defines_tmp, f_defines);
    moveifmod(f_options_tmp, f_options);
    moveifmod(f_profile_tmp, f_profile);
    moveifmod(f_exports_tmp, f_exports);

    if (!partdir && !f_show) printf("merged tree installed in %s\n", dest);
    exit (0);
}


/* Should we ignore "path" */

int ignorefile(path)
    char *path;
{
    int i, l, m;

    l = strlen(path);
    for (i = 0; f_ext_ignore[i]; ++i) {
	m = strlen(f_ext_ignore[i]);
	if (!strncmp(&path[l-m], f_ext_ignore[i], m)) return 1;
    }
    for (i = 0; f_ignore[i]; ++i) {
	if (!strcmp(path, f_ignore[i])) return 1;
    }
    return 0;
}

/*
 * During a partial merge, ignore directories that are neither above nor
 * below directories given with the '-p' option
 */

int matchpat()
{
    int a, b, c;
    struct strlist *p;

    for (p = partdir; p; p = p->next) {
	a = strlen(cursrc);
	b = strlen(p->name);
	if (b < a) a = b;
	c = strncmp(cursrc, p->name, a);
	if (!c) break;
    }
    return (p == 0);
}

/* Explore the source tree and build the list of links to make */

void walk()
{
    DIR *dir;
    struct dirent *direntp;
    struct stat statb;
    char buf[MAX_LEN], tgt[MAX_LEN];
    struct mg mg;
    int saveprio, broken;

    if (partdir && matchpat()) return;

    mg.next = chain;
    if (chain) {
	mg.hidden = chain->hidden;
	mg.local = chain->local;
    } else {
	mg.hidden = 0;
	mg.local = 0;
    }
    chain = &mg;
    analysemg(&mg);
    if (isvalid()) {
	saveprio = prio;
	prio++;
	if (mg.present) {
	    prio += mg.prio;
	}

	dir = opendir(cursrc);
	if (dir) {
	    broken = 1;
	    while ((direntp = readdir(dir))) {
		broken = 1;
		if (!direntp->d_name[0]) break;
		broken = 0;
		if (!isalnum(direntp->d_name[0])
		    &&  (direntp->d_name[0] != '_')
		    && ((direntp->d_name[0] != '.')
			|| (direntp->d_name[strlen(direntp->d_name)-1] == '.')))
		  continue;
		if (ignorefile(direntp->d_name)) continue;
		strcpy (buf, cursrc);
		combine (buf, direntp->d_name);
		if (lstat(buf, &statb)) {
		    if (errno == ENOENT) {
		      printf("errno == ENOENT %s\n", buf);
			broken = 1;
			break;
		    }
		    continue;
		}
		if (S_ISDIR((unsigned int)statb.st_mode)) {
		    if (!allowdir(direntp->d_name)) {
                        struct movefile *p;
			enterdir(direntp->d_name, direntp->d_name);
			if (strcmp(buf, cursrc)) panic("buf != cursrc");
			walk();
			leavedir();
                        p = chain->forks;
                        while (p) {
                            if (!strcmp(p->name, direntp->d_name)) {
                                enterdir(direntp->d_name, p->newname);
                                walk();
                                leavedir();
                            }
                            p = p->next;
                        }
		    }
		} else if (!f_export && !newname(direntp->d_name, tgt)) {
                    addalloc(buf, tgt, mg.hidden, prio);
		}
	    }
	    if (broken) {
		printf("PANIC : Either your compiler or your filesystem is badly broken...\n");
		printf("Remove /usr/ucb from your PATH and recompile %s\n", cmd);
		printf("If it does not fix the problem, contact your system administrator...\n");
		panic("Internal error");
	    }
	    closedir (dir);
	}
	
	prio = saveprio;
    }
    
    chain = mg.next;
    closemg (&mg);
}

/*
 * Verify if a subdir can be entered
 */

int allowdir(dname)
    char *dname;
{
    struct strlist *p;

    p = chain->ignore;
    while (p) {
	if (!strcmp(p->name, dname)) return 1;
	p = p->next;
    }
    p = chain->subdirs;
    if (!p) return 0;
    while (p) {
	if (!strcmp(p->name, dname)) return 0;
	p = p->next;
    }
    return 1;
}

/*
 * add a file name to a group. Create the group if necessary
 */

void add2group(gname, fname)
    char *gname, *fname;
{
    struct group *g;
    struct gfile *f;
    unsigned int s;

    f = (struct gfile *) malloc (sizeof(struct gfile));
    f->name = copy(fname);
    f->prio = prio;
    s = hash(gname);
    g = hg[s];
    while (g) {
	if (!strcmp(g->name, gname)) {
	    f->next = g->files;
	    g->files = f;
	    return;
	}
	g = g->next;
    }
    g = (struct group *) malloc (sizeof(struct group));
    g->name = copy(gname);
    g->next = hg[s];
    g->files = f;
    g->used = 0;
    f->next = 0;
    hg[s] = g;
}


/* Add a file to the list of things to merge.. */

void addalloc(source, target, flag, prio)
    char *source, *target;
    int flag, prio;
{
    struct alloc *p;
    int i;
    unsigned int h;

    if (window) {
	if (strncmp(target, window, lwindow)) return;
	for (i = lwindow+1; target[i]; ++i) {
	    if (target[i] == '/') return;
	}
    }
    p = (struct alloc *) malloc (sizeof(struct alloc));
    p->next = alloc;
    alloc = p;
    p->source = copy(source);
    p->abssource = p->source;
    p->target = copy(target);
    p->prio = prio;
    p->flag = flag;
    nalloc++;
    h = hash(target);
    hhints[h] = 1;
    p->cksum = h;
}


/*
 * Read and analyze a merge.rf
 */

void analysemg(mg)
     struct mg *mg;
{
    FILE *f;
    char buf[MAX_LEN];

    mg->present = mg->exit = 0;
    pvalid = 0;
    valid[0] = Undef;
    mg->mem = 0;
    mg->movefile = 0;
    mg->ignore = 0;
    mg->subdirs = 0;
    mg->forks = 0;
    mg->prio = 0;

    strcpy (buf, cursrc);
    combine (buf, f_merge);
    mg->name = copy(buf);
    mg->line = 0;

    f = fopen(buf, "r");
    if (f != 0) {
	mg->present = 1;
	while (!mg->exit && fgets(buf, MAX_LEN, f)) {
	    mg->line++;
	    tokenize(buf);
	    analysetokens(mg);
	}
	fclose (f);
    }
    if ((vflag & V_MG) && mg->present) {
	printf ("%s : ", mg->name);
	if (isvalid()) printf ("merged");
	else printf ("pruned");
	printf ("\n");
    }
}


/*
 * Split a line into tokens
 */

void tokenize(s)
     char *s;
{
    int i, d, f, q;

    ntoken = 0;
    tokens[0] = 0;
    i = q = 0;
    do {
	d = s[i];
	f = (!q) && (d <= ' ' || d == ',' || d == ';' || d == ':');
	if (d == '"') {
	    q = 1-q;
	    f = 1;
	}
	if (!f && tokens[ntoken] == 0) {
	    tokens[ntoken] = &s[i];
	}
	if (f) {
	    s[i] = 0;
	    if (tokens[ntoken]) {
		tokens[++ntoken] = 0;
	    }
	}
	i++;
    } while (d);
    if (ntoken && tokens[0][0] == '#') ntoken = 0;
}

/*
 * Check if the current line in merge.rf should be executed
 */
int isvalid()
{
    int i;

    for (i = 0; i <= pvalid; ++i) {
	if (valid[i] == False) return 0;
    }
    return 1;
}

/* 
 * The dictionnary
 */

struct dict_entry dict[] = 
{
    {"define",	adefine,2, 0, 0, sdefine, hdefine},
    {"echo",	aecho,	2, 0, 0, secho, hecho},
    {"else",	aelse,	1, 1, 1, selse,	helse},
    {"endif",	aendif,	1, 1, 1, sendif, hendif},
    {"error",	aerror,	2, 0, 0, serror, herror},
    {"exit",	aexit,	1, 1, 0, sexit,	hexit},
    {"export",	aexport,2, 0, 0, sexport, hexport},
    {"exportas",aexportas,	2, 3, 0, sexportas, hexportas},
    {"exportexe",aexportexe,	2, 0, 0, sexportexe, hexportexe},
    {"Fork",	afork,	2, 3, 0, sfork, hfork},
    {"Group",	agroup,	2, 0, 0, sgroup, hgroup},
    {"Hide",	ahide,	1, 0, 0, shide,	hhide},
    {"if",	aif,	1, 0, 1, sif, hif},
    {"ignore",	aignore,2, 0, 0, signore, hignore},
    {"import",	aimport,2, 0, 0, simport, himport},
    {"move",	amove,	2, 3, 0, smove, hmove},
    {"option",	aoption,2, 0, 0, soption, hoption},
    {"priority",aprio,	2, 2, 0, sprio,	hprio},
    {"rename",	arename,2, 3, 0, srename, hrename},
    {"set",	aset   ,2, 3, 0, sset, hset},
    {"local",	alocal ,2, 2, 0, slocal, hlocal},
    {"subdirs",	asubdirs,	1, 0, 0, ssubdirs, hsubdirs},
    {"then",	athen,	1, 1, 1, sthen,	hthen},
    {0, 0, 0, 0, 0, 0, 0}
};


/* 
 * Analyze the current tokenized line in a merge.rf 
 */

void analysetokens(mg)
     struct mg *mg;
{
    struct dict_entry *p;

    p = &dict[0];
    if (ntoken == 0) return;
    while (p->name && tokcmp(p->name, tokens[0])) p++;
    if (!p->name) {
	panic ("%s(%d) : unknow command '%s'\n",
	       mg->name, mg->line, tokens[0]);
    }
    if (p->argmin && ntoken < p->argmin) {
	panic ("%s(%d) : not enough fields in %s",
	       mg->name, mg->line, p->name);
    }
    if (p->argmax && ntoken > p->argmax) {
	panic ("%s(%d) : too many fields in %s",
	       mg->name, mg->line, p->name);
    }
    if (!isvalid() && !p->immediate) return;
    (*p->fun)(mg);    
}


/*
 * Compare a token with a word in the dictionary
 */
int tokcmp(tok, s)
    char *tok, *s;
{
    char a, b;

    do {
	a = *s++;
	b = *tok++;
	if (a >= 'a' && a <= 'z') a += 'A'-'a';
	if (b >= 'a' && b <= 'z') b += 'A'-'a';
	if (a != b) return 1;
    } while (a && b);
    return 0;
}


/*
 * Compare 's' with a flag. If 's' or 's=...' exists returns 0.
 */
int flagcmp(flg, s)
    char *flg, *s;
{
    char a, b;

    do {
	a = *s++;
	b = *flg++;
	if (a >= 'a' && a <= 'z') a += 'A'-'a';
	if (b >= 'a' && b <= 'z') b += 'A'-'a';
	if (a != b) return (a != 0 || b != '=');
    } while (a && b);
    return 0;
}


/* Check if 's' is a correct directive. 's' comes from a profile file */

char *cmdcmp(cmd, s)
    char *cmd, *s;
{
    char a, b, *old, *p;

    old = cmd;
    do {
	a = *s++;
	b = *cmd++;
	if (a >= 'a' && a <= 'z') a += 'A'-'a';
	if (b >= 'a' && b <= 'z') b += 'A'-'a';
    } while (b && a == b);
    if (b) return 0;
    while (a && a <= ' ') a = *s++;
    if (a != '=') return 0;
    a = *s++;
    while (a && a <= ' ') a = *s++;
    p = s--;
    while (*p > ' ') p++;
    *p = 0;
    return s;
}

/* 
 * mkmerge language implementation
 */

char sprio[] = "[+n,-n,n]";
char hprio[] = "change current priority";

void aprio(mg)
    struct mg *mg;
{
    if (tokens[1][0] != '-' && tokens[1][0] != '+') mg->prio = 0;
    mg->prio += atoi(tokens[1]) << PRIO_SHIFT;
}


char sif[] = "B1 ...";
char hif[] = "set the current status to status||(B1&&...)";

void aif(mg)
     struct mg *mg;
{
    int i, j, d;

    if (valid[pvalid] == Undef) valid[pvalid] = False;
    for (i = 1; i < ntoken; ++i) {
	struct flag *flag;
	d = (tokens[i][0] == '!' ? 1 : 0);

	for (flag = mg->local; flag ; flag = flag->next) {
	    if (!flagcmp(flag->name, &tokens[i][d])) {
		if (flag->off != d) return;
                break;
	    }
	}
        if (flag) continue;

	for (j = 0; j < nflag; ++j) {
	    if (!flagcmp(Flags[j].name, &tokens[i][d])) {
		Flags[j].used = j;
		break;
	    }
	}
	if ((j < nflag) == d) return;
    }
    valid[pvalid] = True;
}

char sthen[] = "";
char hthen[] = "mark the end of a test";

void athen(mg)
    struct mg *mg;
{
    valid[++pvalid] = Undef;
}


char sdefine[] = "FLAG ...";
char hdefine[] = "add FLAG to defines.lst";

void adefine(mg)
     struct mg *mg;
{
    int i;
    FILE *f;

    if (!f_show && !partdir) {
	fcreated |= F_DEFINES;
	f = fopen2 (f_defines_tmp, "a");
	for (i = 1; i < ntoken; ++i) {
	    fprintf (f, "%s\n", tokens[i]);
	}
	fclose (f);
    }
}


char soption[] = "FLAG=VAL ...";
char hoption[] = "add FLAG=VAL to options.lst";

void aoption(mg)
     struct mg *mg;
{
    int i;
    FILE *f;

    if (!f_show && !partdir) {
	fcreated |= F_OPTIONS;
	f = fopen2 (f_options_tmp, "a");
	for (i = 1; i < ntoken; ++i) {
	    fprintf (f, "%s\n", tokens[i]);
	}
	fclose (f);
    }
}


char secho[] = "MESSAGE ...";
char hecho[] = "display MESSAGE";

void aecho(mg)
     struct mg *mg;
{
    int i;

    if (vflag & V_ECHO) {
	for (i = 1; i < ntoken; ++i) {
	    printf ("%s\n", tokens[i]);
	}
    }
}


char sset[] = "FLAG VALUE";
char hset[] = "Override a flag value in the config file";

void aset(mg)
    struct mg *mg;
{
    int i;
    char buf[MAX_LEN], *fo;

    strcpy(buf, tokens[1]);
    strcat(buf, "=");
    strcat(buf, tokens[2]);
    fo = copy(buf);
    for (i = 0; i < nflag; ++i) {
	if (!flagcmp(Flags[i].name, buf)) break;
    }
    Flags[i].over = fo;
    if (i == nflag) {
	Flags[i].name = "_";
	Flags[i].used = i;
	nflag++;
    }
}

char selse[] = "";
char helse[] = "invert the current status";

void aelse(mg)
     struct mg *mg;
{
    switch (valid[pvalid]) {
    case True:
	valid[pvalid] = False;
	break;
    case False:
	valid[pvalid] = True;
	break;
    case Undef:
	if (pvalid == 0) panic("%s(%d): else with no matching if",
			       mg->name, mg->line);
	switch(valid[pvalid-1]) {
	case True:
	    valid[pvalid-1] = False;
	    break;
	case False:
	    valid[pvalid-1] = True;
	    break;
	case Undef:
	    panic("unbalanced else");
	    break;
	}
    }
}


char serror[] = "MESSAGE ...";
char herror[] = "display MESSAGE and abort mkmerge";

void aerror(mg)
     struct mg *mg;
{
    aecho (mg);
    panic ("merge aborted by %s(%d)", mg->name, mg->line);
}


char sexport[] = "PATH FILE ...";
char hexport[] = "export FILE to PATH";

void aexport(mg)
     struct mg *mg;
{
    int i, lb, lt;
    char buf[MAX_LEN], src[MAX_LEN], tgt[MAX_LEN];

    strcpy (buf, curtgt);
    strcat (buf, "/");
    lb = strlen(buf);

    strcpy (tgt, exdir);
    strcat (tgt, "/");
    strcat (tgt, tokens[1]);
    strcat (tgt, "/");
    lt = strlen(tgt);
    for (i = 2; i < ntoken; ++i) {
	buf[lb] = 0;
	strcat (buf, tokens[i]);
	tgt[lt] = 0;
	strcat (tgt, tokens[i]);
	if (!newname(buf, src)) {
	    addalloc(src, tgt, A_EXPORT, prio);
	} else {
	    panic ("aexport : newname failed on '%s' -> '%s', tgt = '%s'\n", 
		    buf, src, tgt);
	}
    }
}


char sexportexe[] = "PATH FILE ...";
char hexportexe[] = "export FILE to PATH";

void aexportexe(mg)
     struct mg *mg;
{
    int i, lb, lt;
    char buf[MAX_LEN], src[MAX_LEN], tgt[MAX_LEN], exe[MAX_LEN];

    if (!f_extexe) {
	aexport(mg);
	return;
    }

    strcpy (buf, curtgt);
    strcat (buf, "/");
    lb = strlen(buf);

    strcpy (tgt, exdir);
    strcat (tgt, "/");
    strcat (tgt, tokens[1]);
    strcat (tgt, "/");
    lt = strlen(tgt);
    for (i = 2; i < ntoken; ++i) {
	strcpy(exe, tokens[i]);
	strcat(exe, ".exe");
	buf[lb] = 0;
	strcat (buf, tokens[i]);
	strcat (buf, ".exe");
	tgt[lt] = 0;
	strcat (tgt, tokens[i]);
	strcat (tgt, ".exe");
	if (!newname(buf, src)) {
	    addalloc(src, tgt, A_EXPORT, prio);
	} else {
	    panic ("aexportexe : newname failed on '%s' -> '%s', tgt = '%s'\n",
		    buf, src, tgt);
	}
    }
}


char sexportas[] = "PATH FILE";
char hexportas[] = "export FILE to PATH";

void aexportas(mg)
    struct mg *mg;
{
    char buf[MAX_LEN], src[MAX_LEN], tgt[MAX_LEN];

    strcpy (buf, curtgt);
    strcat (buf, "/");

    strcpy (tgt, exdir);
    strcat (tgt, "/");
    strcat (tgt, tokens[1]);

    strcat (buf, tokens[2]);
    if (!newname(buf, src)) {
	addalloc(src, tgt, A_EXPORT, prio);
    } else {
	panic ("aexportas : newname failed on '%s' -> '%s', tgt = '%s'\n", 
	       buf, src, tgt);
    }
}

char sgroup[] = "GROUP FILE ...";
char hgroup[] = "add FILE to GROUP";

void agroup(mg)
     struct mg *mg;
{
    int i, ls;
    char src[MAX_LEN];

    strcpy (src, cursrc);
    strcat (src, "/");
    ls = strlen(src);

    for (i = 2; i < ntoken; ++i) {
	src[ls] = 0;
	strcat (src, tokens[i]);
	if (!access(src, F_OK)) add2group(tokens[1], src);
    }
}


/* combine 2 paths */

void combine(a, r)
    char *a, *r;
{
    char c;
    int i, j;

    if (r[0] == '/') {
	strcpy(a, r);
	return;
    }
    while (*r) {
	i = 0;
	while (r[i] && r[i] != '/') ++i;
	c = r[i];
	if (i == 2 && !strncmp(r, "..", i)) {
	    j = bazname(a);
	    a[j-1] = 0;
	} else if (i != 1 || strncmp(r, ".", i)) {
	    j = strlen(a);
	    a[j] = '/';
	    strncpy(&a[j+1], r, i);
	    a[i+j+1] = 0;
	}
	r = &r[i];
	if (c) r++;
    }
}


char smove[] = "PATH";
char hmove[] = "move the current directory to PATH";

void amove(mg)
     struct mg *mg;
{
    if (tokens[1][0] == '/') {
	panic ("%s(%d) : invalid path '%s'\n", mg->name, mg->line, tokens[1]);
    }
    combine(curtgt, tokens[1]);
}


char srename[] = "FILE NEWNAME";
char hrename[] = "rename FILE to NEWNAME";

void arename(mg)
     struct mg *mg;
{
    struct movefile *p;

    p = (struct movefile *)mallocmg(mg, sizeof(struct movefile));
    p->next = mg->movefile;

    p->name = (char *)mallocmg(mg, 1+strlen(tokens[1]));
    strcpy (p->name, tokens[1]);

    p->newname = (char *)mallocmg(mg, 1+strlen(tokens[2]));
    strcpy (p->newname, tokens[2]);

    mg->movefile = p;
}


char slocal[] = "var=value";
char hlocal[] = "declare a local flag known only in the subdirs.";

void alocal(mg)
     struct mg *mg;
{
    struct flag *f;
    int		l;

    f = (struct flag*) malloc(sizeof(struct flag));
    bzero(f, sizeof(struct flag));
    f->name = copy(tokens[1]);
    l = strlen(tokens[1]);

    if (f->name && !strcmp(&f->name[l-4],"=off")) {
	f->off = 1;
	f->name[l-4] = 0;
    }
    if (f->name && !strcmp(&f->name[l-3],"=no")) {
	f->off = 1;
	f->name[l-3] = 0;
    }
    if (f->name && !strcmp(&f->name[l-4],"=yes")) {
	f->name[l-4] = 0;
    }
    if (f->name && !strcmp(&f->name[l-3],"=on")) {
	f->name[l-3] = 0;
    }

    f->next = mg->local;
    mg->local = f;
}


char sfork[] = "DIRECTORY NEWNAME";
char hfork[] = "fork DIRECTORY to NEWNAME";

void afork(mg)
     struct mg *mg;
{
    struct movefile *p;

    p = (struct movefile *)mallocmg(mg, sizeof(struct movefile));
    p->next = mg->forks;

    p->name = (char *)mallocmg(mg, 1+strlen(tokens[1]));
    strcpy (p->name, tokens[1]);

    p->newname = (char *)mallocmg(mg, 1+strlen(tokens[2]));
    strcpy (p->newname, tokens[2]);

    mg->forks = p;
}


char signore[] = "FILE ...";
char hignore[] = "don't merge FILE";

void aignore(mg)
    struct mg *mg;
{
    int i;
    struct strlist *p;
    for (i = 1; i < ntoken; ++i) {
	p = (struct strlist *)mallocmg(mg, sizeof(struct strlist));
	p->next = mg->ignore;
	p->name = copy(tokens[i]);
	mg->ignore = p;
    }
}


char ssubdirs[] = "DIRECTORY ...";
char hsubdirs[] = "enter DIRECTORY";

void asubdirs(mg)
    struct mg *mg;
{
    int i;
    struct strlist *p;
    if (ntoken == 1) {
	p = (struct strlist *)mallocmg(mg, sizeof(struct strlist));
	p->next = 0;
	p->name = copy(".");
	mg->subdirs = p;
    }
    for (i = 1; i < ntoken; ++i) {
	p = (struct strlist *)mallocmg(mg, sizeof(struct strlist));
	p->next = mg->subdirs;
	p->name = copy(tokens[i]);
	mg->subdirs = p;
    }
}


char shide[] = "FILE ...";
char hhide[] = "don't merge FILE";

void ahide(mg)
    struct mg *mg;
{
    mg->hidden = A_HIDE;
}


char simport[] = "GROUP ...";
char himport[] = "import GROUP in the current directory";

void aimport(mg)
    struct mg *mg;
{
    int i, j;
    char src[MAX_LEN], tgt[MAX_LEN];
    
    for (i = 1; i < ntoken; ++i) {
	j = bazname(tokens[i]);
	strcpy (src, curtgt);
	combine (src, tokens[i]);
	strcpy (tgt, curtgt);
	combine (tgt, &tokens[i][j]);
	addalloc(src, tgt, A_IMPORT | (j ? 0 : A_GROUP), prio);
    }
}


char sendif[] = "";
char hendif[] = "undefine the current status";

void aendif(mg)
     struct mg *mg;
{
    if (valid[pvalid] == Undef) pvalid--;
    if (pvalid < 0) panic("%s(%d) : too many endifs", mg->name, mg->line);
    valid[pvalid] = Undef;
}


char sexit[] = "";
char hexit[] = "don't merge current directory";

void aexit(mg)
     struct mg *mg;
{
    valid[pvalid] = False;
    mg->exit = 1;
}



/* Alloc a merge.rf structure */

void *mallocmg(mg, l)
    struct mg *mg;
    int l;
{
    void *p;
    struct mem *q;

    p = malloc(l);
    if (!p) panic ("malloc failed");
    q = (struct mem *) malloc(sizeof(struct mem));
    if (!q) panic ("malloc failed");
    q->addr = p;
    q->next = mg->mem;
    mg->mem = q;
    return p;
}


/*  Free a merge.rf structure */

void closemg(mg)
    struct mg *mg;
{
    struct mem *p;

    while (mg->mem) {
	free (mg->mem->addr);
	p = mg->mem->next;
	free (mg->mem);
	mg->mem = p;
    }
}

/* convert an old name into a new name */

int newname(old, new)
    char *old, *new;
{
    int k, l;
    struct movefile *movefile;
    struct strlist *ignore;

    /* attach the file to a directory if needed */
    if (old[0] == '/') {
	strcpy (new, old);
    } else {
	strcpy (new, curtgt);
	combine (new, old);
    }

    /* was this file renamed ? */
    movefile = chain->movefile;
    while (movefile) {
	prune (new, movefile->newname, movefile->name);
	movefile = movefile->next;
    }

    /* was this file ignored ? */ 
    l = strlen(new);
    for (ignore = chain->ignore; ignore; ignore = ignore->next) {
	k = strlen(ignore->name);
	if (l < k+1) continue;
	if (new[l-k-1] == '/' && !strcmp(&new[l-k], ignore->name)) return 1;
    }

    /* verify that this file is still is the merge directory */
    l = destl;
    k = strlen(new);
    if (k > l && new[l] == '/' && strncmp(new, dest, l) == 0) return 0;
    /* try the export directory */
    l = strlen(exdir);
    if (k > l && new[l] == '/' && strncmp(new, exdir, l) == 0) return 0;
    /* tried to escape ... */
    panic ("newname failed :\nold=%s\nnew=%s\nsrc=%s\ntgt=%s\n",
	    old, new, cursrc, curtgt);
}

void prune(s, newname, name)
    char *s, *newname, *name;
{
    int f, l, ln;

    f = 0;
    for (ln = 0; newname[ln]; ++ln) {
	if (newname[ln] == '/') break;
    }
    l = strlen(s);
    while (l) {
	l--;
	if (s[l] != '/') continue;
	if (f == 0) {
	    if (strcmp(&s[l+1], name)) return;
	    f = l;
	    if (newname[ln] == 0) break;
	}
	if (!strncmp(&s[l+1], newname, ln)) {
	    f = l;
	    break;
	}
    }
    if (f) strcpy (&s[f+1], newname);
}

void conflicts_src()
{
    int i;
    struct alloc *p;

    talloc = (struct alloc **) malloc (nalloc*sizeof(struct alloc *));
    if (!talloc) panic ("malloc failed in conflicts");
    p = alloc;
    for (i = 0; i < nalloc; ++i) {
	talloc[i] = p;
	p = p->next;
    }
}

void conflicts_tgt()
{
    int i, j, f;

    qsort (talloc, nalloc, sizeof(struct alloc *), compar_tgt);
    f = 0;
    for (i = 0, j = -1; i < nalloc; ++i) {
	if (j < 0 || strcmp(talloc[j]->target, talloc[i]->target)) {
	    if (!f) j++;
	    f = 0;
	    talloc[j] = talloc[i];
	} else if (talloc[j]->prio == talloc[i]->prio) {
	    if (vflag & V_CONFLICTS || !f_cont) {
		    printf ("conflict between :\n\t'%s'\n\t'%s'\n",
			    talloc[j]->source, talloc[i]->source);
		}
		++nbrconflicts;
		f = 1;
	    if (!f_cont) panic ("conflict impossible to solve");
	}
    }
    nalloc = j+1-f;
}


/* Handle conflicts */

void conflicts()
{
    conflicts_src();
    conflicts_tgt();
}

int compar_src(a, b)
    const void *a, *b;
{
    struct alloc *p, *q;

    int comp;
    p = *(struct alloc **) a;
    q = *(struct alloc **) b;
    comp = strcmp(p->source, q->source);
    if (comp == 0) {
	comp = q->prio - p->prio;
    }
    return comp;
}

int compar_tgt(a, b)
    const void *a, *b;
{
    struct alloc *p, *q;

    int comp;
    p = *(struct alloc **) a;
    q = *(struct alloc **) b;
    comp = strcmp(p->target, q->target);
    if (comp == 0) {
	comp = q->prio - p->prio;
    }
    return comp;
}

char *plural(n, s, p)
    int n;
    char *s, *p;
{
    return (n > 1) ? p : s;
}


/* Display statistics */

void dispstat(msg, n, s, p)
    int n;
    char *msg, *s, *p;
{
    if (n) {
	printf (msg, n, plural(n, s, p));
    }
}

/* Make all links (or copies) */

void domerge()
{
    int i, j, k, rc, retry, inretry, canretry, pgr;
    char c;

    retry = inretry = 0;
    canretry = 1;
    pgr = 0;
    for (i = 0; i < nalloc || retry; ++i) {
	if (i >= nalloc && retry) {
	    i = 0;
	    retry = 0;
	    inretry++;
	    if (pgr == 0) canretry = 0;
	    pgr = 0;
	}
	if (inretry && !(talloc[i]->flag & A_RETRY)) continue;
	if (((f_relative || (talloc[i]->flag & A_IMPORT))
	     && (f_copy == C_SYMLINK)) ||
	    (talloc[i]->flag & A_EXPORT)) {
	    if (!f_noslink || !(talloc[i]->flag & A_EXPORT)) {
                allocrel(talloc[i]);
            }
	}
	if (f_copy == C_SYMLINK || (talloc[i]->flag & A_EXPORT)) {
	    if (!verifylink(talloc[i]->target, talloc[i]->source)) {
		nbrlok++;
		continue;
	    }
	}
	if (vflag & V_LINK) {
	    printf ("%s -> %s\n", talloc[i]->target, talloc[i]->source);
	}
	if (f_show) continue;
	unlink(talloc[i]->target);
	rc = dolink (talloc[i]);
	if (rc) {
	    for (j = k = 0; talloc[i]->target[j]; ++j) {
		if (talloc[i]->target[j] == '/') k = j;
	    }
	    c = talloc[i]->target[k];
	    talloc[i]->target[k] = 0;
	    mkdirp (talloc[i]->target);
	    talloc[i]->target[k] = c;
	    rc = dolink (talloc[i]);
	    if (rc) {
		if (canretry) {
		    retry++;
		    talloc[i]->flag |= A_RETRY;
		    continue;
		} else {
		    panic("error linking %s to %s, errno %d", 
			  talloc[i]->target, talloc[i]->source,
			  errno);
		}
	    }
	    if (vflag & V_DIR) {
		talloc[i]->target[k] = 0;
		printf ("created %s/\n", talloc[i]->target);
		talloc[i]->target[k] = c;
	    }
	}
	if (rc == 0) {
	    talloc[i]->flag &= ~A_RETRY;
	    pgr++;
	    if (f_copy == C_SYMLINK || (talloc[i]->flag && A_EXPORT)) {
		nbrslink++;
	    } else if (f_copy == C_COPY) {
		nbrcopy++;
	    } else {
		nbrhlink++;
	    }
	}
    }
    if (vflag & V_STATS) {
	while (fcreated) {
	    nbrcreat += (fcreated & 1);
	    fcreated /= 2;
	}
	dispstat ("%5d director%s created\n", nbrdir, "y", "ies");
	dispstat ("%5d file%s copied\n", nbrcopy, "", "s");
	dispstat ("%5d file%s created\n", nbrcreat, "", "s");
	dispstat ("%5d file%s untouched\n", nbrleft, "", "s");
	dispstat ("%5d symbolic link%s created\n", nbrslink, "", "s");
	dispstat ("%5d hard link%s created\n", nbrhlink, "", "s");
	dispstat ("%5d link%s untouched\n", nbrlok, "", "s");
	dispstat ("%5d conflict%s\n", nbrconflicts, "", "s");
    }
    if (vflag & V_GROUP) {
	for (i = 0; i < HASHSIZE; ++i) {
	    struct group *g;
	    struct gfile *f;
	    g = hg[i];
	    while (g) {
		printf ("group '%s' :\n", g->name);
		f = g->files;
		while (f) {
		    printf ("\t'%s'\n", f->name);
		    f = f->next;
		}
		g = g->next;
	    }
	}
    }
}


/* Verify if a link already exists, and is correct */

int verifylink(path, link)
    char *path, *link;
{
    char buf[MAX_LEN];
    int rc;

    rc = readlink(path, buf, MAX_LEN);
    if (rc < 0) {
	if (f_append && errno == EINVAL) {
	    nbrleft++;
	    return 0;
	}
	return 1;
    }
    buf[rc] = 0;
    if (strcmp(buf, link)) {
	return 1;
    }
    return 0;
}


/* Turn an absolute symbolic link into a relative one */

char *abstorel(source, target)
    char *source, *target;
{
    static char buf[MAX_LEN];

    int i, j, l;
    for (i = j = 0; target[i] == source[i]; ++i) {
	if (target[i] == '/') j = i+1;
    }
    buf[0] = 0;
    for (l = 0; target[i]; ++i) {
	if (target[i] == '/') {
	    buf[l++] = '.';
	    buf[l++] = '.';
	    buf[l++] = '/';
	}
    }
    strcpy(&buf[l], &source[j]);
    return buf;
}

void allocrel(t)
    struct alloc *t;
{
    char *p;

    t->abssource = copy(t->source);
    p = abstorel(t->source, t->target);
    t->source = copy(p);
}


/* Make a link or a copy */

int dolink(t)
    struct alloc *t;
{
    int rc;
    
        /* find if the source comes from the split tree */
    if (t->flag & A_EXPORT) {
        int fromsplit, copylater, i;
        unsigned int h;
        fromsplit = 0;
        copylater = 0;
        h = hash(t->abssource);
        if (hhints[h]) {
            for (i = 0; !fromsplit && i < nalloc; ++i) {
                if (h != talloc[i]->cksum) continue;
                if (strcmp(t->abssource, talloc[i]->target)) continue;
                fromsplit = 1;
            }
        }
	if (!f_noslink) {
	    rc = symlink (t->source, t->target);
	} else {
            if (fromsplit) {
                if (f_copy == C_COPY) {
                    rc = docopy (t->source, t->target);
                } else {
                    rc = link(t->source, t->target);
                }
	    } else {
                    /* the source will be produced during generation */
                copylater = 1;
                rc = 0;
            }
	}
        if (rc == 0) addexport(t, fromsplit, copylater);
    } else if (f_copy == C_SYMLINK) {
	rc = symlink (t->source, t->target);
    } else if (f_copy == C_COPY) {
	rc = docopy (t->source, t->target);
    } else {
	rc = link (t->source, t->target);
    }
    return rc;
}

int docopy(source, target)
    char *source;
    char *target;
{
    static char buf[32768];
    struct stat statb;
    int fs, ft, rc, l;

    ft = open(target, O_RDWR | O_TRUNC | O_CREAT | O_BINARY, 0777);
    if (ft < 0) return -1;
    fs = open(source, O_RDONLY | O_BINARY);
    if (fs < 0) {
	close(ft);
	return -1;
    }
    fstat (fs, &statb);
    while ((rc = read(fs, buf, 32768)) > 0) {
	l = write(ft, buf, rc);
	if (l != rc) panic ("error writing to %s\n", target);
    }
    chmod (target, statb.st_mode);
    close (ft);
    close (fs);
    return rc;
}


/* group expansion */

void expandgroups()
{
    struct alloc *newalloc, *p;

    newalloc = 0;
    while (alloc) {
	p = alloc;
	alloc = alloc->next;
	if (p->flag & (A_GROUP | A_HIDE)) {
	    if ((p->flag & A_HIDE)) {
		nalloc--;
		continue;
	    }
	    if (expandfile(p)) {
		nalloc--;
		continue;
	    }
	}
	p->next = newalloc;
	newalloc = p;
    }
    alloc = newalloc;
}


/* Expand file name beloging to a group */

int expandfile(p)
    struct alloc *p;
{
    int j, k;
    unsigned int s;
    char *name;
    struct group *g;
    struct gfile *f;
    char buf[MAX_LEN];

    name = &p->source[bazname(p->source)];
    j = bazname(p->target);
    /* file renamed ? certainly not a group */
    if (strcmp(&p->target[j], name)) return 0;

    /* try to find a group with the same name */
    s = hash(name);
    g = hg[s];
    while (g) {
	if (!strcmp(g->name, name)) break;
	g = g->next;
    }
    /* if the source and the target are the same, it is an empty group */
    if (!g) return (strcmp(p->source, p->target) == 0);

    /* we have a group, expand it */
    g->used = 1;
    f = g->files;
    strcpy (buf, p->target);
    while (f) {
	k = bazname(f->name);
	strcpy (&buf[j], &f->name[k]);
	addalloc(f->name, buf, p->flag, f->prio);
	f = f->next;
    }
    return 1;
}


/* Add the allocation to the list of things to export */

void addexport(t, fromsplit, copylater)
    struct alloc *t;
    int fromsplit, copylater;
{
    struct export *p;

    p = (struct export *)malloc(sizeof(*p));
    p->next = exports;
    p->source = copy(&t->abssource[1+destl]);
    p->target = copy(&t->target[1+destl]);
    p->fromsplit = fromsplit;
    p->copylater = copylater;
    exports = p;
}


/* Write the list of not-yet exported files to 'exports.lst' */

void dumpexports()
{
    FILE *f;
    struct export *p;

    if (!f_show && !partdir && exports) {
	fcreated |= F_EXPORTS;
	f = fopen2(f_exports_tmp, "w");
	for (p = exports; p; p = p->next) {
            fprintf (f, "%c%c %s %s\n", "-s"[p->fromsplit], "-c"[p->copylater],
                     p->source, p->target);
	}
	fclose(f);
    }
}


/* Handle '-U' and '-u' options */

void update(flag)
    int flag;
{
    char cwd[MAX_LEN];
    char wd[MAX_LEN];
    char buf[MAX_LEN], buf2[MAX_LEN];
    int k, l;
    DIR *dir;
    struct dirent *direntp;
    int vflag2;

    if (!getcwd(cwd, MAX_LEN)) panic("error in getcwd");
    if (flag) {
	window = copy(cwd);
	lwindow = strlen(window);
    }
    l = MAX_LEN;
    while (1) {
	if (!getcwd(wd, MAX_LEN)) panic("error in getcwd");
	k = strlen(wd);
	if (!access(f_profile, 0)) break;
	if (k >= l) {
	    panic ("the current directory is not a valid merged tree");
	}
	l = k;
	chdir("..");
    }
    strcat(wd, "/");
    strcat(wd, f_profile);
    optf(wd);
    l = readlink("Makefile", buf, MAX_LEN);
    if (l > 0 && buf[0] == '.') f_relative = 1;
    wd[k] = 0;
    optt(wd);
    chdir(cwd);
    vflag2 = vflag | (1 << (strchr(verb_opt, 'l')-verb_opt));
    if (flag) {
	dir = opendir(".");
	if (!dir) panic("failed to open . in %s", cwd);
	while ((direntp = readdir(dir))) {
	    l = readlink(direntp->d_name, buf, MAX_LEN);
	    if (l > 0) {
		buf[l] = 0;
		strcpy(buf2, cwd);
		combine(buf2, buf);
		if (!strncmp(buf2, wd, k)) continue;
		l = bazname(buf2);
		if (l) buf2[l-1] = 0;
		optp(buf2);
		vflag = vflag2;
	    }
	}
	closedir(dir);
    }
}

/*
 * purge the merged tree :
 * all symbolic links will be removed
 * directory that wont contain symbolic links will be removed
 */

char cdir[MAX_LEN];
int lcdir;
struct strlist *dirok;

    void
creep(splash)
    int splash;
{
    DIR *dir;
    struct dirent *direntp;
    int l, rc, h;
    struct stat st;
    char *fname;
    struct strlist *d;

    dir = opendir(".");
    if (!dir) panic("failed to open %s", cdir);
    h = hash(cdir);
    d = dirok;
    while (d) {
        if (d->val == h && !strcmp(cdir, d->name)) break;
        d = d->next;
    }
    while ((direntp = readdir(dir))) {
	fname = direntp->d_name;
	if (fname[0] == '.') continue;
	rc = lstat(fname, &st);
	if (rc) panic("lstat failed on %s/%s", cdir, fname);
	if (S_ISDIR(st.st_mode)) {
	    l = lcdir;
	    cdir[l] = '/';
	    strcpy(&cdir[l+1], fname);
	    lcdir = strlen(cdir);
	    chdir(cdir);
	    creep(1);
	    cdir[l] = 0;
	    lcdir = l;
	    chdir(cdir);
            if (!f_show) {
                rc = rmdir(fname);
            } else {
                rc = 0;
            }
            if ((vflag & V_ZAP) && !rc) {
                printf("rmdir %s/%s\n", cdir, fname);
            }
	} else if (S_ISLNK(st.st_mode) || (!d && splash)) {
            if (!f_show) {
                rc = unlink(fname);
            } else {
                rc = 0;
            }
            if ((vflag & V_ZAP) && !rc) {
                printf("unlink %s/%s\n", cdir, fname);
            }
        }
    }
    closedir(dir);
}

    void
purge()
{
    int i, l, pl;
    struct strlist *d;

        /* purge will be faster if talloc is sorted according to targets */
    dirok = 0;
    pl = 0;
    for (i = nalloc-1; i>=0; --i) {
        l = bazname(talloc[i]->target);
        if (pl != l || strncmp(talloc[i]->target, dirok->name, l-1)) {
            d = (struct strlist *) malloc(sizeof(*d));
            d->name = malloc(l);
            strncpy(d->name, talloc[i]->target, l-1);
            d->name[l-1] = 0;
            d->next = dirok;
            d->val = hash(d->name);
            dirok = d;
        }
        pl = l;
    }
    lcdir = strlen(dest);
    strcpy(cdir, dest);
    chdir(dest);
    creep(0);
    chdir(dest);
}

/*
 * POSIX calls, for non-POSIX systems
 */

#ifdef chdir
#undef chdir
/* stupid chdir for stupid operating systems */
int losechdir(char *where)
{
    char *p, *q, c = '/';
    int rc;
    for (p = q = where; c; p = ++q) {
        while (*q && (q-p < 32 || *q != '/')) q++;
        c = *q;
        if (c) *q = 0;
        rc = chdir(p); 
	if (c) *q = c;
	if (rc) panic("chdir to '%s' failed", p);
    }
    return 0;
}
#endif
