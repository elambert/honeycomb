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



#include <string.h>
#include <stdlib.h>
#include <strings.h>

#include "merger.h"

typedef struct list {
    db_record_t record;
    db_handle_t *handle;
    struct list *next;
} list_t;

static list_t *_list = NULL;

static void
insert(list_t *elem)
{
    list_t *cur = _list;
    list_t *prev = NULL;

    if (_list == NULL) {
        elem->next = NULL;
        _list = elem;
        return;
    }
    
    while ((cur) && (compare_records(&cur->record, &elem->record) <= 0)) {
        prev = cur;
        cur = prev->next;
    }

    if (!prev) {
        // First element
        elem->next = _list;
        _list = elem;
        return;
    }
    
    elem->next = cur;
    prev->next = elem;
}

int
merger_init(db_handle_t *handles,
            int nb_nodes)
{
    int err;
    list_t *elem;
    int i;

    for (i=0; i<nb_nodes; i++) {
        elem = (list_t*)malloc(sizeof(list_t));
        if (!elem) {
            fprintf(stderr, "malloc failed\n");
            return(1);
        }
        elem->handle = handles+i;
        elem->next = NULL;
        err = db_get_next(elem->handle, &elem->record);
        switch (err) {
        case 0:
            // EOF of database ???
            free(elem);
            break;

        case 1:
            // Got an element
            insert(elem);
            break;

        default:
            // Error
            free(elem);
            return(1);
        }
    }

    return(0);
}

void
merger_destroy()
{
    list_t *elem = _list;
    list_t *next;
    
    while (elem) {
        next = elem->next;
        free(elem);
        elem = next;
    }
    _list = NULL;
}

int
merger_next(db_record_t *record)
{
    int nb_read;
    list_t *head = _list;

    if (!head) {
        return(0);
    }

    bcopy(&head->record, record, sizeof(db_record_t));
    _list = head->next;

    // Recycle list
    nb_read = db_get_next(head->handle, &head->record);
    switch (nb_read) {
    case 0:
        // End of database
        free(head);
        break;

    case 1:
        // Got a new result
        insert(head);
        break;
        
    default:
        // Error
        fprintf(stderr, "Failed to get the next OID from the database\n");
        free(head);
        return(-1);
    }
    
    return(1);
}

void
merger_dump()
{
    list_t *elem = _list;

    printf("------ MERGER DUMP ------\n");
    while (elem) {
        printf("%s - %d - %d\n",
               elem->record.oid,
               elem->record.map,
               elem->record.fragment);
        elem = elem->next;
    }
    printf("---------- END ----------\n\n");
}
