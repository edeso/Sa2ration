package com.xda.sa2ration;

import android.app.AlertDialog;
import android.content.*;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.text.HtmlCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.xda.sa2ration.databinding.ActivityMainBinding;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String PERSISTENT_PREFERENCES = "preferences";
    public static final String PERSISTENT_COLOR_SATURATION = "persist.sys.sf.color_saturation";
    public static final String PERSISTENT_NATIVE_MODE = "persist.sys.sf.native_mode";
    public static final String PERSISTENT_AUTOSTART = "autostart";
    private static final int STEP_SB = 5;

    private ActivityMainBinding binding;
    private String saturation = "";
    private String mode = "";
    private boolean autostart = true;
    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Enforces the dark theme by default
        //AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        super.onCreate(savedInstanceState);

        if (false && !CommandController.testSudo())
            new AlertDialog.Builder(this)
                    .setMessage(R.string.warning_no_root)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok, (v, a) -> finish())
                    .show();

        // TODO probably need a persistent notification to work reliably
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        receiver = new NewBootReceiver();
        registerReceiver(receiver, filter);

        binding = ActivityMainBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        initSaturationBar();
        initImageView();
        initCm();
        initAutostart();
        initButtons();

        // simple flow layout workaround
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        if (displayMetrics.widthPixels / (float) displayMetrics.heightPixels >= 1.3)
            ((LinearLayout) findViewById(R.id.linearlayout)).setOrientation(LinearLayout.HORIZONTAL);

        // for API 29+ force color of status-/navbar to bright as our bg color is dark
        WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(false);
        insetsController.setAppearanceLightNavigationBars(false);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            binding.getRoot().setFitsSystemWindows(true);
            // set our status-/navbar bg color for APIs where we fisSystemWindows
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
        } else {
            // API35 edge-to-edge fix: apply needed system bar paddings
            ViewCompat.setOnApplyWindowInsetsListener(
                    binding.getRoot(),
                    new OnApplyWindowInsetsListener() {
                        @NonNull
                        @Override
                        public WindowInsetsCompat onApplyWindowInsets(@NonNull View view, @NonNull WindowInsetsCompat insets) {
                            // Retrieve the insets for the system bars (status bar, nav bar, etc.)
                            Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                            // apply to toolbar as top padding (keeping bg color)
                            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) binding.toolbar.getLayoutParams();
                            lp.topMargin = systemBarsInsets.top;
                            // apply to frame to position scrollbar properly
                            view.setPadding(systemBarsInsets.left, view.getPaddingTop(), systemBarsInsets.right, systemBarsInsets.bottom);
                            return WindowInsetsCompat.CONSUMED;
                        }
                    }
            );
        }
        // set toolbar icon
        getSupportActionBar().setIcon(R.drawable.icon_padded);
    }


    // Values are persisted on pause, in order to restore them when system is rebooted
    // anyways, you can force persist them with the save button
    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    /**
     * Initialized bottom buttons, for force save and default values.
     */
    private void initButtons() {
        binding.content.resetButton.setOnClickListener(v -> {
            reset();
            v.setEnabled(false);
            binding.content.saveButton.setEnabled(false);
        });
        binding.content.applyButton.setOnClickListener(v -> {
            CommandController.setSaturation(saturation);
            v.setEnabled(false);
        });
        binding.content.saveButton.setOnClickListener(v -> {
            preference(PERSISTENT_COLOR_SATURATION, saturation);
            preference(PERSISTENT_NATIVE_MODE, mode);
            preference(PERSISTENT_AUTOSTART, Boolean.toString(autostart));
            Toast.makeText(this, R.string.values_are_saved, Toast.LENGTH_SHORT).show();
            v.setEnabled(false);
        });

        refreshResetButton();
        refreshApplyButton();
        binding.content.saveButton.setEnabled(false);
    }

    /**
     * Initializes color management control.
     */
    private void initCm() {
        Switch dci = binding.content.dci;

        String savedMode = preference(PERSISTENT_NATIVE_MODE);
        if (savedMode != null) {
            mode = savedMode;
            boolean enabled = mode.equals("0");
            binding.content.dci.setChecked(enabled);
        }
        dci.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mode = isChecked ? "0" : "";
            if (mode.equals("0"))
                CommandController.setMode(mode);

            refreshResetButton();
            refreshSaveButton();
        });
    }

    private void initAutostart() {
        // restore saved state
        String savedMode = preference(PERSISTENT_AUTOSTART);
        if (savedMode != null)
            binding.content.autostart.setChecked(Boolean.valueOf(savedMode));

        binding.content.autostart.setOnCheckedChangeListener((buttonView, isChecked) -> {
            autostart = isChecked;
            refreshResetButton();
            refreshSaveButton();
        });
    }

    /**
     * Initializes saturation SeekBar control.
     */
    private void initSaturationBar() {
        binding.content.seekBar.incrementProgressBy(STEP_SB);

        String savedSaturation = preference(PERSISTENT_COLOR_SATURATION);
        if (savedSaturation != null)
            saturation = savedSaturation;

        int progress;
        try {
            progress = (int) (Float.parseFloat(saturation) * 100);
        } catch (NumberFormatException e) {
            // ignore, start with 1.00
            progress = 100;
        }
        binding.content.seekBar.setProgress(progress);
        binding.content.textView.setText(format(progress / 100F));

        binding.content.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            String lastValue = "";

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float steppedValue = Math.round((float) seekBar.getProgress() / STEP_SB) * STEP_SB / 100F;
                saturation = format(steppedValue);
                if (lastValue.equals(saturation))
                    return;

                CommandController.setSaturation(saturation);
                binding.content.textView.setText(saturation);
                lastValue = saturation;

                refreshResetButton();
                refreshSaveButton();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    /**
     * Initialized ImageView with its onClick Listener.
     */
    private void initImageView() {
        ImageView preview = findViewById(R.id.imageView);
        preview.setOnClickListener(v -> {
            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.photo_by)
                    .setMessage(HtmlCompat.fromHtml(getResources().getString(R.string.photo_by_desc), HtmlCompat.FROM_HTML_MODE_LEGACY))
                    .show();

            TextView link = alertDialog.findViewById(android.R.id.message);
            link.setLinksClickable(true);
            link.setMovementMethod(LinkMovementMethod.getInstance());
        });
    }

    private void refreshResetButton() {
        boolean resetEnabled = !"".equals(saturation) || !"".equals(mode);
        binding.content.resetButton.setEnabled(resetEnabled);
    }

    private void refreshApplyButton() {
        String systemSaturation = CommandController.getProp(PERSISTENT_COLOR_SATURATION);
        boolean enabled = !saturation.isEmpty() && !saturation.equals(systemSaturation);
        binding.content.applyButton.setEnabled(enabled);
    }

    private void refreshSaveButton() {
        String savedAutostart = preference(PERSISTENT_AUTOSTART);
        boolean enabled = (savedAutostart == null && !autostart) ||
                (savedAutostart != null && Boolean.valueOf(savedAutostart) != autostart) ||
                (!saturation.isEmpty() && !saturation.equals(preference(PERSISTENT_COLOR_SATURATION))) ||
                (!mode.isEmpty() && !mode.equals(preference(PERSISTENT_NATIVE_MODE)));
        binding.content.saveButton.setEnabled(enabled);
    }

    /**
     * Reset values to default ones.
     */
    private void reset() {
        saturation = "";
        binding.content.seekBar.setProgress(100);
        CommandController.setSaturation("1.0");

        mode = "";
        binding.content.dci.setChecked(false);

        autostart = true;
        binding.content.autostart.setChecked(true);

        // unset saved values
        preference(PERSISTENT_COLOR_SATURATION, null);
        preference(PERSISTENT_NATIVE_MODE, null);
        preference(PERSISTENT_AUTOSTART, null);
    }

    /**
     * Formats current progress for its representation in TextView.
     *
     * @param progress current progress.
     * @return formatted string that represents progress.
     */
    private String format(float progress) {
        return String.format(Locale.US, "%.2f", progress);
    }

    /**
     * Local read preference method using current context.
     *
     * @param key
     * @return value
     */
    private String preference(String key) {
        return preference(this, key);
    }

    /**
     * Local write preference method using current context.
     *
     * @param key
     * @param value
     */
    private void preference(String key, String value) {
        preference(this, key, value);
    }

    /**
     * Actual read preference method.
     *
     * @param context
     * @param key
     * @return value or null
     */
    public static String preference(Context context, String key) {
        if (context == null || key == null)
            return null;

        SharedPreferences prefs = context.getSharedPreferences(PERSISTENT_PREFERENCES, Context.MODE_PRIVATE);
        return prefs.getString(key, null);
    }

    /**
     * Actual write preference method.
     * Saves a value to a key. if value is null the preference gets deleted.
     *
     * @param context
     * @param key
     * @param value
     * @return
     */
    public static void preference(Context context, String key, String value) {
        if (context == null || key == null)
            return;

        SharedPreferences prefs = context.getSharedPreferences(PERSISTENT_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (value == null)
            editor.remove(key);
        else
            editor.putString(key, value);

        editor.apply();
    }
}
