package misat11.za.game;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import misat11.za.Main;
import misat11.za.utils.SpawnUtils;
import misat11.za.utils.Title;
import misat11.za.utils.GameSign;
import misat11.za.utils.SoundGen;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static misat11.lib.lang.I18n.*;

public class Game {

	private String name;
	private Location pos1;
	private Location pos2;
	private Location spawn;
	private PhaseInfo[] phases;
	private int pauseCountdown = 60;
	private List<GamePlayer> players = new ArrayList<GamePlayer>();
	private List<SmallArena> smallarenas = new ArrayList<SmallArena>();
	private World world;
	private List<GameStore> gameStore = new ArrayList<GameStore>();
	private Location boss = null;

	// STATUS
	private GameStatus status = GameStatus.DISABLED;
	private int inPhase = 0;
	private int countdown = 0;
	private BukkitTask task;
	private BossBar bossbar;
	private LivingEntity bossEntity = null;
	private Scoreboard gameScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

	private Game() {

	}

	public String getName() {
		return name;
	}

	public World getWorld() {
		return world;
	}

	public void setWorld(World world) {
		if (this.world == null) {
			this.world = world;
		}
	}

	public void setName(String name) {
		this.name = name;
	}

	public Location getPos1() {
		return pos1;
	}

	public void setPos1(Location pos1) {
		this.pos1 = pos1;
	}

	public Location getPos2() {
		return pos2;
	}

	public void setPos2(Location pos2) {
		this.pos2 = pos2;
	}

	public Location getSpawn() {
		return spawn;
	}

	public void setSpawn(Location spawn) {
		this.spawn = spawn;
	}

	public PhaseInfo[] getPhases() {
		return phases;
	}

	public void setPhases(PhaseInfo[] phases) {
		this.phases = phases;
	}

	public void setSmallArenas(List<SmallArena> smallarenas) {
		this.smallarenas = smallarenas;
	}

	public int getPauseCountdown() {
		return pauseCountdown;
	}

	public void setPauseCountdown(int pauseCountdown) {
		this.pauseCountdown = pauseCountdown;
	}

	public int countPlayers() {
		return this.players.size();
	}

	public List<SmallArena> getSmallArenas() {
		return smallarenas;
	}

	public List<GameStore> getGameStores() {
		return gameStore;
	}

	public void setGameStores(List<GameStore> gameStore) {
		this.gameStore = gameStore;
	}

	public Location getBoss() {
		return boss;
	}

	public void setBoss(Location boss) {
		this.boss = boss;
	}

	public void joinPlayer(GamePlayer player) {
		if (status == GameStatus.DISABLED) {
			player.changeGame(null);
			return;
		}
		boolean isEmpty = players.isEmpty();
		if (!players.contains(player)) {
			players.add(player);
		}

		player.player.teleport(spawn);
		player.player.setPlayerTime(
				status == GameStatus.RUNNING_IN_PHASE || status == GameStatus.RUNNING_BOSS_GAME ? 14000L : 6000L,
				false);

		String message = i18n("join").replace("%name%", player.player.getDisplayName());
		for (GamePlayer p : players)
			p.player.sendMessage(message);

		if (isEmpty) {
			runTask();
		} else {
			bossbar.addPlayer(player.player);
			updateSigns();
		}
	}

	public void leavePlayer(GamePlayer player) {
		if (status == GameStatus.DISABLED) {
			return;
		}
		if (players.contains(player)) {
			players.remove(player);
		}

		String message = i18n("leave").replace("%name%", player.player.getDisplayName());
		bossbar.removePlayer(player.player);
		player.player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
		for (GamePlayer p : players)
			p.player.sendMessage(message);
		if (players.isEmpty()) {
			cancelTask();
			if (status == GameStatus.RUNNING_IN_PHASE) {
				phases[inPhase].phaseEnd();
			}
			inPhase = 0;
			status = GameStatus.WAITING;
			countdown = 0;
			for (GameStore store : gameStore) {
				store.forceKill();
			}
			if (bossEntity != null) {
				bossEntity.setHealth(0);
				bossEntity = null;
			}
		}
		updateSigns();
	}

	public static Game loadGame(File file) {
		if (!file.exists()) {
			return null;
		}
		FileConfiguration configMap = new YamlConfiguration();
		try {
			configMap.load(file);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
			return null;
		}

		Game game = new Game();
		game.name = configMap.getString("name");
		game.pauseCountdown = configMap.getInt("pauseCountdown");
		game.world = Bukkit.getWorld(configMap.getString("world"));
		game.pos1 = readLocationFromString(game.world, configMap.getString("pos1"));
		game.pos2 = readLocationFromString(game.world, configMap.getString("pos2"));
		game.spawn = readLocationFromString(game.world, configMap.getString("spawn"));
		if (configMap.getBoolean("bossenabled"))
			game.boss = readLocationFromString(game.world, configMap.getString("boss"));
		if (configMap.isSet("smallarenas")) {
			for (String sarenaN : configMap.getConfigurationSection("smallarenas").getKeys(false)) {
				ConfigurationSection sarena = configMap.getConfigurationSection("smallarenas")
						.getConfigurationSection(sarenaN);
				SmallArena sa = new SmallArena(sarenaN, readLocationFromString(game.world, sarena.getString("pos1")),
						readLocationFromString(game.world, sarena.getString("pos2")));
				game.smallarenas.add(sa);
			}
		}
		List<PhaseInfo> phasel = new ArrayList<PhaseInfo>();
		for (String phaseN : configMap.getConfigurationSection("phases").getKeys(false)) {
			ConfigurationSection phase = configMap.getConfigurationSection("phases").getConfigurationSection(phaseN);
			PhaseInfo pi = new PhaseInfo(phase.getInt("countdown"));
			for (String monsterN : phase.getConfigurationSection("monsters").getKeys(false)) {
				pi.addMonster(new MonsterInfo(phase.getInt("monsters." + monsterN), EntityType.valueOf(monsterN)));
			}
			for (SmallArena sa : game.smallarenas) {
				if (phase.isSet("small." + sa.name)) {
					List<MonsterInfo> mi = new ArrayList<MonsterInfo>();
					for (String smallMonsterN : phase.getConfigurationSection("small." + sa.name).getKeys(false)) {
						mi.add(new MonsterInfo(phase.getInt("small." + sa.name + "." + smallMonsterN),
								EntityType.valueOf(smallMonsterN)));
					}
					if (!mi.isEmpty()) {
						sa.monsters.put(pi, mi);
					}
				}
			}
			phasel.add(pi);
		}
		game.phases = phasel.toArray(new PhaseInfo[phasel.size()]);
		if (configMap.isSet("stores")) {
			List<String> stores = (List<String>) configMap.getList("stores");
			for (String store : stores) {
				game.gameStore.add(new GameStore(readLocationFromString(game.world, store)));
			}
		}
		game.start();
		Main.getInstance().getLogger().info("Arena " + game.name + " loaded!");
		Main.addGame(game);
		game.updateSigns();
		return game;
	}

	public static Location readLocationFromString(World world, String location) {
		int lpos = 0;
		double x = 0;
		double y = 0;
		double z = 0;
		float yaw = 0;
		float pitch = 0;
		for (String pos : location.split(";")) {
			lpos++;
			switch (lpos) {
			case 1:
				x = Double.parseDouble(pos);
				break;
			case 2:
				y = Double.parseDouble(pos);
				break;
			case 3:
				z = Double.parseDouble(pos);
				break;
			case 4:
				yaw = Float.parseFloat(pos);
				break;
			case 5:
				pitch = Float.parseFloat(pos);
				break;
			default:
				break;
			}
		}
		return new Location(world, x, y, z, yaw, pitch);
	}

	public static String setLocationToString(Location location) {
		return location.getX() + ";" + location.getY() + ";" + location.getZ() + ";" + location.getYaw() + ";"
				+ location.getPitch();
	}

	public void saveToConfig() {
		File dir = new File(Main.getInstance().getDataFolder(), "arenas");
		if (!dir.exists())
			dir.mkdir();
		File file = new File(dir, name + ".yml");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		FileConfiguration configMap = new YamlConfiguration();
		configMap.set("name", name);
		configMap.set("pauseCountdown", pauseCountdown);
		configMap.set("world", world.getName());
		configMap.set("pos1", setLocationToString(pos1));
		configMap.set("pos2", setLocationToString(pos2));
		configMap.set("spawn", setLocationToString(spawn));
		configMap.set("bossenabled", boss != null);
		if (boss != null)
			configMap.set("boss", setLocationToString(boss));
		int lid = 0;
		for (PhaseInfo phase : phases) {
			configMap.set("phases." + lid + ".countdown", phase.getCountdown());
			for (MonsterInfo minfo : phase.getMonsters()) {
				configMap.set("phases." + lid + ".monsters." + minfo.getEntityType().name(), minfo.getCountdown());
			}
			if (!smallarenas.isEmpty()) {
				for (SmallArena sa : smallarenas) {
					if (sa.monsters.containsKey(phase)) {
						for (MonsterInfo monster : sa.monsters.get(phase)) {
							configMap.set("phases." + lid + ".small." + sa.name + "." + monster.getEntityType().name(),
									monster.getCountdown());
						}
					}
				}
			}
			lid++;
		}
		if (!smallarenas.isEmpty()) {
			for (SmallArena sa : smallarenas) {
				configMap.set("smallarenas." + sa.name + ".pos1", setLocationToString(sa.pos1));
				configMap.set("smallarenas." + sa.name + ".pos2", setLocationToString(sa.pos2));
			}
		}
		if (!gameStore.isEmpty()) {
			List<String> nL = new ArrayList<String>();
			for (GameStore store : gameStore) {
				nL.add(setLocationToString(store.loc));
			}
			configMap.set("stores", nL);
		}

		try {
			configMap.save(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static Game createGame(String name) {
		Game game = new Game();
		game.name = name;

		return game;
	}

	public void start() {
		if (status == GameStatus.DISABLED) {
			status = GameStatus.WAITING;
		}
	}

	public void stop() {
		cancelTask();
		List<GamePlayer> clonedPlayers = (List<GamePlayer>) ((ArrayList<GamePlayer>) players).clone();
		for (GamePlayer p : clonedPlayers)
			p.changeGame(null);
		status = GameStatus.DISABLED;
	}

	public void joinToGame(Player player) {
		if (status == GameStatus.DISABLED) {
			return;
		}
		GamePlayer gPlayer = Main.getPlayerGameProfile(player);
		gPlayer.changeGame(this);
	}

	public void leaveFromGame(Player player) {
		if (status == GameStatus.DISABLED) {
			return;
		}
		if (Main.isPlayerInGame(player)) {
			GamePlayer gPlayer = Main.getPlayerGameProfile(player);

			if (gPlayer.getGame() == this) {
				gPlayer.changeGame(null);
			}
		}
	}

	public void run() {
		if (status == GameStatus.WAITING) {
			status = GameStatus.RUNNING_PAUSE;
			String title = i18n("zombie_pause_subtitle", false);
			bossbar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SEGMENTED_20);
			for (GamePlayer p : players)
				bossbar.addPlayer(p.player);
			for (GameStore store : gameStore) {
				store.spawn();
			}
			updateSigns();
		}
		countdown++;
		updateScoreboard();
		if (status == GameStatus.RUNNING_IN_PHASE) {
			if (countdown > phases[inPhase].getCountdown()) {
				phases[inPhase].phaseEnd();
				String title = i18n("zombie_pause_title", false);
				String subtitle = i18n("zombie_pause_subtitle", false);
				bossbar.setTitle(subtitle);
				bossbar.setProgress(0);
				bossbar.setColor(BarColor.RED);
				status = GameStatus.RUNNING_PAUSE;
				countdown = 0;
				if ((inPhase + 1) >= phases.length) {
					if (boss != null) {
						inPhase = -1;
					} else {
						inPhase = 0;
					}
				} else {
					inPhase++;
				}
				for (GamePlayer p : players) {
					Title.send(p.player, title, subtitle);
					p.player.setPlayerTime(6000L, false);
				}
				for (GameStore store : gameStore) {
					store.spawn();
				}
			} else {
				phases[inPhase].phaseRun(countdown, this);
				bossbar.setProgress((double) countdown / (double) phases[inPhase].getCountdown());
			}
		} else if (status == GameStatus.RUNNING_PAUSE) {
			if (countdown > pauseCountdown) {
				String title = i18n("zombie_start_title", false);
				String subtitle = i18n("zombie_start_subtitle", false);
				if (inPhase == -1) {
					status = GameStatus.RUNNING_BOSS_GAME;
					bossbar.setColor(BarColor.PURPLE);
					subtitle = i18n("giant_start_subtitle", false);
				} else {
					status = GameStatus.RUNNING_IN_PHASE;
					bossbar.setColor(BarColor.GREEN);
				}
				countdown = 0;
				bossbar.setTitle(subtitle);
				bossbar.setProgress(0);
				if (Main.isSpigot()) {
					if (status == GameStatus.RUNNING_IN_PHASE) {
						final List<EntityType> monstersInPhase = new ArrayList<EntityType>();
						for (MonsterInfo info : phases[inPhase].getMonsters()) {
							monstersInPhase.add(info.getEntityType());
						}
						if (!smallarenas.isEmpty()) {
							for (SmallArena sarena : smallarenas) {
								if (sarena.monsters.containsKey(phases[inPhase])) {
									for (MonsterInfo minfo : sarena.monsters.get(phases[inPhase])) {
										if (!monstersInPhase.contains(minfo.getEntityType())) {
											monstersInPhase.add(minfo.getEntityType());
										}
									}
								}
							}
						}
						new BukkitRunnable() {

							public int index = 0;

							@Override
							public void run() {
								if (monstersInPhase.size() > 0 && index < monstersInPhase.size()) {
									for (GamePlayer p : players) {
										p.player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
												TextComponent.fromLegacyText(WordUtils.capitalize(monstersInPhase
														.get(index).name().replaceAll("_", " ").toLowerCase())));
									}
									index++;
								} else {
									this.cancel();
								}

							}
						}.runTaskTimerAsynchronously(Main.getInstance(), 20, 40);
					}
				}
				for (GamePlayer p : players) {
					p.player.setPlayerTime(14000L, false);
					Title.send(p.player, title, subtitle);
					SoundGen.play("ENTITY_ZOMBIE_AMBIENT", p.player);
					if (p.teleportAura != 0) {
						p.teleportAura--;
					} else {
						p.player.teleport(spawn);
					}
				}
				for (GameStore store : gameStore) {
					store.kill();
				}
			} else {
				if (countdown >= (pauseCountdown - 4)) {
					String title = ChatColor.YELLOW.toString() + Integer.toString(pauseCountdown - countdown + 1);
					for (GamePlayer p : players) {
						Title.send(p.player, title, "");
						SoundGen.play("ENTITY_EXPERIENCE_ORB_PICKUP", p.player);
					}
				}
				bossbar.setProgress((double) countdown / (double) pauseCountdown);
			}
		} else if (status == GameStatus.RUNNING_BOSS_GAME) {
			if (bossEntity == null) {
				bossEntity = SpawnUtils.spawnGiant(boss);
			} else if (bossEntity.isDead()) {
				bossEntity = null;
				String title = i18n("zombie_pause_title", false);
				String subtitle = i18n("zombie_pause_subtitle", false);
				bossbar.setTitle(subtitle);
				bossbar.setProgress(0);
				bossbar.setColor(BarColor.RED);
				status = GameStatus.RUNNING_PAUSE;
				countdown = 0;
				inPhase = 0;
				for (GamePlayer p : players) {
					Title.send(p.player, title, subtitle);
					p.player.setPlayerTime(6000L, false);
				}
				for (GameStore store : gameStore) {
					store.spawn();
				}
			} else {
				bossbar.setProgress(bossEntity.getHealth() / bossEntity.getMaxHealth());
			}
		}
	}

	public GameStatus getStatus() {
		return status;
	}

	private void runTask() {
		if (task != null) {
			if (Bukkit.getScheduler().isQueued(task.getTaskId())) {
				task.cancel();
			}
			task = null;
		}
		task = (new BukkitRunnable() {

			public void run() {
				Game.this.run();
			}

		}.runTaskTimer(Main.getInstance(), 0, 20));
	}

	private void cancelTask() {
		if (task != null) {
			if (Bukkit.getScheduler().isQueued(task.getTaskId())) {
				task.cancel();
			}
			task = null;
		}
	}

	public void skip() {
		if (status == GameStatus.RUNNING_BOSS_GAME) {
			bossEntity.setHealth(0);
		}
		if (status == GameStatus.RUNNING_IN_PHASE) {
			countdown = phases[inPhase].getCountdown();
			new BukkitRunnable() {

				@Override
				public void run() {
					if ((inPhase + 1) >= phases.length) {
						if (boss != null) {
							inPhase = -1;
						} else {
							inPhase = 0;
						}
					} else {
						inPhase++;
					}

				}
			}.runTaskLater(Main.getInstance(), 40);
		}
		if (status == GameStatus.RUNNING_PAUSE) {
			if ((inPhase + 1) >= phases.length) {
				if (boss != null) {
					inPhase = -1;
				} else {
					inPhase = 0;
				}
			} else {
				inPhase++;
			}
			countdown = 0;
		}
	}

	public void skip(final int phase) {
		if (status == GameStatus.RUNNING_BOSS_GAME) {
			bossEntity.setHealth(0);
			new BukkitRunnable() {

				@Override
				public void run() {
					if (phase > phases.length) {
						inPhase = 0;
					} else {
						inPhase = phase;
					}

				}
			}.runTaskLater(Main.getInstance(), 40);
		}
		if (status == GameStatus.RUNNING_IN_PHASE) {
			countdown = phases[inPhase].getCountdown() - 1;
			new BukkitRunnable() {

				@Override
				public void run() {
					if (phase > phases.length) {
						inPhase = 0;
					} else {
						inPhase = phase - 1;
					}

				}
			}.runTaskLater(Main.getInstance(), 40);
		}
		if (status == GameStatus.RUNNING_PAUSE) {
			if (phase > phases.length) {
				inPhase = 0;
			} else {
				inPhase = phase;
			}
			countdown = 0;
		}
	}

	private void updateScoreboard() {
		if (!Main.getConfigurator().config.getBoolean("scoreboard.enabled")) {
			return;
		}

		gameScoreboard.clearSlot(DisplaySlot.SIDEBAR);

		Objective obj = gameScoreboard.getObjective("display");
		if (obj != null) {
			obj.unregister();
		}

		obj = gameScoreboard.registerNewObjective("display", "dummy");
		obj.setDisplaySlot(DisplaySlot.SIDEBAR);
		obj.setDisplayName(this.formatScoreboardString(
				Main.getConfigurator().config.getString("scoreboard.title", "§aZombieApocalypse")));

		List<String> rows = Main.getConfigurator().config.getStringList("scoreboard.content");
		int rowMax = rows.size();
		if (rows == null || rows.isEmpty()) {
			return;
		}

		for (String row : rows) {
			if (row.trim().equals("")) {
				for (int i = 0; i <= rowMax; i++) {
					row = row + " ";
				}
			}

			Score score = obj.getScore(this.formatScoreboardString(row));
			score.setScore(rowMax);
			rowMax--;
		}

		for (GamePlayer player : players) {
			player.player.setScoreboard(gameScoreboard);
		}
	}

	private String formatScoreboardString(String row) {
		row = row.replaceAll("%players%", Integer.toString(players.size()));
		row = row.replaceAll("%arena%", name);
		String phaseStr = inPhase >= 0 ? Integer.toString(inPhase) : i18nonly("scoreboard_boss");
		row = row.replaceAll("%phase%", phaseStr);
		row = row.replaceAll("%elapsedTime%", getFormattedTime(countdown));
		row = row.replaceAll("%countdown%", Integer.toString(countdown));
		return row;
	}

	public String getFormattedTime(int countdown) {
		String colorCodes = "";

		if (status == GameStatus.RUNNING_PAUSE) {
			colorCodes += ChatColor.RED;
		} else if (status == GameStatus.RUNNING_IN_PHASE) {
			colorCodes += ChatColor.GREEN;
		} else if (status == GameStatus.RUNNING_BOSS_GAME) {
			colorCodes += ChatColor.DARK_PURPLE;
		}

		int min;
		int sec;
		String minStr;
		String secStr;

		min = (int) Math.floor(countdown / 60);
		sec = countdown % 60;

		minStr = (min < 10) ? "0" + min : String.valueOf(min);
		secStr = (sec < 10) ? "0" + sec : String.valueOf(sec);

		return colorCodes + minStr + ":" + secStr;
	}

	public void updateSigns() {
		List<GameSign> gameSigns = Main.getSignsForGame(this);

		if (gameSigns.isEmpty()) {
			return;
		}

		String statusLine = "";
		String playersLine = "";
		switch (status) {
		case DISABLED:
			statusLine = i18nonly("sign_status_disabled");
			playersLine = i18nonly("sign_status_disabled_players");
			break;
		case WAITING:
			statusLine = i18nonly("sign_status_waiting");
			playersLine = i18nonly("sign_status_waiting_players");
			break;
		default:
			statusLine = i18nonly("sign_status_running");
			playersLine = i18nonly("sign_status_running_players");
		}
		playersLine = playersLine.replace("%players%", Integer.toString(players.size()));

		List<String> texts = new ArrayList<>(Main.getConfigurator().config.getStringList("sign"));

		for (int i = 0; i < texts.size(); i++) {
			String text = texts.get(i);
			texts.set(i, text.replaceAll("%arena%", this.getName()).replaceAll("%status%", statusLine)
					.replaceAll("%players%", playersLine));
		}

		for (GameSign sign : gameSigns) {
			if (sign.getLocation().getChunk().isLoaded()) {
				Block block = sign.getLocation().getBlock();
				if (block.getState() instanceof Sign) {
					Sign state = (Sign) block.getState();
					for (int i = 0; i < texts.size() && i < 4; i++) {
						state.setLine(i, texts.get(i));
					}
					state.update();
				}
			}
		}
	}

}
