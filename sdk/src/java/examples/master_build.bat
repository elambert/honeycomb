@ECHO OFF
REM Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
REM Use is subject to license terms.
REM
REM Builds all Java example applications. 

javac -source 1.4 -classpath ../lib/honeycomb-client.jar *.java
jar cvf ../lib/honeycomb-sdk.jar *.class *.txt
del *.class
