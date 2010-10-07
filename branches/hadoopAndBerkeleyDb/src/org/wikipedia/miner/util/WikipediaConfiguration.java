package org.wikipedia.miner.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wikipedia.miner.db.WDatabase.CachePriority;
import org.wikipedia.miner.db.WDatabase.DatabaseType;
import org.wikipedia.miner.util.text.TextProcessor;
import org.xml.sax.SAXException;

public class WikipediaConfiguration {
	
	private enum ParamName{langCode,databaseDirectory,defaultTextProcessor,minLinksIn,minSenseProbability,minLinkProbability,databaseToCache,stopwordFile,disambiguationModel,linkDetectionModel,unknown} ;
	
	private String langCode ;

	private File dbDirectory ;
	private TextProcessor defaultTextProcessor = null ;

	private HashMap<DatabaseType, CachePriority> databasesToCache = new HashMap<DatabaseType, CachePriority>() ;

	private HashSet<String> stopwords = new HashSet<String>() ;
	private File detectionModel ;
	private File disambigModel ;


	private int minLinksIn = 0;
	private float minLinkProbability = 0 ;
	private float minSenseProbability = 0 ;

	private boolean readOnly ;
	
	
	
	
	
	public WikipediaConfiguration(Element xml) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		initFromXml(xml) ;
	}

	public WikipediaConfiguration(File configFile) throws ParserConfigurationException, SAXException, IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.parse(configFile);
		doc.getDocumentElement().normalize();
		
		initFromXml(doc.getDocumentElement()) ;
	}

	public WikipediaConfiguration(String langCode, File dbDirectory, boolean readOnly) {
		this.langCode = langCode ;
		this.dbDirectory = dbDirectory ;
		this.readOnly = readOnly ;
	}
	
	
	
	

	public String getLangCode() {
		return langCode ;
	}
		
	public File getDatabaseDirectory() {
		return dbDirectory ;
	}

	public boolean isReadOnly() {
		return readOnly ;
	}

	public void setDefaultTextProcessor(TextProcessor tp) {
		defaultTextProcessor = tp ;
	}

	public TextProcessor getDefaultTextProcessor() {
		return defaultTextProcessor ;
	}

	public void addDatabaseToCache(DatabaseType type) {
		databasesToCache.put(type, CachePriority.space) ;
	}
	
	public void addDatabaseToCache(DatabaseType type, CachePriority priority) {
		databasesToCache.put(type, priority) ;
	}
	
	public Set<DatabaseType> getDatabasesToCache() {
		return databasesToCache.keySet() ;
	}
	
	public CachePriority getCachePriority(DatabaseType databaseType) {
		return databasesToCache.get(databaseType) ;
	}

	public int getMinLinksIn() {
		return minLinksIn;
	}

	public void setMinLinksIn(int minLinksIn) {
		this.minLinksIn = minLinksIn;
	}

	public float getMinLinkProbability() {
		return minLinkProbability;
	}

	public void setMinLinkProbability(float minLinkProbability) {
		this.minLinkProbability = minLinkProbability;
	}

	public float getMinSenseProbability() {
		return minSenseProbability;
	}

	public void setMinSenseProbability(float minSenseProbability) {
		this.minSenseProbability = minSenseProbability;
	}

	public boolean isStopword(String stopword) {

		return stopwords.contains(stopword.trim()) ;
	}

	public void setStopwords(HashSet<String> stopwords) {
		this.stopwords = stopwords ;
	}

	public void setStopwords(File stopwordFile) throws IOException {

		stopwords = new HashSet<String>() ;

		BufferedReader input = new BufferedReader(new FileReader(stopwordFile)) ;
		String line ;
		while ((line=input.readLine()) != null) 
			stopwords.add(line.trim()) ;
	}

	public File getDetectionModel() {
		return detectionModel;
	}

	public void setDetectionModel(File detectionModel) {
		this.detectionModel = detectionModel;
	}

	public File getDisambigModel() {
		return disambigModel;
	}

	public void setDisambigModel(File disambigModel) {
		this.disambigModel = disambigModel;
	}
	
	
	
	@SuppressWarnings("unchecked")
	private void initFromXml(Element xml) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		
		NodeList children = xml.getChildNodes() ;
		
		for (int i=0 ; i<children.getLength() ; i++) {
			
			Node xmlChild = children.item(i) ;
			
			if (xmlChild.getNodeType() == Node.ELEMENT_NODE) {
				
				Element xmlParam = (Element)xmlChild ;
				
				String paramName = xmlParam.getNodeName() ;
				String paramValue = getParamValue(xmlParam) ;
				
				if (paramValue == null)
					continue ;
				
				switch(resolveParamName(xmlParam.getNodeName())) {
				
				case langCode:
					this.langCode = paramValue ;
					break ;
				case databaseDirectory:
					this.dbDirectory = new File(paramValue) ;
					break ;
				case defaultTextProcessor:
					Class tpClass = Class.forName(paramValue) ;
					this.defaultTextProcessor = (TextProcessor)tpClass.newInstance() ;
					break ;
				case minLinksIn:
					this.minLinksIn = Integer.valueOf(paramValue) ;
					break ;
				case minSenseProbability:
					this.minSenseProbability = Float.valueOf(paramValue) ;
					break ;
				case minLinkProbability:
					this.minLinkProbability = Float.valueOf(paramValue) ;
					break ;
				case databaseToCache: 
					if (xmlParam.hasAttribute("priority"))
						addDatabaseToCache(DatabaseType.valueOf(paramValue), CachePriority.valueOf(xmlParam.getAttribute("priority"))) ;
					else
						addDatabaseToCache(DatabaseType.valueOf(paramValue)) ;
					break ;
				case stopwordFile:
					this.setStopwords(new File(paramValue)) ;
					break ;
				case disambiguationModel:
					this.disambigModel = new File(paramValue) ;
					break ;
				case linkDetectionModel:
					this.detectionModel = new File(paramValue) ;
					break ;
				default:
					Logger.getLogger(WikipediaConfiguration.class).warn("Ignoring unknown parameter: '" + paramName + "'") ;
				} ;
			}
			
		
			//TODO: throw fit if mandatory params (langCode, dbDirectory) are missing. 	
		}
	}
	
	private String getParamValue(Element xmlParam) {
		
		Node nodeContent = xmlParam.getChildNodes().item(0) ;
		
		if (nodeContent == null)
			return null ;
		
		if (nodeContent.getNodeType() != Node.TEXT_NODE)
			return null ;
		
		String content = nodeContent.getTextContent().trim() ;
		
		if (content.length() == 0)
			return null ;
		
		return content ;
	}
		
	private ParamName resolveParamName(String name) {
		try {
			return ParamName.valueOf(name.trim()) ;
		} catch (Exception e) {
			return ParamName.unknown ;
		}
	}
	
	public static void main(String args[]) throws Exception {
		
		WikipediaConfiguration conf = new WikipediaConfiguration(new File("/home/dmilne/workspaces/Eclipse/wikipediaMiner_hadoopAndBerkeleyDB/configs/en.xml")) ;
		
	}
}
