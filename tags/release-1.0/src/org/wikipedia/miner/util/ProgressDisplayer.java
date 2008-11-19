/*
 *    ProgressDisplayer.java
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

package org.wikipedia.miner.util;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date ;

/**
 * @author David Milne
 *
 * This class provides a very naive means of displaying the progress of a task, with percentage of completion, time 
 * spent, and estimated time remaining. 
 */
public class ProgressDisplayer {

	String taskMessage ;
	long totalSteps ;
	long stepsDone ;
	Date startTime ;
	DecimalFormat percentFormat ;
	DecimalFormat digitFormat ;  
	SimpleDateFormat timeFormat ;
	SimpleDateFormat dateFormat ;
	
	double prevProgress ;
	double minReportedProgress ;
	
	int lastOutputLength ;
	
	/**
	 * Intitializes a new ProgressDisplayer with a message to be printed along with progress, and the number of steps the task is expected to take.
	 * 
	 * @param taskMessage a message that will be printed along with every progress update
	 * @param totalSteps the number of steps the task is expected to take.
	 */
	public ProgressDisplayer(String taskMessage, long totalSteps) {
		
		this.taskMessage = taskMessage ;
		this.totalSteps = totalSteps ;
		this.prevProgress = 0 ;
		
		this.startTime = new Date() ;
		this.percentFormat = new DecimalFormat("#0.00 %") ;
		this.digitFormat = new DecimalFormat("00") ;
	}
	
	/**
	 * Intitializes a new ProgressDisplayer with a message to be printed along with progress, and the number of steps the task is expected to take.
	 * minReportedProgress specifies how much progress must be made between updates,
	 * 
	 * @param taskMessage a message that will be printed along with every progress update
	 * @param totalSteps the number of steps the task is expected to take.
	 * @param minReportedProgress the minimum progress (as a fraction between 0 and 1) that must be made between messages
	 */
	public ProgressDisplayer(String taskMessage, long totalSteps, double minReportedProgress) {
		
		this.taskMessage = taskMessage ;
		this.totalSteps = totalSteps ;
		this.prevProgress = 0 ;
		this.minReportedProgress = minReportedProgress ;
		
		this.startTime = new Date() ;
		this.percentFormat = new DecimalFormat("#0.00 %") ;
		this.digitFormat = new DecimalFormat("00") ;
	}
	
	/**
	 * Returns the number of steps that have been done so far.
	 * 
	 * @return as above
	 */
	public long getStepsDone() {
		return stepsDone ;
	}
	
	/**
	 * Increments progress by one step and prints a message, if appropriate.
	 */
	public void update() {
		this.update(stepsDone + 1) ;
	}
	
	/**
	 * Updates progress and prints a message, if appropriate.
	 * 
	 * @param stepsDone the number of steps that have been completed so far
	 */
	public void update(long stepsDone) {
		this.stepsDone = stepsDone ;	
		
		String output = "" ;
		if (taskMessage != null)
			output = taskMessage + ": " ;
		
		double progress = (double)stepsDone/totalSteps ;

		if (stepsDone > 0) {
			Date currTime = new Date() ;
			long timeElapsed = currTime.getTime() - startTime.getTime() ;
			
			long timeTotal = (long)(timeElapsed * ((double)totalSteps/(stepsDone))) ; 
			long timeLeft = timeTotal - timeElapsed ;
					
			output = output + percentFormat.format(progress) 
			+ " in " + formatTime(timeElapsed) 
			+ ", ETA " + formatTime(timeLeft) ;	
		} 
		
		if ((progress-prevProgress)>minReportedProgress) {
			System.out.println(output) ;
			prevProgress = progress ;
		}
	}
	
	private String formatTime(long time) {
		
		int hours = 0 ;
		int minutes = 0 ; 
		int seconds = 0;
		
		seconds= (int)((double)time/1000) ;
		
		if (seconds>60) {
			minutes = (int)((double)seconds/60) ;
			seconds = seconds - (minutes * 60) ;
			
			if (minutes > 60) {
				hours = (int)((double)minutes/60) ;
				minutes = minutes - (hours * 60) ;
			}
		}
		
		return digitFormat.format(hours) + ":" + digitFormat.format(minutes) + ":" + digitFormat.format(seconds) ;
	}
	
}
