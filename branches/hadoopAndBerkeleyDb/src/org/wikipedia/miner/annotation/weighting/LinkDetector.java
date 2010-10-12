/*
 *    LinkDetector.java
 *    Copyright (C) 2007 David Milne, d.n.milne@gmail.com
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */


package org.wikipedia.miner.annotation.weighting;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.apache.log4j.Logger;
import org.wikipedia.miner.annotation.*;
import org.wikipedia.miner.annotation.ArticleCleaner.SnippetLength;
import org.wikipedia.miner.annotation.preprocessing.DocumentPreprocessor;
import org.wikipedia.miner.model.*;
import org.wikipedia.miner.util.*;

import weka.classifiers.Classifier;
import weka.core.*;

/**
 * A machine learned link detector. Given a set of Wikipedia topics mentioned within a document (as identified by the TopicDetector)
 * this will weight each topic by the probability that they are worthy of linking to. This is learned and tested by 
 * analyzing links within Wikipedia.
 * <p>
 * See the following paper for a more detailed explanation:
 * Milne, D. and Witten, I.H. (2008) Learning to link with Wikipedia. In Proceedings of the ACM Conference on Information and Knowledge Management (CIKM'2008), Napa Valley, California.
 * <p>
 * This will run hideously slowly unless anchors and inLinks have been cached.
 * 
 * @author David Milne
 */
public class LinkDetector extends TopicWeighter{
	
	private Wikipedia wikipedia ;
	private ArticleCleaner cleaner ;
		
	private FastVector attributes ;
	private Instances trainingData ;
	private Instances header ;
	private Classifier classifier ;
	
	/**
	 * Initialises a new LinkDetector. If the given wikipedia has been configured with a link detection model ({@link WikipediaConfiguration#getDetectionModel()}), then the
	 * link detector will be ready for use. Otherwise a model must be loaded, or a new one extracted from training data.
	 * 
	 * @param wikipedia an active wikipedia, ideally with label and pageLinkIn databases cached to memory
	 * @throws Exception if there is a problem with the wikipedia database, or the detection model (if specified). 
	 */
	public LinkDetector(Wikipedia wikipedia) throws Exception {
		this.wikipedia = wikipedia ;
		this.cleaner = new ArticleCleaner() ;
		
		attributes = new FastVector() ;

		attributes.addElement(new Attribute("occurances")) ;
		attributes.addElement(new Attribute("maxDisambigConfidence")) ;
		attributes.addElement(new Attribute("avgDisambigConfidence")) ;
		attributes.addElement(new Attribute("relatednessToOtherTopics")) ;
		attributes.addElement(new Attribute("maxLinkProbability")) ;
		attributes.addElement(new Attribute("avgLinkProbability")) ;
		attributes.addElement(new Attribute("generality")) ;
		attributes.addElement(new Attribute("firstOccurance")) ;
		attributes.addElement(new Attribute("lastOccurance")) ;
		attributes.addElement(new Attribute("spread")) ;
		//attributes.addElement(new Attribute("relatednessToContext")) ;

		FastVector bool = new FastVector();
		bool.addElement("TRUE") ;
		bool.addElement("FALSE") ;		
		attributes.addElement(new Attribute("isLinked", bool)) ;

		this.header = new Instances("wikification_headerOnly", attributes, 0) ;
		header.setClassIndex(header.numAttributes() -1) ;
		
		if (wikipedia.getConfig().getDetectionModel() != null) {
			loadClassifier(wikipedia.getConfig().getDetectionModel()) ;
		}
	}
	
	/**
	 * Weights and sorts the the given list of topics according to how likely they are to be Wikipedia links if the 
	 * document they were extracted from was a Wikipedia article. 
	 * 
	 * @param topics a collection of topics to be weighted
	 * @return an ArrayList of the same topics, where the weight of each topic is the probability that it is a link. 
	 * @throws Exception if the link detector has not yet been trained
	 */
	public ArrayList<Topic> getWeightedTopics(Collection<Topic> topics) throws Exception  {

		if (classifier == null)
			throw new Exception("You must train the link detector first.") ;

		ArrayList<Topic> weightedTopics = new ArrayList<Topic>() ;

		for (Topic topic: topics) {

			double[] values = new double[header.numAttributes()];

			values[0] = topic.getOccurances() ;
			values[1] = topic.getMaxDisambigConfidence() ;
			values[2] = topic.getAverageDisambigConfidence() ;
			values[3] = topic.getRelatednessToOtherTopics() ;
			values[4] = topic.getMaxLinkProbability() ;
			values[5] = topic.getAverageLinkProbability() ;

			if (topic.getGenerality() != null)
				values[6] = topic.getGenerality() ;
			else
				values[6] = Instance.missingValue();

			values[7] = topic.getFirstOccurance() ;
			values[8] = topic.getLastOccurance() ;
			values[9] = topic.getSpread() ;
			
			//values[10] = topic.getRelatednessToContext() ;

			values[10] = Instance.missingValue() ;

			Instance instance = new Instance(1.0, values) ;
			instance.setDataset(header) ;
			
			double prob = classifier.distributionForInstance(instance)[0] ;
			topic.setWeight((float)prob) ;
			weightedTopics.add(topic) ;
		}

		Collections.sort(weightedTopics) ;
		
		return weightedTopics ;
	}
	
	
	/**
	 * Trains the link detector on a set of Wikipedia articles. This only builds up the training data. 
	 * You will still need to build a classifier in order to use the trained link detector. 
	 * 
	 * @param articles the set of articles to use for training. You should make sure these are reasonably tidy, and roughly representative (in size, link distribution, etc) as the documents you intend to process automatically. 
	 * @param snippetLength the portion of each article that should be considered for training (see ArticleCleaner). 
	 * @param datasetName a name that will help explain the set of articles and resulting model later.
	 * @param td a topic detector, which is connected to a fully trained disambiguator.
	 * @param rc a cache in which relatedness measures will be saved so they aren't repeatedly calculated. Make this null if using extremely large training sets, so that caches will be reset from document to document, and won't grow too large.   
	 * @throws Exception 
	 */
	public void train(ArticleSet articles, SnippetLength snippetLength, String datasetName, TopicDetector td, RelatednessCache rc) throws Exception{

		trainingData = new Instances(datasetName, attributes, 0) ;
		trainingData.setClassIndex(trainingData.numAttributes() -1) ;

		ProgressTracker tracker = new ProgressTracker(articles.getArticleIds().size(), "training", LinkDetector.class) ;
		for (int id: articles.getArticleIds()) {
			
			Article art = null;
			try {
				art = new Article(wikipedia.getEnvironment(), id) ;
			} catch (Exception e) {
				System.err.println("Warning: " + id + " is not a valid article") ;
			} 
			
			if (art != null) 
				train(art, snippetLength, td, rc) ;
			
			tracker.update() ;
		}
	}

	/**
	 * Saves the training data generated by train() to the given file.
	 * The data is saved in WEKA's arff format. 
	 * 
	 * @param file the file to save the training data to
	 * @throws IOException if the file cannot be written to
	 */
	@SuppressWarnings("unchecked")
	public void saveTrainingData(File file) throws IOException {
		
		Logger.getLogger(LinkDetector.class).info("saving training data") ;
		
		BufferedWriter writer = new BufferedWriter(new FileWriter(file)) ;
		writer.write(header.toString()) ;
		
		Enumeration<Instance> e = trainingData.enumerateInstances() ;
		while (e.hasMoreElements()) {
			Instance i = e.nextElement() ;
			writer.write(i.toString() + "\n") ;
		}
		writer.close();
	}

	/**
	 * Loads training data from the given file.
	 * The file must be a valid WEKA arff file. 
	 * 
	 * @param file the file to save the training data to
	 * @throws IOException if the file cannot be read.
	 * @throws Exception if the file does not contain valid training data.
	 */
	public void loadTrainingData(File file) throws Exception{
		Logger.getLogger(LinkDetector.class).info("loading training data") ;
		
		trainingData = new Instances(new FileReader(file)) ;
		trainingData.setClassIndex(trainingData.numAttributes()-1) ;
	}

	/**
	 * Serializes the classifer and saves it to the given file.
	 * 
	 * @param file the file to save the classifier to
	 * @throws IOException if the file cannot be read
	 */
	public void saveClassifier(File file) throws IOException {
		Logger.getLogger(LinkDetector.class).info("saving classifier") ;
		
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
		oos.writeObject(classifier);
		oos.flush();
		oos.close();
	}

	/**
	 * Loads the classifier from file
	 * 
	 * @param file 
	 * @throws Exception 
	 */
	public void loadClassifier(File file) throws Exception {
		Logger.getLogger(LinkDetector.class).info("loading classifier") ;
		
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
		classifier = (Classifier) ois.readObject();
		ois.close();
	}

	/**
	 * 
	 * 
	 * @param classifier
	 * @throws Exception
	 */
	public void buildClassifier(Classifier classifier) throws Exception {
		System.out.println("LinkDetector: Building classifier...") ;
		
		weightTrainingInstances() ;
		
		if (trainingData == null) {
			throw new Exception("You must load training data or train on a set of articles before builing classifier.") ;
		} else {
			this.classifier = classifier ;
			classifier.buildClassifier(trainingData) ;
		}
	}
	
	
	/**
	 * Tests the link detector on a set of Wikipedia articles, to see how well it makes the same 
	 * decisions as the original article editors did. You need to train the link detector and build 
	 * a classifier before using this.
	 * 
	 * @param testSet the set of articles to use for testing. You should make sure these are reasonably tidy, and roughly representative (in size, link distribution, etc) as the documents you intend to process automatically.
	 * @param snippetLength the portion of each article that should be considered for testing (see ArticleCleaner). 
	 * @param td a topic detector (along with a fully trained and built disambiguator) 
	 * @param rc a cache in which relatedness measures will be saved so they aren't repeatedly calculated. Make this null if using extremely large testing sets, so that caches will be reset from document to document, and won't grow too large.
	 * @return Result a result (including recall, precision, f-measure) of how well the classifier did.   
	 * @throws Exception if there is a problem with the classifier
	 */
	public Result<Integer> test(ArticleSet testSet, SnippetLength snippetLength, TopicDetector td, RelatednessCache rc) throws Exception{

		if (classifier == null) 
			throw new Exception("You must build (or load) classifier first.") ;
		
		double worstRecall = 1 ;
		double worstPrecision = 1 ;
		
		int articlesTested = 0 ;
		int perfectRecall = 0 ;
		int perfectPrecision = 0 ;

		Result<Integer> r = new Result<Integer>() ;

		ProgressTracker tracker = new ProgressTracker(testSet.getArticleIds().size(), "Testing", LinkDetector.class) ;
		for (int id: testSet.getArticleIds()) {
			
			Article art = null ;
			
			try {
				art = new Article(wikipedia.getEnvironment(), id) ;
			} catch (Exception e) {
				System.err.println("Warning: " + id + " is not a valid article") ;
			} 
			
			
			if (art != null) {
				
				articlesTested ++ ;
				
				Result<Integer> ir = test(art, snippetLength, td, rc) ;
				
				if (ir.getRecall() ==1) perfectRecall++ ;
				if (ir.getPrecision() == 1) perfectPrecision++ ;
				
				worstRecall = Math.min(worstRecall, ir.getRecall()) ;
				worstPrecision = Math.min(worstPrecision, ir.getPrecision()) ;
				
				r.addIntermediateResult(ir) ;
				
			}
			
			tracker.update() ;
		}

		System.out.println("worstR:" + worstRecall + ", worstP:" + worstPrecision) ;
		System.out.println("tested:" + articlesTested + ", perfectR:" + perfectRecall + ", perfectP:" + perfectPrecision) ;

		return r ;
	}

	private void train(Article article, SnippetLength snippetLength, TopicDetector td, RelatednessCache rc) throws Exception{
		
		String text = cleaner.getCleanedContent(article, snippetLength) ;
		
		HashSet<Integer> groundTruth = getGroundTruth(article, snippetLength) ;

		Collection<Topic> topics = td.getTopics(text, rc) ;
		for (Topic topic: topics) {
			double[] values = new double[trainingData.numAttributes()];

			values[0] = topic.getOccurances() ;
			values[1] = topic.getMaxDisambigConfidence() ;
			values[2] = topic.getAverageDisambigConfidence() ;
			values[3] = topic.getRelatednessToOtherTopics() ;
			values[4] = topic.getMaxLinkProbability() ;
			values[5] = topic.getAverageLinkProbability() ;

			if (topic.getGenerality() >= 0)
				values[6] = topic.getGenerality() ;
			else
				values[6] = Instance.missingValue();

			values[7] = topic.getFirstOccurance() ;
			values[8] = topic.getLastOccurance() ;
			values[9] = topic.getSpread() ;
			
			//values[10] = topic.getRelatednessToContext() ;

			if (groundTruth.contains(topic.getId()))
				values[10] = 0 ;
			else
				values[10] = 1 ;

			trainingData.add(new Instance(1.0, values));
		}
	}

	private Result<Integer> test(Article article, SnippetLength snippetLength, TopicDetector td, RelatednessCache rc) throws Exception{
		System.out.println(" - testing " + article) ;
		
		if (rc == null)
			rc = new RelatednessCache(article.getEnvironment().getConfiguration().getReccommendedRelatednessModes()) ;
		
		String text = cleaner.getCleanedContent(article, snippetLength) ;
		
		Collection<Topic> topics = td.getTopics(text, rc) ;
		
		ArrayList<Topic> weightedTopics = this.getWeightedTopics(topics) ;
		
		HashSet<Integer> linkedTopicIds = new HashSet<Integer>() ;
		for (Topic topic: weightedTopics) {
			if (topic.getWeight() > 0.5) {
				//we think this should be linked to
				linkedTopicIds.add(topic.getId()) ;			
			}
		}

		Result<Integer> result = new Result<Integer>(linkedTopicIds, getGroundTruth(article, snippetLength)) ;
		System.out.println(" - " + result) ;
		return result ;
	}
	
	private HashSet<Integer> getGroundTruth(Article article, SnippetLength snippetLength) throws Exception {
		
		HashSet<Integer> links = new HashSet<Integer>() ;
		
		String content = cleaner.getMarkupLinksOnly(article, snippetLength) ;
				
		Pattern linkPattern = Pattern.compile("\\[\\[(.*?)\\]\\]") ; 
		Matcher linkMatcher = linkPattern.matcher(content) ;
		
		while (linkMatcher.find()) {			
			String linkText = content.substring(linkMatcher.start()+2, linkMatcher.end()-2) ;
						
			int pos = linkText.lastIndexOf('|') ;
			if (pos>0) {
				linkText = linkText.substring(0, pos) ;
			}
			
			linkText = Character.toUpperCase(linkText.charAt(0)) + linkText.substring(1) ;     // Get first char and capitalize
			Article link = wikipedia.getArticleByTitle(linkText) ;
			
			if (link != null) 
				links.add(link.getId()) ;
		}
		links.add(article.getId()) ;
		
		return links ;		
	}
	
	@SuppressWarnings("unchecked")
	private void weightTrainingInstances() {
		
		double positiveInstances = 0 ;
		double negativeInstances = 0 ; 
		
		Enumeration<Instance> e = trainingData.enumerateInstances() ;
		
		while (e.hasMoreElements()) {
			Instance i = e.nextElement() ;
			
			double isValidSense = i.value(attributes.size()-1) ;
			
			if (isValidSense == 0) 
				positiveInstances ++ ;
			else
				negativeInstances ++ ;
		}
		
		double p = (double) positiveInstances / (positiveInstances + negativeInstances) ;
				
		System.out.println("stats: positive=" +p + ", negative=" + (1-p)) ;
		
		e = trainingData.enumerateInstances() ;
		
		while (e.hasMoreElements()) {
			Instance i = e.nextElement() ;
			
			double isLinked = i.value(attributes.size()-1) ;
			
			if (isLinked == 0) 
				i.setWeight(0.5 * (1.0/p)) ;
			else
				i.setWeight(0.5 * (1.0/(1-p))) ;
		}
	}

	
	/**
	 * A demo of how to train and test the link detector. 
	 * 
	 * @param args	an array of 2 or 4 String arguments; the connection string of the Wikipedia 
	 * database server, the name of the Wikipedia database and (optionally, if anonymous access
	 * is not allowed) a username and password for the database.
	 * 
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		
				
		
		
		//use a text processor, so that terms and items in wikipedia will both be case-folded before being compared.
		//TextProcessor tp = new CaseFolder() ;
		
		//File stopwordFile = null ; //new File("/research/wikipediaminer/data/stopwords.txt") ;
		
		File confFile = new File("/home/dmilne/workspaces/Eclipse/wikipediaMiner_hadoopAndBerkeleyDB/configs/en.xml") ; 		
		WikipediaConfiguration conf = new WikipediaConfiguration(confFile) ;
		
		//set up an instance of Wikipedia
		Wikipedia wikipedia = new Wikipedia(conf, false) ;
		
		
		//gather article sets for training and testing
		//ArticleSet trainSet = new ArticleSet(new File("/home/dnk2/workspace/wikipediaminer/data/articleSets/trainingIds.csv")) ;
		//ArticleSet testSet = new ArticleSet(new File("/home/dnk2/workspace/wikipediaminer/data/articleSets/testIds_wikify.csv")) ;
		
		// use relatedness cache, so we won't repeat these calculations unnecessarily
		RelatednessCache rc = null ; //new RelatednessCache() ;
		
		// use a pre-trained disambiguator
		Disambiguator disambiguator = new Disambiguator(wikipedia) ;
		//disambiguator.loadClassifier(new File("/home/dmilne/workspaces/Eclipse/wikipedia-miner_server/models/disambig.model")) ;
		
		// connect disambiguator to a new topic detector
		TopicDetector topicDetector = new TopicDetector(wikipedia, disambiguator, true, false) ;
		
		// train a new link detector		
		LinkDetector linkDetector = new LinkDetector(wikipedia) ;
		
		
		/*
		linkDetector.train(trainSet, SnippetLength.full, "LinkDetection_Training", topicDetector, null) ;

		// build link detection classifier
		Classifier classifier = new Bagging() ;
		classifier.setOptions(Utils.splitOptions("-P 10 -S 1 -I 10 -W weka.classifiers.trees.J48 -- -U -M 2")) ;
		linkDetector.buildClassifier(classifier) ;
		
		linkDetector.saveClassifier(new File("/home/dnk2/workspace/data/models/linkDetect_06032009.model")) ;
		*/
		//linkDetector.loadClassifier(new File("/home/dmilne/workspaces/Eclipse/wikipedia-miner_server/models/linkDetect.model")) ;
		
		File testFile = new File("/home/dmilne/Desktop/Schuchert_ATexof_1929_260.txt") ;
		
		
		String testText = DocumentPreprocessor.getContent(testFile) ;
		ArrayList<Topic> weightedTopics = linkDetector.getWeightedTopics(topicDetector.getTopics(testText, null)) ;
		for (Topic t: weightedTopics) 
				System.out.println(t + "[" + t.getWeight() + "]") ;
			
		
		
		
		/*
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		
		while (true) {
			System.out.println("Please enter a line of text to wikify (or ENTER to quit)") ;
			
			String text = input.readLine() ;
			
			if (text == null || text.length()==0) 
				break ;
			
			ArrayList<Topic> weightedTopics = linkDetector.getWeightedTopics(topicDetector.getTopics(text, null)) ;
			for (Topic t: weightedTopics) {
				System.out.println(t + "[" + t.getWeight() + "]") ;
			}
		}
		*/
		// test		
		//Result<Integer> r = linkDetector.test(testSet, null, ArticleCleaner.ALL, topicDetector, null) ;
		//System.out.println(r) ; 
	}
	

}
