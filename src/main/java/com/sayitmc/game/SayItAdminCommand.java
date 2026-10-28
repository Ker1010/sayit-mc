package com.sayitmc.game;

import java.util.Locale;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.permissions.Permissions;

/**
 * {@code /sayitadmin} for operators: {@code language <code>} picks the language every player learns,
 * {@code time <seconds>} the time per word. Players cannot choose either.
 */
final class SayItAdminCommand {
	private static final int ERROR_COLOR = 0xFF5555;

	private SayItAdminCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, SayItServer game) {
		dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("sayitadmin")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> status(ctx.getSource(), game))
				.then(LiteralArgumentBuilder.<CommandSourceStack>literal("language")
						.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("code", StringArgumentType.word())
								.suggests((ctx, builder) -> {
									String typed = builder.getRemaining().toLowerCase(Locale.ROOT);

									for (String code : Languages.COMMON_CODES) {
										if (code.startsWith(typed)) {
											builder.suggest(code);
										}
									}

									return builder.buildFuture();
								})
								.executes(ctx -> setLanguage(ctx.getSource(), StringArgumentType.getString(ctx, "code"), game))))
				.then(LiteralArgumentBuilder.<CommandSourceStack>literal("strictness")
						.then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("percent", IntegerArgumentType.integer(50, 100))
								.executes(ctx -> setStrictness(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "percent"), game))))
				.then(LiteralArgumentBuilder.<CommandSourceStack>literal("time")
						.then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("seconds", IntegerArgumentType.integer(3, 120))
								.executes(ctx -> setTime(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds"), game)))));
	}

	private static int status(CommandSourceStack source, SayItServer game) {
		ServerConfig config = game.config();
		reply(source, "Everyone learns " + Languages.displayName(config.language) + " (" + config.language + "), "
				+ config.secondsToAnswer + "s per word, " + Math.round(config.matchThreshold * 100)
				+ "% strict. Change with /sayitadmin language <code>, time <seconds> or strictness <percent>.", false);
		return Command.SINGLE_SUCCESS;
	}

	private static int setLanguage(CommandSourceStack source, String code, SayItServer game) {
		String language = code.toLowerCase(Locale.ROOT);

		if (!Languages.isValidCode(language)) {
			reply(source, language + " is not a Minecraft language code. Try ja_jp, zh_cn, ko_kr, de_de, fr_fr, es_es...", true);
			return 0;
		}

		ServerConfig next = game.config().copy();
		next.language = language;
		game.apply(next);
		game.save();
		reply(source, "Everyone now learns " + Languages.displayName(language) + " (" + language + ").", false);
		return Command.SINGLE_SUCCESS;
	}

	private static int setStrictness(CommandSourceStack source, int percent, SayItServer game) {
		ServerConfig next = game.config().copy();
		next.matchThreshold = percent / 100.0;
		game.apply(next);
		game.save();
		reply(source, "Spoken words now have to be " + percent + "% right (3-letter words exactly).", false);
		return Command.SINGLE_SUCCESS;
	}

	private static int setTime(CommandSourceStack source, int seconds, SayItServer game) {
		ServerConfig next = game.config().copy();
		next.secondsToAnswer = seconds;
		game.apply(next);
		game.save();
		reply(source, "Everyone now gets " + seconds + " seconds per word.", false);
		return Command.SINGLE_SUCCESS;
	}

	private static void reply(CommandSourceStack source, String message, boolean error) {
		MutableComponent text = Component.literal(message);
		source.sendSuccess(() -> error ? text.withColor(ERROR_COLOR) : text, false);
	}
}
