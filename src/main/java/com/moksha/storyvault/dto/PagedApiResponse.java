package com.moksha.storyvault.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedApiResponse<T> {

    private boolean success;
    private String message;
    private List<T> data;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
    private LocalDateTime timestamp;

    public static <T> PagedApiResponse<T> success(String message, List<T> data,
                                                   long totalElements, int totalPages,
                                                   int page, int size) {
        return PagedApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .page(page)
                .size(size)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
