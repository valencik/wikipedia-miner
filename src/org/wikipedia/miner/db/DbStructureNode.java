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
	
	public static void main(String[] args) throws Exception{
		
		File berkeleyDir = new File("/Users/dmilne/Research/wikipedia/databases/simple/20080620") ;
		File luceneDir = new File("/Users/dmilne/Research/wikipedia/indexes/simple/20080620") ;
		

		WikipediaEnvironment we = new WikipediaEnvironment(berkeleyDir, luceneDir, false) ;
		
		String text = we.getPageContent(5072) ;
		DbStructureNode struct = we.getPageStructure(5072) ;
		
		//String structString = "[1059,1159,1269,1271][1271,1379]([1383,1387][1390,1447,1498,1558,1599,1670])([1674,1697][1700,1777,1861,1941,1943][1943,1995,2086])([2090,2096][2099,2142,2188,2284,2385,2387][2387,2440])([2444,2450][2453,2488,2553,2606,2834])([2838,2844][2847,2915,3004,3093,3177])([3181,3193][3196,3376,3420,3474,3548,3576,3660,3739,3978,4028,4079,4240,4312,4422,4594,4668,4744,4874,5077,5117,5263])" ;
		//System.out.println(structString) ;
		
		//DbStructureNode struct = DbStructureNode.parseStructureString(structString) ;
		
		DbLink[] linksOut = we.getLinksOut(5072) ;
		
		System.out.println(linksOut.length) ;
		
		for (DbLink link1:linksOut) {
			for (DbLink link2:linksOut) {
				
				
				
				if (!link1.equals(link2)) {
					
					
					
					Vector<String> sentences = new Vector<String>() ;
					
					for (int pos1:link1.getPositions()) {
						for (int pos2:link2.getPositions()) {
							
							DbStructureNode lowestInCommon = struct.getLowestSectionSurrounding(pos1, pos2) ;
							
							if (lowestInCommon != null) {
								int[] commonSentence = lowestInCommon.getSentenceSurrounding(pos1, pos2) ;
								if (commonSentence != null) 
									sentences.add(text.substring(commonSentence[0], commonSentence[1]).replace("\n", " ")) ;
							}
						}
					}
					
					if (sentences.size() > 0) {
						
						DbPage p1 = we.getPageDetails(link1.getId()) ;
						DbPage p2 = we.getPageDetails(link2.getId()) ;
						System.out.println(p1 + " vs " + p2) ;
						
						for (String sentence:sentences) 
							System.out.println( " - " + sentence) ;
					}
				}
			}
			
		}
		
		
	}
	
	
	
}
