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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.*;

import org.wikipedia.miner.model.Article.AnchorText;
import org.wikipedia.miner.util.*;
import org.wikipedia.miner.util.morphology.*;


/**
 * This class serves as the main portal into a Wikipedia database, and is intended to provide convenient methods
 * for analysing it's content. 
 * 
 * @author David Milne
 */
public class Wikipedia {

	private WikipediaDatabase wikipediaDatabase ;

	/**
	 * Initializes a newly created Wikipedia and attempts to make a connection to the mysql
	 * database defined by the arguments given. In addition, it will check
	 * that the wikipedia database is complete; that all necessary tables and indexes exist.
	 * 
	 * @param databaseServer	the connection string for the server (e.g 130.232.231.053:8080 or bob:8080)
	 * @param databaseName	the name of the database (e.g <em>enwiki</em>)
	 * @param userName	the user for the sql database (null if anonymous)
	 * @param password	the users password (null if anonymous)
	 */
	public Wikipedia(String databaseServer, String databaseName, String userName, String password) throws Exception{
		wikipediaDatabase = new WikipediaDatabase(databaseServer, databaseName, userName, password) ;
	}

	/**
	 * Returns the wikipedia database that this Wikipedia instance is connected to
	 * @return the wikipedia database
	 */
	public WikipediaDatabase getDatabase() {
		return wikipediaDatabase ;
	}

	/**
	 * Returns the root Category (<a href="http://en.wikipedia.org/wiki/Category:Fundamental">Fundamental</a>), 
	 * from which all other categories can be browsed.
	 * 
	 * @return the root (fundamental)
	 * @throws SQLException
	 */
	public Category getFundamentalCategory() throws SQLException {
		return new Category(wikipediaDatabase, "Fundamental") ;
	}

	/**
	 * Returns the Page referenced by the given id. The page can be cast into the appropriate type for 
	 * more specific functionality. 
	 *  
	 * @param id	the id of the Page to retrieve.
	 * @return the Page referenced by the given id, or null if one does not exist. 
	 * @throws SQLException if there is a problem with the wikipedia database.
	 */
	public Page getPageById(int id) throws SQLException {

		String title = null ;
		int type = 0 ;

		Statement stmt = wikipediaDatabase.createStatement() ;
		ResultSet rs = stmt.executeQuery("SELECT page_title, page_type FROM page WHERE page_id=" + id) ;

		if (rs.first()) {
			try {
				title = new String(rs.getBytes(1), "UTF-8") ;
			} catch (Exception e) {} ;	

			type = rs.getInt(2) ;
		}

		rs.close() ;
		stmt.close();

		switch (type) {
		case Page.ARTICLE: 
			return new Article(wikipediaDatabase, id, title) ;
		case Page.REDIRECT: 
			return new Redirect(wikipediaDatabase, id, title) ;
		case Page.CATEGORY: 
			return new Category(wikipediaDatabase, id, title) ;
		case Page.DISAMBIGUATION: 
			return new Disambiguation(wikipediaDatabase, id, title) ;
		}

		return null ;
	}

	/**
	 * Returns the Article referenced by the given (case sensitive) title. If the title
	 * matches a redirect, this will be resolved to return the redirect's target.
	 * 
	 * The given title must be matched exactly to return an article. If you want some more leeway,
	 * use getMostLikelyArticle() instead. 
	 *  
	 * @param title	the title of an Article (or it's redirect).
	 * @return the Article referenced by the given title, or null if one does not exist
	 * @throws SQLException if there is a problem with the wikipedia database
	 */
	public Article getArticleByTitle(String title) throws SQLException {
		
		try {
			return new Article(wikipediaDatabase, title) ;			
		} catch (Exception e) {};
		
		try {
			Redirect r = new Redirect(wikipediaDatabase, title) ;
			return r.getTarget() ;			
		} catch (Exception e) {};
		
		return null ;
	}
	
	/**
	 * Returns the Category referenced by the given (case sensitive) title. 
	 * 
	 * The given title must be matched exactly to return a Category. 
	 *  
	 * @param title	the title of an Article (or it's redirect).
	 * @return the Article referenced by the given title, or null if one does not exist
	 * @throws SQLException if there is a problem with the wikipedia database
	 */
	public Category getCategoryByTitle(String title) throws SQLException {
		
		try {
			return new Category(wikipediaDatabase, title) ;			
		} catch (Exception e) {};
				
		return null ;
	}

	/**
	 * Returns the most likely article for a given term. For example, searching for "tree" will return
	 * the article "30579: Tree", rather than "30806: Tree (data structure)" or "7770: Christmas tree"
	 * This is defined by the number of times the term is used as an anchor for links to each of these 
	 * destinations. 
	 *  
	 * An optional morphological processor (may be null) can be used to alter the way anchor texts are 
	 * retrieved (eg via stemming or casefolding) 
	 * 
	 * @param term	the term to obtain articles for
	 * @param mp	an optional morphologicalProcessor to modify how the term is searched for. 
	 * 
	 * @return the most likely sense of the given term.
	 * 
	 * @throws SQLException if there is a problem with the wikipedia database
	 */
	public Article getMostLikelyArticle(String term, MorphologicalProcessor mp) throws SQLException{

		//wikipediaDatabase.checkMorphologicalProcessor(mp) ;

		Anchor anch = new Anchor(term, mp, wikipediaDatabase) ;

		if (anch == null) 
			return null ;

		Article article = null ;

		for (Page sense:anch.getSenses()) {
			if (sense.getType() == Page.ARTICLE) {
				article = new Article(wikipediaDatabase, sense.getId(), sense.getTitle()) ;
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
	 * 
	 * This order is calculated from the number of times the text is used to link to each article; 
	 * The most obvious well-known sense (the one which is the destination for most "club" links) 
	 * is first in the list.
	 *  
	 * An optional morphological processor (may be null) can be used to alter the way anchor texts 
	 * are retrieved (e.g. via stemming or casefolding) 
	 * 
	 * @param term	the term to obtain articles for
	 * @param mp	the morphologicalProcessor by which the term is compared to wikipedia anchors.
	 * 
	 * @return the SortedVector of all relevant Articles, ordered by commoness of the link being made.
	 * 
	 * @throws SQLException if there is a problem with the wikipedia database
	 */
	public SortedVector<Article> getWeightedArticles(String term, MorphologicalProcessor mp) throws SQLException{

		Anchor anch = new Anchor(term, mp, wikipediaDatabase) ;

		if (anch == null) 
			return null ;

		SortedVector<Article> articles = new SortedVector<Article>() ;

		for (Anchor.Sense sense:anch.getSenses()) {
			if (sense.getType() == Page.ARTICLE) {
				Article article = new Article(wikipediaDatabase, sense.getId(), sense.getTitle()) ;
				article.setWeight(sense.getProbability()) ;
				articles.add(article, true) ;
			}
		}

		return articles ;
	}

	/**
	 * Returns a SortedVector of all Articles which are about the given term, sorted by how well 
	 * known they are as a sense of the term, and how strongly they relate to the given context 
	 * articles. 
	 * 
	 * For example, searching for "club" returns "10830: Football team" at the top of the list
	 * if you provide "3928: Ball" and "26853: Sport" as context, and "305482: Nightclub" if you 
	 * provide "18839: Music" and "7885: Dance".
	 *  
	 * An optional morphological processor (may be null) can be used to alter the way anchor texts 
	 * are retrieved (e.g. via stemming or casefolding) 
	 * 
	 * @param term	the term to obtain articles for
	 * @param mp	the morphologicalProcessor by which the term is compared to wikipedia anchors.
	 * @param contextArticles	a collection of articles that relate to the intended meaning of the term.
	 * 
	 * @return the SortedVector of all relevant Articles, ordered how well-known they are and how they relate to context articles.
	 * 
	 * @throws SQLException if there is a problem with the wikipedia database
	 */
	public SortedVector<Article> getWeightedArticles(String term, MorphologicalProcessor mp, Collection<Article> contextArticles) throws SQLException{

		Anchor anch = new Anchor(term, mp, wikipediaDatabase) ;
		SortedVector<Anchor.Sense> senses = anch.getSenses() ;

		SortedVector<Article> articles = new SortedVector<Article>() ;

		if (senses.size() == 0)
			return articles ;

		if (senses.size() == 1){
			Anchor.Sense sense = senses.first() ;
			articles.add(new Article(wikipediaDatabase, sense.getId(), sense.getTitle()), true) ;
			return articles ;
		}

		for (Anchor.Sense sense: anch.getSenses()) {

			//if (sense.getType() == Page.ARTICLE) {
			Article candidate = new Article(wikipediaDatabase, sense.getId(), sense.getTitle()) ;

			double relatedness = 0 ;
			double obviousness = sense.getProbability() ;

			for (Article context: contextArticles) {
				double r = candidate.getRelatednessTo(context) ;
				relatedness = relatedness + r ;
			}
			candidate.setWeight(relatedness+obviousness) ;
			articles.add(candidate, false) ;
		}
		return articles ;
	}

	/**
	 * Returns a SortedVector of all Articles which are about the given term, sorted by how well 
	 * known they are as a sense of the term, and how strongly they relate to the given context 
	 * terms.
	 * 
	 * This is just a convenience method, which resolves each term with getMostLikelyArticle(), and then
	 * calls the above method. 
	 *  
	 * An optional morphological processor (may be null) can be used to alter the way anchor texts are retrieved 
	 * (eg via stemming or casefolding) 
	 * 
	 * @param term	the term to obtain articles for
	 * @param mp	the morphologicalProcessor by which the term is compared to wikipedia anchors.
	 * @param contextTerms	an array of phrases or terms that relate to the intended meaning of the term.
	 * 
	 * @return the SortedVector of all relevant Articles, ordered by commoness of the link being made and relatedness to context articles.
	 * 
	 * @throws SQLException if there is a problem with the wikipedia database
	 */
	public SortedVector<Article> getWeightedArticles(String term, MorphologicalProcessor mp, String[] contextTerms) throws SQLException{
		System.out.print(" - context: " ) ;
		Vector<Article> contextArticles = new Vector<Article>() ;

		for (String ct: contextTerms) {
			System.out.print(ct + " ");
			Article ca = getMostLikelyArticle(ct, mp) ;
			if (ca != null){
				contextArticles.add(ca) ;
			}

		}
		System.out.println() ;
		return getWeightedArticles(term, mp, contextArticles) ;
	}
	
	/**
	 * A convenience method that returns an instance of Wikipedia, initialized according to the given 
	 * array of String arguments. 
	 * 
	 * @param args	an array at least 2 String arguments; the connection string of the wikipedia 
	 * database server, the name of the wikipedia database and (optionally, if annonymous access
	 * is not allowed) a username and password.
	 * @throws Exception if the arguments are invalid, or if there is a problem connecting to the database.
	 */
	public static Wikipedia getInstanceFromArguments(String[] args) throws Exception{
				
		if (args.length < 2) 
			throw new Exception("You must specify at least a server and database. Username and password are optional") ;		
		
		if (args.length == 2)
			return new Wikipedia(args[0], args[1], null, null) ;
		
		if (args.length == 3)
			return new Wikipedia(args[0], args[1], args[2], null) ;
				
		return new Wikipedia(args[0], args[1], args[2], args[3]) ;
	}

	/**
	 * Provides a demo of the functionality provided by this toolkit.
	 * 
	 * @param args	an array of 2 or 4 String arguments; the connection string of the wikipedia 
	 * database server, the name of the wikipedia database and (optionally, if annonymous access
	 * is not allowed) a username and password for the database.
	 */
	public static void main(String[] args) throws Exception{
		Wikipedia self = Wikipedia.getInstanceFromArguments(args) ;
		
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
			
			Cleaner cleaner = new Cleaner() ;

			System.out.println("All articles for \"" + term + "\":") ;
			SortedVector<Article> articles = self.getWeightedArticles(term, cleaner) ;

			if (articles.size() == 0) {
				System.out.println(" - no articles found") ;
				return ;
			}

			for (Article article: articles) 
				System.out.println(" - " + article) ;

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
			
			Article bestArticle = (Article)self.getWeightedArticles(term, cleaner, ca).first() ;
			System.out.println(" - " + bestArticle) ;

			System.out.println("\nDetails for Article " + bestArticle) ;

			System.out.println(" - Anchors:") ;
			for (AnchorText at:bestArticle.getAnchorTexts()) {
				System.out.println("   - " + at.getText() + " (used " + at.getCount() + " times)") ;
			}

			System.out.println(" - Redirects:") ;
			for (Redirect r: bestArticle.getRedirects()) 
				System.out.println("   - " + r) ;

			System.out.println(" - Translations:") ;
			HashMap<String,String> translations = bestArticle.getTranslations() ;
			for (String lang:translations.keySet()){
				System.out.println("   - " + lang + ", " + translations.get(lang)) ;
			}

			System.out.println(" - Parent categories:") ;
			for (Category c: bestArticle.getParentCategories()) 
				System.out.println("   - " + c) ;

			System.out.println(" - Articles linked to:") ;
			for (Article a: bestArticle.getLinksOut()) 
				System.out.println("   - " + a) ;
				
		}
	}
}
