package com.yn.simplesodiumeditor;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
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

  @NonNull
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View superView = super.onCreateView(inflater, container, savedInstanceState);
    View root = inflater.inflate(R.layout.settings_fragment, container, false);
    ViewGroup rootListContainer = root.findViewById(android.R.id.list_container);
    if (superView.getParent() != null) ((ViewGroup) superView.getParent()).removeView(superView);
    rootListContainer.addView(superView, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return root;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    Toolbar toolbar = view.findViewById(R.id.settingsToolbar);
    toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
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
