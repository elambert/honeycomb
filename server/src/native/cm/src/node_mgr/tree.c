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
 * Honeycomb project
 *
 * Cluster Management
 *
 * $Header: /root/honeycomb/honeycomb/src/cm/src/node_mgr/tree.c,v 1.2 2003/08/15 17:42:42 sarnoud Exp $
 */

#include <malloc.h>
#include <trace.h>

#include "tree.h"

/*
 * Internal type declarations
 */

typedef struct hc_tree_t {
    struct hc_tree_t *father;
    struct hc_tree_t *first_child;
    struct hc_tree_t *next_brother;
    struct hc_tree_t *previous_brother;
    void *datas;
} hc_tree_t;

/*
 * Private routines
 */

static void
hc_tree_reset_pointers(hc_tree_t *node)
{
    node->father = NULL;
    node->first_child = NULL;
    node->next_brother = NULL;
    node->previous_brother = NULL;
}

/*
 * API implementation
 */

hc_tree_opaque_t *
hc_tree_allocate()
{
    hc_tree_t *result = NULL;

    result = (hc_tree_t*)malloc(sizeof(hc_tree_t));
    if (!result) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "malloc failed");
        return(NULL);
    }
	
    hc_tree_reset_pointers(result);
    result->datas = NULL;

    return((hc_tree_opaque_t*)result);
}

void
hc_tree_free(hc_tree_opaque_t *node)
{
    free(node); 
}

void
hc_tree_set_datas(hc_tree_opaque_t *op_node,
                  void *datas)
{
    hc_tree_t *node = (hc_tree_t*)op_node;

    node->datas = datas;
}

void *
hc_tree_get_datas(hc_tree_opaque_t *op_node)
{
    hc_tree_t *node = (hc_tree_t*)op_node;
	
    return(node->datas);
}

void
hc_tree_add_child(hc_tree_opaque_t *op_father,
                  hc_tree_opaque_t *op_child)
{
    hc_tree_t *father = (hc_tree_t*)op_father;
    hc_tree_t *child = (hc_tree_t*)op_child;
    hc_tree_t *last_child = NULL;

    hc_tree_reset_pointers(child);

    if (!father->first_child) {
        father->first_child = child;
        child->father = father;
        return;
    }

    last_child = father->first_child;
    while (last_child->next_brother) {
        last_child = last_child->next_brother;
    }

    last_child->next_brother = child;
    child->previous_brother = last_child;

    /* Update the father of the new child brothers (if any) */
    last_child = child;
    while (last_child) {
        last_child->father = father;
        last_child = last_child->next_brother;
    }
}

void
hc_tree_add_brother(hc_tree_opaque_t *op_node,
                    hc_tree_opaque_t *op_new_brother)
{
    hc_tree_t *node = (hc_tree_t*)op_node;
    hc_tree_t *new_brother = (hc_tree_t*)op_new_brother;
    hc_tree_t *current_brother = NULL;

    hc_tree_reset_pointers(new_brother);

    current_brother = node->next_brother;
    node->next_brother = new_brother;
    new_brother->previous_brother = node;

    new_brother->next_brother = current_brother;
    if (current_brother) {
        current_brother->previous_brother = new_brother;
    }

    new_brother->father = node->father;
}

void
hc_tree_remove(hc_tree_opaque_t *op_node)
{
    hc_tree_t *node = (hc_tree_t*)op_node;
    hc_tree_t *child = NULL;

    if (!node->previous_brother) {
        /* I am the first child. Update the father */
        if (node->father) {
            node->father->first_child = node->next_brother;
        }
    }

    if (node->next_brother) {
        node->next_brother->previous_brother = node->previous_brother;
    }

    if (node->previous_brother) {
        node->previous_brother->next_brother = node->next_brother;
    }

    if ((node->first_child)
        && (node->father)) {
        /* Attach the children to the father */
        hc_tree_add_child((hc_tree_opaque_t*)node->father,
                          (hc_tree_opaque_t*)node->first_child);
    } else {
        /* Make all my children orphans */
        child = node->first_child;
    
        while (child) {
            child->father = NULL;
            child = child->next_brother;
        }
    }

    hc_tree_reset_pointers(node);
}

hc_tree_opaque_t *
hc_tree_get_first_child(hc_tree_opaque_t *op_father)
{
    hc_tree_t *father = (hc_tree_t*)op_father;

    return((hc_tree_opaque_t*)father->first_child);
}

hc_tree_opaque_t *
hc_tree_get_next_brother(hc_tree_opaque_t *op_node)
{
    hc_tree_t *node = (hc_tree_t*)op_node;

    return((hc_tree_opaque_t*)node->next_brother);
}

hc_tree_opaque_t *
hc_tree_get_father(hc_tree_opaque_t *op_node)
{
    hc_tree_t *node = (hc_tree_t*)op_node;

    return((hc_tree_opaque_t*)node->father);
}
