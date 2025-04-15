package com.example.smscallmonitor;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
public class TimeUtil {
    public static String getCurrentFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date now = new Date();
        return sdf.format(now);
    }
    public static String getFormattedTimeFromDate(Date datetime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(datetime);
    }
}
