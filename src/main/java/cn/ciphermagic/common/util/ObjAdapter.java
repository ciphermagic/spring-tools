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
 * Object adapter, conversion between objects, such as: VO to PO, PO to VO
 *
 * @author: CipherCui
 */
public class ObjAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ObjAdapter.class);

    /**
     * Generally used for the conversion of multiple A objects to B objects
     * <p>
     * After the objectMapper is used to convert the object, run the decorator to decorate it.
     * The decorator is an expression of the form (vo,po) -&gt; {vo.setA(po.getB()}.
     *
     * @param collection  source object collection
     * @param targetClass target type
     * @param decorator   modify the target object
     * @param <A>         source object generic
     * @param <B>         target generic
     * @return target object collection
     */
    public static <A, B> List<B> convert(Collection<A> collection, Class<B> targetClass, BiConsumer<A, B> decorator) {
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

    /**
     * Generally used for conversion of multiple A objects to B objects
     *
     * @param collection  source object collection
     * @param targetClass target type
     * @param <A>         source object generic
     * @param <B>         target generic
     * @return target object collection
     */
    public static <A, B> List<B> convert(Collection<A> collection, Class<B> targetClass) {
        if (collection == null || collection.size() == 0) {
            return new ArrayList<>();
        } else {
            ObjectMapper om = new ObjectMapper();
            om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return collection.stream().map(a -> om.convertValue(a, targetClass)).collect(Collectors.toList());
        }
    }

    /**
     * Generally used for the conversion of a single A object to a B object
     *
     * @param a           source object
     * @param targetClass target type
     * @param decorator   modify the target object
     * @param <A>         source object generic
     * @param <B>         target generic
     * @return target object
     */
    @SuppressWarnings("unchecked")
    public static <A, B> B convert(Object a, Class<B> targetClass, BiConsumer<A, B> decorator) {
        B b = null;
        if (a == null) {
            try {
                b = targetClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                LOG.error("" + e);
            }
        } else {
            List<A> list = new ArrayList<>();
            list.add((A) a);
            List<B> result = ObjAdapter.convert(list, targetClass, decorator);
            b = result.get(0);
        }
        return b;
    }

    /**
     * Generally used for the conversion of a single A object to a B object
     *
     * @param a           source object
     * @param targetClass target type
     * @param <T>         target generic
     * @return target object
     */
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
            om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            b = om.convertValue(a, targetClass);
        }
        return b;
    }

}
