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
package textsecure.sms;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;

import org.thoughtcrime.securesms.sms.OutgoingTextMessage;

import java.util.List;

import textsecure.service.SendReceiveServiceSSS;

public class MessageSenderSSS {
	// debugging
	private static final String TAG = "MessageSenderSSS";

	public static long send(Context context, MasterSecret masterSecret,
			OutgoingTextMessage message, long threadId) {
		if (threadId == -1)
			threadId = DatabaseFactory.getThreadDatabase(context)
					.getThreadIdFor(message.getRecipients());

		List<Long> messageIds = DatabaseFactory.getEncryptingSmsDatabase(
				context).insertMessageOutbox(masterSecret, threadId, message);

		for (long messageId : messageIds) {
			Log.w(TAG, "Got message id for new message: " + messageId);

			Intent intent = new Intent(SendReceiveServiceSSS.SEND_SMS_ACTION,
					null, context, SendReceiveServiceSSS.class);
			intent.putExtra("message_id", messageId);
			ComponentName cname = context.startService(intent);
			if (cname != null)
				Log.w(TAG,
						"service was already running, its component name is "
								+ cname.toString());
		}

		return threadId;
	}

}
