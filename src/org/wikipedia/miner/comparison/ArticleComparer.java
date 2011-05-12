package org.wikipedia.miner.comparison;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;

import jsc.correlation.SpearmanCorrelation;
import jsc.datastructures.PairedData;

import org.apache.log4j.Logger;
import org.wikipedia.miner.db.WDatabase.DatabaseType;
import org.wikipedia.miner.db.WEnvironment.StatisticName;
import org.wikipedia.miner.db.struct.DbLinkLocation;
import org.wikipedia.miner.db.struct.DbLinkLocationList;
import org.wikipedia.miner.model.Article;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.util.ProgressTracker;
import org.wikipedia.miner.util.WikipediaConfiguration;
import org.wikipedia.miner.util.ml.*;

import weka.classifiers.Classifier;
import weka.classifiers.functions.GaussianProcesses;
import weka.core.Instance;

public class ArticleComparer {

	/**
	 * Data used to generate article relatedness measures. 
	 * 
	 */
	public enum DataDependency {
		
		/**
		 * Use links made to articles to measure relatedness. You should cache {@link DatabaseType#pageLinksIn} if using this mode extensively.  
		 */
		pageLinksIn,
		
		/**
		 * Use links made from articles to measure relatedness. You should cache {@link DatabaseType#pageLinksOut} and {@link DatabaseType#pageLinkCounts} if using this mode extensively. 
		 */
		pageLinksOut, 
		
		/**
		 * Use link counts to measure relatedness. You should cache {@link DatabaseType#pageLinkCounts} if using this mode extensively. 
		 */
		linkCounts
	} ;
	
	private enum LinkDirection{In, Out} ;


	Wikipedia wikipedia ;
	EnumSet<DataDependency> dependancies ;

	int wikipediaArticleCount ;
	Double m ;
	
	enum Attributes {
		inLinkGoogleMeasure, 
		inLinkUnion, 
		inLinkIntersection, 
		inLinkVectorMeasure,
		outLinkGoogleMeasure, 
		outLinkUnion, 
		outLinkIntersection, 
		outLinkVectorMeasure,
	}
	
	Decider<Attributes,Double> relatednessMeasurer ;
	Dataset<Attributes,Double> trainingDataset ;

	//DoublePredictorOld relatednessMeasurer ;

	public ArticleComparer(Wikipedia wikipedia) throws Exception {
		
		WikipediaConfiguration conf = wikipedia.getConfig() ;
		
		if (conf.getArticleComparisonDependancies() == null) 
			throw new Exception("The given wikipedia configuration does not specify default article comparison data dependancies");
		
		init(wikipedia, conf.getArticleComparisonDependancies()) ;
	}
	
	public ArticleComparer(Wikipedia wikipedia, EnumSet<DataDependency> dependancies) throws Exception {
		init(wikipedia, dependancies) ;
	}
	
	@SuppressWarnings("unchecked")
	private void init(Wikipedia wikipedia, EnumSet<DataDependency> dependancies) throws Exception {
		
		if (!dependancies.contains(DataDependency.pageLinksIn) && !dependancies.contains(DataDependency.pageLinksOut))
			throw new Exception("Dependancies must include at least pageLinksIn or pageLinksOut") ;

		this.wikipedia = wikipedia ;
		this.dependancies = dependancies ;

		wikipediaArticleCount = new Long(wikipedia.getEnvironment().retrieveStatistic(StatisticName.articleCount)).intValue() ;
		m = Math.log(wikipediaArticleCount) ;
		
		relatednessMeasurer = (Decider<Attributes, Double>) new DeciderBuilder<Attributes>("articleComparer", Attributes.class) 
			.setDefaultAttributeTypeNumeric()
			.setClassAttributeTypeNumeric("relatedness")
			.build();

		/*
		ArrayList<String> attrNames = new ArrayList<String>();

		if (dependancies.contains(DataDependency.pageLinksIn)) {

			attrNames.add("inLinkGoogleMeasure") ;
			attrNames.add("inLinkGoogleSentenceMeasure") ;
			//attrNames.add("inLinkUnion") ;
			//attrNames.add("inLinkIntersection") ;

			if (dependancies.contains(DataDependency.linkCounts)) 
				attrNames.add("inLinkVectorMeasure") ;
		}

		if (dependancies.contains(DataDependency.pageLinksOut)) {

			attrNames.add("outLinkGoogleMeasure") ;
			attrNames.add("outLinkGoogleSentenceMeasure") ;
			//attrNames.add("outLinkUnion") ;
			//attrNames.add("outLinkIntersection") ;

			if (dependancies.contains(DataDependency.linkCounts)) 
				attrNames.add("outLinkVectorMeasure") ;
		}

		relatednessMeasurer = new DoublePredictorOld("articleRelatednessMeasurer", attrNames.toArray(new String[attrNames.size()]), "articleRelatedness") ;
		*/
		
		if (wikipedia.getConfig().getArticleComparisonModel() != null) 
			this.loadClassifier(wikipedia.getConfig().getArticleComparisonModel()) ;
	}

	public Double getRelatedness(Article artA, Article artB) throws Exception {

		if (!relatednessMeasurer.isReady())
			throw new Exception("You must train+build a new article relatedness measuring classifier or load an existing one first") ;

		if (artA.getId() == artB.getId()) 
			return 1.0 ; //TODO: Configurable?

		ArticleComparison cmp = getComparison(artA, artB) ;
		if (cmp == null)
			return 0.0 ;
		if (cmp.getInLinkGoogleSentenceMeasure() == 1)
			return 0.0 ;
		
		
		return 1-cmp.getInLinkGoogleMeasure() ;
		//return relatednessMeasurer.getDecision(getInstance(cmp, null)) ;
	}

	public void train(ComparisonDataSet dataset) throws Exception {

		trainingDataset = relatednessMeasurer.createNewDataset() ;
		
		ProgressTracker pn = new ProgressTracker(dataset.getItems().size(), "training", ArticleComparer.class) ;
		for (ComparisonDataSet.Item item: dataset.getItems()) {

			if (item.getIdA() < 0 || item.getIdB() < 0)
				continue ;
			
			Article artA = null;
			try{ 
				artA = new Article(wikipedia.getEnvironment(), item.getIdA()) ;
			} catch (Exception e) {
				Logger.getLogger(ArticleComparer.class).warn(item.getIdA() + " is not a valid article") ;
			}

			Article artB = null;
			try{ 
				artB = new Article(wikipedia.getEnvironment(), item.getIdB()) ;
			} catch (Exception e) {
				Logger.getLogger(ArticleComparer.class).warn(item.getIdB() + " is not a valid article") ;
			}

			if (artA != null && artB != null) 
				train(artA, artB, item.getRelatedness()) ;

			pn.update() ;
		}
	}


	/**
	 * Saves the training data generated by train() to the given file.
	 * The data is saved in WEKA's arff format. 
	 * 
	 * @param file the file to save the training data to
	 * @throws IOException if the file cannot be written to
	 */
	public void saveTrainingData(File file) throws IOException, Exception {
		trainingDataset.save(file) ;
	}

	/**
	 * Loads training data from the given file.
	 * The file must be a valid WEKA arff file. 
	 * 
	 * @param file the file to save the training data to
	 * @throws IOException if the file cannot be read.
	 * @throws Exception if the file does not contain valid training data.
	 */
	public void loadTrainingData(File file) throws IOException, Exception{
		trainingDataset.load(file) ;
	}

	/**
	 * Serializes the classifer and saves it to the given file.
	 * 
	 * @param file the file to save the classifier to
	 * @throws IOException if the file cannot be read
	 */
	public void saveClassifier(File file) throws IOException {
		relatednessMeasurer.save(file) ;
	}

	/**
	 * Loads the classifier from file
	 * 
	 * @param file 
	 * @throws Exception 
	 */
	public void loadClassifier(File file) throws Exception {
		relatednessMeasurer.load(file) ;
	}


	/**
	 * 
	 * 
	 * @param classifier
	 * @throws Exception
	 */
	public void buildClassifier(Classifier classifier) throws Exception {

		relatednessMeasurer.train(classifier, trainingDataset) ;
	}

	/**
	 * 
	 * 
	 * @param classifier
	 * @throws Exception
	 */
	public void buildDefaultClassifier() throws Exception {

		Classifier classifier = new GaussianProcesses() ;
		relatednessMeasurer.train(classifier, trainingDataset) ;
	}


	public SpearmanCorrelation test(ComparisonDataSet dataset) throws Exception {

		ArrayList<Double> manualMeasures = new ArrayList<Double>() ;
		ArrayList<Double> autoMeasures = new ArrayList<Double>() ;

		ProgressTracker pn = new ProgressTracker(dataset.getItems().size(), "testing", ArticleComparer.class) ;
		for (ComparisonDataSet.Item item: dataset.getItems()) {

			if (item.getIdA() < 0 || item.getIdB() < 0)
				continue ;
			
			Article artA = null;
			try{ 
				artA = new Article(wikipedia.getEnvironment(), item.getIdA()) ;
			} catch (Exception e) {
				Logger.getLogger(ArticleComparer.class).warn(item.getIdA() + " is not a valid article") ;
			}

			Article artB = null;
			try{ 
				artB = new Article(wikipedia.getEnvironment(), item.getIdB()) ;
			} catch (Exception e) {
				Logger.getLogger(ArticleComparer.class).warn(item.getIdB() + " is not a valid article") ;
			}

			if (artA != null && artB != null) {
				manualMeasures.add(item.getRelatedness()) ;
				autoMeasures.add(this.getRelatedness(artA, artB)) ;
			} 

			pn.update() ;
		}

		double[][] data = new double[manualMeasures.size()][2] ;
		for (int i=0 ; i<manualMeasures.size(); i++) {
			data[i][0] = manualMeasures.get(i) ;
			data[i][1] = autoMeasures.get(i) ;
		}

		SpearmanCorrelation sc = new SpearmanCorrelation(new PairedData(data)) ;
		return sc ;
	}

	private void train(Article artA, Article artB, double relatedness) throws Exception {

		ArticleComparison cmp = getComparison(artA, artB) ;

		if (cmp == null) return ;

		trainingDataset.add(getInstance(cmp, relatedness)) ;
	}

	private ArticleComparison getComparison(Article artA, Article artB) {

		ArticleComparison cmp = new ArticleComparison(artA, artB) ;

		if (dependancies.contains(DataDependency.pageLinksIn)) 
			cmp = setPageLinkFeatures(cmp, LinkDirection.In, dependancies.contains(DataDependency.linkCounts)) ;

		if (dependancies.contains(DataDependency.pageLinksOut)) 
			cmp = setPageLinkFeatures(cmp, LinkDirection.Out, dependancies.contains(DataDependency.linkCounts)) ;

		if (!cmp.inLinkFeaturesSet() && !cmp.outLinkFeaturesSet())
			return null ;

		return cmp ;
	}

	// names of all parameters make sense if we assume dir is out
	private ArticleComparison setPageLinkFeatures(ArticleComparison cmp, LinkDirection dir, boolean useLinkCounts) {

		//don't gather training or testing data when articles are the same: this screws up normalization
		if (cmp.getArticleA().getId() == cmp.getArticleB().getId())
			return cmp ;

		ArrayList<DbLinkLocation>linksA = getLinkLocations(cmp.getArticleA().getId(), dir) ;
		ArrayList<DbLinkLocation>linksB = getLinkLocations(cmp.getArticleB().getId(), dir) ;

		int[] tmp = identifyIntersectionStuff(cmp, dir, useLinkCounts, linksA, linksB) ;

		int intersection = tmp[0] ;
		int sentenceIntersection = tmp[1];
		int union = tmp[3] ;

		//calculate google distance inspired measure
		Double googleMeasure = null ;
		Double googleSentenceMeasure = null ;

		if (intersection == 0) {
			googleMeasure = 1.0 ;
			googleSentenceMeasure = 1.0 ;
		} else {
			double a = Math.log(linksA.size()) ;
			double b = Math.log(linksB.size()) ;
			double ab = Math.log(intersection) ;

			googleMeasure = (Math.max(a, b) -ab) / (m - Math.min(a, b)) ;
		
			//do rough normalization
			if (googleMeasure > 1)
				googleMeasure = 1.0 ;

			//calculate sentence based measure
			if (sentenceIntersection == 0)
				googleSentenceMeasure = 1.0 ;
			else {
				double abs = Math.log(sentenceIntersection) ;
				googleSentenceMeasure = (Math.max(a, b) -abs) / (m - Math.min(a, b)) ;
				
				if (googleSentenceMeasure > 1)
					googleSentenceMeasure = 1.0 ;
			}
			
		}

		//calculate vector (tfidf) inspired measure

		Double vectorMeasure = null ;
		/*
		if (useLinkCounts) {

			if (vectA.isEmpty() || vectB.isEmpty())
				vectorMeasure = Math.PI/2 ;
			else {

				double dotProduct = 0 ;
				double magnitudeA = 0 ;
				double magnitudeB = 0 ;

				//StringBuffer strA = new StringBuffer() ;
				//StringBuffer strB = new StringBuffer() ;


				for (int x=0;x<vectA.size();x++) {
					double valA = vectA.get(x) ;
					double valB = vectB.get(x) ;

					/*
				if (valA > 0)
					strA.append(df.format(valA) + "\t") ;
				else 
					strA.append("-.---\t") ;

				if (valB > 0)
					strB.append(df.format(valB)+ "\t") ;
				else
					strB.append("-.---\t") ;
					 */
		/*
					dotProduct = dotProduct + (valA * valB) ;
					magnitudeA = magnitudeA + (valA * valA) ;
					magnitudeB = magnitudeB + (valB * valB) ;
				}

				magnitudeA = Math.sqrt(magnitudeA) ;
				magnitudeB = Math.sqrt(magnitudeB) ;

				vectorMeasure = Math.acos(dotProduct / (magnitudeA * magnitudeB)) ;		
				if (vectorMeasure.isNaN())
					vectorMeasure = Math.PI/2 ;
			}

			//if (vectorMeasure.isNaN()) {
			//	System.out.println("A: (" + cmp.getArticleA() + ") " + strA) ;
			//	System.out.println("B: (" + cmp.getArticleB() + ") " + strB) ;
			//}
		}*/

		double intersectionProportion ;
		if (union == 0)
			intersectionProportion = 0 ;
		else
			intersectionProportion = (double)intersection/union ;

		//System.out.println("Intersection: " + intersection + "\tUnion: " + union) ;
		//System.out.println("Relatedness:" + df.format(googleMeasure)) ;
		//System.out.println();

		if (dir == LinkDirection.Out)
			cmp.setOutLinkFeatures(googleMeasure, googleSentenceMeasure, vectorMeasure, union, intersectionProportion) ;
		else
			cmp.setInLinkFeatures(googleMeasure, googleSentenceMeasure, vectorMeasure, union, intersectionProportion) ;

		return cmp ;
	}
	
	private int[] identifyIntersectionStuff(ArticleComparison cmp, LinkDirection dir, boolean useLinkCounts, ArrayList<DbLinkLocation>linksA, ArrayList<DbLinkLocation>linksB) {
		
		int intersection = 0 ;
		int sentenceIntersection = 0 ;
		int union = 0 ;

		int indexA = 0 ;
		int indexB = 0 ;

		//ArrayList<Double> vectA = new ArrayList<Double>() ;
		//ArrayList<Double> vectB = new ArrayList<Double>() ;

		//get denominators for link frequency
		Integer linksFromSourceA = 0 ;
		Integer linksFromSourceB = 0 ;
		if (useLinkCounts) {
			if (dir == LinkDirection.Out) {
				linksFromSourceA = cmp.getArticleA().getTotalLinksOutCount() ;
				linksFromSourceB = cmp.getArticleB().getTotalLinksOutCount() ;
			} else {
				linksFromSourceA = cmp.getArticleA().getTotalLinksInCount() ;
				linksFromSourceB = cmp.getArticleB().getTotalLinksInCount() ;
			}
		}

		while (indexA < linksA.size() || indexB < linksB.size()) {

			//identify which links to use (A, B, or both)

			boolean useA = false;
			boolean useB = false;
			boolean mutual = false ;

			DbLinkLocation linkA = null ;
			DbLinkLocation linkB = null ;
			
			if (indexA < linksA.size()) 
				linkA = linksA.get(indexA) ;

			if (indexB < linksB.size()) 
				linkB = linksB.get(indexB) ;

			if (linkA != null && linkB != null && linkA.getLinkId()==linkB.getLinkId()) {
				useA = true ;
				useB = true ;
				intersection ++ ;
				
				if (hasSentenceIntersection(linkA.getSentenceIndexes(), linkB.getSentenceIndexes()))
					sentenceIntersection++ ;
			} else {
				if (linkA != null && (linkB == null || linkA.getLinkId() < linkB.getLinkId())) {
					useA = true ;

					if (linkA.getLinkId() == cmp.getArticleB().getId()) {
						intersection++ ;
						mutual = true ;
					}

				} else {
					useB = true ;

					if (linkB.getLinkId() == cmp.getArticleA().getId()) {
						intersection++ ;
						mutual = true ;
					}
				}
			}
			union ++ ;

			if (useLinkCounts) {
				System.out.println("Using link counts") ;
				
				Article linkArt ;
				if (useA)
					linkArt = new Article(wikipedia.getEnvironment(), linkA.getLinkId()) ;
				else
					linkArt = new Article(wikipedia.getEnvironment(), linkB.getLinkId()) ;
				
				//calculate lfiaf values for each vector
				int linksToTarget ;
				if (dir == LinkDirection.Out)
					linksToTarget = linkArt.getTotalLinksInCount() ;
				else
					linksToTarget = linkArt.getTotalLinksOutCount() ;

				double valA = 0 ;
				double valB = 0 ;

				if (mutual) {
					valA = 1 ; 
					valB = 1 ;
				} else {
					if (useA) valA = getLfiaf(linkA.getSentenceIndexes().size(), linksFromSourceA, linksToTarget) ;
					if (useB) valB = getLfiaf(linkB.getSentenceIndexes().size(), linksFromSourceB, linksToTarget) ;
				}

				/*
				if (useA) {
					valA = getLfiaf(linkA.getSentenceIndexes().size(), linksFromSourceA, linksToTarget) ;
					if (mutual)
						valB = valA ;
				}

				if (useB) {
					valB = getLfiaf(linkB.getSentenceIndexes().size(), linksFromSourceB, linksToTarget) ;
					if (mutual)
						valB = valA ;
				}
				 */
				//vectA.add(valA) ;
				//vectB.add(valB) ;
			}

			if (useA)
				indexA++ ;
			if (useB)
				indexB++ ;
		}
		
		int result[] = {intersection,sentenceIntersection,union} ;
		return result ;
	}

	
	private boolean hasSentenceIntersection(ArrayList<Integer> sentenceIndexesA, ArrayList<Integer>sentenceIndexesB) {
		
		int indexA = 0 ;
		int indexB = 0 ;

		while (indexA < sentenceIndexesA.size() && indexB < sentenceIndexesB.size())  {

			int sentenceA = sentenceIndexesA.get(indexA) ;
			int sentenceB = sentenceIndexesB.get(indexB) ;

			if (sentenceA == sentenceB) {
				return true ;
			} else {
				if ((sentenceA < sentenceB && sentenceA > 0)|| sentenceB < 0) {
					indexA ++ ;
				} else {
					indexB ++ ;
				}
			}
		}
		return false ;
	}

	private ArrayList<DbLinkLocation> getLinkLocations(int artId, LinkDirection dir) {

		DbLinkLocationList lll ;

		if (dir == LinkDirection.In)
			lll = wikipedia.getEnvironment().getDbPageLinkIn().retrieve(artId) ;
		else
			lll = wikipedia.getEnvironment().getDbPageLinkOut().retrieve(artId) ;

		if (lll == null || lll.getLinkLocations() == null) 
			return new ArrayList<DbLinkLocation>() ;

		return lll.getLinkLocations() ;
	}


	private double getLfiaf(int linksFromSourceToTarget, int linksFromSource, int linksToTarget) {

		if (linksFromSourceToTarget == 0 || linksFromSource == 0) 
			return 0 ;

		double linkFreq = (double)linksFromSourceToTarget/linksToTarget ;

		double inverseArtFreq = Math.log(wikipediaArticleCount/linksFromSource) ;

		return linkFreq * inverseArtFreq ;
	}

	private double wrapMissingValue(Number val) {

		if (val == null)
			return Instance.missingValue() ;
		else
			return val.doubleValue() ;
	}

	private Instance getInstance(ArticleComparison cmp, Double relatedness) throws ClassMissingException, AttributeMissingException {

		InstanceBuilder<Attributes, Double> ib = relatednessMeasurer.getInstanceBuilder() ;
		
		if (dependancies.contains(DataDependency.pageLinksIn)) {
			
			ib.setAttribute(Attributes.inLinkGoogleMeasure, wrapMissingValue(cmp.getInLinkGoogleMeasure())) ;
			ib.setAttribute(Attributes.inLinkUnion, wrapMissingValue(cmp.getInLinkUnion())) ;
			ib.setAttribute(Attributes.inLinkIntersection, wrapMissingValue(cmp.getInLinkIntersectionProportion())) ;
			
			if (dependancies.contains(DataDependency.linkCounts)) 
				ib.setAttribute(Attributes.inLinkVectorMeasure, wrapMissingValue(cmp.getInLinkVectorMeasure())) ;
		}
		
		if (dependancies.contains(DataDependency.pageLinksOut)) {			
			ib.setAttribute(Attributes.outLinkGoogleMeasure, wrapMissingValue(cmp.getOutLinkGoogleMeasure())) ;
			ib.setAttribute(Attributes.outLinkUnion, wrapMissingValue(cmp.getOutLinkUnion())) ;
			ib.setAttribute(Attributes.outLinkIntersection, wrapMissingValue(cmp.getOutLinkIntersectionProportion())) ;

			if (dependancies.contains(DataDependency.linkCounts)) 
				ib.setAttribute(Attributes.outLinkVectorMeasure, wrapMissingValue(cmp.getOutLinkVectorMeasure())) ;
		}
		
		if (relatedness != null)
			ib.setClassAttribute(relatedness) ;

		return ib.build() ;
	}

}
