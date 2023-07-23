package com.project.chagok.crawler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.chagok.crawler.constants.CategoryType;
import com.project.chagok.crawler.constants.SiteType;
import com.project.chagok.crawler.dto.ProjectStudyDto;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class HolaCrawlerService implements CrawlerService{

    private String holaBaseUrl = "https://holaworld.io";
    private List<ProjectStudyDto> projectStudyDatas = new ArrayList<>();
    private HashSet<String> visitedPages = new HashSet<>();
    private final WebDriver driver;

    Logger logger = Logger.getLogger(this.getClass().getName());

    @Override
    @Scheduled(cron = "* */3 * * * *")
    public void processCrawling() {
        List<String> parsePages = gatherUrls();

        parseData(parsePages);
    }

    @Override
    public List<String> gatherUrls() {

        driver.get(holaBaseUrl);

        final int waitMills = 200;

        ArrayList<String> willParseUrls = new ArrayList<>();

        for (int page = 1; page <= 7; page++) {

            // list items 목록 파싱
            final String ListSelector = ".studyItem_studyItem__1Iipn";
            // 리액트 기반, 컴포넌트 시간 지연을 위함
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(waitMills));
            List<WebElement> previewElements = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.cssSelector(ListSelector)));

            for (WebElement preview : previewElements) {

                // 마감 날짜 파싱
                final String deadLineDateSelector = ".studyItem_schedule__3oAnA p:nth-child(2)";
                WebElement deadLineElement = preview.findElement(By.cssSelector(deadLineDateSelector));
                //마감일 지나지 않은 글만 크롤링.
                if (validateDeadLineDate(deadLineElement.getText())) {

                    // list item url 파싱
                    String url = preview.getAttribute("href");
                    if (isVisited(url)) // 이미 방문한 페이지인지 url 기반 검사.
                        return willParseUrls;

                    willParseUrls.add(url);
                    visitedPages.add(url); // visited page 추가
                }
            }

            // pagination next 버튼 파싱
            final String PaginationNextBtnXPath = "//button[@aria-label='Go to next page']";
            WebElement nextButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(PaginationNextBtnXPath)));

            nextButton.click();
        }

        return willParseUrls;

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
            String holaJsonString = driver.findElement(By.cssSelector("pre")).getText();

            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(holaJsonString);

                String title = jsonNode.get("title").asText();
                String nickname = jsonNode.get("author").get("nickName").asText();
                LocalDateTime createdTime = extractDateFromJson(jsonNode.get("createdAt").asText());
                List<String> techStacksList = new ArrayList<>();
                jsonNode.get("language").elements().forEachRemaining(techstack -> techStacksList.add(techstack.asText()));
                String sourceUrl = url;
                CategoryType category = extractCategoryFromJSon(jsonNode.get("type").asText());

                ProjectStudyDto projectStudyDto = ProjectStudyDto.builder()
                        .type(SiteType.HOLA)
                        .title(title)
                        .nickname(nickname)
                        .createdDate(createdTime)
                        .sourceUrl(sourceUrl)
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

    boolean validateDeadLineDate(String parsingDeadLine) {

        DateTimeFormatter formatters = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate deadLineDate = LocalDate.parse(parsingDeadLine, formatters);

        // 마감일이 현재 날짜 이후라면, true
        return deadLineDate.isAfter(LocalDate.now());
    }

    boolean isVisited(String url) {
        return visitedPages.contains(url);
    }

    public static String extractBoardJsonFromUrl(String parsingUrl) {
        final String BaseJsonUrl = "https://api.holaworld.io/api/posts/";

        Pattern regex = Pattern.compile("study/(.+)");
        Matcher matcher = regex.matcher(parsingUrl);

        if (matcher.find()) {
            String extractedBoardId = matcher.group(1);
            return BaseJsonUrl+extractedBoardId;
        }

        return null;
    }

    static CategoryType extractCategoryFromJSon(String parsingCategory) {
        if (parsingCategory.equals("1"))
            return CategoryType.PROJECT;
        else if (parsingCategory.equals("2"))
            return CategoryType.STUDY;

        return null;
    }

    static LocalDateTime extractDateFromJson(String parsingDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME;

        ZonedDateTime zonedDateTime = ZonedDateTime.parse(parsingDate, formatter);

        return zonedDateTime.toLocalDateTime();
    }
}
