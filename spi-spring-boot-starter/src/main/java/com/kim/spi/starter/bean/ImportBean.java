package com.kim.spi.starter.bean;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author huangjie
 * @description import注解bean
 * @date 2022-10-03
 */
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "import")
public class ImportBean {

	private String importName;

	public String getImportName() {
		return importName;
	}

	@Override
	public String toString() {
		return "ImportBean{" + "importName='" + importName + '\'' + '}';
	}

	public void setImportName(String importName) {
		this.importName = importName;
	}

}
