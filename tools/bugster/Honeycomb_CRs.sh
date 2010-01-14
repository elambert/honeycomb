#!/bin/sh
#
# $Id$
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
# Program looks up all open honeycomb 'Defects' and to create a web page
#	(does not report RFEs)
# It does a total and what's been submitted in last 7 days.
#
#
# Currently, output is at:
# http://futureworld.central.sun.com/HC/ALL_Honeycomb_Defects.html
#
#
# Author:  Mark Schein
Last_updated="06/16/05"
Version="1.1"
# pragma ident   "@(#)Honeycomb_CRs.sh 1.1     06/16/05 SMI"



# set path
PATH=$PATH:/usr/bin:/bin:/usr/dist/exe;export PATH

# get date - used for calendar file format entry
# setting vars y, m, d  because SCCS will expand  or %m% 
y="%Y"
m="%m"
d="%d"
DATE=`date "+${y}${m}${d}"`

# cd to the correct directory (ie the dirname)
# so we can write the files in the project's Status directory
cd `dirname $0`

# create the file tnsnames.ora to be used by the sqlplus cmd
echo "SBLRPT =" > tnsnames.ora
echo " 	  (DESCRIPTION = " >> tnsnames.ora
echo " 	    (ADDRESS_LIST = " >> tnsnames.ora
echo "  	     (ADDRESS = (PROTOCOL = TCP)(HOST = swsblrpt.central.sun.com)(PORT = 1525)) " >> tnsnames.ora
echo " 	    ) " >> tnsnames.ora
echo "    	    (CONNECT_DATA = " >> tnsnames.ora
echo "    	      (SERVICE_NAME = sblrpt) " >> tnsnames.ora
echo "   	    ) " >> tnsnames.ora
echo "  	  ) " >> tnsnames.ora

# now set the TNS_ADMIN var
TNS_ADMIN=.;export TNS_ADMIN

OPEN="SQL_FOR_ALL_Honeycomb_OPEN_DEFECTS"
# create SQL file ./${OPEN}
# this poll of the SQL db gets current Honeycomb open defects 
cat <<END > ./${OPEN}

set linesize 190
set pagesize 30000
set head on
set tab off
set feedback off
column "Bug Id" format 9999999
column "Product" format a40 tru
column "Category" format a30 tru
column "P" format a1 tru
column "Synopsis" format a80 tru
column "Submit Dat" format a10 tru
column "State" format a10 tru

select
a.cr_number "Bug Id",
a.product "Product",
a.category "Category",
substr(a.priority, 0, 1) "P",
a.synopsis "Synopsis",
NVL(TO_CHAR(TO_DATE(a.date_submitted, 'DD-MON-RR'), 'MM/DD/YYYY'), 'NULL') "Submit Dat",
SUBSTR(a.status,INSTR(a.status,'-')+1,15) "State"
from
        report_maker.change_requests a,
        report_maker.pipe b
where
        b.cr_row_id = a.row_id
        and
	a.product like 'honeycomb' 
	and
	a.status not LIKE '8-F%'
	and
	a.status not LIKE '9-F%'
	and
	a.status not LIKE '10-F%'
	and
	a.status not LIKE '11-C%'
order by
	a.priority;
END

# Temp file name for sql poll
OPEN_1WK="SQL_FOR_ALL_Honeycomb_OPEN_DEFECTS_1wk_old"
# create SQL file ./${OPEN_1WK}
# this poll of the SQL db gets Honeycomb open defects that were submitted
# in the last 7 days
cat <<END > ./${OPEN_1WK}

set linesize 190
set pagesize 30000
set head on
set tab off
set feedback off
column "Bug Id" format 9999999
column "Product" format a40 tru
column "Category" format a30 tru
column "P" format a1 tru
column "Synopsis" format a80 tru
column "Submit Dat" format a10 tru
column "State" format a10 tru

select
a.cr_number "Bug Id",
a.product "Product",
a.category "Category",
substr(a.priority, 0, 1) "P",
a.synopsis "Synopsis",
NVL(TO_CHAR(TO_DATE(a.date_submitted, 'DD-MON-RR'), 'MM/DD/YYYY'), 'NULL') "Submit Dat",
SUBSTR(a.status,INSTR(a.status,'-')+1,15) "State"
from
        report_maker.change_requests a,
        report_maker.pipe b
where
        b.cr_row_id = a.row_id
        and
	a.product like 'honeycomb' 
	and
	a.status not LIKE '8-F%'
	and
	a.status not LIKE '9-F%'
	and
	a.status not LIKE '10-F%'
	and
	a.status not LIKE '11-C%'
	and
	a.date_submitted >= trunc(sysdate) -7
order by
        a.priority;
END

# temp files for bugster db data poll
TEMP1="bugs_temp.$$"
TEMP2="bugs_temp_1wk.$$"

# gather data to be used to update the project page .p1, .p2, .p3, and .p45
# files
sqlplus LOGINNAME/LOGINPASSWD@sblrpt < ./${OPEN} | egrep -v "^$|^Copyright|^Connected|^Oracle|^With|^JServer|^SQL|^Product|^-------|^  Bug|^Submit" > ${TEMP1}
sqlplus LOGINNAME/LOGINPASSWD@sblrpt < ./${OPEN_1WK} | egrep -v "^$|^Copyright|^Connected|^Oracle|^With|^JServer|^SQL|^Product|^-------|^  Bug|^Submit" > ${TEMP2}



# file name for html page
HTMLPAGE="ALL_Honeycomb_Defects.html"
# cleanup  {some day need dates for weekly reports}
rm -f ${HTMLPAGE}


# creating web page title & body
echo "<title>Bugster Poll of Honeycomb Open Defects as of `date`</title>" > ${HTMLPAGE}
echo "<body bgcolor=white>" >> ${HTMLPAGE}
echo "<center><H1>Bugster Poll of Honeycomb Open Defects</H1><br><font size=-1>(Bugster DB Polled As Of `date`)</font></H1></center>" >> ${HTMLPAGE}

echo "<p><center><i><font size=-1 color=blue>Please note the number links, in the table below, point to live data on bugster, so it is possible the numbers will vary if CRs are opened or closed since the data polled.</font></i></center>" >> ${HTMLPAGE}

echo "<center><table border=1 bordercolorlight=\"#002173\" bordercolordark=\"#002173\" bordercolor=\"#002173\" style=\"border-collapse: collapse; width: 685px;\">" >> ${HTMLPAGE}
echo "<tr>" >> ${HTMLPAGE}
echo "<td bgcolor=\"#6699ff\"><b><font color=white>Honeycomb Product</font></b></td>" >> ${HTMLPAGE}
echo "<td bgcolor=\"#6699ff\"><b><font color=white><center>Total<br>Open<br>CRs</center></font></b></td>" >> ${HTMLPAGE}
echo "<td bgcolor=\"#6699ff\"><b><font color=white><center>Total<br>P1</center></font></b></td>" >> ${HTMLPAGE}
echo "<td bgcolor=\"#6699ff\"><b><font color=white><center>Total<br>P2</center></font></b></td>" >> ${HTMLPAGE}
echo "<td bgcolor=\"#6699ff\"><b><font color=white><center>Total<br>P3</center></font></b></td>" >> ${HTMLPAGE}
echo "<td bgcolor=\"#6699ff\"><b><font color=white><center>Total<br>P4</center></font></b></td>" >> ${HTMLPAGE}
echo "<td bgcolor=\"#6699ff\"><b><font color=white><center>Total<br>P5</center></font></b></td>" >> ${HTMLPAGE}
echo "<td bgcolor=\"#e6e6ff\"><b><font color=black><center>New<br>CRs<br>in the<br>last<br>7 days<br>P1</center></font></b></td>" >> ${HTMLPAGE}
echo "<td bgcolor=\"#e6e6ff\"><b><font color=black><center>New<br>CRs<br>in the<br>last<br>7 days<br>P2</center></font></b></td>" >> ${HTMLPAGE}
echo "<td bgcolor=\"#e6e6ff\"><b><font color=black><center>New<br>CRs<br>in the<br>last<br>7 days<br>P3</center></font></b></td>" >> ${HTMLPAGE}
echo "<td bgcolor=\"#e6e6ff\"><b><font color=black><center>New<br>CRs<br>in the<br>last<br>7 days<br>P4</center></font></b></td>" >> ${HTMLPAGE}
echo "<td bgcolor=\"#e6e6ff\"><b><font color=black><center>New<br>CRs<br>in the<br>last<br>7 days<br>P5</center></font></b></td>" >> ${HTMLPAGE}
echo "</tr>" >> ${HTMLPAGE}


# Drawing fields for Honeycomb Products Defects  1 week old (only looks up Honeycomb 
# 'Defect's) change 'area=Defect' to 'area=ALL' to get RFEs too
#
# The grep's and variable 'P' are used to look for all, p1-p5, 
# 7 days old p1-p5 bugs from the SQL polls of the bugster DB.
# The grep | wc -l gives a count and then I create an html link by
# using bugster EzReport cgi script to and the proper parameter to give
# me the data I require when clicking the link.

# note 'for' loop in case you want to do a number of Bugster CR products.
for i in honeycomb; do

echo "<tr>"
echo "<td bgcolor="#99ccff">"
echo "<a href=\"http://swsblweb1.central.sun.com:8080/EzReport/get_btrpt.cgi?&rtitle=${i}+Open+Defect+Report&product=${i}&p1=on&p2=on&p3=on&p4=on&p5=on&area=Defect&status_range=none&status=1-Dispatched&status=2-Incomplete&status=3-Accepted&status=4-Defer&status=5-Cause+Known&status=6-Fix+Understood&status=7-Fix+in+Progress&time_zone=GMT&records_limit=1000&fields=cr_number&fields=priority&fields=area&fields=product&fields=category&fields=sub_category&fields=release&fields=status&fields=date_submitted&fields=responsible_engineer&fields=submitted_by&fields=commit_to_fix_in_build&fields=synopsis&product_length=18&hook1_length=10&category_length=14&hook2_length=10&sub_category_length=12&hook3_length=10&release_length=10&hook4_length=10&build_length=10&hook5_length=10&synopsis_length=100&hook6_length=10&order1=priority&order1direction=asc&order2=category&order2direction=asc&order3=sub_category&order3direction=asc&order4=release&order4direction=desc&fmt_details=on&fmt_summary=on&fmt_chart=on&fmt_url=on&z_axis=non&y_axis=none&x_axis=none&custom_fmt_details=on&custom_fmt_summary=on>\">${i}</a>"
echo "</td>"


P=0;P=`grep " ${i}             " ${TEMP1} | wc -l | awk '{print $1}'`
echo "<td bgcolor="#99ccff">"
echo "<a href=\"http://swsblweb1.central.sun.com:8080/EzReport/get_btrpt.cgi?&rtitle=${i}+Open+Defect+Report&product=${i}&p1=on&p2=on&p3=on&p4=on&p5=on&area=Defect&status_range=none&status=1-Dispatched&status=2-Incomplete&status=3-Accepted&status=4-Defer&status=5-Cause+Known&status=6-Fix+Understood&status=7-Fix+in+Progress&time_zone=GMT&records_limit=1000&fields=cr_number&fields=priority&fields=area&fields=product&fields=category&fields=sub_category&fields=release&fields=status&fields=date_submitted&fields=responsible_engineer&fields=submitted_by&fields=commit_to_fix_in_build&fields=synopsis&product_length=18&hook1_length=10&category_length=14&hook2_length=10&sub_category_length=12&hook3_length=10&release_length=10&hook4_length=10&build_length=10&hook5_length=10&synopsis_length=100&hook6_length=10&order1=priority&order1direction=asc&order2=category&order2direction=asc&order3=sub_category&order3direction=asc&order4=release&order4direction=desc&fmt_details=on&fmt_summary=on&fmt_chart=on&fmt_url=on&z_axis=non&y_axis=none&x_axis=none&custom_fmt_details=on&custom_fmt_summary=on>\"><center>${P}</center></a>"
echo "</td>"


P=0;P=`grep " ${i}             " ${TEMP1} | grep "^................................................................................ 1 " | wc -l | awk '{print $1}'`
echo "<td bgcolor="#99ccff">"
echo "<a href=\"http://swsblweb1.central.sun.com:8080/EzReport/get_btrpt.cgi?&rtitle=${i}+Open+Defect+Report+For+Priorty+1+CRs&product=${i}&p1=on&area=Defect&status_range=none&status=1-Dispatched&status=2-Incomplete&status=3-Accepted&status=4-Defer&status=5-Cause+Known&status=6-Fix+Understood&status=7-Fix+in+Progress&time_zone=GMT&records_limit=1000&fields=cr_number&fields=priority&fields=area&fields=product&fields=category&fields=sub_category&fields=release&fields=status&fields=date_submitted&fields=responsible_engineer&fields=submitted_by&fields=commit_to_fix_in_build&fields=synopsis&product_length=18&hook1_length=10&category_length=14&hook2_length=10&sub_category_length=12&hook3_length=10&release_length=10&hook4_length=10&build_length=10&hook5_length=10&synopsis_length=100&hook6_length=10&order1=priority&order1direction=asc&order2=category&order2direction=asc&order3=sub_category&order3direction=asc&order4=release&order4direction=desc&fmt_details=on&fmt_summary=on&fmt_chart=on&fmt_url=on&z_axis=non&y_axis=none&x_axis=none&custom_fmt_details=on&custom_fmt_summary=on>\"><center>${P}</center></a>"
echo "</td>"

P=0;P=`grep " ${i}             " ${TEMP1} | grep "^................................................................................ 2 " | wc -l | awk '{print $1}'`
echo "<td bgcolor="#99ccff">"
echo "<a href=\"http://swsblweb1.central.sun.com:8080/EzReport/get_btrpt.cgi?&rtitle=$i+Open+Defect+Report+For+Priorty+2+CRs&product=${i}&p2=on&area=Defect&status_range=none&status=1-Dispatched&status=2-Incomplete&status=3-Accepted&status=4-Defer&status=5-Cause+Known&status=6-Fix+Understood&status=7-Fix+in+Progress&time_zone=GMT&records_limit=1000&fields=cr_number&fields=priority&fields=area&fields=product&fields=category&fields=sub_category&fields=release&fields=status&fields=date_submitted&fields=responsible_engineer&fields=submitted_by&fields=commit_to_fix_in_build&fields=synopsis&product_length=18&hook1_length=10&category_length=14&hook2_length=10&sub_category_length=12&hook3_length=10&release_length=10&hook4_length=10&build_length=10&hook5_length=10&synopsis_length=100&hook6_length=10&order1=priority&order1direction=asc&order2=category&order2direction=asc&order3=sub_category&order3direction=asc&order4=release&order4direction=desc&fmt_details=on&fmt_summary=on&fmt_chart=on&fmt_url=on&z_axis=non&y_axis=none&x_axis=none&custom_fmt_details=on&custom_fmt_summary=on>\"><center>${P}</center></a>"
echo "</td>"

P=0;P=`grep " ${i}             " ${TEMP1} | grep "^................................................................................ 3 " | wc -l | awk '{print $1}'`
echo "<td bgcolor="#99ccff">"
echo "<a href=\"http://swsblweb1.central.sun.com:8080/EzReport/get_btrpt.cgi?&rtitle=$i+Open+Defect+Report+For+Priorty+3+CRs&product=${i}&p3=on&area=Defect&status_range=none&status=1-Dispatched&status=2-Incomplete&status=3-Accepted&status=4-Defer&status=5-Cause+Known&status=6-Fix+Understood&status=7-Fix+in+Progress&time_zone=GMT&records_limit=1000&fields=cr_number&fields=priority&fields=area&fields=product&fields=category&fields=sub_category&fields=release&fields=status&fields=date_submitted&fields=responsible_engineer&fields=submitted_by&fields=commit_to_fix_in_build&fields=synopsis&product_length=18&hook1_length=10&category_length=14&hook2_length=10&sub_category_length=12&hook3_length=10&release_length=10&hook4_length=10&build_length=10&hook5_length=10&synopsis_length=100&hook6_length=10&order1=priority&order1direction=asc&order2=category&order2direction=asc&order3=sub_category&order3direction=asc&order4=release&order4direction=desc&fmt_details=on&fmt_summary=on&fmt_chart=on&fmt_url=on&z_axis=non&y_axis=none&x_axis=none&custom_fmt_details=on&custom_fmt_summary=on>\"><center>${P}</center></a>"
echo "</td>"

P=0;P=`grep " ${i}             " ${TEMP1} | grep "^................................................................................ 4 " | wc -l | awk '{print $1}'`
echo "<td bgcolor="#99ccff">"
echo "<a href=\"http://swsblweb1.central.sun.com:8080/EzReport/get_btrpt.cgi?&rtitle=$i+Open+Defect+Report+For+Priorty+4+CRs&product=${i}&p4=on&area=Defect&status_range=none&status=1-Dispatched&status=2-Incomplete&status=3-Accepted&status=4-Defer&status=5-Cause+Known&status=6-Fix+Understood&status=7-Fix+in+Progress&time_zone=GMT&records_limit=1000&fields=cr_number&fields=priority&fields=area&fields=product&fields=category&fields=sub_category&fields=release&fields=status&fields=date_submitted&fields=responsible_engineer&fields=submitted_by&fields=commit_to_fix_in_build&fields=synopsis&product_length=18&hook1_length=10&category_length=14&hook2_length=10&sub_category_length=12&hook3_length=10&release_length=10&hook4_length=10&build_length=10&hook5_length=10&synopsis_length=100&hook6_length=10&order1=priority&order1direction=asc&order2=category&order2direction=asc&order3=sub_category&order3direction=asc&order4=release&order4direction=desc&fmt_details=on&fmt_summary=on&fmt_chart=on&fmt_url=on&z_axis=non&y_axis=none&x_axis=none&custom_fmt_details=on&custom_fmt_summary=on>\"><center>${P}</center></a>"
echo "</td>"

P=0;P=`grep " ${i}             " ${TEMP1} | grep "^................................................................................ 5 " | wc -l | awk '{print $1}'`
echo "<td bgcolor="#99ccff">"
echo "<a href=\"http://swsblweb1.central.sun.com:8080/EzReport/get_btrpt.cgi?&rtitle=$i+Open+Defect+Report+For+Priorty+5+CRs&product=${i}&p5=on&area=Defect&status_range=none&status=1-Dispatched&status=2-Incomplete&status=3-Accepted&status=4-Defer&status=5-Cause+Known&status=6-Fix+Understood&status=7-Fix+in+Progress&time_zone=GMT&records_limit=1000&fields=cr_number&fields=priority&fields=area&fields=product&fields=category&fields=sub_category&fields=release&fields=status&fields=date_submitted&fields=responsible_engineer&fields=submitted_by&fields=commit_to_fix_in_build&fields=synopsis&product_length=18&hook1_length=10&category_length=14&hook2_length=10&sub_category_length=12&hook3_length=10&release_length=10&hook4_length=10&build_length=10&hook5_length=10&synopsis_length=100&hook6_length=10&order1=priority&order1direction=asc&order2=category&order2direction=asc&order3=sub_category&order3direction=asc&order4=release&order4direction=desc&fmt_details=on&fmt_summary=on&fmt_chart=on&fmt_url=on&z_axis=non&y_axis=none&x_axis=none&custom_fmt_details=on&custom_fmt_summary=on>\"><center>${P}</center></a>"
echo "</td>"


P=0;P=`grep " ${i}             " ${TEMP2} | grep "^................................................................................ 1 " | wc -l | awk '{print $1}'`
echo "<td bgcolor=\"#e6e6ff\">"
echo "<a href=\"http://swsblweb1.central.sun.com:8080/EzReport/get_btrpt.cgi?&rtitle=This+Weeks+New+Open+Defect+Report+For+$i+Priorty+1+CRs&product=${i}&p1=on&to_cr_age=7&area=Defect&status_range=none&status=1-Dispatched&status=2-Incomplete&status=3-Accepted&status=4-Defer&status=5-Cause+Known&status=6-Fix+Understood&status=7-Fix+in+Progress&time_zone=GMT&records_limit=1000&fields=cr_number&fields=priority&fields=area&fields=product&fields=category&fields=sub_category&fields=release&fields=status&fields=date_submitted&fields=responsible_engineer&fields=submitted_by&fields=commit_to_fix_in_build&fields=synopsis&product_length=18&hook1_length=10&category_length=16&hook2_length=10&sub_category_length=14&hook3_length=10&release_length=10&hook4_length=10&build_length=10&hook5_length=10&synopsis_length=80&hook6_length=10&order1=priority&order1direction=asc&order2=category&order2direction=asc&order3=sub_category&order3direction=asc&order4=release&order4direction=desc&fmt_details=on&fmt_summary=on&fmt_chart=on&fmt_url=on&z_axis=non&y_axis=none&x_axis=none&custom_fmt_details=on&custom_fmt_summary=on\"><font color=red><center>${P}</center></font></a>"
echo "</td>"

P=0;P=`grep " ${i}             " ${TEMP2} | grep "^................................................................................ 2 " | wc -l | awk '{print $1}'`
echo "<td bgcolor=\"#e6e6ff\">"
echo "<a href=\"http://swsblweb1.central.sun.com:8080/EzReport/get_btrpt.cgi?&rtitle=This+Weeks+New+Open+Defect+Report+For+$i+Priorty+2+CRs&product=${i}&p2=on&to_cr_age=7&area=Defect&status_range=none&status=1-Dispatched&status=2-Incomplete&status=3-Accepted&status=4-Defer&status=5-Cause+Known&status=6-Fix+Understood&status=7-Fix+in+Progress&time_zone=GMT&records_limit=1000&fields=cr_number&fields=priority&fields=area&fields=product&fields=category&fields=sub_category&fields=release&fields=status&fields=date_submitted&fields=responsible_engineer&fields=submitted_by&fields=commit_to_fix_in_build&fields=synopsis&product_length=18&hook1_length=10&category_length=16&hook2_length=10&sub_category_length=14&hook3_length=10&release_length=10&hook4_length=10&build_length=10&hook5_length=10&synopsis_length=80&hook6_length=10&order1=priority&order1direction=asc&order2=category&order2direction=asc&order3=sub_category&order3direction=asc&order4=release&order4direction=desc&fmt_details=on&fmt_summary=on&fmt_chart=on&fmt_url=on&z_axis=non&y_axis=none&x_axis=none&custom_fmt_details=on&custom_fmt_summary=on\"><font color=red><center>${P}</center></font></a>"
echo "</td>"

P=0;P=`grep " ${i}             " ${TEMP2} | grep "^................................................................................ 3 " | wc -l | awk '{print $1}'`
echo "<td bgcolor=\"#e6e6ff\">"
echo "<a href=\"http://swsblweb1.central.sun.com:8080/EzReport/get_btrpt.cgi?&rtitle=This+Weeks+New+Open+Defect+Report+For+$i+Priorty+3+CRs&product=${i}&p3=on&to_cr_age=7&area=Defect&status_range=none&status=1-Dispatched&status=2-Incomplete&status=3-Accepted&status=4-Defer&status=5-Cause+Known&status=6-Fix+Understood&status=7-Fix+in+Progress&time_zone=GMT&records_limit=1000&fields=cr_number&fields=priority&fields=area&fields=product&fields=category&fields=sub_category&fields=release&fields=status&fields=date_submitted&fields=responsible_engineer&fields=submitted_by&fields=commit_to_fix_in_build&fields=synopsis&product_length=18&hook1_length=10&category_length=16&hook2_length=10&sub_category_length=14&hook3_length=10&release_length=10&hook4_length=10&build_length=10&hook5_length=10&synopsis_length=80&hook6_length=10&order1=priority&order1direction=asc&order2=category&order2direction=asc&order3=sub_category&order3direction=asc&order4=release&order4direction=desc&fmt_details=on&fmt_summary=on&fmt_chart=on&fmt_url=on&z_axis=non&y_axis=none&x_axis=none&custom_fmt_details=on&custom_fmt_summary=on\"><font color=red><center>${P}</center></font></a>"
echo "</td>"

P=0;P=`grep " ${i}             " ${TEMP2} | grep "^................................................................................ 4 " | wc -l | awk '{print $1}'`
echo "<td bgcolor=\"#e6e6ff\">"
echo "<a href=\"http://swsblweb1.central.sun.com:8080/EzReport/get_btrpt.cgi?&rtitle=This+Weeks+New+Open+Defect+Report+For+$i+Priorty+4+CRs&product=${i}&p4=on&to_cr_age=7&area=Defect&status_range=none&status=1-Dispatched&status=2-Incomplete&status=3-Accepted&status=4-Defer&status=5-Cause+Known&status=6-Fix+Understood&status=7-Fix+in+Progress&time_zone=GMT&records_limit=1000&fields=cr_number&fields=priority&fields=area&fields=product&fields=category&fields=sub_category&fields=release&fields=status&fields=date_submitted&fields=responsible_engineer&fields=submitted_by&fields=commit_to_fix_in_build&fields=synopsis&product_length=18&hook1_length=10&category_length=16&hook2_length=10&sub_category_length=14&hook3_length=10&release_length=10&hook4_length=10&build_length=10&hook5_length=10&synopsis_length=80&hook6_length=10&order1=priority&order1direction=asc&order2=category&order2direction=asc&order3=sub_category&order3direction=asc&order4=release&order4direction=desc&fmt_details=on&fmt_summary=on&fmt_chart=on&fmt_url=on&z_axis=non&y_axis=none&x_axis=none&custom_fmt_details=on&custom_fmt_summary=on\"><font color=red><center>${P}</center></font></a>"
echo "</td>"

P=0;P=`grep " ${i}             " ${TEMP2} | grep "^................................................................................ 5 " | wc -l | awk '{print $1}'`
echo "<td bgcolor=\"#e6e6ff\">"
echo "<a href=\"http://swsblweb1.central.sun.com:8080/EzReport/get_btrpt.cgi?&rtitle=This+Weeks+New+Open+Defect+Report+For+$i+Priorty+5+CRs&product=${i}&p5=on&to_cr_age=7&area=Defect&status_range=none&status=1-Dispatched&status=2-Incomplete&status=3-Accepted&status=4-Defer&status=5-Cause+Known&status=6-Fix+Understood&status=7-Fix+in+Progress&time_zone=GMT&records_limit=1000&fields=cr_number&fields=priority&fields=area&fields=product&fields=category&fields=sub_category&fields=release&fields=status&fields=date_submitted&fields=responsible_engineer&fields=submitted_by&fields=commit_to_fix_in_build&fields=synopsis&product_length=18&hook1_length=10&category_length=16&hook2_length=10&sub_category_length=14&hook3_length=10&release_length=10&hook4_length=10&build_length=10&hook5_length=10&synopsis_length=80&hook6_length=10&order1=priority&order1direction=asc&order2=category&order2direction=asc&order3=sub_category&order3direction=asc&order4=release&order4direction=desc&fmt_details=on&fmt_summary=on&fmt_chart=on&fmt_url=on&z_axis=non&y_axis=none&x_axis=none&custom_fmt_details=on&custom_fmt_summary=on\"><font color=red><center>${P}</center></font></a>"
echo "</td>"


echo "</tr>"
done >> ${HTMLPAGE}

# finish closing off the html page
echo "</table></center>" >> ${HTMLPAGE}


echo "<p><center><b>List of 'New' Honeycomb's Open CRs (Defects only) in the last 7 days</b></center>" >>${HTMLPAGE}

echo "<ul><font size=-1>" >>${HTMLPAGE}
awk '{printf("<li><a href=\"http://monaco.sfbay/detail.jsf?cr=%s\">%s</a><br>\n",$1,$0)}'  ./${TEMP2} >> ${HTMLPAGE}


echo "</font></ul><p><p><center><b>List of All Honeycomb's Open CRs (Defects only)</b></center>" >>${HTMLPAGE}

echo "<ul><font size=-1>" >>${HTMLPAGE}
awk '{printf("<li><a href=\"http://monaco.sfbay/detail.jsf?cr=%s\">%s</a><br>\n",$1,$0)}'  ./${TEMP1} >> ${HTMLPAGE}


echo "</font></ul></body>" >> ${HTMLPAGE}

# Cleanup
rm -f ${TEMP2}
rm -f ${TEMP1}
rm -f tnsnames.ora
rm -f ./${OPEN}
rm -f ./${OPEN_1WK}
