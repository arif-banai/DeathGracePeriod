package me.smeef.deathgraceperiod;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * This Spigot plugin implements a grace period for players after they respawn.
 * Once active, a player in grace may not take damage.
 * The grace period goes away after a configurable amount of time, or if the
 * player attempts to attack, place or break blocks or interact with inventory-containing blocks/entities.
 *
 * @author  Arif Banai
 * @version beta 5
 * @since   2020-04-11
 *
 */
public final class DeathGracePeriod extends JavaPlugin implements Listener {

    //This is in units of Ticks. 1 second = 20 ticks
    private long invincibleTime;

    //Prefix to use in chat messages, usually to a player.
    private final String prefix = "[" + ChatColor.RED + "DeathGrace" + ChatColor.RESET + "] ";

    //TODO Maybe use one HashMap<String, Entry<Long, Integer>>
    private HashMap<String, Long> playersOnGracePeriod;
    private HashMap<String, Integer> playerGraceEndingTask;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        //Time is in ticks, so multiply by 20 to convert from seconds to ticks
        this.invincibleTime = getConfig().getLong("invincibility-duration", 60L) * 20L;

        playersOnGracePeriod = new HashMap<String, Long>();
        playerGraceEndingTask = new HashMap<String, Integer>();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        //Unregister EventHandlers from this plugin
        HandlerList.unregisterAll((JavaPlugin) this);
    }

    /**
     * If a player places a block while in a grace period, end the grace period.
     *
     * @param event The event where a Block is place by a Player
     */
    @EventHandler
    public void onBlockPlaceEvent(final @NotNull BlockPlaceEvent event) {
        Player p = event.getPlayer();
        String playerUUID = p.getUniqueId().toString();

        if(playersOnGracePeriod.containsKey(playerUUID)) {
            cancelGraceEndingTask(playerUUID);
            p.sendMessage(prefix + "You are no longer invulnerable because you placed a block!");
        }
    }

    /**
     * If a player breaks a block while in a grace period, end the grace period.
     *
     * @param event The event where a Block is broken by a Player
     */
    @EventHandler
    public void onBlockBreakEvent(final @NotNull BlockBreakEvent event) {
        Player p = event.getPlayer();
        String playerUUID = p.getUniqueId().toString();

        if(playersOnGracePeriod.containsKey(playerUUID)) {
            cancelGraceEndingTask(playerUUID);
            p.sendMessage(prefix + "You are no longer invulnerable because you broke a block!");
        }
    }

    /**
     * If a player is in a grace period, and interacts (right-clicks) on a block that
     * has an inventory, or otherwise is not allowed to be interacted with during
     * a grace period, that player's grace period is disabled.
     *
     * @param event The event where a Player interacts with something
     */
    @EventHandler
    public void onPlayerInteractEvent(final @NotNull PlayerInteractEvent event) {
        Player p = event.getPlayer();
        String playerUUID = p.getUniqueId().toString();

        if(playerHasGracePeriod(playerUUID)) {
            if(event.getAction() == Action.RIGHT_CLICK_BLOCK && !event.isBlockInHand()) {
                Block clickedBlock = event.getClickedBlock();

                if(clickedBlock != null) {
                    BlockState blockState = clickedBlock.getState();
                    BlockData blockData = clickedBlock.getBlockData();
                    Material material = clickedBlock.getType();

                    if(blockState instanceof InventoryHolder ||
                            material == Material.ANVIL ||
                            material == Material.CHIPPED_ANVIL ||
                            material == Material.DAMAGED_ANVIL) {

                        cancelGraceEndingTask(playerUUID);
                        p.sendMessage(prefix + "You are no longer invulnerable because you interacted with a block!");
                    }
                }
            }
        }
    }

    /**
     * TODO javadoc for PlayerInteractEntity
     *
     * @param event The event where a Player interacts with an Entity
     */
    @EventHandler
    public void onPlayerInteractEntityEvent(final @NotNull PlayerInteractEntityEvent event) {
        Player p = event.getPlayer();
        String playerUUID = p.getUniqueId().toString();

        if(playerHasGracePeriod(playerUUID)) {
            Entity targetEntity = event.getRightClicked();

            /*
            TODO Check to make sure all the cases where the grace period
                 should end are handled. Any Entity with an inventory or
                 some degree of interactibility (beyond movement) should
                 break the grace period.
             */

            if(targetEntity instanceof InventoryHolder) {
                cancelGraceEndingTask(playerUUID);
                p.sendMessage(prefix + "You are no longer invulnerable because you interacted with an entity!");
            }
        }
    }

    /** This handles the event where an entity is damaged. If the entity is a player, and is on the grace period,
     * the event is canceled (damage is not received), and the victim and attacker (if applicable) are messaged.
     *
     * If the attacking entity is a Player and is on Grace Period, the grace period is revoked.
     *
     * @param event The event where an Entity receives damage
     */
    @EventHandler
    public void onPlayerDamageEvent(final @NotNull EntityDamageEvent event) {
        Entity e = event.getEntity();

        if(e instanceof Player) {
            Player p = (Player) e;
            String playerUUID = p.getUniqueId().toString();

            if (playersOnGracePeriod.containsKey(playerUUID)) {
                event.setCancelled(true);
                this.getLogger().info("Player is on grace period");

                long timeAtRespawn = playersOnGracePeriod.get(playerUUID);

                //invincibleTime is converted from ticks to milliseconds, as (1/20) * 1000 = 50
                long timeLeft = (invincibleTime * 50) - (System.currentTimeMillis() - timeAtRespawn);

                //invincibleTime is converted to seconds to tell the player how much longer they are invulnerable
                p.sendMessage(prefix + "You are protected from damage for "
                        + ChatColor.GREEN + (timeLeft / 1000)
                        + ChatColor.RESET + " seconds!");
            }
        }

        if(event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageByEntityEvent = (EntityDamageByEntityEvent) event;
            Entity enemy = damageByEntityEvent.getDamager();

            if(enemy instanceof Player) {
                Player damager = (Player) enemy;
                String damagerUUID = damager.getUniqueId().toString();

                if(event.isCancelled()) {
                    damager.sendMessage(prefix + "This player can't take damage yet!");
                }

                if(playerHasGracePeriod(damagerUUID)) {
                    event.setCancelled(true);
                    this.getLogger().info("Attacker was on grace period");

                    cancelGraceEndingTask(damagerUUID);
                    damager.sendMessage(prefix + "You are no longer invulnerable because you are attacking!");
                }
            }
        }
    }

    /**
     * Adds the respawned player to the gracePeriod HashMap to signify this player
     * is invulnerable. A task is scheduled to remove the player from the HashMap after
     * a configurable amount of time (measured in ticks) passes, therefore signifying they are
     * no longer invulnerable.
     *
     * @param event Event where a player respawns
     */
    @EventHandler
    public void onPlayerRespawnEvent(final @NotNull PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        String playerUUID = p.getUniqueId().toString();

        long timeAtRespawn = System.currentTimeMillis();

        playersOnGracePeriod.put(playerUUID, timeAtRespawn);
        p.sendMessage(prefix + "You are now invulnerable for " + ChatColor.GREEN + (invincibleTime / 20) + ChatColor.RESET + " seconds!");

        int taskID = Bukkit.getScheduler().runTaskLater(this, () -> {
            playersOnGracePeriod.remove(playerUUID);
            p.sendMessage(prefix + "You are no longer on grace period!");
        }, invincibleTime).getTaskId();

        playerGraceEndingTask.put(playerUUID, taskID);
    }

    /**
     * Remove the entry using the UUID key from both HashMaps.
     * Used to prematurely remove a player from the Grace Period.
     * The BukkitTask associated with the player's grace period must be canceled.
     *
     * @param UUID The player's UUID, the key used in the HashMaps
     */
    private void cancelGraceEndingTask(String UUID){
        playersOnGracePeriod.remove(UUID);
        Bukkit.getServer().getScheduler().cancelTask(playerGraceEndingTask.remove(UUID));
    }

    /**
     * Checks if a player has an active grace period.
     *
     * @param UUID String representation of a Player's UUID
     * @return true if player has a grace period, false otherwise
     */
    private boolean playerHasGracePeriod(String UUID) {
        return playersOnGracePeriod.containsKey(UUID);
    }
}
