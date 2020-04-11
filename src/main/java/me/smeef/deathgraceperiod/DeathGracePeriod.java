package me.smeef.deathgraceperiod;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;

public final class DeathGracePeriod extends JavaPlugin implements Listener {

    //This is in units of Ticks. 1 second = 20 ticks
    private long invincibleTime;

    private final String prefix = "[" + ChatColor.RED + "DeathGrace" + ChatColor.RESET + "] ";

    private HashMap<String, Long> playersOnGracePeriod;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        //Time is in ticks, so multiply by 20 to convert from seconds to ticks
        this.invincibleTime = getConfig().getLong("invincibility-duration", 60L) * 20L;

        playersOnGracePeriod = new HashMap<String, Long>();
        getServer().getPluginManager().registerEvents(this, this);

    }

    @Override
    public void onDisable() {
    }

    /** This handles the event where an entity, who is a player,
     * receives damage. It negates all incoming damage if the player
     * has recently died.
     *
     * If the damage was caused by another player,
     * send a message to the attacking player that
     * their target is invulnerable.
     * @param event The event where an Entity receives damage
     */
    @EventHandler
    public void onPlayerDamageEvent(final EntityDamageEvent event) {
        Entity e = event.getEntity();

        if(e instanceof Player) {
            Player p = (Player) e;
            String playerUUID = p.getUniqueId().toString();

            if (playersOnGracePeriod.containsKey(playerUUID)) {
                event.setCancelled(true);
                this.getLogger().info("Player is on grace period");

                long timeAtRespawn = playersOnGracePeriod.get(playerUUID);

                //invincibleTime is converted from ticks to milliseconds, as (1/20) * 1000 = 50
                long timeLeft = (invincibleTime *  50) - (System.currentTimeMillis() - timeAtRespawn);

                //invincible time is converted to seconds to tell the player how much longer they are invulnerable
                p.sendMessage(prefix + "You are protected from damage for "
                        + ChatColor.GREEN + (timeLeft / 1000)
                        + ChatColor.RESET + " seconds!");

                if(event instanceof EntityDamageByEntityEvent) {
                    EntityDamageByEntityEvent damageByEntityEvent = (EntityDamageByEntityEvent) event;

                    Entity enemy = damageByEntityEvent.getDamager();

                    if(enemy instanceof Player) {
                        Player damager = (Player) enemy;
                        damager.sendMessage(prefix + "This player can't take damage yet!");
                    }
                }

            } else {
                this.getLogger().info("Player is NOT on grace period");
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
    public void onPlayerRespawnEvent(final PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        String playerUUID = p.getUniqueId().toString();

        long timeAtRespawn = System.currentTimeMillis();

        playersOnGracePeriod.put(playerUUID, timeAtRespawn);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            playersOnGracePeriod.remove(playerUUID);
            p.sendMessage(prefix + "You are no longer on grace period!");
        }, invincibleTime);
    }
}
