package cn.ciphermagic.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @Author: CipherCui
 * @Description: 校验工具类
 * @Date: 13:14 2018/4/8
 */
public class ValidateUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ValidateUtil.class);

    public static void checkSQL(Supplier<Integer> sql, String msg, Consumer<String> err) {
        ValidateUtil.checkSQL(sql, msg, err, Boolean.TRUE);
    }

    /**
     * 检查数据库操作是否异常
     *
     * @param sql         数据库操作lambda表达式，返回值为影响行数
     * @param msg         错误提示信息
     * @param includeZero 影响行数为0时，是否报错
     */
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
