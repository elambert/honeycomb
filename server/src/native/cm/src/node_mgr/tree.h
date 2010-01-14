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
 * This file contains a generic tree implementation
 */

#ifndef _TREE_H_
#define _TREE_H_

typedef void *hc_tree_opaque_t;

/*
 * hc_tree_allocate returns NULL in case of failure.
 */

hc_tree_opaque_t *
hc_tree_allocate();

/*
 * free does not manage the links with fathers and sieblings.
 *
 * You should call hc_tree_remove first to remove the node from the tree.
 */

void
hc_tree_free(hc_tree_opaque_t *node);

void
hc_tree_set_datas(hc_tree_opaque_t *node,
		  void *datas);

void *
hc_tree_get_datas(hc_tree_opaque_t *node);

void
hc_tree_add_child(hc_tree_opaque_t *father,
		  hc_tree_opaque_t *child);

void
hc_tree_add_brother(hc_tree_opaque_t *node,
		    hc_tree_opaque_t *new_brother);

/*
 * The following routine removes the node from the tree.
 *
 * But it does free the memory. You should then call hc_tree_free on the
 * removed node to make it happen.
 *
 */

void
hc_tree_remove(hc_tree_opaque_t *node);

hc_tree_opaque_t *
hc_tree_get_first_child(hc_tree_opaque_t *father);

hc_tree_opaque_t *
hc_tree_get_next_brother(hc_tree_opaque_t *node);

hc_tree_opaque_t *
hc_tree_get_father(hc_tree_opaque_t *node);

#endif /* _TREE_H_ */
