package com.questionforge.context;

import java.util.List;
import java.util.Map;

public record ContextBundle(
    MethodNode targetMethod,
    EnclosingClass enclosingClass,
    List<MethodNode> callers,
    List<MethodNode> callees,
    Map<String, Object> metadata,
    boolean truncated,
    List<DroppedItem> droppedItems,
    List<String> warnings
) {}
