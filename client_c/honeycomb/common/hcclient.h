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



#ifndef __HCCLIENT__
#define __HCCLIENT__

#include <time.h>

#include "hc.h"

#ifdef __cplusplus
extern "C" {
#endif 

/* hc nvoa typedefs */

typedef enum hc_types_{
  HC_UNKNOWN_TYPE = -1,
  HC_BOGUS_TYPE = 0,
  HC_STRING_TYPE = 1,
  HC_LONG_TYPE = 2, 
  HC_DOUBLE_TYPE = 3,
  HC_BYTE_TYPE = 4,
  HC_CHAR_TYPE = 5,
  HC_BINARY_TYPE = 6,
  HC_DATE_TYPE = 7,
  HC_TIME_TYPE = 8,
  HC_TIMESTAMP_TYPE = 9,
  HC_OBJECTID_TYPE = 10
} hc_type_t;

typedef struct hc_value_ {
  hc_type_t	     hcv_type;
  union {
    hc_long_t	     hcv_long;
    hc_string_t	     hcv_string;
    hc_double_t	     hcv_double;
    hc_byte_t	     hcv_byte;
    hc_bytearray_t   hcv_bytearray;
    struct tm        hcv_tm;
    struct timespec  hcv_timespec;
  } hcv;
} hc_value_t;
#define	HC_EMPTY_VALUE_INIT {0,{0}}

/* schema -- describes the data map.   Downloaded from the server
   periodically. */
typedef void hc_schema_t;

/* Session */
/* Structure describing the connection from one thread to one
   Honeycomb silo. */ 
 typedef void hc_session_t; 


/* Name-Value Record. */
/* structure describing the dynamically allocated metadata map */
typedef void hc_nvr_t;

/* Query */
/* structure to hold query (prepared statement) */
typedef void hc_pstmt_t;

/* Query ResultSet */
/* structure to fetch results from a Query */
typedef void hc_query_result_set_t;

/* UniqueValues ResultSet */
/* structure to fetch results from a UniqueValues query */
typedef void hc_unique_values_result_set_t;

/*****************************************/
/* SESSION */

HONEYCOMB_EXTERN hcerr_t hc_session_create_ez (char *host, int port, hc_session_t **sessionp);
HONEYCOMB_EXTERN hcerr_t hc_session_free (hc_session_t *session);
HONEYCOMB_EXTERN hcerr_t hc_session_get_schema (hc_session_t *session, hc_schema_t **schemap);
HONEYCOMB_EXTERN hcerr_t hc_session_get_status (hc_session_t *session, int32_t *responsep,char **errstrp);
HONEYCOMB_EXTERN hcerr_t hc_session_get_host (hc_session_t *session, char **hostp, int *portp);
HONEYCOMB_EXTERN hcerr_t hc_session_get_archive (hc_session_t *session, hc_archive_t **archivep);
HONEYCOMB_EXTERN hcerr_t hc_session_get_platform_result(hc_session_t *session, int32_t *connect_errnop, int32_t *platform_resultp);

/*****************************************/
/* MISCELLANEOUS */

HONEYCOMB_EXTERN char *hc_decode_hc_type(hc_type_t type);
HONEYCOMB_EXTERN hcerr_t hc_schema_get_length(hc_schema_t *schema, char *name, int *length);

/*****************************************/
/* STORE */

HONEYCOMB_EXTERN hcerr_t hc_store_both_ez(hc_session_t *session,
					  read_from_data_source data_source_reader, void *cookie,
					  hc_nvr_t *nvr,
					  hc_system_record_t *system_record);

HONEYCOMB_EXTERN hcerr_t hc_store_metadata_ez(hc_session_t *session,
					      hc_oid *oid,
					      hc_nvr_t *nvr,
					      hc_system_record_t *system_record);

/*****************************************/
/* RETRIEVE */

HONEYCOMB_EXTERN hcerr_t hc_retrieve_metadata_ez (hc_session_t *session,
						  hc_oid *oid, 
						  hc_nvr_t **nvrpp);

HONEYCOMB_EXTERN hcerr_t hc_retrieve_ez(hc_session_t *session,
                                        write_to_data_destination data_writer, void *cookie,
                                        hc_oid *oid);

HONEYCOMB_EXTERN hcerr_t hc_range_retrieve_ez(hc_session_t *session,
					      write_to_data_destination data_writer, void *cookie,
					      hc_oid *oid,
					      hc_long_t firstbyte, hc_long_t lastbyte);

/*****************************************/
/* QUERY - Return OID */

HONEYCOMB_EXTERN hcerr_t hc_pstmt_create(hc_session_t *session, const hc_string_t query, hc_pstmt_t **ptr);
HONEYCOMB_EXTERN hcerr_t hc_pstmt_free(hc_pstmt_t *pstmt);
HONEYCOMB_EXTERN hcerr_t hc_pstmt_set_string(hc_pstmt_t *pstmt, int which, char *str);
HONEYCOMB_EXTERN hcerr_t hc_pstmt_set_char(hc_pstmt_t *pstmt, int which, char *str);
HONEYCOMB_EXTERN hcerr_t hc_pstmt_set_double(hc_pstmt_t *pstmt, int which, double value);
HONEYCOMB_EXTERN hcerr_t hc_pstmt_set_long(hc_pstmt_t *pstmt, int which, hc_long_t value);
HONEYCOMB_EXTERN hcerr_t hc_pstmt_set_date(hc_pstmt_t *pstmt, int which, struct tm *value);
HONEYCOMB_EXTERN hcerr_t hc_pstmt_set_timestamp(hc_pstmt_t *pstmt, int which, struct timespec *value);
HONEYCOMB_EXTERN hcerr_t hc_pstmt_set_time(hc_pstmt_t *pstmt, int which, time_t value);
HONEYCOMB_EXTERN hcerr_t hc_pstmt_set_binary(hc_pstmt_t *pstmt, int which, unsigned char *value, int len);

HONEYCOMB_EXTERN hcerr_t hc_query_ez(hc_session_t *session,
				     hc_string_t query,
                                     hc_string_t selects[],
                                     int n_selects,
                                     int max_records,
				     hc_query_result_set_t **rsetp);

HONEYCOMB_EXTERN hcerr_t hc_pstmt_query_ez(hc_pstmt_t *pstmt,
                                           hc_string_t selects[],
                                           int n_selects,
                                           int max_records,
                                           hc_query_result_set_t **rsetp);

HONEYCOMB_EXTERN hcerr_t hc_qrs_next_ez(hc_query_result_set_t *rset, 
                                        hc_oid *oid, 
                                        hc_nvr_t **nvrp, 
                                        int *finishedp);

HONEYCOMB_EXTERN hcerr_t hc_qrs_is_query_complete(hc_query_result_set_t *rset,
						  int *completep);

HONEYCOMB_EXTERN hcerr_t hc_qrs_get_query_integrity_time(hc_query_result_set_t *rset,
						  hc_long_t *query_time);

HONEYCOMB_EXTERN hcerr_t hc_qrs_free(hc_query_result_set_t *rsetp);

HONEYCOMB_EXTERN hcerr_t hc_query_holds_ez (hc_session_t *session,
					    hc_string_t query,
                                            int max_recs,
					    hc_query_result_set_t **rsetp);

/*****************************************/
/* DELETE */
 
HONEYCOMB_EXTERN hcerr_t hc_delete_ez(hc_session_t *session,
				      hc_oid *oid);

/*****************************************/
/* SCHEMA */

HONEYCOMB_EXTERN hcerr_t hc_schema_get_type(hc_schema_t *schema, char *name, hc_type_t *rettype);
HONEYCOMB_EXTERN hcerr_t hc_schema_get_count(hc_schema_t *hsp, hc_long_t *countp);
HONEYCOMB_EXTERN hcerr_t hc_schema_get_type_at_index(hc_schema_t *hsp, hc_long_t index, char **namep, hc_type_t *typep);

/*****************************************/
/* CHECK_INDEXED */
HONEYCOMB_EXTERN hcerr_t hc_check_indexed_ez(hc_session_t *session, 
					     hc_oid *oid,
					     int *resultp);

/*****************************************/
/* NAME-VALUE RECORD */

HONEYCOMB_EXTERN hcerr_t hc_nvr_create(hc_session_t *session, hc_long_t initsize, hc_nvr_t **retnvr);
HONEYCOMB_EXTERN hcerr_t hc_nvr_free(hc_nvr_t *nvr);
/* Building a name-value record */
HONEYCOMB_EXTERN hcerr_t hc_nvr_add_value(hc_nvr_t *nvr, char *name, hc_value_t value);
HONEYCOMB_EXTERN hcerr_t hc_nvr_add_string(hc_nvr_t *nvr, char *name, hc_string_t value);
HONEYCOMB_EXTERN hcerr_t hc_nvr_add_char(hc_nvr_t *nvr, char *name, hc_string_t value);
HONEYCOMB_EXTERN hcerr_t hc_nvr_add_long(hc_nvr_t *nvr, char *name, hc_long_t value);
HONEYCOMB_EXTERN hcerr_t hc_nvr_add_double(hc_nvr_t *nvr, char *name, hc_double_t value);
HONEYCOMB_EXTERN hcerr_t hc_nvr_add_date(hc_nvr_t *nvr, char *name, struct tm *value);
HONEYCOMB_EXTERN hcerr_t hc_nvr_add_timestamp(hc_nvr_t *nvr, char *name, struct timespec *value);
HONEYCOMB_EXTERN hcerr_t hc_nvr_add_time(hc_nvr_t *nvr, char *name, time_t value);
HONEYCOMB_EXTERN hcerr_t hc_nvr_add_binary(hc_nvr_t *nvr, char *name, int size, unsigned char *bytes);
/* Iterating through a name-value record */
HONEYCOMB_EXTERN hcerr_t hc_nvr_get_count(hc_nvr_t *nvr, hc_long_t *retcount);
HONEYCOMB_EXTERN hcerr_t hc_nvr_get_value_at_index(hc_nvr_t *nvr, hc_long_t index, char **namep, hc_value_t *valuep);
/* Examining a name-value record, by name */
HONEYCOMB_EXTERN hcerr_t hc_nvr_get_string(hc_nvr_t *nvr, char *name, hc_string_t *valuep);
HONEYCOMB_EXTERN hcerr_t hc_nvr_get_long(hc_nvr_t *nvr, char *name, hc_long_t *valuep);
HONEYCOMB_EXTERN hcerr_t hc_nvr_get_double(hc_nvr_t *nvr, char *name, hc_double_t *valuep);
HONEYCOMB_EXTERN hcerr_t hc_nvr_get_date(hc_nvr_t *nvr, char *name, struct tm *valuep);
HONEYCOMB_EXTERN hcerr_t hc_nvr_get_time(hc_nvr_t *nvr, char *name, time_t *valuep);
HONEYCOMB_EXTERN hcerr_t hc_nvr_get_timestamp(hc_nvr_t *nvr, char *name, struct timespec *valuep);
HONEYCOMB_EXTERN hcerr_t hc_nvr_get_binary(hc_nvr_t *nvr, char *name, int *len, unsigned char **bytes);
/* Convenience methods for dealing with string arrays instead of name-value structures */
HONEYCOMB_EXTERN hcerr_t hc_nvr_add_from_string(hc_nvr_t *nvr, char *name, char *value);
HONEYCOMB_EXTERN hcerr_t hc_nvr_create_from_string_arrays(hc_session_t *session, hc_nvr_t **nvrp, 
							  char **names, char **values, hc_long_t nitems);
HONEYCOMB_EXTERN hcerr_t hc_nvr_convert_to_string_arrays(hc_nvr_t *nvr, 
							 char ***namesp, char ***valuesp, int *nitemsp);

#ifdef __cplusplus
}	/* End #extern "C" scope */
#endif

#endif	/* __HCCLIENT__ */
