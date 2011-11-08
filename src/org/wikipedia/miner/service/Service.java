package org.wikipedia.miner.service;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementMap;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.service.UtilityMessages.ErrorMessage;
import org.wikipedia.miner.service.UtilityMessages.HelpMessage;
import org.wikipedia.miner.service.param.BooleanParameter;
import org.wikipedia.miner.service.param.EnumParameter;
import org.wikipedia.miner.service.param.Parameter;
import org.wikipedia.miner.service.param.ParameterGroup;
import org.wikipedia.miner.service.param.StringArrayParameter;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


@SuppressWarnings("serial")
public abstract class Service extends HttpServlet {

	public enum ResponseFormat {XML,JSON,DIRECT} ; 

	private ServiceHub hub ;
	
	@Expose
	@Attribute
	private String groupName ;
	
	@Expose
	@SerializedName(value="description")
	@Attribute (name="description")
	private String shortDescription ;
	
	@SuppressWarnings("unused")
	@Expose
	@SerializedName(value="details")
	@Element(name="details", data=true)
	private String detailsMarkup ;

	@Expose
	@ElementList
	private Vector<ParameterGroup> parameterGroups ;
	
	@Expose
	@ElementList
	@SuppressWarnings("rawtypes")
	private Vector<Parameter> globalParameters ;
	
	@Expose
	@ElementList
	@SuppressWarnings("rawtypes")
	private Vector<Parameter> baseParameters ;
	
	@Expose
	@ElementList
	private Vector<Example> examples = new Vector<Example>() ;


	boolean wikipediaSpecific ;
	boolean supportsDirectResponse ;
	
	protected EnumParameter<ResponseFormat> prmResponseFormat ;
	protected BooleanParameter prmHelp ;
	protected StringArrayParameter prmWikipedia ;

	private DecimalFormat progressFormat = new DecimalFormat("#0%") ;

	@SuppressWarnings("rawtypes")
	public Service(String groupName, String shortDescription, String detailsMarkup, boolean wikipediaSpecific, boolean supportsDirectResponse) {

		//this.name = name ;
		this.groupName = groupName ;
		this.shortDescription = shortDescription ;
		this.detailsMarkup = detailsMarkup ;
		this.parameterGroups = new Vector<ParameterGroup>() ;
		this.globalParameters = new Vector<Parameter>() ;
		this.baseParameters = new Vector<Parameter>() ;

		this.wikipediaSpecific = wikipediaSpecific ;
		this.supportsDirectResponse = supportsDirectResponse ;
	}

	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		hub = ServiceHub.getInstance(config.getServletContext()) ;

		String[] descResponseFormat = {"in XML format", "in JSON format", "directly, without any additional information such as request parameters. This format will not be valid for some services."} ;
		prmResponseFormat = new EnumParameter<ResponseFormat>("responseFormat", "the format in which the response should be returned", ResponseFormat.XML, ResponseFormat.values(), descResponseFormat) ;
		baseParameters.add(prmResponseFormat) ;
		
		if (wikipediaSpecific) {
			String[] valsWikipedia = getHub().getWikipediaNames() ;
			String[] dscsWikipedia = new String[valsWikipedia.length] ;
			
			for (int i=0 ; i<valsWikipedia.length ; i++) {
				dscsWikipedia[i] = getHub().getWikipediaDescription(valsWikipedia[i]) ;
				
				if (dscsWikipedia[i] == null)
					dscsWikipedia[i] = "No description available" ;
			}
			prmWikipedia = new StringArrayParameter("wikipedia", "Which edition of Wikipedia to retrieve information from", getHub().getDefaultWikipediaName(), valsWikipedia, dscsWikipedia) ;
			baseParameters.add(prmWikipedia) ;
		}
		
		prmHelp = new BooleanParameter("help", "If <b>true</b>, this will return a description of the service and the parameters available", false) ;
		baseParameters.add(prmHelp) ;
		
		hub.registerService(this) ;
	}
	
	public void addExample(Example example) {
		this.examples.add(example) ;
	}

	public ServiceHub getHub() {
		return hub ;
	}	                                                            

	public Wikipedia getWikipedia(HttpServletRequest request) {

		String wikiName = prmWikipedia.getValue(request) ;

		Wikipedia wiki = hub.getWikipedia(wikiName) ;

		return wiki ;
	}

	public String getWikipediaName(HttpServletRequest request) {
		return prmWikipedia.getValue(request) ; 
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

		doGet(request, response) ;

	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

		ResponseFormat responseFormat = prmResponseFormat.getValue(request) ;
		boolean requestingHelp = prmHelp.getValue(request) ;
		
		response.setCharacterEncoding("UTF8") ;
		
		switch(responseFormat) {
		
		case DIRECT:
			response.setContentType("text/html") ;
			break ;
		case XML:
			response.setContentType("application/xml") ;
			break ;
		case JSON: 
			response.setContentType("application/json");
			break ;
		}
		
		Message msg ;
		
		try {
			if (!requestingHelp) {
				
				if (wikipediaSpecific && requiresWikipedia()) {
					Wikipedia wikipedia = getWikipedia(request) ;
					double loadProgress= wikipedia.getEnvironment().getProgress() ;
					
					if (loadProgress < 1)
						throw new ProgressException(loadProgress) ;
				}
				
				if (isUsageLimitExceeded(request))
					throw new UsageLimitException() ;
			}
			
			if (responseFormat == ResponseFormat.DIRECT) {
				buildUnwrappedResponse(request, response) ;
				response.getWriter().flush() ;
				return ;
			} else {
	
				if (requestingHelp)
					msg = new HelpMessage(request, this) ;
				else 
					msg = buildWrappedResponse(request) ;
			}
			
			
		} catch (Exception e) {
			if (responseFormat == ResponseFormat.DIRECT)
				throw new ServletException(e) ;
			else
				msg = new ErrorMessage(request, e) ;
		}
		
		try {
			if (responseFormat == ResponseFormat.XML) {
				getHub().getXmlSerializer().write(msg, System.out) ;
				getHub().getXmlSerializer().write(msg, response.getWriter());
			} else {
				getHub().getJsonSerializer().toJson(msg, response.getWriter());
			}
			
			response.getWriter().flush() ;
			
		} catch (Exception e) {
			throw new ServletException(e) ;
		}
	}

	@Override
	public void destroy() {
		getHub().dropService(this) ;
	}


	public abstract Message buildWrappedResponse(HttpServletRequest request) throws Exception;

	public void buildUnwrappedResponse(HttpServletRequest request, HttpServletResponse response) throws Exception{
		throw new UnsupportedOperationException() ;
	}
	
	public int getUsageCost(HttpServletRequest request) {
		return 1 ;
	}
	
	private boolean isUsageLimitExceeded(HttpServletRequest request) {
		Client client = getHub().identifyClient(request) ;
		
		if (client == null)
			return false ;
		
		if (getUsageCost(request)==0)
			return false ;
		
		return client.update(getUsageCost(request)) ; 
	}
	
	public boolean requiresWikipedia() {
		return wikipediaSpecific ;
	}
	
	public String getBasePath(HttpServletRequest request) {

		StringBuffer path = new StringBuffer() ;
		path.append(request.getScheme()) ;
		path.append("://") ;
		path.append(request.getServerName()) ;
		path.append(":") ;
		path.append(request.getServerPort()) ;
		path.append(request.getContextPath()) ;

		return path.toString() ;
	}
	
	public String getGroupName() {
		if (groupName != null)
			return groupName ;
		else 
			return "ungrouped" ;
	}
	
	public String getShortDescription() {
		return shortDescription ;
	}

	public void addParameterGroup(ParameterGroup paramGroup) {
		parameterGroups.add(paramGroup) ;
	}


	@SuppressWarnings("rawtypes")
	public void addGlobalParameter(Parameter param) {
		globalParameters.add(param) ;
	}

	public ParameterGroup getSpecifiedParameterGroup(HttpServletRequest request) {


		for (ParameterGroup paramGroup:parameterGroups) {
			if (paramGroup.isSpecified(request))
				return paramGroup ;		
		}

		return null ;
	}


	public static class Example {
		
		@Expose
		@Element(data=true)
		private String description ;
		
		@Expose
		@ElementMap(attribute=true, entry="parameter", key="name")
		private Map<String,String> parameters ;
		
		@Expose
		@Attribute
		private String url ;
		
		public Example(String description, LinkedHashMap<String,String>params, String servletName) {
			this.description = description ;
			this.parameters = params ;
			
			StringBuffer sb = new StringBuffer() ;
			sb.append(servletName) ;
			
			int index = 0 ;
			for (Map.Entry<String, String> e:parameters.entrySet())  {
				
				if (index == 0)
					sb.append("?") ;
				else
					sb.append("&") ;
				
				sb.append(e.getKey()) ;
				sb.append("=") ;
				sb.append(e.getValue()) ;
				
				index++ ;
			}
			
			url = sb.toString() ;
		}

		public String getDescription() {
			return description;
		}

		public Map<String, String> getParameters() {
			return parameters;
		}

		public String getUrl() {
			return url;
		}
	}
	
	public class ExampleBuilder {
		
		private String description ;
		private LinkedHashMap<String,String> params = new LinkedHashMap<String,String>() ;
		
		public ExampleBuilder(String description) {
			this.description = description ;
		}
		
		public <T> ExampleBuilder addParam(Parameter<T> param, T value) {
			
			params.put(param.getName(), param.getValueForDescription(value)) ;
			return this ;
		}
		
		public Example build() {
			return new Example(description, params, getServletName()) ;
		}
	
	}
	
	public class ProgressException extends Exception {
		private double _progress ;
		
		public ProgressException(double progress) {
			super("Wikipedia is not yet ready. Current progress is " + progressFormat.format(progress)) ;
			_progress = progress ;
		}

		public double getProgress() {
			return _progress;
		}
	}
	
	public static class UsageLimitException extends Exception {
		public UsageLimitException() {
			super("You have exceeded your usage limits") ;
		}
	}

	public static class Message {
		
		@Expose
		@Attribute
		private String service ;
		
		@Expose
		@ElementMap(attribute=true, entry="param", key="name")
		private HashMap<String, String> request = new HashMap<String,String>();
		
		public Message(HttpServletRequest httpRequest) {
			this.service = httpRequest.getServletPath() ;
			
			for (@SuppressWarnings("unchecked")
			Enumeration<String> e = httpRequest.getParameterNames() ; e.hasMoreElements() ;) {
				String paramName = e.nextElement() ;
				request.put(paramName, httpRequest.getParameter(paramName)) ;
			}
		}

		public String getServiceName() {
			return service;
		}

		public HashMap<String, String> getRequest() {
			return request;
		}
	}
	
	
	
	
}
