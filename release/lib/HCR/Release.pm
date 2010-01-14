
#
# $Id: Release.pm 11495 2007-09-12 01:32:14Z iamsds $
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

package HCR::Release;

use HCR::VersionTree;
use HCR::Utils;
use HCR::Login;
use HCR::Definition;

sub new {
  my $name = shift;
  my $path = shift;
  my $self = ();

  $self->{name} = $name;
  $self->{path} = $path;
  $self->{version} = HCR::VersionTree::parse_dir($path, "-");
  $self->{def} = read_definition($path);

  bless $self;
  return($self);
}

sub read_definition {
  my $path = shift;
  my $line;
  my @res = ();
  my $position = 0;
  my $filename = $path."/definition";

  open(FILE, $filename) || die "\
Invalid release !
Couldn't read definition file [$filename]\
\n";
  while ($line=<FILE>) {
    chomp($line);
    if ((length($line) > 0)
	&& (substr($line,0,1) ne "#")) {
      $res[$position] = HCR::Definition::new($line);
      $position++;
    }
  }
  close(FILE);

  return(\@res);
}

sub simple_export {
  my $self = shift;
  my $modules = shift;
  my $module = shift;
  my $dirname = shift;

  my $module_name;

  if (($module->can_checkout() == 1) || (!$module->{version}) ) {

    # This module is not cponly. Take the latest version
    my $version = $modules->get_version($module->{module}) || die "\
Unknown module [$module->{module}]\
\n";
    my $version_rc = $version->release_candidate($module->{version});
    if ($version_rc->{depth} == 0) {
      die "The module $module has not yet been published.\
You can publish it using hcr_module_publish.pl\
\n";
    }

    $module_name = $module->{module}."-".$version_rc->toString();

  } else {
    # This module is cponly. Take the version in the definition file
    $module_name = $module->{module}."-".$module->{version};
  }

  if ($module->{module}=~/^build/) {
    my @args = ("cp", "-R", "$::hcr_modules/$module->{module}/$module_name",
		"$dirname/build");
    system(@args) && die "\
Failed to copy [$module_name]\
\n";

    # Set the permissions
    @args = ("chmod", "-R", "u+w", "$dirname/".$module->{module});
    system(@args) && die "\
Failed to give write permissions to $dirname/".$module->{module}."\
\n";

  } else {
    symlink("../../../modules/$module->{module}/$module_name",
	    $dirname."/".$module->{module});
  }

  return($module_name);
}

sub export_new {
  my $self = shift;
  my $modules = shift;
  my $newvers = $self->{version}->fork();

  my $release_name = $self->{name}."-".$newvers->toString();
  my $dirname = "$self->{path}/$release_name";
  mkdir($dirname, 0755);
  my @cp_args = ("cp", $self->{path}."/definition", $dirname);
  system(@cp_args) && die "\
Failed to copy the definition file from $self->{path} to $dirname\
\n";

  # Create the hc_version file
  open(VERS, ">$dirname/hc_version") || die "\
Failed to create the hc_version file [$dirname/hc_version]\
\n";

  # Create links for all modules
  my $defref = $self->{def};
  my $module;
  print "\n";

  # Collect version of each module, append to version file later
  my $module_versions = "";

  foreach $module (@$defref) {

    my $module_name = $self->simple_export($modules, $module, $dirname);

    my $opened = 1;
    open(MOD, $dirname."/".$module->{module}."/hc_version") || ($opened = 0);
    if ($opened) {
      while (<MOD>) {
	$module_versions .= $_;
      }
      close(MOD);
    } else {
      print "Failed to read hc_version for module $module_name\n";
    }
	
    print "Imported module $module_name\n";
  }

  # Read in the "UberVersion - X.Y.Z including HC stuff + switch, dos, etc.)
  # Top line of the file contains public release name, 
  # additional lines have extra info used for versioning during upgrade.
  # NOTE: We might do away with this if we can better merge upgrade and the release system

  my $topline = 1;
  my $uberver = $dirname."/build/version";
  if (open(UBERVERS, $uberver)) {
    while(<UBERVERS>) {
        if ($topline) {
            chomp $_;
            print VERS "ST5800 $_ release [$release_name]\n";
	    print VERS $module_versions;
            $topline = 0;
        } else {
            print VERS $_;
        }
    }
    close(UBERVERS);
  } else {
    print "Failed to read UberVersion from $uberver: $!";
    # Public release name is unknown
    print VERS "ST5800 release [$release_name]\n";
    print VERS $module_versions;
  }

  close(VERS);

  print "\nCreated release $release_name\n\n";
}

sub build {
  my $self = shift;
  my $modules = shift;
  my $lmod = $self->{def};
  my $module;
  my $release_name = "$self->{name}-".$self->{version}->release_candidate()->toString();
  my $root_dir = "$self->{path}/$release_name";
  my $build_module = undef;
  my $contains_platform = 0;
  
  foreach $module (@$lmod) {
    if ($module->to_be_built()) {
      print "Building $module->{module}\n";
      my $log_file = "$root_dir/build/build_$module->{module}.log";
      my $ant_target = "pkg";
      if ($module->{module} =~ /^external/) {
        $ant_target = "build_pkg";
      }
      my @args = ("sh", "-c", "cd $root_dir/$module->{module}; ant -Dbasedir=$root_dir/build ${ant_target} > $log_file 2>&1");
      system(@args) && die "\
Compilation of $module->{module} failed. See [$log_file] for more details\
";
      print "Compilation of $module->{module} succeeded\n";
    }
    if ($module->{module} =~ /^build/) {
      $build_module = $module;
    }
    if ($module->{module} =~ /^platform/) {
      $contains_platform = 1;
    }
  }

  # Make the DVD
  if ($contains_platform == 1) {
    print "Making the DVD\n";
    my @args = ("sudo", "sh", "-c", "cd $root_dir/platform; ant -Dbasedir=$root_dir/build dvd > $root_dir/build/build_dvd.log");
    system(@args) && die "\
Failed to make the DVD. See [$root_dir/build/build_dvd.log] for more details\
";
    print "The DVD has been successfully built\n";
    my @args = ("sudo", "chown", "-R", "-f", "hcbuild", "$root_dir/build/platform", "$root_dir/build/pkgdir");
    system(@args) && die "\
Warning: Failed to change the owner back to hcbuild\
";
  } else {
    print "The platform module is not used. Don't make the DVD\n";
  }

  # Move the build directory to AUTOBUILT and recheck out build

  my @args = ("mv", "$root_dir/build", "$root_dir/AUTOBUILT");
  system(@args) && die "\
Failed to move [$root_dir/build] to [$root_dir/AUTOBUILT]\
";

  if ($build_module) {
    my $name = $self->simple_export($modules, $build_module, $root_dir);
    print "Module $name has been reexported\n";
  }

  @args = ("sudo", "chmod", "-R", "-f", "a-w", "$root_dir");
  system(@args) && die "\
Failed to remove the write permission to [$root_dir]\
\n";

  print "***** Compilation of [$release_name] succeeded *****\n";
}

sub get_module_version {
  my $self = shift;
  my $module = shift;
  my $def = undef;
  my $lref = $self->{def};

  for (my $i=0; (!$def) && ($i<@$lref); $i++) {
    if ($$lref[$i]->{module}=~/^$module/) {
      $def = $$lref[$i];
      $found = 1;
    }
  }

  if (!$def) {
    die "\
Module $module is not used in release $self->{name}\
\n";
  }

  return($def->{version});
}

sub get_module_definition {
  my $self = shift;
  my $module = shift;
  my $def = undef;
  my $lref = $self->{def};

  for (my $i=0; (!$def) && ($i<@$lref); $i++) {
    if ($$lref[$i]->{module}=~/^$module/) {
      $def = $$lref[$i];
      $found = 1;
    }
  }

  if (!$def) {
    die "\
Module $module is not used in release $self->{name}\
\n";
  }

  return($def);
}

sub get_modules {
  my $self = shift;
  my @result = ();
  my $lref = $self->{def};
  my $mod;
    
  foreach $mod (@$lref) {
    push @result, ($mod->{module});
  }

  return(@result);
}

sub register {
  my $self = shift;
  my $modules = shift;
  my $defref = $self->{def};
  my $module;
  my $login = HCR::Login::new();

  my @login_args = $login->get_svn_arguments();

  foreach $module (@$defref) {
    my $version_tree = $modules->get_version($module->{module}) || die "\
Unknown module [$module->{module}]\
\n";
    if (!$module->can_checkout()) {
      print "Module $module->{module} is copy only. Skiping the registration\n";
    } elsif ($module->{version}) {
      my $version = $version_tree->get($module->{version});
      if (!$version) {
	die "\
Version $module->{version} of module $module->{module} has not been published !\
\n";
      }
      if ($version->{depth} > 0) {
	my $repos = $::hcr_svn_tags."/$module->{module}-".$version->toString();
	my $repos_dest = $::hcr_svn_dev."/$module->{module}-".$version->toString();
	
	# Check if the development branch already exists
	
	my @args = ("sh", "-c", "svn ls $repos_dest @login_args > /dev/null 2>&1");
	my $exit_code = system(@args);
	
	$exit_code = $exit_code >> 8;
	
	if ($exit_code == 0) {
	  print "The [$repos_dest] development branch already exists\n";
	} else {
	  # We have to create a development branch
	  print "Creating a development branch for $module->{module} [$repos_dest]\n";
	  @args = ("svn", "copy",
		   "--message", "6226407\nkeep open\nRegistration of release ".$self->{name},
		   $repos, $repos_dest);
	  push @args, @login_args;
	  system(@args) && die "\
svn copy failed\
\n";
	}
      }
    } else {
      print "Module $module->{module} comes from the trunk in that release. No need to create a branch\n";
    }
  }

  print "\nAll the development branches have been created\n\n";
}

sub print_current_definition {
  my $self = shift;
  my $modules = shift;
  my $defref = $self->{def};

  print "# Definition file automaticallly generated from release ".$self->{name}."\n\n";

  my $module;
  foreach $module (@$defref) {

    my $version = $modules->get_version($module->{module}) || die "\
Unknown module [$module->{module}]\
\n";
    my $version_rc = $version->release_candidate($module->{version});
    if ($version_rc->{depth} == 0) {
      die "The module $module has not yet been published.\
You can publish it using hcr_module_publish.pl\
\n";
    }

    $module->print_def($version_rc->toString());
  }
}

1;
