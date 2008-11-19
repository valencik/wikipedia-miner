/*
 *    MarkupStripper.java
 *    Copyright (C) 2007 David Milne, d.n.milne@gmail.com
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

import java.io.*;
import java.util.*;
import java.util.regex.*;
import org.wikipedia.miner.model.*;

/**
 * This provides tools to strip out markup from wikipedia articles, or anything else that has been written
 * in mediawiki's format. It's all pretty simple, so don't expect perfect parsing.   
 * 
 * @author David Milne
 */
public class MarkupStripper {
	
	/**
	 * Strips a string of all markup; tries to turn it into plain text	 
	 * 
	 * @param markup the text to be stripped
	 * @return the stripped text
	 */
	public static String stripEverything(String markup) {
		
		String strippedMarkup = stripTemplates(markup) ;
		strippedMarkup = stripTables(strippedMarkup) ;
		strippedMarkup = stripLinks(strippedMarkup) ;
		strippedMarkup = stripHTML(strippedMarkup) ;
		strippedMarkup = stripExternalLinks(strippedMarkup) ;
		strippedMarkup = stripFormatting(strippedMarkup) ;
		strippedMarkup = stripExcessNewlines(strippedMarkup) ;
		
		return strippedMarkup ;
	}
	
	/**
	 * Strips all links from the given markup; anything like [[this]] is replaced. If it is a link to a wikipedia article, 
	 * then it is replaced with its anchor text. If it is a link to anything else (category, translation, etc) then it is removed
	 * completely.	 
	 * 
	 * @param markup the text to be stripped
	 * @return the stripped text
	 */
	public static String stripLinks(String markup) {
		
		Vector<Integer> linkStack = new Vector<Integer>() ; 
		
		Pattern p = Pattern.compile("(\\[\\[|\\]\\])") ;
		Matcher m = p.matcher(markup) ;
		
		StringBuffer sb = new StringBuffer() ;
		int lastIndex = 0 ;
		
		while (m.find()) {
			
			String tag = markup.substring(m.start(), m.end()) ;
			
			if (tag.equals("[["))
				linkStack.add(m.start()) ;
			else {
				if (!linkStack.isEmpty()) {
					int linkStart = linkStack.lastElement() ;
					linkStack.remove(linkStack.size()-1) ;
					
					if (linkStack.isEmpty()) {
						sb.append(markup.substring(lastIndex, linkStart)) ;
						
						//we have the whole link, with other links nested inside if it's an image
						String linkMarkup = markup.substring(linkStart+2, m.start()) ;
						sb.append(stripLink(linkMarkup)) ;
						
						lastIndex = m.end() ;
					}
				}
			}
		}
		
		sb.append(markup.substring(lastIndex)) ;
		
		return sb.toString() ;
	}
	
	/**
	 * Strips all non-article links from the given markup; anything like [[this]] is removed unless it
	 * goes to a wikipedia article, redirect, or disambiguation page. 
	 * 
	 * @param markup the text to be stripped
	 * @return the stripped text
	 */
	public static String stripNonArticleLinks(String markup) {
		
		Vector<Integer> linkStack = new Vector<Integer>() ; 
		
		Pattern p = Pattern.compile("(\\[\\[|\\]\\])") ;
		Matcher m = p.matcher(markup) ;
		
		StringBuffer sb = new StringBuffer() ;
		int lastIndex = 0 ;
		
		while (m.find()) {
			
			String tag = markup.substring(m.start(), m.end()) ;
			
			if (tag.equals("[["))
				linkStack.add(m.start()) ;
			else {
				if (!linkStack.isEmpty()) {
					int linkStart = linkStack.lastElement() ;
					linkStack.remove(linkStack.size()-1) ;
					
					if (linkStack.isEmpty()) {
						sb.append(markup.substring(lastIndex, linkStart)) ;
						
						//we have the whole link, with other links nested inside if it's an image
						String linkMarkup = markup.substring(linkStart+2, m.start()) ;
						
						if (!linkMarkup.matches("(?s)^(\\S*?):.*"))
							sb.append("[[" + linkMarkup + "]]") ;
						
						lastIndex = m.end() ;
					}
				}
			}
		}
		
		sb.append(markup.substring(lastIndex)) ;
		
		return sb.toString() ;
	}
	
	private static String stripLink(String linkMarkup) {
		
		if (linkMarkup.matches("(?s)^(\\S*?):.*")) {
			//has prefix, so lets ignore link completely
			return "" ;
		} else {
			int pos = linkMarkup.lastIndexOf("|") ;
			
			if (pos>0) {
				//link is piped 
				return linkMarkup.substring(pos+1) ;
			} else {
				//link is not piped ;
				return linkMarkup ;
			}
		}
	}

	/**
	 * Strips all templates from the given markup; anything like {{this}}. 
	 * 
	 * @param markup the text to be stripped
	 * @return the stripped text
	 */
	public static String stripTemplates(String markup) {
		
		Vector<Integer> templateStack = new Vector<Integer>() ; 
		
		Pattern p = Pattern.compile("(\\{\\{|\\}\\})") ;
		Matcher m = p.matcher(markup) ;
		
		StringBuffer sb = new StringBuffer() ;
		int lastIndex = 0 ;
		
		while (m.find()) {
			
			String tag = markup.substring(m.start(), m.end()) ;
			
			if (tag.equals("{{"))
				templateStack.add(m.start()) ;
			else {
				if (!templateStack.isEmpty()) {
					int templateStart = templateStack.lastElement() ;
					templateStack.remove(templateStack.size()-1) ;
					
					if (templateStack.isEmpty()) {
						sb.append(markup.substring(lastIndex, templateStart)) ;
						
						//we have the whole template, with other templates nested inside					
						lastIndex = m.end() ;
					}
				}
			}
		}
		
		sb.append(markup.substring(lastIndex)) ;
		return sb.toString() ;
	}
	
	
	/**
	 * Strips all tables from the given markup; anything like {|this|}. 
	 * 
	 * @param markup the text to be stripped
	 * @return the stripped text
	 */
	public static String stripTables(String markup) {
		
		Vector<Integer> tableStack = new Vector<Integer>() ; 
		
		Pattern p = Pattern.compile("(\\{\\||\\|\\})") ;
		Matcher m = p.matcher(markup) ;
		
		StringBuffer sb = new StringBuffer() ;
		int lastIndex = 0 ;
		
		while (m.find()) {
			
			String tag = markup.substring(m.start(), m.end()) ;
			
			if (tag.equals("{|"))
				tableStack.add(m.start()) ;
			else {
				if (!tableStack.isEmpty()) {
					int templateStart = tableStack.lastElement() ;
					tableStack.remove(tableStack.size()-1) ;
					
					if (tableStack.isEmpty()) {
						sb.append(markup.substring(lastIndex, templateStart)) ;
						
						//we have the whole table, with other tables nested inside					
						lastIndex = m.end() ;
					}
				}
			}
		}
		
		sb.append(markup.substring(lastIndex)) ;
		
		return sb.toString() ;
	}
	
	
	/**
	 * Strips all <ref> tags from the given markup; both those that provide links to footnotes, and the footnotes themselves.
	 * 
	 * @param markup the text to be stripped
	 * @return the stripped text
	 */
	public static String stripRefs(String markup) {
		
		String strippedMarkup = markup.replaceAll("<ref\\\\>", "") ;					//remove simple ref tags
		strippedMarkup = strippedMarkup.replaceAll("(?s)<ref>(.*?)</ref>", "") ;			//remove ref tags and all content between them. 
		strippedMarkup = strippedMarkup.replaceAll("(?s)<ref\\s(.*?)>(.*?)</ref>", "") ; 	//remove ref tags and all content between them (with attributes).
	    
		return strippedMarkup ;
	}
	
	/**
	 * Strips all html tags and comments from the given markup. Text found between tags is not removed.
	 * 
	 * @param markup the text to be stripped
	 * @return the stripped text
	 */
	public static String stripHTML(String markup) {
		
		String strippedMarkup = stripRefs(markup) ;
		
		strippedMarkup = strippedMarkup.replaceAll("(?s)<!--(.*?)-->","") ;	//strip comments
		strippedMarkup = strippedMarkup.replaceAll("<(.*?)>", "") ;	// remove remaining tags ;	
		
		
		return strippedMarkup ;
	}
	
	
	/**
	 * Strips all links to external web pages; anything like [this] that starts with "http" or "www". 
	 * 
	 * @param markup the text to be stripped
	 * @return the stripped text
	 */
	public static String stripExternalLinks(String markup) {
		
		String strippedMarkup = markup.replaceAll("\\[(http|www)(.*?)\\]", "") ;
		return strippedMarkup ;
	}
	
	/**
	 * Strips all wiki formatting, the stuff that makes text bold, italicised, intented, listed, or made into headers. 
	 * 
	 * @param markup the text to be stripped
	 * @return the stripped text
	 */
	public static String stripFormatting(String markup) {
		
		String strippedMarkup = markup.replaceAll("'{2,}", "") ;       //remove all bold and italic markup ;
		strippedMarkup = strippedMarkup.replaceAll("={2,}","") ;	   //remove all header markup
		strippedMarkup = strippedMarkup.replaceAll("\n:+", "\n") ;	   //remove indents.
		strippedMarkup = strippedMarkup.replaceAll("\n(\\*+)", "\n") ; //remove list markers.
		
		return strippedMarkup ;
	}
	
	
	/**
	 * Collapses consecutive newlines into at most two newlines. 
	 * This is provided because stripping out templates and tables often leaves large gaps in the text.  
	 * 
	 * @param markup the text to be stripped
	 * @return the stripped text
	 */
	public static String stripExcessNewlines(String markup) {
		
		String strippedMarkup = markup.replaceAll("\n{3,}", "\n\n") ;		
		return strippedMarkup ;
	}
	
	
	/**
	 * Provides a demo of MarkupStripping
	 * 
	 * @param args an array of arguments for connecting to a wikipedia datatabase: server and database names at a minimum, and optionally a username and password
	 * @throws Exception if there is a problem with the wikipedia database.
	 */	
	public static void main(String[] args) throws Exception{
		Wikipedia wikipedia = Wikipedia.getInstanceFromArguments(args) ;
		
		if (!wikipedia.getDatabase().isContentImported()) {
			System.out.println("Page content has not been imported, so we can't demo anything.\n") ;
			return ;
		}
		
		BufferedReader in = new BufferedReader( new InputStreamReader( System.in ) );			

		while (true) {
			System.out.println("Enter article title (or enter to quit): ") ;
			String title = in.readLine() ;

			if (title == null || title.equals(""))
				break ;

			Article article = wikipedia.getArticleByTitle(title) ;
			
			if (article == null) {
				System.out.println("Could not find exact match. Searching through anchors instead") ;
				article = wikipedia.getMostLikelyArticle(title, null) ; 
			}
			
			if (article == null) {
				System.out.println("Could not find exact article. Try again") ;
			} else {
				String markup = article.getContent() ;
				
				System.out.println("\n\n========Page Markup========\n\n") ;
				System.out.println(markup) ;
				
				System.out.println("\n\n========Stripped Content========\n\n") ;
				
				markup = MarkupStripper.stripEverything(markup) ;
				System.out.println(markup) ;
			}
		}		
	}
}
