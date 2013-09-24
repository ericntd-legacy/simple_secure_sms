package sg.edu.dukenus.simplesecuresms;

import android.os.Bundle;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;

import com.actionbarsherlock.app.SherlockActivity;

public class PassphraseRequiredSherlockActivitySSS extends SherlockActivity implements PassphraseRequiredActivitySSS {
	// debugging
	private final String TAG = "PassphraseRequiredSherlockActivitySSS";

  private final PassphraseRequiredMixinSSS delegate = new PassphraseRequiredMixinSSS();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
	  
    super.onCreate(savedInstanceState);
    Log.w(TAG, "onCreate");
    delegate.onCreate(this, this);
  }

  @Override
  protected void onResume() {
	  
    super.onResume();
    Log.w(TAG, "onResume");
    
    delegate.onResume(this, this);
    
  }

  @Override
  protected void onPause() {
	  Log.w(TAG, "onPause");
    super.onPause();
    delegate.onPause(this, this);
  }
  
  /*@Override
  protected void onStop() {
    super.onStop();
    delegate.onStop(this, this);
  }*/

  @Override
  protected void onDestroy() {
	  Log.w(TAG, "onDestroy");
    super.onDestroy();
    delegate.onDestroy(this, this);
  }

  @Override
  public void onMasterSecretCleared() {
	  Log.w(TAG, "onMasterSecretCleared");
    finish();
  }

  @Override
  public void onNewMasterSecret(MasterSecret masterSecret) {}

}
