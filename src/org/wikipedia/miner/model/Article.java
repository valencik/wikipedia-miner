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

import java.util.* ; 

import org.wikipedia.miner.db.WEnvironment;
import org.wikipedia.miner.db.WDatabase.DatabaseType ;
import org.wikipedia.miner.db.WEnvironment.StatisticName;
import org.wikipedia.miner.db.struct.DbIntList;
import org.wikipedia.miner.db.struct.DbLabelForPage;
import org.wikipedia.miner.db.struct.DbLabelForPageList;
import org.wikipedia.miner.db.struct.DbLinkLocation;
import org.wikipedia.miner.db.struct.DbLinkLocationList;
import org.wikipedia.miner.db.struct.DbPage;
import org.wikipedia.miner.db.struct.DbPageLinkCounts;
import org.wikipedia.miner.db.struct.DbTranslations;
import org.wikipedia.miner.util.WikipediaConfiguration;

/**
 * Represents articles in Wikipedia; the pages that contain descriptive text regarding a particular topic. 
 */
public class Article extends Page {

	/**
	 * Data used to calculate relatedness measures. 
	 * 
	 * @see Article#getRelatednessTo(Article, EnumSet)
	 */
	public enum RelatednessDependancy{

		/**
		 * Use links made to articles to measure relatedness. You should cache {@link DatabaseType#pageLinksIn} if using this mode extensively.  
		 */
		inLinks, 

		/**
		 * Use links made from articles to measure relatedness. You should cache {@link DatabaseType#pageLinksOut} and {@link DatabaseType#pageLinkCounts} if using this mode extensively. 
		 */
		outLinks,
		
		/**
		 * Use link counts to measure relatedness. You should cache {@link DatabaseType#pageLinkCounts} if using this mode extensively. 
		 */
		linkCounts
	} ;


	/**
	 * Initialises a newly created Article so that it represents the article given by <em>id</em>.
	 * 
	 * @param env	an active WEnvironment
	 * @param id	the unique identifier of the article
	 */
	public Article(WEnvironment env, int id) {
		super(env, id) ;
	}

	protected Article(WEnvironment env, int id, DbPage pd) {
		super(env, id, pd) ;
	}

	/**
	 * Returns a array of {@link Redirect Redirects}, sorted by id, that point to this article.
	 * 
	 * @return	an array of Redirects, sorted by id
	 */
	public Redirect[] getRedirects()  {

		DbIntList tmpRedirects = env.getDbRedirectSourcesByTarget().retrieve(id) ;
		if (tmpRedirects == null || tmpRedirects.getValues() == null) 
			return new Redirect[0] ;

		Redirect[] redirects = new Redirect[tmpRedirects.getValues().size()] ;
		for (int i=0 ; i<tmpRedirects.getValues().size() ; i++)
			redirects[i] = new Redirect(env, tmpRedirects.getValues().get(i)) ;	

		return redirects ;	
	}

	/**
	 * Returns an array of {@link Category Categories} that this article belongs to. These are the categories 
	 * that are linked to at the bottom of any Wikipedia article. Note that one of these will be the article's
	 * equivalent category, if one exists.
	 * 
	 * @return	an array of Categories, sorted by id
	 */
	public Category[] getParentCategories() {

		DbIntList tmpParents = env.getDbArticleParents().retrieve(id) ;
		if (tmpParents == null || tmpParents.getValues() == null) 
			return new Category[0] ;

		Category[] parentCategories = new Category[tmpParents.getValues().size()] ;

		int index = 0 ;
		for (int id:tmpParents.getValues()) {
			parentCategories[index] = new Category(env, id) ;
			index++ ;
		}

		return parentCategories ;	
	}

	//TODO:equivalent categories
	/**
	 * Returns the {@link Category} that relates to the same concept as this article. For instance, calling 
	 * this for "6678: Cat" returns the category "799717: Cats"
	 * 
	 * Note that many articles do not have equivalent categories; they are only used when the article 
	 * describes a general topic for which there are other, more specific, articles. Consequently, 
	 * this method will often return null. 
	 * 
	 * @return	the equivalent Category, or null
	 *//*
	public Category getEquivalentCategory() {

		Category equivalentCategory = null ;

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

		return equivalentCategory ;
	}*/

	/**
	 * Returns an array of {@link Article Articles} that link to this article. These 
	 * are defined by the internal hyperlinks within article text. If these hyperlinks came via 
	 * redirects, then they are resolved.
	 * 
	 * @return	the array of Articles that link to this article, sorted by id.
	 */
	public Article[] getLinksIn() {

		DbLinkLocationList tmpLinks = env.getDbPageLinkIn().retrieve(id) ;
		if (tmpLinks == null || tmpLinks.getLinkLocations() == null) 
			return new Article[0] ;

		Article[] links = new Article[tmpLinks.getLinkLocations().size()] ;

		int index = 0 ;
		for (DbLinkLocation ll:tmpLinks.getLinkLocations()) {
			links[index] = new Article(env, ll.getLinkId()) ;
			index++ ;
		}

		return links ;		
	}

	/**
	 * Returns an array of {@link Article}s, sorted by article id, that this article 
	 * links to. These are defined by the internal hyperlinks within article text. 
	 * If these hyperlinks point to redirects, then these are resolved. 
	 * 
	 * @return	an array of Articles that this article links to, sorted by id
	 */
	public Article[] getLinksOut()  {

		DbLinkLocationList tmpLinks = env.getDbPageLinkOut().retrieve(id) ;
		if (tmpLinks == null || tmpLinks.getLinkLocations() == null) 
			return new Article[0] ;

		Article[] links = new Article[tmpLinks.getLinkLocations().size()] ;

		int index = 0 ;
		for (DbLinkLocation ll:tmpLinks.getLinkLocations()) {
			links[index] = new Article(env, ll.getLinkId()) ;
			index++ ;
		}

		return links ;	
	}

	/**
	 * Returns the title of the article translated into the language given by <em>languageCode</em>
	 * (i.e. fn, jp, de, etc) or null if translation is not available. 
	 * 
	 * @param languageCode	the (generally 2 character) language code.
	 * @return the translated title if it is available; otherwise null.
	 */	
	public String getTranslation(String languageCode)  {		

		DbTranslations t = env.getDbTranslations().retrieve(id) ;

		if (t == null)
			return null ;

		if (t.getTranslationsByLangCode() == null)
			return null ;

		return t.getTranslationsByLangCode().get(languageCode.toLowerCase()) ;
	}

	/**
	 * Returns a TreeMap associating language code with translated title for all available translations 
	 * 
	 * @return a TreeMap associating language code with translated title.
	 */	
	public TreeMap<String,String> getTranslations() {
		DbTranslations t = env.getDbTranslations().retrieve(id) ;

		if (t == null)
			return new TreeMap<String,String>() ;
		else
			return t.getTranslationsByLangCode() ;
	}

	/**
	 * <p>
	 * Calculates a weight of the semantic relation between this article and the argument one. 
	 * The stronger the semantic relation, the higher the weight returned. 
	 * i.e "6678: Cat" has a higher relatedness to "4269567: Dog" than to "27178: Shoe".
	 * <p>
	 * The measure is based on the links extending out from and/or in to each of the articles being compared, 
	 * depending on the given relatedness modes.
	 * </p>
	 * 
	 * <p>
	 * The details of this measure (and an evaluation) is described in the paper:
	 * <br/>
	 * Milne, D and Witten, I.H. (2008) An effective, low-cost measure of semantic relatedness obtained from Wikipedia links. In Proceedings of WIKIAI'08. 
	 * </p>
	 * 
	 * @param article the article to measure relatedness against
	 * @param modes the set of modes to use to calculate the measure. If more than one mode is specified, then the average will be returned.
	 * @return the weight of the semantic relation between this article and the argument one.
	 */
	public float getRelatednessTo(Article article, EnumSet<RelatednessDependancy> dependancies) {

		if (dependancies == null || dependancies.isEmpty())
			throw new IllegalArgumentException("You must specify at least one relatedness dependancy") ;

		float total = 0 ;
		int count = 0 ;

		if (dependancies.contains(RelatednessDependancy.inLinks)) {
			total += getRelatednessFromInLinks(article) ;
			count ++ ;
		}

		if (dependancies.contains(RelatednessDependancy.outLinks)) {
			total += getRelatednessFromOutLinks(article) ;
			count ++ ;
		}

		return total/count ;
	}


	/**
	 * Measures the semantic relatedness between this article and the argument one, using
	 * the modes recommended by the current Wikipedia configuration. 
	 * 
	 * @see WikipediaConfiguration#getReccommendedRelatednessModes() ;
	 * 
	 * @param article the article to measure relatedness against
	 * @return the weight of the semantic relation between this article and the argument one.
	 */
	public float getRelatednessTo(Article article) {
		EnumSet<RelatednessDependancy> modes = env.getConfiguration().getReccommendedRelatednessDependancies() ;

		return getRelatednessTo(article, modes) ;
	}



	/**
	 * @return the total number of links that are made to this article 
	 */
	public int getTotalLinksInCount()  {

		DbPageLinkCounts lc = env.getDbPageLinkCounts().retrieve(id) ;

		if (lc == null) 
			return 0 ;
		else
			return lc.getTotalLinksIn() ;
	}

	/**
	 * @return the number of distinct articles which contain a link to this article 
	 */
	public int getDistinctLinksInCount()  {

		DbPageLinkCounts lc = env.getDbPageLinkCounts().retrieve(id) ;

		if (lc == null) 
			return 0 ;
		else
			return lc.getDistinctLinksIn() ;
	}

	/**
	 * @return the total number links that this article makes to other articles 
	 */
	public int getTotalLinksOutCount() {

		DbPageLinkCounts lc = env.getDbPageLinkCounts().retrieve(id) ;

		if (lc == null) 
			return 0 ;
		else
			return lc.getTotalLinksOut() ;
	}

	/**
	 * @return the number of distinct articles that this article links to 
	 */
	public int getDistinctLinksOutCount() {

		DbPageLinkCounts lc = env.getDbPageLinkCounts().retrieve(id) ;

		if (lc == null) 
			return 0 ;
		else
			return lc.getDistinctLinksOut() ;
	}


	private float getRelatednessFromOutLinks(Article article) {

		if (getId() == article.getId()) 
			return 1 ;

		int totalArticles = env.retrieveStatistic(StatisticName.articleCount).intValue() ;

		DbLinkLocationList idListA = env.getDbPageLinkOut().retrieve(id) ; 
		DbLinkLocationList idListB = env.getDbPageLinkOut().retrieve(article.id) ; 

		if (idListA==null || idListB==null) 
			return 0 ;

		ArrayList<DbLinkLocation> linksA = idListA.getLinkLocations() ;
		ArrayList<DbLinkLocation> linksB = idListB.getLinkLocations() ;

		if (linksA==null || linksB==null) 
			return 0 ;

		int indexA = 0 ;
		int indexB = 0 ;

		ArrayList<Double> vectA = new ArrayList<Double>() ;
		ArrayList<Double> vectB = new ArrayList<Double>() ;

		while (indexA < linksA.size() || indexB < linksB.size()) {

			DbLinkLocation linkA = null ;
			DbLinkLocation linkB = null ;		

			if (indexA < linksA.size())
				linkA = linksA.get(indexA) ;

			if (indexB < linksB.size())
				linkB = linksB.get(indexB) ;

			if (linkA != null && linkB != null && linkA.getLinkId()==linkB.getLinkId()) {

				int totalLinksIn = new Article(env, linkA.getLinkId()).getTotalLinksInCount() ;
				double probability = Math.log((double)totalArticles/totalLinksIn) ;
				vectA.add(probability) ;
				vectB.add(probability) ;

				indexA ++ ;
				indexB ++ ;
			} else {

				if (linkA != null && (linkB == null || linkA.getLinkId() < linkB.getLinkId())) {

					int totalLinksIn = new Article(env, linkA.getLinkId()).getTotalLinksInCount() ;
					double probability = Math.log((double)totalArticles/totalLinksIn) ;
					vectA.add(probability) ;

					if (linkA.getLinkId() == article.getId())
						vectB.add(probability) ;
					else
						vectB.add(0.0) ;

					indexA ++ ;
				} else {
					int totalLinksIn = new Article(env, linkB.getLinkId()).getTotalLinksInCount() ;
					double probability = Math.log((double)totalArticles/totalLinksIn) ;					
					vectB.add(probability) ;

					if (linkB.getLinkId() == id)
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
			float valA = vectA.get(x).floatValue() ;
			float valB = vectB.get(x).floatValue() ;

			dotProduct = dotProduct + (valA * valB) ;
			magnitudeA = magnitudeA + (valA * valA) ;
			magnitudeB = magnitudeB + (valB * valB) ;
		}

		magnitudeA = Math.sqrt(magnitudeA) ;
		magnitudeB = Math.sqrt(magnitudeB) ;

		Double sr = Math.acos(dotProduct / (magnitudeA * magnitudeB)) ;		
		sr = (Math.PI/2) - sr ; // reverse, so 0=no relation, PI/2= same
		sr = sr / (Math.PI/2) ; // normalise, so measure is between 0 and 1 ;				

		return sr.floatValue() ;
	}


	private float getRelatednessFromInLinks(Article article) {

		if (getId() == article.getId()) 
			return 1 ;

		DbLinkLocationList idListA = env.getDbPageLinkIn().retrieve(id) ; 
		DbLinkLocationList idListB = env.getDbPageLinkIn().retrieve(article.id) ; 

		if (idListA==null || idListB==null) 
			return 0 ;

		ArrayList<DbLinkLocation> linksA = idListA.getLinkLocations() ;
		ArrayList<DbLinkLocation> linksB = idListB.getLinkLocations() ;

		if (linksA==null || linksB==null) 
			return 0 ;

		int linksBoth = 0 ;

		int indexA = 0 ;
		int indexB = 0 ;

		while (indexA < linksA.size() && indexB < linksB.size()) {

			int linkA = linksA.get(indexA).getLinkId() ;
			int linkB = linksB.get(indexB).getLinkId() ;

			if (linkA == linkB) {
				linksBoth ++ ;
				indexA ++ ;
				indexB ++ ;
			} else {

				if (linkA < linkB) {
					if (linkA == article.getId()) 
						linksBoth ++ ;

					indexA ++ ;
				} else {

					if (linkB == id) 
						linksBoth ++ ;

					indexB ++ ;
				}
			}
		}

		float a = (float)Math.log(linksA.size()) ;
		float b = (float)Math.log(linksB.size()) ;
		float ab = (float)Math.log(linksBoth) ;
		float m = (float)Math.log(env.retrieveStatistic(StatisticName.articleCount)) ;

		float sr = (Math.max(a, b) -ab) / (m - Math.min(a, b)) ;

		if (Float.isNaN(sr) || Float.isInfinite(sr) || sr > 1)
			sr = 1 ;

		sr = 1-sr ;
		return sr ;
	}


	/**
	 * Returns an array of {@link Label Labels} that have been used to refer to this article.
	 * They are sorted by the number of times each label is used.
	 * 
	 * @return an array of {@link Label Labels} that have been used to refer to this article. 
	 */
	public Label[] getLabels() {

		DbLabelForPageList tmpLabels = env.getDbLabelsForPage().retrieve(id) ; 
		if (tmpLabels == null || tmpLabels.getLabels() == null) 
			return new Label[0] ;

		Label[] labels = new Label[tmpLabels.getLabels().size()] ;

		int index = 0 ;
		for (DbLabelForPage ll:tmpLabels.getLabels()) {
			labels[index] = new Label(ll) ;
			index++ ;
		}

		return labels ;	
	}




	/**
	 * This efficiently identifies sentences within this article that contain links to the given target article. 
	 * The actual text of these sentences can be obtained using {@link Page#getSentenceMarkup(int)}
	 * 
	 * @param art the article of interest. 
	 * @return an array of sentence indexes that contain links to the given article.
	 */
	public Integer[] getSentenceIndexesMentioning(Article art) {

		DbLinkLocationList tmpLinks = env.getDbPageLinkIn().retrieve(art.getId()) ;
		if (tmpLinks == null || tmpLinks.getLinkLocations() == null) 
			return new Integer[0] ;

		DbLinkLocation key = new DbLinkLocation(id, null) ;
		int index = Collections.binarySearch(tmpLinks.getLinkLocations(), key, new Comparator<DbLinkLocation>(){
			public int compare(DbLinkLocation a, DbLinkLocation b) {
				return new Integer(a.getLinkId()).compareTo(b.getLinkId()) ;
			}
		}) ;

		if (index < 0)
			return new Integer[0] ;

		ArrayList<Integer> sentenceIndexes = tmpLinks.getLinkLocations().get(index).getSentenceIndexes() ;

		return sentenceIndexes.toArray(new Integer[sentenceIndexes.size()]) ;
	}

	/**
	 * This efficiently identifies sentences within this article that contain links to all of the given target articles. 
	 * The actual text of these sentences can be obtained using {@link Page#getSentenceMarkup(int)}
	 * 
	 * @param arts the articles of interest. 
	 * @return an array of sentence indexes that contain links to the given article.
	 */
	public Integer[] getSentenceIndexesMentioning(ArrayList<Article> arts) {


		TreeMap<Integer, Integer> sentenceCounts = new TreeMap<Integer, Integer>() ;

		//associate sentence indexes with number of arts mentioned.
		for (Article art:arts) {

			System.out.println(" - Checking art " + art) ;

			for (Integer sentenceIndex: getSentenceIndexesMentioning(art)) {

				System.out.println(" - - Adding sentence " + sentenceIndex) ;

				Integer count = sentenceCounts.get(sentenceIndex) ;
				if (count == null)
					sentenceCounts.put(sentenceIndex, 1) ;
				else
					sentenceCounts.put(sentenceIndex, count + 1) ;	
			}
		}

		//gather all sentences that mention all arts
		ArrayList<Integer> validSentences = new ArrayList<Integer>() ;
		Iterator<Map.Entry<Integer, Integer>> iter = sentenceCounts.entrySet().iterator() ;

		while (iter.hasNext()) {
			Map.Entry<Integer, Integer> e = iter.next();

			System.out.println(" - " + e.getKey() + ", " + e.getValue()) ;

			if (e.getValue() == arts.size())
				validSentences.add(e.getKey()) ;
		}

		return validSentences.toArray(new Integer[validSentences.size()]) ;
	}


	/**
	 * A label that has been used to refer to the enclosing {@link Article}. These are mined from the title of the article, the 
	 * titles of {@link Redirect redirects} that point to the article, and the anchors of links that point to the article.   
	 */
	public class Label {

		private String text ;

		private long linkDocCount ;
		private long linkOccCount ;

		private boolean fromTitle ;
		private boolean fromRedirect ;
		private boolean isPrimary ;

		protected Label(DbLabelForPage l) {

			this.text = l.getText() ;
			this.linkDocCount = l.getLinkDocCount() ;
			this.linkOccCount = l.getLinkOccCount() ;
			this.fromTitle = l.getFromTitle() ;
			this.fromRedirect = l.getFromRedirect() ;
			this.isPrimary = l.getIsPrimary() ;
		}


		/**
		 * @return the text of this label (the title of the article or redirect, or the anchor of the link
		 */
		public String getText() {
			return text ;
		}

		/**
		 * @return the number of pages that contain links that associate this label with the enclosing {@link Article}.
		 */
		public long getLinkDocCount() {
			return linkDocCount;
		}

		/**
		 * @return the number of times this label occurs as the anchor text in links that refer to the enclosing {@link Article}.
		 */
		public long getLinkOccCount() {
			return linkOccCount;
		}

		/**
		 * @return true if this label matches the title of the enclosing {@link Article}, otherwise false.
		 */
		public boolean isFromTitle() {
			return fromTitle;
		}

		/**
		 * @return true if there is a {@link Redirect} that associates this label with the enclosing {@link Article}, otherwise false.
		 */
		public boolean isFromRedirect() {
			return fromRedirect;
		}

		/**
		 * @return true if the enclosing {@link Article} is the primary, most common sense for the given label, otherwise false.
		 */
		public boolean isPrimary() {
			return isPrimary;
		}
	}



	//public static ============================================================

	/*
	public static void main(String[] args) throws Exception {


		File databaseDirectory = new File("/research/dmilne/wikipedia/db/en/20100130");



		Wikipedia w = new Wikipedia(databaseDirectory) ;




		Article nzBirds = w.getMostLikelyArticle("Birds of New Zealand", null) ;
		//Article kiwi = w.getMostLikelyArticle("Kiwi", null) ;

		/*
		DbLinkLocationList ll = w.getEnvironment().getDbPageLinkOut().retrieve(kiwi.getId()) ;

		for (DbLinkLocation l:ll.getLinkLocations()) {
			System.out.print(" - " + l.getLinkId() +  ":") ;
			for (Integer s:l.getSentenceIndexes()) 
				System.out.print(" " + s) ;
			System.out.println() ;
		}
	 */

	//System.out.println(kiwi) ;

	/*
		Article nz = w.getMostLikelyArticle("New Zealand", null) ;

		for (Article art:kiwi.getLinksOut()){

			if (art.equals(nz))
				System.out.println(" - link: " + art) ;
		}

		for (Article art:nz.getLinksIn()) {
			if (art.equals(kiwi))
				System.out.println(" - link in: " + art) ;
		}


		ArrayList<Article> arts = new ArrayList<Article>() ;
		arts.add(w.getMostLikelyArticle("Kiwi", null)) ;
		arts.add(w.getMostLikelyArticle("Takahe", null)) ;


		System.out.println(nzBirds.getMarkup()) ;


		for (Article art:arts) {
			System.out.println("retrieving sentences mentioning " + art) ;

			for (int si: nzBirds.getSentenceIndexesMentioning(art)){
				System.out.println(nzBirds.getSentenceMarkup(si)) ;
			}

		}

		System.out.println("retrieving sentences mentioning all") ;

		for (int si: nzBirds.getSentenceIndexesMentioning(arts)){
			System.out.println(nzBirds.getSentenceMarkup(si)) ;
		}



	}*/


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
