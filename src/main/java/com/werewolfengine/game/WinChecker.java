package com.werewolfengine.game;

import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.GameWinner;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;

import java.util.EnumSet;

/**
 * PRD §4.4.4 — 屠边：狼全灭好人赢；四神全灭或四民全灭狼赢（愚者翻牌仍算神坑存活）。
 */
public final class WinChecker {

    private static final EnumSet<Role> GOD_ROLES = EnumSet.of(
            Role.SEER, Role.WITCH, Role.HUNTER, Role.IDIOT);

    private WinChecker() {
    }

    /** @return null if game continues */
    public static GameWinner evaluate(GameRoomState room) {
        int wolvesAlive = 0;
        for (PlayerState p : room.getPlayers().values()) {
            if (p.isAlive() && p.getRole() == Role.WEREWOLF) {
                wolvesAlive++;
            }
        }
        if (wolvesAlive == 0) {
            return GameWinner.VILLAGERS;
        }

        int godsAlive = 0;
        int villagersAlive = 0;
        for (PlayerState p : room.getPlayers().values()) {
            if (!p.isAlive()) {
                continue;
            }
            Role r = p.getRole();
            if (r == Role.VILLAGER) {
                villagersAlive++;
            } else if (r != null && GOD_ROLES.contains(r)) {
                godsAlive++;
            }
        }

        if (godsAlive == 0) {
            return GameWinner.WEREWOLVES;
        }
        if (villagersAlive == 0) {
            return GameWinner.WEREWOLVES;
        }
        return null;
    }
}
