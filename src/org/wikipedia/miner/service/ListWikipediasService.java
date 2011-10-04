package org.wikipedia.miner.service;

import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;

import org.simpleframework.xml.*;

import com.google.gson.annotations.Expose;

public class ListWikipediasService extends Service{

	public ListWikipediasService() {
		super("meta","Lists available editions of Wikipedia", 
				"<p>This service lists the different editions of Wikipedia that are available</p>",
				false,false
				);
	}

	@Override
	public Response buildWrappedResponse(HttpServletRequest request) throws Exception {
		
		Response response = new Response() ;
		
		for (String wikiName: getHub().getWikipediaNames()) {
			String desc = getHub().getWikipediaDescription(wikiName) ;
			boolean isDefault = wikiName.equals(getHub().getDefaultWikipediaName()) ;
			response.addWikipedia(new Wikipedia(wikiName, desc, isDefault)) ;
		}
		
		return response ;
	}

	public static class Response extends Service.Response {
		
		@Expose
		@ElementList(inline=true)
		private ArrayList<Wikipedia> wikipedias = new ArrayList<Wikipedia>() ;
		
		public void addWikipedia(Wikipedia w) {
			wikipedias.add(w) ;
		}
	}
	
	public static class Wikipedia {
		
		@Expose
		@Attribute
		private String name ;
		
		@Expose
		@Attribute
		private String description ;
		
		@Expose
		@Attribute
		private boolean isDefault ;
		
		public Wikipedia(String name, String description, boolean isDefault) {
			this.name = name ;
			this.description = description ;
			this.isDefault = isDefault ;
		}
	}
}
