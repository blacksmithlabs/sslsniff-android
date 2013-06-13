package com.blacksmithlabs.sslsniff_android.service;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import android.widget.Toast;

import java.io.*;
import java.util.*;

/**
 * Created by brian on 6/12/13.
 */
public class LogReader extends Service {
	public IBinder onBind(Intent intent) {
		if (LogReader.class.getName().equals(intent.getAction())) {
			return binder;
		}

		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		// Unregister all the callbacks
		for (RemoteCallbackList<LogReaderCallback> callbacks : callbackHandlers.values()) {
			callbacks.kill();
		}

		// Kill our threads
		for (LogReaderThread reader : readers.values()) {
			reader.stopReading();
			reader.interrupt();
		}
	}

	private HashMap<String, LogReaderThread> readers = new HashMap<String, LogReaderThread>();

	protected void tailLog(String logFile) throws FileNotFoundException {
		if (readers.containsKey(logFile))
			return;

		logMessageBuffer.put(logFile, new ArrayList<String>());

		LogReaderThread reader = new LogReaderThread(logFile);
		reader.start();

		readers.put(logFile, reader);
	}

	private HashMap<String, ArrayList<String>> logMessageBuffer = new HashMap<String, ArrayList<String>>();

	private void sendLogMessage(String logFile, String newMessage) {
		final RemoteCallbackList<LogReaderCallback> callbacks = callbackHandlers.get(logFile);
		if (callbacks == null)
			return;

		// Broadcast to all clients the new value
		final int count = callbacks.beginBroadcast();

		ArrayList<String> messages = logMessageBuffer.get(logFile);
		messages.add(newMessage);

		if (count > 0) {
			Iterator<String> it = messages.iterator();
			String message = null;

			while (it.hasNext()) {
				message = it.next();

				for (int i=0; i<count; i++) {
					callbacks.getBroadcastItem(i).logMessage(message);
				}
			}

			messages.clear();
		}

		callbacks.finishBroadcast();
	}

	private void sendLogClosed(String logFile, String error) {
		final RemoteCallbackList<LogReaderCallback> callbacks = callbackHandlers.get(logFile);
		if (callbacks == null)
			return;

		// Broadcast to all clients the new value
		final int count = callbacks.beginBroadcast();

		if (count > 0) {
			for (int i=0; i<count; i++) {
				callbacks.getBroadcastItem(i).logClosed(error);
			}
		}

		callbacks.finishBroadcast();
	}

	public interface LogReaderCallback extends IInterface {
		/**
		 * Callback with the message most recently received from the log
		 * @param message
		 */
		public void logMessage(String message);

		/**
		 * Callback for if an error is encountered or the log file is closed
		 * @param error the optional error message if one was encountered
		 */
		public void logClosed(String error);
	}

	private LogReaderBinder binder = new LogReaderBinder();

	private HashMap<String, RemoteCallbackList<LogReaderCallback>> callbackHandlers
			= new HashMap<String, RemoteCallbackList<LogReaderCallback>>();

	public class LogReaderBinder extends Binder {
		public Set<String> getLogFiles() {
			return readers.keySet();
		}

		public boolean tailLog(String logFile, LogReaderCallback callback) {
			if (logFile == null || logFile.isEmpty())
				return false;

			if (!callbackHandlers.containsKey(logFile)) {
				callbackHandlers.put(logFile, new RemoteCallbackList<LogReaderCallback>());
				try {
					LogReader.this.tailLog(logFile);
				} catch (FileNotFoundException e) {
					return false;
				}
			}

			if (callback != null) {
				registerCallback(logFile, callback);
			}

			return true;
		}

		public boolean registerCallback(String logFile, LogReaderCallback cb) {
			if (cb != null && callbackHandlers.containsKey(logFile)) {
				callbackHandlers.get(logFile).register(cb);
				return true;
			}
			return false;
		}
	}


	private class LogReaderThread extends Thread {
		private String logFile;
		private BufferedReader reader;
		private boolean keepReading = true;

		public LogReaderThread(String logFile) throws FileNotFoundException {
			this.logFile = logFile;
			reader = new BufferedReader(new FileReader(new File(logFile)));
		}

		public void stopReading() {
			keepReading = false;
		}

		@Override
		public void run() {
			String line;
			while (keepReading) {
				try {
					line = reader.readLine();
					if (line == null) {
						// Wait a bit and try again
						Thread.sleep(1000);
					} else {
						sendLogMessage(logFile, line);
					}
				} catch (InterruptedException e) {
					// Continue on
				} catch (IOException e) {
					keepReading = false;
					sendLogClosed(logFile, e.getMessage());
				}
			}
		}
	}
}
