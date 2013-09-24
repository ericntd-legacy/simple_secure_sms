package sg.edu.dukenus.simplesecuresms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.ConversationListFragment;
import org.thoughtcrime.securesms.ImportExportActivity;
import org.thoughtcrime.securesms.PassphraseActivity;
import org.thoughtcrime.securesms.PassphraseCreateActivity;
import org.thoughtcrime.securesms.PassphraseRequiredSherlockFragmentActivity;
import org.thoughtcrime.securesms.ReviewIdentitiesActivity;
import org.thoughtcrime.securesms.RoutingActivity;
import org.thoughtcrime.securesms.ViewLocalIdentityActivity;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.notifications.MessageNotifier;

import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.service.KeyCachingService;

import textsecure.service.KeyCachingServiceSSS;
import textsecure.service.SendReceiveServiceSSS;

import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Menu;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.util.Log;
//import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class ConversationListActivitySSS extends
		PassphraseRequiredSherlockFragmentActivitySSS implements
		ConversationListFragmentSSS.ConversationSelectedListener,
		ListView.OnItemClickListener {
	// debugging
	private final String TAG = "ConversationListActivitySSS";
	private final boolean D = true;
	
	private final String APP_NAME = "SimpleSecureSMS";
	
	private final DynamicTheme dynamicTheme = new DynamicTheme();
	private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

	private ConversationListFragmentSSS fragment;
	private MasterSecret masterSecret;
	private DrawerLayout drawerLayout;
	private ListView drawerList;

	@Override
	public void onCreate(Bundle icicle) {
		dynamicTheme.onCreate(this);
		//dynamicLanguage.onCreate(this);
		super.onCreate(icicle);
		Log.w(TAG, TAG + " onCreate...");
		
		setContentView(R.layout.activity_conversation_list_activity_sss);
		getSupportActionBar().setTitle(APP_NAME);

		//initializeNavigationDrawer();
		initializeSenderReceiverService();
		initializeResources();
		//initializeContactUpdatesReceiver();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (D) Log.w(TAG, TAG + " onResume...");
		dynamicTheme.onResume(this);
		//dynamicLanguage.onResume(this);
	}

	@Override
	public void onDestroy() {
		if (D) Log.w(TAG, TAG + "onDestroy...");
		MemoryCleaner.clean(masterSecret);
		super.onDestroy();
	}
	
	/*@Override
	public void onPause() {
		super.onPause();
		if (D) Log.w(TAG, TAG + " onPause...");
	}*/
	
	/*@Override
	public void onStop() {
		super.onStop();
		if (D) Log.w(TAG, TAG + " onStop...");
	}*/

	@Override
	public void onMasterSecretCleared() {
		// this.fragment.setMasterSecret(null);
		Log.w(TAG, "onMasterSecretCleared");
		// what to do here?
		//startActivity(new Intent(this, ConversationListActivitySSS.class));
		super.onMasterSecretCleared();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuInflater inflater = this.getSupportMenuInflater();
		menu.clear();

		inflater.inflate(R.menu.simple_secure_sms_normal, menu);

		super.onPrepareOptionsMenu(menu);
		return true;
	}
	
	@Override
	public void onItemClick(AdapterView parent, View view, int position, long id) {
		/*String[] values = getResources().getStringArray(
				R.array.navigation_drawer_values);
		String selected = values[position];

		Intent intent;

		if (selected.equals("import_export")) {
			intent = new Intent(this, ImportExportActivity.class);
			intent.putExtra("master_secret", masterSecret);
		} else if (selected.equals("my_identity_key")) {
			intent = new Intent(this, ViewLocalIdentityActivity.class);
			intent.putExtra("master_secret", masterSecret);
		} else if (selected.equals("contact_identity_keys")) {
			intent = new Intent(this, ReviewIdentitiesActivity.class);
			intent.putExtra("master_secret", masterSecret);
		} else {
			return;
		}

		drawerLayout.closeDrawers();
		startActivity(intent);*/
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);

		int defaultType = ThreadDatabase.DistributionTypes.DEFAULT;

		/*
		 * switch (item.getItemId()) { case R.id.menu_new_message:
		 * createConversation(-1, null, defaultType); return true; case
		 * R.id.menu_settings: handleDisplaySettings(); return true; case
		 * R.id.menu_clear_passphrase: handleClearPassphrase(); return true;
		 * case R.id.menu_mark_all_read: handleMarkAllRead(); return true; case
		 * android.R.id.home: handleNavigationDrawerToggle(); return true; }
		 */
		int id = item.getItemId();
		if (id == R.id.menu_new_message) {
			createConversation(-1, null, defaultType);
			return true;
		} else if (id == R.id.menu_settings) {
			handleDisplaySettings();
			return true;
		} else if (id == R.id.menu_clear_passphrase) {
			handleClearPassphrase();
			return true;
		} else if (id == R.id.menu_mark_all_read) {
			handleMarkAllRead();
			return true;
		} else if (id == android.R.id.home) {
			handleNavigationDrawerToggle();
			return true;
		}

		return false;
	}

	@Override
	public void onCreateConversation(long threadId, Recipients recipients,
			int distributionType) {
		createConversation(threadId, recipients, distributionType);
	}

	private void createConversation(long threadId, Recipients recipients,
			int distributionType) {
		Intent intent = new Intent(this, ConversationActivitySSS.class);
		intent.putExtra(ConversationActivitySSS.RECIPIENTS_EXTRA, recipients);
		intent.putExtra(ConversationActivitySSS.THREAD_ID_EXTRA, threadId);
		intent.putExtra(ConversationActivitySSS.MASTER_SECRET_EXTRA, masterSecret);
		intent.putExtra(ConversationActivitySSS.DISTRIBUTION_TYPE_EXTRA,
				distributionType);

		startActivity(intent);
	}

	private void handleNavigationDrawerToggle() {
		if (drawerLayout.isDrawerOpen(drawerList)) {
			drawerLayout.closeDrawer(drawerList);
		} else {
			drawerLayout.openDrawer(drawerList);
		}
	}

	private void handleDisplaySettings() {
		Intent preferencesIntent = new Intent(this,
				ApplicationPreferencesActivity.class);
		preferencesIntent.putExtra("master_secret", masterSecret);
		startActivity(preferencesIntent);
	}

	private void handleClearPassphrase() {
		Intent intent = new Intent(this, KeyCachingServiceSSS.class);
		intent.setAction(KeyCachingServiceSSS.CLEAR_KEY_ACTION);
		startService(intent);
	}

	private void handleMarkAllRead() {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				DatabaseFactory
						.getThreadDatabase(ConversationListActivitySSS.this)
						.setAllThreadsRead();
				MessageNotifier.updateNotification(
						ConversationListActivitySSS.this, masterSecret);
				return null;
			}
		}.execute();
	}

	/*private void initializeNavigationDrawer() {
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);

		int[] attributes = new int[] { R.attr.navigation_drawer_icons,
				R.attr.navigation_drawer_shadow };
		String[] from = new String[] { "navigation_icon", "navigation_text" };
		int[] to = new int[] { R.id.navigation_icon, R.id.navigation_text };

		TypedArray iconArray = obtainStyledAttributes(attributes);
		int iconArrayResource = iconArray.getResourceId(0, -1);
		TypedArray icons = getResources().obtainTypedArray(iconArrayResource);
		String[] text = getResources().getStringArray(
				R.array.navigation_drawer_text);

		List<HashMap<String, String>> items = new ArrayList<HashMap<String, String>>();

		for (int i = 0; i < text.length; i++) {
			HashMap<String, String> item = new HashMap<String, String>();
			item.put("navigation_icon",
					Integer.toString(icons.getResourceId(i, -1)));
			item.put("navigation_text", text[i]);
			items.add(item);
		}

		DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		ListView drawer = (ListView) findViewById(R.id.left_drawer);
		SimpleAdapter adapter = new SimpleAdapter(this, items,
				R.layout.navigation_drawer_item, from, to);

		drawerLayout.setDrawerShadow(iconArray.getDrawable(1),
				GravityCompat.START);
		drawer.setAdapter(adapter);
		drawer.setOnItemClickListener(this);

		iconArray.recycle();
		icons.recycle();
	}*/

	/*private void initializeContactUpdatesReceiver() {
		ContentObserver observer = new ContentObserver(null) {
			@Override
			public void onChange(boolean selfChange) {
				super.onChange(selfChange);
				RecipientFactory.clearCache();
			}
		};

		getContentResolver().registerContentObserver(
				ContactsContract.Contacts.CONTENT_URI, true, observer);
	}*/

	private void initializeSenderReceiverService() {
		Intent bindIntent = new Intent(this, KeyCachingServiceSSS.class);
	    bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
	    
		// why is SendReceiveServiceSSS initialised with Intent of SEND_SMS_ACTION?
		Intent smsSenderIntent = new Intent(SendReceiveServiceSSS.SEND_SMS_ACTION,
				null, this, SendReceiveServiceSSS.class);
		/*Intent mmsSenderIntent = new Intent(SendReceiveServiceSSS.SEND_MMS_ACTION,
				null, this, SendReceiveServiceSSS.class);*/
		startService(smsSenderIntent);
		//startService(mmsSenderIntent);
	}

	private void initializeResources() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
					WindowManager.LayoutParams.FLAG_SECURE);
		}

		//this.drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		//this.drawerList = (ListView) findViewById(R.id.left_drawer);
		//this.masterSecret = (MasterSecret) getIntent().getParcelableExtra("master_secret");
		
		// inject the masterSecret value since this is not launched from another activity such as PassphraseCreateActivity
		String passphrase = "hardcodedpass";
	    this.masterSecret      = MasterSecretUtil.generateMasterSecret(ConversationListActivitySSS.this, passphrase);

		this.fragment = (ConversationListFragmentSSS) this.getSupportFragmentManager().findFragmentById(R.id.fragment_content);

		this.fragment.setMasterSecret(masterSecret);
		
		//TODO: to start KeyCachingServiceSSS
		// Do I need to bind KeyCachingServiceSSS to something?
		
	}
	private KeyCachingServiceSSS keyCachingService;
	private ServiceConnection serviceConnection = new ServiceConnection() {
	      @Override
	      public void onServiceConnected(ComponentName className, IBinder service) {
	        keyCachingService = ((KeyCachingServiceSSS.KeyCachingBinder)service).getService();
	        keyCachingService.setMasterSecret(masterSecret);

	        ConversationListActivitySSS.this.unbindService(ConversationListActivitySSS.this.serviceConnection);

	        MemoryCleaner.clean(masterSecret);
	        //cleanup();

	        ConversationListActivitySSS.this.setResult(RESULT_OK);
	        ConversationListActivitySSS.this.finish();
	      }

	      @Override
	      public void onServiceDisconnected(ComponentName name) {
	        keyCachingService = null;
	      }
	  };

}
