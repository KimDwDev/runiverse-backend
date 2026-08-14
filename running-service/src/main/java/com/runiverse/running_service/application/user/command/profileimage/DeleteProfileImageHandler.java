package com.runiverse.running_service.application.user.command.profileimage;

import com.runiverse.running_service.application.user.port.in.DeleteProfileImageUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DeleteProfileImageHandler implements DeleteProfileImageUsecase {


    @Override
    public void handle(DeleteProfileImageCommand command) {
        // s3 객체를 지우지 않고 DB의 key연결을 끊는다.
    }
}
