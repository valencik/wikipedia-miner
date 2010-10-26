package org.wikipedia.miner.comparison;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import jsc.correlation.SpearmanCorrelation;
import jsc.datastructures.PairedData;

import org.wikipedia.miner.model.Article;
import org.wikipedia.miner.model.Label;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.util.ProgressTracker;
import org.wikipedia.miner.util.ml.BinaryPredictor;
import org.wikipedia.miner.util.ml.DoublePredictor;

import weka.classifiers.Classifier;
import weka.classifiers.functions.GaussianProcesses;
import weka.classifiers.meta.Bagging;
import weka.core.Instance;
import weka.core.Utils;

public class LabelComparer {

	private Wikipedia wikipedia ;
	private ArticleComparer articleComparer ;
	
	private BinaryPredictor senseSelector ;
	private DoublePredictor relatednessMeasurer ;
	
	public LabelComparer(Wikipedia wikipedia, ArticleComparer articleComparer) {
		this.wikipedia = wikipedia ;
		this.articleComparer = articleComparer ;
		
		String[] senseSelectionAttributes = {"predictedRelatedness", "avgPriorProbability", "maxPriorProbability", "minPriorProbability"} ;
		senseSelector = new BinaryPredictor("labelSenseSelector", senseSelectionAttributes, "isValidSensePair") ;

		String[] relatednessMeasuringAttributes = {"bestSenseRelatedness", "maxSenseRelatedness", "avgSenseRelatedness", "weightedAvgSenseRelatedness", "concatenationPriorLinkProbability", "concatenationOccurances"} ;
		relatednessMeasurer = new DoublePredictor("labelRelatednessMeasurer", relatednessMeasuringAttributes, "labelRelatedness") ;
	}
	
	public Double getRelatedness(Label labelA, Label labelB) throws Exception {

		if (!senseSelector.isReady())
			throw new Exception("You must train+build a new label sense selection classifier or load an existing one first") ;
		
		if (!relatednessMeasurer.isReady())
			throw new Exception("You must train+build a new label relatedness measuring classifier or load and existing one first") ;

		if (labelA.getText().equals(labelB.getText()))
			return 1.0 ; //TODO: make configurable?
			
		SensePair bestSensePair = null ;
		
		double totalSpRelatedness = 0 ;
		double maxSpRelatedness = 0 ;
		double totalWeightedSpRelatedness = 0 ;
		double totalWeight = 0 ;
		int spCount = 0 ;
		
		//compare every sense of labelA against every sense of label B
		for (Label.Sense senseA:labelA.getSenses()) {
			
			if (senseA.getPriorProbability() < wikipedia.getConfig().getMinSenseProbability())
				continue ;
			
			for (Label.Sense senseB:labelB.getSenses()) {
				
				if (senseB.getPriorProbability() < wikipedia.getConfig().getMinSenseProbability())
					continue ;
				
				SensePair sp = new SensePair(senseA, senseB) ;
				sp.setWeight(senseSelector.getPrediction(getFeaturesForSenseSelection(sp, null))) ;
					
				if (sp.getPredictedRelatedness() > maxSpRelatedness)
					maxSpRelatedness = sp.getPredictedRelatedness() ;
				
				totalSpRelatedness += sp.getPredictedRelatedness() ;
				totalWeightedSpRelatedness += (sp.getAvgPriorProbability() * sp.getPredictedRelatedness()) ;
				totalWeight += sp.getAvgPriorProbability() ;
				spCount++ ;
				
				if (bestSensePair == null || sp.getWeight() > bestSensePair.getWeight())
					bestSensePair = sp ;
			}
		}
		
		if (bestSensePair == null)
			return null ;
		
		double avgSpRelatedness = totalSpRelatedness/spCount ;
		double weightedAvgSpRelatedness = totalWeightedSpRelatedness/totalWeight ;
		Label concatenation = new Label(wikipedia.getEnvironment(), labelA.getText() + " " + labelB.getText()) ;
		
		return relatednessMeasurer.getPrediction(getFeaturesForRelatednessMeasuring(bestSensePair, maxSpRelatedness, avgSpRelatedness, weightedAvgSpRelatedness, concatenation, null)) ;
	}
	
	
	public void train(ComparisonDataSet dataset, String datasetName) throws Exception {

		senseSelector.initializeTrainingData(datasetName) ;
		relatednessMeasurer.initializeTrainingData(datasetName) ;

		ProgressTracker pn = new ProgressTracker(dataset.getItems().size(), "training", LabelComparer.class) ;
		for (ComparisonDataSet.Item item: dataset.getItems()) {

			train(item) ;
			pn.update() ;
		}
		
		senseSelector.finalizeTrainingData() ;
		relatednessMeasurer.finalizeTrainingData() ;
	}
	
	public void saveSenseSelectionTrainingData(File file) throws IOException, Exception {
		senseSelector.saveTrainingData(file) ;
	}
	
	public void saveRelatednessMeasuringTrainingData(File file) throws IOException, Exception {
		relatednessMeasurer.saveTrainingData(file) ;
	}
	
	
	
	public SpearmanCorrelation testRelatednessPrediction(ComparisonDataSet dataset) throws Exception {

		ArrayList<Double> manualMeasures = new ArrayList<Double>() ;
		ArrayList<Double> autoMeasures = new ArrayList<Double>() ;

		ProgressTracker pt = new ProgressTracker(dataset.getItems().size(), "testing", LabelComparer.class) ;
		for (ComparisonDataSet.Item item: dataset.getItems()) {

			Label labelA = new Label(wikipedia.getEnvironment(), item.getTermA()) ;
			Label labelB = new Label(wikipedia.getEnvironment(), item.getTermB()) ;

			Double manual = item.getRelatedness() ;
			Double auto  = 	getRelatedness(labelA, labelB) ;

			if (auto != null) {
				manualMeasures.add(manual) ;
				autoMeasures.add(auto) ;
			}
			
			pt.update()  ;
		}

		double[][] data = new double[manualMeasures.size()][2] ;
		for (int i=0 ; i<manualMeasures.size(); i++) {
			data[i][0] = manualMeasures.get(i) ;
			data[i][1] = autoMeasures.get(i) ;
		}

		SpearmanCorrelation sc = new SpearmanCorrelation(new PairedData(data)) ;
		return sc ;
	}
	
	
	public void buildDefaultClassifiers() throws Exception {
		Classifier ssClassifier = new Bagging() ;
		ssClassifier.setOptions(Utils.splitOptions("-P 10 -S 1 -I 10 -W weka.classifiers.trees.J48 -- -U -M 2")) ;
		senseSelector.buildClassifier(ssClassifier) ;
		
		Classifier rmClassifier = new GaussianProcesses() ;
		relatednessMeasurer.buildClassifier(rmClassifier) ;
	}
	
	private void train(ComparisonDataSet.Item item) throws Exception {
		
		Label labelA = new Label(wikipedia.getEnvironment(), item.getTermA()) ;
		Article artA = new Article(wikipedia.getEnvironment(), item.getIdA()) ;
		
		Label labelB = new Label(wikipedia.getEnvironment(), item.getTermB()) ;
		Article artB =  new Article(wikipedia.getEnvironment(), item.getIdB()) ;
		
		SensePair correctSensePair = null ;
		
		double totalSpRelatedness = 0 ;
		double maxSpRelatedness = 0 ;
		double totalWeightedSpRelatedness = 0 ;
		double totalWeight = 0 ;
		int spCount = 0 ;

		for (Label.Sense senseA:labelA.getSenses()) {
			
			if (senseA.getPriorProbability() < wikipedia.getConfig().getMinSenseProbability())
				continue ;
			
			for (Label.Sense senseB:labelB.getSenses()) {
				
				if (senseB.getPriorProbability() < wikipedia.getConfig().getMinSenseProbability())
					continue ;
				
				SensePair sp = new SensePair(senseA, senseB) ;
				
				if (senseA.getId() == artA.getId() && senseB.getId() == artB.getId()) {
					senseSelector.addTrainingInstance(getFeaturesForSenseSelection(sp, true)) ;
					correctSensePair = sp ;
				} else {
					senseSelector.addTrainingInstance(getFeaturesForSenseSelection(sp, false)) ;
				}
				
				if (sp.getPredictedRelatedness() > maxSpRelatedness)
					maxSpRelatedness = sp.getPredictedRelatedness() ;
				
				totalSpRelatedness += sp.getPredictedRelatedness() ;
				totalWeightedSpRelatedness += (sp.getAvgPriorProbability() * sp.getPredictedRelatedness()) ;
				totalWeight += sp.getAvgPriorProbability() ;
				spCount++ ;
			}
		}
		
		if (correctSensePair == null)
			return ;
		
		double avgSpRelatedness = totalSpRelatedness/spCount ;
		double weightedAvgSpRelatedness = totalWeightedSpRelatedness/totalWeight ;
		Label concatenation = new Label(wikipedia.getEnvironment(), labelA.getText() + " " + labelB.getText()) ;
		
		relatednessMeasurer.addTrainingInstance(getFeaturesForRelatednessMeasuring(correctSensePair, maxSpRelatedness, avgSpRelatedness, weightedAvgSpRelatedness, concatenation, item.getRelatedness())) ;
	}
	
	
	
	
	private double[] getFeaturesForSenseSelection(SensePair sp, Boolean isValid) {
		
		double[] features = new double[5] ;

		features[0] = sp.getPredictedRelatedness() ;
		features[1] = sp.getAvgPriorProbability() ;
		features[2] = sp.getMaxPriorProbability() ;
		features[3] = sp.getMinPriorProbability() ;
		if (isValid == null)
			features[4] = Instance.missingValue() ;
		else 
			features[4] = BinaryPredictor.booleanToDouble(isValid) ;
		
		return features ;
	}
	
	private double[] getFeaturesForRelatednessMeasuring(SensePair bestSensePair, double maxSpRelatedness, double avgSpRelatedness, double weightedAvgSpRelatedness, Label concatenation, Double relatedness){
		
		double[] features = new double[7] ;
	
		features[0] = bestSensePair.getPredictedRelatedness() ;
		features[1] = maxSpRelatedness ;
		features[2] = avgSpRelatedness ;
		features[3] = weightedAvgSpRelatedness ;
		features[4] = concatenation.getLinkProbability() ;
		features[5] = concatenation.getOccCount() ;
		if (relatedness == null)
			features[6] = Instance.missingValue() ;
		else 
			features[6] = relatedness ;
		
		return features ;
	}
	
	public class SensePair implements Comparable<SensePair> {
		
		private Label.Sense senseA ;
		private Label.Sense senseB ;
		private Double avgPriorProbability ;
		private double maxPriorProbability ;
		private double minPriorProbability ;
		
		private Double predictedRelatedness ;
		private Double weight = null ;
		
		public SensePair(Label.Sense senseA, Label.Sense senseB) throws Exception {
			
			this.senseA = senseA ;
			this.senseB = senseB ;
			
			maxPriorProbability = Math.max(senseA.getPriorProbability(), senseB.getPriorProbability()) ;
			minPriorProbability = Math.min(senseA.getPriorProbability(), senseB.getPriorProbability()) ;
			avgPriorProbability = (maxPriorProbability+minPriorProbability)/2 ;
			
			predictedRelatedness = articleComparer.getRelatedness(senseA, senseB) ;
		}
		
		

		public Double getWeight() {
			return weight;
		}



		public void setWeight(Double weight) {
			this.weight = weight;
		}

		public Double getAvgPriorProbability() {
			return avgPriorProbability;
		}



		public double getMaxPriorProbability() {
			return maxPriorProbability;
		}



		public double getMinPriorProbability() {
			return minPriorProbability;
		}



		public Label.Sense getSenseA() {
			return senseA;
		}



		public Label.Sense getSenseB() {
			return senseB;
		}



		public Double getPredictedRelatedness() {
			return predictedRelatedness;
		}

		@Override
		public int compareTo(SensePair sp) {
			
			int cmp = 0 ;
			
			if (weight != null && sp.weight != null) {
				cmp =  sp.weight.compareTo(weight) ;
				if (cmp != 0)
					return cmp ;
			}
			
			cmp = sp.avgPriorProbability.compareTo(avgPriorProbability) ;
			if (cmp != 0)
				return cmp ;
			
			cmp = senseA.compareTo(sp.senseA) ;
			if (cmp != 0)
				return cmp ;
			
			cmp = senseB.compareTo(sp.senseB) ;
			return cmp ;
		}
		
		
	}
}
