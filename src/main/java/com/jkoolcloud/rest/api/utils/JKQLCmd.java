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
package com.jkoolcloud.rest.api.utils;

import java.util.concurrent.TimeUnit;

import com.jkoolcloud.rest.api.service.JKQueryAsync;
import com.jkoolcloud.rest.api.service.JKQueryHandle;
import com.jkoolcloud.rest.api.service.JKRetryConnectionHandler;
import com.jkoolcloud.rest.api.service.JKTraceConnectionHandler;
import com.jkoolcloud.rest.api.service.JKTraceQueryCallback;

public class JKQLCmd {
	public static void main(String[] args) {
		JKCmdOptions options = new JKCmdOptions(JKQLCmd.class, args);
		if (options.usage != null) {
			System.out.println(options.usage);
			System.exit(-1);
		}
		options.print();
		JKTraceQueryCallback callback = new JKTraceQueryCallback(System.out, options.json_path, options.trace);
		try {
			
			// setup jKool WebSocket connection and connect
			JKQueryAsync jkQueryAsync = new JKQueryAsync(
					System.getProperty("jk.ws.uri", options.uri),
					System.getProperty("jk.access.token", options.token));
			if (options.retryTimeMs > 0) {
				jkQueryAsync.addConnectionHandler(new JKRetryConnectionHandler(options.retryTimeMs, TimeUnit.MILLISECONDS));
			}
			jkQueryAsync.addConnectionHandler(new JKTraceConnectionHandler(System.out, options.trace));
			jkQueryAsync.addDefaultCallbackHandler(callback);
			jkQueryAsync.connect();
			
			// run query in async mode with a callback
			JKQueryHandle qhandle = jkQueryAsync.callAsync(options.query, options.maxRows, new JKTraceQueryCallback(System.out, options.json_path, options.trace));
			System.out.println("Submitted query=\"" + qhandle.getQuery() + "\", id=" + qhandle.getId());
			if (!qhandle.isSubscribeQuery()) {
				// standard query only one response expected
				qhandle.awaitOnDead(options.waitTimeMs, TimeUnit.MILLISECONDS);
			} else {
				// streaming query, so lets collect responses until timeout
				Thread.sleep(options.waitTimeMs);
				System.out.println("Cancelling query=\"" + qhandle.getQuery() + "\", id=" + qhandle.getId());
				qhandle = jkQueryAsync.cancelAsync(qhandle);
				if (qhandle != null) {
					qhandle.awaitOnDead(options.waitTimeMs, TimeUnit.MILLISECONDS);
				}
			}
			jkQueryAsync.close();
		} catch (Throwable e) {
			System.err.println("Failed to execute: " + options.toString());
			e.printStackTrace();
		} finally {
			System.out.println("Stats: msg.recvd=" + callback.getMsgCount() + ", err.count=" + callback.getErrorCount());
		}
	}
}
