package org.wikipedia.miner.service.param;

import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;

import org.simpleframework.xml.*;
import org.wikipedia.miner.service.ServiceHub;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;




/**
 * A parameter or argument for a service. Parameters should know how to extract their value from an {@link HttpServletRequest}
 * and document themselves as an xml description.
 * 
 * @param <T> the type of value to expect.
 */
public abstract class Parameter<T> {

	@Expose
	@Attribute
	private String name ;
	
	@Expose
	@Element(data=true) 
	private String description ;

	private T defaultValue ;

	@Expose
	@SerializedName(value="datatype")
	@Attribute(name="datatype")
	private String dataTypeName ;
	
	
	//these are only needed for serialization
	@Expose
	@Attribute
	private boolean optional ;
	
	@Expose
	@SerializedName(value="defaultValue")
	@Attribute(name="defaultValue", required=false)
	private String defaultValueForSerialization ;
	


	@Expose
	@SerializedName(value="possibleValues")
	@ElementMap(name="possibleValues", entry="possibleValue", key="name", value="description", required=false)
	protected HashMap<String,String> valueDescriptionsByName = null ;
	
	
	/**
	 * Returns the name of the parameter
	 * 
	 * @return the name of the parameter
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns a short textual description of what this parameter does
	 * 
	 * @return a short textual description of what this parameter does
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Returns the value to be used if no value is manually specified in the service request. This may be null, in which 
	 * case the parameter is considered mandatory: all requests to the service must specify a value 
	 * for this parameter
	 * 
	 * @return the value to be used if none is manually specified in the service request. 
	 */
	public T getDefaultValue() {
		return defaultValue;
	}
	
	public String getValueForDescription(T val) {
		return val.toString() ;
	}
	
	/**
	 * Initialises a new parameter
	 * 
	 * @param name the name of the parameter
	 * @param description a short description of what this parameter does
	 * @param defaultValue the value to use when requests do not specify a value for this parameter (may be null)
	 */
	public Parameter(String name, String description, T defaultValue, String dataTypeName) {
		this.name = name ;
		this.description = description ;
		this.defaultValue = defaultValue ;
		this.dataTypeName = dataTypeName ;
		
		
		if (defaultValue != null) {
			optional = true ;
			defaultValueForSerialization = getValueForDescription(defaultValue) ; 
		} else {
			optional = false ;
		}
	}
	
	/**
	 * Returns the value of this parameter, as specified in the given request. 
	 * If the request specifies an invalid value, or none at all, then the default value will be returned.
	 * 
	 * @param request the request made to the service
	 * @return the value of this parameter, as specified in the given request.
	 * @throws IllegalArgumentException if the value specified in this request cannot be parsed
	 */
	public abstract T getValue(HttpServletRequest request) throws IllegalArgumentException ;
	
}
