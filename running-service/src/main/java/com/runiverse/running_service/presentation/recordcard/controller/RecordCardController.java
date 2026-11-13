package com.runiverse.running_service.presentation.recordcard.controller;

import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.port.in.CreateRecordCardDraftUsecase;
import com.runiverse.running_service.presentation.recordcard.request.CreateRecordCardDraftRequest;
import com.runiverse.running_service.presentation.recordcard.response.CreateRecordCardDraftResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// 기록 카드 작성·편집 — 작성은 두 단계다: 작성 요청(9-1)이 업로드 URL을 주고, 확정(9-2)이 카드를 저장한다
@RestController
@RequestMapping("record-cards")
@RequiredArgsConstructor
public class RecordCardController {

    private final CreateRecordCardDraftUsecase createRecordCardDraftUsecase;

    // 9-1 — 카드는 아직 없다. 작성 내용은 Redis 초안에만 두고 200으로 업로드 URL을 준다
    @PostMapping
    public ResponseEntity<CreateRecordCardDraftResponse> createDraft(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRecordCardDraftRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        CreateRecordCardDraftResult result = createRecordCardDraftUsecase.handle(new CreateRecordCardDraftCommand(
                userId, request.runningRecordId(), request.activityType(), request.memo(), request.visibility(),
                toImages(request.images()), request.thumbnailIndex()));
        return ResponseEntity.ok(CreateRecordCardDraftResponse.from(result));
    }

    // 생략한 사진 목록은 빈 목록과 같다 — Command가 null을 빈 목록으로 바꾼다
    private static List<CreateRecordCardDraftCommand.Image> toImages(List<CreateRecordCardDraftRequest.Image> images) {
        if (images == null) {
            return null;
        }
        return images.stream()
                .map(image -> new CreateRecordCardDraftCommand.Image(
                        image.mimeType(), image.fileSizeBytes(), image.fileName(), image.caption()))
                .toList();
    }
}
