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
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

#define	COMPILING_HONEYCOMB
#include "hcclient.h"
#include "hcinternals.h"
#include "hcnvoa.h"
#include "hcnvoai.h"
#include "multicell.h"
#include "hcoai.h"

#define NSEC_MILLISEC_CONV 1000000

static hcerr_t init_retrieve_result_set(hc_query_handle_t *handle, hc_operation_t op_type, char *url);
hcerr_t encode64_to_string(char prefix, char *src, char **dest);
hcerr_t validate_pstmt(hc_pstmt_t *pstmt);

static char *attributes_names[] = { "version" };
static char *attributes_values[] = { "1.1" };

/* Destructively convert string to upper case */
/* static char* ucase(char* s){ */
/*   hc_string_index_t i = 0; */
/*   for (i=0; i< strlen(s); i++){ */
/*     s[i] = toupper(s[i]); */
/*   } */
/*   return s; */
/* } */

void transition_state(void *handle, hc_op_state_t state){
  HC_DEBUG_DETAIL(("%s -> %s", decode_state(op_state(handle)), 
            decode_state(state)));
  op_state(handle) = state;
}


hcerr_t hc_select_all(hc_session_t *session) {
    return hcoa_select_all(Session_archive(session));
}

void hc_save_error_info(hcoa_base_handle_t *base, hc_int_session_t *session) {

  session->connect_errno = base->connect_errno;
  session->platform_result = base->platform_result;
}

void hc_init_error_info(hc_int_session_t *session) {
  session->errstr[0] = '\0';
  session->connect_errno = 0;
  session->platform_result = 0;
  session->response_code = 0;
}

/* readable version of metadata type */
char* hc_decode_hc_type(hc_type_t t){

  if (t == HC_LONG_TYPE)
    return "long";

  if (t == HC_STRING_TYPE)
    return "string";

  if (t == HC_DOUBLE_TYPE)
    return "double";

  if (t == HC_BYTE_TYPE)
    return "byte";

  if (t == HC_CHAR_TYPE)
    return "char";

  if (t == HC_DATE_TYPE)
    return "date";

  if (t == HC_TIME_TYPE)
    return "time";

  if (t == HC_TIMESTAMP_TYPE)
    return "timestamp";

  if (t == HC_BINARY_TYPE)
    return "binary";

  if (t == HC_OBJECTID_TYPE)
    return "objectid";

  return "<hc_decode_hc_type:UNKNOWN TYPE>";
}

/**************************************************************
 *                  Retrieve Metadata                         *
 **************************************************************/

/* XML callbacks */

#ifdef DEBUG_QUEUES
static int element_chain_length(hc_retrieve_metadata_handle_t *handle) {
  hc_name_value_cell_t *top = handle->top;
  hc_name_value_cell_t *last = handle->last;
  hc_name_value_cell_t *cell = handle->top;
  hc_name_value_cell_t *prev = cell;
  int count = 0;
  
  while(cell != NULL) {
    count++;
    prev = cell;
    cell = cell->next;
  }
  return count;
}
#endif /* DEBUG_QUEUES */

static hcerr_t retrieve_metadata_end_element_handler(char *element_name, void *data){
  return HCERR_OK;
}

static int is_v1_1(char **names, char **values, int n) {
  int i;
  for (i=0; i<n; i++) {
    /* 'encoding' is only set for 1.1 & higher */
    if (strcmp(names[i], "value") == 0  &&  strcmp(values[i], "1.1") == 0)
      return 1;
  }
  return 0;
}

static hcerr_t retrieve_metadata_start_element_handler(char *element_name, void *data, char **names, char **values, int n){

  hc_retrieve_metadata_handle_t *handle = (hc_retrieve_metadata_handle_t *) data;
  int i = 0;

  // handle->v1_1 switches
  if (strcmp(element_name, VERSION_TAG) == 0) {
    handle->v1_1 = is_v1_1(names, values, n);
  } else if (strcmp(element_name, SYSTEM_MD_TAG) == 0) {
    handle->v1_1 = 0;
  }
  HC_DEBUG_DETAIL(("%s -> %d\n", SYSTEM_MD_TAG, handle->v1_1));

  if (strcmp(element_name, handle->element_tag) == 0) {
    char *name  = NULL;
    char *value  = NULL;
    char *length = NULL;
    
    HC_DEBUG_IO_DETAIL(("retrieve_metadata_start_element_handler: element_name=%s n=%d",
			element_name,n));
    /* Find the name and value attributes & length if applicable (schema)  */
    for (i = 0; i < n; i++){
      
      /* ---> CASE SENSITIVITY??? */
      if (strcmp(*(names + i), HC_OA_ATTRIBUTE_NAME_TAG) == 0){
	name = *(values + i);
      }
      /* For metadata, this is "value", for schema, this is "type" */
      else if (strcmp(*(names + i), handle->value_tag) == 0){
	value = *(values + i);
      }
      else if (strcmp(*(names+i), HC_OA_ATTRIBUTE_LENGTH_TAG) == 0){
        length = *(values + i);
      }
    }


    if (name != NULL && value != NULL){
      /* These become garbage when I close the parser. */
      hc_name_value_cell_t *cell;

      ALLOCATOR(cell, sizeof (hc_name_value_cell_t));

      cell->v1_1 = handle->v1_1;

      cell->name = name;
      /* keep track of cumulative string length so that we 
       * know what size data structure to create at the end
       * for a return value [??]
       */
      cell->value = value;
      // track field length for schema
      cell->length = -1;
      if (length != NULL) {
        if (!hc_isnum(length)) {
	  HC_ERR_LOG(("retrieve_metadata_start_element_hander:"
		      " field length string '%s' is not numeric",
		      length));
          return HCERR_XML_MALFORMED_XML;
	}
        cell->length = atoi(length);
      }
      
      if (handle->top == NULL){
	handle->last = handle->top = cell;
      }
      handle->last->next = cell;
      handle->last = cell;
      cell->next = NULL;
      handle->count++;
      HC_DEBUG_IO_DETAIL(("retrieve_metadata_start_element_handler: storing:"
		       " name=%s value=%s count="LL_FORMAT,
		       name,value,handle->count));
    }
  } else {
    HC_DEBUG_IO_DETAIL(("retrieve_metadata_start_element_handler: skipping %s",
		     element_name));
  }
  return HCERR_OK;
}


static hcerr_t hc_init_metadata_handle(hc_retrieve_metadata_handle_t *handle,
                                       hc_operation_t op_type,
				       hc_handle_type_t handle_type,
				       char *url, hc_oid oid){
  hcerr_t err = HCERR_OK;

  HC_DEBUG_DETAIL(("hc_init_metadata_handle: handle=0x%lx",(long)handle));
  handle->top = NULL;
  handle->last = NULL;
  handle->count = 0;
  handle->value_tag = NVOA_VALUE_TAG;
  op_state(handle) = OP_UNDEF;
  transition_state(handle, READING_METADATA_RECORD);

  err = hcoa_retrieve_create(&(handle->retrieve_handle), 
			     handle->session->archive,
			     url, 
			     oid, 
			     hc_retrieve_metadata_download_callback, 
			     (void *)handle);
  if (err != HCERR_OK)
    return err;
  handle_type(handle) = RETRIEVE_METADATA_HANDLE | RETRIEVE_HANDLE | handle_type;
  op_type(handle) = op_type;

  err = hcoa_retrieve_start_transmit(&handle->retrieve_handle.base);
  return err;
}


/* Retrieve Metadata */
hcerr_t hc_internal_retrieve_metadata_create(hc_retrieve_metadata_handle_t *handle, 
                                             hc_int_session_t *session, hc_oid oid) {
  hc_int_archive_t *arc = (hc_int_archive_t *)session->archive;
  char retrieve_url[MAX_HTTP_URL_LEN];
  cell_id_t cellId = 0;
  hcerr_t err = 0;
  cell_t *cell;

  // if (hc_should_refresh_cells_info(session)) {
  //   require_ok(hcoa_archive_update_create(session->archive));
  // }

  handle->session = session;
  hc_init_error_info(session);

  /* construct a retrieve url to use for the http get */
  cellId = get_cell_id(oid);
  cell = get_cell(arc, cellId);
  if (cell == NULL) {
    HC_ERR_LOG(("hc_internal_retrieve_metadata_create: failed"
		" to find cell %d from oid %s", 
		cellId, oid));
    return HCERR_PLATFORM_GENERAL_ERROR;
  }
  HC_DEBUG_DETAIL(("cellId %d => %s:%d", cellId, cell->addr, cell->port));

  if (snprintf(retrieve_url, MAX_HTTP_URL_LEN, "%s://%s:%d/%s?%s=%s",
	       HTTP_PROTOCOL_TAG, cell->addr, cell->port, 
	       RETRIEVE_METADATA_URL, ID_PARAMETER, oid) == MAX_HTTP_URL_LEN) {
    HC_ERR_LOG(("hcoa_internal_retrieve_metadata_create:   url length exceeded internal maximum %d",
		MAX_HTTP_URL_LEN));
    return HCERR_URL_TOO_LONG;
  }

  err = hc_init_metadata_handle(handle, 
				RETRIEVE_METADATA, RETRIEVE_METADATA_HANDLE, 
				retrieve_url, oid);
  if (err != HCERR_OK) 
    return err;

  handle->element_tag = HC_OA_ATTRIBUTE_TAG;

  /* initialize xml parser */
  err = xml_create(&retrieve_metadata_start_element_handler,
		   &retrieve_metadata_end_element_handler,
		   (void *) handle,
		   &(handle->parser), 
		   allocator, deallocator);
  return err;
}


hcerr_t hc_retrieve_metadata_create(void **handle, hc_session_t *session,
				    hc_oid oid) {
  hcerr_t res;

  ALLOCATOR(*handle, sizeof(hc_retrieve_metadata_handle_t));
  memset(*handle, 0, sizeof (hc_retrieve_metadata_handle_t));
  res = hc_internal_retrieve_metadata_create((hc_retrieve_metadata_handle_t *) *handle, 
					     (hc_int_session_t *)session, oid);
  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}


long hc_retrieve_metadata_download_callback(void *context, char *buff, long nbytes) {
  hc_long_t parsed_to = 0;
  hcerr_t err;
  hc_retrieve_metadata_handle_t *handle = (hc_retrieve_metadata_handle_t *)context;
  hc_int_archive_t *archive = (hc_int_archive_t *) handle->session->archive;

  assert_handle(handle, RETRIEVE_METADATA_HANDLE, ANY_OP, ANY_STATE);
  if (nbytes < 0) {
    return nbytes;	/* nbytes == -1 indicates end of download */
  }

  if (op_state(handle) == READING_SYSTEM_RECORD){
    /* Already read NV metadata, currently reading System Record */
    err = xml_parse(handle->parser, buff, nbytes, &parsed_to);
    if (err != HCERR_OK) {
      HC_ERR_LOG(("hc_retrieve_metadata_download_callback:  err case 1"));
      return -1;
    }
  } else {
    /* xml_parse reads the full amount of data unless the top-level
       XML document reaches an end. */
    err = xml_parse(handle->parser, buff, nbytes, &parsed_to);
    if (err != HCERR_OK) {
      HC_ERR_LOG(("hc_retrieve_metadata_download_callback:  err case 2"));
      return -1;
    }
    if (parsed_to != 0 && parsed_to + 1 != nbytes) {

      /* finished N/V metadata, now read System Record */
      transition_state((void *)handle, READING_SYSTEM_RECORD);
      err = xml_parse(handle->parser, buff + parsed_to, nbytes - parsed_to,
                      &parsed_to);
      if (err != HCERR_OK) {
	HC_ERR_LOG(("hc_retrieve_metadata_download_callback:  err case 3"));
	return -1;
      }
    }	/* End If (parsed_to != 0 && parsed_to + 1 != nbytes) */
  }	/* End If/Else (op_state(handle) == READING_SYSTEM_RECORD) */

  HC_DEBUG_IO_DETAIL(("hc_retrieve_metadata_download_callback:"
		      " downloaded %ld bytes (parsed_to="LL_FORMAT"), ending in state %s", 
		      nbytes, parsed_to, decode_state(op_state(handle))));

  return nbytes;
}

static hcerr_t make_metadata_arrays(hc_retrieve_metadata_handle_t *handle, hc_nvr_t **nvrp) {
  hc_nvr_t *nvr;
  hcerr_t res;

  res = hc_nvr_create(handle->session, handle->count, nvrp);
  nvr = *nvrp;	// may be null if res != HCERR_OK
  /* --> reverses order... */
  while (handle->top != NULL){
    hc_name_value_cell_t *current = handle->top;
    if (nvr != NULL) {
      hcerr_t err = hc_nvr_add_from_encoded_string(nvr, current->name, current->value,
						   current->v1_1);
      if (res == HCERR_OK) {
	if (err != HCERR_OK) {
	  HC_DEBUG(("make_metadata_arrays: remembering first received error: %d",
		    err));
	  res = err;
	}
      }
    }
    handle->top = handle->top->next;
    deallocator(current);
  }
  return res;
}

/* Caller is responsible to free names/values strings */
hcerr_t hc_internal_retrieve_metadata_close(hc_retrieve_metadata_handle_t *handle, 
					    hc_nvr_t **nvrp) {
  hcerr_t res;
  hcerr_t err;
  hc_int_session_t *session = handle->session;

  *nvrp = 0;
  res = hcoa_internal_retrieve_close(&handle->retrieve_handle, 
				     &session->response_code, session->errstr, session->errstr_len);

  err = make_metadata_arrays(handle, nvrp);
  if (res == HCERR_OK)
    res = err;
  xml_cleanup(handle->parser);
  hc_save_error_info(&handle->retrieve_handle.base,session);
  return res;
}


hcerr_t hc_retrieve_metadata_close(void *handle, hc_nvr_t **nvrp){

  hcerr_t err = hc_internal_retrieve_metadata_close((hc_retrieve_metadata_handle_t *) handle, nvrp);

  FREE_HANDLE(handle, hc_retrieve_metadata_handle_t, RETRIEVE_METADATA_HANDLE);
  return err;
}




/**************************************************************
 *          Retrieve Schema (Cache Configuration)             *
 **************************************************************/

hcerr_t hc_internal_retrieve_schema_create(hc_retrieve_schema_handle_t *handle, 
					   hc_int_session_t *session) {
  char retrieve_url[MAX_HTTP_URL_LEN];
  hc_oid oid;
  hcerr_t err = 0;
  hc_int_archive_t *arc = (hc_int_archive_t *) session->archive;
  cell_t *cell = &arc->default_cell;

  handle->session = session;
  hc_init_error_info(session);

  /* construct a retrieve url to use for the http get */
  if (snprintf(retrieve_url, MAX_HTTP_URL_LEN, 
               "%s://%s:%d/%s?%s=%s&binary=false",
	       HTTP_PROTOCOL_TAG, cell->addr, cell->port,
	       GET_CACHE_CONFIGURATION_URL, CACHE_PARAMETER, NVOA_ID) == MAX_HTTP_URL_LEN) {
    HC_ERR_LOG(("hcoa_internal_retrieve_schema_create:   url length exceeded internal maximum %d",
		MAX_HTTP_URL_LEN));
    return HCERR_URL_TOO_LONG;
  }

  /* Create the wrapped retrieve_handle, which manages the cURL connection */
  /* --> move to init function */

  FILL_INVALID_OID(oid); 

  hc_init_metadata_handle(handle, RETRIEVE_SCHEMA, RETRIEVE_SCHEMA_HANDLE, retrieve_url, oid);
  handle->value_tag = NVOA_SCHEMA_VALUE_TAG;
  handle->element_tag = HC_OA_ATTRIBUTE_TAG;

  /* initialize xml parser */
  err = xml_create(&retrieve_metadata_start_element_handler,
		   &retrieve_metadata_end_element_handler,
		   (void *) handle,
		   &(handle->parser), 
		   allocator, deallocator);

  return err;
}


hcerr_t hc_retrieve_schema_create(void **handle, hc_session_t *session) {
  hcerr_t res;
  ALLOCATOR(*handle, sizeof(hc_retrieve_schema_handle_t));
  memset(*handle, 0, sizeof (hc_retrieve_schema_handle_t));
  res = hc_internal_retrieve_schema_create((hc_retrieve_schema_handle_t *) *handle, 
					   (hc_int_session_t *) session);
  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}

/* Caller is responsible to free names/values strings */
hcerr_t hc_internal_retrieve_schema_close(hc_retrieve_schema_handle_t *handle){
  
  int i = 0;
  char **names;
  hc_type_t *types;
  int *lengths;
  hc_long_t count;
  hc_int_schema_t *schema;
  hc_int_session_t *session = handle->session;
  hcerr_t res;
  hcerr_t err;

  res = hcoa_internal_retrieve_close(&handle->retrieve_handle, 
				     &session->response_code, 
                                     session->errstr, 
                                     session->errstr_len);

  if (res == HCERR_OK && session->response_code == SERVER_SUCCESS) {
    count = handle->count;
    ALLOCATOR(names, sizeof(char*) * count);
    memset(names,0,sizeof(char*) * count);

    ALLOCATOR(types, sizeof(hc_type_t) * count);
    memset(types,0,sizeof(hc_type_t) * count);

    ALLOCATOR(lengths, sizeof(int) * count);
    memset(lengths, 0, sizeof(int) * count);

    /* reverses order...*/
    while (handle->top != NULL) {
      hc_name_value_cell_t *current = handle->top;
      
      HC_DEBUG_DETAIL(("     name=%s, type=%s",
		       current->name, current->value));
      ALLOCATOR(names[i], sizeof(char) * strlen(current->name) + 1);
      strcpy(names[i], current->name);

      lengths[i] = current->length;

      /* HC_LONG_TYPE, HC_BYTE_TYPE, HC_DOUBLE_TYPE, HC_STRING_TYPE*/
      if (strcmp(LONG_TYPE, current->value) == 0){
	types[i] = HC_LONG_TYPE;
      } else if (strcmp(STRING_TYPE, current->value) == 0){
	types[i] = HC_STRING_TYPE;
      } else if (strcmp(CHAR_TYPE, current->value) == 0){
        types[i] = HC_CHAR_TYPE;
      } else if (strcmp(DOUBLE_TYPE, current->value) == 0){
	types[i] = HC_DOUBLE_TYPE;
      } else if (strcmp(BYTE_TYPE, current->value) == 0){
	types[i] = HC_BYTE_TYPE;
      } else if (strcmp(BINARY_TYPE, current->value) == 0){
        types[i] = HC_BINARY_TYPE;
      } else if (strcmp(DATE_TYPE, current->value) == 0){
        types[i] = HC_DATE_TYPE;
      } else if (strcmp(TIMESTAMP_TYPE, current->value) == 0){
        types[i] = HC_TIMESTAMP_TYPE;
      } else if (strcmp(TIME_TYPE, current->value) == 0){
        types[i] = HC_TIME_TYPE;
      } else if (strcmp(OBJECTID_TYPE, current->value) == 0){
        types[i] = HC_OBJECTID_TYPE;
      } else {
	types[i] = HC_UNKNOWN_TYPE;
      }

      i++;
      handle->top = handle->top->next;
      deallocator(current);
    }	/* End While (handle->top != NULL) */

    /* Update the schema to contain the new information. */
    /* [???]  Eventually we may want to share the schema, in which
       case we need locking here. */ 
    if (session->schema == NULL) {
      ALLOCATOR(schema, sizeof(hc_int_schema_t));
      session->schema = (hc_schema_t *)schema;
      schema->names = names;
      schema->types = types;
      schema->lengths = lengths;
      schema->count = count;
    } else {
      /* Schema exists -- must update it.  */
      /* [???] May want to scan the schema for changes before
	 bothering to lock things down. */
      /* IF (something changed) { */
      schema = (hc_int_schema_t *)session->schema;
      deallocator(schema->names);
      deallocator(schema->types);
      deallocator(schema->lengths);
      schema->names = names;
      schema->types = types;
      schema->lengths = lengths;
      schema->count = count;
    /* } End IF */
    }	/* End If/Else (session->schema == NULL) */

  }	/* End If (session->response_code == SERVER_SUCCESS) */

  err = xml_cleanup(handle->parser);
  if (res == HCERR_OK)
    res = err;	/* In case there is a new error */

  hc_save_error_info(&handle->retrieve_handle.base,session);

  return res;
}


hcerr_t hc_retrieve_schema_close(void *handle) {

  hcerr_t err = hc_internal_retrieve_schema_close((hc_retrieve_schema_handle_t *)handle);

  FREE_HANDLE(handle, hc_retrieve_schema_handle_t, RETRIEVE_SCHEMA_HANDLE);
  return err;
}

/**************************************************************
 *              Store Metadata/Store Both                     *
 **************************************************************/

hcerr_t hc_internal_store_metadata_create(hc_store_metadata_handle_t *metadata_handle, 
                                          char *buffer,
                                          hc_long_t buffer_length,
					  hc_int_session_t *session,
                                          hc_long_t chunk_size_bytes, 
                                          hc_long_t window_size_chunks, 
                                          hc_oid oid){
  hcerr_t res;  
  char *version_name[] = {"value"};
  char *version_value[] = {"1.1"};

  HC_DEBUG(("hc_internal_store_metadata_create"));

  metadata_handle->session = session;
  metadata_handle->writer = NULL;
  hc_init_error_info(session);

  // if (hc_should_refresh_cells_info(session)) {
  //   /* Prime the pump to refresh the multicell info */
  //   require_ok(hcoa_archive_update_create(session->archive));
  // }

  res = hcoa_store_create_init_handle(&metadata_handle->store_handle,
				      session->archive,
				      &hc_store_metadata_upload_callback,
				      (void *)metadata_handle,
                                      -1, // cellid; -1 = use oid to choose
				      chunk_size_bytes, 
				      window_size_chunks,
				      oid);
  if (res != HCERR_OK) {
    return res;
  }

  handle_type(metadata_handle) |= STORE_METADATA_HANDLE;
  op_type(metadata_handle) = STORE_METADATA;
  transition_state(metadata_handle, ADDING_METADATA);

  metadata_handle->wbuffer = buffer;
  metadata_handle->wbuflen = buffer_length;

  metadata_handle->md_data_source_reader = NULL;
  metadata_handle->md_stream = NULL;

  session->response_code = 200;
  session->errstr[0] = '\0';
  memcpy(*&metadata_handle->oid, oid, sizeof(hc_oid));

  res = start_buffered_document(NVOA_TAG, 
				buffer,
				buffer_length,
				&(metadata_handle->writer),
				allocator,
				deallocator,
				TRUE);
  if (res != HCERR_OK) return res;

  require_ok(start_element(metadata_handle->writer, VERSION_TAG, version_name, version_value, 1));
  require_ok(end_element(metadata_handle->writer, VERSION_TAG));
  require_ok(start_element(metadata_handle->writer, HC_OA_ATTRIBUTES_TAG, 
                             attributes_names, attributes_values, 0));
  return HCERR_OK;
}

hcerr_t hc_store_metadata_create(void **metadata_handle, 
				 char *buffer,
				 hc_long_t  buffer_length,
				 hc_session_t *session,
				 hc_long_t chunk_size_bytes, 
				 hc_long_t window_size_chunks, 
				 hc_oid oid){

  hcerr_t res;

  ALLOCATOR(*metadata_handle, sizeof(hc_store_metadata_handle_t));
  memset(*metadata_handle, 0, sizeof (hc_store_metadata_handle_t));
  res = hc_internal_store_metadata_create((hc_store_metadata_handle_t *)*metadata_handle, 
					  buffer,
					  buffer_length,
					  (hc_int_session_t *) session,
					  chunk_size_bytes, 
					  window_size_chunks, 
					  oid);
  if (res != HCERR_OK) {
    /* [???] Should free metadata_handle->writer here, if non-null */
    deallocator(*metadata_handle);
    *metadata_handle = NULL;
  }
  return res;
}


hcerr_t hc_internal_store_both_create(hc_store_metadata_handle_t *metadata_handle, 
                                      char *buffer,
                                      hc_long_t buffer_length,
				      read_from_data_source ext_data_source_reader,
				      void *ext_stream,
				      hc_int_session_t *session,
                                      int cellid,
                                      hc_long_t chunk_size_bytes, 
                                      hc_long_t window_size_chunks){
  
  static char *version_name[] = {"value"};
  static char *version_value[] = {"1.1"};
  hcerr_t res;
  hc_oid link_oid;

  FILL_INVALID_OID(link_oid);
  
  metadata_handle->writer = NULL;
  metadata_handle->session = session;
  hc_init_error_info(session);

  if((res = hcoa_store_create_init_handle(&metadata_handle->store_handle,
					  session->archive,
					  &hc_store_metadata_upload_callback,
					  metadata_handle,
                                          cellid,
					  chunk_size_bytes, 
					  window_size_chunks,
					  link_oid)) != HCERR_OK) {
    return res;
  }
  handle_type(metadata_handle) |= STORE_METADATA_HANDLE;
  op_type(metadata_handle) = STORE_BOTH;
  transition_state(metadata_handle, ADDING_METADATA);

  metadata_handle->wbuffer = buffer;
  metadata_handle->wbuflen = buffer_length;

  metadata_handle->md_data_source_reader = ext_data_source_reader;
  metadata_handle->md_stream = ext_stream;

  session->response_code = 200;
  session->errstr[0] = '\0';

  res = start_buffered_document(NVOA_TAG, 
				buffer,
				buffer_length,
				&(metadata_handle->writer),
				allocator,
				deallocator,
				TRUE);
  if (res != HCERR_OK) return res;

  require_ok(start_element(metadata_handle->writer, VERSION_TAG, version_name, version_value, 1));
  require_ok(end_element(metadata_handle->writer, VERSION_TAG));
  require_ok(start_element(metadata_handle->writer, HC_OA_ATTRIBUTES_TAG, 
                             attributes_names, attributes_values, 0));
  return res;
}

hcerr_t hc_store_both_create(void **metadata_handle, 
			     char *buffer,
			     hc_long_t buffer_length,
			     read_from_data_source ext_data_source_reader,
			     void *ext_stream,
			     hc_session_t *session,
                             int cellid,
			     hc_long_t chunk_size_bytes, 
			     hc_long_t window_size_chunks){
  hcerr_t err;
  ALLOCATOR(*metadata_handle, sizeof (hc_store_metadata_handle_t));
  memset(*metadata_handle, 0, sizeof (hc_store_metadata_handle_t));
  err = hc_internal_store_both_create((hc_store_metadata_handle_t *)*metadata_handle, 
                                      buffer,
                                      buffer_length,
				      ext_data_source_reader,
				      ext_stream,
				      (hc_int_session_t *) session,
                                      cellid,
                                      chunk_size_bytes, 
                                      window_size_chunks);

  if (err != HCERR_OK) {
    /* [???] Should free xml_writer here if non null */
    deallocator(*metadata_handle);
    *metadata_handle = NULL;
  }
  return err;
}

hcerr_t hc_internal_add_metadata(hc_store_metadata_handle_t *metadata_handle, 
                                 char *name, 
                                 char *value){
  char *names[] = {HC_OA_ATTRIBUTE_NAME_TAG, HC_OA_ATTRIBUTE_VALUE_TAG};
  char *values[2], *name_enc;
  hcerr_t res;

  assert_handle(metadata_handle, STORE_METADATA_HANDLE, ANY_OP, ANY_STATE);

  // encode name (value is already encoded)
  res = encode64_to_string(0, name, &name_enc);
  if (res != HCERR_OK) return res;

  values[0] = name_enc;
  values[1] = value;
  HC_DEBUG(("hc_internal_add_metadata(name=%s, value=%s)",name,value));

  res = start_element(metadata_handle->writer, HC_OA_ATTRIBUTE_TAG, names, values, 2);
  deallocator(name_enc);
  if (res != HCERR_OK) {
    return res;
  }
  res = end_element(metadata_handle->writer, HC_OA_ATTRIBUTE_TAG);
  return res;
}

hcerr_t hc_add_metadata(void *metadata_handle, 
                        char *name, 
                        char *value){

  return hc_internal_add_metadata((hc_store_metadata_handle_t *)metadata_handle, 
                                  name, 
                                  value);
}

hcerr_t hc_add_metadata_date(void *metadata_handle,
                             hc_string_t name,
                             struct tm *value) {
  char	buffer[50];
  int res;

  /* Validate against schema: type(name)=long */
  res = hc_encode_date(buffer, value);
  if (res != HCERR_OK) 
    return res;
  return hc_internal_add_metadata((hc_store_metadata_handle_t *)metadata_handle,
                                  name, 
                                  buffer);
}
hcerr_t hc_add_metadata_time(void *metadata_handle,
                             hc_string_t name,
                             time_t value) {
  char  buffer[50];
  int res;
                                                                                
  /* Validate against schema: type(name)=long */
  res = hc_encode_time(buffer, value);
  if (res != HCERR_OK)
    return res;
  return hc_internal_add_metadata((hc_store_metadata_handle_t *)metadata_handle,                                  name,
                                  buffer);
}
hcerr_t hc_add_metadata_timestamp(void *metadata_handle,
                             hc_string_t name,
                             struct timespec *value) {
  char	buffer[50];
  int res;

  /* Validate against schema: type(name)=long */
  res = hc_encode_timestamp(buffer, value);
  if (res != HCERR_OK) 
    return res;
  return hc_internal_add_metadata((hc_store_metadata_handle_t *)metadata_handle,
                                  name, 
                                  buffer);
}

hcerr_t hc_add_metadata_long(void *metadata_handle, 
			     hc_string_t name, 
                             char tag,
			     hc_long_t value){

  char	buffer[50];	/* long enough for any long value printed representation */
  int res;

  /* Validate against schema: type(name)=long */
  res = hc_encode_long(buffer, tag, value);
  if (res != HCERR_OK) 
    return res;
  return hc_internal_add_metadata((hc_store_metadata_handle_t *)metadata_handle,
                                  name, 
                                  buffer);
}

hcerr_t hc_add_metadata_double(void *metadata_handle, 
			       hc_string_t name, 
			       hc_double_t value){

  char	buffer[50];	/* long enough for any floating point printed representation */
  int res;

  /* Validate against schema: type(name)=double */
  res = hc_encode_double(buffer, value);
  if (res != HCERR_OK) 
    return res;
  return hc_internal_add_metadata((hc_store_metadata_handle_t *)metadata_handle, 
                                  name, 
                                  buffer);
}
hcerr_t hc_add_metadata_string(void *metadata_handle, 
			       hc_string_t name, 
                               char tag,
			       hc_string_t value){
  hc_store_metadata_handle_t *handle = (hc_store_metadata_handle_t *)
                                         metadata_handle;
  hcerr_t res;
  int len;
  hc_string_t s;

  /* Validate against schema: type(name)=string or char */

  // make encoded version w/ tag char in front
  len = strlen(value);
  ALLOCATOR(s, (len/3+1)*4+2);
  encode64(len, (unsigned char *)value, tag, s);
  res = hc_internal_add_metadata(handle, 
                                  name, 
                                  s);
  deallocator(s);
  return res;
}

hcerr_t hc_add_metadata_bytearray(void *metadata_handle,
                                  char *name,
                                  char tag, 
                                  hc_bytearray_t *value) {
  hc_store_metadata_handle_t *handle = (hc_store_metadata_handle_t *)
                                         metadata_handle;
  char *buf;
  hcerr_t res;
  int out_len = value->len/3; // full blocks
  if (value->len % 3) 
    out_len++; // partial block
  out_len *= 4; // chars per block
  out_len++; // null
  if (tag)
    out_len++;
  ALLOCATOR(buf, out_len);
  encode64(value->len, value->bytes, tag, buf);
  res = hc_internal_add_metadata(handle, name, buf);
  deallocator(buf);
  return res;
}

hcerr_t hc_add_metadata_byte(void *metadata_handle, 
			     hc_string_t name, 
			     hc_byte_t value){

  char	buffer[50];	/* long enough for any byte printed representation */
  int res;

  /* Validate against schema: type(name)=byte */
  res = hc_encode_byte(buffer, value);
  if (res != HCERR_OK) 
    return res;
  return hc_internal_add_metadata((hc_store_metadata_handle_t *)metadata_handle, 
                                  name, 
                                  buffer);
}

hcerr_t hc_encode_long(char *buffer, char tag, hc_long_t value) {
  sprintf(buffer, "%c" LL_FORMAT, tag, value);
  return HCERR_OK;
}

hcerr_t hc_encode_double(char *buffer, hc_double_t value) {
  sprintf(buffer, "%c%llx", DOUBLE_TAG, value);
  return HCERR_OK;
}

hcerr_t hc_encode_date(char *buffer, struct tm *tm) {
  sprintf(buffer, "%c%d-%02d-%02d", DATE_TAG, 
                  1900+tm->tm_year, 1+tm->tm_mon, tm->tm_mday);
  return HCERR_OK;
}

hcerr_t hc_encode_time(char *buffer, time_t t) {
  struct tm tm;
  gmtime_r(&t, &tm);
  sprintf(buffer, "%c%02d:%02d:%02d", TIME_TAG,
          tm.tm_hour, tm.tm_min, tm.tm_sec);
  return HCERR_OK;
}

hcerr_t hc_encode_timestamp(char *buffer, struct timespec *ts) {
  struct tm tm;
  time_t timer;

  timer = ts->tv_sec;
  HC_DEBUG_DETAIL(("hc_encode_timestamp: time=%ld:%ld",
		   (long)ts->tv_sec,(long)ts->tv_nsec));
  gmtime_r(&timer, &tm);
  sprintf(buffer, "%c%d-%02d-%02dT%02d:%02d:%02d.%03dZ", TIMESTAMP_TAG,
          1900+tm.tm_year, 1+tm.tm_mon, tm.tm_mday,
          tm.tm_hour, tm.tm_min, tm.tm_sec, ts->tv_nsec / NSEC_MILLISEC_CONV);
  return HCERR_OK;
}

hcerr_t hc_encode_byte(char *buffer, hc_byte_t value) {

  if (value < -128 || value > 127)
    return HCERR_ILLEGAL_VALUE_FOR_METADATA;
  sprintf(buffer, "%d", value);  // Check this.
  return HCERR_OK;
}

hcerr_t hc_nvr_add_from_encoded_string(hc_nvr_t *pubnvr, 
				       char *encoded_name, 
				       char *encoded_value, 
				       int v1_1){
  hc_int_nvr_t *nvr = (hc_int_nvr_t *)pubnvr;
  hcerr_t res;
  hc_value_t tmp_value = HC_EMPTY_VALUE_INIT;
  hc_type_t expected_type;
  char *name;
  validate_nvr (nvr);
  if (encoded_name == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  if (v1_1 == 0) {
    name = encoded_name;
  } else {
    res = decode64_to_string(encoded_name, &name);
    if (res != HCERR_OK) {
      return res;
    }
  }

  res = hc_schema_get_type(Session_schema(nvr->session),
			   name, &expected_type);
  if (res != HCERR_OK) 
    return res;

  if (v1_1) {
    res = hc_decode_value1_1(encoded_value, &tmp_value, expected_type);
  } else {
    res = hc_decode_value(encoded_value, &tmp_value, expected_type);
  }
  if (res != HCERR_OK) 
    return res;

  HC_DEBUG_IO_DETAIL(("hc_nvr_add_from_encoded_string: (v=%d)"
		      " encoded_name=%s => name=%s"
		      " encoded_value=%s => actual_type=%s",
		      v1_1,encoded_name,name,
		      encoded_value,
		      hc_decode_hc_type(tmp_value.hcv_type)));

  res = hc_nvr_add_value(pubnvr, name, tmp_value);
  if (v1_1)
    deallocator(name);

  return res;
}


extern char base64_errstr[];

hcerr_t decode64_to_string(char *src, char **dest) {
  int len, buflen;

  len = strlen(src);
  if (len < 4) { // 1 block
    HC_ERR_LOG(("decode64_to_string illegal length %d"
		" HCERR_XML_MALFORMED_XML", len));
    return HCERR_XML_MALFORMED_XML;
  }
  buflen = 3 * len / 4 + 1;  // max

  ALLOCATOR(*dest, buflen);
  buflen = decode64(src, (unsigned char *)*dest);
  if (buflen == -1) {
    deallocator(*dest);
    *dest = NULL;
    HC_ERR_LOG(("decode64_to_string buffer %s HCERR_XML_MALFORMED_XML %s",
		src,
		base64_errstr));
    return HCERR_XML_MALFORMED_XML;
  }
  (*dest)[buflen] = '\0'; // null terminate

  return HCERR_OK;
}

hcerr_t encode64_to_string(char prefix, char *src, char **dest) {
  int len, buflen;

  len = strlen(src);
  buflen = 4 * (len / 3 + 1) + 1;
  if (prefix != 0)
    buflen++;
  ALLOCATOR(*dest, buflen);
  encode64(len, (unsigned char *)src, prefix, *dest);

  return HCERR_OK;
}

/*
 *  decode values from strings passed by server with encoding=1.1
 */
hcerr_t hc_decode_value1_1(char *buffer, hc_value_t *valuep, 
			   hc_type_t expected_type) {
  hc_value_t tmp_value = HC_EMPTY_VALUE_INIT;
  hcerr_t res;
  char tag;

  memset(&tmp_value, 0, sizeof(tmp_value));

  HC_DEBUG_IO_DETAIL(("hc_decode_value_1_1: translating received string '%s'"
		      " to expected type %s",
		      buffer,
		      hc_decode_hc_type(expected_type)));

  // skip type code
  tag = *buffer++;
  tmp_value.hcv_type = expected_type;

  switch (tag) {
  case STRING_TAG:
  case CHAR_TAG:
    if (expected_type != HC_STRING_TYPE && expected_type != HC_CHAR_TYPE) {
      HC_ERR_LOG(("Don't know how to convert STRING/CHAR to expected type %d",
		  expected_type));
      return HCERR_XML_MALFORMED_XML;
    }
    res = decode64_to_string(buffer, &tmp_value.hcv.hcv_string);
    if (res != HCERR_OK) return res;
    HC_DEBUG_IO_DETAIL(("hc_decode_value (string/char): input=%s output=%s",
			buffer,
			tmp_value.hcv.hcv_string));
    break;
  case LONG_TAG:
    if (expected_type != HC_LONG_TYPE) {
      HC_ERR_LOG(("Don't know how to convert LONG to expected type %d",
		  expected_type));
      return HCERR_XML_MALFORMED_XML;
    }
    if (!hc_isnum(buffer)) {
      HC_ERR_LOG(("hc_decode_value: illegal long value: %s",buffer));
      return HCERR_XML_MALFORMED_XML;
    }
    tmp_value.hcv.hcv_long = hc_atol(buffer);
    HC_DEBUG_IO_DETAIL(("hc_decode_value (long): input=%s output="LL_FORMAT,
			buffer,
			tmp_value.hcv.hcv_long));
    break;
  case DATE_TAG:
    if (expected_type != HC_DATE_TYPE) {
      HC_ERR_LOG(("Don't know how to convert DATE to expected type %d",
		  expected_type));
      return HCERR_XML_MALFORMED_XML;
    }
    if (sscanf(buffer, "%d-%d-%d", 
	       &tmp_value.hcv.hcv_tm.tm_year,
	       &tmp_value.hcv.hcv_tm.tm_mon,
	       &tmp_value.hcv.hcv_tm.tm_mday) != 3) {
      HC_ERR_LOG(("hc_decode_value: illegal date value: %s",buffer));
      return HCERR_XML_MALFORMED_XML;
    }
    tmp_value.hcv.hcv_tm.tm_year -= 1900;
    tmp_value.hcv.hcv_tm.tm_mon -= 1;
    HC_DEBUG_IO_DETAIL(("hc_decode_value (date): input=%s output='%d-%d-%d'",
			buffer,
			tmp_value.hcv.hcv_tm.tm_year,
			tmp_value.hcv.hcv_tm.tm_mon,
			tmp_value.hcv.hcv_tm.tm_mday));
    break;
  case TIMESTAMP_TAG: {
    struct tm tm;
    long msec;
    if (expected_type != HC_TIMESTAMP_TYPE) {
      HC_ERR_LOG(("Don't know how to convert TIMESTAMP to expected type %d"
		  "->HCERR_XML_MALFORMED_XML",
		  expected_type));
      return HCERR_XML_MALFORMED_XML;
    }
    memset(&tm, 0, sizeof(tm));
    if (sscanf(buffer, "%d-%d-%dT%d:%d:%d.%dZ", 
	       &tm.tm_year, &tm.tm_mon, &tm.tm_mday,
	       &tm.tm_hour, &tm.tm_min, &tm.tm_sec,
	       &msec) != 7) {
      HC_ERR_LOG(("hc_decode_value: illegal timestamp value: %s"
		  "->HCERR_XML_MALFORMED_XML",
		  buffer));
      return HCERR_XML_MALFORMED_XML;
    }
    tm.tm_year -= 1900;
    tm.tm_mon -= 1;
    tmp_value.hcv.hcv_timespec.tv_sec = mkgmtime(&tm);	/* see mktime.c in this directory */
    tmp_value.hcv.hcv_timespec.tv_nsec = msec * NSEC_MILLISEC_CONV;
    HC_DEBUG_IO_DETAIL(("hc_decode_value (timestamp): input=%s (%d-%d-%dT%d:%d:%d.%d) output=%ld:%ld",
			buffer,
			tm.tm_year, tm.tm_mon, tm.tm_mday,
			tm.tm_hour, tm.tm_min, tm.tm_sec, 
			msec,
			tmp_value.hcv.hcv_timespec.tv_sec,
			tmp_value.hcv.hcv_timespec.tv_nsec));
    break;
  }
  case TIME_TAG: {
    struct tm tm;
    if (expected_type != HC_TIME_TYPE) {
      HC_ERR_LOG(("Don't know how to convert TIME to expected type %d"
		  "->HCERR_XML_MALFORMED_XML",
		  expected_type));
      return HCERR_XML_MALFORMED_XML;
    }
    memset(&tm, 0, sizeof(tm));
    if (sscanf(buffer, "%d:%d:%d",
	       &tm.tm_hour, &tm.tm_min, &tm.tm_sec) != 3) {
      HC_ERR_LOG(("hc_decode_value: illegal time on-the-wire value: '%s'"
		  "->HCERR_XML_MALFORMED_XML",
		  buffer));
      return HCERR_XML_MALFORMED_XML;
    }
    tmp_value.hcv.hcv_timespec.tv_sec = tm.tm_sec + 60 * tm.tm_min +
                                        3600 * tm.tm_hour;
    tmp_value.hcv.hcv_timespec.tv_nsec = 0;
    HC_DEBUG_IO_DETAIL(("hc_decode_value (time): input=%s output=%ld",
			buffer,tmp_value.hcv.hcv_timespec.tv_sec));
    break;
  }
  case DOUBLE_TAG:
    if (expected_type != HC_DOUBLE_TYPE) {
      HC_ERR_LOG(("Don't know how to convert DOUBLE to expected type %d"
		  "->HCERR_XML_MALFORMED_XML",
		  expected_type));
      return HCERR_XML_MALFORMED_XML;
    }
    if (sscanf(buffer, "%llx", &tmp_value.hcv.hcv_double) != 1) {
      HC_ERR_LOG(("hc_decode_value: illegal double on-the-wire value: %s",
		  "->HCERR_XML_MALFORMED_XML",
		  buffer));
      return HCERR_XML_MALFORMED_XML;
    }
    HC_DEBUG_IO_DETAIL(("hc_decode_value(double): input=%s output=%.17g",
			buffer,tmp_value.hcv.hcv_double));
    break;
  case OBJECTID_TAG:
  case BINARY_TAG: {
    int len, buflen;
    unsigned char *uc_buf;
    if (expected_type != HC_BINARY_TYPE && expected_type != HC_OBJECTID_TYPE) {
      HC_ERR_LOG(("Don't know how to convert BINARY to expected type %d"
		  "->HCERR_XML_MALFORMED_XML",
		  expected_type));
      return HCERR_XML_MALFORMED_XML;
    }
    len = strlen(buffer);
    if (len < 4) { // 1 block
      HC_ERR_LOG(("hc_decode_value: %d is not enough bytes"
		  " to represent binary value",
		  len))
      return HCERR_XML_MALFORMED_XML;
    }
    buflen = 3 * len / 4;  // max
    ALLOCATOR(uc_buf, buflen);
    buflen = decode64(buffer, uc_buf);
    if (buflen == -1) {
    HC_ERR_LOG(("decode64 binary value %s HCERR_XML_MALFORMED_XML %s",
		buffer,
		base64_errstr));
      deallocator(uc_buf);
      return HCERR_XML_MALFORMED_XML;
    }
    tmp_value.hcv.hcv_bytearray.bytes = uc_buf;
    tmp_value.hcv.hcv_bytearray.len = buflen;
    break;
/* %% */
  }
  default:
    HC_ERR_LOG(("hc_decode_value1_1: unexpected type tag %d with expected_type=%d", 
		tag,expected_type));
    return HCERR_XML_MALFORMED_XML;
  }
  *valuep = tmp_value;
  return HCERR_OK;
}

/* decode values from client or stored in server pre-1.1 */
hcerr_t hc_decode_value(char *buffer, hc_value_t *valuep, hc_type_t type) {
  char *endp = NULL;
  hc_value_t tmp_value = HC_EMPTY_VALUE_INIT;
  hcerr_t res;

  tmp_value.hcv_type = type;

  switch (type) {
  case HC_STRING_TYPE:
  case HC_CHAR_TYPE:
    res = hc_copy_string(buffer, &tmp_value.hcv.hcv_string);
    if (res != HCERR_OK) return res;
    break;
  case HC_LONG_TYPE:
    if (!hc_isnum(buffer)) {
      HC_ERR_LOG(("hc_decode_value (v1.0): LONG value '%s' is"
		  " incorrect format",buffer));
      return HCERR_XML_MALFORMED_XML;
    }
    tmp_value.hcv.hcv_long = hc_atol(buffer);
    break;
  case HC_DOUBLE_TYPE:
    tmp_value.hcv.hcv_double = strtod(buffer, &endp);
    if (endp != buffer + strlen(buffer)) {
      HC_ERR_LOG(("hc_decode_value (v1.0): DOUBLE value '%s' is"
		  " incorrect format",buffer));
      return HCERR_XML_MALFORMED_XML;
    }
    break;
  case HC_DATE_TYPE:
    if (sscanf(buffer, "%d-%d-%d", 
	       &tmp_value.hcv.hcv_tm.tm_year,
	       &tmp_value.hcv.hcv_tm.tm_mon,
	       &tmp_value.hcv.hcv_tm.tm_mday) != 3) {
      HC_ERR_LOG(("hc_decode_value: illegal date value: %s",buffer));
      return HCERR_XML_MALFORMED_XML;
    }
    tmp_value.hcv.hcv_tm.tm_year -= 1900;
    tmp_value.hcv.hcv_tm.tm_mon -= 1;
    break;
  case HC_TIMESTAMP_TYPE: {
    struct tm tm;
    long msec;
    int fields;
    memset(&tm, 0, sizeof(tm));
    if ((fields=sscanf(buffer, "%d-%d-%dT%d:%d:%d.%dZ", 
	       &tm.tm_year, &tm.tm_mon, &tm.tm_mday,
	       &tm.tm_hour, &tm.tm_min, &tm.tm_sec,
	       &msec)) != 7) {
      HC_ERR_LOG(("hc_decode_value: illegal timestamp value: %s"
		  "->HCERR_XML_MALFORMED_XML",
		  buffer));
      return HCERR_XML_MALFORMED_XML;
    }
    tm.tm_year -= 1900;
    tm.tm_mon -= 1;
    tmp_value.hcv.hcv_timespec.tv_sec = mkgmtime(&tm);	// see mktime.c 
    tmp_value.hcv.hcv_timespec.tv_nsec = msec * NSEC_MILLISEC_CONV;
    break;
  }
  case HC_TIME_TYPE: {
    struct tm tm;
    memset(&tm, 0, sizeof(tm));
    if (sscanf(buffer, "%d:%d:%d",
	       &tm.tm_hour, &tm.tm_min, &tm.tm_sec) != 3) {
      HC_ERR_LOG(("hc_decode_value: illegal time on-the-wire value: '%s'"
		  "->HCERR_XML_MALFORMED_XML",
		  buffer));
      return HCERR_XML_MALFORMED_XML;
    }
    tmp_value.hcv.hcv_timespec.tv_sec = tm.tm_sec + 60 * tm.tm_min +
                                        3600 * tm.tm_hour;
    tmp_value.hcv.hcv_timespec.tv_nsec = 0;
    break;
  }
  case HC_BINARY_TYPE:
  case HC_OBJECTID_TYPE:
    res = hc_from_hex_string(buffer,
			     &tmp_value.hcv.hcv_bytearray.len,
			     &tmp_value.hcv.hcv_bytearray.bytes);
    if (res != HCERR_OK) 
      return res;
    break;

  default:
    HC_ERR_LOG(("hc_decode_value (v1.0): don't know how"
		" to decode type %d",type));
    return HCERR_XML_MALFORMED_XML;
  }
  *valuep = tmp_value;
  return HCERR_OK;
}

hcerr_t hc_decode_long(char *buffer, hc_long_t *valuep) {
  *valuep = hc_atol(buffer);
  return HCERR_OK;
}
hcerr_t hc_decode_double(char *buffer, int hex, hc_double_t *valuep) {

  if (hex) {
    sscanf(buffer, "%llx", valuep);
  } else {
    // legacy 
    char *endp = NULL;
    *valuep = strtod(buffer,&endp);
    if (endp != buffer + strlen(buffer)) {
      HC_ERR_LOG(("hc_decode_double: illegal double value %s hex=%d HCERR_XML_MALFORMED_XML",
		  buffer,hex));
      return HCERR_XML_MALFORMED_XML;
    }
  }
  return HCERR_OK;
}
hcerr_t hc_decode_byte(char *buffer,hc_byte_t *valuep) {
  *valuep = atoi(buffer);
  return HCERR_OK;
}




hcerr_t hc_store_metadata_worker(hc_store_metadata_handle_t *handle, 
                                 int *finishedp){
  hcerr_t res;

  assert_handle(handle, STORE_METADATA_HANDLE, ANY_OP, ANY_STATE);

  if (op_state(handle) == ADDING_METADATA) {
    res = hc_store_metadata_start_transmit(handle);
    if (res != HCERR_OK) {
      return res;
    }
  }

  res = hcoa_io_worker(handle, finishedp);
  return res;
}

hcerr_t hc_store_metadata_start_transmit(hc_store_metadata_handle_t *metadata_handle){
  hcerr_t res = HCERR_OK;  
  hc_int_archive_t *arc;
  hcerr_t err = HCERR_OK;
  hc_long_t metadata_len;
  cell_t *cell;

  /* Finished adding metadata, ready to initialize connection and start storing... */

  arc = metadata_handle->store_handle.base.archive;

  assert(op_state(metadata_handle) == ADDING_METADATA);

  /* Close XML document */
  require_ok(end_element(metadata_handle->writer, HC_OA_ATTRIBUTES_TAG));
  require_ok(end_document_and_close(metadata_handle->writer, &metadata_len));

  
  /* construct a store url to use for the http post */
  if (op_type(metadata_handle) == STORE_BOTH){
    //%%MULTICELL
    if (metadata_handle->store_handle.cellid == -1) {
      // allocate cell
      cell = get_store_cell(arc);
      if (cell == NULL) {
        HC_ERR_LOG(("hc_store_metadata_start_transmit: multicell out of space"));
        return HCERR_PLATFORM_GENERAL_ERROR;
      }
    } else {
      cell = lookup_cell(arc, metadata_handle->store_handle.cellid);
      if (cell == NULL) {
        HC_ERR_LOG(("hc_store_metadata_start_transmit: cell does not exist"));
        return HCERR_BAD_REQUEST;
      }
    }
    if (snprintf(metadata_handle->store_handle.base.url, MAX_HTTP_URL_LEN, 
		 "%s://%s:%d/%s?%s=%s&%s=" LL_FORMAT, HTTP_PROTOCOL_TAG, 
		 cell->addr, cell->port, 
		 STORE_BOTH_URL, CACHE_PARAMETER, NVOA_ID,
		 METADATA_LENGTH_PARAMETER, metadata_len) == MAX_HTTP_URL_LEN) {
      HC_ERR_LOG(("hc_store_metadata_start_transmit:   url length exceeded internal maximum %d",
		  MAX_HTTP_URL_LEN));
      return HCERR_URL_TOO_LONG;
    }

  } else {
    /* STORE_METADATA */
    //%%MULTICELL
    cell_id_t cellId = get_cell_id(metadata_handle->oid);
    cell = get_cell(arc, cellId);
    if (cell == NULL) {
      HC_ERR_LOG(("hc_store_metadata_start_transmit: failed to"
		  " find cell %d for oid %s", 
		  cellId, metadata_handle->oid));
      return HCERR_PLATFORM_GENERAL_ERROR;
    }
    HC_DEBUG_DETAIL(("store_metadata: Cell id %d => %s:%d", cellId, 
                                      cell->addr, cell->port));
    if (snprintf(metadata_handle->store_handle.base.url, MAX_HTTP_URL_LEN, 
		 "%s://%s:%d/%s?%s=%s&%s=%s", HTTP_PROTOCOL_TAG,
		 cell->addr, cell->port, 
		 STORE_METADATA_URL, 
		 ID_PARAMETER, metadata_handle->oid,
		 CACHE_PARAMETER, NVOA_ID) == MAX_HTTP_URL_LEN) {
      HC_ERR_LOG(("hc_store_metadata_start_transmit:   url length exceeded internal maximum %d",
		  MAX_HTTP_URL_LEN));
      return HCERR_URL_TOO_LONG;
    }

  }	/* End If/Else (op_type(metadata_handle) == STORE_BOTH) */

  /* Make connection for upload */
  err = hcoa_store_create_init_http(&metadata_handle->store_handle);
  if (err != HCERR_OK){
    HC_ERR_LOG(("hc_internal_store_metadata: hc_store_create_init_http returned error code %d",res));
    return err;
  }
  metadata_handle->store_handle.base.wbuf = metadata_handle->store_handle.base.wend = metadata_handle->wbuffer;
  metadata_handle->store_handle.base.wbuflen = metadata_len;

  HC_DEBUG_IO_DETAIL(("hc_store_metadata_start_transmit: sending metadata (len=%d): %.*s",
		      (int)metadata_len, (int)metadata_len, metadata_handle->store_handle.base.wbuf));
  /* Buffer with XML representation of metadata ready to upload */
  transition_state(metadata_handle,  STORING_METADATA);

  return HCERR_OK;
}	

long hc_store_metadata_upload_callback(void *context,
				       char *buffer, 
				       long size) {
  hc_store_metadata_handle_t *handle = (hc_store_metadata_handle_t*) context;
  long bytes_written;
 
  assert_handle(handle, STORE_METADATA_HANDLE, ANY_OP, ANY_STATE);

  if (op_state(handle) == STORING_METADATA) {
    bytes_written = hcoa_store_upload_from_buffer(context, buffer, size);
    if (bytes_written != 0) {
      return bytes_written;
    } 
    HC_DEBUG_DETAIL(("hc_store_metadata_upload_callback: done storing metadata"));
    if (op_type(handle) == STORE_METADATA){
      return 0;
    }
    assert(op_type(handle) == STORE_BOTH);
    transition_state(handle,STORING_DATA);
  }
  assert(op_state(handle) == STORING_DATA);


  bytes_written = (*handle->md_data_source_reader)(handle->md_stream, buffer, size);
  HC_DEBUG_DETAIL(("hcoa_store_metadata_upload_callback: read %ld bytes from client",
	    bytes_written));

  return bytes_written;
}

hcerr_t hc_internal_store_metadata_close(hc_store_metadata_handle_t *handle, 
                                         hc_system_record_t *sysrec){
  hc_int_session_t *session = handle->session;
  hcerr_t res;

 res = hcoa_internal_store_close(&handle->store_handle,
				 &session->response_code, 
                                 session->errstr, 
                                 session->errstr_len,
				 sysrec);

 hc_save_error_info(&handle->store_handle.base,session);

 return res;

}

hcerr_t hc_store_metadata_close(void *handle, hc_system_record_t *sysrec){

  hcerr_t err;

  HC_DEBUG(("hc_store_metadata_close"));
  validate_handle(handle, STORE_METADATA_HANDLE, ANY_OP, ANY_STATE);
  err = hc_internal_store_metadata_close((hc_store_metadata_handle_t *)handle,
						 sysrec);
  FREE_HANDLE(handle, hc_store_metadata_handle_t, STORE_METADATA_HANDLE);
  return err;
}


/**************************************************************
 *                         Query                              *
 **************************************************************/



#define RESULT_COUNT 500

static hcerr_t hc_query_init_record_set(hc_query_handle_t *handle, int init_cellid){
  HC_DEBUG_DETAIL(("hc_query_init_record_set: handle=0x%lx, init_cellid=%d",(long)handle,init_cellid));
  handle->honeycomb_cookie = NULL;
  handle->response_count = 0;
  ALLOCATOR(handle->record_attributes_count, sizeof(int) * RESULT_COUNT);
  handle->attribute_count_list_size = RESULT_COUNT;
  handle->current_record = 0;
  transition_state(handle, QUERY_RETRIEVING_RESULTS);

  return HCERR_OK;
}


static hcerr_t query_start_element_handler (char *element_name, void *data, 
                                            char **names, char **values, 
                                            int n){

  hcerr_t res = HCERR_OK;
  hc_query_handle_t *handle = (hc_query_handle_t *) data;
  int i = 0;
  char *query_time_str;
  hc_long_t chunk_query_integrity_time;	/* from latest chunk */


  if (strcmp(element_name, handle->retrieve_metadata_handle.element_tag) == 0){
    handle->response_count++;
    /* Delegate to the metadata parser */
    res = retrieve_metadata_start_element_handler (element_name, &handle->retrieve_metadata_handle, names, values, n);
  }
  else if (strcmp(element_name, HC_OA_QUERY_RESULTS_COOKIE_TAG) == 0){
    /* <Cookie value="foobar" /> */
    for (i = 0; i < n; i++){
      if (strcmp(*(names + i), handle->retrieve_metadata_handle.value_tag) == 0){
	handle->honeycomb_cookie = *(values + i);
      }
    }
  }  else if (strcmp(element_name, HC_OA_QUERY_INTEGRITY_TIME_TAG) == 0){
    /* <Query-Integrity-Time value="1234567890" /> */
    for (i = 0; i < n; i++){
      if (strcmp(*(names + i), handle->retrieve_metadata_handle.value_tag) == 0){
	query_time_str = *(values + i);
	res = hc_decode_long(query_time_str,
			     &chunk_query_integrity_time);
	HC_DEBUG_DETAIL(("query_start_element_handler: parsed"
			 " new query integrity time="LL_FORMAT
			 " old query integrity time="LL_FORMAT,
			 chunk_query_integrity_time,
			 handle->query_integrity_time));
	if (chunk_query_integrity_time <
	    handle->query_integrity_time)
	  handle->query_integrity_time = chunk_query_integrity_time;
      }
    }
  }
  return res;
}


static hcerr_t query_plus_start_element_handler (char *element_name, 
                                                 void *data, 
                                                 char **names, char **values, 
                                                 int n){
  hcerr_t res = HCERR_OK;
  hc_query_handle_t *handle = (hc_query_handle_t *) data;
  int i = 0;
  char *query_time_str;
  hc_long_t chunk_query_integrity_time;	/* from latest chunk */

  HC_DEBUG_DETAIL(("query_plus_start_element_handler: element_name=%s n=%d",element_name,n));

  if (strcmp(element_name, handle->retrieve_metadata_handle.element_tag) == 0) {
    /* Delegate to metadata parser */
    handle->retrieve_metadata_handle.v1_1 = 1;
    res = retrieve_metadata_start_element_handler (element_name, &handle->retrieve_metadata_handle, names, values, n);
  }
  else if (strcmp(element_name, HC_OA_QUERY_RESULTS_COOKIE_TAG) == 0){
    for (i = 0; i < n; i++){
      if (strcmp(*(names + i), handle->retrieve_metadata_handle.value_tag) == 0){
	handle->honeycomb_cookie = *(values + i);
      }
    }
  }
  else if (strcmp(element_name, HC_OA_QUERY_INTEGRITY_TIME_TAG) == 0){
    for (i = 0; i < n; i++){
      if (strcmp(*(names + i), handle->retrieve_metadata_handle.value_tag) == 0){
	query_time_str = *(values + i);
	res = hc_decode_long(query_time_str,
			     &chunk_query_integrity_time);
	HC_DEBUG_DETAIL(("query_start_element_handler: parsed"
			 " new query integrity time="LL_FORMAT
			 " old query integrity time="LL_FORMAT,
			 chunk_query_integrity_time,
			 handle->query_integrity_time));
	if (chunk_query_integrity_time <
	    handle->query_integrity_time)
	  handle->query_integrity_time = chunk_query_integrity_time;
      }
    }
  }
  return res;
}


static hcerr_t query_end_element_handler (char *element_name, void *data){
  return HCERR_OK;
}

#ifdef	DEBUG_QUEUES
/* Check that the table of counts adds up to the length of the linked
   list. */
static int query_plus_response_list_length(hc_query_handle_t *handle) {
  int i;
  int count = 0;

  for (i = handle->current_record; i < handle->response_count;i++) {
    count += handle->record_attributes_count[i];
  }
  return count;
}
#endif	/* DEBUG_QUEUES */


static hcerr_t query_plus_end_element_handler (char *element_name, void *data){
  hc_query_handle_t *handle = (hc_query_handle_t*) data;

  queue_assert(query_plus_response_list_length(handle)+handle->retrieve_metadata_handle.count==element_chain_length(&handle->retrieve_metadata_handle));

  if (strcmp(element_name, QUERY_PLUS_RESULT_TAG) == 0){
    HC_DEBUG_DETAIL(("query_plus_end_element_handler: Ending one more result:"
	      " handle->response_count=%d new item count %d",
	      handle->response_count,
	      (int)handle->retrieve_metadata_handle.count));
    /* Check for overflow */
    if (handle->response_count == handle->attribute_count_list_size){
      int new_size = handle->attribute_count_list_size * 2;
      int i;
      int *newtable = reallocator (handle->record_attributes_count,
				    new_size * sizeof(int*));
      if (newtable == NULL) {
	HC_ERR_LOG(("ERROR: query_plus_end_element_handler:   out of memory"));
	return HCERR_OOM;
      }
      handle->record_attributes_count = newtable;
      for (i = handle->response_count; i < new_size; i++) {
	handle->record_attributes_count[i] = -1;
      }
      handle->attribute_count_list_size = new_size;
    }	/* End If (handle->response_count==...) */
    
    /* Note how many attributes supplied for this record */
    handle->record_attributes_count[handle->response_count] = handle->retrieve_metadata_handle.count;
    handle->retrieve_metadata_handle.count = 0;
    handle->response_count++;
    queue_assert(query_plus_response_list_length(handle)==element_chain_length(&handle->retrieve_metadata_handle));
  }
  return HCERR_OK;
}


static hcerr_t populate_query_body(hc_query_handle_t *handle, char *header, char *query){
  require_ok(add_request_header(session_ptr(handle), HC_QUERY_BODY_CONTENT_HEADER, header));

/*   sprintf(content_length, "%d", strlen(query)); */
/*   require_ok(add_request_header(session_ptr(handle), "Content-Length", content_length)); */
  ((hcoa_base_handle_t *)handle)->wbuf = ((hcoa_base_handle_t *)handle)->wend = query;
  ((hcoa_base_handle_t *)handle)->wbuflen = strlen(query);

  return HCERR_OK;
}


static int count_char(const char *str, char c) {
  int i, ct = 0;
  for (i=0; str[i] != '\0'; i++)
    if (str[i] == c)
      ct++;
  return ct;
}

static int make_param(char **p, int index, char *val) {
  int plen = 30; // strlen <parameter index="" value=""/>
  plen += 10 + strlen(val); // 10 = overkill for index
  ALLOCATOR(*p, plen);
  return sprintf(*p, "<parameter index=\"%d\" value=\"%s\"/>\n", index, val);
}

static hcerr_t pstmt_to_xml(hc_int_pstmt_t *pstmt, char *selects[], int n_selects, char **ptr) {
  char *query_enc, **args, **enc_selects, *xml, *p;
  hcerr_t res;
  hc_query_binding_t *b;
  int n_bindings = 0;
  int i, len = 0;

  *ptr = NULL;

  len = strlen(XML_HDR) + 1;

  HC_DEBUG_DETAIL(("q: [%s]\n", pstmt->query));
  res = encode64_to_string('S', pstmt->query, &query_enc);
  if (res != HCERR_OK)
    return res;
  HC_DEBUG_DETAIL(("q_enc: [%s]\n", query_enc));

  len += 60 + strlen(query_enc); // includes begin & end of <Prepared-Statement

  //
  //  make <parameter> elements
  //
  b = pstmt->bindings;
  while (b != NULL) {
    n_bindings++;
    b = b->next;
  }
  if (n_bindings > 0) {
    ALLOCATOR(args, n_bindings * sizeof(char *));
    b = pstmt->bindings;
    for (i=0; i<n_bindings; i++) {
      char buffer[2048];
      hcerr_t res;
      switch (b->value.hcv_type) {
        case HC_LONG_TYPE:
          hc_encode_long(buffer, LONG_TAG, b->value.hcv.hcv_long);
          len += make_param(&args[i], b->index, buffer);
          break;
        case HC_DOUBLE_TYPE:
          hc_encode_double(buffer, b->value.hcv.hcv_double);
          len += make_param(&args[i], b->index, buffer);
          break;
        case HC_DATE_TYPE:
          hc_encode_date(buffer, &b->value.hcv.hcv_tm);
          len += make_param(&args[i], b->index, buffer);
          break;
        case HC_TIME_TYPE:
          hc_encode_time(buffer, b->value.hcv.hcv_timespec.tv_sec);
          len += make_param(&args[i], b->index, buffer);
          break;
        case HC_TIMESTAMP_TYPE:
          hc_encode_timestamp(buffer, &b->value.hcv.hcv_timespec);
          len += make_param(&args[i], b->index, buffer);
          break;
        case HC_CHAR_TYPE: {
          char *s;
          res = encode64_to_string(0, b->value.hcv.hcv_string, &s);
          if (res != HCERR_OK)
            return res;
          ALLOCATOR(args[i], 40 + strlen(s));
          len += sprintf(args[i], "<parameter index=\"%d\" value=\"C%s\"/>\n", 
                         b->index, s);
          deallocator(s);
          break;
        }
        case HC_STRING_TYPE: {
          char *s;
          res = encode64_to_string(0, b->value.hcv.hcv_string, &s);
          if (res != HCERR_OK)
            return res;
          ALLOCATOR(args[i], 40 + strlen(s));
          len += sprintf(args[i], "<parameter index=\"%d\" value=\"S%s\"/>\n", 
                         b->index, s);
          deallocator(s);
          break;
        }
        case HC_OBJECTID_TYPE:
        case HC_BINARY_TYPE: {
          char *buf;
          ALLOCATOR(buf, (b->value.hcv.hcv_bytearray.len/3+1)*4+2);
          encode64(b->value.hcv.hcv_bytearray.len, 
                   b->value.hcv.hcv_bytearray.bytes, BINARY_TAG, buf);
          ALLOCATOR(args[i], 40 + strlen(buf));
          len += sprintf(args[i], "<parameter index=\"%d\" value=\"%s\"/>\n",
                         b->index, buf);
          deallocator(buf);
          break;
        }
        default:
          HC_ERR_LOG(("TYPE NOT IMPL: %d\n", b->value.hcv_type));
	  assert(FALSE);
          return HCERR_NO_SUCH_TYPE;
      }
      len += 2; // for indent
      b = b->next;
    }
  }

  if (n_selects > 0) {
    //
    //  make <select> elements
    //
    ALLOCATOR(enc_selects, n_selects * sizeof(char *));

    for (i=0; i<n_selects; i++) {
      char *s;
      res = encode64_to_string(0, selects[i], &s);
      if (res != HCERR_OK)
        return res;
      ALLOCATOR(enc_selects[i], 30 + strlen(s));
      len += sprintf(enc_selects[i], "  <select value=\"%s\"/>\n", s);
      deallocator(s);
    }
  }

  //
  //  put it all in 1 block
  //
  ALLOCATOR(xml, len);
  sprintf(xml, "%s\n<Prepared-Statement sql=\"%s\">\n", XML_HDR, query_enc);
  deallocator(query_enc);
  p = xml + strlen(xml);
  if (n_bindings > 0) {
    for (i=0; i<n_bindings; i++) {
      sprintf(p, "  %s", args[i]);
      p += strlen(p);
      deallocator(args[i]);
    }
    deallocator(args);
  }
  if (n_selects > 0) {
    for (i=0; i<n_selects; i++) {
      sprintf(p, "%s", enc_selects[i]);
      p += strlen(p);
      deallocator(enc_selects[i]);
    }
    deallocator(enc_selects);
  }

  sprintf(p, "</Prepared-Statement>");

  HC_DEBUG_DETAIL(("xml3:\n%s\n", xml));
 
  *ptr = xml;

  return HCERR_OK;
}

hcerr_t hc_internal_create_query(hc_query_handle_t *handle, 
                                 hc_string_t query,
                                 hc_int_pstmt_t *pstmt,
                                 hc_int_session_t *session,
                                 int32_t results_per_fetch, 
                                 hc_long_t chunk_size_bytes, 
                                 hc_long_t window_size_chunks,
                                 char *cache_id){

  hcerr_t res;
  cell_t *cell;

  assert(!(query != NULL  &&  pstmt != NULL));

  if (query != NULL) {
    HC_DEBUG(("hc_internal_create_query(handle=0x%x,query='%s'",
	    handle,query));
  } else {
    HC_DEBUG(("hc_internal_create_query(handle=0x%x,query='%s'",
            handle,pstmt->query));
  }

  handle->query_integrity_time = HC_MAX_LONG_VALUE;


  /* construct a retrieve url to use for the http get */
  handle->retrieve_metadata_handle.session = session;
  hc_init_error_info(session);
  handle->results_per_fetch = results_per_fetch;
  handle->request_path = QUERY_PATH;
  handle->old_cookie = NULL;
  if (strlen(cache_id)+1 > MAX_CACHE_ID_LEN)
    return HCERR_BUFFER_OVERFLOW;
  strcpy (((hcoa_base_handle_t*)handle)->cache, cache_id);

  //%%MULTICELL
  // save copy of query for cells after 1st
  if (query == NULL) {
    res = pstmt_to_xml(pstmt, NULL, 0, &handle->xml);
    if (res != HCERR_OK)
      return res;
  } else {
    // make a simple pstmt
    hc_pstmt_t *px;
    res = hc_pstmt_create(session, query, &px);
    if (res != HCERR_OK)
      return res;
    res = pstmt_to_xml(px, NULL, 0, &handle->xml);
    if (res != HCERR_OK)
      return res;
    res = hc_pstmt_free(px);
    if (res != HCERR_OK)
      return res;
  }
  res = init_query_mcell(handle);
  if (res != HCERR_OK)
    return res;
  cell = get_cur_query_cell(handle);

  if (snprintf(handle->url,
	       MAX_HTTP_URL_LEN, "%s://%s:%d/%s?%s=%s&binary=false&maxresults=%d",
	       HTTP_PROTOCOL_TAG, cell->addr, cell->port,
	       QUERY_PATH, 
	       CACHE_PARAMETER, ((hcoa_base_handle_t*)handle)->cache, 
/* 	       QUERY_PARAMETER, query,  */
	       results_per_fetch) == MAX_HTTP_URL_LEN) {
      HC_ERR_LOG(("hcoa_internal_create_query:   url length exceeded internal maximum %d",
		MAX_HTTP_URL_LEN));
    return HCERR_URL_TOO_LONG;
  }
  handle->start_element_handler = &query_start_element_handler;
  handle->end_element_handler = &query_end_element_handler;

  /* The element that the name/value pairs are in. 
     (cf retrieve_metadata_start_element_handler) */
  handle->retrieve_metadata_handle.element_tag = QUERY_RESULT_TAG;

  res = init_retrieve_result_set(handle, QUERY, handle->url);
  if (res != HCERR_OK)
    return res;

  res = populate_query_body(handle, XML_IN_BODY, handle->xml);

  return res;
}

hcerr_t hc_create_query(void **handle, 
                        hc_string_t query,
			            hc_session_t *session,
                        int32_t results_per_fetch, 
                        hc_long_t chunk_size_bytes, 
                        hc_long_t window_size_chunks, char *cache_id){

  hcerr_t res;

  ALLOCATOR(*handle,sizeof(hc_query_handle_t));
  memset(*handle,0,sizeof(hc_query_handle_t));
  res =  hc_internal_create_query((hc_query_handle_t *)*handle, 
                                  query, NULL,
				                  (hc_int_session_t *) session,
                                  results_per_fetch, 
                                  chunk_size_bytes, 
                                  window_size_chunks, cache_id);
  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}

hcerr_t hc_create_query_pstmt(void **handle, 
                        hc_pstmt_t *pstmt,
                        int32_t results_per_fetch, 
                        hc_long_t chunk_size_bytes, 
                        hc_long_t window_size_chunks){

  hc_int_pstmt_t *p = (hc_int_pstmt_t *) pstmt;
  hcerr_t res = validate_pstmt(pstmt);
  if (res != HCERR_OK)
    return res;

  ALLOCATOR(*handle,sizeof(hc_query_handle_t));
  memset(*handle,0,sizeof(hc_query_handle_t));
  res =  hc_internal_create_query((hc_query_handle_t *)*handle, 
                                  NULL, p,
				  p->session,
                                  results_per_fetch, 
                                  chunk_size_bytes, 
                                  window_size_chunks, NVOA_ID);
  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}

static void bad_state (char *format_string, char *arg){
  HC_ERR_LOG((format_string, arg));
}

hcerr_t hc_internal_next_query_result (hc_query_handle_t *handle, 
                                       hc_oid *oid){

  HC_DEBUG_DETAIL(("+++++hc_internal_next_query_result"));
  if (op_state(handle) != QUERY_SERVING_RESULTS && op_state(handle) != QUERY_SERVING_FINAL_RESULTS){
    bad_state("Bad state: %s. Expected QUERY_SERVING_RESULTS or QUERY_SERVING_FINAL_RESULTS.", 
              decode_state(op_state(handle)));
    return HCERR_HANDLE_IN_WRONG_STATE_FOR_OPERATION;
  }
  else if (handle->current_record == handle->response_count){
    return HCERR_READ_PAST_LAST_RESULT;
  } else {
    hc_name_value_cell_t *current = handle->retrieve_metadata_handle.top;

    queue_assert(handle->retrieve_metadata_handle.count == element_chain_length(&handle->retrieve_metadata_handle));
    assert(current != NULL);

    memcpy(*oid, current->value, sizeof(hc_oid));
    (*oid)[sizeof(hc_oid)-1] = '\0';

    handle->retrieve_metadata_handle.top = 
                                    handle->retrieve_metadata_handle.top->next;
    handle->retrieve_metadata_handle.count--;
    handle->current_record++;

    deallocator(current);

    if (handle->current_record == handle->response_count){
      if (op_state(handle) == QUERY_SERVING_RESULTS){
        transition_state(handle, QUERY_DONE_SERVING_RESULTS);
      }
      else if (op_state(handle) == QUERY_SERVING_FINAL_RESULTS){
        transition_state(handle, QUERY_DONE_SERVING_CELL);
      }
      else{
        bad_state("Bad state: %s. Expected QUERY_RETRIEVING_RESULTS or QUERY_SERVING_FINAL_RESULTS\n",
                  decode_state(op_state(handle)));
        return HCERR_HANDLE_IN_WRONG_STATE_FOR_OPERATION;
      }
    }
    return HCERR_OK;
  }
}


hcerr_t hc_next_query_result(void *handle, hc_oid *oid, hc_nvr_t **nvrp){
  hc_query_handle_t *h = (hc_query_handle_t *)handle;

  // %% check handle
  if (h->request_path == NULL)
    return HCERR_HANDLE_IN_WRONG_STATE_FOR_OPERATION;
  if (strcmp(h->request_path, QUERY_PATH) == 0) {
    if (nvrp != NULL) 
      *nvrp = NULL;
    return hc_internal_next_query_result(h, oid);
  } else if (strcmp(h->request_path, QUERY_PLUS_PATH) == 0) {
    return hc_internal_next_query_plus_result(h, oid, nvrp);
  } else
    return HCERR_HANDLE_IN_WRONG_STATE_FOR_OPERATION;
}


/**************************************************************
 *                    Query Plus                              *
 **************************************************************/

/* this function is shared between query and query_plus */
static hcerr_t init_retrieve_result_set(hc_query_handle_t *handle, hc_operation_t op_type, char *url) {
  hc_oid oid;
  hcerr_t res = HCERR_OK;
  FILL_INVALID_OID(oid); 

  HC_DEBUG_DETAIL(("init_retrieve_result_set: handle=0x%x op_type=%s",handle,decode_op(op_type)));

  res = hc_init_metadata_handle(&handle->retrieve_metadata_handle, op_type, QUERY_HANDLE, url, oid);
  if (res != HCERR_OK) 
    return res;

  res = hc_query_init_record_set(handle, TRUE);
  if (res != HCERR_OK) 
    return res;

  queue_assert(query_plus_response_list_length(handle)==element_chain_length(&handle->retrieve_metadata_handle));

  /* initialize xml parser */
  res = xml_create(handle->start_element_handler,
		   handle->end_element_handler,
		   (void *) handle,
		   &(handle->retrieve_metadata_handle.parser), 
		   allocator, deallocator);
  return res;
}


hcerr_t hc_internal_create_query_plus(hc_query_handle_t *handle, 
                                      hc_string_t query,
                                      hc_int_pstmt_t *pstmt,
                                      hc_string_t fields[], int n_fields,
				                      hc_int_session_t *session,
                                      int32_t results_per_fetch, 
                                      hc_long_t chunk_size_bytes, 
                                      hc_long_t window_size_chunks){

  hcerr_t res = HCERR_OK;
  cell_t *cell;

  handle->old_cookie = NULL;

  handle->retrieve_metadata_handle.session = session;
  hc_init_error_info(session);

  if (n_fields < 1)
    return HCERR_MISSING_SELECT_CLAUSE;

  assert(!(query != NULL  &&  pstmt != NULL));

  if (query != NULL) {
    HC_DEBUG(("hc_internal_create_query_plus(handle=0x%x,query='%s' n_fields=%d",
	    handle,query,n_fields));
  } else {
    HC_DEBUG(("hc_internal_create_query_plus(handle=0x%x,query='%s' n_fields=%d",
            handle,pstmt->query,n_fields));
  }

  if (query == NULL  &&  pstmt == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  handle->query_integrity_time = HC_MAX_LONG_VALUE;

  /* construct a retrieve url to use for the http get */
  handle->results_per_fetch = results_per_fetch;
  handle->request_path = QUERY_PLUS_PATH;
  if (sizeof(NVOA_ID)+1 > MAX_CACHE_ID_LEN)
    return HCERR_BUFFER_OVERFLOW;
  strcpy(((hcoa_base_handle_t*)handle)->cache, NVOA_ID);

  //%%MULTICELL
  // save copy of query for cells after 1st
  if (query == NULL) {
    res = pstmt_to_xml(pstmt, fields, n_fields, &handle->xml);
    if (res != HCERR_OK)
      return res;
  } else {
    // make a simple pstmt
    hc_pstmt_t *px;
    res = hc_pstmt_create(session, query, &px);
    if (res != HCERR_OK)
      return res;
    res = pstmt_to_xml(px, fields, n_fields, &handle->xml);
    if (res != HCERR_OK)
      return res;
    res = hc_pstmt_free(px);
    if (res != HCERR_OK)
      return res;
  }
  res = init_query_mcell(handle);
  if (res != HCERR_OK)
    return res;
  cell = get_cur_query_cell(handle);

  if (snprintf(handle->url,
	       MAX_HTTP_URL_LEN, "%s://%s:%d/%s?%s=%s&binary=false&maxresults=%d",
	       HTTP_PROTOCOL_TAG, cell->addr, cell->port, 
	       QUERY_PLUS_PATH, 
	       CACHE_PARAMETER, ((hcoa_base_handle_t*)handle)->cache, 
/* 	       QUERY_PARAMETER, query,  */
	       results_per_fetch) == MAX_HTTP_URL_LEN) {
    HC_ERR_LOG(("hcoa_internal_create_query_plus:   url length exceeded internal maximum %d",
		MAX_HTTP_URL_LEN));
    return HCERR_URL_TOO_LONG;
  }
  handle->start_element_handler = &query_plus_start_element_handler;
  handle->end_element_handler = &query_plus_end_element_handler;



  /* The element that the name/value pairs are in. 
     (cf retrieve_metadata_start_element_handler) */
  handle->retrieve_metadata_handle.element_tag = QUERY_ATTRIBUTE_TAG;

  res = init_retrieve_result_set(handle, QUERY_PLUS, handle->url);
  if (res != HCERR_OK)
    return res;
  handle_type(handle) |= QUERY_HANDLE;

  res = populate_query_body(handle, XML_IN_BODY, handle->xml);
  return res;
}


hcerr_t hc_create_query_plus(void **handle, 
			     hc_string_t query,
			     hc_string_t fields[], int n_fields,
			     hc_session_t *session,
			     int32_t results_per_fetch, 
			     hc_long_t chunk_size_bytes, 
			     hc_long_t window_size_chunks){
 
  hcerr_t res;

  ALLOCATOR(*handle, sizeof(hc_query_handle_t));
  memset(*handle,0,sizeof(hc_query_handle_t));

  res = hc_internal_create_query_plus((hc_query_handle_t *) *handle, 
				      query, NULL,
				      fields, n_fields,
				      (hc_int_session_t *) session,
				      results_per_fetch, 
				      chunk_size_bytes, 
				      window_size_chunks);
  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}

hcerr_t hc_create_query_plus_pstmt(void **handle, 
			     hc_pstmt_t *pstmt,
			     hc_string_t fields[], int n_fields,
			     int32_t results_per_fetch, 
			     hc_long_t chunk_size_bytes, 
			     hc_long_t window_size_chunks){

  hc_int_pstmt_t *p = (hc_int_pstmt_t *) pstmt;
  hcerr_t res = validate_pstmt(pstmt);
  if (res != HCERR_OK)
    return res;

  ALLOCATOR(*handle, sizeof(hc_query_handle_t));
  memset(*handle,0,sizeof(hc_query_handle_t));

  res = hc_internal_create_query_plus((hc_query_handle_t *) *handle, 
				      NULL, p,
				      fields, n_fields,
				      p->session,
				      results_per_fetch, 
				      chunk_size_bytes, 
				      window_size_chunks);
  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}


static hcerr_t cleanup_query_handle(hc_query_handle_t *handle, int all){
  hcerr_t res = HCERR_OK;
  
  if (handle->retrieve_metadata_handle.parser != NULL) {
    res = xml_cleanup(handle->retrieve_metadata_handle.parser);
    handle->retrieve_metadata_handle.parser = NULL;
  }
  
  while(handle->retrieve_metadata_handle.top != NULL) {
    hc_name_value_cell_t *current = handle->retrieve_metadata_handle.top;
    handle->retrieve_metadata_handle.top = handle->retrieve_metadata_handle.top->next;
    deallocator(current);
  }
  handle->retrieve_metadata_handle.last = NULL;
  handle->response_count = 0;
  deallocator(handle->record_attributes_count);
  handle->record_attributes_count = NULL;
  if (all) {
    if (handle->xml) {
      deallocator(handle->xml);
      handle->xml = NULL;
    }
  }
  if (handle->old_cookie != NULL) {
    deallocator(handle->old_cookie);
    handle->old_cookie = NULL;
  }
  return res;
}

static hcerr_t free_record_set(hc_query_handle_t *handle){
  return hc_internal_close_query(handle, 0);
}


/*
 * The "turn-the-crank" function. This function does not block, 
 * and should be called whever select indicates available IO 
 * until the chunkdone parameter returns a non-zero value.
 * It can also be called at other times to restart a query when the 
 * customer wants more data.
 * On return:	result_size = number of data items ready to process.
 *		chunkdone = TRUE if received a complete XML-document
 *			worth of results.
 */
hcerr_t hc_internal_retrieve_query (hc_query_handle_t *handle, 
                                    hc_long_t *result_size, 
                                    int *chunkdone, 
                                    int *skip_select) {
  hcerr_t err = HCERR_OK;
  cell_t *cell;

  *chunkdone = FALSE;
  *skip_select = FALSE;
  *result_size = 0;

  validate_handle(handle, QUERY_HANDLE, ANY_OP, ANY_STATE);

/*   HC_DEBUG_DETAIL(("hc_internal_retrieve_query at entry: handle=0x%x %s result_size="LL_FORMAT" chunkdone=%d",  */
/* 		   handle,decode_state(op_state(handle)), */
/* 		   *result_size, *chunkdone));   */

  if (op_state(handle)==QUERY_SERVING_RESULTS) {
    HC_ERR_LOG(("hc_internal_retrieve_query: wrong state for operation: %s",
	      decode_state(op_state(handle))));
    return HCERR_HANDLE_IN_WRONG_STATE_FOR_OPERATION;
  }

  if (op_state(handle) == QUERY_FINISHED || op_state(handle) == QUERY_SERVING_FINAL_RESULTS) {
    *chunkdone = TRUE;
    return HCERR_OK;
  }

  /* If finished with this cell, move on to the next cell. 
     If not multicell or if no more cells, then done. */
  if (op_state(handle) == QUERY_DONE_SERVING_CELL) {
    handle->cur_cell++;
    if (handle->cur_cell >= handle->ncells) {
      /* Out of cells in the Silo. */
      *chunkdone = TRUE;
      transition_state(handle, QUERY_FINISHED);
      return HCERR_OK;
    }
    /* 
     * There are more cells in the Silo -- keep looking. Set up the
     * handle to search the next cell.  Compare to code in case below
     * for QUERY_DONE_SERVING_RESULTS.
     */
    HC_DEBUG_DETAIL(("hc_internal_retrieve_query: trying next cell"));
    cell = get_cur_query_cell(handle);
    if (snprintf(handle->url,
		 MAX_HTTP_URL_LEN, "%s://%s:%d/%s?%s=%s&binary=false&maxresults=%d",
		 HTTP_PROTOCOL_TAG, cell->addr, cell->port,
		 handle->request_path, 
		 CACHE_PARAMETER, ((hcoa_base_handle_t*)handle)->cache, 
		 handle->results_per_fetch) == MAX_HTTP_URL_LEN) {
      return HCERR_URL_TOO_LONG;
    }
    // this wipes out any pending responses, but there aren't any here.
    require_ok(free_record_set(handle)); // close this session & start anew

    // this also wipes out any pending responses
    require_ok(init_retrieve_result_set(handle, 
					op_type(handle), 
					handle->url));
    require_ok(populate_query_body(handle, 
				   XML_IN_BODY, 
				   handle->xml));
    transition_state(handle, QUERY_RETRIEVING_RESULTS);
      // go turn the crank some more
  }	//End If (op_state(handle) == QUERY_DONE_SERVING_CELL)


  if (op_state(handle) == QUERY_DONE_SERVING_RESULTS){
    /* Go back to server for next batch of results */
    char query_url[MAX_HTTP_URL_LEN];
    char *cookie;
    /* We stored the format string at create time, now add cookie */
    HC_DEBUG_DETAIL(("hc_internal_retrieve_query: going back to server for more results"));

    assert(handle->honeycomb_cookie != NULL);
    // existing cookie string will be deallocted in name/value pair cleanup
    ALLOCATOR(cookie, strlen(handle->honeycomb_cookie)+1);
    strcpy(cookie, handle->honeycomb_cookie);

    cell = get_cur_query_cell(handle);
    assert(cell!=NULL);

    if (snprintf(query_url,
             MAX_HTTP_URL_LEN, "%s://%s:%d/%s?%s=%s&binary=false&maxresults=%d",
             HTTP_PROTOCOL_TAG, cell->addr, cell->port,
             handle->request_path, 
             CACHE_PARAMETER, ((hcoa_base_handle_t*)handle)->cache,
             handle->results_per_fetch) == MAX_HTTP_URL_LEN) {
      return HCERR_URL_TOO_LONG;
    }
    require_ok (free_record_set(handle)); // close this session and start anew
    require_ok (init_retrieve_result_set(handle, op_type(handle), query_url));

    // remember this cookie string so we can deallocate it later
    assert(handle->old_cookie == NULL);
    handle->old_cookie = cookie;

    require_ok (populate_query_body(handle, HC_QUERY_BODY_CONTENT_COOKIE, cookie));

    transition_state(handle, QUERY_RETRIEVING_RESULTS);
  } 

  /* TURN THE CRANK */

  /* Keep reading metadata until current XML document is complete.
     "metadata_done" means that one set of query results is complete, and we
     should go ask for more. */
  err = hcoa_io_worker(&handle->retrieve_metadata_handle.retrieve_handle.base, chunkdone);
  if (err != HCERR_OK) {
    return err;
  }
  assert(handle->current_record == 0);
  
  if (*chunkdone){
    HC_DEBUG(("hc_internal_retrieve_query: hc_retrieve_metadata finished a chunk with %d entries", 
	      handle->response_count));
    if (handle->response_count <= 0){
      // Chunk received, but held no results
      if (handle->honeycomb_cookie != NULL) {
      /* A rare or perhaps even impossible case where there are no
	 results but there is a cookie!   In this case, we could
	 query the server again and see if it gives us some results. 
	 What we actually do is cause an error and give up.
	 Otherwise, there is some danger of an infinite client-server
	 loop if the server returns the same (non) results next
	 time.*/
	HC_ERR_LOG(("No cookie and no results! Bad response!"));
	/* FIXME: Define a real error for this case. */
	return HCERR_XML_MALFORMED_XML;
      } // End If/ElseIf/Else (handle->response_count > 0)

      /* No results AND no cookie -- done with this cell */
      transition_state(handle,QUERY_DONE_SERVING_CELL);
      *skip_select = TRUE;	// tell caller to come back faster
      *chunkdone = FALSE;	// tell caller no results to process
      return HCERR_CAN_CALL_AGAIN;
    } // End If (handle->response_count <= 0) 

      /* Chunk rcvd and it held some results from the query */
    *result_size = handle->response_count;

    if (handle->honeycomb_cookie == NULL) {
      transition_state(handle, QUERY_SERVING_FINAL_RESULTS);
    } else {
      transition_state(handle, QUERY_SERVING_RESULTS);
    }
  }	// End If (*chunkdone)

  //We turned the crank.   All done.
  return HCERR_OK;

}

hcerr_t hc_query_available_items(void *pubhandle, hc_long_t *countp) {
  hc_query_handle_t *handle = (hc_query_handle_t *)pubhandle;

  *countp = 0;
  validate_handle(handle, QUERY_HANDLE, ANY_OP, ANY_STATE);

  if (op_state(handle) != QUERY_SERVING_RESULTS &&
      op_state(handle) != QUERY_SERVING_FINAL_RESULTS) {
    return HCERR_OK;
  }
  *countp = handle->response_count - handle->current_record;
  return HCERR_OK;
}

hcerr_t hc_query_get_integrity_time(void *pubhandle, hc_long_t *timep) {
  hc_query_handle_t *handle = (hc_query_handle_t *)pubhandle;

  *timep = 0;
  validate_handle(handle, QUERY_HANDLE, ANY_OP, ANY_STATE);

  HC_DEBUG_DETAIL(("hc_query_get_query_integrity_time returning "LL_FORMAT,
		   handle->query_integrity_time));
  *timep = handle->query_integrity_time;
  return HCERR_OK;
}

hcerr_t hc_retrieve_query (void *handle, hc_long_t *result_size, int *done, int *skip_select) {
  return hc_internal_retrieve_query ((hc_query_handle_t *)handle, result_size, done, skip_select);
}


/* Caller is responsible to free name/value strings */
hcerr_t hc_internal_next_query_plus_result(hc_query_handle_t *handle, 
                                           hc_oid *oid, 
                                           hc_nvr_t **nvrp) {
  unsigned char *bytes;
  hc_nvr_t *nvr;
  hcerr_t res;
  int byteslen;

  HC_DEBUG_DETAIL(("+++++hc_internal_next_query_plus_result"));
  assert(op_state(handle) == QUERY_SERVING_RESULTS || op_state(handle) == QUERY_SERVING_FINAL_RESULTS);
  if (op_state(handle) != QUERY_SERVING_RESULTS && op_state(handle) != QUERY_SERVING_FINAL_RESULTS){
    bad_state("Bad state: %s. Expected QUERY_SERVING_(FINAL_)RESULTS.\n", decode_state(op_state(handle)));
    return HCERR_HANDLE_IN_WRONG_STATE_FOR_OPERATION;
  }
  else if (handle->current_record == handle->response_count){
    return HCERR_READ_PAST_LAST_RESULT;
  }
  else {
    int i = 0;
    int nitems = handle->record_attributes_count[handle->current_record];
    queue_assert(query_plus_response_list_length(handle)==element_chain_length(&handle->retrieve_metadata_handle));

    res = hc_nvr_create(handle->retrieve_metadata_handle.session, nitems,&nvr);
    if (res != HCERR_OK) 
      return res;
    if (nvrp != NULL)
      *nvrp = nvr;
    for (i = 0; i < nitems; i++){
      hc_name_value_cell_t *current = handle->retrieve_metadata_handle.top;
      assert(current != NULL);

      HC_DEBUG_DETAIL(("internal_next_query_plus_result: item %d name=%s, value=%s",
		i,current->name,current->value));
      /* Read from encoded strings.  current->v1_1 is the encoding type to use. */
      res = hc_nvr_add_from_encoded_string(nvr, current->name, current->value, current->v1_1);
      if (res != HCERR_OK) 
	return res;

      handle->retrieve_metadata_handle.top = handle->retrieve_metadata_handle.top->next;
      handle->retrieve_metadata_handle.count--;
#ifdef	DEBUG_QUEUES
      /* The following is necessary to make the queue double-checks work properly */
      if (handle->retrieve_metadata_handle.top == NULL)
	handle->retrieve_metadata_handle.last = NULL;
#endif
      deallocator(current);
    }
    res = hc_nvr_get_binary(nvr, HC_OA_OBJECT_ID_KEY, &byteslen, &bytes);
    if (res != HCERR_OK) {
      return res;
    }
    if (byteslen != sizeof(hc_oid)/2) {
      HC_ERR_LOG(("Received OID has wrong number of bytes: %d",
		  byteslen));
      return HCERR_XML_MALFORMED_XML;
    }
    res = hc_to_hex_string(byteslen, bytes, (char *) oid);

    handle->current_record++;
    queue_assert(query_plus_response_list_length(handle)==element_chain_length(&handle->retrieve_metadata_handle));

    /*     printf ("handle->current_record: %d handle->response_count: %d\n", handle->current_record, handle->response_count); */

    /* Done with this batch; transition state to 
     * fetch another if there is more.
     */
    if (handle->current_record == handle->response_count){
      if (op_state(handle) == QUERY_SERVING_RESULTS){
        transition_state(handle, QUERY_DONE_SERVING_RESULTS);
      } else if (op_state(handle) == QUERY_SERVING_FINAL_RESULTS){
        transition_state(handle, QUERY_DONE_SERVING_CELL);
      }
    }
    if (nvrp == NULL) {
      res = hc_nvr_free(nvr);
      if (res != HCERR_OK)
	return res;
    }
    return HCERR_OK;
  }
  /*   //make_metadata_arrays(handle, names, values, n); */
}


hcerr_t hc_next_query_plus_result(void *handle, hc_oid *oid, hc_nvr_t **nvrp){
  return  hc_internal_next_query_plus_result((hc_query_handle_t *)handle, oid, nvrp);
}


hcerr_t hc_internal_close_query (hc_query_handle_t  *handle, int all) {
  hcerr_t res;
  hcerr_t err;
  hc_int_session_t *session = handle->retrieve_metadata_handle.session;

  validate_handle(&(handle->retrieve_metadata_handle), RETRIEVE_METADATA_HANDLE, ANY_OP, ANY_STATE);

  res = hcoa_internal_retrieve_close (&(handle->retrieve_metadata_handle.retrieve_handle),
                             &session->response_code, session->errstr, session->errstr_len);

  err = cleanup_query_handle(handle, all);
  if (res == HCERR_OK)
    res = err;

  hc_save_error_info(&handle->retrieve_metadata_handle.retrieve_handle.base, session);
  return res;
}

hcerr_t hc_close_query (void *handle) {
  hcerr_t err = HCERR_OK;

  validate_handle(handle, QUERY_HANDLE, ANY_OP, ANY_STATE);

  err = hc_internal_close_query ((hc_query_handle_t *)handle, TRUE);
  FREE_HANDLE(handle, hc_query_handle_t, QUERY_HANDLE);
  return err;
}


/* check_indexed */

hcerr_t hc_internal_check_indexed_create(hc_check_indexed_handle_t *handle,
					 hc_int_session_t *session, 
					 hc_oid oid) {
  char retrieve_url[MAX_HTTP_URL_LEN];
  hcerr_t err = HCERR_OK;
  cell_id_t cellId = 0;
  cell_t *cell;
  hc_int_archive_t *arc;
  validate_oid(oid);
  validate_session(session);

  handle->session = session;
  arc = (hc_int_archive_t *)session->archive;

  //%%MULTICELL
  cellId = get_cell_id(oid);
  cell = get_cell(arc, cellId);
  if (cell == NULL) {
    HC_ERR_LOG(("hc_internal_check_indexed_object_create: failed to"
		" find cell %d for oid %s", 
		cellId, oid));
    return HCERR_PLATFORM_GENERAL_ERROR;
  }
  HC_DEBUG_DETAIL(("cellId %d => %s:%d", cellId, cell->addr, cell->port));

  /* construct a check_indexed url to use for the http get */
  if (snprintf(retrieve_url, MAX_HTTP_URL_LEN, "%s://%s:%d/%s?%s=%s&%s=%s",
	       HTTP_PROTOCOL_TAG, cell->addr, cell->port, 
	       CHECK_INDEXED_URL, 
	       CACHE_PARAMETER, NVOA_ID,
	       ID_PARAMETER, oid) == MAX_HTTP_URL_LEN) {
    HC_ERR_LOG(("hc_internal_check_indexed_object_create: url length exceeded internal maximum %d",
		MAX_HTTP_URL_LEN));
    return HCERR_URL_TOO_LONG;
  }


  /* Compare the following to hcoa_internal_create_delete */
  err = hcoa_retrieve_create(&(handle->retrieve_handle),
			     arc,
			     retrieve_url, oid, 
			     hcoa_retrieve_to_buffer_callback, (void *)handle);
  
  /* Set up to read data into the read buffer in the handle */
  handle->retrieve_handle.base.rbuf = 
    handle->retrieve_handle.base.rend = 
      handle->readbuffer;
  handle->retrieve_handle.base.rbuflen = sizeof(handle->readbuffer);

  handle_type(handle) |= CHECK_INDEXED_HANDLE;
  if (err != HCERR_OK) {
    return err;
  }

  err = hcoa_retrieve_start_transmit(&handle->retrieve_handle.base);
  return err;
}

/* ---> */
hcerr_t hc_check_indexed_create(void **handle,
				hc_session_t *session,
				hc_oid oid) {
  hcerr_t res;

  ALLOCATOR(*handle, sizeof(hc_check_indexed_handle_t));
  memset(*handle, 0, sizeof (hc_check_indexed_handle_t));
  res = hc_internal_check_indexed_create((hc_check_indexed_handle_t *)*handle,
					 (hc_int_session_t *)session,
					 oid); 
  if (res != HCERR_OK) {
    deallocator(*handle);
    *handle = NULL;
  }
  return res;
}

hcerr_t hc_internal_check_indexed_close(hc_check_indexed_handle_t *handle, 
					int *resultp) {
  hcerr_t res;
  hc_int_session_t *session = handle->session;
  unsigned char *cp;
  int len;

  validate_handle(handle, CHECK_INDEXED_HANDLE, ANY_OP, ANY_STATE);

  res = hcoa_internal_retrieve_close(&handle->retrieve_handle, 
				     &session->response_code, session->errstr, session->errstr_len);
  if (res != HCERR_OK) {
    return res;
  }
  /* now parse the returned int from handle->readbuffer */
  cp = (unsigned char *)handle->retrieve_handle.base.rbuf;
  len = handle->retrieve_handle.base.rbuflen;
  if (len < 4) {
    HC_ERR_LOG(("hc_internal_check_indexed_close:"
		" too few bytes to represent binary integer (%d)",
		len));
    return HCERR_XML_MALFORMED_XML;
  }
  *resultp = ((((cp[0]*256)+cp[1])*256+cp[2])*256+cp[3]);
  
  HC_DEBUG(("hc_internal_check_indexed_close => %d",*resultp));

  return HCERR_OK;
}

hcerr_t hc_check_indexed_close(void *handle, int *resultp) {
  hcerr_t res;
  res = hc_internal_check_indexed_close((hc_check_indexed_handle_t *)handle, 
					resultp);
  FREE_HANDLE(handle, hc_check_indexed_handle_t, CHECK_INDEXED_HANDLE);

  return res;
}
/*

To Do:

1. URLEncoder.encode
*/
/*

<Query-Plus-Results>
<Result>
<attribute name="object_id" value="010001ff2a89bbfedf11d9bd32000423ae05e2000000000200000000"/>
<attribute name="title" value="FOOBAR1"/>
</Result>
<Cookie value=""/>
</Query-Plus-Results>
*/
