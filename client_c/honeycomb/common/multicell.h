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



#ifndef __MULTI_CELL___
#define __MULTI_CELL___

#include "hc.h"

#define MAX_CELLID 127

typedef uint16_t cell_id_t;
typedef uint64_t cell_capacity_t;

typedef struct rule_ {
  cell_id_t id;
  int       start;
  int       end;
} rule_t;

typedef struct cell_ {
  cell_id_t       id;
  char            *addr;
  int             port;
  cell_capacity_t max_capacity;
  cell_capacity_t used_capacity;
  rule_t          *rules;
} cell_t;

/*  In this implementation we assume cells are contiguous. 
    -- from cell 1 to silo_size. */
#define MAX_CELLS 250
typedef struct silo_ {
  uint64_t major_version;
  uint64_t minor_version;
  uint16_t silo_size;
  cell_t   *cells[MAX_CELLS];
} silo_t;
/* Initial Request puts these values (-1.0) in for multicell
   major and minor version. */
#define	MULTICELL_INIT_MAJOR_VERSION -1
#define	MULTICELL_INIT_MINOR_VERSION 0

extern hcerr_t init_multi_cell(silo_t **silo);
extern hcerr_t free_multi_cell(silo_t *silo);
extern void print_silo(silo_t *silo);
extern cell_id_t get_cell_id(hc_oid oid);

#endif  /*  __MULTI_CELL___ */
