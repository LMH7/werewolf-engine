package com.werewolfengine.game;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.GameWinner;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.game.model.SeerCheckResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多夜剧本集成测试（固定座位 1～12）。
 * <ul>
 *   <li>{@link #simulateThreeNightWolfWin()} — 狼人三夜屠神胜</li>
 *   <li>{@link #simulateFiveNightVillagersWin()} — 自刀骗药、平票、五夜好人胜</li>
 *   <li>{@link #simulateFourNightWolfWinLastGodExiled()} — 女巫两夜、毒预、票猎终局狼胜</li>
 * </ul>
 */
class GameScenarioDemoTest {

    private static final int W1 = 1;
    private static final int W2 = 2;
    private static final int W3 = 3;
    private static final int W4 = 4;
    private static final int V1 = 5;
    private static final int V2 = 6;
    private static final int V3 = 7;
    private static final int V4 = 8;
    private static final int IDIOT = 9;
    private static final int SEER = 10;
    private static final int WITCH = 11;
    private static final int HUNTER = 12;

    private final GameStateMachine sm = new GameStateMachine();

    @Test
    void simulateThreeNightWolfWin() {
        String roomId = "scenario_3night";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        assignFixedRoles(sm.getRoom(roomId).orElseThrow());

        line("═══ 狼人杀引擎 · 三夜剧本模拟（PRD v1.0.12）═══");
        printBoard(sm.getRoom(roomId).orElseThrow(), "开局发牌");

        // ── 第一夜：狼刀村民，女巫救，预言家查猎人 ──
        section("第一夜 · 狼人刀 #" + V1 + "（村民）");
        runWolfKill(roomId, V1);
        log("  刀口决议 seat=" + refresh(roomId).getPendingWolfKillTarget());

        section("第一夜 · 预言家 #" + SEER + " 查验 #" + HUNTER + "（猎人）");
        runSeerCheck(roomId, HUNTER);
        GameRoomState room = refresh(roomId);
        assertThat(room.getLastSeerCheckResult()).isEqualTo(SeerCheckResult.GOOD);
        log("  查验结果：好人（神职对外仅展示 GOOD/WOLF）");

        section("第一夜 · 女巫 #" + WITCH + " 使用解药救 #" + V1);
        runWitchSave(roomId);
        room = refresh(roomId);
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_DEATH_ANNOUNCE);
        assertThat(room.getLastNightDeaths()).isEmpty();
        log("  昨夜无人死亡（解药生效）");

        advancePastNightAnnounce(roomId);
        room = refresh(roomId);
        assertThat(room.getPhase()).isEqualTo(GamePhase.DAY_DISCUSS);
        log("  首夜无遗言（无人死亡）→ 进入白天讨论");

        // ── 白天：投票猎人出局，开枪带走预言家 ──
        section("第一天 · 投票放逐猎人 #" + HUNTER);
        finishDayVoteExile(roomId, HUNTER, SEER);
        room = refresh(roomId);
        assertThat(room.getPlayer(SEER).isAlive()).isFalse();
        assertThat(room.getPlayer(HUNTER).isAlive()).isFalse();
        log("  猎人 #" + HUNTER + " 与预言家 #" + SEER + " 已出局");

        // ── 第二夜：狼刀女巫，女巫毒狼 ──
        section("第二夜 · 狼人刀女巫 #" + WITCH);
        runWolfKill(roomId, WITCH);
        log("  女巫可见刀口 target=" + refresh(roomId).getPendingWolfKillTarget());

        section("第二夜 · 预言家 #" + SEER + " 已死亡，阶段空过");
        room = refresh(roomId);
        if (room.getPhase() == GamePhase.NIGHT_SEER) {
            log("  （引擎保留 NIGHT_SEER 阶段位，无人操作）");
        }

        section("第二夜 · 女巫 #" + WITCH + " 毒杀狼人 #" + W1 + "（不救，同夜仅毒）");
        runWitchPoison(roomId, W1);
        room = refresh(roomId);
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_DEATH_ANNOUNCE);
        assertThat(room.getLastNightDeaths()).containsExactlyInAnyOrder(WITCH, W1);
        log("  昨夜死亡：女巫 #" + WITCH + "、狼人 #" + W1);

        advancePastNightAnnounce(roomId);
        room = refresh(roomId);
        assertThat(room.getPhase()).isEqualTo(GamePhase.DAY_DISCUSS);
        assertThat(room.getRound()).isEqualTo(2);
        log("  第二夜无遗言 → 白天讨论");

        // ── 白天：投票白痴翻牌 ──
        section("第二天 · 投票 #" + IDIOT + "（白痴），翻牌不离场");
        finishDayVoteIdiotReveal(roomId, IDIOT);
        room = refresh(roomId);
        assertThat(room.getPlayer(IDIOT).isAlive()).isTrue();
        assertThat(room.getPlayer(IDIOT).isIdiotRevealed()).isTrue();
        assertThat(room.getPlayer(IDIOT).isCanVote()).isFalse();
        log("  白痴翻牌，仍可发言、不可投票");

        // ── 第三夜：狼刀白痴，屠边狼胜 ──
        section("第三夜 · 狼人刀白痴 #" + IDIOT);
        runWolfKill(roomId, IDIOT);
        runSeerCheck(roomId, V2);
        runWitchSkip(roomId);
        room = refresh(roomId);
        assertThat(room.getPhase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(room.getWinner()).isEqualTo(GameWinner.WEREWOLVES);
        log("  白痴死亡 → 四神全灭 → 狼人获胜");

        printBoard(room, "终局");
        line("═══ 剧本结束：WEREWOLVES 胜 ═══");
    }

    @Test
    void simulateFiveNightVillagersWin() {
        String roomId = "scenario_5night_good";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        assignFixedRoles(sm.getRoom(roomId).orElseThrow());

        line("═══ 狼人杀引擎 · 五夜剧本（好人胜）═══");
        printBoard(refresh(roomId), "开局");

        // ── 第一夜：狼自刀 #1，预查猎，女巫不救 ──
        section("第一夜 · 狼人自刀 #" + W1 + "（骗解药）");
        runWolfSelfKill(roomId, W1);
        runSeerCheck(roomId, HUNTER);
        assertThat(refresh(roomId).getLastSeerCheckResult()).isEqualTo(SeerCheckResult.GOOD);
        log("  预言家查验 #" + HUNTER + " → 好人");

        section("第一夜 · 女巫 #" + WITCH + " 不救（SKIP）");
        runWitchSkip(roomId);
        GameRoomState room = refresh(roomId);
        assertThat(room.getLastNightDeaths()).containsExactly(W1);
        assertThat(room.isWitchAntidoteRemaining()).isTrue();
        log("  狼人 #" + W1 + " 死亡，解药未用");

        advancePastNightAnnounce(roomId);
        assertThat(refresh(roomId).getPhase()).isEqualTo(GamePhase.DAY_DISCUSS);

        section("第一天 · 投票平票，无人出局");
        Map<Integer, Integer> day1Votes = new HashMap<>();
        day1Votes.put(W2, V1);
        day1Votes.put(W3, V1);
        day1Votes.put(W4, V1);
        day1Votes.put(SEER, V1);
        day1Votes.put(WITCH, V1);
        day1Votes.put(V1, V2);
        day1Votes.put(V2, V2);
        day1Votes.put(V3, V2);
        day1Votes.put(V4, V2);
        day1Votes.put(HUNTER, V2);
        finishDayVoteTie(roomId, day1Votes);
        room = refresh(roomId);
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_WOLF);
        assertThat(room.getRound()).isEqualTo(2);
        assertThat(room.getPlayer(W1).isAlive()).isFalse();
        log("  #" + V1 + " 与 #" + V2 + " 各 5 票，白痴弃票 → 平票入第二夜");

        // ── 第二夜：刀白痴，女巫救，预查狼 ──
        section("第二夜 · 狼人刀白痴 #" + IDIOT);
        runWolfKill(roomId, IDIOT);
        runSeerCheck(roomId, W2);
        assertThat(refresh(roomId).getLastSeerCheckResult()).isEqualTo(SeerCheckResult.WOLF);
        log("  预言家查验 #" + W2 + " → 狼人");

        section("第二夜 · 女巫救 #" + IDIOT);
        runWitchSave(roomId);
        room = refresh(roomId);
        assertThat(room.getLastNightDeaths()).isEmpty();
        assertThat(room.isWitchAntidoteRemaining()).isFalse();
        advancePastNightAnnounce(roomId);

        section("第二天 · 投票放逐狼人 #" + W2);
        finishDayVoteExile(roomId, W2, (Integer) null);
        assertThat(refresh(roomId).getPlayer(W2).isAlive()).isFalse();
        log("  狼人 #" + W2 + " 出局");

        // ── 第三夜：刀预言家；白天票猎带村民 ──
        section("第三夜 · 狼人刀预言家 #" + SEER);
        runWolfKill(roomId, SEER);
        runSeerCheck(roomId, V3);
        runWitchSkip(roomId);
        room = refresh(roomId);
        assertThat(room.getLastNightDeaths()).containsExactly(SEER);
        advancePastNightAnnounce(roomId);

        section("第三天 · 投票放逐猎人 #" + HUNTER + "，开枪带走 #" + V1);
        finishDayVoteExile(roomId, HUNTER, V1);
        room = refresh(roomId);
        assertThat(room.getPlayer(HUNTER).isAlive()).isFalse();
        assertThat(room.getPlayer(SEER).isAlive()).isFalse();
        assertThat(room.getPlayer(V1).isAlive()).isFalse();
        log("  预言家、猎人、村民 #" + V1 + " 已出局");

        // ── 第四夜：刀村民；白天票狼 ──
        section("第四夜 · 狼人刀村民 #" + V2);
        runWolfKill(roomId, V2);
        runSeerCheck(roomId, V4);
        runWitchSkip(roomId);
        room = refresh(roomId);
        assertThat(room.getLastNightDeaths()).containsExactly(V2);
        advancePastNightAnnounce(roomId);

        section("第四天 · 投票放逐狼人 #" + W3);
        finishDayVoteExile(roomId, W3, (Integer) null);
        assertThat(refresh(roomId).getPlayer(W3).isAlive()).isFalse();

        // ── 第五夜：刀女巫；白天票最后一狼 ──
        section("第五夜 · 狼人刀女巫 #" + WITCH);
        runWolfKill(roomId, WITCH);
        runSeerCheck(roomId, W4);
        runWitchSkip(roomId);
        room = refresh(roomId);
        assertThat(room.getLastNightDeaths()).containsExactly(WITCH);
        advancePastNightAnnounce(roomId);

        section("第五天 · 投票放逐最后一狼 #" + W4 + "（狼全灭，即时终局）");
        finishDayVoteExile(roomId, W4, (Integer) null);
        room = refresh(roomId);
        assertThat(room.getPhase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(room.getWinner()).isEqualTo(GameWinner.VILLAGERS);
        assertThat(room.getPlayer(W4).isAlive()).isFalse();
        assertThat(room.aliveWolfIds()).isEmpty();

        printBoard(room, "终局");
        line("═══ 剧本结束：VILLAGERS 胜 ═══");
    }

    @Test
    void simulateFourNightWolfWinLastGodExiled() {
        String roomId = "scenario_4night_wolf";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        assignFixedRoles(sm.getRoom(roomId).orElseThrow());

        line("═══ 狼人杀引擎 · 四夜剧本（最后一神猎人被票，狼胜）═══");
        printBoard(refresh(roomId), "开局");

        // ── 第一夜：刀女巫自救，预查白痴，白天归票村民 ──
        section("第一夜 · 狼人刀女巫 #" + WITCH);
        runWolfKill(roomId, WITCH);
        runSeerCheck(roomId, IDIOT);
        assertThat(refresh(roomId).getLastSeerCheckResult()).isEqualTo(SeerCheckResult.GOOD);
        log("  预言家查验 #" + IDIOT + "（白痴）→ 好人");

        section("第一夜 · 女巫 #" + WITCH + " 自救（SAVE）");
        runWitchSave(roomId);
        GameRoomState room = refresh(roomId);
        assertThat(room.getLastNightDeaths()).isEmpty();
        assertThat(room.isWitchAntidoteRemaining()).isFalse();
        advancePastNightAnnounce(roomId);

        section("第一天 · 归票放逐村民 #" + V1);
        finishDayVoteExile(roomId, V1, (Integer) null);
        assertThat(refresh(roomId).getPlayer(V1).isAlive()).isFalse();
        log("  村民 #" + V1 + " 出局");

        // ── 第二夜：再刀女巫，毒药带走预言家 ──
        section("第二夜 · 狼人再刀女巫 #" + WITCH);
        runWolfKill(roomId, WITCH);
        log("  刀口 target=" + refresh(roomId).getPendingWolfKillTarget());

        section("第二夜 · 预言家 #" + SEER + " 查验 #" + V3 + "（随后被毒）");
        runSeerCheck(roomId, V3);

        section("第二夜 · 女巫 #" + WITCH + " 毒杀预言家 #" + SEER + "（解药已用，无法自救）");
        runWitchPoison(roomId, SEER);
        room = refresh(roomId);
        assertThat(room.getLastNightDeaths()).containsExactlyInAnyOrder(WITCH, SEER);
        assertThat(room.getPlayer(WITCH).isAlive()).isFalse();
        assertThat(room.getPlayer(SEER).isAlive()).isFalse();
        advancePastNightAnnounce(roomId);

        section("第二天 · 投票放逐狼人 #" + W1);
        finishDayVoteExile(roomId, W1, (Integer) null);
        assertThat(refresh(roomId).getPlayer(W1).isAlive()).isFalse();

        // ── 第三夜：刀村民；白天归票白痴翻牌 ──
        section("第三夜 · 狼人刀村民 #" + V2);
        runWolfKill(roomId, V2);
        runSeerCheck(roomId, V3);
        runWitchSkip(roomId);
        room = refresh(roomId);
        assertThat(room.getLastNightDeaths()).containsExactly(V2);
        advancePastNightAnnounce(roomId);

        section("第三天 · 归票 #" + IDIOT + "（白痴翻牌）");
        finishDayVoteIdiotReveal(roomId, IDIOT);
        room = refresh(roomId);
        assertThat(room.getPlayer(IDIOT).isAlive()).isTrue();
        assertThat(room.getPlayer(IDIOT).isIdiotRevealed()).isTrue();
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_WOLF);
        assertThat(room.getRound()).isEqualTo(4);
        log("  白痴翻牌，进入第四夜");

        // ── 第四夜：刀白痴；白天归票猎人，最后一神 → 狼胜 ──
        section("第四夜 · 狼人刀白痴 #" + IDIOT);
        runWolfKill(roomId, IDIOT);
        runSeerCheck(roomId, V4);
        runWitchSkip(roomId);
        room = refresh(roomId);
        assertThat(room.getLastNightDeaths()).containsExactly(IDIOT);
        advancePastNightAnnounce(roomId);

        section("第四天 · 归票猎人 #" + HUNTER + "（场上唯一存活神职，R23 狼胜）");
        skipDiscuss(roomId);
        unanimousVote(roomId, HUNTER);
        room = refresh(roomId);
        assertThat(room.getPhase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(room.getWinner()).isEqualTo(GameWinner.WEREWOLVES);
        assertThat(room.getPlayer(HUNTER).isAlive()).isFalse();
        log("  最后一神被票 → 不进猎人开枪 → 狼人获胜");

        printBoard(room, "终局");
        line("═══ 剧本结束：WEREWOLVES 胜 ═══");
    }

    private void assignFixedRoles(GameRoomState room) {
        Map<Integer, Role> layout = Map.ofEntries(
                Map.entry(W1, Role.WEREWOLF),
                Map.entry(W2, Role.WEREWOLF),
                Map.entry(W3, Role.WEREWOLF),
                Map.entry(W4, Role.WEREWOLF),
                Map.entry(V1, Role.VILLAGER),
                Map.entry(V2, Role.VILLAGER),
                Map.entry(V3, Role.VILLAGER),
                Map.entry(V4, Role.VILLAGER),
                Map.entry(IDIOT, Role.IDIOT),
                Map.entry(SEER, Role.SEER),
                Map.entry(WITCH, Role.WITCH),
                Map.entry(HUNTER, Role.HUNTER)
        );
        layout.forEach((seat, role) -> room.getPlayer(seat).setRole(role));
    }

    private void runWolfKill(String roomId, int target) {
        GameRoomState room = refresh(roomId);
        for (int w : room.aliveWolfIds()) {
            sm.handleAction(roomId, cmd(w, GameActionType.KILL, target, GamePhase.NIGHT_WOLF));
        }
    }

    /** R17/R17a — 本夜先狼队商议，再全员刀指定存活狼人（自刀骗药）. */
    private void runWolfSelfKill(String roomId, int wolfTarget) {
        GameRoomState room = refresh(roomId);
        int speaker = room.aliveWolfIds().getFirst();
        sm.handleAction(roomId, cmd(speaker, GameActionType.WOLF_CHAT, null, GamePhase.NIGHT_WOLF));
        for (int w : room.aliveWolfIds()) {
            sm.handleAction(roomId, cmd(w, GameActionType.KILL, wolfTarget, GamePhase.NIGHT_WOLF));
        }
    }

    private void runSeerCheck(String roomId, int target) {
        GameRoomState room = refresh(roomId);
        if (room.getPhase() != GamePhase.NIGHT_SEER) {
            return;
        }
        int seer = room.seerSeat();
        if (seer > 0 && room.getPlayer(seer).isAlive()) {
            sm.handleAction(roomId, cmd(seer, GameActionType.CHECK, target, GamePhase.NIGHT_SEER));
        }
    }

    private void runWitchSave(String roomId) {
        GameRoomState room = refresh(roomId);
        if (room.getPhase() == GamePhase.NIGHT_WITCH) {
            sm.handleAction(roomId, cmd(WITCH, GameActionType.SAVE, null, GamePhase.NIGHT_WITCH));
        }
    }

    private void runWitchPoison(String roomId, int target) {
        GameRoomState room = refresh(roomId);
        if (room.getPhase() == GamePhase.NIGHT_WITCH) {
            sm.handleAction(roomId, cmd(WITCH, GameActionType.POISON, target, GamePhase.NIGHT_WITCH));
        }
    }

    private void runWitchSkip(String roomId) {
        GameRoomState room = refresh(roomId);
        if (room.getPhase() == GamePhase.NIGHT_WITCH) {
            sm.handleAction(roomId, cmd(WITCH, GameActionType.SKIP, null, GamePhase.NIGHT_WITCH));
        }
    }

    private void advancePastNightAnnounce(String roomId) {
        GameRoomState room = refresh(roomId);
        if (room.getPhase() != GamePhase.NIGHT_DEATH_ANNOUNCE) {
            return;
        }
        sm.advanceDayAnnounce(roomId);
        completeLastWords(roomId);
        resolveHunterShootIfPending(roomId, null);
    }

    private void finishDayVoteExile(String roomId, int exileTarget, Integer hunterShootTarget) {
        skipDiscuss(roomId);
        unanimousVote(roomId, exileTarget);
        GameRoomState room = refresh(roomId);
        if (room.getPhase() == GamePhase.GAME_OVER) {
            return;
        }
        assertThat(room.getPhase()).isEqualTo(GamePhase.EXILE_DEATH_ANNOUNCE);

        sm.advanceDayAnnounce(roomId);
        completeLastWords(roomId);
        resolveHunterShootIfPending(roomId, hunterShootTarget);

        room = refresh(roomId);
        if (room.getPhase() == GamePhase.GAME_OVER) {
            return;
        }
        assertThat(room.getPhase()).isIn(GamePhase.CHECK_WIN, GamePhase.NIGHT_WOLF, GamePhase.DAY_DISCUSS);
        if (room.getPhase() == GamePhase.CHECK_WIN) {
            refresh(roomId);
        }
    }

    private void finishDayVoteIdiotReveal(String roomId, int idiotSeat) {
        skipDiscuss(roomId);
        unanimousVote(roomId, idiotSeat);
        GameRoomState room = refresh(roomId);
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_WOLF);
    }

    /** R14 平票：voterTargets 中 null 表示弃票. */
    private void finishDayVoteTie(String roomId, Map<Integer, Integer> voterTargets) {
        skipDiscuss(roomId);
        castVotes(roomId, voterTargets);
        GameRoomState room = refresh(roomId);
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_WOLF);
    }

    private void castVotes(String roomId, Map<Integer, Integer> voterTargets) {
        GameRoomState room = refresh(roomId);
        assertThat(room.getPhase()).isEqualTo(GamePhase.DAY_VOTE);
        for (int voter : room.alivePlayerIds()) {
            PlayerState p = room.getPlayer(voter);
            if (!p.isCanVote()) {
                continue;
            }
            if (!voterTargets.containsKey(voter)) {
                sm.handleAction(roomId, cmd(voter, GameActionType.SKIP_VOTE, null, GamePhase.DAY_VOTE));
                continue;
            }
            Integer target = voterTargets.get(voter);
            if (target == null) {
                sm.handleAction(roomId, cmd(voter, GameActionType.SKIP_VOTE, null, GamePhase.DAY_VOTE));
            } else {
                sm.handleAction(roomId, cmd(voter, GameActionType.VOTE, target, GamePhase.DAY_VOTE));
            }
        }
    }

    private void resolveHunterShootIfPending(String roomId, Integer shootTarget) {
        GameRoomState room = refresh(roomId);
        if (room.getPhase() != GamePhase.HUNTER_SHOOT) {
            return;
        }
        int hunter = room.getHunterShooterSeat();
        if (shootTarget != null) {
            sm.handleAction(roomId, cmd(hunter, GameActionType.SHOOT, shootTarget, GamePhase.HUNTER_SHOOT));
        } else {
            sm.handleAction(roomId, cmd(hunter, GameActionType.SKIP, null, GamePhase.HUNTER_SHOOT));
        }
    }

    private void completeLastWords(String roomId) {
        GameRoomState room = refresh(roomId);
        while (room.getPhase() == GamePhase.LAST_WORDS) {
            int speaker = room.getLastWordsOrder().get(room.getLastWordsIndex());
            sm.handleAction(roomId,
                    new GameActionCommand(speaker, GameActionType.SKIP_SPEAK, null, GamePhase.LAST_WORDS));
            room = refresh(roomId);
        }
    }

    private void skipDiscuss(String roomId) {
        GameRoomState room = refresh(roomId);
        while (room.getPhase() == GamePhase.DAY_DISCUSS) {
            int speaker = room.getDiscussOrder().get(room.getDiscussIndex());
            sm.handleAction(roomId, cmd(speaker, GameActionType.SKIP_SPEAK, null, GamePhase.DAY_DISCUSS));
            room = refresh(roomId);
        }
    }

    private void unanimousVote(String roomId, int target) {
        GameRoomState room = refresh(roomId);
        assertThat(room.getPhase()).isEqualTo(GamePhase.DAY_VOTE);
        for (int id : room.alivePlayerIds()) {
            PlayerState p = room.getPlayer(id);
            if (p.isCanVote()) {
                sm.handleAction(roomId, cmd(id, GameActionType.VOTE, target, GamePhase.DAY_VOTE));
            }
        }
    }

    private GameRoomState refresh(String roomId) {
        return sm.getRoom(roomId).orElseThrow();
    }

    private static GameActionCommand cmd(int player, GameActionType action, Integer target, GamePhase phase) {
        return new GameActionCommand(player, action, target, phase);
    }

    private static void printBoard(GameRoomState room, String title) {
        section(title);
        log("  阶段=" + room.getPhase() + "  轮次=" + room.getRound()
                + (room.getWinner() != null ? "  胜负=" + room.getWinner() : ""));
        for (int seat = 1; seat <= GameRoomState.SEAT_COUNT; seat++) {
            PlayerState p = room.getPlayer(seat);
            String status = p.isAlive() ? "存活" : "死亡";
            String extra = "";
            if (p.getRole() == Role.IDIOT && p.isIdiotRevealed()) {
                extra = " [已翻牌]";
            }
            log(String.format("  #%2d %-10s %s%s", seat, p.getRole(), status, extra));
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("▶ " + title);
    }

    private static void line(String title) {
        System.out.println();
        System.out.println(title);
    }

    private static void log(String msg) {
        System.out.println(msg);
    }
}
