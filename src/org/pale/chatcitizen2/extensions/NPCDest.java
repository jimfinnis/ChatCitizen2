package org.pale.chatcitizen2.extensions;

import net.citizensnpcs.api.npc.NPC;

import org.pale.chatcitizen2.ChatTrait;
import org.pale.chatcitizen2.plugininterfaces.NPCDestinations;
import org.pale.simplechat.actions.ActionException;
import org.pale.simplechat.actions.Cmd;
import org.pale.simplechat.actions.Value;
import org.pale.simplechat.values.NoneValue;
import org.pale.simplechat.values.StringValue;
import org.pale.simplechat.Conversation;

public class NPCDest {
	// npcgo (none|timeInSeconds locationtagOrNumber -- ) go to location for a given time in millisecs (none=1 day), returning:
	//	NOND	(not an NPC destinations npc)
	//	NOLOC	(no location tag of that name)
	//  OK      (everything worked)
	
	@Cmd public static void npcgo(Conversation c) throws ActionException{
		String loc = c.popString();
		Value tt = c.pop();
		int time;
		if(tt == NoneValue.instance)
			time = 86400*1000;
		else 
			time = tt.toInt();
		
		String out;
		
		ChatTrait t = (ChatTrait)c.instance.getData(); 
		NPC npc  = t.getNPC();
		NPCDestinations.NPCDestData d = t.nddat;
		if(d==null)
			out = "NOND";
		else {
			if(d.go(loc,time))
				out = "OK";
			else
				out = "NOLOC";
		}
		c.push(new StringValue(out));
	}
}
