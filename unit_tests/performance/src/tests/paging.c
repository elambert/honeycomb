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
#include <stdlib.h>

int main (int argc, char *argv[]) {
   if (argc < 2) { 
      printf ("%s <no. of 4K pages to allocate>\n");
      exit (1); 
   }
   int npages = atoi (argv[1]);
 
   // Allocate 1M memory chunks and write to random pages 
   int *memp[10]; 
   int p, i;
   for (p=0; p<10; p++) {;
      memp[p] = (int *) malloc (npages * 1024000 * sizeof(int));
      if (memp[p] == NULL) {
         perror ("malloc error");
         break;
      } 
      int page;
      for (i=0; i<npages; i++) {
         page = rand () % npages;
         memset (memp[p]+(page*1024000), page, sizeof(int) * 1024000); 
         printf ("wrote to page %d within memory block 0x%x\n", page, memp[p]); 
      } 
   }

   // cleanup
   for (i=0; i<p; i++) { 
      if (memp[i] != NULL) {
         printf ("free'ing memory block 0x%x\n", memp[i]);
         free (memp[i]);
      } 
   } 
}
