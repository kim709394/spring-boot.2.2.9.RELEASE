package com.kim.spi.starter.bean;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author huangjie
 * @description  spi机制配置类bean
 * @date 2022-10-03
 */
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "spi")
public class SpiBean {


	private Integer id;
	private String name;


	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "SpiBean{" + "id=" + id + ", name='" + name + '\'' + '}';
	}
}
