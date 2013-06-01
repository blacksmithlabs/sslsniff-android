package com.blacksmithlabs.sslsniff_android;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Created by brian on 5/17/13.
 */
public class MainActivity extends Activity implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

	private ListView listview = null;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			/* enable hardware acceleration on Android >= 3.0 */
			final int FLAG_HARDWARE_ACCELERATED = WindowManager.LayoutParams.class.getDeclaredField("FLAG_HARDWARE_ACCELERATED").getInt(null);
			getWindow().setFlags(FLAG_HARDWARE_ACCELERATED, FLAG_HARDWARE_ACCELERATED);
		} catch (Exception e) {
		}

		setContentView(R.layout.main);
		Api.assertDependencies(this, true);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!Api.hasRootAccess(this, false)) {
			Api.alert(this, getResources().getString(R.string.root_required));
			finish();
		}

		if (Api.isEnabled(this)) {
			LogActivity.LogOptions options = Api.restoreLogOptions(this);
			if (options != null && options.app != null) {
				startLog(options);
				finish();
				return;
			}
		}

		if (this.listview == null) {
			this.listview = (ListView) this.findViewById(R.id.listview);
			this.listview.setOnItemClickListener(this);
			this.listview.setOnItemLongClickListener(this);
		}

		refreshHeader();
		showOrLoadApplications();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (this.listview != null) {
			this.listview.setAdapter(null);
		}
	}

	private void refreshHeader() {
		// TODO determine if running and update mode accordingly
	}

	protected void startLog(LogActivity.LogOptions options) {
		Intent intent = new Intent(this, LogActivity.class);
		intent.putExtra(LogActivity.OPTIONS_EXTRA, options);
		startActivity(intent);
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
		view.setSelected(true);

		ListEntry entry = (ListEntry)view.getTag();

		LogActivity.LogOptions options = new LogActivity.LogOptions();
		options.app = entry.app;
		options.mode = LogActivity.SniffMode.AUTHORITY;
		options.certInfo = null; // default cert
		options.logFile = null; // default file

		Log.d("sslsniff-android", "Sniffing App: " + entry.app.toString());

		startLog(options);
		finish();
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
		// TODO show advanced sniffing options
		return false;
	}

	private void showOrLoadApplications() {
		if (Api.applications == null) {
			// Load the apps and display the progress dialog
			final Resources res = getResources();
			final ProgressDialog progress = ProgressDialog.show(this, res.getString(R.string.working), res.getString(R.string.reading_apps), true);
			new Handler() {
				@Override
				public void handleMessage(Message msg) {
					Api.getApps(MainActivity.this);
					try { progress.dismiss(); } catch (Exception ex) {}
					showApplications();
				}
			}.sendEmptyMessageDelayed(0, 100);
		} else {
			// Already cached, just show the list
			showApplications();
		}
	}

	private void showApplications() {
		final Api.DroidApp[] apps = Api.getApps(this);
		if (apps == null) {
			Api.alert(this, getString(R.string.error_reading_apps));
			return;
		}
		// Sort applications - selected first, then alphabetical
		Arrays.sort(apps, new Comparator<Api.DroidApp>() {
			@Override
			public int compare(Api.DroidApp o1, Api.DroidApp o2) {
				if (o1.firstseen != o2.firstseen) {
					return (o1.firstseen ? -1 : 1);
				}
				if (o1.selected == o2.selected) {
					return String.CASE_INSENSITIVE_ORDER.compare(o1.names[0], o2.names[0]);
				}
				if (o1.selected)
					return -1;
				return 1;
			}
		});

		final LayoutInflater inflater = getLayoutInflater();
		final ListAdapter adapter = new ArrayAdapter<Api.DroidApp>(this, R.layout.listitem, R.id.itemtext, apps) {
			@Override
			public View getView(final int position, View convertView, ViewGroup parent) {
				ListEntry entry;
				if (convertView == null || (entry = (ListEntry) convertView.getTag()) == null) {
					convertView = inflater.inflate(R.layout.listitem, parent, false);
					Log.d("sslsniff-android", ">> inflate(" + convertView + ")");
					entry = new ListEntry();
					entry.text = (TextView)convertView.findViewById(R.id.itemtext);
					entry.icon = (ImageView)convertView.findViewById(R.id.itemicon);
					convertView.setTag(entry);
				}

				final Api.DroidApp app = apps[position];
				entry.app = app;
				entry.text.setText(app.toString());
				entry.icon.setImageDrawable(app.cached_icon);
				if (!app.icon_loaded && app.appinfo != null) {
					// Load the icon is a separate thread
					new LoadIconTask().execute(app, getPackageManager(), convertView);
				}

				convertView.setSelected(app.selected);

				return convertView;
			}
		};
		this.listview.setAdapter(adapter);
	}

	private static class LoadIconTask extends AsyncTask<Object, Void, View> {
		@Override
		protected View doInBackground(Object... objects) {
			try {
				final Api.DroidApp app = (Api.DroidApp)objects[0];
				final PackageManager pm = (PackageManager)objects[1];
				final View viewToUpdate = (View)objects[2];
				if (!app.icon_loaded) {
					app.cached_icon = pm.getApplicationIcon(app.appinfo);
					app.icon_loaded = true;
				}
				// Return the view to update an "onPostExecute"
				// Note we cannot be sure that this view still references "app"
				return viewToUpdate;
			} catch (Exception ex) {
				Log.e("sslsniff-android", "Error loading icon", ex);
				return null;
			}
		}

		@Override
		protected void onPostExecute(View view) {
			try {
				// This is executed in the UI thread, so it is safe to use viewToUpdate.getTag() and modify the UI
				final ListEntry entryToUpdate = (ListEntry) view.getTag();
				entryToUpdate.icon.setImageDrawable(entryToUpdate.app.cached_icon);
			} catch (Exception ex) {
				Log.e("sslsniff-android", "Error showing icon", ex);
			}
		}
	}

	/**
	 * Entry representing an application in the screen
	 */
	private static class ListEntry {
		private TextView text;
		private ImageView icon;
		private Api.DroidApp app;
	}

}