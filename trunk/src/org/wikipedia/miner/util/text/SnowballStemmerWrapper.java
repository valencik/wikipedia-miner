/*
 *    SnowballStemmerWrapper.java
 *    Copyright (C) 2009 Giulio Paci, g.paci@cineca.it
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
 * This class provides a TextProcessor based on snowball stemmers.
 * Have a look at http://snowball.tartarus.org/
 *
 * @author Giulio Paci
 */	
public class SnowballStemmerWrapper extends TextProcessor {
    private int repeat;
	private Cleaner cleaner ;
    private SnowballStemmer stemmer;
    private String language;

    /**
	 * Initializes a newly created Stemmer (English).
	 */
	public SnowballStemmerWrapper() {
		this.cleaner = new Cleaner();
        this.stemmer = (SnowballStemmer) new englishStemmer();
        this.language = "english";
        this.repeat = 1;
	}

	/**
	 * Initializes a newly created Stemmer for a specific language.
	 */
	public SnowballStemmerWrapper(String language) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		this.cleaner = new Cleaner();
        this.language = language;
        this.selectLanguage(language);
        this.repeat = 1;
	}

	/**
	 * Returns the name of the current SnowballStemmer (includes language information).
	 *
	 * @return	the name of this TextProcessor.
	 */
    @Override
    public String getName(){
        return this.getClass().getSimpleName() + "_" + this.language;
    }


	/**
	 * Returns a string that provides complete information to setup the current configuration of the
     * text processor.
	 *
	 * @return	a textual description of the TextProcessor.
	 */
    @Override
    public String toString(){
        return super.toString() + "<string<" + TextProcessor.encodeParam(this.language);
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
	 * Returns the processed version of the argument string.
	 * 
	 * @param	text	the string to be processed
	 * @return the processed string
	 */	
	public synchronized String processText(String text) {
		StringBuffer processedText = new StringBuffer() ;
		String[] terms = text.toLowerCase().split("\\s+") ;
		for(int i=0;i<terms.length; i++)
        {
            if(terms[i].length() > 0)
            {
                this.stemmer.setCurrent(terms[i]);
                for (int j = this.repeat; j != 0; j--) {
                    this.stemmer.stem();
                }
				processedText.append(cleaner.processText(stemmer.getCurrent()));
				processedText.append(" ");
            }
		}

		return processedText.toString().trim() ;
	}
}


