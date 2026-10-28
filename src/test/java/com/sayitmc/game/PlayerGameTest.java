package com.sayitmc.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerGameTest {
	private static final int TIMEOUT_TICKS = 10 * 20 + PlayerGame.GRACE_TICKS;

	private final List<String> log = new ArrayList<>();
	private final Set<String> learned = new HashSet<>();
	private final ServerConfig config = new ServerConfig();
	private final PlayerGame game = new PlayerGame();
	private final PlayerGame.Actions actions = new PlayerGame.Actions() {
		@Override
		public void startChallenge(int id, String itemId, int seconds) {
			log.add("start " + id + " " + itemId + " " + seconds);
		}

		@Override
		public void endChallenge(int id, Outcome outcome) {
			log.add("end " + id + " " + outcome);
		}

		@Override
		public void clearItem(String itemId) {
			log.add("clear " + itemId);
		}

		@Override
		public void clearStashedItem(String itemId) {
			log.add("clear stashed " + itemId);
		}
	};
	private final Set<String> dropped = new HashSet<>();
	private long now;

	@BeforeEach
	void join() {
		game.hello(true, actions);
		tick("minecraft:dirt");
	}

	@Test
	void itemsCarriedWhenJoiningAreNotAsked() {
		assertEquals(List.of(), log);
	}

	@Test
	void newItemStartsChallengeAndTimeoutClearsIt() {
		tick("minecraft:dirt", "minecraft:wheat");
		assertEquals(List.of("start 1 minecraft:wheat 10"), log);

		game.listening(1, now, "Weizen");
		advance(TIMEOUT_TICKS, "minecraft:dirt", "minecraft:wheat");
		assertEquals(List.of("start 1 minecraft:wheat 10"), log, "still within the grace period");

		tick("minecraft:dirt", "minecraft:wheat");
		assertEquals(List.of("start 1 minecraft:wheat 10", "clear minecraft:wheat", "end 1 CLEARED"), log);
	}

	@Test
	void clockWaitsForTheClientToListen() {
		tick("minecraft:wheat");
		advance(TIMEOUT_TICKS * 2, "minecraft:wheat");
		assertEquals(List.of("start 1 minecraft:wheat 10"), log);
	}

	@Test
	void droppingTheItemResetsItsTimer() {
		tick("minecraft:wheat");
		game.listening(1, now, "Weizen");
		advance(100, "minecraft:wheat");
		drop("minecraft:wheat");
		assertEquals("end 1 DROPPED", log.get(1));

		// Picked up again: a fresh challenge with a full timer.
		tick("minecraft:wheat");
		assertEquals("start 2 minecraft:wheat 10", log.get(2));
		game.listening(2, now, "Weizen");
		advance(TIMEOUT_TICKS, "minecraft:wheat");
		assertEquals(3, log.size(), "the new timer has not run out yet");
	}

	@Test
	void correctAnswerIsLearnedAndNeverAskedAgain() {
		tick("minecraft:wheat");
		game.listening(1, now, "Weizen");
		game.answer(1, Verdict.CORRECT, true, learned::add, now, config, actions);
		assertEquals(List.of("start 1 minecraft:wheat 10", "end 1 CORRECT"), log);
		assertTrue(learned.contains("minecraft:wheat"));

		tick();
		tick("minecraft:wheat");
		advance(TIMEOUT_TICKS * 2, "minecraft:wheat");
		assertEquals(2, log.size());
	}

	@Test
	void puttingTheItemAwayDoesNotSaveIt() {
		tick("minecraft:wheat");
		game.listening(1, now, "Weizen");
		tick();
		assertEquals(1, log.size(), "stashing (chest, bundle) does not reset the timer");

		advance(TIMEOUT_TICKS);
		assertEquals("end 1 CLEARED", log.get(1));

		tick("minecraft:wheat");
		assertEquals("clear stashed minecraft:wheat", log.get(2), "cleared as soon as it comes back");
	}

	@Test
	void stashedItemThatComesBackInTimeKeepsItsTimer() {
		tick("minecraft:wheat");
		game.listening(1, now, "Weizen");
		advance(50);
		advance(50, "minecraft:wheat");
		assertEquals(1, log.size());
		advance(TIMEOUT_TICKS - 99, "minecraft:wheat");
		assertEquals(List.of("start 1 minecraft:wheat 10", "clear minecraft:wheat", "end 1 CLEARED"), log);
	}

	@Test
	void countdownForTheNameTag() {
		tick("minecraft:wheat");
		assertEquals(-1, game.secondsLeft(now, config), "not counting until the client listens");
		game.listening(1, now, "Weizen");
		assertEquals(10, game.secondsLeft(now, config));
		advance(21, "minecraft:wheat");
		assertEquals(9, game.secondsLeft(now, config));
		assertEquals("Weizen", game.runningWord());
	}

	@Test
	void wordFromTheClientIsCleanedBeforeEveryoneSeesIt() {
		assertEquals("小麦", PlayerGame.cleanWord("§c小麦"));
		assertEquals("ab", PlayerGame.cleanWord("a\nb"));
		assertEquals(32, PlayerGame.cleanWord("x".repeat(100)).length());
	}

	@Test
	void wrongQuizAnswerClearsRightAway() {
		tick("minecraft:wheat");
		game.listening(1, now, "Weizen");
		game.answer(1, Verdict.WRONG, true, learned::add, now, config, actions);
		assertEquals(List.of("start 1 minecraft:wheat 10", "clear minecraft:wheat", "end 1 CLEARED"), log);
		assertTrue(learned.isEmpty());
	}

	@Test
	void clientThatCannotJudgeCancelsWithoutClearing() {
		tick("minecraft:wheat");
		game.answer(1, Verdict.UNJUDGEABLE, true, learned::add, now, config, actions);
		advance(TIMEOUT_TICKS * 2, "minecraft:wheat");
		assertEquals(List.of("start 1 minecraft:wheat 10", "end 1 CANCELLED"), log);
	}

	@Test
	void itemsWaitTheirTurnOneAtATime() {
		tick("minecraft:wheat", "minecraft:bread");
		assertEquals(1, log.size());
		String first = log.getFirst().split(" ")[2];
		String second = first.equals("minecraft:wheat") ? "minecraft:bread" : "minecraft:wheat";

		game.answer(1, Verdict.CORRECT, true, learned::add, now, config, actions);
		tick("minecraft:wheat", "minecraft:bread");
		assertEquals("start 2 " + second + " 10", log.get(2));
	}

	@Test
	void serverLanguageChangeCancelsAndStartsOver() {
		tick("minecraft:wheat");
		game.reset(actions);
		assertEquals("end 1 CANCELLED", log.get(1));

		tick("minecraft:wheat");
		assertEquals(2, log.size(), "items already held become the new baseline");
	}

	@Test
	void turningTheGameOffCancelsAndStopsAsking() {
		tick("minecraft:wheat");
		game.hello(false, actions);
		tick("minecraft:wheat", "minecraft:bread");
		assertEquals(List.of("start 1 minecraft:wheat 10", "end 1 CANCELLED"), log);
	}

	@Test
	void clearingCanBeTurnedOff() {
		config.clearItemsOnTimeout = false;
		tick("minecraft:wheat");
		game.listening(1, now, "Weizen");
		advance(TIMEOUT_TICKS + 1, "minecraft:wheat");
		assertEquals(List.of("start 1 minecraft:wheat 10", "end 1 TIMED_OUT"), log);
	}

	private void tick(String... held) {
		now++;
		game.tick(now, new HashSet<>(List.of(held)), learned::contains, dropped::contains, config, actions);
		dropped.clear();
	}

	/** The player throws the item on the ground. */
	private void drop(String itemId, String... stillHeld) {
		dropped.add(itemId);
		tick(stillHeld);
	}

	private void advance(int ticks, String... held) {
		for (int i = 0; i < ticks; i++) {
			tick(held);
		}
	}
}
