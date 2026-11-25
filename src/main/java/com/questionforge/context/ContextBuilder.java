package com.questionforge.context;


public interface ContextBuilder {
    ContextBundle buildContext(String targetMethodId, int depth);
}
