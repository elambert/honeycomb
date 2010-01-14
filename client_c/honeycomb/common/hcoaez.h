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



/**
 * Honeycomb EZ Object Archive Client Library
 *
 */

#ifndef __HCOAEZ__
#define __HCOAEZ__


#include "hcoa.h"
#include "hcoai.h"
#include "hcclient.h"
#include "hc.h"

#ifdef __cplusplus
#extern "C" {
#endif 

#define STORE_BUFFER_SIZE 76384

/*****************************************/
/* STORE */

/*   Upload data from the supplied source to honeycomb and fill in the supplied  */
/*   System Record with the resulting OID, creation time, size, and data hash. */ 

HONEYCOMB_EXTERN hcerr_t hcoa_store_ez(hc_archive_t *archive, 
				       read_from_data_source data_source_reader, 
				       void *stream,
				       hc_system_record_t *hc_system_record,
				       int32_t *response_code, char *errstr, int errstr_len);


/*   Upload data from file_name to honeycomb and fill in the supplied          */
/*   System Record with the resulting OID, creation time, size, and data hash. */
HONEYCOMB_EXTERN hcerr_t hcoa_store_ez_upload_file (hc_archive_t *archive, 
						    char *file_name, 
						    hc_system_record_t *hc_system_record,
						    int32_t *response_code, char *errstr, int errstr_len);


/*****************************************/
/* RETRIEVE */

/* Download data for the supplied OID from honeycomb to the supplied writer function */
HONEYCOMB_EXTERN hcerr_t hcoa_retrieve_ez(hc_archive_t *archive, 
					  write_to_data_destination data_writer, void *stream,
					  hc_oid *oid,
					  int32_t *response_code, char *errstr, int errstr_len);

/* Download a range of data for the supplied OID from honeycomb to the supplied writer function */
HONEYCOMB_EXTERN hcerr_t hcoa_range_retrieve_ez(hc_archive_t *archive, 
						write_to_data_destination data_writer, void *stream,
						hc_oid *oid,
						hc_long_t firstbyte, hc_long_t lastbyte,
						int32_t *response_code, char *errstr, int errstr_len);

/* Download data for the supplied OID from honeycomb to the supplied file name. */
HONEYCOMB_EXTERN hcerr_t hcoa_retrieve_ez_download_file (hc_archive_t *archive,
							 char *file_name, 
							 hc_oid *oid,
							 int32_t *response_code, char *errstr, int errstr_len);




/*****************************************/
/* DELETE */

/* Delete the object with the specified OID from honeycomb. */
HONEYCOMB_EXTERN hcerr_t hcoa_delete_ez(hc_archive_t *archive, hc_oid *oid, 
					int32_t *response_code, char *errstr, int errstr_len);



/*****************************************/
/* COMPLIANCE */

HONEYCOMB_EXTERN hcerr_t hcoa_set_retention_date_ez (hc_archive_t *archive,
                                                     hc_oid *oid,
                                                     time_t timestamp,
					                                 int32_t *response_code, 
                                                     char *errstr, 
                                                     int errstr_len);

HONEYCOMB_EXTERN hcerr_t hcoa_get_retention_date_ez (hc_archive_t *archive,
                                                     hc_oid *oid,
                                                     time_t *timestamp,
					                                 int32_t *response_code, 
                                                     char *errstr, 
                                                     int errstr_len);

HONEYCOMB_EXTERN hcerr_t hcoa_add_hold_ez (hc_archive_t *archive,
                                           hc_oid *oid,
                                           char* tag,
					                       int32_t *response_code, 
                                           char *errstr, 
                                           int errstr_len);

HONEYCOMB_EXTERN hcerr_t hcoa_del_hold_ez (hc_archive_t *archive,
                                           hc_oid *oid,
                                           char* tag,
                                           int32_t *response_code, 
                                           char *errstr, 
                                           int errstr_len);

HONEYCOMB_EXTERN hcerr_t hcoa_get_date_ez (hc_archive_t *archive,
                                           time_t *timestamp,
                                           int32_t *reponse_code,
                                           char *errstr,
                                           int errsr_len);
#ifdef __cplusplus
}	/* End #extern "C" scope */
#endif

#endif	/* __HCOAEZ__ */
