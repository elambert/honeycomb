#!/usr/bin/bash

#
# $Id: run_sucker.sh 10855 2007-05-19 02:54:08Z bberndt $
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

# TODO Have to do run_sucker sequentially until we have a way of checking done

DIR=`cd \`dirname $0\`; pwd`


printUsage() {
    echo "$0 [-s skip nodes ] nodeCount outputDirectory"
    echo "  eg: $0 -s \"1 2\" 16 /var/adm/sucker"
}



while getopts "s:h" opt; do
    case $opt in
        s ) 

            SKIPFIRST=`echo "$OPTARG" | awk '{print $1}'`
            SKIPSECOND=`echo "$OPTARG" | awk '{print $2}'`
            
            echo -n "Will skip node $SKIPFIRST"
            if [ $SKIPSECOND ] ; then
                echo -n " and $SKIPSECOND"
            fi
            echo "."
            ;;
        h ) printUsage
            exit 0
            ;;
        \? )  printUsage

        exit 1
    esac
done
shift $(($OPTIND -1 ))


run_sucker() {
    rcp $DIR/sucker $1:
    echo "Starting scrubing on $1. removing $2"
    rsh $1 rm -rf $2
    rsh $1 mkdir -p $2
    rsh $1 ./sucker -d $2 -q
#    echo sucker run complete - copying from $1:$2/$1.db
#    echo rcp $1:$2/$1.db .
    rcp $1:$2/$1.db $2
    rsh $1 rm $2/$1.db sucker
#    $DIR/dump -f $1.db > /dev/null
    echo "Node $1 done"
}

if [ ! $1 ]
then
    echo "Enter the number of nodes as argument"
    exit 1
fi

if [ ! $2 ]
then
    echo "Provide the directory in which to put output"
    exit 2
fi

OUTDIR=$2

echo "Running in a $1 nodes configuration output to $OUTDIR"

rm -rf $OUTDIR
mkdir -p $OUTDIR
cd $OUTDIR

i=0

while [ $i -lt $1 ]
do  
  if [ "$(($i+1))" = "$SKIPFIRST" ] || [ "$(($i+1))" = "$SKIPSECOND" ] ; then
      echo "skipping node $(($i+101))"
  else       
      node="hcb$(($i+101))"            
      run_sucker $node $OUTDIR &
  fi
  i=$(($i+1))
done

echo "run $DIR/analyze -d $OUTDIR -n $1 | tee OUTPUT"
