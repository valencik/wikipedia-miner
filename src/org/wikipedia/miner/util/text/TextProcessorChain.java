/*
 *    TextProcessorChain.java
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

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Vector;


/**
 * This class provides a wrapper to be able to combine more than one textprocessor.
 * 
 * This involves adding spaces when underscores or camelcasing is used, converting characters to 
 * lowercase, and discarding whitespace and disambiguation information (the text found within
 * parentheses in many wikipedia titles).
 */
public class TextProcessorChain extends TextProcessor {

    private Vector<TextProcessor> text_processor_list;

    public TextProcessorChain() {
        this.text_processor_list = new Vector<TextProcessor>();
    }

    public TextProcessorChain(TextProcessor tp) {
        this.text_processor_list = new Vector<TextProcessor>();
        this.text_processor_list.add(tp);
    }


    /**
     * Appends a TextProcessor to the chain.
     *
     * @param tp	the TextProcessor to be appended.
     * @return  true (as per the general contract of Collection.add).
     */
    public boolean addTextProcessor(TextProcessor tp) {
        return this.text_processor_list.add(tp);
    }


    /**
     * Returns a processed copy of the argument text.
     *
     * @param text	the text to be processed.
     * @return	the processed version of this text.
     */
    public String processText(String text) {
        Iterator itr = text_processor_list.iterator();

        String t = text;
        while (itr.hasNext()) {
            text = ((TextProcessor) itr.next()).processText(text);
        }
		return text;
	}

    public static void main(String args[]) throws IOException{
        TextProcessorChain tpc = new TextProcessorChain();

        SnowballStemmerWrapper stem = new SnowballStemmerWrapper();
        tpc.addTextProcessor(stem);

        String input_filename = args[1];
        String stopword_filename = args[0];

        tpc.addTextProcessor(new StopwordRemover(new File(stopword_filename)));
        File tmp = new File(input_filename);
        FileInputStream fstream = new FileInputStream(tmp);
        DataInputStream in = new DataInputStream(fstream);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String strLine;
        String tokens[];

        while ((strLine = br.readLine()) != null) {
            tokens = strLine.split("\t");
            strLine = tokens[0];
            System.out.println(strLine + "\t" + tpc.processText(strLine) + "\t" + stem.processText(strLine));
        }
    }
}
