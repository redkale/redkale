/*
 *
 */
package org.redkale.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * 时间日期工具类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public final class Times {

    private static final int ZONE_RAW_OFFSET = TimeZone.getDefault().getRawOffset();

    static final String FORMAT_DAY = "%1$tY-%1$tm-%1$td"; // yyyy-MM-dd

    static final String FORMAT_SECONDS = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS"; // yyyy-MM-dd HH:mm:ss

    static final String FORMAT_MILLS = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL"; // yyyy-MM-dd HH:mm:ss.fff

    private Times() {
        // do nothind
    }

    /**
     * 获取格式为yyyy-MM-dd HH:mm:ss的当前时间
     *
     * @return 格式为yyyy-MM-dd HH:mm:ss的时间值
     */
    public static String now() {
        return String.format(FORMAT_SECONDS, System.currentTimeMillis());
    }

    /**
     * 获取格式为yyyy-MM-dd HH:mm:ss.fff的当前时间
     *
     * @return 格式为yyyy-MM-dd HH:mm:ss.fff的时间值
     */
    public static String nowMillis() {
        return String.format(FORMAT_MILLS, System.currentTimeMillis());
    }

    /**
     * 获取当天2015-01-01格式的string值
     *
     * @return 2015-01-01格式的string值
     */
    public static String nowDay() {
        return String.format(FORMAT_DAY, System.currentTimeMillis());
    }

    /**
     * 将指定时间格式化为 yyyy-MM-dd
     *
     * @param time 待格式化的时间
     * @return 格式为yyyy-MM-dd的时间值
     */
    public static String formatDay(long time) {
        return String.format(FORMAT_DAY, time);
    }

    /**
     * 将指定时间格式化为 yyyy-MM-dd HH:mm:ss
     *
     * @param time 待格式化的时间
     * @return 格式为yyyy-MM-dd HH:mm:ss的时间值
     */
    public static String formatTime(long time) {
        return String.format(FORMAT_SECONDS, time);
    }

    /**
     * 将时间值转换为长度为9的36进制值
     *
     * @param time 时间值
     * @return 36进制时间值
     */
    public static String format36time(long time) {
        return Long.toString(time, 36);
    }

    /**
     * 获取当天凌晨零点的格林时间
     *
     * @return 毫秒数
     */
    public static long midnight() {
        return midnight(System.currentTimeMillis());
    }

    /**
     * 获取指定时间当天凌晨零点的格林时间
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    public static long midnight(long time) {
        return (time + ZONE_RAW_OFFSET) / 86400000 * 86400000 - ZONE_RAW_OFFSET;
    }

    /**
     * 获取当天20151231格式的int值
     *
     * @return 20151231格式的int值
     */
    public static int today() {
        java.time.LocalDate today = java.time.LocalDate.now();
        return today.getYear() * 10000 + today.getMonthValue() * 100 + today.getDayOfMonth();
    }

    /**
     * 获取当天151231格式的int值
     *
     * @return 151231格式的int值
     */
    public static int todayYYMMDD() {
        java.time.LocalDate today = java.time.LocalDate.now();
        return today.getYear() % 100 * 10000 + today.getMonthValue() * 100 + today.getDayOfMonth();
    }

    /**
     * 获取当天1512312359格式的int值
     *
     * @return 1512312359格式的int值
     */
    public static int todayYYMMDDHHmm() {
        java.time.LocalDateTime today = java.time.LocalDateTime.now();
        return today.getYear() % 100 * 100_00_00_00
                + today.getMonthValue() * 100_00_00
                + today.getDayOfMonth() * 100_00
                + today.getHour() * 100
                + today.getMinute();
    }

    /**
     * 获取当天20151231235959格式的int值
     *
     * @return 20151231235959格式的int值
     */
    public static long todayYYYYMMDDHHmmss() {
        java.time.LocalDateTime today = java.time.LocalDateTime.now();
        return today.getYear() * 100_00_00_00_00L
                + today.getMonthValue() * 100_00_00_00
                + today.getDayOfMonth() * 100_00_00
                + today.getHour() * 100_00
                + today.getMinute() * 100
                + today.getSecond();
    }

    /**
     * 获取当天151231235959格式的int值
     *
     * @return 151231235959格式的int值
     */
    public static long todayYYMMDDHHmmss() {
        java.time.LocalDateTime today = java.time.LocalDateTime.now();
        return today.getYear() % 100 * 100_00_00_00_00L
                + today.getMonthValue() * 100_00_00_00
                + today.getDayOfMonth() * 100_00_00
                + today.getHour() * 100_00
                + today.getMinute() * 100
                + today.getSecond();
    }

    /**
     * 获取明天20151230格式的int值
     *
     * @return 20151230格式的int值
     */
    public static int tomorrow() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 获取明天151230格式的int值
     *
     * @return 151230格式的int值
     */
    public static int tomorrowYYMMDD() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.get(Calendar.YEAR) % 100 * 10000
                + (cal.get(Calendar.MONTH) + 1) * 100
                + cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 获取昨天20151230格式的int值
     *
     * @return 20151230格式的int值
     */
    public static int yesterday() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 获取昨天151230格式的int值
     *
     * @return 151230格式的int值
     */
    public static int yesterdayYYMMDD() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        return cal.get(Calendar.YEAR) % 100 * 10000
                + (cal.get(Calendar.MONTH) + 1) * 100
                + cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 获取指定时间的20160202格式的int值
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    public static int yyyyMMdd(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 获取指定时间的160202格式的int值
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    public static int yyMMdd(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        return cal.get(Calendar.YEAR) % 100 * 10000
                + (cal.get(Calendar.MONTH) + 1) * 100
                + cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 获取当天16020223格式的int值
     *
     * @param time 指定时间
     * @return 16020223格式的int值
     */
    public static int yyMMDDHHmm(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        return cal.get(Calendar.YEAR) % 100 * 100_00_00
                + (cal.get(Calendar.MONTH) + 1) * 100_00
                + cal.get(Calendar.DAY_OF_MONTH) * 100
                + cal.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 获取时间点所在星期的周一
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    public static long monday(long time) {
        ZoneId zid = ZoneId.systemDefault();
        Instant instant = Instant.ofEpochMilli(time);
        LocalDate ld = instant.atZone(zid).toLocalDate();
        ld = ld.minusDays(ld.getDayOfWeek().getValue() - 1);
        return ld.atStartOfDay(zid).toInstant().toEpochMilli();
    }

    /**
     * 获取时间点所在星期的周日
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    public static long sunday(long time) {
        ZoneId zid = ZoneId.systemDefault();
        Instant instant = Instant.ofEpochMilli(time);
        LocalDate ld = instant.atZone(zid).toLocalDate();
        ld = ld.plusDays(7 - ld.getDayOfWeek().getValue());
        return ld.atStartOfDay(zid).toInstant().toEpochMilli();
    }

    /**
     * 获取时间点所在月份的1号
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    public static long monthFirstDay(long time) {
        ZoneId zid = ZoneId.systemDefault();
        Instant instant = Instant.ofEpochMilli(time);
        LocalDate ld = instant.atZone(zid).toLocalDate().withDayOfMonth(1);
        return ld.atStartOfDay(zid).toInstant().toEpochMilli();
    }

    /**
     * 获取时间点所在月份的最后一天
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    public static long monthLastDay(long time) {
        ZoneId zid = ZoneId.systemDefault();
        Instant instant = Instant.ofEpochMilli(time);
        LocalDate ld = instant.atZone(zid).toLocalDate();
        ld = ld.withDayOfMonth(ld.lengthOfMonth());
        return ld.atStartOfDay(zid).toInstant().toEpochMilli();
    }

    /**
     * 将时间格式化, 支持%1$ty 和 %ty两种格式
     *
     * @param format 格式
     * @param size 带%t的个数，值小于0则需要计算
     * @param time 时间
     * @since 2.7.0
     * @return 时间格式化
     */
    public static String formatTime(String format, int size, Object time) {
        if (size < 0) {
            int c = 0;
            if (!format.contains("%1$")) {
                for (char ch : format.toCharArray()) {
                    if (ch == '%') {
                        c++;
                    }
                }
            }
            size = c;
        }
        if (size <= 1) {
            return String.format(format, time);
        }
        if (size == 2) {
            return String.format(format, time, time);
        }
        if (size == 3) {
            return String.format(format, time, time, time);
        }
        if (size == 4) {
            return String.format(format, time, time, time, time);
        }
        if (size == 5) {
            return String.format(format, time, time, time, time, time);
        }
        if (size == 6) {
            return String.format(format, time, time, time, time, time, time);
        }
        if (size == 7) {
            return String.format(format, time, time, time, time, time, time, time);
        }
        if (size == 8) {
            return String.format(format, time, time, time, time, time, time, time);
        }
        Object[] args = new Object[size];
        for (int i = 0; i < size; i++) {
            args[i] = time;
        }
        return String.format(format, args);
    }
}
