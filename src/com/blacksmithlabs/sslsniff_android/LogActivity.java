package com.blacksmithlabs.sslsniff_android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
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

		if (savedInstanceState == null) {
			options = (LogOptions)getIntent().getParcelableExtra(OPTIONS_EXTRA);
		} else {
			options = savedInstanceState.getParcelable(OPTIONS_EXTRA);
		}

		if (options == null) {
			TextView txt = (TextView)findViewById(R.id.logtext);
			txt.setText(R.string.log_no_options);
		} else {
			Api.saveLogOptions(this, options);

			Api.applyIPTablesRules(this, options, true);
			// TODO call API to set up and start ssl-sniff for these rules
			// TODO start log tailing service (or something)
		}
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
		public SniffMode mode;

		public LogOptions() {}

		private LogOptions(Parcel parcel) {
			app = parcel.readParcelable(Api.DroidApp.class.getClassLoader());
			parcel.readIntArray(ports);
			logFile = parcel.readString();
			certInfo = parcel.readString();
			mode = SniffMode.valueOf(parcel.readString());
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel parcel, int flags) {
			parcel.writeParcelable(app, flags);
			parcel.writeIntArray(ports);
			parcel.writeString(logFile);
			parcel.writeString(certInfo);
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