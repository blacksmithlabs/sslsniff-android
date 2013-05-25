package com.blacksmithlabs.sslsniff_android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.Toast;

import java.io.*;
import java.util.*;

/**
 * Created by brian on 5/17/13.
 */
public class Api {

	/** root script filename */
	public static final String SCRIPT_FILE = "sslsniff.sh";

	// Preference constants
	public static final String PREFS_NAME = "sslsniffPrefs";
	public static final String PREF_SELECTED_UID = "SelectedUid";

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
	 * Create the generic shell script header used to determine which iptables binary to use.
	 * @param ctx context
	 * @return script header
	 */
	private static String scriptHeader(Context ctx) {
		final String dir = ctx.getDir("bin",0).getAbsolutePath();
		final String myiptables = dir + "/iptables_armv7";
		final String mysslsniff = dir + "/sslsniff_armv7";
		return "" +
				"IPTABLES=iptables\n" +
				"BUSYBOX=busybox\n" +
				"GREP=grep\n" +
				"ECHO=echo\n" +
				"# Try to find busybox\n" +
				"if " + dir + "/busybox_g1 --help >/dev/null 2>/dev/null ; then\n" +
				"	BUSYBOX="+dir+"/busybox_g1\n" +
				"	GREP=\"$BUSYBOX grep\"\n" +
				"	ECHO=\"$BUSYBOX echo\"\n" +
				"elif busybox --help >/dev/null 2>/dev/null ; then\n" +
				"	BUSYBOX=busybox\n" +
				"elif /system/xbin/busybox --help >/dev/null 2>/dev/null ; then\n" +
				"	BUSYBOX=/system/xbin/busybox\n" +
				"elif /system/bin/busybox --help >/dev/null 2>/dev/null ; then\n" +
				"	BUSYBOX=/system/bin/busybox\n" +
				"fi\n" +
				"# Try to find grep\n" +
				"if ! $ECHO 1 | $GREP -q 1 >/dev/null 2>/dev/null ; then\n" +
				"	if $ECHO 1 | $BUSYBOX grep -q 1 >/dev/null 2>/dev/null ; then\n" +
				"		GREP=\"$BUSYBOX grep\"\n" +
				"	fi\n" +
				"	# Grep is absolutely required\n" +
				"	if ! $ECHO 1 | $GREP -q 1 >/dev/null 2>/dev/null ; then\n" +
				"		$ECHO The grep command is required. DroidWall will not work.\n" +
				"		exit 1\n" +
				"	fi\n" +
				"fi\n" +
				"# Try to find iptables\n" +
				"if " + myiptables + " --version >/dev/null 2>/dev/null ; then\n" +
				"	IPTABLES="+myiptables+"\n" +
				"fi\n" +
				"# Try to find sslsniff\n" +
				"if " + mysslsniff + " --help >/dev/null 2>/dev/null; then \n" +
				"    SSLSNIFF="+mysslsniff+"\n" +
				"fi\n" +
				"";
	}

	/**
	 * Get the pid of the sslsniff app that we've been running
	 * @param ctx context
	 * @return The PID. Empty string if not found.
	 */
	private static String getSnifferPID(Context ctx) {
		String pid = "";

		final File pidFile = new File(ctx.getDir("run",Context.MODE_PRIVATE), "sslsniff_armv7.pid");
		if (pidFile.exists() && pidFile.length() > 0) {
			try {
				BufferedReader reader = new BufferedReader(new FileReader(pidFile));
				pid = reader.readLine().trim();
			} catch (Exception ex) {
				// Well, dang...
			}
		}

		return pid;
	}

	/**
	 * Get whether we are actively recording traffic or not
	 * @param ctx context
	 * @return boolean if we are or not
	 */
	public static boolean isEnabled(Context ctx) {
		final String pid = getSnifferPID(ctx);

		if (!pid.isEmpty()) {
			final StringBuilder script = new StringBuilder();
			try {
				script.append(scriptHeader(ctx));
				script.append("ps ").append(pid).append(" | $GREP ").append(pid);

				final StringBuilder result = new StringBuilder();
				int code = runScript(ctx, script.toString(), result);
				if (code == 0) {
					return true;
				}
				return !result.toString().isEmpty();
			} catch (Exception e) {
				// Well, that sucks
			}
		}

		return false;
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
	 * Check if we have root access
	 * @param ctx mandatory context
	 * @param showErrors indicates if errors should be alerted
	 * @return boolean true if we have root
	 */
	public static boolean hasRootAccess(final Context ctx, boolean showErrors) {
		if (hasroot) return true;
		final StringBuilder res = new StringBuilder();
		try {
			// Run an empty script just to check root access
			if (runScriptAsRoot(ctx, "exit 0", res) == 0) {
				hasroot = true;
				return true;
			}
		} catch (Exception e) {
		}
		if (showErrors) {
			alert(ctx, "Could not acquire root access.\n" +
					"You need a rooted phone to use sslsniff.\n\n" +
					"If this phone is already rooted, please make sure sslsniff has enough permissions to execute the \"su\" command.\n" +
					"Error message: " + res.toString());
		}
		return false;
	}

	/**
	 * Runs a script, wither as root or as a regular user (multiple commands separated by "\n").
	 * @param ctx mandatory context
	 * @param script the script to be executed
	 * @param res the script output response (stdout + stderr)
	 * @param timeout timeout in milliseconds (-1 for none)
	 * @return the script exit code
	 */
	public static int runScript(Context ctx, String script, StringBuilder res, long timeout, boolean asroot) {
		final File file = new File(ctx.getDir("bin",0), SCRIPT_FILE);
		final ScriptRunner runner = new ScriptRunner(file, script, res, asroot);
		runner.start();

		try {
			if (timeout > 0) {
				runner.join(timeout);
			} else {
				runner.join();
			}
			if (runner.isAlive()) {
				// Timed-out
				runner.interrupt();
				runner.join(150);
				runner.destroy();
				runner.join(50);
			}
		} catch (InterruptedException ex) {
		}

		return runner.exitcode;
	}
	/**
	 * Runs a script as root (multiple commands separated by "\n").
	 * @param ctx mandatory context
	 * @param script the script to be executed
	 * @param res the script output response (stdout + stderr)
	 * @param timeout timeout in milliseconds (-1 for none)
	 * @return the script exit code
	 */
	public static int runScriptAsRoot(Context ctx, String script, StringBuilder res, long timeout) {
		return runScript(ctx, script, res, timeout, true);
	}
	/**
	 * Runs a script as root (multiple commands separated by "\n") with a default timeout of 20 seconds.
	 * @param ctx mandatory context
	 * @param script the script to be executed
	 * @param res the script output response (stdout + stderr)
	 * @return the script exit code
	 * @throws IOException on any error executing the script, or writing it to disk
	 */
	public static int runScriptAsRoot(Context ctx, String script, StringBuilder res) throws IOException {
		return runScriptAsRoot(ctx, script, res, 40000);
	}
	/**
	 * Runs a script as a regular user (multiple commands separated by "\n") with a default timeout of 20 seconds.
	 * @param ctx mandatory context
	 * @param script the script to be executed
	 * @param res the script output response (stdout + stderr)
	 * @return the script exit code
	 * @throws IOException on any error executing the script, or writing it to disk
	 */
	public static int runScript(Context ctx, String script, StringBuilder res) throws IOException {
		return runScript(ctx, script, res, 40000, false);
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
		final String savedUid = prefs.getString(PREF_SELECTED_UID, "");
		final HashSet<Integer> selected = new HashSet<Integer>();
		if (!savedUid.isEmpty()) {
			try {
				selected.add(Integer.parseInt(savedUid));
			} catch (Exception ex) {
				// That didn't work...
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
			for (DroidApp dapp : special) {
				if (dapp.uid != -1 && !map.containsKey(dapp.uid)) {
					// Is it selected?
					if (selected.contains(dapp.uid)) {
						dapp.selected = true;
					}
					map.put(dapp.uid, dapp);
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
		/** toString cache */
		String tostr;
		/** whether we are monitoring this app or not */
		boolean selected;
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
				if (uid > 0) s.append(uid).append(": ");
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

	/**
	 * Internal thread used to execute scripts (as root or not).
	 */
	private static final class ScriptRunner extends Thread {
		private final File file;
		private final String script;
		private final StringBuilder res;
		private final boolean asroot;
		public int exitcode = -1;
		private Process exec;

		/**
		 * Creates a new script runner.
		 * @param file temporary script file
		 * @param script script to run
		 * @param res response output
		 * @param asroot if true, executes the script as root
		 */
		public ScriptRunner(File file, String script, StringBuilder res, boolean asroot) {
			this.file = file;
			this.script = script;
			this.res = res;
			this.asroot = asroot;
		}
		@Override
		public void run() {
			try {
				file.createNewFile();
				final String abspath = file.getAbsolutePath();
				// make sure we have execution permission on the script file
				Runtime.getRuntime().exec("chmod 777 "+abspath).waitFor();
				// Write the script to be executed
				final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file));
				if (new File("/system/bin/sh").exists()) {
					out.write("#!/system/bin/sh\n");
				}
				out.write(script);
				if (!script.endsWith("\n")) {
					out.write("\n");
				}
				out.write("exit\n");
				out.flush();
				out.close();
				if (this.asroot) {
					// Create the "su" request to run the script
					exec = Runtime.getRuntime().exec("su -c "+abspath);
				} else {
					// Create the "sh" request to run the script
					exec = Runtime.getRuntime().exec("sh "+abspath);
				}
				final InputStream stdout = exec.getInputStream();
				final InputStream stderr = exec.getErrorStream();
				final byte buf[] = new byte[8192];
				int read = 0;
				while (true) {
					final Process localexec = exec;
					if (localexec == null) {
						break;
					}
					try {
						// get the process exit code - will raise IllegalThreadStateException if still running
						this.exitcode = localexec.exitValue();
					} catch (IllegalThreadStateException ex) {
						// The process is still running
					}
					// Read stdout
					if (stdout.available() > 0) {
						read = stdout.read(buf);
						if (res != null)
							res.append(new String(buf, 0, read));
					}
					// Read stderr
					if (stderr.available() > 0) {
						read = stderr.read(buf);
						if (res != null)
							res.append(new String(buf, 0, read));
					}
					if (this.exitcode != -1) {
						// finished
						break;
					}
					// Sleep for the next round
					Thread.sleep(50);
				}
			} catch (InterruptedException ex) {
				if (res != null)
					res.append("\nOperation timed-out");
			} catch (Exception ex) {
				if (res != null)
					res.append("\n" + ex);
			} finally {
				destroy();
			}
		}
		/**
		 * Destroy this script runner
		 */
		public synchronized void destroy() {
			if (exec != null)
				exec.destroy();
			exec = null;
		}
	}
}
