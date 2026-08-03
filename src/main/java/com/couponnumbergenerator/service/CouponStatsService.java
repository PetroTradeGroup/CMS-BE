package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.response.CouponStatsSnapshot;

public interface CouponStatsService {

    CouponStatsSnapshot collect();
}