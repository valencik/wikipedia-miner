#!/usr/bin/perl -w

=head1 NAME

ExtractWikipediaData - Perl script for extracting useful summaries from a Wikipedia dump 

=head1 DESCRIPTION

describe how to call...

describe extracted files...
	
=cut
	
use strict ;

use Parse::MediaWikiDump;	
use Getopt::Long ;
use File::Basename;

use ProgressMonitor ;
use Stripper ;


binmode(STDOUT, ':utf8');





#get options ===============================================================================================

my $passes = 2 ; 
my $log ;
my $languageFile ;


GetOptions("passes=i"=>\$passes, 'log' => \$log, "languageFile=s"=>\$languageFile) ;

print " - data will be split into $passes passes for memory-intesive operations. Try using more passes if you run into problems.\n" ;

if ($log) {
	print " - problems will be logged to \"log.txt\" and \"strippingLog.txt\" in the data directory you specified\n" ;
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
	open (LOG, "> $data_dir/log.txt") or die "'$data_dir' is not writable. \n" ;
	binmode(LOG, ':utf8');

	Stripper::setLogfile("$data_dir/strippingLog.txt") ;
}

# get progress =========================================================================================	
	
my $progress = 0 ;
my $progressFile = "$data_dir/progress.csv" ;
	
if (-e $progressFile) {	
	open (PROGRESS, $progressFile) ;
		
	foreach (<PROGRESS>) {
		$progress = $_ ;
	}
	close PROGRESS ;
}


# now actually do the work! ============================================================================	


extractPageSummary() ;
extractRedirectSummary() ;
extractCoreSummaries() ;
print ("done!\n") ;

	


# page summary =========================================================================================

#my @ids = () ;		  #ordered array of page ids
my %pages_ns0 = () ;	#case normalized title -> id
my %pages_ns14 = () ;   #case normalized title -> id
	
sub extractPageSummary {
	if ($progress >= 1) {
		readPageSummaryFromCsv() ;
	} else {
		extractPageSummaryFromDump();
		$progress = 1 ;
		save_progress() ;
	}
}


sub readPageSummaryFromCsv {
	
	my $pm = ProgressMonitor->new(-s "$data_dir/page.csv", "reading page summary from csv file") ;
	my $parts_done = 0 ;

	open(PAGE, "$data_dir/page.csv") ;
	binmode (PAGE, ':utf8') ;
	
	while (defined (my $line = <PAGE>)) {
		$parts_done = $parts_done + length $line ;	
		chomp($line) ;
	
		if ($line =~ m/^(\d+),\"(.+)\",(\d+)$/) {
			
			my $page_id = int $1 ;
			my $page_title = unescape($2) ;
			my $page_type = int $3 ;
			
			
			
			#$page_title = decode_utf8($page_title) ;
			my $normalizedTitle = normalizeTitle($page_title) ;
			
			#print("$page_id ; $normalizedTitle ; $page_type\n") ;
			
			#push(@ids, $page_id) ;
			
			
			if ($page_type == 2) {
				$pages_ns14{$normalizedTitle} = $page_id ;
			} else {
				my $existing_id = $pages_ns0{$normalizedTitle} ;
				
				if (defined $existing_id) {
					if ($page_type != 3) {
						# only replace with non-redirect
						$pages_ns0{$normalizedTitle} = $page_id ; 
					}
				} else {
					$pages_ns0{$normalizedTitle} = $page_id ;
				}
			}
		} else {
			logProblem("\"$line\" does not match our expected format for lines in \"page.csv\"") ;			
		}
		
		$pm->update($parts_done) ;
	}	
		
	close PAGE ;
	$pm->done() ;
}
	

sub extractPageSummaryFromDump() {
	
	my $pm = ProgressMonitor->new(-s $dump_file, "extracting page summary from dump file") ;

	open (PAGE, "> $data_dir/page.csv") ;
	binmode (PAGE, ':utf8') ;

	my $article_count = 0 ;
	my $redirect_count = 0 ;
	my $category_count = 0 ;
	my $disambig_count = 0 ;

	my $pages = Parse::MediaWikiDump::Pages->new($dump_file) ;
	my $page ;

	while(defined($page = $pages->next())) {
	
		$pm->update($pages->current_byte) ;

		my $id = int($page->id) ;
		my $title = $page->title ;
		my $text = $page->text ;
		$text = lc($$text) ;

		my $namespace = $page->namespace;
		my $namespace_key = $namespaces{lc($namespace)} ;
   
		# check if namespace is valid
		if ($page->namespace ne "" && defined $namespace_key) {
			$title = substr $title, (length $page->namespace) + 1;
		} else {
			$namespace = "" ;
			$namespace_key = 0 ;
		}
	
		#identify the type of the page (1=Article,2=Category,3=Redirect,4=Disambig)
		my $type ;
		if ($namespace_key == 0) {
			if (defined $page->redirect) {
				$type = 3 ;
				$redirect_count ++ ;
			} else {
				if($text =~ m/$dt_test/ or $text =~ m/$dc_test/) {
					$type = 4 ;
					$disambig_count ++ ;
				} else {
					$type = 1 ;
					$article_count ++ ;
				}
			}
		}
		if ($namespace_key ==14) {
			if (defined $page->redirect) {
				$type = 3 ;
				$redirect_count ++ ;
			} else {
				$type = 2 ;
				$category_count ++ ;
			}
		}
	   	if (defined $type) {
	   		
			my $normalizedTitle = normalizeTitle($title) ;
	
			if ($namespace_key==0) {
				my $existing_id = $pages_ns0{$normalizedTitle} ;
				if (defined $existing_id) {
					# we have a collision
					logProblem("page $id:$title collides with existing id:$existing_id") ;

					if ($type != 3) {
						# only replace with non-redirect
						$pages_ns0{$normalizedTitle} = $id ; 
					}
				} else {
					$pages_ns0{$normalizedTitle} = $id ;
				}
			} else {
				my $existing_id = $pages_ns14{$normalizedTitle} ;
				if (defined $existing_id) {
					#we have a collision
					logProblem("page $id:$title collides with existing id:$existing_id") ;
										
					if ($type != 3) {
						# only replace with non-redirect
						$pages_ns14{$normalizedTitle} = $id ; 
					}
				} else {
					$pages_ns14{$normalizedTitle} = $id ;
				}
			}
			
			#save original title
			print PAGE "$id,\"".escape($title)."\",$type\n" ;
		}
	}

	open (STATS, "> $data_dir/stats.csv") ;
	print STATS "$article_count,$category_count,$redirect_count,$disambig_count\n" ;
	close STATS ;
 
	close PAGE ;
	
	$pm->done() ;
}


# redirect summary =========================================================================================

my %redirects = () ;	#from_id -> to_id

sub extractRedirectSummary {
		
	if ($progress >= 2) {
		readRedirectSummaryFromCsv() ;
	} else {
		extractRedirectSummaryFromDump();
		$progress = 2 ;
		save_progress() ;
	}
}
	
sub readRedirectSummaryFromCsv {
	
	my $pm = ProgressMonitor->new(-s "$data_dir/redirect.csv", "reading redirect summary from csv file") ;
	my $parts_done = 0 ;
	
	open(REDIRECT, "$data_dir/redirect.csv") ;
	
	while (defined (my $line = <REDIRECT>)) {
		$parts_done = $parts_done + length $line ;	
		chomp($line) ;
	
		if ($line =~ m/^(\d+),(\d+)$/) {
			my $rd_from = int $1 ;
			my $rd_to = int $2 ;
			$redirects{$rd_from} = $rd_to ;
		}
		$pm->update($parts_done) ;
	}	
	close REDIRECT ;
	$pm->done() ;
}
	
sub extractRedirectSummaryFromDump {
	
	my $pm = ProgressMonitor->new(-s $dump_file, "extracting redirect summary from dump file") ;

	open (REDIRECT, "> $data_dir/redirect.csv") ;

	my $pages = Parse::MediaWikiDump::Pages->new($dump_file) ;
	my $page ;

	while(defined($page = $pages->next())) {

		$pm->update($pages->current_byte) ;

		my $id = int($page->id) ;
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
	
		if ($namespace_key==0 or $namespace_key==14) {

			if (defined $page->redirect) {
				my $link_markup = $page->redirect ;
				my $target_lang ="";
				
				if ($link_markup =~ m/^([a-z]{1}.+?):(.+)/) {
					#check that someone hasnt put a valid namespace here
					if (not defined $namespaces{lc($1)}) {
						$target_lang = $1 ;
						$link_markup = $2 ;
					}
				}
				
				$link_markup =~ s/^\:// ;
		
				my $target_namespace = "" ; 
				my $target_ns_key = 0 ;
				if ($link_markup =~ m/^(.+?):(.+)/) {
					$target_namespace = lc($1) ;
					$target_ns_key = $namespaces{$target_namespace} ;
					if (defined $target_ns_key) {
						$target_namespace = lc($1) ;
						$link_markup = $2 ;
					} else {
						$target_namespace = "" ;
						$target_ns_key = 0 ;
					}
				}
				
				$link_markup =~ s/^\:// ;

				my $target_title = "";
				if ($link_markup =~ m/^(.+?)\|(.+)/) {
					$target_title = normalizeTitle($1) ;
				} else {
					$target_title = normalizeTitle($link_markup) ;
				}
			
				my $target_id ;
				if ($target_ns_key == 0) {
					$target_id = $pages_ns0{$target_title} ;
				}
		
				if ($target_ns_key == 14) {
					$target_id = $pages_ns14{$target_title} ;
				}
			
				if ($target_ns_key == 0 || $target_ns_key == 14) {
					if (defined($target_id)) {
						$redirects{$id} = $target_id ;
						print REDIRECT "$id,$target_id\n" ;
					} else {
						if ($log) {
							print LOG "Could not resolve redirect from $id:$title to $target_title in namespace $target_ns_key\n";
						}
					}
				}
			}
		} 
  	}

	$pm->done() ;
	
	close REDIRECT ;
	
	$progress = 2 ;
	save_progress() ;
}


# other core tables =============================================================================================================	
	
sub extractCoreSummaries {
	if ($progress < 3) {
		extractCoreSummariesFromDump();
		$progress = 3 ;
		save_progress() ;
	}
}
	
sub extractCoreSummariesFromDump {
	
	print("extracting core summaries from dump file\n") ;
	
	open (PAGELINK, "> $data_dir/pagelink.csv") ;
	open (CATLINK, "> $data_dir/categorylink.csv") ;
	open (TRANSLATION, "> $data_dir/translation.csv") ;
	binmode(TRANSLATION, ':utf8') ;
	open (EQUIVALENCE, "> $data_dir/equivalence.csv") ;
	open(ANCHOR, "> $data_dir/anchor.csv") ;
	binmode(ANCHOR, ':utf8');
	
	for (my $pass = 0 ; $pass<$passes ; $pass++) {
		
			
		my $pm = ProgressMonitor->new(-s $dump_file, " - pass ".($pass+1)." of $passes") ;	
	
		my $pages = Parse::MediaWikiDump::Pages->new($dump_file);
		my $page ;
				
		my %anchors = () ; 
		my $anchorDestCount = 0 ;
	
		while(defined($page = $pages->next())) {
		
			$pm->update($pages->current_byte) ;
				
			my $id = int($page->id) ;
			my $title = $page->title ;
			
			Stripper::setCurrDoc("$id:$title") ;
		
		
			my $namespace = $page->namespace;
			my $namespace_key = $namespaces{lc($namespace)} ;
			
			my %seenAnchors = () ;
			   
			# check if namespace is valid
			if ($page->namespace ne "" && defined $namespace_key) {
				$title = substr $title, (length $page->namespace) + 1;
			} else {
				$namespace = "" ;
				$namespace_key = 0 ;
			}

			if ($namespace_key==14 && $pass==0) {
				#find this category's equivalent article
				my $equivalent_id = resolve_link(normalizeTitle($title), 0) ;
	
				if (defined $equivalent_id) {
					print EQUIVALENCE "$id,$equivalent_id\n" ;
				}
			}
	
			if (($namespace_key==0 or $namespace_key==14) and not defined $page->redirect) {
						
				my $textRef = $page->text ;	
				my $text = $$textRef ;
				
				my $stripped_text = Stripper::stripAllButLinks($text, " ") ;
				if (length($stripped_text) != length($text)) {
					logProblem("Stripped version of $id:$title is not the same length as the original") ;
				}
	
				#gather links
				my @linkRegions = Stripper::gatherInternalLinks(\$stripped_text) ;
				@linkRegions = Stripper::collapseRegionList(\@linkRegions) ;
				
				my $i = @linkRegions ;
		
				for (my $i=0 ; $i<scalar(@linkRegions) ; $i++) {
					
					my $start = $linkRegions[$i][0] ;
					my $end = $linkRegions[$i][1] ;
							
					my $link_markup = substr($stripped_text, $start+2, ($end-$start)-4) ;
								
					my $target_lang ="";
					if ($link_markup =~ m/^([a-z]{1}.+?):(.+)/) {
						#check that someone hasnt put a valid namespace here
						if (not defined $namespaces{lc($1)}) {
							$target_lang = $1 ;
							$link_markup = $2 ;
						}
					}
		
					my $target_namespace = "" ; 
					my $target_ns_key = 0 ;
					if ($link_markup =~ m/^(.+?):(.+)/) {
						$target_namespace = lc($1) ;
						$target_ns_key = $namespaces{$target_namespace} ;
						if (defined $target_ns_key) {
							$target_namespace = lc($1) ;
							$link_markup = $2 ;
						} else {
							$target_namespace = "" ;
							$target_ns_key = 0 ;
						}
					}
		
					my $target_title = "";
					my $anchor_text = "" ;
					if ($link_markup =~ m/^(.+?)\|(.+)/) {
						$target_title = normalizeTitle($1) ;
						$anchor_text = $2 ;
					} else {
						$target_title = normalizeTitle($link_markup) ;
						$anchor_text = $link_markup ;
					}
	
					if ($target_lang ne "") {
						
						if ($pass == 0) {
							print TRANSLATION "$id,\"".escape($target_lang)."\",\"".escape($target_title)."\"\n" ;
						}
					} else {
						if ($target_ns_key==0) {
							#page link
							my $target_id = resolve_link($target_title, $target_ns_key) ;
							
							if (defined $target_id) {
								
								if ($pass == 0) {
									print PAGELINK "$id,$target_id,$start\n" ;
								}
	
								if ($target_id % $passes == $pass) {
	
									#save this anchor:dest combination as a three element array, with total count as first element, distinct count as 2nd element and flag (0) as third
									my $ref = $anchors{"$anchor_text:$target_id"} ;
									my @array ;
						
									if (defined $ref) {
										@array = @{$ref} ;
									}else {
										@array = (0,0,0) ;
										$anchorDestCount ++ ;
									}
	 
		 							#increment total count
			 						$array[0] = $array[0] + 1 ;
			 						
			 						if (not $seenAnchors{"$anchor_text:$target_id"}) {
			 							#increment distinct count
			 							$array[1] = $array[1] + 1 ;
			 							$seenAnchors{"$anchor_text:$target_id"} = 1 ;
			 						}
			 
									$anchors{"$anchor_text:$target_id"} = \@array ;
								}
								
							} else {
								logProblem("could not resolve page link to $target_title") ;
							}
						}
				
						if ($target_ns_key==14 && $pass==0) {
							#category link
							my $parent_id = resolve_link($target_title, $target_ns_key) ;
				
							if (defined $parent_id) {
								print CATLINK "$parent_id,$id\n" ;
							} else {
								logProblem("could not resolve category link to $target_title") ;
							}
						}
					
					} 
				}
			}
		}
	
		$pm->done() ;
	
		#now flag any anchor:dest combinations that are mirrored by redirects or article titles, and add titles and redirects if they havent been used as anchors yet.
		
		$pm = ProgressMonitor->new(-s "$data_dir/page.csv", " - - adding titles and redirects to anchor summary") ;
		my $parts_done = 0 ;
		
		open (PAGE, "$data_dir/page.csv") ;
		binmode (PAGE, ':utf8') ;
		
		while (defined (my $line = <PAGE>)) {
			$parts_done = $parts_done + length $line ;	
			chomp($line) ;
		
			if ($line =~ m/^(\d+),\"(.+)\",(\d+)$/) {
				
				my $page_id = int $1 ;				
				my $page_title = $2 ;
				my $page_type = int $3 ;				
					
				my $flag = 0 ;
					
				if ($page_type == 3) {
					#this is a redirect, need to resolve it
					
					my %redirects_seen = () ;
					while (defined($page_id) and defined($redirects{$page_id})){
								
						if (defined $redirects_seen{$page_id}) {
							$page_id = undef ;
							last ;
						} else {
							$redirects_seen{$page_id} = 1 ;
							$page_id = $redirects{$page_id} ;
						}
					}
					
					if (defined $page_id) {
						$flag = 1 ;				
					}
				}
						
				if ($page_type == 1) {
					#this is a page title
					$flag = 2 ;
				}
				
					
				if ($flag > 0 && $page_id % $passes == $pass) {
					my $ref = $anchors{"$page_title:$page_id"} ;
					my @array ;
						
					if (defined $ref) {
						#this has already been used as an anchor, needs to be flagged.
						@array = @{$ref} ;
						$array[2] = $flag ;					
					}else {
						#this has never been used as an anchor, needs to be added.
						@array = (0,0,$flag) ;
						$anchorDestCount++ ;
					}
		 
		 			$anchors{"$page_title:$page_id"} = \@array ;
				}
			}
			$pm->update($parts_done) ;
		}
		
		$pm->done() ;
		close PAGE ;
	
		#now we need to save the anchors we have gathered
		$pm = ProgressMonitor->new($anchorDestCount, " - - saving anchors") ;
			
		while (my ($key, $ref) = each(%anchors)) {
			$parts_done++ ;
		
			if ($key =~ m/(.+?):(\d+)/) {
				my $anchor = $1 ;
				my $target_id = $2 ;
				
				my @array = @{$ref} ;
				print ANCHOR "\"".escape($anchor)."\",$target_id,$array[0],$array[1],$array[2]\n" ;
			}
			$pm->update() ; 
		}
		$pm->done() ;
	}
	
	
	
	
	close PAGELINK ;
	close CATLINK ;
	close TRANSLATION ;
	close EQUIVALENCE ;
	close(ANCHOR) ;	
}
	





















sub save_progress() {
	open (PROGRESS, "> $progressFile") ;
	print PROGRESS $progress ;
	close PROGRESS ;	
}


# text cleaning stuff ======================================================================================

sub normalizeAnchor {
	my $anchor = shift ;
	
	$anchor =~ s/\s{2,}/ /g;  			#collapse multiple spaces
	$anchor =~ s/^\s+|\s+$//g;  			#remove leading & trailing spaces
	
	return $anchor;
}



# normalizes the given page title so that it will be matched to entries saved in the page table 
sub normalizeTitle {  
	my $title = shift ;
		
	$title =~ s/_+/ /g; 				#replace underscores with spaces
	$title =~ s/\s{2,}/ /g;  			#collapse multiple spaces
	$title =~ s/\#.+//; 				#remove page-internal part of link (the bit after the #)
	$title =~ s/^\s+|\s+$//g;  			#remove leading & trailing spaces
	
	$title =~ s/^(\w)/\u$1/g;		#make first letter of first word uppercase
	
	return $title;
}


sub resolve_link {
    my $title = shift ;
    my $namespace = shift ;

    #print " - resolving link $namespace:$title\n" ;

    my $target_id ;

    if ($namespace == 0) {
		$target_id = $pages_ns0{$title} ;
    }

    if ($namespace == 14) {
		$target_id = $pages_ns14{$title} ;
    }

    my %redirects_seen = () ;
    while (defined($target_id) and defined($redirects{$target_id})){
			#print " - - redirect $target_id\n" ;
			if (defined $redirects_seen{$target_id}) {
			    #seen this before, so cant resolve this loop of redirects
			    last ;
			} else {
			    $redirects_seen{$target_id} = 1 ;
			    $target_id = $redirects{$target_id} ;
			}
    }
    return $target_id ;
}

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

sub logProblem {
	
	my $message = shift ;
	
	if ($log) {
		print LOG "$message\n" ;		
	}	
}
