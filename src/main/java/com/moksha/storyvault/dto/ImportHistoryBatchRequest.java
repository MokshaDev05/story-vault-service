package com.moksha.storyvault.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ImportHistoryBatchRequest {

    @Min(1)
    private int currentPage;

    private Integer totalPages;

    @Valid
    @NotEmpty
    private List<ImportedStoryEntry> entries;
}
