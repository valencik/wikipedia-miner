package org.wikipedia.miner.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.util.Span;

import org.wikipedia.miner.model.Label;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.util.NGrammer.NGramSpan;

public class NgramExtraction {

	
	Wikipedia wikipedia ;
	NGrammer nGrammer ;
	
	Pattern topicPattern = Pattern.compile("\\[\\[(\\d+)\\|(.*?)\\]\\]") ;
	Pattern quotePattern = Pattern.compile("\"(.*?)\"");
	
	
	public NgramExtraction(WikipediaConfiguration conf) {
		this.wikipedia = new Wikipedia(conf, false) ;
		this.nGrammer = new NGrammer(conf.getSentenceDetector(), conf.getTokenizer()) ;
	}
	
	public void test(String query) {
		
		ArrayList<Span> contiguousSpans = new ArrayList<Span>() ;
		
		HashMap<Long, Integer> topicIdsBySpan = new HashMap<Long, Integer>() ;
		
		String cleanedQuery = cleanTopicMarkup(query, contiguousSpans, topicIdsBySpan) ;
		cleanedQuery = cleanQuoteMarkup(cleanedQuery, contiguousSpans) ;
		
		Collections.sort(contiguousSpans) ;
		
		//System.out.println("Cleaned query: " + cleanedQuery) ;
		for (NGramSpan span:nGrammer.ngramPosDetect(cleanedQuery)) {
			
			//System.out.println("  ngram: " + span.getCoveredText(cleanedQuery) + " " + span.getStart() + ", " + span.getEnd()) ;
			
			if (!isSpanValid(span, contiguousSpans)) {
				//System.out.println("   invalid") ;
				continue ;
			}
			
			Integer topicId = topicIdsBySpan.get(getKey(span)) ;
			
			//if (topicId != null)
			//	System.out.println("   topic: " + topicId) ;
			
			Label label = wikipedia.getLabel(span, cleanedQuery) ;
			
			if (!label.exists()) {
				continue ;
			}
			
			//System.out.println("   lp: " + label.getLinkProbability()) ;
		}
	}
	
	private Long getKey(Span s) {
		long key = s.getStart() + (s.getEnd() << 30) ;
		return key ;
	}
	
	private boolean isSpanValid(Span span, ArrayList<Span> contiguousSpans) {
		
		for (Span s:contiguousSpans) {
			
			if (s.equals(span))
				return true ;
			
			if (s.intersects(span) || s.crosses(span) || s.contains(span) || span.contains(s))
				return false ;
			
			if (s.getStart() > span.getEnd())
				break ;
		}
		
		return true ;
		
	}
	
	private String cleanTopicMarkup(String query, ArrayList<Span> contiguousSpans, HashMap<Long, Integer> topicIdsBySpan) {
		
		StringBuffer sb = new StringBuffer() ;
		
		int lastCopyPoint = 0;
		Matcher m = topicPattern.matcher(query)  ;
			
		while (m.find()) {
			sb.append(query.substring(lastCopyPoint, m.start())) ;
			
			Span span = new Span(sb.length(), sb.length() + m.group(2).length()) ;
				
			contiguousSpans.add(span) ;
			topicIdsBySpan.put(getKey(span), Integer.parseInt(m.group(1))) ;
			
			sb.append(m.group(2)) ;
			lastCopyPoint = m.end();
		}
		
		sb.append(query.substring(lastCopyPoint)) ;
		return sb.toString() ;	
	}
	
	private String cleanQuoteMarkup(String query, ArrayList<Span> contiguousSpans) {
		StringBuffer sb = new StringBuffer() ;
		
		int lastCopyPoint = 0;
		Matcher m = quotePattern.matcher(query)  ;
			
		while (m.find()) {
			sb.append(query.substring(lastCopyPoint, m.start())) ;
			
			Span span = new Span(sb.length(), sb.length() + m.group(1).length()) ;
				
			contiguousSpans.add(span) ;
			
			sb.append(m.group(1)) ;
			lastCopyPoint = m.end();
		}
		
		sb.append(query.substring(lastCopyPoint)) ;
		return sb.toString() ;	
	}
	
	
	public static void main(String args[]) throws Exception{
		
		WikipediaConfiguration conf = new WikipediaConfiguration(new File("configs/en.xml")) ;
		conf.clearDatabasesToCache() ;
		
		NgramExtraction self = new NgramExtraction(conf) ;
		
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in)) ;
		
		while (true) {
			System.out.println("Enter query:") ;
			String query = input.readLine() ;
			
			if (query.trim().length() == 0)
				break ;
			
			self.test(query) ;
		}
		
		
		
		
		
		
		//query = query.replaceAll("^[\\W]*", "") ;
		//query = query.replaceAll("[\\W]*$", "") ;

		
		
	}
	
	
}
