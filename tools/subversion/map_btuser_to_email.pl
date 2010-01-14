#!/usr/bin/perl -w
#
# $Id: map_btuser_to_email.pl 11664 2007-11-20 00:07:32Z elambert $
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

#
# XXX Don't edit this directly on hc-subversion!
# Edit this in your copy of the repository
# in honeycomb/tools/subversion and then scp
# it over to hc-subversion after commiting the change.
#
# scp map_btuser_to_email.pl root@subversion:/usr/local/bt-client
#

use strict;

my $user = $ARGV[0];
defined $user or die("Usage: map_user_to_email.pl username");
my $email = map_user_to_email($user);
print "$email\n";

sub map_user_to_email {
    my $username = shift;

        # This is a map of sun login id -> sun email address which is used
        # when people commit code to bug ids with no responsible engineer
        # set.  An RE must be set to do the status transitions we do.
        #
        # XXX need to add to this when people join!
        my %convert_person;
        $convert_person{"mgoff"} = "michael.goff\@sun.com";
        $convert_person{"sarahg"} = "sarah.gordon\@sun.com";
        $convert_person{"jw146271"} = "jeremy.werner\@sun.com";
        $convert_person{"ar146282"} = "amber.roy\@sun.com";
        $convert_person{"sarnoud"} = "sacha.arnoud\@sun.com";
        $convert_person{"twc"} = "tim.curry\@sun.com";
        $convert_person{"sm152240"} = "shamim.mohamed\@sun.com";
        $convert_person{"rw151951"} = "robert.wygand\@sun.com";
        $convert_person{"jd151549"} = "joshua.dobies\@sun.com";
        $convert_person{"wr152514"} = "wilson.ross\@sun.com";
        $convert_person{"as147040"} = "andy.siu\@sun.com";
        $convert_person{"mn145548"} = "mike.nugent\@sun.com";
        $convert_person{"dm155201"} = "daria.mehra\@sun.com";
        $convert_person{"jr152025"} = "joseph.russack\@sun.com";
        $convert_person{"stephb"} = "stephane.brossier\@sun.com";
        $convert_person{"ds158322"} = "david.sobeck\@sun.com";
        $convert_person{"mschein"} = "mark.schein\@sun.com";
        $convert_person{"bpdavis"} = "bp.davis\@sun.com";
        $convert_person{"ta144659"} = "tatyana.alexeyev\@sfbay";
        $convert_person{"fb160468"} = "fred.blau\@sun.com";
        $convert_person{"elambert"} = "eric.lambert\@sun.com";
        $convert_person{"rg162296"} = "rodney.gomes\@sun.com";
        $convert_person{"pb193982"} = "peter.buckingham\@sun.com";
        $convert_person{"sm193776"} = "sameer.mehta\@sun.com";
        $convert_person{"pc198268"} = "peter.cudhea\@sun.com";
        $convert_person{"gp198228"} = "girish.pahlya\@sun.com";
        $convert_person{"sp198635"} = "scott.paulinski\@sun.com";
        $convert_person{"kriewald"} = "larry.kriewald\@sun.com";
        $convert_person{"td151192"} = "tad.dolphay\@sun.com";
        $convert_person{"ws148587"} = "william.schoofs\@sun.com";
        $convert_person{"jdunn"} = "jonathan.dunn\@sun.com";
        $convert_person{"lf70604"} = "ludovic.fernandez\@sun.com";
        $convert_person{"ad120940"} = "andrei.dancus\@sun.com";
        $convert_person{"dp127224"} = "douglas.parent\@sun.com";
        $convert_person{"bberndt"} = "becky.berndt\@sun.com";
        $convert_person{"tt107269"} = "tina.tyan\@sun.com";
        $convert_person{"sc1444"} = "sam.cramer\@sun.com";
        $convert_person{"ks202890"} = "satish.visvanathan\@sun.com";
        $convert_person{"jp203679"} = "joseph.pallepamu\@sun.com";
        $convert_person{"jb127219"} = "juliana.arnolds\@sun.com";
        $convert_person{"jk142663"} = "jolly.kundu\@sun.com";
        $convert_person{"ronaldso"} = "ronald.so\@sun.com";
        $convert_person{"hs154345"} = "hairong.sun\@sun.com";
        $convert_person{"erikr"} = "erik.rockstrom\@sun.com";
        $convert_person{"ct35888"} = "charles.ting\@sun.com";
        $convert_person{"ktripp"} = "kristina.tripp\@sun.com";
        $convert_person{"lm156032"} = "mallika.mulakaluri\@sun.com";
        $convert_person{"cp129962"} = "Clark.Piepho\@sun.com";
        $convert_person{"rasmusc"} = "Christian.Rasmussen\@sun.com";
        $convert_person{"am143972"} = "Allan.Matthews\@sun.com";
        $convert_person{"md200329"} = "matthew.dragani\@sun.com";
        $convert_person{"ld154710"} = "lenworth.daley\@sun.com";
        $convert_person{"mb156541"} = "mark.r.butler\@sun.com";
        $convert_person{"dr129993"} = "diane.ramsey\@sun.com";
        $convert_person{"ad210840"} = "ashok.damodaran\@sun.com";
        $convert_person{"mc210319"} = "matthew.coneybeare\@sun.com";
	$convert_person{"iamsds"} = "stephen.salbato\@sun.com";
	$convert_person{"as137830"} = "anupama.subramanya\@sun.com";
	$convert_person{"pm141145"} = "paul.monday\@sun.com";
	$convert_person{"jdunham"} = "jim.dunham\@sun.com";
	$convert_person{"jm21264"} = "james.moore\@sun.com";

        my $email_addr = $convert_person{$username};
        if (!defined $email_addr || $email_addr eq "") {
                $email_addr = "";
        }

        return $email_addr;
}
