package org.wikipedia.miner.util.ml;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;

import org.apache.log4j.Logger;
import org.wikipedia.miner.comparison.ArticleComparer;

import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

public class DoublePredictor {

	private String name ;

	protected FastVector attributes ;
	protected Instances trainingData ;
	protected boolean trainingFinalized = false ;

	protected Instances header ;
	protected Classifier classifier ;


	protected DoublePredictor(String name) {
		this.name = name ;
	}


	public DoublePredictor(String name, String[] attributeNames, String classAttributeName) {

		this.name = name ;

		attributes = new FastVector() ;
		for (String attrName:attributeNames) 
			attributes.addElement(new Attribute(attrName)) ;

		attributes.addElement(new Attribute(classAttributeName)) ;

		header = new Instances(name + "_header", attributes, 0) ;
		header.setClassIndex(header.numAttributes() -1) ;	
	}

	public boolean isReady() {
		
		return (classifier != null) ;
		
	}
	
	public double getPrediction(double[] attributeValues) throws Exception {

		if (classifier == null) 
			throw new Exception("You must train+build a new classifier or load an existing one first") ;

		if (attributeValues.length != attributes.size())
			throw new Exception("You should provide " + attributes.size() + " attribute values, not " + attributeValues.length) ;

		if (!Instance.isMissingValue(attributeValues[attributeValues.length-1]))
			throw new Exception("You already know the value of the class attribute; nothing to predict") ;

		Instance instance = new Instance(1.0, attributeValues) ;
		instance.setDataset(header) ;

		return classifier.classifyInstance(instance) ;
	}
	

	public void initializeTrainingData(String datasetName) throws Exception {

		trainingData =  new Instances(name + "_" + datasetName, attributes, 0) ;
		trainingData.setClassIndex(trainingData.numAttributes()-1) ;
	}

	public void addTrainingInstance(double[] attributeValues) throws Exception {



		if (attributeValues.length != attributes.size())
			throw new Exception("You should provide " + attributes.size() + " attribute values, not " + attributeValues.length) ;

		if (attributeValues[attributeValues.length-1] == Instance.missingValue())
			throw new Exception("Attributes do not provide a class calue to learn from.") ;

		if (trainingData == null)
			throw new Exception("You must initialize the training data first") ;

		Instance instance = new Instance(1.0, attributeValues) ;
		trainingData.add(instance) ;

	}

	public void finalizeTrainingData() {
		trainingFinalized = true ;
	}

	/**
	 * Saves the training data gathered so far to the given file.
	 * The data is saved in WEKA's arff format. 
	 * 
	 * @param file the file to save the training data to
	 * @throws IOException if the file cannot be written to
	 * @throws Exception if there is no training data to save
	 */
	@SuppressWarnings("unchecked")
	public void saveTrainingData(File file) throws IOException, Exception {

		if (trainingData == null || trainingData.numInstances() == 0)
			throw new Exception("There is no training data to save") ;

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
	@SuppressWarnings("unchecked")
	public void loadTrainingData(File file) throws Exception{
		Logger.getLogger(ArticleComparer.class).info("loading training data") ;

		Instances tmpTrainingData = new Instances(new FileReader(file)) ;

		//check that all attributes match
		Enumeration<Attribute> e = tmpTrainingData.enumerateAttributes() ;
		int attrIndex = 0 ;
		while (e.hasMoreElements()) {
			Attribute fileAttr = e.nextElement() ;
			Attribute origAttr = (Attribute) attributes.elementAt(attrIndex) ;

			if (fileAttr.name().equals(origAttr.name()))
				throw new Exception("Expected " + origAttr.name() + ", found " + fileAttr.name()) ;

			attrIndex ++ ;
		}

		trainingData = tmpTrainingData ;
		trainingData.setClassIndex(trainingData.numAttributes()-1) ;

		finalizeTrainingData() ;
	}

	public void buildClassifier(Classifier classifier) throws Exception {

		if (trainingData == null || trainingData.numInstances() == 0) 
			throw new Exception("You must load or build training data before building classifier") ;

		if (trainingData == null || trainingData.numInstances() == 0) 
			throw new Exception("You must finalize training data before building classifier") ;

		this.classifier = classifier ;
		classifier.buildClassifier(trainingData) ;

	} 

	/**
	 * Loads the classifier from file
	 * 
	 * @param file 
	 * @throws Exception 
	 */
	public void loadClassifier(File file) throws Exception {
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
		classifier = (Classifier) ois.readObject();
		ois.close();
		
		if (classifier == null)
			throw new Exception(file.getPath() +  " does not contain a valid classifier for " + name + " predictor.") ;
	}


	/**
	 * Serializes the classifier and saves it to the given file.
	 * 
	 * @param file the file to save the classifier to
	 * @throws IOException if the file cannot be written to
	 */
	public void saveClassifier(File file) throws IOException {
		Logger.getLogger(ArticleComparer.class).info("saving classifier") ;

		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
		oos.writeObject(classifier);
		oos.flush();
		oos.close();
	}

}
