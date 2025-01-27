package com.liuzhihang.doc.view.utils;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.config.TagsSettings;
import com.liuzhihang.doc.view.constant.FieldTypeConstant;
import com.liuzhihang.doc.view.dto.Body;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 参数处理工具
 *
 * @author liuzhihang
 * @date 2020/11/17 15:15
 */
public class ParamPsiUtils {


    @NotNull
    public static Body buildBodyParam(@NotNull Settings settings, PsiField field, PsiType[] genericArr) {

        Body body = new Body();
        body.setRequired(isRequired(field));
        body.setName(field.getName());
        body.setPsiElement(field);

        PsiType type = field.getType();

        body.setType(type.getPresentableText());

        PsiDocComment docComment = field.getDocComment();

        if (docComment != null) {
            // param.setExample();
            // 参数举例, 使用 tag 判断
            body.setDesc(CustomPsiCommentUtils.getComment(docComment));
        }

        if (type instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(type.getPresentableText())) {
            return body;
        } else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION)) {
            // List Set or HashSet
            List<Body> list = new ArrayList<>();
            PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);
            PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
            if (iterableClass != null) {
                for (PsiField psiField : iterableClass.getAllFields()) {
                    if (!settings.getExcludeFieldNames().contains(psiField.getName())
                            && !CustomPsiUtils.hasModifierProperty(psiField, PsiModifier.STATIC)) {

                        Body requestParam = buildBodyParam(settings, psiField, null);
                        list.add(requestParam);
                    }
                }
            }
            body.setBodyList(list);
        } else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
            // HashMap or Map
            List<Body> list = new ArrayList<>();
            PsiType matValueType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, false);
            PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(matValueType);
            if (iterableClass != null) {
                for (PsiField psiField : iterableClass.getAllFields()) {
                    if (!settings.getExcludeFieldNames().contains(psiField.getName())
                            && !CustomPsiUtils.hasModifierProperty(psiField, PsiModifier.STATIC)) {
                        Body requestParam = buildBodyParam(settings, psiField, null);
                        list.add(requestParam);
                    }
                }
            }
            body.setBodyList(list);
        } else {
            PsiType psiType;
            if (type.getPresentableText().equals("T") && genericArr != null && genericArr.length >= 1) {
                // T 泛型
                psiType = genericArr[0];
            } else if (type.getPresentableText().equals("K") && genericArr != null && genericArr.length >= 2) {
                // K 泛型
                psiType = genericArr[1];
            } else {
                psiType = type;
            }

            if (FieldTypeConstant.FIELD_TYPE.containsKey(psiType.getPresentableText())) {
                body.setType(psiType.getPresentableText());
                return body;
            }

            PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
            List<Body> list = new ArrayList<>();
            if (psiClass != null && !psiClass.isEnum() && !psiClass.isInterface() && !psiClass.isAnnotationType()) {
                for (PsiField psiField : psiClass.getAllFields()) {
                    if (!settings.getExcludeFieldNames().contains(psiField.getName())
                            && !CustomPsiUtils.hasModifierProperty(psiField, PsiModifier.STATIC)) {
                        Body requestParam = buildBodyParam(settings, psiField, null);
                        list.add(requestParam);
                    }
                }
            }
            body.setBodyList(list);
        }

        return body;
    }

    /**
     * 判断字段是否必填
     *
     * @param field
     * @return
     */
    private static boolean isRequired(PsiField field) {

        Project project = field.getProject();
        // 判断是否必填
        Settings setting = Settings.getInstance(project);
        boolean annotated = AnnotationUtil.isAnnotated(field, setting.getFieldRequiredAnnotationName(), 0);

        // 查看注释
        TagsSettings tagsSettings = TagsSettings.getInstance(project);
        PsiDocComment docComment = field.getDocComment();

        if (docComment == null) {
            return annotated;
        }

        PsiDocTag requiredTag = docComment.findTagByName(tagsSettings.getRequired());

        // Swagger 注解


        return annotated || requiredTag != null;
    }


    @NotNull
    public static Map<String, Object> getFieldsAndDefaultValue(PsiClass psiClass, PsiType[] genericArr) {

        Map<String, Object> fieldMap = new LinkedHashMap<>();
        // Map<String, Object> commentFieldMap = new LinkedHashMap<>();

        if (psiClass != null && !psiClass.isEnum() && !psiClass.isInterface() && !psiClass.isAnnotationType()) {
            for (PsiField field : psiClass.getAllFields()) {

                if (field.getModifierList() != null && field.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
                    continue;
                }


                PsiType type = field.getType();
                String name = field.getName();
                // 判断注解 javax.annotation.Resource   org.springframework.beans.factory.annotation.Autowired
                PsiAnnotation[] annotations = field.getAnnotations();
                if (annotations.length > 0 && containsAnnotation(annotations)) {
                    fieldMap.put(name, "");
                } else if (type instanceof PsiPrimitiveType) {
                    // 基本类型
                    fieldMap.put(name, PsiTypesUtil.getDefaultValue(type));
                } else {
                    //reference Type
                    String fieldTypeName = type.getPresentableText();
                    // 指定的类型
                    if (FieldTypeConstant.FIELD_TYPE.containsKey(fieldTypeName)) {
                        fieldMap.put(name, FieldTypeConstant.FIELD_TYPE.get(fieldTypeName));
                    } else if (type instanceof PsiArrayType) {
                        //array type
                        List<Object> list = new ArrayList<>();
                        PsiType deepType = type.getDeepComponentType();
                        String deepTypeName = deepType.getPresentableText();
                        if (deepType instanceof PsiPrimitiveType) {
                            list.add(PsiTypesUtil.getDefaultValue(deepType));
                        } else if (FieldTypeConstant.FIELD_TYPE.containsKey(deepTypeName)) {
                            list.add(FieldTypeConstant.FIELD_TYPE.get(deepTypeName));
                        } else {
                            list.add(getFieldsAndDefaultValue(PsiUtil.resolveClassInType(deepType), null));
                        }
                        fieldMap.put(name, list);
                    } else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION)) {
                        // List Set or HashSet
                        List<Object> list = new ArrayList<>();
                        PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);
                        PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
                        if (iterableClass != null) {
                            String classTypeName = iterableClass.getName();
                            if (FieldTypeConstant.FIELD_TYPE.containsKey(classTypeName)) {
                                list.add(FieldTypeConstant.FIELD_TYPE.get(classTypeName));
                            } else {
                                list.add(getFieldsAndDefaultValue(iterableClass, null));
                            }
                        }
                        fieldMap.put(name, list);
                    } else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
                        // HashMap or Map
                        fieldMap.put(name, new HashMap<>(4));
                    } else if (psiClass.isEnum() || psiClass.isInterface() || psiClass.isAnnotationType()) {
                        // enum or interface
                        fieldMap.put(name, "");
                    } else {

                        if (type.getPresentableText().equals("T") && genericArr != null && genericArr.length >= 1) {
                            // T 泛型
                            type = genericArr[0];
                        } else if (type.getPresentableText().equals("K") && genericArr != null && genericArr.length >= 2) {
                            // K 泛型
                            type = genericArr[1];
                        }

                        if (FieldTypeConstant.FIELD_TYPE.containsKey(type.getPresentableText())) {
                            fieldMap.put(name, FieldTypeConstant.FIELD_TYPE.get(type.getPresentableText()));
                        } else {
                            fieldMap.put(name, getFieldsAndDefaultValue(PsiUtil.resolveClassInType(type), null));
                        }


                    }
                }
            }
        }
        return fieldMap;
    }

    /**
     * 是否包含指定的注解
     *
     * @param annotations
     * @return
     */
    private static boolean containsAnnotation(@NotNull PsiAnnotation[] annotations) {
        for (PsiAnnotation annotation : annotations) {
            if (FieldTypeConstant.ANNOTATION_TYPES.contains(annotation.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }


    @NotNull
    public static List<Body> buildRespBody(Settings settings, PsiType returnType) {

        List<Body> list = new ArrayList<>();
        if (returnType instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(returnType.getPresentableText())) {
            Body body = new Body();
            body.setRequired(false);
            body.setName(null);
            body.setType(returnType.getPresentableText());
            list.add(body);
        } else if (returnType instanceof PsiClassType) {

            PsiClassType psiClassType = (PsiClassType) returnType;

            // 返回值可能是 Result<T> Result<T, K> 泛型的

            PsiClass psiClass = PsiUtil.resolveClassInType(returnType);
            if (psiClass != null) {
                if (InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
                    PsiType[] parameters = psiClassType.getParameters();
                    if (parameters.length != 0) {
                        PsiType psiType = parameters[0];

                        if (psiType instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(psiType.getPresentableText())) {
                            return list;
                        }

                        // 泛型的类型
                        PsiClass genericsPsiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);

                        if (genericsPsiClass != null) {
                            return buildBodyList(settings, genericsPsiClass, null);
                        }
                    }
                } else {
                    return buildBodyList(settings, psiClass, psiClassType.getParameters());
                }
            }
        } else {
            // 其他类型
        }
        return list;
    }

    @NotNull
    public static List<Body> buildBodyList(Settings settings, @NotNull PsiClass psiClass, PsiType[] o) {

        List<Body> list = new ArrayList<>();


        for (PsiField field : psiClass.getAllFields()) {

            if (settings.getExcludeFieldNames().contains(field.getName())) {
                continue;
            }
            // 排除掉被 static 修饰的字段
            if (CustomPsiUtils.hasModifierProperty(field, PsiModifier.STATIC)) {
                continue;
            }

            Body requestParam = ParamPsiUtils.buildBodyParam(settings, field, o);
            list.add(requestParam);
        }
        return list;
    }


    @NotNull
    public static String getRespBodyJson(Settings settings, PsiType returnType) {


        if (returnType instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(returnType.getPresentableText())) {
            return "";
        } else if (returnType instanceof PsiClassType) {

            PsiClassType psiClassType = (PsiClassType) returnType;

            PsiClass psiClass = PsiUtil.resolveClassInType(returnType);
            if (psiClass != null) {

                if (InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
                    // 集合类型
                    PsiType[] parameters = psiClassType.getParameters();

                    if (parameters.length == 0) {
                        return "[]";
                    }

                    PsiType psiType = parameters[0];

                    if (psiType instanceof PsiPrimitiveType || FieldTypeConstant.FIELD_TYPE.containsKey(psiType.getPresentableText())) {
                        return "[]";
                    }

                    PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
                    Map<String, Object> fieldMap = ParamPsiUtils.getFieldsAndDefaultValue(iterableClass, null);

                    Object[] objectArr = {fieldMap};

                    return GsonFormatUtil.gsonFormat(objectArr);
                } else {
                    Map<String, Object> fieldMap = ParamPsiUtils.getFieldsAndDefaultValue(psiClass, psiClassType.getParameters());

                    return GsonFormatUtil.gsonFormat(fieldMap);
                }
            }
        } else {
            // 其他类型
        }

        return "";
    }

}
