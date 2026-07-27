package com.xda.sa2ration;

import android.util.Log;

import java.io.*;
import java.util.concurrent.*;

public class CommandController {

    static ExecutorService executor = new ThreadPoolExecutor(
            1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(2),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.DiscardOldestPolicy());
    //Executors.newSingleThreadExecutor();

    /**
     * Gets property from Android system
     *
     * @param systemProperty property name
     * @return String may be empty
     */
    public static String getProp(String systemProperty) {
        return execCommand("getprop " + systemProperty);
    }

    /**
     * Sets a system property value
     *
     * @param systemProperty property name
     * @param value          new value for the property
     * @return String may be empty
     */
    public static String setProp(String systemProperty, String value) {
        return execCommand("setprop " + systemProperty + " " + value);
    }

    /**
     * Executes a set of commands as root.
     *
     * @param commands String array containing the commands.
     * @return the result, if any.
     */
    private static String execCommand(String... commands) {
        StringBuilder sb = new StringBuilder();
        try {
            Process su = Runtime.getRuntime().exec("su");
            try (DataOutputStream outputStream = new DataOutputStream(su.getOutputStream())) {
                for (String command : commands) {
                    outputStream.writeBytes(command + "\n");
                    outputStream.flush();
                }
                outputStream.writeBytes("exit\n");
                outputStream.flush();
                su.waitFor();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(su.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.e("No Root?", e.getMessage());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return sb.toString();
    }

    public static void setSaturation(String saturation) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                execCommand(
                        "setprop " + MainActivity.PERSISTENT_COLOR_SATURATION + " " + saturation,
                        "service call SurfaceFlinger 1022 f " + saturation
                );
            }
        });
    }

    public static void setMode(String mode) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                execCommand(
                        "service call SurfaceFlinger 1023 i32 " + mode,
                        "setprop " + MainActivity.PERSISTENT_NATIVE_MODE + " " + mode
                );
            }
        });
    }

    /**
     * Test whether user has root access.
     *
     * @return true if user has root access, false otherwise.
     */
    public static boolean testSudo() {
        boolean success = false;
        try {
            Process su = Runtime.getRuntime().exec("su");

            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            DataInputStream inputStream = new DataInputStream(su.getInputStream());
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            while (bufferedReader.readLine() != null) {
                bufferedReader.readLine();
            }
            su.waitFor();
            success = su.exitValue() != 13;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return success;
    }

}
