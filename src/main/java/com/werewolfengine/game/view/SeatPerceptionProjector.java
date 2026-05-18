package com.werewolfengine.game.view;

import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.game.observability.ActionLogEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Projects {@code action_log} into seat-visible {@link VisibleEvent}s (ADR-004).
 */
public final class SeatPerceptionProjector {

    private SeatPerceptionProjector() {
    }

    public static SeatPerceptionSlice project(GameRoomState room, int seat, List<ActionLogEntry> log) {
        if (room == null || log == null || log.isEmpty()) {
            return new SeatPerceptionSlice(List.of());
        }
        List<VisibleEvent> events = new ArrayList<>();
        for (ActionLogEntry entry : log) {
            LogVisibility vis = SeatVisibility.visibilityForEntry(room, seat, entry);
            if (vis == LogVisibility.HIDDEN) {
                continue;
            }
            VisibleEvent event = toVisibleEvent(room, seat, entry, vis);
            if (event != null) {
                events.add(event);
            }
        }
        return new SeatPerceptionSlice(events);
    }

    private static VisibleEvent toVisibleEvent(
            GameRoomState room,
            int seat,
            ActionLogEntry entry,
            LogVisibility vis
    ) {
        if (entry.playerId() == 0) {
            return systemEvent(entry, vis);
        }
        if (entry.thinking() != null && !entry.thinking().isBlank()) {
            if (seat != entry.playerId()) {
                return null;
            }
            return new VisibleEvent(
                    entry.round(),
                    entry.phase(),
                    PerceptionEventKind.SELF_THINKING,
                    entry.playerId(),
                    entry.target(),
                    truncate(entry.thinking(), 120)
            );
        }
        return playerEvent(room, seat, entry, vis);
    }

    private static VisibleEvent systemEvent(ActionLogEntry entry, LogVisibility vis) {
        String msg = entry.content();
        if (msg == null) {
            return null;
        }
        PerceptionEventKind kind;
        String summary;
        if (msg.startsWith("WOLF_KILL_RESOLVED")) {
            kind = PerceptionEventKind.WOLF_KILL_RESOLVED;
            Integer target = entry.target();
            summary = target != null ? "本夜刀口：座位 " + target : "本夜刀口已确定";
        } else if (msg.startsWith("NIGHT_DEATHS")) {
            kind = PerceptionEventKind.NIGHT_DEATH;
            summary = formatNightDeaths(msg);
        } else if (msg.startsWith("EXILE_ANNOUNCED")) {
            kind = PerceptionEventKind.EXILE_DEATH;
            summary = formatSeatMessage(msg, "放逐公布：座位 ");
        } else if (msg.startsWith("IDIOT_REVEALED")) {
            kind = PerceptionEventKind.IDIOT_REVEAL;
            summary = formatSeatMessage(msg, "愚者翻牌：座位 ");
        } else if (msg.startsWith("GAME_OVER")) {
            kind = PerceptionEventKind.GAME_OVER;
            summary = msg.replace("GAME_OVER ", "对局结束 ");
        } else if (msg.startsWith("HUNTER_SHOT")) {
            kind = PerceptionEventKind.HUNTER_SHOOT;
            summary = msg.replace("HUNTER_SHOT ", "猎人开枪 ");
        } else if (msg.startsWith("VOTE_") || msg.startsWith("EXILE_RESOLVED")) {
            kind = PerceptionEventKind.VOTE_RESULT;
            summary = msg;
        } else {
            if (vis == LogVisibility.HIDDEN) {
                return null;
            }
            kind = PerceptionEventKind.SYSTEM_OTHER;
            summary = msg;
        }
        return new VisibleEvent(
                entry.round(),
                entry.phase(),
                kind,
                null,
                entry.target(),
                summary
        );
    }

    private static VisibleEvent playerEvent(
            GameRoomState room,
            int seat,
            ActionLogEntry entry,
            LogVisibility vis
    ) {
        GameActionType action = entry.action();
        if (action == null) {
            return null;
        }
        boolean self = entry.playerId() == seat;
        PerceptionEventKind kind = classifyPlayerKind(room, seat, entry, action, self, vis);
        String summary = summarizePlayerAction(room, seat, entry, action, self, kind);
        if (summary == null || summary.isBlank()) {
            return null;
        }
        return new VisibleEvent(
                entry.round(),
                entry.phase(),
                kind,
                entry.playerId(),
                entry.target(),
                summary
        );
    }

    private static PerceptionEventKind classifyPlayerKind(
            GameRoomState room,
            int seat,
            ActionLogEntry entry,
            GameActionType action,
            boolean self,
            LogVisibility vis
    ) {
        if (action == GameActionType.WOLF_CHAT) {
            return PerceptionEventKind.WOLF_CHAT;
        }
        if (action == GameActionType.VOTE || action == GameActionType.SKIP_VOTE) {
            return PerceptionEventKind.PUBLIC_VOTE;
        }
        if (action == GameActionType.SPEAK || action == GameActionType.SKIP_SPEAK) {
            return self ? PerceptionEventKind.SELF_ACTION : PerceptionEventKind.PUBLIC_SPEAK;
        }
        if (self) {
            if (entry.phase() == GamePhase.NIGHT_WITCH
                    && (action == GameActionType.SAVE || action == GameActionType.POISON || action == GameActionType.SKIP)) {
                return PerceptionEventKind.WITCH_SELF;
            }
            if (action == GameActionType.CHECK) {
                return PerceptionEventKind.SEER_RESULT;
            }
            return PerceptionEventKind.SELF_ACTION;
        }
        return switch (action) {
            case SHOOT -> PerceptionEventKind.HUNTER_SHOOT;
            default -> PerceptionEventKind.SYSTEM_OTHER;
        };
    }

    private static String summarizePlayerAction(
            GameRoomState room,
            int seat,
            ActionLogEntry entry,
            GameActionType action,
            boolean self,
            PerceptionEventKind kind
    ) {
        int actor = entry.playerId();
        Integer target = entry.target();
        String seatLabel = "座位" + actor;
        return switch (kind) {
            case PUBLIC_SPEAK -> {
                String c = entry.content();
                yield c != null && !c.isBlank()
                        ? seatLabel + " 发言：" + truncate(c, 120)
                        : seatLabel + " 发言";
            }
            case PUBLIC_VOTE -> {
                if (self) {
                    yield target != null ? "你投票给座位" + target : "你弃票";
                }
                yield target != null
                        ? seatLabel + " 投票给座位" + target
                        : seatLabel + " " + action.name();
            }
            case WOLF_CHAT -> {
                String c = entry.content();
                if (self) {
                    yield c != null && !c.isBlank()
                            ? "你狼频道：" + truncate(c, 120)
                            : "你狼频道发言";
                }
                yield c != null && !c.isBlank()
                        ? seatLabel + " 狼频道：" + truncate(c, 120)
                        : seatLabel + " 狼频道发言";
            }
            case SEER_RESULT -> {
                if (!self) {
                    yield null;
                }
                String alignment = seerAlignment(room, target);
                yield target != null
                        ? "你查验座位" + target + "：" + alignment
                        : "你完成了查验";
            }
            case WITCH_SELF -> selfActionText(action, target);
            case SELF_ACTION -> selfActionText(action, target);
            case HUNTER_SHOOT -> target != null
                    ? seatLabel + " 开枪带走座位" + target
                    : seatLabel + " 开枪";
            default -> self
                    ? selfActionText(action, target)
                    : seatLabel + " " + action.name() + (target != null ? " 座位" + target : "");
        };
    }

    private static String selfActionText(GameActionType action, Integer target) {
        return switch (action) {
            case KILL -> target != null ? "你投票刀座位" + target : "你参与刀人";
            case SAVE -> target != null ? "你使用解药救座位" + target : "你使用解药";
            case POISON -> target != null ? "你毒杀座位" + target : "你使用毒药";
            case CHECK -> target != null ? "你查验座位" + target : "你查验";
            case SPEAK -> "你发言";
            case VOTE -> target != null ? "你投票给座位" + target : "你投票";
            case SKIP_VOTE -> "你弃票";
            case SKIP_SPEAK -> "你跳过发言";
            case WOLF_CHAT -> "你狼频道发言";
            case SHOOT -> target != null ? "你开枪带走座位" + target : "你开枪";
            default -> "你执行 " + action.name() + (target != null ? " 座位" + target : "");
        };
    }

    private static String seerAlignment(GameRoomState room, Integer target) {
        if (target == null) {
            return "未知";
        }
        PlayerState t = room.getPlayer(target);
        if (t == null || t.getRole() == null) {
            return "未知";
        }
        return t.getRole() == Role.WEREWOLF ? "狼" : "好人";
    }

    private static String formatNightDeaths(String msg) {
        String seats = msg.contains("seats=") ? msg.substring(msg.indexOf("seats=") + 6) : "";
        return seats.isBlank() ? "昨夜平安" : "昨夜死亡：" + seats;
    }

    private static String formatSeatMessage(String msg, String prefix) {
        int idx = msg.indexOf("seat=");
        if (idx < 0) {
            return msg;
        }
        String seatPart = msg.substring(idx + 5).split("\\s")[0];
        return prefix + seatPart;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }
}
