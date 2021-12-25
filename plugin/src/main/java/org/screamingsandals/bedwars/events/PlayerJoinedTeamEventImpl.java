package org.screamingsandals.bedwars.events;

import lombok.Data;
import org.jetbrains.annotations.Nullable;
import org.screamingsandals.bedwars.api.events.PlayerJoinedTeamEvent;
import org.screamingsandals.bedwars.game.GameImpl;
import org.screamingsandals.bedwars.game.TeamImpl;
import org.screamingsandals.bedwars.player.BedWarsPlayer;
import org.screamingsandals.lib.event.SEvent;

@Data
public class PlayerJoinedTeamEventImpl implements PlayerJoinedTeamEvent<GameImpl, BedWarsPlayer, TeamImpl>, SEvent {
    private final GameImpl game;
    private final BedWarsPlayer player;
    private final TeamImpl team;
    @Nullable
    private final TeamImpl previousTeam;
}