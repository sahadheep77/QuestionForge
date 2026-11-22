package com.questionforge.context;

public record MethodNode(
    String methodId,
    String signature,
    String code,
    boolean signatureOnly,
    int hopDistance,
    String direction
) {}
