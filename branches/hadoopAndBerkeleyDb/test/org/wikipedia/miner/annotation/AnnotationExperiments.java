package org.wikipedia.miner.annotation;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.wikipedia.miner.annotation.ArticleCleaner.SnippetLength;
import org.wikipedia.miner.annotation.weighting.LinkDetector;
import org.wikipedia.miner.comparison.ArticleComparer;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.util.ArticleSet;
import org.wikipedia.miner.util.Result;
import org.wikipedia.miner.util.WikipediaConfiguration;
import org.xml.sax.SAXException;

import weka.classifiers.Classifier;
import weka.classifiers.meta.Bagging;
import weka.core.Utils;

public class AnnotationExperiments {
	
	public static void main(String args[]) throws Exception {
		
		WikipediaConfiguration conf = new WikipediaConfiguration(new File("configs/en_web.xml")) ;
		
		Wikipedia wikipedia = new Wikipedia(conf, false) ;
		
		/*
		Pattern mustMatch = Pattern.compile("\\{\\{Featured article\\s*\\}\\}") ;
				
		int[] sizes = {150,100,100,100} ;
		
		ArticleSet[] sets = ArticleSet.buildExclusiveArticleSets(sizes, wikipedia, 50,50,-1F,-1F, 500, 50000,0.3F, mustMatch, null) ;
		
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
		d.saveTrainingData(new File("data/disambig.arff")) ;
		
		//build disambiguation classifier
		Classifier dClassifier = new Bagging() ;
		dClassifier.setOptions(Utils.splitOptions("-P 10 -S 1 -I 10 -W weka.classifiers.trees.J48 -- -U -M 2")) ;
		d.buildClassifier(dClassifier) ;
		d.saveClassifier(new File("models/topicDisambiguation.model")) ;
		
		Result<Integer> dResult = d.test(disambigSet, wikipedia, SnippetLength.full, null) ;
		System.out.println("final: " + dResult) ;
		
		TopicDetector td = new TopicDetector(wikipedia, d, false, false) ;
		
		LinkDetector ld = new LinkDetector(wikipedia) ;
		
		
		ld.train(trainingSet,  SnippetLength.full, "blah", td, null) ;
		ld.saveTrainingData(new File("data/detect.arff")) ;
		
		Classifier ldClassifier = new Bagging() ;
		ldClassifier.setOptions(Utils.splitOptions("-P 10 -S 1 -I 10 -W weka.classifiers.trees.J48 -- -U -M 2")) ;
		ld.buildClassifier(ldClassifier) ;
		ld.saveClassifier(new File("models/linkDetection.model")) ;
		
		
		Result<Integer> ldResult  = ld.test(detectSet, SnippetLength.full, td, null) ;
		System.out.println("final: " + ldResult) ;
		
	}
	
}
