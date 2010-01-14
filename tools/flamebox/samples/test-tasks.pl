#
# This is the url to post the results to.
#
$posturl = 'http://localhost/cgi-bin/postresult.cgi';

#
# Define each task below.
#
$tasks{"hello"} = {
    repository=>"/repository", 
    module=>"sport",
    branch=>"HEAD",
    dir=>"/",
    commands=> [ "env", 
		 "echo hello"]};

$tasks{"ls"} = {
    repository=>"/repository", 
    module=>"sport",
    branch=>"HEAD",
    dir=>"/",
    commands=> [ "env", 
		 "ls"]};
