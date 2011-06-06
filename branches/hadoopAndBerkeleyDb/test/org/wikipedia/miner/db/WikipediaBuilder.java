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
		
		WikipediaConfiguration conf = new WikipediaConfiguration(new File("configs/en.xml")) ;
		
		//WEnvironment.buildEnvironment(conf, conf.getDataDirectory(), true) ;
		
		WEnvironment.prepareTextProcessor(new PorterStemmer(), conf, new File("/tmp"), true, 2) ;
	}
}
