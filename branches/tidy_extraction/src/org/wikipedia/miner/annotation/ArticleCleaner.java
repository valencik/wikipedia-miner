/*
 *    ArticleCleaner.java
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

package org.wikipedia.miner.annotation;

import org.wikipedia.miner.util.MarkupStripper;
import org.wikipedia.miner.model.Article;

/**
 *	A utility class for cleaning up Wikipedia articles so that they can be used to train and test disambiguation, link detection, etc.
 *
 * @author David Milne
 */
public class ArticleCleaner {

	/**
	 * all of the article will be extracted and cleaned
	 */
	public static final int ALL = 0 ;
	/**
	 * only the first sentence of the article will be extracted and cleaned
	 */
	public static final int FIRST_SENTENCE = 1;
	/**
	 * only the first paragraph of the article will be extracted and cleaned
	 */
	public static final int FIRST_PARAGRAPH = 2 ;
	

	private MarkupStripper stripper = new MarkupStripper() ;
	private String[] unwantedSections = {"see also", "references", "further sources", "further reading", "footnotes", "external links", "bibliography", "notes", "notes and references", "other websites"} ;

	/**
	 * @param article the article to clean
	 * @param snippetLength the portion of the article that is to be extracted and cleaned (ALL, FIRST_SENTENCE, or FIRST_PARAGRAPH)
	 * @return the content (or snippet) of the given article, with all markup removed except links to other articles.  
	 * @throws Exception
	 */
	public String getMarkupLinksOnly(Article article, int snippetLength) throws Exception {
		
		if (snippetLength == FIRST_SENTENCE || snippetLength == FIRST_PARAGRAPH) {
			
			String content ;
			
			if (snippetLength == FIRST_SENTENCE) 
				content = article.getFirstSentence() ;
			else
				content = article.getFirstParagraph() ;
			
			content = content.replaceAll("'{2,}", "") ; 
			return content ;
			
		} else {
			String content = article.getContent() ;
			
			content = stripper.stripAllButLinksAndEmphasis(content, null) ;
			content = stripper.stripSections(content, unwantedSections, null) ;
			content = stripper.stripSectionHeaders(content, null) ;
			
			content = content.replaceAll("'{2,}", "") ; 
			
			return content ;
		}
		
		
	}
	
	/**
	 * @param article the article to clean
	 * @param snippetLength the portion of the article that is to be extracted and cleaned (ALL, FIRST_SENTENCE, or FIRST_PARAGRAPH)
	 * @return the content of the given article, with all markup removed.  
	 * @throws Exception
	 */
	public String getCleanedContent(Article article,  int snippetLength) throws Exception{
		
		if (snippetLength == FIRST_SENTENCE || snippetLength == FIRST_PARAGRAPH) {
			
			String content ;
			
			if (snippetLength == FIRST_SENTENCE) 
				content = article.getFirstSentence() ;
			else
				content = article.getFirstParagraph() ;
			
			content = stripper.stripInternalLinks(content, null) ;
			content = content.replaceAll("'{2,}", "") ; 
			
			return content ;
	
		} else {
		
			String content = article.getContent() ;
			content = stripper.stripToPlainText(content, null) ; 
			content = stripper.stripSections(content, unwantedSections, null) ;
			content = stripper.stripSectionHeaders(content, null) ;
			
			content = content.replaceAll("'{2,}", "") ; 
		
			return content ;
		}
	}
}
