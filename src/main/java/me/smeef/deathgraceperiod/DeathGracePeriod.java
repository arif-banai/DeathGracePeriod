package me.smeef.deathgraceperiod;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class DeathGracePeriod extends JavaPlugin implements Listener {

    private int invincibleTime;

    private final String prefix = "[ + " + ChatColor.RED + "DeathGrace" + ChatColor.RESET + "]";

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        //Time is in ticks, so multiply by 20 to convert from seconds to ticks
        this.invincibleTime = getConfig().getInt("invincibility-duration", 60) * 20;
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
            PotionEffect effectOnPlayer = p.getPotionEffect(PotionEffectType.HEALTH_BOOST);
            if (effectOnPlayer != null) {
                int activeEffectAmplifier = effectOnPlayer.getAmplifier();
                if(activeEffectAmplifier >= 200) {
                    event.setCancelled(true);

                    p.sendMessage(prefix + "You are protected from damage for "
                            + ChatColor.GREEN + effectOnPlayer.getDuration()
                            + ChatColor.RESET + "seconds!");

                    if(event instanceof EntityDamageByEntityEvent) {
                        EntityDamageByEntityEvent damageByEntityEvent = (EntityDamageByEntityEvent) event;

                        Entity enemy = damageByEntityEvent.getDamager();

                        if(enemy instanceof Player) {
                            Player damager = (Player) enemy;
                            damager.sendMessage(prefix + "This player can't take damage yet!");
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerRespawnEvent(final PlayerRespawnEvent event) {
        Player p = event.getPlayer();

        PotionEffect invincible = new PotionEffect(PotionEffectType.HEALTH_BOOST,
                invincibleTime,
                200,
                true,
                true,
                true);

        p.addPotionEffect(invincible);
        p.sendMessage(prefix + "You are now invincible for " + ChatColor.GREEN + invincibleTime + ChatColor.RESET + " seconds!");
    }
}
