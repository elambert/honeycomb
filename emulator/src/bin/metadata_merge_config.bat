@echo off
if "%OS%" == "Windows_NT" setlocal

rem
rem $Id$
rem
rem Copyright 2006 Sun Microsystems, Inc.  All rights reserved.
rem Use is subject to license terms.
rem

set DIR=%~dp0..

set CLASSPATH="%DIR%\lib\honeycomb-common.jar";"%DIR%\lib\honeycomb-emulator.jar"

java -cp "%CLASSPATH%" -Demulator.root="%DIR%" com.sun.honeycomb.emd.config.MergeConfig %1
