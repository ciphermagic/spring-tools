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

    public static void checkSQL(Supplier<Integer> sql, String msg, Consumer<String> err) {
        ValidateUtil.checkSQL(sql, msg, err, Boolean.TRUE);
    }

    public static void checkSQL(Supplier<Integer> sql, String msg, Consumer<String> err, boolean includeZero) {
        try {
            int row = sql.get();
            if (includeZero) {
                if (row <= 0) {
                    err.accept(msg);
                }
            } else {
                // 允许没有一条记录操作成功时,不回滚事务
                if (row < 0) {
                    err.accept(msg);
                }
            }
        } catch (Exception e) {
            LOG.error("" + e);
            throw e;
        }
    }

}
