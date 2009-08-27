#!/usr/bin/perl
=head1 NAME

CloughSentenceSplitter - Perl module for splitting text into sentences. 

=head1 SYNOPSIS

  use CloughSentenceSplitter ;
  CloughSentenceSplitter::loadDictionary("splitter.dict") ;
  CloughSentenceSplitter::loadAbbreviations("splitter.abv") ;
  
  my $text ; #declare the text to be split here - read it in from a file or something.
  
  my @sentences = CloughSentenceSplitter::getSentences($text) ;
  
  for my $sentence (@sentences) {
  	
  	#the returned sentences deliberately preserve whitespace
  	#before printing it is a good idea to clean this up
  	
  	$sentence =~ s/\s/ /g;       #replace all whitespace with space
  	$sentence =~ s/\s{2,}/ /g;   #collapse multiple spaces
  	$sentence =~ s/^\s+|\s+$//g; #remove leading and trailing whitespace (trim)
  	
  	print "$sentence\n" ; 
  } 

=head1 DESCRIPTION

This module provides a slightly modified version of Paul Clough's rule-based sentence 
splitter (http://ir.shef.ac.uk/cloughie/software.html).

The modifications are:


 - enforced compliance to strict pragma.

 - structured to be used as a module rather than a stand-alone program.

 - common terms and abbreviations are now stored in an associative array for efficiency.

 - ensured that returned sentences exactly match input string. 


=head2 Functions

=over 4

=cut

package CloughSentenceSplitter ;

use strict ;

my %common_terms = () ;
my %abbreviations = () ;


=item * loadDictionary($dictionaryFile)

Loads a dictionary of common terms. The dictionary should contain one term per line. 

=cut

sub loadDictionary {
	
	my $dictionary = shift;

	if (open(DICT, $dictionary)) {

		while (defined (my $line = <DICT>)) {
			chomp($line);
			if ($line !~ /^[A-Z]/) {
				$common_terms{$line} = 1;	
			}
		}		
		
		close(DICT);
	} else {
		die("cannot open dictionary file.\n") ;
	}
}


=item * loadAbbreviations($dictionaryFile)

Loads a file of abbreviations. The file should contain one abbreviation per line. 

=cut

sub loadAbbreviations {
	
	my $abbrv_file = shift ;

	if (open(ABBRV, $abbrv_file)) {

		while (defined (my $line = <ABBRV>)) {
			chomp($line);
			$abbreviations{$line} = 1;	
		}
		
		close(ABBRV);
	} else {
		die("cannot open abbreviations file.\n") ;
	}
}


=item * getSentenceBreaks($text)

Identifies sentence breaks in the given text, and returns an array of starting positions. 
Positions are given as character offsets in the string. 

=cut

sub getSentenceBreaks {
	
	my $text = shift ;
	my $len = 0;
	
	if (not %abbreviations) {
		die "You must load an abbreviation file first!\n" ;
	}
	
	if (not %common_terms) {
		die "You must load a dictionary file first!\n" ;
	}
	
	my @sentenceBreaks = () ;
	
	#blank out carriage returns
	$text =~ tr/[\n\r]/ /;
	
	# make sure there are always spaces following punctuation to enable splitter to work 
	# properly - covers such cases as believe.I ... where a space has forgotten to be 
	# inserted.
	# DAVE: Actually, don't do that. It doesn't seem like this would happen very often, and fixing it would 
	# mess up our character alignment.
	#$text =~ s/(.*)([.!?])(.*)/$1$2 $3/g;
	
	
	#DAVE: what was this for?
	#$text.="\n";

	# sentence ends with [.!?], followed by capital or number. Use base-line splitter and then
	# use some heuristics to improve upon this e.g. dealing with Mr. and etc. 
	# In this rather large regex we allow for quotes, brackets etc. 
	# $1 = the complete sentence including beginning punctuation and brackets
	# $2 = the punctuation mark - either [.!?:]
	# $3 = the brackets or quotes after the [!?.:]. This is non-grouping i.e. does not consume.
	# $4 = the next word after the [.?!:].This is non-grouping i.e. does not consume.
	# $5 = rather than a next word, it may have been the last sentence in the file. Therefore capture
	#      punctuation and brackets before end of file. This is non-grouping i.e. does not consume.
	while ($text =~ /([\'\"`]*[({[]?[a-zA-Z0-9]+.*?)([\.!?:])(?:(?=([([{\"\'`)}\]<]*[ ]+)[([{\"\'`)}\] ]*([A-Z0-9][a-z]*))|(?=([()\"\'`)}\<\] ]+)\s))/gs ) {
		
		my $sentence = $1;
		my $punctuation = $2;
		
		my $punctuationPos = $+[2] ;
		
		my $stuff_after_period ;
		if (defined($3)) { 
			$stuff_after_period = $3; 
		} else { 
			if ($5) { 
				$stuff_after_period = $5; 
			} else { 
				$stuff_after_period = ""; 
			}
		}
		
		$stuff_after_period =~ s/\s//g;
		
		$punctuationPos += length($stuff_after_period) ;
		
			
		my @words = split(/\s+/, $sentence);
		$len+=@words;
		
		my $next_word ;
		if ($4) { $next_word = $4; } else { $next_word = ""; }
		
		my $isSentence = 0 ;
		
		if ($punctuation =~ /[\.]/) {
			# consider the word before the period => is it an abbreviation? (then not full-stop)
			# Abbreviation if:
			#  1) all consonants and not all capitalised (and contain no lower case y e.g. shy, sly
			#  2) a span of single letters followed by periods
			#  3) a single letter (except I).
			#  4) in the known abbreviations list.
			# In above cases, then the period is NOT a full stop.
			
			# perhaps only one word e.g. P.S rather than a whole sentence
			$sentence =~ /\s+([a-zA-Z\.]+)$|([a-zA-Z\.]+)$/;
			
			my $last_word ;
			if ($1) { $last_word = $1; } else { $last_word = $2; }
			
			if (not defined $last_word) {
				$last_word = "" ;
			}
						
			if ( (($last_word !~ /.*[AEIOUaeiou]+.*/)&&($last_word =~ /.*[a-z]+.*/)&&($last_word !~ /.*[y]+.*/))|| 
			     ($last_word =~ /([a-zA-Z][\.])+/)||
			     (($last_word =~ /^[A-Za-z]$/)&&($last_word !~ /^[I]$/))||
			     ($abbreviations{$last_word})) { 		
	
				# We have an abbreviation, but this could come at the middle or end of a 
				# sentence. Therefore we assume that the abbreviation is not at the end of 
				# a sentence if the next word is a common word and the abbreviation occurs
				# less than 5 words from the start of the sentence.		     	
			     	
				$next_word = lc $next_word;
				 
				if ($common_terms{$next_word} && $len > 6) {
					$isSentence = 1 ;
				}
			} else {
				$isSentence = 1 ;
			}
		
		} else {	
		 	# only consider sentences if : comes after at least 6 words from start of 
		  	# sentence
		  	if (($punctuation =~ /[!?]/)||(($punctuation =~ /[:]/)&&($len > 6))) {
		    	$isSentence = 1 ;
		  	} 
		}
		
		push(@sentenceBreaks, $punctuationPos) ;
		$len = 0;	
	}

	return @sentenceBreaks ;
}


=item * getSentences($text)

Returns an array of sentences in the given text. No characters are modified; if you concatenate all sentences together you will get the original string.

=cut

sub getSentences {
	
	my $text = shift ;
	my @sentenceBreaks = getSentenceBreaks($text) ;
	my @sentences = () ;
	
	my $lastPos = 0 ;
				
	for my $pos (@sentenceBreaks) {
		my $sentence = substr($text, $lastPos, $pos-$lastPos) ;
		push(@sentences, $sentence) ;
					
		$lastPos = $pos ;
	}
	
	if ($lastPos < length($text)) {
		my $sentence = substr($text, $lastPos) ;
		push(@sentences, $sentence) ;
	}
	
	return @sentences ;
}

=back

=head1 AUTHOR

Paul Clough (cloughie@dcs.shef.ac.uk), modified by David Milne (d.n.milne@gmail.com)

=head1 COPYRIGHT

This library is free software; you can redistribute it and/or
modify it under the same terms as Perl itself.

=cut


1;
