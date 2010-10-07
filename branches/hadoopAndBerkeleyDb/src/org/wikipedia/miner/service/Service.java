package org.wikipedia.miner.service;

import java.io.IOException;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.Map;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.service.param.BooleanParameter;
import org.wikipedia.miner.service.param.EnumParameter;
import org.wikipedia.miner.service.param.Parameter;
import org.wikipedia.miner.service.param.ParameterGroup;
import org.wikipedia.miner.service.param.StringArrayParameter;

public abstract class Service extends HttpServlet {

	public enum ResponseFormat {XML,DIRECT} ; 

	private ServiceHub hub ;
	private String description ;

	private Vector<ParameterGroup> parameterGroups ;
	@SuppressWarnings("unchecked")
	private Vector<Parameter> globalParameters ;


	private EnumParameter<ResponseFormat> prmResponseFormat ;
	private BooleanParameter prmHelp ;
	private StringArrayParameter prmWikipedia ;

	private Transformer serializer ;


	private DecimalFormat progressFormat = new DecimalFormat("#0%") ;

	@SuppressWarnings("unchecked")
	public Service(String description) {

		//this.name = name ;
		this.description = description ;
		this.parameterGroups = new Vector<ParameterGroup>() ;
		this.globalParameters = new Vector<Parameter>() ;




		try {
			serializer = TransformerFactory.newInstance().newTransformer();

			serializer.setOutputProperty(OutputKeys.INDENT, "yes");
			serializer.setOutputProperty(OutputKeys.METHOD,"xml");
			serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");

		} catch (TransformerConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransformerFactoryConfigurationError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		//TODO: get config file from servletconfig
		hub = ServiceHub.getInstance(config.getServletContext()) ;



		String[] descResponseFormat = {"in XML format", "directly, without any additional information such as request parameters. This format will not be valid for some services."} ;
		prmResponseFormat = new EnumParameter<ResponseFormat>("responseFormat", "the format in which the response should be returned", ResponseFormat.XML, ResponseFormat.values(), descResponseFormat) ;
		addGlobalParameter(prmResponseFormat) ;

		prmHelp = new BooleanParameter("help", "If <b>true</b>, this will return a description of the service and the parameters available", false) ;
		addGlobalParameter(prmHelp) ;

		//TODO: set this up from service hub
		
		String[] valsWikipedia = getHub().getWikipediaNames() ;
		String[] dscsWikipedia = new String[valsWikipedia.length] ;
		
		for (int i=0 ; i<valsWikipedia.length ; i++) {
			dscsWikipedia[i] = getHub().getWikipediaDescription(valsWikipedia[i]) ;
			
			if (dscsWikipedia[i] == null)
				dscsWikipedia[i] = "No description available" ;
		}
		
		prmWikipedia = new StringArrayParameter("wikipedia", "Which version of Wikipedia to use", getHub().getDefaultWikipediaName(), valsWikipedia, dscsWikipedia) ;
		addGlobalParameter(prmWikipedia) ;
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

		try {

			response.setHeader("Cache-Control", "no-cache"); 
			response.setCharacterEncoding("UTF8") ;

			if (prmHelp.getValue(request)) {
				response.setContentType("application/xml");

				serializer.transform(new DOMSource(getXmlDescription()), new StreamResult(response.getWriter()));
				return ;
			}


			Wikipedia wikipedia = getWikipedia(request) ;
			double loadProgress = wikipedia.getEnvironment().getProgress() ;

			ResponseFormat responseFormat = prmResponseFormat.getValue(request) ;

			if (responseFormat == ResponseFormat.DIRECT) { 

				if (requiresWikipedia() && loadProgress < 1)
					throw new ServletException("Wikipedia is not yet ready. Current progress is " + progressFormat.format(loadProgress)) ;

				try {
					buildUnwrappedResponse(request, response) ;
				} catch (Exception e) {
					throw new ServletException(e) ;
				}

				return ;
			}

			Element xmlRoot = getHub().createElement("WikipediaMiner") ;
			xmlRoot.setAttribute("service", request.getServletPath()) ;
			xmlRoot.appendChild(getXmlRequest(request)) ;

			Element xmlResponse = getHub().createElement("Response") ;

			if (requiresWikipedia() && loadProgress < 1) 
				xmlResponse = buildErrorResponse("Wikipedia is not yet ready. Current progress is " + progressFormat.format(loadProgress), xmlResponse) ;
			else {
				try {
					xmlResponse = buildWrappedResponse(request, xmlResponse) ;
				} catch (Exception e) {
					throw new ServletException(e) ;
				}
			}

			xmlRoot.appendChild(xmlResponse) ;

			response.setContentType("application/xml");
			response.setHeader("Cache-Control", "no-cache"); 
			response.setCharacterEncoding("UTF8") ;

			serializer.transform(new DOMSource(xmlRoot), new StreamResult(response.getWriter()));
		} catch (TransformerException e) {
			throw new ServletException(e) ;
		}
	}

	@Override
	public void destroy() {
		getHub().dropService(this) ;
	}


	public abstract Element buildWrappedResponse(HttpServletRequest request, Element response) throws Exception;


	public void buildUnwrappedResponse(HttpServletRequest request, HttpServletResponse response) throws Exception{
		throw new UnsupportedOperationException() ;
	}

	public boolean requiresWikipedia() {
		return true ;
	}

	public Element buildErrorResponse(String message, Element response) {

		response.setAttribute("error", message) ;
		return response ;
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

	public Element getXmlDescription() {

		Element xml = hub.createElement("Response") ;
		xml.appendChild(hub.createElement("Details", description)) ;

		for(ParameterGroup paramGroup:parameterGroups) 
			xml.appendChild(paramGroup.getXmlDescription(hub)) ;

		for (Parameter param:globalParameters) 
			xml.appendChild(param.getXmlDescription(hub)) ;

		return xml ;
	}


	public void addParameterGroup(ParameterGroup paramGroup) {
		parameterGroups.add(paramGroup) ;
	}

	@SuppressWarnings("unchecked")
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

	private Element getXmlRequest(HttpServletRequest request) {

		Element xmlRequest = getHub().createElement("Request") ;

		for (Enumeration<String> e = request.getParameterNames() ; e.hasMoreElements() ;) {
			String paramName = e.nextElement() ;
			xmlRequest.setAttribute(paramName, request.getParameter(paramName)) ;
		}

		return xmlRequest ;
	}



}
