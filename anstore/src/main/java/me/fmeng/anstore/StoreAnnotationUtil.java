package me.fmeng.anstore;

import com.google.common.base.Splitter;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 存储注解关系,不可变对象
 *
 * @author fmeng
 * @since 2018/01/25
 */
@Slf4j
@SuppressWarnings("all")
public class StoreAnnotationUtil {

    public static final String SCAN_PACKAGE_ARGS_NAME_PREFIX = "storeanns.base.package";
    public static final String SCAN_PACKAGE_ARGS_VALUE_SPLITTER = ",";

    private static volatile Multimap<Class<? extends Annotation>, Class<?>> ANNOTATION_CLASS_MAP = MultimapBuilder.hashKeys(5).hashSetValues(5).build();
    private static volatile Multimap<Class<? extends Annotation>, Field> ANNOTATION_FIELD_MAP = MultimapBuilder.hashKeys(5).hashSetValues(5).build();
    private static volatile Multimap<Class<? extends Annotation>, Method> ANNOTATION_METHOD_MAP = MultimapBuilder.hashKeys(5).hashSetValues(5).build();
    private static volatile Set<MarkedUnit> MARKED_UNITS = Sets.newLinkedHashSetWithExpectedSize(30);
    private static volatile Set<String> PACKAGE_PATHS = Sets.newHashSetWithExpectedSize(3);

    static {
        initAnnotationsToStore();
    }

    /**
     * 初始化工具类
     */
    public static void initAnnotationsToStore() {
        System.setProperty(StoreAnnotationUtil.SCAN_PACKAGE_ARGS_NAME_PREFIX
                + ".anstore.StoreAnnotationUtil", "me.fmeng.anstore");
    }

    /**
     * 获取标注在类上的数据
     */
    public static Multimap<Class<? extends Annotation>, Class<?>> getAnnotationClassMap() {
        tryReScanPackageAndFlushData();
        return ANNOTATION_CLASS_MAP;
    }

    /**
     * 获取标注在属性上的数据
     */
    public static Multimap<Class<? extends Annotation>, Field> getAnnotationFieldMap() {
        tryReScanPackageAndFlushData();
        return ANNOTATION_FIELD_MAP;
    }

    /**
     * 获取标注在方法上的数据
     */
    public static Multimap<Class<? extends Annotation>, Method> getAnnotationMethodMap() {
        tryReScanPackageAndFlushData();
        return ANNOTATION_METHOD_MAP;
    }

    /**
     * 获取所有的数据标注项
     */
    public static Set<MarkedUnit> getMarkedUnits() {
        tryReScanPackageAndFlushData();
        return MARKED_UNITS;
    }

    /**
     * 检查是否有新增加的要扫描的包
     * 重新刷新数据，添加新扫描的数据信息
     */
    private static synchronized void tryReScanPackageAndFlushData() {
        Set<String> newAddedPackagePaths = Sets.difference(checkAndGetPackagePaths(), PACKAGE_PATHS).immutableCopy();
        if (newAddedPackagePaths == null || newAddedPackagePaths.isEmpty()) {
            return;
        }
        log.info("扫描支持@StoreAnnotation包, newAddedPackagePaths={}", newAddedPackagePaths);
        PACKAGE_PATHS.addAll(newAddedPackagePaths);
        // 刷新数据
        Reflections finder = createFinder(newAddedPackagePaths);
        // 查找所有被StoreAnnotation标注的注解
        Set<Class<?>> annotationClassSet = finder.getTypesAnnotatedWith(StoreAnnotation.class, true);
        Set<Class<? extends Annotation>> annotationTypeClassSet = annotationClassSet.stream()
                .map(c -> (Class<? extends Annotation>) c)
                .collect(Collectors.toSet());
        if (annotationClassSet == null || annotationClassSet.isEmpty()) {
            return;
        }
        log.info("找到@StoreAnnotation标记的注解, annotationTypeClassSet={}", annotationTypeClassSet);
        for (Class<? extends Annotation> a : annotationTypeClassSet) {
            // 业务注解可以注解的位置
            Set<ElementType> elementTypes = Sets.newHashSet(a.getAnnotation(Target.class).value());
            // 在类上使用注解
            if (elementTypes.contains(ElementType.TYPE)) {
                Set<Class<?>> markedClassSet = finder.getTypesAnnotatedWith(a, true);
                if (markedClassSet != null && !markedClassSet.isEmpty()) {
                    ANNOTATION_CLASS_MAP.putAll(a, markedClassSet);
                    markedClassSet.forEach(c -> MARKED_UNITS.add(new MarkedUnit(a, c, null, null, c.getAnnotation(a), MarkedUnit.STORE_LOCATION_CLASS)));
                    log.info("{}注解的类markedClassSet={}", a, markedClassSet);
                }
            }
            // 在属性上注解
            if (elementTypes.contains(ElementType.FIELD)) {
                Set<Field> markedFieldSet = finder.getFieldsAnnotatedWith(a);
                if (markedFieldSet != null && !markedFieldSet.isEmpty()) {
                    ANNOTATION_FIELD_MAP.putAll(a, markedFieldSet);
                    markedFieldSet.forEach(f -> MARKED_UNITS.add(new MarkedUnit(a, f.getDeclaringClass(), f, null, f.getAnnotation(a), MarkedUnit.STORE_LOCATION_FIELD)));
                    log.info("{}注解的属性markedFieldSet={}", a, markedFieldSet);
                }
            }
            // 在方法注解
            if (elementTypes.contains(ElementType.METHOD)) {
                Set<Method> markedMethodSet = finder.getMethodsAnnotatedWith(a);
                if (markedMethodSet != null && !markedMethodSet.isEmpty()) {
                    ANNOTATION_METHOD_MAP.putAll(a, markedMethodSet);
                    markedMethodSet.forEach(m -> MARKED_UNITS.add(new MarkedUnit(a, m.getDeclaringClass(), null, m, m.getAnnotation(a), MarkedUnit.STORE_LOCATION_METHOD)));
                    log.info("{}注解的方法markedMethodSet={}", a, markedMethodSet);
                }
            }
        }
    }

    /**
     * 获取当前属性中所有的包集合
     */
    private static Set<String> checkAndGetPackagePaths() {
        Set<String> packagePaths = Sets.newHashSetWithExpectedSize(2);
        Splitter splitter = Splitter.on(SCAN_PACKAGE_ARGS_VALUE_SPLITTER);
        for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
            String key = e.getKey().toString();
            if (key != null && key.toLowerCase().startsWith(SCAN_PACKAGE_ARGS_NAME_PREFIX)) {
                String packagesStr = e.getValue().toString();
                if (packagesStr != null && !packagesStr.isEmpty()) {
                    List<String> packages = splitter.splitToList(packagesStr);
                    if (packages != null && !packages.isEmpty()) {
                        packagePaths.addAll(packages);
                    }
                }
            }
        }
        if (packagePaths.isEmpty()) {
            throw new IllegalArgumentException("没有添加任何以"
                    + SCAN_PACKAGE_ARGS_VALUE_SPLITTER + "为前缀的属性");
        }
        return packagePaths;
    }

    /**
     * 获取包扫描器
     */
    private static Reflections createFinder(Set<String> packagePaths) {
        Set<URL> packageUrls = packagePaths.stream().flatMap(p -> ClasspathHelper.forPackage(p).stream())
                .collect(Collectors.toSet());
        String[] packages = packagePaths.stream().toArray(String[]::new);
        // 设置符合条件的Finder
        Reflections finder = new Reflections(new ConfigurationBuilder()
                .setUrls(packageUrls)
                .setScanners(new TypeAnnotationsScanner().filterResultsBy(new FilterBuilder().includePackage(packages))
                        , new MethodAnnotationsScanner().filterResultsBy(new FilterBuilder().includePackage(packages))
                        , new FieldAnnotationsScanner().filterResultsBy(new FilterBuilder().includePackage(packages))
                        , new SubTypesScanner().filterResultsBy(new FilterBuilder().includePackage(packages))
                )
                .filterInputsBy(new FilterBuilder().includePackage(packages))
        );
        return finder;
    }
}
