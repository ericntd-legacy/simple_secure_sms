package textsecure.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.CanonicalSessionMigrator;

import textsecure.util.WorkerThreadSSS;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Services that handles sending/receiving of SMS/MMS.
 * 
 * @author Moxie Marlinspike
 */

public class SendReceiveServiceSSS extends Service {
	// debugging
	private final String TAG = "SendReceiveServiceSSS";

	public static final String SEND_SMS_ACTION = "org.thoughtcrime.securesms.SendReceiveService.SEND_SMS_ACTION";
	public static final String SENT_SMS_ACTION = "org.thoughtcrime.securesms.SendReceiveService.SENT_SMS_ACTION";
	public static final String DELIVERED_SMS_ACTION = "org.thoughtcrime.securesms.SendReceiveService.DELIVERED_SMS_ACTION";
	public static final String RECEIVE_SMS_ACTION = "org.thoughtcrime.securesms.SendReceiveService.RECEIVE_SMS_ACTION";
	/*
	 * public static final String SEND_MMS_ACTION =
	 * "org.thoughtcrime.securesms.SendReceiveService.SEND_MMS_ACTION"; public
	 * static final String SEND_MMS_CONNECTIVITY_ACTION =
	 * "org.thoughtcrime.securesms.SendReceiveService.SEND_MMS_CONNECTIVITY_ACTION"
	 * ; public static final String RECEIVE_MMS_ACTION =
	 * "org.thoughtcrime.securesms.SendReceiveService.RECEIVE_MMS_ACTION";
	 * public static final String DOWNLOAD_MMS_ACTION =
	 * "org.thoughtcrime.securesms.SendReceiveService.DOWNLOAD_MMS_ACTION";
	 * public static final String DOWNLOAD_MMS_CONNECTIVITY_ACTION =
	 * "org.thoughtcrime.securesms.SendReceiveService.DOWNLOAD_MMS_CONNECTIVITY_ACTION"
	 * ;
	 */

	private static final int SEND_SMS = 0;
	private static final int RECEIVE_SMS = 1;
	/*
	 * private static final int SEND_MMS = 2; private static final int
	 * RECEIVE_MMS = 3; private static final int DOWNLOAD_MMS = 4;
	 */

	private ToastHandler toastHandler;

	private SmsReceiverSSS smsReceiver;
	private SmsSenderSSS smsSender;
	/*
	 * private MmsReceiver mmsReceiver; private MmsSender mmsSender; private
	 * MmsDownloader mmsDownloader;
	 */
	private MasterSecret masterSecret;
	private boolean hasSecret;

	private NewKeyReceiver newKeyReceiver;
	private ClearKeyReceiver clearKeyReceiver;
	private List<Runnable> workQueue;
	private List<Runnable> pendingSecretList;
	private Thread workerThread;

	/*
	 * private ServiceConnection serviceConnection;
	 * 
	 * private final IBinder mBinder = new LocalBinder(); public class
	 * LocalBinder extends Binder { public SendReceiveServiceSSS getService() {
	 * return SendReceiveServiceSSS.this; } }
	 */

	@Override
	public void onCreate() {
		Log.w(TAG, TAG + " created");
		initializeHandlers();
		initializeProcessors();
		initializeAddressCanonicalization();
		initializeWorkQueue();
		initializeMasterSecret();
	}

	// This is the old onStart method that will be called on the pre-2.0
	// platform. On 2.0 or later we override onStartCommand() so this
	// method will not be called.
	@Override
	public void onStart(Intent intent, int startId) {
		handleStartCommand(intent);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		handleStartCommand(intent);
		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return START_STICKY;
	}

	public void handleStartCommand(Intent intent) {
		if (intent == null)
			return;

		Log.w(TAG,
				TAG + " started and running with intent " + intent.getAction());

		if (intent.getAction().equals(SEND_SMS_ACTION))
			scheduleSecretRequiredIntent(SEND_SMS, intent);
		else if (intent.getAction().equals(RECEIVE_SMS_ACTION))
			scheduleIntent(RECEIVE_SMS, intent);
		else if (intent.getAction().equals(SENT_SMS_ACTION))
			scheduleIntent(SEND_SMS, intent);
		else if (intent.getAction().equals(DELIVERED_SMS_ACTION))
			scheduleIntent(SEND_SMS, intent);
		/*
		 * else if (intent.getAction().equals(SEND_MMS_ACTION) ||
		 * intent.getAction().equals(SEND_MMS_CONNECTIVITY_ACTION))
		 * scheduleSecretRequiredIntent(SEND_MMS, intent); else if
		 * (intent.getAction().equals(RECEIVE_MMS_ACTION))
		 * scheduleIntent(RECEIVE_MMS, intent); else if
		 * (intent.getAction().equals(DOWNLOAD_MMS_ACTION) ||
		 * intent.getAction().equals(DOWNLOAD_MMS_CONNECTIVITY_ACTION))
		 * scheduleSecretRequiredIntent(DOWNLOAD_MMS, intent);
		 */
		else
			Log.w(TAG,
					"Received intent with unknown action: "
							+ intent.getAction());
	}

	@Override
	public IBinder onBind(Intent intent) {
		// return mBinder;
		return null;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		super.onUnbind(intent);

		Log.w(TAG, "the service is unbound");
		return true;
	}

	@Override
	public void onDestroy() {
		Log.w(TAG, "onDestroy()...");
		super.onDestroy();

		if (newKeyReceiver != null)
			unregisterReceiver(newKeyReceiver);

		if (clearKeyReceiver != null)
			unregisterReceiver(clearKeyReceiver);

		unbindService(serviceConnection);
	}

	private void initializeHandlers() {
		toastHandler = new ToastHandler();
	}

	private void initializeProcessors() {
		smsReceiver = new SmsReceiverSSS(this);
		smsSender = new SmsSenderSSS(this, toastHandler);
		/*
		 * mmsReceiver = new MmsReceiver(this); mmsSender = new MmsSender(this,
		 * toastHandler); mmsDownloader = new MmsDownloader(this, toastHandler);
		 */
	}

	private void initializeWorkQueue() {
		pendingSecretList = new LinkedList<Runnable>();
		workQueue = new LinkedList<Runnable>();
		workerThread = new WorkerThreadSSS(workQueue,
				"SendReceveService-WorkerThreadSSS");// the worker thread takes
														// jobs from the work
														// queue to execute

		workerThread.start();
	}

	private void initializeMasterSecret() {
		hasSecret = false;
		newKeyReceiver = new NewKeyReceiver();
		clearKeyReceiver = new ClearKeyReceiver();

		IntentFilter newKeyFilter = new IntentFilter(
				KeyCachingServiceSSS.NEW_KEY_EVENT);
		registerReceiver(newKeyReceiver, newKeyFilter,
				KeyCachingServiceSSS.KEY_PERMISSION, null);

		IntentFilter clearKeyFilter = new IntentFilter(
				KeyCachingServiceSSS.CLEAR_KEY_EVENT);
		registerReceiver(clearKeyReceiver, clearKeyFilter,
				KeyCachingServiceSSS.KEY_PERMISSION, null);

		Intent bindIntent = new Intent(this, KeyCachingServiceSSS.class);
		Log.w(TAG, "binding KeyCachingServiceSSS to SendReceiveService");
		/*serviceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName className,
					IBinder service) {
				Log.w(TAG,
						"SendReceiveServiceSSS is connected to "
								+ className.toString());
				KeyCachingServiceSSS keyCachingService = ((KeyCachingServiceSSS.KeyCachingBinder) service)
						.getService();
				MasterSecret masterSecret = keyCachingService.getMasterSecret();

				initializeWithMasterSecret(masterSecret);

				SendReceiveServiceSSS.this.unbindService(this);
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
			}
		};*/
		try {
			// getApplicationContext().bindService(bindIntent,
			// serviceConnection, Context.BIND_DEBUG_UNBIND);
			bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);// binding
																					// KeyCachingServiceSSS
																					// to
																					// SendReceiveService
																					// SSS
																					// or
																					// the
																					// other
																					// way
																					// round?
		} catch (Exception e) {
			Log.e(TAG,
					"could not bind KeyCachingServiceSSS to SendReceiveServiceSSS",
					e);
		}
	}

	private void initializeWithMasterSecret(MasterSecret masterSecret) {
		Log.w(TAG, "SendReceive service got master secret.");

		if (masterSecret != null) {
			synchronized (workQueue) {
				this.masterSecret = masterSecret;
				this.hasSecret = true;

				Iterator<Runnable> iterator = pendingSecretList.iterator();
				while (iterator.hasNext())
					workQueue.add(iterator.next());

				workQueue.notifyAll();
			}
		}
	}

	private void initializeAddressCanonicalization() {
		CanonicalSessionMigrator.migrateSessions(this);
	}

	private void scheduleIntent(int what, Intent intent) {
		Log.w(TAG,
				"adding work (Receive SMS, receive Sent SMS status or receive Delivered SMS status) to the work queue");
		Runnable work = new SendReceiveWorkItem(intent, what);

		synchronized (workQueue) {
			Log.w(TAG, "there are currently " + workQueue.size()
					+ " works in the work queue");
			workQueue.add(work);
			workQueue.notifyAll();
		}
	}

	private void scheduleSecretRequiredIntent(int what, Intent intent) {
		Log.w(TAG, "adding Send SMS work to the work queue");
		Runnable work = new SendReceiveWorkItem(intent, what);

		synchronized (workQueue) {// why is synchronized is used here? is it to
									// protect/ lock workQueue agains access
									// from other threads or processes? which
									// threads or processes?
			// somehow the works in the queue stalled, current workaround is to
			// empty the queue every time
			/*
			 * workQueue.clear(); workQueue.add(work); workQueue.notifyAll();
			 */
			Log.w(TAG,
					"there are currently " + workQueue.size()
							+ " works in the work queue and hasSecret is "
							+ hasSecret + " pending work list's size is "
							+ pendingSecretList.size());
			if (hasSecret) {
				workQueue.add(work);
				workQueue.notifyAll();
			} else {
				pendingSecretList.add(work);
			}
		}
	}

	private class SendReceiveWorkItem implements Runnable {
		private final Intent intent;
		private final int what;

		public SendReceiveWorkItem(Intent intent, int what) {
			this.intent = intent;
			this.what = what;
		}

		@Override
		public void run() {
			Log.w(TAG, "One sendreceive work is executing intent is " + intent
					+ " and what is " + what + " with mastersecret is "
					+ masterSecret + " and intent is " + intent);
			try {
				switch (what) {
				case RECEIVE_SMS:
					smsReceiver.process(masterSecret, intent);
					return;
				case SEND_SMS:
					smsSender.process(masterSecret, intent);
					return;
					/*
					 * case RECEIVE_MMS: mmsReceiver.process(masterSecret,
					 * intent); return; case SEND_MMS:
					 * mmsSender.process(masterSecret, intent); return; case
					 * DOWNLOAD_MMS: mmsDownloader.process(masterSecret,
					 * intent); return;
					 */
				}
			} catch (NullPointerException e) {
				// mastersecret is null when application just start with a
				// default send sms job but that job is unreal
				Log.e(TAG,
						"either masterSecret or intent is null, mastersecret is "
								+ masterSecret + " while intent is " + intent);
			}
		}
	}

	public class ToastHandler extends Handler {
		public void makeToast(String toast) {
			Message message = this.obtainMessage();
			message.obj = toast;
			this.sendMessage(message);
		}

		@Override
		public void handleMessage(Message message) {
			Toast.makeText(SendReceiveServiceSSS.this, (String) message.obj,
					Toast.LENGTH_LONG).show();
		}
	}

	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.w(TAG, TAG + " is connected to " + className.toString());
			KeyCachingServiceSSS keyCachingService = ((KeyCachingServiceSSS.KeyCachingBinder) service)
					.getService();
			MasterSecret masterSecret = keyCachingService.getMasterSecret();

			initializeWithMasterSecret(masterSecret);

			SendReceiveServiceSSS.this.unbindService(this);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
		}
	};

	private class NewKeyReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.w(TAG, "Got a MasterSecret broadcast...");
			initializeWithMasterSecret((MasterSecret) intent
					.getParcelableExtra("master_secret"));
		}
	}

	/**
	 * This class receives broadcast notifications to clear the MasterSecret.
	 * 
	 * We don't want to clear it immediately, since there are potentially jobs
	 * in the work queue which require the master secret. Instead, we reset a
	 * flag so that new incoming jobs will be evaluated as if no mastersecret is
	 * present.
	 * 
	 * Then, we add a job to the end of the queue which actually clears the
	 * masterSecret value. That way all jobs before this moment will be
	 * processed correctly, and all jobs after this moment will be evaluated as
	 * if no mastersecret is present (and potentially held).
	 * 
	 * When we go to actually clear the mastersecret, we ensure that the flag is
	 * still false. This allows a new mastersecret broadcast to come in
	 * correctly without us clobbering it.
	 * 
	 */
	private class ClearKeyReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.w(TAG, "Got a clear mastersecret broadcast...");

			synchronized (workQueue) {
				SendReceiveServiceSSS.this.hasSecret = false;
				workQueue.add(new Runnable() {
					@Override
					public void run() {
						Log.w(TAG, "Running clear key work item...");

						synchronized (workQueue) {
							if (!SendReceiveServiceSSS.this.hasSecret) {
								Log.w(TAG, "Actually clearing key...");
								SendReceiveServiceSSS.this.masterSecret = null;
							}
						}
					}
				});

				workQueue.notifyAll();
			}
		}
	}
}