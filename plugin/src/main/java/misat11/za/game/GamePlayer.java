package misat11.za.game;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import misat11.za.Main;

public class GamePlayer {

	public final Player player;
	public int coins = 0;
	public int teleportAura = 0;
	public Map<String, Integer> mobkills = new HashMap<>();
	public Map<String, Integer> pvpkills = new HashMap<>();
	public Map<String, Integer> mobdeaths = new HashMap<>();
	public Map<String, Integer> pvpdeaths = new HashMap<>();
	private Game game = null;

	private StoredInventory oldinventory = new StoredInventory();

	public GamePlayer(Player player) {
		this.player = player;
		loadGamePlayerData();
	}

	public void changeGame(Game game) {
		if (this.game != null && game == null) {
			this.game.leavePlayer(this);
			this.game = null;
			this.saveInventory();
			this.clean();
			this.restoreInv();
		} else if (this.game == null && game != null) {
			this.storeInv();
			this.clean();
			this.restoreInventory();
			this.game = game;
			this.game.joinPlayer(this);
		} else if (this.game != null && game != null) {
			this.game.leavePlayer(this);
			this.game = game;
			this.game.joinPlayer(this);
		}
		saveGamePlayerData();
	}
	
	public int countKills() {
		return countMobKills() + countPvPKills();
	}
	
	public int countDeaths() {
		return countMobDeaths() + countPvPDeaths();
	}
	
	public double getKD() {
		return (double) countKills() / (double) (countDeaths() == 0 ? 1 : countDeaths());
	}
	
	public int countMobKills() {
		int total = 0;
		for (int i : mobkills.values()) {
			total += i;
		}
		return total;
	}
	
	public int countMobDeaths() {
		int total = 0;
		for (int i : mobdeaths.values()) {
			total += i;
		}
		return total;
	}
	
	public int countPvPKills() {
		int total = 0;
		for (int i : pvpkills.values()) {
			total += i;
		}
		return total;
	}
	
	public int countPvPDeaths() {
		int total = 0;
		for (int i : pvpdeaths.values()) {
			total += i;
		}
		return total;
	}

	public Game getGame() {
		return game;
	}

	public boolean isInGame() {
		return game != null;
	}

	public void storeInv() {
		oldinventory.inventory = player.getInventory().getContents();
		oldinventory.armor = player.getInventory().getArmorContents();
		oldinventory.xp = Float.valueOf(player.getExp());
		oldinventory.effects = player.getActivePotionEffects();
		oldinventory.mode = player.getGameMode();
		oldinventory.left = player.getLocation();
		oldinventory.level = player.getLevel();
		oldinventory.listName = player.getPlayerListName();
		oldinventory.displayName = player.getDisplayName();
		oldinventory.foodLevel = player.getFoodLevel();
	}

	public void restoreInv() {
		player.getInventory().setContents(oldinventory.inventory);
		player.getInventory().setArmorContents(oldinventory.armor);

		player.addPotionEffects(oldinventory.effects);
		player.setLevel(oldinventory.level);
		player.setExp(oldinventory.xp);
		player.setFoodLevel(oldinventory.foodLevel);

		for (PotionEffect e : player.getActivePotionEffects())
			player.removePotionEffect(e.getType());

		player.addPotionEffects(oldinventory.effects);

		player.setPlayerListName(oldinventory.listName);
		player.setDisplayName(oldinventory.displayName);

		player.setGameMode(oldinventory.mode);

		if (oldinventory.mode == GameMode.CREATIVE)
			player.setAllowFlight(true);

		player.updateInventory();
		player.teleport(oldinventory.left);
		player.resetPlayerTime();
	}

	public void saveGamePlayerData() {
		String saveCfgGroup = player.getName().toLowerCase();
		File dir = new File(Main.getInstance().getDataFolder(), "players");
		if (!dir.exists())
			dir.mkdir();
		File file = new File(dir, saveCfgGroup + ".yml");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		FileConfiguration pconfig = new YamlConfiguration();
		try {
			pconfig.load(file);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
		}
		pconfig.set("coins", coins);
		pconfig.set("teleportAura", teleportAura);
		for (Map.Entry<String, Integer> entry : mobkills.entrySet()) {
			pconfig.set("kills." + entry.getKey() + ".mobs", entry.getValue());
		}
		for (Map.Entry<String, Integer> entry : pvpkills.entrySet()) {
			pconfig.set("kills." + entry.getKey() + ".players", entry.getValue());
		}
		for (Map.Entry<String, Integer> entry : mobdeaths.entrySet()) {
			pconfig.set("deaths." + entry.getKey() + ".mobs", entry.getValue());
		}
		for (Map.Entry<String, Integer> entry : pvpdeaths.entrySet()) {
			pconfig.set("deaths." + entry.getKey() + ".players", entry.getValue());
		}
		try {
			pconfig.save(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void clean() {

		PlayerInventory inv = this.player.getInventory();
		inv.setArmorContents(new ItemStack[4]);
		inv.setContents(new ItemStack[] {});

		this.player.setAllowFlight(false);
		this.player.setFlying(false);
		this.player.setExp(0.0F);
		this.player.setLevel(0);
		this.player.setSneaking(false);
		this.player.setSprinting(false);
		this.player.setFoodLevel(20);
		this.player.setSaturation(10);
		this.player.setExhaustion(0);
		this.player.setHealth(20.0D);
		this.player.setFireTicks(0);
		this.player.setGameMode(GameMode.SURVIVAL);

		if (this.player.isInsideVehicle()) {
			this.player.leaveVehicle();
		}

		for (PotionEffect e : this.player.getActivePotionEffects()) {
			this.player.removePotionEffect(e.getType());
		}

		this.player.updateInventory();
	}

	public void loadGamePlayerData() {
		String saveCfgGroup = player.getName().toLowerCase();
		File dir = new File(Main.getInstance().getDataFolder(), "players");
		if (!dir.exists())
			dir.mkdir();
		File file = new File(dir, saveCfgGroup + ".yml");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		FileConfiguration pconfig = new YamlConfiguration();
		try {
			pconfig.load(file);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
		}
		if (pconfig.isSet("coins"))
			coins = pconfig.getInt("coins");
		if (pconfig.isSet("teleportAura"))
			teleportAura = pconfig.getInt("teleportAura");
		if (pconfig.isSet("kills")) {
			ConfigurationSection kills = pconfig.getConfigurationSection("kills");
			for (String killN : kills.getKeys(false)) {
				ConfigurationSection kill = kills.getConfigurationSection(killN);
				int mobs = kill.getInt("mobs", 0);
				int players = kill.getInt("players", 0);
				mobkills.put(killN, mobs);
				pvpkills.put(killN, players);
			}
		}
		if (pconfig.isSet("deaths")) {
			ConfigurationSection deaths = pconfig.getConfigurationSection("deaths");
			for (String deathN : deaths.getKeys(false)) {
				ConfigurationSection death = deaths.getConfigurationSection(deathN);
				int mobs = death.getInt("mobs", 0);
				int players = death.getInt("players", 0);
				mobdeaths.put(deathN, mobs);
				pvpdeaths.put(deathN, players);
			}
		}

		saveGamePlayerData();
	}

	public void saveInventory() {
		String saveCfgGroup = player.getName().toLowerCase();
		File dir = new File(Main.getInstance().getDataFolder(), "players");
		if (!dir.exists())
			dir.mkdir();
		File file = new File(dir, saveCfgGroup + ".yml");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		FileConfiguration c = new YamlConfiguration();
		try {
			c.load(file);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
		}
		c.set("inventory.armor", player.getInventory().getArmorContents());
		c.set("inventory.content", player.getInventory().getContents());
		c.set("inventory.lvl", player.getLevel());
		c.set("inventory.xp", player.getExp());
		try {
			c.save(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void restoreInventory() {
		String saveCfgGroup = player.getName().toLowerCase();
		File dir = new File(Main.getInstance().getDataFolder(), "players");
		if (!dir.exists())
			dir.mkdir();
		File file = new File(dir, saveCfgGroup + ".yml");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		FileConfiguration c = new YamlConfiguration();
		try {
			c.load(file);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
		}
		if (!c.isSet("inventory")) {
			new BukkitRunnable() {

				@Override
				public void run() {
					Main.getKits().openForPlayer(player);
				}

			}.runTask(Main.getInstance());
		}
		if (c.isSet("inventory.armor")) {
			ItemStack[] armor = c.getList("inventory.armor").toArray(new ItemStack[0]);
			player.getInventory().setArmorContents(armor);
		}
		if (c.isSet("inventory.content")) {
			ItemStack[] content = c.getList("inventory.content").toArray(new ItemStack[0]);
			player.getInventory().setContents(content);
		}
		if (c.isSet("inventory.lvl")) {
			player.setLevel(c.getInt("inventory.lvl"));
		}
		if (c.isSet("inventory.xp")) {
			player.setExp((float) c.getDouble("inventory.xp"));
		}
	}

}
