package com.kim.springboot.demo;

import com.kim.spi.starter.annotation.EnableSpiConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author huangjie
 * @description
 * @date 2022-03-28
 */
// 启动类注解
@SpringBootApplication
//启用spi配置
@EnableSpiConfig
public class SpringbootDemoApplication {

	public static void main(String[] args) {
		// 启动run方法执行
		SpringApplication.run(SpringbootDemoApplication.class);
	}

}
