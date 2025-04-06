package com.example.spf.controllers;

import com.example.spf.dtos.ProductDTO;
import com.example.spf.services.FileStorageService;
import com.example.spf.services.SearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

@RestController
@RequestMapping("api")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService, FileStorageService fileStorageService) {
        this.searchService = searchService;
    }

    @GetMapping("search/image")
    public ResponseEntity<Mono<List<ProductDTO>>> findByImage(
            @RequestParam("image") MultipartFile image){
        return ResponseEntity.ok(searchService.findByImage(image));
    }

    @GetMapping("search/text")
    public ResponseEntity<Mono<List<ProductDTO>>> findByText(
            @RequestParam("text") String text){
        return ResponseEntity.ok(searchService.findByText(text));
    }
}
