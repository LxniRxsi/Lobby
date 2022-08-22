package net.cubeslide.lobbysystem.handler;

import fr.mrmicky.fastboard.FastBoard;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;
import java.util.UUID;
import net.cubeslide.lobbysystem.LobbySystem;
import net.cubeslide.lobbysystem.commands.BuildCMD;
import net.cubeslide.lobbysystem.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.Inventory;

public class PlayerHandler implements Listener {


  public static final String navigator_name = "§8[§dNavigator§8]";
  public static final String playerhider_name = "§8[§3Player§bHider§8]";
  public static final String enderpearl_name = "§8[§2Ender§aPearl§8]";

  public static final String silenthub_name = "§8[§6§lSilentHub§r§8]";
  private static final HashMap<UUID, Integer> playerUsedEP = new HashMap<>();

  final LobbySystem instance = LobbySystem.getInstance();

  public static void setInventory(Player player) {
    final Inventory playerInv = player.getInventory();
    playerInv.clear();
    playerInv.setItem(1,
        new ItemBuilder(Material.BLAZE_ROD).setDisplayname(playerhider_name).build());

    if(player.hasPermission("cubeslide.silenthub")) {
      playerInv.setItem(3, new ItemBuilder(Material.TNT).setDisplayname(silenthub_name).build());
    }

    playerInv.setItem(4, new ItemBuilder(Material.COMPASS).setDisplayname(navigator_name).build());

    playerInv.setItem(7, new ItemBuilder(Material.ENDER_PEARL).setDisplayname(enderpearl_name)
        .setLore(Arrays.asList("§7You can use this enderpearl.","§7You will receive a new one in 30 seconds."))
        .build());
  }

  public static HashMap<UUID, Integer> getPlayerUsedEP() {
    return playerUsedEP;
  }
  public static final LinkedList<UUID> inSilentHubList = new LinkedList<>();

  private final HashMap<UUID, Long> lastSilentHubUse = new HashMap<>();

  @EventHandler
  public void on(PlayerJoinEvent event) {
    final LobbySystem instance = LobbySystem.getInstance();
    final Player player = event.getPlayer();
    final String prefix = LobbySystem.getPrefix();
    event.setJoinMessage(null);
    if (instance.getConfig().get("spawn") != null) {
      player.teleport((Location) instance.getConfig().get("spawn"));
    } else {
      player.sendMessage(prefix + "§cSpawn Error: §7Please contact a team member.");
    }
    setInventory(player);
    player.setWalkSpeed(0.3F);
    player.setMaxHealth(6D);
    for (Player hiders : Bukkit.getOnlinePlayers()) {
      if(InventoryHandler.hidden.contains(hiders.getUniqueId())) {
        hiders.hidePlayer(event.getPlayer());
      }

      if(inSilentHubList.contains(hiders.getUniqueId()) ) {
        hiders.hidePlayer(player);
        player.hidePlayer(hiders);
      }
    }

    FastBoard board = new FastBoard(player);
    board.updateTitle(
        LobbySystem.getInstance().getConfig().getString("scoreboard.title").replace("&", "§"));
    instance.getBoards().put(player.getUniqueId(), board);
  }

  @EventHandler
  public void on(PlayerQuitEvent event) {
    final LobbySystem instance = LobbySystem.getInstance();
    final Player player = event.getPlayer();
    event.setQuitMessage(null);
    FastBoard board = instance.getBoards().remove(player.getUniqueId());
    if (board != null) {
      board.delete();
    }
  }

  @EventHandler
  public void on(AsyncPlayerChatEvent event) {
    final Player player = event.getPlayer();
    final UUID uuid = player.getUniqueId();

    if(inSilentHubList.contains(uuid)) {
      player.sendMessage(LobbySystem.getPrefix() + "§cYou can't chat while being in the Silenthub.");
      event.getRecipients().remove(player);
      event.setCancelled(true);
    }

    for(Player all : event.getRecipients()) {

      if(inSilentHubList.contains(all.getUniqueId())) {
        event.getRecipients().remove(all);
      }
    }

  }

  @EventHandler
  public void on(PlayerInteractEvent event) {
    final Player player = event.getPlayer();

    if(event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) return;

    if (player.getItemInHand().getType() == Material.ENDER_PEARL) {
      playerUsedEP.put(player.getUniqueId(), 30);
      return;
    }


    final UUID uuid = player.getUniqueId();
    if(player.getItemInHand().getType() == Material.TNT) {

        if(!lastSilentHubUse.containsKey(uuid)) {
          lastSilentHubUse.put(uuid, System.currentTimeMillis());
        } else {

          if(System.currentTimeMillis() - lastSilentHubUse.get(uuid) < 5000) {
            player.sendMessage(LobbySystem.getPrefix() + "§cPlease wait before using this Item again!");
            event.setCancelled(true);
            return;
          }

          lastSilentHubUse.put(uuid, System.currentTimeMillis());
        }

        event.setCancelled(true);
        if(inSilentHubList.contains(uuid)) {
          inSilentHubList.remove(uuid);
          for(Player all : Bukkit.getOnlinePlayers()) {
            all.showPlayer(player);
            player.showPlayer(all);
          }
          player.sendMessage(LobbySystem.getPrefix() + "§cYou are no longer in the Silenthub!");
        } else {
          inSilentHubList.add(uuid);
          for(Player all : Bukkit.getOnlinePlayers()) {
            all.hidePlayer(player);
            player.hidePlayer(all);
          }
          player.sendMessage(LobbySystem.getPrefix() + "§aYou are now in the Silenthub!");
        }
    }



  }

  @EventHandler
  public void onBreak(BlockBreakEvent event) {
    final Player player = event.getPlayer();
    final UUID playerUUID = player.getUniqueId();
    if (!BuildCMD.getInBuildMode().contains(playerUUID)) {
      event.setCancelled(true);
    }
  }


  @EventHandler
  public void on(PlayerDropItemEvent event) {
    final Player player = event.getPlayer();
    final UUID playerUUID = player.getUniqueId();
    if (!BuildCMD.getInBuildMode().contains(playerUUID)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void on(PlayerPickupItemEvent event) {
    final Player player = event.getPlayer();
    final UUID playerUUID = player.getUniqueId();
    if (!BuildCMD.getInBuildMode().contains(playerUUID)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void on(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return;
    }
    final Player player = (Player) event.getEntity();
    final UUID playerUUID = player.getUniqueId();
    if (!BuildCMD.getInBuildMode().contains(playerUUID)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void on(PlayerMoveEvent event) {
    final Player player = event.getPlayer();
    final UUID playerUUID = player.getUniqueId();
    if (BuildCMD.getInBuildMode().contains(playerUUID) || player.getLocation().getY() > 0) {
      return;
    }
    if (player.getLocation().getBlockY() < 0) {
      Location loc = CheckpointHandler.getCurrentCheckpoint().get(playerUUID);
      if (loc == null) {
        player.teleport((Location) Objects.requireNonNull(instance.getConfig().get("spawn")));
      } else {
        player.teleport(CheckpointHandler.getCurrentCheckpoint().get(playerUUID));
        player.sendMessage(LobbySystem.getPrefix()
            + "You were teleported to your last checkpoint. Use §3/checkpoint remove§7 to respawn at spawn.");
      }
    }
  }

  @EventHandler
  public void on(WorldInitEvent event) {
    event.getWorld().setKeepSpawnInMemory(false);
  }

  @EventHandler
  public void on(WeatherChangeEvent event) {
    event.setCancelled(true);
  }
}
