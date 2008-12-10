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

import org.wikipedia.miner.util.text.*;
import org.wikipedia.miner.util.*;
import org.wikipedia.miner.model.WikipediaDatabase.CachedAnchor ;


import gnu.trove.TIntHashSet;

import java.text.DecimalFormat;
import java.sql.*;
import java.io.* ;

/**
 * This class represents a term or phrase that is used to link to pages in Wikipedia.  
 * 
 * These provide your best way of searching for articles relating to or describing a particluar term. 
 * 
 * @author David Milne
 */
public class Anchor implements Comparable{
	
	private WikipediaDatabase database ;
	private TextProcessor tp ;
	
	private String text ;
	
	private int linkCount ;
	private int occCount ;
	
	private SortedVector<Sense> senses ;
	
	public Anchor(String text, TextProcessor tp, WikipediaDatabase wd) throws SQLException {

		this.database = wd ;
		this.tp = tp ;
		
		this.text = text ;
		
		if (wd.areAnchorsCached(tp))
			initializeFromCache() ;
		else
			initializeFromDatabase() ;		
	}
	
	
	private void initializeFromCache() throws SQLException{
		
		String t = text ;
		if (tp != null)
			t = tp.processText(t) ;
		
		CachedAnchor ca = database.cachedAnchors.get(t) ;
		
		if (ca != null) {
			linkCount = ca.linkCount ;
			occCount = ca.occCount ;
			// no need to setup senses. They are sitting in memory already, so getSenses() can look them up efficiently  
		} else {
			//given text was never used as an anchor.
			linkCount = 0 ;
			occCount = 0 ;
		}
	}
	
	private void initializeFromDatabase() throws SQLException{
		
		Statement stmt = database.createStatement() ;
		ResultSet rs ;
		
		if (database.areAnchorOccurancesSummarized()) {
			occCount = 0 ;
			linkCount = 0 ;
			//	will leave loading of senses for when getSenses() is called

			if (tp==null) 
				rs = stmt.executeQuery("SELECT ao_linkCount, ao_occCount FROM anchor_occurance WHERE ao_text=\"" + database.addEscapes(text) + "\"") ;
			else 
				rs = stmt.executeQuery("SELECT ao_linkCount, ao_occCount FROM anchor_occurance_" + tp.getName() + " WHERE ao_text=\"" + database.addEscapes(tp.processText(text)) + "\"") ;
			
			if (rs.first()) {
				linkCount = rs.getInt(1) ;
				occCount = rs.getInt(2) ;
			}
		} else {
			linkCount = 0 ;
			occCount = -1 ; //flag this as being unavailable
			
			//we have to iterate though all senses to get link count, so lets load them up now
			senses = new SortedVector<Sense>() ;
			
			if (tp==null)
				rs = stmt.executeQuery("SELECT an_to, an_count FROM anchor WHERE an_text=\"" + text + "\" ORDER BY an_count DESC, an_to") ;
			else 
				rs = stmt.executeQuery("SELECT an_to, an_count FROM anchor_" + tp.getName() + " WHERE an_text=\"" + tp.processText(text) + "\" ORDER BY an_count DESC, an_to") ;
			
			while (rs.next()) {
				int an_to = rs.getInt(1) ;
				int an_count = rs.getInt(2) ;

				linkCount = linkCount + an_count ;

				try{
					Sense sense = new Sense(an_to, an_count, database) ;
					senses.add(sense, true) ;
				} catch (Exception e) { };
			}
		}
		
		rs.close();
		stmt.close();
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
	 * Returns the number of times the text is used as a link to any wikipedia page
	 * 
	 * @return see above.
	 */
	public int getLinkCount() {
		return linkCount ;
	}
	
	/**
	 * Returns the number of articles in which this term appears (regardless of wheither it is a link or not).
	 * 
	 * @return see above.
	 * @throws SQLException if ngrams have not been summarized
	 */
	public int getOccurranceCount() throws SQLException{
		if (occCount<0)
			throw new SQLException("Occurance counts for anchors are not available--they have not been summarized") ;
		else
			return occCount ;
	}
	
	/**
	 * Returns the probability that ngram is used as a link within wikipedia.
	 * 
	 * @return see above.
	 * @throws SQLException if ngrams have not been summarized
	 */
	public double getLinkProbability() throws SQLException{
		if (occCount<0)
			throw new SQLException("Link probabilities for anchors are not available. Occurance counts have not been summarized") ;
		
		if (occCount == 0) return 0 ;
		
		double prob = ((double)linkCount)/occCount ;
			
		if (prob > 1) 
			return 1 ;
		else
			return prob ;
	}
	
	public String toString() {
		return text ;
	}
	
	public boolean linksTo(Article article) throws SQLException {
		
		for (Sense sense:getSenses()) {
			if (sense.getId() == article.getId())
				return true ;			
		} 
		return false ;		
	}
	
	/**
	 * Returns a sorted vector of AnchorSenses that this text is used to link to, 
	 * in descending order of the number of times the text is used as a link to that 
	 * particular destination. 
	 * 
	 * @return see above.
	 */
	public SortedVector<Sense> getSenses() throws SQLException{
		
		if (senses != null) 
			return senses ;		
			
		if (database.areAnchorsCached(tp)) {
			//load senses from cache. Dont save them to this.senses, because then we would have two copies in memory
			SortedVector<Sense> senses = new SortedVector<Sense>() ;
			
			String t = text ;
			if (tp != null)
				t = tp.processText(t) ;
			
			CachedAnchor ca = database.cachedAnchors.get(t) ;
			
			if (ca == null)
				return senses ;
			
			for (int[] s:ca.senses) {
				try{
					Sense sense = new Sense(s[0], s[1], database) ;
					senses.add(sense, false) ;
				} catch (Exception e) {} ;		
			}
			
			return senses ;
		} else {
			// load senses from cache. Save to this.senses, so we dont have to do this again next time
			
			this.senses = new SortedVector<Sense>() ;
			
			Statement stmt = database.createStatement() ;
			ResultSet rs ;
			
			if (tp == null)
				rs = stmt.executeQuery("SELECT an_to, an_count FROM anchor WHERE an_text=\"" + database.addEscapes(text) + "\" ORDER BY an_count DESC") ;
			else
				rs = stmt.executeQuery("SELECT an_to, an_count FROM anchor_" + tp.getName() + " WHERE an_text=\"" + database.addEscapes(tp.processText(text)) + "\" ORDER BY an_count DESC") ;
			
			while (rs.next()) {
				int an_to = rs.getInt(1) ;
				int an_count = rs.getInt(2) ;
				
				try{
					Sense sense = new Sense(an_to, an_count, database) ;
					this.senses.add(sense, false) ;
				} catch (Exception e) {} ;
			}
			rs.close();
			stmt.close();
			
			return senses ;
		}
		
	}
	
	
	
	/**
	 * Returns the relatedness of this anchor to another. 
	 * 
	 * The relatedness measure is described in:
	 * TODO: add reference.
	 * 
	 * @return see above.
	 */
	public double getRelatednessTo(Anchor anch) throws SQLException{
		
		Anchor anchCombined = new Anchor(text + " " + anch.getText(), null, database) ;
		
		double wc = anchCombined.getLinkCount() ;
		if (wc > 0) wc = Math.log(wc)/30 ;
				
		double minProb = 0.005 ;
		double benchmark_relatedness = 0 ;
		double benchmark_distance = 0.20 ;
		
		SortedVector<CandidatePair> candidates = new SortedVector<CandidatePair>() ;
		
		int sensesA = 0 ;
		int sensesB = 0 ;

		for (Anchor.Sense sense1: getSenses()) {

			if (sense1.getProbability() < minProb) break ;
			sensesA++ ;
			sensesB = 0 ;

			for (Anchor.Sense sense2: anch.getSenses()) {

				if (sense2.getProbability() < minProb) break ;
				sensesB++ ;

				double relatedness = sense1.getRelatednessTo(sense2) ;
				double obviousness = (sense1.getProbability() + sense2.getProbability()) / 2 ;

				if (relatedness > (benchmark_relatedness - benchmark_distance)) {

					//System.out.println(" - - likely candidate " + candidate + ", r=" + relatedness + ", o=" + sense.getProbability()) ;
					// candidate a likely sense
					if (relatedness > benchmark_relatedness + benchmark_distance) {
						//this has set a new benchmark of what we consider likely
						//System.out.println(" - - new benchmark") ;
						benchmark_relatedness = relatedness ;
						candidates.clear() ;
					}
					candidates.add(new CandidatePair(sense1, sense2, relatedness, obviousness), false) ;
				}
			}
		}
		
		CandidatePair bestSenses = candidates.first() ;
						
		double sr = bestSenses.relatedness + wc ;
		return sr ;
	}
		
	public int compareTo(Anchor a) {
		return text.compareTo(a.getText()) ;
	}
	
	public int compareTo(Object o) {
		Anchor a = (Anchor)o ;
		return compareTo(a) ;
	}
	
	/**
	 * Represents a particular sense or destination of an Anchor.
	 */
	public class Sense extends Article{
		int occCount ;
		
		public Sense(int id, int occCount, WikipediaDatabase wd) throws SQLException {
			super(wd, id) ;
			
			this.occCount = occCount ;
			setWeight(this.occCount) ;
		}
		
		public int getOccurances() {
			return occCount ;
		}
		
		public double getProbability() {
			return ((double)occCount) / linkCount ;
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
		
		Wikipedia wikipedia = Wikipedia.getInstanceFromArguments(args) ;
		
		File dataDirectory = new File("/research/wikipediaminer/data/en/20080727/") ;

		BufferedReader in = new BufferedReader( new InputStreamReader( System.in ) );			

		TextProcessor tp = null ; //new Cleaner() ;

		while (true) {
			System.out.println("Enter a term (or press ENTER to quit): ") ;
			String termA = in.readLine() ;

			if (termA == null || termA.equals(""))
				break ;

			System.out.println("Enter second term (or ENTER to just lookup \"" + termA + "\")") ;
			String termB = in.readLine() ;

			Anchor anA = new Anchor(termA, tp, wikipedia.getDatabase()) ;
			
			System.out.println("\"" + anA.getText() + "\"") ;
			System.out.println(" - occurs in " + anA.getLinkCount() + " documents as links") ;
			if (wikipedia.getDatabase().areAnchorOccurancesSummarized())
				System.out.println(" - occurs in " + anA.getOccurranceCount() + " documents over all") ;
			System.out.println(" - possible destinations:") ;
			for (Sense sense: anA.getSenses()) 
				System.out.println("    - " + sense + " - " + sense.getOccurances() + " (" + df.format(sense.getProbability() * 100) + "%)");
	
			System.out.println();

			if (termB != null && !termB.equals("")) {
				
				Anchor anB = new Anchor(termB, tp, wikipedia.getDatabase()) ;

				System.out.println("\"" + anB.getText() + "\"") ;
				System.out.println(" - occurs in " + anB.getLinkCount() + " documents as links") ;
				if (wikipedia.getDatabase().areAnchorOccurancesSummarized())
					System.out.println(" - occurs in " + anA.getOccurranceCount() + " documents over all") ;
				System.out.println(" - possible destinations:") ;
				for (Sense sense: anB.getSenses()) 
					System.out.println("    - " + sense + " - " + sense.getOccurances() + " (" + df.format(sense.getProbability() * 100) + "%)");
				
				System.out.println();
	
				System.out.println("\nRelatedness of \"" + termA + "\" to \"" + termB + "\": " + df.format(anA.getRelatednessTo(anB) * 100) + "%") ;
				
			}
		}
	}
	
	private class CandidatePair implements Comparable {
		
		Sense senseA ;
		Sense senseB ;
		double relatedness ;
		double obviousness ;
		
		public CandidatePair(Sense senseA, Sense senseB, double relatedness, double obviousness) {
			this.senseA = senseA ;
			this.senseB = senseB ;
			this.relatedness = relatedness ;
			this.obviousness = obviousness ;			
		}
		
		public int compareTo(Object o) {
			CandidatePair cp = (CandidatePair) o ;
			return new Double(cp.obviousness).compareTo(obviousness) ;
		}
		
		public String toString() {
			return senseA + "," + senseB + ",r=" + relatedness + ",o=" + obviousness ;
		}
	}
	
}
