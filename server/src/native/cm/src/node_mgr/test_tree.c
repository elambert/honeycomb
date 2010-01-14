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
 * This program is the development test for the tree implementation.
 */

#include <stdio.h>
#include <trace.h>

#include "tree.h"

/* #define PRINT_TREE */

static int test_nb = 0;

static void
print_tree(hc_tree_opaque_t *tree,
           int depth)
{
    hc_tree_opaque_t *node;
    char padding[12];
    int i;

    if (!tree) {
        printf("\n");
        return;
    }

    for (i=0; i<2*depth; i++) {
        padding[i] = ' ';
    }
    padding[2*depth] = '\0';

    printf("%d ", (int)hc_tree_get_datas(tree));
    print_tree(hc_tree_get_first_child(tree), depth+1);

    node = hc_tree_get_next_brother(tree);
    while (node) {
        printf("%s%d", padding, (int)hc_tree_get_datas(node));
        print_tree(hc_tree_get_first_child(node), depth+1);
        node = hc_tree_get_next_brother(node);
    }
}

static int
check(hc_tree_opaque_t *tree,
      char *comparison)
{
    hc_tree_opaque_t *node;
    hc_tree_opaque_t *last_father;
    int i;

    test_nb++;

    last_father = tree;
    node = tree;
    i = 0;

#ifdef PRINT_TREE
    print_tree(tree, 0);
#endif

    while (last_father) {
        while (node) {
            if ((int)hc_tree_get_datas(node) != comparison[i]-'1'+1) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "Comparison failed for element %d (%d-%c)",
                         i+1,
                         (int)hc_tree_get_datas(node),
                         comparison[i]);
                return(1);
            }

            ++i;
            node = hc_tree_get_next_brother(node);
        }

        last_father = hc_tree_get_first_child(last_father);
        node = last_father;
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "Test nbr %d passed", test_nb);

    return(0);
}

int
main()
{
    hc_tree_opaque_t *tree1 = NULL;
    hc_tree_opaque_t *tree2 = NULL;
    hc_tree_opaque_t *tree3 = NULL;
    hc_tree_opaque_t *tree4 = NULL;

    cm_openlog("test_tree", CM_TRACE_LEVEL_NOTICE);

    tree1 = hc_tree_allocate();
    tree2 = hc_tree_allocate();
    tree3 = hc_tree_allocate();
    tree4 = hc_tree_allocate();
    if ((!tree1) || (!tree2) || (!tree3) || (!tree4)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_tree_allocate failed");
        cm_closelog();
        return(1);
    }

    hc_tree_set_datas(tree1, (void*)1);
    hc_tree_set_datas(tree2, (void*)2);
    hc_tree_set_datas(tree3, (void*)3);
    hc_tree_set_datas(tree4, (void*)4);

    hc_tree_add_child(tree1, tree2);
    hc_tree_add_child(tree1, tree3);
    hc_tree_add_child(tree3, tree4);
    
    if (check(tree1, "1234")) {
        cm_closelog();
        return(1);
    }

    hc_tree_remove(tree3);
    if (check(tree1, "124")) {
        cm_closelog();
        return(1);
    }

    hc_tree_add_child(tree2, tree3);
    if (check(tree1, "1243")) {
        cm_closelog();
        return(1);
    }

    hc_tree_remove(tree4);
    if (check(tree1, "123")) {
        cm_closelog();
        return(1);
    }

    hc_tree_add_brother(tree2, tree4);
    if (check(tree1, "1243")) {
        cm_closelog();
        return(1);
    }

    hc_tree_remove(tree2);
    if (check(tree1, "143")) {
        cm_closelog();
        return(1);
    }

    hc_tree_add_child(hc_tree_get_father(tree4), tree2);
    if (check(tree1, "1432")) {
        cm_closelog();
        return(1);
    }

    hc_tree_free(tree1);
    hc_tree_free(tree2);
    hc_tree_free(tree3);
    hc_tree_free(tree4);
    
    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "All the tree tests passed");

    cm_closelog();
    return(0);
}
