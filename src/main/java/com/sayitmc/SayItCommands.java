package com.sayitmc;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * {@code /sayit} - status, {@code language <code>}, {@code time <seconds>}, {@code on|off},
 * {@code test} (quiz the held item), {@code skip}, {@code forget}.
 */
final class SayItCommands {
	private SayItCommands() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(literal("sayit")
				.executes(ctx -> status(ctx.getSource()))
				.then(literal("language")
						.then(argument("code", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TargetLanguage.codes(), builder))
								.executes(SayItCommands::setLanguage)))
				.then(literal("time")
						.then(argument("seconds", IntegerArgumentType.integer(3, 120))
								.executes(SayItCommands::setTime)))
				.then(literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
				.then(literal("off").executes(ctx -> setEnabled(ctx.getSource(), false)))
				.then(literal("test").executes(ctx -> testHeldItem(ctx.getSource())))
				.then(literal("skip").executes(ctx -> {
					SayItClient.challenges().skip();
					return Command.SINGLE_SUCCESS;
				}))
				.then(literal("forget").executes(ctx -> forget(ctx.getSource()))));
	}

	private static int status(FabricClientCommandSource source) {
		SayItConfig config = SayItConfig.get();
		source.sendFeedback(Component.literal("Say It! is " + (config.enabled ? "on" : "off")
				+ " - learning " + TargetLanguage.displayName(config.language) + " (" + config.language + "), "
				+ LearnedItems.count(config.language) + " items learned, " + config.secondsToAnswer + "s per word."));
		source.sendFeedback(Component.literal("Change language with /sayit language <code>, e.g. ja_jp, zh_cn, ko_kr, de_de, fr_fr, es_es."));
		return Command.SINGLE_SUCCESS;
	}

	private static int setLanguage(CommandContext<FabricClientCommandSource> ctx) {
		String code = StringArgumentType.getString(ctx, "code");

		if (!TargetLanguage.exists(code)) {
			ctx.getSource().sendError(Component.literal("Unknown language code: " + code));
			return 0;
		}

		SayItConfig.get().language = code;
		SayItConfig.save();
		TargetLanguage.invalidate();
		SayItClient.challenges().clear();
		SayItClient.speech().unload();
		SayItClient.speech().prepare(code);
		ctx.getSource().sendFeedback(Component.literal("Now learning " + TargetLanguage.displayName(code)
				+ ". The speech model downloads in the background the first time."));
		return Command.SINGLE_SUCCESS;
	}

	private static int setTime(CommandContext<FabricClientCommandSource> ctx) {
		SayItConfig.get().secondsToAnswer = IntegerArgumentType.getInteger(ctx, "seconds");
		SayItConfig.save();
		ctx.getSource().sendFeedback(Component.literal("You now get " + SayItConfig.get().secondsToAnswer + " seconds per word."));
		return Command.SINGLE_SUCCESS;
	}

	private static int setEnabled(FabricClientCommandSource source, boolean enabled) {
		SayItConfig.get().enabled = enabled;
		SayItConfig.save();

		if (!enabled) {
			SayItClient.challenges().clear();
		}

		source.sendFeedback(Component.literal("Say It! is now " + (enabled ? "on" : "off") + "."));
		return Command.SINGLE_SUCCESS;
	}

	private static int testHeldItem(FabricClientCommandSource source) {
		ItemStack held = source.getPlayer().getMainHandItem();

		if (held.isEmpty()) {
			source.sendError(Component.literal("Hold an item to practice it."));
			return 0;
		}

		SayItClient.challenges().offer(held, true);
		return Command.SINGLE_SUCCESS;
	}

	private static int forget(FabricClientCommandSource source) {
		String language = SayItConfig.get().language;
		LearnedItems.clear(language);
		SayItClient.challenges().clear();
		source.sendFeedback(Component.literal("Forgot every learned " + TargetLanguage.displayName(language) + " word."));
		return Command.SINGLE_SUCCESS;
	}
}
