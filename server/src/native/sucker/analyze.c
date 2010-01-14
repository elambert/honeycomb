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



#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "database.h"
#include "merger.h"

#define NB_FRAGMENTS 7
#define NB_PARITY_FRAGMENTS 2

static void
usage()
{
    printf("Usage: analyze -d dir -n nb_nodes\n\
\t-d dir\t\tdirectory containing the DBs\n\
\t-n nb_nodes\tnumber of nodes to analyze\n\
");
}

typedef struct {
    int valid;
    char node;
    char disk;
} fragment_t;

static int
analyze(db_handle_t *handles,
        int nb_nodes)
{
    int err;

    db_record_t record;
    char reference[OID_LENGTH+1];
    fragment_t frags[NB_FRAGMENTS];
    int count;
    int bad_object_detected = 0;
    int nb_valid_objects = 0;
    int nb_bad_objects = 0;
    int nb_bad_frags = 0;
    int nb_lost_objects = 0;
    int nb_duplicate_fragments = 0;
    int move_on;
    
    err = merger_init(handles, nb_nodes);
    if (err) {
        fprintf(stderr, "merger_init failed\n");
        return;
    }
    
    err = merger_next(&record);

    while (err == 1) {
        strncpy(reference, record.oid,OID_LENGTH);
        bad_object_detected = 0;
        move_on = 0;

        for (count=0; count<NB_FRAGMENTS; count++) {
            if ((err == 1) && (!strncmp(reference, record.oid,OID_LENGTH)) && (record.fragment <= count)) {
                if (record.fragment < count) {
                    printf("%s : duplicate fragment %d: %d:%d %d:%d\n",
                           record.oid, record.fragment,
                           frags[record.fragment].node, frags[record.fragment].disk,
                           record.node, record.disk);
                    nb_duplicate_fragments++;
                    count--;
                } else {
                    frags[count].valid = 1;
                    frags[count].node = record.node;
                    frags[count].disk = record.disk;
                }
                err = merger_next(&record);
                if ((err==1) && (count == NB_FRAGMENTS-1) && (!strcmp(reference, record.oid))) {
                    count--;
                }
            } else {
                // We jumped over the next OID
                bad_object_detected = 1;
                frags[count].valid = 0;
            }
        }

        if (bad_object_detected) {
	    nb_bad_frags = 0;
	    printf("%s : ", reference);
            for (count=0; count<NB_FRAGMENTS; count++) {
                if (frags[count].valid) {
                    printf("%d:%d ", frags[count].node, frags[count].disk);
                } else {
		    nb_bad_frags++;
                    printf("XXX:X ");
                }
            }
            printf(" = %d bad", nb_bad_frags);
	    if(nb_bad_frags > NB_PARITY_FRAGMENTS) {
	        printf(" DATA LOSS\n");
	        nb_lost_objects++;
	    } else {
	        printf("\n");
	    }
            nb_bad_objects++;
        } else {
            nb_valid_objects++;
        }
    }
    
    merger_destroy();
    
    printf("\nValid objects:\t\t%d\n\
Incomplete objects:\t%d\n\
Lost objects:\t%d\n\
Duplicate fragments:\t%d\n",
           nb_valid_objects, nb_bad_objects, nb_lost_objects, nb_duplicate_fragments);
    return nb_lost_objects;
}

int
main(int argc,
     char *argv[])
{
    int nb_nodes = -1;
    char *home = NULL;
    char c;
    db_handle_t *handles = NULL;
    int i, j;
    int err;
    char db_filename[32];
    int nb_lost_objects = 0;

    while ((c=getopt(argc, argv, "d:n:")) != EOF) {
        switch (c) {
        case 'd':
            home = optarg;
            break;

        case 'n':
            nb_nodes = atoi(optarg);
            break;

        default:
            usage();
            return(1);
        }
    }

    if (!home) {
        fprintf(stderr, "You have to specify a DB directory\n");
        usage();
        return(1);
    }

    if (nb_nodes == -1) {
        fprintf(stderr, "Invalid number of nodes\n");
        usage();
        return(1);
    }

    handles = (db_handle_t*)malloc(nb_nodes*sizeof(db_handle_t));
    if (!handles) {
        fprintf(stderr, "malloc failed\n");
        return(1);
    }

    j=0;
    for (i=0; i<nb_nodes; i++) {
        snprintf(db_filename, sizeof(db_filename), "hcb%d.db",
                 i+101);
        err = database_init(handles+j, home, db_filename, 0);
        if (err) {
            fprintf(stderr, "Failed to open the db for hcb%d [%d]\n",
                    i+101, err);

        } else {
            j++;
        }
    }

    nb_lost_objects = analyze(handles, j);

    for (i=0; i<j; i++) {
        database_close(handles+i);
    }
    free(handles);
    handles = NULL;
    
    return(nb_lost_objects);
}
