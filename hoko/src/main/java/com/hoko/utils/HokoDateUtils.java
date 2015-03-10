package com.hoko.utils;


import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utilities methods for manipulating dates in iso8601 format. This is much much faster and GC
 * friendly than using SimpleDateFormat so highly suitable if you (un)serialize lots of date
 * objects.
 */
public class HokoDateUtils {

    /**
     * ID to represent the 'GMT' string
     */
    private static final String GMT_ID = "GMT";

    /**
     * The GMT timezone
     */
    private static final TimeZone TIMEZONE_GMT = TimeZone.getTimeZone(GMT_ID);

    /*
    /**********************************************************
    /* Static factories
    /**********************************************************
     */

    /**
     * Accessor for static GMT timezone instance.
     *
     * @return The GMT Timezone
     */
    public static TimeZone timeZoneGMT() {
        return TIMEZONE_GMT;
    }

    /*
    /**********************************************************
    /* Formatting
    /**********************************************************
     */

    /**
     * Format a date into 'yyyy-MM-ddThh:mm:ss.sssZ' (GMT timezone, no milliseconds precision)
     *
     * @param date the date to format
     * @return the date formatted as 'yyyy-MM-ddThh:mm:ss.sssZ'
     */
    public static String format(Date date) {
        return format(date, true, TIMEZONE_GMT, false);
    }

    /**
     * Format a date into 'yyyy-MM-dd'
     *
     * @param date the date to format
     * @return the date formatted as 'yyyy-MM-dd'
     */
    public static String formatDate(Date date) {
        return format(date, false, TIMEZONE_GMT, true);
    }

    /**
     * Format a date into 'yyyy-MM-ddThh:mm:ss[.sss]Z' (GMT timezone)
     *
     * @param date   the date to format
     * @param millis true to include millis precision otherwise false
     * @return the date formatted as 'yyyy-MM-ddThh:mm:ss[.sss]Z'
     */
    public static String format(Date date, boolean millis) {
        return format(date, millis, TIMEZONE_GMT, false);
    }

    /**
     * Format date into yyyy-MM-ddThh:mm:ss[.sss][Z|[+-]hh:mm]
     *
     * @param date     the date to format
     * @param millis   true to include millis precision otherwise false
     * @param tz       timezone to use for the formatting (GMT will produce 'Z')
     * @param dateOnly boolean whether to return only the date.
     * @return the date formatted as yyyy-MM-ddThh:mm:ss[.sss][Z|[+-]hh:mm]
     */
    public static String format(Date date, boolean millis, TimeZone tz, boolean dateOnly) {
        if (date == null)
            return null;
        Calendar calendar = new GregorianCalendar(tz, Locale.US);
        calendar.setTime(date);

        // estimate capacity of buffer as close as we can (yeah, that's pedantic ;)
        int capacity = dateOnly ? "yyyy-MM-dd".length() : "yyyy-MM-ddThh:mm:ss".length();
        if (!dateOnly) {
            capacity += millis ? ".sss".length() : 0;
            capacity += tz.getRawOffset() == 0 ? "Z".length() : "+hh:mm".length();
        }
        StringBuilder formatted = new StringBuilder(capacity);

        padInt(formatted, calendar.get(Calendar.YEAR), "yyyy".length());
        formatted.append('-');
        padInt(formatted, calendar.get(Calendar.MONTH) + 1, "MM".length());
        formatted.append('-');
        padInt(formatted, calendar.get(Calendar.DAY_OF_MONTH), "dd".length());
        if (!dateOnly) {
            formatted.append('T');
            padInt(formatted, calendar.get(Calendar.HOUR_OF_DAY), "hh".length());
            formatted.append(':');
            padInt(formatted, calendar.get(Calendar.MINUTE), "mm".length());
            formatted.append(':');
            padInt(formatted, calendar.get(Calendar.SECOND), "ss".length());
            if (millis) {
                formatted.append('.');
                padInt(formatted, calendar.get(Calendar.MILLISECOND), "sss".length());
            }

            int offset = tz.getOffset(calendar.getTimeInMillis());
            if (offset != 0) {
                int hours = Math.abs((offset / (60 * 1000)) / 60);
                int minutes = Math.abs((offset / (60 * 1000)) % 60);
                formatted.append(offset < 0 ? '-' : '+');
                padInt(formatted, hours, "hh".length());
                formatted.append(':');
                padInt(formatted, minutes, "mm".length());
            } else {
                formatted.append('Z');
            }
        }

        return formatted.toString();
    }

    /*
    /**********************************************************
    /* Parsing
    /**********************************************************
     */

    /**
     * Parse a date from ISO-8601 formatted string. It expects a format
     * yyyy-MM-ddThh:mm:ss[.sss][Z|[+-]hh:mm]
     *
     * @param date ISO string to parse in the appropriate format.
     * @return the parsed date
     * @throws IllegalArgumentException if the date is not in the appropriate format
     */
    public static Date parse(String date) {
        Exception fail;
        try {
            int offset = 0;

            // extract year
            int year = parseInt(date, offset, offset += 4);
            checkOffset(date, offset, '-');

            // extract month
            int month = parseInt(date, offset += 1, offset += 2);
            checkOffset(date, offset, '-');

            // extract day
            int day = parseInt(date, offset += 1, offset += 2);
            checkOffset(date, offset, 'T');

            // extract hours, minutes, seconds and milliseconds
            int hour = parseInt(date, offset += 1, offset += 2);
            checkOffset(date, offset, ':');

            int minutes = parseInt(date, offset += 1, offset += 2);
            checkOffset(date, offset, ':');

            int seconds = parseInt(date, offset += 1, offset += 2);
            // milliseconds can be optional in the format
            // always use 0 otherwise returned date will include millis of current time
            int milliseconds = 0;
            if (date.charAt(offset) == '.') {
                checkOffset(date, offset, '.');
                milliseconds = parseInt(date, offset += 1, offset += 3);
            }

            // extract timezone
            String timezoneId;
            char timezoneIndicator = date.charAt(offset);
            if (timezoneIndicator == '+' || timezoneIndicator == '-') {
                timezoneId = GMT_ID + date.substring(offset);
            } else if (timezoneIndicator == 'Z') {
                timezoneId = GMT_ID;
            } else {
                throw new IndexOutOfBoundsException("Invalid time zone indicator "
                        + timezoneIndicator);
            }
            TimeZone timezone = TimeZone.getTimeZone(timezoneId);
            if (!timezone.getID().equals(timezoneId)) {
                throw new IndexOutOfBoundsException();
            }

            Calendar calendar = new GregorianCalendar(timezone);
            calendar.setLenient(false);
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month - 1);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minutes);
            calendar.set(Calendar.SECOND, seconds);
            calendar.set(Calendar.MILLISECOND, milliseconds);

            return calendar.getTime();
        } catch (IndexOutOfBoundsException e) {
            fail = e;
        } catch (NumberFormatException e) {
            fail = e;
        } catch (IllegalArgumentException e) {
            fail = e;
        }
        String input = (date == null) ? null : ('"' + date + "'");
        throw new IllegalArgumentException("Failed to parse date [" + input
                + "]: " + fail.getMessage(), fail);
    }

    /**
     * Check if the expected character exist at the given offset of the
     *
     * @param value    the string to check at the specified offset
     * @param offset   the offset to look for the expected character
     * @param expected the expected character
     * @throws IndexOutOfBoundsException if the expected character is not found
     */
    private static void checkOffset(String value, int offset, char expected) throws
            IndexOutOfBoundsException {
        char found = value.charAt(offset);
        if (found != expected) {
            throw new IndexOutOfBoundsException("Expected '" + expected + "' character but "
                    + "found '" + found + "'");
        }
    }

    /**
     * Parse an integer located between 2 given offsets in a string
     *
     * @param value      the string to parse
     * @param beginIndex the start index for the integer in the string
     * @param endIndex   the end index for the integer in the string
     * @return the int
     * @throws NumberFormatException if the value is not a number
     */
    private static int parseInt(String value, int beginIndex, int endIndex) throws
            NumberFormatException {
        if (beginIndex < 0 || endIndex > value.length() || beginIndex > endIndex) {
            throw new NumberFormatException(value);
        }
        // use same logic as in Integer.parseInt() but less generic we're not supporting negative
        // values
        int i = beginIndex;
        int result = 0;
        int digit;
        if (i < endIndex) {
            digit = Character.digit(value.charAt(i++), 10);
            if (digit < 0) {
                throw new NumberFormatException("Invalid number: " + value);
            }
            result = -digit;
        }
        while (i < endIndex) {
            digit = Character.digit(value.charAt(i++), 10);
            if (digit < 0) {
                throw new NumberFormatException("Invalid number: " + value);
            }
            result *= 10;
            result -= digit;
        }
        return -result;
    }

    /**
     * Zero pad a number to a specified length
     *
     * @param buffer buffer to use for padding
     * @param value  the integer value to pad if necessary.
     * @param length the length of the string we should zero pad
     */
    private static void padInt(StringBuilder buffer, int value, int length) {
        String strValue = Integer.toString(value);
        for (int i = length - strValue.length(); i > 0; i--) {
            buffer.append('0');
        }
        buffer.append(strValue);
    }
}