package org.wikipedia.miner.service;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.wikipedia.miner.comparison.ArticleComparer;
import org.wikipedia.miner.model.Article;
import org.wikipedia.miner.model.Category;
import org.wikipedia.miner.model.Page;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.service.Service.ExampleBuilder;
import org.wikipedia.miner.service.param.BooleanParameter;
import org.wikipedia.miner.service.param.IntParameter;
import org.wikipedia.miner.service.param.ParameterGroup;
import org.wikipedia.miner.service.param.StringParameter;

public class BrowseService extends Service {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1138307426429979318L;

	private enum GroupName{pageId,articleTitle,categoryTitle} ; 
	
	private ParameterGroup grpPageId ;
	private IntParameter prmPageId ;
	
	private ParameterGroup grpArticleTitle ;
	private StringParameter prmArticleTitle ;
	
	private ParameterGroup grpCategoryTitle ;
	private StringParameter prmCategoryTitle ;
	
	private BooleanParameter prmInLinks ;
	private BooleanParameter prmOutLinks ;
	private BooleanParameter prmParentCategories ;
	private BooleanParameter prmChildCategories ;
	private BooleanParameter prmChildArticles ;
	private IntParameter prmMaxConnections ;
	private IntParameter prmStartIndex ;
	
	private BooleanParameter prmRelatedness ;

	//TODO: equivalent category, equivalent article

	public BrowseService() {
		super("Explores the connections between Wikipedia pages", 
				"<p>This service provides access to the connections between Wikipedia pages. " +
				"If queried with an article, it can return the different articles it links to, the articles " +
				"that link to it, and the categories it belongs to. If queried with a category, it can return " +
				"its parent categories, child categories and child articles</p>",
				true, false);
	}
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		
		grpPageId = new ParameterGroup(GroupName.pageId.name(), "To browse an article or category by its unique id") ;
		prmPageId = new IntParameter("id", "the unique identifier of the page to gather connections from", null) ;
		grpPageId.addParameter(prmPageId) ; 
		addParameterGroup(grpPageId) ;

		grpArticleTitle = new ParameterGroup(GroupName.articleTitle.name(), "To browse an article by its title") ;
		prmArticleTitle = new StringParameter("artTitle", "the (case sensitive) title of the article to gather connections from", null) ;
		grpArticleTitle.addParameter(prmArticleTitle) ;
		addParameterGroup(grpArticleTitle) ;
		
		grpCategoryTitle = new ParameterGroup(GroupName.categoryTitle.name(), "To browse an category by its title") ;
		prmCategoryTitle = new StringParameter("catTitle", "the (case sensitive) title of the category to gather connections from", null) ;
		grpCategoryTitle.addParameter(prmCategoryTitle) ;
		addParameterGroup(grpCategoryTitle) ;
		
		
		
		prmInLinks = new BooleanParameter("inLinks", "<b>true</b> if articles that link to this article should be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmInLinks) ;

		prmOutLinks = new BooleanParameter("outLinks", "<b>true</b> if articles that this article links to should be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmOutLinks) ;

		prmParentCategories = new BooleanParameter("parentCategories", "<b>true</b> if parent categories of this article or category should be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmParentCategories) ;

		prmChildCategories = new BooleanParameter("childCategories", "<b>true</b> if child categories of this category should be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmChildCategories) ;

		prmChildArticles = new BooleanParameter("childArticles", "<b>true</b> if child articles of this category should be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmChildArticles) ;

		prmMaxConnections = new IntParameter("max", "the maximum number of connections that should be returned for inLinks, outLinks, etc. A max of <b>0</b> will result in all connections being returned", 250) ;
		addGlobalParameter(prmMaxConnections) ;
		
		prmStartIndex = new IntParameter("startIndex", "the index of the first connection to return. Combined with <b>max</b>, this parameter allows the user to page through large lists of connections", 0) ;
		addGlobalParameter(prmStartIndex) ;
		
		prmRelatedness = new BooleanParameter("relatedness", "<b>true</b> if the relatedness of in- and out-links should be measured, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmRelatedness) ;
		
	
		addExample(
				new ExampleBuilder("Gather the first 50 articles that link to <i>New Zealand</i>").
				addParam(prmArticleTitle, "New Zealand").
				addParam(prmInLinks, true).
				addParam(prmMaxConnections, 50).
				build()
		) ;
		
		addExample(
				new ExampleBuilder("Gather the next 50 articles that link to <i>New Zealand</i>").
				addParam(prmArticleTitle, "New Zealand").
				addParam(prmInLinks, true).
				addParam(prmMaxConnections, 50).
				addParam(prmStartIndex, 50).
				build()
		) ;
		
		addExample(
				new ExampleBuilder("Measure relatedness of <i>Kiwi</i> to all of the articles it links to").
				addParam(prmPageId, 17362).
				addParam(prmOutLinks, true).
				addParam(prmRelatedness, true).
				addParam(prmMaxConnections, 0).
				build() 
		) ;
		
		addExample(
				new ExampleBuilder("Gather all connections of the category <i>New Zealand</i>").
				addParam(prmCategoryTitle, "New Zealand").
				addParam(prmParentCategories, true).
				addParam(prmChildCategories, true).
				addParam(prmChildArticles, true).
				addParam(prmMaxConnections, 0).
				build() 
		) ;
	}
	

	@Override
	public Element buildWrappedResponse(HttpServletRequest request, Element xmlResponse) throws Exception {

		Wikipedia wikipedia = getWikipedia(request) ;
		
		ArticleComparer artComparer = null ;
		if (prmRelatedness.getValue(request)) {
			artComparer = getHub().getArticleComparer(this.getWikipediaName(request)) ;
			if (artComparer == null) 
				this.buildWarningResponse("Relatedness measures are unavalable for this instance of wikipedia", xmlResponse) ;
		}
		
		ParameterGroup grp = getSpecifiedParameterGroup(request) ;
		
		if (grp == null) {
			xmlResponse.setAttribute("unspecifiedParameters", "true") ;
			return xmlResponse ;
		}
			
		switch(GroupName.valueOf(grp.getName())) {
		
		case pageId :
			Integer id = prmPageId.getValue(request) ;
			
			Page page = wikipedia.getPageById(id) ;
			if (page==null) 
				return buildErrorResponse("'" + id + "' is an unknown id", xmlResponse) ;
			
			switch(page.getType()) {
				case article:
				case disambiguation:
					return browseArticle((Article)page, artComparer, request, xmlResponse) ;
				case category:
					return browseCategory((Category)page, request, xmlResponse) ;
				default:
					return buildErrorResponse("'" + id + "' is not an article or category id", xmlResponse) ;
			}
		case articleTitle :
			String artTitle = prmArticleTitle.getValue(request) ;
			Article art = wikipedia.getArticleByTitle(artTitle) ;
			
			if (art == null)
				return buildErrorResponse("'" + artTitle + "' is an unknown article title", xmlResponse) ;
			
			return browseArticle(art, artComparer, request, xmlResponse) ;
		case categoryTitle :
			String catTitle = prmCategoryTitle.getValue(request) ;
			Category cat = wikipedia.getCategoryByTitle(catTitle) ;
			
			if (cat == null)
				return buildErrorResponse("'" + catTitle + "' is an unknown article title", xmlResponse) ;
			
			return browseCategory(cat, request, xmlResponse) ;
		}
		
		return xmlResponse ;
	}


	private Element browseArticle(Article art, ArticleComparer artComparer, HttpServletRequest request, Element xmlResponse) throws DOMException, Exception {

		int startIndex = prmStartIndex.getValue(request) ;
		
		int max = prmMaxConnections.getValue(request) ;
		if (max <= 0) 
			max = Integer.MAX_VALUE ;
		else
			max = max + startIndex ;
 		
		if (prmOutLinks.getValue(request)) {

			Article[] linksOut = art.getLinksOut() ;

			Element xmlLinks = getHub().createElement("OutLinks") ;
			xmlLinks.setAttribute("total", String.valueOf(linksOut.length)) ;

			for (int i=startIndex ; i < max && i < linksOut.length ; i++) {

				Element xmlLink = getHub().createElement("OutLink") ;
				xmlLink.setAttribute("id", String.valueOf(linksOut[i].getId())) ;
				xmlLink.setAttribute("title", linksOut[i].getTitle()) ;
				if (artComparer != null)
					xmlLink.setAttribute("relatedness", getHub().format(artComparer.getRelatedness(art, linksOut[i]))) ;

				xmlLinks.appendChild(xmlLink) ;
			}
			xmlResponse.appendChild(xmlLinks) ;
		}

		if (prmInLinks.getValue(request)) {

			Article[] linksIn = art.getLinksIn() ;

			Element xmlLinks = getHub().createElement("InLinks") ;
			xmlLinks.setAttribute("total", String.valueOf(linksIn.length)) ;

			for (int i=startIndex ; i < max && i < linksIn.length ; i++) {

				Element xmlLink = getHub().createElement("InLink") ;
				xmlLink.setAttribute("id", String.valueOf(linksIn[i].getId())) ;
				xmlLink.setAttribute("title", linksIn[i].getTitle()) ;
				if (artComparer != null)
					xmlLink.setAttribute("relatedness", getHub().format(artComparer.getRelatedness(art, linksIn[i]))) ;

				xmlLinks.appendChild(xmlLink) ;
			}
			xmlResponse.appendChild(xmlLinks) ;
		}
		
		if (prmParentCategories.getValue(request)) {
			
			Category[] parents = art.getParentCategories() ;
			
			Element xmlParents = getHub().createElement("ParentCategories") ;
			xmlParents.setAttribute("total", String.valueOf(parents.length)) ;

			for (int i=startIndex ; i < max && i < parents.length ; i++) {

				Element xmlParent = getHub().createElement("ParentCategory") ;
				xmlParent.setAttribute("id", String.valueOf(parents[i].getId())) ;
				xmlParent.setAttribute("title", parents[i].getTitle()) ;
				
				xmlParents.appendChild(xmlParent) ;
			}
			xmlResponse.appendChild(xmlParents) ;
		}

		return xmlResponse ;
	}

	public Element browseCategory(Category cat, HttpServletRequest request, Element xmlResponse) {

		int startIndex = prmStartIndex.getValue(request) ;
		
		int max = prmMaxConnections.getValue(request) ;
		if (max <= 0) 
			max = Integer.MAX_VALUE ;
		else
			max = max + startIndex ;


		if (prmParentCategories.getValue(request)) {
			
			Category[] parents = cat.getParentCategories() ;
			
			Element xmlParents = getHub().createElement("ParentCategories") ;
			xmlParents.setAttribute("total", String.valueOf(parents.length)) ;

			for (int i=startIndex ; i < max && i < parents.length ; i++) {

				Element xmlParent = getHub().createElement("ParentCategory") ;
				xmlParent.setAttribute("id", String.valueOf(parents[i].getId())) ;
				xmlParent.setAttribute("title", parents[i].getTitle()) ;
				
				xmlParents.appendChild(xmlParent) ;
			}
			xmlResponse.appendChild(xmlParents) ;
		}
		
		if (prmChildCategories.getValue(request)) {
			
			Category[] children = cat.getChildCategories() ;
			
			Element xmlChildren = getHub().createElement("ChildCategories") ;
			xmlChildren.setAttribute("total", String.valueOf(children.length)) ;

			for (int i=startIndex ; i < max && i < children.length ; i++) {

				Element xmlChild = getHub().createElement("ChildCategory") ;
				xmlChild.setAttribute("id", String.valueOf(children[i].getId())) ;
				xmlChild.setAttribute("title", children[i].getTitle()) ;
				
				xmlChildren.appendChild(xmlChild) ;
			}
			xmlResponse.appendChild(xmlChildren) ;
		}
		
		if (prmChildArticles.getValue(request)) {
			
			Article[] children = cat.getChildArticles() ;
			
			Element xmlChildren = getHub().createElement("ChildArticles") ;
			xmlChildren.setAttribute("total", String.valueOf(children.length)) ;

			for (int i=startIndex ; i < max && i < children.length ; i++) {

				Element xmlChild = getHub().createElement("ChildArticle") ;
				xmlChild.setAttribute("id", String.valueOf(children[i].getId())) ;
				xmlChild.setAttribute("title", children[i].getTitle()) ;
				
				xmlChildren.appendChild(xmlChild) ;
			}
			xmlResponse.appendChild(xmlChildren) ;
		}

		return xmlResponse ;
	}

}
