package io.hyperfoil.tools.h5m.api;

public enum NodeType {
    FINGERPRINT("fp"),
    FIXED_THRESHOLD("ft"),
    JQ("jq"),
    JS("js"),
    JSONATA("nata"),
    RELATIVE_DIFFERENCE("rd"),
    ROOT("root"),
    SPLIT("split"),
    SQL_JSONPATH_ALL_NODE("sql-all"),
    SQL_JSONPATH_NODE("sql"),
    USER_INPUT("user");

    private final String display;

    NodeType(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
