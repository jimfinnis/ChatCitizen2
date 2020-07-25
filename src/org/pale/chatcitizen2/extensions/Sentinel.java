package org.pale.chatcitizen2.extensions;

import java.util.UUID;

import net.citizensnpcs.api.npc.NPC;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.pale.chatcitizen2.ChatTrait;
import org.pale.chatcitizen2.Plugin;
import org.pale.simplechat.Conversation;
import org.pale.simplechat.actions.ActionException;
import org.pale.simplechat.actions.Cmd;
import org.pale.simplechat.actions.Value;
import org.pale.simplechat.values.DoubleValue;
import org.pale.simplechat.values.IntValue;
import org.pale.simplechat.values.NoneValue;
import org.pale.simplechat.values.StringValue;

public class Sentinel {
	
	// (-- timeinseconds|none) return time since attack (NEED TO CHECK UNITS) or none if not a sentinel
	@Cmd public static void sentinel_timeSinceAttack(Conversation c) throws ActionException {
		NPC npc  = ((ChatTrait)c.instance.getData()).getNPC();
		org.pale.chatcitizen2.plugininterfaces.Sentinel.SentinelData d = Plugin.getInstance().sentinelPlugin.makeData(npc);
		if(d==null){
			c.push(NoneValue.instance);
		} else {
			c.push(new IntValue((int)d.timeSinceAttack));
		}
	}
			
	// (-- timeinseconds|none) return time since spawn (NEED TO CHECK UNITS) or none if not a sentinel
	@Cmd public static void sentinel_timeSinceSpawn(Conversation c) throws ActionException {
		NPC npc  = ((ChatTrait)c.instance.getData()).getNPC();
		org.pale.chatcitizen2.plugininterfaces.Sentinel.SentinelData d = Plugin.getInstance().sentinelPlugin.makeData(npc);
		if(d==null){
			c.push(NoneValue.instance);
		} else {
			c.push(new IntValue((int)d.timeSinceSpawn));
		}
	}
	
	// (-- string|none) return name of player, "something" or "nothing".
	@Cmd public static void sentinel_guarding(Conversation c) throws ActionException {
		NPC npc  = ((ChatTrait)c.instance.getData()).getNPC();
		org.pale.chatcitizen2.plugininterfaces.Sentinel.SentinelData d = Plugin.getInstance().sentinelPlugin.makeData(npc);
		if(d==null){
			c.push(NoneValue.instance);
		} else {
			c.push(new StringValue(d.guarding));
		}
	}
			
	// (-- double|none) return health of sentinel
	@Cmd public static void sentinel_health(Conversation c) throws ActionException {
		NPC npc  = ((ChatTrait)c.instance.getData()).getNPC();
		org.pale.chatcitizen2.plugininterfaces.Sentinel.SentinelData d = Plugin.getInstance().sentinelPlugin.makeData(npc);
		if(d==null){
			c.push(NoneValue.instance);
		} else {
			c.push(new DoubleValue(d.health));
		}
	}		
			
	// (name|none -- boolean) attempt to guard a player (interlocutor if none), returns 1 if player exists
	@Cmd public static void sentinel_guard(Conversation c) throws ActionException {
		Value nv = c.pop();
		Player p;
		if(nv == NoneValue.instance){
			p = (Player)c.source;
		} else {
			p = Bukkit.getServer().getPlayer(nv.str());
			if(p==null){
				c.push(new IntValue(false));
				return;
			}
		}
		NPC npc  = ((ChatTrait)c.instance.getData()).getNPC();
		Plugin.getInstance().sentinelPlugin.setGuard(npc,p.getUniqueId());
		c.push(new IntValue(true));
	}
			
	// (--) remove any guard status
	@Cmd public static void sentinel_guardoff(Conversation c) throws ActionException {
		NPC npc  = ((ChatTrait)c.instance.getData()).getNPC();
		Plugin.getInstance().sentinelPlugin.setGuard(npc,null);
	}
	
	// (-- str) appears to return some kind of debug string?
	@Cmd public static void sentinel_debug(Conversation c) throws ActionException {
		NPC npc  = ((ChatTrait)c.instance.getData()).getNPC();
		org.pale.chatcitizen2.plugininterfaces.Sentinel.SentinelData d = Plugin.getInstance().sentinelPlugin.makeData(npc);
		if(d!=null)
			c.push(new StringValue(d.debug));
		else
			c.push(new StringValue("not a sentinel"));
	}
}
