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
 * This is a general purpose list implementation.
 *
 * It covers FIFO lists and ordered lists
 */

#ifndef _LIST_H_
#define _LIST_H_

typedef int (*hc_list_comparison_t)(void *a, void *b);

typedef void *hc_list_opaque_t;

/*
 * The hc_list_allocate routine creates a list.
 *
 * If the comparison function is NULL, then a FIFO is created
 */

hc_list_opaque_t *
hc_list_allocate(hc_list_comparison_t comparison);

typedef void (*hc_list_data_free_t)(void *datas);

void
hc_list_free(hc_list_opaque_t *list,
	     hc_list_data_free_t data_free);

int
hc_list_add_element(hc_list_opaque_t *list,
		    void *element);

void *
hc_list_get_first_element(hc_list_opaque_t *list);

void *
hc_list_extract_first_element(hc_list_opaque_t *list);

#endif /* _LIST_H_ */
