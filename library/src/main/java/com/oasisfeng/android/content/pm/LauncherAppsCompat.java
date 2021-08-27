package com.oasisfeng.android.content.pm;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.Objects;

/**
 * Backward-compatibility helper for {@link LauncherApps}.
 *
 * Created by Oasis on 2018/1/18.
 */
@RequiresApi(LOLLIPOP) public class LauncherAppsCompat {

	public LauncherAppsCompat(final Context context) {
		mLauncherApps = Objects.requireNonNull((LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE));
	}

	@RequiresApi(N) @SuppressLint("NewApi")
	public ApplicationInfo getApplicationInfo(final String pkg, final int flags, final UserHandle user) throws PackageManager.NameNotFoundException {
		final ApplicationInfo info = mLauncherApps.getApplicationInfo(pkg, flags, user);
		if (SDK_INT < O && info == null)	// On Android 7.x, LauncherApps.getApplicationInfo() does not throw but return null for package not found.
			throw new PackageManager.NameNotFoundException("Package " + pkg + " not found for user " + user.hashCode());
		return info;
	}

	@RequiresApi(N) public @Nullable ApplicationInfo getApplicationInfoNoThrows(final String pkg, final int flags, final UserHandle user) {
		return getApplicationInfoNoThrows(mLauncherApps, pkg, flags, user);
	}

	@RequiresApi(N) @SuppressLint("NewApi") public static @Nullable ApplicationInfo getApplicationInfoNoThrows(
			final LauncherApps la, final String pkg, final int flags, final UserHandle user) {
		try {
			return la.getApplicationInfo(pkg, flags, user);
		} catch (final PackageManager.NameNotFoundException e) {
			return null;
		}
	}

	public final LauncherApps get() {
		return mLauncherApps;
	}

	private final LauncherApps mLauncherApps;
}
