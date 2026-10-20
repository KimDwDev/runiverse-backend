package com.runiverse.running_service.unit_test.user.application;

import com.runiverse.running_service.application.user.command.backgroundimage.BackgroundImageKeyPolicy;
import com.runiverse.running_service.application.user.command.profileimage.ProfileImageKeyPolicy;
import com.runiverse.running_service.application.user.common.ImageContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배경 이미지 key 정책 단위 테스트")
public class BackgroundImageKeyPolicyTest {

    private static final UUID OWNER = UUID.fromString("019ff918-a5ac-75b6-b20b-350454888411");
    private static final UUID IMAGE_ID = UUID.fromString("019ffa54-917f-7477-9482-5792597ef3b0");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000009");

    @Test
    @DisplayName("key는 소유자 prefix 아래에 mimeType으로 정한 확장자로 만든다")
    void createBuildsKeyUnderOwnerPrefix() {
        // when
        String key = BackgroundImageKeyPolicy.create(OWNER, IMAGE_ID, ImageContentType.PNG).value();

        // then
        assertThat(key).isEqualTo("backgrounds/%s/%s.png".formatted(OWNER, IMAGE_ID));
    }

    @Test
    @DisplayName("prefix는 backgrounds/{userId}/ 이다")
    void prefixOfEndsWithSeparator() {
        // when & then -> 탈퇴 시 이 prefix 전체를 지우므로 유저 폴더 경계까지 포함해야 한다
        assertThat(BackgroundImageKeyPolicy.prefixOf(OWNER)).isEqualTo("backgrounds/%s/".formatted(OWNER));
    }

    @Test
    @DisplayName("자기 prefix로 만든 key는 소유자로 인정한다")
    void ownKeyIsOwned() {
        // given
        String key = BackgroundImageKeyPolicy.create(OWNER, IMAGE_ID, ImageContentType.JPEG).value();

        // when & then
        assertThat(BackgroundImageKeyPolicy.isOwnedBy(key, OWNER)).isTrue();
    }

    @Test
    @DisplayName("다른 사용자의 key는 소유자로 인정하지 않는다")
    void otherUsersKeyIsNotOwned() {
        // given
        String key = BackgroundImageKeyPolicy.create(OWNER, IMAGE_ID, ImageContentType.JPEG).value();

        // when & then -> 남의 key를 자기 배경으로 붙이는 것을 막는 지점이다
        assertThat(BackgroundImageKeyPolicy.isOwnedBy(key, OTHER)).isFalse();
    }

    @Test
    @DisplayName("자기 프로필 사진 key는 배경 key로 인정하지 않는다")
    void ownProfileKeyIsNotOwnedAsBackground() {
        // given -> 같은 유저여도 prefix가 달라 프로필 사진을 배경으로 돌려 쓸 수 없다
        String profileKey = ProfileImageKeyPolicy.create(OWNER, IMAGE_ID, ImageContentType.JPEG).value();

        // when & then
        assertThat(BackgroundImageKeyPolicy.isOwnedBy(profileKey, OWNER)).isFalse();
    }

    @Test
    @DisplayName("자기 배경 key는 프로필 key로 인정하지 않는다")
    void ownBackgroundKeyIsNotOwnedAsProfile() {
        // given
        String backgroundKey = BackgroundImageKeyPolicy.create(OWNER, IMAGE_ID, ImageContentType.JPEG).value();

        // when & then
        assertThat(ProfileImageKeyPolicy.isOwnedBy(backgroundKey, OWNER)).isFalse();
    }

    @Test
    @DisplayName("경로 조작으로 자기 prefix를 흉내 낸 key는 인정하지 않는다")
    void traversalKeyIsNotOwned() {
        // given -> 클라이언트가 보낸 문자열이므로 이런 값이 올 수 있다
        String key = "backgrounds/%s/../%s/stolen.jpg".formatted(OTHER, OWNER);

        // when & then
        assertThat(BackgroundImageKeyPolicy.isOwnedBy(key, OWNER)).isFalse();
    }

    @Test
    @DisplayName("prefix가 일부만 겹치는 key는 인정하지 않는다")
    void partialPrefixIsNotOwned() {
        // given -> 구분자 없이 이어 붙여 앞부분만 같은 경우
        String key = "backgrounds/%sabc/photo.jpg".formatted(OWNER);

        // when & then
        assertThat(BackgroundImageKeyPolicy.isOwnedBy(key, OWNER)).isFalse();
    }
}
