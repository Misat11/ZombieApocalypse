package misat11.za.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import misat11.za.Main;
import misat11.za.game.GamePlayer;
import misat11.za.utils.Shop;

public class Menu implements Listener {

	private Inventory inv;
	private HashMap<String, String> ids = new HashMap<String, String>();

	public Menu(Plugin p) {
		inv = Bukkit.getServer().createInventory(null, 54, "[ZA] Shop");
		int lastpos = 0;

		Set<String> s = Main.getConfigurator().shopconfig.getConfigurationSection("shop-items").getKeys(false);

		for (String i : s) {
			Material material = Material
					.getMaterial(Main.getConfigurator().shopconfig.getString("shop-items." + i + ".item"));
			if (material != null) {
				inv.setItem(lastpos, createItem(
						material,
						Main.getConfigurator().shopconfig.getInt("shop-items." + i + ".item-damage"),
						Main.getConfigurator().shopconfig.getString("shop-items." + i + ".name"),
						Integer.toString(Main.getConfigurator().shopconfig.getInt("shop-items." + i + ".points"))));
				addIds(Integer.toString(lastpos), i);
				lastpos = lastpos + 1;
			}
		}

		Bukkit.getServer().getPluginManager().registerEvents(this, p);
	}

	private ItemStack createItem(Material material, int damage, String name, String coins) {
		ItemStack i = new ItemStack(material, 1, (short) damage);
		ItemMeta im = i.getItemMeta();
		im.setDisplayName(name);
		im.setLore(Arrays.asList("Coins:", coins));
		i.setItemMeta(im);
		return i;
	}

	public void addIds(String key, String value) {
		this.ids.put(key, value);
	}

	public String getIds(String key) {
		return this.ids.get(key);
	}

	public void show(Player p) {
		p.openInventory(inv);
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent e) {
		if (!e.getInventory().equals(inv))
			return;
		Bukkit.getLogger().info("inventory equals");
		e.setCancelled(true);
		if (!(e.getWhoClicked() instanceof Player)) {
			e.getWhoClicked().closeInventory();
			return; // How this happened?
		}
		Player player = (Player) e.getWhoClicked();
		if (!Main.isPlayerInGame(player)) {
			player.closeInventory();
			return;
		}
		String i = getIds(Integer.toString(e.getSlot()));
		GamePlayer gPlayer = Main.getPlayerGameProfile((Player) e.getWhoClicked());
		boolean buy = Shop.buyItem(gPlayer, i);
		if (buy == true) {
			player.closeInventory();
			player.sendMessage(I18n._("buy_succes")
					.replace("%item%", Main.getConfigurator().shopconfig.getString("shop-items." + i + ".name"))
					.replace("%yourcoins%", Integer.toString(gPlayer.coins)));
		} else {
			player.closeInventory();
			player.sendMessage(I18n._("buy_no_coins")
					.replace("%item%", Main.getConfigurator().shopconfig.getString("shop-items." + i + ".name"))
					.replace("%yourcoins%", Integer.toString(gPlayer.coins)));

		}
	}
}