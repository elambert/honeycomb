#
# This is the url to post the results to.
#
our $posturl = 'http://qatts/fbox-cgi/postresult.cgi';

# globals
our $rep = "/svn/qatt"; # cvs or subversion repository
our $branch = "trunk"; # default branch
our $bdir = "/tmp/flamebox/build"; # where to put build directories
our $module = "quince";

#
# Define each task below.
#
$tasks{"build-quince"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-build",
    dir => "/",
    commands => [ "env", 
		  "rm -rf ${bdir}/quince",
		  "mkdir -p ${bdir}/quince",
		  "cd $bdir",
		  "svn checkout svn://wiki/$rep/quince",
		  "cd quince",
		  "find ./ -name '*.pl' -exec perl -c {} \\;"
		  ]};

$tasks{"hello"} = {
    repository => $rep, 
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands=> [ "env", 
		 "echo hello"
		 ]};

$tasks{"ls"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands => [ "env", 
		  "ls"
		  ]};

$tasks{"up-copy-empty-dir"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands => [ 'rsh winqac1 "set LOGLEVEL=5 & perl TestScripts/run_cifs.pl 1"' ]
};

$tasks{"up-copy-empty-file"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands => [ 'rsh winqac1 "set LOGLEVEL=5 & perl TestScripts/run_cifs.pl 2"' ]
};

$tasks{"down-copy-empty-dir"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands => [ 'rsh winqac1 "set LOGLEVEL=5 & perl TestScripts/run_cifs.pl 3"' ]
};

$tasks{"down-copy-empty-file"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands => [ 'rsh winqac1 "set LOGLEVEL=5 & perl TestScripts/run_cifs.pl 4"' ]
};

$tasks{"up-move-empty-dir"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands => [ 'rsh winqac1 "set LOGLEVEL=5 & perl TestScripts/run_cifs.pl 5"' ]
};

$tasks{"up-move-empty-file"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands => [ 'rsh winqac1 "set LOGLEVEL=5 & perl TestScripts/run_cifs.pl 6"' ]
};

$tasks{"down-move-empty-dir"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands => [ 'rsh winqac1 "set LOGLEVEL=5 & perl TestScripts/run_cifs.pl 7"' ]
};

$tasks{"down-move-empty-file"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands => [ 'rsh winqac1 "set LOGLEVEL=5 & perl TestScripts/run_cifs.pl 8"' ]
};

$tasks{"rename-empty-dir"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands => [ 'rsh winqac1 "set LOGLEVEL=5 & perl TestScripts/run_cifs.pl 9"' ]
};

$tasks{"rename-empty-file"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands => [ 'rsh winqac1 "set LOGLEVEL=5 & perl TestScripts/run_cifs.pl 10"' ]
};

$tasks{"delete-empty-dir"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands => [ 'rsh winqac1 "set LOGLEVEL=5 & perl TestScripts/run_cifs.pl 11"' ]
};

$tasks{"delete-empty-file"} = {
    repository => $rep,
    module => $module,
    branch => $branch,
    group => "test-test",
    dir => "/",
    commands => [ 'rsh winqac1 "set LOGLEVEL=5 & perl TestScripts/run_cifs.pl 12"' ]
};

