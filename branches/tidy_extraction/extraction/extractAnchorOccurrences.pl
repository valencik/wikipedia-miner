#!/usr/bin/perl -w

=head1 NAME

ExtractAnchorOccurrences - Perl script for extracting the number of times anchors occur in Wikipedia 

=head1 DESCRIPTION

This script counts occurances of ngrams (words and phrases) in a Wikipedia dump. Ngrams are only counted
if they correspond to anchors (the text used within links between wikipedia articles), which must first be 
extracted using extractWikipediaData.pl script.

The script requires a directory which contains a single Wikipedia xml dump 
and the csv files produced by extractWikipediaData.pl (Specifically, the anchor.csv file)

This is a very large process; there are millions of wikipedia articles, and each 
contains thousands of ngrams. Luckily, this script can easily be run
on several machines or processors at once.  Use -passes=<num> to specify how many 
machines it will be run on, and on each machine use -passIndex=<num> to specify which 
part of the data the machine will process (-passIndex must be a number from 1 to -passes).

If only one machine is used (-passes=1), then this script produces a single file called 
anchor_occurance.csv in the same directory as the xml dump. The file is described in the 
README of your Wikipedia-Miner distribution.

If multiple machines are used, each will produce a numbered file from anchor_occurance_1.csv to
anchor_occurance_<passes>.csv. Use the mergeAnchorOccurances.pl script to produce anchor_occurance.csv
from these.

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
my $passIndex ; 
my $log ;
my $languageFile ;
my $max_ngram_length = 10 ;

GetOptions("passes=i"=>\$passes, "passIndex=i"=>\$passIndex, 'log' => \$log, "languageFile=s"=>\$languageFile);

if (!defined $passes) {
	die "You must specify passes=<num> ; the number of passes this task will be split into.\n" ;
}

if (!defined $passIndex) {
	die "You must specify passIndex=<num> ; the index (a number between 1 and $passes) of the pass to perform.\n" ; 
}

if ($passIndex > 0 and $passIndex <= $passes) {
	print " - performing pass $passIndex of $passes\n" ;
} else {
	die " - passIndex must be a number between 1 and $passes\n" ;
}

if ($log) {
	print " - problems will be logged to \"occurrences.log\" in the data directory you specified\n" ;
}

if (not defined $languageFile) {
	$languageFile = "./languages.xml" ;
} 
print " - language dependant variables will be loaded from \"$languageFile\"\n" ;


#get data directory and dump file ==========================================================================

my $data_dir = shift(@ARGV) or die " - you must specify a writable directory containing a single WikiMedia dump file\n" ;

my $dump_file ;
my @files = <$data_dir/*>;
foreach my $file (@files) {
	if ($file =~ m/pages-articles.xml/i) {
		if (defined $dump_file) {
			die " - '$data_dir' contains multiple dump files\n" ;
		} else {
			$dump_file = $file ;
		}
	}
}

if (not defined $dump_file) {
	die " - '$data_dir' does not contain a WikiMedia dump file\n" ;
}




# get namespaces ===========================================================================================

my %namespaces = () ;
my $categoryPrefix ;

open(DUMP, $dump_file) or die "dump file '$dump_file' is not readable.\n" ;
binmode(DUMP, ':utf8');
	
while (defined (my $line = <DUMP>)) {

	$line =~ s/\s//g ;  #clean whitespace

	if ($line =~ m/<\/namespaces>/i) {
		last ;
	}
		
	if ($line =~ m/<namespaceKey=\"(\d+)\">(.*)<\/namespace>/i){
		$namespaces{lc($2)} = $1 ;
			
		if ($1 == 14) {
			$categoryPrefix = $2 ;
		}
	}
		
	if ($line =~ m/<namespaceKey=\"(\d+)\"\/>/i) {
		$namespaces{""} = $1 ;
	}
}
close DUMP ;


# language dependent variables =========================================================================

my $languageCode = getLanguageCode($dump_file) ;
my @langVariables = &getLanguageVariables($languageFile, $categoryPrefix, $languageCode);
  
my $langName = $langVariables[0] ;
my $root_category = $langVariables[1] ;
my $dt_test = $langVariables[2] ;
my $dc_test = $langVariables[3] ;


# logging===============================================================================================

if($log) {
	Stripper::setLogfile("$data_dir/occurrences.log") ;
}

open(NGRAMS, "> $data_dir/ngrams.txt") ;
binmode(NGRAMS, ':utf8') ;


# get the vocabulary of ngrams that we are interested in ==============================================

my %ngramCounts = () ;			 #ngram text -> array where first element = total count and 2nd = distinct count

open(ANCHOR, "$data_dir/anchor.csv") || die "'$data_dir/anchor.csv' could not be found. You must run extractWikipediaData.pl first!\n" ;
binmode(ANCHOR, ':utf8') ;

my $pm = ProgressMonitor->new(-s "$data_dir/anchor.csv", "Loading anchor vocabulary") ;
my $parts_done = 0 ;

while (defined(my $line=<ANCHOR>)) {
	$parts_done = $parts_done + length $line ;

	chomp($line) ;
		
	if ($line =~ m/\"(.+?)",(\d+),(\d+)(,\d+),(\d+)/) {
		my $anchor = unescape($1) ;
		
		my @array = (0,0) ;
		
		$ngramCounts{$anchor} = \@array  ;
	}
	$pm->update($parts_done) ;
}
close ANCHOR ;

$pm->done() ; 


# now measure how many wikipedia articles the ngrams are found in =============================================

$pm = ProgressMonitor->new(-s $dump_file, "Gathering anchor occurances") ;

my $pages = Parse::MediaWikiDump::Pages->new($dump_file) ;
my $page ;

while(defined($page = $pages->next())) {

	$pm->update($pages->current_byte) ;

	my $id = int($page->id) ;
	my $title = $page->title ;
	
	Stripper::setCurrDoc("$id:$title") ;
	
	if ($id % $passes == ($passIndex-1)) {
		#only process pages that are valid for this pass
		my $namespace = $page->namespace;
		my $namespace_key = $namespaces{lc($namespace)} ;
			   
		# check if namespace is valid
		if ($page->namespace ne "" && defined $namespace_key) {
			$title = substr $title, (length $page->namespace) + 1;
		} else {
			$namespace = "" ;
			$namespace_key = 0 ;
		}
	
		#only process articles
		if ($namespace_key==0 and not defined $page->redirect) {
			
			#only interested in first ngram occurance in each document.
			my %ngrams_seen = () ;
			
			my $textRef = $page->text ;
			my $text = $$textRef ;
			
			#print NGRAMS "$text\n" ;
			
			$text = Stripper::stripToPlainText($text) ;
			
			#it is a little tricky to decide wheither an ngram can span a line break. 

			$text =~ s/\n\s*\n/\n\n/g ; #collapse whitespace if it is the only thing found between two linebreaks
			$text =~ s/(?<!\n)\n(?!\n)/ /g ;  #replace isolated line breaks with spaces			
			$text =~ s/ {2,}/ /g ;  #collapse multiple spaces
			
			#print NGRAMS "$text\n" ;
			
			#now process text one line at a time
			while($text =~ m/(.*?)\n/gi) {		
				gather_ngrams($1, \%ngrams_seen) ;	
			}
		}
	} 
}

$pm->done() ;


# now save the occurrence counts==========================================================================

open(OCCURRENCES, "> $data_dir/anchor_occurrence_".$passIndex.".csv") ;
binmode(OCCURRENCES, ':utf8');

$pm = ProgressMonitor->new(scalar keys %ngramCounts, "Saving anchor occurrences") ; 
$parts_done = 0 ;

while (my ($ngram, $arrayRef) = each(%ngramCounts) ) {
	$pm->update() ;
	
	my @counts = @{$arrayRef} ;

	print OCCURRENCES "\"$ngram\",$counts[0],$counts[1]\n" ;
}

$pm->done() ;
close(OCCURRENCES) ;














sub gather_ngrams {
	
	my $text = shift ;
	
	#print NGRAMS "$text\n" ;
	
	$text = " $text " ;
	
	my $ref = shift ;
	my %ngrams_seen = %$ref ;	
	
	#gather all positions where ngrams could possibly split on
	my @splits = () ;
	while ($text =~ m/\W/g) {	
		push(@splits, pos($text)) ;
	}
	
	for (my $i=0 ; $i<scalar(@splits) ; $i++) {

		my $startIndex = $splits[$i]  ;
		
		for (my $j=min($i + $max_ngram_length, scalar(@splits)-1) ; $j > $i ; $j--) {
			my $currIndex = $splits[$j] ;	
			my $ngram = substr($text, $startIndex, $currIndex-$startIndex-1) ;
			
			if ($ngram =~ m/^\s+/ or $ngram =~ m/\s+$/ or $ngram eq "" ) {
				next ;
			}
			
			#print NGRAMS " - '$ngram'\n" ;
			
			if (length($ngram)==1 && substr($text, $startIndex-1, 1) eq "'") {
				next ;
			} 
			
			
			my $countsRef = $ngramCounts{$ngram} ;
			
			if (defined $countsRef) {
				
				my @counts = @{$countsRef} ;
				
				$counts[0] ++ ;
				
				if (not defined $ngrams_seen{$ngram}) {
					$counts[1] ++ ;
				}
				
				$ngramCounts{$ngram} = \@counts ;
			} 
		  	
		  	$ngrams_seen{$ngram} = 1 ;
		}
	}
}


sub min {
	
	$a = shift ;
	$b = shift ;
	
	if (defined $a and defined $b) {
		if ($a < $b){
			return $a ;
		} else {
			return $b ;
		}
	} else {
		if (defined $a) {
			return $a ;
		} else {
			return $b ;
		}
	}
}




# text cleaning stuff ======================================================================================


# escape newlines and quotes; the only two characters which would screw up importing data into mysql.
sub escape {
	
	my $text = shift ;
	
	$text =~ s/\n/\\n/g ;
	$text =~ s/\r/\\r/g ;
	$text =~ s/\"/\\"/g ;
	
	return $text ;
}

# unescape newlines and quotes; the only two characters which would screw up importing data into mysql.
sub unescape {
	
	my $text = shift ;
	
	$text =~ s/\\n/\n/g ;
	$text =~ s/\\r/\r/g ;
	$text =~ s/\\"/\"/g ;
	
	return $text ;
}

# language dependent stuff =================================================================================

sub getLanguageVariables($$) {
	
	my $fileName = shift ;
	my $categoryPrefix = shift ;
	my $languageCode = shift ;
	
	open(LANGUAGES, $fileName) or die "Could not locate language definition file '$fileName'" ;
	binmode(LANGUAGES, ':utf8') ;
		
	my $languageData = "" ;
		
	while (defined (my $line = <LANGUAGES>)) { 
		$languageData = "$languageData$line" ;
	}
	
	close(LANGUAGES) ;
		
	while($languageData =~ m/<Language(.*?)>(.*?)<\/Language>/gis) {
		
		my $attributes = $1 ;
		my $content = $2 ;
			
		my $code ;
		if ($attributes =~ m/code\s*=\s*\"(.*?)\"/i) {
			$code = $1 ;
		}
			
		if (defined $code and $code eq $languageCode) { 
		
			my $name ;
			if ($attributes =~ m/name\s*=\s*\"(.*?)\"/i) {
				$name = $1 ;
			}
		
			my $root_category ;
			if ($content =~ m/<RootCategory>(.*?)<\/RootCategory>/i) {
				$root_category = $1 ;
			}
			
			my @disambig_categories = () ;
			while($content =~ m/<DisambiguationCategory>(.*?)<\/DisambiguationCategory>/gis) {
				push(@disambig_categories, $1) ;
			}
			
			my @disambig_templates = () ;
			while($content =~ m/<DisambiguationTemplate>(.*?)<\/DisambiguationTemplate>/gis) {
				push(@disambig_templates, $1) ;
			}
			
			if (not defined $name or not defined $root_category or not @disambig_categories or not @disambig_templates) {
				die "language definition for $code is not valid" ;
			}
			
			my @array = () ;
			
			push (@array, $name) ;
			push (@array, $root_category) ;
			
			my $dt_test  ;
			if (scalar @disambig_templates == 1) {
				$dt_test = $disambig_templates[0] ;
			}else {
				$dt_test = "(".join("|", @disambig_templates).")" ;
			}
			$dt_test = "\\{\\{".lc($dt_test)."\\}\\}" ;
			push (@array, $dt_test) ;		
		
			my $dc_test = join("|", @disambig_categories) ;	
			if (scalar @disambig_categories == 1) {
				$dc_test = $disambig_categories[0] ;
			}else {
				$dc_test = "(".join("|", @disambig_categories).")" ;
			}
			$dc_test = "\\[\\[".$categoryPrefix.":".lc($dc_test)."\\]\\]" ;
			push (@array, $dc_test) ;
			
			return @array ;
		}
	}
	die "could not find '$languageCode' language definition." ;
}



sub getLanguageCode {
		
	my $dump_name = fileparse(shift);
				
	if ($dump_name =~ m/^(.*?)wiki-(.*?)-pages-articles.xml$/i) {
		return $1 ;
	}
		
	print "Could not determine the language of this dump from it's filename, so we will assume it is an English one.\n" ;
	return "en" ;
}
