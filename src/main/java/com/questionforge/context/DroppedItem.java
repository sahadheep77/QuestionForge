package com.questionforge.context;

public record DroppedItem(
    String methodId,
    String reason,
    int hopDistance
) {}
