/*
 *    Wikipedia.java
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

package org.wikipedia.miner.model;

import java.io.*;
import java.util.*;

import org.wikipedia.miner.db.* ;
import org.wikipedia.miner.db.WikipediaEnvironment.Statistic;
import org.wikipedia.miner.model.Article.AnchorText;
import org.wikipedia.miner.util.*;
import org.wikipedia.miner.util.text.*;

import com.sleepycat.je.DatabaseException;


/**
 * This class serves as the main portal into a Wikipedia database, and is intended to provide convenient methods
 * for analyzing it's content. 
 * 
 * @author David Milne
 */
public class Wikipedia {

	private WikipediaEnvironment environment ;

	/**
	 * Initializes a newly created Wikipedia and attempts to make a connection to the mysql
	 * database defined by the arguments given. In addition, it will check
	 * that the Wikipedia database is complete; that all necessary tables and indexes exist.
	 * 
	 * @param databaseServer	the connection string for the server (e.g 130.232.231.053:8080 or bob:8080)
	 * @param databaseName	the name of the database (e.g <em>enwiki</em>)
	 * @param userName	the user for the SQL database (null if anonymous)
	 * @param password	the users password (null if anonymous)
	 * @throws Exception if there is a problem connecting to the database, or if the database is not complete.
	 */
	public Wikipedia(File databaseDirectory) throws Exception{
		environment = new WikipediaEnvironment(databaseDirectory, false) ; 
	}
	
	public Wikipedia(WikipediaEnvironment environment) {
		this.environment = environment ;
	}

	/**
	 * @return the Wikipedia database that this is connected to
	 */
	public WikipediaEnvironment getEnvironment() {
		return environment ;
	}

	/**
	 * Returns the root Category (<a href="http://en.wikipedia.org/wiki/Category:Fundamental">Fundamental</a>), 
	 * from which all other categories can be browsed.
	 * 
	 * @return the root (fundamental) category
	 */
	public Category getRootCategory() {
		return new Category(environment, environment.getStatisticValue(Statistic.ROOT_ID)) ;
	}

	/**
	 * Returns the Page referenced by the given id. The page can be cast into the appropriate type for 
	 * more specific functionality. 
	 *  
	 * @param id	the id of the Page to retrieve.
	 * @return the Page referenced by the given id, or null if one does not exist. 
	 */
	public Page getPageById(int id) {
		return Page.createPage(environment, id) ;
	}

	/**
	 * Returns the Article referenced by the given (case sensitive) title. If the title
	 * matches a redirect, this will be resolved to return the redirect's target.
	 * <p>
	 * The given title must be matched exactly to return an article. If you want some more leeway,
	 * use getMostLikelyArticle() instead. 
	 *  
	 * @param title	the title of an Article (or it's redirect).
	 * @return the Article referenced by the given title, or null if one does not exist
	 */
	public Article getArticleByTitle(String title) {
		
		title = title.substring(0,1).toUpperCase() + title.substring(1) ;
		Anchor anchor = new Anchor(environment, title) ;
		
		Article fromTitle = null ;
		Article fromRedirect = null ;
		
		for (Anchor.Sense sense:anchor.getSenses()) {
			if (sense.matchesTitle()) {
				fromTitle = sense ;
				break ;
			}
			
			if (sense.matchesRedirect())
				fromRedirect = sense ;
		}
		
		if (fromTitle != null) 
			return fromTitle ;
		else
			return fromRedirect ;
	}
	
	/**
	 * Returns the Category referenced by the given (case sensitive) title. 
	 * 
	 * The given title must be matched exactly to return a Category. 
	 *  
	 * @param title	the title of an Article (or it's redirect).
	 * @return the Article referenced by the given title, or null if one does not exist
	 */
	public Category getCategoryByTitle(String title) {
		
		//TODO
		
		return null ;
	}

	/**
	 * Returns the most likely article for a given term. For example, searching for "tree" will return
	 * the article "30579: Tree", rather than "30806: Tree (data structure)" or "7770: Christmas tree"
	 * This is defined by the number of times the term is used as an anchor for links to each of these 
	 * destinations. 
	 *  <p>
	 * An optional text processor (may be null) can be used to alter the way anchor texts are 
	 * retrieved (e.g. via stemming or case folding) 
	 * 
	 * @param term	the term to obtain articles for
	 * @param tp	an optional TextProcessor to modify how the term is searched for. 
	 * 
	 * @return the most likely sense of the given term.
	 * 
	 * @throws DatabaseException if the Wikipedia database has not been prepared
	 * for the given text processor.
	 */
	public Article getMostLikelyArticle(String term, TextProcessor tp)throws DatabaseException{

		//TODO:check text processors
		//if (tp != null)
		//	database.checkTextProcessor(tp) ;

		Anchor anch = new Anchor(environment, term, tp) ;

		if (anch == null) 
			return null ;

		Article article = null ;

		for (Anchor.Sense sense:anch.getSenses()) {
			if (sense.getType() == Page.ARTICLE) {
				article = sense ;
				break ;
			}
		}

		return article ;
	}

	/**
	 * Returns a SortedVector of all Articles which are about the given term, sorted by how well 
	 * known they are as a sense of the term. For example, searching for "club" returns
	 * the article about associations of people, followed by articles about nightclubs, 
	 * football teams, the type of weapon, etc. 
	 * <p>
	 * This order is calculated from the number of times the text is used to link to each article; 
	 * The most obvious well-known sense (the one which is the destination for most "club" links) 
	 * is first in the list.
	 * <p>
	 * An optional text processor (may be null) can be used to alter the way anchor texts 
	 * are retrieved (e.g. via stemming or case folding) 
	 * 
	 * @param term	the term to obtain articles for
	 * @param tp	the TextProcessor by which the term is compared to Wikipedia anchors.
	 * 
	 * @return the SortedVector of all relevant Articles, ordered by commoness of the link being made.
	 * 
	 * @throws DatabaseException if there is a problem with the Wikipedia database
	 */
	public TreeSet<Article> getWeightedArticles(String term, TextProcessor tp) throws DatabaseException{

		//TODO:check text processor
		
		Anchor anch = new Anchor(environment, term, tp) ;
		TreeSet<Article> articles = new TreeSet<Article>() ;

		for (Anchor.Sense sense:anch.getSenses()) {
			if (sense.getType() == Page.ARTICLE) {
				Article article = new Article(environment, sense.getId(), sense.getTitle()) ;
				article.setWeight(sense.getProbability()) ;
				articles.add(article) ; 
			}
		}

		return articles ;
	}

	/**
	 * Returns a SortedVector of all Articles which are about the given term, sorted by how well 
	 * known they are as a sense of the term, and how strongly they relate to the given context 
	 * articles. 
	 * <p>
	 * For example, searching for "club" returns "10830: Football team" at the top of the list
	 * if you provide "3928: Ball" and "26853: Sport" as context, and "305482: Nightclub" if you 
	 * provide "18839: Music" and "7885: Dance".
	 *  <p>
	 * An optional text processor (may be null) can be used to alter the way anchor texts 
	 * are retrieved (e.g. via stemming or casefolding) 
	 * 
	 * @param term	the term to obtain articles for
	 * @param tp	the TextProcessor by which the term is compared to wikipedia anchors.
	 * @param contextArticles	a collection of articles that relate to the intended meaning of the term.
	 * @return the SortedVector of all relevant Articles, ordered how well-known they are and how they relate to context articles.
	 * 
	 * @throws DatabaseException if there is a problem with the wikipedia database
	 */
	public TreeSet<Article> getWeightedArticles(String term, TextProcessor tp, Collection<Article> contextArticles) throws DatabaseException {

		Anchor anch = new Anchor(environment, term, tp) ;
		Anchor.Sense[] senses = anch.getSenses() ;

		TreeSet<Article> articles = new TreeSet<Article>() ;

		if (senses.length == 0)
			return articles ;

		if (senses.length == 1){
			articles.add(senses[0]) ;
			return articles ;
		}

		for (Anchor.Sense sense:senses) {
			Article candidate = new Article(environment, sense.getId()) ;

			float relatedness = 0 ;
			float obviousness = sense.getProbability() ;

			for (Article context: contextArticles) {
				float r = candidate.getRelatednessTo(context) ;
				relatedness = relatedness + r ;
			}
			candidate.setWeight(relatedness+obviousness) ;
			articles.add(candidate) ;
		}
		return articles ;
	}

	/**
	 * Returns a SortedVector of all Articles which are about the given term, sorted by how well 
	 * known they are as a sense of the term, and how strongly they relate to the given context 
	 * terms.
	 * <p>
	 * This is just a convenience method, which resolves each context term with getMostLikelyArticle(), and then
	 * calls the above method. 
	 * <p>
	 * An optional morphological processor (may be null) can be used to alter the way anchor texts are retrieved 
	 * (e.g. via stemming or casefolding) 
	 * 
	 * @param term	the term to obtain articles for
	 * @param tp	the TextProcessor by which the term is compared to wikipedia anchors.
	 * @param contextTerms	an array of phrases or terms that relate to the intended meaning of the term.
	 * 
	 * @return the SortedVector of all relevant Articles, ordered by commonness of the link being made and relatedness to context articles.
	 * 
	 * @throws DatabaseException if there is a problem with the wikipedia database
	 */
	public TreeSet<Article> getWeightedArticles(String term, TextProcessor tp, String[] contextTerms) throws DatabaseException{
		System.out.print(" - context: " ) ;
		Vector<Article> contextArticles = new Vector<Article>() ;

		for (String ct: contextTerms) {
			System.out.print(ct + " ");
			Article ca = getMostLikelyArticle(ct, tp) ;
			if (ca != null){
				contextArticles.add(ca) ;
			}

		}
		System.out.println() ;
		return getWeightedArticles(term, tp, contextArticles) ;
	}
	
	/**
	 * A convenience method for quickly finding out if the given text is ever used as an anchor
	 * in Wikipedia. If this returns false, then all of the getArticle methods will return null or empty sets. 
	 * 
	 * @param text the text to search for
	 * @param tp an optional TextProcessor (may be null)
	 * @return true if there is an anchor corresponding to the given text, otherwise false
	 * @throws DatabaseException if there is a problem with the Wikipedia database
	 */
	public boolean isAnchor(String text, TextProcessor tp) throws DatabaseException {
		
		return environment.getAnchor(text, tp)  != null ;
	}
	
	/**
	 * @return an iterator for all pages in the database, in order of ascending ids.
	 */
	public PageIterator getPageIterator() {
		return new PageIterator(environment) ;
	}
	
	/**
	 * @param pageType the type of page of interest (ARTICLE, CATEGORY, REDIRECT or DISAMBIGUATION_PAGE)
	 * @return an iterator for all pages in the database of the given type, in order of ascending ids.
	 */
	public PageIterator getPageIterator(short pageType) {
		return new PageIterator(environment, pageType) ;		
	}

	/**
	 * Provides a demo of the functionality provided by this toolkit.
	 * 
	 * @param args	an array of 2 or 4 String arguments; the connection string of the Wikipedia 
	 * database server, the name of the Wikipedia database and (optionally, if anonymous access
	 * is not allowed) a username and password for the database.
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		
		File dataDir = new File("/Users/dmilne/Research/wikipedia/databases/en/20090822") ;
		Wikipedia self = new Wikipedia(dataDir) ;
		
		BufferedReader in = new BufferedReader( new InputStreamReader( System.in ) );			
				
		while (true) {
			System.out.println("Enter term to search for (or press ENTER to quit): ") ;
			
			String term = in.readLine() ;
			
			if (term == null || term.equals(""))
				break ;
			
			Vector<String> context = new Vector<String>() ;
			
			while (true) {
				System.out.println("Enter context term (or press ENTER to search for \"" + term + "\"):") ;
				String contextTerm = in.readLine() ;
				
				if (contextTerm == null || contextTerm.equals(""))
					break ;
				
				context.add(contextTerm) ;
			}
			
			TextProcessor tp = null ;
			
			if (self.isAnchor(term, tp)) {
				
				System.out.println("All articles for \"" + term + "\":") ;
				TreeSet<Article> articles = self.getWeightedArticles(term, tp) ;

				for (Article article: articles) 
					System.out.println(" - " + article + "," + article.getWeight()) ;

				String cs = "" ;
				String[] ca = new String[context.size()] ;

				if (context.size() > 0) {
					int index = 0 ;
					for (String ct:context) {
						cs = cs + ct + ", " ;
						ca[index] = ct ;
						index ++ ;
					}
					cs = cs.substring(0, cs.length() - 2) ;
				}

				System.out.println("\nBest article for \"" + term + "\" given {" + cs + "} as context: ") ;

				Article bestArticle = (Article)self.getWeightedArticles(term, tp, ca).first() ;
				System.out.println(" - " + bestArticle) ;

				System.out.println("\nDetails for Article " + bestArticle) ;
				
				System.out.println() ;
				System.out.println(bestArticle.getFirstParagraph()) ;

				System.out.println(" - Anchors:") ;
				for (AnchorText at:bestArticle.getAnchorTexts()) {
					System.out.println("   - " + at.getText() + " (used " + at.getTotalCount() + " times)") ;
				}

				System.out.println("\n - Redirects:") ;
				for (Redirect r: bestArticle.getRedirects()) 
					System.out.println("\t" + r) ;

				System.out.println("\n\n - Translations:") ;
				HashMap<String,String> translations = bestArticle.getTranslations() ;
				for (String lang:translations.keySet())
					System.out.println("\t" + translations.get(lang) + " (" + lang + ")") ;
				
				System.out.println("\n\n - Parent categories:") ;
				for (Category c: bestArticle.getParentCategories()) 
					System.out.println("\t" + c) ;
				
				System.out.println("\n\n - Articles links out:") ;
				for (Article a: bestArticle.getLinksOut()) 
					System.out.println("\t" + a) ;
				
				System.out.println("\n\n - Articles links in:") ;
				for (Article a: bestArticle.getLinksIn()) 
					System.out.println("\t" + a) ;
				
				System.out.println() ;
			} else {
				System.out.println("I have no idea what you are talking about") ;
			}
		}
	}
}
