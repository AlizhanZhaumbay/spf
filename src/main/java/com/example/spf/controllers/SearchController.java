package com.example.spf.controllers;

import com.example.spf.dtos.ProductDTO;
import com.example.spf.services.FileStorageService;
import com.example.spf.services.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("api")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService, FileStorageService fileStorageService) {
        this.searchService = searchService;
    }

    @PostMapping("find")
    public ResponseEntity<Mono<List<ProductDTO>>> find(
            @RequestParam("image") MultipartFile image){
        return ResponseEntity.ok(searchService.findByImage(image));
    }
}
