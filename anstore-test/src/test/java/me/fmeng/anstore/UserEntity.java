package me.fmeng.anstore;

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
