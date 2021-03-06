package org.wikipedia.miner.service;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.service.UtilityMessages.InvalidIdMessage;
import org.wikipedia.miner.service.UtilityMessages.InvalidTitleMessage;
import org.xjsf.Service;
import org.xjsf.UtilityMessages.ErrorMessage;
import org.xjsf.UtilityMessages.ParameterMissingMessage;
import org.xjsf.param.BooleanParameter;
import org.xjsf.param.EnumParameter;
import org.xjsf.param.IntParameter;
import org.xjsf.param.ParameterGroup;
import org.xjsf.param.StringParameter;

import com.google.gson.annotations.Expose;

@SuppressWarnings("serial")
public class ExploreArticleService extends WMService{

	//TODO: modify freebase image request to use article titles rather than ids
	//TODO: if lang is not en, use languageLinks to translate article title to english.

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

				"<p></p>", false
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

		addGlobalParameter(getWMHub().getFormatter().getEmphasisFormatParam()) ;
		addGlobalParameter(getWMHub().getFormatter().getLinkFormatParam()) ;

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

	public Service.Message buildWrappedResponse(HttpServletRequest request) throws Exception {

		Wikipedia wikipedia = getWikipedia(request) ;

		ArticleComparer artComparer = null ;
		if (prmLinkRelatedness.getValue(request)) {
			artComparer = getWMHub().getArticleComparer(this.getWikipediaName(request)) ;
			if (artComparer == null) 
				return new ErrorMessage(request, "Relatedness measures are unavailable for this instance of wikipedia") ;
		}

		ParameterGroup grp = getSpecifiedParameterGroup(request) ;

		if (grp == null) 
			return new ParameterMissingMessage(request) ;

		Article art = null ;

		switch(GroupName.valueOf(grp.getName())) {

		case id :
			Integer id = prmId.getValue(request) ;

			org.wikipedia.miner.model.Page page = wikipedia.getPageById(id) ;
			if (page==null) 
				return new InvalidIdMessage(request, id) ;

			switch(page.getType()) {
			case disambiguation:
			case article:
				art = (Article)page ;
				break ;
			default:
				return new InvalidIdMessage(request, id) ;
			}
			break ;
		case title :
			String title = prmTitle.getValue(request) ;
			art = wikipedia.getArticleByTitle(title) ;

			if (art == null)
				return new InvalidTitleMessage(request, title) ;
			break ;
		}

		Message msg = new Message(request, art) ;
		
		if (prmDefinition.getValue(request)) {
			String definition = null ;

			if (prmDefinitionLength.getValue(request)==DefinitionLength.SHORT) 
				definition = art.getSentenceMarkup(0) ; 
			else
				definition = art.getFirstParagraphMarkup() ; 

			msg.setDefinition(getWMHub().getFormatter().format(definition, request, wikipedia)) ;
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
					msg.addLabel(new Label(lbl, total)) ;

			}
		}

		if (prmTranslations.getValue(request)) {
			TreeMap<String,String> translations = art.getTranslations() ;
			for (Map.Entry<String,String> entry:translations.entrySet()) 
				msg.addTranslation(new Translation(entry.getKey(), entry.getValue())) ;	
		}


		if (prmImages.getValue(request)) {
				URL freebaseRequest = new URL("http://www.freebase.com/api/service/mqlread?query={\"query\":{\"key\":[{\"namespace\":\"/wikipedia/en_id\",\"value\":\"" + art.getId() + "\"}], \"type\":\"/common/topic\", \"article\":[{\"id\":null}], \"image\":[{\"id\":null}]}}") ;
	
				String freebaseResponse = getWMHub().getRetriever().getWebContent(freebaseRequest) ;
	
				freebaseResponse = freebaseResponse.replaceAll("\\s", "") ;
	
				Matcher m = fb_imagePattern.matcher(freebaseResponse) ;
	
				if (m.find()) {
					Matcher n = fb_idPattern.matcher(m.group(1)) ;
					while (n.find()) {
						String url = "http://www.freebase.com/api/trans/image_thumb" + n.group(1).replace("\\/", "/") + "?maxwidth=" + prmImageWidth.getValue(request) + "&maxheight=" + prmImageHeight.getValue(request) ;
						msg.addImage(new Image(url)) ;
					}
				}
		}

		if (prmParentCategories.getValue(request)) {
			Category[] parents = art.getParentCategories() ;

			msg.setTotalParentCategories(parents.length) ;
			for (Category parent:parents) 
				msg.addParentCategory(new Page(parent)) ;		
		}

		if (prmOutLinks.getValue(request)) {

			int start = prmOutLinkStart.getValue(request) ;
			int max = prmOutLinkMax.getValue(request) ;
			if (max <= 0) 
				max = Integer.MAX_VALUE ;
			else
				max = max + start ;

			Article[] linksOut = art.getLinksOut() ;

			msg.setTotalOutLinks(linksOut.length) ;
			for (int i=start ; i < max && i < linksOut.length ; i++) {
				Page p = new Page(linksOut[i]) ;
				if (artComparer != null)
					p.setRelatedness(artComparer.getRelatedness(art, linksOut[i])) ;

				msg.addOutLink(p) ;
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

			msg.setTotalInLinks(linksIn.length) ;
			for (int i=start ; i < max && i < linksIn.length ; i++) {
				Page p = new Page(linksIn[i]) ;
				if (artComparer != null)
					p.setRelatedness(artComparer.getRelatedness(art, linksIn[i])) ;

				msg.addInLink(p) ;
			}
		}
		
		return msg ;
	}

	public static class Message extends Service.Message {

		@Expose
		@Attribute
		private int id ;

		@Expose
		@Attribute
		private String title ;

		@Expose
		@Element(required=false, data=true)
		private String definition ;

		@Expose
		@ElementList(required=false, entry="image") 
		private ArrayList<Image> images = null ;
		
		@Expose
		@ElementList(required=false, entry="label") 
		private ArrayList<Label> labels = null ;

		@Expose
		@ElementList(required=false, entry="tranlation") 
		private ArrayList<Translation> translations = null ;

		@Expose
		@ElementList(required=false, entry="parentCategory") 
		private ArrayList<Page> parentCategories = null ;

		@Expose
		@Attribute(required=false)
		private Integer totalParentCategories ;

		@Expose
		@ElementList(required=false, entry="inLink") 
		private ArrayList<Page> inLinks = null ;

		@Expose
		@Attribute(required=false)
		private Integer totalInLinks ;

		@Expose
		@ElementList(required=false, entry="outLink") 
		private ArrayList<Page> outLinks = null ;

		@Expose
		@Attribute(required=false)
		private Integer totalOutLinks ;

		private Message(HttpServletRequest request, Article art) {	
			super(request) ;
			this.id = art.getId();
			this.title = art.getTitle();
		}

		private void setDefinition(String markup) {
			this.definition = markup ;
		}
		
		private void addImage(Image image) {
			if (images == null)
				images = new ArrayList<Image>() ;
			
			images.add(image) ;
		}

		private void addLabel(Label label) {
			if (labels == null)
				labels = new ArrayList<Label>() ;

			labels.add(label) ;
		}

		private void addTranslation(Translation t) {
			if (translations == null)
				translations = new ArrayList<Translation>() ;

			translations.add(t) ;
		}

		private void addParentCategory(Page p) {
			if (parentCategories == null)
				parentCategories = new ArrayList<Page>() ;

			parentCategories.add(p) ;
		}

		private void setTotalParentCategories(int total) {
			totalParentCategories = total ;
		}

		private void addInLink(Page p) {
			if (inLinks == null)
				inLinks = new ArrayList<Page>() ;

			inLinks.add(p) ;
		}

		private void setTotalInLinks(int total) {
			totalInLinks = total ;
		}

		private void addOutLink(Page p) {
			if (outLinks == null)
				outLinks = new ArrayList<Page>() ;

			outLinks.add(p) ;
		}

		private void setTotalOutLinks(int total) {
			totalOutLinks = total ;
		}

		public int getId() {
			return id;
		}

		public String getTitle() {
			return title;
		}

		public String getDefinition() {
			return definition;
		}

		public List<Image> getImages() {
			
			if (images == null) return Collections.unmodifiableList(new ArrayList<Image>()) ;
			
			return Collections.unmodifiableList(images);
		}

		public List<Label> getLabels() {
			
			if (labels == null) return Collections.unmodifiableList(new ArrayList<Label>()) ;
			
			return Collections.unmodifiableList(labels);
		}

		public List<Translation> getTranslations() {
			
			if (translations == null) return Collections.unmodifiableList(new ArrayList<Translation>()) ;
			
			return Collections.unmodifiableList(translations);
		}

		public List<Page> getParentCategories() {
			
			if (parentCategories == null) return Collections.unmodifiableList(new ArrayList<Page>()) ;
			
			return Collections.unmodifiableList(parentCategories);
		}

		public Integer getTotalParentCategories() {
			return totalParentCategories;
		}

		public List<Page> getInLinks() {
			
			if (inLinks == null) return Collections.unmodifiableList(new ArrayList<Page>()) ;
			
			return Collections.unmodifiableList(inLinks);
		}

		public Integer getTotalInLinks() {
			return totalInLinks;
		}

		public List<Page> getOutLinks() {
			
			if (outLinks == null) return Collections.unmodifiableList(new ArrayList<Page>()) ;
			
			return Collections.unmodifiableList(outLinks);
		}

		public Integer getTotalOutLinks() {
			return totalOutLinks;
		}
	}

	public static class Image {
		@Expose
		@Attribute
		private String url ;
		
		private Image(String url) {
			this.url = url ;
		}
		
		public String getUrl() {
			return url ;
		}
	}

	public static class Label {
		@Expose
		@Attribute
		private String text ;

		@Expose
		@Attribute
		private long occurrances ;

		@Expose
		@Attribute
		private double proportion ;

		@Expose
		@Attribute
		private boolean isPrimary ;

		@Expose
		@Attribute
		private boolean fromRedirect ;

		@Expose
		@Attribute
		private boolean fromTitle ;

		private Label(Article.Label lbl, long totalOccurrances) {

			text = lbl.getText() ;
			occurrances = lbl.getLinkOccCount() ;
			proportion = (double) occurrances/totalOccurrances ;
			isPrimary = lbl.isPrimary() ;
			fromRedirect = lbl.isFromRedirect() ;
			fromTitle = lbl.isFromTitle() ;
		}

		public String getText() {
			return text;
		}

		public long getOccurrances() {
			return occurrances;
		}

		public double getProportion() {
			return proportion;
		}

		public boolean isPrimary() {
			return isPrimary;
		}

		public boolean isFromRedirect() {
			return fromRedirect;
		}

		public boolean isFromTitle() {
			return fromTitle;
		}
	}

	public static class Translation {

		@Expose
		@Attribute
		private String lang ;

		@Expose
		@Text(data=true)
		private String text ;

		private Translation(String lang, String text) {
			this.lang = lang ;
			this.text = text ;
		}

		public String getLang() {
			return lang;
		}

		public String getText() {
			return text;
		}
	}

	public static class Page {

		@Expose
		@Attribute
		private int id ;

		@Expose
		@Attribute
		private String title ;

		@Expose
		@Attribute(required=false)
		private Double relatedness ;

		protected Page(org.wikipedia.miner.model.Page p) {
			this.id = p.getId() ;
			this.title = p.getTitle() ;
		}

		protected void setRelatedness(double relatedness) {
			this.relatedness = relatedness ;
		}

		public int getId() {
			return id;
		}

		public String getTitle() {
			return title;
		}

		public Double getRelatedness() {
			return relatedness;
		}
	}

}
