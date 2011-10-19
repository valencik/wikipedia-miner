package org.wikipedia.miner.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import org.wikipedia.miner.model.Label;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.util.NGrammer.NGramSpan;

public class NgramExtraction {

	public static void main(String args[]) throws Exception{
		
		WikipediaConfiguration conf = new WikipediaConfiguration(new File("configs/en.xml")) ;
		conf.clearDatabasesToCache() ;
		
		Wikipedia wikipedia = new Wikipedia(conf, false) ;
		
		
		NGrammer nGrammer = new NGrammer(conf.getSentenceDetector(), conf.getTokenizer()) ;
		
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in)) ;
		
		while (true) {
			System.out.println("Enter query:") ;
			String query = input.readLine() ;
			
			if (query.trim().length() == 0)
				break ;
			
			NGramSpan spans[] = nGrammer.ngramPosDetect(query) ;
			
			if (spans.length == 0) {
				System.out.println("No ngrams") ;
				continue ;
			}
				
			Label label = wikipedia.getLabel(spans[0], query) ;
			
			System.out.println(label.getText() + ", " + label.getLinkProbability()) ;
		}
		
		
		
		
		
		
		//query = query.replaceAll("^[\\W]*", "") ;
		//query = query.replaceAll("[\\W]*$", "") ;

		
		
	}
	
	
}
