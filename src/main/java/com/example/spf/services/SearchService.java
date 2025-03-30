package com.example.spf.services;

import com.example.spf.dtos.ProductDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    @Value("${application.key}")
    private String applicationKey;

    @Value("${search.country}")
    private String searchCountry;

    @Value("${search.hl}")
    private String searchHl;

    private final WebClient webClient;

    private final Environment environment;

    private final FileStorageService fileStorageService;

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    public SearchService(WebClient webClient, Environment environment, FileStorageService fileStorageService) {
        this.webClient = webClient;
        this.environment = environment;
        this.fileStorageService = fileStorageService;
    }

    public Mono<List<ProductDTO>> findByImage(MultipartFile multipartFile) {

        final String searchEngine = environment.getProperty("image.search.engine");
        final String searchType = environment.getProperty("image.search.type");

        final String imagePath;
        try {
            imagePath = fileStorageService.saveFile(multipartFile);
        } catch (IOException e) {
            throw new RuntimeException("Файл неправильно загружен");
        }

        MultiValueMap<String, String> requestBody = MultiValueMap.fromSingleValue(Map.of(
                "engine", searchEngine,
                "search_type", searchType,
                "url", imagePath,
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
                    log.info(json.toString());
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
                            .filter(product -> isSourceInPopularMarketplaces(product.getLink()))
                            .toList();
                });
    }

    public Mono<List<ProductDTO>> findByText(String text) {
        final String textSearchEngine = environment.getProperty("text.search.engine");
        final String productSearchEngine = environment.getProperty("product.search.engine");

        MultiValueMap<String, String> requestBody = MultiValueMap.fromSingleValue(Map.of(
                "engine", textSearchEngine,
                "api_key", applicationKey,
                "location", searchCountry,
                "hl", searchHl,
                "q", text
        ));

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.queryParams(requestBody).build())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                        Mono.error(new RuntimeException("Введены неправильные данные")))
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                        Mono.error(new RuntimeException("Что-то пошло не так"))
                )
                .bodyToMono(Map.class)
                .flatMapMany(json -> {
                    log.info(json.toString());
                    List<Map<String, Object>> visualMatches = (List<Map<String, Object>>) json.get("shopping_results");
                    return Flux.fromIterable(visualMatches);
                })
                .flatMap(product -> {
                    ProductDTO dto = new ProductDTO();
                    dto.setTitle((String) product.get("title"));
                    dto.setPrice((String) product.get("price"));
                    dto.setLink((String) product.get("product_link"));
                    dto.setSource((String) product.get("seller"));

                    Map<String, String> image = (Map<String, String>) product.get("image");
                    dto.setImageLink(image.get("link"));

                    String productId = (String) product.get("product_id");

                    // Получаем дополнительные данные о продукте, включая images
                    MultiValueMap<String, String> productDetailsParams = MultiValueMap.fromSingleValue(Map.of(
                            "engine", productSearchEngine,
                            "api_key", applicationKey,
                            "product_id", productId
                    ));

                    return webClient.get()
                            .uri(uriBuilder -> uriBuilder.queryParams(productDetailsParams).build())
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(productDetailsJson -> {
                                List<String> images = (List<String>) productDetailsJson.get("images");
                                if (images != null && !images.isEmpty()) {
                                    dto.setImageLink(images.get(0)); // Установка первого изображения
                                }
                                return dto;
                            });
                })
                .filter(product -> isSourceInPopularMarketplaces(product.getLink()))
                .collectList();
    }


    private boolean isSourceInPopularMarketplaces(String source){
//        String[] marketPlaces = {"kaspi.kz", "ozon.kz", "satu.kz", "technodom.kz"};
        return source.contains(".kz");
    }
}
