#!/bin/bash

# Check arg

if [ $# -eq 1 ] ; then
  DATAVIP=$1
else
  echo "Usage: $0 <datavip>" >&2
  exit 1
fi

# Determine platform we are running on

OS=`uname -s`
MACH=`uname -p`

if [ ".$OS" = ".SunOS" ] ; then
  REL=`uname -r`
  if [ ".$REL" = ".5.9" ] ; then
    REL=9
  else
    REL=10
  fi
  if [ ".$MACH" = ".sparc" ] ; then
    PLATFORM=sol_${REL}_sparc
  else
    PLATFORM=sol_${REL}_x86
  fi
else
  if [ ".$OS" = ".Linux" ] ; then
    PLATFORM="$OS"
  else
    PLATFORM=Win32
  fi
fi

export LD_LIBRARY_PATH=../dist/c/$PLATFORM/lib

echo HC: $DATAVIP
echo C API arch: $PLATFORM 
echo C API LD_LIBRARY_PATH: $LD_LIBRARY_PATH
echo 

C_EXE_DIR=../dist/c/examples/$PLATFORM/build
JAVA_EXE_DIR=../dist/java/scripts

echo store in C
DATE=`date`
QUERY="system.test.type_string='${DATE}'"

if [ ! -e $C_EXE_DIR/StoreFile ]; then
  echo no $C_EXE_DIR/StoreFile - probably need to compile >&2
  exit 1
fi

COID=`$C_EXE_DIR/StoreFile $DATAVIP /etc/passwd \
		-m "system.test.type_string=$DATE" | head -n 1`
#echo $COID

echo retrieve in java
$JAVA_EXE_DIR/RetrieveData.sh $DATAVIP $COID /tmp/JR.$$

echo check retrieved data
cmp /etc/passwd /tmp/JR.$$ > /dev/null
if [ $? -ne 0 ] ; then
  echo retrieved file /tmp/JR.$$ does not match /etc/passwd
  exit 1
fi
/bin/rm -f /tmp/JR.$$

echo query in java
JOID=`$JAVA_EXE_DIR/Query.sh $DATAVIP "$QUERY"`
JOID=`echo $JOID | awk '{print $1}'`
#echo $JOID

echo check retrieved oid
if [ "$COID" != "$JOID" ] ; then
  echo incorrect result:
  echo "  stored:  $COID"
  echo "  queried: $JOID"
  exit 1
fi

echo
echo store in java
DATE=`date`
QUERY=system.test.type_string=\'$DATE\'
JOID=`$JAVA_EXE_DIR/StoreFile.sh $DATAVIP /etc/passwd \
			-m "system.test.type_string=$DATE" | head -n 1`
#echo $JOID

echo retrieve in C
$C_EXE_DIR/RetrieveData $DATAVIP $JOID /tmp/JR.$$

echo check retrieved data
cmp /etc/passwd /tmp/JR.$$ > /dev/null
if [ $? -ne 0 ] ; then
  echo retrieved file /tmp/JR.$$ does not match /etc/passwd
  exit 1
fi
/bin/rm -f /tmp/JR.$$

echo query in C
COID=`$C_EXE_DIR/Query $DATAVIP "$QUERY"`
COID=`echo $COID | awk '{print $1}'`
#echo $COID

echo check retrieved oid
if [ "$COID" != "$JOID" ] ; then
  echo incorrect result:
  echo "  stored:  $JOID"
  echo "  queried: $COID"
  exit 1
fi

echo
echo test PASSED
exit 0
