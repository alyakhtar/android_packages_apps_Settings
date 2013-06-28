/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.inputmethod;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.provider.UserDictionary;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class UserDictionaryList extends SettingsPreferenceFragment {
    public static final String USER_DICTIONARY_SETTINGS_INTENT_ACTION =
            "android.settings.USER_DICTIONARY_SETTINGS";
    private String mLocale;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getActivity()));
        getActivity().getActionBar().setTitle(R.string.user_dict_settings_title);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Intent intent = getActivity().getIntent();
        final String localeFromIntent =
                null == intent ? null : intent.getStringExtra("locale");

        final Bundle arguments = getArguments();
        final String localeFromArguments =
                null == arguments ? null : arguments.getString("locale");

        final String locale;
        if (null != localeFromArguments) {
            locale = localeFromArguments;
        } else if (null != localeFromIntent) {
            locale = localeFromIntent;
        } else {
            locale = null;
        }
        mLocale = locale;
    }

    public static TreeSet<String> getUserDictionaryLocalesSet(Activity activity) {
        @SuppressWarnings("deprecation")
        final Cursor cursor = activity.managedQuery(UserDictionary.Words.CONTENT_URI,
                new String[] { UserDictionary.Words.LOCALE },
                null, null, null);
        final TreeSet<String> localeSet = new TreeSet<String>();
        if (null == cursor) {
            // The user dictionary service is not present or disabled. Return null.
            return null;
        } else if (cursor.moveToFirst()) {
            final int columnIndex = cursor.getColumnIndex(UserDictionary.Words.LOCALE);
            do {
                final String locale = cursor.getString(columnIndex);
                localeSet.add(null != locale ? locale : "");
            } while (cursor.moveToNext());
        }
        // CAVEAT: Keep this for consistency of the implementation between Keyboard and Settings
        // if (!UserDictionarySettings.IS_SHORTCUT_API_SUPPORTED) {
        //     // For ICS, we need to show "For all languages" in case that the keyboard locale
        //     // is different from the system locale
        //     localeSet.add("");
        // }

        final InputMethodManager imm =
                (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        final List<InputMethodInfo> imis = imm.getEnabledInputMethodList();
        for (final InputMethodInfo imi : imis) {
            final List<InputMethodSubtype> subtypes =
                    imm.getEnabledInputMethodSubtypeList(
                            imi, true /* allowsImplicitlySelectedSubtypes */);
            for (InputMethodSubtype subtype : subtypes) {
                final String locale = subtype.getLocale();
                if (!TextUtils.isEmpty(locale)) {
                    localeSet.add(locale);
                }
            }
        }

        // We come here after we have collected locales from existing user dictionary entries and
        // enabled subtypes. If we already have the locale-without-country version of the system
        // locale, we don't add the system locale to avoid confusion even though it's technically
        // correct to add it.
        if (!localeSet.contains(Locale.getDefault().getLanguage().toString())) {
            localeSet.add(Locale.getDefault().toString());
        }

        return localeSet;
    }

    /**
     * Creates the entries that allow the user to go into the user dictionary for each locale.
     * @param userDictGroup The group to put the settings in.
     * @return the shown language set
     */
    protected TreeSet<String> createUserDictSettingsAndReturnSet(PreferenceGroup userDictGroup) {
        final Activity activity = getActivity();
        userDictGroup.removeAll();
        final TreeSet<String> localeSet =
                UserDictionaryList.getUserDictionaryLocalesSet(activity);
        if (mLocale != null) {
            // If the caller explicitly specify empty string as a locale, we'll show "all languages"
            // in the list.
            localeSet.add(mLocale);
        }

        if (localeSet.isEmpty()) {
            userDictGroup.addPreference(createUserDictionaryPreference(null, activity));
        } else {
            for (String locale : localeSet) {
                userDictGroup.addPreference(createUserDictionaryPreference(locale, activity));
            }
        }
        return localeSet;
    }

    /**
     * Create a single User Dictionary Preference object, with its parameters set.
     * @param locale The locale for which this user dictionary is for.
     * @return The corresponding preference.
     */
    protected Preference createUserDictionaryPreference(String locale, Activity activity) {
        final Preference newPref = new Preference(getActivity());
        final Intent intent = new Intent(USER_DICTIONARY_SETTINGS_INTENT_ACTION);
        if (null == locale) {
            newPref.setTitle(Locale.getDefault().getDisplayName());
        } else {
            if ("".equals(locale))
                newPref.setTitle(getString(R.string.user_dict_settings_all_languages));
            else
                newPref.setTitle(Utils.createLocaleFromString(locale).getDisplayName());
            intent.putExtra("locale", locale);
            newPref.getExtras().putString("locale", locale);
        }
        newPref.setIntent(intent);
        newPref.setFragment(com.android.settings.UserDictionarySettings.class.getName());
        return newPref;
    }

    @Override
    public void onResume() {
        super.onResume();
        final TreeSet<String> localeSet = createUserDictSettingsAndReturnSet(getPreferenceScreen());
        if (localeSet.size() <= 1) {
            // Redirect to UserDictionarySettings if the user needs only one language.
            final Bundle extras = new Bundle();
            if (!localeSet.isEmpty()) {
                // If the size of localeList is 0, we don't set the locale parameter in the
                // extras. This will be interpreted by the UserDictionarySettings class as
                // meaning "the current locale".
                // Note that with the current code for
                // UserDictionaryList#getUserDictionaryLocalesSet()
                // the locale list always has at least one element, since it always includes
                // the current locale explicitly.
                // @see UserDictionaryList.getUserDictionaryLocalesSet().
                extras.putString("locale", localeSet.first());
            }
            startFragment(this,
                    com.android.settings.UserDictionarySettings.class.getCanonicalName(), -1,
                    extras);
            finish();
        }
    }
}