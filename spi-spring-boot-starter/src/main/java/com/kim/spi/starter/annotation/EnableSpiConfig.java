package com.kim.spi.starter.annotation;

import com.kim.spi.starter.config.SpiMarker;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author huangjie
 * @description 热插拔技术，spi配置标记类引入进ioc容器
 * @date 2022-10-03
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Import({SpiMarker.class})
public @interface EnableSpiConfig {
}
