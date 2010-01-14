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



#include <malloc.h>

#include "list.h"

/*
 * Private type definitions
 */

typedef struct hc_list_elem_t {
    struct hc_list_elem_t *next;
    struct hc_list_elem_t *previous;
    void *datas;
} hc_list_elem_t;

typedef struct {
    hc_list_elem_t *first_elem;
    hc_list_elem_t *last_elem;
    hc_list_comparison_t comparison;
} hc_list_t;

/*
 * Private routines
 */

/*
 * API implementation
 */

hc_list_opaque_t *
hc_list_allocate(hc_list_comparison_t comparison)
{
    hc_list_t *result = NULL;

    result = (hc_list_t*)malloc(sizeof(hc_list_t));
    if (!result) {
	return(NULL);
    }
    
    result->first_elem = NULL;
    result->last_elem = NULL;
    result->comparison = comparison;

    return((hc_list_opaque_t*)result);
}

void
hc_list_free(hc_list_opaque_t *p_list,
	     hc_list_data_free_t data_free)
{
    hc_list_elem_t *cur = NULL;
    hc_list_elem_t *next = NULL;
    hc_list_t *list = (hc_list_t*)p_list;

    cur = list->first_elem;
    while (cur) {
	next = cur->next;
	cur->next = NULL;
	cur->previous = NULL;
	if (data_free) {
	    data_free(cur->datas);
	}
	cur->datas = NULL;
	free(cur);
	cur = next;
    }

    list->first_elem = NULL;
    list->last_elem = NULL;
    list->comparison = NULL;
    free(list);
}

int
hc_list_add_element(hc_list_opaque_t *p_list,
		    void *element)
{
    hc_list_t *list = (hc_list_t*)p_list;
    hc_list_elem_t *elem = NULL;
    hc_list_elem_t *current = NULL;

    elem = (hc_list_elem_t*)malloc(sizeof(hc_list_elem_t));
    if (!elem) {
	return(1);
    }

    elem->next = NULL;
    elem->previous = NULL;
    elem->datas = element;

    if (!list->first_elem) {
	list->first_elem = elem;
	list->last_elem = elem;
	return(0);
    }

    /* We have to insert the elem at the right position using the comparison function */

    current = NULL;
    if (list->comparison) {
	current = list->first_elem;
	while ((current)
	       && (list->comparison(current->datas, element) == -1)) {
	    current = current->next;
	}
    }

    if (!current) {
	/* All the current elements are smaller. It must be inserted at the last position
	   -or- there is not comparison routine */
	elem->previous = list->last_elem;
	list->last_elem->next = elem;
	list->last_elem = elem;
	return(0);
    }

    /* elem must take the place of current */
    if (current->previous) {
	current->previous->next = elem;
	elem->previous = current->previous;
    } else {
	/* elem is inserted at the first position */
	list->first_elem = elem;
    }

    current->previous = elem;
    elem->next = current;
    
    return(0);
}

void *
hc_list_get_first_element(hc_list_opaque_t *p_list)
{
    hc_list_t *list = (hc_list_t*)p_list;

    if (!list->first_elem) {
	return(NULL);
    }

    return(list->first_elem->datas);
}

void *
hc_list_extract_first_element(hc_list_opaque_t *p_list)
{
    hc_list_t *list = (hc_list_t*)p_list;
    hc_list_elem_t *elem = NULL;
    void *result = NULL;

    if (!list->first_elem) {
	return(NULL);
    }

    elem = list->first_elem;
    list->first_elem = elem->next;
    if (elem->next) {
	elem->next->previous = NULL;
    } else {
	list->last_elem = NULL;
    }

    result = elem->datas;
    elem->next = NULL;
    elem->previous = NULL;
    elem->datas = NULL;
    free(elem);

    return(result);
}
