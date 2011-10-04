package org.wikipedia.miner.service;

import java.util.ArrayList;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.w3c.dom.Element;
import org.wikipedia.miner.model.Article;
import org.wikipedia.miner.model.Category;
import org.wikipedia.miner.model.Page;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.service.ExploreArticleService.ResponseImage;
import org.wikipedia.miner.service.ExploreArticleService.ResponseLabel;
import org.wikipedia.miner.service.ExploreArticleService.ResponsePage;
import org.wikipedia.miner.service.ExploreArticleService.ResponseTranslation;
import org.wikipedia.miner.service.Service.ParameterMissingResponse;
import org.wikipedia.miner.service.param.BooleanParameter;
import org.wikipedia.miner.service.param.IntParameter;
import org.wikipedia.miner.service.param.ParameterGroup;
import org.wikipedia.miner.service.param.StringParameter;

import com.google.gson.annotations.Expose;

public class ExploreCategoryService extends Service{

	private enum GroupName{id,title} ; 
	
	private ParameterGroup grpId ;
	private IntParameter prmId ;
	
	private ParameterGroup grpTitle ;
	private StringParameter prmTitle ;
	
	private BooleanParameter prmParentCategories ;
	
	private BooleanParameter prmChildCategories ;
	private IntParameter prmChildCategoryMax ;
	private IntParameter prmChildCategoryStart ;
	
	private BooleanParameter prmChildArticles ;
	private IntParameter prmChildArticleMax ;
	private IntParameter prmChildArticleStart ;
	
	public ExploreCategoryService() {
		super("core","Provides details of individual categories",
			
			"<p></p>",
			true, false
		);
	}
	
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		
		grpId = new ParameterGroup(GroupName.id.name(), "To retrieve category by  id") ;
		prmId = new IntParameter("id", "The unique identifier of the category to explore", null) ;
		grpId.addParameter(prmId) ;
		addParameterGroup(grpId) ;
		
		grpTitle = new ParameterGroup(GroupName.title.name(), "To retrieve category by title") ;
		prmTitle = new StringParameter("title", "The (case sensitive) title of the category to explore", null) ;
		grpTitle.addParameter(prmTitle) ;
		addParameterGroup(grpTitle) ;
		
		prmParentCategories = new BooleanParameter("parentCategories", "<b>true</b> if parent categories of this category should be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmParentCategories) ;
		
		prmChildCategories = new BooleanParameter("childCategories", "<b>true</b> if child categories of this category should be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmChildCategories) ;
		
		prmChildCategoryMax = new IntParameter("childCategoryMax", "the maximum number of child categories that should be returned. A max of <b>0</b> will result in all child categories being returned", 250) ;
		addGlobalParameter(prmChildCategoryMax) ;
		
		prmChildCategoryStart = new IntParameter("childCategoryStart", "the index of the first child category to return. Combined with <b>childCategoryMax</b>, this parameter allows the user to page through large lists of child categories", 0) ;
		addGlobalParameter(prmChildCategoryStart) ;
		
		prmChildArticles = new BooleanParameter("childArticles", "<b>true</b> if child articles of this category should be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmChildArticles) ;
		
		prmChildArticleMax = new IntParameter("childArticleMax", "the maximum number of child articles that should be returned. A max of <b>0</b> will result in all child articles being returned", 250) ;
		addGlobalParameter(prmChildArticleMax) ;
		
		prmChildArticleStart = new IntParameter("childArticleStart", "the index of the first child article to return. Combined with <b>childArticleMax</b>, this parameter allows the user to page through large lists of child articles", 0) ;
		addGlobalParameter(prmChildArticleStart) ;
		
	}
	
	public Service.Response buildWrappedResponse(HttpServletRequest request) throws Exception {
		
		
		Wikipedia wikipedia = getWikipedia(request) ;
		
		ParameterGroup grp = getSpecifiedParameterGroup(request) ;
		
		if (grp == null) 
			return new ParameterMissingResponse() ;
		
		Category cat = null ;
		
		switch(GroupName.valueOf(grp.getName())) {
		
		case id :
			Integer id = prmId.getValue(request) ;
			
			Page page = wikipedia.getPageById(id) ;
			if (page==null) 
				return new InvalidIdResponse(id) ;
			
			switch(page.getType()) {
				case category:
					cat = (Category)page ;
					break ;
				default:
					return new InvalidIdResponse(id) ;
			}
			break ;
		case title :
			String title = prmTitle.getValue(request) ;
			cat = wikipedia.getCategoryByTitle(title) ;
			
			if (cat == null)
				return new InvalidTitleResponse(title) ;
			break ;
		}
		
		Response response = new Response(cat) ;
		
		if (prmParentCategories.getValue(request)) {
			
			Category[] parents = cat.getParentCategories() ;
			
			response.setTotalParentCategories(parents.length) ;

			for (Category parent:parents) 
				response.addParentCategory(new ResponsePage(parent)) ;
		}
		
		if (prmChildCategories.getValue(request)) {
			
			int start = prmChildCategoryStart.getValue(request) ;
			int max = prmChildCategoryMax.getValue(request) ;
			if (max <= 0) 
				max = Integer.MAX_VALUE ;
			else
				max = max + start ;
		
			Category[] children = cat.getChildCategories() ;
			
			response.setTotalChildCategories(children.length) ;
			for (int i=start ; i < max && i < children.length ; i++) 
				response.addChildCategory(new ResponsePage(children[i])) ;
		}
		
		if (prmChildArticles.getValue(request)) {
			
			int start = prmChildArticleStart.getValue(request) ;
			int max = prmChildArticleMax.getValue(request) ;
			if (max <= 0) 
				max = Integer.MAX_VALUE ;
			else
				max = max + start ;
			
			Article[] children = cat.getChildArticles() ;
			
			response.setTotalChildArticles(children.length) ;
			for (int i=start ; i < max && i < children.length ; i++) 
				response.addChildArticle(new ResponsePage(children[i])) ;
		}

		return response ;
	}

	
	public class InvalidIdResponse extends Service.ErrorResponse {

		@Expose 
		@Attribute
		private Integer invalidId ;

		public InvalidIdResponse(Integer id) {
			super("'" + id + "' is not a valid article id") ;	
			invalidId = id ;
		}
	}

	public class InvalidTitleResponse extends Service.ErrorResponse {

		@Expose 
		@Attribute
		private String invalidTitle ;

		public InvalidTitleResponse(String title) {
			super("'" + title + "' is not a valid article title") ;	
			invalidTitle = title ;
		}
	}
	
	public static class Response extends Service.Response {

		@Expose
		@Attribute
		int id ;

		@Expose
		@Attribute
		String title ;

		@Expose
		@ElementList(required=false, entry="parentCategory") 
		ArrayList<ResponsePage> parentCategories = null ;

		@Expose
		@Attribute(required=false)
		private Integer totalParentCategories ;
		
		@Expose
		@ElementList(required=false, entry="childCategory") 
		ArrayList<ResponsePage> childCategories = null ;

		@Expose
		@Attribute(required=false)
		private Integer totalChildCategories ;
		
		@Expose
		@ElementList(required=false, entry="childArticle") 
		ArrayList<ResponsePage> childArticles = null ;

		@Expose
		@Attribute(required=false)
		private Integer totalChildArticles ;

	

		public Response(Category cat) {	
			this.id = cat.getId();
			this.title = cat.getTitle();
		}

		public void addParentCategory(ResponsePage p) {
			if (parentCategories == null)
				parentCategories = new ArrayList<ResponsePage>() ;

			parentCategories.add(p) ;
		}

		public void setTotalParentCategories(int total) {
			totalParentCategories = total ;
		}

		public void addChildCategory(ResponsePage p) {
			if (childCategories == null)
				childCategories = new ArrayList<ResponsePage>() ;

			childCategories.add(p) ;
		}

		public void setTotalChildCategories(int total) {
			totalChildCategories = total ;
		}

		public void addChildArticle(ResponsePage p) {
			if (childArticles == null)
				childArticles = new ArrayList<ResponsePage>() ;

			childArticles.add(p) ;
		}

		public void setTotalChildArticles(int total) {
			totalChildArticles = total ;
		}


	}
	
}
