/*
 *    MarkupStripper.java
 *    Copyright (C) 2007 David Milne, d.n.milnegmail.com
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.wikipedia.miner.util;

import java.util.*;
import java.util.regex.*;
import org.wikipedia.miner.model.* ;

/**
 * This provides tools to strip out markup from wikipedia articles, or anything else that has been written
 * in mediawiki's format. It's all pretty simple, so don't expect perfect parsing. It is particularly bad at 
 * dealing with templates (these are simply removed rather than resolved).  
 * 
 * author David Milne
 */
public class MarkupStripper {

	private Pattern linkPattern = Pattern.compile("\\[\\[(.*?:)?(.*?)(\\|.*?)?\\]\\]") ;

	/**
	 * Returns a copy of the given text, where all markup has been removed except for 
	 * internal links to other wikipedia pages (e.g. to articles or categories), section 
	 * headers, list markers, and bold/italic markers. 
	 * 
	 * By default, unwanted text is completely discarded. You can optionally specify 
	 * a character to replace the regions that are discared, so that the length of the 
	 * string and the locations of unstripped characters is not modified.
	 */
	public String stripAllButLinksAndEmphasis(String text, Character replacement) {

		//deal with comments and math regions entirely seperately. 
		//Comments often contain poorly nested items that the remaining things will complain about.
		//Math regions contain items that look confusingly like templates.
		Vector<int[]> regions = gatherSimpleRegions(text, "\\<\\!--(.*?)--\\>") ;
		regions = mergeRegionLists(regions, gatherComplexRegions(text, "\\<math(\\s*?)([^>\\/]*?)\\>", "\\<\\/math(\\s*?)\\>")) ;
		String clearedText = stripRegions(text, regions, replacement) ;

		//deal with templates entirely seperately. They often end in |}} which confuses the gathering of tables.
		regions = gatherTemplates(clearedText) ;
		clearedText = stripRegions(clearedText, regions, replacement) ;

		//now gather all of the other regions we want to ignore	
		regions = gatherTables(clearedText) ;

		regions = mergeRegionLists(regions, gatherHTML(clearedText)) ;
		regions = mergeRegionLists(regions, gatherExternalLinks(clearedText)) ;
		regions = mergeRegionLists(regions, gatherMagicWords(clearedText)) ;

		//ignore these regions now (they need to be blanked before we can correctly identify the remaining regions)
		clearedText = stripRegions(clearedText, regions, replacement) ;

		regions = gatherMisformattedStarts(clearedText) ;
		clearedText = stripRegions(clearedText, regions, replacement) ;

		return clearedText ;
	}

	/**
	 * Returns a copy of the given text, where all links to wikipedia pages 
	 * (categories, articles, etc) have been removed. Links to articles are 
	 * replaced with the appropriate anchor text. All other links are removed completely.
	 * 
	 * By default, unwanted text is completely discarded. You can optionally specify 
	 * a character to replace the regions that are discarded, so that the length of the 
	 * string and the locations of unstripped characters is not modified.
	 */
	public String stripInternalLinks(String text, Character replacement) {

		Vector<int[]> regions = gatherComplexRegions(text, "\\[\\[", "\\]\\]") ;

		StringBuffer strippedText = new StringBuffer() ;
		int lastPos = text.length() ;

		//because regions are sorted by end position, we work backwards through them
		int i = regions.size() ;

		while (i > 0) {
			i -- ;

			int[] region = regions.elementAt(i) ;

			//only deal with this region is not within a region we have already delt with. 
			if (region[0] < lastPos) {

				//copy everything between this region and start of last one we dealt with. 
				strippedText.insert(0,text.substring(region[1], lastPos)) ;

				String linkMarkup = text.substring(region[0], region[1]) ;

				// by default (if anything goes wrong) we will keep the link as it is
				String strippedLinkMarkup = linkMarkup ;


				Matcher m = linkPattern.matcher(linkMarkup) ;
				if (m.matches()) {

					String prefix = m.group(1) ;
					String dest = m.group(2) ;
					String anchor = m.group(3) ;

					if (prefix != null) {
						// this is not a link to another article, so get rid of it entirely
						if (replacement != null) 
							strippedLinkMarkup = linkMarkup.replaceAll(".",replacement.toString()) ;			
						else 
							strippedLinkMarkup = "" ;
					} else {
						if (anchor != null) {
							//this has an anchor defined, so use that but blank out everything else

							if (replacement != null) 
								strippedLinkMarkup = replacement + replacement + dest.replaceAll(".", replacement.toString()) + replacement + anchor.substring(1) + anchor.substring(1) + replacement ;
							else
								strippedLinkMarkup = anchor.substring(1) ;

						} else {
							//this has no anchor defined, so treat dest as anchor and blank out everything else

							if (replacement != null) {
								strippedLinkMarkup = replacement + replacement + dest + replacement + replacement ;
							} else {
								strippedLinkMarkup = dest ;
							}
						}
					}				
				} else {
					//logProblem("our pattern for delimiting links has a problem") ;
				}

				strippedText.insert(0,strippedLinkMarkup) ;
				lastPos = region[0] ;
			}
		}	

		if (lastPos > 0) 
			strippedText.insert(0,text.substring(0, lastPos)) ;

		return strippedText.toString() ; 	
	}

	/**
	 * Returns a copy of the given text, where all links to wikipedia pages
	 * that are not articles (categories, language links, etc) have been removed.
	 * 
	 * By default, unwanted text is completely discarded. You can optionally specify
	 * a character to replace the regions that are discarded, so that the length of the
	 * string and the locations of unstripped characters is not modified.
	 */
	public String stripNonArticleInternalLinks(String text, Character replacement) {

		//currItem = "non-article internal links" ;

		Vector<int[]> regions = gatherComplexRegions(text, "\\[\\[", "\\]\\]") ;

		StringBuffer strippedText = new StringBuffer() ;
		int lastPos = text.length() ;

		//because regions are sorted by end position, we work backwards through them
		int i = regions.size() ;

		while (i > 0) {
			i -- ;

			int[] region = regions.elementAt(i) ;

			//only deal with this region is not within a region we have already delt with. 
			if (region[0] < lastPos) {

				//copy everything between this region and start of last one we dealt with. 
				strippedText.insert(0, text.substring(region[1], lastPos)) ;

				String linkMarkup = text.substring(region[0], region[1]) ;

				//print("link [region[0],region[1]] = linkMarkup\n\n") ;

				// by default (if anything goes wrong) we will keep the link as it is
				String strippedLinkMarkup = linkMarkup ;
				Matcher m = linkPattern.matcher(linkMarkup) ;
				if (m.matches()) {

					String prefix = m.group(1) ;
					String dest = m.group(2) ;
					String anchor = m.group(3) ;

					if (prefix != null) {
						// this is not a link to another article, so get rid of it entirely
						if (replacement != null) {
							strippedLinkMarkup = linkMarkup.replaceAll(".", replacement.toString()) ;			
						} else {
							strippedLinkMarkup = "" ;
						}
					} 

				} else {
					//logProblem("our pattern for delimiting links has a problem") ;
				}

				strippedText.insert(0, strippedLinkMarkup) ;
				lastPos = region[0] ;
			}
		}	

		if (lastPos > 0) 
			strippedText.insert(0, text.substring(0, lastPos)) ;

		return strippedText.toString() ; 	
	}




	/**
	 * Convenience method which combines both of the above methods - i.e. returns a copy of the
	 * given text, where all markup has been removed except for section headers and list markers.
	 *
	 * By default, unwanted text is completely discarded. You can optionally specify 
	 * a character to replace the regions that are discared, so that the length of the 
	 * string and the locations of unstripped characters is not modified. 
	 */

	public String stripToPlainText(String text, Character replacement) {

		String clearedText = stripAllButLinksAndEmphasis(text, replacement) ;
		clearedText = stripInternalLinks(clearedText, replacement) ;

		return clearedText ;	
	}



	/**
	 * Returns a copy of the given text, where the given regions have been removed. 
	 * Regions are identified using one of the gather methods.
	 * 
	 * By default, unwanted text is completely discarded. You can optionally specify
	 * a character to replace the regions that are discared, so that the length of the 
	 * string and the locations of unstripped characters is not modified.
	 */
	public String stripRegions(String text, Vector<int[]> regions, Character replacement) {

		StringBuffer clearedText = new StringBuffer() ;

		int lastPos = text.length() ;

		//because regions are sorted by end position, we work backwards through them
		int i = regions.size() ;

		while (i > 0) {
			i -- ;

			int[] region = regions.elementAt(i) ;


			//only deal with this region is not within a region we have already delt with. 
			if (region[0] < lastPos) {

				//print (" - - dealing with it\n") ;

				//copy text after this region and before beginning of the last region we delt with
				if (region[1] < lastPos) 
					clearedText.insert(0, text.substring(region[1], lastPos)) ;

				if (replacement != null) {
					String fill = text.substring(region[0],region[1]).replaceAll(".", replacement.toString()) ;
					clearedText.insert(0, fill) ;
				}

				lastPos = region[0] ;
			} else {
				//print (" - - already dealt with\n") ;

			}
		}

		clearedText.insert(0, text.substring(0, lastPos)) ;	
		return clearedText.toString() ;
	}


//	======================================================================================================

	/**
	 * Gathers areas within the text which correspond to links to other wikipedia pages
	 * (as identified by [[ and ]] pairs). Note: these can be nested (e.g. for images)
	 */
	public Vector<int[]> gatherInternalLinks(String text) {
		//currItem = "internal links" ;

		return gatherComplexRegions(text, "\\[\\[", "\\]\\]") ;
	}

	/**
	 * Gathers areas within the text which correspond to templates (as identified by {{ and }} pairs). 
	 */
	public Vector<int[]> gatherTemplates(String text) {
		//currItem = "templates" ;
		return gatherComplexRegions(text, "{{", "}}") ;
	}

	/**
	 * Gathers areas within the text which correspond to tables (as identified by {| and |} pairs). 
	 */
	public Vector<int[]> gatherTables(String text) {
		//currItem = "tables" ;
		return gatherComplexRegions(text, "\\{\\|", "\\|\\}") ;
	}

	/**
	 * Gathers areas within the text which correspond to html tags. 
	 * 
	 * DIV and REF regions will enclose beginning and ending tags, and everything in between,
	 * since we assume this content is supposed to be discarded. All other regions will only include the
	 * individual tag, since we assume the content between such pairs is supposed to be retained. 
	 */
	public Vector<int[]> gatherHTML(String text) {

		//currItem = "html" ;

		//gather and merge references
		Vector<int[]> regions = gatherReferences(text) ;

		//gather <div> </div> pairs
		regions = mergeRegionLists(regions, gatherComplexRegions(text, "\\<div(\\s*?)([^>\\/]*?)\\>", "\\<\\/div(\\s*?)\\>")) ;
		
		//gather remaining tags
		regions = mergeRegionLists(regions, gatherSimpleRegions(text, "\\<(.*?)\\>")) ;
		
		return regions ;
	}


	/**
	 * Gathers areas within the text which correspond to references (text to support claims or facts).
	 * The regions will enclose beginning and ending tags, and everything in between,
	 * since we assume this content is supposed to be discarded. 
	 */
	public Vector<int[]> gatherReferences(String text) {

		//currItem = "references" ;

		//gather <ref/>
		Vector<int[]> regions = gatherSimpleRegions(text, "\\<ref(\\s*?)([^>]*?)\\/\\>") ;

		//gather <ref> </ref> pairs (these shouldnt be nested, but what the hell...)
		regions = mergeRegionLists(regions, gatherComplexRegions(text, "\\<ref(\\s*?)([^>\\/]*?)\\>", "\\<\\/ref(\\s*?)\\>")) ;

		return regions ;
	}


	/**
	 * Gathers items which MediaWiki documentation mysteriously refers to as "majic words": e.g. __NOTOC__
	 */
	public Vector<int[]> gatherMagicWords(String text) {

		//currItem = "magic words" ;
		return gatherSimpleRegions(text, "\\_\\_([A-Z]+)\\_\\_") ;
	}

	/**
	 * Gathers all links to external web pages
	 */
	public Vector<int[]> gatherExternalLinks(String text) {
		//currItem = "external links" ;
		return gatherSimpleRegions(text, "\\[(http|www|ftp).*?\\]") ;
	}

	/**
	 * Gathers bold and italic markup
	 */
	public Vector<int[]> gatherEmphasis(String text) {
		//currItem = "emphasis" ;
		return gatherSimpleRegions(text, "'{2,}") ; 
	}

	/**
	 * Gathers markup which indicates indented items, or numbered and unnumbered list items
	 */
	public Vector<int[]> gatherListAndIndentMarkers(String text) {
		//currItem = "list and intent markers" ;

		Vector<int[]> regions = gatherSimpleRegions(text, "\n( *)([//*:]+)") ;

		//increment start positions of all regions by one, so they don't include the newline character
		for (int[] region:regions)
			region[0]++ ;

		//add occurance of list item on first line (if there is one)
		regions = mergeRegionLists(regions, gatherSimpleRegions(text, "^( *)([//*:]+)")) ;
		return regions ;
	}

	/**
	 * Gathers paragraphs within the text referred to by the given pointer, which are at the 
	 * start and begin with an indent. These correspond to quotes or disambiguation and 
	 * navigation notes that the author should have used templates to identify, but didn't. 
	 * This will only work after templates, and before list markers have been cleaned out.
	 */
	public Vector<int[]> gatherMisformattedStarts(String text) {

		//currItem = "starts" ;

		String[] lines = text.split("\n") ;

		int ignoreUntil = 0 ;

		for (String line:lines) {

			//print(" - - 'line'\n") ;

			boolean isWhitespace = line.matches("^(\\s*)$") ;
			boolean isIndented = line.matches("^(\\s*):") ;
			//my isItalicised = (line =~ m/^(\s*)'{2,}(.*?)'{2,}(\s*)/) ;
			
			if (isWhitespace || isIndented)  {
				//want to ignore this line
				ignoreUntil = ignoreUntil + line.length() + 1 ;	
				//print(" - - - discard\n") ;		
			} else {
				//print(" - - - keep\n") ;
				break ;
			}		
		}
		
		int[] region = {0, ignoreUntil} ;

		Vector<int[]> regions = new Vector<int[]>() ;
		regions.add(region) ;
		
		return regions ;
	}


	/**
	 * Gathers simple regions: ones which cannot be nested within each other.
	 * 
	 *  The returned regions (an array of start and end positions) will be sorted 
	 *  by end position (and also by start position, since they can't overlap) 
	 */ 
	public Vector<int[]> gatherSimpleRegions(String text, String regex) {

		//an array of regions we have identified
		//each region is given as an array containing start and end character indexes of the region. 
		Vector<int[]> regions = new Vector<int[]>() ;
		
		Pattern p = Pattern.compile(regex, Pattern.DOTALL) ;
		Matcher m = p.matcher(text) ;

		while(m.find()) {
			int[] region = {m.start(), m.end()} ;
			regions.add(region) ;
		}

		return regions ;
	}


	/**
	 * Gathers complex regions: ones which can potentially be nested within each other.
	 * 
	 * The returned regions (an array of start and end positions) will be either
	 * non-overlapping or cleanly nested, and sorted by end position. 
	 */ 
	public Vector<int[]> gatherComplexRegions(String text, String startRegex, String endRegex) {

		//an array of regions we have identified
		//each region is given as an array containing start and end character indexes of the region. 
		Vector<int[]> regions = new Vector<int[]>() ;

		//a stack of region starting positions
		Vector<Integer> startStack = new Vector<Integer>() ;
		
		
		Pattern p = Pattern.compile("((" + startRegex + ")|(" + endRegex + "))", Pattern.DOTALL) ;
		Matcher m = p.matcher(text) ;
		
		while(m.find()) {

			Integer p1 = m.start() ;
			Integer p2 = m.end() ;  
			

			if (m.group(2) != null) {
				//this is the start of an item
				startStack.add(p1) ;
			} else {
				//this is the end of an item
				if (!startStack.isEmpty()) {
					int start = startStack.elementAt(startStack.size()-1) ;
					startStack.removeElementAt(startStack.size()-1) ;
					
					int[] region = {start, p2} ;
					regions.add(region) ;

					//print (" - item [region[0],region[1]]: ".substr(text, region[0], region[1]-region[0])."\n") ;
				} else {
					//logProblem("oops, we found the end of an item, but have no idea where it started") ;
				}
			}
		}

		if (!startStack.isEmpty()) {
			//logProblem("oops, we got to the end of the text and still have items that have been started but not finished") ;
		}

		return regions ;
	}


	/**
	 * Collapses a region list, by discarding any regions which are contained within 
	 * other regions.
	 * 
	 * The resulting region list will be non-overlapping and sorted by end positions.
	 */
	private Vector<int[]> collapseRegionList(Vector<int[]> regions) {

		Vector<int[]> newRegions = new Vector<int[]>() ;

		int index = regions.size() -1 ;

		int lastPos = -1 ;

		while (index >= 0) {

			int[] region = regions.elementAt(index) ;

			if (lastPos <0 || region[1] <= lastPos) {
				newRegions.add(0, region) ;
				lastPos = region[0] ;
			}
			
			index-- ;
		}

		return newRegions ;	
	}

	/**
	 * Merges two lists of regions into one sorted list. Regions that are contained
	 * within other regions are discarded.
	 * 
	 * The resulting region list will be non-overlapping and sorted by end positions.
	 */
	private Vector<int[]> mergeRegionLists(Vector<int[]> regionsA, Vector<int[]> regionsB) {

		int indexA = regionsA.size() -1 ;
		int indexB = regionsB.size() - 1;

		Vector<int[]> newRegions = new Vector<int[]>() ;

		int lastPos = -1 ;

		while (indexA >= 0 && indexB >= 0) {

			int[] regionA = regionsA.elementAt(indexA) ;
			int[] regionB = regionsB.elementAt(indexB) ;

			if (lastPos >= 0 && regionA[0] >= lastPos && regionA[0] >= lastPos) {
				//both of these are inside regions that we have already dealt with, so discard them
				indexA-- ;
				indexB-- ;
			} else {
				if (regionB[1] > regionA[1]) {

					//lets see if we need to copy B across
					if ((regionB[0] >= regionA[0] && regionB[1] <= regionA[1]) || (lastPos>=0 && regionB[0] >= lastPos)) {
						//either A or the last region we dealt with completely contains B, so we just discard B
					} else {
						//deal with B now
						int[] newRegion = {regionB[0], min(regionB[1], lastPos)} ;
						newRegions.add(0, newRegion) ;
						lastPos = regionB[0] ;
					}

					indexB-- ;				
				} else {

					//lets see if we need to copy A across

					if ((regionA[0] >= regionB[0] && regionA[1] <= regionB[1]) || (lastPos>=0 && regionA[0] >= lastPos)) {
						//either B or the last region we dealt with completely contains A, so we just discard A
					} else {
						//deal with A now
						int[] newRegion = {regionA[0], min(regionA[1], lastPos)} ;
						newRegions.add(0, newRegion) ;
						lastPos = regionA[0] ;
					}

					indexA-- ;	
				}
			}
		}

		//deal with any remaining A regions
		while (indexA >= 0) {

			int[] regionA = regionsA.elementAt(indexA) ;

			if (lastPos >= 0 && regionA[0] > lastPos) {
				//this is already covered, so ignore it
			} else {
				int[] newRegion = {regionA[0], min(regionA[1], lastPos)} ;
				newRegions.add(0, newRegion) ;
				lastPos = regionA[0] ;
			}

			indexA-- ;
		}

		//deal with any remaining B regions
		while (indexB >= 0) {

			int[] regionB = regionsB.elementAt(indexB) ;

			if (lastPos >= 0 && regionB[0] > lastPos) {
				//this is already covered, so ignore it
			} else {
				int[] newRegion = {regionB[0], min(regionB[1], lastPos)} ;
				newRegions.add(0, newRegion) ;
				lastPos = regionB[0] ;
			}

			indexB-- ;
		}

		return newRegions ;
	}

	
	private int min(int a, int b) {

		if (a>=0 && b>=0) {
			return Math.min(a,b) ;
		} else {
			if (a>=0)
				return a ;
			else 
				return b ;
		}
	}
	
	public static void main(String[] args) throws Exception{
		
		Wikipedia wikipedia = new Wikipedia("wdm", "enwiki_20090306", "student", "*****") ;
		MarkupStripper ms = new MarkupStripper() ;
		
		Article art = wikipedia.getMostLikelyArticle("Tree", null) ;
		
		String content = art.getContent() ;
		
		String cleanedContent = ms.stripAllButLinksAndEmphasis(content, null) ;
		
		System.out.println(cleanedContent) ;
	}

}
