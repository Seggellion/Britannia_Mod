package com.seggellion.britannia_mod.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Ultra-thin façade around Log4j 2 so you can log with a single static import.
 *
 * Usage:
 *     OLog.info("Something happened: {}", value);
 *     OLog.error(ex);
 */
public final class OLog {

    private static final Logger LOG = LogManager.getLogger("Britannia");

    private OLog() {}   // utility – no instances

    /* ------------------------------------------------------------------ */
    /* Convenience methods – uses Log4j formatting ({}) instead of String.format */
    /* ------------------------------------------------------------------ */

    public static void debug(String msg, Object... args) { if (LOG.isDebugEnabled()) LOG.debug(msg, args); }
    public static void info (String msg, Object... args) { LOG.info (msg, args); }
    public static void warn (String msg, Object... args) { LOG.warn (msg, args); }

    public static void error(String msg, Object... args) { LOG.error(msg, args); }
    public static void error(Throwable t)                { LOG.error(t); }
    public static void error(String msg, Throwable t)    { LOG.error(msg, t); }
}
