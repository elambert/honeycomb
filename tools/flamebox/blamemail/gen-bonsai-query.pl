#!/usr/bin/perl

#
# Copyright 2004 Riverbed Technology, Inc.
# $Id: gen-bonsai-query.pl,v 1.5 2004/11/25 00:32:58 timlee Exp $
# This script is intended to be used in flamebox scripts whenever generating
# a URL to the checkins between builds is desired.
#
use POSIX qw(strftime);

$build_date_dir = "/var/tmp/build-dates";

if (defined($ARGV[0])) {
    $build_name = $ARGV[0];
} elsif (-f "$build_date_dir/current-build-name" ) {
    $build_name = `/usr/bin/tail -1 $build_date_dir/current-build-name`;
    chomp $build_name;
} else {
    print "No build specified, and no $build_date_dir/current-build-name .\n";
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
	print 'LINK <a href="';
	print "http://internal.nbttech.com/bonsai/cvsquery.cgi?treeid=default&module=all&branch=$branch&branchtype=match&dir=&file=&filetype=match&who=&whotype=match&sortby=Date&date=explicit&mindate=${previous}&maxdate=${current}&cvsroot=%2Frepository";
	$previous_localtime = strftime ("%a %b %e %H:%M:%S %Z %Y", localtime($previous));
	print '"' . ">Changes since the previous build $build_name dated $previous_localtime in $branch branch</a>\n";
    }
}

exit 0;
