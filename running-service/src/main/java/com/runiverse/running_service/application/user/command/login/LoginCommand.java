package com.runiverse.running_service.application.user.command.login;

public record LoginCommand(
        String email,
        String password
) {

}
