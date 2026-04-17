package com.judge.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class JudgeException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public JudgeException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static JudgeException notFound(String message) {
        return new JudgeException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static JudgeException forbidden(String message) {
        return new JudgeException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }

    public static JudgeException badRequest(String message) {
        return new JudgeException("BAD_REQUEST", message, HttpStatus.BAD_REQUEST);
    }
}
