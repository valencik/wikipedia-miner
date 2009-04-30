/*
 *    StopwordRemover.java
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

package org.wikipedia.miner.util.text;

import org.tartarus.snowball.*;
import org.tartarus.snowball.ext.*;


/**
 * This class provides moderate morphology. This involves cleaning the text using a TextCleaner then
 * removing all stopwords.
 */	
public class SnowballStemmerWrapper extends TextProcessor {
    private int repeat;
	private Cleaner cleaner ;
    private SnowballStemmer stemmer;
	/**
	 * Initializes a newly created PorterStemmer.
	 */
	public SnowballStemmerWrapper() {
		this.cleaner = new Cleaner();
        this.stemmer = (SnowballStemmer) new englishStemmer();
        this.repeat = 1;
	}

	/**
	 * Initializes a newly created PorterStemmer.
	 */
	public SnowballStemmerWrapper(String language) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		this.cleaner = new Cleaner();
        this.selectLanguage(language);
        this.repeat = 1;
	}



	/**
	 * Select a language for the SnowballStemmer.
	 */
    public void selectLanguage(String language) throws ClassNotFoundException, InstantiationException, IllegalAccessException
    {
        Class stemClass = Class.forName("org.tartarus.snowball.ext." + language + "Stemmer");
        this.stemmer = (SnowballStemmer) stemClass.newInstance();
    }

	/**
	 * Returns the processed version of the argument string. This involves 
	 * removing all stopwords, then cleaning each remaining term.
	 * 
	 * @param	text	the string to be processed
	 * @return the processed string
	 */	
	public synchronized String processText(String text) {
		String t = text ;
        t = this.cleaner.processText(t);
        String ret = "";
        
		String[] terms = t.split("[:space:]") ;
		for(int i=0;i<terms.length; i++)
        {
            if(terms[i].length() > 0)
            {
                this.stemmer.setCurrent(terms[i]);
                for (int j = this.repeat; j != 0; j--) {
                    this.stemmer.stem();
                }
                ret += stemmer.getCurrent() + " ";
            }
        }
		
		return ret.trim();
	}
}


