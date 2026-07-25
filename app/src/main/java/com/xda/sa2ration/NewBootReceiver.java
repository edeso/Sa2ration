package com.xda.sa2ration;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class NewBootReceiver extends BroadcastReceiver {

    /**
     * Receives new boot event. Applies persisted saturation and cm values, if present.
     * @param context context passed
     * @param intent intent passed
     */
    @Override
    public void onReceive(Context context, Intent intent) {

        switch (intent.getAction()) {
            case Intent.ACTION_BOOT_COMPLETED:
                String savedAutostart = MainActivity.preference( context, MainActivity.PERSISTENT_AUTOSTART);
                // autostart disabled?
                if (!Boolean.valueOf(savedAutostart))
                    return;
                break;
            case Intent.ACTION_SCREEN_ON:
            case Intent.ACTION_USER_PRESENT:
                // TODO: possibly implement a persistent notification and apply setting on every login
                //Toast.makeText(context, intent.getAction().toString(), Toast.LENGTH_SHORT).show();
            default:
                return;
        }

        String saturation = MainActivity.preference( context, MainActivity.PERSISTENT_COLOR_SATURATION);
        if (saturation != null)
            CommandController.setSaturation(saturation);

        String mode = MainActivity.preference( context, MainActivity.PERSISTENT_NATIVE_MODE);
        if (mode != null)
            CommandController.setMode(mode);
    }

}
