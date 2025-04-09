package com.example.spf.services;

import com.example.spf.dtos.Filters;
import com.example.spf.dtos.ProductDTO;
import com.example.spf.requests.SearchRequest;
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
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

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

    public Mono<List<ProductDTO>> findByImage(MultipartFile multipartFile, SearchRequest request) {

        final String searchEngine = environment.getProperty("image.search.engine");
        final String searchType = environment.getProperty("image.search.type");
        final String searchCountry = environment.getProperty("image.search.country");
        final Filters filters = (request != null) ? request.filters() : Filters.emptyValue();
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
                            .filter(filterByMarketplaceExists(filters))
                            .toList();
                });
    }

    public Mono<List<ProductDTO>> findByText(String text, SearchRequest request) {
        final String textSearchEngine = environment.getProperty("text.search.engine");
        final String location = environment.getProperty("text.search.location");
        final Filters filters = (request != null) ? request.filters() : Filters.emptyValue();

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
                            .filter(product -> !Objects.isNull(product.get("link")) && !product.get("link").equals("#"))
                            .map(product -> {
                                Object delivery = product.get("delivery");
                                boolean freeDelivery = (!Objects.isNull(delivery) && delivery.equals("Бесплатная доставка"));

                                ProductDTO dto = new ProductDTO();
                                dto.setTitle((String) product.get("title"));
                                dto.setPrice((String) product.get("price"));
                                dto.setLink((String) product.get("link"));
                                dto.setSource((String) product.get("seller"));
                                dto.setImageLink((String) product.get("thumbnail"));

                                if(!Objects.isNull(product.get("rating")) && !product.get("rating").equals("#")){
                                    dto.setRating((double) product.get("rating"));
                                }
                                dto.setFreeDelivery(freeDelivery);
                                dto.setLogoUrl(fetchLogoUrl(dto.getLink()));
                                return dto;
                            })
                            .filter(filterByMarketplaceExists(filters))
                            .toList();
                });

    }

    private Predicate<ProductDTO> filterByMarketplaceExists(Filters filters) {
        if (filters != null && filters.marketplaces() != null && !filters.marketplaces().isEmpty()) {
            List<String> allowed = filters.marketplaces();
            return product -> marketplaceFilter(product.getSource(), allowed);
        } else {
            return product -> isSourceInKz(product.getSource());
        }
    }


    private boolean isSourceInKz(String source) {
        return source.contains(".kz");
    }

    private boolean marketplaceFilter(String source, List<String> marketPlaces){
        return marketPlaces.stream().anyMatch(marketPlace -> source.toLowerCase().contains(marketPlace.toLowerCase()));
    }

    private String fetchHost(String productUrl) {
        try {
            URL url = new URL(productUrl);
            return url.getHost();
        } catch (MalformedURLException e) {
            log.error("Url is mailformed, productUrl: {}", productUrl);
            throw new RuntimeException("Url is mailformed!");
        }
    }

    private String fetchLogoUrl(String productUrl) {
        return logoParserUrl.replace("{host}", fetchHost(productUrl));
    }
}
