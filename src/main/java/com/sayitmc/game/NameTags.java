package com.sayitmc.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Shows what each player is being asked above their head, as a name tag suffix ({@code Steve [小麦 7s]}),
 * so everyone nearby can follow along. Uses one scoreboard team per player; players already in another
 * team (from a datapack or another mod) are left alone.
 */
final class NameTags {
	private static final String TEAM_PREFIX = "sayitmc_";
	private static final int FLASH_TICKS = 40;
	private static final int GOLD = 0xF5C542;
	private static final int GREEN = 0x5BD46C;
	private static final int RED = 0xE5534B;
	private static final int GRAY = 0xAAAAAA;

	private record Flash(Component text, long until) {
	}

	private final Map<UUID, Flash> flashes = new HashMap<>();
	/** What each player's suffix shows now, to only send changes. */
	private final Map<UUID, String> shown = new HashMap<>();

	void flash(Entity player, Outcome outcome, long now) {
		Component text = switch (outcome) {
			case CORRECT -> Component.literal(" ✔").withColor(GREEN);
			case CLEARED, TIMED_OUT -> Component.literal(" ✘").withColor(RED);
			default -> null;
		};

		if (text != null) {
			flashes.put(player.getUUID(), new Flash(text, now + FLASH_TICKS));
		}
	}

	void update(MinecraftServer server, Entity player, PlayerGame game, long now, ServerConfig config) {
		int secondsLeft = game.secondsLeft(now, config);
		Flash flash = flashes.get(player.getUUID());
		Component suffix = null;

		if (secondsLeft >= 0) {
			suffix = Component.literal(" [").withColor(GRAY)
					.append(Component.literal(game.runningWord()).withColor(GOLD))
					.append(Component.literal(" " + secondsLeft + "s]").withColor(GRAY));
		} else if (flash != null && flash.until() > now) {
			suffix = flash.text();
		} else {
			flashes.remove(player.getUUID());
		}

		set(server, player, suffix);
	}

	/** Puts {@code suffix} after the player's name, or removes it when null. */
	void set(MinecraftServer server, Entity player, Component suffix) {
		String text = suffix == null ? "" : suffix.getString();

		if (text.equals(shown.getOrDefault(player.getUUID(), ""))) {
			return;
		}

		ServerScoreboard board = server.getScoreboard();
		String entry = player.getScoreboardName();
		String teamName = TEAM_PREFIX + player.getUUID().toString().substring(0, 8);
		PlayerTeam team = board.getPlayerTeam(teamName);
		PlayerTeam current = board.getPlayersTeam(entry);

		if (current != null && current != team) {
			return;
		}

		if (team == null) {
			if (suffix == null) {
				return;
			}

			team = board.addPlayerTeam(teamName);
		}

		if (current == null) {
			board.addPlayerToTeam(entry, team);
		}

		team.setPlayerSuffix(suffix == null ? Component.empty() : suffix);
		shown.put(player.getUUID(), text);
	}

	void forget(MinecraftServer server, Entity player) {
		flashes.remove(player.getUUID());
		shown.remove(player.getUUID());

		if (server != null) {
			PlayerTeam team = server.getScoreboard().getPlayerTeam(TEAM_PREFIX + player.getUUID().toString().substring(0, 8));

			if (team != null) {
				server.getScoreboard().removePlayerTeam(team);
			}
		}
	}

	/** Removes every team this mod made, e.g. ones left in the world save by a crash. */
	void removeAll(MinecraftServer server) {
		flashes.clear();
		shown.clear();
		ServerScoreboard board = server.getScoreboard();

		for (PlayerTeam team : new ArrayList<>(board.getPlayerTeams())) {
			if (team.getName().startsWith(TEAM_PREFIX)) {
				board.removePlayerTeam(team);
			}
		}
	}
}
