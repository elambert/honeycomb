$Id: README.txt 3324 2005-01-16 19:58:43Z ar146282 $

Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.

To run unit tests:
    $ cd svn/build/unit_tests/dist
    $ ./run_tests.sh

To run layout simulator:
    $ ./run.sh PrintSimStats

Other utility programs:
    $ ./run.sh PrintLayout
    $ ./run.sh PrintOneMap

To run PrintLayout from cluster node:
    $HON hcb101 ~ $ java -cp /opt/honeycomb/lib/honeycomb-utests.jar:/opt/honeycomb/lib/honeycomb-server.jar -Djava.library.path=/opt/honeycomb/lib com.sun.honeycomb.layout.PrintLayout


