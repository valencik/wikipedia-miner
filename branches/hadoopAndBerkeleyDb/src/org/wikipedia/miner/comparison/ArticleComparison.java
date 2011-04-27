package org.wikipedia.miner.comparison;

import org.wikipedia.miner.model.Article;

public class ArticleComparison {

	private Article articleA ;
	private Article articleB ;
	
	private boolean inLinkFeaturesSet= false ;
	private Double inLinkGoogleMeasure ;
	private Double inLinkGoogleSentenceMeasure ;
	private Double inLinkVectorMeasure ;
	private Integer inLinkUnion ;
	private Double inLinkIntersectionProportion ;
	
	
	private boolean outLinkFeaturesSet = false;
	private Double outLinkGoogleMeasure ;
	private Double outLinkGoogleSentenceMeasure ;
	private Double outLinkVectorMeasure ;
	private Integer outLinkUnion ;
	private Double outLinkIntersectionProportion ;
	
	public ArticleComparison(Article artA, Article artB) {
		articleA = artA ;
		articleB = artB ;
	}
	
	
	
	public Article getArticleA() {
		return articleA;
	}



	public Article getArticleB() {
		return articleB;
	}


	public boolean inLinkFeaturesSet() {
		return inLinkFeaturesSet ;
	}
	

	public Double getInLinkGoogleMeasure() {
		return inLinkGoogleMeasure;
	}
	
	public Double getInLinkGoogleSentenceMeasure() {
		return inLinkGoogleSentenceMeasure;
	}
	
	public Double getInLinkVectorMeasure() {
		return inLinkVectorMeasure ;
	}


	public Integer getInLinkUnion() {
		return inLinkUnion;
	}



	public Double getInLinkIntersectionProportion() {
		return inLinkIntersectionProportion;
	}


	public boolean outLinkFeaturesSet() {
		return outLinkFeaturesSet ;
	}

	public Double getOutLinkGoogleMeasure() {
		return outLinkGoogleMeasure;
	}
	
	public Double getOutLinkGoogleSentenceMeasure() {
		return outLinkGoogleSentenceMeasure;
	}
	
	public Double getOutLinkVectorMeasure() {
		return outLinkVectorMeasure ;
	}



	public Integer getOutLinkUnion() {
		return outLinkUnion;
	}



	public Double getOutLinkIntersectionProportion() {
		return outLinkIntersectionProportion;
	}



	public void setInLinkFeatures(Double googleMeasure, Double googleSentenceMeasure, Double vectorMeasure, Integer union, Double intersectionProportion) {
		
		inLinkFeaturesSet = true ;
		inLinkGoogleMeasure = googleMeasure ;
		inLinkGoogleSentenceMeasure = googleSentenceMeasure ;
		inLinkVectorMeasure = vectorMeasure ;
		inLinkUnion = union ;
		inLinkIntersectionProportion = intersectionProportion ;
	}
	
	public void setOutLinkFeatures(Double googleMeasure, Double googleSentenceMeasure, Double vectorMeasure, Integer union, Double intersectionProportion) {
		
		outLinkFeaturesSet = true ;
		outLinkGoogleMeasure = googleMeasure ;
		outLinkGoogleSentenceMeasure = googleSentenceMeasure ;
		outLinkVectorMeasure = vectorMeasure ;
		outLinkUnion = union ;
		outLinkIntersectionProportion = intersectionProportion ;
	}
	
}
