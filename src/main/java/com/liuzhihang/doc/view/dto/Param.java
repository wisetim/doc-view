package com.liuzhihang.doc.view.dto;

import com.intellij.psi.PsiElement;

/**
 * @author liuzhihang
 * @date 2020/2/27 16:39
 */
public class Param {


    /**
     * 参数的 psiElement
     */
    private PsiElement psiElement;

    /**
     * 是否必须
     */
    private Boolean required;
    /**
     * 参数名
     */
    private String name;
    /**
     * 参数示例
     */
    private String example;

    /**
     * 参数描述
     */
    private String desc;

    /**
     * 类型
     */
    private String type;


    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public PsiElement getPsiElement() {
        return psiElement;
    }

    public void setPsiElement(PsiElement psiElement) {
        this.psiElement = psiElement;
    }
}
