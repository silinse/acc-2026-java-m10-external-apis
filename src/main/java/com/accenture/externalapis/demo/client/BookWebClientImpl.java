package com.accenture.externalapis.demo.client;

import com.accenture.externalapis.demo.config.ExternalServiceProperties;
import com.accenture.externalapis.demo.dto.BookApiResponse;
import com.accenture.externalapis.demo.dto.BookDto;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
public class BookWebClientImpl implements BookWebClient {

    private final WebClient webClient;

    public BookWebClientImpl(WebClient.Builder builder, ExternalServiceProperties properties) {

        this.webClient = builder
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Override
    public Mono<BookDto> getBookAsync(Long id) {
        Mono<BookApiResponse> response = webClient.get()
                .uri("/books/{id}", id)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, res -> {
                    if (res.statusCode().value() == 404) {
                        return Mono.error(new ClientException("Book with id: " + id + " not found"));
                    }
                    return Mono.error(new ClientException("External API client error: " + res.statusCode()));
                })
                .onStatus(HttpStatusCode::is5xxServerError, res ->
                        Mono.error(new ClientException("External API server error: " + res.statusCode()))
                )
                .bodyToMono(BookApiResponse.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorMap(WebClientResponseException.class, e -> {
                    if (e.getCause() instanceof UnsupportedMediaTypeException) {
                        return new ClientException("External API returned an unsupported content type", e);

                    }
                    return new ClientException("External API response error: " + e.getMessage(), e);
                })
                .onErrorMap(WebClientRequestException.class, e ->
                        new ClientException("External API connection failed", e)
                )
                .onErrorMap(TimeoutException.class, e ->
                        new ClientException("External API request timed out", e)
                )
                .onErrorMap(DecodingException.class, e ->
                        new ClientException("External API returned an invalid response", e)
                )
                .switchIfEmpty(Mono.error(
                        new ClientException("External API returned an empty response")
                ));

        return response.map(book -> new BookDto(
                book.title(),
                book.author(),
                book.genre(),
                book.price(),
                book.isbn(),
                book.publishedYear()
        ));
    }

    @Override
    public Flux<BookDto> getAllBooksAsync() {
        Flux<BookApiResponse> response = webClient.get()
                .uri("/books")
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, res ->
                        Mono.error(new ClientException("External API client error: " + res.statusCode()))
                )
                .onStatus(HttpStatusCode::is5xxServerError, res ->
                        Mono.error(new ClientException("External API server error: " + res.statusCode()))
                )
                .bodyToFlux(BookApiResponse.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorMap(WebClientRequestException.class, e ->
                        new ClientException("External API connection failed", e)
                )
                .onErrorMap(TimeoutException.class, e ->
                        new ClientException("External API request timed out", e)
                );

        return response
                .map(book -> new BookDto(
                        book.title(),
                        book.author(),
                        book.genre(),
                        book.price(),
                        book.isbn(),
                        book.publishedYear()
                ));
    }

    @Override
    public Mono<List<BookDto>> getBooksInParallel(Long id1, Long id2) {
        Mono<BookDto> book1 = getBookAsync(id1);
        Mono<BookDto> book2 = getBookAsync(id2);

        return Mono.zip(book1, book2)
                .map(tuple -> List.of(
                        tuple.getT1(),
                        tuple.getT2()
                ));
    }
}
