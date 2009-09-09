#!/usr/bin/perl

=head1 NAME

Stripper - Perl module identifying and discarding regions in MediaWiki markup.

=head1 SYNOPSIS

  use Stripper;
  my $markup = "'''MediaWiki''' is a [[World Wide Web|web-based]] [[wiki software]] application used by all projects of the [[Wikimedia Foundation]].";
  my $cleanText = Stripper::stripToPlainText($markup) ;
  print $cleanText ; # outputs "MediaWiki is a web-based wiki software application used by all projects of the Wikimedia Foundation."

=head1 DESCRIPTION

This module provides various methods for identifying and discarding certian regions of MediaWiki markup (such as templates, tables, links, references, etc) with the aim of returning it to plain text. 
The methods for detecting regions are designed to handle nesting. The methods for discarding regions can optionally perserve space, so that the length of the string and the locations of unstripped characters is not effected. 
None of these methods modify the input string in any way. 

By default, the stripper will not inform you if it encounters a problem in the markup (e.g. a template that starts but is not finished). You can modify this by specifying a file to log problems to. 

=head2 Functions

=cut




package Stripper;

use strict ;

my $logFile ;
my $currDoc ;
my $currItem ;




=head3 Logging functions

=over 4




=item * setLogfile($pathToLogFile)

Specifies the file for logging problems that occur in the markup. The file's 
content will be overwritten. Death will occur if the file is not writable.

=cut

sub setLogfile {
	$logFile = shift ;	
	
	open (LOG, "> $logFile") or die("$logFile is not writable");	
	binmode LOG, ':utf8';
}


=item * setCurrDoc($documentIdentifier)

Specifies and identifier for the document that is currently being processed. This is only used 
to make the log file more informative. 

=cut

sub setCurrDoc {
	$currDoc = shift ;	
}

=item * setCurrDoc($documentIdentifier)

Returns the idenifier for the document that is currently being processed. 

=cut

sub getCurrDoc {
	return $currDoc ;	
}

#======================================================================================================

=back

=head3 Stripping functions

=over 4

=item * stripAllButLinks($text)

Returns a copy of the given text, where all markup has been removed except for 
internal links to other wikipedia pages (e.g. to articles or categories), section 
headers, list markers, and bold/italic markers. 

By default, unwanted text is completely discarded. You can optionally specify 
a character to replace the regions that are discared, so that the length of the 
string and the locations of unstripped characters is not modified.

=cut

sub stripAllButLinks {
	
	my $text = shift ;
	my $replacementChar = shift ;
	
	#deal with comments and math regions entirely seperately. 
	#Comments often contain poorly nested items that the remaining things will complain about.
	#Math regions contain items that look confusingly like templates.
	my @regions = gatherSimpleRegions(\$text, "\\<\\!--(.*?)--\\>") ;
	my @tmpRegions = gatherComplexRegions(\$text, "\\<math(\\s*?)([^>\\/]*?)\\>", "\\<\\/math(\\s*?)\\>") ;
	@regions = mergeRegionLists(\@regions, \@tmpRegions) ;
	my $clearedText = stripRegions(\$text, \@regions, $replacementChar) ;
	
	#deal with templates entirely seperately. They often end in |}} which confuses the gathering of tables.
	@regions = gatherTemplates(\$clearedText) ;
	$clearedText = stripRegions(\$clearedText, \@regions, $replacementChar) ;

	#now gather all of the other regions we want to ignore	
	@regions = gatherTables(\$clearedText) ;
	
	@tmpRegions = gatherHTML(\$clearedText) ;
	@regions = mergeRegionLists(\@regions, \@tmpRegions) ;
	
	@tmpRegions = gatherExternalLinks(\$clearedText) ;
	@regions = mergeRegionLists(\@regions, \@tmpRegions) ;
	
	@tmpRegions = gatherMagicWords(\$clearedText) ;
	@regions = mergeRegionLists(\@regions, \@tmpRegions) ;
	
	#ignore these regions now (they need to be blanked before we can correctly identify the remaining regions)
	$clearedText = stripRegions(\$clearedText, \@regions, $replacementChar) ;
	
	
	@regions = gatherMisformattedStarts(\$clearedText) ;
	
	#@tmpRegions = gatherEmphasis(\$clearedText) ;
	#@regions = mergeRegionLists(\@regions, \@tmpRegions) ;
	
	#@tmpRegions = gatherListAndIndentMarkers(\$clearedText) ;
	#@regions = mergeRegionLists(\@regions, \@tmpRegions) ;
	
	#ignore these regions as well. 
	$clearedText = stripRegions(\$clearedText, \@regions, $replacementChar) ;
	
	return $clearedText ;
}

=item * stripInternalLinks($text)

Returns a copy of the given text, where all links to wikipedia pages 
(categories, articles, etc) have been removed. Links to articles are 
replaced with the appropriate anchor text. All other links are removed completely.

By default, unwanted text is completely discarded. You can optionally specify 
a character to replace the regions that are discarded, so that the length of the 
string and the locations of unstripped characters is not modified.

=cut

sub stripInternalLinks {
	
	$currItem = "internal links" ;
		
	my $text = shift ;
	my $replacementChar = shift ;
	
	my @regions = gatherComplexRegions(\$text, "\\[\\[", "\\]\\]") ;
	
	my $strippedText = "" ;
	my $lastPos = length($text) ;
	
	#because regions are sorted by end position, we work backwards through them
	my $i = @regions ;
	
	while ($i > 0) {
		$i -- ;
		
		my $start = $regions[$i][0] ;
		my $end = $regions[$i][1] ;
				
		#only deal with this region is not within a region we have already delt with. 
		if ($start < $lastPos) {
			
			#copy everything between this region and start of last one we dealt with. 
			$strippedText = substr($text, $end, $lastPos-$end) . $strippedText ;
			
			my $linkMarkup = substr($text, $start, $end-$start) ;
					
			#print("link [$start,$end] = $linkMarkup\n\n") ;
					
			# by default (if anything goes wrong) we will keep the link as it is
			my $strippedLinkMarkup = $linkMarkup ;
					
			if ($linkMarkup =~ m/^\[\[(.*?:)?(.*?)(\|.*?)?\]\]$/s) {
						
				my $prefix = $1 ;
				my $dest = $2 ;
				my $anchor = $3 ;
				
				if (defined $prefix) {
					# this is not a link to another article, so get rid of it entirely
					if (defined $replacementChar && not $replacementChar eq "") {
						$strippedLinkMarkup = sprintf("%*s", $end-$start, '') ;			
					} else {
						$strippedLinkMarkup = "" ;
					}
				} else {
					if (defined $anchor) {
						#this has an anchor defined, so use that but blank out everything else
						
						if (defined $replacementChar && not $replacementChar eq "") {
							$strippedLinkMarkup = sprintf("%*s", 2+length($dest)+1, '') . substr($anchor, 1) . sprintf("%*s", 2, '') ;
						} else {
							$strippedLinkMarkup = substr($anchor, 1)
						}
					} else {
						#this has no anchor defined, so treat dest as anchor and blank out everything else

						if (defined $replacementChar && not $replacementChar eq "") {
							$strippedLinkMarkup = sprintf("%*s", 2, '') . $dest . sprintf("%*s", 2, '') ;
						} else {
							$strippedLinkMarkup = $dest ;
						}
					}
				}
				
				if (defined $replacementChar and not $replacementChar eq "" and not $replacementChar eq " ") {
					$strippedLinkMarkup =~ s/ /$replacementChar/g ;					
				}
				
			} else {
				logProblem("our pattern for delimiting links has a problem") ;
			}
			
			$strippedText = $strippedLinkMarkup . $strippedText ;
			$lastPos = $start ;
		}
 	}	
 	
 	if ($lastPos > 0) {
 		$strippedText = substr($text, 0, $lastPos) . $strippedText ;
 	}
		
	return $strippedText ; 	
}



=item * stripNonArticleInternalLinks($text)

Returns a copy of the given text, where all links to wikipedia pages 
that are not articles (categories, language links, etc) have been removed. 

By default, unwanted text is completely discarded. You can optionally specify 
a character to replace the regions that are discarded, so that the length of the 
string and the locations of unstripped characters is not modified.

=cut

sub stripNonArticleInternalLinks {
	
	$currItem = "non-article internal links" ;
		
	my $text = shift ;
	my $replacementChar = shift ;
	
	my @regions = gatherComplexRegions(\$text, "\\[\\[", "\\]\\]") ;
	
	my $strippedText = "" ;
	my $lastPos = length($text) ;
	
	#because regions are sorted by end position, we work backwards through them
	my $i = @regions ;
	
	while ($i > 0) {
		$i -- ;
		
		my $start = $regions[$i][0] ;
		my $end = $regions[$i][1] ;
				
		#only deal with this region is not within a region we have already delt with. 
		if ($start < $lastPos) {
			
			#copy everything between this region and start of last one we dealt with. 
			$strippedText = substr($text, $end, $lastPos-$end) . $strippedText ;
			
			my $linkMarkup = substr($text, $start, $end-$start) ;
					
			#print("link [$start,$end] = $linkMarkup\n\n") ;
					
			# by default (if anything goes wrong) we will keep the link as it is
			my $strippedLinkMarkup = $linkMarkup ;
					
			if ($linkMarkup =~ m/^\[\[(.*?:)?(.*?)(\|.*?)?\]\]$/s) {
						
				my $prefix = $1 ;
				my $dest = $2 ;
				my $anchor = $3 ;
				
				if (defined $prefix) {
					# this is not a link to another article, so get rid of it entirely
					if (defined $replacementChar && not $replacementChar eq "") {
						$strippedLinkMarkup = sprintf("%*s", $end-$start, '') ;			
					} else {
						$strippedLinkMarkup = "" ;
					}
				} 
				
				if (defined $replacementChar and not $replacementChar eq "" and not $replacementChar eq " ") {
					$strippedLinkMarkup =~ s/ /$replacementChar/g ;					
				}
				
			} else {
				logProblem("our pattern for delimiting links has a problem") ;
			}
			
			$strippedText = $strippedLinkMarkup . $strippedText ;
			$lastPos = $start ;
		}
 	}	
 	
 	if ($lastPos > 0) {
 		$strippedText = substr($text, 0, $lastPos) . $strippedText ;
 	}
		
	return $strippedText ; 	
}



=item * stripToPlainText($text)

Convenience method which combines both of the above methods - i.e. returns a copy of the 
given text, where all markup has been removed except for section headers and list markers. 

By default, unwanted text is completely discarded. You can optionally specify 
a character to replace the regions that are discared, so that the length of the 
string and the locations of unstripped characters is not modified.

=cut

sub stripToPlainText {
	
	my $text = shift ;
	my $replacementChar = shift ;
	
	my $clearedText = stripAllButLinks($text, $replacementChar) ;
	$clearedText = stripInternalLinks($clearedText, $replacementChar) ;
	
	return $clearedText ;	
}


=item * stripRegions($text,$regionsRef)

Returns a copy of the given text, where the given regions have been removed. Regions are identified using
one of the gather methods discussed in the next section. 

By default, unwanted text is completely discarded. You can optionally specify 
a character to replace the regions that are discared, so that the length of the 
string and the locations of unstripped characters is not modified.

=cut

sub stripRegions {
	
	my $textRef = shift ;
	my $text = $$textRef ;
	
	my $regionsRef = shift ;
	my @regions = @$regionsRef ;
	
	my $replacementChar = shift ;
	
	my $clearedText = "" ;
	
	my $lastPos = length($text) ;
	
	#because regions are sorted by end position, we work backwards through them
	my $i = @regions ;
	
	while ($i > 0) {
		$i -- ;
		
		my $start = $regions[$i][0] ;
		my $end = $regions[$i][1] ;
		
		#print (" - looking at region [$start,$end]\n") ;
		
		#only deal with this region is not within a region we have already delt with. 
		
		if ($start < $lastPos) {
			
			#print (" - - dealing with it\n") ;
			
			#copy text after this region and before beginning of the last region we delt with
			if ($end < $lastPos) {
				$clearedText = substr($text, $end, $lastPos-$end) . $clearedText ;
			}
			
			if (defined $replacementChar && not $replacementChar eq "") {
				my $fill = sprintf("%*s", $end-$start, '') ;
				if (not $replacementChar eq " ") {
					$fill =~ s/ /$replacementChar/g ;					
				}
				$clearedText = $fill . $clearedText ;
			}
			
			$lastPos = $start ;
		} else {
			#print (" - - already dealt with\n") ;
			
		}
	}
	
	$clearedText = substr($text, 0, $lastPos) . $clearedText ;
	
	return $clearedText ;
}


#======================================================================================================

=back 

=head3 Gathering functions

=over 4


=item * gatherInternalLinks($textRef)

Gathers areas within the text which correspond to links to other wikipedia pages
(as identified by [[ and ]] pairs). 

=cut

sub gatherInternalLinks {
	$currItem = "internal links" ;
	
	my @regions = gatherComplexRegions(shift, "\\[\\[", "\\]\\]") ;
	
	return @regions ;
}


=item * gatherTemplates($textRef)

Gathers areas within the text which correspond to templates (as identified by {{ and }} pairs). 

=cut

sub gatherTemplates {
	$currItem = "templates" ;
	my @regions = gatherComplexRegions(shift, "{{", "}}") ;
		
	return @regions ;
}

=item * gatherTables($textRef)

Gathers areas within the text which correspond to tables (as identified by {| and |} pairs). 

=cut

sub gatherTables {
	$currItem = "tables" ;
	my @regions = gatherComplexRegions(shift, "\\{\\|", "\\|\\}") ;
	#printRegionList(\@regions) ;
	
	return @regions ;
}

=item * gatherHTML($textRef)

Gathers areas within the text which correspond to html tags. 
DIV and REF regions will enclose beginning and ending tags, and everything in between, 
since we assume this content is supposed to be discarded. All other regions will only include the
individual tag, since we assume the content between such pairs is supposed to be retained. 

=cut

sub gatherHTML {
	
	$currItem = "html" ;
	
	my $textRef = shift ;
	
	#gather and merge references
	my @regions = gatherReferences($textRef, "\\<(.*?)\\>") ;
	
	#gather <div> </div> pairs
	my @tmpRegions = gatherComplexRegions($textRef, "\\<div(\\s*?)([^>\\/]*?)\\>", "\\<\\/div(\\s*?)\\>") ;
	@regions = mergeRegionLists(\@regions, \@tmpRegions) ;
	
	#gather remaining tags
	@tmpRegions = gatherSimpleRegions($textRef, "\\<(.*?)\\>") ;
	@regions = mergeRegionLists(\@regions, \@tmpRegions) ;
	
	return @regions ;
}


=item * gatherReferences($textRef)

Gathers areas within the text which correspond to references (text to support claims or facts).
The regions will enclose beginning and ending tags, and everything in between, 
since we assume this content is supposed to be discarded. 

=cut

sub gatherReferences {
	
	$currItem = "references" ;

	my $textRef = shift ;
	
	#gather <ref/>
	my @regions = gatherSimpleRegions($textRef, "\\<ref(\\s*?)([^>]*?)\\/\\>") ;
	
	#gather <ref> </ref> pairs (these shouldnt be nested, but what the hell...)
	my @tmpRegions = gatherComplexRegions($textRef, "\\<ref(\\s*?)([^>\\/]*?)\\>", "\\<\\/ref(\\s*?)\\>") ;
	@regions = mergeRegionLists(\@regions, \@tmpRegions) ;
	
	return @regions ;
}


=item * gatherMagicWords($textRef)

Gathers items which MediaWiki documentation mysteriously refers to as "majic words": e.g. __NOTOC__

=cut

sub gatherMagicWords {
	
	$currItem = "magic words" ;

	return gatherSimpleRegions(shift, "\\_\\_([A-Z]+)\\_\\_") ;
}

=item * gatherExternalLinks($textRef)

Gathers all links to external web pages

=cut

sub gatherExternalLinks {

	$currItem = "external links" ;
	
	return gatherSimpleRegions(shift, "\\[(http|www|ftp).*?\\]") ;
}

=item * gatherEmphasis($textRef)

Gathers bold and italic markup

=cut

sub gatherEmphasis {
	$currItem = "emphasis" ;
	
	return gatherSimpleRegions(shift, "'{2,}") ; 
}

=item * gatherListAndIndentMarkers($textRef)

Gathers markup which indicates indented items, or numbered and unnumbered list items

=cut

sub gatherListAndIndentMarkers {
	$currItem = "list and intent markers" ;
	
	my $textRef = shift ;
		
	my @regions = gatherSimpleRegions($textRef, "\n( *)([#*:]+)") ;
	
	#increment start positions of all regions by one, so they don't include the newline character
	for (my $i=0 ; $i< @regions ; $i++) {
		$regions[$i][0]++ ;
	}
	
	#add occurance of list item on first line (if there is one)
	my @tmpRegions = gatherSimpleRegions($textRef, "^( *)([#*:]+)") ;
	@regions = mergeRegionLists(\@regions, \@tmpRegions) ;
	
	return @regions ;
}

=item * gatherMisformattedStarts($textRef)

Gathers paragraphs within the text referred to by the given pointer, which are at the 
start and begin with an indent. These correspond to quotes or disambiguation and navigation 
notes that the author should have used templates to identify, but didn't. This will only work 
after templates, and before list markers have been cleaned out.

=cut

sub gatherMisformattedStarts {
	
	$currItem = "starts" ;
	
	my $textRef = shift ;
	my $text = $$textRef ;
	
	my @lines = split("\n", $text) ;
	
	my $ignoreUntil = 0 ;
	
	for my $line (@lines) {
		
		#print(" - - '$line'\n") ;
		
		my $isWhitespace = ($line =~ m/^(\s*)$/) ;
		my $isIndented = ($line =~ m/^(\s*):/) ;
		#my $isItalicised = ($line =~ m/^(\s*)'{2,}(.*?)'{2,}(\s*)$/) ;
		
		if ($isWhitespace || $isIndented)  {
			#want to ignore this line
			$ignoreUntil = $ignoreUntil + length($line) + 1 ;	
			
			#print(" - - - discard\n") ;		
		} else {
			#print(" - - - keep\n") ;
			
			last ;
		}		
	}
	
	my @regions = () ;
	push(@regions, [0,$ignoreUntil]) ;
	
	return @regions ;
}
	

=item * gatherSimpleRegions($textRef, $regex)

Gathers simple regions: ones which cannot be nested within each other. 
The returned regions (an array of start and end positions) will be sorted 
by end position (and also by start position, since they can't overlap) 

=cut

sub gatherSimpleRegions {
	
	my $textRef = shift ;
	my $text = $$textRef ;
	
	my $regex = shift ;
	
	#an array of regions we have identified
	#each region is given as an array containing start and end character indexes of the region. 
	my @regions = () ;
	
	while($text =~ m/($regex)/gs) {
		
		my $p2 = pos($text) ;  
		my $p1 = $p2 - length($1) ;
		
		my @region = ($p1, $p2) ;
		push (@regions, [@region]) ;
	}
		
	return @regions ;
}


=item * gatherComplexRegions($textRef, $startRegex, $endRegex)

Gathers complex regions: ones which can potentially be nested within each other. 
The returned regions (an array of start and end positions) will be either 
non-overlapping or cleanly nested, and sorted by end position. 

=cut

sub gatherComplexRegions {
	
	my $textRef = shift ;
	my $text = $$textRef ;
	
	my $startRegex = shift ;
	my $endRegex = shift ;
	
	#an array of regions we have identified
	#each region is given as an array containing start and end character indexes of the region. 
	my @regions = () ;
	
	#a stack of region starting positions
	my @startStack = () ;
		
	while($text =~ m/(($startRegex)|($endRegex))/gs) {
			
		my $p2 = pos($text) ;  
		my $p1 = $p2 - length($1) ;
						
		if (defined $2) {
			#this is the start of an item
			push(@startStack, $p1) ;
		} else {
			#this is the end of an item
			if (@startStack) {
				my @region = (pop(@startStack), $p2) ;
				push (@regions, [@region]) ;
				
				#print (" - item [$region[0],$region[1]]: ".substr($text, $region[0], $region[1]-$region[0])."\n") ;
			} else {
				logProblem("oops, we found the end of an item, but have no idea where it started") ;
			}
		}
	}
	
	if (@startStack) {
		logProblem("oops, we got to the end of the text and still have items that have been started but not finished") ;
	}
		
	return @regions ;
}


=item * collapseRegionList($arrayRef) 

Collapses a region list, by discarding any regions which are contained within other regions.
The resulting region list will be non-overlapping and sorted by end positions.

=cut

sub collapseRegionList {
	
	my $ref = shift ;

	if (!defined $ref) {
		return ;
	}
	
	my @regions = @$ref ;
	my @newRegions = () ;	
	
	my $index = @regions -1 ;
	
	my $lastPos ;
	
	while ($index >= 0) {
		
		my $start = $regions[$index][0] ;
		my $end = $regions[$index][1] ;
		
		if (not defined $lastPos or $end <= $lastPos) {
			my @region = ($start, $end) ;
			
			unshift(@newRegions, [@region]) ;
			
			$lastPos = $start ;
		}
		
		
		$index-- ;
	}
	
	return @newRegions ;	
}
		




=item * mergeRegionLists($arrayRef1, $arrayRef2)

Merges two lists of regions into one sorted list. Regions that are contained 
within other regions are discarded.
The resulting region list will be non-overlapping and sorted by end positions.

=cut

sub mergeRegionLists {
	
	my $refA = shift ;
	my $refB = shift ;
		
	my @regionsA ;
	if (defined $refA) {
		@regionsA = @$refA ;
	} else {
		@regionsA = () ;
	}
	
	my @regionsB ;
	if (defined $refB) {
		@regionsB = @$refB ;
	} else {
		@regionsB = () ;
	}	
	
	my $indexA = @regionsA -1 ;
	my $indexB = @regionsB - 1;
	
	my @newRegions = () ;
	
	my $lastPos ;
	
	while ($indexA >= 0 and $indexB >= 0) {

		my $startA = $regionsA[$indexA][0] ;
		my $endA = $regionsA[$indexA][1] ;
		
		my $startB = $regionsB[$indexB][0] ;
		my $endB = $regionsB[$indexB][1] ;

		if (defined $lastPos and $startA >= $lastPos and $startB >= $lastPos) {
			#both of these are inside regions that we have already dealt with, so discard them
			$indexA-- ;
			$indexB-- ;
		} else {
			if ($endB > $endA) {
				
				#lets see if we need to copy B across
				if (($startB >= $startA && $endB <= $endA) || (defined $lastPos && $startB >= $lastPos)) {
					#either A or the last region we dealt with completely contains B, so we just discard B
				} else {
					#deal with B now
					unshift(@newRegions, [$startB, min($endB, $lastPos)]) ;
					$lastPos = $startB ;
				}
				
				$indexB-- ;				
			} else {
				
				#lets see if we need to copy A across
				
				if (($startA >= $startB && $endA <= $endB) || (defined $lastPos && $startA >= $lastPos)) {
					#either B or the last region we dealt with completely contains A, so we just discard A
				} else {
					#deal with A now
					unshift(@newRegions, [$startA, min($endA, $lastPos)]) ;
					$lastPos = $startA ;
				}
				
				$indexA-- ;	
			}
		}
	}
	
	#deal with any remaining A regions
	while ($indexA >= 0) {
			
		my $startA = $regionsA[$indexA][0] ;
		my $endA = $regionsA[$indexA][1] ;
					
		if (defined $lastPos and $startA > $lastPos) {
			#this is already covered, so ignore it
		} else {
			unshift(@newRegions, [$startA, min($endA, $lastPos)]) ;
			$lastPos = $startA ;
		}
			
		$indexA-- ;
	}
		
	#deal with any remaining B regions
	while ($indexB >= 0) {
			
		my $startB = $regionsB[$indexB][0] ;
		my $endB = $regionsB[$indexB][1] ;
					
		if (defined $lastPos and $startB > $lastPos) {
			#this is already covered, so ignore it
		} else {	
			unshift(@newRegions, [$startB, min($endB, $lastPos)]) ;
			$lastPos = $startB ;
		}
			
		$indexB-- ;
	}
		
	return @newRegions ;
}







## private stuff =====================================================================================

sub printRegionList {
	
	my $ref = shift ;
	my @regions = @$ref ;
	
	my $i=0 ;
	
	while ($i < scalar(@regions)) {
		print("[$regions[$i][0],$regions[$i][1]] ") ;
		$i++ ;		
	}
	
	print("\n") ;
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


sub setCurrItem {
	$currItem = shift ;	
}

sub logProblem {
	
	if (not defined $logFile) {
		return ;
	}
	
	my $msg = shift ;
	
	my $info ;
	
	if (defined $currItem) {
		$info = "looking for $currItem" ;
	}
	
	if (defined $currDoc) {
		$info = $info . " in $currDoc" ;
	}
	
	if (defined $info) {
		$msg = "$msg ($info)" ;		
	} 
	
	print LOG "$msg\n" ;
	
	#flush LOG, so we see message now
	select((select(LOG), $| = 1)[0]);
}


=back

=head1 AUTHOR

David Milne (d.n.milne@gmail.com)

=head1 COPYRIGHT

Copyright 2009 Waikato University.  All rights reserved.

This library is free software; you can redistribute it and/or
modify it under the same terms as Perl itself.

=cut


1;