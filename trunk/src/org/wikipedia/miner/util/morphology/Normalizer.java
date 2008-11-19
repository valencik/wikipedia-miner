/*
 *    Normalizer.java
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

package org.wikipedia.miner.util.morphology;

import java.io.BufferedReader;
import java.io.File ;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;



/**
 * This class provides very agressive morphology. This involves cleaning the text using a TextCleaner,
 * removing all stopwords, stemming individual terms using porter's stemmer, and ordering the remaining
 * terms alphabetically.
 */	
public class Normalizer extends MorphologicalProcessor {

	HashSet<String> stopwords ;
	PorterStemmer stemmer ;
	
	/**
	 * Initializes a newly created Normalizer with a list of stopwords contained within the given file. 
	 * The file must be in a format where each word is found on its own line.  
	 * 
	 * @param	stopwordFile	the file of stopwords
	 * @throws	IOException		if there is a problem reading from the file of stopwords
	 */	
	public Normalizer(File stopwordFile) throws IOException {
		
		stemmer = new PorterStemmer() ;
		stopwords = new HashSet<String>() ;
		
		BufferedReader input = new BufferedReader(new FileReader(stopwordFile)) ;
		
		String line ;
		while ((line = input.readLine()) != null) {
			String word = line.trim().toLowerCase() ;
			stopwords.add(word) ;
		}
	}

	/**
	 * Initializes a newly created Normalizer with a HashSet of stopwords.
	 * 
	 * @param	stopwords	the HashSet of stopwords
	 */
	public Normalizer(HashSet<String> stopwords) {
		this.stemmer = new PorterStemmer() ;
		this.stopwords = stopwords ;
	}
	
	
	/**
	 * Returns the normalized version of the argument string. This involves cleaning the text using a TextCleaner,
	 * removing all stopwords, stemming individual terms using porter's stemmer, and ordering the remaining
	 * terms alphabetically. 
	 * 
	 * @param	text	the string to be normalized
	 * @return the normalized string
	 */	
	public String processText(String text) {
		String t = text ;
		String[] terms = t.split(" ") ;
		TreeSet<String> orderedTerms = new TreeSet<String>() ;
		for(int i=0;i<terms.length; i++)
		{
			if (!stopwords.contains(terms[i])) 
			{
				String stemmed = stemmer.processText(terms[i]);
				orderedTerms.add(stemmed) ;
			}
		}
		String t2 = "" ;
		
		Iterator i = orderedTerms.iterator() ;
		while (i.hasNext()) {
			String term = (String) i.next() ;
			t2 = t2 + " " + term ; 
		}
		t2 = t2.trim();
		
		if (t2.equals("")) {
			return t;
		}
		return t2;
	}


}
