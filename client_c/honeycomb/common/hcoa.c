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
#include <limits.h>
#include <sys/types.h>
#include <assert.h>
#include <stdarg.h>
#include <ctype.h>

#define	COMPILING_HONEYCOMB
#include "hc.h"
#include "platform.h"
#include "hcinternals.h"
#include "hcoa.h"
#include "hcoai.h"
#include "multicell.h"

/* Platform collection of all sessions (singleton) */

hc_long_t hc_debug_flags = HC_DEBUG_FLAGS_DEFAULT;

hc_debug_function_t hc_debug = hc_printf;

long hc_low_speed_time = 800L;

static int64_t hc_atoll(const char *p) {
  int64_t n;
  int c, neg = 0;
  unsigned char	*up = (unsigned char *)p;

  if (!isdigit(c = *up)) {
    while (isspace(c))
      c = *++up;
    switch (c) {
    case '-':
      neg++;
      /* FALLTHROUGH */
    case '+':
      c = *++up;
    }
    if (!isdigit(c))
      return (0);
  }
  for (n = '0' - c; isdigit(c = *++up); ) {
    n *= 10; /* two steps to avoid unnecessary overflow */
    n += '0' - c; /* accum neg to avoid surprises at MAX */
  }
  return (neg ? n : -n);
}

/* Assert expected op and state */
hcerr_t hc_is_valid_handle(void *handle, hc_handle_type_t handle_type, 
                           hc_operation_t op, hc_op_state_t state){

  if (handle == NULL){
    return HCERR_NULL_HANDLE;
  }
  else{
    hcoa_base_handle_t *base_handle = &((hcoa_store_handle_t *) handle)->base;

    if (base_handle->magic_number != hc_magic_number){
      HC_ERR_LOG(("INVALID_HANDLE 0x%lx", (long)base_handle));
      return HCERR_INVALID_HANDLE;
    }

    if (handle_type != ANY_HANDLE && 
        (base_handle->handle_type & handle_type) == 0){
      HC_ERR_LOG(("Invalid handle: %s doesn't match %s\n", 
		decode_handle_type(base_handle->handle_type), 
		decode_handle_type(handle_type)));
      HC_ERR_LOG(("%d != %d\n", 
		base_handle->handle_type, handle_type));
      return HCERR_WRONG_HANDLE_FOR_OPERATION;
    }
    if (op != ANY_OP && base_handle->op_type != op){
      HC_ERR_LOG(("Invalid Handle: found %s, expected %s for 0x%lx\n", 
		decode_op(base_handle->op_type), 
		decode_op(op), (long)base_handle));
      return HCERR_WRONG_HANDLE_FOR_OPERATION;
    }
    else if (state != ANY_STATE && base_handle->op_state != state){
      HC_ERR_LOG(("Invalid Handle: %s != %s for 0x%lx\n", 
		decode_state(base_handle->op_state), 
		decode_state(state), (long)base_handle));
      return HCERR_HANDLE_IN_WRONG_STATE_FOR_OPERATION;
    }
    else{
      return HCERR_OK;
    }
  }
}

hcerr_t assert_handle(void *handle, hc_handle_type_t handle_type, 
                           hc_operation_t op, hc_op_state_t state){

  if (handle == NULL){
    return HCERR_NULL_HANDLE;
  }
  else{
    hcoa_base_handle_t *base_handle = &((hcoa_store_handle_t *) handle)->base;

    if (base_handle->magic_number != hc_magic_number){
      HC_ERR_LOG(("INVALID_HANDLE 0x%lx", (long)base_handle));
      assert(base_handle->magic_number == hc_magic_number);
      return HCERR_INVALID_HANDLE;
    }

    if (handle_type != ANY_HANDLE && 
        (base_handle->handle_type & handle_type) == 0){
      HC_ERR_LOG(("%s doesn't match %s\n", 
		decode_handle_type(base_handle->handle_type), 
		decode_handle_type(handle_type)));
      HC_ERR_LOG(("%d != %d\n", 
		base_handle->handle_type, handle_type));
      assert(handle_type == ANY_HANDLE || (base_handle->handle_type & handle_type) != 0);
      return HCERR_WRONG_HANDLE_FOR_OPERATION;
    }
    if (op != ANY_OP && base_handle->op_type != op){
      HC_ERR_LOG(("found %s, expected %s for 0x%lx\n", 
		decode_op(base_handle->op_type), 
		decode_op(op), (long)base_handle));
      assert(op == ANY_OP || base_handle->op_type == op);
      return HCERR_WRONG_HANDLE_FOR_OPERATION;
    }
    else if (state != ANY_STATE && base_handle->op_state != state){
      HC_ERR_LOG(("%s != %s for 0x%lx\n", 
		decode_state(base_handle->op_state), 
		decode_state(state), (long)base_handle));
      assert(state == ANY_STATE || base_handle->op_state != state);
      return HCERR_HANDLE_IN_WRONG_STATE_FOR_OPERATION;
    }
    else{
      return HCERR_OK;
    }
  }
}


static void describe_handle(void *handle){
  hcoa_base_handle_t *base_handle = handle;
  printf ("%s: %s, %s\n", 
	  decode_handle_type(base_handle->handle_type),
          decode_op(base_handle->op_type),
          decode_state(base_handle->op_state));
}

hcerr_t hc_is_valid_oid (hc_oid oid){
  int i = 0;

  if (oid == NULL) {
    HC_ERR_LOG(("Bad NULL oid"));
    return HCERR_INVALID_OID;
  }

  if (strcmp(HC_INVALID_OID, oid) == 0){
    /* This is OK; query and retrieve schema use */
    /* this value because there is no oid involved */
    return HCERR_OK;
  }
  for (i=0; i<OID_HEX_CHRSTR_LENGTH-1; i++){
    if (!(oid[i] >= '0' && oid[i] <= '9') &&
        !(oid[i] >= 'a' && oid[i] <= 'f') &&
        !(oid[i] >= 'A' && oid[i] <= 'F')){
      HC_ERR_LOG(("Bad oid char at index %d: '%c' in oid '%s'", i, oid[i], oid));
      return HCERR_INVALID_OID;
    }
  }
  return HCERR_OK;
}

hcerr_t assert_oid (hc_oid oid){
  int i = 0;

  if (oid == NULL) {
    HC_ERR_LOG(("Bad NULL oid"));
    assert(oid != NULL);
    return HCERR_INVALID_OID;
  }
  if (strcmp(HC_INVALID_OID, oid) == 0){
    /* This is OK; query and retrieve schema use */
    /* this value because there is no oid involved */
    return HCERR_OK;
  }
  for (i=0; i<OID_HEX_CHRSTR_LENGTH-1; i++){
    if (!(oid[i] >= '0' && oid[i] <= '9') &&
        !(oid[i] >= 'a' && oid[i] <= 'f') &&
        !(oid[i] >= 'A' && oid[i] <= 'F')){
      HC_ERR_LOG(("Bad oid char at index %d: '%c' in oid '%s'", i, oid[i], oid));
      assert((oid[i] >= '0' && oid[i] <= '9') ||
	     (oid[i] >= 'a' && oid[i] <= 'f') ||
	     (oid[i] >= 'A' && oid[i] <= 'F'));
      return HCERR_INVALID_OID;
    }
  }
  return HCERR_OK;
}

/* Platform I/O callbacks (context indicates which sesssion I/O is for) */

/* This is called by uploadfn in the platform layer.
   Supply some more data to upload.
   Returning 0 means end-of-file.
   Returning -1 is a convention to cancel the upload.
*/
long hcoa_store_upload_callback(void *context,
				char *buffer, 
				long size) {
  hcoa_store_handle_t *handle = (hcoa_store_handle_t*) context;
  long nbytes;
 
  assert_handle(handle, STORE_HANDLE, ANY_OP, ANY_STATE);
  assert(size > 0);
  assert(handle->store_data_source_reader);

  /* Curl really should not call us again after we return EOF. */
  assert(handle->write_completed == 0);	

  /* 
   * The windowing logic to deal with write-failover belongs here.  If
   * we turn it on, then we would keep a side-buffer of data we have
   * read from the data source that has not yet been ack'd by the
   * server.  Keep track of how much data we are sending.  The read
   * callback should keep track of the acks we receive.  Given curl's
   * limitations, there is not really a way to throttle back the
   * sending side to avoid swamping the server. Every time curl asks
   * for data we must give it data.  
   */

  /* cf hc_store_metadata_upload_callback */
  nbytes = (*handle->store_data_source_reader)(handle->store_stream, buffer, size);

  if (nbytes > 0) {
    HC_DEBUG_DETAIL(("hcoa_store_upload_callback: uploading %ld bytes from data source",
		     nbytes));
    handle->last_sent_byte += nbytes;
    if (handle->failsafe) {
      handle->last_sent_chunk = (hc_long_t) (handle->last_sent_byte / handle->chunk_size_bytes);
    }

  } else if (nbytes < 0) {
    HC_ERR_LOG(("hcoa_store_upload_callback: client asked to cancel upload"));
  } else {
    HC_DEBUG_DETAIL(("hcoa_store_upload_callback: saw eof"));
    handle->write_completed = 1;
  }
  return nbytes;
}

/* helper function for hc_store_read_callback */
static long parse_acks_maybe_xml(hcoa_store_handle_t *handle, char *buf, hc_long_t buflen) {

  hc_int_archive_t *archive = handle->base.archive;
  int i;
  hc_long_t parsed = 0;
  hcerr_t res;

  assert_handle(handle, ANY_HANDLE, ANY_OP, ANY_STATE);

  /* There is a case where ALL of the data has been sent and
     acknowledged before the read_from_data_source function 
     tells us that the upload is complete, and we tell the 
     server end of file, and then the server sends the 
     system record, at which point this function is called again. */
  if(handle->write_completed && 
     handle->last_committed_chunk == handle->last_sent_chunk &&
     !handle->all_chunks_committed) {
    HC_DEBUG_DETAIL(("all chunks committed, case 2"));
    handle->all_chunks_committed = 1;
  }
  
  i = 0;
  if (!handle->all_chunks_committed) {

    for (; i < buflen;  i++) {

      /* Handle each received character and handle exceptions */
      if (IS_HEX_DIGIT(buf[i])) {
	handle->last_commit_ack[handle->last_commit_ack_len] = buf[i];
	handle->last_commit_ack_len++;
	if (handle->last_commit_ack_len >= sizeof(handle->last_commit_ack)-1) {
	  HC_ERR_LOG(("parse_acks_maybe_xml: ERROR: too many ack digits received, abandoning transfer!"
		    " last_commit_ack_len=%d, commit_ack=(%.*s)",
		    handle->last_commit_ack_len,
		    (int)handle->last_commit_ack_len,handle->last_commit_ack));
	  return -1;
	}	/* End If (too many hex digits?) */
      } else if (buf[i] == '\n') {
	long just_committed_chunk = -1;

	/* The server has acknowledged receipt of another chunk */
	handle->last_commit_ack[handle->last_commit_ack_len] = '\0';
	just_committed_chunk = strtol(handle->last_commit_ack, (char **)NULL, 16);
	/* reset handle's buffer for next ack */
	handle->last_commit_ack_len = 0;

	/* Was this the ack for the last chunk? */
	HC_DEBUG_DETAIL(("parse_acks_maybe_xml: committed up to %ld", just_committed_chunk));

	if (just_committed_chunk > handle->last_committed_chunk) {
	  handle->last_committed_chunk = just_committed_chunk;
	  if (handle->write_completed && 
	      handle->last_committed_chunk == handle->last_sent_chunk) {

	    HC_DEBUG_DETAIL(("all chunks committed, normal case"));

	    handle->all_chunks_committed = 1;
	    break;	/* now go read xml bits */
	  }	/* End If (handle->write_completed && ...) */

	}	/* End If (just_committed_chunk > handle->last_committed_chunk) */

      } else if (buf[i] == '\r') {
	/* CR is allowed after LF at the end of each ack */
	if (handle->last_commit_ack_len != 0) {
	  HC_ERR_LOG(("parse_acks_maybe_xml: received unexpected <CR> character! out of synch"));
	  return -1;
	}

      } else if (buf[i] == '<') {
	  HC_ERR_LOG(("parse_acks_maybe_xml: '<' character before last_chunk_committed: out of synch"));
	  /* Maybe we should accept the system_record at this point???
	   * For now we would prefer to find the bugs!
	   */
	  return -1;

      } else {
	  HC_ERR_LOG(("parse_acks_maybe_xml: received illegal character '%c'! out of synch!", buf[i]));
	  return -1;
      }	/* End If/Else/Elseif on buf[i] */

    }	/* End For ( i = 0; i < buflen;  i++) */

    /* Finished reading all the bytes from the input */
    if (! handle->all_chunks_committed) {
      /* The transfer is not yet complete -- keep looking next time. */
      return buflen;
    }

  }	/* End If (!handle->all_chunks_committed) */

  /* Now the parsing of acks is complete, so start to read the
     system record. */
  assert(handle->all_chunks_committed);

  if (i < buflen) {
    HC_DEBUG_IO_DETAIL(("parsing header: %.*s",(int)buflen-i,&buf[i]));
    // MULTICELL
    HC_DEBUG_IO_DETAIL(("parse_mcell %d\n", archive->get_multicell_config));
    if (archive->get_multicell_config) {
      res = xml_parse(archive->parser, &buf[i], buflen-i, &parsed);
      if (res != HCERR_OK) 
        return -1;
      if (parsed == 0)
        return buflen;
      archive->get_multicell_config = 0;
      i += parsed;
      if (i == buflen)
        return buflen;
    }
    res = xml_parse(handle->parser, &buf[i], buflen-i, &parsed);
    if (res != HCERR_OK) 
      return -1;
    if (parsed) {
      /* Validate received system record */
      if (hc_is_valid_oid(handle->hc_system_record.oid) != HCERR_OK ||
	  handle->hc_system_record.size < 0) {
	hc_system_record_t *sysrec = &handle->hc_system_record;

	HC_ERR_LOG(("ERROR: parse_acks_maybe_xml: xfer completed, but system record is not valid!\n"
		  "    oid=%s (valid=%s)\n"
		  "    data_size="LL_FORMAT", Ctime="LL_FORMAT", DelTime="LL_FORMAT"\n "
		  "    Shred=0x%x, Alg=%s, Digest=%s",
		  sysrec->oid, hc_decode_hcerr(hc_is_valid_oid(sysrec->oid)),
		  sysrec->size, sysrec->creation_time, sysrec->deleted_time,
		  (int)sysrec->shredMode, sysrec->digest_algo, sysrec->data_digest));
      } else {
	HC_DEBUG_DETAIL(("parse_acks_maybe_xml:  saw end of system_record.  xfer complete"));
	handle->system_record_complete = 1;
      }
    }
  }
  return buflen;
}
/* This is called by downloadfn in the platform layer during store
   operations.  Process more data downloaded to us by the HTTP
   socket.  This is where we implement most of logic to keep track of
   how much data has been sent and retrieved, for write-failover.
   Must return the number of bytes processed = the number of bytes
   they asked us to process, or else the connection is terminated.
*/
long hcoa_store_read_callback(void *context,
			      char *readbuf, 
			      long size) {
  long nbytes;
  hcoa_store_handle_t *handle = (hcoa_store_handle_t*) context;
  int32_t response_code;

  get_session_response_code(handle->base.session_ptr, &response_code);
  HC_DEBUG_IO_DETAIL(("hcoa_store_read_callback"));
  if(response_code == SERVER_SUCCESS  || response_code == 0) {
    nbytes = parse_acks_maybe_xml(handle, readbuf, size);
  } else {
    nbytes = get_error((hcoa_base_handle_t *) &handle->base, readbuf, size);
  }
  HC_DEBUG_IO_DETAIL(("hcoa_store_read_callback: size=%ld response_code=%d: result=%ld",
	    size, response_code, nbytes));
  return nbytes;
}

/* This is called by downloadfn in the platform layer during all kinds of
   retrieve operation, including queries.
   Process more data downloaded to us by the HTTP socket.
   Must return the number of bytes processed = the number of bytes
   they asked us to process, or else the connection is terminated.
*/
long hcoa_retrieve_callback(void *context,
			    char *buffer, 
			    long size) {
  long nbytes;
  hcoa_retrieve_handle_t *handle = (hcoa_retrieve_handle_t*) context;
  hc_int_archive_t *archive = handle->base.archive;
  int32_t response_code;
  hc_long_t parsed_to = 0;
  hcerr_t err;
  long size_left = 0;
  
  assert_handle(handle, RETRIEVE_HANDLE, ANY_OP, ANY_STATE);
  // MULTICELL
  if (archive->get_multicell_config) {
    HC_DEBUG(("hcoa_retrieve_callback ->get_multicell_config\n"));
    err = xml_parse(archive->parser, buffer, size, &parsed_to);
    if (err != HCERR_OK)
      return -1;
    if (parsed_to == 0)
      return size;
    parsed_to++;
    archive->get_multicell_config = 0;
    if (parsed_to >= size) {
        // This should not happen as can't parse more data than exists in buffer
        if (parsed_to > size) {
            HC_ERR_LOG(("hcoa_retrieve_callback: Parsed more data than in buffer. Size: %d  Parsed: %lld",
                size, parsed_to));
        }
      return size;
    }
  }

  get_session_response_code(handle->base.session_ptr,&response_code);
  HC_DEBUG_IO_DETAIL(("hcoa_retrieve_callback(size=%ld) - response_code %d",
		      (long)size,response_code));
  
  if (response_code != SERVER_SUCCESS && response_code != 0) {
    /* A non-success HTTP response code indicates that the body 
     * describes an internal server error -- don't try to parse it, 
     * accumulate error info in the handle's error buffer.
     */ 
    if (size > 0) {
        size_left = size-parsed_to;
    }
    nbytes = get_error(&handle->base, buffer+parsed_to, size_left);

  } else if (size == 0) {
    /* Cancel rest of transfer */
    nbytes = -1;
  } else if (handle->read_data_writer == NULL) {
    /* To support delete ops with minimum disruption, if there is no
       data_writer for this handle, then save incoming data into the
       handle's read buffer. Only the delete op should have a read
       buffer. */
    nbytes = hcoa_retrieve_to_buffer_callback((void *)handle,buffer+parsed_to,size-parsed_to);
  } else {
    /* Give the data to the MD level or to the user to process
       further. (cf hc_retrieve_metadata_download_callback, or
       hcoa_retrieve_to_buffer_callback, for example) */
    nbytes = (*handle->read_data_writer)(handle->read_stream, buffer+parsed_to, size-parsed_to);
  }	/* End If/Else (response_code == SERVER_SUCCESS) */

  if (nbytes < 0) {
    HC_ERR_LOG(("hcoa_retrieve_callback: canceling rest of transfer!"));

  }

  return nbytes+parsed_to;
}

long hcoa_retrieve_to_buffer_callback(void *context, char *buff, long nbytes) {
  hcoa_base_handle_t *base = (hcoa_base_handle_t *)context;
  long read;
  int32_t response_code;

  assert_handle(base, ANY_HANDLE, ANY_OP, ANY_STATE);
  assert(base->rbuf != NULL && base->rend != NULL);

  /* If receiving an error, save the data in our error buffer */
  get_session_response_code(base->session_ptr, &response_code);
  if(response_code != SERVER_SUCCESS && response_code != 0) {
    nbytes = get_error(base, base->rbuf,base->rend - base->rbuf);
    /* Set up to read anew */
    base->rend = base->rbuf;
    return nbytes;
  }

  /* calculate if we can read it all out or not, and if not, then how much */
  read = base->rbuflen - 
    (base->rend - base->rbuf); /* room left in read base hdl buf */
  
  if(nbytes <= read) {
    read = nbytes;
  }
  HC_DEBUG_IO_DETAIL(("hcoa_retrieve_to_buffer_callback:"
		   " downloaded %ld bytes, cur_read=%ld", 
		   read,(base->rend - base->rbuf)+read));

  if (read > 0) {
    memcpy(base->rend, buff, read);
    /* update read end ptr in base handle */
    base->rend += read; 
  }
  return read;
}  

static long platform_download_callback(void *context, char *buffer, long size) {
  long nbytes;

  nbytes = hcoa_retrieve_to_buffer_callback(context,buffer,size);
  return nbytes;
}


static long platform_upload_callback(void *context, char *buffer, long size) {
  hcoa_base_handle_t *base = (hcoa_base_handle_t*) context;
  long nbytes;

  assert_handle(base, ANY_HANDLE, ANY_OP, ANY_STATE);
  
  nbytes = hcoa_store_upload_from_buffer(context,buffer,size);

  return nbytes;
}

static hcerr_t retrieve_mcell_start_element_handler(char *element_name, void *data, char **names, char **values, int n){

  hc_int_archive_t *archive = (hc_int_archive_t *) data;
  hcerr_t err;
  int i;

  if (!strcmp(element_name, "Multicell-Descriptor")) {
    int64_t version_major = -999;
    int64_t version_minor = -999;

    for (i=0; i<n; i++) {
      if (!strcmp(names[i], "version-major")) {
        if (!hc_isnum(values[i])) {
          HC_ERR_LOG(("Multicell version-major not a number\n"));
          return HCERR_XML_PARSE_ERROR;
        }
        version_major = hc_atoll(values[i]);
      } else if (!strcmp(names[i], "version-minor")) {
        if (!hc_isnum(values[i])) {
          HC_ERR_LOG(("Multicell version-minor not a number\n"));
          return HCERR_XML_PARSE_ERROR;
        }
        version_minor = hc_atoll(values[i]);
      }
    }
    if (version_major == -999  ||  version_minor == -999) {
      HC_ERR_LOG(("Multicell version_major or version-minor missing\n"));
      return HCERR_XML_PARSE_ERROR;
    }
    if (archive->silo_parse != NULL) {
      err = free_multi_cell(archive->silo_parse);
      if (err != HCERR_OK)
        return err;
    }
    err = init_multi_cell(&archive->silo_parse);
    if (err != HCERR_OK)
      return err;
    archive->silo_parse->major_version = version_major;
    archive->silo_parse->minor_version = version_minor;

  } else if (!strcmp(element_name, "Cell")) {

    cell_t *cell;

    if (archive->silo_parse == NULL)
      return HCERR_XML_PARSE_ERROR;

    if (archive->silo_parse->silo_size >= MAX_CELLS) {
      HC_ERR_LOG(("Multicell cell too many cells (%d)\n", 
                                          archive->silo_parse->silo_size+1));
      return HCERR_XML_PARSE_ERROR;
    }
    // TODO - check that each field is filled. For now assuming
    // that java clnt has solved protocol bugs

    ALLOCATOR(cell, sizeof(cell_t));
    memset(cell, 0, sizeof(cell_t));

    for (i=0; i<n; i++) {
      if (!strcmp(names[i], "id")) {
        if (!hc_isnum(values[i])) {
          HC_ERR_LOG(("Multicell cell id not a number\n"));
          return HCERR_XML_PARSE_ERROR;
        }
        cell->id = atoi(values[i]);
      } else if (!strcmp(names[i], "data-vip")) {
        char *p = strchr(values[i], ':');
        if (p == NULL) {
          err = hc_copy_string(values[i], &cell->addr);
          if (err != HCERR_OK)
            return err;
          cell->port = DEFAULT_PORT;
        } else {
          *p = '\0';
          err = hc_copy_string(values[i], &cell->addr);
          if (err != HCERR_OK)
            return err;
          p++;
          if (!hc_isnum(p)) {
            HC_ERR_LOG(("Multicell cell port not a number\n"));
            return HCERR_XML_PARSE_ERROR;
          }
          cell->port = atoi(p);
        }
      } else if (!strcmp(names[i], "total-capacity")) {
        if (!hc_isnum(values[i])) {
          HC_ERR_LOG(("Multicell cell total-capacity not a number\n"));
          return HCERR_XML_PARSE_ERROR;
        }
        cell->max_capacity = hc_atoll(values[i]);
      } else if (!strcmp(names[i], "used-capacity")) {
        if (!hc_isnum(values[i])) {
          HC_ERR_LOG(("Multicell cell used-capacity not a number\n"));
          return HCERR_XML_PARSE_ERROR;
        }
        cell->used_capacity = hc_atoll(values[i]);
      }
    }
    archive->silo_parse->cells[archive->silo_parse->silo_size] = cell;
    archive->silo_parse->silo_size++;
  } // skip Rule for now
  return HCERR_OK;
}
 
static hcerr_t retrieve_mcell_end_element_handler(char *element_name, void *data){
  hc_int_archive_t *archive = (hc_int_archive_t *) data;
  hcerr_t err;

  if (archive->silo_parse == NULL)
    return HCERR_XML_PARSE_ERROR;
  if (!strcmp(element_name, "Multicell-Descriptor")) {
    if (archive->silo != NULL) {
      err = free_multi_cell(archive->silo);
      if (err != HCERR_OK)
        return err;
    }
    archive->silo = archive->silo_parse;
    archive->silo_parse = NULL;
    //print_silo(archive->silo);
  }
  return HCERR_OK;
}

long platform_header_callback(void *context, char *buffer, long size) {
  hc_int_archive_t *arc = (hc_int_archive_t *) context;
  hcerr_t res;

  assert(arc != NULL);

  // MULTICELL
  if (strncmp(buffer, EXPECT_MULTICELL_CFG_HEADER, 
                      strlen(EXPECT_MULTICELL_CFG_HEADER)) == 0) {
    int prev = arc->get_multicell_config;
    arc->get_multicell_config = 1;
    HC_DEBUG(("platform_header_callback: mcell %d -> 1\n", prev));
    // init parser
    res = xml_create(&retrieve_mcell_start_element_handler,
                     &retrieve_mcell_end_element_handler,
                     (void *) arc,
                     &(arc->parser),
                     allocator, 
                     deallocator);
    if (res != HCERR_OK) {
      return res;
    }
  } 
  return size; /* just pretend we swallowed it all */
}

int hc_isnum(const char *s) {
  int i;
  for (i=0; s[i] != '\0'; i++)
    if (!isdigit(s[i]))
      return 0;
  return 1;
}

hc_long_t hc_atol(char *s){
  int len = strlen(s);
  int i = 0;
  hc_long_t l = 0;
  for (i = 0; i < len; i++){
    l = l * 10;
    l += s[i] - '0';
  }
  return l;
}


/* XML callbacks */

static hcerr_t hcoa_sysmd_start_element_handler (char *element_name, void *data, 
						 char **names, char **values, int n){
  /* Should we bother to keep track of proper nesting for well-formed XML? */
  int i = 0;

  if (strcmp(element_name, HC_OA_ATTRIBUTE_TAG) == 0){
    char *name  = NULL;
    char *value  = NULL;
    hc_system_record_t *system_record = (hc_system_record_t *) data;
    
    for (i = 0; i < n; i++){
      
      /* ---> CASE SENSITIVITY??? */
      if (strcmp(*(names + i), "name") == 0){
	name = *(values + i);
      }
      else if (strcmp(*(names + i), "value") == 0){
	value = *(values + i);
      }
    }


    if (name != NULL && value != NULL){
      
      HC_DEBUG_IO_DETAIL(("hcoa_sysmd_start_element_handler:"
			  " name=%s, value=%s", 
			  name,value));
      if (strcmp(name, HC_OA_OBJECT_ID_KEY) == 0){
	strncpy((char*)(system_record->oid), value, sizeof(hc_oid));
      }
      else if (strcmp(name, HC_OA_OBJECT_SIZE_KEY) == 0){
	system_record->size = hc_atol(value);
      }
      else if (strcmp(name, HC_OA_OBJECT_CTIME_KEY) == 0){
	system_record->creation_time = hc_atol(value);
      }
      else if (strcmp(name, HC_OA_OBJECT_DIGEST_ALG_KEY) == 0){
	strncpy(system_record->digest_algo, value, sizeof(hc_digest_algo));
      }
      else if (strcmp(name, HC_OA_OBJECT_DIGEST_KEY) == 0){
	strcpy(system_record->data_digest, value);
      }
      else if (strcmp(name, HC_OA_OBJECT_LINK_KEY) == 0){
	/* Ignore this key: it has an invalid value and will go away */
      }
      else if (strcmp(name, HC_OA_OBJECT_QUERY_READY_KEY) == 0){
	system_record->is_indexed = hc_atol(value);
	HC_DEBUG_IO_DETAIL(("hcoa_sysmd_start_element_handler:"
			    " is_indexed: %d",
			    system_record->is_indexed));
      }
      else{
	/* ignore other attributes */
	HC_ERR_LOG(("hcoa_sysmd_start_element_handler: failed to match any of %s %s %s %s %s %s",
        	       HC_OA_OBJECT_ID_KEY, HC_OA_OBJECT_SIZE_KEY,
        	       HC_OA_OBJECT_CTIME_KEY, HC_OA_OBJECT_DIGEST_ALG_KEY,
        	       HC_OA_OBJECT_DIGEST_KEY,HC_OA_OBJECT_QUERY_READY_KEY));
	HC_ERR_LOG((" %s=\"%s\"\n", name, value));
      }
    }
  }
  return HCERR_OK;
}


static hcerr_t hcoa_sysmd_end_element_handler (char *name, void *data){
  return HCERR_OK;
}



/* Library setup, teardown, and utilities */


/* Customer code can call these functions to allocate and free
   memory.   That way if we or they are using a Debug Heap, neither of
   us gets confused about the other's memory! */

void * hc_alloc(size_t size) {
  if (allocator == NULL) hc_init(malloc, free, realloc);
  return allocator(size);
}
void hc_free(void *p) {
  if (allocator == NULL) hc_init(malloc, free, realloc);
  deallocator(p);
}
void * hc_realloc(void *p, size_t size) {
  if (allocator == NULL) hc_init(malloc, free, realloc);
  return reallocator(p, size);
}

hcerr_t hc_init(allocator_t a, deallocator_t d, reallocator_t r) {
  allocator = a;
  deallocator = d;
  reallocator = r;
  return platform_global_init();
}

hcerr_t hc_cleanup(void) {
  /* [???] - Should this also clean up all active sessions, or just
     leave them to crash and burn? */
  hcerr_t res;

  res = platform_global_cleanup();
  return res;
}

hcerr_t
hc_set_debug_flags(hc_long_t debug) {

  HC_LOG(("hc_set_debug_flags: setting debug flags to "LL_FORMAT" (0x"LLX_FORMAT")", debug, debug));
  hc_debug_flags = debug;
  return HCERR_OK;
}

void hc_printf(const char *fmt, ...) {
    va_list args;

    fflush(stdout);
    va_start(args, fmt);
    vfprintf(stdout, fmt, args);
    va_end(args);
    printf("\n");
    fflush(stdout);
}


hcerr_t hc_set_debug_function(hc_debug_function_t fn) {
  hc_debug = fn;
  HC_DEBUG_DETAIL(("hc_set_debug_function:  set debug function to 0x%lx",(long)fn));

  return HCERR_OK;
}

hcerr_t hc_set_global_parameter(hc_option option, ...) {
  va_list param;
  hcerr_t res = HCERR_OK;

  va_start(param, option);

  switch (option) {
  case HCOPT_DEBUG_FLAGS:
    {
      hc_long_t debugflags = va_arg(param, hc_long_t);

      hc_set_debug_flags(debugflags);
    }
    break;

  case HCOPT_DEBUG_FUNCTION:
    {
      hc_debug_function_t debugfn = va_arg(param, hc_debug_function_t);

      hc_set_debug_function(debugfn);
    }
    break;

  case HCOPT_LOW_SPEED_TIME:
    {
      long timeout = va_arg(param, long);

      hc_low_speed_time = timeout;
      HC_DEBUG_DETAIL(("hc_set_global_parameter: st hc_low_speed_time to %ld",
		hc_low_speed_time));
    }
    break;
  default:
    HC_ERR_LOG(("hc_set_global_parameter:  unknown option %d",option));
    res = HCERR_ILLEGAL_ARGUMENT;
  }
  return res;
}

hcerr_t hcoa_archive_init(hc_archive_t **arcp, char *host, int port) {
  hc_int_archive_t *arc;
  hcerr_t res;

  if (allocator == NULL) hc_init(malloc, free, realloc);

  arc = (hc_int_archive_t *)allocator(sizeof(hc_int_archive_t));
  if (arc == NULL) 
    return HCERR_OOM;
  memset(arc, 0, sizeof(*arc));
  arc->magic_number = hc_archive_magic_number;

  if (port == -1) {
    port = DEFAULT_PORT;
  }
  arc->default_cell.port = port;

  res = hc_copy_string(host, &arc->default_cell.addr);
  if (res != HCERR_OK) {
    goto cleanup;
  }
  
  res = platform_session_init(&arc->hcoa_http_sessionset_ptr);
  if (res != HCERR_OK) {
    goto cleanup;
  }

  // init silo/hive so it will have major & minor versions
  res = init_multi_cell(&arc->silo);
  if (res != HCERR_OK) {
    goto cleanup;
  }

  /* Everything is hunky-dory */
  *arcp = (hc_archive_t *)arc;
  return HCERR_OK;

cleanup:
    HC_ERR_LOG(("hcoa_archive_init: failed, error=%d",res));
    hcoa_archive_cleanup((hc_archive_t *)arc);
    return res;
}


hcerr_t hcoa_archive_cleanup(hc_archive_t *pubarc) {
  hcerr_t res;
  hc_int_archive_t *arc = (hc_int_archive_t *)pubarc;

  assert(arc != NULL && arc->hcoa_http_sessionset_ptr != NULL);
  res = platform_session_cleanup(arc->hcoa_http_sessionset_ptr); 

  if (arc->parser) {
      xml_cleanup(arc->parser);
  }
  if (arc->silo) {
      free_multi_cell(arc->silo);
  }
  if (arc->silo_parse) {
      free_multi_cell(arc->silo_parse);
  }
  if (arc->default_cell.addr) {
    deallocator(arc->default_cell.addr);
  }
  memset((char *)arc, 0, sizeof(*arc));
  deallocator(arc);
  
  return res;
}


hcerr_t hcoa_get_platform_name(char *name, int len) {
  return platform_get_platform_name(name, len);
}


/* Error reading function */

long get_error(hcoa_base_handle_t *h, char *buf, long buflen) {
  hcoa_base_handle_t *handle = (hcoa_base_handle_t *)h;
  hc_string_index_t roomleft =  (ERRSTR_MAX_LEN-1) - handle->errstrlen;
  hc_string_index_t copylen = buflen;
  
  if(roomleft < buflen) {
    copylen = roomleft;
  }
  
  if (copylen > 0) {
    memcpy(handle->errstr + handle->errstrlen, buf, copylen);
    handle->errstrlen += copylen;
  }
  HC_DEBUG_DETAIL(("get_error(%ld bytes added, total %u)",buflen,handle->errstrlen));
  
  return copylen;
}


hcerr_t copy_errstr(hcoa_base_handle_t *h, char *buf, hc_string_index_t len) {
  hcoa_base_handle_t *handle = (hcoa_base_handle_t *)h;
  hc_string_index_t copylen = handle->errstrlen;

  assert_handle(h, ANY_HANDLE, ANY_OP, ANY_STATE);
  assert(len >= 1);

  if((len-1) < copylen) {
    copylen = (len-1);
  }

  HC_DEBUG_DETAIL(("copy_errstr:  error='%.*s'",copylen,handle->errstr));
  memcpy(buf, handle->errstr, copylen);
  buf[copylen] = '\0';
  if (copylen > 0) {
    HC_ERR_LOG(("Received error string(len=%d): %.*s",copylen,copylen,handle->errstr));
  }
  return HCERR_OK;
}

hcerr_t init_base(hcoa_base_handle_t *handle, hc_archive_t *archive, hc_handle_type_t handle_type, hc_operation_t op){

  hc_int_archive_t *arc = (hc_int_archive_t *)archive;
  validate_archive(arc);

  if (handle == NULL)
    return HCERR_NULL_HANDLE;
  handle->archive = arc;
  handle->rbuf = handle->rend = NULL;
  handle->rbuflen = 0;
  handle->wbuf = handle->wend = NULL;
  handle->session_ptr = NULL;
  handle->wbuflen = 0;
  handle->rcompleted = 0;
  handle->wcompleted = 0;
  handle->errstrlen = 0;
  handle->op_type = op;
  handle->handle_type = handle_type;
  handle->op_state = OP_UNDEF;
  handle->magic_number = hc_magic_number;
  return HCERR_OK;
}


/* Retrieve */


/* Base function used by all retrieves-- including queries */
hcerr_t hcoa_retrieve_create(hcoa_retrieve_handle_t *handle, 
			     hc_archive_t *archive,
			     char *retrieve_url, hc_oid link_oid,
			     write_to_data_destination data_writer, void *stream) {
  hcerr_t res = HCERR_OK;    

  validate_oid(link_oid);
  
  /* initialize some handle structures */
  res = init_base(&handle->base, archive, RETRIEVE_HANDLE, RETRIEVE_DATA);
  if (res != HCERR_OK) return res;

  memcpy((void *) handle->base.link_oid, (const void *) link_oid,
         OID_HEX_CHRSTR_LENGTH);

  strcpy(handle->base.url, retrieve_url);

  handle->read_data_writer = data_writer;
  handle->read_stream = stream;

  return HCERR_OK;
}

/* basic retrieve w/o an oid - used by get date */
hcerr_t hcoa_retrieve_create_nooid(hcoa_retrieve_handle_t *handle, 
			     hc_archive_t *archive,
			     char *retrieve_url,
			     write_to_data_destination data_writer, void *stream) {
  hcerr_t res = HCERR_OK;    

  //validate_oid(link_oid);
  
  /* initialize some handle structures */
  res = init_base(&handle->base, archive, RETRIEVE_HANDLE, RETRIEVE_DATA);
  if (res != HCERR_OK) return res;

  //memcpy((void *) handle->base.link_oid, (const void *) link_oid,
   //      OID_HEX_CHRSTR_LENGTH);

  strcpy(handle->base.url, retrieve_url);

  handle->read_data_writer = data_writer;
  handle->read_stream = stream;

  return HCERR_OK;
}

hcerr_t hcoa_range_retrieve_add_header(hcoa_base_handle_t *base) {
  hcoa_range_retrieve_handle_t *handle;
  char range_hdr[MAX_HTTP_HEADER_LEN];

  assert_handle(base,RETRIEVE_HANDLE, RANGE_RETRIEVE_DATA, ANY_STATE);


  handle = (hcoa_range_retrieve_handle_t *)base;

  HC_DEBUG_DETAIL(("hcoa_range_retrieve_add_header(0x%lx) firstbyte "LL_FORMAT" lastbyte "LL_FORMAT,
	    (long)base,handle->firstbyte,handle->lastbyte));

  if (handle->firstbyte != RANGE_RETRIEVE_UNKNOWN_SIZE 
      && handle->lastbyte != RANGE_RETRIEVE_UNKNOWN_SIZE) {
    if (handle->firstbyte < 0 || handle->lastbyte < handle->firstbyte){
      return HCERR_ILLEGAL_ARGUMENT;
    }
    sprintf(range_hdr, LL_FORMAT "%s" LL_FORMAT, 
	    handle->firstbyte, RANGE_SEPARATOR, handle->lastbyte);
  } else if(handle->firstbyte != RANGE_RETRIEVE_UNKNOWN_SIZE) {
    sprintf(range_hdr, LL_FORMAT "%s", 
	    handle->firstbyte, RANGE_SEPARATOR);
  } else {
    sprintf(range_hdr, "%s" LL_FORMAT, 
	    RANGE_SEPARATOR, handle->lastbyte);
  }
  return add_request_header(handle->retrieve_handle.base.session_ptr, RANGE_HEADER, range_hdr);
}

  /* do this after calling add_request_headers for any headers */
hcerr_t hcoa_retrieve_start_transmit(hcoa_base_handle_t *base) {

  return hcoa_retrieve_create_init_http(base);
}



static hcerr_t add_headers(hcoa_base_handle_t *handle){
  char multicell[1024];
  require_ok(add_request_header(handle->session_ptr, "Expect", ""));
  require_ok(add_request_header(handle->session_ptr, "Content-Length", ""));
  require_ok(add_request_header(handle->session_ptr, "Accept", ""));
  require_ok(add_request_header(handle->session_ptr, "Content-Type", ""));
  // The User-Agent string is used by ProtocolHandler.sendError to determine
  // the client type. If this string is changed sendError may need to be modified.
  require_ok(add_request_header(handle->session_ptr, "User-Agent", "Honeycomb C API Client 1.1"));
  require_ok(add_request_header(handle->session_ptr, "Honeycomb-Version", "1"));
  require_ok(add_request_header(handle->session_ptr, "Honeycomb-Minor-Version", "1"));
  if (snprintf(multicell, sizeof(multicell), "%lld.%lld", handle->archive->silo->major_version, handle->archive->silo->minor_version) == sizeof(multicell))
    return HCERR_BUFFER_OVERFLOW;
  require_ok(add_request_header(handle->session_ptr, MULTICELL_CFG_VERSION, multicell));
  require_ok(add_request_header(handle->session_ptr, "Transfer-Encoding", "chunked"));
  return HCERR_OK;
}

hcerr_t hcoa_retrieve_create_init_http(hcoa_base_handle_t *base) {
  hcerr_t res = HCERR_OK;

  HC_DEBUG_DETAIL(("hcoa_retrieve_create_init_http : Starting RETRIEVE Data operation"));
  
  /* construct the get session using the http platform */

  base->archive->get_multicell_config = 0;

  if ((handle_type(base) & QUERY_HANDLE) != 0){
    // put query or cookie in body of POST
    res = create_post_session(base->archive->hcoa_http_sessionset_ptr, 
                              &(base->session_ptr),
                              base->url, 
                              platform_header_callback, 
                              base->archive,
                              hcoa_retrieve_callback,
                              base,
                              platform_upload_callback, 
                              base,
                              TRUE);
    if (res != HCERR_OK)
      return res;
  }
  else{
    res = create_get_session(base->archive->hcoa_http_sessionset_ptr, 
                             &(base->session_ptr),
                             base->url, 
                             platform_header_callback, 
                             base->archive,
                             hcoa_retrieve_callback,
                             base,
                             platform_upload_callback, 
                             base);
    if (res != HCERR_OK) {
      HC_ERR_LOG(("hcoa_retrieve_create_init_http: create_get_session returned error code %d %s", 
                res,hc_decode_hcerr(res)));
      return res;
    }
  }
  res = add_headers(base);
  if (res != HCERR_OK) {
    HC_ERR_LOG(("hcoa_retrieve_create_init_http: add_headers returned error code %d %s",
                res,hc_decode_hcerr(res)));
    return res;
  }


  if (HC_DEBUG_ERR_IF)
    require_ok(add_request_header(base->session_ptr, HC_REQUEST_STACK_TRACE_HEADER, "true"));

  /* Are additional headers are needed? - 
     dispatch on handle type or op type or debug type*/
  if (op_type(base) == RANGE_RETRIEVE_DATA) {
    res = hcoa_range_retrieve_add_header(base);
  }

  return res;
}
 

/* Retrieve Object */


hcerr_t hcoa_internal_retrieve_object_create(hcoa_retrieve_handle_t *handle, 
					     hc_archive_t *archive, 
					     hc_oid oid,
					     write_to_data_destination data_writer, void *stream) {
  cell_id_t cellId;
  hc_int_archive_t *arc = (hc_int_archive_t *)archive;
  cell_t *cell;
  hcerr_t res;
  char retrieve_url[MAX_HTTP_URL_LEN];

  validate_oid(oid);

  /* construct a retrieve url to use for the http get */

  HC_DEBUG(("Retrieving %s", oid));
  //%%MULTICELL
  cellId = get_cell_id(oid);
  cell = get_cell(arc, cellId);
  if (cell == NULL) {
    HC_ERR_LOG(("hcoa_internal_retrieve_object_create: failed to"
		" find cell %d for oid %s", 
		cellId, oid));
    return HCERR_PLATFORM_GENERAL_ERROR;
  }
  HC_DEBUG_DETAIL(("cellId %d => %s:%d", cellId, cell->addr, cell->port));

  if (snprintf(retrieve_url, MAX_HTTP_URL_LEN, "%s://%s:%d/%s?%s=%s",
	       HTTP_PROTOCOL_TAG, cell->addr, cell->port, 
	       RETRIEVE_URL, ID_PARAMETER, oid) == MAX_HTTP_URL_LEN) {
    HC_ERR_LOG(("hcoa_internal_retrieve_object_create: url length exceeded internal maximum %d",
		MAX_HTTP_URL_LEN));
    return HCERR_URL_TOO_LONG;
  }

  res = hcoa_retrieve_create(handle, archive, retrieve_url, oid, data_writer, stream);

  return res;
}


hcerr_t hcoa_retrieve_object_create(void **h, 
				    hc_archive_t *archive, 
				    hc_oid oid,
				    write_to_data_destination data_writer, void *stream) {
  hcerr_t res;
  hcoa_retrieve_handle_t *handle;

  ALLOCATOR(*h, sizeof(hcoa_retrieve_handle_t));
  handle = *h;
  res = hcoa_internal_retrieve_object_create(handle,
					     archive, 
					     oid, 
					     data_writer, stream);
  if (res != HCERR_OK) {
    return res;
  }

  res = hcoa_retrieve_start_transmit(&handle->base);

  return res;
}


hcerr_t hcoa_internal_range_retrieve_create(hcoa_range_retrieve_handle_t *handle, 
					    hc_archive_t *archive, 
					    hc_oid oid, 
					    hc_long_t firstbyte, hc_long_t lastbyte,
					    write_to_data_destination data_writer, 
					    void *stream) {
  hcerr_t res = 0;

  validate_oid(oid);

  res = hcoa_internal_retrieve_object_create(&handle->retrieve_handle, archive, oid, data_writer, stream);
  if(res != HCERR_OK) {
    return res;
  }

  /* By setting these here, we cause hcoa_retrieve_create_init_http
     to add a "range: x-y" header before invoking the platform layer. */
  op_type(handle) = RANGE_RETRIEVE_DATA;
  handle->firstbyte = firstbyte;
  handle->lastbyte = lastbyte;

  return HCERR_OK;
}


hcerr_t hcoa_range_retrieve_create(void **h, 
				   hc_archive_t *archive, 
				   hc_oid oid, 
				   hc_long_t firstbyte, hc_long_t lastbyte,
				   write_to_data_destination data_writer, void *stream) {
  hcerr_t res;
  hcoa_range_retrieve_handle_t *handle;

  HC_DEBUG(("hcoa_range_retrieve_create(oid=%s, firstbyte="LL_FORMAT", lastbyte="LL_FORMAT")", 
	    oid,firstbyte,lastbyte));

  ALLOCATOR(*h, sizeof(hcoa_range_retrieve_handle_t));
  handle = *h;
  res = hcoa_internal_range_retrieve_create(handle,
					    archive,
					    oid, 
					    firstbyte, lastbyte,
					    data_writer, stream);
  if (res != HCERR_OK) {
    return res;
  }

  res = hcoa_retrieve_start_transmit(&handle->retrieve_handle.base);

  return res;
}

hcerr_t hcoa_io_worker(void *h, int *donep) {
  hcoa_base_handle_t *base = (hcoa_base_handle_t *)h;
  hcerr_t res = 0;
  hc_int_archive_t *arc; 
  int sessions_just_finished = -1;

  assert_handle(base, ANY_HANDLE, ANY_OP, ANY_STATE);
  
  arc = base->archive;

  *donep = 0;

  /* Let the platform layer do some I/O */
  res = do_io(arc->hcoa_http_sessionset_ptr, &sessions_just_finished);
  if (res != HCERR_OK) {
    return res;
  }

  /* Assertion: This handle's transfer is running and we have called
     do_io at least one time that includes this handle. */

  /* Now check on the status of our own transfer */
  res = hcoa_session_completion_check(base,donep);

  /* Nothing else to do until the IO completes! Caller will check the
   * "done" flag before calling select and then call us again.
   */
  return res;

}

hcerr_t hcoa_retrieve_error(int32_t response_code, int32_t connect_errno, int32_t platform_result) {
  hcerr_t res = HCERR_OK;

  if(response_code == SERVER_ERR_NOT_FOUND) {
    res = HCERR_NO_SUCH_OBJECT;
  } else if(response_code == SERVER_ERR_BAD_REQUEST) {
    res = HCERR_BAD_REQUEST;
  } else if(response_code == SERVER_ERR_NOT_FOUND) {
    res = HCERR_NO_SUCH_OBJECT;
  } else if(response_code == SERVER_ERR_INTERNAL_ERROR) {
    res = HCERR_INTERNAL_SERVER_ERROR;
  } else if (connect_errno || platform_result){
    res = platform_interpret_error(response_code, connect_errno, platform_result);
  } else if (response_code == SERVER_SUCCESS) {
    res = HCERR_OK;
  } else {
    HC_ERR_LOG(("hcoa_retrieve_error: response_code %d not known,"
		" treating as HCERR_BAD_REQUEST",
		response_code));
    res = HCERR_BAD_REQUEST;
  }
  if (res != HCERR_OK) {
    HC_ERR_LOG(("hcoa_retrieve_error(%d,%d,%d) => %d %s",
		response_code,connect_errno,platform_result,
		res,hc_decode_hcerr(res)));
  }
  
  return res;
}

hcerr_t hcoa_internal_close(hcoa_base_handle_t *base,
			    int32_t *response_codep,
			    char *errstr, hc_string_index_t errstrlen) {
  hcerr_t res;

  get_session_response_code(base->session_ptr, &base->response_code);
  get_session_platform_result(base->session_ptr, &base->platform_result);
  get_session_connect_errno(base->session_ptr, &base->connect_errno);

  *response_codep = base->response_code;
  close_session(base->session_ptr);
  base->session_ptr = NULL;
  copy_errstr (base, errstr, errstrlen);
  
  res  = hcoa_retrieve_error(base->response_code, base->connect_errno, base->platform_result);

  return res;
}

hcerr_t hcoa_internal_retrieve_close(hcoa_retrieve_handle_t *handle,
				     int32_t *response_code,
				     char *errstr, hc_string_index_t errstrlen) {

  validate_handle(handle, RETRIEVE_HANDLE, ANY_OP, ANY_STATE);

  return hcoa_internal_close(&handle->base, response_code,errstr,errstrlen);
}


hcerr_t hcoa_retrieve_close(void *h,
                          int32_t *response_code,
                          char *errstr, hc_string_index_t errstrlen) {

  hcerr_t res;

  res = hcoa_internal_retrieve_close((hcoa_retrieve_handle_t *)h,
				     response_code,
				     errstr, errstrlen);
  FREE_HANDLE(h, hcoa_retrieve_handle_t, RETRIEVE_HANDLE);
  return res;
}


hcerr_t init_system_record(hc_system_record_t *system_record) {
  memchr(system_record, (char)0, sizeof(hc_system_record_t));
  return HCERR_OK;
}


/* Store */


/* Store helper function to do 1st half of init - init the handle */

hcerr_t hcoa_store_create_init_handle(hcoa_store_handle_t *handle, 
				      hc_archive_t *archive, 
				      read_from_data_source store_data_source_reader,
				      void *stream,
                                      int cellid,
				      hc_long_t chunk_size_bytes,	
				      hc_long_t window_size_chunks,
				      hc_oid link_oid) {

  hcerr_t res = HCERR_OK;  
  
  /* initialize some handle structures */
  handle->cellid = cellid;
  handle->chunk_size_bytes = chunk_size_bytes;
  handle->window_size_chunks = window_size_chunks;
  handle->last_committed_chunk = 0;  /* 1 indexed */
  handle->last_sent_chunk = 0;        /* 1 indexed */
  handle->last_sent_byte = 0;         /* 1 indexed */
  handle->all_chunks_committed = 0;
  handle->last_commit_ack_len = 0;
  handle->system_record_complete = 0;
  handle->write_completed = 0;
  // disabling failsafe since server doesn't handle it
  handle->failsafe = 0;
  if(chunk_size_bytes <=0 || window_size_chunks <= 0) {
    handle->failsafe = 0;
  }

  res = init_base (&handle->base, archive, STORE_HANDLE, STORE_DATA);
  if (res != HCERR_OK) return res;

  handle->base.rbuf = handle->base.rend = handle->readbuffer;
  handle->base.rbuflen = sizeof(handle->readbuffer);

  handle->store_data_source_reader = store_data_source_reader;
  handle->store_stream = stream;

  memset(&handle->hc_system_record, 0, sizeof(hc_system_record_t));
  memcpy(handle->base.link_oid, link_oid, OID_HEX_CHRSTR_LENGTH); 

  op_state(handle) = OP_UNDEF;

  init_system_record(&(handle->hc_system_record));
  handle->parser = NULL;
  
  /* initialize xml parser for receiving system metadata. */
  res = xml_create(&hcoa_sysmd_start_element_handler,
		   &hcoa_sysmd_end_element_handler,
		   (void *)&(handle->hc_system_record),
		   &(handle->parser),
		   allocator, 
		   deallocator);
  if (res != HCERR_OK) {
    return res;
  }
  
  return HCERR_OK;
}


/* store helper function to do second half init - set up HTTP POST stuff */

hcerr_t hcoa_store_create_init_http(hcoa_store_handle_t *handle) {
  hcerr_t res = HCERR_OK;
  hc_int_archive_t *arc = handle->base.archive;
  char chunksize_str[32];

  assert_handle(handle, ANY_HANDLE, ANY_OP, ANY_STATE);

  HC_DEBUG_DETAIL(("Connecting to store_url: %s", handle->base.url));
  /* construct the post session using the http platform */
  handle->base.archive->get_multicell_config = 0;
  if ((res = create_post_session(handle->base.archive->hcoa_http_sessionset_ptr, 
				 &handle->base.session_ptr,
				 handle->base.url, 
				 platform_header_callback, 
				 handle->base.archive,
				 hcoa_store_read_callback, 
				 &(handle->base),
				 hcoa_store_upload_callback, 
				 &(handle->base),
				 TRUE)) != HCERR_OK) {
      return res;
  }

  add_headers((hcoa_base_handle_t *)handle);

  /* Should we ask the server to include full error traces? (stackdump) */
  if (HC_DEBUG_ERR_IF)
    require_ok(add_request_header(session_ptr(handle), HC_REQUEST_STACK_TRACE_HEADER, "true"));
  
  if(handle->failsafe) {
    sprintf(chunksize_str, LL_FORMAT, handle->chunk_size_bytes);
    require_ok(add_request_header(session_ptr(handle), CHUNKSIZE_HEADER, chunksize_str));
  } else {
    handle->all_chunks_committed = 1;
  }	/* End If/Else (handle->failsafe) */
    return res;
}


/* Store common */

long hcoa_store_upload_from_buffer(void *context, char *buffer, long size) {
  hcoa_base_handle_t *base = (hcoa_base_handle_t *)context;
  hc_buffer_index_t already_written = -1; 
  hc_buffer_index_t bytes_left_to_send = -1; 
  long bytes_written;

  assert(base->wbuflen > 0);
  assert(size > 0);

  already_written = base->wend - base->wbuf; 
  bytes_left_to_send = base->wbuflen - already_written; 

  assert(bytes_left_to_send >= 0);

  bytes_written = size;
  if (bytes_written >= bytes_left_to_send) {
    bytes_written = bytes_left_to_send;
  }
 
  if (bytes_written == 0) {
    return 0;
  }

  memcpy(buffer, base->wend, bytes_written);
  
  /* update read end ptr in base handle */
  base->wend += bytes_written;

  return bytes_written;
}

hcerr_t hcoa_session_completion_check(void *handle, int *finishedp) {
  hcoa_base_handle_t *base = (hcoa_base_handle_t *)handle;

  /* Check for Session Completion */
  if (!base->rcompleted) {
    hcerr_t res = session_completion_check(base->session_ptr,&base->rcompleted);
    if (res != HCERR_OK)
      return res;
  }

  *finishedp = base->rcompleted;
  return HCERR_OK;
}

hcerr_t hcoa_store_create(hcoa_store_handle_t *handle, 
			  hc_archive_t *archive, 
			  read_from_data_source store_data_source_reader,
			  void *stream,
			  hc_long_t chunk_size_bytes, 
			  hc_long_t window_size_chunks) {

  hcerr_t res;
  hc_oid link_oid;
  hc_int_archive_t *arc = (hc_int_archive_t *)archive;
  cell_t *cell;

  FILL_INVALID_OID(link_oid);

  if ((res = hcoa_store_create_init_handle(handle, 
					   archive,
					   store_data_source_reader,
					   stream,
                                           -1, // default cellid selection
					   chunk_size_bytes,
					   window_size_chunks,
					   link_oid)) 
      != HCERR_OK) {
    return res;
  }
  //%%MULTICELL
  cell = get_store_cell(arc);
  if (cell == NULL) {
    HC_ERR_LOG(("hcoa_storecreate: multicell out of space"));
    return HCERR_PLATFORM_GENERAL_ERROR;
  }

  if (snprintf(handle->base.url, MAX_HTTP_URL_LEN, "%s://%s:%d/%s", 
	       HTTP_PROTOCOL_TAG, cell->addr, cell->port, STORE_URL) == MAX_HTTP_URL_LEN) {
    HC_ERR_LOG(("hcoa_storecreate:   url length exceeded internal maximum %d",
		MAX_HTTP_URL_LEN));
    return HCERR_URL_TOO_LONG;
  }

  return HCERR_OK;
}


/* Store object */
hcerr_t hcoa_internal_store_object_create(hcoa_store_handle_t *handle, 
					  hc_archive_t *archive, 
					  read_from_data_source store_data_source_reader,
					  void *stream,
					  hc_long_t chunk_size_bytes, 
					  hc_long_t window_size_chunks) {
  hcerr_t err = hcoa_store_create(handle, 
				  archive,
				  store_data_source_reader,
				  stream,
				  chunk_size_bytes, 
				  window_size_chunks);
  return err;
}

hcerr_t hcoa_store_object_create(void **handle, 
				 hc_archive_t *archive, 
				 read_from_data_source store_data_source_reader,
				 void *stream,
				 hc_long_t chunk_size_bytes, 
				 hc_long_t window_size_chunks) {

  hcerr_t res;

  ALLOCATOR(*handle, sizeof(hcoa_store_handle_t));
  res = hcoa_internal_store_object_create((hcoa_store_handle_t *)*handle, 
					  archive,
					  store_data_source_reader,
					  stream,
					  chunk_size_bytes, 
					  window_size_chunks);

  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}

#define READBUFLEN 16384


hcerr_t hcoa_internal_get_last_committed_offset (hcoa_store_handle_t *handle,
                                               hc_long_t *last_committed_offset) {

  *last_committed_offset = 
    handle->last_committed_chunk * handle->chunk_size_bytes;
  return HCERR_OK;
}


hcerr_t hcoa_get_last_committed_offset (void *h,
				      hc_long_t *last_committed_offset) {

  return hcoa_internal_get_last_committed_offset ((hcoa_store_handle_t*) h,
                                                last_committed_offset);
}


hcerr_t hcoa_internal_store_close(hcoa_store_handle_t *handle,
				  int32_t *response_code, 
                                  char *errstr, 
                                  hc_string_index_t errstrlen,
				  hc_system_record_t *sysrec) {

  hcerr_t res;

  validate_handle(handle, STORE_HANDLE, ANY_OP, ANY_STATE);
  
  HC_DEBUG_DETAIL(("hcoa_internal_store_close"));
  memcpy(sysrec, &(handle->hc_system_record), sizeof(hc_system_record_t));

  xml_cleanup(handle->parser);

  res = hcoa_internal_close(&handle->base,response_code,errstr,errstrlen);

  if (res == HCERR_OK && !handle->system_record_complete) {
    HC_ERR_LOG(("hcoa_store_close: IO is complete but did not receive complete system record!"));
    res = HCERR_FAILED_TO_GET_SYSTEM_RECORD;
  }
  return res;
}

hcerr_t hcoa_store_close(void *h,
			 int32_t *response_code, char *errstr, hc_string_index_t errstrlen,
			 hc_system_record_t *sysrec) {
  hcerr_t res;

  res = hcoa_internal_store_close((hcoa_store_handle_t *)h,
				  response_code, errstr, errstrlen,
				  sysrec);
  FREE_HANDLE(h, hcoa_store_handle_t, STORE_HANDLE);
  
  return res;
}


/* delete */

hcerr_t hcoa_internal_delete_object_create(hcoa_delete_handle_t *handle,
					   hc_archive_t *archive, 
					   hc_oid oid,
					   char *buf, int buflen) {
  hcerr_t err = HCERR_OK;
  cell_id_t cellId = 0;
  hc_int_archive_t *arc = (hc_int_archive_t *)archive;
  cell_t *cell;

  validate_oid(oid);

  /* construct a delete url to use for the http get */
  //%%MULTICELL
  cellId = get_cell_id(oid);
  cell = get_cell(arc, cellId);
  if (cell == NULL) {
    HC_ERR_LOG(("hcoa_internal_delete_object_create: failed to"
		" find cell %d for oid %s", 
		cellId, oid));
    return HCERR_PLATFORM_GENERAL_ERROR;
  }

  if (snprintf(handle->base.url, MAX_HTTP_URL_LEN, "%s://%s:%d/%s?%s=%s",
	       HTTP_PROTOCOL_TAG, cell->addr, cell->port, DELETE_URL, ID_PARAMETER, oid) == MAX_HTTP_URL_LEN) {
    HC_ERR_LOG(("hcoa_internal_delete_object_create: url length exceeded internal maximum %d",
		MAX_HTTP_URL_LEN));
    return HCERR_URL_TOO_LONG;
  }


  err = hcoa_retrieve_create(handle,
			     archive, 
			     handle->base.url, oid, 
			     hcoa_retrieve_to_buffer_callback, (void *)handle);
  handle->base.rbuf = handle->base.rend = buf;
  handle->base.rbuflen = buflen;
  handle_type(handle) |= DELETE_HANDLE;
  if (err != HCERR_OK) {
    return err;
  }

  err = hcoa_retrieve_start_transmit(&handle->base);
  return err;
}

/* ---> */
hcerr_t hcoa_delete_object_create(void **handle,
				  hc_archive_t *archive, 
				  hc_oid oid,
				  char *buf, int buflen) {
  hcerr_t res;

  ALLOCATOR(*handle, sizeof(hcoa_delete_handle_t));
  res = hcoa_internal_delete_object_create((hcoa_delete_handle_t *)*handle,
					   archive,
					   oid, buf, buflen);
  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}


#define DELBUFLEN 16384


hcerr_t hcoa_internal_delete_close(hcoa_delete_handle_t *handle, 
                                 int32_t *response_code,
                                 char *errstr, hc_string_index_t errstrlen) {
  
  validate_handle(handle, DELETE_HANDLE, ANY_OP, ANY_STATE);
  return hcoa_internal_retrieve_close((hcoa_retrieve_handle_t *) handle, response_code,
				      errstr, errstrlen);
}

hcerr_t hcoa_delete_close(void *handle, 
                        int32_t *response_code,
                        char *errstr, hc_string_index_t errstrlen) {
  hcerr_t res;
  res = hcoa_internal_delete_close((hcoa_delete_handle_t *)handle, 
				   response_code,
				   errstr, errstrlen);
  FREE_HANDLE(handle, hcoa_delete_handle_t, DELETE_HANDLE);

  return res;
}


/* TODO: retrieve_config and hcoa_status need to be done still */

hcerr_t hcoa_retrieve_config(char *url, char **config) {
  return HCERR_NOT_YET_IMPLEMENTED;
}


hcerr_t hcoa_status(char *address, int *status) {
  return HCERR_NOT_YET_IMPLEMENTED;
}


/* sessionset i/o */


/* fd stuff */

hcerr_t hcoa_multi_fdset(hc_archive_t *archive, 
			 fd_set *read_fd_set,
			 fd_set *write_fd_set,
			 fd_set *exc_fd_set,
			 int *max_fdp) {
  return get_multi_fdset(((hc_int_archive_t *)archive)->hcoa_http_sessionset_ptr,
			 read_fd_set,
			 write_fd_set,
			 exc_fd_set,
			 max_fdp);
}

hcerr_t hcoa_select_all(hc_archive_t *arc) {
  fd_set read_fd_set;
  fd_set write_fd_set;
  fd_set exc_fd_set;
  int max;
  hcerr_t res;
  int selectres;
  struct timeval timeout;

  FD_ZERO(&read_fd_set);
  FD_ZERO(&write_fd_set);
  FD_ZERO(&exc_fd_set);

  res = hcoa_multi_fdset(arc,&read_fd_set,&write_fd_set, &exc_fd_set, &max);
  if (res != HCERR_OK) {
    return res;
  }

  timeout.tv_sec = 3;
  timeout.tv_usec = 0; 

  /* 
  HC_DEBUG_IODETAIL(("hcoa_select_all sleeping for %d seconds with max_fd=%d",
		     timeout.tv_sec,max));
  */
  selectres = select(max+1, &read_fd_set, &write_fd_set, NULL, &timeout);

  return HCERR_OK;
}

/* global progress checks */

hcerr_t hcoa_sessions_in_progress(hc_archive_t *archive, int *num) {
    assert(archive != NULL);
    return sessions_in_progress(((hc_int_archive_t *)archive)->hcoa_http_sessionset_ptr, num);
}


char *hc_decode_hcerr(hcerr_t res) {
  if (res == HCERR_OK) {
    return "HCERR_OK";
  } else if (res == HCERR_NOT_INITED) {
    return "HCERR_NOT_INITED";
  } else if (res == HCERR_ALREADY_INITED) {
    return "HCERR_ALREADY_INITED";
  } else if (res == HCERR_INIT_FAILED) {
    return "HCERR_INIT_FAILED";
  } else if (res == HCERR_OOM) {
    return "HCERR_OOM";
  } else if (res == HCERR_NOT_YET_IMPLEMENTED) {
    return "HCERR_NOT_YET_IMPLEMENTED";
  } else if (res == HCERR_SESSION_CREATE_FAILED) {
    return "HCERR_SESSION_CREATE_FAILED";
  } else if (res == HCERR_ADD_HEADER_FAILED) {
    return "HCERR_ADD_HEADER_FAILED";
  } else if (res == HCERR_IO_ERR) {
    return "HCERR_IO_ERR";
  } else if (res == HCERR_FAILOVER_OCCURRED) {
    return "HCERR_FAILOVER_OCCURRED";
  } else if (res == HCERR_CAN_CALL_AGAIN) {
    return "HCERR_CAN_CALL_AGAIN";
  } else if (res == HCERR_GET_RESPONSE_CODE_FAILED) {
    return "HCERR_GET_RESPONSE_CODE_FAILED";
  } else if (res == HCERR_CONNECTION_FAILED) {
    return "HCERR_CONNECTION_FAILED";
  } else if (res == HCERR_BAD_REQUEST) {
    return "HCERR_BAD_REQUEST";
  } else if (res == HCERR_NO_SUCH_OBJECT) {
    return "HCERR_NO_SUCH_OBJECT";
  } else if (res == HCERR_INTERNAL_SERVER_ERROR) {
    return "HCERR_INTERNAL_SERVER_ERROR";
  } else if (res == HCERR_FAILED_GETTING_FDSET) {
    return "HCERR_FAILED_GETTING_FDSET";
  } else if (res == HCERR_FAILED_CHECKING_FDSET) {
    return "HCERR_FAILED_CHECKING_FDSET";
  } else if (res == HCERR_MISSING_SELECT_CLAUSE) {
    return "HCERR_MISSING_SELECT_CLAUSE";
  } else if (res == HCERR_URL_TOO_LONG) {
    return "HCERR_URL_TOO_LONG";
  } else if (res == HCERR_COULD_NOT_OPEN_FILE) {
    return "HCERR_COULD_NOT_OPEN_FILE";
  } else if (res == HCERR_FAILED_TO_WRITE_TO_FILE) {
    return "HCERR_FAILED_TO_WRITE_TO_FILE";
  } else if (res == HCERR_INVALID_OID) {
    return "HCERR_INVALID_OID";
  } else if (res == HCERR_NULL_ARCHIVE) {
    return "HCERR_NULL_ARCHIVE";
  } else if (res == HCERR_INVALID_ARCHIVE) {
    return "HCERR_INVALID_ARCHIVE";
  } else if (res == HCERR_NULL_SESSION) {
    return "HCERR_NULL_SESSION";
  } else if (res == HCERR_INVALID_SESSION) {
    return "HCERR_INVALID_SESSION";
  } else if (res == HCERR_NULL_HANDLE) {
    return "HCERR_NULL_HANDLE";
  } else if (res == HCERR_INVALID_HANDLE) {
    return "HCERR_INVALID_HANDLE";      
      
  } else if (res == HCERR_INVALID_SCHEMA) {
    return "HCERR_INVALID_SCHEMA";
  } else if (res == HCERR_INVALID_NVR) {
    return "HCERR_INVALID_NVR";
  } else if (res == HCERR_INVALID_RESULT_SET) {
    return "HCERR_INVALID_RESULT_SET";

  } else if (res == HCERR_WRONG_HANDLE_FOR_OPERATION) {
    return "HCERR_WRONG_HANDLE_FOR_OPERATION";
  } else if (res == HCERR_HANDLE_IN_WRONG_STATE_FOR_OPERATION) {
    return "HCERR_HANDLE_IN_WRONG_STATE_FOR_OPERATION";
  } else if (res == HCERR_READ_PAST_LAST_RESULT) {
    return "HCERR_READ_PAST_LAST_RESULT";
  } else if (res == HCERR_XML_PARSE_ERROR) {
    return "HCERR_XML_PARSE_ERROR";
  } else if (res == HCERR_XML_MALFORMED_XML) {
    return "HCERR_XML_MALFORMED_XML";
  } else if (res == HCERR_XML_EXPECTED_LT) {
    return "HCERR_XML_EXPECTED_LT";
  } else if (res == HCERR_XML_INVALID_ELEMENT_TAG) {
    return "HCERR_XML_INVALID_ELEMENT_TAG";
  } else if (res == HCERR_XML_MALFORMED_START_ELEMENT) {
    return "HCERR_XML_MALFORMED_START_ELEMENT";
  } else if (res == HCERR_XML_MALFORMED_END_ELEMENT) {
    return "HCERR_XML_MALFORMED_END_ELEMENT";
  } else if (res == HCERR_XML_BAD_ATTRIBUTE_NAME) {
    return "HCERR_XML_BAD_ATTRIBUTE_NAME";
  } else if (res == HCERR_XML_BUFFER_OVERFLOW) {
    return "HCERR_XML_BUFFER_OVERFLOW";
  } else if (res == HCERR_BUFFER_OVERFLOW) {
    return "HCERR_BUFFER_OVERFLOW";
  } else if (res == HCERR_OUT_OF_MEMORY) {
    return "HCERR_OUT_OF_MEMORY";
  } else if (res == HCERR_NO_SUCH_TYPE) {
    return "HCERR_NO_SUCH_TYPE";
  } else if (res == HCERR_ILLEGAL_VALUE_FOR_METADATA) {
    return "HCERR_ILLEGAL_VALUE_FOR_METADATA";
  } else if (res == HCERR_NO_SUCH_ATTRIBUTE) {
    return "HCERR_NO_SUCH_ATTRIBUTE";
  } else if (res == HCERR_NO_MORE_ATTRIBUTES) {
    return "HCERR_NO_MORE_ATTRIBUTES";
  } else if (res == HCERR_FAILED_GETTING_SILO_DATA) {
    return "HCERR_FAILED_GETTING_SILO_DATA";
  } else if (res == HCERR_PLATFORM_NOT_INITED) {
      return "HCERR_PLATFORM_NOT_INITED";
  } else if (res == HCERR_PLATFORM_ALREADY_INITED) {
    return "HCERR_PLATFORM_ALREADY_INITED";
  } else if (res == HCERR_PLATFORM_INIT_FAILED) {
    return "HCERR_PLATFORM_INIT_FAILED";
  } else if (res == HCERR_PLATFORM_HEADER_TOO_LONG) {
    return "HCERR_PLATFORM_HEADER_TOO_LONG";
  } else if (res == HCERR_PLATFORM_TOO_LATE_FOR_HEADERS) {
    return "HCERR_PLATFORM_TOO_LATE_FOR_HEADERS";
  } else if (res == HCERR_PLATFORM_NOT_ALLOWED_FOR_GET) {
    return "HCERR_PLATFORM_NOT_ALLOWED_FOR_GET";
  } else if (res == HCERR_FAILED_TO_GET_SYSTEM_RECORD) {
      return "HCERR_FAILED_TO_GET_SYSTEM_RECORD";
  } else if (res == HCERR_PARTIAL_FILE) {
    return "HCERR_PARTIAL_FILE";
  } else if (res == HCERR_ABORTED_BY_CALLBACK) {
    return "HCERR_ABORTED_BY_CALLBACK";
  } else if (res == HCERR_PLATFORM_GENERAL_ERROR) {
    return "HCERR_PLATFORM_GENERAL_ERROR";
  } else if (res == HCERR_ILLEGAL_ARGUMENT) {
    return "HCERR_ILLEGAL_ARGUMENT";
  } else if (res == HCERR_CLIENT_GAVE_UP) {
    return "HCERR_CLIENT_GAVE_UP";
  } else {
    return "UNKNOWN ERROR";
  }
}


char *decode_op(hc_operation_t op) {
  if (op == UNITIALIZED) {
    return "UNITIALIZED";
  }else if (op == ANY_OP) {
    return "ANY_OP";

  }else if (op == STORE_DATA) {
    return "STORE_DATA";
  }else if (op == STORE_METADATA) {
    return "STORE_METADATA";
  }else if (op == STORE_BOTH) {
    return "STORE_BOTH";

  }else if (op == RETRIEVE_DATA) {
    return "RETRIEVE_DATA";
  }else if (op == RANGE_RETRIEVE_DATA) {
    return "RANGE_RETRIEVE_DATA";
  }else if (op == RETRIEVE_METADATA) {
    return "RETRIEVE_METADATA";
  }else if (op == RETRIEVE_SCHEMA) {
    return "RETRIEVE_SCHEMA";

  }else if (op == QUERY) {
    return "QUERY";
  }else if (op == QUERY_PLUS) {
    return "QUERY_PLUS";
  
  }else{
    return "unknown";
  }
}

char *decode_handle_type(hc_handle_type_t ht) {
  if (0 != (ht & QUERY_HANDLE)) {
    return "QUERY_HANDLE";
  }
  else if (0 != (ht & RETRIEVE_SCHEMA_HANDLE)) {
    return "RETRIEVE_SCHEMA_HANDLE";
  }
  else if (0 != (ht & STORE_METADATA_HANDLE)) {
    return "STORE_METADATA_HANDLE";
  }
  else if (0 != (ht & RETRIEVE_METADATA_HANDLE)) {
    return "RETRIEVE_METADATA_HANDLE";
  }
  else if (0 != (ht & STORE_HANDLE)) {
    return "STORE_HANDLE";
  }
  else if (0 != (ht & RETRIEVE_HANDLE)) {
    return "RETRIEVE_HANDLE";
  }
  else if (0 != (ht & DELETE_HANDLE)) {
    return "DELETE_HANDLE";
  }
  else if (0 != (ht & BASE_HANDLE)) {
    return "BASE_HANDLE";
  }  
  else if (0 != (ht & ANY_HANDLE)) {
    return "ANY_HANDLE";
  }
  else{
    return "UNKNOWN HANDLE";
  }
}

char *decode_state(hc_op_state_t state) {
  if (state == QUERY_RETRIEVING_RESULTS) {
    return "QUERY_RETRIEVING_RESULTS";
  } else  if (state == QUERY_SERVING_FINAL_RESULTS) {
    return "QUERY_SERVING_FINAL_RESULTS";
  } else  if (state == QUERY_SERVING_RESULTS) {
    return "QUERY_SERVING_RESULTS";
  } else  if (state == QUERY_DONE_SERVING_RESULTS) {
    return "QUERY_DONE_SERVING_RESULTS";
  } else  if (state == QUERY_DONE_SERVING_CELL) {
    return "QUERY_DONE_SERVING_CELL";
  } else  if (state == QUERY_FINISHED) {
    return "QUERY_FINISHED";

  } else  if (state == OP_UNDEF) {
    return "OP_UNDEF";
  } else  if (state == ANY_STATE) {
    return "ANY_STATE";

  } else  if (state == READING_METADATA_RECORD) {
    return "READING_METADATA_RECORD";
  } else  if (state == READING_SYSTEM_RECORD) {
    return "READING_SYSTEM_RECORD";

  } else  if (state == STORING_DATA) {
    return "STORING_DATA";
  } else  if (state == ADDING_METADATA) {
    return "ADDING_METADATA";
  } else  if (state == STORING_METADATA) {
    return "STORING_METADATA";

  } else {
    return "unknown";
  }
  /*NOTREACHED*/
}

/* store pseudocode:

In store_data:
1. Check failover_pending
2. Non-blocking read poll
Update last_committed_chunk with any pending input
3. If ((last_sent_chunk - last_committed_chunk) < window_size_chunks)
signal E_BUSY
4. Write (non-blocking)
5. Update last_sent_chunk


*/

/* set retention */
/*
hcerr_t hcoa_internal_set_retention_create(hcoa_compliance_handle_t *handle,
					   hc_archive_t *archive, 
					   hc_oid oid,
                       time_t timestamp,
					   char *buf, int buflen) {
  hcerr_t err = HCERR_OK;
  cell_id_t cellId = 0;
  cell_t *cell;
  hc_int_archive_t *arc = (hc_int_archive_t *)archive;

  validate_oid(oid);

  cellId = get_cell_id(oid);
  cell = get_cell(arc, cellId);
  if (cell == NULL) {
    HC_ERR_LOG(("hcoa_internal_set_retention_create: failed to"
		" find cell %d for oid %s",
		cellId, oid));
    return HCERR_PLATFORM_GENERAL_ERROR;
  }
  
  if (snprintf(handle->base.url, MAX_HTTP_URL_LEN, "%s://%s:%d/%s?%s=%s&%s=%lld",
	       HTTP_PROTOCOL_TAG, cell->addr, cell->port, SET_RETENTION_URL, ID_PARAMETER, oid, DATE_PARAMETER, timestamp) == MAX_HTTP_URL_LEN) {
    HC_ERR_LOG(("hcoa_internal_set_retention_create: url length exceeded internal maximum %d",
		MAX_HTTP_URL_LEN));
    return HCERR_URL_TOO_LONG;
  }


  err = hcoa_retrieve_create(handle,
			     archive, 
			     handle->base.url, oid, 
			     hcoa_retrieve_to_buffer_callback, (void *)handle);
  handle->base.rbuf = handle->base.rend = buf;
  handle->base.rbuflen = buflen;
  handle_type(handle) |= COMPLIANCE_HANDLE;
  if (err != HCERR_OK) {
    return err;
  }

  err = hcoa_retrieve_start_transmit(&handle->base);
  return err;
}
*/

/* ---> */
/*
hcerr_t hcoa_set_retention_create(void **handle,
				  hc_archive_t *archive, 
				  hc_oid oid,
                  time_t timestamp,
				  char *buf, int buflen) {
  hcerr_t res;

  ALLOCATOR(*handle, sizeof(hcoa_compliance_handle_t));
  res = hcoa_internal_set_retention_create((hcoa_compliance_handle_t *)*handle,
					   archive,
					   oid, timestamp, buf, buflen);
  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}
*/

/* get retention */
/*
hcerr_t hcoa_internal_get_retention_create(hcoa_compliance_handle_t *handle,
					   hc_archive_t *archive, 
					   hc_oid oid,
                       time_t *timestamp,
					   char *buf, int buflen) {
  hcerr_t err = HCERR_OK;
  cell_id_t cellId = 0;
  cell_t *cell;
  hc_int_archive_t *arc = (hc_int_archive_t *)archive;

  validate_oid(oid);

  cellId = get_cell_id(oid);
  cell = get_cell(arc, cellId);
  if (cell == NULL) {
    HC_ERR_LOG(("hcoa_internal_get_retention_create: failed"
		" to find cell %d for oid %s", 
		cellId, oid));
    return HCERR_PLATFORM_GENERAL_ERROR;
  }
  
  if (snprintf(handle->base.url, MAX_HTTP_URL_LEN, "%s://%s:%d/%s?%s=%s&%s=%d",
	       HTTP_PROTOCOL_TAG, cell->addr, cell->port, GET_RETENTION_URL, ID_PARAMETER, oid) == MAX_HTTP_URL_LEN) {
    HC_ERR_LOG(("hcoa_internal_get_retention_create: url length exceeded internal maximum %lld",
		MAX_HTTP_URL_LEN));
    return HCERR_URL_TOO_LONG;
  }


  err = hcoa_retrieve_create(handle,
			     archive, 
			     handle->base.url, oid, 
			     hcoa_retrieve_to_buffer_callback, (void *)handle);
  handle->base.rbuf = handle->base.rend = buf;
  handle->base.rbuflen = buflen;
  handle_type(handle) |= COMPLIANCE_HANDLE;
  if (err != HCERR_OK) {
    return err;
  }

  err = hcoa_retrieve_start_transmit(&handle->base);
  return err;
}
*/

/* ---> */
/*
hcerr_t hcoa_get_retention_create(void **handle,
				  hc_archive_t *archive, 
				  hc_oid oid,
                  time_t *timestamp,
				  char *buf, int buflen) {
  hcerr_t res;

  ALLOCATOR(*handle, sizeof(hcoa_compliance_handle_t));
  res = hcoa_internal_get_retention_create((hcoa_compliance_handle_t *)*handle,
					   archive,
					   oid, timestamp, buf, buflen);
  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}
*/

/*
hcerr_t hcoa_internal_compliance_close(hcoa_compliance_handle_t *handle, 
                                       int32_t *response_code,
                                       char *errstr, 
                                       hc_string_index_t errstrlen) {
  
  validate_handle(handle, COMPLIANCE_HANDLE, ANY_OP, ANY_STATE);
  return hcoa_internal_retrieve_close((hcoa_retrieve_handle_t *) handle, response_code, errstr, errstrlen);
}
*/

/* ---> */
/*
hcerr_t hcoa_compliance_close(void *handle, 
                              int32_t *response_code,
                              char *errstr, hc_string_index_t errstrlen) {
  hcerr_t res;
  res = hcoa_internal_compliance_close((hcoa_delete_handle_t *)handle, 
				   response_code,
				   errstr, errstrlen);
  FREE_HANDLE(handle, hcoa_compliance_handle_t, COMPLIANCE_HANDLE);

  return res;
}
*/

/* add legal hold */
/*
hcerr_t hcoa_internal_add_hold_create(hcoa_compliance_handle_t *handle,
					   hc_archive_t *archive, 
					   hc_oid oid,
                       char *tag,
					   char *buf, int buflen) {
  hcerr_t err = HCERR_OK;
  cell_id_t cellId = 0;
  cell_t *cell;
  hc_int_archive_t *arc = (hc_int_archive_t *)archive;

  validate_oid(oid);

  cellId = get_cell_id(oid);
  cell = get_cell(arc, cellId);
  if (cell == NULL) {
    HC_ERR_LOG(("hcoa_internal_add_hold_create: failed to"
		" find cell %d for oid %s", cellId, oid));
    return HCERR_PLATFORM_GENERAL_ERROR;
  }

  if (snprintf(handle->base.url, MAX_HTTP_URL_LEN, "%s://%s:%d/%s?%s=%s&%s=%s",
	       HTTP_PROTOCOL_TAG, cell->addr, cell->port, ADD_HOLD_URL, ID_PARAMETER, oid, HOLD_TAG_PARAMETER, tag) == MAX_HTTP_URL_LEN) {
    HC_ERR_LOG(("hcoa_internal_add_hold_create: url length exceeded internal maximum %d",
		MAX_HTTP_URL_LEN));
    return HCERR_URL_TOO_LONG;
  }


  err = hcoa_retrieve_create(handle,
			     archive, 
			     handle->base.url, oid, 
			     hcoa_retrieve_to_buffer_callback, (void *)handle);
  handle->base.rbuf = handle->base.rend = buf;
  handle->base.rbuflen = buflen;
  handle_type(handle) |= COMPLIANCE_HANDLE;
  if (err != HCERR_OK) {
    return err;
  }

  err = hcoa_retrieve_start_transmit(&handle->base);
  return err;
}
*/

/* ---> */
/*
hcerr_t hcoa_add_hold_create(void **handle,
				  hc_archive_t *archive, 
				  hc_oid oid,
                  char *tag,
				  char *buf, int buflen) {
  hcerr_t res;

  ALLOCATOR(*handle, sizeof(hcoa_compliance_handle_t));
  res = hcoa_internal_add_hold_create((hcoa_compliance_handle_t *)*handle,
					   archive,
					   oid, tag, buf, buflen);
  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}
*/

/* delete legal Hold */
/*
hcerr_t hcoa_internal_del_hold_create(hcoa_compliance_handle_t *handle,
					   hc_archive_t *archive, 
					   hc_oid oid,
                       char *tag,
					   char *buf, int buflen) {
  hcerr_t err = HCERR_OK;
  cell_id_t cellId = 0;
  cell_t *cell;
  hc_int_archive_t *arc = (hc_int_archive_t *)archive;

  validate_oid(oid);

  cellId = get_cell_id(oid);
  cell = get_cell(arc, cellId);
  if (cell == NULL) {
    HC_ERR_LOG(("hcoa_internal_del_hold_create: failed to"
		" find cell %d for oid %s",
		cellId, oid));
    return HCERR_PLATFORM_GENERAL_ERROR;
  }

  if (snprintf(handle->base.url, MAX_HTTP_URL_LEN, "%s://%s:%d/%s?%s=%s&%s=%s",
	       HTTP_PROTOCOL_TAG, cell->addr, cell->port, REMOVE_HOLD_URL, ID_PARAMETER, oid, HOLD_TAG_PARAMETER, tag) == MAX_HTTP_URL_LEN) {
    HC_ERR_LOG(("hcoa_internal_del_hold_create: url length exceeded internal maximum %d",
		MAX_HTTP_URL_LEN));
    return HCERR_URL_TOO_LONG;
  }


  err = hcoa_retrieve_create(handle,
			     archive, 
			     handle->base.url, oid, 
			     hcoa_retrieve_to_buffer_callback, (void *)handle);
  handle->base.rbuf = handle->base.rend = buf;
  handle->base.rbuflen = buflen;
  handle_type(handle) |= COMPLIANCE_HANDLE;
  if (err != HCERR_OK) {
    return err;
  }

  err = hcoa_retrieve_start_transmit(&handle->base);
  return err;
}
*/

/* ---> */
/*
hcerr_t hcoa_del_hold_create(void **handle,
				  hc_archive_t *archive, 
				  hc_oid oid,
                  char *tag,
				  char *buf, int buflen) {
  hcerr_t res;

  ALLOCATOR(*handle, sizeof(hcoa_compliance_handle_t));
  res = hcoa_internal_del_hold_create((hcoa_compliance_handle_t *)*handle,
					   archive,
					   oid, tag, buf, buflen);
  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}
*/

/* get date */
/*
hcerr_t hcoa_internal_get_date_create(hcoa_compliance_handle_t *handle,
					   hc_archive_t *archive, 
                       time_t *timestamp,
					   char *buf, int buflen) {
  hcerr_t err = HCERR_OK;
  cell_id_t cellId = 0;
  cell_t *cell;
  hc_int_archive_t *arc = (hc_int_archive_t *)archive;

  cell = &arc->default_cell;
  if (cell == NULL) {
    HC_ERR_LOG(("hcoa_internal_get_date_create: no default cell"));
    return HCERR_PLATFORM_GENERAL_ERROR;
  }

  if (snprintf(handle->base.url, MAX_HTTP_URL_LEN, "%s://%s:%d/%s?",
	       HTTP_PROTOCOL_TAG, cell->addr, cell->port, GET_DATE_URL) == MAX_HTTP_URL_LEN) {
    HC_ERR_LOG(("hcoa_internal_get_date_create: url length exceeded internal maximum %lld",
		MAX_HTTP_URL_LEN));
    return HCERR_URL_TOO_LONG;
  }


  err = hcoa_retrieve_create_nooid(handle,
			     archive, 
			     handle->base.url,
			     hcoa_retrieve_to_buffer_callback, (void *)handle);
  handle->base.rbuf = handle->base.rend = buf;
  handle->base.rbuflen = buflen;
  handle_type(handle) |= COMPLIANCE_HANDLE;
  if (err != HCERR_OK) {
    return err;
  }

  err = hcoa_retrieve_start_transmit(&handle->base);
  return err;
}
*/

/* ---> */
/*
hcerr_t hcoa_get_date_create(void **handle,
				  hc_archive_t *archive, 
                  time_t *timestamp,
				  char *buf, int buflen) {
  hcerr_t res;

  ALLOCATOR(*handle, sizeof(hcoa_compliance_handle_t));
  res = hcoa_internal_get_date_create((hcoa_compliance_handle_t *)*handle,
					   archive, timestamp, buf, buflen);
  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}
*/
