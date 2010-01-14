@echo off
if "%OS%" == "Windows_NT" setlocal

rem
rem $Id$
rem
rem Copyright 2006 Sun Microsystems, Inc.  All rights reserved.
rem Use is subject to license terms.
rem

set DIR=%~dp0..

set CLASSPATH="%DIR%\lib\bsh-2.0b2.jar";"%DIR%\lib\honeycomb-common.jar";"%DIR%\lib\servlet-4.2.19.jar";"%DIR%\lib\jetty-4.2.20.jar";"%DIR%\lib\concurrent.jar";"%DIR%\lib\honeycomb-emulator.jar";"%DIR%\lib\jug.jar";"%DIR%\lib";"%DIR%\lib\derby-10.1.1.0.jar"

java -Xms64m -classpath %CLASSPATH% -Demulator.root="%DIR%" -Dmd.cache.path="%DIR%\lib\md_caches" -Dfscache.class=com.sun.honeycomb.fscache.DerbyFileCache -Djava.util.logging.config.file="%DIR%\config\logger.cfg" -Duid.lib.path=emulator com.sun.honeycomb.cm.NodeMgr
