package com.oasisfeng.android.content;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

/** @author Oasis */
// TODO: Batch the continuous modifications into one broadcast
public class MultiProcessSharedPreferences {

	static final String KActionSharedPrefsChanged = "com.oasisfeng.android.content.ACTION_SHARED_PREFS_CHANGED";
	static final String KExtraName = "name";
	static final String KExtraKey = "key";
	static final String KExtraPid = "pid";

	@SuppressLint("InlinedApi") private static int MODE_MULTI_PROCESS = Context.MODE_MULTI_PROCESS;

	public static SharedPreferences get(final Context context, final String name, final int mode) {
		if (mSingleton == null) {
			synchronized(mLock) {
				if (mSingleton == null) {
					mSingleton = new MultiProcessSharedPreferences(context);
				}
			}
		}

		return mSingleton.getSharedPreferences(context, name, mode);
	}

	private SharedPreferencesWrapper getSharedPreferences(final Context context, final String name, final int mode) {
		final SharedPreferences prefs = context.getSharedPreferences(name, mode | MODE_MULTI_PROCESS);
		if (prefs == null) return null;		// Should not happen, but still check for safety (in case of custom implementation)
		SharedPreferencesWrapper wrapper = mTracked.get(prefs);		// SharedPreferences instance should be singleton.
		if (wrapper != null) return wrapper;

		Log.d(TAG, "Tracking shared preferences: " + name);
		wrapper = new SharedPreferencesWrapper(name, prefs);
		mTracked.put(prefs, wrapper);

		prefs.registerOnSharedPreferenceChangeListener(wrapper);
		return wrapper;
	}

	private void onUpdated(final Intent intent) {
		final int my_pid = Process.myPid();
		final int pid = intent.getIntExtra(KExtraPid, my_pid);
		final String name = intent.getStringExtra(KExtraName);
		final String key = intent.getStringExtra(KExtraKey);
		if (pid == my_pid || TextUtils.isEmpty(name) || TextUtils.isEmpty(key)) return;
		Log.d(TAG, "Broadcast of change received from process " + pid + ": " + key + " changed in " + name);

		final SharedPreferences prefs = mAppContext.getSharedPreferences(name, MODE_MULTI_PROCESS);
		final SharedPreferencesWrapper wrapper = mTracked.get(prefs);
		if (wrapper == null) return;
		wrapper.notifyListeners(key);
	}

	private MultiProcessSharedPreferences(final Context context) {
		mAppContext = context.getApplicationContext();
		mAppContext.registerReceiver(new BroadcastReceiver() { @Override public void onReceive(final Context c, final Intent intent) {
			onUpdated(intent);
		}}, new IntentFilter(KActionSharedPrefsChanged));
	}

	private final Context mAppContext;
	private final Map<SharedPreferences, SharedPreferencesWrapper> mTracked = new HashMap<SharedPreferences, SharedPreferencesWrapper>();

	private static MultiProcessSharedPreferences mSingleton;
	private static Object mLock = new Object();

	static final String TAG = "MPSharedPrefs";

	class SharedPreferencesWrapper implements SharedPreferences, OnSharedPreferenceChangeListener {

		@Override public void registerOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener listener) {
			synchronized (this) {
				mListeners.put(listener, Boolean.TRUE);
			}
			mDelegate.registerOnSharedPreferenceChangeListener(listener);
		}

		@Override public void unregisterOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener listener) {
			mDelegate.unregisterOnSharedPreferenceChangeListener(listener);
			synchronized (this) {
				mListeners.remove(listener);
			}
		}

		void notifyListeners(final String key) {
			final Set<OnSharedPreferenceChangeListener> listeners;
			synchronized (this) {
				listeners = new HashSet<OnSharedPreferenceChangeListener>(mListeners.keySet());
			}
			for (final OnSharedPreferenceChangeListener listener : listeners) {
				Log.d(MultiProcessSharedPreferences.TAG, "Notify listener: " + listener);
				listener.onSharedPreferenceChanged(this, key);
			}
		}

		public SharedPreferencesWrapper(final String name, final SharedPreferences prefs) {
			mName = name;
			mDelegate = prefs;
		}

		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		@Override public Set<String> getStringSet(final String key, final Set<String> defValues) { return mDelegate.getStringSet(key, defValues); }
		@Override public Map<String, ?> getAll() { return mDelegate.getAll(); }
		@Override public String getString(final String key, final String defValue) { return mDelegate.getString(key, defValue); }
		@Override public int getInt(final String key, final int defValue) { return mDelegate.getInt(key, defValue); }
		@Override public long getLong(final String key, final long defValue) { return mDelegate.getLong(key, defValue); }
		@Override public float getFloat(final String key, final float defValue) { return mDelegate.getFloat(key, defValue); }
		@Override public boolean getBoolean(final String key, final boolean defValue) { return mDelegate.getBoolean(key, defValue); }
		@Override public boolean contains(final String key) { return mDelegate.contains(key); }
		@Override public Editor edit() { return mDelegate.edit(); }

		@Override public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
			Log.d(TAG, key + " changed in shared preferences " + mName + ", broadcast this change to other processes.");
			sharedPreferences.edit().commit();		// Force commit to avoid reloading ahead of flushing.
			final Intent intent = new Intent(KActionSharedPrefsChanged)
			.putExtra(KExtraName, mName).putExtra(KExtraKey, key).putExtra(KExtraPid, Process.myPid());
			mAppContext.sendBroadcast(intent);
		}

		private final String mName;
		private final SharedPreferences mDelegate;
		private final Map<OnSharedPreferenceChangeListener, Boolean> mListeners = new WeakHashMap<OnSharedPreferenceChangeListener, Boolean>();
	}
}
