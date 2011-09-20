import java.io.File;

import org.wikipedia.miner.db.WDatabase.DatabaseType;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.util.WikipediaConfiguration;


public class SnippetAnnotator {

	Wikipedia _wikipedia ;

	public SnippetAnnotator(Wikipedia wikipedia) {
		
		
	}
	
	public String wikify(String input) {
		
		
	}
	

	public static void main(String args[]) throws Exception {

		File dataDir = new File(args[0]) ;

		WikipediaConfiguration conf = new WikipediaConfiguration(new File(args[1])) ;
		conf.addDatabaseToCache(DatabaseType.label) ;
		conf.addDatabaseToCache(DatabaseType.pageLinksInNoSentences) ;

		Wikipedia wikipedia = new Wikipedia(conf, false) ;
	}
}
