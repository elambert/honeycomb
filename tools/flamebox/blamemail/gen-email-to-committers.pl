#!/usr/bin/perl

#
# Copyright 2004 Riverbed Technology, Inc.
# $Id: gen-email-to-committers.pl,v 1.2 2004/11/25 00:32:58 timlee Exp $
# This script is intended to be used in flamebox scripts whenever generating
# a list of email addresses for the committers who checked in since the last
# previous build.
#
$build_date_dir = "/var/tmp/build-dates";

if (defined($ARGV[0])) {
    $build_name = $ARGV[0];
} elsif (-f "$build_date_dir/current-build-name" ) {
    $build_name = `/usr/bin/tail -1 $build_date_dir/current-build-name`;
    chomp $build_name;
} else {
    print STDERR "No build specified, and no $build_date_dir/current-build-name .\n";
    exit 1;
}

if (defined($ARGV[1])) {
    $branch = $ARGV[1];
} elsif (-f "$build_date_dir/current-branch") {
    $branch = `/usr/bin/tail -1 $build_date_dir/current-branch`;
    chomp $branch;
} else {
    $branch = "HEAD";
}

if ( ! -f $build_date_dir ) {
    system ("/bin/mkdir -p $build_date_dir");
}

if ( -f "$build_date_dir/${build_name}-current" && -f "$build_date_dir/${build_name}-previous" ) {
    $current = `/usr/bin/tail -1 $build_date_dir/${build_name}-current`;
    chomp $current;
    $previous = `/usr/bin/tail -1 $build_date_dir/${build_name}-previous`;
    chomp $previous;
    if ( $current ne "" && $previous ne "" ) {
	$email_addresses = `/usr/bin/wget -q -O - 'http://internal.nbttech.com/bonsai/cvsquery.cgi?treeid=default&module=all&branch=$branch&branchtype=match&dir=&file=&filetype=match&who=&whotype=match&sortby=Date&date=explicit&mindate=${previous}&maxdate=${current}&cvsroot=%2Frepository' | /bin/grep -E 'email=[a-z]' | /bin/sed -e 's,.*/registry/who.cgi?email=,,' -e "s,' onClick.*,," | /bin/sort | /usr/bin/uniq`;
	chomp $email_addresses;
	$email_addresses =~ s/\n/,/g;
	print $email_addresses . "\n";
    }
}

exit 0;
