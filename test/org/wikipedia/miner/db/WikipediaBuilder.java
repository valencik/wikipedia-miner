package org.wikipedia.miner.db;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;

import org.wikipedia.miner.util.WikipediaConfiguration;
import org.wikipedia.miner.util.text.PorterStemmer;
import org.xml.sax.SAXException;

public class WikipediaBuilder {

	public static void main(String args[]) throws ParserConfigurationException, SAXException, IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, XMLStreamException {
		
		if (args.length != 1) {
			System.err.println("Invalid args: no path to wikipedia miner config file") ;
			
			return ;
		}
		
		File confFile = new File(args[0]) ;
		
		if (!confFile.canRead()) {
			System.err.println(confFile + " is not readable") ;
			return ;
		}
		
		WikipediaConfiguration conf = new WikipediaConfiguration(confFile) ;
		
		File dataDir = conf.getDatabaseDirectory() ;
		
		if (dataDir == null) 
			System.err.println(confFile + " does not specify a data directory") ;
		
		WEnvironment.buildEnvironment(conf, conf.getDataDirectory(), true) ;
		
	}
}
