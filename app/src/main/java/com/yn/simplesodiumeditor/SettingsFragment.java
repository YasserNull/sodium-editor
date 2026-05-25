package com.yn.simplesodiumeditor;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsFragment extends PreferenceFragmentCompat
    implements SharedPreferences.OnSharedPreferenceChangeListener {

  private SharedPreferences prefs;

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.prefs, rootKey);
    prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    updateSummary(findPreference("theme"));
    updateSummary(findPreference("scroll_mode"));
  }

  @Override
  public void onResume() {
    super.onResume();
    prefs.registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onPause() {
    super.onPause();
    prefs.unregisterOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if ("theme".equals(key)) {
      updateSummary(findPreference(key));
      if (getActivity() instanceof MainActivity) {
        ((MainActivity) getActivity()).onThemeChanged();
      }
    } else if ("scroll_mode".equals(key)) {
      updateSummary(findPreference(key));
      int mode = Integer.parseInt(sharedPreferences.getString(key, "2"));
      if (getActivity() instanceof MainActivity) {
        ((MainActivity) getActivity()).onScrollModeChanged(mode);
      }
    }
  }

  private void updateSummary(Preference pref) {
    if (pref instanceof ListPreference) {
      pref.setSummary(((ListPreference) pref).getEntry());
    }
  }
}
