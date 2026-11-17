package com.runiverse.running_service.domain.running.room;

import com.runiverse.running_service.domain.running.room.exception.NotRunningRoomChatMessageException;
import com.runiverse.running_service.domain.running.room.exception.NotRunningRoomChatSenderException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatChangedAtRequiredException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatDeletedException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatNotSavedException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatRoomRequiredException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatSenderRequiredException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatTypeRequiredException;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomChatContent;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomChatId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomChatType;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

// 대기방에서 오간 채팅 한 건 — 보낸 본인만 고치고 지운다. 지우면 되돌리지 않고 그 뒤로는 고칠 수 없다.
// 입장·퇴장 안내 줄도 같은 애그리거트다 — 내용이 없고 고치거나 지우지 않는다.
// 방과 같은 패키지지만 RunningRoom이 들고 있지 않은 별도 애그리거트다 — 방은 ID로만 가리킨다.
// 방에 넣으면 채팅마다 방 행을 잠가 참가·이탈과 서로 기다린다(architecture.md 레이어별 규칙).
// 방 안에 있는지(배정·JOINED·방 MATCHING/MATCHED)는 방과 신청을 봐야 해서 application이 판정한다.
// 보낸 사람이 탈퇴해도 이 채팅은 그대로다 — 탈퇴는 users에 행이 없는 것으로 드러난다
@Getter
public class RunningRoomChat {

    private final RunningRoomChatId runningRoomChatId;   // 저장 전에는 null
    private final RunningRoomId runningRoomId;
    private final UserId userId;                         // 보낸 사람 — 안내 줄은 들어오거나 나간 사람
    private final RunningRoomChatType type;
    private RunningRoomChatContent content;              // 지금 보이는 내용 — 지워도 남는다(신고 대응). 안내 줄은 null
    private LocalDateTime editedAt;                      // 마지막 수정 시각 — 고친 적 없으면 null
    private LocalDateTime deletedAt;                     // 지운 시각 — 한 번 찍히면 바뀌지 않는다

    private RunningRoomChat(RunningRoomChatId runningRoomChatId, RunningRoomId runningRoomId, UserId userId,
                            RunningRoomChatType type, RunningRoomChatContent content,
                            LocalDateTime editedAt, LocalDateTime deletedAt) {
        if (runningRoomId == null) {
            throw new RunningRoomChatRoomRequiredException();
        }
        if (userId == null) {
            throw new RunningRoomChatSenderRequiredException();
        }
        if (type == null) {
            throw new RunningRoomChatTypeRequiredException();
        }
        this.runningRoomChatId = runningRoomChatId;
        this.runningRoomId = runningRoomId;
        this.userId = userId;
        this.type = type;
        this.content = content;
        this.editedAt = editedAt;
        this.deletedAt = deletedAt;
    }

    // 새로 보내는 채팅 — ID는 저장할 때 생긴다
    public static RunningRoomChat send(RunningRoomId runningRoomId, UserId sender, String content) {
        return new RunningRoomChat(null, runningRoomId, sender, RunningRoomChatType.MESSAGE,
                new RunningRoomChatContent(content), null, null);
    }

    public static RunningRoomChat joined(RunningRoomId runningRoomId, UserId userId) {
        return notice(runningRoomId, userId, RunningRoomChatType.JOINED);
    }

    public static RunningRoomChat rejoined(RunningRoomId runningRoomId, UserId userId) {
        return notice(runningRoomId, userId, RunningRoomChatType.REJOINED);
    }

    public static RunningRoomChat left(RunningRoomId runningRoomId, UserId userId) {
        return notice(runningRoomId, userId, RunningRoomChatType.LEFT);
    }

    // 저장된 채팅을 되돌린다
    public static RunningRoomChat restore(Long runningRoomChatId, Long runningRoomId, UUID userId,
                                          RunningRoomChatType type, String content,
                                          LocalDateTime editedAt, LocalDateTime deletedAt) {
        RunningRoomChatContent restored = type == RunningRoomChatType.MESSAGE
                ? new RunningRoomChatContent(content)
                : null;
        return new RunningRoomChat(new RunningRoomChatId(runningRoomChatId), new RunningRoomId(runningRoomId),
                new UserId(userId), type, restored, editedAt, deletedAt);
    }

    // 내용을 바꾸고 바꾸기 직전 내용을 이력으로 돌려준다 — 이력 저장은 같은 트랜잭션에서 호출한 쪽이 한다.
    // 같은 내용으로 고쳐도 수정으로 친다
    public RunningRoomChatHistory edit(UserId editor, String newContent, LocalDateTime editedAt) {
        RunningRoomChatId savedId = requireChangeable(editor, editedAt);
        RunningRoomChatContent next = new RunningRoomChatContent(newContent);
        RunningRoomChatHistory history = RunningRoomChatHistory.of(savedId, content);
        this.content = next;
        this.editedAt = editedAt;
        return history;
    }

    // 소프트 삭제 — 내용은 남기고 지운 시각만 찍는다. 되돌리는 경로는 없다
    public void delete(UserId requester, LocalDateTime deletedAt) {
        requireChangeable(requester, deletedAt);
        this.deletedAt = deletedAt;
    }

    public boolean isSentBy(UserId userId) {
        return this.userId.equals(userId);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isEdited() {
        return editedAt != null;
    }

    public Optional<RunningRoomChatId> getRunningRoomChatId() {
        return Optional.ofNullable(runningRoomChatId);
    }

    public Optional<RunningRoomChatContent> getContent() {
        return Optional.ofNullable(content);
    }

    public Optional<LocalDateTime> getEditedAt() {
        return Optional.ofNullable(editedAt);
    }

    public Optional<LocalDateTime> getDeletedAt() {
        return Optional.ofNullable(deletedAt);
    }

    private static RunningRoomChat notice(RunningRoomId runningRoomId, UserId userId, RunningRoomChatType type) {
        return new RunningRoomChat(null, runningRoomId, userId, type, null, null, null);
    }

    // 고치기·지우기 공통 조건 — 저장됐고, 사람이 쓴 말이고, 보낸 본인이고, 아직 지우지 않았다
    private RunningRoomChatId requireChangeable(UserId requester, LocalDateTime changedAt) {
        if (changedAt == null) {
            throw new RunningRoomChatChangedAtRequiredException();
        }
        if (runningRoomChatId == null) {
            throw new RunningRoomChatNotSavedException();
        }
        if (type != RunningRoomChatType.MESSAGE) {
            throw new NotRunningRoomChatMessageException();
        }
        if (!isSentBy(requester)) {
            throw new NotRunningRoomChatSenderException();
        }
        if (isDeleted()) {
            throw new RunningRoomChatDeletedException();
        }
        return runningRoomChatId;
    }
}
