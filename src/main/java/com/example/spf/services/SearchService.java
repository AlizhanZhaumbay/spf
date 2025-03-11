package com.example.spf.services;

import com.example.spf.dtos.ProductDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    @Value("${search.engine}")
    private String searchEngine;

    @Value("${search.type}")
    private String searchType;

    @Value("${application.key}")
    private String applicationKey;

    @Value("${temp.image.url}")
    private String tempImageUrl;

    @Value("${search.country}")
    private String searchCountry;

    @Value("${search.hl}")
    private String searchHl;

    private final WebClient webClient;

    public SearchService(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<List<ProductDTO>> findByImage(MultipartFile multipartFile) {
        MultiValueMap<String, String> requestBody = MultiValueMap.fromSingleValue(Map.of(
                "engine", searchEngine,
                "search_type", searchType,
                "url", tempImageUrl,
                "api_key", applicationKey,
                "country", searchCountry,
                "hl", searchHl
        ));

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.queryParams(requestBody).build()
                )
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                        Mono.error(new RuntimeException("Введены неправильные данные")))
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                        Mono.error(new RuntimeException("Что-то пошло не так"))
                )
                .bodyToMono(Map.class)
                .map(json -> {
                    List<Map<String, Object>> visualMatches = (List<Map<String, Object>>) json.get("visual_matches");
                    return visualMatches.stream()
                            .map(product -> {
                                ProductDTO dto = new ProductDTO();
                                dto.setTitle((String) product.get("title"));
                                dto.setPrice((String) product.get("price"));
                                dto.setLink((String) product.get("link"));
                                dto.setSource((String) product.get("source"));

                                LinkedHashMap<String, String> image = (LinkedHashMap<String, String>) product.get("image");
                                dto.setImageLink(image.get("link"));

                                return dto;
                            })
                            .toList();
                });
    }
}
