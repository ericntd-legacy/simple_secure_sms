package sg.edu.dukenus.simplesecuresms;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;

import textsecure.service.KeyCachingServiceSSS;

public class PassphraseRequiredMixinSSS {
	// debugging
	private static final String TAG = "PassphraseRequiredMixinSSS";

	private KeyCachingServiceConnection serviceConnection;
	private BroadcastReceiver clearKeyReceiver;
	private BroadcastReceiver newKeyReceiver;

	public void onCreate(Context context, PassphraseRequiredActivitySSS activity) {
		initializeClearKeyReceiver(context, activity);
	}

	public void onResume(Context context, PassphraseRequiredActivitySSS activity) {
		Log.w(TAG, TAG + " onResume");
		initializeNewKeyReceiver(context, activity);
		initializeServiceConnection(context, activity);
		KeyCachingServiceSSS.registerPassphraseActivityStarted(context);
	}

	public void onPause(Context context, PassphraseRequiredActivitySSS activity) {
		Log.w(TAG, TAG + " onPause");
		removeNewKeyReceiver(context);
		removeServiceConnection(context);
		KeyCachingServiceSSS.registerPassphraseActivityStopped(context);
	}

	public void onStop(Context context, PassphraseRequiredActivitySSS activity) {
		Log.w(TAG, TAG + " onStop");
		// removeServiceConnection(context);// not sure if this is necessary,
		// tryign to combat the leaked
		// ServiceConnection exception
	}

	public void onDestroy(Context context,
			PassphraseRequiredActivitySSS activity) {
		Log.w(TAG, TAG + " onDestroy");
		// removeServiceConnection(context);// not sure if this is necessary,
		// tryign to combat the leaked
		// ServiceConnection exception

		removeClearKeyReceiver(context);
	}

	private void initializeClearKeyReceiver(Context context,
			final PassphraseRequiredActivitySSS activity) {
		this.clearKeyReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				activity.onMasterSecretCleared();
			}
		};

		IntentFilter filter = new IntentFilter(
				KeyCachingServiceSSS.CLEAR_KEY_EVENT);
		context.registerReceiver(clearKeyReceiver, filter,
				KeyCachingServiceSSS.KEY_PERMISSION, null);
	}

	private void initializeNewKeyReceiver(Context context,
			final PassphraseRequiredActivitySSS activity) {
		this.newKeyReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				activity.onNewMasterSecret((MasterSecret) intent
						.getParcelableExtra("master_secret"));
			}
		};

		IntentFilter filter = new IntentFilter(
				KeyCachingServiceSSS.NEW_KEY_EVENT);
		context.registerReceiver(newKeyReceiver, filter,
				KeyCachingServiceSSS.KEY_PERMISSION, null);
	}

	private void initializeServiceConnection(Context context,
			PassphraseRequiredActivitySSS activity) {
		Log.w(TAG, "initializing service connection to KeyCachingServiceSSS");
		Intent cachingIntent = new Intent(context, KeyCachingServiceSSS.class);
		context.startService(cachingIntent);

		this.serviceConnection = new KeyCachingServiceConnection(activity);

		Intent bindIntent = new Intent(context, KeyCachingServiceSSS.class);
		context.bindService(bindIntent, serviceConnection,
				Context.BIND_AUTO_CREATE);
		// serviceConnection.isBound = true;
	}

	private void removeClearKeyReceiver(Context context) {
		if (clearKeyReceiver != null) {
			context.unregisterReceiver(clearKeyReceiver);
			clearKeyReceiver = null;
		}
	}

	private void removeNewKeyReceiver(Context context) {
		if (newKeyReceiver != null) {
			context.unregisterReceiver(newKeyReceiver);
			newKeyReceiver = null;
		}
	}

	private void removeServiceConnection(Context context) {
		Log.w(TAG, "Removing serviceconnection with KeyCachingServiceSSS");
		if (this.serviceConnection != null && this.serviceConnection.isBound()) {
			serviceConnection.isBound = false;
			context.unbindService(this.serviceConnection);
		}
	}

	private static class KeyCachingServiceConnection implements
			ServiceConnection {
		private final PassphraseRequiredActivitySSS activity;// what does this
																// activity has
																// to do with
																// ConversationListActivitySSS?

		private boolean isBound;

		public KeyCachingServiceConnection(
				PassphraseRequiredActivitySSS activity) {
			this.activity = activity;
			this.isBound = false;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.w(TAG,
					"KeyCachingServiceSSS is bound to PassphraseRequiredSherlockActivitySSS or ConversationListActivitySSS? "+name);
			KeyCachingServiceSSS keyCachingService = ((KeyCachingServiceSSS.KeyCachingBinder) service)
					.getService();
			MasterSecret masterSecret = keyCachingService.getMasterSecret();
			this.isBound = true;

			if (masterSecret == null) {
				activity.onMasterSecretCleared();// this calls
													// ConversationListActivitySSS.onMasterSecretCleared()
													// which calls
													// PassphraseRequiredSherlockActivitySSS.onMasterSecretCleared()?
			} else {
				activity.onNewMasterSecret(masterSecret);
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			this.isBound = false;
		}

		public boolean isBound() {
			return this.isBound;
		}
	}

}
