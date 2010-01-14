#!/bin/bash
#
# $Id$
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.
#

# sets up the APC script
echo "Setting up the APC Automated Script..."

if [ -e /opt/test/bin/apc/JAVAabt.tar ]; then
    rm -f /opt/test/bin/apc/JAVAabt.tar
fi

if [ -e /opt/test/bin/apc/JAVAjist.tar ]; then 
    rm -f /opt/test/bin/apc/JAVAjist.tar
fi

if [ -e /JAVAjist.tar ]; then 
    rm -f /JAVAjist.tar
fi

if [ -e /JAVAabt.tar ]; then 
    rm -f /JAVAabt.tar
fi

gzip -d -f -c /opt/test/bin/apc/JAVAabt.tar.gz > /mnt/test/JAVAabt.tar
gzip -d -f -c /opt/test/bin/apc/JAVAjist.tar.gz > /mnt/test/JAVAjist.tar
tar -xvf /mnt/test/JAVAabt.tar -C /
tar -xvf /mnt/test/JAVAjist.tar -C /
chmod +x /etc/init.d/abt /opt/abt/bin/abt
/etc/init.d/abt start

echo "Done setting up the APC Automated Script!"
echo ""
echo "Run by using:"
echo ""
echo "    /opt/abt/bin/abt Power<ACTION>PduPort on=<USERNAME>:<PASSWORD>\@<IP>:<PORT> logDir=/mnt/test"
echo ""
echo "    ACTION:   'On' or 'Off'"
echo "    USERNAME: Username of the APC"
echo "    PASSWORD: Password of the APC"
echo "    IP:       IP of the APC"
echo "    PORT:     Power port on the APC to apply the action to"
echo ""