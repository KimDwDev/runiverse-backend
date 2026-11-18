package com.runiverse.running_service.unit_test.notification.domain.campaign;

import com.runiverse.running_service.domain.notification.campaign.PushCampaign;
import com.runiverse.running_service.domain.notification.campaign.exception.PushCampaignBodyRequiredException;
import com.runiverse.running_service.domain.notification.campaign.exception.PushCampaignTitleRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("푸시 캠페인 도메인 단위 테스트")
class PushCampaignTest {

    @Test
    @DisplayName("제목과 본문을 그대로 담는다")
    void keepsTitleAndBody() {
        // when
        PushCampaign campaign = new PushCampaign("서비스 점검 안내", " 11월 20일 02시부터 30분간 점검해요 ");

        // then -> 앞뒤 공백도 운영이 넣은 그대로 보낸다
        assertThat(campaign.getTitle()).isEqualTo("서비스 점검 안내");
        assertThat(campaign.getBody()).isEqualTo(" 11월 20일 02시부터 30분간 점검해요 ");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\n"})
    @DisplayName("제목이 없거나 공백뿐이면 필수 예외가 발생한다")
    void blankTitleFails(String title) {
        // when & then -> 길이 CHECK는 공백만 있는 문구를 통과시킨다
        assertThatThrownBy(() -> new PushCampaign(title, "본문"))
                .isInstanceOf(PushCampaignTitleRequiredException.class)
                .hasMessage("푸시 캠페인 제목은 필수입니다.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\n"})
    @DisplayName("본문이 없거나 공백뿐이면 필수 예외가 발생한다")
    void blankBodyFails(String body) {
        // when & then
        assertThatThrownBy(() -> new PushCampaign("제목", body))
                .isInstanceOf(PushCampaignBodyRequiredException.class)
                .hasMessage("푸시 캠페인 본문은 필수입니다.");
    }
}
