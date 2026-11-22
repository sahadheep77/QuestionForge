package com.questionforge.context;

import java.util.List;

public record EnclosingClass(
    String classId,
    String className,
    List<String> fields,
    List<String> methodSignatures,
    String sourceCode
) {}
