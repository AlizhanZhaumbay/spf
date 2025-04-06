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
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    @Value("${application.key}")
    private String applicationKey;

    @Value("${search.hl}")
    private String searchHl;

    @Value("${logo.parser.url}")
    private String logoParserUrl;

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
        final String searchCountry = environment.getProperty("image.search.country");

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
                .uri(uriBuilder ->
                        uriBuilder.path("")
                                .queryParams(requestBody)
                                .build()
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

                                Map<String, Object> image = (Map<String, Object>) product.get("image");
                                dto.setImageLink((String) image.get("link"));
                                dto.setLogoUrl(fetchLogoUrl(dto.getLink()));

                                return dto;
                            })
                            .filter(product -> isSourceInKz(product.getLink()))
                            .toList();
                });
    }

    public Mono<List<ProductDTO>> findByText(String text) {
        final String textSearchEngine = environment.getProperty("text.search.engine");
        final String location = environment.getProperty("text.search.location");

        MultiValueMap<String, String> requestBody = MultiValueMap.fromSingleValue(Map.of(
                "engine", textSearchEngine,
                "api_key", applicationKey,
                "location", location,
                "hl", searchHl,
                "q", text,
                "gl", "kz",
                "google_domain", "google.kz"
        ));

        return webClient.get()
                .uri(uriBuilder ->
                        uriBuilder.path("")
                                .queryParams(requestBody)
                                .build()
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Ошибка запроса: статус = {}, тело ответа = {}", response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Ошибка запроса: " + errorBody));
                                })
                )
                .bodyToMono(Map.class)
                .map(json -> {
                    log.info(json.toString());
                    List<Map<String, Object>> shoppingAds = (List<Map<String, Object>>) json.get("shopping_results");
                    if (shoppingAds == null) {
                        return Collections.emptyList();
                    }
                    return shoppingAds.stream()
                            .map(product -> {
                                ProductDTO dto = new ProductDTO();
                                dto.setTitle((String) product.get("title"));
                                dto.setPrice((String) product.get("price"));
                                dto.setLink((String) product.get("link"));
                                dto.setSource((String) product.get("seller"));
                                dto.setImageLink((String) product.get("thumbnail"));
                                dto.setLogoUrl(fetchLogoUrl(dto.getLink()));
                                return dto;
                            })
                            .filter(product -> isSourceInKz(product.getLink()))
                            .toList();
                });

    }

    private boolean isSourceInKz(String source) {
        return source.contains(".kz");
    }

    private String fetchHost(String productUrl) {
        try {
            URL url = new URL(productUrl);
            return url.getHost();
        } catch (MalformedURLException e) {
            throw new RuntimeException("Url is mailformed!");
        }
    }

    private String fetchLogoUrl(String productUrl) {
        return logoParserUrl.replace("{host}", fetchHost(productUrl));
    }
}
