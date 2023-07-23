package com.project.chagok.crawler.config;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CrawlerConfiguration {

    @Bean
    public WebDriver chromeWebDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");

        return new ChromeDriver(options);
    }
}
