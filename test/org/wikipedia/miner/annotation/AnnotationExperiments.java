package org.wikipedia.miner.annotation;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

import javax.xml.parsers.ParserConfigurationException;

import org.wikipedia.miner.annotation.ArticleCleaner.SnippetLength;
import org.wikipedia.miner.annotation.weighting.LinkDetector;
import org.wikipedia.miner.comparison.ArticleComparer;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.util.ArticleSet;
import org.wikipedia.miner.util.WikipediaConfiguration;
import org.xml.sax.SAXException;

public class AnnotationExperiments {

	public static void main(String args[]) throws Exception {
		
		WikipediaConfiguration conf = new WikipediaConfiguration(new File("configs/en_web.xml")) ;
		
		Wikipedia wikipedia = new Wikipedia(conf, false) ;
		
		/*
		int[] sizes = {150,100,100,100} ;
		
		ArticleSet[] sets = ArticleSet.buildExclusiveArticleSets(sizes, wikipedia, 50,50,-1F,-1F, 500, 50000,0.3F) ;
		
		sets[0].save(new File("data/articles_training.txt")) ;
		sets[1].save(new File("data/articles_develop.txt")) ;
		sets[2].save(new File("data/articles_testDisambig.txt")) ;
		sets[3].save(new File("data/articles_testDetect.txt")) ;
		
		*/
		
		ArticleSet trainingSet = new ArticleSet(new File("data/articles_training.txt"), wikipedia) ;
		ArticleSet disambigSet = new ArticleSet(new File("data/articles_testDisambig.txt"), wikipedia) ;
		ArticleSet detectSet = new ArticleSet(new File("data/articles_testDetect.txt"), wikipedia) ;
		
		Disambiguator d = new Disambiguator(wikipedia) ;
		d.train(trainingSet, SnippetLength.full, "blah", null) ;
		
		d.test(disambigSet, wikipedia, SnippetLength.full, null) ;
		
		TopicDetector td = new TopicDetector(wikipedia, d, false, false) ;
		
		LinkDetector ld = new LinkDetector(wikipedia) ;
		
		ld.train(trainingSet,  SnippetLength.full, "blah", td, null) ;
		ld.test(detectSet, SnippetLength.full, td, null) ;
		
	}
	
}
