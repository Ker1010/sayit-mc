package com.sayitmc.game;

/** How a challenge ended, as decided by the server. */
public enum Outcome {
	/** Said correctly; the item is learned. */
	CORRECT,
	/** Time ran out; the item was taken away. */
	CLEARED,
	/** The item left the inventory, so its timer was reset. */
	DROPPED,
	/** Ended without a verdict, e.g. the player has no microphone or switched language. */
	CANCELLED,
	/** Time ran out, but the server is set to keep items. */
	TIMED_OUT;

	public static Outcome byId(int id) {
		Outcome[] values = values();
		return id >= 0 && id < values.length ? values[id] : CANCELLED;
	}
}
