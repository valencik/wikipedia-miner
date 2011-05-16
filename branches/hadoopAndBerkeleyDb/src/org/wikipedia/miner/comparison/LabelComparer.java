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
import org.wikipedia.miner.util.ml.*;

import weka.classifiers.Classifier;
import weka.classifiers.functions.GaussianProcesses;
import weka.classifiers.meta.Bagging;
import weka.core.Instance;
import weka.core.Utils;

public class LabelComparer {

	private Wikipedia wikipedia ;
	private ArticleComparer articleComparer ;
	
	private enum SenseAttr {
		predictedRelatedness, avgPriorProbability, maxPriorProbability, minPriorProbability, avgGenerality, maxGenerality, minGenerality
	}
	
	private enum RelatednessAttr {
		bestSenseRelatedness, maxSenseRelatedness, avgSenseRelatedness, weightedAvgSenseRelatedness, concatenationPriorLinkProbability, concatenationOccurances
	}
	
	private Decider<SenseAttr,Boolean> senseSelector ;
	private Dataset<SenseAttr,Boolean> senseDataset ;
	
	private Decider<RelatednessAttr, Double> relatednessMeasurer ;
	private Dataset<RelatednessAttr,Double> relatednessDataset ;
	
	public LabelComparer(Wikipedia wikipedia, ArticleComparer articleComparer) throws Exception {
		this.wikipedia = wikipedia ;
		this.articleComparer = articleComparer ;
		
		senseSelector = (Decider<SenseAttr, Boolean>) new DeciderBuilder<SenseAttr>("labelSenseSelector", SenseAttr.class)
			.setDefaultAttributeTypeNumeric()
			.setClassAttributeTypeBoolean("isValid")
			.build();
		
		relatednessMeasurer = (Decider<RelatednessAttr, Double>) new DeciderBuilder<RelatednessAttr>("labelRelatednessMeasurer", RelatednessAttr.class)
			.setDefaultAttributeTypeNumeric()
			.setClassAttributeTypeNumeric("relatedness")
			.build();
		
		if (wikipedia.getConfig().getLabelDisambiguationModel() != null) {
			loadDisambiguationClassifier(wikipedia.getConfig().getLabelDisambiguationModel()) ;
		}
		
		if (wikipedia.getConfig().getLabelComparisonModel() != null)
			loadComparisonClassifier(wikipedia.getConfig().getLabelComparisonModel()) ;
	}
	
	public ComparisonDetails compare(Label labelA, Label labelB) throws Exception {

		if (!senseSelector.isReady())
			throw new Exception("You must train+build a new label sense selection classifier or load an existing one first") ;
		
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

		senseDataset = senseSelector.createNewDataset() ;
		relatednessDataset = relatednessMeasurer.createNewDataset() ;
		
		ProgressTracker pn = new ProgressTracker(dataset.getItems().size(), "training", LabelComparer.class) ;
		for (ComparisonDataSet.Item item: dataset.getItems()) {

			train(item) ;
			pn.update() ;
		}
		
		//TODO: filter to resolve skewness?
	}
	
	public void saveDisambiguationTrainingData(File file) throws IOException, Exception {
		senseDataset.save(file) ;
	}
	
	public void saveComparisonTrainingData(File file) throws IOException, Exception {
		relatednessDataset.save(file) ;
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
		senseSelector.load(file) ;
	}
	
	public void loadComparisonClassifier(File file) throws Exception {
		relatednessMeasurer.load(file) ;
	}
	
	public void saveDisambiguationClassifier(File file) throws Exception {
		senseSelector.save(file) ;
	}
	
	public void saveComparisonClassifier(File file) throws Exception {
		relatednessMeasurer.save(file) ;
	}
	

	
	public void buildDefaultClassifiers() throws Exception {
		Classifier ssClassifier = new Bagging() ;
		ssClassifier.setOptions(Utils.splitOptions("-P 10 -S 1 -I 10 -W weka.classifiers.trees.J48 -- -U -M 2")) ;
		senseSelector.train(ssClassifier, senseDataset) ;
		
		Classifier rmClassifier = new GaussianProcesses() ;
		relatednessMeasurer.train(rmClassifier, relatednessDataset) ;
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
				relatednessDataset.add(getInstance()) ;
			} else {
				//relatedness must be predicted
				labelRelatedness = relatednessMeasurer.getDecision(getInstance()) ;
			}
		}
		
		protected Instance getInstance() throws ClassMissingException, AttributeMissingException {
			
			InstanceBuilder<RelatednessAttr,Double> ib = relatednessMeasurer.getInstanceBuilder() ;
			
			if (plausableInterpretations.size() > 0)
				ib.setAttribute(RelatednessAttr.bestSenseRelatedness, plausableInterpretations.get(0).getSenseRelatedness()) ;
			else
				ib.setAttribute(RelatednessAttr.bestSenseRelatedness, Instance.missingValue()) ;
			
			ib.setAttribute(RelatednessAttr.maxSenseRelatedness, maxSpRelatedness) ;
			ib.setAttribute(RelatednessAttr.avgSenseRelatedness, avgSpRelatedness) ;
			ib.setAttribute(RelatednessAttr.weightedAvgSenseRelatedness, weightedAvgSpRelatedness) ;
			ib.setAttribute(RelatednessAttr.concatenationPriorLinkProbability, concatenation.getLinkProbability()) ;
			ib.setAttribute(RelatednessAttr.concatenationOccurances, Math.log(concatenation.getOccCount()+1)) ;
			
			if (labelRelatedness != null)
				ib.setClassAttribute(labelRelatedness) ;
			
			return ib.build() ;
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
				
				senseDataset.add(getInstance()) ;
				
			} else {
				disambiguationConfidence = senseSelector.getDecisionDistribution(getInstance()).get(true) ;
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
		
		protected Instance getInstance() throws ClassMissingException, AttributeMissingException {
			
			InstanceBuilder<SenseAttr,Boolean> ib = senseSelector.getInstanceBuilder() ;
			
			ib.setAttribute(SenseAttr.predictedRelatedness, senseRelatedness) ;
			ib.setAttribute(SenseAttr.avgPriorProbability, avgPriorProbability) ;
			ib.setAttribute(SenseAttr.maxPriorProbability, maxPriorProbability) ;
			ib.setAttribute(SenseAttr.minPriorProbability, minPriorProbability) ;
			
			if (disambiguationConfidence != null)
				ib.setClassAttribute(isValid) ;
			
			return ib.build() ;
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
