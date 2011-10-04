import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;

import org.wikipedia.miner.annotation.Disambiguator;
import org.wikipedia.miner.annotation.Topic;
import org.wikipedia.miner.annotation.TopicDetector;
import org.wikipedia.miner.annotation.preprocessing.DocumentPreprocessor;
import org.wikipedia.miner.annotation.preprocessing.PreprocessedDocument;
import org.wikipedia.miner.annotation.preprocessing.WikiPreprocessor;
import org.wikipedia.miner.annotation.tagging.DocumentTagger;
import org.wikipedia.miner.annotation.tagging.WikiTagger;
import org.wikipedia.miner.annotation.tagging.DocumentTagger.RepeatMode;
import org.wikipedia.miner.annotation.weighting.LinkDetector;
import org.wikipedia.miner.comparison.ArticleComparer;
import org.wikipedia.miner.db.WDatabase.DatabaseType;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.util.WikipediaConfiguration;


public class SnippetAnnotator {
	
	DocumentPreprocessor _preprocessor ; 
	ArticleComparer _comparer ;
	Disambiguator _disambiguator ;
	TopicDetector _topicDetector ;
	LinkDetector _linkDetector ;
	DocumentTagger _tagger ;
	
	DecimalFormat _df = new DecimalFormat("#0%") ;

	public SnippetAnnotator(Wikipedia wikipedia) throws Exception {
		_preprocessor = new WikiPreprocessor(wikipedia) ;
		_disambiguator = new Disambiguator(wikipedia) ;
		_topicDetector = new TopicDetector(wikipedia, _disambiguator, true, false) ;
		_linkDetector = new LinkDetector(wikipedia) ;
		_tagger = new WikiTagger() ;
	}
	
	public void annotate(String originalMarkup) throws Exception {

			PreprocessedDocument doc = _preprocessor.preprocess(originalMarkup) ;
			
			Collection<Topic> allTopics = _topicDetector.getTopics(doc, null) ;
			System.out.println("\nAll detected topics:") ;
			for (Topic t:allTopics)
			     System.out.println(" - " + t.getTitle()) ;
			
			ArrayList<Topic> bestTopics = _linkDetector.getBestTopics(allTopics, 0.5) ;
			System.out.println("\nTopics that are probably good links:") ;
			for (Topic t:bestTopics)
			     System.out.println(" - " + t.getTitle() + "[" + _df.format(t.getWeight()) + "]" ) ;
			
			String newMarkup = _tagger.tag(doc, bestTopics, RepeatMode.ALL) ;
			System.out.println("\nAugmented markup:\n" + newMarkup + "\n") ;

	}
	

	public static void main(String args[]) throws Exception {
		
		WikipediaConfiguration conf = new WikipediaConfiguration(new File(args[0])) ;
		conf.clearDatabasesToCache() ;
		conf.setTopicDisambiguationModel(new File("temp/disambig.model")) ;
		conf.setLinkDetectionModel(new File("temp/detect.model")) ;
		
		Wikipedia wikipedia = new Wikipedia(conf, false) ;
		
		SnippetAnnotator annotator = new SnippetAnnotator(wikipedia) ;
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in)) ;
		
		while (true) {
			System.out.println("Enter snippet to annotate (or ENTER to quit):") ;
			String line = reader.readLine();
			
			if (line.trim().length() == 0)
				break ;
			
			annotator.annotate(line) ;
		}
	}
}
