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



#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <string.h>
#include <assert.h>
#include <sys/stat.h>

#define	COMPILING_HONEYCOMB
#include "hcoai.h"
#include "hcoaez.h"
#include "hcinternals.h"

#define STORE_BUFFER_SIZE 76384


hcerr_t hcoa_store_ez(hc_archive_t *archive, 
		      read_from_data_source data_source_reader, 
		      void *stream,
		      hc_system_record_t *hc_system_record,
		      int32_t *response_code, 
		      char *errstr, 
		      int errstr_len) {

  hcoa_store_handle_t *handle;
  hcerr_t res = -1;
  hc_long_t chunk_size_bytes = 1024 * 1024; /* 1 MB */
  hc_long_t window_size_chunks = 9999;         /* max non-committed in-flight chunks*/
  int done = FALSE;

  HC_DEBUG(("+++hcoa_store_ez"));
  res = hcoa_store_object_create((void **)&handle, archive, 
				 data_source_reader, stream, 
				 chunk_size_bytes,window_size_chunks);
  if (res != HCERR_OK) {
    HC_ERR_LOG(("hcoa_store_ez: Failed to create store: %d\n", res));
    return res;
  }

  while (!done) {

    if ((res = hcoa_io_worker(&handle->base,&done)) != HCERR_OK) {
      HC_ERR_LOG(("hcoa_store_ez: hcoa_io_worker saw error %d, done=%d", 
		res, done));
      break;
    }
    if (done == FALSE) {
      hcoa_select_all(archive);
    }
  }	/* End While (!done) */
    
  /* close the handle and print out the system record fields */
  res = hcoa_store_close(handle, response_code, errstr, errstr_len, 
			 hc_system_record);
  if(res == HCERR_OK) {
    HC_DEBUG(("Stored to %s\n"
	      "Size: " LL_FORMAT "\n"
	      "Creation Time: " LL_FORMAT "\n", 
	      hc_system_record->oid,
	      hc_system_record->size,
	      hc_system_record->creation_time));
  }else{
    HC_ERR_LOG(("Store failed\n"
	      "Response code: %d\n"
	      "Error: %s\n", 
	      *response_code, errstr));
  }
  return res;
}



/* Helper function for hcoa_store_ez_upload_file */
static long read_from_file_data_source (void *stream, char *buff, long n){
  long nbytes = 

  nbytes = read((int) stream, buff, n);
  
  return nbytes;
}

/*   Upload data from file_name to honeycomb and fill in the supplied          */
/*   System Record with the resulting OID, creation time, size, and data hash. */
hcerr_t hcoa_store_ez_upload_file (hc_archive_t *archive, 
				   char *file_name, 
				   hc_system_record_t *hc_system_record,
				   int32_t *response_code, char *errstr, int errstr_len){

  int infile = -1;
  hcerr_t err = -1;

  HC_DEBUG(("++++hcoa_store_ez_upload_file(file_name=%s)",file_name));

  if ((infile = open(file_name, O_RDONLY | O_BINARY)) == -1) {
    HC_ERR_LOG(("Failed to open data file '%s' for store: %d\n", file_name, infile));
    return HCERR_COULD_NOT_OPEN_FILE;
  }
  err = hcoa_store_ez (archive, 
		       &read_from_file_data_source, (void *)infile,
		       hc_system_record,
		       response_code, errstr, errstr_len);
  close(infile);
  return err;
}



#define RETRIEVE_BUFFER_SIZE 16384

/* Download data for the supplied OID from honeycomb to the supplied writer function */

hcerr_t hcoa_retrieve_ez(hc_archive_t *archive, 
			 write_to_data_destination data_writer, void *stream,
			 hc_oid *oid, 
			 int32_t *response_code, char *errstr, int errstr_len) {

  hcoa_retrieve_handle_t *handle;
  hcerr_t res;
  int done = FALSE;
  
  HC_DEBUG(("+++hcoa_retrieve_ez"));
  res = hcoa_retrieve_object_create((void **) &handle, archive,  *oid,
				    data_writer,stream);
  if (res != HCERR_OK) {
    HC_ERR_LOG(("hcoa_retrieve_ez: Failed to create retrieve: %d\n", res));
    return res;
  }
  
  
  while(done == FALSE) {  
    
    res = hcoa_io_worker(handle,&done);
    if (res != HCERR_OK) {
      break;
    }
    if (!done) {
      hcoa_select_all(archive);
    }
  }
  
  res = hcoa_retrieve_close(handle, response_code, errstr, errstr_len);
  return res;
}

hcerr_t hcoa_range_retrieve_ez(hc_archive_t *archive, 
			       write_to_data_destination data_writer, void *stream,
			       hc_oid *oid, 
			       hc_long_t firstbyte, hc_long_t lastbyte,
			       int32_t *response_code, char *errstr, int errstr_len) {

  hcoa_retrieve_handle_t *handle;
  hcerr_t res;
  int done = FALSE;

  /* check range args before creating handle */
  if (firstbyte < 0  ||  firstbyte > lastbyte  ||  lastbyte < -1) {
    HC_ERR_LOG(("hcoa_range_retrieve_ez: invalid range parameter"));
    return HCERR_ILLEGAL_ARGUMENT;
  }

  res = hcoa_range_retrieve_create((void **) &handle, archive,  *oid,
				   firstbyte, lastbyte,
				   data_writer,stream);
  if (res != HCERR_OK) {
    HC_ERR_LOG(("hcoa_range_retrieve_ez: Failed to create retrieve: %d", res));
    return res;
  }
  
  
  while(done == FALSE) {  
    
    res = hcoa_io_worker(handle,&done);
    if (res != HCERR_OK) {
      break;
    }
    if (!done) {
      hcoa_select_all(archive);
    }
  }
  
  res = hcoa_retrieve_close(handle, response_code, errstr, errstr_len);
  return res;
}


/* Helper function for hcoa_retrieve_ez_download_file */

static long write_downloaded_data(void *stream, char *buff, long n){
  int pos = 0;

  HC_DEBUG_IO_DETAIL(("write_downloaded_data(n=%ld) to stream %ld",n,(long)stream));

  while (pos < n){
    int i = write ((int) stream, buff + pos, n - pos);
    if (i < 0)
      return i;
    if (i == 0)
      break;
    pos += i;
  }
  return pos;
}

/* Download data for the supplied OID from honeycomb to the supplied file name. */
hcerr_t hcoa_retrieve_ez_download_file (hc_archive_t *archive, 
					char *file_name,
					hc_oid *oid,
					int32_t *response_code, char *errstr, int errstr_len) {
  hcerr_t res = HCERR_OK;
  int outfile;

  outfile = open(file_name, O_CREAT|O_WRONLY|O_LARGEFILE|O_TRUNC|O_BINARY, S_IREAD|S_IWRITE);
  HC_DEBUG(("+++hcoa_retrieve_ez_download_file(file=%s,oid=%s)=>outfile=%ld",
	    file_name,(char *)oid,(long)outfile));

  if (outfile == -1) {
    HC_ERR_LOG(("Failed to open data file '%s' for writing %d.\n", file_name,outfile));
    return HCERR_COULD_NOT_OPEN_FILE;
  }
  res = hcoa_retrieve_ez (archive, 
			  &write_downloaded_data, (void *) outfile,
			  oid, 
			  response_code, errstr, errstr_len);
  close(outfile);
  return res;
}


hcerr_t hcoa_delete_ez(hc_archive_t *archive, hc_oid *oid, 
		       int32_t *response_code, char *errstr, int errstr_len) {
  hcoa_delete_handle_t *handle;
  hcerr_t res;
  int done = FALSE;
  char buf[RETRIEVE_BUFFER_SIZE];
  
  HC_DEBUG(("+++hcoa_delete_ez(oid=%s)",oid));
  if((res = hcoa_delete_object_create((void **) &handle, archive,  *oid,
				      buf, sizeof (buf))) != HCERR_OK) {
    HC_ERR_LOG(("Failed to create delete handle for %s: %d", oid, res));
    return res;
  }
  
  while(!done) {  
    res = hcoa_io_worker(handle, &done);
    if (res != HCERR_OK) {
      break;
    }
    if (!done) {
      hcoa_select_all(archive);
    }
  }
  
  return hcoa_delete_close(handle, response_code, errstr, errstr_len);
}

/********************************************************
 *
 * Bug 6554027 - hide retention features
 *
 *******************************************************/
/*
hcerr_t hcoa_set_retention_date_ez(hc_archive_t *archive, hc_oid *oid, 
    time_t timestamp, int32_t *response_code, char *errstr, int errstr_len) {
  hcoa_compliance_handle_t *handle;
  hcerr_t res;
  int done = FALSE;
  char buf[RETRIEVE_BUFFER_SIZE];
  
  HC_DEBUG(("+++hcoa_set_retention_ez(oid=%s)",oid));
  if((res = hcoa_set_retention_create((void **) &handle, archive,  *oid,
				      timestamp, buf, sizeof (buf))) != HCERR_OK) {
    HC_ERR_LOG(("Failed to create retention handle handle for %s: %d", oid, res));
    return res;
  }
  
  while(!done) {  
    res = hcoa_io_worker(handle, &done);
    if (res != HCERR_OK) {
      break;
    }
    if (!done) {
      hcoa_select_all(archive);
    }
  }
  
  return hcoa_compliance_close(handle, response_code, errstr, errstr_len);
}

hcerr_t hcoa_get_retention_date_ez (hc_archive_t *archive, hc_oid *oid,
                                    time_t *timestamp, 
                                    int32_t *response_code, 
                                    char *errstr, int errstr_len) {
  hcoa_compliance_handle_t *handle;
  hcerr_t res;
  int done = FALSE;
  char buf[RETRIEVE_BUFFER_SIZE];
  
  HC_DEBUG(("+++hcoa_get_retention_ez(oid=%s)",oid));
  if((res = hcoa_get_retention_create((void **) &handle, archive,  *oid,
				      timestamp, buf, sizeof (buf))) != HCERR_OK) {
    HC_ERR_LOG(("Failed to create retention handle handle for %s: %d", oid, res));
    return res;
  }
  
  while(!done) {  
    res = hcoa_io_worker(handle, &done);
    if (res != HCERR_OK) {
      break;
    }
    if (!done) {
      hcoa_select_all(archive);
    }
  }
  
  return hcoa_compliance_close(handle, response_code, errstr, errstr_len);
}

hcerr_t hcoa_add_hold_ez (hc_archive_t *archive, hc_oid *oid, char *tag,
    int32_t *response_code, char *errstr, int errstr_len) {
  hcoa_compliance_handle_t *handle;
  hcerr_t res;
  int done = FALSE;
  char buf[RETRIEVE_BUFFER_SIZE];
  
  HC_DEBUG(("+++hcoa_add_hold_ez(oid=%s)",oid));
  if((res = hcoa_add_hold_create((void **) &handle, archive,  *oid,
				      tag, buf, sizeof (buf))) != HCERR_OK) {
    HC_ERR_LOG(("Failed to create legal hold handle handle for %s: %d", oid, res));
    return res;
  }
  
  while(!done) {  
    res = hcoa_io_worker(handle, &done);
    if (res != HCERR_OK) {
      break;
    }
    if (!done) {
      hcoa_select_all(archive);
    }
  }
  
  return hcoa_compliance_close(handle, response_code, errstr, errstr_len);
}

hcerr_t hcoa_del_hold_ez (hc_archive_t *archive, hc_oid *oid, char *tag,
    int32_t *response_code, char *errstr, int errstr_len) {
  hcoa_compliance_handle_t *handle;
  hcerr_t res;
  int done = FALSE;
  char buf[RETRIEVE_BUFFER_SIZE];
  
  HC_DEBUG(("+++hcoa_add_hold_ez(oid=%s)",oid));
  if((res = hcoa_del_hold_create((void **) &handle, archive,  *oid,
				      tag, buf, sizeof (buf))) != HCERR_OK) {
    HC_ERR_LOG(("Failed to create legal hold handle handle for %s: %d", oid, res));
    return res;
  }
  
  while(!done) {  
    res = hcoa_io_worker(handle, &done);
    if (res != HCERR_OK) {
      break;
    }
    if (!done) {
      hcoa_select_all(archive);
    }
  }
  
  return hcoa_compliance_close(handle, response_code, errstr, errstr_len);
}

hcerr_t hcoa_get_date_ez (hc_archive_t *archive,
                          time_t *timestamp,
                          int32_t *response_code,
                          char *errstr,
                          int errstr_len) {
  hcoa_compliance_handle_t *handle;
  hcerr_t res;
  int done = FALSE;
  char buf[RETRIEVE_BUFFER_SIZE];
  
  if((res = hcoa_get_date_create((void **) &handle, archive, timestamp,
				       buf, sizeof (buf))) != HCERR_OK) {
    HC_ERR_LOG(("Failed to create get date handle for %d", res));
    return res;
  }
  
  while(!done) {  
    res = hcoa_io_worker(handle, &done);
    if (res != HCERR_OK) {
      break;
    }
    if (!done) {
      hcoa_select_all(archive);
    }
  }
  
  return hcoa_compliance_close(handle, response_code, errstr, errstr_len); 
}
*/
