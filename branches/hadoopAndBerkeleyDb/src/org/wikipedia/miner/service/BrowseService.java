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
import org.wikipedia.miner.service.param.BooleanParameter;
import org.wikipedia.miner.service.param.IntParameter;

public class BrowseService extends Service {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1138307426429979318L;

	private IntParameter prmId ;

	private BooleanParameter prmInLinks ;
	private BooleanParameter prmOutLinks ;
	private BooleanParameter prmParentCategories ;
	private BooleanParameter prmChildCategories ;
	private BooleanParameter prmChildArticles ;
	private IntParameter prmMax ;
	private BooleanParameter prmRelatedness ;

	//TODO: allow access via article title or category title.
	//TODO: equivalent category, equivalent article

	public BrowseService() {
		super("<p>This service provides access to the connections between Wikipedia articles and categories.</p>");

		

	}
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		prmId = new IntParameter("id", "the unique identifier of the page to gather connections from", null) ;
		addGlobalParameter(prmId) ;

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

		prmMax = new IntParameter("max", "the maximum number of connections that should be returned for inLinks, outLinks, etc. A max of <b>0</b> will result in all connections being returned", 250) ;
		addGlobalParameter(prmMax) ;

		prmRelatedness = new BooleanParameter("relatedness", "<b>true</b> if the relatedness of in- and out-links should be measured, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmRelatedness) ;
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
		
		Integer id = prmId.getValue(request) ;
		if (id == null) 
			return buildErrorResponse("You must specify an id", xmlResponse) ;
		
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
	}


	private Element browseArticle(Article art, ArticleComparer artComparer, HttpServletRequest request, Element xmlResponse) throws DOMException, Exception {

		int max = prmMax.getValue(request) ;

		if (prmOutLinks.getValue(request)) {

			Article[] linksOut = art.getLinksOut() ;

			Element xmlLinks = getHub().createElement("OutLinks") ;
			xmlLinks.setAttribute("total", String.valueOf(linksOut.length)) ;
			int count = 0 ;

			for (Article link: linksOut) {
				if (max > 0 && count++ == max) break ;

				Element xmlLink = getHub().createElement("OutLink") ;
				xmlLink.setAttribute("id", String.valueOf(link.getId())) ;
				xmlLink.setAttribute("title", link.getTitle()) ;
				if (artComparer != null)
					xmlLink.setAttribute("relatedness", getHub().format(artComparer.getRelatedness(art, link))) ;

				xmlLinks.appendChild(xmlLink) ;
			}
			xmlResponse.appendChild(xmlLinks) ;
		}

		if (prmInLinks.getValue(request)) {

			Article[] linksIn = art.getLinksIn() ;

			Element xmlLinks = getHub().createElement("InLinks") ;
			xmlLinks.setAttribute("total", String.valueOf(linksIn.length)) ;
			int count = 0 ;

			for (Article link: linksIn) {
				if (max > 0 && count++ == max) break ;

				Element xmlLink = getHub().createElement("InLink") ;
				xmlLink.setAttribute("id", String.valueOf(link.getId())) ;
				xmlLink.setAttribute("title", link.getTitle()) ;
				if (artComparer != null)
					xmlLink.setAttribute("relatedness", getHub().format(artComparer.getRelatedness(art, link))) ;

				xmlLinks.appendChild(xmlLink) ;
			}
			xmlResponse.appendChild(xmlLinks) ;
		}
		
		if (prmParentCategories.getValue(request)) {
			
			Category[] parents = art.getParentCategories() ;
			
			Element xmlParents = getHub().createElement("ParentCategories") ;
			xmlParents.setAttribute("total", String.valueOf(parents.length)) ;
			int count = 0 ;

			for (Category cat: parents) {
				if (max > 0 && count++ == max) break ;

				Element xmlParent = getHub().createElement("ParentCategory") ;
				xmlParent.setAttribute("id", String.valueOf(cat.getId())) ;
				xmlParent.setAttribute("title", cat.getTitle()) ;
				
				xmlParents.appendChild(xmlParent) ;
			}
			xmlResponse.appendChild(xmlParents) ;
		}

		return xmlResponse ;
	}

	public Element browseCategory(Category cat, HttpServletRequest request, Element xmlResponse) {

		int max = prmMax.getValue(request) ;


		if (prmParentCategories.getValue(request)) {
			
			Category[] parents = cat.getParentCategories() ;
			
			Element xmlParents = getHub().createElement("ParentCategories") ;
			xmlParents.setAttribute("total", String.valueOf(parents.length)) ;
			int count = 0 ;

			for (Category parent: parents) {
				if (max > 0 && count++ == max) break ;

				Element xmlParent = getHub().createElement("ParentCategory") ;
				xmlParent.setAttribute("id", String.valueOf(parent.getId())) ;
				xmlParent.setAttribute("title", parent.getTitle()) ;
				
				xmlParents.appendChild(xmlParent) ;
			}
			xmlResponse.appendChild(xmlParents) ;
		}
		
		if (prmChildCategories.getValue(request)) {
			
			Category[] children = cat.getChildCategories() ;
			
			Element xmlChildren = getHub().createElement("ChildCategories") ;
			xmlChildren.setAttribute("total", String.valueOf(children.length)) ;
			int count = 0 ;

			for (Category child: children) {
				if (max > 0 && count++ == max) break ;

				Element xmlChild = getHub().createElement("ChildCategory") ;
				xmlChild.setAttribute("id", String.valueOf(child.getId())) ;
				xmlChild.setAttribute("title", child.getTitle()) ;
				
				xmlChildren.appendChild(xmlChild) ;
			}
			xmlResponse.appendChild(xmlChildren) ;
		}
		
		if (prmChildArticles.getValue(request)) {
			
			Article[] children = cat.getChildArticles() ;
			
			Element xmlChildren = getHub().createElement("ChildArticles") ;
			xmlChildren.setAttribute("total", String.valueOf(children.length)) ;
			int count = 0 ;

			for (Article child: children) {
				if (max > 0 && count++ == max) break ;

				Element xmlChild = getHub().createElement("ChildArticle") ;
				xmlChild.setAttribute("id", String.valueOf(child.getId())) ;
				xmlChild.setAttribute("title", child.getTitle()) ;
				
				xmlChildren.appendChild(xmlChild) ;
			}
			xmlResponse.appendChild(xmlChildren) ;
		}

		return xmlResponse ;
	}

}
