package dev.weedsecurity.security;

import dev.weedsecurity.WeedSecurity;
import dev.weedsecurity.db.SecurityDatabase;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;
import java.util.UUID;

public final class PinService {
 private final WeedSecurity plugin; private final SecurityDatabase db; private final SecureRandom random=new SecureRandom();
 public PinService(WeedSecurity plugin,SecurityDatabase db){this.plugin=plugin;this.db=db;}
 public boolean hasPin(UUID uuid){
  try(PreparedStatement p=db.connection().prepareStatement("SELECT pin_hash FROM player_security WHERE uuid=?")){p.setString(1,uuid.toString());try(ResultSet r=p.executeQuery()){return r.next()&&r.getString(1)!=null;}}
  catch(SQLException e){plugin.getLogger().severe("PIN lookup failed: "+e.getMessage());return false;}
 }
 public boolean setup(UUID uuid,String pin){
  if(!valid(pin))return false;
  byte[] salt=new byte[16];random.nextBytes(salt);
  try(PreparedStatement p=db.connection().prepareStatement("INSERT INTO player_security(uuid,pin_hash,pin_salt,updated_at) VALUES(?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET pin_hash=excluded.pin_hash,pin_salt=excluded.pin_salt,attempts=0,locked_until=0,trusted_ip_hash=NULL,trusted_until=0,updated_at=excluded.updated_at")){
   p.setString(1,uuid.toString());p.setString(2,hash(pin,salt));p.setString(3,Base64.getEncoder().encodeToString(salt));p.setLong(4,System.currentTimeMillis());p.executeUpdate();return true;
  }catch(SQLException e){plugin.getLogger().severe("PIN setup failed: "+e.getMessage());return false;}
 }
 public boolean verify(UUID uuid,String pin,String ip){
  String storedHash=null,saltText=null;int attempts=0;long locked=0;
  try(PreparedStatement p=db.connection().prepareStatement("SELECT pin_hash,pin_salt,attempts,locked_until FROM player_security WHERE uuid=?")){
   p.setString(1,uuid.toString());try(ResultSet r=p.executeQuery()){if(!r.next())return false;storedHash=r.getString(1);saltText=r.getString(2);attempts=r.getInt(3);locked=r.getLong(4);}
  }catch(SQLException e){plugin.getLogger().severe("PIN read failed: "+e.getMessage());return false;}
  long now=System.currentTimeMillis();if(storedHash==null||now<locked)return false;
  boolean valid=false;
  try{byte[] expected=Base64.getDecoder().decode(storedHash);byte[] actual=Base64.getDecoder().decode(hash(pin,Base64.getDecoder().decode(saltText)));valid=MessageDigest.isEqual(expected,actual);}catch(Exception ignored){}
  try(PreparedStatement p=db.connection().prepareStatement("UPDATE player_security SET attempts=?,locked_until=?,trusted_ip_hash=?,trusted_until=?,updated_at=? WHERE uuid=?")){
   if(valid){p.setInt(1,0);p.setLong(2,0);p.setString(3,ipHash(ip));p.setLong(4,now+plugin.getConfig().getLong("security.trusted-ip-hours",24)*3600000L);}
   else{int next=attempts+1;p.setInt(1,next);p.setLong(2,next>=plugin.getConfig().getInt("security.max-attempts",5)?now+plugin.getConfig().getLong("security.lock-minutes",15)*60000L:0);p.setString(3,null);p.setLong(4,0);}
   p.setLong(5,now);p.setString(6,uuid.toString());p.executeUpdate();
  }catch(SQLException e){plugin.getLogger().severe("PIN state update failed: "+e.getMessage());return false;}
  db.audit(uuid,uuid,valid?"PIN_ACCEPTED":"PIN_REJECTED",valid?"Trusted IP renewed":"Failed PIN attempt");
  return valid;
 }
 public boolean trusted(UUID uuid,String ip){
  try(PreparedStatement p=db.connection().prepareStatement("SELECT trusted_ip_hash,trusted_until FROM player_security WHERE uuid=?")){
   p.setString(1,uuid.toString());try(ResultSet r=p.executeQuery()){return r.next()&&r.getLong(2)>System.currentTimeMillis()&&ipHash(ip).equals(r.getString(1));}
  }catch(SQLException e){return false;}
 }
 public boolean locked(UUID uuid){
  try(PreparedStatement p=db.connection().prepareStatement("SELECT locked_until FROM player_security WHERE uuid=?")){p.setString(1,uuid.toString());try(ResultSet r=p.executeQuery()){return r.next()&&r.getLong(1)>System.currentTimeMillis();}}
  catch(SQLException e){return true;}
 }
 public void reset(UUID uuid){
  try(PreparedStatement p=db.connection().prepareStatement("DELETE FROM player_security WHERE uuid=?")){p.setString(1,uuid.toString());p.executeUpdate();}
  catch(SQLException e){plugin.getLogger().severe("PIN reset failed: "+e.getMessage());}
 }
 public boolean valid(String pin){return pin!=null&&pin.matches("\\d{"+plugin.getConfig().getInt("security.pin-length-min",4)+","+plugin.getConfig().getInt("security.pin-length-max",12)+"}");}
 private String hash(String pin,byte[] salt){
  try{PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,210000,256);byte[] out=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();spec.clearPassword();return Base64.getEncoder().encodeToString(out);}
  catch(Exception e){throw new IllegalStateException("PBKDF2 unavailable",e);}
 }
 private String ipHash(String ip){
  try{String secret=plugin.getConfig().getString("security.ip-hash-secret");if(secret==null||secret.length()<32)throw new IllegalStateException("security.ip-hash-secret must be at least 32 characters");byte[] bytes=MessageDigest.getInstance("SHA-256").digest((secret+"|"+ip).getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(bytes);}
  catch(Exception e){throw new IllegalStateException("IP hashing failed",e);}
 }
}
