/**
 * New BSD License
 * http://www.opensource.org/licenses/bsd-license.php
 * Copyright 2009-2016 RaptorProject (https://github.com/Raptor-Fics-Interface/Raptor)
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of the RaptorProject nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package raptor.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import raptor.Raptor;
import raptor.chat.ChatEvent;
import raptor.chat.ChatLogger;
import raptor.connector.Connector;
import raptor.connector.MessageCallback;
import raptor.pref.PreferenceKeys;

/**
 * A service which invokes chatEventOccured on added ChatListeners when a
 * ChatEvents arrive on a connector.
 */
public class ChatService {
	public static final Pattern TIMESTAMP_2_PING_REGEX = Pattern.compile("^Average ping time for.*\\sis\\s(\\d+)ms\\.");

	public static interface ChatListener {
		public void chatEventOccured(ChatEvent e);

		public boolean isHandling(ChatEvent e);

		public void pingArrived(long millis);
	}

	protected Connector connector = null;
	protected List<ChatListener> listeners = new ArrayList<ChatListener>(5);
	protected List<ChatListener> mainConsoleListeners = new ArrayList<ChatListener>(5);
	protected ChatLogger logger = null;

	/**
	 * Constructs a chat service for the specified connector.
	 * 
	 * @param connector
	 */
	public ChatService(Connector connector) {
		this.connector = null;
		logger = new ChatLogger(connector,
				Raptor.USER_RAPTOR_HOME_PATH + "/chatcache/" + connector.getShortName() + ".txt");
	}

	protected void setupPing() {
//		ThreadService.getInstance().
//			if (connector.isConnected() && connector.isTimesseal2On()) {
//				connector.invokeOnNextRegexMatch("^Average ping time for.*\\sis\\s(\\d+)ms\\.", new MessageCallback() {
//					@Override
//					public boolean matchReceived(ChatEvent event) {
//						String message = event.getMessage();
//						
//						Matcher matcher = TIMESTAMP_2_PING_REGEX.matcher(message);
//						
//						if (matcher.matches() && matcher.groupCount() > 0) {
//							long ping = Long.parseLong(matcher.group(1));
//							publishPingEvent(ping);
//							return false;
//						}
//						else {
//							return false;
//						}
//						
//					}
//				});
//				connector.sendMessage("ping", true);
//			}
//		}
	}

	/**
	 * Adds a ChatServiceListener to the chat service. Please remove the
	 * listener when you no longer need the ChatService to avoid memory leaks.
	 */
	public void addChatServiceListener(ChatListener listener) {
		listeners.add(listener);
	}

	public void addMainConsoleListener(ChatListener listener) {
		mainConsoleListeners.add(listener);
	}

	/**
	 * Disposes all resources the ChatService is using.
	 */
	public void dispose() {
		listeners.clear();
		if (logger != null) {
			logger.delete();
		}
		listeners = null;
		logger = null;
		connector = null;
	}

	/**
	 * Returns the Chat Services Chat Logger.
	 */
	public ChatLogger getChatLogger() {
		return logger;
	}

	/**
	 * Returns the Connector backing this ChatService.
	 */
	public Connector getConnector() {
		return connector;
	}

	/**
	 * Chat events are published asynchronously.
	 */
	public void publishChatEvent(final ChatEvent event) {
		ThreadService.getInstance().run(new Runnable() {
			public void run() {
				if (listeners == null) {
					return;
				}
				boolean wasHandled = false;
				for (ChatListener listener : listeners) {
					if (listener.isHandling(event)) {
						listener.chatEventOccured(event);
						wasHandled = true;
					}
				}

				if (!wasHandled || !Raptor.getInstance().getPreferences()
						.getBoolean(PreferenceKeys.CHAT_REMOVE_SUB_TAB_MESSAGES_FROM_MAIN_TAB)) {
					for (ChatListener listener : mainConsoleListeners) {
						if (listener.isHandling(event)) {
							listener.chatEventOccured(event);
							wasHandled = true;
						}
					}
				}
				logger.write(event);
			}
		});
	}

	/**
	 * Chat events are published asynchronously.
	 */
	public void publishPingEvent(final long pingMillis) {
		ThreadService.getInstance().run(new Runnable() {
			public void run() {
				if (listeners == null) {
					return;
				}
				for (ChatListener listener : listeners) {
					listener.pingArrived(pingMillis);
				}
			}
		});
	}

	/**
	 * Removes a listener from the ChatService.
	 */
	public void removeChatServiceListener(ChatListener listener) {
		listeners.remove(listener);
		mainConsoleListeners.remove(listener);
	}

}
