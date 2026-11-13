package dev.weedsecurity;

import dev.weedsecurity.command.SecurityCommand;
import dev.weedsecurity.command.PinCommand;
import dev.weedsecurity.db.SecurityDatabase;
import dev.weedsecurity.security.PinService;
import dev.weedsecurity.security.SecurityListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class WeedSecurity extends JavaPlugin {
 private SecurityDatabase database;
 private PinService pins;
 @Override public void onEnable(){
  saveDefaultConfig();
  database=new SecurityDatabase(this);
  try { database.open(); } catch(Exception ex){getLogger().severe("Database startup failed: "+ex.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
  pins=new PinService(this,database);
  getServer().getPluginManager().registerEvents(new SecurityListener(this,pins),this);
  getCommand("weedsecurity").setExecutor(new SecurityCommand(this,database));
  getCommand("weedsecurity").setTabCompleter((org.bukkit.command.TabCompleter) getCommand("weedsecurity").getExecutor());
  getCommand("pincode").setExecutor(new PinCommand(this,pins,database));
  getCommand("pincode").setTabCompleter((org.bukkit.command.TabCompleter) getCommand("pincode").getExecutor());
  getLogger().info("WeedSecurity enabled. SQLite storage is ready.");
 }
 @Override public void onDisable(){if(database!=null)database.close();}
 public PinService pins(){return pins;}
 public SecurityDatabase database(){return database;}
}
