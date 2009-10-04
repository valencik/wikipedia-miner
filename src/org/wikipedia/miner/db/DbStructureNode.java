package org.wikipedia.miner.db;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.wikipedia.miner.model.Wikipedia;

public class DbStructureNode implements Comparable<DbStructureNode>{

	private int start ;
	//private DbStructureNode parent ;
	private Vector<DbStructureNode> childVect ;
	private DbStructureNode[] children ;
	private int[] sentenceBreaks ;
	
	//initialize as a section, where we have no idea what it's children will be
	public DbStructureNode(int start) {
		
		this.start = start ;
		this.children = null ;
		this.childVect = new Vector<DbStructureNode>() ;
		this.sentenceBreaks = null ;
	}
	
	public DbStructureNode(int start, DbStructureNode[] children) {
		
		this.start = start ;
		this.children = children ;
		this.childVect = null ;
		this.sentenceBreaks = null ;
	}
	
	//initialize as a paragraph
	public DbStructureNode(int[] sentenceBreaks) {
		
		this.start = sentenceBreaks[0] ;
		this.children = null ;		
		this.childVect = null ;		
		this.sentenceBreaks = sentenceBreaks ;
	}
	
	public int getStartPosition() {
		return start ;
	}
	
	public DbStructureNode[] getChildren() {
		return children ;
	}
	
	public int[] getSentenceBreaks() {
		return sentenceBreaks ;
	}
	
	
	public void addChild(DbStructureNode child) {
		
		this.childVect.add(child) ;
	}
	
	public void finalizeChildren() {
		this.children = this.childVect.toArray(new DbStructureNode[this.childVect.size()]) ;
		this.childVect = null ;
	}
	
	public DbStructureNode getLowestSectionSurrounding(int pos) {
		return getLowestSectionSurrounding(new DbStructureNode(pos)) ;
	}
	
	private DbStructureNode getLowestSectionSurrounding(DbStructureNode node) {
		
		if (this.children == null)
			return this ;
		
		int index = Arrays.binarySearch(children, node) ;
		if (index < 0)
			index = Math.abs(index) -2 ;
		
		if (index < children.length) 
			return children[index].getLowestSectionSurrounding(node) ;
		else
			return this ;
	}
	
	public DbStructureNode getLowestSectionSurrounding(int pos1, int pos2) {
		
		DbStructureNode node1 = new DbStructureNode(pos1) ;
		DbStructureNode node2 = new DbStructureNode(pos2) ;
		
		return getLowestSectionSurrounding(node1, node2) ;
	}
	
	private DbStructureNode getLowestSectionSurrounding(DbStructureNode node1, DbStructureNode node2) {
		
		if (this.children == null)
			return this ;
		
		int index1 = Arrays.binarySearch(children, node1) ;
		if (index1 < 0)
			index1 = Math.abs(index1) -2 ;
		
		int index2 = Arrays.binarySearch(children, node2) ;
		if (index2 < 0)
			index2 = Math.abs(index2)-2 ;
		
		if (index1 == index2 && index1 < children.length) 
			return children[index1].getLowestSectionSurrounding(node1, node2) ;
		else
			return this ;
	}
	
	public int[] getSentenceSurrounding(int pos) {
			
		if (sentenceBreaks == null) 
			return null ;
		
		int index = Arrays.binarySearch(sentenceBreaks, pos) ;
		if (index < 0)
			index = Math.abs(index) -2 ;
		
		if (index < (sentenceBreaks.length -1)) {
			int[] sentence = {sentenceBreaks[index], sentenceBreaks[index+1]} ;
			return sentence ;
		} else {
			return null ;
		}
			
		
	}
	
	public int[] getSentenceSurrounding(int pos1, int pos2) {
		
		if (sentenceBreaks == null) 
			return null ;
		
		int index1 = Arrays.binarySearch(sentenceBreaks, pos1) ;
		if (index1 < 0)
			index1 = Math.abs(index1) -2 ;
		
		int index2 = Arrays.binarySearch(sentenceBreaks, pos2) ;
		if (index2 < 0)
			index2 = Math.abs(index2)-2 ;
		
		if (index1 == index2 && index1 < (sentenceBreaks.length-1)) {
			int[] sentence = {sentenceBreaks[index1], sentenceBreaks[index1+1]} ;
			return sentence ;
		} else
			return null ;
	}
	
	
	public int compareTo(DbStructureNode n) {
		return new Integer(start).compareTo(n.start) ;
	}
	
	public static DbStructureNode parseStructureString(String structureString) {
		
		Pattern p = Pattern.compile("(\\()|(\\))|\\[(.*?)\\]") ;
		Matcher m = p.matcher(structureString) ;
		
		DbStructureNode rootNode = new DbStructureNode(0) ;
		
		Vector<DbStructureNode> stack = new Vector<DbStructureNode>() ;
		stack.insertElementAt(rootNode, 0) ;
		
		int lastPos = 0 ;
		while (m.find()) {
			
			if (m.group(1) != null) {
				//this is the start of a section
				
				//create a new section node, as a child of current node, make it current node
				DbStructureNode newNode = new DbStructureNode(lastPos) ;
				stack.firstElement().addChild(newNode) ;
				stack.insertElementAt(newNode, 0) ;
			}
			
			if (m.group(2) != null) {
				//this is the end of a section
				
				//pop node of the stack, so we return to working with it's parent
				stack.firstElement().finalizeChildren() ;
				stack.removeElementAt(0) ;
			}
			
			if (m.group(3) != null) {
				//this is a paragraph
				
				String[] values = m.group(3).split(",") ;
				int[] sentenceBreaks = new int[values.length] ;
				for (int i=0 ; i<values.length ; i++)
					sentenceBreaks[i] = Integer.parseInt(values[i]) ;
				
				DbStructureNode newNode = new DbStructureNode(sentenceBreaks) ;
				
				stack.firstElement().addChild(newNode) ;
				
				lastPos = sentenceBreaks[sentenceBreaks.length-1] ;
			}
		}
		rootNode.finalizeChildren() ;
		
		return rootNode ;
	}
	
}
