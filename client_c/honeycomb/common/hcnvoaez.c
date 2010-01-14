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
#include <string.h>
#include <stdlib.h>
#include <assert.h>

#define	COMPILING_HONEYCOMB
#include "hcclient.h"
#include "hcoaez.h"
#include "hcnvoai.h"
#include "hcinternals.h"

#define STORE_BUFFER_SIZE 76384


/****************************************************/
/*                     SESSION                      */
/****************************************************/
hcerr_t hc_session_create_ez(char *host, int port, hc_session_t **sessionp) {
  hc_int_session_t *conn;
  hcerr_t res;

  if (sessionp == NULL || host == NULL) {
    return HCERR_ILLEGAL_ARGUMENT;
  } 
  *sessionp = NULL;

  if (allocator == NULL) hc_init(malloc,free,realloc);

  HC_DEBUG(("+++hc_session_create_ez"));
  ALLOCATOR(conn, sizeof(hc_int_session_t));
  memset((char *)conn,0,sizeof(*conn));
  conn->magic_number = hc_session_magic_number;

  /* Create connection to cURL package */
  res = hcoa_archive_init(&conn->archive, host, port);
  if (res != HCERR_OK) {
    HC_ERR_LOG(("hc_session_create_ez: hcoa_archive_init error"));
    hc_session_free(conn);
    return res;
  }
  conn->errstr_len = SESSION_ERRSTR_LEN;
  ALLOCATOR(conn->errstr, conn->errstr_len);
  memset(conn->errstr,0,conn->errstr_len);

  res = hc_session_update_schema_ez(conn);
  if (res != HCERR_OK) {
    HC_ERR_LOG(("hc_session_create_ez: Failed to create session: %d (%s)",
		res,hc_decode_hcerr(res)));
    if (conn->errstr[0] != 0 || conn->response_code != 0) {
      HC_ERR_LOG(("Response_code=%d errstr=%s",
		  conn->response_code, conn->errstr));
    }
    hc_session_free(conn);
    return res;
  }

  *sessionp = (hc_session_t *)conn;

  return HCERR_OK;
 
}

hcerr_t hc_session_free(hc_session_t *pubsession) {
  hc_int_session_t *session = (hc_int_session_t *)pubsession;
  validate_session(session);
  if (session->archive) hcoa_archive_cleanup(session->archive);
  if (session->schema) hc_schema_free(session->schema);
  if (session->errstr) deallocator(session->errstr);
  session->magic_number = 0;
  deallocator(session);
  return HCERR_OK;
}

hcerr_t hc_session_get_schema (hc_session_t *session, hc_schema_t **schemap) {
  validate_session(session);
  *schemap = Session_schema(session);
  return HCERR_OK;
}

hcerr_t hc_session_get_status (hc_session_t *session, int32_t *responsep, char **errstrp) {
  validate_session(session);
  *responsep = Session_response_code(session);
  *errstrp = Session_errstr(session);
  return HCERR_OK;
}
hcerr_t hc_session_get_host (hc_session_t *session, char **hostp, int *portp) {
  hc_int_session_t *sess = (hc_int_session_t *) session;
  hc_int_archive_t *arc = (hc_int_archive_t *) sess->archive;
  cell_t *cell = &arc->default_cell;
  validate_session(session);
  *hostp = cell->addr;
  *portp = cell->port;
  return HCERR_OK;
}

hcerr_t hc_session_get_archive (hc_session_t *session, hc_archive_t **archivep) {
  validate_session(session);
  *archivep = Session_archive(session);
  return HCERR_OK;
}
hcerr_t hc_session_get_platform_result (hc_session_t *session, int32_t *connect_errnop, int32_t *platform_resultp) {
  hc_int_session_t *sp = (hc_int_session_t *)session;
  validate_session(session);
  *connect_errnop = sp->connect_errno;
  *platform_resultp = sp->platform_result;
  return HCERR_OK;
}

/****************************************************/
/*                     STORE                        */
/****************************************************/

static hcerr_t hc_store_metadata_driver(hc_store_metadata_handle_t *handle,
					hc_system_record_t *system_record) {

  hcerr_t res = -1;
  int done = FALSE;
  hc_session_t *session;
  validate_handle(handle, STORE_METADATA_HANDLE, ANY_OP, ADDING_METADATA);
  session = handle->session;
  validate_session(session);
  if (system_record == NULL) return HCERR_ILLEGAL_ARGUMENT;

  memset((char *)system_record,0,sizeof(system_record));
  HC_DEBUG(("hc_store_metadata_driver"));


  /* As long as perform_io returns CAN_CALL_AGAIN, we keep calling it.   
     Most of the work gets done by callbacks.  */
  while (!done) {

    if ((res = hc_store_metadata_worker(handle, &done)) != HCERR_OK) {
      return res;
    }
    if (done == FALSE) {
      hc_select_all(session);
    }
  }	/* End While (!done) */
    
  /* always close the handle */
  res = hc_store_metadata_close(handle, system_record);
  return res;
}


static hc_long_t chunk_size_bytes = 1024 * 1024; /* 1 MB */
static hc_long_t window_size_chunks = 9999;         /* max non-committed
                                                       in-flight chunks*/


static hcerr_t build_md_xml(void *real_handle,
                            hc_int_nvr_t *nvr){  
  int i = 0;
  hcerr_t res = HCERR_OK;

  if (nvr != NULL) {
    for (i = 0; i<nvr->count; i++){
      switch (nvr->values[i].hcv_type) {
      case HC_LONG_TYPE:
        res = hc_add_metadata_long(real_handle, nvr->names[i], LONG_TAG,
                                   nvr->values[i].hcv.hcv_long);
        break;
      case HC_DATE_TYPE:
        res = hc_add_metadata_date(real_handle, nvr->names[i], 
                                   &nvr->values[i].hcv.hcv_tm);
        break;
      case HC_TIME_TYPE:
        res = hc_add_metadata_time(real_handle, nvr->names[i], 
                                   nvr->values[i].hcv.hcv_timespec.tv_sec);
        break;
      case HC_TIMESTAMP_TYPE:
        res = hc_add_metadata_timestamp(real_handle, nvr->names[i], 
                                   &nvr->values[i].hcv.hcv_timespec);
        break;
      case HC_DOUBLE_TYPE:
        res = hc_add_metadata_double(real_handle, nvr->names[i], 
                                     nvr->values[i].hcv.hcv_double);
        break;
      case HC_STRING_TYPE:
        res = hc_add_metadata_string(real_handle, nvr->names[i], STRING_TAG,
                                     nvr->values[i].hcv.hcv_string);
        break;
      case HC_CHAR_TYPE:
        res = hc_add_metadata_string(real_handle, nvr->names[i], CHAR_TAG,
                                     nvr->values[i].hcv.hcv_string);
        break;
      case HC_BINARY_TYPE:
        res = hc_add_metadata_bytearray(real_handle, nvr->names[i], BINARY_TAG,
                                        &(nvr->values[i].hcv.hcv_bytearray));
        break;
      case HC_OBJECTID_TYPE:
        res = hc_add_metadata_bytearray(real_handle, nvr->names[i], 
					OBJECTID_TAG,
                                        &(nvr->values[i].hcv.hcv_bytearray));
        break;
      case HC_BYTE_TYPE:
        res = hc_add_metadata_byte(real_handle, nvr->names[i], 
				   nvr->values[i].hcv.hcv_byte);
        break;
      default:
        return HCERR_NO_SUCH_TYPE;
      }
      if (res != HCERR_OK) {
	return res;
      }
    }
  }
  return HCERR_OK;
}

/* Function signature so that I can use the same buffer overflow */
/* handler loop for both store_metadata and store_both and pass in a function pointer */
typedef hcerr_t (*make_md_handle) (void **handle, 
                                   char *buffer,
                                   hc_long_t buffer_length,
                                   read_from_data_source ext_data_source_reader,
                                   void *ext_stream,
                                   hc_session_t *session,
                                   int cellid,
                                   hc_long_t chunk_size_bytes, 
                                   hc_long_t window_size_chunks);


static hcerr_t hc_store_metadata_common(hc_session_t *session,
                                        int cellid,
                                        hc_nvr_t *pubnvr,
                                        read_from_data_source data_source_reader, 
                                        void *cookie,
                                        make_md_handle maker,
                                        hc_system_record_t *system_record) {
  hcerr_t res = HCERR_OK;
  hc_store_metadata_handle_t *handle;
  hc_int_nvr_t *nvr = pubnvr;
  hc_long_t overflow_size = STORE_BUFFER_SIZE;
  char metadatabuf[STORE_BUFFER_SIZE];
  char *overflow = NULL;


  validate_session(session);
  if (nvr != NULL){
    validate_nvr(nvr);
  }
  if (system_record == NULL){
    return HCERR_ILLEGAL_ARGUMENT;
  }

  res = (*maker)((void**) &handle, 
                 metadatabuf, 
                 STORE_BUFFER_SIZE,
                 data_source_reader, 
                 cookie,
                 session, 
                 cellid,
                 chunk_size_bytes, 
                 window_size_chunks);

  require_ok(res);
  res = build_md_xml((void*)handle, nvr);

  /* Keep doubling MD buffer size until it all fits. */
  /* Obviously it would be better to transmit the old buffer and reuse it, */
  /* but the logic is compilcated because the underlying asychronous model */
  /* needs the single thread to be in the select/turn the crank state...*/
  while (res == HCERR_XML_BUFFER_OVERFLOW){
    FREE_HANDLE(handle, hc_store_metadata_handle_t, STORE_METADATA_HANDLE);
    handle = NULL;
    if (overflow != NULL){
      deallocator(overflow);
    }
    overflow_size = overflow_size * 2;
    ALLOCATOR(overflow, overflow_size);
    res = (*maker)((void**) &handle, 
                   overflow, 
                   overflow_size,
                   data_source_reader, 
                   cookie,
                   session, 
                   cellid,
                   chunk_size_bytes, 
                   window_size_chunks);

    require_ok(res);
    res = build_md_xml((void*)handle, nvr);
  }
  require_ok(res);

  res = hc_store_metadata_driver ((hc_store_metadata_handle_t *)handle, 
                                  system_record);
  if (overflow != NULL){
    deallocator(overflow);
  }
  return res;
}


hcerr_t hc_store_both_ez(hc_session_t *session,
			 read_from_data_source data_source_reader,
                         void *cookie,
			 hc_nvr_t *pubnvr,
			 hc_system_record_t *system_record) {

  HC_DEBUG(("+++hc_store_both_ez (nvr=0x%lx"));
  return hc_store_metadata_common(session,
                                  -1, // any cell
                                  pubnvr,
                                  data_source_reader, 
                                  cookie,
                                  hc_store_both_create,
                                  system_record);
}

hcerr_t hc_store_both_cell_ez(hc_session_t *session,
                              int cellid,
                              read_from_data_source data_source_reader,
                              void *cookie,
                              hc_nvr_t *pubnvr,
                              hc_system_record_t *system_record) {

  HC_DEBUG(("+++hc_store_both_cell_ez (nvr=0x%lx"));

  if (cellid < 0  ||  cellid > MAX_CELLID) {
    return HCERR_ILLEGAL_ARGUMENT;
  }

  return hc_store_metadata_common(session,
                                  cellid,
                                  pubnvr,
                                  data_source_reader,
                                  cookie,
                                  hc_store_both_create,
                                  system_record);
}

static hcerr_t hc_store_metadata_callback(void **handle, 
                                          char *buffer,
                                          hc_long_t buffer_length,
                                          read_from_data_source ext_data_source_reader,
                                          void *oid,
                                          hc_session_t *session,
                                          int cellid,
                                          hc_long_t chunk_size_bytes, 
                                          hc_long_t window_size_chunks){
  return hc_store_metadata_create(handle, 
                                  buffer,
                                  buffer_length,
                                  session,
                                  chunk_size_bytes, 
                                  window_size_chunks,
                                  *(hc_oid *)oid);
}


hcerr_t hc_store_metadata_ez(hc_session_t *session,
			     hc_oid *oid,
			     hc_nvr_t *pubnvr,
			     hc_system_record_t *system_record) {
  HC_DEBUG(("+++hc_store_metadata_ez"));
  validate_oid_ptr(oid);

  return hc_store_metadata_common(session,
                                  -1, // use oid cell
                                  pubnvr,
                                  NULL, 
                                  oid,
                                  hc_store_metadata_callback,
                                  system_record);
}



/****************************************************/
/*                  RETRIEVE                        */
/****************************************************/
hcerr_t hc_retrieve_metadata_ez(hc_session_t *session,
				hc_oid *oid, 
				hc_nvr_t **nvrp) {
  hc_retrieve_metadata_handle_t *handle;
  int done = FALSE;
  hcerr_t res = HCERR_OK;

  HC_DEBUG(("+++hc_retrieve_metadata_ez"));
  validate_session(session);
  validate_oid_ptr(oid);
  if ((res = hc_retrieve_metadata_create ((void**)&handle, session, *oid)) != HCERR_OK) {
    return res;
  }
  
  while(done == FALSE) {  

    res = hcoa_io_worker(handle, &done);
    if (res != HCERR_OK)
      break;
    
    if (done == FALSE) {
      hc_select_all(session);
    }
  }
  res = hc_retrieve_metadata_close(handle, nvrp);
  return res;  
}

hcerr_t hc_retrieve_ez(hc_session_t *pubsession,
                       write_to_data_destination data_writer, void *cookie,
                       hc_oid *oid) {
  hc_int_session_t *session = (hc_int_session_t *)pubsession;


  HC_DEBUG(("hc_retrieve_ez"));
  validate_session(session);
  validate_oid_ptr(oid);

  return hcoa_retrieve_ez(session->archive, data_writer, cookie, oid, 
			  &session->response_code, session->errstr,  session->errstr_len);
}

hcerr_t hc_range_retrieve_ez(hc_session_t *pubsession,
			     write_to_data_destination data_writer, void *cookie,
			     hc_oid *oid,
			     hc_long_t firstbyte, hc_long_t lastbyte) {
  hc_int_session_t *session = (hc_int_session_t *)pubsession;


  HC_DEBUG(("++++hc_range_retrieve_ez(oid=%s,firstbyte="LL_FORMAT", lastbyte="LL_FORMAT")",
	    (char *)oid,firstbyte, lastbyte));
  validate_session(session);
  validate_oid_ptr(oid);
  return hcoa_range_retrieve_ez(session->archive, data_writer, cookie, oid, 
				firstbyte, lastbyte,
				&session->response_code, session->errstr,  session->errstr_len);
}

/****************************************************/
/*                  SCHEMA                          */
/****************************************************/

hcerr_t hc_schema_get_count(hc_schema_t *schema, hc_long_t *countp) {
  hc_int_schema_t *hsp = (hc_int_schema_t *)schema;
  validate_schema(schema);

  *countp = hsp->count;

  return HCERR_OK;
}
hcerr_t hc_schema_get_type_at_index(hc_schema_t *schema, hc_long_t index, char **namep, hc_type_t *typep) {
  hc_int_schema_t *hsp = (hc_int_schema_t *)schema;
  validate_schema(hsp);
  if (index < 0 || index >= hsp->count) {
    return HCERR_ILLEGAL_ARGUMENT;
  }
  *namep = hsp->names[index];
  *typep = hsp->types[index];

  return HCERR_OK;
}


hcerr_t hc_schema_get_index(hc_int_schema_t *hsp, char *name, hc_long_t *retindex) {
  hc_long_t i;
  validate_schema(hsp);
  if (hsp->count == 0) 
    return HCERR_ILLEGAL_ARGUMENT;

  for (i = 0; i< hsp->count; i++) {
    if (strcmp(hsp->names[i],name) == 0) {
      *retindex = i;
      return HCERR_OK;
    }
  }
  return HCERR_ILLEGAL_VALUE_FOR_METADATA;
}

hcerr_t hc_schema_get_type(hc_schema_t *schema, char *name, hc_type_t *rettype) {
  hc_int_schema_t *hsp = (hc_int_schema_t *)schema;
  hcerr_t res;
  hc_long_t index;
  validate_schema(schema);
  if (name == NULL) 
    return HCERR_ILLEGAL_ARGUMENT;

  res = hc_schema_get_index(hsp, name, &index);
  if (res != HCERR_OK) 
    return res;

  *rettype = hsp->types[index];
  return HCERR_OK;
}

hcerr_t hc_schema_get_length(hc_schema_t *schema, char *name, int *length)
{
  hc_int_schema_t *hsp = (hc_int_schema_t *)schema;
  hcerr_t res;
  hc_long_t index;
  validate_schema(schema);
  if (name == NULL) 
    return HCERR_ILLEGAL_ARGUMENT;

  res = hc_schema_get_index(hsp, name, &index);
  if (res != HCERR_OK) 
    return res;

  *length = hsp->lengths[index];
  return HCERR_OK;
}

hcerr_t hc_schema_validate_type(hc_schema_t *schema, char *name, hc_type_t type) {
  hc_int_schema_t *hsp = (hc_int_schema_t *)schema;
  hcerr_t res;
  hc_long_t index;
  validate_schema(schema);
  if (name == NULL) 
    return HCERR_ILLEGAL_ARGUMENT;

  res = hc_schema_get_index(hsp,name, &index);
  if (res != HCERR_OK) 
    return res;
  // String data is allowed for any schema type
  if (type == HC_STRING_TYPE) {
    return HCERR_OK;
  }
  if (hsp->types[index] != type) {
    HC_ERR_LOG(("hc_schema_validate_type wrong type for [%s] expected %d schema %d\n", 
		name, type, hsp->types[index]));
    return HCERR_ILLEGAL_VALUE_FOR_METADATA;
  }
  return HCERR_OK;
}

hcerr_t hc_schema_free(hc_schema_t *schema) {
  hc_int_schema_t *hsp = (hc_int_schema_t *)schema;
  int i;
  validate_schema(schema);

  for (i=0; i<hsp->count; i++){
    deallocator(hsp->names[i]);
  }
  deallocator(hsp->names);
  deallocator(hsp->types);
  deallocator(hsp->lengths);
  deallocator(hsp);

  return HCERR_OK;
}


hcerr_t hc_session_update_schema_ez(hc_session_t *session) {

  hc_retrieve_metadata_handle_t *handle;
  hcerr_t res;
  hcerr_t err;
  int done = FALSE;
  hc_int_schema_t *hsp;
  validate_session(session);
  
  ALLOCATOR(hsp, sizeof(hc_int_schema_t));
  if (hsp == NULL) return HCERR_OOM;
  Session_schema(session) = (hc_schema_t *)hsp;
  memset(hsp,0,sizeof(*hsp));

  if ((res = hc_retrieve_schema_create ((void**)&handle, session)) != HCERR_OK) {
    return res;
  }
  
  while(done == FALSE) {

    res = hcoa_io_worker(handle, &done);
    if (res != HCERR_OK){
      break;
    }    

    if (done == FALSE) {
      hc_select_all(session);
    }
  }
  err  = hc_retrieve_schema_close(handle);
  if (res == HCERR_OK)
    res = err;

  return res;
}

/****************************************************/
/*                     QUERY                        */
/****************************************************/

hcerr_t hc_pstmt_create(hc_session_t *session, const hc_string_t query, hc_pstmt_t **ptr) {
  hc_int_pstmt_t *q;
  int len;

  validate_session(session);

  ALLOCATOR(*ptr, sizeof(hc_int_pstmt_t));
  memset(*ptr, 0, sizeof(hc_int_pstmt_t));
  q = (hc_int_pstmt_t *) *ptr;
  q->magic_number = hc_pstmt_magic_number;

  len = strlen(query);
  ALLOCATOR(q->query, len+1);
  memcpy(q->query, query, len+1);
  q->session = (hc_int_session_t *) session;
 
  return HCERR_OK;
}

hcerr_t validate_pstmt(hc_pstmt_t *pstmt) {

  hc_int_pstmt_t *q = (hc_int_pstmt_t *) pstmt;

  if (q == NULL)
    return HCERR_NULL_HANDLE;

  if (q->magic_number != hc_pstmt_magic_number)
    return HCERR_INVALID_HANDLE;

  return HCERR_OK;
}

static void hc_pstmt_free_binding(hc_query_binding_t *b) {
  switch (b->value.hcv_type) {
    case HC_STRING_TYPE:
    case HC_CHAR_TYPE:
      deallocator(b->value.hcv.hcv_string);
      break;
    case HC_OBJECTID_TYPE:
    case HC_BINARY_TYPE:
      deallocator(b->value.hcv.hcv_bytearray.bytes);
      break;
  }
  deallocator(b);
}

hcerr_t hc_pstmt_free(hc_pstmt_t *pstmt) {

  hc_int_pstmt_t *q = (hc_int_pstmt_t *) pstmt;
  hc_query_binding_t *b = q->bindings;

  hcerr_t res = validate_pstmt(pstmt);
  if (res != HCERR_OK) return res;

  deallocator(q->query);

  while (b != NULL) {
    hc_query_binding_t *b2 = b->next;
    hc_pstmt_free_binding(b);
    b = b2;
  }
  deallocator(pstmt);
  return HCERR_OK;
}

// replace index-matching binding if found
static void hc_pstmt_add_binding(hc_int_pstmt_t *p, hc_query_binding_t *b) {
  hc_query_binding_t *bb;

  assert(p != NULL  &&  b != NULL);

  bb = p->bindings;
  if (bb == NULL) {
    p->bindings = b;
    return;
  }
  if (bb->index == b->index) {
    p->bindings = b;
    b->next = bb->next;
    hc_pstmt_free_binding(bb);
    return;
  }
  while (bb->next != NULL) {
    if (bb->next->index == b->index) {
      b->next = bb->next->next;
      hc_pstmt_free_binding(bb->next);
      bb->next = b;
      return;
    }
    bb = bb->next;
  }
  bb->next = b;
  b->next = NULL;
}

hcerr_t hc_pstmt_set_string(hc_pstmt_t *pstmt, int which, hc_string_t value) {
  hc_int_pstmt_t *p = (hc_int_pstmt_t *) pstmt;
  hc_query_binding_t *b;

  hcerr_t res = validate_pstmt(pstmt);
  if (res != HCERR_OK) 
    return res;

  ALLOCATOR(b, sizeof(hc_query_binding_t));
  b->next = NULL;
  b->index = which;

  b->value.hcv_type = HC_STRING_TYPE;

  res = hc_copy_string(value, &b->value.hcv.hcv_string);
  if (res != HCERR_OK)
    return res;

  hc_pstmt_add_binding(p, b);

  return HCERR_OK;
}


hcerr_t hc_pstmt_set_double(hc_pstmt_t *pstmt, int which, double value) {
  hc_int_pstmt_t *q = (hc_int_pstmt_t *) pstmt;
  hc_query_binding_t *b;

  hcerr_t res = validate_pstmt(pstmt);
  if (res != HCERR_OK) return res;

  ALLOCATOR(b, sizeof(hc_query_binding_t));
  b->next = NULL;
  b->index = which;
  b->value.hcv_type = HC_DOUBLE_TYPE;
  b->value.hcv.hcv_double = value;

  hc_pstmt_add_binding(q, b);

  return HCERR_OK;
}

hcerr_t hc_pstmt_set_long(hc_pstmt_t *pstmt, int which, hc_long_t value) {
  hc_int_pstmt_t *q = (hc_int_pstmt_t *) pstmt;
  hc_query_binding_t *b;

  hcerr_t res = validate_pstmt(pstmt);
  if (res != HCERR_OK) return res;

  ALLOCATOR(b, sizeof(hc_query_binding_t));
  b->next = NULL;
  b->index = which;
  b->value.hcv_type = HC_LONG_TYPE;
  b->value.hcv.hcv_long = value;

  hc_pstmt_add_binding(q, b);

  return HCERR_OK;
}

hcerr_t hc_pstmt_set_date(hc_pstmt_t *pstmt, int which, struct tm *value) {
  hc_int_pstmt_t *q = (hc_int_pstmt_t *) pstmt;
  hc_query_binding_t *b;

  hcerr_t res = validate_pstmt(pstmt);
  if (res != HCERR_OK) return res;

  ALLOCATOR(b, sizeof(hc_query_binding_t));
  b->next = NULL;
  b->index = which;
  b->value.hcv_type = HC_DATE_TYPE;
  b->value.hcv.hcv_tm = *value;

  hc_pstmt_add_binding(q, b);

  return HCERR_OK;
}

hcerr_t hc_pstmt_set_time(hc_pstmt_t *pstmt, int which, time_t value) {
  hc_int_pstmt_t *q = (hc_int_pstmt_t *) pstmt;
  hc_query_binding_t *b;

  hcerr_t res = validate_pstmt(pstmt);
  if (res != HCERR_OK) return res;

  ALLOCATOR(b, sizeof(hc_query_binding_t));
  b->next = NULL;
  b->index = which;
  b->value.hcv_type = HC_TIME_TYPE;
  b->value.hcv.hcv_timespec.tv_sec = value;

  hc_pstmt_add_binding(q, b);

  return HCERR_OK;
}

hcerr_t hc_pstmt_set_timestamp(hc_pstmt_t *pstmt, int which, struct timespec *value) {
  hc_int_pstmt_t *q = (hc_int_pstmt_t *) pstmt;
  hc_query_binding_t *b;

  hcerr_t res = validate_pstmt(pstmt);
  if (res != HCERR_OK) return res;

  ALLOCATOR(b, sizeof(hc_query_binding_t));
  b->next = NULL;
  b->index = which;
  b->value.hcv_type = HC_TIMESTAMP_TYPE;
  b->value.hcv.hcv_timespec = *value;

  hc_pstmt_add_binding(q, b);

  return HCERR_OK;
}

hcerr_t hc_pstmt_set_binary(hc_pstmt_t *pstmt, int which, unsigned char *bytes, int len) {
  hc_int_pstmt_t *q = (hc_int_pstmt_t *) pstmt;
  hc_query_binding_t *b;
  hc_bytearray_t *ba;

  hcerr_t res = validate_pstmt(pstmt);
  if (res != HCERR_OK) return res;

  if (len < 1)
    return HCERR_BAD_REQUEST;

  ALLOCATOR(b, sizeof(hc_query_binding_t));
  b->next = NULL;
  b->index = which;
  b->value.hcv_type = HC_BINARY_TYPE;

  ba = &(b->value.hcv.hcv_bytearray);

  ba->len = len;
  ALLOCATOR(ba->bytes, len);
  memcpy(ba->bytes, bytes, len);

  hc_pstmt_add_binding(q, b);
 
  return HCERR_OK; 
}


static hcerr_t hc_query_old_ez(hc_session_t *session,
		     hc_string_t query,
                     int max_recs,
		     hc_query_result_set_t **rsetp) {

  hc_long_t nitems;
  hcerr_t res;

  HC_DEBUG(("+++hc_query_old_ez"));
  validate_session(session);
  if (query == NULL)
    return HCERR_ILLEGAL_ARGUMENT;
  if (max_recs < -1)
    return HCERR_ILLEGAL_ARGUMENT;
  res = hc_create_query((void *)rsetp, query, session, max_recs, 0, 0, NVOA_ID);
  if (res != HCERR_OK) {
    return res;
  }

  res = hc_qrs_fetch_ez(*rsetp, &nitems);	/* get first XML chunk */
  if (res != HCERR_OK && *rsetp != NULL) {
    hcerr_t err;
    err = hc_qrs_free(*rsetp);
    HC_DEBUG(("hc_query_old_ez: hc_qrs_free returning error code %d %s",err, hc_decode_hcerr(err)));
  }
  return res;
}


static hcerr_t hc_pstmt_query(hc_pstmt_t *pstmt,
                              int max_recs,
		              hc_query_result_set_t **rsetp) {

  hc_long_t nitems;
  hcerr_t res;

  HC_DEBUG(("+++hc_pstmt_query"));
  if (pstmt == NULL)
    return HCERR_ILLEGAL_ARGUMENT;
  if (max_recs < -1)
    return HCERR_ILLEGAL_ARGUMENT;
  res = hc_create_query_pstmt((void *)rsetp, pstmt, 100, 0, 0);
  if (res != HCERR_OK) {
    return res;
  }

  res = hc_qrs_fetch_ez(*rsetp, &nitems);	/* get first XML chunk */
  if (res != HCERR_OK && *rsetp != NULL) {
    hcerr_t err;
    err = hc_qrs_free(*rsetp);
    HC_DEBUG(("hc_pstmt_query: hc_qrs_free returning error code %d %s",err, hc_decode_hcerr(err)));
  }
  return res;
}

hcerr_t hc_qrs_next_ez(hc_query_result_set_t *rset, hc_oid *oid, hc_nvr_t **nvrp, int *finishedp) {
  hc_long_t nitems = 0;
  hcerr_t res;

  HC_DEBUG_DETAIL(("hc_qrs_next_ez"));
  if (oid == NULL)
    return HCERR_ILLEGAL_ARGUMENT;
  validate_query_result_set(rset);

  *finishedp = FALSE;
  res = hc_query_available_items(rset, &nitems);
  if (res != HCERR_OK) {
    return res; 
  }
  if (nitems == 0) {
    res = hc_qrs_fetch_ez(rset, &nitems);	/* get next XML chunk */
    if (res != HCERR_OK) {
      return res; 
    }
  }
  if (nitems == 0) {
    *finishedp = TRUE;
    return HCERR_OK;
  }

  res = hc_next_query_result(rset, oid, nvrp);

  return res;
}

/* Fetch a bunch of query data */
hcerr_t hc_qrs_fetch_ez(hc_query_result_set_t *rset, hc_long_t *countp) {
  hc_query_handle_t *handle;
  hcerr_t res;
  int chunk_done = FALSE;	/* TRUE when one chunk of metadata has been received */

  HC_DEBUG_DETAIL(("++++hc_qrs_fetch_ez"));
  validate_query_result_set(rset);
  handle = (hc_query_handle_t *)rset;
  for(;;) {
    /*
     * We keep asking calling retrieve_query until a full top-level
     *  XML document has been received.  
     */
    int skip_select = FALSE;
    *countp = 0;
    chunk_done = FALSE;
    /* hc_retrieve_query will validate handle */
    res = hc_retrieve_query(handle, countp, &chunk_done, &skip_select);
    HC_DEBUG_IO_DETAIL(("hc_qrs_fetch_ez: sees err=%d, *countp=%lld, chunk_done=%d, skip_select=%d",
			res, *countp, chunk_done, skip_select));
    if (res != HCERR_OK && res != HCERR_CAN_CALL_AGAIN){
      return res;
    }
    
    if (chunk_done) {
      return HCERR_OK;
    }
    if (!skip_select) {
      hc_select_all(handle->retrieve_metadata_handle.session);
    }
    
  }	/* End For */
  /*NOTREACHED */
}

hcerr_t hc_qrs_get_query_integrity_time(hc_query_result_set_t *rset, 
					hc_long_t *query_timep) {
  return hc_query_get_integrity_time(rset, query_timep);
}
hcerr_t hc_qrs_is_query_complete(hc_query_result_set_t *rset, 
				 int *completep) {
  hcerr_t res;
  hc_long_t query_time;

  *completep = FALSE;
  res = hc_qrs_get_query_integrity_time(rset,&query_time);
  if (res != HCERR_OK)
    return res;

  *completep = (query_time > 0);
  return HCERR_OK;
}


hcerr_t hc_qrs_free(hc_query_result_set_t *rset) {
  hcerr_t res;

  HC_DEBUG(("hc_qrs_free"));
  res = hc_close_query((void *)rset);
  return res;
}


/****************************************************/
/*                  QUERY PLUS                      */
/****************************************************/
static hcerr_t hc_query_plus_old_ez(hc_session_t *session,
			  hc_string_t query, 
			  char *selects[],
			  int n_selects,
                          int max_recs,
			  hc_query_result_set_t **rsetp) {
  hc_long_t nitems;
  hcerr_t res;

  HC_DEBUG(("hc_query_plus_ez"));
  validate_session(session);
  if (query == NULL || selects == NULL)
    return HCERR_ILLEGAL_ARGUMENT;
  if (max_recs < -1)
    return HCERR_ILLEGAL_ARGUMENT;
  res = hc_create_query_plus((void**)rsetp, query, selects, n_selects, session,
                              max_recs, 0, 0);
  if (res != HCERR_OK) {
    return res;
  }

  res = hc_qrs_fetch_ez(*rsetp, &nitems);	/* get first XML chunk */
  if (res != HCERR_OK && *rsetp != NULL) {
    hcerr_t err;
    err = hc_qrs_free(*rsetp);
    HC_DEBUG(("hc_query_plus_ez: returning error code %d %s",err, hc_decode_hcerr(err)));
  }
  return res;
}

static hcerr_t hc_pstmt_query_plus(hc_pstmt_t *pstmt, 
			  hc_string_t selects[],
			  int n_selects,
                          int max_recs,
			  hc_query_result_set_t **rsetp) {
  hc_long_t nitems;
  hcerr_t res;

  HC_DEBUG(("hc_query_plus_pstmt"));
  if (pstmt == NULL  ||  selects == NULL)
    return HCERR_ILLEGAL_ARGUMENT;
  if (max_recs < -1)
    return HCERR_ILLEGAL_ARGUMENT;
  res = hc_create_query_plus_pstmt((void**)rsetp, pstmt, selects, n_selects, 
                                   max_recs, 0, 0);
  if (res != HCERR_OK) {
    return res;
  }

  res = hc_qrs_fetch_ez(*rsetp, &nitems);	/* get first XML chunk */
  if (res != HCERR_OK && *rsetp != NULL) {
    hcerr_t err;
    err = hc_qrs_free(*rsetp);
    HC_DEBUG(("hc_query_plus_pstmt: returning error code %d %s",err, hc_decode_hcerr(err)));
  }
  return res;
}

// for now, piggyback at the ez level
hcerr_t hc_query_ez(hc_session_t *session,
			  hc_string_t query, 
			  char *selects[],
			  int n_selects,
                          int max_recs,
			  hc_query_result_set_t **rsetp) {

  if (max_recs < 1) {
    HC_ERR_LOG(("ERROR: hc_query_ez:  recs_per_fetch must be > 0"));
    return HCERR_ILLEGAL_ARGUMENT;
  }

  if (selects == NULL)
    return hc_query_old_ez(session, query, max_recs, rsetp);
  else
    return hc_query_plus_old_ez(session, query, selects, n_selects, max_recs, 
                                                                      rsetp);
}

hcerr_t hc_pstmt_query_ez(hc_pstmt_t *pstmt, 
			  hc_string_t selects[],
			  int n_selects,
                          int max_recs,
			  hc_query_result_set_t **rsetp) {
  if (max_recs < 1) {
    HC_ERR_LOG(("ERROR: hc_pstmt_query_ez:  recs_per_fetch must be > 0"));
    return HCERR_ILLEGAL_ARGUMENT;
  }
  if (selects == NULL)
    return hc_pstmt_query(pstmt, max_recs, rsetp);
  else
    return hc_pstmt_query_plus(pstmt, selects, n_selects, max_recs, rsetp);
}


/****************************************************/
/*		    DELETE                          */
/****************************************************/


hcerr_t hc_delete_ez(hc_session_t *pubsession, hc_oid *oid) {
  hc_int_session_t *session = (hc_int_session_t *)pubsession;

  HC_DEBUG(("hc_delete_ez"));
  validate_session(session);
  validate_oid_ptr(oid);
  return hcoa_delete_ez(session->archive, oid, 
			&session->response_code, session->errstr, session->errstr_len);
}

/****************************************************/
/*                  CHECK_INDEXED                   */
/****************************************************/
hcerr_t hc_check_indexed_ez(hc_session_t *session,
			    hc_oid *oid,
			    int *resultp) {
  hc_check_indexed_handle_t *handle;
  int done = FALSE;
  hcerr_t res = HCERR_OK;

  HC_DEBUG(("+++hc_check_indexed_ez"));
  validate_session(session);
  validate_oid_ptr(oid);
  if ((res = hc_check_indexed_create ((void**)&handle, session, *oid)) != HCERR_OK) {
    return res;
  }
  
  while(done == FALSE) {  

    res = hcoa_io_worker(handle, &done);
    if (res != HCERR_OK)
      break;
    
    if (done == FALSE) {
      hc_select_all(session);
    }
  }
  res = hc_check_indexed_close(handle, resultp);
  return res;  
}

#define	NAME_VALUE_DEFAULT_SIZE 100
#define	NAME_VALUE_EXPAND_SIZE	100

hcerr_t hc_nvr_create(hc_session_t *session, hc_long_t nslots, hc_nvr_t **retnvr) {
  hc_int_nvr_t	*nvr = NULL;
  char **names = NULL;
  hc_value_t *values = NULL;

  *retnvr = 0;

  validate_session(session);
  if (retnvr == NULL)
    return HCERR_ILLEGAL_ARGUMENT;
  
  if (nslots == 0) 
    nslots = NAME_VALUE_DEFAULT_SIZE;
  if (nslots < 0) 
    return HCERR_ILLEGAL_ARGUMENT;

  nvr = (hc_int_nvr_t *) allocator(sizeof(hc_int_nvr_t));
  names = (char **) allocator(sizeof(char *) * nslots);
  values = (hc_value_t *) allocator(sizeof(hc_value_t) * nslots);

  if (nvr == NULL || names == NULL || values == NULL) {
    if (nvr) deallocator(nvr);
    if (names) deallocator(names);
    if (values) deallocator(values);
    HC_ERR_LOG(("ERROR: hc_nvr_create:  Out of memory"));
    return HCERR_OOM;
  }
  memset(nvr,0,sizeof(*nvr));
  memset(names,0,sizeof(char *) * nslots);
  memset(values,0,sizeof(hc_value_t) * nslots);

  nvr->session = session;
  nvr->nslots = nslots;
  nvr->count = 0;
  nvr->names = names;
  nvr->values = values;
  
  *retnvr = (hc_nvr_t *)nvr;
  return HCERR_OK;
}

hcerr_t hc_nvr_expand(hc_int_nvr_t *nvr, hc_long_t new_nslots) {
  hc_long_t	orig_nslots;
  char**	newnames;
  hc_value_t	*newvalues;

  validate_nvr (nvr);
  orig_nslots = nvr->nslots;
  assert(new_nslots > orig_nslots);
  newnames = (char **)reallocator((void *)nvr->names,sizeof(char *) * new_nslots);
  newvalues = reallocator((void *)nvr->values,sizeof(hc_value_t) * new_nslots);
  if (newnames == NULL || newvalues == NULL) {
    /* Allocation failed, but keep the data we have. */
    if (newnames) nvr->names = newnames;
    if (newvalues) nvr->values = newvalues;
    return HCERR_OOM;
  }
  nvr->names = newnames;
  nvr->values = newvalues;
  nvr->nslots = new_nslots;
  return HCERR_OK;
}

static hcerr_t free_nvr_value(hc_int_nvr_t *nvr, int index) {
  if (index < 0  ||  index >= nvr->count)
    return HCERR_NO_SUCH_ATTRIBUTE;

    switch (nvr->values[index].hcv_type) {
      case HC_STRING_TYPE:
      case HC_CHAR_TYPE:
        deallocator(nvr->values[index].hcv.hcv_string);
        break;
      case HC_OBJECTID_TYPE:
      case HC_BINARY_TYPE:
        deallocator(nvr->values[index].hcv.hcv_bytearray.bytes);
        break;
    }
    return HCERR_OK;
}

hcerr_t hc_nvr_free(hc_nvr_t *pubnvr){
  int index;
  hc_int_nvr_t *nvr = pubnvr;
  hcerr_t res;

  validate_nvr (nvr);

  if (nvr->names != NULL || nvr->values != NULL) {
      for (index = 0; index < nvr->count; index++) {
        if (nvr->names[index] != NULL ) {
          deallocator(nvr->names[index]);
        }
        if (nvr->values != NULL) {
          res = free_nvr_value(nvr, index);
          if (res != HCERR_OK)
            return res;
        }
      }
      if(nvr->names != NULL)
        deallocator(nvr->names);
      
      if (nvr->values != NULL)
        deallocator(nvr->values);
      
      deallocator(nvr);
  }

  return HCERR_OK;
}

hcerr_t hc_nvr_add_value(hc_nvr_t *pubnvr, char *name, hc_value_t value){
  hcerr_t res;
  char *tmp_name;
  int namelen;
  int index;
  hc_int_nvr_t *nvr = pubnvr;  
  validate_nvr (nvr);

  if (name == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  res = hc_schema_validate_type(Session_schema(nvr->session),
				name, value.hcv_type);
  if (res != HCERR_OK) 
    return res;

  // replace dup if present
  for (index=0; index<nvr->count; index++) {
    if (strcmp(nvr->names[index], name) == 0) {
      // replace
      res = free_nvr_value(nvr, index);
      if (res != HCERR_OK)
        return res;
      nvr->values[index] = value;
      return HCERR_OK;
    }
  }

  // add
  if (nvr->count >= nvr->nslots) {
    res = hc_nvr_expand(nvr,nvr->nslots + NAME_VALUE_EXPAND_SIZE);
    if (res != HCERR_OK) 
      return res;
  }

  namelen = strlen(name);
  ALLOCATOR(tmp_name,namelen+1);	/* include the null byte */
  memcpy(tmp_name,name,namelen+1);
  
  index = nvr->count;
  nvr->names[index] = tmp_name;
  nvr->values[index] = value;
  nvr->count++;

  return HCERR_OK;
}


hcerr_t hc_nvr_add_string(hc_nvr_t *pubnvr, char *name, hc_string_t value) {
  hc_value_t value_tmp = HC_EMPTY_VALUE_INIT;
  hcerr_t res;

  validate_nvr(pubnvr);
  if (name == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  value_tmp.hcv_type = HC_STRING_TYPE;

  res = hc_copy_string(value, &value_tmp.hcv.hcv_string);
  if (res != HCERR_OK) return res;

  res = hc_nvr_add_value(pubnvr, name, value_tmp);
  if (res != HCERR_OK) {
    deallocator(value_tmp.hcv.hcv_string);
  }
  return res;
}

hcerr_t hc_nvr_add_char(hc_nvr_t *pubnvr, char *name, hc_string_t value) {
  hc_value_t value_tmp = HC_EMPTY_VALUE_INIT;
  hcerr_t res;

  validate_nvr(pubnvr);
  if (name == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  value_tmp.hcv_type = HC_CHAR_TYPE;

  res = hc_copy_string(value, &value_tmp.hcv.hcv_string);
  if (res != HCERR_OK) return res;

  res = hc_nvr_add_value(pubnvr, name, value_tmp);
  if (res != HCERR_OK) {
    deallocator(value_tmp.hcv.hcv_string);
  }
  return res;
}


hcerr_t hc_nvr_add_binary(hc_nvr_t *nvr, char *name, int size, unsigned char *bytes) {
  hc_value_t value_tmp = HC_EMPTY_VALUE_INIT;
  hcerr_t res;
  if (name == NULL  ||  size < 1  ||  bytes == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  value_tmp.hcv_type = HC_BINARY_TYPE;
  ALLOCATOR(value_tmp.hcv.hcv_bytearray.bytes, size);
  memcpy(value_tmp.hcv.hcv_bytearray.bytes, bytes, size);
  value_tmp.hcv.hcv_bytearray.len = size;

  res = hc_nvr_add_value(nvr, name, value_tmp);
  if (res != HCERR_OK) {
    deallocator(value_tmp.hcv.hcv_bytearray.bytes);
  }
  return res;
}

hcerr_t hc_nvr_add_long(hc_nvr_t *nvr, char *name, hc_long_t value) {
  hc_value_t value_tmp = HC_EMPTY_VALUE_INIT;
  value_tmp.hcv.hcv_long = value;
  value_tmp.hcv_type = HC_LONG_TYPE;
  if (name == NULL)
    return HCERR_ILLEGAL_ARGUMENT;
  
  return hc_nvr_add_value(nvr, name, value_tmp);
}

hcerr_t hc_nvr_add_date(hc_nvr_t *nvr, char *name, struct tm *value) {
  hc_value_t value_tmp = HC_EMPTY_VALUE_INIT;
  value_tmp.hcv.hcv_tm = *value;
  value_tmp.hcv_type = HC_DATE_TYPE;
  if (name == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  return hc_nvr_add_value(nvr, name, value_tmp);
}

hcerr_t hc_nvr_add_timestamp(hc_nvr_t *nvr, char *name, struct timespec *value) {
  hc_value_t value_tmp = HC_EMPTY_VALUE_INIT;
  value_tmp.hcv.hcv_timespec = *value;
  value_tmp.hcv_type = HC_TIMESTAMP_TYPE;
  if (name == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  return hc_nvr_add_value(nvr, name, value_tmp);
}

hcerr_t hc_nvr_add_time(hc_nvr_t *nvr, char *name, time_t value) {
  hc_value_t value_tmp = HC_EMPTY_VALUE_INIT;
  value_tmp.hcv.hcv_timespec.tv_sec = value;
  value_tmp.hcv_type = HC_TIME_TYPE;
  if (name == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  return hc_nvr_add_value(nvr, name, value_tmp);
}


hcerr_t hc_nvr_add_double(hc_nvr_t *nvr, char *name, hc_double_t value) {
  hc_value_t value_tmp = HC_EMPTY_VALUE_INIT;
  value_tmp.hcv.hcv_double = value;
  value_tmp.hcv_type = HC_DOUBLE_TYPE;
  
  return hc_nvr_add_value(nvr, name, value_tmp);
}


hcerr_t hc_nvr_add_byte(hc_nvr_t *nvr, char *name, hc_byte_t value){
  hc_value_t value_tmp = HC_EMPTY_VALUE_INIT;
  value_tmp.hcv.hcv_long = 0;
  value_tmp.hcv.hcv_byte = value;
  value_tmp.hcv_type = HC_BYTE_TYPE;
  if (name == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  return hc_nvr_add_value(nvr, name, value_tmp);
}


hcerr_t hc_nvr_add_from_string(hc_nvr_t *pubnvr, 
                               char *name, 
                               char *value){
  hc_int_nvr_t *nvr = (hc_int_nvr_t *)pubnvr;
  hcerr_t res;
  hc_value_t tmp_value = HC_EMPTY_VALUE_INIT;
  hc_type_t type;
  validate_nvr (nvr);
  if (name == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  res = hc_schema_get_type(Session_schema(nvr->session),
			   name, &type);
  if (res != HCERR_OK) 
    return res;

  res = hc_decode_value(value, &tmp_value, type);
  if (res != HCERR_OK) 
    return res;

  res = hc_nvr_add_value(pubnvr, name, tmp_value);
  return res;
}


hcerr_t hc_nvr_create_from_string_arrays(hc_session_t *session, 
                                         hc_nvr_t **nvrp, 
                                         char **names, 
                                         char **values, 
                                         hc_long_t nitems) {
    hc_long_t i;
    hcerr_t res;
    hc_nvr_t *nvr;

    validate_session(session);
    validate_nvr (nvrp);
    if (names == NULL || values == NULL)
      return HCERR_ILLEGAL_ARGUMENT;

    res = hc_nvr_create(session, nitems, nvrp);
    if (res != HCERR_OK) 
      return res;
    nvr = *nvrp;

    for (i = 0; i < nitems; i++){
      res = hc_nvr_add_from_string(nvr, names[i], values[i]);
      if (res != HCERR_OK) {
        if (res == HCERR_XML_MALFORMED_XML)
          return HCERR_ILLEGAL_ARGUMENT;
	return res;
      }
    }
    return HCERR_OK;
}

hcerr_t hc_nvr_get_count(hc_nvr_t *nvr, hc_long_t *countp) {
  validate_nvr (nvr);
  *countp = ((hc_int_nvr_t *)nvr)->count;
  return HCERR_OK;
}

hcerr_t hc_nvr_get_index(hc_nvr_t *pubnvr, char *name, hc_long_t *retindex) {
  hc_long_t i;
  hc_int_nvr_t *nvr = pubnvr;
  validate_nvr (nvr);
  if (name == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  for (i = 0; i< nvr->count; i++) {
    if (strcmp(nvr->names[i],name) == 0) {
      *retindex = i;
      return HCERR_OK;
    }
  }
  return HCERR_NO_SUCH_ATTRIBUTE;
}

/* returned name and value are readonly and will stay valid until
   hc_nvr_free or hc_nvr_convert_to_string_array is called. */
hcerr_t hc_nvr_get_value_at_index(hc_nvr_t *pubnvr, hc_long_t index, char **namep, hc_value_t *valuep) {
  hc_int_nvr_t *nvr = pubnvr;
  validate_nvr (nvr);
  if (namep == NULL || valuep == NULL)
    return HCERR_ILLEGAL_ARGUMENT;
  if (index < 0) 
    return HCERR_ILLEGAL_ARGUMENT;
  if (index > nvr->count)
    return HCERR_NO_MORE_ATTRIBUTES;
  *namep = nvr->names[index];
  *valuep = nvr->values[index];
  return HCERR_OK;
}




/* string must be copied before nvr is freed. */
hcerr_t hc_nvr_get_string(hc_nvr_t *nvr, char *name, hc_string_t *valuep) {
  hcerr_t res;
  hc_long_t index;
  int type;

  validate_nvr (nvr);
  if (name == NULL || valuep == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  res = hc_nvr_get_index(nvr,name,&index);
  if (res != HCERR_OK) return res;

  type = ((hc_int_nvr_t *)nvr)->values[index].hcv_type;
  if (type != HC_STRING_TYPE && type != HC_CHAR_TYPE) {
    HC_ERR_LOG(("hc_nvr_get_string: don't know how to convert type %d to String"
		" HCERR_ILLEGAL_VALUE_FOR_METADATA",
		type));
    return HCERR_ILLEGAL_VALUE_FOR_METADATA;
  }

  *valuep = ((hc_int_nvr_t *)nvr)->values[index].hcv.hcv_string;

  return HCERR_OK;
}

/* bytes must be copied by caller before nvr is freed */
hcerr_t hc_nvr_get_binary(hc_nvr_t *nvr, char *name, int *len, unsigned char **bytes) {
  hcerr_t res;
  hc_long_t index;
  hc_bytearray_t *ba;
  int expected;
  
  validate_nvr (nvr);
  if (name == NULL || len == NULL || bytes == NULL) {
    HC_ERR_LOG(("hc_nvr_get_binary called with illegal arguments"));
    return HCERR_ILLEGAL_ARGUMENT;
  }
  
  res = hc_nvr_get_index(nvr, name, &index);
  if (res != HCERR_OK) return res;
   
  expected = ((hc_int_nvr_t *)nvr)->values[index].hcv_type;
  if (expected != HC_BINARY_TYPE && expected != HC_OBJECTID_TYPE) {
    HC_ERR_LOG(("hc_nvr_get_binary: unexpected data type %d (%s)",
		expected, hc_decode_hc_type(expected)));
    return HCERR_ILLEGAL_VALUE_FOR_METADATA;
  }
  ba = &(((hc_int_nvr_t *)nvr)->values[index].hcv.hcv_bytearray);

  *len = ba->len;
  *bytes = ba->bytes;
  return HCERR_OK;
}
 
hcerr_t hc_nvr_get_long(hc_nvr_t *nvr, char *name, hc_long_t *valuep) {
  hcerr_t res;
  hc_long_t index;

  validate_nvr (nvr);
  if (name == NULL || valuep == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  res = hc_nvr_get_index(nvr,name,&index);
  if (res != HCERR_OK) return res;

  if (((hc_int_nvr_t *)nvr)->values[index].hcv_type != HC_LONG_TYPE) {
    HC_ERR_LOG(("hc_nvr_get_long: HCERR_ILLEGAL_VALUE_FOR_METADATA"));
    return HCERR_ILLEGAL_VALUE_FOR_METADATA;
  }

  *valuep = ((hc_int_nvr_t *)nvr)->values[index].hcv.hcv_long;

  return HCERR_OK;
}

hcerr_t hc_nvr_get_date(hc_nvr_t *nvr, char *name, struct tm *valuep) {
  hcerr_t res;
  hc_long_t index;

  validate_nvr (nvr);
  if (name == NULL || valuep == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  res = hc_nvr_get_index(nvr,name,&index);
  if (res != HCERR_OK) return res;

  if (((hc_int_nvr_t *)nvr)->values[index].hcv_type != HC_DATE_TYPE)
    return HCERR_ILLEGAL_VALUE_FOR_METADATA;

  *valuep = ((hc_int_nvr_t *)nvr)->values[index].hcv.hcv_tm;

  return HCERR_OK;
}

hcerr_t hc_nvr_get_time(hc_nvr_t *nvr, char *name, time_t *valuep) {
  hcerr_t res;
  hc_long_t index;
   
  validate_nvr (nvr);
  if (name == NULL || valuep == NULL)
    return HCERR_ILLEGAL_ARGUMENT;
     
  res = hc_nvr_get_index(nvr,name,&index);
  if (res != HCERR_OK) return res;
   
  if (((hc_int_nvr_t *)nvr)->values[index].hcv_type != HC_TIME_TYPE)
    return HCERR_ILLEGAL_VALUE_FOR_METADATA;
     
  *valuep = ((hc_int_nvr_t *)nvr)->values[index].hcv.hcv_timespec.tv_sec;
   
  return HCERR_OK;
}

hcerr_t hc_nvr_get_timestamp(hc_nvr_t *nvr, char *name, struct timespec *valuep) {
  hcerr_t res;
  hc_long_t index;

  validate_nvr (nvr);
  if (name == NULL || valuep == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  res = hc_nvr_get_index(nvr,name,&index);
  if (res != HCERR_OK) return res;

  if (((hc_int_nvr_t *)nvr)->values[index].hcv_type != HC_TIMESTAMP_TYPE)
    return HCERR_ILLEGAL_VALUE_FOR_METADATA;

  *valuep = ((hc_int_nvr_t *)nvr)->values[index].hcv.hcv_timespec;

  return HCERR_OK;
}


hcerr_t hc_nvr_get_double(hc_nvr_t *nvr, char *name, hc_double_t *valuep) {
  hcerr_t res;
  hc_long_t index;
  validate_nvr (nvr);
  if (name == NULL || valuep == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  res = hc_nvr_get_index(nvr,name,&index);
  if (res != HCERR_OK) return res;

  if (((hc_int_nvr_t *)nvr)->values[index].hcv_type != HC_DOUBLE_TYPE) {
    HC_ERR_LOG(("hc_nvr_get_double: HCERR_ILLEGAL_VALUE_FOR_METADATA"));
    return HCERR_ILLEGAL_VALUE_FOR_METADATA;
  }

  *valuep = ((hc_int_nvr_t *)nvr)->values[index].hcv.hcv_double;

  return HCERR_OK;
}


hcerr_t hc_nvr_get_byte(hc_nvr_t *nvr, char *name, hc_byte_t *valuep){
  hcerr_t res;
  hc_long_t index;
  validate_nvr (nvr);
  if (name == NULL || valuep == NULL)
    return HCERR_ILLEGAL_ARGUMENT;

  res = hc_nvr_get_index(nvr,name,&index);
  if (res != HCERR_OK) return res;

  if (((hc_int_nvr_t *)nvr)->values[index].hcv_type != HC_BYTE_TYPE)
    return HCERR_ILLEGAL_VALUE_FOR_METADATA;

  *valuep = ((hc_int_nvr_t *)nvr)->values[index].hcv.hcv_byte;

  return HCERR_OK;
}

/* returns an allocated string with the value.   Does not copy the
   value if it is already a string.  Just returns it.   See
   hc_nvr_convert_to_string_arrays for details.
   */
hcerr_t hc_get_value_as_string(hc_value_t value, char **result) {
  char	buffer[1024];	/* long enough for any long value printed representation */
  int res = HCERR_OK;
  int i;

  switch (value.hcv_type) {
    case HC_CHAR_TYPE:
    case HC_STRING_TYPE:
      *result = value.hcv.hcv_string;
      break;
    case HC_OBJECTID_TYPE:
    case HC_BINARY_TYPE: 
      i = value.hcv.hcv_bytearray.len;
      ALLOCATOR(*result, (i+1)*2);
      res = hc_to_hex_string(i,
			     value.hcv.hcv_bytearray.bytes,
			     *result);
      break;
    case HC_LONG_TYPE:
      // tag is provided but skipped
      hc_encode_long(buffer, LONG_TAG, value.hcv.hcv_long); 
      res = hc_copy_string(buffer+1, result);
      break;
    case HC_DATE_TYPE:
      hc_encode_date(buffer, &value.hcv.hcv_tm);
      res = hc_copy_string(buffer+1, result);
      break;
    case HC_TIME_TYPE:
      hc_encode_time(buffer, value.hcv.hcv_timespec.tv_sec);
      res = hc_copy_string(buffer+1, result);
      break;
    case HC_TIMESTAMP_TYPE:
      hc_encode_timestamp(buffer, &value.hcv.hcv_timespec);
      res = hc_copy_string(buffer+1, result);
      break;
    case HC_DOUBLE_TYPE:
      sprintf(buffer, "%.17G", value.hcv.hcv_double);
      res = hc_copy_string(buffer, result);
      break;
    default:
      HC_ERR_LOG(("hc_get_value_as_string: HCERR_NO_SUCH_TYPE %d",
		  value.hcv_type));
      return HCERR_NO_SUCH_TYPE;
  }
  return res;
}



/* Destructively modify a name-value-record into a pair of string arrays */
hcerr_t hc_nvr_convert_to_string_arrays(hc_nvr_t *pubnvr, char ***namesp, char ***valuesp, int *nitemsp) {
    hc_long_t i;
    hcerr_t res;
    char *tmp_string;
    char **values;
    hc_int_nvr_t *nvr = pubnvr;
    validate_nvr (nvr);
    if (namesp == NULL || valuesp == NULL)
      return HCERR_ILLEGAL_ARGUMENT;

    *valuesp = values = (char **) allocator(sizeof(char *) * nvr->count);
    if (*valuesp == NULL) return HCERR_OOM;

    for (i = 0; i < nvr->count; i++){
      res = hc_get_value_as_string(nvr->values[i],&tmp_string);
      if (res != HCERR_OK) return res;
      values[i] = tmp_string;
    }
    *nitemsp = nvr->count;
    *namesp = nvr->names;
    deallocator(nvr->values);	/* Don't deallocate individual strings
				   -- they are in *valuesp now! */
    deallocator((hc_int_nvr_t *)nvr);
    return HCERR_OK;
}

hcerr_t hc_copy_string(char *value, char **result) {
  int valuelen;

  valuelen = strlen(value)+1;

  ALLOCATOR(*result, valuelen);

  strncpy(*result,value,valuelen);
  return HCERR_OK;
}

hcerr_t hc_to_hex_string(int len, unsigned char *value, char *result) {
  int i;
  char *tocp;

  tocp = result;
  for (i = 0; i < len; i++) {
    sprintf(tocp, "%02x", value[i]);
    tocp += 2;
  }
  *tocp = 0;
  return HCERR_OK;
}

hcerr_t hc_from_hex_string(char *value, int *retlen, unsigned char **result) {
  int i;
  unsigned char *tocp;
  int valuelen;
  int len;
  int byteval;

  valuelen = strlen(value);
  len = (valuelen+1)/2;
  ALLOCATOR(*result, len);
  *retlen = len;

  tocp = *result;
  for (i = 0; i < valuelen; i++) {
    char c = value[i];
    if (c >= 'A' && c <= 'F') {
      byteval = (int)(c-'A')+10;
    } else if (c >= 'a' && c <= 'f') {
      byteval = (int)(c-'a')+10;
    } else if (c >= '0' && (c) <= '9') {
      byteval = (int)(c-'0');
    } else {
      HC_ERR_LOG(("Hex String value"
		  " has illegal char '%c' at index %d",
		  c,i));
      deallocator(tocp);
      return HCERR_XML_PARSE_ERROR;
    }
    assert(byteval >= 0 && byteval < 16);
    if (i % 2 == 0) {
      tocp[i/2] = byteval <<= 4;
    } else {
      tocp[i/2] += byteval;
    }
  }
  return HCERR_OK;
}

hcerr_t hc_query_holds_ez (hc_session_t *session,
		                   hc_string_t query,
                           int max_recs,
		                   hc_query_result_set_t **rsetp) {

  hc_long_t nitems;
  hcerr_t res;

  HC_DEBUG(("+++hc_query_holds_ez"));
  validate_session(session);
  if (query == NULL)
    return HCERR_ILLEGAL_ARGUMENT;
  if (max_recs < -1)
    return HCERR_ILLEGAL_ARGUMENT;
  res = hc_create_query((void *)rsetp, query, session, max_recs, 0, 0, SYSTEM_CACHE);
  if (res != HCERR_OK) {
    return res;
  }

  res = hc_qrs_fetch_ez(*rsetp, &nitems);	/* get first XML chunk */
  if (res != HCERR_OK && *rsetp != NULL) {
    hcerr_t err;
    err = hc_qrs_free(*rsetp);
    HC_DEBUG(("hc_query_holds_ez: hc_qrs_free returning error code %d %s",err, hc_decode_hcerr(err)));
  }
  return res;
}
