#
# These are the default repository, module, and branch.
#
$repository = "/repository";
$module = "sport";
$branch = "HEAD";

#
# The default spacing for the rows in seconds.
#
$period = 5 * 60;

#
# The default time in seconds for page.
#
$length = 6 * 60 * 60;

#
# This the unix directory path and the url path, respectively, to the
# task images (i.e., vertical text for the column headings).
#
#$imagepath = "/home/davidwu/public_html/task_img";
#$imageurl = "task_img";
$imagepath = "/usr/local/www/flamebox/html/task_img";
$imageurl = "http://tinderbox/flamebox/task_img";

#
# This the unix directory path and the url path, respectively, to the
# logs.
#
#$logpath = "/home/davidwu/public_html/logs";
#$logurl = "logs";
$logpath = "/usr/local/www/flamebox/html/logs";
$logurl = "http://tinderbox/fbox-cgi/showlog.cgi";

#
# This is the url to bonsai query interface. You must include the
# treeid parameter here.
#
$bonsaiurl = "http://bonsai/bonsai/cvsquery.cgi?treeid=sport";

#
# These are the parameters to the flamebox database.
#
$dburl = "DBI:mysql:flamebox";
$dbuser = "nobody";
$dbpasswd = "";

#
# These are the parameters to the bonsai database.
#
$bonsai_dburl = "DBI:mysql:bonsai";
$bonsai_dbuser = "nobody";
$bonsai_dbpasswd = "";

#============================================================================
#
# User-extensible subroutines
#

#
# Parses a line and returns line type.
#
# OK - means the line parsed fine and no action should be taken.
# INFO - means the line contains information that should be appened to the message
#        area.
# ERROR - means the line is an error line, so make a link to it in the message
#         area.
# WARNING - means the line is a warning line, so make a link to it in the message
#           area.
#

sub parse_line
{
    my ($line) = (@_);

    # Match ignore commands.
    if ($line =~ m/.*flamebox-ignore\s+([\S]+)\s+([^\n\r]+)/) {
	# Add a new ignore pattern.
	my $key = $1;
	my $pat = $2;
	$ignores{$key} = $pat;
	return "OK";
    } elsif ($line =~ m/.*flamebox-ignore\s+([\S]+)\s+([^\n\r]+)/) {
	# Remove an existing ignore pattern.
	my $key = $1;
	if (defined $ignores{$key}) {
	    delete $ignores{$key};
	    return "OK";
	} else {
	    return "ERROR";
	}
    } elsif ($line =~ m/.*tinderbox-ignore\s+([\S]+)\s+([^\n\r]+)/) {
	# Add a new ignore pattern.
	my $key = $1;
	my $pat = $2;
	$ignores{$key} = $pat;
	return "OK";
    } elsif ($line =~ m/.*tinderbox-ignore\s+([\S]+)\s+([^\n\r]+)/) {
	# Remove an existing ignore pattern.
	my $key = $1;
	if (defined $ignores{$key}) {
	    delete $ignores{$key};
	    return "OK";
	} else {
	    return "ERROR";
	}
    } elsif ($line =~ m/Running Test: .* \(/) {
	return "INFO";
    }

    # Check ignore patterns.
    foreach my $pat (values %ignores) {
	if ($line =~ m/$pat/) {
	    return "OK";
	}
    }
    
    # Match errors.
    if (($line =~ m/\sORA-\d/)		||		# Oracle
	($line =~ m/\bNo such file or directory\b/)	||
	($line =~ m/\b[Uu]nable to\b/)	||		
	($line =~ m/\bnot found\b/)		||		# shell path
	($line =~ m/\b[Dd]oes not\b/)	||		# javac error
	($line =~ m/\b[Cc]ould not\b/)	||		# javac error
	($line =~ m/\b[Cc]an\'t\b/)		||		# javac error
	($line =~ m/\b[Cc]an not\b/)		||		# javac error
	($line =~ m/\bDied\b/)		||		# Perl error
	($line =~ m/\b(?<!\/)[Ee]rror(?!\.)\b/)||		# C make error
	($line =~ m/\b[Ff]atal\b/)		||		# link error
	($line =~ m/\b[Dd]eprecated\b/)	||		# java error
	($line =~ m/\b[Aa]ssertion\b/)	||		# test error
	($line =~ m/\b[Aa]borted\b/)		||		# cvs error
	($line =~ m/\b[Ff]ailed\b/)		||		# java nmake
	($line =~ m/Unknown host /)		||		# cvs error
	($line =~ m/\: cannot find module/)	||		# cvs error
	($line =~ m/No such file or directory/)	 ||	# cpp error
	($line =~ m/jmake.MakerFailedException:/) ||         # Java error
	($line =~ m/FAILED/) ||                              # Unit test error
	($line =~ m/Segmentation fault/) ||                  # Seg faults
	($line =~ m/Bus error/) ||                           # Bus error
	($line =~ m/core dumped/) ||                         # Core dump
	($line =~ m/dumping core/) ||                        # Core dump
	($line =~ m/STACK TRACE/) ||                         # trace
	($line =~ m/\b[Ee]rror\b/) ||                        # General error
	($line =~ m/ERROR/) || 				# General error
	($line =~ m/err\]/) ||                               # err on logs
	($line =~ m/fatal\]/) ||                             # fatal on logs
	0) {

	return "ERROR";
    } elsif (($line =~ m/^[-._\/A-Za-z0-9]+\.[A-Za-z0-9]+\:[0-9]+\:/) ||
	     ($line =~ m/^\"[-._\/A-Za-z0-9]+\.[A-Za-z0-9]+\"\, line [0-9]+\:/) ||

	     ($line =~ m/\b[Ww]arning\b/) ||
	     ($line =~ m/not implemented:/) ||
	     0) {
	return "WARNING";
    } else {
	return "OK";
    }
}

1;

