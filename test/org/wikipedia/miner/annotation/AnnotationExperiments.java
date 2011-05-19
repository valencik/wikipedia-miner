package org.wikipedia.miner.annotation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.wikipedia.miner.annotation.ArticleCleaner.SnippetLength;
import org.wikipedia.miner.annotation.weighting.LinkDetector;
import org.wikipedia.miner.comparison.ArticleComparer.DataDependency;
import org.wikipedia.miner.db.WDatabase.CachePriority;
import org.wikipedia.miner.db.WDatabase.DatabaseType;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.util.ArticleSet;
import org.wikipedia.miner.util.MemoryMeasurer;
import org.wikipedia.miner.util.Result;
import org.wikipedia.miner.util.WikipediaConfiguration;

public class AnnotationExperiments {


	ArticleSet _trainingSet ;
	ArticleSet _disambigSet ;
	ArticleSet _detectSet ;

	public AnnotationExperiments(Wikipedia wikipedia, File trainingSet, File disambigSet, File detectSet) throws Exception {

		//_wikipedia = wikipedia ;

		_trainingSet = new ArticleSet(trainingSet, wikipedia) ;
		_disambigSet = new ArticleSet(disambigSet, wikipedia) ;
		_detectSet = new ArticleSet(detectSet, wikipedia) ;

	}

	public static void main(String args[]) throws Exception {

		WikipediaConfiguration conf = new WikipediaConfiguration(new File("configs/en.xml")) ;

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

		AnnotationExperiments ae = new AnnotationExperiments(
				wikipedia, 
				new File("data/articles_training.txt"), 
				new File("data/articles_testDisambig.txt"), 
				new File("data/articles_testDetect.txt")
		) ;
		
		wikipedia.close();


		ArrayList<WikificationDataPoint> results = new ArrayList<WikificationDataPoint>() ;


		ArrayList<DataDependency> d = new ArrayList<DataDependency>() ;

		//pageLinksIn
		d.add(DataDependency.pageLinksIn) ;
		conf = configureConf(conf, d) ;
		results.add(ae.doExperiment(conf)) ;
		//conf = configureConf(conf, d, true) ;
		//lblCmpResults.add(ce.doLabelComparisonExperiment(conf, true)) ;

		//pageLinksIn+linkCounts
		d.add(DataDependency.linkCounts) ;
		conf = configureConf(conf,d) ;
		results.add(ae.doExperiment(conf)) ;

		//pageLinksOut
		d.clear();
		d.add(DataDependency.pageLinksOut) ;
		conf = configureConf(conf, d) ;
		results.add(ae.doExperiment(conf)) ;

		//pageLinksOut+linkCounts
		d.add(DataDependency.linkCounts) ;
		conf = configureConf(conf,d) ;
		results.add(ae.doExperiment(conf)) ;

		//pageLinksIn+pageLinksOut+linkCounts
		d.add(DataDependency.pageLinksIn) ;
		conf = configureConf(conf,d) ;
		results.add(ae.doExperiment(conf)) ;


		System.out.println("\n\nFINAL RESULTS\n\n") ;
		for (WikificationDataPoint p:results) {
			System.out.println(p) ;
		}











	}

	private static WikipediaConfiguration configureConf(WikipediaConfiguration conf, ArrayList<DataDependency> d) {

		conf.clearDatabasesToCache() ;

		EnumSet<DataDependency> dependencies = EnumSet.copyOf(d) ;
		conf.setArticleComparisonDependancies(dependencies) ;

		if (dependencies.contains(DataDependency.pageLinksIn))
			conf.addDatabaseToCache(DatabaseType.pageLinksInNoSentences, CachePriority.speed) ;

		if (dependencies.contains(DataDependency.pageLinksOut))
			conf.addDatabaseToCache(DatabaseType.pageLinksOutNoSentences, CachePriority.speed) ;

		if (dependencies.contains(DataDependency.linkCounts))
			conf.addDatabaseToCache(DatabaseType.pageLinkCounts, CachePriority.speed) ;

		conf.addDatabaseToCache(DatabaseType.articlesByTitle, CachePriority.space) ;
		conf.addDatabaseToCache(DatabaseType.label, CachePriority.space) ;

		return conf ;
	}

	private WikificationDataPoint doExperiment(WikipediaConfiguration conf) throws IOException, Exception {

		


		WikificationDataPoint point = new WikificationDataPoint() ;
		point.dependencies = conf.getArticleComparisonDependancies() ;
		point.lang = conf.getLangCode() ;
		point.usingML = (conf.getArticleComparisonModel() != null) ;


		long memStart = MemoryMeasurer.getBytesUsed() ;
		long timeStart = System.currentTimeMillis() ;

		Wikipedia wikipedia = new Wikipedia(conf, false) ;

		long timeEnd = System.currentTimeMillis() ;
		long memEnd = MemoryMeasurer.getBytesUsed() ;

		point.cacheTime = timeEnd-timeStart ;
		point.cacheSpace = memEnd-memStart ;


		Disambiguator d = new Disambiguator(wikipedia) ;

		long disambigTrainStart = System.currentTimeMillis() ;
		d.train(_trainingSet, SnippetLength.full, "blah", null) ;
		d.buildDefaultClassifier() ;
		long disambigTrainEnd = System.currentTimeMillis() ;

		point.disambig_trainTime = disambigTrainEnd - disambigTrainStart ;

		long disambigTestStart = System.currentTimeMillis() ;
		Result<Integer> disambigResult = d.test(_disambigSet, wikipedia, SnippetLength.full, null) ;
		System.out.println("final: " + disambigResult) ;
		long disambigTestEnd = System.currentTimeMillis() ;

		point.disambig_testTime = disambigTestEnd - disambigTestStart ;
		point.disambig_p = disambigResult.getPrecision() ;
		point.disambig_r = disambigResult.getRecall() ;
		point.disambig_f = disambigResult.getFMeasure() ;

		TopicDetector td = new TopicDetector(wikipedia, d, false, false) ;

		LinkDetector ld = new LinkDetector(wikipedia) ;

		long detectTrainStart = System.currentTimeMillis() ;
		ld.train(_trainingSet,  SnippetLength.full, "blah", td, null) ;
		ld.buildDefaultClassifier() ;
		long detectTrainEnd = System.currentTimeMillis() ;

		point.detect_trainTime = detectTrainEnd - detectTrainStart ;

		long detectTestStart = System.currentTimeMillis() ;
		Result<Integer> detectResult  = ld.test(_detectSet, SnippetLength.full, td, null) ;
		long detectTestEnd = System.currentTimeMillis() ;

		System.out.println("final: " + detectResult) ;

		point.detect_testTime = detectTestEnd - detectTestStart ;
		point.detect_p = detectResult.getPrecision() ;
		point.detect_r = detectResult.getRecall() ;
		point.detect_f = detectResult.getFMeasure() ;
		
		
		
		wikipedia.close();
		
		
		System.out.println("Sleeping for garbage collection...") ;
		System.gc() ;	
		Thread.sleep(60000) ;
		

		return point ;
	}

	private class WikificationDataPoint { 

		public EnumSet<DataDependency> dependencies ;

		public String lang ;
		public boolean usingML;

		public long disambig_trainTime ;
		public long disambig_testTime ;

		public long detect_trainTime ;
		public long detect_testTime ;

		public double disambig_p ;
		public double disambig_r ;
		public double disambig_f ;

		public double detect_p ;
		public double detect_r ;
		public double detect_f ;

		public long cacheTime ;
		public long cacheSpace ;

		public String toString() {

			StringBuffer sb = new StringBuffer() ;
			sb.append(getDependencyString(dependencies)) ;
			sb.append("\t") ;
			sb.append(lang) ;
			sb.append("\t") ;
			sb.append(usingML) ;
			sb.append("\t") ;
			sb.append(disambig_p) ;
			sb.append("\t") ;
			sb.append(disambig_r) ;
			sb.append("\t") ;
			sb.append(disambig_f) ;
			sb.append("\t") ;
			sb.append(disambig_trainTime) ;
			sb.append("\t") ;
			sb.append(disambig_testTime) ;
			sb.append("\t") ;
			sb.append(detect_p) ;
			sb.append("\t") ;
			sb.append(detect_r) ;
			sb.append("\t") ;
			sb.append(detect_f) ;
			sb.append("\t") ;
			sb.append(detect_trainTime) ;
			sb.append("\t") ;
			sb.append(detect_testTime) ;
			sb.append("\t") ;
			sb.append(cacheTime) ;
			sb.append("\t") ;
			sb.append(cacheSpace) ;


			return sb.toString() ;
		}
	}



	public String getDependencyString(EnumSet<DataDependency> dependencies) {

		StringBuffer sb = new StringBuffer() ;
		if (dependencies.contains(DataDependency.pageLinksIn)) 
			sb.append(DataDependency.pageLinksIn + "+") ;

		if (dependencies.contains(DataDependency.pageLinksOut)) 
			sb.append(DataDependency.pageLinksOut + "+") ;

		if (dependencies.contains(DataDependency.linkCounts)) 
			sb.append(DataDependency.linkCounts + "+") ;

		sb.deleteCharAt(sb.length() -1) ;
		return sb.toString() ;
	}




}
