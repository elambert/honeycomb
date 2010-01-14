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
#include <sys/types.h>
#include <string.h>
#include <assert.h>

#define	COMPILING_HONEYCOMB
#include "hc.h"
#include "hcinternals.h"
#include "hcoa.h"
#include "hcnvoai.h"
#include "multicell.h"

#define PCT_POWER_OF_TWO

/* Refresh cell info every 30 minutes */
#define POWER_OF_TWO_REFRESH (60 * 30)

hcerr_t init_multi_cell(silo_t **silop) {
  silo_t *silo;

  ALLOCATOR(silo, sizeof(silo_t));
  memset((char *)silo,0,sizeof(*silo));

  // Whenever we recreate the silo, start out with version = -1.0
  silo->major_version = MULTICELL_INIT_MAJOR_VERSION;
  silo->minor_version = MULTICELL_INIT_MINOR_VERSION;

  *silop = silo;
  return HCERR_OK;
}

hcerr_t free_multi_cell(silo_t *silo) {
    int i;

    assert(silo!=NULL);

    for (i = 0; i < MAX_CELLS; i++) {
      if (silo->cells[i] != NULL) {
        if (silo->cells[i]->addr != NULL) {
          deallocator(silo->cells[i]->addr);
        }
	deallocator(silo->cells[i]);
	silo->cells[i] = NULL;
      }
    }
    deallocator(silo);
    return HCERR_OK;
}

void print_silo(silo_t *silo) {
  int i;

  HC_DEBUG_MC(("hive version:  %lld.%lld\n", silo->major_version, silo->minor_version));

  for (i = 0; i < silo->silo_size; i++) {
    cell_t *cell = silo->cells[i];

    HC_DEBUG_MC(("\tId: %d  addr: %s:%d  max: %lld  used: %lld\n",
	cell->id, cell->addr, cell->port, 
	cell->max_capacity, cell->used_capacity));
  }
}

cell_t *get_cell(hc_int_archive_t *archive, cell_id_t id) {
  silo_t *silo = archive->silo;
  int i;

  if (HC_DEBUG_MC_IF) {
    print_silo(silo);
  }

  if (silo->silo_size == 0) {
    if (id == 0) {
      // hack to accommodate emulator
      // since it doesn't do mcell config
      return &archive->default_cell;
    }
    return NULL;
  }
  for (i = 0; i < silo->silo_size; i++) {
    if (silo->cells[i]->id == id)
      return silo->cells[i];
  }
  return NULL;
}

void print_cell_info(hc_query_handle_t *handle) {
  int i;

  if (handle->ncells == 0) {
      HC_DEBUG_MC(("  no mcell_ids\n"));
    return;
  }
  for (i = 0; i<handle->ncells; i++) {
      HC_DEBUG_MC(("  %d  id %d\n", i, handle->mcell_ids[i]))
  }
}

hcerr_t init_query_mcell(hc_query_handle_t *handle) {

  hc_int_archive_t *arc = (hc_int_archive_t *)
                          handle->retrieve_metadata_handle.session->archive;
  int i;

  if (arc->silo->silo_size == 0) {
    handle->ncells = 0;
    handle->cur_cell = -1;
    return HCERR_OK;
  }
  handle->ncells = arc->silo->silo_size;
  for (i = 0; i<handle->ncells; i++) {
    handle->mcell_ids[i] = arc->silo->cells[i]->id;
    HC_DEBUG_MC(("  %d  id %d\n", i, handle->mcell_ids[i]));
  }
  handle->cur_cell = 0;

  return HCERR_OK;
}


cell_t *get_cur_query_cell(hc_query_handle_t *handle) {

  hc_int_archive_t *arc = (hc_int_archive_t *)
                          handle->retrieve_metadata_handle.session->archive;

  if (HC_DEBUG_MC_IF) {
    print_cell_info(handle);
  }

  if (handle->ncells == 0)
    return &arc->default_cell;

  HC_DEBUG(("get cell %d id %d\n", handle->cur_cell, 
            handle->mcell_ids[handle->cur_cell]));
  return get_cell(arc, handle->mcell_ids[handle->cur_cell]);
}

static cell_t *get_store_2_cell(silo_t *silo) {

  struct timeval tp;
  cell_t * res = NULL;
  cell_t *cell1, *cell2;
  float f1, f2;
  float diff;
  int r;
  float tmp;
  cell_t *tmp1 = silo->cells[0];
  cell_t *tmp2 = silo->cells[1];
  
#ifdef PCT_POWER_OF_TWO
  /* Windows requires signed 64_t's to cast */
  int64_t used = tmp1->used_capacity;
  int64_t max = tmp1->max_capacity;
  f1 = (1 - (float) used / max) * 100;
  used = tmp2->used_capacity;
  max = tmp2->max_capacity;
  f2 = (1 - (float) used / max) * 100;

  if (f1 < f2) {
      cell1 = tmp1;
      cell2 = tmp2;
      diff = f2 - f1;
  } else {
      cell1 = tmp2;
      cell2 = tmp1;
      diff = f1 - f2;
      tmp = f1;
      f1 = f2;
      f2 = tmp;
  }
#else 
  int64_t available1 = tmp1->max_capacity - tmp1->used_capacity;
  int64_t available2 = tmp2->max_capacity - tmp2->used_capacity;
  if (available1 < available2) {
      cell1 = tmp1;
      cell2 = tmp2;
      diff = (available2 - available1);
      diff = (diff / (float) available2) * 100;
  } else {
      cell1 = tmp2;
      cell2 = tmp1;
      diff = (available1 - available2);
      diff = (diff / (float) available1) * 100;
  }
#endif

  gettimeofday(&tp, NULL); 
  srand(tp.tv_usec);

  r = (rand() % 100);
  if (r > (50 + (int) diff)) {
      res = cell1;
  } else {
      res = cell2;
  }

#ifdef PCT_POWER_OF_TWO  
  HC_DEBUG_MC(("get_store_2_cell : cell %d free = %f, cell %d free = %f"
    ", diff = %f, (int diff = %d) r = %d, chose %d\n",
      cell1->id, f1, cell2->id, f2, diff, (int) diff, r, res->id));
#endif

  return res;
}


static cell_t *get_store_N_cell(silo_t *silo) {

  struct timeval tp;
  int i, i1, i2;
  cell_t *cell1, *cell2;
  float f1, f2;

  gettimeofday(&tp, NULL); 
  srand(tp.tv_usec);

  i1 = (rand() % silo->silo_size);
  cell1 = silo->cells[i1];

  do {
    i2 = (rand() % silo->silo_size);
  } while (i2 == i1);
  cell2 = silo->cells[i2];

  // handle null-config issue, simply & partially
  if (cell1->max_capacity == 0)
    return cell2;
  if (cell2->max_capacity == 0)
    return cell1;

#ifdef PCT_POWER_OF_TWO
{
  /* Windows requires signed 64_t's to cast */
  int64_t used = cell1->used_capacity;
  int64_t max = cell1->max_capacity;
  f1 = (float)used / max;
  used = cell2->used_capacity;
  max = cell2->max_capacity;
  f2 = (float)used / max;
}
  if (f1 < f2) {
    return cell1;
  }
#else
  if ((cell1->max_capacity - cell1->used_capacity) >
      (cell2->max_capacity - cell2->used_capacity)) {
    return cell1;
  }
#endif
  if (cell2->max_capacity - cell2->used_capacity > 0) {
    return cell2;
  }

  for (i = 0; i < silo->silo_size*2; i++) {
    i2 = (rand() % silo->silo_size);
    cell2 = silo->cells[i2];
    if (cell2->max_capacity - cell2->used_capacity > 0)
      return cell2;
  }
  for (i = 0; i < silo->silo_size; i++) {
    cell2 = silo->cells[i];
    if (cell2->max_capacity - cell2->used_capacity > 0)
      return cell2;
  }

  return NULL;
}


/*  Implements PowerOfTwo algorithm: */
/*  Get two random cells, and pick the one with the lowest capacity */

cell_t *get_store_cell(hc_int_archive_t *archive) {

  silo_t *silo = archive->silo;

  print_silo(silo);

  // use default cell if no config
  if (silo->silo_size == 0)
    return &archive->default_cell;

  // Mono cell
  if (silo->silo_size == 1) {
    return silo->cells[0];
  }

  if (silo->silo_size == 2) {
      return get_store_2_cell(silo);
  }

  return get_store_N_cell(silo);
}


cell_t *lookup_cell(hc_int_archive_t *archive, int cellid) {

  silo_t *silo = archive->silo;
  int i;

  if (cellid < 0  ||  cellid > MAX_CELLID)
    return NULL;

  print_silo(silo);

  for (i = 0; i < silo->silo_size; i++) {
    if (silo->cells[i]->id == cellid)
      return silo->cells[i];
  }
  return NULL;
}


/*  Decode the cellId from the Hex representation of the OID. */

/* [???] This function should take the silo as an argument, so it can 
   verify that the supplied OID even makes sense in the current silo.
*/
cell_id_t get_cell_id(hc_oid oid) {

    char *ptr = (char *) oid + CELL_ID_OFFSET;
    int cellId = 0;

    assert(sizeof(cell_id_t) == CELL_ID_LEN);
    assert(CELL_ID_LEN == 2);

    if (sscanf(ptr, "%02x", &cellId) != 1) {
        HC_ERR_LOG(("get_cell_id: illegal cell id value: %2.2s in oid %s",
		    ptr, oid));
	return -1;
    }
    return cellId;
}

