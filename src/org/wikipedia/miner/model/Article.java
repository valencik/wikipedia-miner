/*
 *    Article.java
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
import java.sql.ResultSet ;
import java.sql.Statement ;
import java.sql.SQLException ;
import java.text.DecimalFormat;
import java.util.* ; 

import org.wikipedia.miner.db.* ;
import org.wikipedia.miner.db.WikipediaEnvironment.Statistic;
import org.wikipedia.miner.util.* ;

/**
 * This class represents articles in Wikipedia; the pages that contain descriptive text regarding a particular topic. 
 * It is intended to contain all properties and methods that are relevant for an article, such as its pertinent statistics,
 * the categories it belongs to, and the articles that link to it.  
 * 
 * @author David Milne
 */
public class Article extends Page {

	/**
	 * Initialises a newly created Article so that it represents the page given by <em>id</em> and <em>title</em>.
	 * 
	 * This is the most efficient constructor as no database lookup is required.
	 * 
	 * @param database	an active WikipediaDatabase 
	 * @param id	the unique identifier of the article
	 * @param title	the (case dependent) title of the article
	 */
	public Article(WikipediaEnvironment environment, int id, String title) {
		super(environment, id, title, ARTICLE) ;
	}

	/**
	 * Initializes a newly created Article so that it represents the article given by <em>id</em>.
	 * 
	 * @param database	an active WikipediaDatabase
	 * @param id	the unique identifier of the article
	 * @	if no page is defined for the id, or if it is not an article.
	 */
	public Article(WikipediaEnvironment environment, int id) {
		super(environment, id) ;
	}
	
	/**
	 * Returns a SortedVector of Redirects that point to this article.
	 * 
	 * @return	a SortedVector of Redirects
	 * @ if there is a problem with the Wikipedia database
	 */
	public Redirect[] getRedirects() {
		
		int[] tmpRedirects = environment.getRedirects(id) ;
		if (tmpRedirects == null) 
			return new Redirect[0] ;
			
		Redirect[] redirects = new Redirect[tmpRedirects.length] ;
		for (int i=0 ; i<tmpRedirects.length ; i++)
			redirects[i] = new Redirect(environment, tmpRedirects[i]) ;	
		
		return redirects ;	
	}

	/**
	 * Returns a SortedVector of Categories that this article belongs to. These are the categories 
	 * that are linked to at the bottom of any Wikipedia article. Note that one of these will be the article's
	 * equivalent category, if one exists.
	 * 
	 * @return	a Vector of WikipediaCategories
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

	//TODO:
	/**
	 * Returns the Category that relates to the same concept as this article. For instance, calling 
	 * this for "6678: Cat" returns the category "799717: Cats"
	 * 
	 * Note that many articles do not have equivalent categories; they are only used when the article 
	 * describes a general topic for which there are other, more specific, articles. Consequently, 
	 * this method will often return null. 
	 * 
	 * @return	the equivalent Category, or null
	 * @ if there is a problem with the wikipedia database
	 */
	public Category getEquivalentCategory() {

		Category equivalentCategory = null ;

		//TODO ;
		/*
		Statement stmt = getWikipediaDatabase().createStatement() ;
		ResultSet rs = stmt.executeQuery("SELECT page_id, page_title FROM equivalence, page WHERE page_id=eq_cat AND eq_art=" + id) ;

		if (rs.first()) {
			try {
				equivalentCategory = new Category(database, rs.getInt(1), new String(rs.getBytes(2), "UTF-8")) ;
			} catch (Exception e) {} ;
		}

		rs.close() ;
		stmt.close() ;	
		*/
		return equivalentCategory ;
	}

	/**
	 * Returns a SortedVector of Articles that link to this article. These 
	 * are defined by the internal hyperlinks within article text. If these hyperlinks came via 
	 * redirects, then they are resolved.
	 * 
	 * @return	the SortedVector of Articles that this article links to
	 * @ if there is a problem with the wikipedia database
	 */
	public Article[] getLinksIn() {
		
		DbLink[] dbLinks = environment.getLinksIn(id) ;
		
		if (dbLinks == null)
			return new Article[0] ;
		
		Article[] articles = new Article[dbLinks.length] ;
		
		for (int i=0 ; i<dbLinks.length ; i++)
			articles[i] = new Article(environment, dbLinks[i].getId()) ;
		
		return articles ;
	}

	/**
	 * Returns a Vector of Articles that this article links to. These 
	 * are defined by the internal hyperlinks within article text. If these hyperlinks point
	 * to redirects, then these are resolved. 
	 * 
	 * @return	the Vector of Articles that this article links to
	 * @ if there is a problem with the wikipedia database
	 */
	public Article[] getLinksOut() {

		DbLink[] dbLinks = environment.getLinksOut(id) ;
		
		if (dbLinks == null)
			return new Article[0] ;
		
		Article[] articles = new Article[dbLinks.length] ;
		
		for (int i=0 ; i<dbLinks.length ; i++)
			articles[i] = new Article(environment, dbLinks[i].getId()) ;
		
		return articles ;
	}

	/**
	 * Returns the title of the article translated into the language given by <em>languageCode</em>
	 * (i.e. fn, jp, de, etc) or null if translation is not available. 
	 * 
	 * @param languageCode	the (generally 2 character) language code.
	 * @return the translated title if it is available; otherwise null.
	 * @ if there is a problem with the Wikipedia database
	 */	
	public String getTranslation(String languageCode)  {		
		return environment.getTranslations(id).get(languageCode) ;
	}

	/**
	 * Returns a HashMap associating language code with translated title for all available translations 
	 * 
	 * @return a HashMap associating language code with translated title.
	 * @ if there is a problem with the Wikipedia database
	 */	
	public HashMap<String,String> getTranslations() {
		return environment.getTranslations(id) ;
	}

	/**
	 * <p>
	 * Calculates a weight of the semantic relation between this article and the argument one. 
	 * The stronger the semantic relation, the higher the weight returned. 
	 * i.e "6678: Cat" has a higher relatedness to "4269567: Dog" than to "27178: Shoe".
	 * This is based on the links extending out from and in to each of the articles being compared. 
	 * </p>
	 * 
	 * <p>
	 * The details of this measure (and an evaluation) is described in the paper:
	 * <br/>
	 * Milne, D and Witten, I.H. (2008) An effective, low-cost measure of semantic relatedness obtained from Wikipedia links. In Proceedings of WIKIAI'08. 
	 * </p>
	 * 
	 * <p>
	 * If you only cache inLinks, then for efficiency's sake relatedness measures will only be calculated from them.
	 * Measures obtained only from inLinks are only marginally less accurate than those obtained from both anyway.
	 * </p>
	 * 
	 * <p>
	 * The reverse is true if you cache only outLinks, although that isnt reccomended. They take up much more memory, and 
	 * resulting measures are not as accurate. 
	 * </p>
	 * 
	 * @param article the other article of interest
	 * @return the weight of the semantic relation between this article and the argument one.
	 * @ if there is a problem with the wikipedia database
	 */
	public float getRelatednessTo(Article article) {
		
		return getRelatednessFromInLinks(article) ;
		
		/*
		if (database.areOutLinksCached() && database.areInLinksCached()) 
			return (getRelatednessFromInLinks(article) + getRelatednessFromOutLinks(article))/2 ;
			
		if (database.areOutLinksCached()) 
			return getRelatednessFromOutLinks(article) ;
		
		if (database.areInLinksCached()) 
			return getRelatednessFromInLinks(article) ;
		
		return (getRelatednessFromInLinks(article) + getRelatednessFromOutLinks(article))/2 ;
		*/
	}
	
	/**
	 * @return the total number of links that are made to this article 
	 * @
	 */
	public int getTotalLinksInCount()  {
		
		int[] linkCounts = environment.getLinkCounts(id) ;
		
		if (linkCounts == null) 
			return 0 ;
		else
			return linkCounts[2] ;
	}
	
	/**
	 * @return the number of distinct articles which contain a link to this article 
	 */
	public int getDistinctLinksInCount()  {
		
		int[] linkCounts = environment.getLinkCounts(id) ;
		
		if (linkCounts == null) 
			return 0 ;
		else
			return linkCounts[3] ;
	}
	
	/**
	 * @return the total number links that this article makes to other articles 
	 */
	public int getTotalLinksOutCount()  {
		
		int[] linkCounts = environment.getLinkCounts(id) ;
		
		if (linkCounts == null) 
			return 0 ;
		else
			return linkCounts[0] ;
	}
	
	/**
	 * @return the number of distinct articles that this article links to 
	 */
	public int getDistinctLinksOutCount()  {
		
		int[] linkCounts = environment.getLinkCounts(id) ;
		if (linkCounts == null) 
			return 0 ;
		else
			return linkCounts[1] ;
	}
	
public Section getStructureRoot() {
		
		DbStructureNode dbRoot = environment.getPageStructure(id) ;
		
		if (dbRoot == null)
			return null ;
		else
			return new Section(dbRoot) ;
	}
	
	/*
	public Vector<StructureNode> getStructurePath(int pos) {
		
		
	
	
	}*/
	
	/*
	public Vector<StructureNode> getStructurePath(int pos1, pos2) {
		
		
		
		
		
	}*/

	public double getProbabilityOfCooccuranceWith(Article article) {
		
		if (getId() == article.getId()) 
			return 0 ;
		
		DbLink[] linksA = environment.getLinksIn(id) ;
		DbLink[] linksB = environment.getLinksIn(article.getId()) ;
		
		if (linksA==null || linksB==null) 
			return 0 ;

		int linksBoth = 0 ;

		int indexA = 0 ;
		int indexB = 0 ;
		
		double aobo = this.getDistinctLinksInCount() * article.getDistinctLinksInCount() ;
		double w4 = Math.pow(this.environment.getStatisticValue(Statistic.ARTICLE_COUNT),4) ;
		
		double probability = 1 ;

		while (indexA < linksA.length && indexB < linksB.length) {

			DbLink linkA = linksA[indexA] ;
			DbLink linkB = linksB[indexB] ;
			
			int compare = linkA.compareTo(linkB) ;

			if (compare == 0) {
				
				Article sharedLink = new Article(environment, linkA.getId()) ;
				
				//if (sharedLink.exists()) {
					
					double p = (Math.pow(sharedLink.getDistinctLinksOutCount(), 2)*aobo)/w4 ;
					System.out.println(" - " + p) ;
					
					if (p>0)
						probability = probability * p ;
				//}
				indexA++ ;
				indexB++ ;
				
			} else {
				
				if (compare < 0) {
					if (linkA.getId() == article.getId()) 
						linksBoth ++ ;
					
					indexA ++ ;
				} else {
					
					if (linkB.getId() == id) 
						linksBoth ++ ;
					
					indexB ++ ;
				}
			}
		}
		
		System.out.println(probability) ;
		return probability ;
	}
	
	public int[] getSentenceBoundsSurrounding(int pos) {
		
		DbStructureNode dbRoot = environment.getPageStructure(id) ;
		if (dbRoot == null)
			return null;
		
		DbStructureNode node = dbRoot.getLowestSectionSurrounding(pos) ;
		return node.getSentenceSurrounding(pos) ;
	}
	
	
	public int[] getSentenceBoundSurrounding(int pos1, int pos2) {
		
		DbStructureNode dbRoot = environment.getPageStructure(id) ;
		if (dbRoot == null)
			return null;
		
		DbStructureNode node = dbRoot.getLowestSectionSurrounding(pos1, pos2) ;
		
		return node.getSentenceSurrounding(pos1, pos2) ;
	}
	
	public int[] getLinkPositions(Article art) {
		
		DbLink[] links = environment.getLinksOut(id) ;
		if (links == null) 
			return null ;
		
		int index = Arrays.binarySearch(links, new DbLink(art.getId(), null)) ;
		
		if (index < 0) 
			return null ;
		
		return links[index].getPositions() ;
	}
	
	
	
/*
	private double getRelatednessFromOutLinks(Article article) {
		
		if (getId() == article.getId()) 
			return 1 ;

		int totalArticles = database.getArticleCount() ;
		int[][] dataA = getLinksOutIdsAndCounts() ;
		int[][] dataB = article.getLinksOutIdsAndCounts() ;

		if (dataA.length == 0 || dataB.length == 0)
			return 0 ;

		int indexA = 0 ;
		int indexB = 0 ;

		Vector<Double> vectA = new Vector<Double>() ;
		Vector<Double> vectB = new Vector<Double>() ;

		while (indexA < dataA.length || indexB < dataB.length) {

			int idA = -1 ;
			int idB = -1 ;

			if (indexA < dataA.length)
				idA = dataA[indexA][0] ;

			if (indexB < dataB.length)
				idB = dataB[indexB][0] ;

			if (idA == idB) {
				double probability = Math.log((double)totalArticles/dataA[indexA][1]) ;
				vectA.add(probability) ;
				vectB.add(probability) ;

				indexA ++ ;
				indexB ++ ;
			} else {

				if ((idA < idB && idA > 0)|| idB < 0) {
					
					double probability = Math.log((double)totalArticles/dataA[indexA][1]) ;
					vectA.add(probability) ;
					if (idA == article.getId())
						vectB.add(probability) ;
					else
						vectB.add(0.0) ;
				
					indexA ++ ;
				} else {
					
					double probability = Math.log((double)totalArticles/dataB[indexB][1]) ;
					vectB.add(new Double(probability)) ;
					if (idB == id)
						vectA.add(probability) ;
					else
						vectA.add(0.0) ;

					indexB ++ ;
				}
			}
		}

		// calculate angle between vectors
		double dotProduct = 0 ;
		double magnitudeA = 0 ;
		double magnitudeB = 0 ;

		for (int x=0;x<vectA.size();x++) {
			double valA = ((Double)vectA.elementAt(x)).doubleValue() ;
			double valB = ((Double)vectB.elementAt(x)).doubleValue() ;

			dotProduct = dotProduct + (valA * valB) ;
			magnitudeA = magnitudeA + (valA * valA) ;
			magnitudeB = magnitudeB + (valB * valB) ;
		}

		magnitudeA = Math.sqrt(magnitudeA) ;
		magnitudeB = Math.sqrt(magnitudeB) ;

		double sr = Math.acos(dotProduct / (magnitudeA * magnitudeB)) ;		
		sr = (Math.PI/2) - sr ; // reverse, so 0=no relation, PI/2= same
		sr = sr / (Math.PI/2) ; // normalize, so measure is between 0 and 1 ;				

		return sr ;
	}*/

	
	private float getRelatednessFromInLinks(Article article) {
		
		if (getId() == article.getId()) 
			return 1 ;
		
		DbLink[] linksA = environment.getLinksIn(id) ;
		DbLink[] linksB = environment.getLinksIn(article.getId()) ;
		
		if (linksA==null || linksB==null) 
			return 0 ;

		int linksBoth = 0 ;

		int indexA = 0 ;
		int indexB = 0 ;

		while (indexA < linksA.length && indexB < linksB.length) {

			DbLink linkA = linksA[indexA] ;
			DbLink linkB = linksB[indexB] ;
			
			int compare = linkA.compareTo(linkB) ;

			if (compare == 0) {
				linksBoth ++ ;
				indexA ++ ;
				indexB ++ ;
			} else {
				
				if (compare < 0) {
					if (linkA.getId() == article.getId()) 
						linksBoth ++ ;
					
					indexA ++ ;
				} else {
					
					if (linkB.getId() == id) 
						linksBoth ++ ;
					
					indexB ++ ;
				}
			}
		}

		float a = (float)Math.log(linksA.length) ;
		float b = (float)Math.log(linksB.length) ;
		float ab = (float)Math.log(linksBoth) ;
		float m = (float)Math.log(environment.getStatisticValue(Statistic.ARTICLE_COUNT)) ;

		float sr = (Math.max(a, b) -ab) / (m - Math.min(a, b)) ;

		if (Float.isNaN(sr) || Float.isInfinite(sr) || sr > 1)
			sr = 1 ;

		sr = 1-sr ;
		return sr ;
	}

	
	/**
	 * Returns a SortedVector of AnchorTexts used to link to this page, in 
	 * descending order of use
	 * 
	 * @return see above.
	 * @ if there is a problem with the sql database
	 */
	public AnchorText[] getAnchorTexts()  {

		DbAnchorText[] dbAnchors = environment.getAnchorTexts(id) ;
		
		if (dbAnchors == null)
			return new AnchorText[0] ;
		
		AnchorText[] anchors = new AnchorText[dbAnchors.length] ;
		
		for (int i=0 ; i<dbAnchors.length ; i++)
			anchors[i] = new AnchorText(dbAnchors[i].getText(), this, dbAnchors[i].getTotalCount(), dbAnchors[i].getDistinctCount()) ;
		
		return anchors ;
	}

	/**
	 * Represents a term or phrase that is used to link to a particular page.
	 */
	public class AnchorText implements Comparable<AnchorText>{

		private String text ;
		private Page destination ;
		private int totalCount ;
		private int distinctCount ;

		/**
		 * Initializes the AnchorText
		 * 
		 * @param text the text used within the anchor
		 * @param destination the id of the article that this anchor links to
		 * @param count the number of times the given text is used to link to the given destination.
		 */
		public AnchorText(String text, Page destination, int totalCount, int distinctCount) {
			this.text = text;
			this.destination = destination ;
			this.totalCount = totalCount ;
			this.distinctCount = distinctCount ;
		}

		/**
		 * Returns the text used to make the link
		 * 
		 * @return see above.
		 */
		public String getText() {
			return text ;
		}

		/**
		 * Returns the destination page of the link
		 * 
		 * @return see above.
		 */
		public Page getDestination() {
			return destination ;
		}

		/**
		 * Returns the number of times the link is made
		 * 
		 * @return see above.
		 */
		public int getTotalCount() {
			return totalCount ;
		}
		
		public int getDistinctCount() {
			return distinctCount ;
		}

		public String toString() {
			return text ;
		}

		/**
		 * Compares this AnchorText to another, so that the AnchorText with the greatest count will be 
		 * considered smaller, and therefore occur earlier in lists.  
		 * 
		 * @param	at	the AnchorText to be compared
		 * @return	see above.
		 */
		public int compareTo(AnchorText at) {
			
			int c = new Integer(at.distinctCount).compareTo(distinctCount) ;
			if (c!=0) return c ;
			
			c = new Integer(at.totalCount).compareTo(totalCount) ;
			if (c!=0) return c ;
			
			return text.compareTo(at.text) ;
		}
	}	

	public static void main(String[] args) throws Exception {
		
		
		File berkeleyDir = new File("/Users/dmilne/Research/wikipedia/databases/simple/20080620") ;
		File luceneDir = new File("/Users/dmilne/Research/wikipedia/indexes/simple/20080620") ;
		
		Wikipedia w = new Wikipedia(berkeleyDir, luceneDir) ;
		
		Article china = w.getMostLikelyArticle("China", null) ;
		
		Vector<Article> articles = new Vector<Article>() ;
		articles.add(w.getMostLikelyArticle("Hong Kong", null)) ;
		articles.add(w.getMostLikelyArticle("Japan", null)) ;
		articles.add(w.getMostLikelyArticle("India", null)) ;
		articles.add(w.getMostLikelyArticle("Asia", null)) ;
		articles.add(w.getMostLikelyArticle("Russia", null)) ;
		articles.add(w.getMostLikelyArticle("Europe", null)) ;
		articles.add(w.getMostLikelyArticle("New Zealand", null)) ;
		articles.add(w.getMostLikelyArticle("Kiwi", null)) ;
		
		for (Article art:articles) {
			art.setWeight((float)(art.getProbabilityOfCooccuranceWith(china) * 2000000)) ;
		}
		
		Article[] sortedArticles = articles.toArray(new Article[articles.size()]) ;
		Arrays.sort(sortedArticles) ;
		
		for (Article art:sortedArticles) {
			System.out.println(art + ", " + art.getWeight()) ;
		}
		
	}
	
	
	/**
	 * Provides a demo of functionality available to Articles
	 * 
	 * @param args an array of arguments for connecting to a wikipedia database: server and database names at a minimum, and optionally a username and password
	 * @throws Exception if there is a problem with the wikipedia database.
	 *//*
	public static void main(String[] args) throws Exception {
		
		Wikipedia wikipedia = Wikipedia.getInstanceFromArguments(args) ;

		BufferedReader in = new BufferedReader( new InputStreamReader( System.in ) );	
		DecimalFormat df = new DecimalFormat("0") ;

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
				System.out.println("\n" + article + "\n") ;

				if (wikipedia.getDatabase().isContentImported()) {
					
					System.out.println(" - first sentence:") ;
					System.out.println("    - " + article.getFirstSentence(null, null)) ;
					
					System.out.println(" - first paragraph:") ;
					System.out.println("    - " + article.getFirstParagraph()) ;
				}
				
				//Category eqCategory = article.getEquivalentCategory() ;
				//if (eqCategory != null) {
				//	System.out.println("\n - equivalent category") ;
				//	System.out.println("    - " + eqCategory) ;
				//}
				
				System.out.println("\n - redirects (synonyms or very small related topics that didn't deserve a seperate article):") ;
				for (Redirect r: article.getRedirects())
					System.out.println("    - " + r);
	
				//System.out.println("\n - anchors (synonyms and hypernyms):") ;
				//for (AnchorText at:article.getAnchorTexts()) 
				//	System.out.println("    - \"" + at.getText() + "\" (used " + at.getCount() + " times)") ;
	
				System.out.println("\n - parent categories (hypernyms):") ;
				for (Category c: article.getParentCategories()) 
					System.out.println("    - " + c); 
				
				System.out.println("\n - language links (translations):") ;
				HashMap<String,String> translations = article.getTranslations() ;
				for (String lang:translations.keySet())
					System.out.println("    - \"" + translations.get(lang) + "\" (" + lang + ")") ;
				
				//System.out.println("\n - pages that this links to (related concepts):") ;
				//for (Article a: article.getLinksOut()) {
				//	System.out.println("    - " + a + " (" + df.format(article.getRelatednessTo(a)*100) + "% related)"); 
				//}
			}
			System.out.println("") ;
		}
	
	}*/
}
