package de.flo56958.minetinker.modifiers.types;

import de.flo56958.minetinker.MineTinker;
import de.flo56958.minetinker.data.ToolType;
import de.flo56958.minetinker.modifiers.Modifier;
import de.flo56958.minetinker.utils.ChatWriter;
import de.flo56958.minetinker.utils.ConfigurationManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KineticPlating extends Modifier implements Listener {

	private static KineticPlating instance;
	private int amount;

	private KineticPlating() {
		super(MineTinker.getPlugin());
		customModelData = 10_016;
	}

	public static KineticPlating instance() {
		synchronized (KineticPlating.class) {
			if (instance == null) {
				instance = new KineticPlating();
			}
		}

		return instance;
	}

	@Override
	public String getKey() {
		return "KineticPlating";
	}

	@Override
	public List<ToolType> getAllowedTools() {
		return Collections.singletonList(ToolType.ELYTRA);
	}

	@Override
	public void reload() {
		FileConfiguration config = getConfig();
		config.options().copyDefaults(true);

		config.addDefault("Allowed", true);
		config.addDefault("Color", "%GRAY%");
		config.addDefault("MaxLevel", 5);
		config.addDefault("SlotCost", 1);
		config.addDefault("Amount", 20); //How much XP should be dropped when triggered

		config.addDefault("EnchantCost", 10);
		config.addDefault("Enchantable", false);

		config.addDefault("Recipe.Enabled", true);
		config.addDefault("Recipe.Top", "PIP");
		config.addDefault("Recipe.Middle", "III");
		config.addDefault("Recipe.Bottom", "PIP");

		Map<String, String> recipeMaterials = new HashMap<>();
		recipeMaterials.put("I", Material.IRON_BLOCK.name());
		recipeMaterials.put("P", Material.PHANTOM_MEMBRANE.name());

		config.addDefault("Recipe.Materials", recipeMaterials);

		ConfigurationManager.saveConfig(config);
		ConfigurationManager.loadConfig("Modifiers" + File.separator, getFileName());

		init(Material.IRON_BLOCK);

		this.amount = config.getInt("Amount", 1);
		this.description = this.description.replace("%amount", String.valueOf(this.amount));
	}

	//----------------------------------------------------------

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void effect(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player)) return;
		if (event.getCause() != EntityDamageEvent.DamageCause.FLY_INTO_WALL) return;

		Player player = (Player) event.getEntity();
		if (!player.hasPermission("minetinker.modifiers.kineticplating.use")) return;
		ItemStack elytra = player.getInventory().getChestplate();

		if (!modManager.hasMod(elytra, this)) return;

		int level = modManager.getModLevel(elytra, this);
		double damageMod = 1.0 - (this.amount * level);
		if (damageMod < 0.0) damageMod = 0.0;
		double oldDamage = event.getDamage();
		double newDamage = oldDamage * damageMod;
		event.setDamage(newDamage);
		ChatWriter.logModifier(player, event, this, elytra, String.format("Damage(%.2f -> %.2f [x%.2f])", oldDamage, newDamage, damageMod));
	}
}
