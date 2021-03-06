package com.logrit.simulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.logrit.simulator.ProcessState.PROCESS_STATE;

public abstract class Queue {
	protected ArrayList<State> states;
	protected ProcessQueue sleeping;
	protected ProcessQueue bursting;
	protected int running_time;
	protected int max_cycles;
	protected int switching_time;
	
	public static int CONTEXT_SWITCHING_TIME = 0;
	
	public Queue(ProcessQueue bursting) {
		this(bursting, new ProcessQueue(PROCESS_STATE.SLEEPING), 0, new ArrayList<State>(), 10000, CONTEXT_SWITCHING_TIME);
	}
	
	public Queue(ProcessQueue bursting, ProcessQueue sleeping, int running_time, ArrayList<State> states) {
		this(bursting, sleeping, running_time, states, 10000, CONTEXT_SWITCHING_TIME);
	}
	
	public Queue(ProcessQueue bursting, ProcessQueue sleeping, int running_time, ArrayList<State> states, int max_cycles, int switching_time) {
		this.bursting = bursting;
		this.sleeping = sleeping;
		this.running_time = running_time;
		this.states = states;
		this.max_cycles = max_cycles;
		this.switching_time = switching_time;
	}
	
	/**
	 * Selects the next process to run
	 * @return
	 */
	public abstract ProcessState selectProcess();
	
	/**
	 * Updates the sleeping processes, and puts any
	 *  that get wakened into the burst queue
	 * @param delta
	 */
	public void populate(int delta) {
		sleeping.updateTime(delta);
		ArrayList<ProcessState> new_processes = sleeping.getPastTimeProcesses();
		
		while(new_processes.size() > 0) {
			ProcessState p = new_processes.remove(0);
			sleeping.removeProcess(p.process);
			bursting.addProcess(p.process, p.timeRemaining());
		}
	}

	public int tick(ProcessState p) {
		ArrayList<ProcessState> processes = bursting.getProcesses();
		
		if(p.arrive_at > 0) {
			return p.arrive_at;
		}
		processes.remove(p);
		
		sleeping.addProcess(p.process, 0).time -= p.process.getBurst_time();
		
		return p.timeRemaining();
	}
	
	/**
	 * Runs through this queue until it is complete...
	 */
	public void run() {
		int counter = 0;
		ProcessState process = selectProcess();
		ProcessStatistics.reset();
		//while(!this.complete() && counter++ < max_cycles) {
		while(counter++ < max_cycles) {
			// Just go for lots of cycles... because
			this.complete(); 
			advance(process);
			// It's possible the tick didn't remove the process,
			//  but left has us skip forward to where it comes in
			//   (ie, if it enters the queue later)
			//  If so, we need to NOT ask for a new process,
			//   or we would have a premptive queue, which is not what we want
			if(!bursting.getProcesses().contains(process)) {
				// If there are no processes in the burst queue, we need to move forward in time
				if(bursting.getProcesses().size() == 0) {
					pop_next_sleeping();
				}
				process = selectProcess();
				running_time += this.switching_time;
			}
		}
		
		ProcessStatistics.setTotalTime(running_time);
		ProcessStatistics.createStats(states);
	}
	
	private void advance(ProcessState process) {
		int delta = this.tick(process);
		this.running_time += delta;
		this.bursting.updateArriveAt(delta);
		this.populate(delta);
		ProcessStatistics.running_time += delta;
	}
	
	private int pop_next_sleeping() {
		ProcessQueue sleeping_pop = null;
		try {
			sleeping_pop = (ProcessQueue) sleeping.clone();
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Collections.sort(sleeping_pop.getProcesses(), new Comparator<ProcessState>() {
			public int compare(ProcessState a, ProcessState b) {
				return b.timeRemaining() - a.timeRemaining();
			}
		});
		
		ProcessState p = sleeping_pop.getProcesses().get(0);
		
		int delta = p.timeRemaining();

		this.running_time += delta;
		this.bursting.updateArriveAt(delta);
		this.populate(delta);
		
		return 0;
	}
	
	/**
	 * Checks to see if the current state has existed before
	 *   If it does, that means we will be repeating something
	 *   that has happened before, so we are done/in an infinite loop
	 * @return
	 */
	protected boolean complete() {
		State current_state = new State(bursting, sleeping, running_time);
		
		for(State s : states) {
			if(s.equals(current_state)) {
				states.add(current_state);
				return true;
			}
		}
		
		states.add(current_state);
		
		return false;
	}
	
	public int getRunningTime() {
		return this.running_time;
	}
}
