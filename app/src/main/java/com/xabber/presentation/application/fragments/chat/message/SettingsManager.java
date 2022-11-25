package com.xabber.presentation.application.fragments.chat.message;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;

//import com.xabber.presentation.XabberApplication;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Pattern;

public class SettingsManager implements OnInitializedListener, SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String NOTIFICATION_PREFERENCES = "notification_preferences";

    private static SettingsManager instance;

    private SettingsManager() {
     //   getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    public static SettingsManager getInstance() {
        if (instance == null) {
            instance = new SettingsManager();
        }
        return instance;
    }

//    private static SharedPreferences getSharedPreferences() {
//        return SharedPreferences.OnSharedPreferenceChangeListener()
//      //  return PreferenceManager.getDefaultSharedPreferences(XabberApplication.Companion.newInstance());
//    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

    }

    @Override
    public void onInitialized() {

    }


    public enum ChatsHistory {

        /**
         * Don't store chat messages.
         */
        none,

        /**
         * Store only unread messages.
         */
        unread,

        /**
         * Store all messages.
         */
        all

    }

    public enum InterfaceTheme {

        /**
         * All windows will be dark.
         */
        dark,

        /**
         * All windows will be light.
         */
        light,

        /**
         * Chat will be light, other windows will be dark.
         */
        normal

    }

    public enum DnsResolverType {
        /**
         * Use DNS resolver based on dnsjava
         * http://dnsjava.org/
         */
        dnsJavaResolver,

        /**
         * Use DNS resolver based on MiniDNS - experimental
         * https://github.com/rtreffer/minidns
         */
        miniDnsResolver
    }

    public enum EventsMessage {

        /**
         * Never notify.
         */
        none,

        /**
         * Notify in chat only.
         */
        chat,

        /**
         * Notify in chat and muc.
         */
        chatAndMuc

    }

    public enum ChatsShowStatusChange {

        /**
         * Always show status change.
         */
        always,

        /**
         * Show status change only in MUC.
         */
        muc,

        /**
         * Never show status change.
         */
        never

    }

    public enum ChatsHideKeyboard {

        /**
         * Always hide keyboard.
         */
        always,

        /**
         * Hide keyboard only in landscape mode.
         */
        landscape,

        /**
         * Never hide keyboard.
         */
        never,
    }

    public enum SpamFilterMode {
        /**
         * Spam filter is disabled.
         */
        disabled,

        /**
         * Receiving messages only from roster.
         */
        onlyRoster,

        /**
         * Receiving messages only from roster.
         * Auth requests only with captcha.
         */
        authCaptcha,

        /**
         * Receiving messages only from roster.
         * No auth requests.
         */
        noAuth
    }

    public enum VibroMode {
        /**
         * Vibrate is disabled.
         */
        disabled,

        /**
         * Default vibration
         */
        defaultvibro,

        /**
         * Short vibration
         */
        shortvibro,

        /**
         * Long vibration
         */
        longvibro,

        /**
         * Vibration only in silent mode
         */
        onlyifsilent
    }

}