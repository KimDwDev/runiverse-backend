package com.runiverse.running_service.unit_test.invite.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.running.invitation.RunningRoomInvitation;
import com.runiverse.running_service.domain.running.invitation.exception.InvalidInvitationStatusTransitionException;
import com.runiverse.running_service.domain.running.invitation.exception.InvalidRunningRoomInvitationIdException;
import com.runiverse.running_service.domain.running.invitation.exception.SelfInvitationException;
import com.runiverse.running_service.domain.running.invitation.vo.RunningRoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.exception.InvalidRunningRoomIdException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("러닝 초대 도메인 단위 테스트")
class RunningRoomInvitationTest {

    private static final Long ROOM_ID = 130L;
    private static final UUID INVITER = UuidCreator.getTimeOrderedEpoch();
    private static final UUID OTHER_INVITER = UuidCreator.getTimeOrderedEpoch();
    private static final UUID INVITEE = UuidCreator.getTimeOrderedEpoch();

    // 저장돼 있던 초대 — 각 테스트는 상태만 바꿔 복원한다
    private static RunningRoomInvitation stored(RunningRoomInvitationStatus status) {
        return RunningRoomInvitation.builder()
                .runningRoomInvitationId(31L)
                .runningRoomId(ROOM_ID)
                .inviterId(INVITER)
                .inviteeId(INVITEE)
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("새 초대는 대기 상태로 시작하고 식별자가 없다")
        void inviteStartsPending() {
            // when
            RunningRoomInvitation invitation = RunningRoomInvitation.invite(ROOM_ID, INVITER, INVITEE);

            // then
            assertThat(invitation.getStatus()).isEqualTo(RunningRoomInvitationStatus.PENDING);
            assertThat(invitation.isPending()).isTrue();
            assertThat(invitation.getRunningRoomId().value()).isEqualTo(ROOM_ID);
            assertThat(invitation.getInviterId().value()).isEqualTo(INVITER);
            assertThat(invitation.getInviteeId().value()).isEqualTo(INVITEE);
            assertThat(invitation.isNew()).isTrue();
            assertThat(invitation.getRunningRoomInvitationId()).isEmpty();
        }

        @Test
        @DisplayName("저장된 초대는 식별자와 상태를 그대로 복원한다")
        void restoresStoredInvitation() {
            // when
            RunningRoomInvitation invitation = stored(RunningRoomInvitationStatus.DECLINED);

            // then
            assertThat(invitation.isNew()).isFalse();
            assertThat(invitation.getRunningRoomInvitationId())
                    .hasValueSatisfying(id -> assertThat(id.value()).isEqualTo(31L));
            assertThat(invitation.getStatus()).isEqualTo(RunningRoomInvitationStatus.DECLINED);
        }

        @Test
        @DisplayName("자기 자신은 초대할 수 없다")
        void cannotInviteSelf() {
            // when & then -> DB CHECK(inviter_id <> invitee_id)에 닿기 전에 막는다
            assertThatThrownBy(() -> RunningRoomInvitation.invite(ROOM_ID, INVITEE, INVITEE))
                    .isInstanceOf(SelfInvitationException.class);
        }

        @Test
        @DisplayName("방 없는 초대는 만들 수 없다")
        void requiresRoom() {
            // when & then
            assertThatThrownBy(() -> RunningRoomInvitation.invite(null, INVITER, INVITEE))
                    .isInstanceOf(InvalidRunningRoomIdException.class);
        }

        @Test
        @DisplayName("1 미만의 식별자로는 복원할 수 없다")
        void rejectsInvalidId() {
            // when & then
            assertThatThrownBy(() -> RunningRoomInvitation.builder()
                    .runningRoomInvitationId(0L)
                    .runningRoomId(ROOM_ID)
                    .inviterId(INVITER)
                    .inviteeId(INVITEE)
                    .build())
                    .isInstanceOf(InvalidRunningRoomInvitationIdException.class);
        }
    }

    @Nested
    @DisplayName("수락 테스트")
    class AcceptTest {

        @Test
        @DisplayName("대기 중인 초대를 수락한다")
        void acceptsPending() {
            // given
            RunningRoomInvitation invitation = stored(RunningRoomInvitationStatus.PENDING);

            // when
            invitation.accept();

            // then
            assertThat(invitation.getStatus()).isEqualTo(RunningRoomInvitationStatus.ACCEPTED);
            assertThat(invitation.isPending()).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RunningRoomInvitationStatus.class, names = {"ACCEPTED", "DECLINED", "LEFT", "EXPIRED"})
        @DisplayName("대기 중이 아닌 초대는 수락할 수 없다")
        void cannotAcceptNonPending(RunningRoomInvitationStatus status) {
            // given -> 거절 뒤 들어가려면 다시 초대받아야 한다
            RunningRoomInvitation invitation = stored(status);

            // when & then
            assertThatThrownBy(invitation::accept)
                    .isInstanceOf(InvalidInvitationStatusTransitionException.class);
            assertThat(invitation.getStatus()).isEqualTo(status);
        }
    }

    @Nested
    @DisplayName("다시 초대 테스트")
    class ReinviteTest {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RunningRoomInvitationStatus.class, names = {"DECLINED", "LEFT"})
        @DisplayName("거절했거나 수락 뒤 나간 초대는 같은 행이 대기로 되살아나고 보낸 사람이 바뀐다")
        void revivesAsPending(RunningRoomInvitationStatus status) {
            // given
            RunningRoomInvitation invitation = stored(status);

            // when -> 방장이 없어 다른 참가자가 다시 부를 수 있다
            invitation.reinvite(OTHER_INVITER);

            // then
            assertThat(invitation.getStatus()).isEqualTo(RunningRoomInvitationStatus.PENDING);
            assertThat(invitation.getInviterId().value()).isEqualTo(OTHER_INVITER);
            assertThat(invitation.getRunningRoomInvitationId()).isPresent();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RunningRoomInvitationStatus.class, names = {"PENDING", "ACCEPTED", "EXPIRED"})
        @DisplayName("대기 중·방에 있음·만료된 초대는 되살리지 않고 보낸 사람도 그대로다")
        void cannotReviveNonEnded(RunningRoomInvitationStatus status) {
            // given -> 대기 중·방에 있음은 애플리케이션이 건너뛰고, 만료는 마감 뒤라 초대 자체가 막힌다
            RunningRoomInvitation invitation = stored(status);

            // when & then
            assertThatThrownBy(() -> invitation.reinvite(OTHER_INVITER))
                    .isInstanceOf(InvalidInvitationStatusTransitionException.class);
            assertThat(invitation.getStatus()).isEqualTo(status);
            assertThat(invitation.getInviterId().value()).isEqualTo(INVITER);
        }

        @Test
        @DisplayName("받은 사람이 자기 자신을 다시 초대할 수 없고 상태도 바뀌지 않는다")
        void cannotReinviteSelf() {
            // given
            RunningRoomInvitation invitation = stored(RunningRoomInvitationStatus.DECLINED);

            // when & then -> 검증이 상태 전이보다 먼저라 반쯤 바뀐 채로 남지 않는다
            assertThatThrownBy(() -> invitation.reinvite(INVITEE))
                    .isInstanceOf(SelfInvitationException.class);
            assertThat(invitation.getStatus()).isEqualTo(RunningRoomInvitationStatus.DECLINED);
        }
    }
}
