package org.fmeng.test;

import me.fmeng.anstore.AllTypeAnnotation;
import me.fmeng.anstore.ClassAnnotation;
import me.fmeng.anstore.FieldAnnotation;
import me.fmeng.anstore.MethodAnnotation;

/**
 * @author fmeng
 * @since 2019/01/06
 */
@AllTypeAnnotation
@ClassAnnotation
public class UserEntity {

    @AllTypeAnnotation
    @FieldAnnotation
    private String name;

    @AllTypeAnnotation
    @MethodAnnotation
    public String getName() {
        return name;
    }
}
