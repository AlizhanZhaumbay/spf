package com.example.spf.controllers;

import com.example.spf.dtos.ProductDTO;
import com.example.spf.requests.ImageSearchRequest;
import com.example.spf.requests.TextSearchRequest;
import com.example.spf.services.SearchService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("api")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping(value = "search/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Mono<List<ProductDTO>>> findByImage(
            @RequestPart("image") MultipartFile image, @RequestPart(value = "data", required = false) ImageSearchRequest request){
        return ResponseEntity.ok(searchService.findByImage(image, request));
    }

    @PostMapping("search/text")
    public ResponseEntity<Mono<List<ProductDTO>>> findByText(
            @RequestBody TextSearchRequest searchRequest){
        return ResponseEntity.ok(searchService.findByText(searchRequest));
    }
}
