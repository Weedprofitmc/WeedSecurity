package dev.weedsecurity;

import dev.weedsecurity.command.SecurityCommand;
import dev.weedsecurity.command.PinCommand;
import dev.weedsecurity.db.SecurityDatabase;
import dev.weedsecurity.security.PinService;
import dev.weedsecurity.security.SecurityListener;
import dev.weedsecurity.menu.SecurityMenus;
import org.bukkit.plugin.java.JavaPlugin;

public final class WeedSecurity extends JavaPlugin {
 private SecurityDatabase database; private PinService pins; private SecurityMenus menus;
 @Override public void onEnable(){
  saveDefaultConfig(); database=new SecurityDatabase(this);
  try{database.open();}catch(Exception ex){getLogger().severe("Database startup failed: "+ex.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
  pins=new PinService(this,database); menus=new SecurityMenus(this,database,pins);
  getServer().getPluginManager().registerEvents(new SecurityListener(this,pins,database),this);
  getServer().getPluginManager().registerEvents(menus,this);
  SecurityCommand security=new SecurityCommand(this,database,pins,menus);
  getCommand("weedsecurity").setExecutor(security); getCommand("weedsecurity").setTabCompleter(security);
  PinCommand pin=new PinCommand(this,pins,database); getCommand("pincode").setExecutor(pin); getCommand("pincode").setTabCompleter(pin);
  getLogger().info("WeedSecurity enabled: PIN, operator allowlist, audit and admin GUIs ready.");
 }
 @Override public void onDisable(){if(database!=null)database.close();}
 public PinService pins(){return pins;} public SecurityDatabase database(){return database;}
}