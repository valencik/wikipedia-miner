package org.wikipedia.miner.service.param;

import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.wikipedia.miner.service.ServiceHub;

import org.apache.xerces.parsers.DOMParser ;

public class EnumParameter<T extends Enum<T>> extends Parameter<T> {

	
	private T[] values ;
	private String[] valueDescriptions ;
	
	private HashMap<String, T> valuesByName ;
	
	public EnumParameter(String name, String description, T defaultValue, T[] allValues, String[] valueDescriptions) {
		super(name, description, defaultValue);
		
		valuesByName = new HashMap<String, T>() ;
		for (T val:allValues) {
			valuesByName.put(val.name().toLowerCase(), val) ;
		}
		
		this.values = allValues ;
		this.valueDescriptions = valueDescriptions ;
	}
	
	@Override
	public Element getXmlDescription(ServiceHub hub) {
		Element xml = super.getXmlDescription(hub) ;
		
		for (int i=0 ; i<values.length ; i++) {
			Element xmlVal = hub.createElement("PossibleValue") ;
			xmlVal.setAttribute("name", values[i].name()) ;
			xmlVal.setAttribute("description", valueDescriptions[i]) ;
			
			xml.appendChild(xmlVal) ;
		}
		
		return xml ;
	}

	@Override
	public T getValue(HttpServletRequest request) {
		
		String s = request.getParameter(getName()) ;
		
		if (s == null)
			return getDefaultValue() ;
		
		T val = valuesByName.get(s.trim().toLowerCase()) ;
		
		if (val == null)
			return getDefaultValue() ;
		
		return val ;
		
	}

}
