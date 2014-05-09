/*
 * Copyright (c) 2014 Nastel Technologies, Inc. All Rights Reserved.
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
package com.nastel.jkool.tnt4j.repository;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.AbstractFileConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.event.ConfigurationErrorEvent;
import org.apache.commons.configuration.event.ConfigurationErrorListener;
import org.apache.commons.configuration.event.ConfigurationEvent;
import org.apache.commons.configuration.event.ConfigurationListener;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;

import com.nastel.jkool.tnt4j.core.OpLevel;
import com.nastel.jkool.tnt4j.sink.DefaultEventSinkFactory;
import com.nastel.jkool.tnt4j.sink.EventSink;


/**
 * <p>This class implements a file based token repository based on a property file following
 * the key=value pairs defined per line. File is auto-reloaded by default based on 20sec refresh
 * time. The reload time can be changed by setting <code>tnt4j.file.respository.refresh</code> property</p>
 *
 * @see TokenRepository
 *
 * @version $Revision: 6 $
 *
 */

public class FileTokenRepository implements TokenRepository {
	private static EventSink logger = DefaultEventSinkFactory.defaultEventSink(FileTokenRepository.class);	
	private static ScheduledExecutorService reloadService = Executors.newScheduledThreadPool(Integer.getInteger("tnt4j.repository.file.reload.pool.size", 5));
	private static HashMap<TokenRepositoryListener, TokenConfigurationListener> LISTEN_MAP = new HashMap<TokenRepositoryListener, TokenConfigurationListener>(49);
	
	private String urlName = null;
	private PropertiesConfiguration config = null;
	private long refDelay = 20000;
	
	/**
	 * Create file/property based token repository instance based on default 
	 * file name or url specified by <code>tnt4j.token.repository</code> java
	 * property which should be found in specified properties.
	 * 
	 */
	public FileTokenRepository() {
		this(System.getProperty("tnt4j.token.repository", "tnt4j-tokens.properties"), 
				Long.getLong("tnt4j.file.respository.refresh", 20000));
	}

	/**
	 * Create file/property based token repository instance given 
	 * a specific filename or url. File name is autoreloaded based on 
	 * <code>tnt4j.file.respository.refresh</code> property which is set to 20000 (ms) 
	 * by default.
	 * 
	 * @param url file name or URL of the property file containing tokens
	 */
	public FileTokenRepository(String url, long refreshDelay) {
    	urlName = url;
    	refDelay = refreshDelay;
	}

	@Override
	public void addRepositoryListener(TokenRepositoryListener listener) {
		TokenConfigurationListener pListener = new TokenConfigurationListener(listener, logger);
		LISTEN_MAP.put(listener, pListener);
		config.addConfigurationListener(pListener);
		config.addErrorListener(pListener);
	}

	@Override
    public void removeRepositoryListener(TokenRepositoryListener listener) {
		TokenConfigurationListener pListener = LISTEN_MAP.get(listener);
		if (pListener != null) {
			LISTEN_MAP.remove(listener);
			config.removeConfigurationListener(pListener);
			config.removeErrorListener(pListener);
		}
	}
	
	@Override
    public Object get(String key) {
		return config.getProperty(key);
	}

	@Override
    public Iterator<String> getKeys() {
	    return config.getKeys();
    }

	@Override
    public void remove(String key) {
	    config.clearProperty(key);
    }

	@Override
    public void set(String key, Object value) {
	    config.setProperty(key, value);
    }

	@Override
    public String getName() {
	    return urlName;
    }

	@Override
	public String toString() {
		return super.toString() + "{url: " + getName() + ", delay: " + refDelay + ", config: " + config + "}";
	}

	@Override
    public boolean isOpen() {
	    return config != null;
    }

	@Override
    public void open() throws IOException {
		if (isOpen()) return;
        try {
         	int urlIndex = urlName.indexOf("://");
	        config = urlIndex > 0? new PropertiesConfiguration(new URL(urlName)): new PropertiesConfiguration(urlName);
	        if (refDelay > 0) {
	        	FileChangedReloadingStrategy reloadConfig = new FileChangedReloadingStrategy();
	        	reloadConfig.setRefreshDelay(refDelay);
	        	config.setReloadingStrategy(reloadConfig);	
	        	reloadService.scheduleAtFixedRate(new ReloadFileRepository(config), refDelay, refDelay, TimeUnit.MILLISECONDS);
	        }
        }  catch (IOException e) {
        	logger.log(OpLevel.ERROR, "Unable to open token repository url=" + urlName + ", reload.ms=" + refDelay, e);
			throw e;
        } catch (Throwable e) {
        	logger.log(OpLevel.ERROR, "Unable to open token repository url=" + urlName + ", reload.ms=" + refDelay, e);
        }	
    }

	@Override
    public void close() throws IOException {
	}
}

class ReloadFileRepository implements Runnable {
	PropertiesConfiguration fileConfiguration = null;
	
	ReloadFileRepository(PropertiesConfiguration config) {
		fileConfiguration = config;
	}
	
	@Override
    public void run() {
		fileConfiguration.getProperty("test.property");	    
    }
	
}

class TokenConfigurationListener implements ConfigurationListener, ConfigurationErrorListener{
	TokenRepositoryListener repListener = null;
	EventSink logger = null;
	
	public TokenConfigurationListener(TokenRepositoryListener listener, EventSink log) {
		repListener = listener;
		logger = log;
	}
	
	@Override
    public void configurationChanged(ConfigurationEvent event) {	
		if (event.isBeforeUpdate()) return;
		if (logger.isSet(OpLevel.DEBUG)) {
			logger.log(OpLevel.DEBUG, "configurationChanged{type: " + event.getType() 
					+ ", " + event.getPropertyName()
					+ ": " + event.getPropertyValue()
					+ "}");
		}
		switch (event.getType()) {
			case AbstractConfiguration.EVENT_ADD_PROPERTY:
				repListener.repositoryChanged(new TokenRepositoryEvent(event.getSource(), 
						TokenRepository.EVENT_ADD_KEY, event.getPropertyName(), event.getPropertyValue(), null));
				break;
			case AbstractConfiguration.EVENT_SET_PROPERTY:
				repListener.repositoryChanged(new TokenRepositoryEvent(event.getSource(), 
						TokenRepository.EVENT_SET_KEY, event.getPropertyName(), event.getPropertyValue(), null));
				break;
			case AbstractConfiguration.EVENT_CLEAR_PROPERTY:
				repListener.repositoryChanged(new TokenRepositoryEvent(event.getSource(), 
						TokenRepository.EVENT_CLEAR_KEY, event.getPropertyName(), event.getPropertyValue(), null));
				break;
			case AbstractConfiguration.EVENT_CLEAR:
				repListener.repositoryChanged(new TokenRepositoryEvent(event.getSource(), 
						TokenRepository.EVENT_CLEAR, event.getPropertyName(), event.getPropertyValue(), null));
				break;
			case AbstractFileConfiguration.EVENT_RELOAD:
				repListener.repositoryChanged(new TokenRepositoryEvent(event.getSource(), 
						TokenRepository.EVENT_RELOAD, event.getPropertyName(), event.getPropertyValue(), null));
				break;
		}
    }

	@Override
    public void configurationError(ConfigurationErrorEvent event) {
		logger.log(OpLevel.ERROR, "Configuration error detected, event=" + event, event.getCause());
		repListener.repositoryError(new TokenRepositoryEvent(event.getSource(), 
				TokenRepository.EVENT_EXCEPTION, event.getPropertyName(), event.getPropertyValue(), event.getCause()));
    }
	
}