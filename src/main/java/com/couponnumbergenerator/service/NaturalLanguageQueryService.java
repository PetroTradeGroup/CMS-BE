package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.response.NaturalLanguageQueryResponse;

public interface NaturalLanguageQueryService {

    NaturalLanguageQueryResponse query(String question, int page, int size);
}