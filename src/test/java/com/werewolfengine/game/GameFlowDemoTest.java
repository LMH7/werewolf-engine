package com.werewolfengine.game;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.message.payload.PhaseSyncPayload;

import org.junit.jupiter.api.Test;

/**
 * 控制台演示一局「第一夜 + 公布死讯 + 可选猎人 + 进入白天讨论」。
 * 运行：{@code mvnw.cmd -q test -Dtest=GameFlowDemoTest#printOneRoundDemo}
 */
class GameFlowDemoTest {

    private final GameStateMachine sm = new GameStateMachine();

    @Test
    void printOneRoundDemo() {
        String roomId = "demo_round";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();

        line("═══ 狼人杀引擎 · 第一夜演示（PRD v1.0.9）═══");
        printRoles(room);

        // ── 夜晚：狼人 ──
        section("1. 夜晚 · 狼人阶段 NIGHT_WOLF");
        int wolf = room.aliveWolfIds().getFirst();
        int victim = pickVillager(room);
        logAction("狼 " + wolf + " 刀", victim);
        sm.handleAction(roomId, cmd(wolf, GameActionType.KILL, victim, GamePhase.NIGHT_WOLF));
        for (int w : room.aliveWolfIds()) {
            if (!room.getWolfKillVotes().containsKey(w)) {
                logAction("狼 " + w + " 跟票", victim);
                sm.handleAction(roomId, cmd(w, GameActionType.KILL, victim, GamePhase.NIGHT_WOLF));
            }
        }
        room = refresh(roomId);
        println("  刀口决议 seat=" + room.getPendingWolfKillTarget() + " → 进入 " + room.getPhase());

        // ── 预言家 ──
        section("2. 夜晚 · 预言家 NIGHT_SEER（先于女巫）");
        room = refresh(roomId);
        if (room.getPhase() == GamePhase.NIGHT_SEER) {
            int seer = room.seerSeat();
            int check = room.alivePlayerIds().stream().filter(id -> id != seer).findFirst().orElseThrow();
            logAction("预言家 " + seer + " 查验", check);
            sm.handleAction(roomId, cmd(seer, GameActionType.CHECK, check, GamePhase.NIGHT_SEER));
            room = refresh(roomId);
            Role checkedRole = room.getPlayer(check).getRole();
            println("  查验结果（仅预言家可见）: " + room.getLastSeerCheckResult()
                    + " （目标 #" + check + " 实际为 " + checkedRole + "）");
        } else {
            println("  （预言家已出局，阶段已自动空过）");
        }

        // ── 女巫 ──
        section("3. 夜晚 · 女巫 NIGHT_WITCH");
        room = refresh(roomId);
        if (room.getPhase() == GamePhase.NIGHT_WITCH) {
            int witch = room.witchSeat();
            logAction("女巫 " + witch, "SKIP（不救不毒）");
            sm.handleAction(roomId, cmd(witch, GameActionType.SKIP, null, GamePhase.NIGHT_WITCH));
            room = refresh(roomId);
        }
        println("  昨夜死亡: " + room.getLastNightDeaths());
        println("  待公布后猎人: " + room.getPendingHunterAfterAnnounce());

        // ── 公布死讯 ──
        section("4. 天亮 · 公布死讯 NIGHT_DEATH_ANNOUNCE");
        assert room.getPhase() == GamePhase.NIGHT_DEATH_ANNOUNCE;
        println("  全场仅公布死亡名单（不区分刀/毒）");
        sm.advanceDayAnnounce(roomId);
        room = refresh(roomId);

        if (room.getPhase() == GamePhase.LAST_WORDS) {
            section("5. 遗言 LAST_WORDS（首夜昨夜死者）");
            println("  遗言顺序: " + room.getLastWordsOrder());
            for (int seat : new java.util.ArrayList<>(room.getLastWordsOrder())) {
                logAction("玩家 " + seat, "SKIP_SPEAK（遗言）");
                sm.handleAction(roomId, cmd(seat, GameActionType.SKIP_SPEAK, null, GamePhase.LAST_WORDS));
                room = refresh(roomId);
            }
        }

        // ── 猎人（若昨夜刀中猎人）──
        if (room.getPhase() == GamePhase.HUNTER_SHOOT) {
            section("6. 猎人开枪 HUNTER_SHOOT（遗言之后）");
            int hunter = room.getHunterShooterSeat();
            int shootTarget = room.alivePlayerIds().stream()
                    .filter(id -> id != hunter)
                    .findFirst()
                    .orElseThrow();
            logAction("猎人 " + hunter + " 开枪", shootTarget);
            sm.handleAction(roomId, cmd(hunter, GameActionType.SHOOT, shootTarget, GamePhase.HUNTER_SHOOT));
            room = refresh(roomId);
        } else {
            section("6. 猎人开枪 — 跳过（本局昨夜无合格猎人）");
        }

        // ── 白天讨论 ──
        section("7. 白天 · 讨论 DAY_DISCUSS");
        println("  发言锚点座位: " + room.getSpeakAnchorSeat());
        println("  发言方向: " + room.getSpeakDirection());
        println("  发言顺序: " + room.getDiscussOrder());
        if (!room.getDiscussOrder().isEmpty()) {
            int first = room.getDiscussOrder().getFirst();
            logAction("玩家 " + first, "SKIP_SPEAK");
            sm.handleAction(roomId, cmd(first, GameActionType.SKIP_SPEAK, null, GamePhase.DAY_DISCUSS));
            room = refresh(roomId);
            println("  下一位发言人索引: " + room.getDiscussIndex());
        }

        section("8. 当前局面快照");
        println("  阶段: " + room.getPhase() + "  |  轮次: " + room.getRound());
        println("  存活: " + room.alivePlayerIds());
        for (int id : room.alivePlayerIds()) {
            PlayerState p = room.getPlayer(id);
            println("    #" + id + " " + p.getRole() + (p.isCanVote() ? "" : " (无票权)"));
        }
        line("═══ 演示结束（完整白天投票 → 下一夜 可继续调 actions API）═══");
    }

    private static int pickVillager(GameRoomState room) {
        return room.alivePlayerIds().stream()
                .filter(id -> room.getPlayer(id).getRole() == Role.VILLAGER)
                .findFirst()
                .orElseThrow();
    }

    private static GameActionCommand cmd(int player, GameActionType action, Integer target, GamePhase phase) {
        return new GameActionCommand(player, action, target, phase);
    }

    private GameRoomState refresh(String roomId) {
        return sm.getRoom(roomId).orElseThrow();
    }

    private static void printRoles(GameRoomState room) {
        println("座位角色（演示用，实盘仅本人可见己方神职）:");
        for (int i = 1; i <= 12; i++) {
            PlayerState p = room.getPlayer(i);
            println("  #" + i + " → " + p.getRole());
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("▶ " + title);
    }

    private static void logAction(String who, Object what) {
        System.out.println("  → " + who + " · " + what);
    }

    private static void println(String s) {
        System.out.println(s);
    }

    private static void line(String s) {
        System.out.println(s);
    }
}
