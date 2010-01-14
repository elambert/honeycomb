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
#include <string.h>
#include <stdlib.h>

#include "ofoto.h"

int start_dir, end_dir;
char **md_vals;
char *file_basename;
int files_per_dir;
of_op callback;

static void
err(char *str)
{
    fprintf(stderr, "%s\n", str);
    exit(1);
}

static void gen_level(int level);

void
of_gen_files(int start_d, int end_d, char *f_basename, int files_per_d,
             of_op operation)
{
    start_dir = start_d;
    if (start_dir < 0  ||  start_dir > MAX_DIR_DENSITY-1)
        err("start_dir");
    end_dir = end_d;
    if (end_dir < start_dir  ||  end_dir > MAX_DIR_DENSITY-1)
        err("end_dir");
    file_basename = f_basename;
    if (f_basename != NULL  &&  strlen(file_basename) < 1)
        err("file_basename");
    files_per_dir = files_per_d;
    callback = operation;

    /*
     *  prepare for md
     */
    md_vals = (char **) malloc((MAX_DIRLEVEL+1) * sizeof(char *));

    /*
     *  iterate over dirs, generating md & filenames
     *  and calling callback function for each file
     */
    gen_level(1);
    
}

static void
gen_level(int level)
{
    int i, j;
    char dvalue[16];
    char fvalue[256];

    md_vals[level-1] = dvalue;

    for (i=start_dir; i<=end_dir; i++) {

        /* generate value of dirname */
        sprintf(dvalue, "%.2x", i);

        /* fname needs to be refreshed after each recursion */
        md_vals[level] = fvalue;

        /*
         *  handle end case or recurse
         */
        if (level == MAX_DIRLEVEL) {
            int n_files = files_per_dir;
            if (n_files == ALL_DIRS)
                n_files = 1;

            /* end case 
            if (files_per_dir < 0) {
                if (files_per_dir == LEAF_DIRS)
                    (*callback)(md_vals, level);
                continue;
            }
            */
            for (j=0; j<n_files; j++) {
                /* 
                 *  generate fname for each size, and callback
                 */
                sprintf(fvalue, "%s_%d_5k", file_basename, j);
                (*callback)(md_vals, FILE_5K);

                sprintf(fvalue, "%s_%d_15k", file_basename, j);
                (*callback)(md_vals, FILE_15K);

                sprintf(fvalue, "%s_%d_50k", file_basename, j);
                (*callback)(md_vals, FILE_50K);

                sprintf(fvalue, "%s_%d_900k", file_basename, j);
                (*callback)(md_vals, FILE_900K);

            }
        } else {
            if (files_per_dir == ALL_DIRS) {
                /*
                 *  want all dirs
                 */
                sprintf(fvalue, "%s_fill_5k", file_basename);
                (*callback)(md_vals, level);
            }
            /* recurse */
            gen_level(level+1);
        }
    }
}

