package dev.weedsecurity.security;

import dev.weedsecurity.WeedSecurity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;

public final class SecurityListener implements Listener {
 private static final String PREFIX="§8[§dWEEDSECURITY§8] §7";
 private final WeedSecurity plugin;private final PinService pins;
 public SecurityListener(WeedSecurity plugin,PinService pins){this.plugin=plugin;this.pins=pins;}
 private boolean verified(org.bukkit.entity.Player p){
  if(!plugin.getConfig().getBoolean("security.pin-enabled",true))return true;
  if(!pins.hasPin(p.getUniqueId()))return false;
  String ip=p.getAddress()==null?"":p.getAddress().getAddress().getHostAddress();
  return pins.trusted(p.getUniqueId(),ip)||p.hasPermission("weedsecurity.pin.bypass");
 }
 @EventHandler public void join(PlayerJoinEvent e){
  if(!plugin.getConfig().getBoolean("security.pin-enabled",true))return;
  var p=e.getPlayer();String ip=p.getAddress()==null?"":p.getAddress().getAddress().getHostAddress();
  if(!pins.hasPin(p.getUniqueId()))p.sendMessage(PREFIX+"§eПервый вход: установите PIN командой §f/pincode set <PIN>");
  else if(!pins.trusted(p.getUniqueId(),ip))p.sendMessage(PREFIX+"§eВведите PIN командой §f/pincode <PIN>");
 }
 @EventHandler public void move(PlayerMoveEvent e){
  if(!verified(e.getPlayer())&&e.getTo()!=null&&(e.getFrom().getBlockX()!=e.getTo().getBlockX()||e.getFrom().getBlockY()!=e.getTo().getBlockY()||e.getFrom().getBlockZ()!=e.getTo().getBlockZ()))e.setTo(e.getFrom());
 }
 @EventHandler public void interact(PlayerInteractEvent e){if(!verified(e.getPlayer()))e.setCancelled(true);}
 @EventHandler public void interactEntity(PlayerInteractEntityEvent e){if(!verified(e.getPlayer()))e.setCancelled(true);}
 @EventHandler public void chat(AsyncPlayerChatEvent e){if(!verified(e.getPlayer())){e.setCancelled(true);e.getPlayer().sendMessage(PREFIX+"§cСначала подтвердите PIN.");}}
 @EventHandler public void command(PlayerCommandPreprocessEvent e){
  String raw=e.getMessage().toLowerCase(java.util.Locale.ROOT);
  if(!e.getPlayer().isOp()&&(raw.equals("/weedsecurity")||raw.startsWith("/weedsecurity ")||raw.equals("/ws")||raw.startsWith("/ws "))){
   e.setCancelled(true);e.getPlayer().sendMessage(PREFIX+"§cВ доступе отказано.");return;
  }
  if(!verified(e.getPlayer())&&!(raw.equals("/pincode")||raw.startsWith("/pincode "))){
   e.setCancelled(true);e.getPlayer().sendMessage(PREFIX+"§cСначала подтвердите PIN.");
  }
 }
 @EventHandler public void inventory(InventoryOpenEvent e){if(e.getPlayer() instanceof org.bukkit.entity.Player p&&!verified(p))e.setCancelled(true);}
 @EventHandler public void click(InventoryClickEvent e){if(e.getWhoClicked() instanceof org.bukkit.entity.Player p&&!verified(p))e.setCancelled(true);}
 @EventHandler public void drop(PlayerDropItemEvent e){if(!verified(e.getPlayer()))e.setCancelled(true);}
 @EventHandler public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof org.bukkit.entity.Player p&&!verified(p))e.setCancelled(true);}
}
