package com.bgaming.luckyandmagic.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {
    public static String parseDateTime(LocalDateTime localDateTime) {
        return localDateTime.format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                + " UTC" + ZonedDateTime.now(ZoneId.systemDefault()).getOffset();
    }
}
