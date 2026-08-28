package com.eamon.bite.season;

/** 四季。声明顺序 = 展示顺序 = 季节循环顺序（秋起）。 */
public enum Season {
    AUTUMN("autumn"),
    WINTER("winter"),
    SPRING("spring"),
    SUMMER("summer");

    private final String id;

    Season(String id) {
        this.id = id;
    }

    /** lang key 段（如 "autumn" → bite.season.autumn）。 */
    public String id() {
        return id;
    }
}
