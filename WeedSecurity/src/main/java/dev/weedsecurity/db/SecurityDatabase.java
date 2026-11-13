package dev.weedsecurity.db;

import dev.weedsecurity.WeedSecurity;
import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

/** SQLite persistence for PIN credentials, trusted sessions and security audit events. */
public final class SecurityDatabase {
 private final WeedSecurity plugin;
 private Connection connection;
 public SecurityDatabase(WeedSecurity plugin){this.plugin=plugin;}
 public synchronized void open() throws SQLException {
  File folder=new File(plugin.getDataFolder(),"data"); if(!folder.exists()&&!folder.mkdirs()) throw new SQLException("Cannot create database directory");
  try{Class.forName("org.sqlite.JDBC");}catch(ClassNotFoundException e){throw new SQLException("SQLite driver missing",e);}
  connection=DriverManager.getConnection("jdbc:sqlite:"+new File(folder,"security.db").getAbsolutePath());
  try(Statement s=connection.createStatement()){
   s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA foreign_keys=ON");
   s.execute("CREATE TABLE IF NOT EXISTS player_security (uuid TEXT PRIMARY KEY, pin_hash TEXT, pin_salt TEXT, trusted_ip_hash TEXT, trusted_until INTEGER NOT NULL DEFAULT 0, attempts INTEGER NOT NULL DEFAULT 0, locked_until INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");
   s.execute("CREATE TABLE IF NOT EXISTS security_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, actor_uuid TEXT, target_uuid TEXT, action TEXT NOT NULL, details TEXT)");
   s.execute("CREATE INDEX IF NOT EXISTS idx_security_logs_time ON security_logs(time)");
  }
 }
 public synchronized Connection connection(){if(connection==null)throw new IllegalStateException("Database is not open");return connection;}
 public synchronized void audit(UUID actor,UUID target,String action,String details){
  try(PreparedStatement p=connection().prepareStatement("INSERT INTO security_logs(time,actor_uuid,target_uuid,action,details) VALUES(?,?,?,?,?)")){
   p.setLong(1,System.currentTimeMillis());p.setString(2,actor==null?null:actor.toString());p.setString(3,target==null?null:target.toString());p.setString(4,action);p.setString(5,details);p.executeUpdate();
  }catch(SQLException e){plugin.getLogger().log(Level.SEVERE,"Could not write security audit record",e);}
 }
 public synchronized void close(){if(connection!=null)try{connection.close();}catch(SQLException e){plugin.getLogger().log(Level.WARNING,"Database close failed",e);}}
}
