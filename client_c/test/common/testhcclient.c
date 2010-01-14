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
#include <string.h>
#include <stdio.h>
#include <fcntl.h>
#include <time.h>

/* this will avoid windows compile warnings */
/* #include <io.h> */

#include "hc.h"
#include "hcclient.h"
#include "hcoaez.h"
#include "hcinternals.h"

char *host = "localhost";
int port = -1; /* means use default */

char *filename = "reallybigdata";
hc_session_t	*the_session;
#define ERRSTR_LEN 4096  
int echo = TRUE;
hc_long_t firstbyte=100;
hc_long_t lastbyte=149;

/* For testing storing and querying of date and timestamp metadata types */
static char dateString[11];
static char timeString[9];
static char timestampString[25];
static struct tm * bd_time = 0;
static time_t cur_time = 0;



void printres(hcerr_t res) {
    printf("(%d) %s\n",res, hc_decode_hcerr(res));
}

void printres_and_error(hcerr_t res) {
  int32_t response_code = -1;
  char *errstr = "";

  hcerr_t err = -1;

  if (the_session) {
    err = hc_session_get_status(the_session, &response_code, &errstr);
  }

  printf("\nResult: ");
  printres(res);
  printf("Response code: %d\n", response_code);
  if (errstr[0] != '\0') {
    printf("\n===== ERROR RESPONSE =====\n"
	   "%s\n"
	   "================\n",
	   errstr);
  }
  printf("\n");
  fflush(stdout);

  if (res != HCERR_OK) 
      exit(1);
}
void old_printres_and_error(hcerr_t res,  int32_t response_code, char *errstr) {
  /* Look at global variables for response_code and errstr */
  printf("Result: ");
  printres(res);
  printf("Response code: %d\n\n", response_code);
  if(res != HCERR_OK && res != HCERR_CONNECTION_FAILED) {
    printf("\n===== ERROR RESPONSE =====\n%s\n================\n", errstr);
  }
  fflush(stdout);
  if (res != HCERR_OK) 
      exit(1);
}


hcerr_t test_retrieve_schema () {
  hc_long_t namebytes = 0;
  hc_schema_t *my_schema;
  hcerr_t res;
  hc_long_t count;
  hc_long_t i;
  char *name;
  hc_type_t type;

  res = hc_session_get_schema(the_session, &my_schema);
  if (res != HCERR_OK){
    return res;
  }
  printf("test_retrieve_schema\n");
  res = hc_schema_get_count(my_schema,&count);
  if (res != HCERR_OK){
    return res;
  }
  for (i=0; i<count; i++){
    res = hc_schema_get_type_at_index(my_schema,i,&name,&type);
    if (res != HCERR_OK){
      return res;
    }
    namebytes = namebytes + strlen(name);
    printf("      %s %s\n", name, hc_decode_hc_type(type));
    }

  printf("test_retrieve_schema retrieved " LL_FORMAT " attributes " LL_FORMAT " name bytes\n", 
	 count, namebytes);

  return HCERR_OK;
}

//      1808     137      -      hcoa_internal_retrieve_schema_close < hc_retrieve_schema_close 

char *long_query_string = "stringnull='Quux \
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\
' OR stringnull LIKE 'FOO%'";


hcerr_t test_query (char *query_string) {
  hc_long_t count;
  hc_oid oid;
  hc_nvr_t *nvr;
  hc_query_result_set_t *qrs;
  hcerr_t res;
  hc_long_t mem;
  int finished = 0;

  res = hc_query_ez (the_session, query_string, NULL, 0, 100, &qrs);
  if (res != HCERR_OK) {
    return res;
  }

  count = 0;
  mem = 0;
  for (;;) {
    res = hc_qrs_next_ez(qrs, &oid, &nvr, &finished);
    if (res != HCERR_OK) {
      return res;
    }

    if (finished) {
      break;
    }
    if (count == 0) {
      printf("First hit: %s\n", oid);
    }
    count++;
    mem += sizeof(oid);
  }

  printf("Total "LL_FORMAT" entries "LL_FORMAT" bytes\n ",count,mem);
  res = hc_qrs_free(qrs);

  return res;
}

hcerr_t test_query_holds (char *query_string) {
  hc_long_t count;
  hc_oid oid;
  hc_nvr_t *nvr;
  hc_query_result_set_t *qrs;
  hcerr_t res;
  hc_long_t mem;
  int finished = 0;

  res = hc_query_holds_ez (the_session, query_string, 100, &qrs);
  if (res != HCERR_OK) {
    return res;
  }

  count = 0;
  mem = 0;
  for (;;) {
    res = hc_qrs_next_ez(qrs, &oid, &nvr, &finished);
    if (res != HCERR_OK) {
      return res;
    }

    if (finished) {
      break;
    }
    if (count == 0) {
      printf("First hit: %s\n", oid);
    }
    count++;
    mem += sizeof(oid);
  }

  printf("Total "LL_FORMAT" entries "LL_FORMAT" bytes\n ",count,mem);
  res = hc_qrs_free(qrs);
  printf ("res: %d", res);
  return res;
}

hcerr_t test_query_pstmt() {
  hc_long_t count;
  hc_oid oid;
  hc_nvr_t *nvr;
  hc_query_result_set_t *qrs;
  hcerr_t res;
  hc_long_t mem;
  int finished = 0;
  hc_pstmt_t *pstmt;
  unsigned char bytes[10];
  int i;

  for (i=0; i<10; i++)
    bytes[i] = i;

  res = hc_pstmt_create(the_session, 
			"\"system.test.type_string\" = ?"
			" and system.test.type_double=?"
			" and system.test.type_binary=?", 
			&pstmt);
  res = hc_pstmt_set_string(pstmt, 1, "Quux");
  if (res != HCERR_OK) return res;

  res = hc_pstmt_set_double(pstmt, 2, -6.25e10 );
  if (res != HCERR_OK) return res;

  res = hc_pstmt_set_binary(pstmt, 3, bytes, 10);
  if (res != HCERR_OK) return res;

  res = hc_pstmt_query_ez(pstmt, NULL, 0, 100, &qrs);
  if (res != HCERR_OK) {
    return res;
  }

  count = 0;
  mem = 0;
  for (;;) {
    res = hc_qrs_next_ez(qrs, &oid, &nvr, &finished);
    if (res != HCERR_OK) {
      return res;
    }

    if (finished) {
      break;
    }
    if (count == 0) {
      printf("First hit: %s\n", oid);
    }
    count++;
    mem += sizeof(oid);
  }

  printf("Total "LL_FORMAT" entries "LL_FORMAT" bytes\n ",count,mem);
  res = hc_pstmt_free(pstmt);
  if (res != HCERR_OK) {
    printf("error %d in hc_pstmt_free\n", res);
    return res;
  }

  res = hc_qrs_free(qrs);

  return res;
}

hcerr_t print_nvr(char *prefix, hc_nvr_t *nvr) {
  hc_long_t nitems;
  hc_long_t index;
  char *name;
  hc_value_t value = HC_EMPTY_VALUE_INIT;
  hcerr_t res;

  res = hc_nvr_get_count(nvr,&nitems);
  if (res != HCERR_OK) return res;
  for (index = 0; index < nitems; index++) {
    res = hc_nvr_get_value_at_index(nvr,index,&name,&value);
    if (res != HCERR_OK) return res;
    printf("%s%s = ",prefix,name);
    switch (value.hcv_type) {
    case HC_LONG_TYPE:
      printf(LL_FORMAT,value.hcv.hcv_long);
      break;
    case HC_DOUBLE_TYPE:
      printf("%.17lg", value.hcv.hcv_long);	/* Check this */
      break;
    case HC_STRING_TYPE:
      printf("%s",value.hcv.hcv_string);
      break;
    case HC_BINARY_TYPE:
    case HC_OBJECTID_TYPE: {
      int len;
      int i;
      unsigned char *fromcp;

      len = value.hcv.hcv_bytearray.len;
      fromcp = value.hcv.hcv_bytearray.bytes;
      for (i = 0; i < len; i++) {
	printf("%02x", fromcp[i]);
      }
    }
    break;
    case HC_DATE_TYPE:{
      struct tm *tm = &(value.hcv.hcv_tm);
      printf("%d-%02d-%02d",
	     1900+tm->tm_year, tm->tm_mon, tm->tm_mday);
    }
    break;

    case HC_TIME_TYPE: {
      time_t t = value.hcv.hcv_timespec.tv_sec;
      struct tm tm;
      gmtime_r(&t, &tm);
      printf("%02d:%02d:%02d",
	     tm.tm_hour, tm.tm_min, tm.tm_sec);
    }
    break;

    case HC_TIMESTAMP_TYPE: {
      struct timespec *ts = &(value.hcv.hcv_timespec);
      struct tm tm;
      time_t timer = ts->tv_sec;
      gmtime_r(&timer, &tm);
      printf("%d-%02d-%02dT%02d:%02d:%02d.%03dZ",
	     1900+tm.tm_year, tm.tm_mon, tm.tm_mday,
	     tm.tm_hour, tm.tm_min, tm.tm_sec, ts->tv_nsec / 1000000);
    }
    break;

    default:
      printf("ERROR: Unknown value type %d",value.hcv_type);
      break;
    }
    printf("\n");
  }
  return HCERR_OK;
}
  
// query_plus is query w/ 'select's
hcerr_t test_query_plus () {
  char *selects[] = {"system.test.type_string","system.test.type_char"};
  char *query_string = 
       "system.test.type_string='Quux' OR system.test.type_char LIKE 'FOO%'";
  hc_long_t count;
  hc_long_t n_to_print = 1;
  hc_oid oid;
  hc_nvr_t *nvr;
  hc_query_result_set_t *rset;
  int finished;
  hcerr_t res;

  res = hc_query_ez (the_session, query_string, selects, 2, 100, &rset);
  if (res != HCERR_OK) 
    return res;

  count = 0;
  finished = 0;
  for (;;) {
    res = hc_qrs_next_ez(rset, &oid, &nvr, &finished);
    if (res != HCERR_OK) {
      return res;
    }

    if (finished) {
      break;
    }
    if (count < n_to_print) {
      printf(" Item "LL_FORMAT"\n",count);
      print_nvr("  ",nvr);
    }
    hc_nvr_free (nvr);
    count++;
  }	/* End For (...) */

  printf("Total "LL_FORMAT" entries.\n ",count);
  res = hc_qrs_free(rset);

  return res;
}


hcerr_t test_accept_formatted_xml(char *data, int len, int finished, void *stream) {
  printf(data);
  fflush(stdout);
  return HCERR_OK;
}


hcerr_t test_xml_common(xml_writer *writer){
  char *attributes[] = {"foo", "bar"};
  char *values[] = {"baz", "quux"};
  hc_long_t n;
  hcerr_t err = start_element (writer, "version", NULL, NULL, 0);
  err = start_element (writer, "attributes", NULL, NULL, 0);
  err = start_element (writer, "attribute", (char**)attributes, (char**)values, 2);
  err = end_element (writer, "attribute");

  /* Leave off this close tag to confirm that it will be implicitly closed... */
  /* err = end_element (writer, "attributes"); */
  return end_document_and_close (writer, &n);
}

hcerr_t test_write_xml() {
  xml_writer *writer;
  start_document ("systemMD", &test_accept_formatted_xml, NULL, &writer, malloc, free, TRUE);
  return test_xml_common(writer);
}

#define XML_BUFF_LEN 1024
hcerr_t test_buffered_write_xml() {
  xml_writer *writer;
  char buff[XML_BUFF_LEN];
  hcerr_t err = start_buffered_document ("systemMD", buff, XML_BUFF_LEN, &writer, malloc, free, TRUE);
  err = test_xml_common(writer);
  printf(buff);
  fflush(stdout);
  return err;
}


long read_from_file_data_source (void *stream, char *buff, long n){
  long nbytes;

  nbytes = read((int) stream, buff, n);
  return nbytes;
}


long echo_downloaded_data (void *stream, char *buff, long n){
  int i = 0;

  if (echo) {
    for (i = 0; i < n; i++){
      putchar(buff[i]);
    }
  }
  return n;
}

void describe_system_record(hc_system_record_t *record){
  printf("Stored to %s\n", record->oid);
  printf("Size: "LL_FORMAT"\n", record->size);
  printf("Creation Time: "LL_FORMAT"\n", record->creation_time);
  printf("Delete Time: "LL_FORMAT"\n", record->deleted_time);
  printf("Shred: 0x%x\n", (int)record->shredMode);
  printf("Alg: %s\n", record->digest_algo);
  printf("Digest: %s\n", record->data_digest);
}

hcerr_t test_query_pstmt_time_date_types() {
  hc_long_t count;
  hc_oid oid;
  hc_nvr_t *nvr;
  hc_query_result_set_t *qrs;
  hcerr_t res;
  hc_long_t mem;
  int finished = 0;
  hc_pstmt_t *pstmt;
  unsigned char bytes[10];
  int i;
  struct timespec timestamp_test;

  

  for (i=0; i<10; i++)
    bytes[i] = i;


 res = hc_pstmt_create(the_session, 
			"\"system.test.type_date\"=?"
			" and system.test.type_time=?"
			" and system.test.type_timestamp=?", 
			&pstmt);

  res = hc_pstmt_set_date(pstmt, 1, bd_time);
  if (res != HCERR_OK) return res;

  res = hc_pstmt_set_time(pstmt, 2, cur_time);
  if (res != HCERR_OK) return res;

  timestamp_test.tv_sec = cur_time;
  timestamp_test.tv_nsec = 0;
  res = hc_pstmt_set_timestamp(pstmt, 3, &timestamp_test);
  if (res != HCERR_OK) return res;

  res = hc_pstmt_query_ez(pstmt, NULL, 0, 100, &qrs);
  if (res != HCERR_OK) {
    return res;
  }

  count = 0;
  mem = 0;
  for (;;) {
    res = hc_qrs_next_ez(qrs, &oid, &nvr, &finished);
    if (res != HCERR_OK) {
      return res;
    }

    if (finished) {
      break;
    }
    if (count == 0) {
      printf("First hit: %s\n", oid);
    }
    count++;
    mem += sizeof(oid);
  }

  printf("Total "LL_FORMAT" entries "LL_FORMAT" bytes\n ",count,mem);
  res = hc_qrs_free(qrs);

  return res;
}

hcerr_t test_store_metadata_time_date_types(hc_oid *oid) {
  hcerr_t err = -1;
  char *names[] = {"system.test.type_date", "system.test.type_time", 
                   "system.test.type_timestamp"};
  char *values[] = {dateString, timeString, timestampString};
  int num = 0;

  hc_system_record_t system_record;
  hc_nvr_t *nvr;

  cur_time = time((time_t) 0);
  bd_time = gmtime(&cur_time);
  
  num = strftime(dateString, 11, "%Y-%m-%d", bd_time);
  num = strftime(timeString, 9, "%X", bd_time);
  num = strftime(timestampString, 25, "%Y-%m-%dT%X.0Z", bd_time);

  if (oid == NULL){
    printf("No OID to test with--aborting\n");
    return HCERR_INVALID_OID;
  }
  err = hc_nvr_create_from_string_arrays(the_session, &nvr,names, values, 3);
  if (err != HCERR_OK) return err;

  err = hc_store_metadata_ez(the_session,oid, nvr, &system_record);

  hc_nvr_free(nvr);
  
  describe_system_record(&system_record);
  return err;
}

hcerr_t test_store_data(hc_oid *oid,  int32_t *response_codep, char *errstr, hc_long_t errstr_len) {
  int infile = -1;
  hcerr_t err = -1;
  hc_system_record_t system_record;
  char  *sessionhost;
  int sessionport;
  hc_archive_t *archive;

  memset(&system_record, 0, sizeof(hc_system_record_t));

  if ((infile = open(filename, O_RDONLY | O_BINARY)) == -1) {
    printf("Failed to open data file '%s' for store: %d\n", filename, infile);
    return HCERR_COULD_NOT_OPEN_FILE;
  }
  hc_session_get_archive(the_session, &archive);
  err = hcoa_store_ez (archive,
		       &read_from_file_data_source, (void *)infile, &system_record,
		       response_codep, errstr, ERRSTR_LEN);
  close(infile);
  if (err == HCERR_OK) {
    memcpy (oid, system_record.oid, sizeof(hc_oid));
    describe_system_record(&system_record);
  }
  return err;
}

hcerr_t test_store_data_from_file(hc_oid *oid,  int32_t *response_codep, char *errstr, hc_long_t errstr_len) {
  hcerr_t err = -1;
  hc_system_record_t system_record;
  hc_archive_t *archive;

  memset(&system_record, 0, sizeof(hc_system_record_t));

  hc_session_get_archive(the_session, &archive);
  err = hcoa_store_ez_upload_file (archive, filename, &system_record,
				   response_codep, errstr, ERRSTR_LEN);
  if (err == HCERR_OK) {
    memcpy (oid, system_record.oid, sizeof(hc_oid));
    describe_system_record(&system_record);
  }
  return err;
}

hcerr_t test_store_both(hc_oid *oid) {
  int infile = -1;
  hcerr_t res = -1;
  char *names[] = {"system.test.type_string", "system.test.type_double", 
                   "system.test.type_binary"};
  char *values[] = {"Quux", "-6.25e10","00010203040506070809"};
  hc_system_record_t system_record;
  hc_nvr_t *nvr;

  if((infile = open(filename, O_RDONLY | O_BINARY)) == -1) {
    printf("Failed to open data file '%s' for store-both: %d\n", filename, infile);
    return HCERR_COULD_NOT_OPEN_FILE;
  }

  res = hc_nvr_create_from_string_arrays(the_session,&nvr,names,values,3);
  
  printf ("Calling hc_store_both_ez with %d\n", port);
  res = hc_store_both_ez (the_session,
			  &read_from_file_data_source, (void *)infile, 
			  nvr, &system_record);
  hc_nvr_free(nvr);
  close(infile);  
  if(res == HCERR_OK) {
    describe_system_record(&system_record);
    memcpy(*oid, &system_record.oid, sizeof(hc_oid));
  }
  return res;
}

hcerr_t test_store_metadata(hc_oid *oid) {
  hcerr_t err = -1;
  char *names[] = {"system.test.type_string", "system.test.type_char"};
  char *values[] = {"FOOBAR1", "lose"};
  hc_system_record_t system_record;
  hc_nvr_t *nvr;

  if (oid == NULL){
    printf("No OID to test with--aborting\n");
    return HCERR_INVALID_OID;
  }
  err = hc_nvr_create_from_string_arrays(the_session, &nvr,names,values,2);
  if (err != HCERR_OK) return err;

  err = hc_store_metadata_ez(the_session,oid, nvr, &system_record);

  hc_nvr_free(nvr);

  describe_system_record(&system_record);
  return err;
}



hcerr_t test_retrieve_metadata(hc_oid *oid) {
  char **names = NULL;
  char **values = NULL;
  int count;
  int i = 0;
  hc_nvr_t *nvr;

  hcerr_t err;

  printf(" hc_retrieve_metadata_ez..\n");
  err = hc_retrieve_metadata_ez(the_session, oid, &nvr);
  if (err != HCERR_OK) {
    printres_and_error(err);
    return err;
  }
  printf(" hc_nvr_convert_to_string_arrays..\n");
  err = hc_nvr_convert_to_string_arrays(nvr,&names,&values,&count);
  if (err != HCERR_OK) {
    printres_and_error(err);
    return err;
  }
  printf("Retrieved %d \n", count);

  for (i=0; i<count; i++){
    printf("%s %s\n", names[i], values[i]);
    free(names[i]);
    free(values[i]);
  }
  free(names);
  free(values);
  return err;
}





/* hcerr_t test_store_serial (int n){ */
/*   int i = 0; */
/*   hc_oid storeoid; */

/*    for (i=0; i<n; i++){ */
/*      printf("%d\n", i); */
/*      clock_t start = clock(); */
/*      clock_t end; */
/*      printres(test_store_data(&storeoid));  */
/*      end = clock(); */
/*      printf("Store took %f seconds\n", (end - start) / (float) CLOCKS_PER_SEC); */
/*      fflush(stdout); */
/*    } */
/* } */




#define PLATSTR_LEN 120

int main(int argc, char **argv) {
  char platform[PLATSTR_LEN];
  hcerr_t hcerr;
  hc_oid storeoid;
  hc_oid store_metadata_oid;
  int32_t response_code = 0;
  int errstr_len = ERRSTR_LEN;
  char errstr[ERRSTR_LEN];
  hc_archive_t *archive;
  hc_long_t debug = 0;
  hc_long_t nholds = 0;
  hc_long_t nobjs = 0;
  struct timeval tp;
  time_t timestamp;
  hc_query_result_set_t *held = NULL;
  int32_t hold_count = 0;
  time_t retention_time = 0;

  if (argc > 1)
    host = argv[1];
  if(argc > 2) 
    port = atoi(argv[2]);
  if (argc > 3) 
    debug = (hc_long_t) atol(argv[3]);
  if(argc > 4) 
    filename = argv[4];
  if (argc > 5)
    echo = atoi(argv[5]);
    
  if (debug) {
    hc_set_debug_flags(debug);
  }

  /* printf("Testing HC Client Library, Version %s on %s:%d.\n", PACKAGE_VERSION, host, port);  */
  
  /*
  printf("TESTING BUFFERED XML...\n");
  test_buffered_write_xml();

  printf("TESTING XML...\n");
  hcerr = test_write_xml();
  if (hcerr == HCERR_OK)
    printf("\nWrite XML succeeded.\n");
  else
    printf("Write XML failed with code %d.\n", hcerr);
  */

  hc_init(malloc,free,realloc);
  hcerr = hcoa_get_platform_name(platform, PLATSTR_LEN);
  if (hcerr == HCERR_OK){
    printf("Operating System Platform: %s\n", platform);
  }
  else{
    printres(hcerr);
  }

  hcerr = hc_session_create_ez(host,port, &the_session);
  if (hcerr != HCERR_OK) 
    printres_and_error(hcerr);

  printf("TESTING RETRIEVE SCHEMA...\n"); 
  printres_and_error (test_retrieve_schema());

  printf("TESTING STORE BOTH...\n"); 
  printres_and_error (test_store_both (&store_metadata_oid));

  printf("TESTING RETRIEVE TO FILE for %x\n", store_metadata_oid); 
  response_code = -1;  
  hc_session_get_archive(the_session, &archive);
  hcerr = hcoa_retrieve_ez_download_file(archive, "retrieved",
                                         &store_metadata_oid,
					 &response_code, errstr, ERRSTR_LEN);
  old_printres_and_error(hcerr,response_code,errstr);

  printf("TESTING RETRIEVE...\n"); 
  printres_and_error(hc_retrieve_ez(the_session, &echo_downloaded_data, NULL, &store_metadata_oid));

   
  printf("TESTING RETRIEVE RANGE ["LL_FORMAT"-"LL_FORMAT"]\n",
	  firstbyte,lastbyte); 
  printres_and_error(hc_range_retrieve_ez(the_session, &echo_downloaded_data, NULL, &store_metadata_oid,firstbyte,lastbyte));

  printf("TESTING RETRIEVE METADATA...\n"); 
  printres_and_error(test_retrieve_metadata(&store_metadata_oid));

  printf("TESTING QUERY...\n");
  printres_and_error (test_query (
      "system.test.type_string='Quux' OR system.test.type_string LIKE 'FOO%'"));

  printf("TESTING QUERY/PSTMT...\n");
  printres_and_error (test_query_pstmt());
  
  printf("TESTING 'QUERY PLUS'...\n");
  printres_and_error (test_query_plus ());

  printf("TESTING STORE METADATA...%s\n", store_metadata_oid); 
  printres_and_error(test_store_metadata(&store_metadata_oid));

  printf("TESTING STORE DATE/TIME/TIMESTAMP METADATA...%s\n", store_metadata_oid); 
  printres_and_error(test_store_metadata_time_date_types(&store_metadata_oid));

  printf("TESTING QUERY/PSTMT DATE/TIME/TIMESTAMP TYPES...\n");
  printres_and_error (test_query_pstmt_time_date_types());

  printf("TESTING DELETE (deleting %s)...\n", store_metadata_oid); 
  printres_and_error(hc_delete_ez(the_session, &store_metadata_oid));

  // see bug 6502173
  //printf("TESTING STORE DATA FROM FILE...\n");  
  //hcerr = test_store_data_from_file(&storeoid, &response_code, errstr, ERRSTR_LEN);
  //old_printres_and_error(hcerr,response_code,errstr);

  // see bug 6502173
  //printf("TESTING STORE DATA...\n");  
  //hcerr = test_store_data(&storeoid, &response_code, errstr, ERRSTR_LEN);
  //old_printres_and_error(hcerr,response_code,errstr);
  //
  //printf("TESTING RETRIEVE DATA...\n"); 
  //printres_and_error(hc_retrieve_ez(the_session, &echo_downloaded_data, NULL, &storeoid));

/* Delete in advanced API fails on bug 6276284, skipping test.

  printf("TESTING DELETE DATA (deleting %s)...\n", storeoid); 
  printres_and_error(hc_delete_ez(the_session, &storeoid));
*/

  /********************************************************
   *
   * Bug 6554027 - hide retention features
   *
   *******************************************************/
  /*
  printf ("TESTING GET DATE...\n");
  timestamp = 0;
  printres_and_error(hcoa_get_date_ez(archive, &timestamp, &response_code, 
                     errstr, ERRSTR_LEN));
  printf("Cluster date = %ld.\n", (long)timestamp);
  */

/* Waiting for fix to bug:  6525743 Retention time compliance C APIs 
   are broken  */
/*   printf ("TESTING SET RETENTION DATE...\n"); */
/*   retention_time = time(0) + 36000; */
/*   printf("Setting retention time for oid %s to %ld\n", */
/* 	 store_metadata_oid, (long)retention_time); */
/*   fflush(stdout); */
/*   printres_and_error (test_store_both (&store_metadata_oid)); */
/*   printres_and_error(hcoa_set_retention_date_ez(archive, &store_metadata_oid,  */
/*                      retention_time, &response_code, errstr, ERRSTR_LEN)); */


/*   timestamp = 0; */
/*   printf ("TESTING GET RETENTION DATE...\n"); */
/*   fflush(stdout); */
/*   printres_and_error(hcoa_get_retention_date_ez(archive, &store_metadata_oid,  */
/*                      &timestamp, &response_code, errstr, ERRSTR_LEN)); */
/*   printf ("get retention time returned time = %ld\n",  */
/* 	  (long)timestamp); */

  /********************************************************
   *
   * Bug 6554027 - hide retention features
   *
   *******************************************************/
  /*
  printf ("TESTING ADD HOLD...\n");
  fflush(stdout);
  printres_and_error(hcoa_add_hold_ez(archive, &store_metadata_oid, 
                     "holdme", &response_code, errstr, ERRSTR_LEN));
  printres_and_error(hcoa_add_hold_ez(archive, &store_metadata_oid, 
                     "holdme too", &response_code, errstr, ERRSTR_LEN));

  printf ("TESTING DEL HOLD...\n");
  printres_and_error(hcoa_del_hold_ez(archive, &store_metadata_oid, 
                     "holdme too", &response_code, errstr, ERRSTR_LEN));
  */
 
  // fails silently w/ [BDBSystemCache.layoutMapIdsToQuery] 
  // EMDException: Query cannot be null - 
  // note 6525743 Retention time compliance C APIs are broken
  //printf ("TESTING OIDS UNDER HOLD...\n");
  //printres_and_error (test_query_holds("queryHold holdme"));

  hc_session_free(the_session);
  hc_cleanup();

  printf("Tests finished.\n");

  exit(0);
}
