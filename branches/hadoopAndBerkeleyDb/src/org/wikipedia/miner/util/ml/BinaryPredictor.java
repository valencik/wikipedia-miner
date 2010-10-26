package org.wikipedia.miner.util.ml;

import java.util.ArrayList;
import java.util.Enumeration;


import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

public class BinaryPredictor extends DoublePredictor {

	
	public BinaryPredictor(String name, String[] attributeNames, String classAttributeName) {
		
		super(name) ;
		
		attributes = new FastVector() ;
		for (String attrName:attributeNames) 
			attributes.addElement(new Attribute(attrName)) ;
		
		FastVector bool = new FastVector();
		bool.addElement("TRUE") ;
		bool.addElement("FALSE") ;		
		attributes.addElement(new Attribute(classAttributeName, bool)) ;
		
		header = new Instances(name + "_header", attributes, 0) ;
		header.setClassIndex(header.numAttributes() -1) ;	
	}
	
	@Override
	public double getPrediction(double[] attributeValues) throws Exception {

		if (classifier == null) 
			throw new Exception("You must train+build a new classifier or load an existing one first") ;

		if (attributeValues.length != attributes.size())
			throw new Exception("You should provide " + attributes.size() + " attribute values, not " + attributeValues.length) ;

		if (!Instance.isMissingValue(attributeValues[attributeValues.length-1]))
			throw new Exception("You already know the value of the class attribute; nothing to predict") ;

		Instance instance = new Instance(1.0, attributeValues) ;
		instance.setDataset(header) ;
		
		//instead of just returning true or false, return the classifier's confidence that it is true
		return classifier.distributionForInstance(instance)[0] ;
	}
	
	
	
	@SuppressWarnings("unchecked")
	@Override
	public void finalizeTrainingData() {
		
		//re-weight training data to account for skewed data
		
		double positiveInstances = 0 ;
		double negativeInstances = 0 ; 
		
		Enumeration<Instance> e = trainingData.enumerateInstances() ;
		
		while (e.hasMoreElements()) {
			Instance i = e.nextElement() ;
			
			boolean isPositive = (i.value(attributes.size()-1) == 0) ;
			
			if (isPositive) 
				positiveInstances ++ ;
			else
				negativeInstances ++ ;
		}
		
		double p = (double) positiveInstances / (positiveInstances + negativeInstances) ;
		
		e = trainingData.enumerateInstances() ;
		
		while (e.hasMoreElements()) {
			Instance i = e.nextElement() ;
			
			double isLinked = i.value(attributes.size()-1) ;
			
			if (isLinked == 0) 
				i.setWeight(0.5 * (1.0/p)) ;
			else
				i.setWeight(0.5 * (1.0/(1-p))) ;
		}
		
		super.finalizeTrainingData() ;
	}
	
	public static double booleanToDouble(boolean val) {
		if (val) 
			return 0 ;
		else
			return 1 ;
	}
}
