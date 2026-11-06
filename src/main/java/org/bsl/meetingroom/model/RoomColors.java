package org.bsl.meetingroom.model;

import java.util.List;
import java.util.Set;

/** Professional light calendar palette, synchronized with frontend/src/constants/roomColors.js. */
public final class RoomColors {
    private RoomColors() {}
    public static final List<String> PALETTE = List.of(
            "#2D79C7", "#168F91", "#8058CF", "#CE7147",
            "#C55487", "#369968", "#247DA8", "#BD831E",
            "#566CDA", "#B461A7", "#3699B4", "#837B36",
            "#B65F56", "#3977AD", "#5E8E55", "#A77643",
            "#7167C0", "#2E9284", "#AF5E93", "#B67538",
            "#4C87B2", "#6F915A", "#AE6577", "#426BB5",
            "#B1882E", "#2A8996", "#986BB5", "#C35F69",
            "#5985AD", "#8B8A46", "#B36D8C", "#567DB4"
    );

    public static String availableColor(Set<String> used) {
        return PALETTE.stream().filter(color -> !used.contains(color)).findFirst().orElse(null);
    }
    public static boolean valid(String color) {
        return color != null && PALETTE.contains(color);
    }
    /** Existing rooms may retain a color chosen under an earlier palette. */
    public static boolean storedHex(String color) {
        return color != null && color.matches("#[0-9a-fA-F]{6}");
    }
}
