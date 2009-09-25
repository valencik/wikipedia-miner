/*
 *    Page.java
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
import java.sql.*;
import java.util.Arrays;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.wikipedia.miner.db.*;
import org.wikipedia.miner.db.WikipediaEnvironment.Statistic;
import org.wikipedia.miner.util.*;


/**
 * This class provides properties and methods that are relevant for all pages in Wikipedia. 
 * It may relate to an article, a redirect to an article, a disambiguation page or a category. 
 * <p>
 * The type of a page can be examined so that an instance of the appropriate sub-class can be obtained 
 * for additional functionality: e.g. if (page.getType == Page.ARTICLE) Article art = (Article)page ;
 * 
 * @author David Milne
 */
public abstract class Page implements Comparable<Page>{

	protected WikipediaEnvironment environment ;

	protected int id ;
	
	protected short type ;
	protected String title ;
	protected boolean detailsSet ;
	

	protected float weight = -1 ;

	/**
	 * the page type corresponding to Articles
	 */
	public static final short ARTICLE = 1 ;

	/**
	 * the page type corresponding to Categories
	 */
	public static final short CATEGORY = 2 ;

	/**
	 * the page type corresponding to Redirects
	 */
	public static final short REDIRECT = 3 ;

	/**
	 * the page type corresponding to DisambiguationPages
	 */
	public static final short DISAMBIGUATION = 4 ;

	/**
	 * Initialises a newly created Page so that it represents the page given by <em>id</em>, <em>title</em> and <em>type</em>.
	 * 
	 * This is the most efficient page constructor as no database lookup is required.
	 * 
	 * @param	database	an active WikipediaDatabase
	 * @param	id	the unique identifier of the page
	 * @param	title	the (case dependent) title of the page
	 * @param	type	the type of the page (ARTICLE, CATEGORY, REDIRECT or DISAMBIGUATION_PAGE)
	 */
	public Page(WikipediaEnvironment environment, int id, String title, short type)  {
		this.environment = environment ;
		this.id = id ;
		this.title = title ;
		this.type = type ;
		this.detailsSet = true ;
	}

	/**
	 * Initializes a newly created Page so that it represents the page given by <em>id</em>. This is also an efficient
	 * constructor, since 
	 * 
	 * @param	database	an active WikipediaDatabase
	 * @param	id	the unique identifier of the Wikipedia page
	 * @ if there is a problem with the Wikipedia database.
	 */
	public Page(WikipediaEnvironment environment, int id) {
		this.environment = environment ;
		this.id = id ;
		this.detailsSet = false ;
	}
	
	public boolean exists() {
		if (!detailsSet) 
			setDetails() ;
		
		return (title == null) ;
	}
	
	
	/**
	 * Sets the weight by which this page will be compared to others.
	 * 
	 * @param weight  the weight by which this page will be compared to others.
	 */
	public void setWeight(float weight) {
		this.weight = weight ;
	}

	/**
	 * Gets the weight by which this page is compared to others.
	 * 
	 * @return the weight by which this page is compared to others.
	 */	
	public float getWeight() {
		return weight ;
	}

	public boolean equals(Object o) {
		Page p = (Page) o ;
		return p.getId() == id ;
	}

	/**
	 * Compares this page to another. If weights are defined for both pages, then the page with the larger 
	 * weight will be considered smaller (and thus appear earlier in sorted lists). Otherwise, the comparison is made based on their unique identifiers. 
	 * 
	 * @param	p	the Page to be compared
	 * @return	see above.
	 */
	public int compareTo(Page p) {

		if (p.getId() == id)
			return 0 ;

		if (p.weight >= 0 && weight >= 0 && p.weight != weight)
			return -1 * (new Float(weight)).compareTo(p.weight) ;

		return (new Integer(id)).compareTo(p.getId()) ;
	}

	/**
	 * Returns a string representation of this page, in the format "<em>id</em> - <em>title</em>".
	 * 
	 * @return a string representation of the page
	 */
	public String toString() {
		String s = getId() + ": " + getTitle() ;
		return s ;
	}

	/**
	 * Returns the database environment in which details of this page is stored.
	 * 
	 * @return the database environment
	 */
	protected WikipediaEnvironment getEnvironment() {
		return environment;
	}

	/**
	 * Returns the unique identifier for this page.
	 * 
	 * @return the unique identifier
	 */
	public int getId() {
		return id;
	}

	/**
	 * Returns the title of this page. 
	 * 
	 * @return the title
	 */
	public String getTitle() {
		if (!detailsSet) setDetails() ;
		
		return title;
	}

	/**
	 * Returns the type of the page, which may be an ARTICLE, CATEGORY, REDIRECT, or DISAMBUGATION
	 * 
	 * @return	the type of the page
	 */
	public short getType() {
		if (!detailsSet) setDetails() ;
		
		return type;
	}

	private void setDetails() {
		DbPage pd = environment.getPageDetails(id) ;
		
		title = pd.getTitle() ;
		type = pd.getType() ;
		detailsSet = true ;
	}
	
	/**
	 * Returns the generality of the page (a function of how far down the category tree it is located).
	 * 
	 * @return	the generality of the page
	 * @ if there is a problem with the Wikipedia database
	 */
	public float getGenerality() {

		Integer depth = environment.getDepth(id) ;
		int maxDepth = environment.getStatisticValue(Statistic.MAX_DEPTH) ;
		
		if (depth == null) {
			return -1 ;
		} else {
			return 1-((float)depth/maxDepth) ; 
		}
	}

	/**
	 * Returns the content of the page, marked up in raw media wiki form. You can then use Markup stripper to
	 * get the parts you are interested in. 
	 * 
	 * @return	content of the page, in raw media wiki format.
	 */
	public String getContent() {

		return environment.getPageContent(id) ;
	}
	
	

	/**
	 * Returns the first sentence from the content of this page, cleaned of all markup except links and 
	 * basic formating. 
	 * This generally serves as a definition of the concept or concepts for which this article, 
	 * disambiguation page or category was written.
	 * 
	 * @param paragraph this is more efficient if you have already gathered the firstParagraph. If not, just use null.
	 * @param ss this is more efficient if you have already constructed a sentence splitter. If not, just use null.
	 * @return the first sentence on this page.
	 * @ if page content has not been imported, or if there is another problem with the Wikipedia database
	 * @throws Exception if there is a problem splitting the text into sentences.
	 */
	public String getFirstSentence() {
		
		String content = getContent() ;
		
		DbStructureNode struct = environment.getPageStructure(id) ;
		
		if (struct == null)
			return null ;
		
		while (struct.getChildren() != null) {
			struct = struct.getChildren()[0] ;
		}
		
		int[] sentenceBreaks = struct.getSentenceBreaks() ;
		if (sentenceBreaks == null)
			return null ;
	
		String sentence = content.substring(sentenceBreaks[0], sentenceBreaks[1]) ;
		
		return sentence ;
	}

	/**
	 * Returns the first paragraph from the content of this page, cleaned of all markup except links and 
	 * basic formating. 
	 * This generally serves as a more specific definition of the concept or concepts for which this 
	 * article, disambiguation page or category was written.
	 * 
	 * @return the first paragraph on this page.
	 * @ if page content has not been imported, or if there is another problem with the Wikipedia database
	 */
	public String getFirstParagraph()  {
		
		String content = getContent() ;
		
		if (content == null) return null ;
		
		DbStructureNode struct = environment.getPageStructure(id) ;
		
		if (struct == null)
			return null ;
		
		while (struct.getChildren() != null) {
			struct = struct.getChildren()[0] ;
		}
		
		int[] sentenceBreaks = struct.getSentenceBreaks() ;
		if (sentenceBreaks == null)
			return null ;
	
		String paragraph = content.substring(sentenceBreaks[0], sentenceBreaks[sentenceBreaks.length-1]) ;
		
		return paragraph ;
	}

	/**
	 * Returns the title without the text used to differentiate between ambiguous terms.
	 * e.g. returns <em>Plane</em> for both <em>Plane (mathematics)</em> and <em>Plane (vehicle)</em>
	 * 
	 * @return the title without scope text
	 */
	public String getTitleWithoutScope() {
		
		Pattern p = Pattern.compile("(.*)\\((.*)\\)") ;
		Matcher m = p.matcher(this.getTitle()) ;
		
		if (m.matches())
			return m.group(1) ;
		else
			return this.getTitle();
	}

	/**
	 * Returns the text found within parenthesis in the title.
	 * This typically indicates scope; to differentiate an ambiguous title: 
	 * 
	 * e.g. Plane (mathematics) v.s. Plane (vehicle). 
	 * 
	 * @return the parenthesisText, or null if none is found.
	 */
	public String getScope() {
		Pattern p = Pattern.compile("(.*)\\((.*)\\)") ;
		Matcher m = p.matcher(this.getTitle()) ;
		
		if (m.matches())
			return m.group(2) ;
		else
			return null;
	}

	/**
	 * Provides a demo of functionality available to Pages
	 * 
	 * @param args an array of arguments for connecting to a wikipedia datatabase: server and database names at a minimum, and optionally a username and password
	 * @throws Exception if there is a problem with the wikipedia database.
	 *//*
	public static void main(String[] args) throws Exception{
		Wikipedia wikipedia = Wikipedia.getInstanceFromArguments(args) ;

		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));	

		while (true) {
			System.out.println("Enter article title (or press ENTER to quit): ") ;
			String title = in.readLine() ;

			if (title == null || title.equals(""))
				break ;

			Article art = wikipedia.getMostLikelyArticle(title, null) ;

			System.out.println("Page: " + art) ;
			System.out.println(" - title without scope: \"" + art.getTitleWithoutScope() + "\"") ;
			System.out.println(" - scope: \"" + art.getScope() + "\"") ;
			System.out.println(" - generality: " + art.getGenerality()) ;
			System.out.println("") ;

			System.out.println(art.getContent()) ;
		}
	}*/

	/**
	 * Instantiates the appropriate subclass of Page given the supplied parameters
	 * 
	 * @param database an active Wikipedia database
	 * @param id the id of the page
	 * @return the instantiated page, which can be safely cast as appropriate
	 */
	public static Page createPage(WikipediaEnvironment environment, int id) {

		Page p = null ;
		
		DbPage pd = environment.getPageDetails(id) ;

		switch (pd.getType()) {
		case Page.ARTICLE:
			p = new Article(environment, id, pd.getTitle()) ;
			break ;
		case Page.REDIRECT:
			p = new Redirect(environment, id, pd.getTitle()) ;
			break ;
		case Page.DISAMBIGUATION:
			p = new Disambiguation(environment, id, pd.getTitle()) ;
			break ;
		case Page.CATEGORY:
			p = new Category(environment, id, pd.getTitle()) ;
			break ;
		}

		return p ;
	}
	
	
	/**
	 * Instantiates the appropriate subclass of Page given the supplied parameters
	 * 
	 * @param database an active Wikipedia database
	 * @param id the id of the page
	 * @param title the title of the page
	 * @param type the type of the page (ARTICLE, CATEGORY, REDIRECT or DISAMBIGUATION_PAGE)
	 * @return the instantiated page, which can be safely cast as appropriate
	 */
	public static Page createPage(WikipediaEnvironment environment, int id, String title, int type) {

		Page p = null ;

		switch (type) {
		case Page.ARTICLE:
			p = new Article(environment, id, title) ;
			break ;
		case Page.REDIRECT:
			p = new Redirect(environment, id, title) ;
			break ;
		case Page.DISAMBIGUATION:
			p = new Disambiguation(environment, id, title) ;
			break ;
		case Page.CATEGORY:
			p = new Category(environment, id, title) ;
			break ;
		}

		return p ;
	}
}
