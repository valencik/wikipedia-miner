/*
 *    ArticleSet.java
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
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.*;

import org.apache.log4j.Logger;
import org.wikipedia.miner.db.WEnvironment.StatisticName;
import org.wikipedia.miner.model.*;
import org.wikipedia.miner.model.Page.PageType;

/**
 * @author David Milne
 *
 *	A set of Wikipedia articles that can be used to train and test disambiguators, linkDetectors, etc. 
 * Can either be generated randomly from Wikipedia, or loaded from file.
 */
public class ArticleSet extends TreeSet<Article> {
	
	//TODO: This screams out for the builder design pattern
	
	private static final long serialVersionUID = 6142971965290887331L;
	
	//private TreeSet<Integer> articleIds = new TreeSet<Integer>() ;
	private MarkupStripper stripper = new MarkupStripper() ;
	
	public ArticleSet() {
		super() ;
		
		
		
	}
	
	/**
	 * Loads this article set from file. The file must contain a list of article ids, separated by newlines. 
	 * If the file is comma separated, then only the first column is used.
	 * 
	 * @param file the file containing article ids.
	 * @throws IOException if the file cannot be read.
	 */
	public ArticleSet(File file, Wikipedia wikipedia) throws IOException{
		
		//articleIds = new TreeSet<Integer>() ;

		BufferedReader reader = new BufferedReader(new FileReader(file)) ;
		String line  ;

		while ((line = reader.readLine()) != null) {
			String[] values = line.split("\t") ;
			int id = new Integer(values[0].trim()) ;
			add((Article)wikipedia.getPageById(id)) ;
		}

		reader.close();		
	}
	
	/**
	 * Generates a set of articles randomly from Wikipedia, given some constraints on what is an acceptable article.
	 * <p>
	 * This first gathers all articles that satisfy the minInLink and minOutLink constraints, and then randomly samples from
	 * these to produce the final set of articles which satisfy all constraints.
	 * <p>
	 * The length of time this takes is very variable. It will work fastest if the minInLink and minOutLink constraints are strict, and 
	 * the other constraints are loose.
	 * <p>
	 * You can ignore any of the constraints by setting them to -1 ;
	 * 
	 * @param wikipedia	an instantiated instance of Wikipedia.
	 * @param size	the desired number of articles
	 * @param minInLinks	the minimum number of links that must be made to an article
	 * @param minOutLinks	the minimum number of links that an article must make
	 * @param minLinkProportion the minimum proportion of links (over total words) that articles must contain
	 * @param maxLinkProportion the maximum proportion of links (over total words) that articles must contain
	 * @param minWordCount  the minimum number of words allowed in an article
	 * @param maxWordCount the maximum number of words allowed in an article
	 * @param maxListProportion the maximum proportion of list items (over total line count) that an article may contain. 
	 */
	public ArticleSet(Wikipedia wikipedia, int size, int minInLinks, int minOutLinks, float minLinkProportion, float maxLinkProportion, int minWordCount, int maxWordCount, float maxListProportion, Pattern mustMatch, Pattern mustNotMatch, ArticleSet exclude) {
		
		Vector<Article> roughCandidates = getRoughCandidates(wikipedia, minInLinks, minOutLinks) ;
		
		buildFromRoughCandidates(wikipedia, roughCandidates, size, minInLinks, minOutLinks, minLinkProportion, maxLinkProportion, minWordCount, maxWordCount, maxListProportion, mustMatch, mustNotMatch, exclude) ;
	}
	
	protected void buildFromRoughCandidates(Wikipedia wikipedia, Vector<Article> roughCandidates, int size, int minInLinks, int minOutLinks, float minLinkProportion, float maxLinkProportion, int minWordCount, int maxWordCount, float maxListProportion, Pattern mustMatch, Pattern mustNotMatch, ArticleSet exclude) {
		
		DecimalFormat df = new DecimalFormat("#0.00 %") ;
		
		int totalRoughCandidates = roughCandidates.size();
		
		ProgressTracker pn = new ProgressTracker(totalRoughCandidates, "Refining candidates (ETA is worst case)", ArticleSet.class) ;
		
		
		
		double lastWarningProgress = 0 ;
		
		while (roughCandidates.size() > 0) {
			
			pn.update() ;
			
			if (size() == size)
				break ; //we have enough ids
			
			//pop a random id
			Integer index = (int)Math.floor(Math.random() * roughCandidates.size()) ;
			Article art = roughCandidates.elementAt(index) ;
			roughCandidates.removeElementAt(index) ;
									
			if (isArticleValid(art, minLinkProportion, maxLinkProportion, minWordCount, maxWordCount, maxListProportion, mustMatch, mustNotMatch, exclude)) 
				add(art) ;
			
			
			// warn user if it looks like we wont find enough valid articles
			double roughProgress = 1-((double) roughCandidates.size()/totalRoughCandidates) ;
			if (roughProgress >= lastWarningProgress + 0.01) {
				double fineProgress = (double)size()/size ;
			
				if (roughProgress > fineProgress) {
					System.err.println("ArticleSet | Warning : we have exhausted " + df.format(roughProgress) + " of the available pages and only gathered " + df.format(fineProgress*100) + " of the articles needed.") ;
					lastWarningProgress = roughProgress ;
				}
			}
		}
		
		if (size() < size)
			System.err.println("ArticleSet | Warning: we could only find " + size() + " suitable articles.") ;
		
	}

	/**
	 * @return the set of article ids, in ascending order.
	 *//*
	public ArrayList<Integer> getArticleIds() {
		return articleIds ;
	}*/
	
	/**
	 * Saves this list of article ids in a text file, separated by newlines. 
	 * If the file exists already, it will be overwritten.
	 * 
	 * @param file the file in which this set is to be saved
	 * @throws IOException if the file cannot be written to.
	 */
	public void save(File file) throws IOException{
		BufferedWriter writer = new BufferedWriter(new FileWriter(file)) ;
		
		for (Article art: this) 
			writer.write(art.getId() + "\n") ;
		
		writer.close() ;
	}
		
	private static Vector<Article> getRoughCandidates(Wikipedia wikipedia, int minInLinks, int minOutLinks)  {
		
		Vector<Article> articles = new Vector<Article>() ;
		int totalArticles = wikipedia.getEnvironment().retrieveStatistic(StatisticName.articleCount).intValue() ;
		
		ProgressTracker pn = new ProgressTracker(totalArticles, "Gathering rough candidates", ArticleSet.class) ;
		
		Iterator<Page> i = wikipedia.getPageIterator(PageType.article) ;
		
		while (i.hasNext()) {
			Article art = (Article)i.next() ;
			pn.update() ;
			
			if (minOutLinks >= 0 && art.getLinksOut().length < minOutLinks)
				continue ;
			
			if (minInLinks >= 0 && art.getLinksIn().length < minInLinks)
				continue ;
			
			articles.add(art) ;
		}
		
		return articles ;
	}
		
	private boolean isArticleValid(Article art, double minLinkProportion, double maxLinkProportion, int minWordCount, int maxWordCount, double maxListProportion, Pattern mustMatch, Pattern mustNotMatch, ArticleSet exclude) {
			
		Logger.getLogger(ArticleSet.class).debug("Evaluating " + art) ;
		
		
		
		//we don't want any disambiguations
		if (art.getType() == PageType.disambiguation) {
			Logger.getLogger(ArticleSet.class).debug(" - rejected due to disambiguation") ;
			return false ;	
			
		}
		
		if (this.contains(art)) {
			Logger.getLogger(ArticleSet.class).debug(" - rejected due to exclusion list") ;
			return false ;	
		}
		
		//TODO: check that list identification works
		//if (art.getType() == PageType.list) 
		//	return false ;	
	
		//check if there are any other constraints
		if (minLinkProportion < 0 && maxLinkProportion < 0 && minWordCount < 0 && maxWordCount < 0 && maxListProportion < 0)
			return true ;
		
		// get and prepare markup
		String markup = art.getMarkup() ;
		
		if (markup == null)
			return false ;
		
		if (mustMatch != null) {
			Matcher m = mustMatch.matcher(markup) ;
			
			if (!m.find()) {
				Logger.getLogger(ArticleSet.class).debug(" - rejected due to mustMatch pattern") ;
				return false ;	
			}
		}
		
		if (mustNotMatch != null) {
			Matcher m = mustNotMatch.matcher(markup) ;
			
			if (m.find()) {
				Logger.getLogger(ArticleSet.class).debug(" - rejected due to mustNotMatch pattern") ;
				return false ;	
			}
		}
		
		markup = stripper.stripToPlainText(markup, null) ; 
		
		markup = stripper.stripExcessNewlines(markup) ;
		
		
		if (maxListProportion >= 0) {
			//we need to count lines and list items
			
			String[] lines = markup.split("\n") ;
			
			int lineCount = 0 ;
			int listCount = 0 ;
			
			for (String line: lines) {
				line = line.replace(':', ' ') ;
				line = line.replace(';', ' ') ;
				
				line = line.trim() ;
				
				if (line.length() > 5) {
					lineCount++ ;

					if (line.startsWith("*") || line.startsWith("#")) 
						listCount++ ;			
				}
			}
			
			float listProportion = ((float)listCount) / lineCount ;
			if (listProportion > maxListProportion) {
				Logger.getLogger(ArticleSet.class).debug(" - rejected for max list proportion " + (listProportion)) ;
				return false ;
			}
		}
		
				
		if (minWordCount >= 0 || maxWordCount >= 0 || minLinkProportion >= 0 || maxLinkProportion >= 0 ) {
			//we need to count words
			
			int wordCount = 0 ;
					
			Pattern wordPattern = Pattern.compile("\\W(\\w+)\\W") ; 
			Matcher wordMatcher = wordPattern.matcher(markup) ;
		
			while (wordMatcher.find()) 			
				wordCount++ ;
		
			if (wordCount < minWordCount && minWordCount != -1) {
				Logger.getLogger(ArticleSet.class).debug(" - rejected for min wordcount " + (wordCount)) ;
				return false ;
				
			}
			
			if (wordCount > maxWordCount && maxWordCount != -1) {
				Logger.getLogger(ArticleSet.class).debug(" - rejected for max wordcount " + (wordCount)) ;
				return false ;
			}
			
			int linkCount = art.getLinksOut().length ;
			float linkProportion = (float)linkCount/wordCount ;
			
			if (minLinkProportion != -1 && linkProportion < minLinkProportion) {
				Logger.getLogger(ArticleSet.class).debug(" - rejected for min link proportion " + (linkProportion)) ;
				return false ;
			}
			
			if (maxLinkProportion != -1 && linkProportion > maxLinkProportion) {
				Logger.getLogger(ArticleSet.class).debug(" - rejected for max link proportion " + (linkProportion)) ;
				return false ;
			}
		}
		
		return true ;
	}
	
	public static ArticleSet[] buildExclusiveArticleSets(int[] sizes, Wikipedia wikipedia, int minInLinks, int minOutLinks, float minLinkProportion, float maxLinkProportion, int minWordCount, int maxWordCount, float maxListProportion, Pattern mustMatch, Pattern mustNotMatch) {
		

		
		
		ArticleSet sets[] = new ArticleSet[sizes.length] ;
		
		ArticleSet gatheredSoFar = new ArticleSet() ;
		
		Vector<Article> roughCandidates = getRoughCandidates(wikipedia, minInLinks, minOutLinks) ;
		
		for (int i=0 ; i<sizes.length ; i++) {
			sets[i] = new ArticleSet() ;
			
			sets[i].buildFromRoughCandidates(wikipedia, roughCandidates, sizes[i], minInLinks, minOutLinks, minLinkProportion, maxLinkProportion, minWordCount, maxWordCount, maxListProportion, mustMatch, mustNotMatch, gatheredSoFar) ;
			
			gatheredSoFar.addAll(sets[i]) ;
		}
		
		return sets ;
	}
}
