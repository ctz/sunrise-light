package io.jbp.sunrise;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.security.SecureRandom;
import java.util.Random;


public class DebugFragment extends PreferenceFragment
{
    SecureRandom random = new SecureRandom();

    @Override
    public void onCreate(Bundle saved)
    {
        super.onCreate(saved);
        addPreferencesFromResource(R.xml.debug_prefs);
        bind();
    }

    public static byte[] concat(byte[] a, byte[] b)
    {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private void send(byte[] msg)
    {
        IO.getInstance().write(msg);
    }

    private void randomRing()
    {
        byte[] v = new byte[4];
        v[0] = (byte) random.nextInt(10);
        v[1] = (byte) random.nextInt(256);
        v[2] = (byte) random.nextInt(256);
        v[3] = (byte) random.nextInt(256);

        send(concat("ring".getBytes(), v));
    }

    private void bind()
    {
        findPreference("off").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                send("blck".getBytes());
                return true;
            }
        });
        findPreference("random-ring").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                randomRing();
                return true;
            }
        });
        findPreference("shift").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                send("shft".getBytes());
                return true;
            }
        });
        findPreference("blend").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                send(concat("bld".getBytes(), new byte[] { 0x01 }));
                return true;
            }
        });
        findPreference("direct").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                send(concat("bld".getBytes(), new byte[] { (byte) 0xff }));
                return true;
            }
        });
        findPreference("rotate").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                for (int x = 0; x < 10; x++)
                    send("shft".getBytes());
                send("rotr".getBytes());
                return true;
            }
        });
    }
}
