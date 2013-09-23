/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package sg.edu.dukenus.simplesecuresms;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.thoughtcrime.securesms.VerifyIdentityActivity;
import org.thoughtcrime.securesms.crypto.InvalidKeyException;
import org.thoughtcrime.securesms.crypto.InvalidVersionException;
import org.thoughtcrime.securesms.crypto.KeyExchangeMessage;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import com.actionbarsherlock.app.SherlockActivity;

import textsecure.crypto.KeyExchangeProcessorSSS;

/**
 * Activity for displaying sent/received session keys.
 * 
 * @author Moxie Marlinspike
 */

public class ReceiveKeyActivitySSS extends
		PassphraseRequiredSherlockActivitySSS {
//public class ReceiveKeyActivitySSS extends SherlockActivity implements
//	PassphraseRequiredActivitySSS {
	// debugging
	private final String TAG = "ReceiveKeyActivitySSS";

	// helper
	//private final PassphraseRequiredMixinSSS delegate = new PassphraseRequiredMixinSSS();

	private TextView descriptionText;

	private Button confirmButton;
	private Button cancelButton;

	private Recipient recipient;
	private long threadId;
	private long messageId;

	private MasterSecret masterSecret;
	private KeyExchangeMessage keyExchangeMessage;
	private KeyExchangeProcessorSSS keyExchangeProcessor;

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);

		//delegate.onCreate(this, this);

		Log.w(TAG, TAG + " onCreate");
		setContentView(R.layout.receive_key_activity);

		initializeResources();

		try {
			initializeKey();
			initializeText();
		} catch (InvalidKeyException ike) {
			Log.w("ReceiveKeyActivity", ike);
		} catch (InvalidVersionException ive) {
			Log.w("ReceiveKeyActivity", ive);
		}
		initializeListeners();
	}

	@Override
	protected void onDestroy() {
		MemoryCleaner.clean(masterSecret);
		super.onDestroy();

		//delegate.onDestroy(this, this);
	}

	/*@Override
	protected void onResume() {
		super.onResume();
		delegate.onResume(this, this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		delegate.onPause(this, this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		delegate.onStop(this, this);
	}*/

	private void initializeText() {
		if (keyExchangeProcessor.isTrusted(keyExchangeMessage))
			initializeTrustedText();
		else
			initializeUntrustedText();
	}

	private void initializeTrustedText() {
		descriptionText
				.setText(getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_trusted_but));
	}

	private void initializeUntrustedText() {
		SpannableString spannableString = new SpannableString(
				getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_different)
						+ " "
						+ getString(R.string.ReceiveKeyActivity_you_may_wish_to_verify_this_contact));
		spannableString
				.setSpan(
						new ClickableSpan() {
							@Override
							public void onClick(View widget) {
								Intent intent = new Intent(
										ReceiveKeyActivitySSS.this,
										VerifyIdentityActivity.class);
								intent.putExtra("recipient", recipient);
								intent.putExtra("master_secret", masterSecret);
								startActivity(intent);
							}
						},
						getString(
								R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_different)
								.length() + 1, spannableString.length(),
						Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

		descriptionText.setText(spannableString);
		descriptionText.setMovementMethod(LinkMovementMethod.getInstance());
	}

	private void initializeKey() throws InvalidKeyException,
			InvalidVersionException {
		String messageBody = getIntent().getStringExtra("body");
		this.keyExchangeMessage = new KeyExchangeMessage(messageBody);
	}

	private void initializeResources() {
		this.descriptionText = (TextView) findViewById(R.id.description_text);
		this.confirmButton = (Button) findViewById(R.id.ok_button);
		this.cancelButton = (Button) findViewById(R.id.cancel_button);
		this.recipient = getIntent().getParcelableExtra("recipient");
		this.threadId = getIntent().getLongExtra("thread_id", -1);
		this.messageId = getIntent().getLongExtra("message_id", -1);
		this.masterSecret = (MasterSecret) getIntent().getParcelableExtra(
				"master_secret");
		Log.w(TAG, "master secret " + masterSecret + " and recipient "
				+ recipient);
		this.keyExchangeProcessor = new KeyExchangeProcessorSSS(this,
				masterSecret, recipient);
	}

	private void initializeListeners() {
		this.confirmButton.setOnClickListener(new OkListener());
		this.cancelButton.setOnClickListener(new CancelListener());
	}

	private class OkListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			Log.w(TAG, TAG + " secure session is confirmed");
			new AsyncTask<Void, Void, Void>() {
				private ProgressDialog dialog;

				@Override
				protected void onPreExecute() {
					dialog = ProgressDialog.show(ReceiveKeyActivitySSS.this,
							"Processing", "Processing key exchange...", true);
				}

				@Override
				protected Void doInBackground(Void... params) {
					keyExchangeProcessor.processKeyExchangeMessage(
							keyExchangeMessage, threadId);
					DatabaseFactory.getEncryptingSmsDatabase(
							ReceiveKeyActivitySSS.this)
							.markAsProcessedKeyExchange(messageId);
					return null;
				}

				@Override
				protected void onPostExecute(Void result) {
					dialog.dismiss();
					finish();
				}
			}.execute();
		}
	}

	private class CancelListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			ReceiveKeyActivitySSS.this.finish();
		}
	}

	/*@Override
	public void onMasterSecretCleared() {
		finish();

	}

	@Override
	public void onNewMasterSecret(MasterSecret masterSecret) {
		// TODO Auto-generated method stub

	}*/
}
