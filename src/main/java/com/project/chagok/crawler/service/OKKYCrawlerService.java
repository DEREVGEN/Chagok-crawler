package com.project.chagok.crawler.service;


import com.beust.ah.A;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.chagok.crawler.constants.CategoryType;
import com.project.chagok.crawler.constants.SiteType;
import com.project.chagok.crawler.dto.ProjectStudyDto;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OKKYCrawlerService implements CrawlerService{

    private final WebDriver driver;

    final String BaseUrl = "https://okky.kr/community/gathering";
    private List<ProjectStudyDto> projectStudyDatas = new ArrayList<>();
    private HashSet<String> visitedPages = new HashSet<>();
    private Logger logger = Logger.getLogger(this.getClass().getName());

    @Override
    @Scheduled(cron = "* */3 * * * *")
    public void processCrawling() {
        List<String> parseUrl;

        parseUrl = gatherUrls();
        parseData(parseUrl);
    }

    @Override
    public List<String> gatherUrls() {

        ArrayList<String> willParseUrls = new ArrayList<>();

        for (int page = 1; ; page++) {
            String nextUrl = BaseUrl + "?page=" + page;

            driver.get(nextUrl);

            // list item 목록 파싱
            final String ListItemsSelector = "li.py-4 .flex.flex-col";
            List<WebElement> listItemsElements = driver.findElements(By.cssSelector(ListItemsSelector));

            for (WebElement listItem : listItemsElements) {

                // list item 아이템 url 파싱
                final String ListItemUrlSelector = "li.py-4 .flex.flex-col .my-2 a";
                String listUrl = listItem.findElement(By.cssSelector(ListItemUrlSelector)).getAttribute("href");

                // list item 작성일 파싱
                final String CreatedDateSelector = "li.py-4 .flex.flex-col .text-xs:nth-child(5)";
                // 작성일 파싱 유무 검증(기준: 한 달 전)
                if (validateDate(listItem.findElement(By.cssSelector(CreatedDateSelector)).getText()) && !isVisited(listUrl)) {
                    willParseUrls.add(listUrl);
                    visitedPages.add(listUrl);
                } else {
                    return willParseUrls;
                }
            }
        }
    }

    @Override
    public void parseData(List<String> urls) {

        /*
        데이터 목록
        1. 제목
        2. 작성자
        3. 작성일
        4. 기술스택
        5. 출처
        6. 타입(프로젝트 or 스터디)
         */

        for (String url : urls) {
            String jsonUrl = extractBoardJsonFromUrl(url);
            driver.get(jsonUrl);

            // parsing json string in pre html tag
            String okkyJsonString = driver.findElement(By.cssSelector("pre")).getText();

            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(okkyJsonString);

                String title = jsonNode.get("pageProps").get("result").get("title").asText();
                String nickname = jsonNode.get("pageProps").get("result").get("displayAuthor").get("nickname").asText();
                LocalDateTime createdTime = LocalDateTime.parse(jsonNode.get("pageProps").get("result").get("dateCreated").asText());
                List<String> techStacksList = new ArrayList<>();
                jsonNode.get("pageProps").get("result").get("tags").elements().forEachRemaining(techElement -> techStacksList.add(techElement.get("name").asText()));
                String sourceUrl = url;
                CategoryType category = CategoryType.STUDY;

                ProjectStudyDto projectStudyDto = ProjectStudyDto.builder()
                        .type(SiteType.OKKY)
                        .title(title)
                        .nickname(nickname)
                        .createdDate(createdTime)
                        .sourceUrl(url)
                        .category(category)
                        .techList(techStacksList)
                        .build();

                projectStudyDatas.add(projectStudyDto);
                logger.info(projectStudyDto.toString());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isVisited(String url) {
        return visitedPages.contains(url);
    }

    public boolean validateDate(String parsingDate) {

        // 임시로... 추가책이 필요함
        final String validDateSuffix = "개월 전";

        if (parsingDate.endsWith(validDateSuffix))
            return false;

        return true;
    }

    public String extractBoardJsonFromUrl(String parsingUrl) {
        final String BaseJsonUrl = "https://okky.kr/_next/data/nPoHzWQhQhUlX11AzLwi6/articles/";

        Pattern regex = Pattern.compile("articles/(\\d+)");
        Matcher matcher = regex.matcher(parsingUrl);

        if (matcher.find()) {
            String extractedBoardId = matcher.group(1);
            return BaseJsonUrl+extractedBoardId+".json";
        }

        return null;
    }

}
