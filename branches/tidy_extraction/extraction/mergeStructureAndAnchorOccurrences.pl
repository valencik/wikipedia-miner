	#!/usr/bin/perl -w

=head1 NAME

MergeAnchorOccurrences - Perl script for merging counts produced by extractAnchorOccurrences.pl 

=head1 DESCRIPTION

This script merges the files produced by ...

=cut
	
use strict ;

use Parse::MediaWikiDump;	
use Getopt::Long ;
use File::Basename;


use ProgressMonitor ;
use Stripper ;

binmode(STDOUT, ':utf8');

#get options ===============================================================================================

my $passes ;

GetOptions("passes=i"=>\$passes);

if (!defined $passes) {
	die "You must specify passes=<num> ; the number of passes that the extractAnchorOccurrences task was split into.\n" ;
}

#get data directory and check ==========================================================================

my $data_dir = shift(@ARGV) or die " - you must specify a writable directory containing output files from extractWikipediaData.pl and extractStructureAndAnchorOccurrences.pl\n" ;

my $anchorFile = "$data_dir/anchor.csv" ;
if (not -e $anchorFile) {
	die "'$anchorFile' is missing\n" ;
}

for (my $i = 1 ; $i<=$passes ; $i++) {
	
	my $file = "$data_dir/anchor_occurrence_$i.csv" ;
	if (not -e $file) {
		die "'$file' is missing\n" ;
	}
	
	$file = "$data_dir/structure_$i.csv" ;
	if (not -e $file) {
		die "'$file' is missing\n" ;
	}
}
	
# read anchors =================================================================================================================
	
my %anchorCounts = () ;
	
open(ANCHOR, $anchorFile) ;
binmode(ANCHOR, ':utf8') ;

my $pm = ProgressMonitor->new(-s $anchorFile, "Loading anchor vocabulary") ;
my $parts_done = 0 ;

my $anchors = 0 ;

while (defined(my $line=<ANCHOR>)) {
	$parts_done = $parts_done + length $line ;

	chomp($line) ;
	
	if ($line =~ m/\"(.+?)\",(\d+),(\d+),(\d+),(\d+)/) {
		my $anchor = $1 ;
		my $id = $2 ;
			
		my $totalLinkCount = $3 ;
		my $distinctLinkCount = $4 ;
			
		my $arrayRef = $anchorCounts{$anchor} ;
			
		if (defined $arrayRef) {
			#we have seen this anchor before, but now it is going to a new destination. Need to merge the occurrence counts
			my @counts = @{$arrayRef} ;
				
			#total counts can simply be added together
			$counts[0] = $counts[0] + $totalLinkCount ;
				
			#distinct counts can't technically be added together, but close enough (assuming one sense per anchor per document)
			$counts[1] = $counts[1] + $distinctLinkCount ;
				
			$anchorCounts{$anchor} = \@counts ;
		} else {
			my @counts = ($totalLinkCount, $distinctLinkCount, 0, 0) ;
			$anchorCounts{$anchor} = \@counts ;
			$anchors++ ;
		}			
	}
	    
	$pm->update($parts_done) ;
}
close ANCHOR ;
$pm->done() ;

# read and merge structure strings ===============================================================

open (STRUCTURE_OUT , "> $data_dir/structure.csv") ;

for (my $i = 1 ; $i<=$passes ; $i++) {
	
	my $file = "$data_dir/structure_$i.csv" ;
		
	open(STRUCTURE_IN, $file) ;

	$pm = ProgressMonitor->new(-s $file, "Merging structure elements (pass $i of $passes)") ;
	my $parts_done = 0 ;

	while (defined(my $line=<STRUCTURE_IN>)) {
		$parts_done = $parts_done + length $line ;
		
		print STRUCTURE_OUT $line ;
		
		$pm->update($parts_done) ;
	}
	
	$pm->done() ;	
	close STRUCTURE_IN ;
}

close STRUCTURE_OUT ;




# read occurrence counts ==========================================================================================================
	
for (my $i = 1 ; $i<=$passes ; $i++) {
	
	my $file = "$data_dir/anchor_occurrence_$i.csv" ;
		
	open(OCCURRENCES, $file) ;
	binmode(OCCURRENCES, ':utf8') ;

	$pm = ProgressMonitor->new(-s $file, "Merging anchor occurrences (pass $i of $passes)") ;
	my $parts_done = 0 ;

	while (defined(my $line=<OCCURRENCES>)) {
		$parts_done = $parts_done + length $line ;
	  	
		if ($line =~ m/^\"(.+?)\",(\d+),(\d+)$/) {
			my $anchor = $1 ;
			my $totalOccurrenceCount = $2 ;
			my $distinctOccurrenceCount = $3 ;
			
			my $arrayRef = $anchorCounts{$anchor} ;
			
			if (defined $arrayRef) {
				my @counts = @{$arrayRef} ;
				
				#total counts can simply be added together
				$counts[2] = $counts[2] + $totalOccurrenceCount ;
				
				#distinct counts can't technically be added together, but close enough (assuming one sense per anchor per document)
				$counts[3] = $counts[3] + $distinctOccurrenceCount ;
				
				$anchorCounts{$anchor} = \@counts ;
			} 
	  	}
		$pm->update($parts_done) ;
	}
	
	$pm->done() ;	
	close OCCURRENCES ;
}
	
# print occurrence counts ==========================================================================================================
	
$pm = ProgressMonitor->new($anchors, "Saving merged anchor occurrences") ;

open(OCCURRENCES, "> $data_dir/anchor_occurrence.csv") ;
binmode(OCCURRENCES, ':utf8');

$parts_done = 0 ;

while (my ($anchor, $arrayRef) = each(%anchorCounts) ) {
	$parts_done++ ;
		
	my @counts = @{$arrayRef} ;
		
    	print OCCURRENCES "\"$anchor\",$counts[0],$counts[1],$counts[2],$counts[3]\n" ;
   	$pm->update($parts_done) ;
}
$pm->done() ;
close(OCCURRENCES) ;