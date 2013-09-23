package sg.edu.dukenus.simplesecuresms;

import org.thoughtcrime.securesms.crypto.MasterSecret;

public interface PassphraseRequiredActivitySSS {
  public void onMasterSecretCleared();
  public void onNewMasterSecret(MasterSecret masterSecret);
}
