package org.wikipedia.miner.service.param;

import javax.servlet.http.HttpServletRequest;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.wikipedia.miner.service.ServiceHub;

import org.apache.xerces.parsers.DOMParser;

public abstract class Parameter<T> {

	private String name ;
	private String description ;
	private T defaultValue ;
	
	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public T getDefaultValue() {
		return defaultValue;
	}

	public Element getXmlDescription(ServiceHub hub) {
		Element xmlParam = hub.createElement("Parameter") ;
		xmlParam.setAttribute("name", name) ;
		xmlParam.appendChild(hub.createElement("Description", description)) ;
		
		if (defaultValue != null) {
			xmlParam.setAttribute("optional", "true") ;
			xmlParam.setAttribute("default", String.valueOf(defaultValue)) ; 
		} else {
			xmlParam.setAttribute("optional", "false") ;
		}
		
		return xmlParam ;
	}
	
	public Parameter(String name, String description, T defaultValue) {
		this.name = name ;
		this.description = description ;
		this.defaultValue = defaultValue ;
	}
	
	public abstract T getValue(HttpServletRequest request) ;
	
}
