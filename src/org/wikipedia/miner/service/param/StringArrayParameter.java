package org.wikipedia.miner.service.param;

import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;

import org.simpleframework.xml.ElementMap;
import org.w3c.dom.Element;
import org.wikipedia.miner.service.ServiceHub;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class StringArrayParameter extends Parameter<String> {

	
	private String[] values ;
	private HashMap<String, String> valuesByNormalizedValue ;
	
	public StringArrayParameter(String name, String description, String defaultValue, String[] allValues, String[] valueDescriptions) {
		super(name, description, defaultValue, "enum");
		
		valuesByNormalizedValue = new HashMap<String, String>() ;
		valueDescriptionsByName = new HashMap<String,String>() ;
		for (int i=0 ; i<allValues.length ; i++) {
			String normVal = normalizeValue(allValues[i]) ;
			valuesByNormalizedValue.put(normVal, allValues[i]) ;
			valueDescriptionsByName.put(normVal, valueDescriptions[i]) ;
		}
		
		this.values = allValues ;
	}
	
	@Override
	public String getValue(HttpServletRequest request) throws IllegalArgumentException {
		
		String s = request.getParameter(getName()) ;
		
		if (s == null)
			return getDefaultValue() ;
		
		String val = valuesByNormalizedValue.get(normalizeValue(s)) ;
		
		if (val == null)
			return getDefaultValue() ;
		
		return val ;
		
	}
	
	private String normalizeValue(String val) {
		return val.trim().toLowerCase() ;
	}

}
