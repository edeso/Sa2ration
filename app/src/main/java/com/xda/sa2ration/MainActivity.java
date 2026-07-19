package com.xda.sa2ration;

import android.app.AlertDialog;
import android.content.*;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.text.HtmlCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.xda.sa2ration.databinding.ActivityMainBinding;
import java8.util.Optional;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String PERSISTENT_COLOR_SATURATION = "persist.sys.sf.color_saturation";
    public static final String PERSISTENT_NATIVE_MODE = "persist.sys.sf.native_mode";
    private static final int STEP_SB = 5;

    private ActivityMainBinding binding;
    private String saturation = "";
    private String mode = "";
    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!CommandController.testSudo()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.warning_no_root)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok, (v, a) -> finish())
                    .show();

        }

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
        initButtons();

        // simple flow layout workaround
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        if ( displayMetrics.widthPixels/displayMetrics.heightPixels >= 2 )
            ((LinearLayout) findViewById(R.id.linearlayout)).setOrientation(LinearLayout.HORIZONTAL);

        if ( Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ) {
             binding.getRoot().setFitsSystemWindows(true);
        }else{
            // API35 edge-to-edge fix: apply needed system bar paddings
            ViewCompat.setOnApplyWindowInsetsListener(
                    binding.getRoot(),
                    new OnApplyWindowInsetsListener() {
                        @NonNull
                        @Override
                        public WindowInsetsCompat onApplyWindowInsets(@NonNull View view, @NonNull WindowInsetsCompat insets) {
                            // Retrieve the insets for the system bars (status bar, nav bar, etc.)
                            Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
                            // apply to toolbar as top padding (keeping bg color)
                            View toolbar = binding.toolbar;
                            toolbar.setPadding(systemBarsInsets.left,systemBarsInsets.top,systemBarsInsets.right,toolbar.getPaddingBottom());
                            // apply to frame to position scrollbar properly
                            view.setPadding(systemBarsInsets.left,view.getPaddingTop(),systemBarsInsets.right,systemBarsInsets.bottom);
                            return WindowInsetsCompat.CONSUMED;
                        }
                    }
            );
        }
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
        binding.content.reset.setOnClickListener(v -> reset());
        binding.content.apply.setOnClickListener(v -> {
            super.onPause();
            preference( PERSISTENT_COLOR_SATURATION, saturation);
            preference( PERSISTENT_NATIVE_MODE, mode);
            Toast.makeText(this, R.string.values_are_saved, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Initializes color management control.
     */
    private void initCm() {
        Switch dci = binding.content.dci;

        String savedMode = preference( PERSISTENT_NATIVE_MODE );
        if (savedMode != null) {
            mode = savedMode;
            boolean enabled = mode.equals("0");
            binding.content.dci.setChecked(enabled);
        }
        dci.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mode = isChecked ? "0" : "";
            if (mode.equals("0"))
                CommandController.execCommand("service call SurfaceFlinger 1023 i32 " + mode);
            CommandController.setProp(PERSISTENT_NATIVE_MODE, mode);
        });
    }

    /**
     * Initializes saturation SeekBar control.
     */
    private void initSaturationBar() {
        binding.content.seekBar.incrementProgressBy(STEP_SB);
        binding.content.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            String lastValue = "";
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float steppedValue = Math.round( (float)seekBar.getProgress() / STEP_SB) * STEP_SB / 100F;
                saturation = format(steppedValue);
                if (lastValue.equals(saturation))
                    return;

                CommandController.execCommand("setprop " + PERSISTENT_COLOR_SATURATION + " " + saturation,
                        "service call SurfaceFlinger 1022 f " + saturation);
                binding.content.textView.setText(saturation);
                lastValue = saturation;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        String savedSaturation = preference( PERSISTENT_COLOR_SATURATION );
        if (savedSaturation != null)
            saturation = savedSaturation;

        int progress;
        try {
            progress = (int) (Float.parseFloat(saturation) * 100);
        } catch ( NumberFormatException e) {
            // ignore, start with 1.00
            progress = 100;
            saturation = "1.00";
        }
        binding.content.seekBar.setProgress(progress);
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

    /**
     * Reset values to default ones.
     */
    private void reset() {
        saturation = "1.00";
        mode = "";
        binding.content.seekBar.setProgress(100);
        binding.content.dci.setChecked(false);
        CommandController.execCommand("setprop " + PERSISTENT_COLOR_SATURATION + " " + saturation,
                "service call SurfaceFlinger 1022 f " + saturation);
        // unset saved values
        preference( PERSISTENT_COLOR_SATURATION, null);
        preference( PERSISTENT_NATIVE_MODE, null);
    }

    /**
     * Formats current progress for its representation in TextView.
     * @param progress current progress.
     * @return formatted string that represents progress.
     */
    private String format(float progress) {
        return String.format(Locale.US, "%.2f", progress);
    }

    /**
     *  Local preference method using current context.
     * @param key
     * @param values
     * @return
     */
    private String preference( String key, String ... values ){
        return preference( this, key, values);
    }

    /**
     *  Global preference method.
     *  Saves a value to a key. if value is null the preference is deleted.
     * @param context
     * @param key
     * @param values
     * @return
     */
    public static String preference(Context context, String key, String ... values ){
        if ( context == null || key == null )
            return null;

        SharedPreferences prefs = context.getSharedPreferences("preferences", 0);
        if ( values == null || values.length > 0 ) {
            String value = values == null ? null : values[0];
            SharedPreferences.Editor editor = prefs.edit();
            if ( value == null || value.isEmpty() )
                editor.remove( key );
            else
                editor.putString( key, value );

            editor.apply();
            return value;
        }

        return prefs.getString( key, null );
    }
}
