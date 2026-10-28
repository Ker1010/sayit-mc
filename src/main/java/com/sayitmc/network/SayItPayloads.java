package com.sayitmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import com.sayitmc.SayItMod;

/**
 * Packets between the server, which runs the game (language, timers, clearing items), and each player's client,
 * which listens to that player's microphone and judges what they said.
 */
public final class SayItPayloads {
	private SayItPayloads() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Hello.TYPE, Hello.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Listening.TYPE, Listening.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Answer.TYPE, Answer.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Forget.TYPE, Forget.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Rules.TYPE, Rules.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Challenge.TYPE, Challenge.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Result.TYPE, Result.CODEC);
	}

	/** Longest item name a client may send for the name tag. */
	private static final int MAX_WORD_LENGTH = 64;

	/** Bumped when packets change, so mismatched mod versions ignore each other instead of failing to decode. */
	private static final String PROTOCOL = "v8/";

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
		return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SayItMod.MOD_ID, PROTOCOL + path));
	}

	/** Client -> server: this player has the mod, and whether they are playing. */
	public record Hello(boolean enabled) implements CustomPacketPayload {
		public static final Type<Hello> TYPE = payloadType("hello");
		public static final StreamCodec<FriendlyByteBuf, Hello> CODEC = CustomPacketPayload.codec(Hello::write, Hello::new);

		private Hello(FriendlyByteBuf buf) {
			this(buf.readBoolean());
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeBoolean(enabled);
		}

		@Override
		public Type<Hello> type() {
			return TYPE;
		}
	}

	/** Client -> server: the microphone is open, start the countdown. */
	public record Listening(int id, String word) implements CustomPacketPayload {
		public static final Type<Listening> TYPE = payloadType("listening");
		public static final StreamCodec<FriendlyByteBuf, Listening> CODEC = CustomPacketPayload.codec(Listening::write, Listening::new);

		private Listening(FriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readUtf(MAX_WORD_LENGTH));
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(id);
			buf.writeUtf(word, MAX_WORD_LENGTH);
		}

		@Override
		public Type<Listening> type() {
			return TYPE;
		}
	}

	/** Client -> server: the player's answer, see {@link com.sayitmc.game.Verdict}. */
	public record Answer(int id, int verdict) implements CustomPacketPayload {
		public static final Type<Answer> TYPE = payloadType("answer");
		public static final StreamCodec<FriendlyByteBuf, Answer> CODEC = CustomPacketPayload.codec(Answer::write, Answer::new);

		private Answer(FriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readVarInt());
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(id);
			buf.writeVarInt(verdict);
		}

		@Override
		public Type<Answer> type() {
			return TYPE;
		}
	}

	/** Client -> server: forget every item learned in the current language. */
	public record Forget() implements CustomPacketPayload {
		public static final Type<Forget> TYPE = payloadType("forget");
		public static final StreamCodec<FriendlyByteBuf, Forget> CODEC = StreamCodec.unit(new Forget());

		@Override
		public Type<Forget> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: the language everyone learns and the time per word, chosen by the server;
	 * {@code mandatory} means players cannot turn the game off; the last two set how strict the speech check is
	 * (in percent, so every player is judged the same).
	 */
	public record Rules(String language, int seconds, boolean mandatory, int matchPercent, int confidencePercent)
			implements CustomPacketPayload {
		public static final Type<Rules> TYPE = payloadType("rules");
		public static final StreamCodec<FriendlyByteBuf, Rules> CODEC = CustomPacketPayload.codec(Rules::write, Rules::new);

		private Rules(FriendlyByteBuf buf) {
			this(buf.readUtf(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(language);
			buf.writeVarInt(seconds);
			buf.writeBoolean(mandatory);
			buf.writeVarInt(matchPercent);
			buf.writeVarInt(confidencePercent);
		}

		@Override
		public Type<Rules> type() {
			return TYPE;
		}
	}

	/** Server -> client: say this item's name, in the {@link Rules#language() server's language}, within {@code seconds}. */
	public record Challenge(int id, String itemId, int seconds) implements CustomPacketPayload {
		public static final Type<Challenge> TYPE = payloadType("challenge");
		public static final StreamCodec<FriendlyByteBuf, Challenge> CODEC = CustomPacketPayload.codec(Challenge::write, Challenge::new);

		private Challenge(FriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readUtf(), buf.readVarInt());
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(id);
			buf.writeUtf(itemId);
			buf.writeVarInt(seconds);
		}

		@Override
		public Type<Challenge> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: how the challenge ended, see {@link com.sayitmc.game.Outcome}. {@code reveal} lets the
	 * client show the answer: the player has missed this item often enough.
	 */
	public record Result(int id, int outcome, boolean reveal) implements CustomPacketPayload {
		public static final Type<Result> TYPE = payloadType("result");
		public static final StreamCodec<FriendlyByteBuf, Result> CODEC = CustomPacketPayload.codec(Result::write, Result::new);

		private Result(FriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(id);
			buf.writeVarInt(outcome);
			buf.writeBoolean(reveal);
		}

		@Override
		public Type<Result> type() {
			return TYPE;
		}
	}
}
