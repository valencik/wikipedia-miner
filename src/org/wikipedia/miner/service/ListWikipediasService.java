package org.wikipedia.miner.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
	public Message buildWrappedResponse(HttpServletRequest request) throws Exception {
		
		Message msg = new Message(request) ;
		
		for (String wikiName: getHub().getWikipediaNames()) {
			String desc = getHub().getWikipediaDescription(wikiName) ;
			boolean isDefault = wikiName.equals(getHub().getDefaultWikipediaName()) ;
			msg.addWikipedia(new Wikipedia(wikiName, desc, isDefault)) ;
		}
		
		return msg ;
	}

	public static class Message extends Service.Message {
		
		@Expose
		@ElementList(inline=true)
		private ArrayList<Wikipedia> wikipedias = new ArrayList<Wikipedia>() ;
		
		private Message(HttpServletRequest request) {
			super(request) ;
		}
		
		private void addWikipedia(Wikipedia w) {
			wikipedias.add(w) ;
		}

		public List<Wikipedia> getWikipedias() {
			return Collections.unmodifiableList(wikipedias);
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
		
		private Wikipedia(String name, String description, boolean isDefault) {
			this.name = name ;
			this.description = description ;
			this.isDefault = isDefault ;
		}

		public String getName() {
			return name;
		}

		public String getDescription() {
			return description;
		}

		public boolean isDefault() {
			return isDefault;
		}
	}
}
