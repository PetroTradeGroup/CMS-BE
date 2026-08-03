package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.ai.AiInsightService;
import com.couponnumbergenerator.ai.InsightPromptBuilder;
import com.couponnumbergenerator.dto.response.CouponStatsSnapshot;
import com.couponnumbergenerator.dto.response.InsightResponse;
import com.couponnumbergenerator.service.CouponStatsService;
import com.couponnumbergenerator.service.InsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightServiceImpl implements InsightService {

    private final CouponStatsService statsService;
    private final InsightPromptBuilder promptBuilder;
    private final AiInsightService aiInsightService;

    @Override
    public InsightResponse generateInsights() {
        log.info("Generating AI insights — collecting stats");
        CouponStatsSnapshot stats = statsService.collect();

        String prompt = promptBuilder.build(stats);
        log.info("Requesting insight from AI provider");
        String summary = aiInsightService.generateInsight(prompt);

        return new InsightResponse(summary, stats, LocalDateTime.now());
    }
}