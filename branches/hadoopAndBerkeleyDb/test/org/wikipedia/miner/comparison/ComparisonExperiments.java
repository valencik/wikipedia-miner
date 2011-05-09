package org.wikipedia.miner.comparison;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumSet;

import jsc.correlation.SpearmanCorrelation;

import org.wikipedia.miner.comparison.ArticleComparer.DataDependency;
import org.wikipedia.miner.db.WDatabase.CachePriority;
import org.wikipedia.miner.db.WDatabase.DatabaseType;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.util.WikipediaConfiguration;

public class ComparisonExperiments {
	
	
	public static void main(String[] args) throws Exception {
		
		if (args.length != 1) {
			System.out.println("Please specify a wikipedia miner configuration file") ;
			System.exit(0) ;
		}
		
		WikipediaConfiguration conf = new WikipediaConfiguration(new File(args[0])) ;
		
		conf.setMinLinksIn(3) ;
		conf.setMinLinkProbability(0.005F) ;
		conf.setMinSenseProbability(0.01F) ;
		
		
		
		
		//conf.setArticleComparisonModel(null) ;
		//conf.setLabelDisambiguationModel(null) ;
		//conf.setLabelComparisonModel(null) ;
		
		//conf.addDatabaseToCache(DatabaseType.pageLinksOut, CachePriority.space) ;
		conf.addDatabaseToCache(DatabaseType.pageLinksIn, CachePriority.space) ;
		//conf.addDatabaseToCache(DatabaseType.pageLinkCounts, CachePriority.space) ;
		
		ArrayList<DataDependency> d = new ArrayList<DataDependency>() ;
		//d.add(DataDependency.pageLinksOut) ;
		d.add(DataDependency.pageLinksIn) ;
		//d.add(DataDependency.linkCounts) ;
		
		EnumSet<DataDependency> dependencies = EnumSet.copyOf(d) ;
		


		Wikipedia wikipedia = new Wikipedia(conf, false) ;

		ComparisonDataSet set = new ComparisonDataSet(new File("/Users/dmilne/Research/wikipedia/datasets/wikipediaSimilarity353.new.csv")) ;
		/*
		ArticleComparer artCmp = new ArticleComparer(wikipedia) ;
		LabelComparer lblCmp = new LabelComparer(wikipedia, artCmp) ;
		
		Label lblA = new Label(wikipedia.getEnvironment(), "Kiwi") ;
		Label lblB = new Label(wikipedia.getEnvironment(), "Takahe") ;
		
		System.out.println(lblCmp.getRelatedness(lblA, lblB)) ;
		*/
		//testArticleComparison(wikipedia, set, dependencies) ;
		testLabelComparison(wikipedia, set, dependencies) ;
		
		//wikipedia.close();
	}

	public static void testArticleComparison(Wikipedia wikipedia, ComparisonDataSet set, EnumSet<DataDependency> dependencies) throws Exception {

		DecimalFormat df = new DecimalFormat("0.##") ;

	
		

		
		ArticleComparer cmp = new ArticleComparer(wikipedia,dependencies) ;
		
		cmp.train(set) ;
		cmp.buildDefaultClassifier() ;
		cmp.saveClassifier(new File("models/articleComparison_inLinks.model")) ;
		
		
		
		//cmp.loadClassifier(new File("/Users/dmilne/Research/wikipedia/temp/artCmp.model")) ;
		
		
		
	
		ComparisonDataSet[][] folds = set.getFolds() ;
		double totalCorrelation = 0 ;

		for (int fold=0 ; fold<folds.length ; fold++) {

			cmp.train(folds[fold][0]) ;
			cmp.buildDefaultClassifier() ;
			SpearmanCorrelation sc = cmp.test(folds[fold][1]) ;

			System.out.println("Fold " + fold) ;
			System.out.println(" - training instances: " + folds[fold][0].size()) ;
			System.out.println(" - testing instances: " + folds[fold][1].size()) ;

			System.out.println(" - Correllation:  " + sc.getR()) ;
			totalCorrelation += sc.getR() ;
		}

		System.out.println("Average Correllation: " + (totalCorrelation/10) ) ;

		
		
		/*
		ArrayList<Double> manualMeasures = new ArrayList<Double>() ;
		ArrayList<Double> autoMeasures = new ArrayList<Double>() ;
		ProgressTracker pt = new ProgressTracker(set.size(), "Gathering relatedness measures", ComparisonTests.class) ;
		for (ComparisonDataSet.Item item:set.getItems()) {

			Article artA = null;
			Article artB = null;

			if (item.getIdA()> 0)
				artA = new Article(wikipedia.getEnvironment(), item.getIdA()) ;

			if (item.getIdB()> 0)
				artB = new Article(wikipedia.getEnvironment(), item.getIdB()) ;


			if (artA != null && artB != null) {

				Double manual = item.getRelatedness() ;
				Double auto  = 	cmp.getRelatedness(artA, artB) ;

				if (auto != null) {

					System.out.println(artA.getTitle() + " v.s. " + artB.getTitle() + " m:" + df.format(manual) + " a:" + df.format(auto)) ;
					manualMeasures.add(manual) ;
					autoMeasures.add(auto) ;
				}
			}
			pt.update() ;
		//	System.out.println("similaritySet.push({termA:\"" + item.getTermA() + "\", termB:\"" + item.getTermB() + "\", relatedness:" + item.getRelatedness() + "}) ;") ;
		}


		double[][] data = new double[manualMeasures.size()][2] ;
		for (int i=0 ; i<manualMeasures.size(); i++) {
			data[i][0] = manualMeasures.get(i) ;
			data[i][1] = autoMeasures.get(i) ;
		}

		SpearmanCorrelation sc = new SpearmanCorrelation(new PairedData(data)) ;

		System.out.println("Correllation: " + sc.getR()) ;
		 


		
		
		
		//System.out.println(cmp.getRelatedness(wikipedia.getMostLikelyArticle("Waikato", null), wikipedia.getMostLikelyArticle("Waikato University", null))) ;

		cmp.train(set, "full 353") ;
		cmp.saveTrainingData(new File("/Users/dmilne/Research/wikipedia/temp/artCmp353.arff")) ;
		cmp.saveClassifier(new File("/Users/dmilne/Research/wikipedia/temp/artCmp.model")) ;
		
		*/
	}

	public static void testLabelComparison(Wikipedia wikipedia, ComparisonDataSet set, EnumSet<DataDependency> dependencies) throws Exception {
		
		/*
		 
		ComparisonDataSet[][] folds = set.getFolds() ;


		double totalCorrelation = 0 ;
		double totalAccuracy = 0 ;

		for (int fold=0 ; fold<folds.length ; fold++) {
			
			//train article comparer
			ArticleComparer artCmp = new ArticleComparer(wikipedia, EnumSet.copyOf(dependencies)) ;
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
		
		*/
		
		ArticleComparer artCmp = new ArticleComparer(wikipedia, EnumSet.copyOf(dependencies)) ;
		artCmp.loadClassifier(new File("../wm_hadoopAndBerkeleyDb/models/articleComparison_inLinks.model")) ;

		LabelComparer lblCmp = new LabelComparer(wikipedia, artCmp) ;

		lblCmp.train(set, "full 353") ;
		
		lblCmp.buildDefaultClassifiers() ;
		
		lblCmp.saveDisambiguationClassifier(new File("models/labelDisambiguation.model")) ;
		lblCmp.saveComparisonClassifier(new File("models/labelComparison.model")) ;
		
		//lblCmp.saveSenseSelectionTrainingData(new File("/Users/dmilne/Research/wikipedia/temp/artCmp353_labelSenseSelection.arff")) ;
		//lblCmp.saveRelatednessMeasuringTrainingData(new File("/Users/dmilne/Research/wikipedia/temp/artCmp353_labelRelatednessMeasuring.arff")) ;

		
		/*

		ArrayList<Double> manualMeasures = new ArrayList<Double>() ;
		ArrayList<Double> autoMeasures = new ArrayList<Double>() ;
		ProgressTracker pt = new ProgressTracker(set.size(), "Gathering relatedness measures", ComparisonTests.class) ;
		for (ComparisonDataSet.Item item:set.getItems()) {

			Label labelA = new Label(wikipedia.getEnvironment(), item.getTermA()) ;
			Label labelB = new Label(wikipedia.getEnvironment(), item.getTermB()) ;

			Double manual = item.getRelatedness() ;
			Double auto  = 	lblCmp.getRelatedness(labelA, labelB) ;

			if (auto != null) {

				System.out.println(labelA.getText() + " v.s. " + labelB.getText() + " m:" + df.format(manual) + " a:" + df.format(auto)) ;
				manualMeasures.add(manual) ;
				autoMeasures.add(auto) ;
			}
			pt.update()  ;
		//	System.out.println("similaritySet.push({termA:\"" + item.getTermA() + "\", termB:\"" + item.getTermB() + "\", relatedness:" + item.getRelatedness() + "}) ;") ;
		}
		
		double[][] data = new double[manualMeasures.size()][2] ;
		for (int i=0 ; i<manualMeasures.size(); i++) {
			data[i][0] = manualMeasures.get(i) ;
			data[i][1] = autoMeasures.get(i) ;
		}

		SpearmanCorrelation sc = new SpearmanCorrelation(new PairedData(data)) ;

		System.out.println("Correllation: " + sc.getR()) ;
		*/
	}

}
