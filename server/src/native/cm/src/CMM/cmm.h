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



#ifndef __CMM_API_H
#define __CMM_API_H

#include <time.h>
#include "cmm_internal_types.h"

#ifdef __cplusplus
extern "C" {
#endif

    typedef struct timespec timespec_t;
    
    typedef struct cmm_member_t {
        cmm_nodeid_t     nodeid;
        cmm_membername_t name;
        cmm_memberaddr_t addr;
        cmm_domainid_t   domainid;
        uint32_t         sflag;
        cmm_incarn_t     incarnation_number;
        cmm_swload_t     software_load_id;
    } cmm_member_t;

    /* Invalid node id */
#define CMM_INVALID_NODE_ID	((cmm_nodeid_t) -1)


    /* MEMBERSHIP ROLE */
#define CMM_MASTER	        0x0001
#define CMM_VICEMASTER	    0x0002
#define CMM_OUT_OF_CLUSTER	0x0004
#define CMM_ROLE_MASK	    0x0007

    /* Other flags */
#define CMM_FROZEN_MEMBER       0x0100
#define CMM_EXCLUDED_MEMBER     0x0200
#define CMM_ELIGIBLE_MEMBER     0x0400

#define CMM_FLAG_DISQUALIFIED   0x0800
#define CMM_FLAG_SYNCHRO_NEEDED 0x1000

    typedef enum {
        CMM_QUALIFIED_MEMBER = -150, /* avoid to mix it with flags */
        CMM_SYNCHRO_READY,	
        CMM_DISQUALIFIED_MEMBER,
        CMM_SYNCHRO_NEEDED      
    } cmm_qualif_t;


    typedef enum  {
        CMM_CMC_NOTIFY_ADD = 20,
        CMM_CMC_NOTIFY_REM,
        CMM_CMC_NOTIFY_SET,
        CMM_CMC_NOTIFY_ALL,
        CMM_CMC_NOTIFY_NONE
    } cmm_cmcfilter_t;

    typedef enum  {
        CMM_MASTER_ELECTED = 250,
        CMM_MASTER_DEMOTED,
        CMM_VICEMASTER_ELECTED,
        CMM_VICEMASTER_DEMOTED,
        CMM_MEMBER_JOINED,
        CMM_MEMBER_LEFT,
        CMM_STALE_CLUSTER,
        CMM_INVALID_CLUSTER,
        CMM_VALID_CLUSTER,
        CMM_NODE_ELIGIBLE,
        CMM_NODE_INELIGIBLE
    } cmm_cmchanges_t;


    typedef struct {
        cmm_cmchanges_t cmchange;
        cmm_nodeid_t    nodeid;
    } cmm_cmc_notification_t;

#if defined(__STDC__) || defined(__cplusplus)
    typedef void (*cmm_notify_t)(const cmm_cmc_notification_t *change_notification,
                                 void *client_data);

    extern cmm_error_t cmm_connect(timespec_t const P_timeout); 
    extern cmm_error_t cmm_disconnect();

    extern cmm_error_t cmm_node_getid(cmm_nodeid_t * const me);

    extern cmm_error_t cmm_member_getinfo(cmm_nodeid_t const nodeid,
                                          cmm_member_t * const member);
    extern cmm_error_t cmm_potential_getinfo(cmm_nodeid_t const nodeid,
                                             cmm_member_t * const member);
    extern cmm_error_t cmm_master_getinfo(cmm_member_t * const member);
    extern cmm_error_t cmm_vicemaster_getinfo(cmm_member_t * const member);

    extern cmm_error_t cmm_member_getcount(uint32_t * const member_count);
    extern cmm_error_t cmm_member_getall(uint32_t       const table_size,
                                         cmm_member_t * const member_table,
                                         uint32_t     * const member_count);

    extern cmm_error_t cmm_mastership_release();
    extern cmm_error_t cmm_membership_remove();

    extern cmm_error_t cmm_node_eligible();
    extern cmm_error_t cmm_node_ineligible();

    extern cmm_error_t cmm_member_setqualif(cmm_nodeid_t const nodeid,
                                            cmm_qualif_t const new_qualif);
    extern cmm_error_t cmm_member_seizequalif();

    extern cmm_error_t cmm_cmc_register(cmm_notify_t callback, void *client_data);
    extern cmm_error_t cmm_cmc_unregister();
    extern cmm_error_t cmm_cmc_filter(	int P_action,
                                        cmm_cmchanges_t *P_event_list,
                                        int P_event_list_count);


    extern cmm_error_t cmm_notify_getfd(int *fd);
    extern cmm_error_t cmm_notify_dispatch();

    extern cmm_error_t cmm_config_reload();
    extern char *cmm_strerror(cmm_error_t errnum);

    extern int cmm_member_isoutofcluster(cmm_member_t const * member);
    extern int cmm_member_isfrozen(cmm_member_t const * member);
    extern int cmm_member_isexcluded(cmm_member_t const * member);
    extern int cmm_member_iseligible(cmm_member_t const * member);

    extern int cmm_member_ismaster(cmm_member_t const * member);
    extern int cmm_member_isvicemaster(cmm_member_t const * member);

    extern int cmm_member_isqualified(cmm_member_t const * member);
    extern int cmm_member_isdisqualified(cmm_member_t const * member);
    extern int cmm_member_isdesynchronized(cmm_member_t const * member);
#else /* NOT __STDC__ */
    typedef void (*cmm_notify_t)();

    extern cmm_error_t cmm_connect(); 
    extern cmm_error_t cmm_disconnect();

    extern cmm_error_t cmm_node_getid();

    extern cmm_error_t cmm_member_getinfo();
    extern cmm_error_t cmm_potential_getinfo();
    extern cmm_error_t cmm_master_getinfo();
    extern cmm_error_t cmm_vicemaster_getinfo();

    extern cmm_error_t cmm_member_getcount();
    extern cmm_error_t cmm_member_getall();

    extern cmm_error_t cmm_mastership_release();
    extern cmm_error_t cmm_membership_remove();


    extern cmm_error_t cmm_member_setqualif();
    extern cmm_error_t cmm_member_seizequalif();

    extern cmm_error_t cmm_cmc_register();
    extern cmm_error_t cmm_cmc_unregister();
    extern cmm_error_t cmm_cmc_filter();


    extern cmm_error_t cmm_notify_getfd();
    extern cmm_error_t cmm_notify_dispatch();

    extern cmm_error_t cmm_config_reload();
    extern char *cmm_strerror(cmm_error_t errnum);

    extern int cmm_member_isoutofcluster();
    extern int cmm_member_isfrozen();
    extern int cmm_member_isexcluded();
    extern int cmm_member_iseligible();

    extern int cmm_member_ismaster();
    extern int cmm_member_isvicemaster();

    extern int cmm_member_isqualified();
    extern int cmm_member_isdisqualified();
    extern int cmm_member_isdesynchronized();
#endif /* __STDC__ */
	
    /* DEPRECATED MACROS */
#define CMM_IS_FROZEN(member)   cmm_member_isfrozen(&(member))
#define CMM_IS_EXCLUDED(member) cmm_member_isexcluded(&(member))
#define CMM_IS_ELIGIBLE(member) cmm_member_iseligible(&(member))

#define CMM_IS_MASTER(member)     cmm_member_ismaster(&(member))
#define CMM_IS_VICEMASTER(member) cmm_member_isvicemaster(&(member))

#define CMM_IS_QUALIFIED(member)      cmm_member_isqualified(&(member))
#define CMM_IS_DISQUALIFIED(member)   cmm_member_isdisqualified(&(member))
#define CMM_IS_SEMI_QUALIFIED(member) cmm_member_isdesynchronized(&(member))
#define CMM_IS_OUT_OF_CLUSTER(member) cmm_member_isoutofcluster(&(member))


#ifdef __cplusplus
}
#endif

#endif 
