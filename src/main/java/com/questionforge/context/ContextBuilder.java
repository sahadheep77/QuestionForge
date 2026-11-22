package com.questionforge.context;

/**
 * Direct Java service contract representing Part 2 (Context Builder).
 */
public interface ContextBuilder {
    ContextBundle buildContext(String targetMethodId, int depth);
}
