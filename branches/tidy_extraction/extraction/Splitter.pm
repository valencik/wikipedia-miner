#!/usr/bin/perl

=head1 NAME

Splitter - Perl module for splitting MediaWiki markup into sections, paragraphs and sentences. 

=head1 SYNOPSIS

  use Splitter ;
  use Stripper ;
  
  Splitter::loadDictionary("splitter.dict") ;
  Splitter::loadAbbreviations("splitter.abv") ;
  
  my $text ; #declare the text to be split here - read it in from a file or something.
  
  my $structure = Splitter::getStructureString($text) ;
  print "Document Structure: $structure\n\n" ;
  
  my $markedText =  Splitter::markDocument($text, $structure) ;
  print "Marked Document:\n $markedText\n" ;

=head1 DESCRIPTION

This module provides methods for identifying section, paragraph and sentence splits in MediaWiki markup.

The sentence splitting functionality depends on Paul Clough's rule-based sentence 
splitter. This should have been included with this code. 

=head2 Functions

=over 4

=cut

package Splitter ;

use strict ;
use Stripper ;
use CloughSentenceSplitter ;



=item * loadDictionary($dictionaryFile)

Loads a dictionary of common terms. The dictionary should contain one term per line. 

=cut

sub loadDictionary {
	
	CloughSentenceSplitter::loadDictionary(shift) ;
}


=item * loadAbbreviations($abbreviationFile)

Loads a file of abbreviations. The file should contain one abbreviation per line. 

=cut

sub loadAbbreviations {
	
	CloughSentenceSplitter::loadAbbreviations(shift) ;
}


=item * getStructureString($text)

Returns a string representation of the given document's structure. This represents sections 
as ( ) pairs, which can be nested. Paragraphs and sentences are represented as 
[<num>,<num>,<num>], where the first num indicates the character offset of the start of
the paragraph, the last indicates the end of it, and the others indicate sentence breaks
within it.

Ideally, the document should have been processed beforehand using the Stripper to remove all
markup other than section headers, list markers and links between articles. If you want this 
structure string to be valid for the original, fully marked up document, then make sure the length of the 
string and the locations of unstripped characters is not modified by the Stripper 
(use a space as the replacement character).

=cut

sub getStructureString {
	
	my $text = shift ;
	my $lvl = shift ;
	my $offset = shift ;
	
	my $structureString = '' ;
	
	if (not defined $lvl) {
		$lvl = 1 ;
	}
	
	if (not defined $offset) {
		$offset = 0 ;
	}
	
	my $titleStart ;
	my $titleEnd ;
	
	my $contentStart = 0;
	my $contentEnd = length($text);
	
	my $start = substr($text, 0, 100) ;
	$start =~ s/\n/ /g ;
	
	#print "Section start: $start\n" ;
	
	if ($text =~ m/^\s*={2,}\s*(.+?)\s*={2,}\s*/) {
		
		$titleStart = $-[1] ;
		$titleEnd = $+[1] ;
		
		#print " - Section title: $1\n" ;
		
		$contentStart = $+[0] ;
		
		if ($text =~ m/\s+$/) {
			$contentEnd = $+[0] ;
		}
		
		
	} else {
		
		if ($text =~ m/^(\s+)/) {
			$contentStart = $+[0] ;
		}
		
		if ($text =~ m/(\s+)$/) {
			$contentEnd = $-[0] ;
		}
	}
	
	if (defined $titleStart) {
		#create a paragraph marker to show where to find section title
			
		$structureString = $structureString . "[" . ($offset + $titleStart) . "," . ($offset + $titleEnd) . "]" ; 
	}
		
	#skip ahead to ignore leading whitespace
	$offset = $offset + $contentStart ;
		
	my $content = substr($text, $contentStart, $contentEnd - $contentStart) ;
	
	$start = substr($content, 0, 100) ;
	$start =~ s/\n/ /g ;
	#print " - Section content start: $start\n" ;
	
		
	#recursively gather structure strings from subsections in content 
	my @sectionBreaks = getSectionBreaks($content, $lvl) ;
	#print (" - subsections: @sectionBreaks\n") ;
			
	my $last_sb = -1 ;
	for my $sb (@sectionBreaks) {
	
		if ($last_sb < 0) {
			my $sectionText = substr($content, 0, $sb) ;
			$structureString = $structureString . getStructureString($sectionText,$lvl+1, $offset) ;
		} else {
			my $sectionText = substr($content, $last_sb, $sb - $last_sb) ;
			$structureString = $structureString . "(" . getStructureString($sectionText,$lvl+1, $offset + $last_sb) . ")" ;
		} 
				
		$last_sb = $sb ;
	}
		
	if ($last_sb >= 0) {
		# this text had sections, and there is still text after the last section marker to deal with
			
		my $sectionText = substr($content, $last_sb) ; 
		$structureString = $structureString . "(" . getStructureString($sectionText,$lvl+1, $offset + $last_sb) . ")" ; 
	} else {
		# this text didn't have any sections, so lets split it into paragraphs and sentences
					
		my @paragraphBreaks = getParagraphBreaks($content) ;
		push (@paragraphBreaks, length($content)) ;
			
		my $last_pb = 0 ;
			
		for my $pb (@paragraphBreaks) {
				
			my $paragraphText = substr($content, $last_pb, $pb-$last_pb) ;
			my @sentenceBreaks = getSentenceBreaks($paragraphText) ;
				
			#positions of sentence breaks are relative to paragraph. make them relative to start of document
			for (my $i=0 ; $i<@sentenceBreaks ; $i++) {
				$sentenceBreaks[$i] += $offset + $last_pb ;
			}
			
			#add start of paragraph
			unshift(@sentenceBreaks, $offset+$last_pb) ;
			
			#add end of paragraph
			push(@sentenceBreaks, $offset + $last_pb + length($paragraphText)) ;
			
			
			$structureString = $structureString . "[" . join(",", @sentenceBreaks) . "]" ;
			
			$last_pb = $pb ;
		}
	}
		
	return $structureString ;
}


=item * markDocument($text, $structureString)

Returns a copy of the given text, marked up with the structure defined in the given structure string.

=cut

sub markDocument {
	
	my $text = shift ;
	my $structure = shift ;
	
	my $markedText = "" ;
	
	my @sectionStack = () ;
	
	my $last_pos = 0 ;
	
	while ($structure =~ m/(\()|(\))|(\[(\d+))|((\d+)\])|(\d+)/g) {
		
		if (defined $1) {
			push(@sectionStack, $last_pos) ;
			my $lvl = @sectionStack ;
			
			$markedText = $markedText . "<Section pos=\"$last_pos\" level=\"$lvl\">" ;			
		}
		
		if (defined $2) {
			pop(@sectionStack) ;
			$markedText = $markedText . "</Section>" ;			
		}
		
		if (defined $3) {
			my $pos = int($4) ;
			$markedText = $markedText . substr($text, $last_pos, $pos-$last_pos) ;
			
			$markedText = $markedText . "<Paragraph pos=\"$pos\">" ;
			$last_pos = $pos ;
		}
		
		if (defined $5) {
			my $pos = int($6) ;
			$markedText = $markedText . substr($text, $last_pos, $pos-$last_pos) ;
			
			$markedText = $markedText . "</Paragraph>" ;	
			$last_pos = $pos ;
		}
		
		if (defined $7) {
			my $pos = int($7) ;
			$markedText = $markedText . substr($text, $last_pos, $pos-$last_pos) ;
			$markedText = $markedText . "<SentenceBreak pos=\"$pos\"/>" ;
			$last_pos = $pos ;		
		}
	}
	
	$markedText = $markedText . substr($text, $last_pos) ;
	
	return $markedText ;
}


=item * getSectionBreaks($text, $level)

Identifies sections at the given level in the given text, and returns an array of thier starting positions. 
Positions are given as character offsets in the string.

=cut

sub getSectionBreaks {
	
	my $text = shift ;
	my $level = shift ;
	
	#print("looking for section breaks $level\n") ;
	
		
	my @sectionBreaks = () ;
	
	while ($text =~ m/(?<=\n)=={$level}(.*?)=={$level}(?!=)/g) {
		push(@sectionBreaks, $-[0]) ;
		#print "found sb $1\n" ;
	}
	
	return @sectionBreaks ;	
}


=item * getParagraphBreaks($text)

Identifies paragraph breaks in the given text, and returns an array of thier starting positions. 
Positions are given as character offsets in the string. 

=cut

sub getParagraphBreaks {
	
	my $text = shift ;
	
	my @paragraphBreaks = () ;
	
	while ($text =~ m/\n\s*\n/g ) {
		push(@paragraphBreaks, pos($text)) ;
	}
	
	return @paragraphBreaks ;	
}

=item * getSentenceBreaks($text)

Identifies sentence breaks in the given text, and returns an array of thier starting positions. 
Positions are given as character offsets in the string. 

This will always break on list markers, and will never break on punctuation that is contained within a link.
It largely ignores newlines, so you should first break the text into paragraphs. 

=cut

sub getSentenceBreaks {
	
	my $text = shift ;
	
	#blank out links, so it is impossible to break a sentence within link markup
	my @linkRegions = Stripper::gatherComplexRegions(\$text, "\\[\\[", "\\]\\]") ;
	my $cleanedText = Stripper::stripRegions(\$text, \@linkRegions, 'A') ;
	
	my $lastPos = 0 ;
	
	my @sentenceBreaks = () ;
		
	while ($cleanedText =~ m/\n\s*[\:\#\*]/g) {
		#explicitly break sentences on any newline which is followed by a list marker.
		my $pos = $-[0] ;
		
		my $currText = substr($cleanedText, $lastPos, $pos - $lastPos) ;
		
		my @sbs = CloughSentenceSplitter::getSentenceBreaks($currText) ;
		
		for my $sb (@sbs) {
			push(@sentenceBreaks, $lastPos+$sb) ;
		}
		
		push(@sentenceBreaks, $pos) ;
		$lastPos = $pos ;
	}
	
	if ($lastPos < length($cleanedText)) {
		
		my $currText = substr($cleanedText, $lastPos) ;
		
		my @sbs = CloughSentenceSplitter::getSentenceBreaks($currText) ;
		
		for my $sb (@sbs) {
			push(@sentenceBreaks, $lastPos+$sb) ;
		}		
	}
	
	return @sentenceBreaks ;
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

