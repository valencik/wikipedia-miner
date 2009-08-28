#!/usr/bin/perl -w

=head1 NAME

ExtractAnchorOccurrences - Perl script for extracting the number of times anchors occur in Wikipedia 

=head1 DESCRIPTION

TODO: describe how to call...

TODO: describe extracted files...
	
=cut
	
use strict ;

use Parse::MediaWikiDump;	
use Getopt::Long ;
use File::Basename;
use Encode ;

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
	die "You must specify currPass=<num> ; the index (a number between 1 and $passes) of the pass to perform.\n" ; 
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


# get the vocabulary of anchors that we are interested in ==============================================

my %anchorFreq = () ;			 #anchor text -> freq

open(ANCHOR, "$data_dir/anchor.csv") || die "'$data_dir/anchor.csv' could not be found. You must run extractWikipediaData.pl first!\n" ;
binmode(ANCHOR, ':utf8') ;

my $pm = ProgressMonitor->new(-s "$data_dir/anchor.csv", "Loading anchor vocabulary") ;
my $parts_done = 0 ;

while (defined(my $line=<ANCHOR>)) {
	$parts_done = $parts_done + length $line ;

	chomp($line) ;
		
	if ($line =~ m/(.+?),(\d+),(\d+)(,\d+)?/) {
		my $anchor = unescape($1) ;
		my $id = $2 ;
		my $count = $3 ;
		
		$anchor = decode_utf8($anchor) ;		
		$anchorFreq{$anchor} = 0 ;
	}
	$pm->update($parts_done) ;
}
close ANCHOR ;

$pm->done() ; 


# now measure how many wikipedia articles they are found in =============================================

$pm = ProgressMonitor->new(-s $dump_file, "Gathering anchor occurances") ;

my $pages = Parse::MediaWikiDump::Pages->new($dump_file) ;
my $page ;

while(defined($page = $pages->page)) {

	$pm->update($pages->current_byte) ;

	my $id = int($page->id) ;
	
	if ($id % $passes == $passIndex) {
		#only process pages that are valid for this pass
		
		my $title = $page->title ;
		
		my $namespace = $page->namespace;
		my $namespace_key = $namespaces{lc($namespace)} ;
			   
		# check if namespace is valid
		if ($page->namespace ne "" && defined $namespace_key) {
			$title = substr $title, (length $page->namespace) + 1;
		} else {
			$namespace = "" ;
			$namespace_key = 0 ;
		}
	
		#only process articles (and disambig pages)
		if ($namespace_key==0 and not defined $page->redirect) {
			
			#only interested in first ngram occurance in each document.
			my %ngrams_seen = () ;
			
			my $textRef = $page->text ;
			my $text = $$textRef ;
			
			$text = Stripper::stripToPlainText($text) ;
			
			#it is a little tricky to decide wheither an ngram can span a line break. 

			$text =~ s/\n\s*\n/\n\n/g ; #collapse whitespace if it is the only thing found between two linebreaks
			$text =~ s/(?<!\n)\n(?!\n)/ /g ;  #replace isolated line breaks with spaces			
			$text =~ s/ {2,}/ /g ;  #collapse multiple spaces
			
			#now process text one line at a time
			while($text =~ m/(.*?)\n/gi) {		
				gather_ngrams($1, \%ngrams_seen) ;	
			}
		}
	} 
}


sub gather_ngrams {
	
	my $text = shift ;
	$text = "\$ $text \$" ;
	
	my $ref = shift ;
	my %ngrams_seen = %$ref ;	
	
	#gather all positions where ngrams could possibly split on
	my @splits = () ;
	while ($text =~ m/([\s\{\}\(\)\[\]\<\>\\\"\'\.\,\!\;\:\-\_\#\@\%\^\&\*\~\|])/g) {	
		push(@splits, pos($text) -1) ;
	}
	
	for (my $i=0 ; $i<scalar(@splits) ; $i++) {

		my $startIndex = $splits[$i] + 1 ;
		
		for (my $j=min($i + $max_ngram_length, scalar(@splits)-1) ; $j > $i ; $j--) {
			my $currIndex = $splits[$j] ;	
			my $ngram = substr($text, $startIndex, $currIndex-$startIndex) ;
			$ngram =~ s/^\s+|\s+$//g;
			
			if ($ngram eq "") { 
				next ; 
			}
			
			if (length($ngram)==1 && substr($text, $startIndex-1, 1) eq "'") {
				next ;
			} 
			
			if (defined $ngrams_seen{$ngram}) {
				next ;
			}
		  	
		  	my $freq = $anchorFreq{$ngram} ;
			
		  	if (defined $freq) {
				$anchorFreq{$ngram} = $freq + 1 ;
		  	}
		  	
		  	$ngrams_seen{$ngram} = 1 ;
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


