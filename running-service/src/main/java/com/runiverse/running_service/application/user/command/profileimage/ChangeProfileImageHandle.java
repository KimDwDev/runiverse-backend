package com.runiverse.running_service.application.user.command.profileimage;

import com.runiverse.running_service.application.user.port.in.ChangeProfileImageUsecase;

public class ChangeProfileImageHandle implements ChangeProfileImageUsecase {

    @Override
    public ChangeProfileImageResult handle(ChangeProfileImageCommand command) {

        // 1.유저가 보낸 티켓 검증

        // 2. user_id로 user찾고 검증

        // 3. user에 profile_image_key 업데이트

        // 반환
        return null;
    }
}
