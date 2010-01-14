#!/usr/bin/perl
use strict;

opendir(DIR, ".");
my @files = grep(/\.log.gz$/,readdir(DIR));
closedir(DIR);

print "making dirs...\n";
for(my $i=0;$i<256;$i++){
	mkdir("$i",777);
	`chmod 777 $i`;
}

print "migrating files...";
foreach my $file (@files) {
	my ($runid)=($file=~/^(\d+)\..*$/);
	if($runid){
		my $dir=$runid%256;
		`mv ./$runid.log.gz $dir/$runid.log.gz`;
	}
}
