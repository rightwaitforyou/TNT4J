/*
 * Copyright (c) 2013 Nastel Technologies, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of Nastel
 * Technologies, Inc. ("Confidential Information").  You shall not disclose
 * such Confidential Information and shall use it only in accordance with
 * the terms of the license agreement you entered into with Nastel
 * Technologies.
 *
 * NASTEL MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, OR NON-INFRINGEMENT. NASTEL SHALL NOT BE LIABLE FOR ANY DAMAGES
 * SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING
 * THIS SOFTWARE OR ITS DERIVATIVES.
 *
 *
 */
package com.nastel.jkool.tnt4j.selector;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import com.nastel.jkool.tnt4j.config.Configurable;
import com.nastel.jkool.tnt4j.core.OpLevel;
import com.nastel.jkool.tnt4j.repository.TokenRepository;
import com.nastel.jkool.tnt4j.repository.TokenRepositoryEvent;
import com.nastel.jkool.tnt4j.repository.TokenRepositoryListener;
import com.nastel.jkool.tnt4j.sink.DefaultEventSinkFactory;
import com.nastel.jkool.tnt4j.sink.EventSink;
import com.nastel.jkool.tnt4j.utils.Utils;

/**
 * <p>
 * <code>DefaultTrackingSelector</code> implements <code>TrackingSelector</code> interface and provides default file
 * based implementation for a tracking selector. Selector file should contain entries as follows:
 * 
 * <code>key=SEV:value-regexp</code> Example (trace all severities, all orders):
 * <code>OrderApp.purchasing.order.id=DEBUG:.*</code>
 * 
 * @see OpLevel
 * 
 * @version $Revision: 7 $
 * 
 */
public class DefaultTrackingSelector implements TrackingSelector, Configurable {
	private static EventSink logger = DefaultEventSinkFactory.defaultEventSink(DefaultTrackingSelector.class);
	private HashMap<Object, TntToken> tokenMap = new HashMap<Object, TntToken>(111);
	private Map<String, Object> config = null;
	private TokenRepository tokenRepository = null;
	private PropertyListener listener = null;

	/**
	 * Create a default tracking selector. Each selector needs to be backed by a repository <code>TokenRepository</code>
	 * .
	 * 
	 */
	public DefaultTrackingSelector() {
	}

	/**
	 * Create a default tracking selector. Each selector needs to be backed by a repository <code>TokenRepository</code>
	 * .
	 * 
	 * @param repository
	 *            token repository implementation
	 */
	public DefaultTrackingSelector(TokenRepository repository) {
		setRepository(repository);
	}

	@Override
	public boolean isOpen() {
		return tokenRepository.isOpen();
	}

	@Override
	public void open() throws IOException {
		try {
			tokenRepository.open();
			listener = new PropertyListener(this, logger);
			tokenRepository.addRepositoryListener(listener);
			reloadConfig();
		} catch (IOException e) {
			logger.log(OpLevel.ERROR, "Unable to load repository=" + tokenRepository, e);
			throw e;
		}
	}

	@Override
	public void close() throws IOException {
		clear();
		if (tokenRepository != null) {
			tokenRepository.removeRepositoryListener(listener);
			Utils.close(tokenRepository);
		}
	}

	protected void reloadConfig() {
		clear();
		Iterator<String> keys = tokenRepository.getKeys();
		while (keys.hasNext()) {
			String key = keys.next();
			putKey(key, tokenRepository.get(key).toString());
		}
	}

	protected void putKey(Object key, Object val) {
		String value = String.valueOf(val);
		int index = value.indexOf(":");
		try {
			TntToken tntToken = null;
			if (index > 0) {
				// token consists of sev:reg-exp pair
				String sevValue = value.substring(0, index);
				String valuePattern = value.substring(index + 1);
				OpLevel sevLimit = OpLevel.valueOf(sevValue.toUpperCase());
				tntToken = new TntToken(key, value, sevLimit, Pattern.compile(valuePattern));
			} else {
				// token only has severity limit specified
				String sevValue = value.trim();
				if (sevValue.length() > 0) {
					OpLevel sevLimit = OpLevel.valueOf(sevValue.toUpperCase());
					tntToken = new TntToken(key, value, sevLimit, null);
				}
			}
			if (tntToken != null) {
				if (logger.isSet(OpLevel.DEBUG)) {
					logger.log(OpLevel.DEBUG, 
							"putkey: repository=" + tokenRepository 
							+ ", token=" + tntToken);
				}
				tokenMap.put(key, tntToken);
			}
		} catch (Throwable ex) {
			logger.log(OpLevel.ERROR, 
					"Failed to process key=" + key 
					+ ", value=" + value 
					+ ", repository=" + tokenRepository, ex);
		}
	}

	@Override
    public boolean isSet(OpLevel sev, Object key) {
	    return isSet(sev, key, null);
    }

	@Override
	public boolean isSet(OpLevel sev, Object key, Object value) {
		boolean match = false;
		if (tokenRepository == null)
			return match;

		TntToken token = tokenMap.get(key);
		if (token != null) {
			boolean sevMatch = (sev.ordinal() >= token.sevLimit.ordinal());
			match = sevMatch
			        && ((value != null && token.valuePatten != null)? token.valuePatten.matcher(value.toString()).matches(): true);
		}
		return match;
	}

	@Override
	public void remove(Object key) {
		tokenMap.remove(key);
	}

	@Override
	public Object get(Object key) {
		TntToken token = tokenMap.get(key);
		return token != null ? token.getValue() : null;
	}

	@Override
	public void set(OpLevel sev, Object key, Object value) {
		putKey(key, value != null ? sev.toString() + ":" + value : sev.toString());
	}

	@Override
	public void set(OpLevel sev, Object key) {
		set(sev, key, null);
	}

	@Override
	public TokenRepository getRepository() {
		return tokenRepository;
	}

	protected void clear() {
		tokenMap.clear();
	}

	@Override
	public void setRepository(TokenRepository repo) {
		Utils.close(this);
		tokenRepository = repo;
	}

	@Override
	public Map<String, Object> getConfiguration() {
		return config;
	}

	@Override
	public void setConfiguration(Map<String, Object> props) {
		config = props;
		try {
			Object obj = Utils.createConfigurableObject("Repository", "Repository.", config);
			setRepository((TokenRepository) obj);
		} catch (Throwable e) {
			logger.log(OpLevel.ERROR, "Unable to process settings=" + props, e);
		}
	}
}

class PropertyListener implements TokenRepositoryListener {
	DefaultTrackingSelector selector = null;
	EventSink logger = null;

	public PropertyListener(DefaultTrackingSelector instance, EventSink log) {
		selector = instance;
		logger = log;
	}

	@Override
	public void repositoryError(TokenRepositoryEvent event) {
		logger.log(OpLevel.ERROR, "Repository error detected, event=" + event, event.getCause());
	}

	@Override
	public void repositoryChanged(TokenRepositoryEvent event) {
		if (logger.isSet(OpLevel.DEBUG)) {
			logger.log(OpLevel.DEBUG, "repositoryChanged {source: " + event.getSource() 
					+ ", type: " + event.getType()
			        + ", " + event.getKey() 
			        + ": " + event.getValue()
			        + "}");
		}
		switch (event.getType()) {
		case TokenRepository.EVENT_ADD_KEY:
		case TokenRepository.EVENT_SET_KEY:
			selector.putKey(event.getKey(), event.getValue());
			break;
		case TokenRepository.EVENT_CLEAR_KEY:
			selector.remove(event.getKey());
			break;
		case TokenRepository.EVENT_CLEAR:
			selector.clear();
			break;
		case TokenRepository.EVENT_RELOAD:
			selector.reloadConfig();
			break;
		case TokenRepository.EVENT_EXCEPTION:
			logger.log(OpLevel.ERROR, "Repository error detected, event=" + event, event.getCause());
			break;
		}
	}
}

class TntToken {
	Object key;
	String value;
	OpLevel sevLimit;
	Pattern valuePatten;

	public TntToken(Object k, String v, OpLevel sev, Pattern val) {
		value = v;
		key = k;
		sevLimit = sev;
		valuePatten = val;
	}

	public String getValue() {
		return value;
	}

	public String toString() {
		return "Token{" + key + ": " + value + ", sev.limit: " + sevLimit + ", pattern: " + valuePatten + "}";
	}
}