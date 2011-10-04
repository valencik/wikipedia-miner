package org.wikipedia.miner.service.param;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;

import org.simpleframework.xml.ElementMap;
import org.w3c.dom.Element;
import org.wikipedia.miner.service.ServiceHub;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * @author dmilne
 *
 * @param <T>
 */
public class EnumSetParameter<T extends Enum<T>> extends Parameter<EnumSet<T>> {

	
	private T[] values ;
	private HashMap<String, T> valuesByName ;
	
	public EnumSetParameter(
			String name, 
			String description,
			EnumSet<T> defaultValue,
			T[] allValues, 
			String[] valueDescriptions
	) {
		super(name, description, defaultValue, "enum list");
		
		valuesByName = new HashMap<String, T>() ;
		valueDescriptionsByName = new HashMap<String,String>() ;
		for (int i=0 ; i<allValues.length ; i++) {
			valuesByName.put(allValues[i].name().toLowerCase(), allValues[i]) ;
			valueDescriptionsByName.put(allValues[i].name().toLowerCase(), valueDescriptions[i]) ;
		}
				
		this.values = allValues ;
	}

	@Override
	public EnumSet<T> getValue(HttpServletRequest request) throws IllegalArgumentException {
		
		String allVals = request.getParameter(getName()) ;
		
		if (allVals == null)		
			return getDefaultValue();
		
		ArrayList<T> _enums = new ArrayList<T>() ;
		for (String val:allVals.split("[,;:]")) {
			
			T _enum = valuesByName.get(val.trim().toLowerCase()) ;
			
			if (_enum != null) {
				_enums.add(_enum) ;
			}
		}

		return EnumSet.copyOf(_enums) ;
	}




}
