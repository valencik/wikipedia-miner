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

	//Wikipedia _wikipedia ;

	ComparisonDataSet _set  ; 

	DecimalFormat _df = new DecimalFormat("0.##") ;

	ArrayList<Label> _randomLabels ;
	ArticleSet _randomArticles ;

	public ComparisonExperiments(Wikipedia wikipedia, ComparisonDataSet set, File randomArticles, File randomLabels) throws Exception {

		//_wikipedia = wikipedia ;
		_set = set ;
		
		loadArticleSet(randomArticles, wikipedia) ;
		loadLabelSet(randomLabels, wikipedia) ;

	}
	
	public static WikipediaConfiguration configureConf(WikipediaConfiguration conf, ArrayList<DataDependency> d, boolean needLabels) {
		
		conf.clearDatabasesToCache() ;
		
		EnumSet<DataDependency> dependencies = EnumSet.copyOf(d) ;
		conf.setArticleComparisonDependancies(dependencies) ;
		
		if (dependencies.contains(DataDependency.pageLinksIn))
			conf.addDatabaseToCache(DatabaseType.pageLinksInNoSentences, CachePriority.space) ;
		
		if (dependencies.contains(DataDependency.pageLinksOut))
			conf.addDatabaseToCache(DatabaseType.pageLinksOutNoSentences, CachePriority.space) ;
		
		if (dependencies.contains(DataDependency.linkCounts))
			conf.addDatabaseToCache(DatabaseType.pageLinkCounts, CachePriority.space) ;
		
		if (needLabels) 
			conf.addDatabaseToCache(DatabaseType.label, CachePriority.space) ;
		
		return conf ;
	}

	public static void main(String[] args) throws Exception {

		//File confFile = new File("configs/en.xml") ;
		//ComparisonDataSet set = new ComparisonDataSet(new File("data/wikipediaSimilarity353_2011.csv"), 10) ;
		//File randomArticleFile = new File("data/randomArticles.txt") ;
		//File randomLabelFile = new File("data/randomLabels.txt") ;
		
		File confFile = new File("configs/de.xml") ;
		ComparisonDataSet set = new ComparisonDataSet(new File("data/gur350.csv"), 4) ;
		File randomArticleFile = new File("data/de_randomArticles.txt") ;
		File randomLabelFile = new File("data/de_randomLabels.txt") ;
		
	
		WikipediaConfiguration conf = new WikipediaConfiguration(confFile) ;
		conf.setArticleComparisonModel(null) ;
		
		Wikipedia wikipedia = new Wikipedia(conf, false) ;
		
		//saveRandomArticleSet(1000, randomArticleFile, wikipedia) ;
		//saveRandomLabelSet(1000, randomLabelFile, wikipedia) ;
		
		ComparisonExperiments ce = new ComparisonExperiments(wikipedia, set, randomArticleFile, randomLabelFile) ;
		wikipedia.close() ;
		
		ArrayList<ArticleComparisonDataPoint> artCmpResults = new ArrayList<ArticleComparisonDataPoint>() ;
		//ArrayList<LabelComparisonDataPoint> lblCmpResults = new ArrayList<LabelComparisonDataPoint>() ;
		
		
		
		
		ArrayList<DataDependency> d = new ArrayList<DataDependency>() ;
		
		boolean useML = true ;
		
		//pageLinksIn
		d.add(DataDependency.pageLinksIn) ;
		conf = configureConf(conf, d, false) ;
		artCmpResults.add(ce.doArticleComparisonExperiment(conf, useML)) ;
		//conf = configureConf(conf, d, true) ;
		//lblCmpResults.add(ce.doLabelComparisonExperiment(conf, true)) ;
		
		//pageLinksIn+linkCounts
		d.add(DataDependency.linkCounts) ;
		conf = configureConf(conf,d, false) ;
		artCmpResults.add(ce.doArticleComparisonExperiment(conf, useML)) ;
		
		//pageLinksOut
		d.clear();
		d.add(DataDependency.pageLinksOut) ;
		conf = configureConf(conf, d, false) ;
		artCmpResults.add(ce.doArticleComparisonExperiment(conf, useML)) ;
		
		//pageLinksOut+linkCounts
		d.add(DataDependency.linkCounts) ;
		conf = configureConf(conf,d, false) ;
		artCmpResults.add(ce.doArticleComparisonExperiment(conf, useML)) ;
		
		//pageLinksIn+pageLinksOut+linkCounts
		d.add(DataDependency.pageLinksIn) ;
		conf = configureConf(conf,d, false) ;
		artCmpResults.add(ce.doArticleComparisonExperiment(conf, useML)) ;
		
		
		System.out.println("\n\nFINAL RESULTS\n\n") ;
		for (ArticleComparisonDataPoint p:artCmpResults) {
			System.out.println(p) ;
		}
		

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


		//File randomLabelFile = new File("data/randomLabels.txt") ;
		//ce.saveRandomLabelSet(1000, randomLabelFile) ;

		//ce.testLabelComparisonWithCrossfoldValidation() ;
		//ce.saveLabelComparisonModelAndTrainingData(new File("models/labelDisamgiguation.model"), new File("models/labelComparison.model"), new File("data/labelDisambig.arff"), new File("data/labelComparison.arff")) ;

		//ArrayList<Label> randomLabels = ce.loadLabelSet(randomLabelFile) ;
		//ce.testLabelComparisonSpeed(randomLabels) ;


		//Label labelA = new Label(wikipedia.getEnvironment(), "Kiwi") ;
		//Label labelB = new Label(wikipedia.getEnvironment(), "Bird") ;

		//ce.printLabelComparisonDetails(labelA, labelB) ;

		//wikipedia.close();
	}


	public ArticleComparisonDataPoint doArticleComparisonExperiment(WikipediaConfiguration conf, boolean useML) throws Exception {

		System.out.println("Sleeping for garbage collection...") ;
		System.gc() ;	
		Thread.sleep(60000) ;
		
		
		ArticleComparisonDataPoint point = new ArticleComparisonDataPoint() ;
		point.dependencies = conf.getArticleComparisonDependancies() ;
		point.lang = conf.getLangCode() ;
		point.usingML = useML ;

		long memStart = MemoryMeasurer.getBytesUsed() ;
		long timeStart = System.currentTimeMillis() ;

		Wikipedia wikipedia = new Wikipedia(conf, false) ;

		long timeEnd = System.currentTimeMillis() ;
		long memEnd = MemoryMeasurer.getBytesUsed() ;

		point.cacheTime = timeEnd-timeStart ;
		point.cacheSpace = memEnd-memStart ;

		point = testArticleComparisonWithCrossfoldValidation(point, wikipedia) ;

		point = testArticleComparisonSpeed(point, wikipedia) ;

		wikipedia.close();
		
		return point ;

	}
	
	
	public LabelComparisonDataPoint doLabelComparisonExperiment(WikipediaConfiguration conf, boolean useML) throws Exception {

		LabelComparisonDataPoint point = new LabelComparisonDataPoint() ;
		point.dependencies = conf.getArticleComparisonDependancies() ;
		point.lang = conf.getLangCode() ;
		point.usingML = useML ;

		long memStart = MemoryMeasurer.getBytesUsed() ;
		long timeStart = System.currentTimeMillis() ;

		Wikipedia wikipedia = new Wikipedia(conf, false) ;

		long timeEnd = System.currentTimeMillis() ;
		long memEnd = MemoryMeasurer.getBytesUsed() ;

		point.cacheTime = timeEnd-timeStart ;
		point.cacheSpace = memEnd-memStart ;

		point = testLabelComparisonWithCrossfoldValidation(point, wikipedia) ;

		point = testLabelComparisonSpeed(point, wikipedia) ;

		wikipedia.close();
		
		return point ;

	}

	public void printAllComparisons(Collection<Article> articles, Wikipedia wikipedia) throws Exception {

		ArticleComparer cmp = new ArticleComparer(wikipedia) ;

		for (Article artA:articles) {
			for (Article artB:articles) {
				double sr = cmp.getRelatedness(artA, artB) ;
				System.out.println(artA + " vs " + artB + ": " + _df.format(sr)) ;
			}
		}

	}

	public ArticleComparisonDataPoint testArticleComparisonWithCrossfoldValidation(ArticleComparisonDataPoint point, Wikipedia wikipedia) throws Exception {

		System.out.println("Testing article comparison") ;

		ComparisonDataSet[][] folds = _set.getFolds() ;
		double totalCorrelation = 0 ;

		for (int fold=0 ; fold<folds.length ; fold++) {

			ArticleComparer cmp = new ArticleComparer(wikipedia) ;

			if (point.usingML) {
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
		
		point.correlation = avgCorrelation ;
		
		return point ;
		
	}






	public void saveArticleComparisonModelAndTrainingData(File modelFile, File arffFile, Wikipedia wikipedia) throws Exception {

		ArticleComparer cmp = new ArticleComparer(wikipedia) ;

		cmp.train(_set) ;
		cmp.buildDefaultClassifier() ;

		if (modelFile != null)
			cmp.saveClassifier(modelFile) ;

		if (arffFile != null)
			cmp.saveTrainingData(arffFile) ;
	}

	public static void saveRandomArticleSet(int size, File output, Wikipedia wikipedia) throws IOException {

		ArticleSet artSet = new ArticleSet() ;

		ArrayList<Integer> allIds = new ArrayList<Integer>() ;

		PageIterator iter = wikipedia.getPageIterator(PageType.article);
		long artCount = wikipedia.getEnvironment().retrieveStatistic(StatisticName.articleCount) ;
		ProgressTracker pt = new ProgressTracker(artCount, "Gathering all articles", ComparisonExperiments.class) ;

		while (iter.hasNext()) {
			pt.update();
			Page p = iter.next();
			if (p.exists())
				allIds.add(p.getId()) ;
		}
		iter.close() ;

		System.out.println(" - gathering random articles") ;

		int collectedArticles = 0 ;
		Random r = new Random() ;

		while (collectedArticles < size) {
			int index = r.nextInt(allIds.size()) ;

			int artId = allIds.get(index) ;
			artSet.add(new Article(wikipedia.getEnvironment(), artId)) ;

			collectedArticles++ ;	
			allIds.remove(index) ;
		}

		artSet.save(output) ;
	}

	public void loadArticleSet(File file, Wikipedia wikipedia) throws Exception {

		_randomArticles = new ArticleSet(file, wikipedia) ;

	}

	public ArticleComparisonDataPoint testArticleComparisonSpeed(ArticleComparisonDataPoint point, Wikipedia wikipedia) throws Exception {

		System.out.println("Testing article comparison speed") ;

		ArticleComparer cmp = new ArticleComparer(wikipedia) ;

		long startTime = System.currentTimeMillis() ;
		int comparisons = 0 ;

		for (Article artA:_randomArticles) {
			for (Article artB:_randomArticles) {
				comparisons++ ;
				double sr = cmp.getRelatedness(artA, artB) ;

				//System.out.println(artA + " vs " + artB + ": " + _df.format(sr)) ;
			}
		}

		long endTime = System.currentTimeMillis() ;
		
		point.millionComparisons = endTime - startTime ;

		System.out.println(comparisons + " comparisons in " + (endTime-startTime) + " ms") ;
		
		return point ;
	}

	public LabelComparisonDataPoint testLabelComparisonWithCrossfoldValidation(LabelComparisonDataPoint point, Wikipedia wikipedia) throws Exception {

		ComparisonDataSet[][] folds = _set.getFolds() ;

		double totalCorrelation = 0 ;
		double totalAccuracy = 0 ;

		for (int fold=0 ; fold<folds.length ; fold++) {

			//train article comparer
			ArticleComparer artCmp = new ArticleComparer(wikipedia) ;
			artCmp.train(folds[fold][0]) ;
			artCmp.buildDefaultClassifier() ;

			//train label comparer
			LabelComparer lblCmp = new LabelComparer(wikipedia, artCmp) ;
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

		point.correlation = (totalCorrelation/10) ;
		point.disambigAccuracy = (totalAccuracy/10) ;

		return point ;
	}

	public void saveLabelComparisonModelAndTrainingData(File disambigModel, File comparisonModel, File disambigArff, File comparisonArff, Wikipedia wikipedia) throws Exception {

		ArticleComparer artCmp = new ArticleComparer(wikipedia) ;
		//artCmp.loadClassifier(new File("../wm_hadoopAndBerkeleyDb/models/articleComparison_inLinks.model")) ;

		LabelComparer lblCmp = new LabelComparer(wikipedia, artCmp) ;

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

	public static void saveRandomLabelSet(int size, File output, Wikipedia wikipedia) throws IOException {

		Writer out = new BufferedWriter(new OutputStreamWriter( new FileOutputStream(output), "UTF-8")) ;

		ArrayList<String> allLabels = new ArrayList<String>() ;

		TextProcessor tp = wikipedia.getConfig().getDefaultTextProcessor() ;

		Iterator<Label> iter = wikipedia.getLabelIterator(tp);
		long labelCount = wikipedia.getEnvironment().getDbLabel(tp).getDatabaseSize() ;

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

	public void loadLabelSet(File file, Wikipedia wikipedia) throws Exception {

		_randomLabels = new ArrayList<Label>() ;
		BufferedReader in = new BufferedReader(new InputStreamReader( new FileInputStream(file), "UTF-8")) ;

		TextProcessor tp = wikipedia.getConfig().getDefaultTextProcessor() ;

		String line ;
		while ((line=in.readLine()) != null) {

			String labelText = line.trim();

			Label label = new Label(wikipedia.getEnvironment(), labelText, tp) ;
			_randomLabels.add(label) ;
		}
	}

	
	
	
	public LabelComparisonDataPoint testLabelComparisonSpeed(LabelComparisonDataPoint point, Wikipedia wikipedia) throws Exception {

		System.out.println("Testing label comparison speed") ;

		ArticleComparer artCmp = new ArticleComparer(wikipedia) ;
		LabelComparer lblCmp = new LabelComparer(wikipedia, artCmp) ;

		long startTime = System.currentTimeMillis() ;
		int comparisons = 0 ;

		for (Label lblA:_randomLabels) {
			for (Label lblB:_randomLabels) {
				comparisons++ ;
				lblCmp.getRelatedness(lblA, lblB) ;
			}
		}

		long endTime = System.currentTimeMillis() ;

		System.out.println(comparisons + " comparisons in " + (endTime-startTime) + " ms") ;

		point.millionComparisons = (endTime-startTime) ;

		return point ;
	}

	public void printLabelComparisonDetails(Label a, Label b, Wikipedia wikipedia) throws Exception {

		ArticleComparer artCmp = new ArticleComparer(wikipedia) ;
		LabelComparer lblCmp = new LabelComparer(wikipedia, artCmp) ;

		ComparisonDetails cd = lblCmp.compare(a, b) ;

		System.out.println(" - Relatedness: " + cd.getLabelRelatedness()) ;
		System.out.println(" - Interpretations: " ) ;

		for (SensePair sp:cd.getPlausableInterpretations()) {
			System.out.println("   - " + sp.getSenseA() + " vs. " + sp.getSenseB() + ":" + sp.getSenseRelatedness()) ;
		}
	}

	private class ArticleComparisonDataPoint { 

		public EnumSet<DataDependency> dependencies ;

		public String lang ;
		public boolean usingML;
		public double correlation ;
		public long millionComparisons ;

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
			sb.append(correlation) ;
			sb.append("\t") ;
			sb.append(millionComparisons) ;
			sb.append("\t") ;
			sb.append(cacheTime) ;
			sb.append("\t") ;
			sb.append(cacheSpace) ;


			return sb.toString() ;
		}
	}

	private class LabelComparisonDataPoint extends ArticleComparisonDataPoint {

		public double disambigAccuracy ;

		public String toString() {

			StringBuffer sb = new StringBuffer() ;
			sb.append(getDependencyString(dependencies)) ;
			sb.append("\t") ;
			sb.append(lang) ;
			sb.append("\t") ;
			sb.append(usingML) ;
			sb.append("\t") ;
			sb.append(correlation) ;
			sb.append("\t") ;
			sb.append(disambigAccuracy) ;
			sb.append("\t") ;
			sb.append(millionComparisons) ;
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

