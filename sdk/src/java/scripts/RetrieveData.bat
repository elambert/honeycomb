@ECHO OFF
REM Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
REM Use is subject to license terms.
REM
REM Launches pre-built example commandline application

set DIR=%~dp0..

java -classpath "%DIR%\lib\honeycomb-sdk.jar";"%DIR%\lib\honeycomb-client.jar" RetrieveData %* 
