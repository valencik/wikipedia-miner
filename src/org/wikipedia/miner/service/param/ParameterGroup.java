package org.wikipedia.miner.service.param;

import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.wikipedia.miner.service.ServiceHub;

import org.apache.xerces.parsers.DOMParser ;

@SuppressWarnings("unchecked")
public class ParameterGroup {
	
	String name ;
	Vector<Parameter> parameters ;
	
	public ParameterGroup(String name) {
		this.name = name ;
		this.parameters = new Vector<Parameter>() ;
	}
	
	public void addParameter(Parameter param) {
		parameters.add(param) ;
	}
	
	
	
	public String getName() {
		return name;
	}

	public Vector<Parameter> getParameters() {
		return parameters;
	}

	public Element getXmlDescription(ServiceHub hub) {
		
		Element xml = hub.createElement("ParameterGroup") ;
		xml.setAttribute("name", name) ;
			
		for (Parameter param:parameters) 
			xml.appendChild(param.getXmlDescription(hub)) ;
			
		return xml ;
	}
	
	public boolean isSpecified(HttpServletRequest request) {
				
		for (Parameter param:parameters) {
			
			if (param.getValue(request) == null) {
				return false ;
			}
		}
		
		return true ;
	}
	
	public static void main(String[] args) {
		
		
	}

}
