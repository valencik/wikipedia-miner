/*
 *    Category.java
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

import java.util.* ;
import org.wikipedia.miner.db.*; 

/**
 * This class represents categories in Wikipedia; the pages that exist to organize articles that discuss related topics. 
 * It is intended to contain all properties and methods that are relevant for a category, such as its pertinent statistics,
 * the categories and articles it contains, and the categories it belongs to.  
 * 
 * @author David Milne
 */
public class Category extends Page {

	/**
	 * Initialises a newly created Category so that it represents the page given by <em>id</em> and <em>title</em>.
	 * 
	 * This is the most efficient constructor as no database lookup is required.
	 * 
	 * @param database	an active WikipediaDatabase 
	 * @param id	the unique identifier of the category
	 * @param title	the (case dependent) title of the category
	 */
	public Category(WikipediaEnvironment environment, int id, String title) {
		super(environment, id, title, CATEGORY) ;
	}

	/**
	 * Initializes a newly created Category so that it represents the category given by <em>id</em>.
	 * 
	 * @param database	an active WikipediaDatabase
	 * @param id	the unique identifier of the category
	 * @	if no page is defined for the id, or if it is not an article.
	 */
	public Category(WikipediaEnvironment environment, int id) {
		super(environment, id) ;
	}

	/**
	 * Returns the Article that relates to the same concept as this category. Note that many categories 
	 * do not have equivalent articles; they to not relate to a single concept, and exist only to organize the 
	 * articles and categories it contains. 
	 * i.e <em>Rugby Teams</em> may have an equivalent article, but <em>Rugby Teams by region</em> is unlikely to.
	 * In this case <em>null</em> will be returned.
	 * 
	 * @return	the equivalent Article, or null
	 */ 
	public Article getEquivalentArticle()  {
		Article equivalentArticle = null ;

		//TODO:
		/*
		Statement stmt = getWikipediaDatabase().createStatement() ;
		ResultSet rs = stmt.executeQuery("SELECT page_id, page_title FROM equivalence, page WHERE page_id=eq_art AND eq_cat=" + id) ;

		if (rs.first()) {
			try {
				equivalentArticle = new Article(database, rs.getInt(1), new String(rs.getBytes(2), "UTF-8")) ;
			} catch (Exception e) {} ;
		}

		rs.close() ;
		stmt.close() ;	
		 */
		return equivalentArticle ;
	}

	/**
	 * Returns a SortedVector of Categories that this category belongs to. These are the categories 
	 * that are linked to at the bottom of any Wikipedia category. 
	 * 
	 * @return	a SortedVector of Categories
	 * @ if there is a problem with the Wikipedia database
	 */
	public Category[] getParentCategories()  {
		
		int[] tmpParents = environment.getParents(id) ;
		if (tmpParents == null) 
			return new Category[0] ;
			
		Category[] parentCategories = new Category[tmpParents.length] ;
		for (int i=0 ; i<tmpParents.length ; i++)
			parentCategories[i] = new Category(environment, tmpParents[i]) ;	
		
		return parentCategories ;	
	}
	
	/**
	 * Returns a SortedVector of Categories that this category contains. These are the categories 
	 * that are presented in alphabetical lists in any Wikipedia category. 
	 * 
	 * @return	a SortedVector of Categories
	 * @ if there is a problem with the Wikipedia database
	 */
	public Category[] getChildCategories()  {
		
		int[] tmpChildren = environment.getChildCategories(id) ;
		if (tmpChildren == null) 
			return new Category[0] ;
			
		Category[] childCategories = new Category[tmpChildren.length] ;
		for (int i=0 ; i<tmpChildren.length ; i++)
			childCategories[i] = new Category(environment, tmpChildren[i]) ;	
		
		return childCategories ;	
	}

	/**
	 * Returns true if the argument article is a child of this category, otherwise false
	 * 
	 * @param page the page of interest
	 * @return true if the argument page is a child of this category, otherwise false
	 */
	public boolean contains(Article article) {

		int[] tmpChildren = environment.getChildArticles(id) ;
		
		if (tmpChildren == null) 
			return false ;
		
		int index = Arrays.binarySearch(tmpChildren, article.getId()) ;
		return (index >= 0) ;	
	}
	
	/**
	 * Returns true if the argument category is a child of this category, otherwise false
	 * 
	 * @param page the page of interest
	 * @return true if the argument page is a child of this category, otherwise false
	 */
	public boolean contains(Category category) {

		int[] tmpChildren = environment.getChildCategories(id) ;
		
		if (tmpChildren == null) 
			return false ;
		
		int index = Arrays.binarySearch(tmpChildren, category.getId()) ;
		return (index >= 0) ;	
	}

	/**
	 * Returns an ordered Vector of Articles that belong to this category.  
	 * 
	 * @return	a Vector of Articles
	 * @ if there is a problem with the wikipedia database
	 */
	public Article[] getChildArticles()  {

		int[] tmpChildren = environment.getChildArticles(id) ;
		if (tmpChildren == null) 
			return new Article[0] ;
			
		Article[] childArticles = new Article[tmpChildren.length] ;
		for (int i=0 ; i<tmpChildren.length ; i++)
			childArticles[i] = new Article(environment, tmpChildren[i]) ;	
		
		return childArticles ;		
	}
	
	/**
	 * Provides a demo of functionality available to Categories
	 * 
	 * @param args an array of arguments for connecting to a wikipedia database: server and database names at a minimum, and optionally a username and password
	 * @throws Exception if there is a problem with the wikipedia database.
	 *//*
	public static void main(String[] args) throws Exception{

		Wikipedia wikipedia = Wikipedia.getInstanceFromArguments(args) ;

		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));	

		while (true) {
			System.out.println("Enter category title (or press ENTER to quit): ") ;
			String title = in.readLine() ;

			if (title == null || title.equals(""))
				break ;

			Category category = wikipedia.getCategoryByTitle(title) ; 

			if (category == null) {
				System.out.println("Could not find category. Try again") ; 
			}else {

				System.out.println("Category: " + category) ; 
				
				if (wikipedia.getDatabase().isContentImported()) {
					
					System.out.println(" - first sentence:") ;
					System.out.println("    - " + category.getFirstSentence(null, null)) ;
					
					System.out.println(" - first paragraph:") ;
					System.out.println("    - " + category.getFirstParagraph()) ;
				}

				//Article eqArticle = category.getEquivalentArticle() ;
				//if (eqArticle != null) {
				//	System.out.println("\n - equivalent article") ;
				//	System.out.println("    - " + eqArticle) ;
				//}
				
				System.out.println("\n - parent categories (broader topics): ") ;
				for (Category c: category.getParentCategories()) 
					System.out.println("    - " + c) ; 
				
				System.out.println("\n - child categories (narrower topics): ") ;
				for (Category c: category.getChildCategories()) 
					System.out.println("    - " + c) ; 

				System.out.println("\n - child articles (narrower topics): ") ;
				for (Article a: category.getChildArticles()) 
					System.out.println("    - " + a) ; 
			}
			System.out.println("") ;
		}
	}*/
}
