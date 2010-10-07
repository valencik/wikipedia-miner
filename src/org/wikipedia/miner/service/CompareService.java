package org.wikipedia.miner.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeSet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.w3c.dom.Element;
import org.wikipedia.miner.model.Article;
import org.wikipedia.miner.model.Label;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.model.Page.PageType;
import org.wikipedia.miner.service.param.BooleanParameter;
import org.wikipedia.miner.service.param.IntListParameter;
import org.wikipedia.miner.service.param.IntParameter;
import org.wikipedia.miner.service.param.ParameterGroup;
import org.wikipedia.miner.service.param.StringParameter;
import org.wikipedia.miner.util.RelatednessCache;
import org.wikipedia.miner.util.text.TextProcessor;


/**
 * 
 * This web service measures relatedness between:
 * <ul>
 * 	<li>pairs of terms (or phrases)</li>
 *  <li>pairs of article ids</li>
 *  <li>lists of article ids</li>
 * </ul>
 * 
 * Pairs of terms will be automatically disambiguated against each other, to be
 * resolved to pairs of articles.  
 * If comparing pairs of terms or pairs of article ids, then users can also 
 * obtain connective topics (articles that link to both things being compared)
 * and explanatory sentences (which mention both things being compared). 
 * 
 * @author David Milne
 */
public class CompareService extends Service{

	private ParameterGroup grpTerms ;
	private StringParameter prmTerm1 ;
	private StringParameter prmTerm2 ;
	
	private ParameterGroup grpIds ;
	private IntParameter prmId1 ;
	private IntParameter prmId2 ;	
	
	private ParameterGroup grpIdLists ;
	private IntListParameter prmIdList1 ;
	private IntListParameter prmIdList2 ;
	
	private BooleanParameter prmSenses ;
	private BooleanParameter prmConnections ;
	private BooleanParameter prmSnippets ;
	
	private IntParameter prmMaxConsConsidered ;
	private IntParameter prmMaxConsReturned ;
	private IntParameter prmMaxConsForSnippets ;
	private IntParameter prmMaxSnippets ;
	
	private BooleanParameter prmEscape ;
	
	
	
	private enum GroupName{termPair,idPair,idLists, none} ; 
	
	/**
	 * Initialises a new CompareService
	 */
	public CompareService() {
		
		super(
				"<p></p>" + 
				"<p></p>"
		);
	}
	

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		grpTerms = new ParameterGroup(GroupName.termPair.name()) ;
		prmTerm1 = new StringParameter("term1", "The first of two terms (or phrases) to compare", null) ;
		grpTerms.addParameter(prmTerm1) ;
		prmTerm2 = new StringParameter("term2", "The second of two terms (or phrases) to compare", null) ;
		grpTerms.addParameter(prmTerm2) ;
		prmSenses = new BooleanParameter("getSenses", "If <b>true</b>, then details of the senses chosen to represent each term will be returned", false) ;
		grpTerms.addParameter(prmSenses) ;
		addParameterGroup(grpTerms) ;
		
		grpIds = new ParameterGroup(GroupName.idPair.name()) ;
		prmId1 = new IntParameter("id1", "The first of two article ids to compare", null) ;
		grpIds.addParameter(prmId1) ;
		prmId2 = new IntParameter("id2", "The second of two article ids to compare", null) ;
		grpIds.addParameter(prmId2) ;
		addParameterGroup(grpIds) ;
		
		grpIdLists = new ParameterGroup(GroupName.idLists.name()) ;
		prmIdList1 = new IntListParameter("ids1", "A list of page ids to compare", null) ;
		grpIdLists.addParameter(prmIdList1) ;
		prmIdList2 = new IntListParameter("ids2", "A second list of page ids to compare. If this is specified, then each article in <em>ids1</em> will be compared against every article in <em>ids2</em>. Otherwise, every article in <em>ids1</em> will be compared against every other article in <em>ids1</em>", new ArrayList<Integer>()) ; 
		grpIdLists.addParameter(prmIdList2) ;
		addParameterGroup(grpIdLists) ;
		
		prmConnections = new BooleanParameter("getConnections", "if <b>true</b>, then the service will list articles that refer to both topics being compared. This parameter is ignored if comparing lists of ids.", false) ;
		addGlobalParameter(prmConnections) ;
		
		prmMaxConsConsidered = new IntParameter("maxConnectionsConsidered", "The maximum number of connections that will be gathered and weighted by thier relatedness to the articles being compared. This parameter is ignored if comparing lists of ids.", 1000) ;
		addGlobalParameter(prmMaxConsConsidered) ;
		
		prmMaxConsReturned = new IntParameter("maxConnectionsReturned", "The maximum number of connections that will be returned. These will be the highest weighted connections. This parameter is ignored if comparing lists of ids.", 250) ;
		addGlobalParameter(prmMaxConsReturned) ;
		
		prmSnippets = new BooleanParameter("getSnippets", "if <b>true</b>, then the service will list sentences that either mention both of the articles being compared, or come from one of the articles and mention the other. This parameter is ignored if comparing lists of ids.", false) ;
		addGlobalParameter(prmSnippets) ;
		
		prmMaxConsForSnippets = new IntParameter("maxConnectionsForSnippets", "The maximum number of connections that will be used to gather snippets from. This parameter is ignored if comparing lists of ids.", 100) ;
		addGlobalParameter(prmMaxConsForSnippets) ;
		
		prmMaxSnippets = new IntParameter("maxSnippets", "The maximum number of connections that will be used to gather snippets from. This parameter is ignored if comparing lists of ids.", 10) ;
		addGlobalParameter(prmMaxSnippets) ;
		
		addGlobalParameter(getHub().getFormatter().getEmphasisFormatParam()) ;
		addGlobalParameter(getHub().getFormatter().getLinkFormatParam()) ;
		
		prmEscape = new BooleanParameter("escapeDefinition", "<true> if sense definitions and sentence snippets should be escaped, <em>false</em> if they are to be encoded directly", false) ;
		addGlobalParameter(prmEscape) ;
	}

	@Override
	public Element buildWrappedResponse(HttpServletRequest request, Element xmlResponse) {
		
		Wikipedia wikipedia = getWikipedia(request) ;
		TextProcessor tp = wikipedia.getEnvironment().getConfiguration().getDefaultTextProcessor() ;
		
		
		ParameterGroup grp = getSpecifiedParameterGroup(request) ;
		
		if (grp == null) {
			xmlResponse.setAttribute("unspecifiedParameters", "true") ;
			return xmlResponse ;
		}
			
		switch(GroupName.valueOf(grp.getName())) {
		
		case termPair :
			
			String term1 = prmTerm1.getValue(request).trim() ;
			String term2 = prmTerm2.getValue(request).trim() ;
			
			Label label1 = new Label(wikipedia.getEnvironment(), term1, tp) ;
			Label.Sense[] senses1 = label1.getSenses() ; 

			if (senses1.length == 0) {
				xmlResponse.setAttribute("unknownTerm", term1) ; 
				return xmlResponse ;
			}
			
			Label label2 = new Label(wikipedia.getEnvironment(), term2, tp) ;
			Label.Sense[] senses2 = label2.getSenses() ; 

			if (senses2.length == 0) {
				xmlResponse.setAttribute("unknownTerm", term2) ; 
				return xmlResponse ;
			}
		
			Label.DisambiguatedSensePair disambiguatedSenses = label1.disambiguateAgainst(label2) ;

			xmlResponse.setAttribute("relatedness", getHub().format(disambiguatedSenses.getRelatedness())) ;
		
			addSenses(xmlResponse, senses1, senses2, disambiguatedSenses, request) ;
			addMutualLinksOrSnippets(xmlResponse, disambiguatedSenses.getSenseA(), disambiguatedSenses.getSenseB(), request, wikipedia) ;
			break ;
			
		case idPair:
			
			Article art1 = new Article(wikipedia.getEnvironment(), prmId1.getValue(request)) ;
			if (!(art1.getType() == PageType.article || art1.getType() == PageType.disambiguation)) {
				xmlResponse.setAttribute("unknownId", String.valueOf(prmId1.getValue(request))) ; 
				return xmlResponse ;
			}

			Article art2 = new Article(wikipedia.getEnvironment(), prmId2.getValue(request)) ;
			if (!(art2.getType() == PageType.article || art2.getType() == PageType.disambiguation)) {
				xmlResponse.setAttribute("unknownId", String.valueOf(prmId2.getValue(request))) ; 
				return xmlResponse ;
			}
			
			xmlResponse.setAttribute("relatedness", getHub().format(art1.getRelatednessTo(art2))) ;
			
			addMutualLinksOrSnippets(xmlResponse, art1, art2, request, wikipedia) ;
			break ;
		case idLists :
			//TODO:
			
			break ;
		}
		
		
		
		
		return xmlResponse ;
	}
	
	
	private Element addSenses(Element response, Label.Sense[] senses1, Label.Sense[] senses2, Label.DisambiguatedSensePair chosenSenses, HttpServletRequest request) {
		
		if (!prmSenses.getValue(request)) 
			return response ;
		
		Element xmlSense1 = getHub().createElement("Sense1");
		xmlSense1.setAttribute("title", chosenSenses.getSenseA().getTitle()) ;
		xmlSense1.setAttribute("id", String.valueOf(chosenSenses.getSenseA().getId())) ;		
		xmlSense1.setAttribute("candidates", String.valueOf(senses1.length)) ;
		response.appendChild(xmlSense1) ;

		Element xmlSense2 = getHub().createElement("Sense2");
		xmlSense2.setAttribute("title", chosenSenses.getSenseB().getTitle()) ;
		xmlSense2.setAttribute("id", String.valueOf(chosenSenses.getSenseB().getId())) ;
		xmlSense2.setAttribute("candidates", String.valueOf(senses2.length)) ;
		response.appendChild(xmlSense2) ;
		
		
		return response ;
	}
	
	
	
	private Element addMutualLinksOrSnippets(Element response, Article art1, Article art2, HttpServletRequest request, Wikipedia wikipedia) {

		boolean getConnections = prmConnections.getValue(request) ;
		boolean getSnippets = prmSnippets.getValue(request) ;
		
		if (!getConnections && !getSnippets)
			return response;
		
		
		//Build a list of pages that link to both art1 and art2, ordered by average relatedness to them
		TreeSet<Article> connections = new TreeSet<Article>() ;
		RelatednessCache rc = new RelatednessCache() ;

		Article[] links1 = art1.getLinksIn() ;
		Article[] links2 = art2.getLinksIn() ;

		int index1 = 0 ;
		int index2 = 0 ;
		
		int maxConsConsidered = prmMaxConsConsidered.getValue(request) ;

		while (index1 < links1.length && index2 < links2.length) {

			Article link1 = links1[index1] ;
			Article link2 = links2[index2] ;

			int compare = link1.compareTo(link2) ;

			if (compare == 0) {
				if (link1.compareTo(art1)!= 0 && link2.compareTo(art2)!= 0) {

					float weight = (rc.getRelatedness(link1, art1) + rc.getRelatedness(link1, art2))/2 ;
					link1.setWeight(weight) ;
					connections.add(link1) ;

					
					if (connections.size() > maxConsConsidered) break  ;
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

		int maxConsReturned = prmMaxConsReturned.getValue(request) ;
		
		if (getConnections) {

			Element xmlConnections = getHub().createElement("Connections");
			response.appendChild(xmlConnections) ;

			int c = 0 ;

			for (Article connection:connections) {
				if (c++ > maxConsReturned) break ;

				Element xmlCon = getHub().createElement("Connection");
				xmlCon.setAttribute("title", connection.getTitle()) ;
				xmlCon.setAttribute("id", String.valueOf(connection.getId())) ;
				xmlCon.setAttribute("relatedness1", getHub().format(rc.getRelatedness(connection, art1))) ;
				xmlCon.setAttribute("relatedness2", getHub().format(rc.getRelatedness(connection, art2))) ;
				xmlConnections.appendChild(xmlCon) ;
			}
			
			response.appendChild(xmlConnections) ;
		}

		if (getSnippets) {

			Element xmlSnippets = getHub().createElement("Snippets");
			response.appendChild(xmlSnippets) ;

			int snippetsCollected=0 ;
			int maxSnippets = prmMaxSnippets.getValue(request) ;

			//look for snippets in art1 which mention art2
			for (int sentenceIndex:art1.getSentenceIndexesMentioning(art2)) {
				if (snippetsCollected >= maxSnippets) break ;
				
				String sentence = art1.getSentenceMarkup(sentenceIndex) ;
				xmlSnippets.appendChild(getSnippetXML(sentence, art1, art2, art1, request, wikipedia)) ;
				snippetsCollected ++ ;
			}
			
			//look for snippets in art2 which mention art1
			for (int sentenceIndex:art2.getSentenceIndexesMentioning(art1)) {
				if (snippetsCollected >= maxSnippets) break ;
				
				String sentence = art2.getSentenceMarkup(sentenceIndex) ;
				xmlSnippets.appendChild(getSnippetXML(sentence, art1, art2, art2, request, wikipedia)) ;
				snippetsCollected ++ ;
			}

			ArrayList<Article> articlesOfInterest = new ArrayList<Article>() ;
			articlesOfInterest.add(art1) ;
			articlesOfInterest.add(art2) ;
			
			int consConsidered = 0 ;
			int maxConsForSnippets = prmMaxConsForSnippets.getValue(request) ;
			
			for (Article connection:connections) {
				
				consConsidered++ ;
				if (consConsidered >= maxConsForSnippets) break ;
				if (snippetsCollected >= maxSnippets) break ;
				
				for (int sentenceIndex:connection.getSentenceIndexesMentioning(articlesOfInterest)) {
					if (snippetsCollected >= maxSnippets) break ;
					
					String sentence = connection.getSentenceMarkup(sentenceIndex) ;
					xmlSnippets.appendChild(getSnippetXML(sentence, art1, art2, connection, request, wikipedia)) ;
					snippetsCollected ++ ;
				}
			}
			
			response.appendChild(xmlSnippets) ;
		}

		return response ;
	}
	
	private Element getSnippetXML(String sentence, Article art1, Article art2, Article source, HttpServletRequest request, Wikipedia wikipedia) {
		
		if (!source.equals(art1) && !source.equals(art2)) {
			//remove emphasis markup
			sentence = sentence.replaceAll("'{2,}", "") ;
		}
		
		HashSet<Integer> topicIds = new HashSet<Integer>() ;
		topicIds.add(art1.getId()) ;
		topicIds.add(art2.getId()) ;
		
		sentence = getHub().getFormatter().highlightTopics(sentence, topicIds, wikipedia) ;
		sentence = getHub().getFormatter().format(sentence, request, wikipedia) ;

		Element xmlSnippet = getHub().createElement("Snippet", sentence);
		xmlSnippet.setAttribute("sourceId", String.valueOf(source.getId())) ;
		xmlSnippet.setAttribute("sourceTitle", source.getTitle()) ;
		return xmlSnippet ;
	}
}
