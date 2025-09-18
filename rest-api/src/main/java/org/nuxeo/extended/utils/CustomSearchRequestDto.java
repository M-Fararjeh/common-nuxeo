package org.nuxeo.extended.utils;

import java.util.List;

public class CustomSearchRequestDto {
    private List<SearchPredicateDto> predicateList;

    public CustomSearchRequestDto() {
    }

    public CustomSearchRequestDto(List<SearchPredicateDto> predicateList) {
        this.predicateList = predicateList;
    }

    public List<SearchPredicateDto> getPredicateList() {
        return predicateList;
    }

    public void setPredicateList(List<SearchPredicateDto> predicateList) {
        this.predicateList = predicateList;
    }
}
