package de.flo56958.MineTinker.Modifiers.Types;

import de.flo56958.MineTinker.Data.ToolType;
import de.flo56958.MineTinker.Main;
import de.flo56958.MineTinker.Modifiers.Modifier;
import de.flo56958.MineTinker.Utilities.ChatWriter;
import de.flo56958.MineTinker.Utilities.ConfigurationManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public class Soulbound extends Modifier implements Listener {

	private static Soulbound instance;
	//Must be UUID as if the Player reconnects the Player-Object gets recreated and is not the same anymore
 	private final HashMap<UUID, ArrayList<ItemStack>> storedItemStacks = new HashMap<>(); //saves ItemStacks until reload (if the player does not respawn instantly)
	private boolean toolDropable;
	private boolean decrementModLevelOnUse;
	private int percentagePerLevel;

	private Soulbound() {
		super(Main.getPlugin());
		customModelData = 10_036;
	}

	public static Soulbound instance() {
		synchronized (Soulbound.class) {
			if (instance == null) {
				instance = new Soulbound();
			}
		}

		return instance;
	}

	@Override
	public String getKey() {
		return "Soulbound";
	}

	@Override
	public List<ToolType> getAllowedTools() {
		return Collections.singletonList(ToolType.ALL);
	}

	@Override
	public void reload() {
		FileConfiguration config = getConfig();
		config.options().copyDefaults(true);

		config.addDefault("Allowed", true);
		config.addDefault("Name", "Soulbound");
		config.addDefault("ModifierItemName", "Powerinfused Beacon");
		config.addDefault("Description", "Do not lose the tool when dying.");
		config.addDefault("DescriptionModifierItem", "%WHITE%Modifier-Item for the Soulbound-Modifier");
		config.addDefault("Color", "%GRAY%");
		config.addDefault("MaxLevel", 1);
		config.addDefault("SlotCost", 2);
		config.addDefault("PercentagePerLevel", 100);
		config.addDefault("DecrementModLevelOnUse", false);
		config.addDefault("ToolDropable", true);
		config.addDefault("OverrideLanguagesystem", false);

		config.addDefault("EnchantCost", 10);
		config.addDefault("Enchantable", false);

		config.addDefault("Recipe.Enabled", true);
		config.addDefault("Recipe.Top", "BLB");
		config.addDefault("Recipe.Middle", "LNL");
		config.addDefault("Recipe.Bottom", "BLB");

		Map<String, String> recipeMaterials = new HashMap<>();
		recipeMaterials.put("B", Material.BLAZE_ROD.name());
		recipeMaterials.put("L", Material.LAVA_BUCKET.name());
		recipeMaterials.put("N", Material.NETHER_STAR.name());

		config.addDefault("Recipe.Materials", recipeMaterials);

		ConfigurationManager.saveConfig(config);
		ConfigurationManager.loadConfig("Modifiers" + File.separator, getFileName());

		init(Material.BEACON, true);

		this.toolDropable = config.getBoolean("ToolDropable", true);
		this.decrementModLevelOnUse = config.getBoolean("DecrementModLevelOnUse", false);
		this.percentagePerLevel = config.getInt("PercentagePerLevel", 100);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void effect(PlayerDeathEvent event) {
		if (event.getKeepInventory()) {
			return;
		}

		Player player = event.getEntity();
		Inventory inventory = player.getInventory();

		for (ItemStack itemStack : inventory.getContents()) {
			if (itemStack == null) {
				continue; // More consistent nullability in NotNull fields
			}

			if (modManager.isArmorViable(itemStack) || modManager.isToolViable(itemStack) || modManager.isWandViable(itemStack)) {
				if (!player.hasPermission("minetinker.modifiers.soulbound.use")) {
					continue;
				}

				if (!modManager.hasMod(itemStack, this)) {
					continue;
				}

				Random rand = new Random();
				if (rand.nextInt(100) > modManager.getModLevel(itemStack, this) * percentagePerLevel) {
					continue;
				}

				storedItemStacks.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()); // ?

				ArrayList<ItemStack> stored = storedItemStacks.get(player.getUniqueId());

				ChatWriter.log(false, player.getDisplayName() + " triggered Soulbound on " + ChatWriter.getDisplayName(itemStack) + ChatColor.GRAY + " (" + itemStack.getType().toString() + ")!");

				if (stored.contains(itemStack)) {
					continue;
				}

				if (decrementModLevelOnUse) {
					int newLevel = modManager.getModLevel(itemStack, this) - 1;

					if (newLevel == 0) {
						modManager.removeMod(itemStack, this);
					} else {
						modManager.getNBTHandler().setInt(itemStack, getKey(), modManager.getModLevel(itemStack, this) - 1);
					}
				}

				stored.add(itemStack.clone());
				itemStack.setAmount(0);
			}
		}
	}

	/**
	 * Effect if a player respawns
	 */
	@EventHandler
	public void effect(PlayerRespawnEvent event) {
		Player player = event.getPlayer();

		if (!player.hasPermission("minetinker.modifiers.soulbound.use")) {
			return;
		}

		if (!storedItemStacks.containsKey(player.getUniqueId())) {
			return;
		}

		ArrayList<ItemStack> stored = storedItemStacks.get(player.getUniqueId());

		for (ItemStack is : stored) {
			if (player.getInventory().addItem(is).size() != 0) { //adds items to (full) inventory
				player.getWorld().dropItem(player.getLocation(), is);
			} // no else as it gets added in if
		}

		storedItemStacks.remove(player.getUniqueId());
	}

	/**
	 * Effect if a player drops an item
	 *
	 * @param event the event
	 */
	@EventHandler(ignoreCancelled = true)
	public void effect(PlayerDropItemEvent event) {
		Item item = event.getItemDrop();
		ItemStack tool = item.getItemStack();

		if (!(modManager.isArmorViable(tool) || modManager.isToolViable(tool) || modManager.isWandViable(tool))) {
			return;
		}

		if (!modManager.hasMod(tool, this)) {
			return;
		}

		if (toolDropable) {
			return;
		}

		event.setCancelled(true);
	}
}
