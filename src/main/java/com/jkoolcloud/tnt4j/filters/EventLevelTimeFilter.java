/*
 * Copyright 2014-2015 JKOOL, LLC.
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
package com.jkoolcloud.tnt4j.filters;

import java.util.Map;
import java.util.regex.Pattern;

import com.jkoolcloud.tnt4j.core.Snapshot;
import com.jkoolcloud.tnt4j.core.TTL;
import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.config.Configurable;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.sink.SinkEventFilter;
import com.jkoolcloud.tnt4j.tracker.TrackingActivity;
import com.jkoolcloud.tnt4j.tracker.TrackingEvent;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * <p>
 * This class implements a simple event filter based on severity level threshold & time performance such as elapsed,
 * wait/wall times and message pattern (regex). Use this class to filter out events/messages based time/level combination.
 * A given severity must be greater than or equal to the given level threshold to pass this filter. 
 * A given activity must be greater or equal to the given elapsed/wait/wall time. 
 * Set time objectives to -1 to disable time based filtering.
 * </p>
 * 
 * @see OpLevel
 * @see EventSink
 * @see SinkEventFilter
 * @see Configurable
 * 
 * @version $Revision: 1 $
 * 
 */
public class EventLevelTimeFilter implements SinkEventFilter, Configurable {
	public static final String LEVEL = "Level";
	public static final String ELAPSED_USEC = "ElapsedUsec";
	public static final String WAIT_USEC = "WaitUsec";
	public static final String WALL_USEC = "WallUsec";
	public static final String TTL_SEC = "TTL";
	public static final String MSG_PATTERN = "MsgRegex";
	
	OpLevel sevLimit;
	Pattern msgPattern;
	String msgRegx = null;
	long elapsedUsec = -1;
	long waitUsec = -1;
	long wallUsec = -1;
	private long ttl = TTL.TTL_CONTEXT;
	Map<String, Object> config = null;

	/**
	 * Create a default filter with <code>OpLevel.INFO</code> as default threshold.
	 * 
	 */
	public EventLevelTimeFilter() {
		sevLimit = OpLevel.INFO;
	}

	/**
	 * Create a default filter with a given level threshold.
	 * 
	 * @param threshold
	 *            severity level threshold
	 * @param elapsedUsc
	 *            elapsed time threshold (-1 disable)
	 * @param waitUsc
	 *            wait time threshold (-1 disable)
	 * @param wallUsc
	 *            wall time threshold (-1 disable)
	 */
	public EventLevelTimeFilter(OpLevel threshold, long elapsedUsc, long waitUsc, long wallUsc) {
		this(threshold, elapsedUsc, waitUsc, wallUsc, null);
	}

	/**
	 * Create a default filter with a given level threshold.
	 * 
	 * @param threshold
	 *            severity level threshold
	 * @param elapsedUsc
	 *            elapsed time threshold (-1 disable)
	 * @param waitUsc
	 *            wait time threshold (-1 disable)
	 * @param wallUsc
	 *            wall time threshold (-1 disable)
	 * @param msgRegex
	 *            message regex (null means all)
	 */
	public EventLevelTimeFilter(OpLevel threshold, long elapsedUsc, long waitUsc, long wallUsc, String msgRegex) {
		sevLimit = threshold;
		elapsedUsec = elapsedUsc;
		waitUsec = waitUsc;
		wallUsec = wallUsc;
		msgRegx = msgRegex;
		if (msgRegx != null) {
			msgPattern = Pattern.compile(msgRegx);
		}
	}

	@Override
	public boolean filter(EventSink sink, TrackingEvent event) {
		if (elapsedUsec >= 0 && event.getOperation().getElapsedTimeUsec() < elapsedUsec) {
			return false;
		}
		if (waitUsec >= 0 && event.getOperation().getWaitTimeUsec() < waitUsec) {
			return false;
		}
		if (msgPattern != null && !msgPattern.matcher(event.getMessagePattern()).matches()) {
			return false;
		}
		if (ttl != TTL.TTL_CONTEXT) event.setTTL(ttl);
		return (event.getSeverity().ordinal() >= sevLimit.ordinal()) && sink.isSet(event.getSeverity());
	}

	@Override
	public boolean filter(EventSink sink, TrackingActivity activity) {
		if (elapsedUsec >= 0 && activity.getElapsedTimeUsec() < elapsedUsec) {
			return false;
		}
		if (waitUsec >= 0 && activity.getWaitTimeUsec() < waitUsec) {
			return false;
		}
		if (wallUsec >= 0 && activity.getWallTimeUsec() < wallUsec) {
			return false;
		}
		if (ttl != TTL.TTL_CONTEXT) activity.setTTL(ttl);
		return (activity.getSeverity().ordinal() >= sevLimit.ordinal()) && sink.isSet(activity.getSeverity());
	}

	@Override
	public boolean filter(EventSink sink, Snapshot snapshot) {
		if (ttl != TTL.TTL_CONTEXT) snapshot.setTTL(ttl);
		return (snapshot.getSeverity().ordinal() >= sevLimit.ordinal()) && sink.isSet(snapshot.getSeverity());
	}

	@Override
	public boolean filter(EventSink sink, long ttl, Source source, OpLevel level, String msg, Object... args) {
		if (msgPattern != null && !msgPattern.matcher(msg).matches()) {
			return false;
		}
		return (level.ordinal() >= sevLimit.ordinal()) && sink.isSet(level);
	}

	@Override
	public Map<String, Object> getConfiguration() {
		return config;
	}

	@Override
	public void setConfiguration(Map<String, Object> settings) {
		config = settings;
		sevLimit = OpLevel.valueOf(Utils.getString(LEVEL, settings, sevLimit.toString()));
		elapsedUsec = Utils.getLong(ELAPSED_USEC, settings, elapsedUsec);
		waitUsec = Utils.getLong(WAIT_USEC, settings, waitUsec);
		wallUsec = Utils.getLong(WALL_USEC, settings, wallUsec);
		ttl = Utils.getLong(TTL_SEC, settings, ttl);;

		msgRegx = Utils.getString(MSG_PATTERN, settings, null);
		if (msgRegx != null) {
			msgPattern = Pattern.compile(msgRegx);
		}
	}
}
