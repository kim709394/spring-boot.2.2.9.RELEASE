package com.kim.spi.starter.annotation;

import com.kim.spi.starter.bean.ImportBean;
import com.kim.spi.starter.config.SpiMarker;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author huangjie
 * @description @import注解引入配置类，热插拔注解
 * @date 2022-10-03
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Import({ImportBean.class})
public @interface EnableImportBean {

}
