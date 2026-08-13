package com.runiverse.running_service.application.user.command.profileimage;

import com.runiverse.running_service.application.user.port.in.CreateProfileImageUploadUrlUsecase;

public class CreateProfileImageUploadUrlHandle implements CreateProfileImageUploadUrlUsecase {

    @Override
    public CreateProfileImageUploadUrlResult handle(CreateProfileImageUploadUrlCommand command) {

        // 1. 이미지 저장을 위한 키네임 생성

        // 2. 키 위치에 맞는 upload_url 생성

        return null;
    }
}
