package cn.ciphermagic.common.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * 对象适配器，对象之间的转换，如：VO转PO，PO转VO
 *
 * @author: CipherCui
 */
public class ObjAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ObjAdapter.class);

    public static <A, B> List<B> convert(Collection<A> collection,
                                         Class<B> targetClass, BiConsumer<A, B> decorator) {
        if (collection == null || collection.size() == 0) {
            return new ArrayList<>();
        } else {
            ObjectMapper om = new ObjectMapper();
            om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return collection.stream().map(a -> {
                B b = om.convertValue(a, targetClass);
                decorator.accept(a, b);
                return b;
            }).collect(Collectors.toList());
        }
    }

    public static <A, B> List<B> convert(Collection<A> collection,
                                         Class<B> targetClass) {
        if (collection == null || collection.size() == 0) {
            return new ArrayList<>();
        } else {
            ObjectMapper om = new ObjectMapper();
            om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false);
            return collection.stream().map(a -> om.convertValue(a, targetClass)).collect(Collectors.toList());
        }
    }

    @SuppressWarnings("unchecked")
    public static <V, T> T convert(Object a, Class<T> targetClass,
                                   BiConsumer<V, T> decorator) {
        T b = null;
        if (a == null) {
            try {
                b = targetClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                LOG.error("" + e);
            }
        } else {
            List<V> list = new ArrayList<>();
            list.add((V) a);
            List<T> result = ObjAdapter.convert(list, targetClass, decorator);
            b = result.get(0);
        }
        return b;
    }

    public static <T> T convert(Object a, Class<T> targetClass) {
        T b = null;
        if (a == null) {
            try {
                b = targetClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                LOG.error("" + e);
            }
        } else {
            ObjectMapper om = new ObjectMapper();
            om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false);
            b = om.convertValue(a, targetClass);
        }
        return b;
    }

}
