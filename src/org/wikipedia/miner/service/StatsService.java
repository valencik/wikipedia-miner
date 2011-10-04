package org.wikipedia.miner.service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;

import org.simpleframework.xml.*;
import org.wikipedia.miner.db.WEnvironment.StatisticName;
import org.wikipedia.miner.model.Wikipedia;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class StatsService extends Service{
	
	DateFormat df ;

	public StatsService() {
		super("meta","Provides statistics of a specific wikipedia version",
				"<p>Retrieves statistics (article counts, last edit date, etc.) for a wikipedia dump.</p>",
				true, false
				);
		
		TimeZone tz = TimeZone.getTimeZone("GMT:00");

		df = new SimpleDateFormat("dd MMM yyyy HH:mm:ss z") ;
	    df.setTimeZone( tz );
	}

	@Override
	public Response buildWrappedResponse(HttpServletRequest request) throws Exception {
		
		Response response = new Response(getWikipediaName(request)) ;
		
		Wikipedia w = getWikipedia(request) ;
		
		for (StatisticName statName:StatisticName.values()) {
			
			long stat = w.getEnvironment().retrieveStatistic(statName) ;
			switch(statName) {
			
			case lastEdit: 
				String date = df.format(new Date(stat)) ;
				response.addStat(statName, date) ;
				break ;
			default: 
				response.addStat(statName, String.valueOf(stat)) ;
				break ;
			}
		}
		
		return response;
	}
	
	public static class Response extends Service.Response {
		
		@SerializedName(value="for")
		@Attribute(name="for")
		private String wikiName ;
		
		@Expose
		@ElementMap(inline=true,attribute=true,entry="statistic",key="name")
		private HashMap<StatisticName,String> statistics = new HashMap<StatisticName,String>() ;
		
		public Response(String wikiName) {
			this.wikiName = wikiName ;
		}
		
		public void addStat(StatisticName name, String value) {
			statistics.put(name, value) ;
		}
	}

}
