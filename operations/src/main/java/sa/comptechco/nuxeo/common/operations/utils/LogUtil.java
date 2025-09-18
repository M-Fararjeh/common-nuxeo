package sa.comptechco.nuxeo.common.operations.utils;

import org.apache.commons.logging.Log;

public class LogUtil {

    public static void log(Log logger, String message, LogEnum logType) {
        if (logType.equals(LogEnum.DEBUG)) {
            if (logger.isDebugEnabled()) {
                logger.debug(message);
            }
        } else if (logType.equals(LogEnum.INFO)) {
            if (logger.isInfoEnabled()) {
                logger.info(message);
            }
        } else if (logType.equals(LogEnum.ERROR)) {
            if (logger.isErrorEnabled()) {
                logger.error(message);
            }
        } else if (logType.equals(LogEnum.WARN)) {
            if (logger.isWarnEnabled()) {
                logger.warn(message);
            }
        } else if (logType.equals(LogEnum.FATAL)) {
            if (logger.isFatalEnabled()) {
                logger.fatal(message);
            }
        } else if (logType.equals(LogEnum.TRACE)) {
            if (logger.isTraceEnabled()) {
                logger.trace(message);
            }
        }
    }
}
