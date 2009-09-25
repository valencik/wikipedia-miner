package org.wikipedia.miner.model;

import java.util.Arrays;

import org.wikipedia.miner.db.DbStructureNode;

public class Paragraph extends StructureNode {

	private int[] sentenceBreaks ;
	
	public Paragraph(StructureNode parent, DbStructureNode dbNode) {
		
		super(parent, StructureNode.PARAGRAPH, dbNode.getStartPosition()) ;
		
		this.sentenceBreaks = dbNode.getSentenceBreaks() ;
		
		this.end = sentenceBreaks[sentenceBreaks.length-1] ;
	}
	
	public int[] getSentenceBreaks() {
		return sentenceBreaks ;
	}
	
	public int[] getSurroundingSentenceBounds(int pos) {
		int index = Arrays.binarySearch(sentenceBreaks, pos) ;
		
		
		if (index < 0)
			index = Math.abs(index) -2 ;
		
		if (index < sentenceBreaks.length-1) {
			int[] sentence = {sentenceBreaks[index], sentenceBreaks[index+1]} ;
			return sentence ;
		} 
		
		return null ;
	}
	
	public int[] getSurroundingSentenceBounds(int pos1, int pos2) {
		
		int index1 = Arrays.binarySearch(sentenceBreaks, pos1) ;
		if (index1 < 0)
			index1 = Math.abs(index1) -2 ;
		
		int index2 = Arrays.binarySearch(sentenceBreaks, pos2) ;
		if (index2 < 0)
			index2 = Math.abs(index2)-2 ;
		
		if (index1 == index2 && index1 < sentenceBreaks.length-1) {
			int[] sentence = {sentenceBreaks[index1], sentenceBreaks[index1+1]} ;
			return sentence ;
		} else
			return null ;
	}
	
}
