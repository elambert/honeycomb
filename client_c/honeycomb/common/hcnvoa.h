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



/* Honeycomb Name/Value Object Archive Client Library
 *
 *
 */

#ifndef __HCNVOA__
#define __HCNVOA__

#include "hcclient.h"

#ifdef __cplusplus
#extern "C" {
#endif 

HONEYCOMB_EXTERN hcerr_t hc_store_metadata_create(void **handle, 
                                                  char *buffer,
                                                  hc_long_t buffer_length,
                                                  hc_session_t *session,
                                                  hc_long_t chunk_size_bytes, 
                                                  hc_long_t window_size_chunks, 
                                                  hc_oid oid);

HONEYCOMB_EXTERN hcerr_t hc_add_metadata(void *handle, char *name, char *value);

HONEYCOMB_EXTERN hcerr_t hc_add_metadata_date(void *metadata_handle,
                                              hc_string_t name,
                                              struct tm *value);
HONEYCOMB_EXTERN hcerr_t hc_add_metadata_time(void *metadata_handle,
                                              hc_string_t name,
                                              time_t value);
HONEYCOMB_EXTERN hcerr_t hc_add_metadata_timestamp(void *metadata_handle,
                                                   hc_string_t name,
                                                   struct timespec *value);
HONEYCOMB_EXTERN hcerr_t hc_add_metadata_string(void *handle, char *name, 
                                               char tag, hc_string_t value);
HONEYCOMB_EXTERN hcerr_t hc_add_metadata_long(void *handle, char *name, 
                                               char tag, hc_long_t value);
HONEYCOMB_EXTERN hcerr_t hc_add_metadata_double(void *handle, char *name, 
                                               hc_double_t value);
HONEYCOMB_EXTERN hcerr_t hc_add_metadata_byte(void *handle, char *name, 
                                               hc_byte_t value);
HONEYCOMB_EXTERN hcerr_t hc_add_metadata_bytearray(void *handle, char *name,
                                               char tag, hc_bytearray_t *value);

HONEYCOMB_EXTERN hcerr_t hc_store_metadata_close(void *handle, hc_system_record_t *sysrec);


/*
 * Store Both
 */

HONEYCOMB_EXTERN hcerr_t hc_store_both_create(void **handle, 
                                              char *buffer,
                                              hc_long_t buffer_length,
					      read_from_data_source ext_data_source_reader,
					      void *ext_stream,
                                              hc_session_t *session,
                                              int cellid,
                                              hc_long_t chunk_size_bytes, 
                                              hc_long_t window_size_chunks);



/*
 * Retrieve Metadata
 */

/* create session to retrieve metadata record */
HONEYCOMB_EXTERN hcerr_t hc_retrieve_metadata_create(void **handle, 
                                                     hc_session_t *session,
						     hc_oid oid);
HONEYCOMB_EXTERN hcerr_t hc_retrieve_metadata_close(void *handle, hc_nvr_t **nvrp);





/*
 * Retrieve Schema
 */

HONEYCOMB_EXTERN hcerr_t hc_retrieve_schema_create(void **handle, hc_session_t *session);

HONEYCOMB_EXTERN hcerr_t hc_retrieve_schema (void *handle,
                                             int *finished);

HONEYCOMB_EXTERN hcerr_t hc_retrieve_schema_close(void *handle);


/*
 * Query
 */

HONEYCOMB_EXTERN hcerr_t hc_create_query(void **handle, 
                                         hc_string_t query,
                                         hc_session_t *session,
                                         int32_t results_per_fetch, 
                                         hc_long_t chunk_size_bytes, 
                                         hc_long_t window_size_chunks,
                                         char *cache_id);

HONEYCOMB_EXTERN hcerr_t hc_create_query_pstmt(void **handle, 
                                         hc_pstmt_t *pstmt,
                                         int32_t results_per_fetch, 
                                         hc_long_t chunk_size_bytes, 
                                         hc_long_t window_size_chunks);

HONEYCOMB_EXTERN hcerr_t hc_query_available_items(void *handle, hc_long_t *countp);

HONEYCOMB_EXTERN hcerr_t hc_query_get_integrity_time(void *handle, hc_long_t *timep);

HONEYCOMB_EXTERN hcerr_t hc_retrieve_query(void *handle, 
                                           hc_long_t *results, 
                                           int *finished,
                                           int *skip_select);

HONEYCOMB_EXTERN hcerr_t hc_next_query_result(void *handle, 
                                              hc_oid *oid, hc_nvr_t **nvrp);

HONEYCOMB_EXTERN hcerr_t hc_close_query (void *handle);


HONEYCOMB_EXTERN hcerr_t hc_create_query_plus(void **handle, 
                                              hc_string_t query,
                                              hc_string_t fields[], 
                                              int n_fields,
                                              hc_session_t *session,
                                              int32_t results_per_fetch, 
                                              hc_long_t chunk_size_bytes, 
                                              hc_long_t window_size_chunks);

HONEYCOMB_EXTERN hcerr_t hc_create_query_plus_pstmt(void **handle, 
                                              hc_pstmt_t *pstmt,
                                              hc_string_t fields[], 
                                              int n_fields,
                                              int32_t results_per_fetch, 
                                              hc_long_t chunk_size_bytes, 
                                              hc_long_t window_size_chunks);

HONEYCOMB_EXTERN hcerr_t hc_next_query_plus_result(void *handle, hc_oid *oid, hc_nvr_t **nvrp);


/*
 * Check_Indexed
 */
HONEYCOMB_EXTERN hcerr_t hc_check_indexed_create(void **handle, 
						 hc_session_t *session,
						 hc_oid oid);
HONEYCOMB_EXTERN hcerr_t hc_check_indexed_close(void *handle, int *resultp);

#ifdef __cplusplus
}	/* End #extern "C" scope */
#endif

#endif	/*  __HCNVOA__ */
