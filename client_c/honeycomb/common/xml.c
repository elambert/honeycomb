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
#include <assert.h>

#define	COMPILING_HONEYCOMB
#include "hc.h"
#include "platform.h"
#include "hcinternals.h"

static char *token_finish(buffer_list_t *buffer_list);

/**
 * This file implements support for XML parsing and writing.
 * The Honeycomb client will work with any implementation of 
 * the platform.h headers; this is the default implementation. 
 * We may replace this with a third party package later if.
 * instancefor, there turn out to be Unicode issues which we 
 * did not anticipate...
 */

#define DEBUG FALSE

#ifdef DEBUG
#include <stdio.h>
#endif


/*************************************
 *           XML Writing             *
 *************************************/

static const hcsize_t HC_XML_WRITE_BUFFER_SIZE = 50;
static const char *HC_XML_DOCUMENT_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

static int PP_INDENT = TRUE;
static int PP_LF = TRUE;

typedef struct hc_tag_stack_{
  char *tag;
  int len;
  struct hc_tag_stack_ *enclosed_by;
} hc_tag_stack_t;

static void pop_tag (hc_tag_stack_t **top){
  hc_tag_stack_t *old_top = *top;
  *top = (*top)->enclosed_by;
  deallocator(old_top);
}

static hcerr_t push_tag (hc_tag_stack_t **top, hc_tag_stack_t *old_top, char *tag){
  /* copy string? */
  ALLOCATOR(*top, sizeof(hc_tag_stack_t));
  (*top)->enclosed_by = old_top;
  (*top)->tag = tag;
  return HCERR_OK;
}



/* Writing/Generating XML */


typedef struct hc_simple_xml_writer_t{
  int write_to_callback;
  char *document_tag;
  char *buffer;
  hc_buffer_index_t buf_len;
  hc_buffer_index_t position;
  write_bytes_callback_t write_callback;
  void *stream;
  int indent;
  hc_tag_stack_t *tag_stack;
  allocator_t allocator;
  deallocator_t deallocator;
}
hc_simple_xml_writer_t;


static hcerr_t hc_write (hc_simple_xml_writer_t *writer, const char *data){
  /* if we are streaming, write to callback */
  if (writer->write_to_callback == TRUE){
    while (writer->position + strlen(data) >= HC_XML_WRITE_BUFFER_SIZE){
      hcerr_t res;
      unsigned int n = HC_XML_WRITE_BUFFER_SIZE - writer->position - 1;
      strncpy (writer->buffer + writer->position, data, n);
      res = (writer->write_callback) (writer->buffer, HC_XML_WRITE_BUFFER_SIZE, FALSE, writer->stream);
      if (res != HCERR_OK) {
	return res;
      }
      writer->position = 0;
      data += n;
    }
  }
  else{
    /* if we are writing to a supplied buffer, the data had better fit... */
    if (strlen(data) + 1 + writer->position >= writer->buf_len){ 
      HC_ERR_LOG(("ERROR: hc_write: XML buffer overflow"));
      return HCERR_XML_BUFFER_OVERFLOW;
    }
  }

  strcpy(writer->buffer + writer->position, data);
  writer->position += strlen(data);
  return HCERR_OK;
}

/* add decorative whitespace */
static hcerr_t pretty_print (hc_simple_xml_writer_t *writer, int open_tag){

  int i;

  if (PP_INDENT == TRUE) {
    if (PP_LF == TRUE) {
      require_ok(hc_write(writer, "\n"));
    }
    if (open_tag == FALSE)
      writer->indent--;

    for (i = 0; i < writer->indent; i++) {
      require_ok(hc_write(writer, "  "));
    }
    if (open_tag == TRUE)
      writer->indent++;
  }
  return HCERR_OK;
}



static hcerr_t base_start_document (char *document_tag, 
			     xml_writer **writer,
                             allocator_t a, 
                             deallocator_t d,
                             int pp){
  hc_simple_xml_writer_t *simple_writer;
  PP_LF = pp;
  PP_INDENT = pp;
  ALLOCATOR(simple_writer, sizeof(hc_simple_xml_writer_t));
  memset(simple_writer, 0, sizeof(hc_simple_xml_writer_t));
  //simple_writer->position = 0;

  ALLOCATOR(simple_writer->document_tag, strlen(document_tag) + 1);
  strcpy(simple_writer->document_tag, document_tag);
  simple_writer->indent = 0;
  simple_writer->tag_stack = NULL;
  simple_writer->allocator = a;
  simple_writer->deallocator = d;
  *writer = (xml_writer*) simple_writer;
  return HCERR_OK;
}



static hcerr_t write_document_element(xml_writer *writer){
  require_ok(hc_write((hc_simple_xml_writer_t *) writer, HC_XML_DOCUMENT_HEADER));
  if (PP_LF == TRUE) {
    require_ok(hc_write((hc_simple_xml_writer_t *) writer, "\n"));
  }
  require_ok(hc_write((hc_simple_xml_writer_t *) writer, "<"));
  require_ok(hc_write((hc_simple_xml_writer_t *) writer, ((hc_simple_xml_writer_t *) writer)->document_tag));
  require_ok(hc_write((hc_simple_xml_writer_t *) writer, ">"));
  return HCERR_OK;
}

/* Caller supplies buffer */
hcerr_t start_buffered_document (char *document_tag, 
				 char *buffer,
				 hc_buffer_index_t len,
				 xml_writer **writer,
                                 allocator_t a, 
                                 deallocator_t d,
				 int pp){
  require_ok(base_start_document (document_tag, writer, a, d, pp));
  ((hc_simple_xml_writer_t *) (*writer))->write_to_callback = FALSE;
  ((hc_simple_xml_writer_t *) (*writer))->buf_len = len;
  ((hc_simple_xml_writer_t *) (*writer))->buffer = buffer;
  ((hc_simple_xml_writer_t *) (*writer))->allocator = a;
  ((hc_simple_xml_writer_t *) (*writer))->deallocator = d;
  require_ok(write_document_element(*writer));
  return HCERR_OK;
}


/* Write to supplied stream when our buffer fills. */
hcerr_t start_document (char *document_tag, 
			write_bytes_callback_t backend_writer,
			void *stream, 
			xml_writer **writer,
                        allocator_t a, 
                        deallocator_t d,
			int pp){
  char *buf;

  require_ok(base_start_document (document_tag, writer, a, d, pp));
  ((hc_simple_xml_writer_t *) (*writer))->write_to_callback = TRUE;
  ALLOCATOR(buf, HC_XML_WRITE_BUFFER_SIZE * sizeof(char));
  ((hc_simple_xml_writer_t *) (*writer))->buffer = buf;
  ((hc_simple_xml_writer_t *) (*writer))->write_callback = backend_writer;  
  ((hc_simple_xml_writer_t *) (*writer))->stream = stream;
  require_ok(write_document_element(*writer));
  return HCERR_OK;
}


hcerr_t start_element (xml_writer *writer,
		       char *element_name, 
		       char **attribute_names, 
		       char **attribute_values,
		       int n_attributes){
  int i;
  hc_simple_xml_writer_t *simple_writer = (hc_simple_xml_writer_t*) writer;
  hc_tag_stack_t *top = 0;

  require_ok(pretty_print(simple_writer, TRUE));
  require_ok(push_tag (&top, simple_writer->tag_stack, element_name));

  simple_writer->tag_stack = top;
  require_ok(hc_write(simple_writer, "<"));
  require_ok(hc_write(simple_writer, element_name));
  /*fprintf(stdout, "%d attributes\n", n_attributes); */
  for (i = 0; i < n_attributes; i++){
    require_ok(hc_write(simple_writer, " "));
    require_ok(hc_write(simple_writer, attribute_names[i]));
    require_ok(hc_write(simple_writer, "=\""));
    require_ok(hc_write(simple_writer, attribute_values[i]));
    require_ok(hc_write(simple_writer, "\""));
  }
  require_ok(hc_write(simple_writer, ">"));
  return HCERR_OK;
}

/* Sacha thinks we should omit the element name, but this is more in line with SAX */
hcerr_t end_element (xml_writer *xml_writer,
		     char *element_name){
  hc_simple_xml_writer_t *writer = (hc_simple_xml_writer_t*) xml_writer;
  char *current_open_element = writer->tag_stack->tag;

  require_ok(pretty_print(writer, FALSE));
  if (strcmp(current_open_element, element_name) != 0)
    HC_ERR_LOG(("expected '%s', closing '%s'\n", current_open_element, element_name));

  require_ok(hc_write(writer, "</"));
  require_ok(hc_write(writer, element_name));
  require_ok(hc_write(writer, ">"));
  pop_tag (&writer->tag_stack);
  return HCERR_OK;
}

hcerr_t end_document_and_close (xml_writer *xml_writer, hc_long_t *length){
  hc_simple_xml_writer_t *writer = (hc_simple_xml_writer_t*) xml_writer;
  hc_tag_stack_t *stack = writer->tag_stack;

  assert(writer->document_tag != NULL);

  while (stack != NULL){
    require_ok(end_element(xml_writer, stack->tag));
    stack = writer->tag_stack;
  }
  require_ok(pretty_print(writer, FALSE));
  require_ok(hc_write(writer, "</"));
  require_ok(hc_write(writer, writer->document_tag));
  require_ok(hc_write(writer, ">"));
  if (PP_LF) {
    require_ok(hc_write(writer, "\n"));
  }

  *length = writer->position;

  if (writer->write_to_callback == TRUE){
    require_ok((writer->write_callback)(writer->buffer, writer->position, TRUE, writer->stream));
    assert(writer->buffer != NULL);
    deallocator(writer->buffer);
  }
  else {
    writer->buffer[writer->position] = 0;
  }

  deallocator(writer->document_tag);
  deallocator(writer);
  return HCERR_OK;
}




/*************************************
 *      XML Reading/Parsing          *
 *************************************/

/*
 * Implement a simple state machine for an event driven parser.
 * As with SAX, you register event handlers (callbacks) which 
 * are notified of parse events.
 *
 * One nice thing about this parser is that it will detect the 
 * end of the document without an explicite EOF.
 */

/* state */
#define OUTSIDE_ELEMENT 0
#define SCANNING_TAG_FIRST_CHAR 1
#define EXPECTING_TAG 2
#define SCANNING_TAG 3
#define SCANNING_ATTRIBUTES 4
#define SCANNING_ATTRIBUTE_NAME 5
#define SCANNING_ATTRIBUTE_VALUE 6
#define SCANNING_CLOSE_TAG 7
#define SCANNING_START_ATTRIBUTE_VALUE 8
#define DOCUMENT_ELEMENT 9
#define EXPECTING_RIGHT_BRACKET 10
#define EXPECTING_OPEN_OR_CLOSE_TAG 11



#define CURRENT_TOKEN_BUFFER_INITIAL_SIZE 10

static hcerr_t make_buffer_list(buffer_list_t *buffer_list){
  buffer_list->current_token_buffer_size = CURRENT_TOKEN_BUFFER_INITIAL_SIZE;
  ALLOCATOR(buffer_list->string_buffer, sizeof(buffer_cell_t));
  buffer_list->string_buffer->previous = NULL;
  buffer_list->current_token_start = 0;
  buffer_list->current_token_position = 0;
  ALLOCATOR(buffer_list->string_buffer->data, CURRENT_TOKEN_BUFFER_INITIAL_SIZE * sizeof(char));

  return HCERR_OK;
}

#define ATTRIBUTE_ARRAYS_INITIAL_SIZE 2

typedef struct simple_xml_parser{
  start_element_handler_t start_element_callback;
  end_element_handler_t end_element_callback;
  void *data;

  /* parser state */
  int state;
  char *current_tag;
  int depth;
  int start_tag;
  int backslash;

  /* Need to support arbitrarily many of these... */
  char **attribute_names/*[ATTRIBUTE_ARRAYS_INITIAL_SIZE]*/;
  char **attribute_values/*[ATTRIBUTE_ARRAYS_INITIAL_SIZE]*/;
  int attribute_arrays_size;
  int current_attribute;
  allocator_t allocator;
  deallocator_t deallocator;

  buffer_list_t buffer_list;
  int count;

} simple_xml_parser;

int count = 0;

/* Initialize parser state to be passed around as an opaque reference */
hcerr_t xml_create (start_element_handler_t start_element_callback,
		    end_element_handler_t end_element_callback, 
		    void *data,
		    xml_parser **parser,
                    allocator_t a, 
                    deallocator_t d){
  simple_xml_parser *simple_parser;
  
  ALLOCATOR(simple_parser, sizeof(simple_xml_parser));
  
  simple_parser->start_element_callback = start_element_callback;
  simple_parser->end_element_callback = end_element_callback;
  simple_parser->data = data;

  simple_parser->attribute_arrays_size = ATTRIBUTE_ARRAYS_INITIAL_SIZE;

  simple_parser->current_attribute = 0;
  simple_parser->depth = 0;
  simple_parser->start_tag = FALSE;
  simple_parser->backslash = FALSE;    
  simple_parser->state = OUTSIDE_ELEMENT;  
  simple_parser->count = count++;
  simple_parser->allocator = a;
  simple_parser->deallocator = d;
  simple_parser->attribute_names = NULL;
  ALLOCATOR(simple_parser->attribute_values, (sizeof(char*) * ATTRIBUTE_ARRAYS_INITIAL_SIZE));
  ALLOCATOR(simple_parser->attribute_names, (sizeof(char*) * ATTRIBUTE_ARRAYS_INITIAL_SIZE));

  require_ok(make_buffer_list(&simple_parser->buffer_list));

  *parser = (xml_parser*) simple_parser;
  return HCERR_OK;
}


static int is_white_space(char c){
  if (c == ' ' || c == '\r' || c == '\t' || c == '\n')
    return TRUE;
  else
    return FALSE;
}


static int is_name_first_char(char c){
  if ((c >= 'a' && c <= 'z') || 
      (c >= 'A' && c <= 'Z') ||
      c == '_' || c == ':')
    return TRUE;
  else
    return FALSE;
}


static int is_name_char(char c){
  if (is_name_first_char(c) == TRUE ||
      (c >= '0' && c <= '9') ||
      c == '-' || c == '.')
    return TRUE;
  else
    return FALSE;
}

static hcerr_t token_append(buffer_list_t *buffer_list, char c){  
  unsigned int malloc_size = 0 ;
  buffer_cell_t *bl = NULL;

  /* Check for overflow, reserving an extra space for null terminator */
  if (buffer_list->current_token_position + 2 >= buffer_list->current_token_buffer_size){
    if (buffer_list->current_token_start == 0){
      /* Token is biffer than buffer size. */
      buffer_list->current_token_buffer_size = buffer_list->current_token_buffer_size * 2;
    }
    malloc_size = buffer_list->current_token_buffer_size * sizeof(char);
    if (DEBUG == TRUE){
      printf("mallocing buffer size %d\n", malloc_size);
      fflush(stdout);
    }
    ALLOCATOR(bl, sizeof(buffer_cell_t));
    ALLOCATOR(bl->data, malloc_size);
    bl->previous = buffer_list->string_buffer;

    /* copy fragment from old buffer */
    strncpy(bl->data, buffer_list->string_buffer->data + buffer_list->current_token_start, 
	    buffer_list->current_token_position - buffer_list->current_token_start);
    buffer_list->current_token_position = buffer_list->current_token_position - buffer_list->current_token_start;
    buffer_list->current_token_start = 0;
    buffer_list->string_buffer = bl;
  }
  buffer_list->string_buffer->data[buffer_list->current_token_position++] = c;
  /* How do I detect out-of-memory? */
  return HCERR_OK;
}


static char *token_finish(buffer_list_t *buffer_list){
  char *val = buffer_list->string_buffer->data + buffer_list->current_token_start;
  /* We know there is room for the null terminator because we looked when appending */
  buffer_list->string_buffer->data[buffer_list->current_token_position] = 0;
  buffer_list->current_token_position++;
  buffer_list->current_token_start = buffer_list->current_token_position;
  return val;
}

static void print_state(int state, int depth){
  printf("depth %d, ", depth);

  switch (state){

  case OUTSIDE_ELEMENT:
    printf("state --> %s\n", "OUTSIDE_ELEMENT");
    return;

  case SCANNING_TAG_FIRST_CHAR:
    printf("state --> %s\n", "SCANNING_TAG_FIRST_CHAR");
    return;

  case EXPECTING_TAG:
    printf("state --> %s\n", "EXPECTING_TAG");
    return;

  case SCANNING_TAG:
    printf("state --> %s\n", "SCANNING_TAG");
    return;

  case SCANNING_ATTRIBUTES:
    printf("state --> %s\n", "SCANNING_ATTRIBUTES");
    return;

  case SCANNING_ATTRIBUTE_NAME:
    printf("state --> %s\n", "SCANNING_ATTRIBUTE_NAME");
    return;

  case SCANNING_ATTRIBUTE_VALUE:
    printf("state --> %s\n", "SCANNING_ATTRIBUTE_VALUE");
    return;

  case SCANNING_CLOSE_TAG:
    printf("state --> %s\n", "SCANNING_CLOSE_TAG");
    return;

  case SCANNING_START_ATTRIBUTE_VALUE:
    printf("state --> %s\n", "SCANNING_START_ATTRIBUTE_VALUE");
    return;

  case DOCUMENT_ELEMENT:
    printf("state --> %s\n", "DOCUMENT_ELEMENT");
    return;


  case EXPECTING_RIGHT_BRACKET:
    printf("state --> %s\n", "EXPECTING_RIGHT_BRACKET");
    return;

  case EXPECTING_OPEN_OR_CLOSE_TAG:
    printf("state --> %s\n", "EXPECTING_OPEN_OR_CLOSE_TAG");
    return;
  }
  printf("state --> UNKOWN STATE %d\n", state);
}

static void change_state(int *state, int new_state, int depth){
  *state = new_state;
  if(DEBUG == TRUE){
    print_state(*state, depth);
    fflush(stdout);
  }
}

hcerr_t close_tag(int *i, simple_xml_parser *parser){
  char *tag = token_finish(&parser->buffer_list);

  if (parser->start_tag == TRUE){
    if (DEBUG == TRUE)
      fprintf(stdout, "End Start  element: %s\n", tag);
    require_ok((*parser->start_element_callback)(tag, 
						 parser->data,
						 parser->attribute_names,
						 parser->attribute_values,
						 parser->current_attribute));
    parser->current_attribute = 0;
    parser->depth++;
    change_state(&parser->state, OUTSIDE_ELEMENT, parser->depth);
    if (DEBUG == TRUE)	    
      print_state(parser->state, parser->depth);
  }
  else{
    if (DEBUG == TRUE)
      fprintf(stdout, "End Close element: %s\n", tag);
    require_ok((*parser->end_element_callback)(tag, parser->data));
    /* --> release string buffers */
    parser->depth--;
    change_state(&parser->state, OUTSIDE_ELEMENT, parser->depth);
  }
  return HCERR_OK;
}

static hcerr_t grow_attribute_arrays(simple_xml_parser *parser){
  char **names;
  char **values;
  int i = 0;

  //[???] Change the following to use "reallocator"
  ALLOCATOR(names, parser->attribute_arrays_size * 2 * sizeof(char*));
  ALLOCATOR(values, parser->attribute_arrays_size * 2 * sizeof(char*));

  for (i = 0; i < parser->current_attribute; i++){
    names[i] = parser->attribute_names[i];
    values[i] = parser->attribute_values[i];
  }
  parser->attribute_arrays_size = parser->attribute_arrays_size * 2;
  deallocator(parser->attribute_names);
  parser->attribute_names = names;
  deallocator(parser->attribute_values);
  parser->attribute_values = values;
  return HCERR_OK;
}

/*
 * Parse succesive blocks of XML data, generating events for the 
 * handlers/callbacks as we go. State is maintained in the 
 * simple_xml_parser object.
 * If the top level XML document ends before the last character, 
 * the "read" parameter indicates how much input was consumed.
 */
hcerr_t xml_parse(xml_parser *formal_parser, char s[], hc_long_t size, hc_long_t *read){
  simple_xml_parser *parser = (simple_xml_parser *)  formal_parser;

  int i = 0;

  if (DEBUG == TRUE){
    print_state(parser->state, parser->depth);
    printf ("in parser with " LL_FORMAT " %s\n", size, s);
    fflush(stdout);
  }

  while (i < size){

    switch(parser->state){

    case OUTSIDE_ELEMENT:
      if (is_white_space(s[i])){
	/*skip_white_space */
	break;
      }
      if (s[i] == '<'){
	parser->start_tag = TRUE;
	change_state(&parser->state, EXPECTING_OPEN_OR_CLOSE_TAG, parser->depth);
      }
      else {
	HC_ERR_LOG(("Expected '<', read %c at %d %s\n", s[i], i, s));
	return HCERR_XML_EXPECTED_LT;
      }
      break;
      
      
    case DOCUMENT_ELEMENT:
      /* discard document element */
      if (s[i] != '>'){
	if (DEBUG == TRUE)
	  printf("discarding %c", s[i]);
	break;
      }
      else{
	parser->state = OUTSIDE_ELEMENT;
	break;
      }

    case EXPECTING_OPEN_OR_CLOSE_TAG:
      if (is_white_space(s[i])){
	/*skip_white_space */
	break;
      }
      if (s[i] == '/'){
	if (DEBUG)
	  printf("parser->start_tag = FALSE\n");
	parser->start_tag = FALSE;
	break;
      }

    case EXPECTING_TAG:
      
      if (is_name_first_char(s[i]) == TRUE){
	change_state(&parser->state, SCANNING_TAG, parser->depth);
	require_ok(token_append(&parser->buffer_list, s[i]));
	break;
      }

      /* Discard document element */
      else if (s[i] == '?' && parser->depth == 0){
	parser->state = DOCUMENT_ELEMENT;
	break;
      }
      else{
	HC_ERR_LOG(("Invalid first character for element name : %c %d %s\n", s[i], i, s));
	return HCERR_XML_INVALID_ELEMENT_TAG;
      }
      
      // FALLTHRU INTENTIONAL???

      /* Start tag is terminated by whitespace, /, or >
	 End tag is terminated by whitespace or >
      */
    case SCANNING_TAG:
      /* Still reading token */
      if (is_name_char(s[i]) == TRUE){
	require_ok(token_append(&parser->buffer_list, s[i]));
	break;
      }
      else if (is_white_space(s[i]) == TRUE) {
	parser->current_tag = token_finish(&parser->buffer_list);
	if (parser->start_tag == TRUE){
	  /*printf("Start element: %s\n", parser->current_tag);*/
	  change_state(&parser->state, SCANNING_ATTRIBUTES, parser->depth);
	  break;
	}
	else{
	  change_state(&parser->state, SCANNING_CLOSE_TAG, parser->depth);
	  break;
	}
      }
      else if (s[i] == '>') {
	if (DEBUG == TRUE)
	  printf("parser->depth: %d\n", parser->depth);
	require_ok(close_tag(&i, parser));
	if (DEBUG == TRUE)
	  printf("parser->depth: %d\n", parser->depth);
	if (parser->depth == 0){
	  *read = i + 1;
	  return HCERR_OK;
	}
      }
      /* <element/> */
      else if (s[i] == '/' && parser->start_tag == TRUE) {
	if (DEBUG == TRUE){
	  printf("Start element: %s\n", parser->current_tag);	
	  printf("End element: %s\n", parser->current_tag);
	}
	change_state(&parser->state, EXPECTING_RIGHT_BRACKET, parser->depth);
	break;
      }
      else {
	HC_ERR_LOG(("Invalid character '%c' in tag. %i %s\n", s[i], i, s));
	return HCERR_XML_INVALID_ELEMENT_TAG;
      }
      break;

    case EXPECTING_RIGHT_BRACKET:
      if (s[i] != '>') {
	HC_ERR_LOG(("Unexpected character %c after close element. %d %s", s[i], i, s));
	return HCERR_XML_MALFORMED_START_ELEMENT;
      }
      if (parser->depth == 0){
	*read = i + 1;
	return HCERR_OK;
      }
      change_state(&parser->state, OUTSIDE_ELEMENT, parser->depth);
      break;


    case SCANNING_CLOSE_TAG:
	  if (is_white_space(s[i])) {
	    break;
	  }
	  if (DEBUG == TRUE)
	    fprintf(stdout, "End element: %s\n", parser->current_tag);
	  if (s[i] != '>') {
	    HC_ERR_LOG(("Unexpected character %c after close element. %d %s", s[i], i, s));
	    return HCERR_XML_MALFORMED_END_ELEMENT;
	  }
	  require_ok((*parser->end_element_callback)(parser->current_tag, parser->data));
	  parser->depth--;
	  if (parser->depth == 0){
	    *read = i + 1;
	    return HCERR_OK;
	  }

	  change_state(&parser->state, OUTSIDE_ELEMENT, parser->depth);
	  break;


      /* Expected tokens:
       *   attribute_name
       *   '/'
       *   >
       */
    case SCANNING_ATTRIBUTES:
      if (is_white_space(s[i])){
	/*skip_white_space */
	break;
      }
      if (is_name_first_char(s[i]) == TRUE) {
	change_state(&parser->state, SCANNING_ATTRIBUTE_NAME, parser->depth);
	require_ok(token_append(&parser->buffer_list, s[i]));
      }
      else if (s[i] == '/' && parser->start_tag == TRUE) {

	if (DEBUG == TRUE){
	  int j = 0;
	  printf("SA Start element: %s\n", parser->current_tag);		 
	  fprintf(stdout, "Start  element: %s %d\n", parser->current_tag, parser->current_attribute);
	  for (j = 0; j < parser->current_attribute; j++){
	    printf(" %s=\"%s\"", *(parser->attribute_names + j), *(parser->attribute_values + j));
	  }
	  fprintf(stdout, "End  element: %s\n", parser->current_tag);
	  fflush(stdout);
	} 
	require_ok((*parser->start_element_callback)(parser->current_tag, 
						     parser->data, 
						     parser->attribute_names,
						     parser->attribute_values,
						     parser->current_attribute));
	require_ok((*parser->end_element_callback)(parser->current_tag, parser->data));

	parser->current_attribute = 0;
	change_state(&parser->state, EXPECTING_RIGHT_BRACKET, parser->depth);
      }
      else  if (s[i] == '>') { 
	if (DEBUG == TRUE){
	  int j = 0;
	  fprintf(stdout, "Start  element event: %s %d\n", parser->current_tag, parser->current_attribute);
	  for (j = 0; j < parser->current_attribute; j++){
	    printf(" %s=\"%s\"", *(parser->attribute_names + j), *(parser->attribute_values + j));
	  }
	}
	require_ok((*parser->start_element_callback)(parser->current_tag, 
						     parser->data,
						     parser->attribute_names,
						     parser->attribute_values,
						     parser->current_attribute));

	parser->current_attribute = 0;
	parser->depth++;
	change_state(&parser->state, OUTSIDE_ELEMENT, parser->depth);
      }
      else{
	HC_ERR_LOG(("Unexpected character %c after close element. %d %s", s[i], i, s));
	return HCERR_XML_MALFORMED_START_ELEMENT;
      }
      break;

    case SCANNING_ATTRIBUTE_NAME:
      if (s[i] == '='){
	if (parser->current_attribute == parser->attribute_arrays_size){
	  require_ok(grow_attribute_arrays(parser));
	}
	parser->attribute_names[parser->current_attribute] = token_finish(&parser->buffer_list);

	change_state(&parser->state, SCANNING_START_ATTRIBUTE_VALUE, parser->depth);
      }
      else if (is_name_char(s[i]) == TRUE) {
	require_ok(token_append(&parser->buffer_list, s[i]));
      }
      else{
	HC_ERR_LOG(("Illegal char %c in attribute name. %i <<%s>>\n", s[i], i, s));
	return HCERR_XML_BAD_ATTRIBUTE_NAME;
      }
      break;

    case SCANNING_START_ATTRIBUTE_VALUE:
      if (is_white_space(s[i])){
	break;
      }
      else if (s[i] != '"'){
	HC_ERR_LOG(("Attribute value does not begin with quote: '%c'. %i %s\n", s[i], i, s));
	return HCERR_XML_BAD_ATTRIBUTE_NAME;
      }
      change_state(&parser->state, SCANNING_ATTRIBUTE_VALUE, parser->depth);
      break;


    case SCANNING_ATTRIBUTE_VALUE:
      if (s[i] == '\\') {
	if (parser->backslash == TRUE){
	  parser->backslash = FALSE;
	}
	else{
	  parser->backslash = TRUE;
	}
      }
      else if (s[i] == '"' && parser->backslash == FALSE) {
	parser->attribute_values[parser->current_attribute++] = token_finish(&parser->buffer_list);
	change_state(&parser->state, SCANNING_ATTRIBUTES, parser->depth);
      	break;
      }
      require_ok(token_append(&parser->buffer_list, s[i]));
      
      break;
    }
    i++;
  }
  return HCERR_OK;
}

static hcerr_t string_buffer_cleanup(buffer_cell_t *string_buffer){
  while (string_buffer != NULL){
    buffer_cell_t *trail = string_buffer;
    string_buffer = string_buffer->previous;
    
    if (trail->data) {
      deallocator(trail->data);
      trail->data = NULL;
    }
    deallocator(trail);
    trail = NULL;
  }
  
  return HCERR_OK;
}

hcerr_t xml_cleanup(xml_parser *formal_parser){
  simple_xml_parser *parser = (simple_xml_parser*) formal_parser;
  
  if (parser == NULL)
      return HCERR_OK;
  
  if (parser->buffer_list.string_buffer != NULL) {
    string_buffer_cleanup(parser->buffer_list.string_buffer);
    parser->buffer_list.string_buffer = NULL;
  }
  if (parser->attribute_names != NULL) {
    deallocator(parser->attribute_names);
    parser->attribute_names = NULL;
  }
  if (parser->attribute_values != NULL) {
    deallocator(parser->attribute_values);
      parser->attribute_values = NULL;
  }
  deallocator(parser);
  parser = NULL;
  formal_parser = NULL;
  return HCERR_OK;
}


