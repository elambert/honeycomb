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



/*
**
**  Internal header file for editline library.
*/
#include <stdio.h>
#if	defined(HAVE_STDLIB)
#include <stdlib.h>
#include <string.h>
#endif	/* defined(HAVE_STDLIB) */
#if	defined(SYS_UNIX)
#include "unix.h"
#endif	/* defined(SYS_UNIX) */
#if	defined(SYS_OS9)
#include "os9.h"
#endif	/* defined(SYS_OS9) */

#include <unistd.h>

#if	!defined(SIZE_T)
#define SIZE_T	unsigned int
#endif	/* !defined(SIZE_T) */

typedef unsigned char	CHAR;

#if	defined(HIDE)
#define STATIC	static
#else
#define STATIC	/* NULL */
#endif	/* !defined(HIDE) */

#if	!defined(CONST)
#if	defined(__STDC__)
#define CONST	const
#else
#define CONST
#endif	/* defined(__STDC__) */
#endif	/* !defined(CONST) */


#define MEM_INC		64
#define SCREEN_INC	256

#define DISPOSE(p)	free((char *)(p))
#define NEW(T, c)	\
	((T *)malloc((unsigned int)(sizeof (T) * (c))))
#define RENEW(p, T, c)	\
	(p = (T *)realloc((char *)(p), (unsigned int)(sizeof (T) * (c))))
#define COPYFROMTO(new, p, len)	\
	(void)memcpy((char *)(new), (char *)(p), (int)(len))

#if !defined (PARAMS)
#  if defined (__STDC__) || defined (__GNUC__) || defined (__cplusplus)
#    define PARAMS(protos) protos
#  else
#    define PARAMS(protos) ()
#  endif
#endif
/* This is the completion callback */
typedef char *rl_compentry_func_t PARAMS((const char *, int));

/*
**  Variables and routines internal to this package.
*/
extern int	rl_eof;
extern int	rl_erase;
extern int	rl_intr;
extern int	rl_kill;
extern int	rl_quit;
#if	defined(DO_SIGTSTP)
extern int	rl_susp;
#endif	/* defined(DO_SIGTSTP) */
extern char	*rl_complete();
extern int	rl_list_possib();
extern void	rl_ttyset();
extern void	rl_add_slash();
extern void rl_initialize();
extern char* readline(const char*);
extern void add_history (char*);

extern rl_compentry_func_t *custom_complete;

#if	!defined(HAVE_STDLIB)
extern char	*getenv();
extern char	*malloc();
extern char	*realloc();
extern char	*memcpy();
extern char	*strcat();
extern char	*strchr();
extern char	*strrchr();
extern char	*strcpy();
extern char	*strdup();
extern int	strcmp();
extern int	strlen();
extern int	strncmp();
#endif	/* !defined(HAVE_STDLIB) */
