/*
 *    EmphasisResolver.java
 *    Copyright (C) 2009 David Milne, d.n.milne@gmail.com
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

import java.util.Arrays;
import java.util.Comparator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This parses MediaWiki syntax for '''bold''' and ''italic'' text with the equivalent html markup.
 * 
 * @author David Milne
 */
public class EmphasisResolver {

	private static final int ITALIC = 2 ;
	private static final int BOLD = 3 ;
	private static final int BOLD_ITALIC = 5 ;

	private static final int START = 0 ;
	private static final int END = 1 ;
	private static final int TYPE = 2 ;
	
	private Pattern pattern = Pattern.compile("'{2,}") ;
	
	/**
	 * Returns a copy of the given markup, where all MediaWiki bold and italic marks 
	 * have been replaced with thier html equivalents. 
	 * 
	 * @param markup the text with MediaWiki markup
	 * @return the text with html markup
	 */
	public String resolveEmphasis(String markup) {
		
		//NOTE: this definitely feels like the hard way to solve the problem. Surely there is a simpler way?
		
		//gather a list of tokens, with start and end positions, type, and wheither it is a start or end token.
		//each token is int array in the form {startOffset, endOffset, type, startOrEnd}
		Vector<int[]> resolvedTokens = new Vector<int[]>() ;
		
		//keep stack of token starts, where each token is a 3 integer array
		//{startOffset,endOffset,type}
		Vector<int[]> unresolvedTokenStack = new Vector<int[]>() ;
		
		Matcher m = pattern.matcher(markup) ;
		while (m.find()) {

			int[] curr = {m.start(), m.end(), m.end()-m.start()} ;

			switch(curr[TYPE]) {

			case ITALIC:
				if (!unresolvedTokenStack.isEmpty()) {
					int[] prev = unresolvedTokenStack.firstElement() ;
					unresolvedTokenStack.remove(0) ;

					if (prev[TYPE]==ITALIC || prev[TYPE]==BOLD_ITALIC) {
												
						//this was the end of an italic region
						int[] startToken = {prev[START], prev[END], ITALIC, START} ;
						int[] endToken = {curr[START], curr[END], ITALIC, END} ;
						
						if (prev[TYPE]==BOLD_ITALIC) {
							startToken[START] += BOLD ;
							
							int[] token = {prev[START], prev[END]-ITALIC, BOLD} ;
							unresolvedTokenStack.add(0, token) ;
						}
						resolvedTokens.add(startToken) ;
						resolvedTokens.add(endToken) ;

					} else {
						unresolvedTokenStack.add(0,prev) ;
						unresolvedTokenStack.add(0, curr) ;
					}
				} else {
					unresolvedTokenStack.add(0, curr) ;
				}

				break ;

			case BOLD: 
				if (!unresolvedTokenStack.isEmpty()) {
					int[] prev = unresolvedTokenStack.firstElement() ;
					unresolvedTokenStack.remove(0) ;

					if (prev[TYPE]==BOLD || prev[TYPE]==BOLD_ITALIC) {
						//this was the end of a bold region
						
						int[] startToken = {prev[START], prev[END], BOLD, START} ;
						int[] endToken = {curr[START], curr[END], BOLD, END} ;
						
						if (prev[TYPE]==BOLD_ITALIC) {
							startToken[START] += ITALIC ;
							int[] token = {prev[START], prev[END]-BOLD, ITALIC} ;
							unresolvedTokenStack.add(0, token) ;
						}
						resolvedTokens.add(startToken) ;
						resolvedTokens.add(endToken) ;
						
					} else {
						unresolvedTokenStack.add(0, prev) ;
						unresolvedTokenStack.add(0, curr) ;
					}
				} else {
					unresolvedTokenStack.add(0, curr) ;
				}
				break ;
				
			case BOLD_ITALIC:
				
				if (!unresolvedTokenStack.isEmpty()) {
					int[] prev = unresolvedTokenStack.firstElement() ;
					unresolvedTokenStack.remove(0) ;
					
					switch (prev[TYPE]) {
					
					case BOLD:
			
						int[] s1 = {prev[START], prev[END], BOLD, START} ;
						int[] e1 = {curr[START], curr[END]-ITALIC, BOLD, END} ;
						
						resolvedTokens.add(s1) ;
						resolvedTokens.add(e1) ;
						
						if (!unresolvedTokenStack.isEmpty()) {
							
							int[] prev2 = unresolvedTokenStack.firstElement() ;
							unresolvedTokenStack.remove(0) ;
							
							if (prev2[TYPE] == ITALIC) {
								
								int[] s2 = {prev2[START], prev2[END], ITALIC, START} ;
								int[] e2 = {curr[START]+BOLD, curr[END], ITALIC, END} ;
								
								resolvedTokens.add(s2) ;
								resolvedTokens.add(e2) ;
							} 						
						}
						
						break ;
						
					case ITALIC:
						
						int[] s3 = {prev[START], prev[END], ITALIC, START} ;
						int[] e3 = {curr[START], curr[END]-BOLD, ITALIC, END} ;
						
						resolvedTokens.add(s3) ;
						resolvedTokens.add(e3) ;
						
						if (!unresolvedTokenStack.isEmpty()) {
							int[] prev2 = unresolvedTokenStack.firstElement() ;
							unresolvedTokenStack.remove(0) ;
							
							if (prev2[TYPE] == BOLD) {
								
								int[] s4 = {prev2[START], prev2[END], BOLD, START} ;
								int[] e4 = {curr[START]+ITALIC, curr[END], BOLD, END} ;
								
								resolvedTokens.add(s4) ;
								resolvedTokens.add(e4) ;
							} 						
						}
						break ;
					
					case BOLD_ITALIC:
						
						int[] s5 = {prev[START], prev[END], BOLD_ITALIC, START} ;
						int[] e5 = {curr[START], curr[END], BOLD_ITALIC, END} ;
						
						resolvedTokens.add(s5) ;
						resolvedTokens.add(e5) ;
						
						break ;
					}
				} else {
					unresolvedTokenStack.add(0, curr) ;
				}
				break ;
			}
		}
		
		//gathered tokens aren't sorted
		int[][] sortedTokens = resolvedTokens.toArray(new int[resolvedTokens.size()][]) ;
		Arrays.sort(sortedTokens, new Comparator<int[]>() {
			public int compare(int[] a, int[] b) {
				return new Integer(a[START]).compareTo(b[START]) ;
			}
		}) ;
		
		//now finaly ready to resolve markup
		
		StringBuffer resolvedMarkup = new StringBuffer() ;
		int lastCopyPos = 0 ;
		
		for (int[] token:sortedTokens) {
			
			resolvedMarkup.append(markup.substring(lastCopyPos, token[START])) ;
			
			if (token[3] == START) {
				
				switch(token[TYPE]) {
				case BOLD:
					resolvedMarkup.append("<b>") ;
					break;
				case ITALIC:
					resolvedMarkup.append("<i>") ;
					break ;
				case BOLD_ITALIC:
					resolvedMarkup.append("<b><i>") ;
					break ;
				}
			} else {
				switch(token[TYPE]) {
				case BOLD:
					resolvedMarkup.append("</b>") ;
					break;
				case ITALIC:
					resolvedMarkup.append("</i>") ;
					break ;
				case BOLD_ITALIC:
					resolvedMarkup.append("</i></b>") ;
					break ;
				}
			}
			
			lastCopyPos = token[END] ;
		}
			
		resolvedMarkup.append(markup.substring(lastCopyPos)) ;
		return resolvedMarkup.toString() ;
	}
	
	public static void main(String[] args) {
		
		EmphasisResolver er = new EmphasisResolver() ;
		
		String markup = "Parsing '''MediaWiki's''' syntax for '''bold''' and ''italic'' markup is a '''''deceptively''' difficult'' task. Whoever came up with the markup scheme should be '''shot'''." ; 
		
		System.out.println(er.resolveEmphasis(markup)) ;
	}
}
