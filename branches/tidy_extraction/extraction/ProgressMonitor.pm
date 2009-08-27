#!/usr/bin/perl

=head1 NAME

ProgressDisplayer - A very lightweight module for monitoring the progress of a task and predicting how long it will take.

=head1 SYNOPSIS

  use ProgressMonitor;
  
  my $partsInJob = 300000 ;
  
  my $pm = ProgressMonitor->new($partsInJob, "Some kind of job") ;
    
  for (my $i=0 ; $i < $partsInJob ; $i++) {
  	$pm->update() ;
  }

=head1 DESCRIPTION

This module provides a very lightweight way of monitoring the progress of a task. 

=head2 Methods

=over 4

=cut

package ProgressMonitor;

use strict ;


=item * new($partsInJob)

Contructs a new ProgressMonitor, for a job that involves the given number of parts. 
You can also optionally provide an identifier for the job, which will be printed 
along with each update message.

=cut

sub new {
	my $class = shift;
	
	my $self = {};
	
	$self->{PARTS_IN_JOB} = shift ;
	$self->{PARTS_DONE} = 0 ;
	
	
	$self->{JOB_NAME} = shift ;
	
	$self->{START_TIME} = time ;
	$self->{LAST_REPORT_TIME} = time ;
	
	$self->{MESSAGE} = undef ;
	
	bless($self, $class) ;
	return $self ;
}


=item * update($partsDone)

Updates the ProgressMonitor. If it has been more than a second since the last update, this will 
print a message in the form: 

<job indentifier>: <percentDone> in <timeTaken>, ETA:<estimated time remaining>

If you do not specify $partsDone, the monitor will assume that the job has progressed by one step. 

=cut

sub update {
	my $self = shift;
	
	if (@_) {
		$self->{PARTS_DONE} = shift ;
	} else {
		$self->{PARTS_DONE} ++ ;
	}
	
	my $curr_time = time ;
	
	if ($curr_time == $self->{LAST_REPORT_TIME} && $self->{PARTS_DONE} < $self->{PARTS_IN_JOB}) {
		#do not report if we reported less than a second ago, unless we have finished.
		return ;
	}
	
	my $work_done = $self->{PARTS_DONE}/$self->{PARTS_IN_JOB} ;    
    my $time_elapsed = $curr_time - $self->{START_TIME} ;
    my $time_expected = (1/$work_done) * $time_elapsed ;
    my $time_remaining = $time_expected - $time_elapsed ;
   	$self->{LAST_REPORT_TIME} = $curr_time ;
    
    #clear previous message from prompt
    if (defined $self->{MESSAGE}) {
		$self->{MESSAGE} =~ s/./\b/g ;
		print $self->{MESSAGE} ;
    }
    
    #flush output, so we definitely see this message
    $| = 1 ;
    
    if ($self->{PARTS_DONE} >= $self->{PARTS_IN_JOB}) {
    	$self->{MESSAGE} = $self->{JOB_NAME}.": done in ".format_time($time_elapsed)."                          " ;
    } else {
    	$self->{MESSAGE} = $self->{JOB_NAME}.": ".format_percent($work_done)." in ".format_time($time_elapsed).", ETA:".format_time($time_remaining) ;
    }
    
    print $self->{MESSAGE} ;
}


=item * done()

Prints a message to show that the job is complete. 

=cut

sub done {
	my $self = shift;
	$self->update($self->{PARTS_IN_JOB}) ;	
	print("\n") ;
}




sub format_percent {
   	return sprintf("%.2f",($_[0] * 100))."%" ;
}

sub format_time {
    my @t = gmtime($_[0]) ;

    my $hr = $t[2] + (24*$t[7]) ;
    my $min = $t[1] ;
    my $sec = $t[0] ;
	
    return sprintf("%02d:%02d:%02d",$hr, $min, $sec) ; 
} 

=back

=head1 AUTHOR

David Milne (d.n.milne@gmail.com)

=head1 COPYRIGHT

Copyright 2009 Waikato University.  All rights reserved.

This library is free software; you can redistribute it and/or
modify it under the same terms as Perl itself.

=cut



1;