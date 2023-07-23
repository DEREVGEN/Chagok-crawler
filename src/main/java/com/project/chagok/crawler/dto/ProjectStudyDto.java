package com.project.chagok.crawler.dto;

import com.project.chagok.crawler.constants.CategoryType;
import com.project.chagok.crawler.constants.SiteType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@ToString
public class ProjectStudyDto {

    private SiteType type;
    private String nickname;
    private String title;
    private LocalDateTime createdDate;
    private String sourceUrl;
    private CategoryType category;
    List<String> techList;

    public void setTechList(String tech) {
        techList.add(tech);
    }

    @Builder
    public ProjectStudyDto(SiteType type, String nickname, String title, LocalDateTime createdDate, String sourceUrl, CategoryType category, List<String> techList) {
        this.type = type;
        this.nickname = nickname;
        this.title = title;
        this.createdDate = createdDate;
        this.sourceUrl = sourceUrl;
        this.category = category;
        this.techList = techList;
    }
}
