package com.sayitmc.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import java.util.Locale;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.sayitmc.network.SayItPayloads;

/**
 * {@code /sayit} - status, {@code language <code>}, {@code on|off}, {@code test} (practice the held item),
 * {@code skip}, {@code time <seconds>}, {@code forget}.
 */
final class SayItCommands {
	private SayItCommands() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(literal("sayit")
				.executes(ctx -> status(ctx.getSource()))
				.then(literal("language")
						.then(argument("code", StringArgumentType.word())
								.suggests((ctx, builder) -> {
									String typed = builder.getRemaining().toLowerCase(Locale.ROOT);

									for (String code : TargetLanguage.COMMON_CODES) {
										if (code.startsWith(typed)) {
											builder.suggest(code);
										}
									}

									return builder.buildFuture();
								})
								.executes(SayItCommands::setLanguage)))
				.then(literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
				.then(literal("off").executes(ctx -> setEnabled(ctx.getSource(), false)))
				.then(literal("test").executes(ctx -> practiceHeldItem(ctx.getSource())))
				.then(literal("skip").executes(ctx -> skip(ctx.getSource())))
				.then(literal("time")
						.then(argument("seconds", IntegerArgumentType.integer(3, 120))
								.executes(SayItCommands::setTime)))
				.then(literal("forget").executes(ctx -> forget(ctx.getSource()))));
	}

	private static int status(FabricClientCommandSource source) {
		ClientConfig config = ClientConfig.get();
		String mode = SayItClient.isServerGame()
				? "playing this server's game (miss the word and the item is cleared, drop it to reset its timer)"
				: "practice mode (this server does not have the mod, nothing is cleared)";
		source.sendFeedback(Component.literal("Say It! is " + (config.enabled ? "on" : "off") + ", learning "
				+ TargetLanguage.displayName(config.language) + " (" + config.language + "), " + mode + "."));
		source.sendFeedback(Component.literal("Change language with /sayit language <code>, e.g. ja_jp, zh_cn, ko_kr, de_de, fr_fr, es_es."));
		return Command.SINGLE_SUCCESS;
	}

	private static int setLanguage(CommandContext<FabricClientCommandSource> ctx) {
		String code = StringArgumentType.getString(ctx, "code").toLowerCase(Locale.ROOT);

		if (!TargetLanguage.exists(code)) {
			ctx.getSource().sendError(Component.literal("Minecraft has no language " + code + ". Try ja_jp, zh_cn, de_de, fr_fr..."));
			return 0;
		}

		ClientConfig.get().language = code;
		ClientConfig.save();
		TargetLanguage.invalidate();
		SayItClient.challenges().clear();
		SayItClient.speech().unload();
		SayItClient.speech().prepare(code);
		SayItClient.sendHello();
		ctx.getSource().sendFeedback(Component.literal("Now learning " + TargetLanguage.displayName(code)
				+ ". The speech model downloads in the background the first time."));
		return Command.SINGLE_SUCCESS;
	}

	private static int setEnabled(FabricClientCommandSource source, boolean enabled) {
		ClientConfig.get().enabled = enabled;
		ClientConfig.save();

		if (!enabled) {
			SayItClient.challenges().clear();
		}

		SayItClient.sendHello();
		source.sendFeedback(Component.literal("Say It! is now " + (enabled ? "on" : "off") + "."));
		return Command.SINGLE_SUCCESS;
	}

	private static int practiceHeldItem(FabricClientCommandSource source) {
		Player player = source.getPlayer();
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);

		if (held.isEmpty()) {
			source.sendError(Component.literal("Hold an item to practice it."));
			return 0;
		}

		if (SayItClient.challenges().current() != null) {
			source.sendError(Component.literal("Finish the current word first."));
			return 0;
		}

		SayItClient.challenges().offerPractice(held, true);
		return Command.SINGLE_SUCCESS;
	}

	private static int skip(FabricClientCommandSource source) {
		if (!SayItClient.challenges().skipPractice()) {
			source.sendError(Component.literal("Server words cannot be skipped. Drop the item to reset its timer."));
			return 0;
		}

		return Command.SINGLE_SUCCESS;
	}

	private static int setTime(CommandContext<FabricClientCommandSource> ctx) {
		ClientConfig.get().secondsToAnswer = IntegerArgumentType.getInteger(ctx, "seconds");
		ClientConfig.save();
		String note = SayItClient.isServerGame() ? " in practice mode (this server sets its own time)." : " per word.";
		ctx.getSource().sendFeedback(Component.literal("You now get " + ClientConfig.get().secondsToAnswer + " seconds" + note));
		return Command.SINGLE_SUCCESS;
	}

	private static int forget(FabricClientCommandSource source) {
		String language = ClientConfig.get().language;
		LocalLearned.clear(language);
		SayItClient.challenges().clear();

		if (SayItClient.isServerGame()) {
			ClientPlayNetworking.send(new SayItPayloads.Forget());
		}

		source.sendFeedback(Component.literal("Forgot every learned " + TargetLanguage.displayName(language) + " word."));
		return Command.SINGLE_SUCCESS;
	}
}
