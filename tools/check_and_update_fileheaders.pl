#!/usr/bin/perl -w
#
# $Id: check_and_update_fileheaders.pl 10853 2007-05-19 02:50:20Z bberndt $
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

use strict;

#
# This script crawls through the source tree and tries to update
# copyright when it thinks it is the right thing to do, otherwise
# it logs about what it finds.  
# 
# NOTE you currently must use full path to root of repository and
# omit the trailing /.
#
# Sample invocation:
#
# /export/home/sarahg/svn/honeycomb-cp2/tools/check_and_update_fileheaders.pl /export/home/sarahg/svn/honeycomb-cp2
#

my @subdirnames_to_omit = (".",
                           "..",
                           ".svn",

                            # XXX bonnie is causing regexp issues...
                           "bonnie++");

# XXX problematic if subset of repo is specified...
my @fullpathdirs_to_omit = (
                            # not ours
                            "external",
                            "emulator/external",
                            "misc/examples/src/gnu",
                            "tools/ethereal-0.10.12",
                            "client_c/curl-7.14.0",
                            "hctest/src/native/util/my_getopt-1.4",
                            "hctest/src/webdav/ofoto/c/Linux/examples/ofoto/expat-2.0.0",
                            "hctest/src/webdav/ofoto/c/Linux/examples/ofoto/neon-0.25.5",
                            "hctest/src/webdav/ofoto/c/sol_x86/examples/ofoto/neon-0.25.5",
                            "hctest/src/webdav/ofoto/c/sol_sparc/examples/ofoto/neon-0.25.5",
                            "hctest/src/webdav/ofoto/java/slide-lib",
                            "server/src/java/com/sun/honeycomb/admin/cli/md5",
                            "server/src/native/sucker/bdb-4.4.20",
                            "platform/switch/switch/config/overlay/etc/rcZ.d/surviving_partner",

                            # maybe not ours
                            "server/src/native/admin/editline",

                            # misc
                            "platform/solaris/src/dvd/patchsrc",

                            # not text files
                            "server/src/java/com/sun/honeycomb/cm/documentation",
                            "server/src/native/cm/docs/design",

                            # not shipping
                            "test",
                            "demos",
                                # "demos/shoutcast-server/shoutcast",
                                # "demos/MusicDrop/3rdparty",
                                # "demos/photo-client/src/dk",
                            "hctest",
                            # XXX is this true?
                            "platform/linux",
                            "server/src/config/clusters",
                            "sdk/test",
                            "misc/simulations",
                            "unit_tests",
                            "tools",
                            );

# skips copyright and keywords check
# MUST DO THESE MANUALLY, if needed
my @fullpathfiles_to_omit = ( # XXX what is sql comment char?
                             "server/src/native/cm/src/common/mysql_config.sql",
                             "test/lib/filestore_mysql.sql",


                             # not our files
                             "server/src/native/cm/ltmain.sh",
                             "server/src/native/switch/libznl2.h",
                             "server/src/native/zrule2/libznl2.h",
                             "server/src/native/zrule2/libsaneznl2.h",
                             "platform/switch/switch/test/testztmd.c",
                             "platform/switch/switch/config/overlay/etc/rcZ.d/S70Surviving_partner",
                             "platform/switch/switch/config/overlay/etc/honeycomb/vrrpd.script.in",
                             "platform/switch/switch/config/overlay/etc/honeycomb/zsp.conf.in",
                             "platform/switch/switch/config/overlay/etc/honeycomb/vrrpd.conf.in",
                             "platform/switch/switch/config/overlay/etc/honeycomb/S70Surviving_partner.in",
                             "platform/switch/switch/config/overlay/etc/honeycomb/zlmd.script.in",
                             "platform/switch/switch/config/overlay/etc/ssh/sshd_config",
                             "platform/switch/switch-silo/test/testztmd.c",
                             "platform/switch/switch-silo/config/overlay/etc/honeycomb/vrrpd.script.in",
                             "platform/switch/switch-silo/config/overlay/etc/honeycomb/zsp.conf.in",
                             "platform/switch/switch-silo/config/overlay/etc/honeycomb/vrrpd.conf.in",
                             "platform/switch/switch-silo/config/overlay/etc/honeycomb/S70Surviving_partner.in",
                             "platform/switch/switch-silo/config/overlay/etc/honeycomb/zlmd.script.in",
                             "platform/switch/switch-silo/config/overlay/etc/ssh/sshd_config",
                             "platform/solaris/src/sp/jumpstart/bin/ersh",
                             "server/src/native/power-control/asf.h",
                             "AlertViewer/nbproject/private/private.xml",
                             "AlertViewer/nbproject/project.xml",
                             "AlertViewer/nbproject/genfiles.properties",
                             "AlertViewer/nbproject/build-impl.xml",
                             "filesystem/src/native/fscache/db.h",

                             # maybe not our files
                             "admingui/src/com/sun/honeycomb/admingui/present/uicomponents/ToolbarButton.java",
                             "tools/buildinstalltest/hcr_bringover",
                             "client_c/tools/mkmerge.c",

                             # not text
                             "sdk/README.lyx",

                             # not shipped / empty
                             "server/perf/tools/node_config.xml.diff",
                             "server/perf/tools/node_config.xml.optimizeit.patch",

                             # misc 
                             # XXX could improve by writing smarter regexp
                             # for certain file types
                             "server/perf/specfiles/honeycomb.specfile",
                             "server/perf/examples/simple.specfile",
                             "test/src/platform/NODES",
                             "platform/switch/switch/config/overlay/etc/honeycomb/example.conf",
                             "platform/switch/switch/config/overlay/etc/ssh/ssh_host_rsa_key.pub",
                             "platform/switch/switch/config/overlay/etc/ssh/ssh_host_rsa_key",
                             "platform/switch/switch/config/overlay/etc/ssh/ssh_host_dsa_key",
                             "platform/switch/switch/config/overlay/etc/ssh/ssh_host_dsa_key.pub",
                             "platform/switch/switch/config/overlay/etc/passwd",
                             "platform/switch/switch/config/overlay/etc/monitrc",
                             "platform/switch/switch/config/overlay/etc/pam.d/sshd",
                             "platform/switch/switch-silo/config/overlay/.zsync/version",
                             "platform/switch/switch-silo/config/overlay/etc/honeycomb/example.conf",
                             "platform/switch/switch-silo/config/overlay/etc/passwd",
                             "platform/switch/switch-silo/config/overlay/etc/monitrc",
                             "platform/solaris/src/dvd-platformtest/jumpstart/rules.ok",
                             "platform/solaris/src/dvd-platformtest/jumpstart/profile",
                             "platform/solaris/src/sp/jumpstart/profiles/node/rules.ok",
                             "platform/solaris/src/sp/jumpstart/profiles/node/sysidcfg",
                             "platform/solaris/src/js/export/jumpstart/profiles/desktop-sparc/rules.ok",
                             "platform/solaris/src/js/export/jumpstart/profiles/desktop-sparc/sysidcfg",
                             "platform/solaris/src/js/export/jumpstart/profiles/desktop/rules.ok",
                             "platform/solaris/src/js/export/jumpstart/profiles/desktop/sysidcfg",
                             "platform/solaris/src/dvd/tools/ssh/id_dsa.pub",
                             "platform/solaris/src/dvd/tools/ssh/id_dsa",
                             "platform/solaris/src/dvd/tools/ssh/authorized_keys",
                             "platform/solaris/src/dvd/tools/sysidcfg",
                             "platform/solaris/src/dvd/jumpstart/rules.ok",
                             "client_c/honeycomb/Win32/merge.rf",
                             "client_c/tools/profile.curl.template",
                             "client_c/tools/profile.honeycomb.template",
                             "client_c/tools/profile.test.template",
                             "emulator/src/derbyCache/MANIFEST.mf",
                             "md_clustra/src/md_cache/MANIFEST.MF",
                             "md_caches/bdb_system/MANIFEST.MF",
                             "md_caches/system_mysql/MANIFEST.MF",
                             "md_caches/sqlite_extended/MANIFEST.MF",
                             "md_caches/bdb_common/MANIFEST.MF",
                             "AlertViewer/Manifest.mf",
                             "build/doxygen.config",
                             "build/version",
                             "server/src/scripts/dimm.in",
                             "server/src/scripts/bios.in",
                             "server/src/scripts/cpu.in",
                             "server/src/scripts/mb.in",
                             "server/src/config/ipmi-pass.in",
                             "AlertViewer/nbproject/private/private.properties",
                             "whitebox/cluster/src/config/sniffer_behaviour.conf",
                             "whitebox/cluster/src/config/sniffer_behaviour.conf.sample1",
                             "whitebox/sp/src/config/config_defaults.properties",
                             "client/src/native/NEWS",
                             "client/src/native/COPYING",
                             "client/src/native/ChangeLog",
                             "client/src/native/AUTHORS",
                             "client/src/native/configure.ac",
                             "server/src/native/cm/src/CMM/lib/mapfile",
                             "hc_version",
                             "sdk/version",
                             "sdk/MANIFEST",
                             "sdk/src/java/Perf.mf",

                             # add other .bat files and windows files?
                             "emulator/src/bin/convert_to_hadbquery.bat",
                             );

my $basename;

my $print_debug = 0;
#my $print_debug = 1;
sub debug {
    if ($print_debug) {
        print @_;
    }
}

# return 1 if file should be ignored
sub ignore_file {
    my ($file) = @_;
    #print "Checking if we should ignore file $file in list of @fullpathfiles_to_omit\n";
    foreach my $f (@fullpathfiles_to_omit) {
        my $fullname = $basename.$f;
        if ($fullname =~ $file) {
            debug "Ignore file $file\n";
            return 1;
        }
        #} else {
        #    print "$fullname not like $file\n";
        #}
    }

    return 0;
}
 
# return 1 if dir should be ignored
sub ignore_dir {
    my ($dir) = @_;
    #print "Checking if we should ignore dir $dir\n";
    foreach my $d (@subdirnames_to_omit) {
        if ($d eq $dir) {
        #if ($d eq $dir || $basename.$d =~ $dir) {
            debug "Ignore subdir $dir\n";
            return 1;
        }
    }
    foreach my $d (@fullpathdirs_to_omit) {
        if ($basename.$d eq $dir) {
            debug "Ignore dir $dir\n";
            return 1;
        }
    }
    # print "Don't ignore dir $dir\n";
    return 0;
}

# XXX future also examine internal file for proper ID string
# incorporate in check_copyright since we read the file any way and it
# should be in the header we read...
sub check_keywords {
    my ($fullpath) = @_;
    my $keywords = `svn propget svn:keywords $fullpath`;
    chomp $keywords;
    if ($keywords eq "") {
        print "$fullpath : no keywords set\n"; 
    } elsif ($keywords ne "Id") {
        print "$fullpath : wrong keywords set: $keywords\n";
    } else {
        print "$fullpath : keywords ok\n";
    }
}
 
sub check_copyright {
    my ($fullpath) = @_;
    # expect this info as the first few lines of the file,
    # possibly with various comment chars as first char in line
    my $copyright_string = "Copyright";
    my $copyright_string_with_date = "Copyright 2006 Sun Microsystems, Inc.  All rights reserved.";
    my $sun = "Sun Microsystems, Inc.";
    my $license_string = "license";
    my $license_string_full = "Use is subject to license terms.";

    # we copy the file to a backup and write to it to
    # preserve the permissions (else build breaks when we lose
    # exec perms)
    my $fullpathorig = "$fullpath.origfile";
    `cp -p $fullpath $fullpathorig`;
    if (!-f $fullpathorig) {
        die "can't cp file $fullpath $fullpathorig: $!";
    }
    #print "copied fullpath $fullpathorig";
    open FILE, $fullpath or die "can't open file $fullpath: $!";
    my $tmpfile = $fullpathorig;
    open(TMPFILE, ">$tmpfile") or die "can't open file $tmpfile: $!";
    my $lines = 0; # count lines
    my $copyonly = 0; # copy contents, don't analyze, set to 1 once we've
                      # found the copyright/license
    my $file_changed = 0; # set to 1 if file has changed

    while (!eof FILE) {
        my $line = <FILE>;
        $lines++;

        if ($copyonly) {
            print TMPFILE $line;
            next;
        }

        #chomp $line;

        my $check_next_line = 0;
        my $comment_char = "";

        #print "found line $line\n";

        # check for copyright
        if ($line =~ $copyright_string_with_date) {
            print "$fullpath has correct 2006 copyright string: $line";
            # must set this to get license string correct
            $comment_char = substr $line, 0, (index $line, $copyright_string);
            $check_next_line = 1;
            print TMPFILE $line;
            $check_next_line = 1;
        } elsif ($line =~ $copyright_string) {
            print "$fullpath has OLD or INCORRECT COPYRIGHT string: $line";

            # check if this looks like a Sun copyright or not
            if ($line =~ $sun) {
               # update this line with our copyright.
               # we must be careful to use the correct comment character!
               $comment_char = substr $line, 0, (index $line, $copyright_string);
               my $newline = $comment_char.$copyright_string_with_date."\n";

               my $tmpfile = $fullpath."tmp";
               print "will REPLACE old copyright line with $newline to file $tmpfile\n";
               print TMPFILE $newline;
               $check_next_line = 1;
               $file_changed = 1;
            } else {
                # some files have ATT copyright and later Sun's
                # copyright...
                print "$fullpath COPYRIGHT DOESN'T LOOK LIKE SUN'S, keep looking: $line";
                print TMPFILE "$line";
            }
        } else {
            print TMPFILE "$line";
        }

        # license should follow copyright line
        if ($check_next_line && !eof FILE) {
            $line = <FILE>;
            $lines++;
            #chomp $line;
            if ($line =~ $license_string_full) {
                print "$fullpath using existing correct license\n";
                print TMPFILE "$line";
            } elsif ($line =~ $license_string) {
                print "$fullpath has INCOMPLETE or INCORRECT license string, will replace old: $line";
                my $newline = $comment_char.$license_string_full."\n";
                print TMPFILE "$newline";
                $file_changed = 1;
            } else {
                print "$fullpath NO LICENSE string...adding one\n";
                print TMPFILE "$comment_char$license_string_full\n";
                print TMPFILE "$line";
                $file_changed = 1;
            }

            # we've tried to process/fix this file, now we just
            # copy the rest of the lines
            $copyonly = 1;
        }
    }

    close FILE;
    close TMPFILE;

    if (!$copyonly) {
        print "$fullpath has NO or INCORRECT COPYRIGHT and maybe no license\n";
        # scrap the tmp file...
        unlink $fullpathorig;
    } elsif (!$file_changed) {
        print "$fullpath looks good, no changes needed\n";
        # scrap the tmp file...
        unlink $fullpathorig;
    } else {
        # commit to using the new file
        rename $fullpathorig, $fullpath; 
        print `svn diff $fullpath`;
        #sleep 2;
    }
}

# given a directory, process files in it and recurse on subdirs
sub process_dir {
    my $dir = shift(@_);
    print "---> Processing $dir\n";
    opendir THISDIR, $dir or die "failed to open dir $dir: $!";
    my @allfiles = readdir THISDIR;
    closedir THISDIR;
    debug "Files in dir $dir are @allfiles\n";
    foreach my $f (@allfiles) {

        # check for obvious dirs we always skip
        next if ignore_dir($f);

        # check full path of other dirs
        my $fullpath = "$dir/$f";

        if (-d $fullpath) {
            debug "$fullpath is a directory\n";
            if (ignore_dir($fullpath)) {
                print "Ignoring dir $fullpath\n";
            } else {
                debug "--> Recursing to process $fullpath\n";
                process_dir($fullpath);
            }
        } elsif (-f $fullpath) {
            debug "Found file $fullpath\n";

            if (-l $fullpath) {
                print "Skipping symlink $fullpath\n";
                next;
            }

            if (ignore_file($fullpath)) {
                print "Ignoring file $fullpath\n";
                next;
            }
            my $mimetype = `svn propget svn:mime-type $fullpath`;
            chomp $mimetype;
            if ($mimetype eq "") {
                # heuristic for text files
                debug "$fullpath has no mime-type\n";
                # future...
                # check_keywords($fullpath);
                check_copyright($fullpath);
           } else {
                # we skip binary files
                print "$fullpath has mime-type $mimetype ... skipping\n";
                if ($mimetype ne "application/octet-stream" && 
                    $mimetype ne "image/jpeg") {
                    die "$fullpath : unexpected mimetype $mimetype\n";
                    #print "$fullpath : unexpected mimetype $mimetype\n";
                }
            }
        } else {
            # shouldn't hit this
            die "entry of unknown type: $fullpath\n";
        }
    }
}

sub usage {
    print "usage: $0 /full/path/to/start/at\n";
    exit 1;
}

# verify args
my ($STARTINGDIR) = @ARGV;
if (!defined $STARTINGDIR) {
    usage();
}

# XXX also check for trailing / in path...would mess up substring search
if ((substr $STARTINGDIR, 0, 1) ne "/") { 
    print "must specify full path starting with /\n";
    usage();
}

# XXX currently not supported to do partial tree
# XXX Arg1 is expected to not have trailing slash...
$basename = $STARTINGDIR."/";
process_dir $STARTINGDIR;
