package org.bsl.meetingroom.model;

import java.util.List;
import java.util.Set;

/** Muted, readable color palette shared with frontend/src/constants/roomColors.js.
 * Calendar events use these as slim accents, never as text backgrounds.
 */
public final class RoomColors {
    private RoomColors() {}
    public static final List<String> PALETTE = List.of(
            "#426B91", "#477C76", "#76618E", "#946C4D",
            "#985D6A", "#5C7499", "#667C4D", "#806A9D",
            "#387C8B", "#9A7148", "#5D6EA7", "#427C64",
            "#9B6081", "#7A684B", "#56798C", "#87735A",
            "#9A6561", "#446FAD", "#6D846F", "#845B8B",
            "#A47A55", "#467F85", "#986E8F", "#6B7DA7",
            "#8D7747", "#6F7B96", "#98735F", "#587E78",
            "#8E657E", "#5973A0", "#7C825B", "#876E96"
    );
    public static String availableColor(Set<String> used) {
        return PALETTE.stream().filter(color -> !used.contains(color)).findFirst().orElse(null);
    }
    public static boolean valid(String color) {
        return color != null && PALETTE.contains(color);
    }
}
