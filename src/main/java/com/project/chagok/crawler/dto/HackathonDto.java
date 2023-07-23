package com.project.chagok.crawler.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@Setter
@Getter
@Builder
@ToString
public class HackathonDto {

    private String url;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String host;
    private String imgUrl;
    private String mainContents;
}
