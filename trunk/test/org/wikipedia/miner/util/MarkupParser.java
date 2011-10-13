package org.wikipedia.miner.util;

import java.io.File;
import java.util.Vector;

import org.wikipedia.miner.model.Article;
import org.wikipedia.miner.model.Wikipedia;

public class MarkupParser {

	public static void main(String args[]) throws Exception {
		
		WikipediaConfiguration conf = new WikipediaConfiguration(new File("configs/en.xml")) ;
		Wikipedia wikipedia = new Wikipedia(conf, false) ;
		MarkupStripper stripper = new MarkupStripper() ;
		
		WikiMinerModel model = new WikiMinerModel(wikipedia) ;
		
		model.setUp();
		
		
		Article art = wikipedia.getArticleByTitle("Epicurus") ;
		
		String markup = art.getMarkup() ;
		
		//Vector<int[]> regions = stripper.gatherTemplates(markup) ;
		//regions = stripper.getIsolatedRegions(regions, markup) ;
		//markup = stripper.stripRegions(markup, regions, null) ;
		
		System.out.println(model.render(markup)) ;
		
		
		
		
		
		model.tearDown() ;
		
		
	}
	
}
