/*
 * Copyright 2014 Nastel Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nastel.jkool.tnt4j.logger;

import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.nastel.jkool.tnt4j.core.ActivityStatus;
import com.nastel.jkool.tnt4j.core.OpLevel;
import com.nastel.jkool.tnt4j.core.OpType;
import com.nastel.jkool.tnt4j.format.EventFormatter;
import com.nastel.jkool.tnt4j.sink.DefaultEventSink;
import com.nastel.jkool.tnt4j.tracker.TrackingActivity;
import com.nastel.jkool.tnt4j.tracker.TrackingEvent;

/**
 * <p><code>EventSink</code> implementation that routes log messages to log4j. This implementation
 * is designed to log messages to log4j framework.</p>
 *
 *
 * @see TrackingEvent
 * @see EventFormatter
 * @see OpLevel
 *
 * @version $Revision: 11 $
 *
 */
public class Log4jEventSink extends DefaultEventSink {
	private static final String[] log4JSevMap = { 
		"INFO", "TRACE", "DEBUG", 
		"INFO", "INFO", "WARN", 
		"ERROR", "FATAL", "FATAL",
        "FATAL", "FATAL" };

	private static final String[] log4JStatusMap = { "INFO", "INFO", "INFO", "ERROR" };

	private Logger logger = null;
	private EventFormatter formatter = null;
	
	/**
	 * Create a new log4j backed event sink
	 * 
	 * @param name log4j event category/application name
	 * @param props java properties used by the event sink
	 * @param frmt event formatter used to format event entries
	 *
	 */
	public Log4jEventSink(String name, Properties props, EventFormatter frmt) {
		logger = Logger.getLogger(name);	
		formatter = frmt;
	}

	@Override
    public void log(TrackingEvent event) {
		if (!acceptEvent(event)) return;
		logger.log(getL4JLevel(event), formatter.format(event), event.getOperation().getThrowable());
		super.log(event);
    }

	@Override
	public void log(TrackingActivity activity) {
		if (!acceptEvent(activity)) return;
		Throwable ex = activity.getThrowable();
		logger.log(getL4JLevel(activity.getStatus()), formatter.format(activity), ex);
		super.log(activity);
	}
	
	/**
	 * Maps <code>TrackingEvent</code> severity to log4j Level.
	 *
	 * @param ev application tracking event
	 * @see OpType
	 */
	public Level getL4JLevel(TrackingEvent ev) {
		return getL4JLevel(ev.getSeverity());
	}

	/**
	 * Maps <code>ActivityStatus</code> severity to log4j Level.
	 *
	 * @param status application activity status
	 * @see ActivityStatus
	 */
	public Level getL4JLevel(ActivityStatus status) {
		return Level.toLevel(log4JStatusMap[status.ordinal()], Level.INFO);
	}

	/**
	 * Maps <code>OpLevel</code> severity to log4j Level.
	 *
	 * @param sev severity level
	 * @see OpType
	 */
	public Level getL4JLevel(OpLevel sev) {
		return Level.toLevel(log4JSevMap[sev.ordinal()], Level.INFO);
	}

	@Override
    public void log(OpLevel sev, String msg) {
		if (!acceptEvent(sev, msg)) return;
		logger.log(getL4JLevel(sev), formatter.format(sev, msg));
		super.log(sev, msg);
	}

	@Override
    public void log(OpLevel sev, String msg, Throwable ex) {
		if (!acceptEvent(sev, msg, ex)) return;
		logger.log(getL4JLevel(sev), formatter.format(sev, msg, ex), ex);
		super.log(sev, msg, ex);
    }

	@Override
    public Object getSinkHandle() {
	    return logger;
    }

	@Override
    public boolean isOpen() {
	    return logger != null;
    }

	@Override
    public void write(Object msg) throws IOException {
		logger.info(formatter.format(msg));
    }

	@Override
    public void open() throws IOException {
    }

	@Override
    public void close() throws IOException {
    }

	@Override
    public boolean isSet(OpLevel sev) {
		return logger.isEnabledFor(getL4JLevel(sev));
	}
}
