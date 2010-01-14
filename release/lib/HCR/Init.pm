#
# $Id: Init.pm 10849 2007-05-19 02:44:57Z bberndt $
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

package HCR::Init;

$::hcr_svn_root = @ENV{HCR_SVN_ROOT} || die "\
The HCR_SVN_ROOT environment variable is not defined\
\n";
if (substr($::hcr_svn_root, length($::hcr_svn_root)-1) eq "/") {
    chop($::hcr_svn_root);
}

$::hcr_svn_trunk = $::hcr_svn_root."/trunk";
$::hcr_svn_dev = $::hcr_svn_root."/hcr_dev";
$::hcr_svn_tags = $::hcr_svn_root."/hcr_tags";

$::hcr_dir = @ENV{HCR_DIR} || die("The HCR_DIR environment variable is not defined\n");
if (substr($::hcr_dir, length($::hcr_dir)-1) eq "/") {
    chop($::hcr_dir);
}
-d $::hcr_dir || die("Directory $::hcr_dir does not exist. Create it first\n");

$::hcr_modules = $::hcr_dir."/modules";
$::hcr_releases = $::hcr_dir."/releases";

print <<END;
# Honeycomb storage product release system
# Copyright 2007 Sun Microsystems, Inc.  All rights reserved.

END


sub init {
    -d $::hcr_modules || mkdir $::hcr_modules, 0755;
    -d $::hcr_releases || mkdir $::hcr_releases, 0755;
}

sub check {
    -d $::hcr_modules || die("Directory $::hcr_modules does not exit. Run repository_init first\n");
    -d $::hcr_releases || die("Directory $::hcr_releases does not exit. Run repository_init first\n");
}

1;

