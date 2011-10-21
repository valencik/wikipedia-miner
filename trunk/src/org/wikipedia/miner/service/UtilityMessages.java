package org.wikipedia.miner.service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.wikipedia.miner.service.Service.Message;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UtilityMessages {

	public static class HelpMessage extends Message {

		@Expose
		@SerializedName(value="serviceDescription")
		@Element(name="serviceDescription")
		private Service service ;
		
		protected HelpMessage(HttpServletRequest httpRequest, Service service) {
			super(httpRequest);
			
			this.service = service ;
		}

		public Service getService() {
			return service;
		}
	}
	

	
	public static class ErrorMessage extends Message {
		
		@Expose
		@Attribute
		private String error ;
		
		@Expose
		@Element (required=false)
		private String trace = null ;
		
		protected ErrorMessage(HttpServletRequest httpRequest, String message) {
			super(httpRequest) ;
			error = message ;
		}
		
		protected ErrorMessage(HttpServletRequest httpRequest, Exception e) {
			super(httpRequest) ;
			error = e.getMessage() ;
					
			ByteArrayOutputStream writer1 = new ByteArrayOutputStream() ;
			PrintWriter writer2 = new PrintWriter(writer1) ;
			
			e.printStackTrace(writer2) ;
			
			writer2.flush() ;
			trace = writer1.toString() ;
		}

		public String getError() {
			return error;
		}

		public String getTrace() {
			return trace;
		}
	}
	
	public static class ParameterMissingMessage extends ErrorMessage {	
		
		public ParameterMissingMessage(HttpServletRequest httpRequest) {
			super(httpRequest, "Parameters missing");
		}
	}
	
	public static class InvalidIdMessage extends ErrorMessage {

		@Expose 
		@Attribute
		private Integer invalidId ;

		protected InvalidIdMessage(HttpServletRequest request, Integer id) {
			super(request, "'" + id + "' is not a valid category id") ;	
			invalidId = id ;
		}

		public Integer getInvalidId() {
			return invalidId;
		}
	}

	public static class InvalidTitleMessage extends ErrorMessage {

		@Expose 
		@Attribute
		private String invalidTitle ;

		protected InvalidTitleMessage(HttpServletRequest request, String title) {
			super(request, "'" + title + "' is not a valid article title") ;	
			invalidTitle = title ;
		}

		public String getInvalidTitle() {
			return invalidTitle;
		}
		
	}
	
	public static class UnknownTermMessage extends ErrorMessage {
		
		@Expose 
		@Attribute
		private String unknownTerm ;

		protected UnknownTermMessage(HttpServletRequest request, String term) {
			super(request, "'" + term + "' is not a known term") ;	
			unknownTerm = term ;
		}

		public String getUnknownTerm() {
			return unknownTerm;
		}
		
		
	}
	
	
}
