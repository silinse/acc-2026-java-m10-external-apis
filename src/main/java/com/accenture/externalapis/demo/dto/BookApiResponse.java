package com.accenture.externalapis.demo.dto;

// Open Swagger UI on the external service (https://external-api.acnbootcamp.lv/swagger-ui.html)
// and look at the response schema for GET /api/books/{id} - add exactly the
// fields it returns, with matching types. Once this matches the raw response,
// design your own BookDto with only the fields you actually need.
public record BookApiResponse(Long id,
                              String title,
                              String author,
                              String genre,
                              Double price,
                              String isbn,
                              Integer publishedYear) {
}
