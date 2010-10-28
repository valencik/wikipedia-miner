package org.wikipedia.miner.comparison;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import jsc.correlation.SpearmanCorrelation;
import jsc.datastructures.PairedData;

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
	
	public LabelComparer(Wikipedia wikipedia, ArticleComparer articleComparer) throws Exception {
		this.wikipedia = wikipedia ;
		this.articleComparer = articleComparer ;
		
		String[] senseSelectionAttributes = {"predictedRelatedness", "avgPriorProbability", "maxPriorProbability", "minPriorProbability"} ;
		senseSelector = new BinaryPredictor("labelSenseSelector", senseSelectionAttributes, "isValidSensePair") ;

		String[] relatednessMeasuringAttributes = {"bestSenseRelatedness", "maxSenseRelatedness", "avgSenseRelatedness", "weightedAvgSenseRelatedness", "concatenationPriorLinkProbability", "concatenationOccurances"} ;
		relatednessMeasurer = new DoublePredictor("labelRelatednessMeasurer", relatednessMeasuringAttributes, "labelRelatedness") ;
		
		if (wikipedia.getConfig().getLabelDisambiguationModel() != null) {
			loadDisambiguationClassifier(wikipedia.getConfig().getLabelDisambiguationModel()) ;
		}
		
		if (wikipedia.getConfig().getLabelComparisonModel() != null)
			loadComparisonClassifier(wikipedia.getConfig().getLabelComparisonModel()) ;
	}
	
	public ComparisonDetails compare(Label labelA, Label labelB) throws Exception {

		//if (!senseSelector.isReady())
		//	throw new Exception("You must train+build a new label sense selection classifier or load an existing one first") ;
		
		if (!relatednessMeasurer.isReady())
			throw new Exception("You must train+build a new label relatedness measuring classifier or load and existing one first") ;

		
		return new ComparisonDetails(labelA, labelB) ;
	}
	
	/**
	 * A convenience function to compare labels without returning details. 
	 * 
	 * @param labelA
	 * @param labelB
	 * @return the semantic relatedness between the two labels
	 * @throws Exception
	 */
	public Double getRelatedness(Label labelA, Label labelB) throws Exception {
		
		ComparisonDetails cmp = compare(labelA, labelB) ;
		return cmp.getLabelRelatedness() ;
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
	
	public void saveDisambiguationTrainingData(File file) throws IOException, Exception {
		senseSelector.saveTrainingData(file) ;
	}
	
	public void saveComparisonTrainingData(File file) throws IOException, Exception {
		relatednessMeasurer.saveTrainingData(file) ;
	}
	
	
	
	public SpearmanCorrelation testRelatednessPrediction(ComparisonDataSet dataset) throws Exception {

		ArrayList<Double> manualMeasures = new ArrayList<Double>() ;
		ArrayList<Double> autoMeasures = new ArrayList<Double>() ;

		ProgressTracker pt = new ProgressTracker(dataset.getItems().size(), "testing relatedness prediction", LabelComparer.class) ;
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
	
	public Double testDisambiguationAccuracy(ComparisonDataSet dataset) throws Exception {
		
		int totalInterpretations = 0 ;
		int correctInterpretations = 0 ;
		
		ProgressTracker pt = new ProgressTracker(dataset.getItems().size(), "testing disambiguation accuracy", LabelComparer.class) ;
		for (ComparisonDataSet.Item item: dataset.getItems()) {
		
			if (item.getIdA() < 0 || item.getIdB() < 0)
				continue ;
			
			totalInterpretations++ ;
			
			Label labelA = new Label(wikipedia.getEnvironment(), item.getTermA()) ;
			Label labelB = new Label(wikipedia.getEnvironment(), item.getTermB()) ;
			
			
			ComparisonDetails details = this.compare(labelA, labelB) ;
			
			SensePair sp = details.getBestInterpretation() ;
			
			if (sp != null) {
				if (sp.getSenseA().getId() == item.getIdA() && sp.getSenseB().getId() == item.getIdB())
					correctInterpretations ++ ;
			}
			
		}
		
		if (totalInterpretations > 0)
			return (double) correctInterpretations/totalInterpretations ;
		else
			return null ;
		
	}
	
	
	public void loadDisambiguationClassifier(File file) throws Exception {
		senseSelector.loadClassifier(file) ;
	}
	
	public void loadComparisonClassifier(File file) throws Exception {
		relatednessMeasurer.loadClassifier(file) ;
	}
	
	public void saveDisambiguationClassifier(File file) throws Exception {
		senseSelector.saveClassifier(file) ;
	}
	
	public void saveComparisonClassifier(File file) throws Exception {
		relatednessMeasurer.saveClassifier(file) ;
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
		Label labelB = new Label(wikipedia.getEnvironment(), item.getTermB()) ;
		
		new ComparisonDetails(labelA, labelB, item.getIdA(), item.getIdB(), item.getRelatedness()) ;
	}
	
			
	public class ComparisonDetails {
		
		private Label labelA ;
		private Label labelB ;
		private Label concatenation ;
		
		private Double labelRelatedness ;
		private ArrayList<SensePair> plausableInterpretations = new ArrayList<SensePair>() ;
		
		private double maxSpRelatedness ;
		private double avgSpRelatedness ;
		private double weightedAvgSpRelatedness ;
		
		public Label getLabelA() {
			return labelA;
		}

		public Label getLabelB() {
			return labelB;
		}

		public Double getLabelRelatedness() {
			return labelRelatedness;
		}

		public ArrayList<SensePair> getPlausableInterpretations() {
			return plausableInterpretations;
		}
		
		public SensePair getBestInterpretation() {
			
			if (plausableInterpretations.size() > 0)
				return plausableInterpretations.get(0) ;
			else
				return null ;
		}
		
		/**
		 * Constructs details for item where correct disambiguation and relatedness are already known (training)
		 * 
		 * @param labelA
		 * @param labelB
		 * @throws Exception 
		 * @throws Exception 
		 */
		private ComparisonDetails(Label labelA, Label labelB, int artA, int artB, double relatedness) throws Exception {
			
			init(labelA, labelB, artA, artB, relatedness) ;
			
			
			
		}
		
		private ComparisonDetails(Label labelA, Label labelB) throws Exception {
			
			init(labelA, labelB, null, null, null) ;
		}
		
		

		private void init(Label labelA, Label labelB, Integer senseIdA, Integer senseIdB, Double relatedness) throws Exception {
			
			double totalSpRelatedness = 0 ;
			double totalWeightedSpRelatedness = 0 ;
			double totalWeight = 0 ;
			int spCount = 0 ;
			
			this.labelA = labelA ;
			this.labelB = labelB ;
			concatenation = new Label(wikipedia.getEnvironment(), labelA.getText() + " " + labelB.getText()) ;
			
			for (Label.Sense senseA:labelA.getSenses()) {
				
				if (senseA.getPriorProbability() < wikipedia.getConfig().getMinSenseProbability())
					continue ;
				
				for (Label.Sense senseB:labelB.getSenses()) {
					
					if (senseB.getPriorProbability() < wikipedia.getConfig().getMinSenseProbability())
						continue ;
					
					SensePair sp ;
					
					if (senseIdA != null && senseIdB != null) {
						//this is a training instance, where correct senses are known
						if (senseA.getId() == senseIdA && senseB.getId() == senseIdB) {
							sp = new SensePair(senseA, senseB, true) ;
							plausableInterpretations.add(sp) ;
						} else {
							sp = new SensePair(senseA, senseB, false) ;
						}
					} else {
						//correct senses must be predicted
						sp = new SensePair(senseA, senseB) ;
						if (sp.getDisambiguationConfidence() > 0.5)
							plausableInterpretations.add(sp) ;
					}
					
					if (sp.getSenseRelatedness() > maxSpRelatedness)
						maxSpRelatedness = sp.getSenseRelatedness() ;
					
					totalSpRelatedness += sp.getSenseRelatedness() ;
					totalWeightedSpRelatedness += (sp.avgPriorProbability * sp.getSenseRelatedness()) ;
					totalWeight += sp.avgPriorProbability ;
					spCount++ ;
				}
			}
						
			Collections.sort(plausableInterpretations) ;	
			
			if (spCount > 0) {
				avgSpRelatedness = totalSpRelatedness/spCount ;
				weightedAvgSpRelatedness = totalWeightedSpRelatedness/totalWeight ;
			} else {
				avgSpRelatedness = 0 ;
				weightedAvgSpRelatedness = 0 ;
			}
			
			if (relatedness != null) {
				//this is a training instance, where relatedness is known
				labelRelatedness = relatedness ;
				relatednessMeasurer.addTrainingInstance(getFeatures()) ;
			} else {
				//relatedness must be predicted
				labelRelatedness = relatednessMeasurer.getPrediction(getFeatures()) ;
			}
		}
		
		protected double[] getFeatures() {
			
			double[] features = new double[7] ;
			
			if (plausableInterpretations.size() > 0)
				features[0] = plausableInterpretations.get(0).getSenseRelatedness() ;
			else
				features[0] = Instance.missingValue() ;
			
			features[1] = maxSpRelatedness ;
			features[2] = avgSpRelatedness ;
			features[3] = weightedAvgSpRelatedness ;
			features[4] = concatenation.getLinkProbability() ;
			features[5] = concatenation.getOccCount() ;
			
			if (labelRelatedness == null)
				features[6] = Instance.missingValue() ;
			else 
				features[6] = labelRelatedness ;
			
			return features ;
		}
	}
	
	public class SensePair implements Comparable<SensePair> {
		
		private Label.Sense senseA ;
		private Label.Sense senseB ;
		private Double avgPriorProbability ;
		private double maxPriorProbability ;
		private double minPriorProbability ;
		
		private Double senseRelatedness ;
		
		private Boolean isValid = null ;
		private Double disambiguationConfidence = null ;
		
		private SensePair(Label.Sense senseA, Label.Sense senseB, Boolean valid) throws Exception {
			init(senseA, senseB, valid) ;
		}
		
		private SensePair(Label.Sense senseA, Label.Sense senseB) throws Exception {
			init(senseA, senseB, null) ;
		}
		
		
		private void init(Label.Sense senseA, Label.Sense senseB, Boolean valid) throws Exception {
			
			this.senseA = senseA ;
			this.senseB = senseB ;
			
			maxPriorProbability = Math.max(senseA.getPriorProbability(), senseB.getPriorProbability()) ;
			minPriorProbability = Math.min(senseA.getPriorProbability(), senseB.getPriorProbability()) ;
			avgPriorProbability = (maxPriorProbability+minPriorProbability)/2 ;
			
			senseRelatedness = articleComparer.getRelatedness(senseA, senseB) ;
			
			if (valid != null) {
				isValid = valid ;
				if (isValid)
					disambiguationConfidence = 1.0 ;
				else
					disambiguationConfidence = 0.0 ;
				
				senseSelector.addTrainingInstance(getFeatures()) ;
				
			} else {
				disambiguationConfidence = senseSelector.getPrediction(getFeatures()) ;
				isValid = (disambiguationConfidence > 0.5) ;
			}
		}
		

		public Double getDisambiguationConfidence() {
			return disambiguationConfidence ;
		}


		public Label.Sense getSenseA() {
			return senseA;
		}


		public Label.Sense getSenseB() {
			return senseB;
		}


		public Double getSenseRelatedness() {
			return senseRelatedness;
		}
		
		protected double[] getFeatures() {
			
			double[] features = new double[5] ;

			features[0] = senseRelatedness ;
			features[1] = avgPriorProbability ;
			features[2] = maxPriorProbability ;
			features[3] = minPriorProbability ;
			
			if (disambiguationConfidence == null)
				features[4] = Instance.missingValue() ;
			else { 
				features[4] = BinaryPredictor.booleanToDouble(isValid) ;
			
			}
			return features ;
		}

		@Override
		public int compareTo(SensePair sp) {
			
			int cmp = 0 ;
			
			if (disambiguationConfidence != null && sp.disambiguationConfidence != null) {
				cmp =  sp.disambiguationConfidence.compareTo(disambiguationConfidence) ;
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