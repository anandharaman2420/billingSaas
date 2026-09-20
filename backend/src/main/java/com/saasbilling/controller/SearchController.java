package com.saasbilling.controller;

import com.saasbilling.dto.search.SearchDtos.GlobalSearchResponse;
import com.saasbilling.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public GlobalSearchResponse search(@RequestParam(required = false) String q) {
        return searchService.search(q);
    }
}
