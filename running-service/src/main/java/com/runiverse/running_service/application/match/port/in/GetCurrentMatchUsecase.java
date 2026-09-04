package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.query.currentmatch.GetCurrentMatchQuery;
import com.runiverse.running_service.application.match.query.currentmatch.GetCurrentMatchResult;

public interface GetCurrentMatchUsecase {

    GetCurrentMatchResult handle(GetCurrentMatchQuery query);
}
