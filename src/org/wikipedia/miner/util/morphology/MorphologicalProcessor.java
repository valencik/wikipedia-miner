/*
 *    MorphologicalProcessor.java
 *    Copyright (C) 2007 David Milne, d.n.milne@gmail.com
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.wikipedia.miner.util.morphology;

/**
 * <p>
 * This abstract class provides a framework of what is expected from a MorphologicalProcessor; a class that 
 * preforms modifications on strings to facilitate matching between them. Conservative morphology 
 * makes few changes to the string, sacrificing recall for precision.
 * Aggressive morphology makes significant changes to the string, sacrificing precision for recall.
 * 
 * @author David Milne
 */
public abstract class MorphologicalProcessor {
	
	/**
	 * Returns a string that identifies this MorphologicalProcessor. It should be human-readable and describe what this
	 * processor does, with enough detail to distinguish it from other MorphologicalProcessors. The default is to return the
	 * name of the class.
	 * 
	 * @return	the name of this MorphologicalProcessor. 
	 */
	public String getName() {
		return this.getClass().getSimpleName() ;
	}
	
	/**
	 * Returns the modified copy of the argument text, after morphological operations have been preformed 
	 * 
	 * @param text	the text to be processed.
	 * @return	the processed version of this text.
	 */
	public abstract String processText(final String text) ;
	
}
