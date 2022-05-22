package cn.ciphermagic.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Time Util
 *
 * @author cipher
 */
public class DateTimeUtil {

    private static final Logger LOG = LoggerFactory.getLogger(DateTimeUtil.class);

    /**
     * Convert format string to date
     *
     * @param strDate string of date
     * @param format  date format
     * @return date
     */
    public static Date strToDate(String strDate, String format) {
        SimpleDateFormat formatter = new SimpleDateFormat(format);
        ParsePosition pos = new ParsePosition(0);
        return formatter.parse(strDate, pos);
    }

    /**
     * Get the time string according to the format
     *
     * @param format date format
     * @return date string
     */
    public static String nowDateByPattern(String format) {
        Date now = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat(format);
        return dateFormat.format(now);
    }

    /**
     * Get the time string according to the format
     *
     * @param date   date
     * @param format date format
     * @return date string
     */
    public static String formatDateByPattern(Date date, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        String formatTimeStr = null;
        if (date != null) {
            formatTimeStr = sdf.format(date);
        }
        return formatTimeStr;
    }

    /**
     * is leap year
     *
     * @param year year
     * @return is leap year
     */
    public static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    /**
     * date to cron expression
     *
     * @param date date
     * @return cron expression
     */
    public static String getCron(Date date) {
        String dateFormat = "ss mm HH dd MM ? yyyy";
        return formatDateByPattern(date, dateFormat);
    }

    /**
     * long type of time to cron expression
     *
     * @param time long type of time
     * @return cron expression
     */
    public static String getCron(Long time) {
        Date date = new Date(time);
        String dateFormat = "ss mm HH dd MM ? yyyy";
        return formatDateByPattern(date, dateFormat);
    }

    /**
     * cron expression to time
     *
     * @param cron cron expression
     * @return long type of time
     */
    public static Long cron2Time(String cron) {
        Long time = null;
        SimpleDateFormat sdf = new SimpleDateFormat("ss mm HH dd MM ? yyyy");
        try {
            Date date = sdf.parse(cron);
            time = date.getTime();
        } catch (ParseException e) {
            LOG.error("" + e);
        }
        return time;
    }

    /**
     * get current timestamp
     *
     * @return current timestamp
     * @see java.sql.Timestamp
     */
    public static Timestamp getTimestamp() {
        return new Timestamp(System.currentTimeMillis());
    }

}
