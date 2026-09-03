/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.common.utils;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 日期处理
 *
 * @author Mark sunlightcs@gmail.com
 */
public class DateUtils {
	/** 时间格式(yyyy-MM-dd) */
	public final static String DATE_PATTERN = "yyyy-MM-dd";
	/** 时间格式(yyyy-MM-dd HH:mm:ss) */
	public final static String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 日期格式化 日期格式为：yyyy-MM-dd
     * @param date  日期
     * @return  返回yyyy-MM-dd格式日期
     */
	public static String format(Date date) {
        return format(date, DATE_PATTERN);
    }

    /**
     * 日期格式化 日期格式为：yyyy-MM-dd
     * @param date  日期
     * @param pattern  格式，如：DateUtils.DATE_TIME_PATTERN
     * @return  返回yyyy-MM-dd格式日期
     */
    public static String format(Date date, String pattern) {
        if(date != null){
            SimpleDateFormat df = new SimpleDateFormat(pattern);
            return df.format(date);
        }
        return null;
    }

    /**
     * 返回格式化日期
     * @param timestamp 时间戳 单位秒
     * @return
     */
    public static String format(Long timestamp) {
        if(timestamp != null){
            SimpleDateFormat df = new SimpleDateFormat(DATE_TIME_PATTERN);
            return df.format(new Date(timestamp * 1000));
        }
        return null;
    }

    /**
     * 返回格式化日期
     * @param timestamp 单位 秒
     * @param pattern
     * @return
     */
    public static String format(Long timestamp, String pattern) {
        DateFormat df = new SimpleDateFormat(pattern);

        return df.format(new Date(timestamp*1000));
    }

    /**
     * 字符串转换成日期
     * @param strDate 日期字符串
     * @param pattern 日期的格式，如：DateUtils.DATE_TIME_PATTERN
     */
    public static Date stringToDate(String strDate, String pattern) {
        if (StringUtils.isBlank(strDate)){
            return null;
        }

        DateTimeFormatter fmt = DateTimeFormat.forPattern(pattern);
        return fmt.parseLocalDateTime(strDate).toDate();
    }

    /**
     * 根据周数，获取开始日期、结束日期
     * @param week  周期  0本周，-1上周，-2上上周，1下周，2下下周
     * @return  返回date[0]开始日期、date[1]结束日期
     */
    public static Date[] getWeekStartAndEnd(int week) {
        DateTime dateTime = new DateTime();
        LocalDate date = new LocalDate(dateTime.plusWeeks(week));

        date = date.dayOfWeek().withMinimumValue();
        Date beginDate = date.toDate();
        Date endDate = date.plusDays(6).toDate();
        return new Date[]{beginDate, endDate};
    }

    /**
     * 对日期的【秒】进行加/减
     *
     * @param date 日期
     * @param seconds 秒数，负数为减
     * @return 加/减几秒后的日期
     */
    public static Date addDateSeconds(Date date, int seconds) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusSeconds(seconds).toDate();
    }

    /**
     * 对日期的【分钟】进行加/减
     *
     * @param date 日期
     * @param minutes 分钟数，负数为减
     * @return 加/减几分钟后的日期
     */
    public static Date addDateMinutes(Date date, int minutes) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusMinutes(minutes).toDate();
    }

    /**
     * 对日期的【小时】进行加/减
     *
     * @param date 日期
     * @param hours 小时数，负数为减
     * @return 加/减几小时后的日期
     */
    public static Date addDateHours(Date date, int hours) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusHours(hours).toDate();
    }

    /**
     * 对日期的【天】进行加/减
     *
     * @param date 日期
     * @param days 天数，负数为减
     * @return 加/减几天后的日期
     */
    public static Date addDateDays(Date date, int days) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusDays(days).toDate();
    }

    /**
     * 对日期的【周】进行加/减
     *
     * @param date 日期
     * @param weeks 周数，负数为减
     * @return 加/减几周后的日期
     */
    public static Date addDateWeeks(Date date, int weeks) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusWeeks(weeks).toDate();
    }

    /**
     * 对日期的【月】进行加/减
     *
     * @param date 日期
     * @param months 月数，负数为减
     * @return 加/减几月后的日期
     */
    public static Date addDateMonths(Date date, int months) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusMonths(months).toDate();
    }

    /**
     * 对日期的【年】进行加/减
     *
     * @param date 日期
     * @param years 年数，负数为减
     * @return 加/减几年后的日期
     */
    public static Date addDateYears(Date date, int years) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusYears(years).toDate();
    }

    /**
     * 根据时间戳获取月份 YYYY-MM 格式
     * @param timestamp 时间戳 单位秒
     * @return
     */
    public static String getMonthByTimeStamp(Long timestamp) {
        DateFormat df = new SimpleDateFormat("YYYY-MM");

        return df.format(new Date(timestamp * 1000));
    }

    /**
     * 检查是否垮月
     * @param starttime 起始时间戳 单位秒
     * @param endtime 截止时间戳 单位秒
     * @return true 没有垮月
     */
    public static boolean checkCrossMonth(Long starttime,Long endtime) {
        if (starttime == null || endtime == null) {
            return false;
        }

        Calendar cal1 = Calendar.getInstance();
        cal1.setTimeInMillis(starttime*1000);

        Calendar cal2 = Calendar.getInstance();
        cal2.setTimeInMillis(endtime*1000);

        int year1 = cal1.get(Calendar.YEAR);
        int year2 = cal2.get(Calendar.YEAR);
        int month1 = cal1.get(Calendar.MONTH);
        int month2 = cal2.get(Calendar.MONTH);

        return year1 == year2 && month1 == month2;
    }

    /**
     * 获取某天开始时间秒数 如 2020-08-24 00:00:00 对应的秒数
     * @param seconds 传入时间戳 单位 秒
     * @return 时间戳 秒
     */
    public static Long getStartTime(Long seconds) {
        if (seconds == null)
            return null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(seconds*1000);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        Long startTime = calendar.getTimeInMillis()/1000;

        return startTime;
    }

    /**
     * 获取某天结束时间秒数 如 2020-08-24 23:59:59 对应的秒数
     * @param seconds 传入时间戳 单位 秒
     * @return 时间戳 秒
     */
    public static Long getEndTime(Long seconds) {
        if (seconds == null)
            return null;
        Long startTime  = getStartTime(seconds);
        Long endTime = startTime + (24*60*60-1);

        return endTime;
    }

    /**
     * 时间戳加减处理 时间戳单位 秒
     * @param timestamp 时间戳
     * @param addsub 加减的数值
     * @param unit 加减数单位 1：小时 2：月
     * @param operation 操作（加、减） 1：加 2：减
     * @return
     */
    public static Long dealTimestamp(Long timestamp,Integer addsub,Integer unit,Integer operation) {
        Long result = 0L;
        if (1 == operation) {//加
            if (1 == unit) {//小时
                result = timestamp + (addsub * 60 * 60L);
            } else if (2 == unit) {//月
                result = timestamp + (addsub * 30 * 60 * 60L);
            } else {
                return null;
            }
        } else if (2 == operation) {//减
            if (1 == unit) {//小时
                result = timestamp - (addsub * 60 * 60L);
            } else if (2 == unit) {//月
                result = timestamp - (addsub * 30 * 60 * 60L);
            } else {
                return null;
            }
        } else {
            return null;
        }

        return result;
    }
}
