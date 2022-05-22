package cn.ciphermagic.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * validate util
 *
 * @author: CipherCui
 */
public class ValidateUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ValidateUtil.class);

    /**
     * Determine whether the number of rows affected by sql is in line with expectations
     *
     * @param sql sql lambda expression
     * @param msg error message
     * @param err error handle lambda expression
     * @see ValidateUtil#checkSql(Supplier, String, Consumer, boolean) includeZero is true
     */
    public static void checkSql(Supplier<Integer> sql, String msg, Consumer<String> err) {
        ValidateUtil.checkSql(sql, msg, err, Boolean.TRUE);
    }

    /**
     * Determine whether the number of rows affected by sql is in line with expectations
     *
     * @param sql         sql lambda expression
     * @param msg         error message
     * @param err         error handle lambda expression
     * @param includeZero Whether the error is caused when the number of rows is 0
     */
    public static void checkSql(Supplier<Integer> sql, String msg, Consumer<String> err, boolean includeZero) {
        try {
            int row = sql.get();
            if (includeZero) {
                if (row <= 0) {
                    err.accept(msg);
                }
            } else {
                // Allows no transaction to be rolled back when no record operation succeeds
                if (row < 0) {
                    err.accept(msg);
                }
            }
        } catch (Exception e) {
            LOG.error("" + e);
            err.accept(msg);
        }
    }

}
