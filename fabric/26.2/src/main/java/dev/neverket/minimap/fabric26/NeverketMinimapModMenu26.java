package dev.neverket.minimap.fabric26;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class NeverketMinimapModMenu26 implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return NeverketMinimapFabric26::createSettingsScreen;
	}
}
