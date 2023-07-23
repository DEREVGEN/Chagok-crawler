package com.project.chagok.crawler.service;

import com.project.chagok.crawler.constants.CategoryType;
import com.project.chagok.crawler.constants.SiteType;
import com.project.chagok.crawler.dto.ProjectStudyDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

@Service
public class InflearnCrawlerService implements CrawlerService{

    private final String baseInflearnUrl = "https://www.inflearn.com";
    private final String[] baseParsingUrls = {"https://www.inflearn.com/community/projects?status=unrecruited", "https://www.inflearn.com/community/studies?status=unrecruited"};

    private List<ProjectStudyDto> projectStudyDatas = new ArrayList<>();
    private HashSet<String> visitedUrls = new HashSet<>();
    private Logger logger = Logger.getLogger(this.getClass().getName());

    @Override
    @Scheduled(cron = "* */3 * * * *")
    public void processCrawling() {

        List<String> parseUrls = gatherUrls();

        parseData(parseUrls);
    }

    @Override
    public List<String> gatherUrls() {

        ArrayList<String> willParseUrls = new ArrayList<>();

        for (String baseUrl : baseParsingUrls) {

            boolean isParsing = true;

            for (int page = 1; isParsing; page++){
                Document parser;

                String nextPageUrl = baseUrl + "&page=" + page;

                try {
                    parser = Jsoup
                            .connect(nextPageUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36")
                            .get();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                // list items 목록 파싱
                final String listItemsSelector = ".question-container";
                Elements listItemsElements = parser.select(listItemsSelector);

                for (Element listItemElement : listItemsElements) {

                    // list item 생성일 파싱
                    final String listCreatedDateSelector = ".question__info-detail span:nth-child(3)";
                    String createdDate = listItemElement.selectFirst(listCreatedDateSelector).text();

                    // list item url 파싱
                    String url = baseInflearnUrl + listItemElement.selectFirst("a").attr("href");

                    // 한 달 전인지 검증 or 이미 방문한 사이트인지 검증
                    if (!validateDate(createdDate) || isVisited(url)) {
                        isParsing = false;
                        break;
                    }

                    // list item 모집유무 파싱(모집중 or 모집완료)
                    final String recruitingSelector = ".badge";
                    // 모집유무 검증
                    if (!isRecruiting(listItemElement.selectFirst(recruitingSelector).text()))
                        continue;

                    willParseUrls.add(url);
                    visitedUrls.add(url);
                }
            }
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
            Document parser;

            try {
                parser = Jsoup
                        .connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36")
                        .get();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // 제목 파싱
            final String titleSelector = ".header__title h1";
            String title = parser.selectFirst(titleSelector).text();

            // 닉네임 파싱
            final String nicknameSelector = ".user-name a";
            String nickname = parser.selectFirst(nicknameSelector).text();

            // 작성일 파싱
            final String createdDateSelector = ".sub-title__value";
            LocalDateTime createdDate = extractDateFromDateString(parser.selectFirst(createdDateSelector).text());

            // 기술스택 파싱
            final String techStacksSelector = ".ac-tag__name";
            List<String> techStacks = new ArrayList<>();
            parser.select(techStacksSelector).forEach(techStacksElement -> techStacks.add(techStacksElement.text()));

            // 원글 url
            String sourceUrl = url;

            //마감일
            CategoryType type = extractCategoryFromUrl(url);

            ProjectStudyDto projectStudyDto = ProjectStudyDto.builder()
                    .type(SiteType.INFLEARN)
                    .title(title)
                    .nickname(nickname)
                    .createdDate(createdDate)
                    .sourceUrl(sourceUrl)
                    .category(type)
                    .techList(techStacks)
                    .type(SiteType.INFLEARN)
                    .build();

            projectStudyDatas.add(projectStudyDto);

            logger.info(projectStudyDto.toString());
        }
    }

    public boolean isVisited(String url) {
        return visitedUrls.contains(url);
    }

    public boolean isRecruiting(String recruitingString) {
        return recruitingString.equals("모집중");
    }

    public boolean validateDate(String parsingDate) {

        // 임시로... 추가책이 필요함
        final String validDateSuffix = "달 전";

        if (parsingDate.endsWith(validDateSuffix))
            return false;

        return true;
    }


    LocalDateTime extractDateFromDateString(String parsingDate) {

        DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yy.MM.dd HH:mm");
        LocalDateTime localDateTime = LocalDateTime.parse(parsingDate, formatter2);

        return localDateTime;
    }

    public CategoryType extractCategoryFromUrl(String parsingUrl)  {
        try {
            String relativePath = new URL(parsingUrl).getPath();
            int toIdx = relativePath.indexOf("/", 1);

            String type = relativePath.substring(1,  toIdx);
            if (type.equals("projects"))
                return CategoryType.PROJECT;
            else if (type.equals("studies"))
                return CategoryType.STUDY;

            return null;

        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        return null;
    }
}
