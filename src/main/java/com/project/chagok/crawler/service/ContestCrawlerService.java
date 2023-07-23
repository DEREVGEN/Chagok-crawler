package com.project.chagok.crawler.service;

import com.project.chagok.crawler.dto.HackathonDto;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class ContestCrawlerService implements CrawlerService{

    private final WebDriver chromeDriver;
    HashSet<Long> visitedPages = new HashSet<>();
    static ArrayList<HackathonDto> hackathonDatas = new ArrayList<>();
    final String hackaThonUrl = "https://contestkorea.com/sub/list.php?int_gbn=1&Txt_bcode=030510001";

    Logger logger = Logger.getLogger(this.getClass().getName());


    /* crawling 프로세스
    1. 셀리니움을 이용한 pagination 및 url 수집
    2. 파싱한 페이지를 토대로, 해당 페이지에 접속, 원하는 데이터 추출 */
    @Scheduled(cron = "0 */3 * * * *")
    public void processCrawling() {
        List<String> parsePages = gatherUrls();

        parseData(parsePages);
        logger.info("rest...");
    }

    public List<String> gatherUrls() {


        chromeDriver.get(hackaThonUrl);

        // 접수중 버튼 셀렉터
        final String RegistrationBtnSelector = "#frm > div > div.clfx.mb_20 > div.f-r > ul > li:nth-child(4) > button";
        // 접수중인 페이지로 이동
        chromeDriver.findElement(By.cssSelector(RegistrationBtnSelector)).click();

        ArrayList<String> willParseUrls = new ArrayList<>();

        while(true) {
            // 리스트 a태그 셀렉터
            final String ListLinkSelector = ".list_style_2 .title a";
            List<WebElement> elementList = chromeDriver.findElements(By.cssSelector(ListLinkSelector));

            // 페이지에 대한 링크를 전부 list에 담음
            for (WebElement element : elementList) {

                // list item url 파싱
                String url = element.getAttribute("href");

                // 페이지 Id 추출하기 위한 delimiter
                final String pageIdDel = "str_no=";
                Long pageId = Long.valueOf(url.substring(url.lastIndexOf(pageIdDel) + pageIdDel.length()));

                // 페이지가 존재하지 않는다면..
                if (!visitedPages.contains(pageId)) {
                    willParseUrls.add(url);
                    visitedPages.add(pageId);
                }
            }

            // 다음 page 버튼 셀렉터
            final String PaginationNextBtnSelector = ".pagination .mg_right";
            WebElement nextButton = chromeDriver.findElement(By.cssSelector(PaginationNextBtnSelector));

            // 해당 페이지는 다음 버튼 페이지네이션을 js onclick기반으로 동작하기 때문에, onclick유무에 따라 다음 페이지가 있는지 없는지 확인 가능
            if (nextButton.getAttribute("onclick") == null) {
                break;
            }
            // 다음페이지로 이동
            nextButton.click();
        }

        return willParseUrls;
    }

    public void parseData(List<String> urls) {
        /*
        추출할 데이터
        1. 제목
        2. 개시일
        3. 종료일
        4. 주최기관
        5. 본문
        6. 포스터 URL
         */

        Document parser;

        for (String url : urls) {

            try {
                parser = Jsoup
                        .connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36")
                        .get();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }


            // 제목 파싱
            final String TitleSelector = ".view_top_area.clfx h1";
            String title = parser.select(TitleSelector).text();

            // 개시일 파싱 및 접수 종료일 파싱
            final String receptionDateSelector = "div.clfx  tr:nth-child(4) > td";
            String receptionDateData = parser.select(receptionDateSelector).text();
            // date format 시작일, 종료일 분리 및 LocalDate formatting
            String[] receptionDates = receptionDateData.split(" ~ ");

            LocalDate startReceptionDate = extractDate(receptionDates[0]);
            LocalDate endReceptionDate = extractDate(receptionDates[1]);

            // 주최기관 파싱
            final String hostSelector = "div.txt_area > table > tbody > tr:nth-child(1) > td";
            String host = parser.select(hostSelector).text();

            // 포스터 이미지 파싱
            final String imgSelector = ".img_area:first-child img";
            String imgUrl = "https://contestkorea.com" + parser.select(imgSelector).attr("src");

            // 글 본문
        /*Element mainContentsElement = parser.select(".view_detail_area .txt h2").first();
        System.out.println(mainContentsElement.nextElementSiblings());*/

            HackathonDto hackathonData = HackathonDto.builder()
                    .url(url)
                    .startDate(startReceptionDate)
                    .endDate(endReceptionDate)
                    .host(host)
                    .imgUrl(imgUrl)
                    .title(title).build();

            logger.info(hackathonData.toString());

            hackathonDatas.add(hackathonData);
        }
    }

    public LocalDate extractDate(String parsingDate) {
        DateTimeFormatter formatters = DateTimeFormatter.ofPattern("yyyy.MM.dd");

        return LocalDate.parse(parsingDate, formatters);
    }
}
