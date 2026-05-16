package com.werewolfengine.game;

import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** PRD §4.3.3 ROLE_ASSIGN — 4狼4民1愚者1预1女1猎（角色枚举 {@link com.werewolfengine.game.model.Role#IDIOT}）. */
final class RoleAssigner {

    private RoleAssigner() {
    }

    static void assign(GameRoomState room) {
        List<Role> roles = new ArrayList<>(List.of(
                Role.WEREWOLF, Role.WEREWOLF, Role.WEREWOLF, Role.WEREWOLF,
                Role.VILLAGER, Role.VILLAGER, Role.VILLAGER, Role.VILLAGER,
                Role.IDIOT,
                Role.SEER,
                Role.WITCH,
                Role.HUNTER
        ));
        Collections.shuffle(roles);
        int i = 0;
        for (PlayerState player : room.getPlayers().values()) {
            player.setRole(roles.get(i++));
        }
    }
}
