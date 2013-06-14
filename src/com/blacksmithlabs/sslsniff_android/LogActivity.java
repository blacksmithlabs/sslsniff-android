package com.blacksmithlabs.sslsniff_android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.*;
import android.content.res.Resources;
import android.os.*;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import com.blacksmithlabs.sslsniff_android.service.LogReader;

import java.io.FileDescriptor;
import java.util.List;

/**
 * Created by brian on 5/27/13.
 */
public class LogActivity extends Activity {
	final public static String OPTIONS_EXTRA = "com.blacksmithlabs.sslsniff_android.LogActivity.options";
	final protected static String LAST_LOG_LINE = "com.blacksmithlabs.sslsniff_android.LogActivity.lastLogLine";

	private LogOptions options;

	private LogReader.LogReaderBinder readerService;
	private int lastLogLine = -1;
	private boolean tailingLogs = false;

	private TextView logTextView;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			/* enable hardware acceleration on Android >= 3.0 */
			final int FLAG_HARDWARE_ACCELERATED = WindowManager.LayoutParams.class.getDeclaredField("FLAG_HARDWARE_ACCELERATED").getInt(null);
			getWindow().setFlags(FLAG_HARDWARE_ACCELERATED, FLAG_HARDWARE_ACCELERATED);
		} catch (Exception e) {
		}

		setContentView(R.layout.log);
		logTextView = (TextView)findViewById(R.id.logtext);

		if (options == null) {
			if (savedInstanceState == null) {
				options = (LogOptions)getIntent().getParcelableExtra(OPTIONS_EXTRA);
			} else {
				options = savedInstanceState.getParcelable(OPTIONS_EXTRA);
			}
		}

		if (options != null && savedInstanceState == null) {
			Api.saveLogOptions(this, options);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		startMITM();
	}

	@Override
	protected void onPause() {
		if (tailingLogs) {
			unbindService(readerConnection);
			tailingLogs = false;
		}
		if (isFinishing()) {
			Api.killMITM(this, false);
			// TODO move this to persistent notification
			stopService(new Intent(this, LogReader.class));
		}
		super.onPause();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelable(OPTIONS_EXTRA, options);
		outState.putInt(LAST_LOG_LINE, lastLogLine);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		options = savedInstanceState.getParcelable(OPTIONS_EXTRA);
		lastLogLine = savedInstanceState.getInt(LAST_LOG_LINE, -1);
	}

	@Override
	public void onBackPressed() {
		promptExit();
	}

	protected void appendText(String append) {
		appendText(append, true);
	}

	protected void appendText(String append, boolean newline) {
		if (newline && !append.endsWith("\n")) {
			append += "\n";
		}

		logTextView.append(append);
	}

	protected Handler logMessageHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			final Bundle msgData = msg.getData();
			final String text = msgData.getString("LogText", "");
			final boolean newline = msgData.getBoolean("NewLine", false);

			final int lineNumber = msgData.getInt("LineNumber", 0);
			final boolean force = msgData.getBoolean("Force", false);

			if (text != null && !text.isEmpty() && (lineNumber > lastLogLine || force)) {
				if (lineNumber - lastLogLine > 1) {
					appendText("...", true);
				}

				lastLogLine = lineNumber;
				appendText(text, newline);
			}
		}
	};

	/**
	 * Start the MITM attack, if it's already in progress, just tail the logs
	 */
	protected void startMITM() {
		if (options == null) {
			options = Api.restoreLogOptions(this);
		}

		final TextView txt = (TextView)findViewById(R.id.logtext);
		if (options == null) {
			txt.setText(R.string.log_no_options);
		} else if (!Api.isEnabled(this)) {
			final Resources res = getResources();

			if (!Api.hasRootAccess(this, false)) {
				appendText(res.getString(R.string.root_required));
				return;
			}


			final ProgressDialog progress = ProgressDialog.show(this, res.getString(R.string.working), res.getString(R.string.starting_mitm), true);
			new Handler() {
				@Override
				public void handleMessage(Message msg) {
					Api.SSLMITM(LogActivity.this, options, true);

					try { progress.dismiss(); } catch (Exception ex) {}

					final String pid = Api.getSnifferPID(LogActivity.this);
					if (pid == null || pid.isEmpty()) {
						appendText(res.getString(R.string.error_starting_sslsniff));
					} else {
						appendText("sslsniff-android started as pid " + pid);
						Log.d("sslsniff-android", "pid: " + pid);
						tailMITMLogs();
					}
				}
			}.sendEmptyMessageDelayed(0, 100);
		} else {
			tailMITMLogs();
		}
	}

	/**
	 * Tail the logs for the MITM attack
	 * The file paths can be found in the options
	 */
	protected void tailMITMLogs() {
		if (!tailingLogs) {
			startService(new Intent(this, LogReader.class));

			final Intent serviceIntent = new Intent(this, LogReader.class);
			serviceIntent.setAction(LogReader.class.getName());
			bindService(serviceIntent, readerConnection, Context.BIND_AUTO_CREATE);
			tailingLogs = true;
		}
	}

	/**
	 * Prompt the user and ask them if they actually want to exit
	 */
	protected void promptExit() {
		final AlertDialog.Builder prompt = new AlertDialog.Builder(this);

		prompt.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int which) {
				finish();

				startActivity(new Intent(LogActivity.this, MainActivity.class));
			}
		});

		prompt.setNegativeButton(android.R.string.no, null);

		prompt.setMessage(R.string.exit_log_prompt);
		prompt.setTitle(R.string.exit_log_title);
		prompt.show();
	}

	private LogServiceConnection readerConnection = new LogServiceConnection();
	private class LogServiceConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
			readerService = ((LogReader.LogReaderBinder)iBinder);

			final List<LogReader.LogFileLine> catchup = readerService.getNewLines(options.logFile, lastLogLine);
			for (LogReader.LogFileLine line : catchup) {
				sendLogMessage(line);
			}

			tailLog(options.logFile);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			readerService = null;
		}

		protected void sendLogMessage(LogReader.LogFileLine line) {
			final Message msg = new Message();
			msg.getData().putString("LogText", line.lineText);
			msg.getData().putInt("LineNumber", line.lineNumber);
			msg.getData().putBoolean("NewLine", true);
			logMessageHandler.sendMessage(msg);
		}

		protected void tailLog(String logFile) {
			readerService.tailLog(logFile, new LogReader.LogReaderCallback() {
				@Override
				public void logMessage(LogReader.LogFileLine line) {
					sendLogMessage(line);
				}

				@Override
				public void logClosed(String error) {
					if (error != null && !error.isEmpty()) {
						final Message msg = new Message();
						msg.getData().putString("LogText", "ERROR: " + error);
						msg.getData().putBoolean("NewLine", true);
						msg.getData().putBoolean("Force", true);
						logMessageHandler.sendMessage(msg);
					}
				}

				@Override
				public IBinder asBinder() {
					return logBinder;
				}
			});
		}
	};

	protected LogActivityBinder logBinder = new LogActivityBinder();
	protected class LogActivityBinder extends Binder {

	};

	/**
	 * The different modes we can use to sniff. AUTHORITY is default.
	 */
	public static enum SniffMode {
		AUTHORITY,
		TARGET,
	};

	/**
	 * Options for how we are going to log for a given application
	 */
	public static class LogOptions implements Parcelable {
		public Api.DroidApp app;
		public int[] ports = {443};
		public String logFile = "sslsniff.log";
		public String certInfo;
		public String outputFile;
		public SniffMode mode;

		public LogOptions() {}

		private LogOptions(Parcel parcel) {
			app = parcel.readParcelable(Api.DroidApp.class.getClassLoader());

			int portsLen = parcel.readInt();
			ports = new int[portsLen];
			parcel.readIntArray(ports);

			logFile = parcel.readString();
			certInfo = parcel.readString();
			outputFile = parcel.readString();
			mode = SniffMode.valueOf(parcel.readString());
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel parcel, int flags) {
			parcel.writeParcelable(app, flags);
			parcel.writeInt(ports.length);
			parcel.writeIntArray(ports);
			parcel.writeString(logFile);
			parcel.writeString(certInfo);
			parcel.writeString(outputFile);
			parcel.writeString(mode.name());
		}

		public static final Creator<LogOptions> CREATOR = new Creator<LogOptions>() {
			@Override
			public LogOptions createFromParcel(Parcel parcel) {
				return new LogOptions(parcel);
			}

			@Override
			public LogOptions[] newArray(int size) {
				return new LogOptions[size];
			}
		};
	};
}