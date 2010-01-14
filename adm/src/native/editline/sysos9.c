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
**  OS-9 system-dependant routines for editline library.
*/
#include "editline.h"
#include <sgstat.h>
#include <modes.h>


void
rl_ttyset(Reset)
    int			Reset;
{
    static struct sgbuf	old;
    struct sgbuf	new;


    if (Reset == 0) {
        _gs_opt(0, &old);
        _gs_opt(0, &new);
        new.sg_backsp = 0;	new.sg_delete = 0;	new.sg_echo = 0;
        new.sg_alf = 0;		new.sg_nulls = 0;	new.sg_pause = 0;
        new.sg_page = 0;	new.sg_bspch = 0;	new.sg_dlnch = 0;
        new.sg_eorch = 0;	new.sg_eofch = 0;	new.sg_rlnch = 0;
        new.sg_dulnch = 0;	new.sg_psch = 0;	new.sg_kbich = 0;
        new.sg_kbach = 0;	new.sg_bsech = 0;	new.sg_bellch = 0;
        new.sg_xon = 0;		new.sg_xoff = 0;	new.sg_tabcr = 0;
        new.sg_tabsiz = 0;
        _ss_opt(0, &new);
        rl_erase = old.sg_bspch;
        rl_kill = old.sg_dlnch;
        rl_eof = old.sg_eofch;
        rl_intr = old.sg_kbich;
        rl_quit = -1;
    }
    else
        _ss_opt(0, &old);
}

void
rl_add_slash(path, p)
    char	*path;
    char	*p;
{
    (void)strcat(p, access(path, S_IREAD | S_IFDIR) ? " " : "/");
}
