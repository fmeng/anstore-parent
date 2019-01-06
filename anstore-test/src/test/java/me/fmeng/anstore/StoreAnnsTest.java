package me.fmeng.anstore;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author fmeng
 * @since 2019/01/06
 */
public class StoreAnnsTest {

    @BeforeClass
    public static void initBasePackage() throws Exception {
        System.setProperty(StoreAnnotationUtil.SCAN_PACKAGE_ARGS_NAME, "me.fmeng");
    }

    @Test
    public void testClassAnnotation() {
        for (MarkedUnit markedUnit : StoreAnnotationUtil.MARKED_UNITS) {
            System.out.println(markedUnit);
        }
    }

}
