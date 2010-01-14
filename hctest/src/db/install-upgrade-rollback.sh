#!/bin/bash
#
# $Id: install-upgrade-rollback.sh 10858 2007-05-19 03:03:41Z bberndt $
#
# Copyright © 2008, Sun Microsystems, Inc.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#
#   # Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
#
#   # Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
#
#   # Neither the name of Sun Microsystems, Inc. nor the names of its
# contributors may be used to endorse or promote products derived from
# this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
# IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
# TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
# PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
# OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
# PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
# PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.



#

WHEREAMI=`cd \`dirname $0\`; pwd`
POSTRESULT=$WHEREAMI/../../../suitcase/src/script/postResult.sh

post () {
    local result=$1
    shift
    echo $POSTRESULT -r $result $*
    $POSTRESULT -r $result $*
}

post skipped upgrade_bad_version install-upgrade-rollback
post skipped rollback_bad_image install-upgrade-rollback
post skipped upgrade_ctlc_sp install-upgrade-rollback
post skipped upgrade_ctlc_switch install-upgrade-rollback
post skipped upgrade_ctlc_node install-upgrade-rollback
post skipped upgrade_node_down install-upgrade-rollback
post skipped upgrade_disk_down install-upgrade-rollback
post skipped upgrade_sp_down install-upgrade-rollback
post skipped upgrade_all_switches_down install-upgrade-rollback
post skipped upgrade_node_crash install-upgrade-rollback
post skipped upgrade_sp_crash install-upgrade-rollback
post skipped upgrade_no_dvd install-upgrade-rollback
post skipped upgrade_bad_dvd install-upgrade-rollback
post skipped upgrade_bad_url install-upgrade-rollback
post skipped upgrade_twice install-upgrade-rollback
post skipped dvd_upgrade_works_all_took_data_config_still_good install-upgrade-rollback
post skipped http_upgrade_works_all_took_data_config_still_good install-upgrade-rollback
post skipped dvd_factory_install_16_node_works install-upgrade-rollback
post skipped dvd_factory_install_defaults_work install-upgrade-rollback
post skipped dvd_factory_install_bad_disk install-upgrade-rollback
post skipped dvd_factory_install_bad_node install-upgrade-rollback
post skipped dvd_reinstall_after_new_disk install-upgrade-rollback
post skipped dvd_reinstall_after_new_node install-upgrade-rollback
