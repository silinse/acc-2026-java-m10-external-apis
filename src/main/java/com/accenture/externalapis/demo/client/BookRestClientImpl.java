package com.accenture.externalapis.demo.client;

import com.accenture.externalapis.demo.config.ExternalServiceProperties;
import com.accenture.externalapis.demo.dto.BookApiResponse;
import com.accenture.externalapis.demo.dto.BookDto;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.UnknownContentTypeException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Component
public class BookRestClientImpl implements BookRestClient {

    private final RestClient restClient;

    public BookRestClientImpl(RestClient.Builder builder, ExternalServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        factory.setConnectTimeout(Duration.ofSeconds(5));

        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public BookDto getBook(Long id) {
        try {
            BookApiResponse response = restClient.get()
                    .uri("/books/{id}", id)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() == 404) {
                            throw new ClientException("Book with id: " + id + " not found");
                        }
                        throw new ClientException("External API client error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ClientException("External API server error: " + res.getStatusCode());
                    })
                    .body(BookApiResponse.class);

            if (response == null) {
                throw new ClientException("External API returned an empty response");
            }

            return new BookDto(response.title(),
                    response.author(),
                    response.genre(),
                    response.price(),
                    response.isbn(),
                    response.publishedYear()
            );
        } catch (ResourceAccessException e) {
            throw new ClientException("External API connection failed or timed out", e);
        } catch (UnknownContentTypeException e) {
            throw new ClientException("External API returned an unsupported content type", e);
        } catch (RestClientException e) {
            throw new ClientException("External API returned an invalid response", e);
        }
    }

    @Override
    public List<BookDto> getAllBooks() {
        try {
            BookApiResponse[] response = restClient.get()
                    .uri("/books")
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ClientException("External API client error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ClientException("External API server error: " + res.getStatusCode());
                    })
                    .body(BookApiResponse[].class);

            if (response == null) {
                throw new ClientException("External API returned an empty response");
            }

            return Arrays.stream(response)
                    .map(book -> new BookDto(
                            book.title(),
                            book.author(),
                            book.genre(),
                            book.price(),
                            book.isbn(),
                            book.publishedYear()
                    ))
                    .toList();
        } catch (ResourceAccessException e) {
            throw new ClientException("External API connection failed or timed out", e);
        } catch (UnknownContentTypeException e) {
            throw new ClientException("External API returned an unsupported content type", e);
        }
    }
}
