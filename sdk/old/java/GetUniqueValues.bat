@ECHO OFF
REM Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
REM Use is subject to license terms.
REM
REM Launches pre-built example commandline application

java -classpath ..\lib\honeycomb-sdk.jar;honeycomb-client.jar GetUniqueValues %* 
