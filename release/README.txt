$Id: README.txt 10849 2007-05-19 02:44:57Z bberndt $

Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.


     ************************************************************

		 Honeycomb Release management system
				(HCR)

     ************************************************************


This document gives an overview of HCR. It exposes the release model
we follow in Honeycomb, as well as the actual commands that have to be
executed to perform all the operations.


----------------------------------------
I. Modules
----------------------------------------

The honeycomb product is cut into "modules". Today 5 modules are
available :
- server (all the code running on the server) ;
- client (the client library) ;
- common (code common to the server and the client library) ;
- external (code for imported technologies, such as Jetty) ;
- test (code written by QA).

Modules are the base elements. Modules are versionned, using a dotted
notation (e.g. 1.2.3). Module server-1.2 is version 1.2 of module
server.

The module versionning follows a tree design. Version 1.2.1 is the
first child of version 1.2.


----------------------------------------
II. Releases
----------------------------------------

Releases are sets of modules that make sense to be released
together.

A release is defined by :
- the list of modules that are part of the release ;
- the root version for each of them.

As we saw, module versions follow a tree pattern. When building a new
version of a release, the HCR software will look for the most
up-to-date direct child of the root version specified in the release
definition of each module, and tie them together.

For example, imagine that the ClientLib release is defined as :
client-4
common-3
(version 4 of client and version 3 of common).

When first created, the release ClientLib-1 will contain client-4 and
common-3. As new development happens, let's say that the client module
evolves and moves to version client-4.1. The next ClientLib release,
ClientLib-2 will tie client-4.1 and common-3 modules together.

Let's say we define a new release ClientLib_cedars at that point, that
is defined as :
client-4.1
common-3

To make changes to that specific release, code will have to be
developed in a branch that will lead to the creation of module
client-4.1.1 (see below).


----------------------------------------
III. How to create releases
----------------------------------------

We will note <HCR_DIR> the top level directory that is used by
HCR. Today this is /export/release/repository on hc-dev2.

Let's say we want to create release Rel :
1. Go to <HCR_DIR>/releases ;
2. Create the Rel directory (mkdir Rel) ;
3. Edit the definition file for that release. The definition file is
   a simple file containing one module definition per line (see
   existing ones for more details).
   The hcr_release_currentdef.sh script can create a definition file
   based o the current state of a release, for branching purposes.
4. Register the release with the hcr_release_register.sh script.

Registering the release will do some sanity checks and will create
necessary development branches in subversion. These development
branches will be place-holders where to check in new code that will go
in the next release version.


----------------------------------------
IV. How to publish a new module version
----------------------------------------

Simply use the hc_module_publish.sh script. The scripts needs the
module name and the release for which a new version has to be
published.

The script will create a new module version, based on the place-holder
defined in III.


----------------------------------------
V. How to publish a new release version
----------------------------------------

Once all the modules we need have been published, it is time to
publish a new release version.

Simply use the hcr_release_publish.sh script. This script only takes
the release name.


----------------------------------------
VI. hcr_module_howto.sh, the only useful script ...
----------------------------------------

When not familiar with the release system, it may be hard to figure
out which branch to checkout, where to putback, what to run when
someone wants to incorporate new code in a release.

The hcr_module_howto.sh script answers all these questions. If you
want to make changes in the client module of release ClientLib, simply
enter :

hcr_module_howto.sh -r ClientLib -m client

This will detail all the steps to follow to make such changes.


----------------------------------------
Appendix A : list of available scripts
----------------------------------------

hcr_module_howto.sh
  Details all the steps to follow in order to incorporate new code in
  a module, in a given release.

hcr_module_bringover.sh
  Automates the steps highlighted in hcr_module_howto.sh.

hcr_module_info.sh
  Lists all the available modules. When run with the -m <module>
  parameter, lists all the available versions of module <module>.

hcr_module_diff.sh
  Prints a diff of the current development state of a module and the
  last known version, in the context of a release.

hcr_module_publish.sh
  Publishes a new module version, for a given release.

hcr_module_unpublish.sh
  Unpublishes a previously published module.
  In order to be unpublished, a module shouldn't be part of an
  existing release and cannot be part of any release definition.

hcr_release_list.sh
  Lists the list of all registered releases.

hcr_release_status.sh
  Prints the current sync status between the latest pusblished version
  of a release and the developement tree.

hcr_release_register.sh
  Registers a new release.

hcr_release_currentdef.sh
  Generates a definition file, based on the current status of a
  release.

hcr_release_publish.sh
  Publishes a new release version.

hcr_repository_init.sh
  Creates the needed directory layout when setting up a new HCR
  repository (rarely used ...)


----------------------------------------
Appendix B: subversion layout
----------------------------------------

The subversion layout has to be modified, to allow scripts to keep
track of the necessary changes.
Let's note <SVN_ROOT> the root level of the subversion tree.
(For honeycomb, it is :
https://subversion.sfbay/repos/honeycomb
).

Under, <SVN_ROOT>, multiple directories are used :

- trunk
  This is the directory that contains top level version of all
  modules. All first level versions of all modules will be exported
  from this location (for example client-1 client-4, but not
  client-3.1).

- hcr_tags
  This contains all the module versions that have been created. You'll
  have one subdirectory for each module version that has been released
  (e.g. client-1, client-4, client-3.1).

= hcr_dev
  This is the place-holder for development branches. When a release
  that contains a non top-level module (e.g. client-4) is registered,
  a new entry will be created under hcr_dev (in that case client-4).
  All subsequent development for that module for that release will
  have to go to that branch. Later on, when next versions of that
  module for that release are published (client-4.x), they will be
  exported from that place.

     ************************************************************
