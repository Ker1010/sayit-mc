package com.sayitmc.game;

/** What the client says about the player's answer. */
public enum Verdict {
	/** Said (or picked) the right word. */
	CORRECT,
	/** Picked a wrong answer in the multiple-choice quiz; treated like running out of time. */
	WRONG,
	/** Cannot be judged (e.g. no translation for this item), so nothing happens to the item. */
	UNJUDGEABLE;

	public static Verdict byId(int id) {
		Verdict[] values = values();
		return id >= 0 && id < values.length ? values[id] : UNJUDGEABLE;
	}
}
