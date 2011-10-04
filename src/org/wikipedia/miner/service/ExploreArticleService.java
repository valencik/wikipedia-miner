package org.wikipedia.miner.service;

import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Text;
import org.wikipedia.miner.comparison.ArticleComparer;
import org.wikipedia.miner.model.Article;
import org.wikipedia.miner.model.Category;
import org.wikipedia.miner.model.Page;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.service.param.BooleanParameter;
import org.wikipedia.miner.service.param.EnumParameter;
import org.wikipedia.miner.service.param.IntParameter;
import org.wikipedia.miner.service.param.ParameterGroup;
import org.wikipedia.miner.service.param.StringParameter;

import com.google.gson.annotations.Expose;

public class ExploreArticleService extends Service{

	//TODO:modify freebase image request to use article titles rather than ids
	//TODO:if lang is not en, use languageLinks to translate article title to english.

	private enum GroupName{id,title} ; 
	public enum DefinitionLength{LONG, SHORT} ;

	private Pattern fb_imagePattern = Pattern.compile("\"image\"\\:\\[(.*?)\\]") ;
	private Pattern fb_idPattern = Pattern.compile("\"id\"\\:\"(.*?)\"") ;

	private ParameterGroup grpId ;
	private IntParameter prmId ;

	private ParameterGroup grpTitle ;
	private StringParameter prmTitle ;

	private BooleanParameter prmDefinition;
	private EnumParameter<DefinitionLength> prmDefinitionLength ;

	private BooleanParameter prmLabels ;

	private BooleanParameter prmTranslations ;

	private BooleanParameter prmImages ;
	private IntParameter prmImageWidth ;
	private IntParameter prmImageHeight ;

	private BooleanParameter prmParentCategories ;

	private BooleanParameter prmInLinks ;
	private IntParameter prmInLinkMax ;
	private IntParameter prmInLinkStart ;

	private BooleanParameter prmOutLinks ;
	private IntParameter prmOutLinkMax ;
	private IntParameter prmOutLinkStart ;

	private BooleanParameter prmLinkRelatedness ;


	public ExploreArticleService() {

		super("core","Provides details of individual articles",

				"<p></p>",
				true, false
		);
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);


		grpId = new ParameterGroup(GroupName.id.name(), "To retrieve article by  id") ;
		prmId = new IntParameter("id", "The unique identifier of the article to explore", null) ;
		grpId.addParameter(prmId) ;
		addParameterGroup(grpId) ;

		grpTitle = new ParameterGroup(GroupName.title.name(), "To retrieve article by title") ;
		prmTitle = new StringParameter("title", "The (case sensitive) title of the article to explore", null) ;
		grpTitle.addParameter(prmTitle) ;
		addParameterGroup(grpTitle) ;


		prmDefinition = new BooleanParameter("definition", "<b>true</b> if a snippet definition should be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmDefinition) ;

		String[] descLength = {"first paragraph", "first sentence"} ;
		prmDefinitionLength = new EnumParameter<DefinitionLength>("definitionLength", "The required length of the definition", DefinitionLength.SHORT, DefinitionLength.values(), descLength) ;
		addGlobalParameter(prmDefinitionLength) ;

		addGlobalParameter(getHub().getFormatter().getEmphasisFormatParam()) ;
		addGlobalParameter(getHub().getFormatter().getLinkFormatParam()) ;

		prmLabels = new BooleanParameter("labels", "<b>true</b> if labels (synonyms, etc) for this topic are to be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmLabels) ;

		prmTranslations = new BooleanParameter("translations", "<b>true</b> if translations (language links) for this topic are to be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmTranslations) ;

		prmImages = new BooleanParameter("images", "Whether or not to retrieve relevant image urls from freebase", false) ;
		addGlobalParameter(prmImages) ;

		prmImageWidth = new IntParameter("maxImageWidth", "Images can be scaled. This defines their maximum width, in pixels", 150) ;
		addGlobalParameter(prmImageWidth) ;

		prmImageHeight = new IntParameter("maxImageHeight", "Images can be scaled. This defines their maximum height, in pixels", 150) ;
		addGlobalParameter(prmImageHeight) ;

		prmParentCategories = new BooleanParameter("parentCategories", "<b>true</b> if parent categories of this category should be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmParentCategories) ;

		prmInLinks = new BooleanParameter("inLinks", "<b>true</b> if articles that link to this article should be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmInLinks) ;

		prmInLinkMax = new IntParameter("inLinkMax", "the maximum number of in-links that should be returned. A max of <b>0</b> will result in all in-links being returned", 250) ;
		addGlobalParameter(prmInLinkMax) ;

		prmInLinkStart = new IntParameter("inLinkStart", "the index of the first in-link to return. Combined with <b>inLinkMax</b>, this parameter allows the user to page through large lists of in-links", 0) ;
		addGlobalParameter(prmInLinkStart) ;

		prmOutLinks = new BooleanParameter("outLinks", "<b>true</b> if articles that this article links to should be returned, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmOutLinks) ;

		prmOutLinkMax = new IntParameter("outLinkMax", "the maximum number of out-links that should be returned. A max of <b>0</b> will result in all out-links being returned", 250) ;
		addGlobalParameter(prmOutLinkMax) ;

		prmOutLinkStart = new IntParameter("outLinkStart", "the index of the first out-link to return. Combined with <b>outLinkMax</b>, this parameter allows the user to page through large lists of out-links", 0) ;
		addGlobalParameter(prmOutLinkStart) ;

		prmLinkRelatedness = new BooleanParameter("linkRelatedness", "<b>true</b> if the relatedness of in- and out-links should be measured, otherwise <b>false</b>", false) ;
		addGlobalParameter(prmLinkRelatedness) ;

	}

	public Service.Response buildWrappedResponse(HttpServletRequest request) throws Exception {

		Wikipedia wikipedia = getWikipedia(request) ;

		ArticleComparer artComparer = null ;
		if (prmLinkRelatedness.getValue(request)) {
			artComparer = getHub().getArticleComparer(this.getWikipediaName(request)) ;
			if (artComparer == null) 
				return new ErrorResponse("Relatedness measures are unavailable for this instance of wikipedia") ;
		}

		ParameterGroup grp = getSpecifiedParameterGroup(request) ;

		if (grp == null) 
			return new ParameterMissingResponse() ;

		Article art = null ;

		switch(GroupName.valueOf(grp.getName())) {

		case id :
			Integer id = prmId.getValue(request) ;

			Page page = wikipedia.getPageById(id) ;
			if (page==null) 
				return new InvalidIdResponse(id) ;

			switch(page.getType()) {
			case disambiguation:
			case article:
				art = (Article)page ;
				break ;
			default:
				return new InvalidIdResponse(id) ;
			}
			break ;
		case title :
			String title = prmTitle.getValue(request) ;
			art = wikipedia.getArticleByTitle(title) ;

			if (art == null)
				return new InvalidTitleResponse(title) ;
			break ;
		}

		Response response = new Response(art) ;

		if (prmDefinition.getValue(request)) {
			String definition = null ;

			if (prmDefinitionLength.getValue(request)==DefinitionLength.SHORT) 
				definition = art.getSentenceMarkup(0) ; 
			else
				definition = art.getFirstParagraphMarkup() ; 

			response.setDefinition(getHub().getFormatter().format(definition, request, wikipedia)) ;
		}

		if (prmLabels.getValue(request)) {
			//get labels for this concept

			Article.Label[] labels = art.getLabels() ;
			int total = 0 ;
			for (Article.Label lbl:labels) 
				total += lbl.getLinkOccCount() ;

			for (Article.Label lbl:labels) {
				long occ = lbl.getLinkOccCount() ;

				if (occ > 0) 
					response.addLabel(new ResponseLabel(lbl, total)) ;

			}
		}

		if (prmTranslations.getValue(request)) {
			TreeMap<String,String> translations = art.getTranslations() ;
			for (Map.Entry<String,String> entry:translations.entrySet()) 
				response.addTranslation(new ResponseTranslation(entry.getKey(), entry.getValue())) ;	
		}


		if (prmImages.getValue(request)) {
				URL freebaseRequest = new URL("http://www.freebase.com/api/service/mqlread?query={\"query\":{\"key\":[{\"namespace\":\"/wikipedia/en_id\",\"value\":\"" + art.getId() + "\"}], \"type\":\"/common/topic\", \"article\":[{\"id\":null}], \"image\":[{\"id\":null}]}}") ;
	
				String freebaseResponse = getHub().getRetriever().getWebContent(freebaseRequest) ;
	
				freebaseResponse = freebaseResponse.replaceAll("\\s", "") ;
	
				Matcher m = fb_imagePattern.matcher(freebaseResponse) ;
	
				if (m.find()) {
					Matcher n = fb_idPattern.matcher(m.group(1)) ;
					while (n.find()) {
						String url = "http://www.freebase.com/api/trans/image_thumb" + n.group(1).replace("\\/", "/") + "?maxwidth=" + prmImageWidth.getValue(request) + "&maxheight=" + prmImageHeight.getValue(request) ;
						response.addImage(new ResponseImage(url)) ;
					}
				}
		}

		if (prmParentCategories.getValue(request)) {
			Category[] parents = art.getParentCategories() ;

			response.setTotalParentCategories(parents.length) ;
			for (Category parent:parents) 
				response.addParentCategory(new ResponsePage(parent)) ;		
		}

		if (prmOutLinks.getValue(request)) {

			int start = prmOutLinkStart.getValue(request) ;
			int max = prmOutLinkMax.getValue(request) ;
			if (max <= 0) 
				max = Integer.MAX_VALUE ;
			else
				max = max + start ;

			Article[] linksOut = art.getLinksOut() ;

			response.setTotalOutLinks(linksOut.length) ;
			for (int i=start ; i < max && i < linksOut.length ; i++) {
				ResponsePage rp = new ResponsePage(linksOut[i]) ;
				if (artComparer != null)
					rp.setRelatedness(artComparer.getRelatedness(art, linksOut[i])) ;

				response.addOutLink(rp) ;
			}
		}

		if (prmInLinks.getValue(request)) {

			int start = prmInLinkStart.getValue(request) ;
			int max = prmInLinkMax.getValue(request) ;
			if (max <= 0) 
				max = Integer.MAX_VALUE ;
			else
				max = max + start ;

			Article[] linksIn = art.getLinksIn() ;

			response.setTotalInLinks(linksIn.length) ;
			for (int i=start ; i < max && i < linksIn.length ; i++) {
				ResponsePage rp = new ResponsePage(linksIn[i]) ;
				if (artComparer != null)
					rp.setRelatedness(artComparer.getRelatedness(art, linksIn[i])) ;

				response.addInLink(rp) ;
			}
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
		@Element(required=false, data=true)
		String definition ;

		@Expose
		@ElementList(required=false, entry="image") 
		ArrayList<ResponseImage> images = null ;
		
		@Expose
		@ElementList(required=false, entry="label") 
		ArrayList<ResponseLabel> labels = null ;

		@Expose
		@ElementList(required=false, entry="tranlation") 
		ArrayList<ResponseTranslation> translations = null ;

		@Expose
		@ElementList(required=false, entry="parentCategory") 
		ArrayList<ResponsePage> parentCategories = null ;

		@Expose
		@Attribute(required=false)
		private Integer totalParentCategories ;

		@Expose
		@ElementList(required=false, entry="inLink") 
		ArrayList<ResponsePage> inLinks = null ;

		@Expose
		@Attribute(required=false)
		private Integer totalInLinks ;

		@Expose
		@ElementList(required=false, entry="outLink") 
		ArrayList<ResponsePage> outLinks = null ;

		@Expose
		@Attribute(required=false)
		private Integer totalOutLinks ;

		public Response(Article art) {	
			this.id = art.getId();
			this.title = art.getTitle();
		}

		public void setDefinition(String markup) {
			this.definition = markup ;
		}
		
		public void addImage(ResponseImage image) {
			if (images == null)
				images = new ArrayList<ResponseImage>() ;
			
			images.add(image) ;
		}

		public void addLabel(ResponseLabel label) {
			if (labels == null)
				labels = new ArrayList<ResponseLabel>() ;

			labels.add(label) ;
		}

		public void addTranslation(ResponseTranslation t) {
			if (translations == null)
				translations = new ArrayList<ResponseTranslation>() ;

			translations.add(t) ;
		}

		public void addParentCategory(ResponsePage p) {
			if (parentCategories == null)
				parentCategories = new ArrayList<ResponsePage>() ;

			parentCategories.add(p) ;
		}

		public void setTotalParentCategories(int total) {
			totalParentCategories = total ;
		}

		public void addInLink(ResponsePage p) {
			if (inLinks == null)
				inLinks = new ArrayList<ResponsePage>() ;

			inLinks.add(p) ;
		}

		public void setTotalInLinks(int total) {
			totalInLinks = total ;
		}

		public void addOutLink(ResponsePage p) {
			if (outLinks == null)
				outLinks = new ArrayList<ResponsePage>() ;

			outLinks.add(p) ;
		}

		public void setTotalOutLinks(int total) {
			totalOutLinks = total ;
		}


	}

	public static class ResponseImage {
		@Expose
		@Attribute
		String url ;
		
		public ResponseImage(String url) {
			this.url = url ;
		}
	}

	public static class ResponseLabel {
		@Expose
		@Attribute
		String text ;

		@Expose
		@Attribute
		long occurrances ;

		@Expose
		@Attribute
		double proportion ;

		@Expose
		@Attribute
		boolean isPrimary ;

		@Expose
		@Attribute
		boolean fromRedirect ;

		@Expose
		@Attribute
		boolean fromTitle ;

		public ResponseLabel(Article.Label lbl, long totalOccurrances) {

			text = lbl.getText() ;
			occurrances = lbl.getLinkOccCount() ;
			proportion = (double) occurrances/totalOccurrances ;
			isPrimary = lbl.isPrimary() ;
			fromRedirect = lbl.isFromRedirect() ;
			fromTitle = lbl.isFromTitle() ;
		}
	}

	public static class ResponseTranslation {

		@Expose
		@Attribute
		String lang ;

		@Expose
		@Text(data=true)
		String text ;

		public ResponseTranslation(String lang, String text) {
			this.lang = lang ;
			this.text = text ;
		}
	}

	public static class ResponsePage {

		@Expose
		@Attribute
		private int id ;

		@Expose
		@Attribute
		private String title ;

		@Expose
		@Attribute(required=false)
		private Double relatedness ;

		public ResponsePage(Page p) {
			this.id = p.getId() ;
			this.title = p.getTitle() ;
		}

		public void setRelatedness(double relatedness) {
			this.relatedness = relatedness ;
		}

	}

}
