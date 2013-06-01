package com.blacksmithlabs.sslsniff_android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.*;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Created by brian on 5/27/13.
 */
public class LogActivity extends Activity {
	public static String OPTIONS_EXTRA = "com.blacksmithlabs.sslsniff_android.LogActivity.options";

	private LogOptions options;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			/* enable hardware acceleration on Android >= 3.0 */
			final int FLAG_HARDWARE_ACCELERATED = WindowManager.LayoutParams.class.getDeclaredField("FLAG_HARDWARE_ACCELERATED").getInt(null);
			getWindow().setFlags(FLAG_HARDWARE_ACCELERATED, FLAG_HARDWARE_ACCELERATED);
		} catch (Exception e) {
		}

		setContentView(R.layout.log);

		if (options == null) {
			if (savedInstanceState == null) {
				options = (LogOptions)getIntent().getParcelableExtra(OPTIONS_EXTRA);
			} else {
				options = savedInstanceState.getParcelable(OPTIONS_EXTRA);
			}
		}

		if (options != null) {
			Api.saveLogOptions(this, options);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		startMITM();
	}

	@Override
	public void onBackPressed() {
		promptExit();
	}

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
			if (!Api.hasRootAccess(this, false)) {
				txt.setText(R.string.root_required);
				return;
			}

			final Resources res = getResources();
			final ProgressDialog progress = ProgressDialog.show(this, res.getString(R.string.working), res.getString(R.string.starting_mitm), true);
			new Handler() {
				@Override
				public void handleMessage(Message msg) {
					Api.SSLMITM(LogActivity.this, options, true);

					try { progress.dismiss(); } catch (Exception ex) {}

					String pid = Api.getSnifferPID(LogActivity.this);
					if (pid == null || pid.isEmpty()) {
						txt.setText(R.string.error_starting_sslsniff);
					} else {
						txt.setText("sslsniff-android started as pid " + pid + "\n");
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
		// TODO start/verify log tailing service (or something)
		// TODO refresh log display with most recent output
	}

	@Override
	protected void onPause() {
		if (isFinishing()) {
			Api.killMITM(this, false);
		}
		super.onPause();
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