package com.blacksmithlabs.sslsniff_android;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;

import java.nio.channels.AsynchronousCloseException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Created by brian on 5/17/13.
 */
public class MainActivity extends Activity implements CompoundButton.OnCheckedChangeListener, View.OnClickListener {

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
		Api.assertBinaries(this, true);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (this.listview == null) {
			this.listview = (ListView) this.findViewById(R.id.listview);
		}

		refreshHeader();
		showOrLoadApplications();
	}

	@Override
	protected void onPause() {
		super.onPause();
		this.listview.setAdapter(null);
	}

	private void refreshHeader() {
		// TODO determine if running and update mode accordingly
	}

	private void showOrLoadApplications() {
		final Resources res = getResources();
		if (Api.applications == null) {
			// Load the apps and display the progress dialog
			final ProgressDialog progress = ProgressDialog.show(this, res.getString(R.string.working), res.getString(R.string.reading_apps), true);
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected void onPostExecute(Void aVoid) {
					try { progress.dismiss(); } catch (Exception ex) {}
					showApplications();
				}

				@Override
				protected Void doInBackground(Void... voids) {
					Api.getApps(MainActivity.this);
					return null;
				}
			}.execute();
		} else {
            // Already cached, just show the list
            showApplications();
        }
	}

	private void showApplications() {
		final Api.DroidApp[] apps = Api.getApps(this);
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

				/*
				if (app.selected) {
					convertView.setBackgroundColor(Color.LTGRAY);
					entry.text.setTextColor(Color.DKGRAY);
				} else {
					convertView.setBackgroundColor(Color.DKGRAY);
					entry.text.setTextColor(Color.LTGRAY);
				}
				*/

				return convertView;
			}
		};
		this.listview.setAdapter(adapter);
	}

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

	}

	@Override
	public void onClick(View view) {

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