/*
 *    Anchor.java
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


package org.wikipedia.miner.model;

import java.io.*;
import java.text.DecimalFormat;
import java.util.TreeSet;

import org.wikipedia.miner.util.text.*;
import org.wikipedia.miner.util.*;
import org.wikipedia.miner.db.*;

import com.sleepycat.je.DatabaseException;

/**
 * This class represents a term or phrase that is used to refer to pages in Wikipedia (including link anchors, page titles, and redirects).  
 * 
 * These provide your best way of searching for articles relating to or describing a particular term. 
 * 
 * @author David Milne
 */
public class Anchor implements Comparable<Anchor>{
	
	private WikipediaEnvironment environment ;
	private TextProcessor tp ;
	
	private String text ;
	
	private DbAnchor dbAnchor ;
	
	private Sense[] senses ;
	
	/**
	 * Initializes an anchor
	 * 
	 * @param text the term or phrase of interest
	 * @param tp a text processor which will be used to alter how the given text and Wikipedia's anchors are matched (may be null)
	 * @param wd an active WikipediaDatabase
	 * @throws DatabaseException if there is a problem with the Wikipedia database
	 */
	public Anchor(WikipediaEnvironment environment, String text)  {

		this.environment = environment ;
		this.tp = null ;
		
		this.text = text ;
		this.dbAnchor = environment.getAnchor(text) ;
			
	}
	
	/**
	 * Initializes an anchor
	 * 
	 * @param text the term or phrase of interest
	 * @param tp a text processor which will be used to alter how the given text and Wikipedia's anchors are matched (may be null)
	 * @param wd an active WikipediaDatabase
	 * @throws DatabaseException if there is a problem with the Wikipedia database
	 */
	public Anchor(WikipediaEnvironment environment, String text, TextProcessor tp) throws DatabaseException {

		this.environment = environment ;
		this.tp = tp ;
		
		this.text = text ;
		this.dbAnchor = environment.getAnchor(text, tp) ;
			
	}
	
	public boolean exists() {
		return (this.dbAnchor != null) ;
	}
	
	/**
	 * Returns the text used to make the link
	 * 
	 * @return see above.
	 */
	public String getText() {
		return text ;
	}
	
	
	
	/**
	 * Returns the number of distinct articles in which this anchor is used as a link to any Wikipedia page
	 * @return see above.
	 */
	public int getDistinctLinkCount() {
		
		if (dbAnchor == null)
			return 0 ;
		else
			return dbAnchor.getDistinctLinks() ;
	}
	
	/**
	 * Returns the total number times this anchor is used as a link to any Wikipedia page
	 * @return see above.
	 */
	public int getTotalLinkCount() {
		
		if (dbAnchor == null)
			return 0 ;
		else
			return dbAnchor.getTotalLinks() ;
	}
	
	
	/**
	 * Returns the number of distinct articles in which this term appears (regardless of whether it is a link or not).
	 * 
	 * @return see above.
	 */
	public int getDistinctOccurranceCount() {
		
		if (dbAnchor == null)
			return 0 ;
		else
			return dbAnchor.getDistinctReferences() ;
	}
	
	/**
	 * Returns the total number of times this term appears (regardless of whether it is a link or not).
	 * 
	 * @return see above.
	 */
	public long getTotalOccurranceCount() {
		
		if (dbAnchor == null)
			return 0 ;
		else
			return dbAnchor.getTotalReferences() ;
	}
	
	
	/**
	 * Returns the probability that ngram is used as a link within Wikipedia.
	 * 
	 * @return see above.
	 */
	public float getLinkProbability() {
		
		
		if (getDistinctOccurranceCount()<= 0) return 0 ;
		
		float prob = ((float)getDistinctLinkCount())/this.getDistinctOccurranceCount() ;
			
		if (prob > 1) 
			return 1 ;
		else
			return prob ;
	}
	
	public String toString() {
		return text ;
	}
		
	/**
	 * Returns a sorted vector of AnchorSenses that this text is used to link to, 
	 * in descending order of the number of times the text is used as a link to that 
	 * particular destination. 
	 * 
	 * @return see above.
	 * @ if there is a problem with the Wikipedia database.
	 */
	public Sense[] getSenses() {
		
		if (senses != null) 
			return senses ;	
		
		if (dbAnchor == null) {
			senses = new Sense[0] ;
			return senses ;
		}
		
		DbSense[] tempSenses = dbAnchor.getSenses() ;
		if (tempSenses == null) {
			senses = new Sense[0] ;
			return senses ;
		}
		
		senses = new Sense[tempSenses.length] ;
		
		for (int i=0 ; i < tempSenses.length ; i++) 
			senses[i] = new Sense(environment, tempSenses[i].getDestination(), tempSenses[i].getTotalCount(), tempSenses[i].getDistinctCount(), tempSenses[i].isTitle(), tempSenses[i].isRedirect()) ;	
		
		return senses ;	
	}
	
	public DisambiguatedSensePair disambiguateAgainst(Anchor anchor) throws DatabaseException {
		
		Anchor anchCombined = new Anchor(environment, this.getText() + " " + anchor.getText()) ;
		double wc = anchCombined.getDistinctLinkCount() ;
		if (wc > 0) 
			wc = Math.log(wc)/30 ;
		
		double minProb = 0.01 ;
		double benchmark_relatedness = 0 ;
		double benchmark_distance = 0.40 ;
		
		TreeSet<DisambiguatedSensePair> candidates = new TreeSet<DisambiguatedSensePair>() ;
		
		int sensesA = 0 ;
		int sensesB = 0 ;
		
		RelatednessCache rc = new RelatednessCache() ;

		for (Anchor.Sense senseA: this.getSenses()) {

			if (senseA.getProbability() < minProb) break ;
			sensesA++ ;
			sensesB = 0 ;

			for (Anchor.Sense senseB: anchor.getSenses()) {

				if (senseB.getProbability() < minProb) break ;
				sensesB++ ;

				//double relatedness = artA.getRelatednessTo(artB) ;
				float relatedness = rc.getRelatedness(senseA, senseB) ;
				float obviousness = (senseA.getProbability() + senseB.getProbability()) / 2 ;

				if (relatedness > (benchmark_relatedness - benchmark_distance)) {

					if (relatedness > benchmark_relatedness + benchmark_distance) {
						//this has set a new benchmark of what we consider likely
						benchmark_relatedness = relatedness ;
						candidates.clear() ;
					}
					candidates.add(new DisambiguatedSensePair(senseA, senseB, relatedness, obviousness)) ;
				}
			}
		}
		
		DisambiguatedSensePair sp = candidates.first() ;
		sp.relatedness += wc ;
		if (sp.relatedness > 1)
			sp.relatedness = 1 ;
		
		return sp ;
	}
	
	
	
	/**
	 * Returns the semantic relatedness of this anchor to another. 
	 * 
	 * The relatedness measure is described in:
	 * Milne, D. and Witten, I.H. (2008) An effective, low-cost measure of semantic relatedness obtained from Wikipedia links. In Proceedings of the first AAAI Workshop on Wikipedia and Artifical Intellegence (WIKIAI'08), Chicago, I.L.
	 * 
	 * @param anchor the anchor to which this should be compared.
	 * @return see above.
	 * @ if there is a problem with the Wikipedia database.
	 */
	public float getRelatednessTo(Anchor anchor) throws DatabaseException{
		
		DisambiguatedSensePair sp = this.disambiguateAgainst(anchor) ;
		return sp.getRelatedness() ;
	}
		
	public int compareTo(Anchor a) {
		return text.compareTo(a.getText()) ;
	}
	
	
	/**
	 * Represents a particular sense or destination of an Anchor; an association between the anchor text and its destination.
	 */
	public class Sense extends Article{
		
		private int totalCount ;
		private int distinctCount ;
		private boolean matchesTitle ;
		private boolean matchesRedirect ;
		
	
		/**
		 * Initializes a sense
		 * 
		 * @param id the id of the relevant article (or disambiguation)
		 * @param occCount the number of times the anchor goes to this destination
		 * @param type e
		 * @param wd an active Wikipedia database
		 * @ if there is a problem 
		 */
		public Sense(WikipediaEnvironment environment, int id, int totalCount, int distinctCount, boolean matchesTitle, boolean matchesRedirect)  {
			super(environment, id) ;
			
			this.totalCount = totalCount ;
			this.distinctCount = distinctCount ;
			this.matchesTitle = matchesTitle ;
			this.matchesRedirect = matchesRedirect ;

			setWeight(this.totalCount) ;
		}
		
		/**
		 * @return the total number of times the anchor goes to this destination
		 */
		public int getTotalOccurances() {
			return totalCount ;
		}
		
		/**
		 * @return the total number articles in which the anchor goes to this destination
		 */
		public int getDistinctOccurances() {
			return distinctCount ;
		}
		
		public boolean matchesTitle() {
			return matchesTitle ;
		}
		
		public boolean matchesRedirect() {
			return matchesRedirect; 
		}
		
		/**
		 * @return the probability that this anchor goes to this destination
		 */
		public float getProbability() {
			
			if (getSenses().length == 1)
				return 1 ;
			
			if (getTotalLinkCount() == 0)
				return 0 ;
			else 			
				return ((float)totalCount) / getTotalLinkCount() ;
		}
	}
	
	/**
	 * Provides a demo of functionality available to Anchors.
	 * 
	 * @param args an array of arguments for connecting to a wikipedia datatabase: server and database names at a minimum, and optionally a username and password
	 * @throws Exception if there is a problem with the wikipedia database.
	 */
	public static void main(String[] args) throws Exception{
		
		DecimalFormat df = new DecimalFormat("0.00") ;
		
		File berkeleyDir = new File("/Users/dmilne/Research/wikipedia/databases/simple/20080620") ;
		File luceneDir = new File("/Users/dmilne/Research/wikipedia/indexes/simple/20080620") ;
		
		Wikipedia wikipedia = new Wikipedia(berkeleyDir, luceneDir) ;
		
		BufferedReader in = new BufferedReader( new InputStreamReader( System.in ) );			

		TextProcessor tp = null ; //new CaseFolder() ; 

		while (true) {
			System.out.println("Enter a term (or press ENTER to quit): ") ;
			String termA = in.readLine() ;

			if (termA == null || termA.equals(""))
				break ;

			System.out.println("Enter second term (or ENTER to just lookup \"" + termA + "\")") ;
			String termB = in.readLine() ;

			Anchor anA = new Anchor(wikipedia.getEnvironment(), termA, tp) ;
			
			System.out.println("\"" + anA.getText() + "\"") ;
			System.out.println(" - occurs " + anA.getTotalLinkCount() + " times in " + anA.getDistinctLinkCount() + " documents as links") ;
			System.out.println(" - occurs " + anA.getTotalOccurranceCount() + " times in " + anA.getDistinctOccurranceCount() + " documents over all") ;
			
			System.out.println(" - possible destinations:") ;
			for (Sense sense: anA.getSenses()) {
				System.out.println("    - " + sense + " (" + df.format(sense.getProbability() * 100) + "%)");
			}
	
			System.out.println();

			if (termB != null && !termB.equals("")) {
				
				Anchor anB = new Anchor(wikipedia.getEnvironment(), termB, tp) ;

				System.out.println("\"" + anB.getText() + "\"") ;
				System.out.println(" - occurs " + anB.getTotalLinkCount() + " times in " + anB.getDistinctLinkCount() + " documents as links") ;
				System.out.println(" - occurs " + anB.getTotalOccurranceCount() + " times in " + anB.getDistinctOccurranceCount() + " documents over all") ;
				
				System.out.println(" - possible destinations:") ;
				for (Sense sense: anB.getSenses()) 
					System.out.println("    - " + sense + " (" + df.format(sense.getProbability() * 100) + "%)");
				
				System.out.println();
	
				System.out.println("\nRelatedness of \"" + termA + "\" to \"" + termB + "\": " + df.format(anA.getRelatednessTo(anB) * 100) + "%") ;
			}
		}
	}
	
	public class DisambiguatedSensePair implements Comparable<DisambiguatedSensePair> {
		
		private Sense senseA ;
		private Sense senseB ;
		private float relatedness ;
		private float obviousness ;
		
		/**
		 * initializes a new pair of candidate senses when disambiguating two anchors against each other
		 * 
		 * @param senseA the candidate sense of the first anchor
		 * @param senseB the candidate sense of the seccond anchor
		 * @param relatedness the amount that these senses relate to each other
		 * @param obviousness the average prior probability of the two senses
		 */
		public DisambiguatedSensePair(Sense senseA, Sense senseB, float relatedness, float obviousness) {
			this.senseA = senseA ;
			this.senseB = senseB ;
			this.relatedness = relatedness ;
			this.obviousness = obviousness ;			
		}
		
		public int compareTo(DisambiguatedSensePair cp) {
			return new Float(cp.obviousness).compareTo(obviousness) ;
		}
		
		public String toString() {
			return senseA + "," + senseB + ",r=" + relatedness + ",o=" + obviousness ;
		}
		
		public Sense getSenseA() {
			return senseA ;
		}
		
		public Sense getSenseB() {
			return senseB ;
		}
		
		public float getRelatedness() {
			return relatedness ;
		}
		
		public float getObviousness() {
			return obviousness ;
		}
	}
	
}
