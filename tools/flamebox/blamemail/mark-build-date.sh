#!/bin/sh

#
# Copyright 2004 Riverbed Technology, Inc.
# $Id: mark-build-date.sh,v 1.3 2004/11/25 00:32:59 timlee Exp $
# This script is intended to be used in build-* flamebox scripts to mark
# build dates.  Use it in tasks.pl immediately after checking out a tree.
#

if [ "x$1" = x ]; then
    echo "usage: $0 build-name [branch]"
    exit 1
fi

build_name=$1
if [ "x$2" != x ]; then
    branch=$2
else
    branch=HEAD
fi

build_date_dir=/var/tmp/build-dates
if [ ! -f $build_date_dir ]; then
    /bin/mkdir -p $build_date_dir
fi

if [ -f $build_date_dir/${build_name}-current ]; then
    /bin/mv $build_date_dir/${build_name}-current $build_date_dir/${build_name}-previous
fi

/bin/date +%s > $build_date_dir/${build_name}-current
echo $build_name > $build_date_dir/current-build-name
echo $branch > $build_date_dir/current-branch

exit 0
