package com.sayitmc.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.sayitmc.game.Languages;
import com.sayitmc.network.SayItPayloads;

/**
 * {@code /sayit} - status, {@code on|off}, {@code test} (practice the held item), {@code skip}, {@code forget}.
 * The language is the server's choice ({@code /sayitadmin language} for operators).
 */
final class SayItCommands {
	private SayItCommands() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(literal("sayit")
				.executes(ctx -> status(ctx.getSource()))
				.then(literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
				.then(literal("off").executes(ctx -> setEnabled(ctx.getSource(), false)))
				.then(literal("test").executes(ctx -> practiceHeldItem(ctx.getSource())))
				.then(literal("skip").executes(ctx -> skip(ctx.getSource())))
				.then(literal("forget").executes(ctx -> forget(ctx.getSource()))));
	}

	private static int status(FabricClientCommandSource source) {
		String language = SayItClient.serverLanguage();

		if (!SayItClient.isServerGame() || language == null) {
			source.sendFeedback(Component.literal("This server does not run Say It!, so there is nothing to say here."));
			return Command.SINGLE_SUCCESS;
		}

		source.sendFeedback(Component.literal("This server's language is " + Languages.displayName(language) + " (" + language + "), "
				+ SayItClient.serverSeconds() + "s per word. You are " + (ClientConfig.get().enabled || SayItClient.isMandatory()
				? "playing." : "not playing (/sayit on).")));
		return Command.SINGLE_SUCCESS;
	}

	private static int setEnabled(FabricClientCommandSource source, boolean enabled) {
		if (!enabled && SayItClient.isMandatory()) {
			source.sendError(Component.literal("This server requires everyone to play Say It!."));
			return 0;
		}

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
		String language = SayItClient.serverLanguage();
		Player player = source.getPlayer();
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);

		if (language == null) {
			source.sendError(Component.literal("This server does not run Say It!."));
			return 0;
		}

		if (held.isEmpty()) {
			source.sendError(Component.literal("Hold an item to practice it."));
			return 0;
		}

		if (!SayItClient.challenges().practice(held, language, SayItClient.serverSeconds())) {
			source.sendError(Component.literal("Finish the current word first."));
			return 0;
		}

		return Command.SINGLE_SUCCESS;
	}

	private static int skip(FabricClientCommandSource source) {
		if (!SayItClient.challenges().skipPractice()) {
			source.sendError(Component.literal("Server words cannot be skipped. Drop the item to reset its timer."));
			return 0;
		}

		return Command.SINGLE_SUCCESS;
	}

	private static int forget(FabricClientCommandSource source) {
		String language = SayItClient.serverLanguage();

		if (!SayItClient.isServerGame() || language == null) {
			source.sendError(Component.literal("This server does not run Say It!."));
			return 0;
		}

		SayItClient.challenges().clear();
		ClientPlayNetworking.send(new SayItPayloads.Forget());
		source.sendFeedback(Component.literal("Forgot every " + Languages.displayName(language) + " word you learned here."));
		return Command.SINGLE_SUCCESS;
	}
}
