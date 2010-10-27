package org.wikipedia.miner.service;

//import info.bliki.wiki.model.WikiModel;

import java.io.File;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.wikipedia.miner.comparison.ArticleComparer;
import org.wikipedia.miner.comparison.LabelComparer;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.util.WikipediaConfiguration;
import org.xml.sax.InputSource;

import com.sun.org.apache.xerces.internal.dom.DocumentImpl;

public class ServiceHub {
	
	private static ServiceHub instance ;
	
	private HubConfiguration config ;
	private HashMap<String, Wikipedia> wikipediasByName ;
	
	private HashMap<String, ArticleComparer> articleComparersByWikiName ;
	private HashMap<String, LabelComparer> labelComparersByWikiName ;
	
	
	public HashSet<Service> registeredServices ;
	
	
	private MarkupFormatter formatter = new MarkupFormatter() ;
	private WebContentRetriever retriever ;
	private Document doc = new DocumentImpl();
	private DOMParser parser = new DOMParser() ;
	
	private DecimalFormat decimalFormat = (DecimalFormat) NumberFormat.getInstance(Locale.US);
	
	//WikiModel wikiModel = new WikiModel("http://www.mywiki.com/wiki/${image}", "http://www.mywiki.com/wiki/${title}");


	
	// Protect the constructor, so no other class can call it
	private ServiceHub(ServletContext context) throws ServletException {

		wikipediasByName = new HashMap<String, Wikipedia>() ;
		articleComparersByWikiName = new HashMap<String, ArticleComparer>()  ;
		labelComparersByWikiName = new HashMap<String, LabelComparer>()  ;
		
		
		registeredServices = new HashSet<Service>() ;
				
		try {
			String hubConfigFile = context.getInitParameter("hubConfigFile") ;
			config = new HubConfiguration(new File(hubConfigFile)) ; 
					
			for (String wikiName:config.getWikipediaNames()) {
				File wikiConfigFile = new File(config.getWikipediaConfig(wikiName)) ;
				WikipediaConfiguration wikiConfig = new WikipediaConfiguration(wikiConfigFile);
				
				Wikipedia wikipedia = new Wikipedia(wikiConfig, true) ;
				wikipediasByName.put(wikiName, wikipedia) ;
				
				ArticleComparer artCmp = null ;
				if (wikiConfig.getArticleComparisonModel() != null) {
					artCmp = new ArticleComparer(wikipedia) ;
					articleComparersByWikiName.put(wikiName, artCmp) ;
				}
				
				if (artCmp != null && wikiConfig.getLabelDisambiguationModel() != null && wikiConfig.getLabelComparisonModel() != null) {
					LabelComparer lblCmp = new LabelComparer(wikipedia, artCmp) ;
					labelComparersByWikiName.put(wikiName, lblCmp) ;
				}
			}
		
		
			retriever = new WebContentRetriever(config) ;
		} catch (Exception e) {
			throw new ServletException(e) ;
		}
	} 
	  
	public static ServiceHub getInstance(ServletContext context) throws ServletException {
		
		if (instance != null) 
			return instance ;
		
		instance = new ServiceHub(context) ;
		return instance ;
		
	}
	
	public void registerService(Service service) {
		registeredServices.add(service) ;
	}
	
	public void dropService(Service service) {
		registeredServices.remove(service) ;
		
		
		if (registeredServices.isEmpty()) {
			
			for (Wikipedia w:wikipediasByName.values()) 
				w.close() ;
		}
	}
	
	public String getDefaultWikipediaName() {
		return config.getDefaultWikipediaName() ;
	}
	
	public Wikipedia getWikipedia(String wikiName) {
		return wikipediasByName.get(wikiName) ;
	}
	
	public String getWikipediaDescription(String wikiName) {
		return config.getWikipediaDescription(wikiName) ;
	}
	
	public String[] getWikipediaNames() {
		
		Set<String> wikipediaNames = wikipediasByName.keySet() ;
		return wikipediaNames.toArray(new String[wikipediaNames.size()]) ;
	}
	
	public ArticleComparer getArticleComparer(String wikiName) {
		return articleComparersByWikiName.get(wikiName) ;
	}
	
	public LabelComparer getLabelComparer(String wikiName) {
		return labelComparersByWikiName.get(wikiName) ;
	}
	
	public MarkupFormatter getFormatter() {
		return formatter ;
	}
	
	public WebContentRetriever getRetriever() {
		return retriever ;
	}
		
	/*
	public Document getResponseDocument() {
		return doc ;
	}*/
	
	public DOMParser getParser() {
		return parser ;
	}
	
	
	public Element createElement(String tagName) {
		
		return doc.createElement(tagName) ;
	}
	
	public Text createTextNode(String data) {
		return doc.createTextNode(data) ;
	}
	
	public Element createElement(String tagName, String xmlContent)  {

		try {
			//try to parse the xml content
			parser.parse(new InputSource(new StringReader("<" + tagName + ">" + xmlContent.replaceAll("&", "&amp;") + "</" + tagName + ">"))) ;	

			Element e = parser.getDocument().getDocumentElement() ;		
			return (Element) doc.importNode(e, true) ;
		} catch (Exception exception) {
			//if that fails, just dump the xml content as a text node within the element. All special characters will be escaped.

			Element e = doc.createElement(tagName) ;
			e.appendChild(doc.createTextNode(xmlContent)) ;
			return e ;			
		}
	}
	
	public String format(double number) {
		return decimalFormat.format(number) ;
	}
	
	
	
	
}
