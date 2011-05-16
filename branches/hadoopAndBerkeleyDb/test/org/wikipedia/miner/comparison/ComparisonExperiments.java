package org.wikipedia.miner.comparison;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Random;

import jsc.correlation.SpearmanCorrelation;

import org.wikipedia.miner.comparison.ArticleComparer.DataDependency;
import org.wikipedia.miner.db.WDatabase.CachePriority;
import org.wikipedia.miner.db.WDatabase.DatabaseType;
import org.wikipedia.miner.db.WEnvironment.StatisticName;
import org.wikipedia.miner.model.Article;
import org.wikipedia.miner.model.Page;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.model.Page.PageType;
import org.wikipedia.miner.util.MemoryMeasurer;
import org.wikipedia.miner.util.PageIterator;
import org.wikipedia.miner.util.ProgressTracker;
import org.wikipedia.miner.util.WikipediaConfiguration;

public class ComparisonExperiments {
	
	Wikipedia _wikipedia ;
	
	ComparisonDataSet _set  ; 
	
	DecimalFormat _df = new DecimalFormat("0.##") ;
	
	
	public ComparisonExperiments(Wikipedia wikipedia, ComparisonDataSet set) {
		
		_wikipedia = wikipedia ;
		_set = set ;
		
		
	}
	
	public static void main(String[] args) throws Exception {
		
		if (args.length != 1) {
			System.out.println("Please specify a wikipedia miner configuration file") ;
			System.exit(0) ;
		}
		
		WikipediaConfiguration conf = new WikipediaConfiguration(new File(args[0])) ;
		
		//conf.setArticleComparisonModel(null) ;
		
		//conf.setMinLinksIn(20) ;
		//conf.setMinLinkProbability(0.005F) ;
		//conf.setMinSenseProbability(0.01F) ;
		
		//conf.setArticleComparisonModel(null) ;
		//conf.setLabelDisambiguationModel(null) ;
		//conf.setLabelComparisonModel(null) ;
		
		conf.addDatabaseToCache(DatabaseType.pageLinksOut, CachePriority.space) ;
		conf.addDatabaseToCache(DatabaseType.pageLinksInNoSentences, CachePriority.space) ;
		conf.addDatabaseToCache(DatabaseType.pageLinkCounts, CachePriority.space) ;
		
		ArrayList<DataDependency> d = new ArrayList<DataDependency>() ;
		d.add(DataDependency.pageLinksOut) ;
		d.add(DataDependency.pageLinksIn) ;
		d.add(DataDependency.linkCounts) ;
		
		EnumSet<DataDependency> dependencies = EnumSet.copyOf(d) ;
		conf.setArticleComparisonDependancies(dependencies) ;
		
		long memStart = MemoryMeasurer.getBytesUsed() ;
		long timeStart = System.currentTimeMillis() ;
		
		Wikipedia wikipedia = new Wikipedia(conf, false) ;
		
		long timeEnd = System.currentTimeMillis() ;
		long memEnd = MemoryMeasurer.getBytesUsed() ;
		
		System.out.println("Memory Used: " + (memEnd - memStart) + "b") ;
		System.out.println("Time Spent: " + (timeEnd - timeStart) + "ms") ;
		
		ComparisonDataSet set = new ComparisonDataSet(new File("data/wikipediaSimilarity353.new.csv")) ;
		
		ComparisonExperiments ce = new ComparisonExperiments(wikipedia, set) ;
		
		
		ce.testArticleComparisonWithCrossfoldValidation(true) ;
		ce.saveArticleComparisonModelAndTrainingData(new File("models/articleComparison_allDependencies.model"), new File("data/articleComparison.arff")) ;
		
		//ce.testArticleComparisonSpeed(100) ;
		
		
		//Eyeball some comparisons directly
		ArrayList<Article> arts = new ArrayList<Article>() ;
		arts.add(wikipedia.getArticleByTitle("Kiwi")) ;
		arts.add(wikipedia.getArticleByTitle("Takahe")) ;
		arts.add(wikipedia.getArticleByTitle("Flightless bird")) ;
		arts.add(wikipedia.getArticleByTitle("Bird")) ;
		arts.add(wikipedia.getArticleByTitle("New Zealand")) ;
		arts.add(wikipedia.getArticleByTitle("Hadoop")) ;
		
		ce.printAllComparisons(arts) ;
		
		
		//testLabelComparison(wikipedia, set, dependencies) ;
		
		
		//wikipedia.close();
	}
	
	public void printAllComparisons(Collection<Article> articles) throws Exception {
		
		ArticleComparer cmp = new ArticleComparer(_wikipedia) ;
		
		for (Article artA:articles) {
			for (Article artB:articles) {
				double sr = cmp.getRelatedness(artA, artB) ;
				System.out.println(artA + " vs " + artB + ": " + _df.format(sr)) ;
			}
		}
		
	}
	
	
	
	public void testArticleComparisonWithCrossfoldValidation(boolean useMachineLearning) throws Exception {
		
		System.out.println("Testing article comparison") ;
		
		ComparisonDataSet[][] folds = _set.getFolds() ;
		double totalCorrelation = 0 ;

		for (int fold=0 ; fold<folds.length ; fold++) {
			
			ArticleComparer cmp = new ArticleComparer(_wikipedia) ;

			if (useMachineLearning) {
				cmp.train(folds[fold][0]) ;
				cmp.buildDefaultClassifier() ;
			}
			SpearmanCorrelation sc = cmp.test(folds[fold][1]) ;

			System.out.println("Fold " + fold) ;
			System.out.println(" - training instances: " + folds[fold][0].size()) ;
			System.out.println(" - testing instances: " + folds[fold][1].size()) ;

			System.out.println(" - Correllation:  " + sc.getR()) ;
			totalCorrelation += sc.getR() ;
		}

		double avgCorrelation = (totalCorrelation/10) ;
		System.out.println("Average Correllation: " + avgCorrelation) ;
	}
	
	public void testArticleComparisonSpeed(int size) throws Exception {
		
		System.out.println("Testing article comparison speed") ;
				
		ArrayList<Integer> allIds = new ArrayList<Integer>() ;
		
		
		PageIterator iter = _wikipedia.getPageIterator(PageType.article);
		long artCount = _wikipedia.getEnvironment().retrieveStatistic(StatisticName.articleCount) ;
		ProgressTracker pt = new ProgressTracker(artCount, "Gathering all articles", ComparisonExperiments.class) ;
		
		while (iter.hasNext()) {
			pt.update();
			Page p = iter.next();
		
			allIds.add(p.getId()) ;
		}
		//iter.close() ;
		
		System.out.println(" - gathering random articles") ;
		
		ArrayList<Article> selectedArticles = new ArrayList<Article>() ;
		Random r = new Random() ;
		
		while (selectedArticles.size() < size) {
			int index = r.nextInt(allIds.size()) ;
			
			Article art = new Article(_wikipedia.getEnvironment(), allIds.get(index)) ;
			if (art.exists()) {
				selectedArticles.add(art) ;
				//System.out.println(art) ;
			}
				
			allIds.remove(index) ;
		}
		
		allIds = null ;
		
		
		ArticleComparer cmp = new ArticleComparer(_wikipedia) ;
		
		
		long startTime = System.currentTimeMillis() ;
		int comparisons = 0 ;
		
		for (Article artA:selectedArticles) {
			for (Article artB:selectedArticles) {
				comparisons++ ;
				double sr = cmp.getRelatedness(artA, artB) ;
				
				System.out.println(artA + " vs " + artB + ": " + _df.format(sr)) ;
			}
		}
		
		long endTime = System.currentTimeMillis() ;
		
		System.out.println(comparisons + " comparisons in " + (endTime-startTime) + " ms") ;
		
		
		
	}
	
	public void saveArticleComparisonModelAndTrainingData(File modelFile, File arffFile) throws Exception {
		
		ArticleComparer cmp = new ArticleComparer(_wikipedia) ;
		
		cmp.train(_set) ;
		cmp.buildDefaultClassifier() ;
		
		if (modelFile != null)
			cmp.saveClassifier(modelFile) ;
		
		if (arffFile != null)
			cmp.saveTrainingData(arffFile) ;
	}
	
	
	
	public void testLabelComparisonWithCrossfoldValidation() throws Exception {
		
		ComparisonDataSet[][] folds = _set.getFolds() ;

		double totalCorrelation = 0 ;
		double totalAccuracy = 0 ;

		for (int fold=0 ; fold<folds.length ; fold++) {
			
			//train article comparer
			ArticleComparer artCmp = new ArticleComparer(_wikipedia) ;
			artCmp.train(folds[fold][0]) ;
			artCmp.buildDefaultClassifier() ;
			
			//train label comparer
			LabelComparer lblCmp = new LabelComparer(_wikipedia, artCmp) ;
			lblCmp.train(folds[fold][0], "353 fold " + fold) ;
			lblCmp.buildDefaultClassifiers() ;
			
			SpearmanCorrelation sc = lblCmp.testRelatednessPrediction(folds[fold][1]) ;
			double accuracy = lblCmp.testDisambiguationAccuracy(folds[fold][1]) ;

			System.out.println("Fold " + fold) ;
			System.out.println(" - training instances: " + folds[fold][0].size()) ;
			System.out.println(" - testing instances: " + folds[fold][1].size()) ;

			System.out.println(" - Relatedness correllation:  " + sc.getR()) ;
			totalCorrelation += sc.getR() ;
			
			System.out.println(" - Disambiguation accuracy:  " + accuracy) ;
			totalAccuracy += accuracy ;
		}

		System.out.println("Average relatedness correllation: " + (totalCorrelation/10) ) ;
		System.out.println("Average disambiguation accuracy: " + (totalAccuracy/10) ) ;
	}
	
	public void saveLabelComparisonModelAndTrainingData(File disambigModel, File comparisonModel, File disambigArff, File comparisonArff) throws Exception {
		
		ArticleComparer artCmp = new ArticleComparer(_wikipedia) ;
		//artCmp.loadClassifier(new File("../wm_hadoopAndBerkeleyDb/models/articleComparison_inLinks.model")) ;

		LabelComparer lblCmp = new LabelComparer(_wikipedia, artCmp) ;

		lblCmp.train(_set, "full 353") ;
		
		lblCmp.buildDefaultClassifiers() ;
		
		if (disambigModel != null)
			lblCmp.saveDisambiguationClassifier(disambigModel) ;
		
		if (comparisonModel != null)
			lblCmp.saveComparisonClassifier(comparisonModel) ;
		
		if (disambigArff != null)
			lblCmp.saveDisambiguationTrainingData(disambigArff) ;
		
		if (comparisonArff != null)
			lblCmp.saveComparisonTrainingData(comparisonArff) ;
	}
	
	public void testLabelComparisonSpeed(int size) throws Exception {
		
		System.out.println("Testing article comparison speed") ;
		
		System.out.println(" - gathering all articles") ;
		
		ArrayList<Integer> allIds = new ArrayList<Integer>() ;
		
		
		Iterator<Page> iter = _wikipedia.getPageIterator(PageType.article);
		ProgressTracker pt = new ProgressTracker(_wikipedia.getEnvironment().retrieveStatistic(StatisticName.articleCount), "Gathering all articles", ComparisonExperiments.class) ;
		while (iter.hasNext()) {
			pt.update();
			Page p = iter.next();
		
			allIds.add(p.getId()) ;
		}
		
		System.out.println(" - gathering random articles") ;
		
		ArrayList<Article> selectedArticles = new ArrayList<Article>() ;
		Random r = new Random() ;
		
		while (selectedArticles.size() < size) {
			int index = r.nextInt(allIds.size()) ;
			
			
			Article art = new Article(_wikipedia.getEnvironment(), allIds.get(index)) ;
			if (art.exists()) {
				selectedArticles.add(art) ;
				System.out.println(art) ;
			}
				
			allIds.remove(index) ;
		}
		
		allIds = null ;
		
		
		ArticleComparer cmp = new ArticleComparer(_wikipedia) ;
		
		
		long startTime = System.currentTimeMillis() ;
		int comparisons = 0 ;
		
		for (Article artA:selectedArticles) {
			for (Article artB:selectedArticles) {
				comparisons++ ;
				cmp.getRelatedness(artA, artB) ;
			}
		}
		
		long endTime = System.currentTimeMillis() ;
		
		System.out.println(comparisons + " comparisons in " + (endTime-startTime) + " ms") ;
			
		
	}
	

}
