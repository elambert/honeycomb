package com.sun.dtf.distribution;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.recorder.Event;
import com.sun.dtf.state.DTFState;
import com.sun.dtf.util.ThreadUtil;

public class DistWorkState extends WorkState {

    private long _start = -1;
    private long _counter = 0;
    private long _currentTime = 0;

    private long _iterations = 0;
    private long _interval = 0;
    private long _unitWork = 0;

    private String _property = null;
    private String _id = null;

    private Distribution _distribution = null;

    private long _workDone = -1;
    private long _distUnitStart = -1;
    
    private int _workersDone = 0;
    private int _workerCount = 0;

    public DistWorkState(long iterations,
                         long interval,
                         long unitWork,
                         int workerCount,
                         String id,
                         String property,
                         Distribution distribution) {
        super(workerCount);
        
        _iterations = iterations;
        _interval = interval;
        _unitWork = unitWork;

        _property = property;
        _id = id;
        
        _workerCount = workerCount;

        _distribution = distribution;
    }

    public synchronized boolean doWork(int workerId) throws DTFException {

        if (_start == -1)
            _start = System.currentTimeMillis();

        if ((_iterations == -1 || _counter < _iterations) && 
            (_interval == -1 || System.currentTimeMillis() - _start < _interval)) {

            /*
             * no distribution to follows means do as much as you can!
             */
            if (_distribution != null) {
                long workGoal = _distribution.result(_currentTime);
                long elapsedTime = System.currentTimeMillis() - _distUnitStart;

                if (_workDone >= workGoal || elapsedTime > _unitWork) {
                    // cycle finished should update stuff...
                    if (_workDone < workGoal && _counter < _iterations)
                        Action.getLogger().warn(
                                "Missed work target by "
                                        + (workGoal - _workDone));

                    if (_id != null) {
                        Event event = new Event(_id);
                        event.addAttribute("workDone", _workDone);
                        event.addAttribute("workGoal", workGoal);
                        // event.addAttribute("elapsedTime", elapsedTime);
                        Action.getRecorder().record(event);
                    }

                    // Rest for a while to make the step the right size.
                    if (elapsedTime < _unitWork)
                        ThreadUtil.pause(_unitWork - elapsedTime);

                    _currentTime++;
                    _workDone = 0;
                    _distUnitStart = System.currentTimeMillis();
                }
            }

            DTFState state = Action.getState();

            if (_property != null)
                state.getConfig().setProperty(_property, "" + _counter);

            if (_id != null)
                state.getConfig().setProperty(_id + ".worker", "" + workerId);

            _counter++;
            _workDone++;
            return true;
        }

        // reaching will end up on no more working being done and all workers
        // will terminate
        _workersDone++;
        
        if (_workersDone == _workerCount)
            notify();
           
        return false;
    }
}
