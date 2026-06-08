package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.ImportJobRequest;
import com.moksha.storyvault.dto.ImportJobResponse;

import java.util.List;

public interface ImportService {
    ImportJobResponse create(ImportJobRequest request);
    List<ImportJobResponse> listAll();
}
