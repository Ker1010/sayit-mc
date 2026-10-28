package com.sayitmc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import com.sayitmc.game.SayItServer;
import com.sayitmc.network.SayItPayloads;

/** Common entrypoint: runs on dedicated servers and inside singleplayer worlds. */
public final class SayItMod implements ModInitializer {
	public static final String MOD_ID = "sayitmc";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SayItPayloads.register();
		new SayItServer().register();
	}
}
