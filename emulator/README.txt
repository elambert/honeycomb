$Id: README.txt 10857 2007-05-19 03:01:32Z bberndt $

Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.


**********************************************************************

		       Honeycomb emulator notes

**********************************************************************

These are basic notes on how to compile, configure and run the honeycomb
emulator.

Let's note <WS> the path to the honeycomb workspace.
The emulator implementation reuses a lot of Honeycomb code and is
actually only a glue to be able to run on a single machine.

The emulator is 100% Java. It is known to run on :
- Solaris 10/x86 ;
- Linux / i686 ;
- Windows ;
- MacOSX (10.3 Panther and 10.4 Tiger).

1. Compilation
--------------------

Since a lot of honeycomb components are reused, honeycomb modules have
to be compiled first :
cd <WS>/external; ant
cd <WS>/common; ant
cd <WS>/server; ant
cd <WS>/md_caches; ant
cd <WS>/filesystem; ant

Then, to compile the emulator, simply run ant :
cd <WS>/emulator; ant

The emulator bits can be found at :
<WS>/build/emulator/dist

You can tar what's under that directory and distribute it / copy it to
an other place. This directory is self contained and has all the bits
to run on the supported platforms.
Let's note <INS> the directory where the emulator has been "installed".

2. Configuration
--------------------

As a real Honeycomb cluster, the emulator hosts metadata and a schema has
to be defined.

The metadata configuration is a concatenation of 2 files :
a. the factory defaults parameters
   (<INS>/config/metadata_config_factory.xml) ;
b. a user specific configuration :
   <INS>/config/metadata_config.xml

Neither of these 2 files should be edited directly.

The proper way to add namespaces, attributes and filesystem views is :
a. to specify them in an empty file, using the XML file format
   specification. Let's call this file the configuration overlay.
b. run the <INS>/bin/metadata_config.merge.sh script, giving that file as a
   single parameter.
   The metadata_config_merge.sh script will read the given file, do some
   checks on the added attributes and then merge the results with the
   existing config.
   After metadata_config_merge.sh successfully runs, you should see the
   newly added attributes / fs views in <INS>/config/metadata_config.xml.

You'll find a couple of overlay examples under <INS>/config.
For example, if you want to store mp3s, you have to set up the mp3 schema
and run :
<INS>/bin/metadata_config.merge.sh <INS>/config/metadata_config_mp3demo.xml

If you want to change the schema after the initial configuration :
i. stop the emulator (see below) ;
ii. merge the new overlay ;
iii. restart the emulator.

3. Execution
--------------------

To start the emulator, simply run :
<INS>/bin/start.sh
The emulator will start.

The log file is at :
<INS>/logs/emulator.log

Data files will be stored under :
<INS>/var/data

All metadata (extended database, emulated OA and filesystem cache) will go
under :
<INS>/var/metadata

4. Use
--------------------

You can use the emulator in place of a regular Honeycomb cluster,
using the usual client library.

The emulator contains the Webdav server and you can therefore "mount"
the machine it is running on using regular webdav clients. The webdav mount
point is :
http://<IP>:8080/webdav

5. Stopping the emulator
--------------------

Simply connect to the machine where the emulator runs, using any web
browser :
http://<IP>:8080/admin

A page will show the list of running services. There is a link at the
bottom of the page to stop the emulator.

6. Windows specific notes
--------------------

The emulator on Windows works the same as on any UNIX platform. The only
differences are the scripts used to configure the metadata schema and to
start. You should use :

<INS>/bin/merge_metadata_config.bat
<INS>/bin/start.bat

respectively.
