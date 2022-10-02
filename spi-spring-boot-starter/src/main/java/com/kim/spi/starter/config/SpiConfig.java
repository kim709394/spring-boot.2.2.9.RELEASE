package com.kim.spi.starter.config;

import com.kim.spi.starter.bean.ImportBean;
import com.kim.spi.starter.bean.SpiBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjie
 * @description 自定义spi机制配置类
 * spi命名规则：
 * SpringBoot提供的starter以 spring-boot-starter-xxx 的方式命名的。
 * 官方建议自定义的starter使用 xxx-spring-boot-starter 命名规则。以区分SpringBoot生态提供
 * 的starter
 * @date 2022-10-03
 */

@Configuration
//当spi标记类存在ioc容器时才进行spi配置
@ConditionalOnBean(SpiMarker.class)
public class SpiConfig {

	@Bean
	public SpiBean spiBean(){

		return new SpiBean();
	}

}
