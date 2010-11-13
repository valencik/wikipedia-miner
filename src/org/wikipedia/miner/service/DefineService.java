package org.wikipedia.miner.service;

import java.net.URL;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.w3c.dom.Element;
import org.wikipedia.miner.model.Article;
import org.wikipedia.miner.model.Page;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.model.Page.PageType;
import org.wikipedia.miner.service.param.BooleanParameter;
import org.wikipedia.miner.service.param.EnumParameter;
import org.wikipedia.miner.service.param.IntParameter;

@SuppressWarnings("serial")
public class DefineService extends Service {


	public enum Length{LONG, SHORT} ;

	private Pattern fb_imagePattern = Pattern.compile("\"image\"\\:\\[(.*?)\\]") ;
	private Pattern fb_idPattern = Pattern.compile("\"id\"\\:\"(.*?)\"") ;

	private IntParameter prmId ;
	private EnumParameter<Length> prmLength ;
	private BooleanParameter prmEscape ;
	private BooleanParameter prmLabels ;
	private BooleanParameter prmTranslations ;
	private BooleanParameter prmImages ;
	private IntParameter prmImageWidth ;
	private IntParameter prmImageHeight ;


	boolean imagesAvailable = false ;

	//TODO: make this accept article titles as well.

	public DefineService() {
		super(
				"<p>This services provides definitions for articles from their first sentences or paragraphs, in either plain text, wiki format, or html. </p>" +
				"<p>It can optionally return the labels (synonyms, etc.) that have been used to refer to the article</p>" +
				"<p>It can also optionally obtain URLs for relevant images from <a href='www.freebase.org'>FreeBase</a></p>" + 
				"<p>You can only obtain definitions from unique page ids. If you want definitions for terms, then use the search service to identify the unique id first.</p>"
		);



	}


	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		prmId = new IntParameter("id", "The unique identifier of the page to define", null) ;
		addGlobalParameter(prmId) ;

		String[] descLength = {"first sentence", "first paragraph"} ;
		prmLength = new EnumParameter<Length>("length", "The required length of the definition", Length.SHORT, Length.values(), descLength) ;
		addGlobalParameter(prmLength) ;

		addGlobalParameter(getHub().getFormatter().getEmphasisFormatParam()) ;
		addGlobalParameter(getHub().getFormatter().getLinkFormatParam()) ;

		prmEscape = new BooleanParameter("escapeDefinition", "Wether the definition markup should be escaped or encoded directly", false) ;
		addGlobalParameter(prmEscape) ;

		prmLabels = new BooleanParameter("labels", "<b>true</b> if labels (synonyms, etc) for this topic are to be returned, otherwise </b>false</b>", false) ;
		addGlobalParameter(prmLabels) ;

		prmTranslations = new BooleanParameter("translations", "<b>true</b> if translations (language links) for this topic are to be returned, otherwise </b>false</b>", false) ;
		addGlobalParameter(prmTranslations) ;

		prmImages = new BooleanParameter("images", "Whether or not to retrieve relevant image urls from freebase", false) ;
		addGlobalParameter(prmImages) ;

		prmImageWidth = new IntParameter("maxImageWidth", "Images can be scaled. This defines their maximum width, in pixels", 150) ;
		addGlobalParameter(prmImageWidth) ;

		prmImageHeight = new IntParameter("maxImageHeight", "Images can be scaled. This defines their maximum height, in pixels", 150) ;
		addGlobalParameter(prmImageHeight) ;
	}




	@Override
	public Element buildWrappedResponse(HttpServletRequest request, Element xmlResponse) throws Exception {

		Wikipedia wikipedia = getWikipedia(request) ;


		Integer id = prmId.getValue(request) ;
		if (id == null) {
			xmlResponse.setAttribute("unspecifiedParameters", "true") ;
			return xmlResponse ;
		}

		Page page = wikipedia.getPageById(id) ;
		if (page==null) 
			return buildErrorResponse("'" + id + "' is an unknown id", xmlResponse) ;


		if (page.getType() != PageType.article) {
			return buildErrorResponse("'" + id + "' is not an article id", xmlResponse) ;
		}

		Article art = (Article)page ;

		xmlResponse.setAttribute("title", art.getTitle()) ;

		String definition = null ;

		if (prmLength.getValue(request)==Length.SHORT) 
			definition = art.getSentenceMarkup(0) ; 
		else
			definition = art.getFirstParagraphMarkup() ; 

		definition = getHub().getFormatter().format(definition, request, wikipedia) ;


		if (prmEscape.getValue(request) == true) {
			Element d = getHub().createElement("Definition") ;
			d.appendChild(getHub().createTextNode(definition)) ;
			xmlResponse.appendChild(d) ;
		} else {			
			xmlResponse.appendChild(getHub().createElement("Definition", definition)) ;
		}

		if (prmLabels.getValue(request)) {
			//get labels for this concept

			Article.Label[] labels = art.getLabels() ;
			if (labels.length > 0) {
				Element xmlLabels = getHub().createElement("Labels") ;

				int total = 0 ;
				for (Article.Label lbl:labels) 
					total += lbl.getLinkOccCount() ;

				for (Article.Label lbl:labels) {
					long occ = lbl.getLinkOccCount() ;

					if (occ > 0) {
						Element xmlLabel = getHub().createElement("Label") ;
						xmlLabel.appendChild(getHub().createTextNode(lbl.getText())) ;
						xmlLabel.setAttribute("occurances", String.valueOf(occ)) ;
						xmlLabel.setAttribute("proportion", getHub().format((double)occ/total)) ;
						xmlLabel.setAttribute("isPrimary", String.valueOf(lbl.isPrimary())) ;
						xmlLabel.setAttribute("fromRedirect", String.valueOf(lbl.isFromRedirect())) ;
						xmlLabel.setAttribute("fromTitle", String.valueOf(lbl.isFromTitle())) ;

						xmlLabels.appendChild(xmlLabel) ;
					}
				}
				xmlResponse.appendChild(xmlLabels) ;
			}
		}

		if (prmTranslations.getValue(request)) {

			TreeMap<String,String> translations = art.getTranslations() ;

			if (translations.size() > 0) {
				Element xmlTranslations = getHub().createElement("Translations") ;

				for (Map.Entry<String,String> entry:translations.entrySet()) {
					Element xmlTranslation = getHub().createElement("Translation") ;
					xmlTranslation.setAttribute("lang", entry.getKey()) ;
					xmlTranslation.appendChild(getHub().createTextNode(entry.getValue())) ;
					xmlTranslations.appendChild(xmlTranslation) ;
				}
				xmlResponse.appendChild(xmlTranslations) ;
			}

		}


		if (prmImages.getValue(request)) {

			URL freebaseRequest = new URL("http://www.freebase.com/api/service/mqlread?query={\"query\":{\"key\":[{\"namespace\":\"/wikipedia/en_id\",\"value\":\"" + id + "\"}], \"type\":\"/common/topic\", \"article\":[{\"id\":null}], \"image\":[{\"id\":null}]}}") ;

			String freebaseResponse = getHub().getRetriever().getWebContent(freebaseRequest) ;

			freebaseResponse = freebaseResponse.replaceAll("\\s", "") ;

			Matcher m = fb_imagePattern.matcher(freebaseResponse) ;

			if (m.find()) {
				Matcher n = fb_idPattern.matcher(m.group(1)) ;
				while (n.find()) {
					Element xmlImage = getHub().createElement("Image") ;
					xmlImage.setAttribute("url", "http://www.freebase.com/api/trans/image_thumb" + n.group(1).replace("\\/", "/") + "?maxwidth=" + prmImageWidth.getValue(request) + "&maxheight=" + prmImageHeight.getValue(request)) ;
					xmlResponse.appendChild(xmlImage) ;
				}
			}

		}


		return xmlResponse;
	}

}
