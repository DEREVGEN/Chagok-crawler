package com.project.chagok.crawler.service;

import java.util.List;

public interface CrawlerService {
    void processCrawling();
    List<String> gatherUrls();
    void parseData(List<String> urls);
}
