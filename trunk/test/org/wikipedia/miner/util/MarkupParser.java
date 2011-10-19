package org.wikipedia.miner.util;

import java.io.File;
import java.util.Vector;

import org.wikipedia.miner.model.Article;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.model.Page.PageType;

public class MarkupParser {

	public static void main(String args[]) throws Exception {
		
		WikipediaConfiguration conf = new WikipediaConfiguration(new File("configs/en.xml")) ;
		conf.clearDatabasesToCache() ;
		Wikipedia wikipedia = new Wikipedia(conf, false) ;
		
		System.out.println("Templates: " + wikipedia.getEnvironment().getDbTemplatesByTitle().getDatabaseSize()) ;
		
		PageIterator iter = wikipedia.getPageIterator(PageType.template) ;
		while (iter.hasNext())
			System.out.println(" - " + iter.next()) ;
		
		
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
