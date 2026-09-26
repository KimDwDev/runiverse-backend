package com.runiverse.running_service.integration_test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

// 대상 클래스의 로거에 붙어 찍힌 로그를 모은다. 테스트가 끝나면 stop()으로 떼어낸다
public class LogCapture {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Class<?> target) {
        this.logger = (Logger) LoggerFactory.getLogger(target);
        appender.start();
        logger.addAppender(appender);
    }

    public static LogCapture of(Class<?> target) {
        return new LogCapture(target);
    }

    // 인자가 채워진 최종 메시지
    public List<String> messages(Level level) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    public void stop() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
