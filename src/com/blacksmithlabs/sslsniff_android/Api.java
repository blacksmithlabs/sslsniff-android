package com.blacksmithlabs.sslsniff_android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Created by brian on 5/17/13.
 */
public class Api {

    /** root script filename */
    public static final String SCRIPT_FILE = "sslsniff.sh";

    // Preference constants
    public static final String PREFS_NAME = "sslsniffPrefs";
    public static final String PREF_SELECTED_UIDS = "SelectedUids";

	// Cached applications
	public static DroidApp applications[] = null;
    // Dow we have root access?
    private static boolean hasroot = false;

	/**
	 * Copies a raw resource file, given its ID to the given location
	 * @param ctx context
	 * @param resid resource id
	 * @param file destination file
	 * @param mode file permissions (E.g.: "755")
	 * @throws java.io.IOException on error
	 * @throws InterruptedException when interrupted
	 */
	private static void copyRawFile(Context ctx, int resid, File file, String mode) throws IOException, InterruptedException {
		final String abspath = file.getAbsolutePath();
		// Write the iptables binary
		final FileOutputStream out = new FileOutputStream(file);
		final InputStream is = ctx.getResources().openRawResource(resid);
		byte buf[] = new byte[1024];
		int len;
		while ((len = is.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		out.close();
		is.close();
		// Change the permissions
		Runtime.getRuntime().exec("chmod "+mode+" "+abspath).waitFor();
	}

	/**
	 * Displays a simple alert box
	 * @param ctx context
	 * @param msg message
	 */
	public static void alert(Context ctx, CharSequence msg) {
		if (ctx != null) {
			new AlertDialog.Builder(ctx)
					.setNeutralButton(android.R.string.ok, null)
					.setMessage(msg)
					.show();
		}
	}

	/**
	 * Asserts that the binary files are installed in the cache directory.
	 * @param ctx context
	 * @param showErrors indicates if errors should be alerted
	 * @return false if binary files could not be installed
	 */
	public static boolean assertBinaries(Context ctx, boolean showErrors) {
		boolean changed = false;
		try {
			// Check sslsniff
			File file = new File(ctx.getDir("bin",0), "sslsniff_armv7");
			if (!file.exists() || file.length() != 6123164) {
				copyRawFile(ctx, R.raw.sslsniff_armv7, file, "755");
				changed = true;
			}
			// Check iptables
			file = new File(ctx.getDir("bin",0), "iptables_armv7");
			if (!file.exists() || file.length() != 1005680) {
				copyRawFile(ctx, R.raw.iptables_armv7, file, "755");
				changed = true;
			}
			// Check busybox
			file = new File(ctx.getDir("bin",0), "busybox_g1");
			if (!file.exists()) {
				copyRawFile(ctx, R.raw.busybox_g1, file, "755");
				changed = true;
			}

			if (changed) {
				Toast.makeText(ctx, R.string.toast_bin_installed, Toast.LENGTH_LONG).show();
			}
		} catch (Exception e) {
			if (showErrors)
				alert(ctx, "Error installing binary files: " + e);
			return false;
		}
		return true;
	}

	/**
	 * Get all the installed application
	 * @param ctx application context (mandatory)
	 * @return a list of applications
	 */
	public static DroidApp[] getApps(Context ctx) {
		// return cached instance if we have it
		if (applications != null) {
			return applications;
		}

		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
        // Allowed application names separated by pipe '|' (persisted)
        final String savedUids = prefs.getString(PREF_SELECTED_UIDS, "");
        final HashSet<Integer> selected = new HashSet<Integer>();
        if (!savedUids.isEmpty()) {
            final StringTokenizer tok = new StringTokenizer(savedUids, "|", false);
            while (tok.hasMoreTokens()) {
                final String uid = tok.nextToken();
                if (!uid.isEmpty()) {
                    try {
                        selected.add(Integer.parseInt(uid));
                    } catch (Exception ex) {
                        // That didn't work...
                    }
                }
            }
        }
        try {
            final PackageManager pkgManager = ctx.getPackageManager();
            final List<ApplicationInfo> installed = pkgManager.getInstalledApplications(0);
            final HashMap<Integer, DroidApp> map = new HashMap<Integer, DroidApp>();
            final SharedPreferences.Editor edit = prefs.edit();

            boolean changed = false;
            String name = null;
            String cachekey = null;
            DroidApp app = null;

            for (final ApplicationInfo apInfo : installed) {
                boolean firstseen = false;
                app = map.get(apInfo.uid);
                // Filter applications which are not allowed to access the Internet
                if (app == null && PackageManager.PERMISSION_GRANTED != pkgManager.checkPermission(android.Manifest.permission.INTERNET, apInfo.packageName)) {
                    continue;
                }

                // Try to get the application label from our cache since getApplicationLable is horribly slow
                cachekey = "cache.label."+apInfo.packageName;
                name = prefs.getString(cachekey, "");
                if (name.isEmpty()) {
                    // Get label and put in cache
                    name = pkgManager.getApplicationLabel(apInfo).toString();
                    edit.putString(cachekey, name);
                    changed = true;
                    firstseen = true;
                }

                if (app == null) {
                    app = new DroidApp();
                    app.uid = apInfo.uid;
                    app.names = new String[] { name };
                    app.appinfo = apInfo;
                    map.put(apInfo.uid, app);
                } else {
                    final String newnames[] = new String[app.names.length + 1];
                    System.arraycopy(app.names, 0, newnames, 0, app.names.length);
                    newnames[app.names.length] = name;
                    app.names = newnames;
                }
                app.firstseen = firstseen;

                // Check if this application is selected
                if (!app.selected && selected.contains(app.uid)) {
                    app.selected = true;
                }
            }

            if (changed) {
                edit.commit();
            }

            // Add special applications to the list
            final DroidApp special[] = {
                    // TODO add in kernel and other apps when we support them
            };
            for (int i=0; i<special.length; i++) {
                app = special[i];
                if (app.uid != -1 && !map.containsKey(app.uid)) {
                    // Is it selected?
                    if (selected.contains(app.uid)) {
                        app.selected = true;
                    }
                    map.put(app.uid, app);
                }
            }

            // Convert the map into an array
            applications = map.values().toArray(new DroidApp[map.size()]);
            return applications;
        } catch (Exception ex) {
            alert(ctx, "error: " + ex);
        }
        return null;
	}

	/**
	 * Small structure to hold an application info
	 */
	public static final class DroidApp {
		/** linux user id */
		int uid;
		/** application names belonging to this user id */
		String names[];
		/** indicates if this application is being monitored */
		boolean selected;
		/** toString cache */
		String tostr;
		/** application info */
		ApplicationInfo appinfo;
		/** cached application icon */
		Drawable cached_icon;
		/** indicates if the icon has been loaded already */
		boolean icon_loaded;
		/** first time seen? */
		boolean firstseen;

		public DroidApp() {
		}
		public DroidApp(int uid, String name, boolean selected) {
			this.uid = uid;
			this.names = new String[] {name};
			this.selected = selected;
		}
		/**
		 * Screen representation of this application
		 */
		@Override
		public String toString() {
			if (tostr == null) {
				final StringBuilder s = new StringBuilder();
				if (uid > 0) s.append(uid + ": ");
				for (int i=0; i<names.length; i++) {
					if (i != 0) s.append(", ");
					s.append(names[i]);
				}
				s.append("\n");
				tostr = s.toString();
			}
			return tostr;
		}
	}
}
