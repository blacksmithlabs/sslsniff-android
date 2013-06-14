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

		logMessageBuffer.put(logFile, new LogFileBuffer(100));

		LogReaderThread reader = new LogReaderThread(logFile);
		reader.start();

		readers.put(logFile, reader);
	}

	private HashMap<String, LogFileBuffer> logMessageBuffer = new HashMap<String, LogFileBuffer>();

	private void sendLogMessage(String logFile, String newMessage, int lineNumber) {
		final RemoteCallbackList<LogReaderCallback> callbacks = callbackHandlers.get(logFile);
		if (callbacks == null)
			return;

		// Broadcast to all clients the new value
		final int count = callbacks.beginBroadcast();

		LogFileLine logFileLine = new LogFileLine(lineNumber, newMessage);

		LogFileBuffer messages = logMessageBuffer.get(logFile);
		messages.add(logFileLine);

		if (count > 0) {
			for (int i=0; i<count; i++) {
				callbacks.getBroadcastItem(i).logMessage(logFileLine);
			}
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

	public static interface LogReaderCallback extends IInterface {
		/**
		 * Callback with the message most recently received from the log
		 * @param line
		 */
		public void logMessage(LogFileLine line);

		/**
		 * Callback for if an error is encountered or the log file is closed
		 * @param error the optional error message if one was encountered
		 */
		public void logClosed(String error);
	}

	public static class LogFileLine {
		final public int lineNumber;
		final public String lineText;

		public LogFileLine(int lineNumber, String lineText) {
			this.lineNumber = lineNumber;
			this.lineText = lineText;
		}
	}

	private static class LogFileBuffer extends Vector<LogFileLine> {
		final private int maxLines;
		final private int bufferPadding = 50;

		public LogFileBuffer(int maxLines) {
			super(maxLines);
			this.maxLines = maxLines > bufferPadding ? maxLines : bufferPadding*2;
		}

		protected void checkCapacity() {
			if (size() > maxLines) {
				removeRange(0, bufferPadding);
			}
		}

		@Override
		public void add(int location, LogFileLine object) {
			super.add(location, object);
			checkCapacity();
		}

		@Override
		public boolean add(LogFileLine object) {
			if (super.add(object)) {
				checkCapacity();
				return true;
			}
			return false;
		}

		@Override
		public synchronized boolean addAll(Collection<? extends LogFileLine> collection) {
			if (super.addAll(collection)) {
				checkCapacity();
				return true;
			}
			return false;
		}

		@Override
		public synchronized boolean addAll(int location, Collection<? extends LogFileLine> collection) {
			if (super.addAll(location, collection)) {
				checkCapacity();
				return true;
			}
			return false;
		}

		@Override
		public synchronized void addElement(LogFileLine object) {
			super.addElement(object);
			checkCapacity();
		}
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

		public List<LogFileLine> getNewLines(String logFile, int startLine) {
			final List<LogFileLine> lines = new LinkedList<LogFileLine>();

			final LogFileBuffer messages = logMessageBuffer.get(logFile);
			if (messages != null) {
				for (LogFileLine line : messages) {
					if (line.lineNumber > startLine) {
						lines.add(line);
					}
				}
			}

			return lines;
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
		final private String logFile;
		final private BufferedReader reader;
		private boolean keepReading = true;
		private int lineNumber = -1;

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
						lineNumber++;
						sendLogMessage(logFile, line, lineNumber);
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
