/*
 *    Comparer.java
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

package org.wikipedia.miner.service;

import gnu.trove.TIntHashSet;
import gnu.trove.TLongHashSet;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.*;

import org.wikipedia.miner.model.*;
import org.wikipedia.miner.util.*;
import org.wikipedia.miner.util.text.*;

import com.sleepycat.je.DatabaseException;


/**
 * This service measures the semantic relatedness between terms.
 * 
 *  @author David Milne
 */
public class Comparer {

	private WikipediaMinerServlet wms ;

	//private boolean defaultShowDetails = false ;
	private int defaultMaxArticlesInCommon = 50 ;
	private int defaultMaxSnippets = 10 ;

	/**
	 * Initializes a new Comparer
	 * @param wms the servlet that hosts this service
	 */
	public Comparer(WikipediaMinerServlet wms) {
		this.wms = wms;
	}

	/**
	 * @return false: the default behavior is to not show details of how terms are compared. 
	 *//*
	public boolean getDefaultShowDetails() {
		return defaultShowDetails ;
	}
	  */

	/**
	 * @return the default maximum number of articles in common to show.
	 */
	public int getDefaultMaxArticlesInCommon() {
		return defaultMaxArticlesInCommon ;
	}

	/**
	 * @return the default maximum number of snippets to show.
	 */
	public int getDefaultMaxSnippets() {
		return defaultMaxSnippets ;
	}

	/**
	 * @return an Element description of this service; what it does, and what parameters it takes.
	 */
	public Element getDescription() {

		Element description = wms.doc.createElement("Description") ;
		description.setAttribute("task", "compare") ;

		description.appendChild(wms.createElement("Details", "<p>This service measures the semantic relatedness between two terms or a set of page ids. From this you can tell, for example, that New Zealand has more to do with <a href=\"" + wms.context.getInitParameter("service_name") + "?task=compare&details=true&term1=New Zealand&term2=Rugby\">Rugby</a> than <a href=\"" + wms.context.getInitParameter("service_name") + "?task=compare&details=true&term1=New Zealand&term2=Soccer\">Soccer</a>, or that Geeks are more into <a href=\"" + wms.context.getInitParameter("service_name") + "?task=compare&details=true&term1=Geek&term2=Computer Games\">Computer Games</a> than the <a href=\"" + wms.context.getInitParameter("service_name") + "?task=compare&details=true&term1=Geek&term2=Olympic Games\">Olympic Games</a> </p>"
				+ "<p>The relatedness measures are calculated from the links going into and out of each page. Links that are common to both pages are used as evidence that they are related, while links that are unique to one or the other indicate the opposite. The relatedness measure is symmetric, so comparing <i>a</i> to <i>b</i> is the same as comparing <i>b</i> to <i>a</i>. </p>" )) ;

		Element group1 = wms.doc.createElement("ParameterGroup") ;
		description.appendChild(group1) ;

		Element paramTerm1 = wms.doc.createElement("Parameter") ;
		paramTerm1.setAttribute("name", "term1") ;
		paramTerm1.appendChild(wms.doc.createTextNode( "The first of two terms (or phrases) to compare.")) ;
		group1.appendChild(paramTerm1) ;

		Element paramTerm2 = wms.doc.createElement("Parameter") ;
		paramTerm2.setAttribute("name", "term2") ;
		paramTerm2.appendChild(wms.doc.createTextNode( "The second of two terms (or phrases) to compare.")) ;
		group1.appendChild(paramTerm2) ;

		Element group2 = wms.doc.createElement("ParameterGroup") ;
		description.appendChild(group2) ;

		Element paramIds = wms.doc.createElement("Parameter") ;
		paramIds.setAttribute("name", "ids") ;
		paramIds.appendChild(wms.doc.createTextNode("A set of page ids to compare, delimited by commas. For efficiency, the results will be returned in comma delimited form rather than xml, with one line for each comparison.")) ;
		group2.appendChild(paramIds) ;

		/*
		Element paramShowDetails = wms.doc.createElement("Parameter") ;
		paramShowDetails.setAttribute("name", "showDetails") ;
		paramShowDetails.setAttribute("optional", "true") ;
		paramShowDetails.appendChild(wms.doc.createTextNode("Specifies whether the details of a relatedness comparison (all of the senses and links that were considered) will be shown. This is much more expensive than merely showing the result of the comparison, so please only obtain the details if you will use them.")) ;
		paramShowDetails.setAttribute("default", String.valueOf(getDefaultShowDetails())) ; 
		description.appendChild(paramShowDetails) ;
		 */

		Element paramSensesShow = wms.doc.createElement("Parameter") ;
		paramSensesShow.setAttribute("name", "showSenses") ;
		paramSensesShow.setAttribute("optional", "true") ;
		paramSensesShow.appendChild(wms.doc.createTextNode("Specifies whether to return details of which sense was chosen for each of the terms of interest. Only valid when comparing two terms")) ;
		paramSensesShow.setAttribute("default", String.valueOf(false)) ; 
		description.appendChild(paramSensesShow) ;

		Element paramArtsInCommonShow = wms.doc.createElement("Parameter") ;
		paramArtsInCommonShow.setAttribute("name", "showArticlesInCommon") ;
		paramArtsInCommonShow.setAttribute("optional", "true") ;
		paramArtsInCommonShow.appendChild(wms.doc.createTextNode("Specifies whether to return a list of articles which mention both of the topics of interest. Only valid when comparing two ids, or two terms")) ;
		paramArtsInCommonShow.setAttribute("default", String.valueOf(false)) ; 
		description.appendChild(paramArtsInCommonShow) ;

		Element paramArtsInCommonCount = wms.doc.createElement("Parameter") ;
		paramArtsInCommonCount.setAttribute("name", "maxArticlesInCommon") ;
		paramArtsInCommonCount.setAttribute("optional", "true") ;
		paramArtsInCommonCount.appendChild(wms.doc.createTextNode("The maximum number of articles to return which mention both of the topics of interest. Only valid when comparing two ids, or two terms")) ;
		paramArtsInCommonCount.setAttribute("default", String.valueOf(getDefaultMaxArticlesInCommon())) ; 
		description.appendChild(paramArtsInCommonCount) ;

		Element paramSnippetsShow = wms.doc.createElement("Parameter") ;
		paramSnippetsShow.setAttribute("name", "showArticlesInCommon") ;
		paramSnippetsShow.setAttribute("optional", "true") ;
		paramSnippetsShow.appendChild(wms.doc.createTextNode("Specifies whether to return a list of sentence snippets which mention both of the topics of interest. Only valid when comparing two ids, or two terms")) ;
		paramSnippetsShow.setAttribute("default", String.valueOf(false)) ; 
		description.appendChild(paramSnippetsShow) ;

		Element paramSnippetsCount = wms.doc.createElement("Parameter") ;
		paramSnippetsCount.setAttribute("name", "maxArticlesInCommon") ;
		paramSnippetsCount.setAttribute("optional", "true") ;
		paramSnippetsCount.appendChild(wms.doc.createTextNode("The maximum number of sentence snippets to return which mention both of the topics of interest. Only valid when comparing two ids, or two terms")) ;
		paramSnippetsCount.setAttribute("default", String.valueOf(getDefaultMaxSnippets())) ; 
		description.appendChild(paramSnippetsCount) ;

		return description ;
	}

	//TODO: add a method for comparing a set of article ids.

	/**
	 * Measures the relatedness between two terms, and 
	 * 
	 * @param term1 the first term to compare
	 * @param term2 the second term to compare
	 * @param details true if the details of a relatedness comparison (all of the senses and links that were considered) are needed, otherwise false.
	 * @param linkLimit the maximum number of page links to return when presenting the details of a relatedness comparison.
	 * @return an Element message of how the two terms relate to each other
	 * @throws Exception
	 */
	public Element compare(String term1, String term2, boolean getSenses, boolean getMutualLinks, boolean getSnippets, int mutualLinkLimit, int snippetLimit, int format, int linkFormat) throws Exception {

		Element response = wms.doc.createElement("CompareResponse") ;

		if (term1 == null || term2 == null) {
			response.setAttribute("unspecifiedParameters", "true") ;
			return response ;
		}
		
		response.setAttribute("term1", term1) ;
		response.setAttribute("term2", term2) ;

		Anchor anchor1 = new Anchor(wms.wikipedia.getEnvironment(), term1, wms.tp) ;
		Anchor.Sense[] senses1 = anchor1.getSenses() ; 

		if (senses1.length == 0) {
			response.setAttribute("unknownTerm", term1) ; 
			return response ;
		}

		Anchor anchor2 = new Anchor(wms.wikipedia.getEnvironment(), term2, wms.tp) ;
		Anchor.Sense[] senses2 = anchor2.getSenses() ; 

		if (senses2.length == 0) {
			response.setAttribute("unknownTerm", term2) ; 
			return response ;
		}

		Anchor.DisambiguatedSensePair disambiguatedSenses = anchor1.disambiguateAgainst(anchor2) ;

		float sr = disambiguatedSenses.getRelatedness() ;

		response.setAttribute("relatedness", wms.df.format(sr)) ;

		Anchor.Sense sense1 = disambiguatedSenses.getSenseA() ;
		Anchor.Sense sense2 = disambiguatedSenses.getSenseB() ;



		if (getSenses) {

			Element xmlSense1 = wms.doc.createElement("Sense1");
			xmlSense1.setAttribute("title", sense1.getTitle()) ;
			xmlSense1.setAttribute("id", String.valueOf(sense1.getId())) ;		
			xmlSense1.setAttribute("candidates", String.valueOf(senses1.length)) ;

			String firstSentence = null;
			try { 
				firstSentence = sense1.getFirstSentence() ;
				firstSentence = wms.definer.format(firstSentence, Definer.FORMAT_HTML, Definer.LINK_TOOLKIT) ;
			} catch (Exception e) {} ;

			if (firstSentence != null) 
				xmlSense1.appendChild(wms.createElement("FirstSentence", firstSentence)) ;

			response.appendChild(xmlSense1) ;



			Element xmlSense2 = wms.doc.createElement("Sense2");
			xmlSense2.setAttribute("title", sense2.getTitle()) ;
			xmlSense2.setAttribute("id", String.valueOf(sense2.getId())) ;
			xmlSense2.setAttribute("candidates", String.valueOf(senses2.length)) ;

			firstSentence = null;
			try { 
				firstSentence = sense2.getFirstSentence() ;
				firstSentence = wms.definer.format(firstSentence, Definer.FORMAT_HTML, Definer.LINK_TOOLKIT) ;
			} catch (Exception e) {} ;

			if (firstSentence != null) 
				xmlSense2.appendChild(wms.createElement("FirstSentence", firstSentence)) ;

			response.appendChild(xmlSense2) ;
		}

		if (getMutualLinks || getSnippets)
			addMutualLinksOrSnippets(response, sense1, sense2, getMutualLinks, getSnippets, mutualLinkLimit, snippetLimit, format, linkFormat) ;

		return response ;
	}

	public Element compare(int id1, int id2, boolean getMutualLinks, boolean getSnippets, int mutualLinkLimit, int snippetLimit, int format, int linkFormat) throws Exception {

		Element response = wms.doc.createElement("CompareResponse") ;
		
		response.setAttribute("id1", String.valueOf(id1)) ;
		response.setAttribute("id2", String.valueOf(id2)) ;

		Article art1 = new Article(wms.wikipedia.getEnvironment(), id1) ;
		if (!(art1.getType() == Page.ARTICLE || art1.getType() == Page.DISAMBIGUATION)) {
			response.setAttribute("unknownId", String.valueOf(id1)) ; 
			return response ;
		}
		
		Article art2 = new Article(wms.wikipedia.getEnvironment(), id2) ;
		if (!(art2.getType() == Page.ARTICLE || art2.getType() == Page.DISAMBIGUATION)) {
			response.setAttribute("unknownId", String.valueOf(id2)) ; 
			return response ;
		}
		
		response.setAttribute("relatedness", wms.df.format(art1.getRelatednessTo(art2))) ;
		
		if (getMutualLinks || getSnippets)
			addMutualLinksOrSnippets(response, art1, art2, getMutualLinks, getSnippets, mutualLinkLimit, snippetLimit, format, linkFormat) ;

		return response ;

	}
	
	public Element compare(String ids1, String ids2) throws Exception {
		
		Element response = wms.doc.createElement("CompareResponse") ;
		response.setAttribute("ids1", ids1) ;
		if (ids2 != null)
			response.setAttribute("ids2", ids2) ;
		
		StringBuffer data = new StringBuffer() ;
		
		//gather articles from ids1 ;
		Vector<Article> articles1 = new Vector<Article>() ;
		for (String id:ids1.split(";")) {
			try {
				articles1.add((Article)wms.wikipedia.getPageById(Integer.parseInt(id))) ;
			} catch (Exception e) {
				//do nothing, this was an invalid id.
			}
		}
		
		//if ids2 is not specified, then we want to compare each item in ids1 with every other one
		if (ids2 == null)
			ids2 = ids1 ;
		
		// gather articles from ids2 ;
		Vector<Article> articles2 = new Vector<Article>() ;
		for (String id:ids2.split(";")) {
			try {
				articles2.add((Article)wms.wikipedia.getPageById(Integer.parseInt(id))) ;
			} catch (Exception e) {
				// do nothing, this was an invalid id.
			}
		}
		
		TLongHashSet doneKeys = new TLongHashSet() ;
		for (Article art1:articles1) {
			for (Article art2:articles2) {
				if(art1.getId() == art2.getId())
					continue ;
				
				//relatedness is symmetric, so create a unique key for this pair of ids were order doesnt matter 
				long min = Math.min(art1.getId(), art2.getId()) ;
				long max = Math.max(art1.getId(), art2.getId()) ;
				long key = min + (max << 30) ;
				
				if(doneKeys.contains(key))
					continue ;
				
				//havent seen this pair before, so output relatedness
				data.append(min + "," + max + "," + wms.df.format(art1.getRelatednessTo(art2)) + "\n") ;
				doneKeys.add(key) ;
			}
		}
		
		response.appendChild(wms.doc.createTextNode(data.toString())) ;
		return response ;
	}

	private Element addMutualLinksOrSnippets(Element response, Article art1, Article art2, boolean getArtsInCommon, boolean getSnippets, int artsInCommonLimit, int snippetLimit, int format, int linkFormat) throws DatabaseException {

		//Build a list of pages that link to both art1 and art2, ordered by average relatedness to them
		TreeSet<Article> mutualLinks = new TreeSet<Article>() ;
		RelatednessCache rc = new RelatednessCache() ;

		Article[] links1 = art1.getLinksIn() ;
		Article[] links2 = art2.getLinksIn() ;

		int index1 = 0 ;
		int index2 = 0 ;

		while (index1 < links1.length && index2 < links2.length) {

			Article link1 = links1[index1] ;
			Article link2 = links2[index2] ;

			int compare = link1.compareTo(link2) ;

			if (compare == 0) {
				if (link1.compareTo(art1)!= 0 && link2.compareTo(art2)!= 0) {
					
					float weight = (rc.getRelatedness(link1, art1) + rc.getRelatedness(link1, art2))/2 ;
					link1.setWeight(weight) ;
					mutualLinks.add(link1) ;
					
					//a rough santity check, so this can't take forever
					if (mutualLinks.size() > 10000) break  ;
				}

				index1 ++ ;
				index2 ++ ;
			} else {
				if (compare < 0)
					index1 ++ ;
				else 
					index2 ++ ;
			}
		}

		if (getArtsInCommon) {

			Element xmlArts = wms.doc.createElement("ArticlesInCommon");
			response.appendChild(xmlArts) ;

			int c = 0 ;

			for (Article ml:mutualLinks) {
				if (c++ > artsInCommonLimit) break ;

				Element xmlArt = wms.doc.createElement("ArticleInCommon");
				xmlArt.setAttribute("title", ml.getTitle()) ;
				xmlArt.setAttribute("id", String.valueOf(ml.getId())) ;
				xmlArt.setAttribute("relatedness1", wms.df.format(rc.getRelatedness(ml, art1))) ;
				xmlArt.setAttribute("relatedness2", wms.df.format(rc.getRelatedness(ml, art2))) ;
				xmlArts.appendChild(xmlArt) ;
			}
		}

		if (getSnippets) {

			Element xmlSnippets = wms.doc.createElement("Snippets");
			response.appendChild(xmlSnippets) ;

			String content1 = art1.getContent() ;
			String content2 = art2.getContent() ;

			int c=0 ;

			//look for snippets in sense2 which mention sense1

			int[] mentions = art2.getLinkPositions(art1) ;
			if (mentions != null) {
				for (Integer pos:mentions) {
					if (c > snippetLimit) break ;
					try {
						int[] sb = art2.getSentenceBoundsSurrounding(pos) ;
						if (sb != null) {
							String sentence = content2.substring(sb[0], sb[1]) ;
							sentence = highlightTopics(sentence, art1, art2) ;
							sentence = wms.definer.format(sentence, format, linkFormat) ;

							Element xmlSnippet = wms.createElement("Snippet", sentence);
							xmlSnippet.setAttribute("sourceId", String.valueOf(art2.getId())) ;
							xmlSnippet.setAttribute("sourceTitle", art2.getTitle()) ;
							xmlSnippets.appendChild(xmlSnippet) ;
							c++ ;
						}
					} catch (Exception e) {} ;
				}
			}

			//look for snippets in sense1 which mention sense2
			mentions = art1.getLinkPositions(art2) ;
			if (mentions != null) {
				for (Integer pos:mentions) {
					if (c> snippetLimit) break ;
					try {
						int[] sb = art1.getSentenceBoundsSurrounding(pos) ;
						if (sb != null) {
							String sentence = content1.substring(sb[0], sb[1]) ;
							sentence = highlightTopics(sentence, art1, art2) ;
							sentence = wms.definer.format(sentence, format, linkFormat) ;

							Element xmlSnippet = wms.createElement("Snippet", sentence);
							xmlSnippet.setAttribute("sourceId", String.valueOf(art1.getId())) ;
							xmlSnippet.setAttribute("sourceTitle", art1.getTitle()) ;
							xmlSnippets.appendChild(xmlSnippet) ;
							c++ ;
						}
					} catch (Exception e) {} ;
				}
			}

			for (Article ml:mutualLinks) {
				if (c > snippetLimit) break ;
				int[] lp1 = ml.getLinkPositions(art1) ;
				int[] lp2 = ml.getLinkPositions(art2) ;

				String mlContent = null ;

				if (lp1 != null && lp2 != null) {
					for (int p1:lp1) {
						for (int p2:lp2) {

							if (c > snippetLimit) break ;
							try {
								int[] sb = ml.getSentenceBoundSurrounding(p1, p2) ;

								if (sb != null) {

									if (mlContent == null)
										mlContent = ml.getContent() ;

									String sentence = mlContent.substring(sb[0], sb[1]) ;
									sentence = highlightTopics(sentence, art1, art2) ;
									sentence = wms.definer.format(sentence, Definer.FORMAT_HTML, Definer.LINK_TOOLKIT) ;

									Element xmlSnippet = wms.createElement("Snippet", sentence);
									xmlSnippet.setAttribute("sourceId", String.valueOf(ml.getId())) ;
									xmlSnippet.setAttribute("sourceTitle", ml.getTitle()) ;
									xmlSnippets.appendChild(xmlSnippet) ;

									c++ ;
								}
							} catch (Exception e) {} ;
						}
					}	
				}	
			}	
		}
		
		return response ;
	}



private String highlightTopics(String markup, Article topic1, Article topic2) throws DatabaseException {


	Matcher m = wms.definer.linkPattern.matcher(markup) ;

	int lastPos = 0 ;
	StringBuffer sb = new StringBuffer() ;

	while(m.find()) {

		String link = m.group(1) ;
		String anchor ;
		String dest ;

		int pos = link.lastIndexOf("|") ;

		if (pos >1) {
			dest = link.substring(0,pos) ;
			anchor = link.substring(pos+1) ;
		} else {
			dest = link ;
			anchor = link ;
		}

		Article art = wms.wikipedia.getArticleByTitle(dest) ;

		if (art.getId() == topic1.getId() || art.getId() == topic2.getId()) {
			sb.append(markup.substring(lastPos, m.start())) ;

			sb.append("'''") ;
			sb.append(anchor) ;
			sb.append("'''") ;

			lastPos = m.end() ;	
		}	
	}

	sb.append(markup.substring(lastPos)) ;
	return sb.toString() ;
}

}
