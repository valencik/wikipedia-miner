package org.wikipedia.miner.comparison;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Random;

import jsc.correlation.SpearmanCorrelation;

import org.wikipedia.miner.comparison.ArticleComparer.DataDependency;
import org.wikipedia.miner.comparison.LabelComparer.ComparisonDetails;
import org.wikipedia.miner.comparison.LabelComparer.SensePair;
import org.wikipedia.miner.db.WDatabase.CachePriority;
import org.wikipedia.miner.db.WDatabase.DatabaseType;
import org.wikipedia.miner.db.WEnvironment.StatisticName;
import org.wikipedia.miner.model.Article;
import org.wikipedia.miner.model.Label;
import org.wikipedia.miner.model.Page;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.model.Page.PageType;
import org.wikipedia.miner.util.ArticleSet;
import org.wikipedia.miner.util.MemoryMeasurer;
import org.wikipedia.miner.util.PageIterator;
import org.wikipedia.miner.util.ProgressTracker;
import org.wikipedia.miner.util.WikipediaConfiguration;
import org.wikipedia.miner.util.text.TextProcessor;

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
		
		conf.setArticleComparisonModel(null) ;
		
		//conf.setMinLinksIn(20) ;
		//conf.setMinLinkProbability(0.005F) ;
		//conf.setMinSenseProbability(0.01F) ;
		
		//conf.setArticleComparisonModel(null) ;
		//conf.setLabelDisambiguationModel(null) ;
		//conf.setLabelComparisonModel(null) ;
		
		conf.setArticleComparisonModel(new File("models/articleComparison_allDependencies.model")) ;
		
		conf.addDatabaseToCache(DatabaseType.pageLinksOut, CachePriority.space) ;
		conf.addDatabaseToCache(DatabaseType.pageLinksInNoSentences, CachePriority.space) ;
		conf.addDatabaseToCache(DatabaseType.pageLinkCounts, CachePriority.space) ;
		conf.addDatabaseToCache(DatabaseType.label, CachePriority.space) ;
		
		
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
	
		//ce.testArticleComparisonWithCrossfoldValidation(true) ;
		//ce.saveArticleComparisonModelAndTrainingData(new File("models/articleComparison_allDependencies.model"), new File("data/articleComparison.arff")) ;
		
		//ce.testArticleComparisonSpeed(100) ;
		
		
		//Eyeball some comparisons directly
		/*
		ArrayList<Article> arts = new ArrayList<Article>() ;
		arts.add(wikipedia.getArticleByTitle("Kiwi")) ;
		arts.add(wikipedia.getArticleByTitle("Takahe")) ;
		arts.add(wikipedia.getArticleByTitle("Flightless bird")) ;
		arts.add(wikipedia.getArticleByTitle("Bird")) ;
		arts.add(wikipedia.getArticleByTitle("New Zealand")) ;
		arts.add(wikipedia.getArticleByTitle("Hadoop")) ;
		
		
		
		ce.printAllComparisons(arts) ;
		*/
		
		
		File randomLabelFile = new File("data/randomLabels.txt") ;
		//ce.saveRandomLabelSet(1000, randomLabelFile) ;
		
		ce.testLabelComparisonWithCrossfoldValidation() ;
		//ce.saveLabelComparisonModelAndTrainingData(new File("models/labelDisamgiguation.model"), new File("models/labelComparison.model"), new File("data/labelDisambig.arff"), new File("data/labelComparison.arff")) ;
		
		//ArrayList<Label> randomLabels = ce.loadLabelSet(randomLabelFile) ;
		//ce.testLabelComparisonSpeed(randomLabels) ;
		
		
		Label labelA = new Label(wikipedia.getEnvironment(), "Kiwi") ;
		Label labelB = new Label(wikipedia.getEnvironment(), "Bird") ;
		
		ce.printLabelComparisonDetails(labelA, labelB) ;
		
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
	
	public void printVectorScoreMultipliers(Collection<Article> articles) throws Exception {
		
		double inTotal = 0 ;
		int inCount = 0 ;
		
		double outTotal = 0 ;
		int outCount = 0 ;
		
		ArticleComparer cmp = new ArticleComparer(_wikipedia) ;
		
		for (Article artA:articles) {
			for (Article artB:articles) {
				double sr = cmp.getRelatedness(artA, artB) ;
				
				
				if (sr != 0 && sr != 1) {
					
					System.out.println(artA + " vs " + artB + ": " + _df.format(sr)) ;
				
					ArticleComparison ac = cmp.getComparison(artA, artB) ;
					
					if (ac.inLinkFeaturesSet() && ac.getInLinkGoogleMeasure() != 0 && ac.getInLinkVectorMeasure() != 0) {
						inCount++ ;
						
						double ratio = ac.getInLinkGoogleMeasure() / ac.getInLinkVectorMeasure() ;
						System.out.println(" - in ratio: " + ratio) ;
						inTotal = inTotal + ratio ;
					}
					
					if (ac.outLinkFeaturesSet() && ac.getOutLinkGoogleMeasure() != 0 && ac.getOutLinkVectorMeasure() != 0) {
						outCount++ ;
						
						double ratio = ac.getOutLinkGoogleMeasure() / ac.getOutLinkVectorMeasure() ;
						System.out.println(" - out ratio: " + ratio) ;
						outTotal = outTotal + ratio ;
					}
					
				}
			}
		}
		
		double inRatio = inTotal/inCount ;
		double outRatio = outTotal/outCount ;
		
		
		System.out.println("Average ratios in:" + inRatio + " out:" + outRatio) ;
		
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
	
	
	
	

	
	public void saveArticleComparisonModelAndTrainingData(File modelFile, File arffFile) throws Exception {
		
		ArticleComparer cmp = new ArticleComparer(_wikipedia) ;
		
		cmp.train(_set) ;
		cmp.buildDefaultClassifier() ;
		
		if (modelFile != null)
			cmp.saveClassifier(modelFile) ;
		
		if (arffFile != null)
			cmp.saveTrainingData(arffFile) ;
	}
	
	public void saveRandomArticleSet(int size, File output) throws IOException {
		
		ArticleSet artSet = new ArticleSet() ;
		
		ArrayList<Integer> allIds = new ArrayList<Integer>() ;
		
		PageIterator iter = _wikipedia.getPageIterator(PageType.article);
		long artCount = _wikipedia.getEnvironment().retrieveStatistic(StatisticName.articleCount) ;
		ProgressTracker pt = new ProgressTracker(artCount, "Gathering all articles", ComparisonExperiments.class) ;
		
		while (iter.hasNext()) {
			pt.update();
			Page p = iter.next();
			if (p.exists())
				allIds.add(p.getId()) ;
		}
		//iter.close() ;
		
		System.out.println(" - gathering random articles") ;
		
		int collectedArticles = 0 ;
		Random r = new Random() ;
		
		while (collectedArticles < size) {
			int index = r.nextInt(allIds.size()) ;
			
			int artId = allIds.get(index) ;
			artSet.add(new Article(_wikipedia.getEnvironment(), artId)) ;
						
			collectedArticles++ ;	
			allIds.remove(index) ;
		}
		
		artSet.save(output) ;
	}
	
	public ArticleSet loadArticleSet(File file) throws Exception {
		
		ArticleSet artSet = new ArticleSet(file, _wikipedia) ;
		
		return artSet ;
	}
	
	public void testArticleComparisonSpeed(ArticleSet articles) throws Exception {
		
		System.out.println("Testing article comparison speed") ;

		ArticleComparer cmp = new ArticleComparer(_wikipedia) ;
		
		long startTime = System.currentTimeMillis() ;
		int comparisons = 0 ;
		
		for (Article artA:articles) {
			for (Article artB:articles) {
				comparisons++ ;
				double sr = cmp.getRelatedness(artA, artB) ;
				
				//System.out.println(artA + " vs " + artB + ": " + _df.format(sr)) ;
			}
		}
		
		long endTime = System.currentTimeMillis() ;
		
		System.out.println(comparisons + " comparisons in " + (endTime-startTime) + " ms") ;
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
	
	public void saveRandomLabelSet(int size, File output) throws IOException {
		
		Writer out = new BufferedWriter(new OutputStreamWriter( new FileOutputStream(output), "UTF8")) ;
		
		ArrayList<String> allLabels = new ArrayList<String>() ;
		
		TextProcessor tp = _wikipedia.getConfig().getDefaultTextProcessor() ;
	
		Iterator<Label> iter = _wikipedia.getLabelIterator(tp);
		long labelCount = _wikipedia.getEnvironment().getDbLabel(tp).getDatabaseSize() ;
		
		ProgressTracker pt = new ProgressTracker(labelCount, "Gathering all labels", ComparisonExperiments.class) ;
		while (iter.hasNext()) {
			pt.update();
			Label l = iter.next();
		
			if (l.exists())
				allLabels.add(l.getText()) ;
		}
		
		System.out.println(" - gathering random labels") ;

		int labelsCollected = 0 ;
		Random r = new Random() ;
		
		while (labelsCollected < size) {
			int index = r.nextInt(allLabels.size()) ;
			
			String l = allLabels.get(index) ;
			out.write(l + "\n") ;
			
			labelsCollected++ ;
			
			
			allLabels.remove(index) ;
		}
		
		out.close();
	}
	
	public ArrayList<Label> loadLabelSet(File file) throws Exception {
		
		ArrayList<Label> labels = new ArrayList<Label>() ;
		BufferedReader in = new BufferedReader(new InputStreamReader( new FileInputStream(file), "UTF8")) ;
		
		TextProcessor tp = _wikipedia.getConfig().getDefaultTextProcessor() ;
		
		String line ;
		while ((line=in.readLine()) != null) {
			
			String labelText = line.trim();
			
			Label label = new Label(_wikipedia.getEnvironment(), labelText, tp) ;
			labels.add(label) ;
			
		}
		
		return labels ;
	}
	
	public void testLabelComparisonSpeed(ArrayList<Label> labels) throws Exception {
		
		System.out.println("Testing label comparison speed") ;
		
		ArticleComparer artCmp = new ArticleComparer(_wikipedia) ;
		LabelComparer lblCmp = new LabelComparer(_wikipedia, artCmp) ;
		
		long startTime = System.currentTimeMillis() ;
		int comparisons = 0 ;
		
		for (Label lblA:labels) {
			for (Label lblB:labels) {
				comparisons++ ;
				lblCmp.getRelatedness(lblA, lblB) ;
			}
		}
		
		long endTime = System.currentTimeMillis() ;
		
		System.out.println(comparisons + " comparisons in " + (endTime-startTime) + " ms") ;
			
		
	}
	
	public void printLabelComparisonDetails(Label a, Label b) throws Exception {
		
		ArticleComparer artCmp = new ArticleComparer(_wikipedia) ;
		LabelComparer lblCmp = new LabelComparer(_wikipedia, artCmp) ;
		
		ComparisonDetails cd = lblCmp.compare(a, b) ;
		
		System.out.println(" - Relatedness: " + cd.getLabelRelatedness()) ;
		System.out.println(" - Interpretations: " ) ;
		
		for (SensePair sp:cd.getPlausableInterpretations()) {
			System.out.println("   - " + sp.getSenseA() + " vs. " + sp.getSenseB() + ":" + sp.getSenseRelatedness()) ;
		}
	}
	

}
