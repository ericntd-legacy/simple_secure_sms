package textsecure.service;

import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.crypto.InvalidKeyException;
import org.thoughtcrime.securesms.crypto.InvalidVersionException;
import org.thoughtcrime.securesms.crypto.KeyExchangeMessage;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.IncomingKeyExchangeMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.MultipartSmsMessageHandler;

import textsecure.crypto.DecryptingQueueSSS;
import textsecure.crypto.KeyExchangeProcessorSSS;

import java.util.List;

public class SmsReceiverSSS {
	// debugging
	private final String TAG = "SmsReceiver";

  private MultipartSmsMessageHandler multipartMessageHandler = new MultipartSmsMessageHandler();

  private final Context context;

  public SmsReceiverSSS(Context context) {
    this.context      = context;
  }


  private IncomingTextMessage assembleMessageFragments(List<IncomingTextMessage> messages) {
    IncomingTextMessage message = new IncomingTextMessage(messages);

    if (WirePrefix.isEncryptedMessage(message.getMessageBody()) ||
        WirePrefix.isKeyExchange(message.getMessageBody()))
    {
      return multipartMessageHandler.processPotentialMultipartMessage(message);
    } else {
      return message;
    }
  }

  private Pair<Long, Long> storeSecureMessage(MasterSecret masterSecret, IncomingTextMessage message) {
    Pair<Long, Long> messageAndThreadId = DatabaseFactory.getEncryptingSmsDatabase(context)
                                                         .insertMessageInbox(masterSecret, message);

    if (masterSecret != null) {
      DecryptingQueueSSS.scheduleDecryption(context, masterSecret, messageAndThreadId.first,
                                         messageAndThreadId.second,
                                         message.getSender(), message.getMessageBody(),
                                         message.isSecureMessage(), message.isKeyExchange());
    }

    return messageAndThreadId;
  }

  private Pair<Long, Long> storeStandardMessage(MasterSecret masterSecret, IncomingTextMessage message) {
    EncryptingSmsDatabase encryptingDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);
    SmsDatabase           plaintextDatabase  = DatabaseFactory.getSmsDatabase(context);

    if (masterSecret != null) {
      return encryptingDatabase.insertMessageInbox(masterSecret, message);
    } else if (MasterSecretUtil.hasAsymmericMasterSecret(context)) {
      return encryptingDatabase.insertMessageInbox(MasterSecretUtil.getAsymmetricMasterSecret(context, null), message);
    } else {
      return plaintextDatabase.insertMessageInbox(message);
    }
  }

  private Pair<Long, Long> storeKeyExchangeMessage(MasterSecret masterSecret,
                                                   IncomingKeyExchangeMessage message)
  {
    if (masterSecret != null &&
        PreferenceManager.getDefaultSharedPreferences(context)
                         .getBoolean(ApplicationPreferencesActivity.AUTO_KEY_EXCHANGE_PREF, true))
    {
      try {
        Recipient recipient                   = new Recipient(null, message.getSender(), null, null);
        KeyExchangeMessage keyExchangeMessage = new KeyExchangeMessage(message.getMessageBody());
        KeyExchangeProcessorSSS processor        = new KeyExchangeProcessorSSS(context, masterSecret, recipient);

        Log.w(TAG, "Received key with fingerprint: " + keyExchangeMessage.getPublicKey().getFingerprint());

        if (processor.isStale(keyExchangeMessage)) {
          message.setStale(true);
        } else if (processor.isTrusted(keyExchangeMessage)) {
          message.setProcessed(true);

          Pair<Long, Long> messageAndThreadId = storeStandardMessage(masterSecret, message);
          processor.processKeyExchangeMessage(keyExchangeMessage, messageAndThreadId.second);

          return messageAndThreadId;
        }
      } catch (InvalidVersionException e) {
        Log.w(TAG, e);
      } catch (InvalidKeyException e) {
        Log.w(TAG, e);
      }
    }

    return storeStandardMessage(masterSecret, message);
  }

  private Pair<Long, Long> storeMessage(MasterSecret masterSecret, IncomingTextMessage message) {
    if      (message.isSecureMessage()) return storeSecureMessage(masterSecret, message);
    else if (message.isKeyExchange())   return storeKeyExchangeMessage(masterSecret, (IncomingKeyExchangeMessage)message);
    else                                return storeStandardMessage(masterSecret, message);
  }

  private void handleReceiveMessage(MasterSecret masterSecret, Intent intent) {
    List<IncomingTextMessage> messagesList = intent.getExtras().getParcelableArrayList("text_messages");
    IncomingTextMessage message            = assembleMessageFragments(messagesList);

    if (message != null) {
      Pair<Long, Long> messageAndThreadId = storeMessage(masterSecret, message);
      MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
    }
  }

  public void process(MasterSecret masterSecret, Intent intent) {
    if (intent.getAction().equals(SendReceiveServiceSSS.RECEIVE_SMS_ACTION)) {
      handleReceiveMessage(masterSecret, intent);
    }
  }
}