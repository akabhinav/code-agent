package com.joz.common.exception;

/** Sealed exception hierarchy — every error has a known type. */
public sealed class JozException extends RuntimeException
        permits JozException.ConfigException, JozException.LLMException,
                JozException.ToolException, JozException.SessionException {

    public JozException(String message) {
        super(message);
    }

    public JozException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Configuration loading or validation failure. */
    public static final class ConfigException extends JozException {
        public ConfigException(String message) { super(message); }
        public ConfigException(String message, Throwable cause) { super(message, cause); }
    }

    /** LLM API call failure. */
    public static final class LLMException extends JozException {
        public LLMException(String message) { super(message); }
        public LLMException(String message, Throwable cause) { super(message, cause); }
    }

    /** Tool execution failure. */
    public static final class ToolException extends JozException {
        public ToolException(String message) { super(message); }
        public ToolException(String message, Throwable cause) { super(message, cause); }
    }

    /** Session persistence failure. */
    public static final class SessionException extends JozException {
        public SessionException(String message) { super(message); }
        public SessionException(String message, Throwable cause) { super(message, cause); }
    }
}
