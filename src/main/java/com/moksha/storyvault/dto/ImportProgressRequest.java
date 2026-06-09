package com.moksha.storyvault.dto;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ImportProgressRequest {

    @Min(0)
    private int currentPage;

    private Integer totalPages;

    @Min(0)
    private int itemsProcessed;

    private List<StoryRequest> stories;
}
