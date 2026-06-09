package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.ImportErrorRequest;
import com.moksha.storyvault.dto.ImportHistoryBatchRequest;
import com.moksha.storyvault.dto.ImportJobRequest;
import com.moksha.storyvault.dto.ImportJobResponse;
import com.moksha.storyvault.dto.ImportProgressRequest;

import java.util.List;

public interface ImportService {
    ImportJobResponse create(ImportJobRequest request);
    ImportJobResponse getById(Long id);
    List<ImportJobResponse> listAll();
    ImportJobResponse start(Long id);
    ImportJobResponse updateProgress(Long id, ImportProgressRequest request);
    ImportJobResponse pause(Long id);
    ImportJobResponse resume(Long id);
    ImportJobResponse cancel(Long id);
    ImportJobResponse complete(Long id);
    ImportJobResponse fail(Long id, ImportErrorRequest request);
    ImportJobResponse processHistoryBatch(Long id, ImportHistoryBatchRequest request);
}
