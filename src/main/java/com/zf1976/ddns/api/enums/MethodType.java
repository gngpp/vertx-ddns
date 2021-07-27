package com.zf1976.ddns.api.enums;

public enum MethodType {
    GET(false),
    PUT(true),
    POST(true),
    DELETE(false),
    HEAD(false),
    PATCH(true),
    OPTIONS(false);

    private final boolean hasContent;

    MethodType(boolean hasContent) {
        this.hasContent = hasContent;
    }

    public boolean hasContent() {
        return hasContent;
    }
}
