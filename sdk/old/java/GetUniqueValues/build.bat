REM Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
REM Use is subject to license terms.
REM
javac -classpath ../../lib/honeycomb-sdk.jar;../../lib/honeycomb-client.jar GetUniqueValues.java
jar cvfm ../build/GetUniqueValues.jar mainClass.mf *.class usage.txt
del *.class
