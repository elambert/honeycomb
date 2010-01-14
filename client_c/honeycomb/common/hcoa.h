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



#ifndef __HCOA__
#define __HCOA__

#include "hc.h"
#include "platform.h"

#ifdef __cplusplus
#extern "C" {
#endif 


/* ARCHIVE */
HONEYCOMB_EXTERN hcerr_t hcoa_archive_init(hc_archive_t **arcp, char *host, int port);
HONEYCOMB_EXTERN hcerr_t hcoa_archive_cleanup(hc_archive_t *arc);


/* Store */

HONEYCOMB_EXTERN hcerr_t hcoa_store_object_create(void **handle, 
						  hc_archive_t *archive,
						  read_from_data_source store_data_source_reader,
						  void *stream,
						  hc_long_t chunk_size_bytes, 
						  hc_long_t window_size_chunks);

HONEYCOMB_EXTERN hcerr_t hcoa_get_last_committed_offset (void *handle,
                                                       hc_long_t *last_committed_offset);

HONEYCOMB_EXTERN hcerr_t hcoa_store_close(void *handle, int32_t *response_code,
                                        char *errstr, hc_string_index_t errstrlen,
                                        hc_system_record_t *sysrec);




/* Retrieve */

/* Initiate a retrieve request, returning a handle */
/* Specify port as -1 for default port */
HONEYCOMB_EXTERN hcerr_t hcoa_retrieve_object_create(void **handle,
						     hc_archive_t *archive,
						     hc_oid oid,
						     write_to_data_destination data_writer, void *stream); 

HONEYCOMB_EXTERN hcerr_t hcoa_range_retrieve_create(void **handle, 
						    hc_archive_t *archive,
						    hc_oid oid, 
						    hc_long_t firstbyte, hc_long_t lastbyte,
						    write_to_data_destination data_writer, void *stream);


/* Turn the crank for retrieve-oriented transfers */
HONEYCOMB_EXTERN hcerr_t hcoa_io_worker(void *handle, int *finishedp);

/* Handle data retrieved using previously created handle. */
HONEYCOMB_EXTERN hcerr_t hcoa_retrieve_close(void *handle, 
					     int32_t *response_code,
					     char *errstr, hc_string_index_t errstrlen);

/* Delete */

HONEYCOMB_EXTERN hcerr_t hcoa_delete_object_create(void **handle,
						   hc_archive_t *archive,
						   hc_oid oid,
						   char *buf, int buflen);

HONEYCOMB_EXTERN hcerr_t hcoa_delete(void *handle, int *finished);

HONEYCOMB_EXTERN hcerr_t hcoa_delete_close(void *handle, 
                                         int32_t *response_code,
                                         char *errstr, hc_string_index_t errstrlen);


/* Compliance Date */

HONEYCOMB_EXTERN hcerr_t hcoa_get_date_create (void** handle,
					                           hc_archive_t *archive, 
                                               time_t *timestamp,
					                           char *buf, int buflen);
                                             
HONEYCOMB_EXTERN hcerr_t hcoa_set_retention_create (void** handle,
					                                hc_archive_t *archive, 
            					                    hc_oid oid,
                                                    time_t timestamp,
					                                char *buf, int buflen);

HONEYCOMB_EXTERN hcerr_t hcoa_get_retention_create (void** handle,
					                                hc_archive_t *archive, 
            					                    hc_oid oid,
                                                    time_t *timestamp,
					                                char *buf, int buflen);
                                             
HONEYCOMB_EXTERN hcerr_t hcoa_add_hold_create (void** handle,
					                           hc_archive_t *archive, 
            					               hc_oid oid,
                                               char *tag,
					                           char *buf, int buflen);

HONEYCOMB_EXTERN hcerr_t hcoa_del_hold_create (void** handle,
					                           hc_archive_t *archive, 
            					               hc_oid oid,
                                               char *tag,
					                           char *buf, int buflen);

HONEYCOMB_EXTERN hcerr_t hcoa_compliance_close(void *handle, 
                        int32_t *response_code,
                        char *errstr, hc_string_index_t errstrlen);

/* Retrieve Config */

HONEYCOMB_EXTERN hcerr_t hcoa_retrieve_config(char *url, char **config); /* TODO */


/* Get Status */

HONEYCOMB_EXTERN hcerr_t hcoa_status(char *address, int *status); /* TODO*/



/* fd_set stuff */

HONEYCOMB_EXTERN hcerr_t hcoa_multi_fdset(hc_archive_t *archive, 
					  fd_set *read_fd_set,
					  fd_set *write_fd_set,
					  fd_set *exc_fd_set,
					  int *max_fdp);

/* global progress checks */

HONEYCOMB_EXTERN hcerr_t hcoa_sessions_in_progress(hc_archive_t *archive, int *num);

#ifdef __cplusplus
}	/* End #extern "C" scope */
#endif

#endif	/* __HCOA__ */
